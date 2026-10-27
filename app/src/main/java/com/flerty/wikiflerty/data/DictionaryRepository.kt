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

/** A word saved to the user's dictionary, with its explanation and where it was met. */
data class DictionaryEntry(
    val id: String,
    val word: String,
    val definition: String,
    /** The sentence from the book where the word was met. */
    val context: String,
    val bookId: String?,
    val bookTitle: String?,
    val locatorJson: String?,
    val createdAt: Long,
) {
    val locator: Locator? get() = locatorJson?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("word", word)
        put("definition", definition)
        put("context", context)
        put("bookId", bookId)
        put("bookTitle", bookTitle)
        put("locator", locatorJson)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): DictionaryEntry = DictionaryEntry(
            id = json.getString("id"),
            word = json.optString("word"),
            definition = json.optString("definition"),
            context = json.optString("context"),
            bookId = json.optNullable("bookId"),
            bookTitle = json.optNullable("bookTitle"),
            locatorJson = json.optNullable("locator"),
            createdAt = json.optLong("createdAt"),
        )

        private fun JSONObject.optNullable(name: String): String? =
            if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
    }
}

/** The user's dictionary, stored in dictionary.json. Later part of sync and export. */
class DictionaryRepository(context: Context) {
    private val file = File(context.filesDir, "dictionary.json")
    private val mutex = Mutex()
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<DictionaryEntry>> = _entries.asStateFlow()

    suspend fun add(
        word: String,
        definition: String,
        context: String,
        bookId: String?,
        bookTitle: String?,
        locator: Locator?,
    ): DictionaryEntry {
        val entry = DictionaryEntry(
            id = UUID.randomUUID().toString(),
            word = word.trim(),
            definition = definition.trim(),
            context = context.trim(),
            bookId = bookId,
            bookTitle = bookTitle,
            locatorJson = locator?.toJSON()?.toString(),
            createdAt = System.currentTimeMillis(),
        )
        update { listOf(entry) + it }
        return entry
    }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    private suspend fun update(transform: (List<DictionaryEntry>) -> List<DictionaryEntry>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = transform(_entries.value)
            _entries.value = updated
            val json = JSONObject().put("version", 1).put("entries", JSONArray().apply { updated.forEach { put(it.toJson()) } })
            val tmp = File(file.parentFile, "dictionary.json.tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(json.toString())
                tmp.delete()
            }
        }
    }

    private fun load(): List<DictionaryEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONObject(file.readText()).optJSONArray("entries") ?: JSONArray()
        (0 until arr.length()).mapNotNull { runCatching { DictionaryEntry.fromJson(arr.getJSONObject(it)) }.getOrNull() }
    }.getOrElse { emptyList() }
}
