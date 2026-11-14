package com.flerty.wikiflerty.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CustomChapter(val id: String, val title: String, val locator: String, val at: Double) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("locator", locator)
        put("at", at)
    }

    companion object {
        fun fromJson(j: JSONObject): CustomChapter? = runCatching {
            CustomChapter(j.getString("id"), j.getString("title"), j.getString("locator"), j.optDouble("at", 0.0))
        }.getOrNull()
    }
}

data class BookToc(val bookId: String, val chapters: List<CustomChapter>, val updatedAt: Long) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("bookId", bookId)
        put("chapters", JSONArray().apply { chapters.forEach { put(it.toJson()) } })
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(j: JSONObject): BookToc? = runCatching {
            val arr = j.optJSONArray("chapters") ?: JSONArray()
            BookToc(
                j.getString("bookId"),
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(CustomChapter::fromJson) },
                j.optLong("updatedAt"),
            )
        }.getOrNull()
    }
}

class CustomTocRepository(context: Context) {
    private val file = File(context.filesDir, "custom_toc.json")
    private val lock = Mutex()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<Map<String, BookToc>> = _items.asStateFlow()

    fun of(bookId: String): BookToc? = _items.value[bookId]

    suspend fun set(bookId: String, chapters: List<CustomChapter>) {
        val next = _items.value.toMutableMap()
        next[bookId] = BookToc(bookId, chapters.sortedBy { it.at }, System.currentTimeMillis())
        _items.value = next
        persist()
    }

    suspend fun replaceAll(list: List<BookToc>) {
        _items.value = list.associateBy { it.bookId }
        persist()
    }

    private suspend fun persist() = lock.withLock {
        val snapshot = _items.value.values.toList()
        withContext(Dispatchers.IO) {
            runCatching { file.writeText(JSONArray().apply { snapshot.forEach { put(it.toJson()) } }.toString()) }
        }
    }

    private fun load(): Map<String, BookToc> = runCatching {
        if (!file.exists()) return emptyMap()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(BookToc::fromJson) }.associateBy { it.bookId }
    }.getOrDefault(emptyMap())
}
