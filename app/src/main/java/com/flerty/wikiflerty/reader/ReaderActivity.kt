package com.flerty.wikiflerty.reader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.content.ClipData
import android.content.ClipboardManager
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.flerty.wikiflerty.tts.ReadAloud
import com.flerty.wikiflerty.tts.ReadAloudSession
import org.readium.r2.shared.publication.Locator
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.locateProgression
import kotlin.math.roundToInt
import org.readium.r2.shared.publication.Publication
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.drawToBitmap
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.R
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.LocalFeedback
import com.flerty.wikiflerty.ui.WikiFlertyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import com.flerty.wikiflerty.ai.AiAction
import com.flerty.wikiflerty.data.ReadStatus
import com.flerty.wikiflerty.ai.AiBusyException
import com.flerty.wikiflerty.ai.AiException
import com.flerty.wikiflerty.ai.AiPrompts
import com.flerty.wikiflerty.ai.AiProvider
import com.flerty.wikiflerty.ai.ChatMessage
import com.flerty.wikiflerty.data.Note
import com.flerty.wikiflerty.data.Bookmark
import kotlinx.coroutines.CancellationException
import org.readium.r2.navigator.epub.css.RsProperties
import kotlinx.coroutines.flow.map
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.AbsoluteUrl

class ReaderActivity : AppCompatActivity(), EpubNavigatorFragment.Listener {
    private val app: App get() = application as App
    private val settings: ReaderSettings get() = app.readerSettings.settings.value
    private val ui = ReaderUiState()
    private lateinit var navigator: EpubNavigatorFragment
    private lateinit var fonts: List<FontEntry>
    private lateinit var bookArea: View
    private lateinit var snapshot: ImageView

    private var systemBars: Insets = Insets.NONE
    private var turning = false
    private var selectionJob: Job? = null
    private var actionMode: ActionMode? = null
    private var bookId: String = ""
    private var pendingLocator: org.readium.r2.shared.publication.Locator? = null
    private var saveTimer: Job? = null
    private var lastSave: Job? = null

    private var visible = false
    private var lastActivity = 0L
    private var lastPosition: Int? = null
    private var positions: List<Locator> = emptyList()
    private var pagesSinceTrack = 0
    private var askedFinished = false

