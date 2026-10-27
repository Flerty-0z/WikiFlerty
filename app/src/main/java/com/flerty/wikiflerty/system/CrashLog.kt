package com.flerty.wikiflerty.system

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CrashEntry(val file: File, val time: Long, val title: String)

class CrashLog(private val context: Context) {
    private val dir = File(context.filesDir, "crashes")
    private val prefs = context.getSharedPreferences("crash_log", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<CrashEntry>> = _entries.asStateFlow()

    val hasUnseen: Boolean get() = prefs.getBoolean("unseen", false) && _entries.value.isNotEmpty()

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun markSeen() {
        prefs.edit().putBoolean("unseen", false).apply()
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        markSeen()
        _entries.value = emptyList()
    }

    fun report(): String = buildString {
        appendLine("Отчёт о сбоях WikiFlerty")
        appendLine(deviceInfo())
        _entries.value.forEach { entry ->
            appendLine()
            appendLine("────────────")
            append(runCatching { entry.file.readText() }.getOrDefault(entry.title))
        }
    }.trim()

    private fun write(thread: Thread, error: Throwable) {
        dir.mkdirs()
        val time = System.currentTimeMillis()
        val text = buildString {
            appendLine("Время: ${format(time)}")
            appendLine("Версия: ${version()}")
            appendLine("Поток: ${thread.name}")
            appendLine()
            append(error.stackTraceToString().lines().take(80).joinToString("\n"))
        }
        File(dir, "crash-$time.txt").writeText(text)
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(10)?.forEach { it.delete() }
        prefs.edit().putBoolean("unseen", true).commit()
    }

    private fun load(): List<CrashEntry> =
        dir.listFiles { f -> f.name.startsWith("crash-") }.orEmpty()
            .mapNotNull { file ->
                val time = file.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull() ?: return@mapNotNull null
                val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
                val title = lines.dropWhile { it.isNotBlank() }.firstOrNull { it.isNotBlank() }?.trim() ?: "Ошибка"
                CrashEntry(file, time, title)
            }
            .sortedByDescending { it.time }

    private fun version(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName.orEmpty()
    }.getOrDefault("?")

    private fun deviceInfo(): String =
        "Устройство: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), версия приложения ${version()}"

    companion object {
        fun format(time: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(time))
    }
}
