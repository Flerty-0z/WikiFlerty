package com.flerty.wikiflerty.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
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
    val apkUrl: String,
    val size: Long,
    val pageUrl: String,
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState()
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState()
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState()
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
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
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
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        _state.value = try {
            val info = fetchLatest()
            prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            if (info != null && info.code > currentCode) UpdateState.Available(info) else UpdateState.UpToDate
        } catch (e: Exception) {
            UpdateState.Failed("Не удалось проверить. Проверь интернет")
        }
    }

    suspend fun download(info: UpdateInfo) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(info, 0f)
        _state.value = try {
            val file = withContext(Dispatchers.IO) { fetchApk(info) }
            UpdateState.Ready(info, file)
        } catch (e: Exception) {
            UpdateState.Failed("Не удалось скачать обновление", info)
        }
    }

    fun install(file: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settings) }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
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
            val assets = json.optJSONArray("assets")
            var url = ""
            var size = 0L
            for (i in 0 until (assets?.length() ?: 0)) {
                val asset = assets!!.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    url = asset.optString("browser_download_url")
                    size = asset.optLong("size")
                    break
                }
            }
            if (url.isBlank()) return@withContext null
            UpdateInfo(
                version = tag.removePrefix("v"),
                code = code,
                notes = json.optString("body").trim(),
                apkUrl = url,
                size = size,
                pageUrl = json.optString("html_url"),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchApk(info: UpdateInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "WikiFlerty-${info.version}.apk")
        val partial = File(dir, "download.part")
        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "WikiFlerty")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.size
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0 && done - lastReport > 256 * 1024) {
                            lastReport = done
                            _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (!partial.renameTo(target)) throw IllegalStateException("rename")
            return target
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val LATEST = "https://api.github.com/repos/Flerty-0z/WikiFlerty/releases/latest"
    }
}
