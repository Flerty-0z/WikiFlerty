package com.flerty.wikiflerty.sync

import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.ai.KeyVault
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Sync: download the file from every connected cloud, merge it into this phone,
 * then upload the merged result back. Runs by hand or automatically when the app opens
 * and when a book is closed.
 */
class SyncManager(private val app: App) {
    val store = SyncStateStore(app)
    /** Dropbox refresh token, encrypted like the AI keys. */
    val vault = KeyVault(app, "sync_keys.bin", "wikiflerty_sync_keys")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Kept between syncs so its short-lived access token is reused. */
    private var dropbox: DropboxStore? = null
    private var dropboxKey = ""

    fun clouds(): List<CloudStore> {
        val s = store.state.value
        return buildList {
            if (s.googleConnected) add(GoogleDriveStore(app))
            if (s.dropboxConnected && s.dropboxAppKey.isNotBlank() && vault.get(DROPBOX_TOKEN) != null) {
                if (dropbox == null || dropboxKey != s.dropboxAppKey) {
                    dropbox = DropboxStore(s.dropboxAppKey) { vault.get(DROPBOX_TOKEN) }
                    dropboxKey = s.dropboxAppKey
                }
                add(dropbox!!)
            }
        }
    }

    val hasCloud: Boolean get() = clouds().isNotEmpty()

    suspend fun syncNow(): Result<String> = mutex.withLock {
        val clouds = clouds()
        if (clouds.isEmpty()) return Result.failure(SyncException("Подключи Google Drive или Dropbox"))
        _running.value = true
        try {
            var report = MergeReport()
            val problems = mutableListOf<String>()
            val reachable = mutableListOf<CloudStore>()
            for (cloud in clouds) {
                try {
                    cloud.download()?.let { text ->
                        report += Backup.merge(app, JSONObject(text), applySettings = store.state.value.syncSettings)
                    }
                    reachable += cloud
                } catch (e: Exception) {
                    problems += e.message ?: "${cloud.title}: ошибка"
                }
            }
            val merged = Backup.snapshot(app).toString()
            for (cloud in reachable) {
                try {
                    cloud.upload(merged)
                } catch (e: Exception) {
                    problems += e.message ?: "${cloud.title}: ошибка"
                }
            }
            val message = if (problems.isEmpty()) report.describe() else problems.joinToString("\n")
            store.update { it.copy(lastSyncAt = System.currentTimeMillis(), lastResult = message) }
            if (problems.isNotEmpty() && reachable.isEmpty()) Result.failure(SyncException(message)) else Result.success(message)
        } finally {
            _running.value = false
        }
    }

    private var lastAuto = 0L

    /** Quietly syncs if it is turned on and has not run in the last minute. */
    fun autoSync() {
        val s = store.state.value
        if (!s.autoSync || !hasCloud || _running.value) return
        val now = System.currentTimeMillis()
        if (now - lastAuto < 60_000) return
        lastAuto = now
        scope.launch { runCatching { syncNow() } }
    }

    // ------------------------------------------------------------ hooks from the data stores

    fun noteDeleted(ids: List<String>) {
        val now = System.currentTimeMillis()
        store.update { it.copy(deletedNotes = it.deletedNotes + ids.associateWith { now }) }
    }

    fun wordDeleted(ids: List<String>) {
        val now = System.currentTimeMillis()
        store.update { it.copy(deletedWords = it.deletedWords + ids.associateWith { now }) }
    }

    fun settingsChanged() {
        store.update { it.copy(settingsChangedAt = System.currentTimeMillis()) }
    }

    fun saveDropbox(appKey: String, refreshToken: String) {
        vault.set(DROPBOX_TOKEN, refreshToken)
        store.update { it.copy(dropboxAppKey = appKey.trim(), dropboxConnected = true) }
    }

    fun disconnectDropbox() {
        vault.set(DROPBOX_TOKEN, null)
        store.update { it.copy(dropboxConnected = false) }
    }

    companion object {
        const val DROPBOX_TOKEN = "dropbox_refresh"

        fun formatTime(time: Long): String =
            if (time <= 0) "ещё не было" else SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(time))
    }
}
