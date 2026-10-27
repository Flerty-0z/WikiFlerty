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
    /** Status, favorite and shelves (Book.metaJson) as JSON text. */
    val meta: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fingerprint", fingerprint)
        put("title", title)
        put("author", author)
        put("progress", progress)
        put("locator", locatorJson)
        put("lastReadAt", lastReadAt)
        meta?.let { put("meta", JSONObject(it)) }
    }

    companion object {
        fun fromJson(j: JSONObject) = ForeignBook(
            fingerprint = j.getString("fingerprint"),
            title = j.optString("title"),
            author = j.optString("author"),
            progress = j.optDouble("progress", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            locatorJson = if (j.isNull("locator")) null else j.optString("locator").takeIf { it.isNotEmpty() },
            lastReadAt = j.optLong("lastReadAt"),
            meta = j.optJSONObject("meta")?.toString(),
        )
    }
}

/** What moving data between phones remembers. Saved to sync_state.json. */
data class SyncState(
    val syncSettings: Boolean = true,
    /** When this phone last changed its settings. */
    val settingsChangedAt: Long = 0,
    /** Deleted notes and words: id → time, so deletions reach other phones too. */
    val deletedNotes: Map<String, Long> = emptyMap(),
    val deletedWords: Map<String, Long> = emptyMap(),
    val foreignBooks: List<ForeignBook> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("syncSettings", syncSettings)
        put("settingsChangedAt", settingsChangedAt)
        put("deletedNotes", JSONObject(deletedNotes))
        put("deletedWords", JSONObject(deletedWords))
        put("foreignBooks", JSONArray().apply { foreignBooks.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(j: JSONObject) = SyncState(
            syncSettings = j.optBoolean("syncSettings", true),
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
