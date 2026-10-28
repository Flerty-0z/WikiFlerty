package com.flerty.wikiflerty.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.sync.Backup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AutoBackupConfig(
    val enabled: Boolean = false,
    val folder: String? = null,
    val intervalDays: Int = 1,
    val includeBooks: Boolean = false,
    val keep: Int = 5,
    val lastTime: Long = 0,
    val lastError: String? = null,
) {
    val folderUri: Uri? get() = folder?.let(Uri::parse)
}

class AutoBackup(private val app: App) {
    private val prefs = app.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<AutoBackupConfig> = _config.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val lock = Mutex()

    private fun load() = AutoBackupConfig(
        enabled = prefs.getBoolean("enabled", false),
        folder = prefs.getString("folder", null),
        intervalDays = prefs.getInt("interval", 1),
        includeBooks = prefs.getBoolean("books", false),
        keep = prefs.getInt("keep", 5),
        lastTime = prefs.getLong("last", 0),
        lastError = prefs.getString("error", null),
    )

    fun update(change: (AutoBackupConfig) -> AutoBackupConfig) {
        val next = change(_config.value)
        _config.value = next
        prefs.edit()
            .putBoolean("enabled", next.enabled)
            .putString("folder", next.folder)
            .putInt("interval", next.intervalDays)
            .putBoolean("books", next.includeBooks)
            .putInt("keep", next.keep)
            .putLong("last", next.lastTime)
            .putString("error", next.lastError)
            .apply()
    }

    fun setFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { app.contentResolver.takePersistableUriPermission(uri, flags) }
        val old = _config.value.folderUri
        if (old != null && old != uri) runCatching { app.contentResolver.releasePersistableUriPermission(old, flags) }
        update { it.copy(folder = uri.toString(), lastError = null) }
    }

    fun folderName(): String? {
        val uri = _config.value.folderUri ?: return null
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val volume = id.substringBefore(':', "")
        val path = id.substringAfter(':', id).trim('/')
        val root = if (volume == "primary" || volume.isEmpty()) "Память телефона" else "SD-карта"
        return if (path.isEmpty()) root else "$root/$path"
    }

    fun isDue(now: Long = System.currentTimeMillis()): Boolean {
        val c = _config.value
        if (!c.enabled || c.folder == null) return false
        return now - c.lastTime >= c.intervalDays * DAY - HOUR
    }

    suspend fun runIfDue() {
        if (isDue()) runNow()
    }

    suspend fun runNow(): Result<Unit> = lock.withLock {
        val c = _config.value
        val tree = c.folderUri ?: return Result.failure(IllegalStateException("Папка не выбрана"))
        _running.value = true
        val result = runCatching { write(tree, c) }
        _running.value = false
        result.onSuccess { update { it.copy(lastTime = System.currentTimeMillis(), lastError = null) } }
            .onFailure { e -> update { it.copy(lastError = e.message ?: "Не удалось сохранить") } }
        result
    }

    private suspend fun write(tree: Uri, c: AutoBackupConfig) {
        val resolver = app.contentResolver
        val dir = withContext(Dispatchers.IO) {
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        val name = "$PREFIX$stamp.wikiflerty"
        val file = withContext(Dispatchers.IO) {
            DocumentsContract.createDocument(resolver, dir, "application/octet-stream", name)
        } ?: throw IllegalStateException("Нет доступа к папке — выбери её заново")
        try {
            Backup.export(app, app, file, c.includeBooks)
        } catch (e: Exception) {
            withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(resolver, file) } }
            throw e
        }
        withContext(Dispatchers.IO) { prune(tree, c.keep) }
    }

    private fun prune(tree: Uri, keep: Int) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableListOf<Pair<String, String>>()
        app.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (name.startsWith(PREFIX)) found += id to name
            }
        }
        found.sortedByDescending { it.second }.drop(keep.coerceAtLeast(1)).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(app.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    companion object {
        private const val PREFIX = "WikiFlerty-auto-"
        private const val HOUR = 60L * 60 * 1000
        private const val DAY = 24 * HOUR
    }
}
