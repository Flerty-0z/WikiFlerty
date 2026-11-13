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

class InkStroke(val color: Long, val width: Float, val points: FloatArray) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("c", String.format("#%08X", color))
        put("w", width.toDouble())
        put("p", JSONArray().apply { points.forEach { put(Math.round(it * 10000f) / 10000.0) } })
    }

    companion object {
        fun fromJson(json: JSONObject): InkStroke? = runCatching {
            val arr = json.getJSONArray("p")
            InkStroke(
                json.getString("c").removePrefix("#").toLong(16),
                json.optDouble("w", 0.01).toFloat(),
                FloatArray(arr.length()) { arr.getDouble(it).toFloat() },
            )
        }.getOrNull()
    }
}

data class Illustration(
    val bookId: String,
    val href: String,
    val chapter: String?,
    val locatorJson: String?,
    val favorite: Boolean,
    val strokes: List<InkStroke>,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val key: String get() = "$bookId|$href"

    fun toJson(): JSONObject = JSONObject().apply {
        put("bookId", bookId)
        put("href", href)
        put("chapter", chapter ?: JSONObject.NULL)
        put("locator", locatorJson ?: JSONObject.NULL)
        put("favorite", favorite)
        put("strokes", JSONArray().apply { strokes.forEach { put(it.toJson()) } })
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Illustration {
            val strokes = json.optJSONArray("strokes")
            return Illustration(
                bookId = json.getString("bookId"),
                href = json.getString("href"),
                chapter = if (json.isNull("chapter")) null else json.optString("chapter").ifBlank { null },
                locatorJson = if (json.isNull("locator")) null else json.optString("locator").ifBlank { null },
                favorite = json.optBoolean("favorite"),
                strokes = if (strokes == null) emptyList() else (0 until strokes.length()).mapNotNull { i -> strokes.optJSONObject(i)?.let { InkStroke.fromJson(it) } },
                createdAt = json.optLong("createdAt"),
                updatedAt = json.optLong("updatedAt", json.optLong("createdAt")),
            )
        }
    }
}

class IllustrationsRepository(context: Context) {
    private val file = File(context.filesDir, "illustrations.json")
    private val mutex = Mutex()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Illustration>> = _items.asStateFlow()

    fun find(bookId: String, href: String): Illustration? = _items.value.firstOrNull { it.bookId == bookId && it.href == href }

    suspend fun upsert(bookId: String, href: String, chapter: String?, locatorJson: String?, change: (Illustration) -> Illustration) = update { list ->
        val now = System.currentTimeMillis()
        val base = list.firstOrNull { it.bookId == bookId && it.href == href }
            ?: Illustration(bookId, href, chapter, locatorJson, favorite = false, strokes = emptyList(), createdAt = now, updatedAt = now)
        val next = change(base).copy(
            chapter = base.chapter ?: chapter,
            locatorJson = base.locatorJson ?: locatorJson,
            updatedAt = now,
        )
        list.filterNot { it.key == next.key } + next
    }

    suspend fun replaceAll(items: List<Illustration>) = update { items }

    suspend fun deleteForBook(bookId: String) = update { list -> list.filterNot { it.bookId == bookId } }

    suspend fun remapBook(from: String, to: String) {
        if (_items.value.none { it.bookId == from }) return
        update { list -> list.map { if (it.bookId == from) it.copy(bookId = to) else it } }
    }

    private suspend fun update(transform: (List<Illustration>) -> List<Illustration>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = transform(_items.value)
            _items.value = updated
            save(updated)
        }
    }

    private fun load(): List<Illustration> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val array = JSONObject(file.readText()).optJSONArray("items") ?: JSONArray()
            (0 until array.length()).mapNotNull { i -> runCatching { Illustration.fromJson(array.getJSONObject(i)) }.getOrNull() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(items: List<Illustration>) {
        val json = JSONObject().apply {
            put("version", 1)
            put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        }
        val tmp = File(file.parentFile, "illustrations.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(json.toString())
            tmp.delete()
        }
    }
}