    private var publication: Publication? = null
    private var lastTtsHighlight: Locator? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var pendingQuoteSave: Triple<QuoteDraft, QuoteLook, QuoteFormat>? = null
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingQuoteSave
        pendingQuoteSave = null
        if (granted && pending != null) saveQuote(pending.first, pending.second, pending.third)
        else if (!granted) Toast.makeText(this, "Нет доступа к галерее", Toast.LENGTH_SHORT).show()
    }
    private var coverPath: String? = null
    private var pageSpan = 0.02
    private var bookmarkJob: Job? = null

    private val bookmarkActions by lazy {
        BookmarkActions(
            onToggle = ::toggleBookmark,
            onOpen = { mark ->
                ui.hideAll()
                mark.locator?.let { navigator.go(it, animated = false) }
                resyncListening()
            },
            onDelete = { mark -> lifecycleScope.launch { app.bookmarks.delete(listOf(mark.id)) } },
        )
    }

    private val quoteActions by lazy {
        QuoteActions(
            onShare = { draft, look, format -> shareQuote(draft, look, format) },
            onSave = { draft, look, format -> requestSaveQuote(draft, look, format) },
        )
    }

    private val ttsActions by lazy {
        TtsActions(
            onStart = { startListening() },
            onToggle = { app.readAloud.toggle() },
            onPrevious = { app.readAloud.previous() },
            onNext = { app.readAloud.next() },
            onStop = { app.readAloud.stop() },
            onSleep = { app.readAloud.setSleep(it) },
            onOpenSettings = { ui.sheet = ReaderSheet.Listen },
        )
    }

    private fun startListening() {
        val pub = publication ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        lifecycleScope.launch {
            val start = runCatching { navigator.firstVisibleElementLocator() }.getOrNull() ?: navigator.currentLocator.value
            ui.sheet = null
            ui.panelsVisible = false
            app.readAloud.start(pub, bookId, start, settings)
        }
    }

    private val mySession: ReadAloudSession? get() = app.readAloud.session.value?.takeIf { it.bookId == bookId }

    private fun resyncListening() {
        val session = mySession ?: return
        if (!session.navigator.playback.value.playWhenReady) return
        lifecycleScope.launch {
            delay(350)
            runCatching { navigator.firstVisibleElementLocator() }.getOrNull()?.let { app.readAloud.go(it) }
        }
    }
    private var bookTitle: String = ""
    private var bookAuthor: String = ""
    private val chat = AiChatState()

    private val aiBridge by lazy {
        AiBridge(
            manager = app.ai,
            chat = chat,
            onStart = ::startAi,
            onFollowUp = ::followUpAi,
            onStop = { chat.job?.cancel() },
            onRetry = ::retryAi,
            onModel = ::switchModel,
            onSaveDictionary = ::saveToDictionary,
            onSaveNote = ::saveAnswerAsNote,
            onChapter = ::startChapterAi,
        )
    }

    private val selectionCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            actionMode = mode
            menu.clear()
            refreshSelection()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.clear()
            refreshSelection()
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            ui.selection = null
        }
    }

    private fun refreshSelection() {
        selectionJob?.cancel()
        selectionJob = lifecycleScope.launch {
            val selection = navigator.currentSelection() ?: return@launch
            val text = selection.locator.text.highlight?.trim().orEmpty()
            if (text.isEmpty()) return@launch
            val pageLocation = IntArray(2).also { navigator.publicationView.getLocationInWindow(it) }
            val rootLocation = IntArray(2).also { findViewById<View>(R.id.reader_root).getLocationInWindow(it) }
            val offsetY = (pageLocation[1] - rootLocation[1]).toFloat()
            val rect = selection.rect
            if (ui.panelsVisible || ui.sheet != null) ui.hideAll()
            ui.selection = SelectionInfo(
                locator = selection.locator,
                text = text,
                top = (rect?.top ?: 200f) + offsetY,
                bottom = (rect?.bottom ?: 240f) + offsetY,
            )
        }
    }

    private val selectionScript by lazy {
        val accent = selectionAccent()
        TextSelection.script(
            "rgb(${android.graphics.Color.red(accent)}, ${android.graphics.Color.green(accent)}, ${android.graphics.Color.blue(accent)})",
        )
    }

    private var pageOffset = floatArrayOf(0f, 0f)
    private val jsQueue = kotlinx.coroutines.channels.Channel<Pair<Boolean, String>>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    private fun currentWebView(): View? {
        var best: View? = null
        var bestArea = 0
        val rect = android.graphics.Rect()
        fun walk(v: View) {
            if (v is android.webkit.WebView && v.isShown && v.getGlobalVisibleRect(rect)) {
                val area = rect.width() * rect.height()
                if (area > bestArea) {
                    bestArea = area
                    best = v
                }
            }
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(navigator.publicationView)
        return best
    }

    private fun updatePageOffset() {
        val page = IntArray(2).also { (currentWebView() ?: navigator.publicationView).getLocationInWindow(it) }
        val root = IntArray(2).also { findViewById<View>(R.id.reader_root).getLocationInWindow(it) }
        pageOffset = floatArrayOf((page[0] - root[0]).toFloat(), (page[1] - root[1]).toFloat())
    }

    private fun onHandles(data: String) {
        if (data.isEmpty() || !::navigator.isInitialized) {
            ui.handles = null
            return
        }
        val json = runCatching { org.json.JSONObject(data) }.getOrNull() ?: return
        updatePageOffset()
        fun point(key: String) = json.optJSONArray(key)?.let {
            androidx.compose.ui.geometry.Offset(it.getDouble(0).toFloat() + pageOffset[0], it.getDouble(1).toFloat() + pageOffset[1])
        }
        ui.handles = SelectionHandles(point("s"), point("e"))
    }

    private fun onHandleDrag(phase: Int, start: Boolean, x: Float, y: Float) {
        val k = resources.displayMetrics.density
        val cx = (x - pageOffset[0]) / k
        val cy = (y - pageOffset[1]) / k
        when (phase) {
            0 -> jsQueue.trySend(false to "window.__wfSel && window.__wfSel.grab('${if (start) "start" else "end"}', $cx, $cy)")
            1 -> jsQueue.trySend(true to "window.__wfSel && window.__wfSel.drag($cx, $cy)")
            else -> jsQueue.trySend(false to "window.__wfSel && window.__wfSel.release()")
        }
    }

    private fun startJsQueue() {
        lifecycleScope.launch {
            for (first in jsQueue) {
                var item = first
                while (item.first) {
                    val next = jsQueue.tryReceive().getOrNull() ?: break
                    if (next.first) {
                        item = next
                    } else {
                        runCatching { navigator.evaluateJavascript(item.second) }
                        item = next
                    }
                }
                runCatching { navigator.evaluateJavascript(item.second) }
            }
        }
    }

    private fun onSelectionEvent(type: String) {
        when (type) {
            "start" -> {
                navigator.publicationView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                selectionJob?.cancel()
                ui.selection = null
            }
            "drag" -> {
                selectionJob?.cancel()
                ui.selection = null
            }
            "done" -> refreshSelection()
            "clear" -> {
                selectionJob?.cancel()
                ui.selection = null
                ui.handles = null
            }
            "auto-end" -> onAutoEnd()
        }
    }

    private fun seekTo(fraction: Double) {
        val f = fraction.coerceIn(0.0, 1.0)
        lifecycleScope.launch {
            val target = if (positions.isNotEmpty()) {
                positions[(f * (positions.size - 1)).roundToInt().coerceIn(0, positions.size - 1)]
            } else {
                app.readerSession?.publication?.locateProgression(f)
            } ?: return@launch
            endSelection()
            navigator.go(target, animated = false)
            resyncListening()
        }
    }

    private fun endSelection() {
        selectionJob?.cancel()
        ui.selection = null
        ui.handles = null
        actionMode?.finish()
        navigator.clearSelection()
    }

    private val noteActions by lazy {
        NoteActions(
            onSave = { draft ->
                lifecycleScope.launch {
                    val id = draft.id
                    if (id == null) {
                        val locator = draft.locator ?: return@launch
                        app.notes.create(bookId, locator, draft.text, draft.note, draft.color, ui.chapter)
                    } else {
                        app.notes.edit(id, draft.note, draft.color)
                    }
                    app.readerSettings.update { it.copy(markerColor = draft.color) }
                    ui.sheet = if (draft.fromList) ReaderSheet.Notes else null
                    ui.editor = null
                }
            },
            onDelete = { id ->
                lifecycleScope.launch {
                    app.notes.delete(id)
                    ui.sheet = if (ui.editor?.fromList == true) ReaderSheet.Notes else null
                    ui.editor = null
                    Toast.makeText(this@ReaderActivity, "Заметка удалена", Toast.LENGTH_SHORT).show()
                }
            },
            onGoTo = { note ->
                ui.hideAll()
                note.locator?.let { navigator.go(it, animated = false) }
            },
            onSelectionNote = {
                val selection = ui.selection
                if (selection != null) {
                    endSelection()
                    ui.openEditor(
                        NoteDraft(null, selection.locator, selection.text, "", settings.markerColor, fromList = false),
                    )
                }
            },
            onSelectionMarker = {
                val selection = ui.selection
                if (selection != null) {
                    endSelection()
                    lifecycleScope.launch {
                        app.notes.create(bookId, selection.locator, selection.text, "", settings.markerColor, ui.chapter)
                    }
                }
            },
            onSelectionCopy = {
                val selection = ui.selection
                if (selection != null) {
                    val clipboard = getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("WikiFlerty", selection.text))
                    endSelection()
                    Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
                }
            },
            onSelectionAi = {
                val selection = ui.selection
                if (selection != null) {
                    chat.job?.cancel()
                    chat.fragment = selection.text
                    chat.locator = selection.locator
                    chat.context = contextAround(selection.locator)
                    endSelection()
                    ui.panelsVisible = false
                    ui.sheet = ReaderSheet.AiMenu
                }
            },
            onSelectionClose = { endSelection() },
            onSelectionQuote = {
                val selection = ui.selection
                if (selection != null) {
                    endSelection()
                    ui.quote = QuoteDraft(selection.text, bookTitle, bookAuthor, coverPath, quoteTypeface())
                    ui.panelsVisible = false
                    ui.sheet = ReaderSheet.Quote
                }
            },
        )
    }

    private var lastColumns: String? = null
    private var lastColumnsScroll = false

    private var injectJob: Job? = null

    private fun injectScripts() {
        injectJob?.cancel()
        injectJob = lifecycleScope.launch {
            for (wait in longArrayOf(0L, 250L, 700L, 1500L)) {
                delay(wait)
                if (!::navigator.isInitialized) continue
                runCatching { navigator.evaluateJavascript(selectionScript) }
                applyColumns(settings)
            }
        }
    }

    private suspend fun applyColumns(s: ReaderSettings) {
        val two = s.columns == "2" && !s.scroll
        val script = if (two) {
            "(function(){var r=document.documentElement.style;if(r.getPropertyValue('--RS__colCount').trim()!=='2'){r.setProperty('--RS__colCount','2');r.setProperty('--RS__colWidth','auto');window.dispatchEvent(new Event('resize'));}})()"
        } else {
            "(function(){var r=document.documentElement.style;if(r.getPropertyValue('--RS__colCount')){r.removeProperty('--RS__colCount');r.removeProperty('--RS__colWidth');window.dispatchEvent(new Event('resize'));}})()"
        }
        runCatching { navigator.evaluateJavascript(script) }
    }

    private var autoJob: Job? = null
    private var autoHref: String? = null
    private var lastAutoSpeed = -1
    private var lastAutoScrollMode = false

    private val autoActions by lazy {
        AutoActions(
            onStart = ::startAuto,
            onToggle = ::toggleAuto,
            onStop = ::stopAuto,
        )
    }

    private fun startAuto() {
        endSelection()
        ui.hideAll()
        ui.footnote = null
        ui.autoActive = true
        ui.autoPaused = false
        ui.autoProgress = 0f
        runAuto()
    }

    private fun toggleAuto() {
        if (!ui.autoActive) return
        ui.autoPaused = !ui.autoPaused
        runAuto()
    }

    private fun stopAuto() {
        ui.autoActive = false
        ui.autoPaused = false
        autoJob?.cancel()
        stopAutoScrollJs()
    }

    private fun runAuto() {
        autoJob?.cancel()
        if (!ui.autoActive || ui.autoPaused) {
            stopAutoScrollJs()
            return
        }
        if (settings.scroll) {
            startAutoScrollJs()
            return
        }
        stopAutoScrollJs()
        autoJob = lifecycleScope.launch {
            var elapsed = (ui.autoProgress * AutoScroll.pageDelayMs(settings.autoSpeed)).toLong()
            while (isActive && ui.autoActive && !ui.autoPaused) {
                delay(AUTO_TICK)
                if (ui.sheet != null || ui.selection != null || ui.footnote != null) continue
                elapsed += AUTO_TICK
                val total = AutoScroll.pageDelayMs(settings.autoSpeed)
                ui.autoProgress = (elapsed.toFloat() / total).coerceIn(0f, 1f)
                if (elapsed < total) continue
                elapsed = 0
                ui.autoProgress = 0f
                if (ui.progression >= 0.9995) {
                    stopAuto()
                    break
                }
                turn(forward = true)
            }
        }
    }

    private fun startAutoScrollJs() {
        val px = AutoScroll.pixelsPerSecond(settings.autoSpeed)
        lifecycleScope.launch { runCatching { navigator.evaluateJavascript("window.__wfAuto && window.__wfAuto.start($px)") } }
    }

    private fun stopAutoScrollJs() {
        lifecycleScope.launch { runCatching { navigator.evaluateJavascript("window.__wfAuto && window.__wfAuto.stop()") } }
    }

    private fun onAutoEnd() {
        if (!ui.autoActive || ui.autoPaused || !settings.scroll) return
        if (!navigator.goForward(animated = false)) stopAuto()
    }

    private val search = SearchState()
    private var searchJob: Job? = null

    private val searchActions by lazy {
        SearchActions(
            state = search,
            onSearch = ::runSearch,
            onOpen = ::openSearchHit,
            onClear = ::clearSearch,
        )
    }

    private fun runSearch(query: String) {
        val publication = app.readerSession?.publication ?: return
        searchJob?.cancel()
        lifecycleScope.launch { navigator.applyDecorations(emptyList(), SEARCH_GROUP) }
        search.results.clear()
        search.current = -1
        search.error = null
        search.limited = false
        search.searched = false
        search.running = true
        searchJob = lifecycleScope.launch {
            val iterator = runCatching {
                publication.search(query, SearchService.Options(caseSensitive = false, diacriticSensitive = false))
            }.getOrNull()
            if (iterator == null) {
                search.error = "В этой книге поиск недоступен"
                search.running = false
                return@launch
            }
            try {
                while (isActive) {
                    val page = iterator.next().getOrNull() ?: break
                    page.locators.forEach { locator ->
                        if (search.results.size < SEARCH_LIMIT) {
                            search.results.add(
                                SearchHit(
                                    locator = locator,
                                    chapter = locator.title,
                                    before = locator.text.before.orEmpty(),
                                    match = locator.text.highlight.orEmpty(),
                                    after = locator.text.after.orEmpty(),
                                ),
                            )
                        }
                    }
                    if (search.results.size >= SEARCH_LIMIT) {
                        search.limited = true
                        break
                    }
                }
            } finally {
                withContext(NonCancellable) { runCatching { iterator.close() } }
                search.running = false
                search.searched = true
            }
        }
    }

    private fun openSearchHit(index: Int) {
        val hit = search.results.getOrNull(index) ?: return
        search.current = index
        ui.sheet = null
        ui.panelsVisible = false
        endSelection()
        navigator.go(hit.locator, animated = false)
        lifecycleScope.launch {
            navigator.applyDecorations(
                listOf(Decoration("search-$index", hit.locator, Decoration.Style.Highlight(tint = selectionAccent(), isActive = true))),
                SEARCH_GROUP,
            )
        }
        resyncListening()
    }

    private fun clearSearch() {
        searchJob?.cancel()
        search.current = -1
        search.results.clear()
        search.searched = false
        search.limited = false
        search.error = null
        lifecycleScope.launch { navigator.applyDecorations(emptyList(), SEARCH_GROUP) }
    }

    private fun showFootnote(raw: String, url: String) {
        endSelection()
        val cleaned = raw.trim()
            .replace(Regex("^[\\[(]?\\d{1,4}[\\])]?[.)]?\\s+"), "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .ifBlank { raw.trim() }
        if (cleaned.isBlank()) return
        ui.panelsVisible = false
        ui.footnote = FootnoteInfo(cleaned, url)
    }

    private val tapListener = object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
            if (ui.autoActive && ui.sheet == null && !ui.panelsVisible) {
                toggleAuto()
                return true
            }
            if (ui.footnote != null) {
                ui.footnote = null
                return true
            }
            if (ui.selection != null) {
                endSelection()
                return true
            }
            if (ui.panelsVisible || ui.sheet != null) {
                ui.hideAll()
                return true
            }
            val s = settings
            if (s.edgeTaps) {
                val width = navigator.publicationView.width.toFloat()
                if (width > 0f) {
                    val x = event.point.x
                    if (x < width * s.edgeZone) {
                        turn(forward = s.leftTapForward)
                        return true
                    }
                    if (x > width * (1 - s.edgeZone)) {
                        turn(forward = true)
                        return true
                    }
                }
            }
            ui.panelsVisible = true
            app.feedback.play(FeedbackSound.Open)
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val session = app.readerSession

        if (session == null) {
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            finish()
            return
        }

        fonts = FontCatalog.available(this)
        publication = session.publication
        bookId = session.bookId
        bookAuthor = session.publication.metadata.authors.joinToString(", ") { it.name }
        val initial = settings

        supportFragmentManager.fragmentFactory = EpubNavigatorFactory(session.publication)
            .createFragmentFactory(
                initialLocator = session.initialLocator,
                initialPreferences = initial.toEpubPreferences(fonts),
                listener = this,
                paginationListener = object : EpubNavigatorFragment.PaginationListener {
                    override fun onPageLoaded() {
                        injectScripts()
                    }
                },
                configuration = navigatorConfiguration(),
            )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)
        ui.handleColor = selectionAccent()
        ui.onHandleDrag = { phase, start, x, y -> onHandleDrag(phase, start, x, y) }
        startJsQueue()
        bookArea = findViewById(R.id.book_area)
        snapshot = findViewById(R.id.page_snapshot)
        snapshot.outlineProvider = ViewOutlineProvider.BOUNDS
        snapshot.elevation = 16 * resources.displayMetrics.density

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<View>(R.id.reader_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            applyBookMargins(settings)
            insets
        }
        applyWindowSettings(initial)

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.navigator_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }
        }
        navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment
        navigator.addInputListener(tapListener)

        navigator.addDecorationListener(
            NOTES_GROUP,
            object : DecorableNavigator.Listener {
                override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                    val note = app.notes.notes.value.firstOrNull { it.id == event.decoration.id } ?: return false
                    ui.openEditor(NoteDraft(note.id, note.locator, note.text, note.note, note.color, fromList = false))
                    app.feedback.play(FeedbackSound.Open)
                    return true
                }
            },
        )
        val bookNotes = app.notes.notes.map { list ->
            list.filter { it.bookId == session.bookId }.sortedBy { it.progression }
        }
        val bookMarks = app.bookmarks.bookmarks.map { list ->
            list.filter { it.bookId == session.bookId }.sortedBy { it.progression }
        }

        val book = app.library.books.value.firstOrNull { it.id == session.bookId }
        val title = book?.title ?: session.publication.metadata.title.orEmpty()
        bookTitle = title
        val toc = flattenToc(session.publication.tableOfContents)
        var cover by mutableStateOf<ImageBitmap?>(null)

        findViewById<ComposeView>(R.id.reader_overlay).setContent {
            CompositionLocalProvider(LocalFeedback provides app.feedback) {
                WikiFlertyTheme {
                    val current by app.readerSettings.settings.collectAsState()
                    val notes by bookNotes.collectAsState(initial = emptyList())
                    val marks by bookMarks.collectAsState(initial = emptyList())
                    ReaderOverlay(
                        state = ui,
                        settings = current,
                        fonts = fonts,
                        title = title,
                        cover = cover,
                        toc = toc,
                        onSettingsChange = { transform -> app.readerSettings.update(transform) },
                        onTocSelect = { item ->
                            navigator.go(item.link, animated = false)
                            resyncListening()
                        },
                        onBack = { finish() },
                        notes = notes,
                        noteActions = noteActions,
                        ai = aiBridge,
                        onEdgeTap = { left -> onStripTap(left) },
                        onBrightnessPreview = { value -> setWindowBrightness(value) },
                        onMarkFinished = {
                            ui.askFinished = false
                            app.appScope.launch { app.library.setStatus(bookId, ReadStatus.Finished) }
                        },
                        tts = ttsActions,
                        bookLanguage = session.publication.metadata.language?.code,
                        onInstallVoices = { ReadAloud.installVoices(this@ReaderActivity) },
                        onSeek = ::seekTo,
                        search = searchActions,
                        auto = autoActions,
                        bookmarks = marks,
                        bookmarkActions = bookmarkActions,
                        quoteActions = quoteActions,
                        onFootnoteGo = { url ->
                            lifecycleScope.launch {
                                runCatching { navigator.evaluateJavascript("window.__wfGo && window.__wfGo(${org.json.JSONObject.quote(url)})") }
                            }
                        },
                    )
                    com.flerty.wikiflerty.ui.AchievementToast()
                }
            }
        }

        lifecycleScope.launch {
            book?.let { app.library.coverFile(it) }?.takeIf { it.exists() }?.let { file ->
                coverPath = file.path
                cover = withContext(Dispatchers.IO) {
                    com.flerty.wikiflerty.ui.CoverCache.load(file, maxWidth = 160)
                }
            }
        }

        lifecycleScope.launch {
            positions = withContext(Dispatchers.Default) {
                runCatching { session.publication.positions() }.getOrDefault(emptyList())
            }
            ui.totalPositions = positions.size
            val total = ui.totalPositions
            app.appScope.launch { app.library.setPositions(session.bookId, total) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    navigator.currentLocator.collect { locator ->
                        injectScripts()
                        val href = locator.href.toString()
                        if (href != autoHref) {
                            autoHref = href
                            if (ui.autoActive && !ui.autoPaused && settings.scroll) startAutoScrollJs()
                        }
                        ui.progression = locator.locations.totalProgression ?: ui.progression
                        ui.position = locator.locations.position
                        ui.chapter = locator.title
                        val position = locator.locations.position
                        val previous = lastPosition
                        if (position != null && previous != null && position > previous && position - previous <= 3) {
                            pagesSinceTrack += position - previous
                        }
                        if (position != null) lastPosition = position
                        trackReading()
                        refreshBookmarkState()
                        maybeAskFinished(locator.locations.totalProgression ?: 0.0)
                        pendingLocator = locator
                        saveTimer?.cancel()
                        saveTimer = launch {
                            delay(1200)
                            flushProgress()
                        }
                    }
                }
                launch {
                    bookNotes.collect { notes -> showHighlights(notes) }
                }
                launch {
                    bookMarks.collect { refreshBookmarkState() }
                }
                launch { followListening() }
                launch {
                    app.readAloud.problems.collect { problem -> ui.ttsProblem = problem }
                }
                launch {
                    app.readAloud.sleep.collect { ui.ttsSleep = it }
                }
                launch {
                    app.readerSettings.settings.collect { s ->
                        navigator.submitPreferences(s.toEpubPreferences(fonts))
                        applyWindowSettings(s)
                        applyListeningSettings(s)
                        if (s.columns != lastColumns || s.scroll != lastColumnsScroll) {
                            lastColumns = s.columns
                            lastColumnsScroll = s.scroll
                            launch { applyColumns(s) }
                        }
                        val speedChanged = s.autoSpeed != lastAutoSpeed || s.scroll != lastAutoScrollMode
                        lastAutoSpeed = s.autoSpeed
                        lastAutoScrollMode = s.scroll
                        if (speedChanged && ui.autoActive && !ui.autoPaused && (s.scroll || autoJob?.isActive != true)) runAuto()
                    }
                }
                launch {
                    var night = settings.isNightNow
                    while (isActive) {
                        delay(30_000)
                        val now = settings.isNightNow
                        if (now != night) {
                            night = now
                            navigator.submitPreferences(settings.toEpubPreferences(fonts))
                            applyWindowSettings(settings)
                        }
                    }
                }
            }
        }
    }

    private suspend fun followListening() {
        app.readAloud.session.collectLatest { session ->
            if (session == null || session.bookId != bookId) {
                ui.ttsActive = false
                ui.ttsPlaying = false
                lastTtsHighlight = null
                navigator.applyDecorations(emptyList(), TTS_GROUP)
                return@collectLatest
            }
            ui.ttsActive = true
            coroutineScope {
                launch {
                    session.navigator.playback.collect { ui.ttsPlaying = it.playWhenReady }
                }
                launch {
                    session.navigator.location.collect { location ->
                        val locator = location.utteranceLocator
                        if (locator == lastTtsHighlight) return@collect
                        lastTtsHighlight = locator
                        navigator.applyDecorations(
                            listOf(Decoration("tts", locator, Decoration.Style.Highlight(tint = settings.ttsColor.toInt()))),
                            TTS_GROUP,
                        )
                        if (settings.ttsFollow) navigator.go(locator, animated = false)
                    }
                }
            }
        }
    }

    private var ttsSettingsSeen: ReaderSettings? = null

    private fun applyListeningSettings(s: ReaderSettings) {
        val before = ttsSettingsSeen
        ttsSettingsSeen = s
        val session = mySession ?: return
        if (before == null) return
        if (before.ttsEngine != s.ttsEngine) {
            val pub = publication ?: return
            val from = session.navigator.location.value.utteranceLocator
            app.readAloud.start(pub, bookId, from, s)
            return
        }
        if (before.ttsVoice != s.ttsVoice || before.ttsSpeed != s.ttsSpeed || before.ttsPitch != s.ttsPitch) {
            app.readAloud.apply(s)
        }
        if (before.ttsColor != s.ttsColor) lastTtsHighlight = null
    }

    private suspend fun showHighlights(notes: List<Note>) {
        val decorations = notes.mapNotNull { note ->
            val locator = note.locator ?: return@mapNotNull null
            Decoration(
                id = note.id,
                locator = locator,
                style = Decoration.Style.Highlight(tint = note.color.toInt()),
            )
        }
        navigator.applyDecorations(decorations, NOTES_GROUP)
    }

    private fun navigatorConfiguration() = EpubNavigatorFragment.Configuration {
        servedAssets += "fonts/.*"
        selectionActionModeCallback = selectionCallback
        registerJavascriptInterface(TextSelection.BRIDGE) {
            TextSelectionBridge(
                onEvent = { type -> runOnUiThread { onSelectionEvent(type) } },
                onFootnote = { text, url -> runOnUiThread { showFootnote(text, url) } },
                onHandles = { data -> runOnUiThread { onHandles(data) } },
            )
        }
        val accent = selectionAccent()
        readiumCssRsProperties = RsProperties(
            selectionBackgroundColor = CssColor(
                "rgba(${android.graphics.Color.red(accent)}, ${android.graphics.Color.green(accent)}, " +
                    "${android.graphics.Color.blue(accent)}, 0.38)",
            ),
        )
        fonts.filter { it.bundled && it.css != null }.forEach { entry ->
            val regular = "fonts/${entry.regular}"
            val italic = entry.italic?.let { "fonts/$it" }
            val bold = entry.bold?.let { "fonts/$it" }

            addFontFamilyDeclaration(org.readium.r2.navigator.preferences.FontFamily(entry.css!!)) {
                addFontFace {
                    addSource(regular)
                    setFontStyle(FontStyle.NORMAL)
                    if (entry.variable) setFontWeight(100..900) else setFontWeight(FontWeight.NORMAL)
                }
                if (bold != null) {
                    addFontFace {
                        addSource(bold)
                        setFontStyle(FontStyle.NORMAL)
                        setFontWeight(FontWeight.BOLD)
                    }
                }
                if (italic != null) {
                    addFontFace {
                        addSource(italic)
                        setFontStyle(FontStyle.ITALIC)
                        if (entry.variable) setFontWeight(100..900) else setFontWeight(FontWeight.NORMAL)
                    }
                }
            }

            if (italic != null) {
                addFontFamilyDeclaration(org.readium.r2.navigator.preferences.FontFamily(entry.italicFamilyName)) {
                    addFontFace {
                        addSource(italic)
                        setFontStyle(FontStyle.NORMAL)
                        if (entry.variable) setFontWeight(100..900) else setFontWeight(FontWeight.NORMAL)
                    }
                    addFontFace {
                        addSource(italic)
                        setFontStyle(FontStyle.ITALIC)
                        if (entry.variable) setFontWeight(100..900) else setFontWeight(FontWeight.NORMAL)
                    }
                }
            }
        }
    }

    private fun selectionAccent(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getColor(android.R.color.system_accent1_300)
        } else {
            0xFF8FB3FF.toInt()
        }

    private class CssColor(private val css: String) : org.readium.r2.navigator.epub.css.Color {
        override fun toCss(): String = css
    }

    private fun contextAround(locator: org.readium.r2.shared.publication.Locator): String {
        val before = locator.text.before.orEmpty().takeLast(280)
        val after = locator.text.after.orEmpty().take(280)
        val highlight = locator.text.highlight.orEmpty()
        return (before + highlight + after).replace(Regex("\\s+"), " ").trim()
    }

    private fun startAi(action: AiAction, question: String?) {
        chat.job?.cancel()
        chat.bubbles.clear()
        chat.error = null
        chat.savedToDictionary = false
        chat.savedToNotes = false
        chat.action = action
        val provider = app.ai.current() ?: return
        val settings = app.ai.settings.value
        chat.provider = provider
        chat.model = settings.modelFor(provider)
        val prompt = AiPrompts.first(action, chat.fragment, chat.context, settings, question)
        val shown = if (action == AiAction.Ask) question.orEmpty().trim() else action.title
        chat.bubbles.add(AiBubble(fromUser = true, shown = shown, sent = prompt))
        sendAi()
    }

    private fun startChapterAi(action: AiAction) {
        val locator = navigator.currentLocator.value
        val progression = locator.locations.progression ?: 0.0
        chat.job?.cancel()
        chat.bubbles.clear()
        chat.error = null
        chat.action = action
        chat.fragment = locator.title?.takeIf { it.isNotBlank() } ?: bookTitle
        chat.context = ""
        chat.streaming = true
        lifecycleScope.launch {
            val end = (progression + pageSpan).coerceIn(0.05, 1.0)
            val raw = runCatching { navigator.evaluateJavascript(CHAPTER_TEXT_JS.replace("__END__", end.toString())) }.getOrNull()
            val text = raw?.let { runCatching { org.json.JSONTokener(it).nextValue() as? String }.getOrNull() }.orEmpty()
            chat.streaming = false
            if (text.isBlank()) {
                chat.error = "Не удалось прочитать текст главы"
                return@launch
            }
            chat.context = text
            startAi(action, null)
        }
    }

    private fun followUpAi(text: String) {
        if (chat.streaming || chat.provider == null) return
        chat.bubbles.add(AiBubble(fromUser = true, shown = text, sent = text))
        chat.savedToDictionary = false
        chat.savedToNotes = false
        sendAi()
    }

    private fun retryAi() {
        if (chat.streaming) return
        if (chat.bubbles.lastOrNull()?.fromUser == false) chat.bubbles.removeAt(chat.bubbles.lastIndex)
        if (chat.bubbles.isNotEmpty()) sendAi()
    }

    private fun switchModel(provider: AiProvider, model: String) {
        chat.job?.cancel()
        chat.provider = provider
        chat.model = model
        app.ai.store.update { it.copy(models = it.models + (provider.id to model), provider = provider.id) }
        lifecycleScope.launch {
            chat.job?.join()
            if (chat.bubbles.lastOrNull()?.fromUser == false) chat.bubbles.removeAt(chat.bubbles.lastIndex)
            if (chat.bubbles.isNotEmpty()) sendAi()
        }
    }

    private fun sendAi() {
        val provider = chat.provider ?: return
        chat.error = null
        val history = chat.bubbles.map { ChatMessage(it.fromUser, it.sent) }
        val system = AiPrompts.system(bookTitle, bookAuthor, app.ai.settings.value)
        chat.bubbles.add(AiBubble(fromUser = false, shown = "", sent = ""))
        chat.streaming = true
        chat.job = lifecycleScope.launch {
            val answer = StringBuilder()
            try {
                var attempt = 0
                while (true) {
                    try {
                        app.ai.chat(provider, chat.model, system, history).collect { piece ->
                            chat.retryNote = null
                            answer.append(piece)
                            val text = answer.toString()
                            if (chat.bubbles.isNotEmpty()) chat.bubbles[chat.bubbles.lastIndex] = AiBubble(false, text, text)
                        }
                        break
                    } catch (e: AiBusyException) {
                        if (answer.isNotEmpty() || attempt >= 2) throw e
                        attempt++
                        chat.retryNote = "Модель перегружена, пробую ещё раз ($attempt из 2)…"
                        delay(if (attempt == 1) 2_000L else 5_000L)
                    }
                }
                if (answer.isEmpty()) throw AiException("Модель вернула пустой ответ. Попробуй другую модель.")
            } catch (e: CancellationException) {
                if (answer.isEmpty()) dropEmptyAnswer()
                throw e
            } catch (e: Exception) {
                if (answer.isEmpty()) dropEmptyAnswer()
                chat.error = e.message ?: "Не удалось получить ответ"
            } finally {
                chat.retryNote = null
                chat.streaming = false
            }
        }
    }

    private fun dropEmptyAnswer() {
        val last = chat.bubbles.lastOrNull()
        if (last != null && !last.fromUser && last.shown.isEmpty()) chat.bubbles.removeAt(chat.bubbles.lastIndex)
    }

    private fun saveToDictionary() {
        val answer = chat.lastAnswer ?: return
        val word = chat.fragment.trim().let { if (it.length > 80) it.take(80) + "…" else it }
        lifecycleScope.launch {
            app.dictionary.add(word, answer, chat.context.take(400), bookId, bookTitle, chat.locator)
            chat.savedToDictionary = true
            Toast.makeText(this@ReaderActivity, "Добавлено в словарь", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAnswerAsNote() {
        val answer = chat.lastAnswer ?: return
        val locator = chat.locator ?: return
        lifecycleScope.launch {
            app.notes.create(bookId, locator, chat.fragment, answer, settings.markerColor, ui.chapter)
            chat.savedToNotes = true
            Toast.makeText(this@ReaderActivity, "Сохранено в заметки", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshBookmarkState() {
        bookmarkJob?.cancel()
        bookmarkJob = lifecycleScope.launch {
            delay(60)
            measurePageSpan()?.let { pageSpan = it }
            ui.bookmarked = bookmarksOnPage(navigator.currentLocator.value).isNotEmpty()
        }
    }

    private suspend fun measurePageSpan(): Double? {
        val raw = runCatching { navigator.evaluateJavascript(PAGE_SPAN_JS) }.getOrNull() ?: return null
        return raw.trim('"', ' ').toDoubleOrNull()?.takeIf { it > 0.0 && it <= 1.0 }
    }

    private fun bookmarksOnPage(locator: Locator): List<Bookmark> {
        val href = locator.href.toString()
        val start = locator.locations.progression ?: return emptyList()
        val end = start + pageSpan
        return app.bookmarks.bookmarks.value.filter { mark ->
            if (mark.bookId != bookId) return@filter false
            val l = mark.locator ?: return@filter false
            val p = l.locations.progression ?: return@filter false
            l.href.toString() == href && p >= start - PAGE_EPSILON && p < end - PAGE_EPSILON
        }
    }

    private fun toggleBookmark() {
        bookmarkJob?.cancel()
        lifecycleScope.launch {
            val locator = navigator.currentLocator.value
            measurePageSpan()?.let { pageSpan = it }
            val existing = bookmarksOnPage(locator)
            if (existing.isNotEmpty()) {
                ui.bookmarked = false
                app.bookmarks.delete(existing.map { it.id })
            } else {
                ui.bookmarked = true
                val first = runCatching { navigator.firstVisibleElementLocator() }.getOrNull()
                val snippet = first?.text?.let { it.highlight ?: it.after }.orEmpty()
                app.bookmarks.create(bookId, locator, ui.chapter ?: locator.title, snippet)
            }
        }
    }

    private fun quoteTypeface(): android.graphics.Typeface? {
        val entry = FontCatalog.find(fonts, settings.font)
        val file = entry.regular ?: return if (entry.id == "sans") android.graphics.Typeface.SANS_SERIF else android.graphics.Typeface.SERIF
        return runCatching { android.graphics.Typeface.createFromAsset(assets, "fonts/$file") }.getOrNull()
    }

    private fun quoteTheme() = QuoteTheme(com.flerty.wikiflerty.ui.AppStyle.base, com.flerty.wikiflerty.ui.AppStyle.current.accent.toInt())

    private suspend fun renderQuote(draft: QuoteDraft, look: QuoteLook, format: QuoteFormat): Bitmap {
        val theme = quoteTheme()
        return withContext(Dispatchers.Default) {
            QuoteCard.render(draft, look, format, theme, QuoteCard.loadCover(draft.coverPath, maxWidth = 480))
        }
    }

    private fun shareQuote(draft: QuoteDraft, look: QuoteLook, format: QuoteFormat) {
        if (quoteActions.busy) return
        quoteActions.busy = true
        lifecycleScope.launch {
            try {
                val bitmap = renderQuote(draft, look, format)
                val uri = withContext(Dispatchers.IO) {
                    val dir = java.io.File(cacheDir, "share").apply { mkdirs() }
                    val file = java.io.File(dir, "quote.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    androidx.core.content.FileProvider.getUriForFile(this@ReaderActivity, "$packageName.files", file)
                }
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.clipData = ClipData.newRawUri("quote", uri)
                startActivity(Intent.createChooser(intent, "Поделиться цитатой"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@ReaderActivity, "Не удалось поделиться", Toast.LENGTH_SHORT).show()
            } finally {
                quoteActions.busy = false
            }
        }
    }

    private fun requestSaveQuote(draft: QuoteDraft, look: QuoteLook, format: QuoteFormat) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingQuoteSave = Triple(draft, look, format)
            storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        saveQuote(draft, look, format)
    }

    private fun saveQuote(draft: QuoteDraft, look: QuoteLook, format: QuoteFormat) {
        if (quoteActions.busy) return
        quoteActions.busy = true
        lifecycleScope.launch {
            val saved = try {
                val bitmap = renderQuote(draft, look, format)
                withContext(Dispatchers.IO) { writeToGallery(bitmap) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            } finally {
                quoteActions.busy = false
            }
            Toast.makeText(this@ReaderActivity, if (saved) "Сохранено в галерею" else "Не удалось сохранить", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeToGallery(bitmap: Bitmap): Boolean {
        val name = "WikiFlerty_${System.currentTimeMillis()}.png"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/WikiFlerty")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            val written = runCatching {
                resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
            }.getOrDefault(false)
            if (!written) {
                resolver.delete(uri, null, null)
                return false
            }
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "WikiFlerty")
            dir.mkdirs()
            val file = java.io.File(dir, name)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            android.media.MediaScannerConnection.scanFile(this, arrayOf(file.path), arrayOf("image/png"), null)
            true
        }
    }

    private fun scrollPage(forward: Boolean) {
        lifecycleScope.launch {
            val moved = runCatching {
                navigator.evaluateJavascript("window.__wfPage ? window.__wfPage(${if (forward) 1 else -1}) : false")
            }.getOrNull()
            if (moved != "true") go(forward, animated = false)
        }
    }

    private fun onStripTap(left: Boolean) {
        if (ui.autoActive) {
            toggleAuto()
            return
        }
        if (ui.selection != null) {
            endSelection()
            return
        }
        val s = settings
        if (s.edgeTaps) {
            turn(forward = !left || s.leftTapForward)
        } else {
            ui.panelsVisible = true
        }
    }

    private fun go(forward: Boolean, animated: Boolean): Boolean =
        if (forward) navigator.goForward(animated = animated) else navigator.goBackward(animated = animated)

    private fun turn(forward: Boolean) {
        if (settings.scroll) {
            scrollPage(forward)
            return
        }
        when (settings.animation) {
            PageAnimation.Slide -> go(forward, animated = true)
            PageAnimation.None -> go(forward, animated = false)
            PageAnimation.Fade, PageAnimation.Cover -> animatedTurn(forward, settings.animation)
        }
        resyncListening()
    }

    private fun animatedTurn(forward: Boolean, animation: PageAnimation) {
        if (turning) {
            go(forward, animated = false)
            return
        }
        turning = true
        capturePage { bitmap ->
            if (bitmap == null) {
                turning = false
                go(forward, animated = false)
                return@capturePage
            }
            snapshot.animate().cancel()
            snapshot.setImageBitmap(bitmap)
            snapshot.alpha = 1f
            snapshot.translationX = 0f
            snapshot.visibility = View.VISIBLE
            if (!go(forward, animated = false)) {
                hideSnapshot()
                return@capturePage
            }
            val width = bookArea.width.toFloat()
            val animator = snapshot.animate()
                .setStartDelay(60)
                .setInterpolator(DecelerateInterpolator(1.4f))
                .withEndAction { hideSnapshot() }
            when (animation) {
                PageAnimation.Fade -> animator.alpha(0f).setDuration(280)
                else -> animator.translationX(if (forward) -width else width).setDuration(320)
            }
            animator.start()
        }
    }

    private fun hideSnapshot() {
        snapshot.visibility = View.GONE
        snapshot.setImageDrawable(null)
        snapshot.translationX = 0f
        snapshot.alpha = 1f
        turning = false
    }

    private fun capturePage(onReady: (Bitmap?) -> Unit) {
        val view = bookArea
        if (view.width == 0 || view.height == 0) {
            onReady(null)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val rect = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
            try {
                PixelCopy.request(
                    window,
                    rect,
                    bitmap,
                    { result -> onReady(if (result == PixelCopy.SUCCESS) bitmap else null) },
                    Handler(Looper.getMainLooper()),
                )
            } catch (e: IllegalArgumentException) {
                onReady(null)
            }
        } else {
            onReady(runCatching { view.drawToBitmap() }.getOrNull())
        }
    }

    private fun applyWindowSettings(s: ReaderSettings) {
        val background = s.pageTheme.background.toInt()
        findViewById<View>(R.id.reader_root)?.setBackgroundColor(background)
        window.decorView.setBackgroundColor(background)

        setWindowBrightness(s.brightness)

        if (s.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val orientation = when (ScreenOrientation.of(s.orientation)) {
            ScreenOrientation.Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ScreenOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ScreenOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (requestedOrientation != orientation) requestedOrientation = orientation

        applySystemBars(s)
        applyBookMargins(s)
    }

    private fun setWindowBrightness(value: Double) {
        val attributes = window.attributes
        attributes.screenBrightness = if (value < 0) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            value.toFloat().coerceIn(0.01f, 1f)
        }
        window.attributes = attributes
    }

    private fun applySystemBars(s: ReaderSettings) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = s.pageTheme.isLight
            isAppearanceLightNavigationBars = s.pageTheme.isLight
            if (s.fullscreen) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun applyBookMargins(s: ReaderSettings) {
        if (!::bookArea.isInitialized) return
        val density = resources.displayMetrics.density
        val params = bookArea.layoutParams as ViewGroup.MarginLayoutParams
        val top = systemBars.top + (20 * density).toInt()
        val bottom = systemBars.bottom + ((if (s.showFlag) 46 else 18) * density).toInt()
        if (params.topMargin != top || params.bottomMargin != bottom) {
            params.topMargin = top
            params.bottomMargin = bottom
            bookArea.layoutParams = params
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && app.readerSession != null) applySystemBars(settings)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val s = settings
        if (s.volumeKeys && ::navigator.isInitialized &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                turn(forward = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun flattenToc(links: List<Link>, depth: Int = 0): List<TocItem> =
        links.flatMap { link ->
            val title = link.title?.trim().orEmpty()
            val self = if (title.isNotEmpty()) listOf(TocItem(title, depth, link)) else emptyList()
            self + flattenToc(link.children, depth + 1)
        }

    private fun flushProgress() {
        saveTimer?.cancel()
        saveTimer = null
        val locator = pendingLocator ?: return
        pendingLocator = null
        val id = bookId
        val previous = lastSave
        lastSave = app.appScope.launch {
            previous?.join()
            app.library.saveProgress(id, locator)
        }
    }

    private fun trackReading() {
        val now = SystemClock.elapsedRealtime()
        if (visible && lastActivity > 0) {
            val gap = now - lastActivity
            val counted = if (gap <= 5 * 60_000L) gap else 60_000L
            val key = app.library.books.value.firstOrNull { it.id == bookId }?.statsKey ?: bookId
            app.stats.add(key, counted, pagesSinceTrack)
            pagesSinceTrack = 0
        }
        lastActivity = now
    }

    private fun maybeAskFinished(progression: Double) {
        if (askedFinished || progression < 0.985) return
        val book = app.library.books.value.firstOrNull { it.id == bookId } ?: return
        askedFinished = true
        if (book.status != ReadStatus.Finished) ui.askFinished = true
    }

    override fun onResume() {
        super.onResume()
        visible = true
        lastActivity = SystemClock.elapsedRealtime()
    }

    override fun onPause() {
        if (ui.autoActive && !ui.autoPaused) {
            ui.autoPaused = true
            runAuto()
        }
        trackReading()
        visible = false
        app.appScope.launch(Dispatchers.IO) {
            app.stats.save()
            if (isFinishing) app.autoBackup.runIfDue()
        }
        com.flerty.wikiflerty.widget.ReadingWidget.refreshSoon(app)
        super.onPause()
    }

    override fun onStop() {
        flushProgress()
        super.onStop()
    }

    override fun onDestroy() {
        flushProgress()
        super.onDestroy()
        if (isFinishing) {
            if (mySession != null) app.readAloud.stop()
            app.closeReader()
        }
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
        } catch (_: ActivityNotFoundException) {
        }
    }

    private companion object {
        const val NAVIGATOR_TAG = "navigator"
        const val NOTES_GROUP = "notes"
        const val TTS_GROUP = "tts"
        const val SEARCH_GROUP = "search"
        const val AUTO_TICK = 100L
        const val SEARCH_LIMIT = 500
        const val PAGE_EPSILON = 0.0005
        const val CHAPTER_TEXT_JS =
            "(function(){var t=(document.body.innerText||'').replace(/\\n{3,}/g,'\\n\\n');" +
                "var n=Math.min(t.length,Math.ceil(t.length*__END__)+600);" +
                "return t.substring(Math.max(0,n-14000),n);})()"
        const val PAGE_SPAN_JS =
            "(function(){var e=document.scrollingElement||document.documentElement;" +
                "var w=e.scrollWidth,h=e.scrollHeight,iw=window.innerWidth,ih=window.innerHeight;" +
                "if(w>iw+4)return iw/w;if(h>ih+4)return ih/h;return 1;})()"
    }
}
