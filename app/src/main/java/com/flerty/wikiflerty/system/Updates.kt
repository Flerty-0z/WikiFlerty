package com.flerty.wikiflerty.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val version: String,
    val code: Int,
    val notes: String,
    val pageUrl: String,
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

class Updates(private val context: Context) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _auto = MutableStateFlow(prefs.getBoolean("auto", true))
    val auto: StateFlow<Boolean> = _auto.asStateFlow()

    var dismissedCode: Int
        get() = prefs.getInt("dismissed", 0)
        set(value) = prefs.edit().putInt("dismissed", value).apply()

    val currentCode: Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName?.substringAfterLast('.')?.toIntOrNull()
            ?: if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    }.getOrDefault(0)

    fun setAuto(value: Boolean) {
        _auto.value = value
        prefs.edit().putBoolean("auto", value).apply()
    }

    suspend fun autoCheck() {
        if (!_auto.value) return
        val last = prefs.getLong("lastCheck", 0)
        if (System.currentTimeMillis() - last < 6 * 60 * 60 * 1000L) return
        check()
    }

    suspend fun check() {
        if (_state.value is UpdateState.Checking) return
        _state.value = UpdateState.Checking
        _state.value = try {
            val info = fetchLatest()
            prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            if (info != null && info.code > currentCode) UpdateState.Available(info) else UpdateState.UpToDate
        } catch (e: Exception) {
            UpdateState.Failed("Не удалось проверить. Проверь интернет")
        }
    }

    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "WikiFlerty")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name")
            val code = tag.substringAfterLast('.').toIntOrNull() ?: return@withContext null
            UpdateInfo(
                version = tag.removePrefix("v"),
                code = code,
                notes = (if (json.isNull("body")) "" else json.optString("body")).trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty(),
                pageUrl = json.optString("html_url"),
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val TELEGRAM = "https://t.me/wikiflerty"
        const val RUSTORE = "https://www.rustore.ru/catalog/app/com.flerty.wikiflerty"
        const val IN_RUSTORE = false
        private const val LATEST = "https://api.github.com/repos/Flerty-0z/WikiFlerty/releases/latest"
    }
}
