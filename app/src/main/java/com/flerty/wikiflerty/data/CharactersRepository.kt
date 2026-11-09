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

data class BookCharacter(
    val id: String,
    val bookId: String,
    val name: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("bookId", bookId)
        put("name", name)
        put("note", note)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): BookCharacter = BookCharacter(
            id = json.getString("id"),
            bookId = json.getString("bookId"),
            name = json.getString("name"),
            note = json.optString("note"),
            createdAt = json.optLong("createdAt"),
            updatedAt = json.optLong("updatedAt"),
        )
    }
}

class CharactersRepository(context: Context) {
    private val file = File(context.filesDir, "characters.json")
    private val mutex = Mutex()

    private val _characters = MutableStateFlow(load())
    val characters: StateFlow<List<BookCharacter>> = _characters.asStateFlow()

    var onDeleted: ((List<String>) -> Unit)? = null

    fun forBook(bookId: String): List<BookCharacter> =
        _characters.value.filter { it.bookId == bookId }.sortedBy { it.name.lowercase() }

    suspend fun save(bookId: String, id: String?, name: String, note: String): BookCharacter {
        val now = System.currentTimeMillis()
        val existing = id?.let { key -> _characters.value.firstOrNull { it.id == key } }
        val saved = existing?.copy(name = name.trim(), note = note.trim(), updatedAt = now)
            ?: BookCharacter(UUID.randomUUID().toString(), bookId, name.trim(), note.trim(), now, now)
        update { list -> list.filterNot { it.id == saved.id } + saved }
        return saved
    }

    suspend fun delete(ids: List<String>) {
        if (ids.isEmpty()) return
        update { list -> list.filterNot { it.id in ids } }
        onDeleted?.invoke(ids)
    }

    suspend fun deleteForBook(bookId: String) = delete(_characters.value.filter { it.bookId == bookId }.map { it.id })

    suspend fun replaceAll(characters: List<BookCharacter>) = update { characters }

    suspend fun remapBook(from: String, to: String) =
        update { list -> list.map { if (it.bookId == from) it.copy(bookId = to) else it } }

    private suspend fun update(transform: (List<BookCharacter>) -> List<BookCharacter>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = transform(_characters.value)
            _characters.value = updated
            save(updated)
        }
    }

    private fun load(): List<BookCharacter> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val array = JSONObject(file.readText()).optJSONArray("characters") ?: JSONArray()
            (0 until array.length()).mapNotNull { i -> runCatching { BookCharacter.fromJson(array.getJSONObject(i)) }.getOrNull() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(characters: List<BookCharacter>) {
        val json = JSONObject().apply {
            put("version", 1)
            put("characters", JSONArray().apply { characters.forEach { put(it.toJson()) } })
        }
        val tmp = File(file.parentFile, "characters.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(json.toString())
            tmp.delete()
        }
    }
}
