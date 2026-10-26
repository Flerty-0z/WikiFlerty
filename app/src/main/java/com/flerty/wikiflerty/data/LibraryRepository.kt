package com.flerty.wikiflerty.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.flerty.wikiflerty.reader.ReadiumServices
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
import org.readium.r2.shared.publication.services.cover

/**
 * Keeps the list of books and their reading progress.
 *
 * Everything is stored in one JSON file (library.json). Later, the same data
 * becomes the base for sync with Google Drive / Dropbox and for export/import.
 */
class LibraryRepository(
    private val context: Context,
    private val readium: ReadiumServices,
) {
    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val libraryFile = File(context.filesDir, "library.json")
    private val mutex = Mutex()

    private val _books = MutableStateFlow(load())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    fun bookFile(book: Book): File = File(booksDir, book.fileName)

    fun coverFile(book: Book): File? = book.coverFileName?.let { File(coversDir, it) }

    /** Copies an EPUB into the app, reads its title, author and cover, and adds it to the library. */
    suspend fun import(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val file = File(booksDir, "$id.epub")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Не удалось прочитать файл"))
            input.use { source -> file.outputStream().use { source.copyTo(it) } }

            val publication = readium.open(file).getOrElse {
                file.delete()
                return@withContext Result.failure(it)
            }
            try {
                val title = publication.metadata.title?.trim()?.takeIf { it.isNotEmpty() }
                    ?: displayName(uri)?.substringBeforeLast('.')
                    ?: "Без названия"
                val author = publication.metadata.authors.joinToString(", ") { it.name }

                // The same book added twice: keep the copy that is already there.
                _books.value.firstOrNull { it.title == title && it.author == author }?.let { existing ->
                    file.delete()
                    return@withContext Result.success(existing)
                }

                val coverName = publication.cover()?.let { bitmap ->
                    val name = "$id.jpg"
                    File(coversDir, name).outputStream().use { out ->
                        bitmap.scaledToHeight(900).compress(Bitmap.CompressFormat.JPEG, 88, out)
                    }
                    name
                }

                val book = Book(
                    id = id,
                    title = title,
                    author = author,
                    fileName = file.name,
                    coverFileName = coverName,
                    addedAt = System.currentTimeMillis(),
                    lastReadAt = null,
                    progress = 0.0,
                    locatorJson = null,
                )
                update { it + book }
                Result.success(book)
            } finally {
                publication.close()
            }
        } catch (e: Exception) {
            file.delete()
            Result.failure(e)
        }
    }

    suspend fun delete(book: Book) = withContext(Dispatchers.IO) {
        update { list -> list.filterNot { it.id == book.id } }
        bookFile(book).delete()
        coverFile(book)?.delete()
    }

    suspend fun markOpened(bookId: String) {
        val now = System.currentTimeMillis()
        update { list -> list.map { if (it.id == bookId) it.copy(lastReadAt = now) else it } }
    }

    suspend fun saveProgress(bookId: String, locator: Locator) {
        val json = locator.toJSON().toString()
        update { list ->
            list.map {
                if (it.id != bookId) {
                    it
                } else {
                    it.copy(
                        locatorJson = json,
                        progress = locator.locations.totalProgression ?: it.progress,
                        lastReadAt = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    private suspend fun update(transform: (List<Book>) -> List<Book>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = transform(_books.value)
            _books.value = updated
            save(updated)
        }
    }

    private fun load(): List<Book> = try {
        if (!libraryFile.exists()) {
            emptyList()
        } else {
            val array = JSONObject(libraryFile.readText()).optJSONArray("books") ?: JSONArray()
            (0 until array.length()).mapNotNull { i ->
                runCatching { Book.fromJson(array.getJSONObject(i)) }.getOrNull()
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(books: List<Book>) {
        val json = JSONObject().apply {
            put("version", 1)
            put("books", JSONArray().apply { books.forEach { put(it.toJson()) } })
        }
        // Write to a temporary file first so a crash never leaves a half-written library.
        val tmp = File(libraryFile.parentFile, "library.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(libraryFile)) {
            libraryFile.writeText(json.toString())
            tmp.delete()
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment

    private fun Bitmap.scaledToHeight(maxHeight: Int): Bitmap {
        if (height <= maxHeight) return this
        val width = (width * maxHeight.toFloat() / height).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, width, maxHeight, true)
    }
}
