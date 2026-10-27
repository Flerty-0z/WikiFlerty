package com.flerty.wikiflerty.sync

import com.flerty.wikiflerty.App
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
 * Sync through Google Drive: download the file, merge it into this phone,
 * then upload the merged result back. Runs by hand or automatically when the app opens
 * and when a book is closed.
 */
class SyncManager(private val app: App) {
    val store = SyncStateStore(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun clouds(): List<CloudStore> =
        if (store.state.value.googleConnected) listOf(GoogleDriveStore(app)) else emptyList()

    val hasCloud: Boolean get() = clouds().isNotEmpty()

    suspend fun syncNow(): Result<String> = mutex.withLock {
        val clouds = clouds()
        if (clouds.isEmpty()) return Result.failure(SyncException("Войди через Google, чтобы включить синхронизацию"))
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

    companion object {
        fun formatTime(time: Long): String =
            if (time <= 0) "ещё не было" else SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(time))
    }
}
