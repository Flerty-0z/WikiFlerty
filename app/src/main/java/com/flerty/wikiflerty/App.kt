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

    lateinit var style: StyleStore
        private set

    lateinit var readerSettings: ReaderSettingsStore
        private set

    lateinit var notes: NotesRepository
        private set

    lateinit var dictionary: DictionaryRepository
        private set

    lateinit var sync: SyncManager
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val readAloud: com.flerty.wikiflerty.tts.ReadAloud by lazy { com.flerty.wikiflerty.tts.ReadAloud(this) }

    val flashcards: com.flerty.wikiflerty.data.FlashcardStore by lazy { com.flerty.wikiflerty.data.FlashcardStore(this) }

    val stats: com.flerty.wikiflerty.data.ReadingStatsStore by lazy { com.flerty.wikiflerty.data.ReadingStatsStore(this) }

    val libraryPrefs: com.flerty.wikiflerty.data.LibraryPrefsStore by lazy { com.flerty.wikiflerty.data.LibraryPrefsStore(this) }

    val ai: AiManager by lazy { AiManager(this) }

    val crashLog: com.flerty.wikiflerty.system.CrashLog by lazy { com.flerty.wikiflerty.system.CrashLog(this) }

    val updates: com.flerty.wikiflerty.system.Updates by lazy { com.flerty.wikiflerty.system.Updates(this) }

    val backgroundAudio: com.flerty.wikiflerty.system.BackgroundAudio by lazy { com.flerty.wikiflerty.system.BackgroundAudio(this) }

    val feedback: Feedback by lazy { Feedback(this, readerSettings.settings) }

    var readerSession: ReaderSession? = null
        private set

    override fun onCreate() {
        super.onCreate()
        crashLog.install()
        style = StyleStore(this)
        readium = ReadiumServices(this)
        library = LibraryRepository(this, readium)
        readerSettings = ReaderSettingsStore(this)
        notes = NotesRepository(this)
        dictionary = DictionaryRepository(this)
        sync = SyncManager(this)

        notes.onDeleted = { sync.noteDeleted(it) }
        dictionary.onDeleted = { sync.wordDeleted(it) }
        readerSettings.onChanged = { sync.settingsChanged() }
        style.onChanged = { sync.settingsChanged() }
        ai.store.onChanged = { sync.settingsChanged() }
        library.onBookAdded = { book -> Backup.onBookAdded(this, book) }
        appScope.launch { library.ensureFingerprints() }
        backgroundAudio.install()
    }

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
