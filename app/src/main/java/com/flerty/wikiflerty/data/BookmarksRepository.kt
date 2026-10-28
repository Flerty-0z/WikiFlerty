package com.flerty.wikiflerty.data

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

data class Bookmark(
    val id: String,
    val bookId: String,
    val locatorJson: String,
    val chapter: String?,
    val snippet: String,
    val progression: Double,
    val createdAt: Long,
) {
    val locator: Locator? get() = runCatching { Locator.fromJSON(JSONObject(locatorJson)) }.getOrNull()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("bookId", bookId)
        put("locator", locatorJson)
        put("chapter", chapter)
        put("snippet", snippet)
        put("progression", progression)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Bookmark = Bookmark(
            id = json.getString("id"),
            bookId = json.getString("bookId"),
            locatorJson = json.getString("locator"),
            chapter = if (json.isNull("chapter")) null else json.optString("chapter").takeIf { it.isNotEmpty() },
            snippet = json.optString("snippet"),
            progression = json.optDouble("progression", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            createdAt = json.optLong("createdAt"),
        )
    }
}

class BookmarksRepository(context: Context) {
    private val file = File(context.filesDir, "bookmarks.json")
    private val mutex = Mutex()

    private val _bookmarks = MutableStateFlow(load())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    var onDeleted: ((List<String>) -> Unit)? = null

    suspend fun create(bookId: String, locator: Locator, chapter: String?, snippet: String): Bookmark {
        val created = Bookmark(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            locatorJson = locator.toJSON().toString(),
            chapter = chapter?.trim()?.takeIf { it.isNotEmpty() },
            snippet = snippet.replace(Regex("\\s+"), " ").trim().take(160),
            progression = locator.locations.totalProgression ?: 0.0,
            createdAt = System.currentTimeMillis(),
        )
        update { it + created }
        return created
    }

    suspend fun delete(ids: List<String>) {
        if (ids.isEmpty()) return
        update { list -> list.filterNot { it.id in ids } }
        onDeleted?.invoke(ids)
    }

    suspend fun deleteForBook(bookId: String) {
        val ids = _bookmarks.value.filter { it.bookId == bookId }.map { it.id }
        delete(ids)
    }

    suspend fun replaceAll(bookmarks: List<Bookmark>) = update { bookmarks }

    suspend fun remapBook(from: String, to: String) =
        update { list -> list.map { if (it.bookId == from) it.copy(bookId = to) else it } }

    private suspend fun update(transform: (List<Bookmark>) -> List<Bookmark>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = transform(_bookmarks.value)
            _bookmarks.value = updated
            save(updated)
        }
    }

    private fun load(): List<Bookmark> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val array = JSONObject(file.readText()).optJSONArray("bookmarks") ?: JSONArray()
            (0 until array.length()).mapNotNull { i -> runCatching { Bookmark.fromJson(array.getJSONObject(i)) }.getOrNull() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(bookmarks: List<Bookmark>) {
        val json = JSONObject().apply {
            put("version", 1)
            put("bookmarks", JSONArray().apply { bookmarks.forEach { put(it.toJson()) } })
        }
        val tmp = File(file.parentFile, "bookmarks.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(json.toString())
            tmp.delete()
        }
    }
}
