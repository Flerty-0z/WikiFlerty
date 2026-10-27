package com.flerty.wikiflerty.sync

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Reading progress of a book that is on another phone but not (yet) on this one. */
data class ForeignBook(
    val fingerprint: String,
    val title: String,
    val author: String,
    val progress: Double,
    val locatorJson: String?,
    val lastReadAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fingerprint", fingerprint)
        put("title", title)
        put("author", author)
        put("progress", progress)
        put("locator", locatorJson)
        put("lastReadAt", lastReadAt)
    }

    companion object {
        fun fromJson(j: JSONObject) = ForeignBook(
            fingerprint = j.getString("fingerprint"),
            title = j.optString("title"),
            author = j.optString("author"),
            progress = j.optDouble("progress", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            locatorJson = if (j.isNull("locator")) null else j.optString("locator").takeIf { it.isNotEmpty() },
            lastReadAt = j.optLong("lastReadAt"),
        )
    }
}

/** What sync remembers between runs. Saved to sync_state.json. */
data class SyncState(
    val googleConnected: Boolean = false,
    val dropboxAppKey: String = "",
    val dropboxConnected: Boolean = false,
    val autoSync: Boolean = true,
    val syncSettings: Boolean = true,
    val lastSyncAt: Long = 0,
    val lastResult: String? = null,
    /** When this phone last changed its settings. */
    val settingsChangedAt: Long = 0,
    /** Deleted notes and words: id → time, so deletions reach other phones too. */
    val deletedNotes: Map<String, Long> = emptyMap(),
    val deletedWords: Map<String, Long> = emptyMap(),
    val foreignBooks: List<ForeignBook> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("googleConnected", googleConnected)
        put("dropboxAppKey", dropboxAppKey)
        put("dropboxConnected", dropboxConnected)
        put("autoSync", autoSync)
        put("syncSettings", syncSettings)
        put("lastSyncAt", lastSyncAt)
        put("lastResult", lastResult)
        put("settingsChangedAt", settingsChangedAt)
        put("deletedNotes", JSONObject(deletedNotes))
        put("deletedWords", JSONObject(deletedWords))
        put("foreignBooks", JSONArray().apply { foreignBooks.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(j: JSONObject) = SyncState(
            googleConnected = j.optBoolean("googleConnected"),
            dropboxAppKey = j.optString("dropboxAppKey"),
            dropboxConnected = j.optBoolean("dropboxConnected"),
            autoSync = j.optBoolean("autoSync", true),
            syncSettings = j.optBoolean("syncSettings", true),
            lastSyncAt = j.optLong("lastSyncAt"),
            lastResult = if (j.isNull("lastResult")) null else j.optString("lastResult"),
            settingsChangedAt = j.optLong("settingsChangedAt"),
            deletedNotes = j.optJSONObject("deletedNotes").toLongMap(),
            deletedWords = j.optJSONObject("deletedWords").toLongMap(),
            foreignBooks = j.optJSONArray("foreignBooks")?.let { a ->
                (0 until a.length()).mapNotNull { runCatching { ForeignBook.fromJson(a.getJSONObject(it)) }.getOrNull() }
            }.orEmpty(),
        )
    }
}

fun JSONObject?.toLongMap(): Map<String, Long> =
    this?.keys()?.asSequence()?.associateWith { optLong(it) }.orEmpty()

class SyncStateStore(context: Context) {
    private val file = File(context.filesDir, "sync_state.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    @Synchronized
    fun update(transform: (SyncState) -> SyncState) {
        val updated = transform(_state.value)
        if (updated == _state.value) return
        _state.value = updated
        runCatching { file.writeText(updated.toJson().toString()) }
    }

    private fun load(): SyncState = runCatching {
        if (file.exists()) SyncState.fromJson(JSONObject(file.readText())) else SyncState()
    }.getOrElse { SyncState() }
}
