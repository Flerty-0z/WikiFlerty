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

/**
 * A highlight in a book, with an optional description.
 * A note with an empty [note] is a plain marker.
 */
data class Note(
    val id: String,
    val bookId: String,
    /** Exact place in the book (a Readium Locator) as JSON. */
    val locatorJson: String,
    /** The highlighted text from the book. */
    val text: String,
    /** The user's own description. */
    val note: String,
    /** Highlight color, 0xAARRGGBB. */
    val color: Long,
    val chapter: String?,
    /** Position in the whole book, 0.0..1.0, used for sorting. */
    val progression: Double,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val locator: Locator? get() = runCatching { Locator.fromJSON(JSONObject(locatorJson)) }.getOrNull()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("bookId", bookId)
        put("locator", locatorJson)
        put("text", text)
        put("note", note)
        put("color", String.format("#%08X", color))
        put("chapter", chapter)
        put("progression", progression)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Note = Note(
            id = json.getString("id"),
            bookId = json.getString("bookId"),
            locatorJson = json.getString("locator"),
            text = json.optString("text"),
            note = json.optString("note"),
            color = parseColor(json.optString("color")),
            chapter = if (json.isNull("chapter")) null else json.optString("chapter").takeIf { it.isNotEmpty() },
            progression = json.optDouble("progression", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            createdAt = json.optLong("createdAt"),
            updatedAt = json.optLong("updatedAt"),
        )

        private fun parseColor(value: String): Long =
            runCatching { value.removePrefix("#").toLong(16).let { if (value.length <= 7) it or 0xFF000000 else it } }
                .getOrDefault(0xFFE9C84A)
    }
}

/** Highlight colors offered everywhere. */
object NoteColors {
    val presets: List<Pair<String, Long>> = listOf(
        "Жёлтый" to 0xFFE9C84A,
        "Зелёный" to 0xFF6FCF97,
        "Голубой" to 0xFF6FA8F5,
        "Розовый" to 0xFFF08FA8,
        "Фиолетовый" to 0xFFB79CFF,
        "Оранжевый" to 0xFFF2A65A,
        "Красный" to 0xFFEF6B6B,
        "Бирюзовый" to 0xFF4FD1C5,
    )
    val default: Long = presets.first().second
}

/** All notes of all books, stored in one file (notes.json). Later part of sync and export. */
class NotesRepository(context: Context) {
    private val file = File(context.filesDir, "notes.json")
    private val mutex = Mutex()

    private val _notes = MutableStateFlow(load())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    suspend fun create(
        bookId: String,
        locator: Locator,
        text: String,
        note: String,
        color: Long,
        chapter: String?,
    ): Note {
        val now = System.currentTimeMillis()
        val created = Note(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            locatorJson = locator.toJSON().toString(),
            text = text.trim(),
            note = note.trim(),
            color = color,
            chapter = chapter,
            progression = locator.locations.totalProgression ?: 0.0,
            createdAt = now,
            updatedAt = now,
        )
        update { it + created }
        return created
    }

    suspend fun edit(id: String, note: String, color: Long) {
        val now = System.currentTimeMillis()
        update { list -> list.map { if (it.id == id) it.copy(note = note.trim(), color = color, updatedAt = now) else it } }
    }

    /** Remembers deletions so sync can remove the same notes on other phones. */
    var onDeleted: ((List<String>) -> Unit)? = null

    suspend fun delete(id: String) {
        update { list -> list.filterNot { it.id == id } }
        onDeleted?.invoke(listOf(id))
    }

    suspend fun deleteForBook(bookId: String) {
        val ids = _notes.value.filter { it.bookId == bookId }.map { it.id }
        update { list -> list.filterNot { it.bookId == bookId } }
        if (ids.isNotEmpty()) onDeleted?.invoke(ids)
    }

    /** Replaces everything at once (after merging with another phone). */
    suspend fun replaceAll(notes: List<Note>) = update { notes }

    /** Points notes of [from] (e.g. a book known only by fingerprint) to the book [to]. */
    suspend fun remapBook(from: String, to: String) =
        update { list -> list.map { if (it.bookId == from) it.copy(bookId = to) else it } }

    private suspend fun update(transform: (List<Note>) -> List<Note>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = transform(_notes.value)
            _notes.value = updated
            save(updated)
        }
    }

    private fun load(): List<Note> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val array = JSONObject(file.readText()).optJSONArray("notes") ?: JSONArray()
            (0 until array.length()).mapNotNull { i -> runCatching { Note.fromJson(array.getJSONObject(i)) }.getOrNull() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(notes: List<Note>) {
        val json = JSONObject().apply {
            put("version", 1)
            put("notes", JSONArray().apply { notes.forEach { put(it.toJson()) } })
        }
        val tmp = File(file.parentFile, "notes.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(json.toString())
            tmp.delete()
        }
    }
}
