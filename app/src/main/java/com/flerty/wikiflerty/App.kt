package com.flerty.wikiflerty

import android.app.Application
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.LibraryRepository
import com.flerty.wikiflerty.reader.ReaderSession
import com.flerty.wikiflerty.reader.ReaderSettingsStore
import com.flerty.wikiflerty.reader.ReadiumServices
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

class App : Application() {

    lateinit var readium: ReadiumServices
        private set

    lateinit var library: LibraryRepository
        private set

    lateinit var readerSettings: ReaderSettingsStore
        private set

    /** The book currently open in the reader, kept in memory while reading. */
    var readerSession: ReaderSession? = null
        private set

    override fun onCreate() {
        super.onCreate()
        readium = ReadiumServices(this)
        library = LibraryRepository(this, readium)
        readerSettings = ReaderSettingsStore(this)
    }

    /** Opens the book file and prepares everything the reader screen needs. */
    suspend fun prepareReader(book: Book): Result<Unit> {
        closeReader()
        val publication = readium.open(library.bookFile(book)).getOrElse { return Result.failure(it) }
        val locator = book.locatorJson?.let {
            runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
        }
        readerSession = ReaderSession(book.id, publication, locator)
        library.markOpened(book.id)
        return Result.success(Unit)
    }

    fun closeReader() {
        readerSession?.publication?.close()
        readerSession = null
    }
}
