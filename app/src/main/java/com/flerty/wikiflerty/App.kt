package com.flerty.wikiflerty

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

class App : Application() {
    lateinit var readium: ReadiumServices
        private set

    lateinit var library: LibraryRepository
        private set
    val imports by lazy { com.flerty.wikiflerty.system.ImportQueue(this) }

    lateinit var style: StyleStore
        private set

    lateinit var readerSettings: ReaderSettingsStore
        private set

    lateinit var notes: NotesRepository
        private set

    lateinit var dictionary: DictionaryRepository
        private set

    lateinit var bookmarks: com.flerty.wikiflerty.data.BookmarksRepository
    lateinit var characters: com.flerty.wikiflerty.data.CharactersRepository
        private set

    lateinit var sync: SyncManager
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val autoInfoLock = kotlinx.coroutines.sync.Mutex()

    val readAloud: com.flerty.wikiflerty.tts.ReadAloud by lazy { com.flerty.wikiflerty.tts.ReadAloud(this) }

    val flashcards: com.flerty.wikiflerty.data.FlashcardStore by lazy { com.flerty.wikiflerty.data.FlashcardStore(this) }

    val stats: com.flerty.wikiflerty.data.ReadingStatsStore by lazy { com.flerty.wikiflerty.data.ReadingStatsStore(this) }

    val achievements: com.flerty.wikiflerty.data.AchievementStore by lazy { com.flerty.wikiflerty.data.AchievementStore(this) }

    val readingTimer by lazy { com.flerty.wikiflerty.system.ReadingTimer(this) }

    val paper: com.flerty.wikiflerty.data.PaperSessionStore by lazy { com.flerty.wikiflerty.data.PaperSessionStore(this) }

    val aiHistory by lazy { com.flerty.wikiflerty.data.AiHistoryStore(this) }

    val heroes by lazy { com.flerty.wikiflerty.data.HeroStore(this) }


    val ambient: com.flerty.wikiflerty.system.AmbientPlayer by lazy { com.flerty.wikiflerty.system.AmbientPlayer(this) }

    val streakFreeze: com.flerty.wikiflerty.data.StreakFreezeStore by lazy { com.flerty.wikiflerty.data.StreakFreezeStore(this) }

    val libraryPrefs: com.flerty.wikiflerty.data.LibraryPrefsStore by lazy { com.flerty.wikiflerty.data.LibraryPrefsStore(this) }

    val ai: AiManager by lazy { AiManager(this) }

    val crashLog: com.flerty.wikiflerty.system.CrashLog by lazy { com.flerty.wikiflerty.system.CrashLog(this) }

    val autoBackup: com.flerty.wikiflerty.system.AutoBackup by lazy { com.flerty.wikiflerty.system.AutoBackup(this) }
    val cloud: com.flerty.wikiflerty.sync.CloudSync by lazy { com.flerty.wikiflerty.sync.CloudSync(this) }

    fun syncedTitle(bookId: String?): String? {
        val fp = bookId?.takeIf { it.startsWith("fp:") }?.removePrefix("fp:") ?: return null
        return sync.store.state.value.foreignBooks.firstOrNull { it.fingerprint == fp }?.title?.takeIf { it.isNotBlank() }
    }

    val updates: com.flerty.wikiflerty.system.Updates by lazy { com.flerty.wikiflerty.system.Updates(this) }

    val backgroundAudio: com.flerty.wikiflerty.system.BackgroundAudio by lazy { com.flerty.wikiflerty.system.BackgroundAudio(this) }

    val feedback: Feedback by lazy { Feedback(this, readerSettings.settings) }

    var readerSession: ReaderSession? = null
        private set

