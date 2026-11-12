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

enum class CharacterRole(val id: String, val title: String) {
    Main("main", "Главный герой"),
    Love("love", "Любовный интерес"),
    Villain("villain", "Антагонист"),
    Support("support", "Второстепенный"),
    Other("other", "Другое");

    companion object {
        fun of(id: String): CharacterRole? = entries.firstOrNull { it.id == id }
        fun title(role: String): String = of(role)?.title ?: role
    }
}

data class BookCharacter(
    val id: String,
    val bookId: String,
    val name: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val role: String = "",
    val looks: String = "",
    val ties: String = "",
    val fate: String = "",
    val fateHidden: Boolean = true,
    val quotes: List<String> = emptyList(),
    val books: List<String> = emptyList(),
    val avatarAt: Long = 0,
) {
    val filled: Int get() = listOf(note, looks, ties, fate).count { it.isNotBlank() } + if (quotes.isNotEmpty()) 1 else 0

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("bookId", bookId)
        put("name", name)
        put("note", note)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("role", role)
        put("looks", looks)
        put("ties", ties)
        put("fate", fate)
        put("fateHidden", fateHidden)
        put("quotes", JSONArray(quotes))
        put("books", JSONArray(books))
        put("avatarAt", avatarAt)
    }

    companion object {
        private fun strings(json: JSONObject, name: String): List<String> =
            json.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() } }.orEmpty()

        fun fromJson(json: JSONObject): BookCharacter = BookCharacter(
            id = json.getString("id"),
            bookId = json.getString("bookId"),
            name = json.getString("name"),
            note = json.optString("note"),
            createdAt = json.optLong("createdAt"),
            updatedAt = json.optLong("updatedAt"),
            role = json.optString("role"),
            looks = json.optString("looks"),
            ties = json.optString("ties"),
            fate = json.optString("fate"),
            fateHidden = json.optBoolean("fateHidden", true),
            quotes = strings(json, "quotes"),
            books = strings(json, "books"),
            avatarAt = json.optLong("avatarAt"),
        )
    }
}

class CharactersRepository(private val context: Context) {
    private val file = File(context.filesDir, "characters.json")
    private val images = File(context.filesDir, "characters_img").apply { mkdirs() }

    fun avatar(id: String): File = File(images, "$id.jpg")

    fun avatars(): List<Pair<String, File>> = images.listFiles().orEmpty().filter { it.name.endsWith(".jpg") }.map { it.name.removeSuffix(".jpg") to it }

    suspend fun setAvatar(id: String, uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        val ok = runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / sample > 900) sample *= 2
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching false
            val side = minOf(bitmap.width, bitmap.height)
            val square = android.graphics.Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 4, side, side)
            val scaled = if (side > 360) android.graphics.Bitmap.createScaledBitmap(square, 360, 360, true) else square
            avatar(id).outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            true
        }.getOrDefault(false)
        if (ok) edit(id) { it.copy(avatarAt = System.currentTimeMillis()) }
        ok
    }

    suspend fun removeAvatar(id: String) {
        withContext(Dispatchers.IO) { runCatching { avatar(id).delete() } }
        edit(id) { it.copy(avatarAt = System.currentTimeMillis()) }
    }

    fun applyAvatar(id: String, bytes: ByteArray, at: Long) {
        runCatching {
            val target = avatar(id)
            target.writeBytes(bytes)
            target.setLastModified(at)
        }
        _characters.value = _characters.value.map { if (it.id == id) it.copy(avatarAt = at) else it }
    }

    suspend fun edit(id: String, change: (BookCharacter) -> BookCharacter): BookCharacter? {
        val now = System.currentTimeMillis()
        var result: BookCharacter? = null
        update { list ->
            list.map { c ->
                if (c.id != id) c else change(c).copy(id = c.id, bookId = c.bookId, createdAt = c.createdAt, updatedAt = now).also { result = it }
            }
        }
        return result
    }

    fun visibleIn(bookId: String, statsKey: String?): List<BookCharacter> =
        _characters.value.filter { it.bookId == bookId || (statsKey != null && statsKey in it.books) }.sortedBy { it.name.lowercase() }

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
        ids.forEach { runCatching { avatar(it).delete() } }
        onDeleted?.invoke(ids)
    }

    suspend fun deleteForBook(bookId: String) = delete(_characters.value.filter { it.bookId == bookId }.map { it.id })

    suspend fun replaceAll(characters: List<BookCharacter>) = update { characters }

    suspend fun remapBook(from: String, to: String) {
        if (_characters.value.none { it.bookId == from }) return
        update { list -> list.map { if (it.bookId == from) it.copy(bookId = to) else it } }
    }

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
