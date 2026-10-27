package com.flerty.wikiflerty

import android.app.Application
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.LibraryRepository
import com.flerty.wikiflerty.data.NotesRepository
import com.flerty.wikiflerty.data.DictionaryRepository
import com.flerty.wikiflerty.ai.AiManager
import com.flerty.wikiflerty.reader.ReaderSession
import com.flerty.wikiflerty.reader.ReaderSettingsStore
import com.flerty.wikiflerty.reader.ReadiumServices
import com.flerty.wikiflerty.ui.Feedback
import com.flerty.wikiflerty.ui.StyleStore
import com.flerty.wikiflerty.sync.Backup
import com.flerty.wikiflerty.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

class App : Application() {

    lateinit var readium: ReadiumServices
        private set

    lateinit var library: LibraryRepository
        private set

    /** The look of the interface (colors, shapes, fonts, background). */
    lateinit var style: StyleStore
        private set

    lateinit var readerSettings: ReaderSettingsStore
        private set

    lateinit var notes: NotesRepository
        private set

    lateinit var dictionary: DictionaryRepository
        private set

    /** Sync with Google Drive / Dropbox, and backups. */
    lateinit var sync: SyncManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** AI providers, keys and requests. */
    val ai: AiManager by lazy { AiManager(this) }

    /** Soft button sounds and vibration, shared by all screens. */
    val feedback: Feedback by lazy { Feedback(this, readerSettings.settings) }

    /** The book currently open in the reader, kept in memory while reading. */
    var readerSession: ReaderSession? = null
        private set

    override fun onCreate() {
        super.onCreate()
        style = StyleStore(this)
        readium = ReadiumServices(this)
        library = LibraryRepository(this, readium)
        readerSettings = ReaderSettingsStore(this)
        notes = NotesRepository(this)
        dictionary = DictionaryRepository(this)
        sync = SyncManager(this)

        // Let sync know about deletions and setting changes, and attach synced data to newly added books.
        notes.onDeleted = { sync.noteDeleted(it) }
        dictionary.onDeleted = { sync.wordDeleted(it) }
        readerSettings.onChanged = { sync.settingsChanged() }
        style.onChanged = { sync.settingsChanged() }
        ai.store.onChanged = { sync.settingsChanged() }
        library.onBookAdded = { book -> Backup.onBookAdded(this, book) }
        appScope.launch { library.ensureFingerprints() }
    }

    /**
     * Opens the book file and prepares everything the reader screen needs.
     * [startAt] opens the book at a given place (a note) instead of the last read position.
     */
    suspend fun prepareReader(book: Book, startAt: Locator? = null): Result<Unit> {
        closeReader()
        val publication = readium.open(library.bookFile(book)).getOrElse { return Result.failure(it) }
        val locator = startAt ?: book.locatorJson?.let {
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