    override fun onCreate() {
        super.onCreate()
        crashLog.install()
        com.flerty.wikiflerty.ui.EInk.init(this)
        streakFreeze
        Thread({ runCatching { com.flerty.wikiflerty.reader.FontCatalog.warm(this) }; runCatching { java.io.File(filesDir, "charmap").deleteRecursively(); java.io.File(filesDir, "pet.json").delete() } }, "font-warm").apply { priority = Thread.MIN_PRIORITY }.start()
        style = StyleStore(this)
        readium = ReadiumServices(this)
        library = LibraryRepository(this, readium)
        readerSettings = ReaderSettingsStore(this)
        notes = NotesRepository(this)
        dictionary = DictionaryRepository(this)
        bookmarks = com.flerty.wikiflerty.data.BookmarksRepository(this)
        characters = com.flerty.wikiflerty.data.CharactersRepository(this)
        sync = SyncManager(this)

        notes.onDeleted = { sync.noteDeleted(it) }
        dictionary.onDeleted = { sync.wordDeleted(it) }
        bookmarks.onDeleted = { sync.bookmarkDeleted(it) }
        characters.onDeleted = { sync.characterDeleted(it) }
        readerSettings.onChanged = { sync.settingsChanged() }
        style.onChanged = {
            sync.settingsChanged()
            appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.delay(600)
                runCatching { com.flerty.wikiflerty.widget.WidgetSkin.refreshAll(this@App) }
                runCatching { com.flerty.wikiflerty.widget.WidgetPreviews.publish(this@App) }
            }
        }
        ai.store.onChanged = { sync.settingsChanged() }
        library.onBookAdded = { book ->
            Backup.onBookAdded(this, book)
            cloud.request(1_000)
            if (libraryPrefs.prefs.value.autoInfo && !book.paper && (book.series.isEmpty() || book.fact(com.flerty.wikiflerty.data.BookFacts.Genres).isBlank() || book.fact(com.flerty.wikiflerty.data.BookFacts.Publisher).isBlank())) {
                appScope.launch {
                    autoInfoLock.withLock {
                        runCatching {
                            val found = com.flerty.wikiflerty.data.BookLookup.auto(book.title, book.author)
                            if (found != null) library.fillDetails(book.id, found.description, found.facts, found.series)
                        }
                        kotlinx.coroutines.delay(500)
                    }
                }
            }
        }
        appScope.launch {
            library.ensureFingerprints()
            library.ensureScan()
        }
        imports.resume()
        backgroundAudio.install()
        achievements.start(appScope)
        runCatching { com.flerty.wikiflerty.system.ReadingReminder.cancel(this) }
        com.flerty.wikiflerty.system.FocusMode.load(this)
        appScope.launch {
            kotlinx.coroutines.flow.combine(readingTimer.watch, paper.stopwatch, com.flerty.wikiflerty.system.FocusMode.enabled) { w, p, _ ->
                (w?.running == true) || (p?.running == true)
            }.collect { reading -> runCatching { com.flerty.wikiflerty.system.FocusMode.update(this@App, reading) } }
        }
        appScope.launch {
            var first = true
            notes.notes.collectLatest {
                if (first) {
                    first = false
                    return@collectLatest
                }
                kotlinx.coroutines.delay(1500)
                kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { com.flerty.wikiflerty.widget.QuoteWidget.refreshAll(this@App) } }
            }
        }
        appScope.launch {
            kotlinx.coroutines.delay(20_000)
            autoBackup.runIfDue()
        }
        appScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(4_000)
            runCatching { com.flerty.wikiflerty.widget.WidgetPreviews.publish(this@App) }
        }
        appScope.launch {
            kotlinx.coroutines.flow.merge(
                bookmarks.bookmarks.drop(1).map { },
                notes.notes.drop(1).map { },
                characters.characters.drop(1).map { },
            ).collect { cloud.request(4_000) }
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            private var ticker: kotlinx.coroutines.Job? = null
            override fun onActivityStarted(activity: android.app.Activity) {
                started++
                if (started == 1) {
                    cloud.request(2_500)
                    ticker?.cancel()
                    ticker = appScope.launch {
                        while (true) {
                            kotlinx.coroutines.delay(4 * 60_000L)
                            cloud.request(0)
                        }
                    }
                }
            }
            override fun onActivityStopped(activity: android.app.Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) {
                    ticker?.cancel()
                    appScope.launch(kotlinx.coroutines.Dispatchers.IO) { library.flush() }
                    cloud.request(0)
                }
            }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
        Thread({
            runCatching {
                val grid = libraryPrefs.prefs.value.view != com.flerty.wikiflerty.data.LibraryView.Shelves
                com.flerty.wikiflerty.ui.warmCovers(library.books.value.mapNotNull { library.coverFile(it) }, grid)
            }
            coversWarm.complete(Unit)
        }, "cover-warm").apply { priority = Thread.NORM_PRIORITY - 1 }.start()
    }

    val coversWarm = kotlinx.coroutines.CompletableDeferred<Unit>()

    suspend fun prepareReader(book: Book, startAt: Locator? = null): Result<Unit> {
        closeReader()
        val publication = readium.open(library.bookFile(book)).getOrElse { return Result.failure(it) }
        val locator = startAt ?: book.locatorJson?.let {
            runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
        }
        val start = locator?.let { if (it.locations.progression != null) it.copy(text = Locator.Text()) else it }
        readerSession = ReaderSession(book.id, publication, start)
        library.markOpened(book.id)
        return Result.success(Unit)
    }

    fun closeReader() {
        readerSession?.publication?.close()
        readerSession = null
    }
}
