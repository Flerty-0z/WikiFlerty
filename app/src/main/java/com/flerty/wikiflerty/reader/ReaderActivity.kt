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
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.withContext
import com.flerty.wikiflerty.ai.AiAction
import com.flerty.wikiflerty.ai.AiException
import com.flerty.wikiflerty.ai.AiPrompts
import com.flerty.wikiflerty.ai.AiProvider
import com.flerty.wikiflerty.ai.ChatMessage
import com.flerty.wikiflerty.data.Note
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

/**
 * The reading screen: the book (Readium) with our panels drawn on top.
 * Tap the middle of the page to show or hide the panels, the edges to turn pages.
 */
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
    private var actionMode: ActionMode? = null
    private var bookId: String = ""
    // Progress is written to disk a moment after the page settles, not on every page turn.
    private var pendingLocator: org.readium.r2.shared.publication.Locator? = null
    private var saveTimer: Job? = null
    private var lastSave: Job? = null
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
        )
    }

    /**
     * Replaces Android's own text selection toolbar with our menu:
     * the system menu stays empty, and our panel appears next to the selected text.
     */
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
        lifecycleScope.launch {
            val selection = navigator.currentSelection() ?: return@launch
            val text = selection.locator.text.highlight?.trim().orEmpty()
            if (text.isEmpty()) return@launch
            // Selection rect is in the page view's coordinates; convert to the whole screen.
            val pageLocation = IntArray(2).also { navigator.publicationView.getLocationInWindow(it) }
            val rootLocation = IntArray(2).also { findViewById<View>(R.id.reader_root).getLocationInWindow(it) }
            val offsetY = (pageLocation[1] - rootLocation[1]).toFloat()
            val rect = selection.rect
            ui.hideAll()
            ui.selection = SelectionInfo(
                locator = selection.locator,
                text = text,
                top = (rect?.top ?: 200f) + offsetY,
                bottom = (rect?.bottom ?: 240f) + offsetY,
            )
        }
    }

    private fun endSelection() {
        ui.selection = null
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
        )
    }

    private val tapListener = object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
            if (ui.selection != null) {
                endSelection()
                return true
            }
            // While panels are open, any tap on the page just closes them.
            if (ui.panelsVisible || ui.sheet != null) {
                ui.hideAll()
                return true
            }
            val s = settings
            if (s.edgeTaps && !s.scroll) {
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
            // Android closed the app in the background and the open book was lost.
            // Go back to the library; the reading position is already saved.
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            finish()
            return
        }

        fonts = FontCatalog.available(this)
        bookId = session.bookId
        bookAuthor = session.publication.metadata.authors.joinToString(", ") { it.name }
        val initial = settings

        // IMPORTANT: the fragment factory must be set before super.onCreate().
        supportFragmentManager.fragmentFactory = EpubNavigatorFactory(session.publication)
            .createFragmentFactory(
                initialLocator = session.initialLocator,
                initialPreferences = initial.toEpubPreferences(fonts),
                listener = this,
                configuration = navigatorConfiguration(),
            )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)
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

        // Tapping a highlight opens its note.
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
                    ReaderOverlay(
                        state = ui,
                        settings = current,
                        fonts = fonts,
                        title = title,
                        cover = cover,
                        toc = toc,
                        onSettingsChange = { transform -> app.readerSettings.update(transform) },
                        onTocSelect = { item -> navigator.go(item.link, animated = false) },
                        onBack = { finish() },
                        notes = notes,
                        noteActions = noteActions,
                        ai = aiBridge,
                        onEdgeTap = { left -> onStripTap(left) },
                        onBrightnessPreview = { value -> setWindowBrightness(value) },
                    )
                }
            }
        }

        lifecycleScope.launch {
            book?.let { app.library.coverFile(it) }?.takeIf { it.exists() }?.let { file ->
                cover = withContext(Dispatchers.IO) {
                    com.flerty.wikiflerty.ui.CoverCache.load(file, maxWidth = 160)
                }
            }
        }

        lifecycleScope.launch {
            ui.totalPositions = withContext(Dispatchers.Default) {
                runCatching { session.publication.positions().size }.getOrDefault(0)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    navigator.currentLocator.collect { locator ->
                        ui.progression = locator.locations.totalProgression ?: ui.progression
                        ui.position = locator.locations.position
                        ui.chapter = locator.title
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
                    app.readerSettings.settings.collect { s ->
                        navigator.submitPreferences(s.toEpubPreferences(fonts))
                        applyWindowSettings(s)
                    }
                }
            }
        }
    }

    /** Draws every note of this book as a colored highlight. */
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

    /** Lets the book use the fonts bundled in assets/fonts, and shows our menu for selected text. */
    private fun navigatorConfiguration() = EpubNavigatorFragment.Configuration {
        servedAssets += "fonts/.*"
        selectionActionModeCallback = selectionCallback
        // Selected text: a see-through system accent color, the text itself keeps its color.
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

            // "Курсив" for the whole book: a family whose normal style is the italic file.
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

    /** The phone's own accent color (Android 12+), or the app accent on older phones. */
    private fun selectionAccent(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getColor(android.R.color.system_accent1_300)
        } else {
            0xFF8FB3FF.toInt()
        }

    private class CssColor(private val css: String) : org.readium.r2.navigator.epub.css.Color {
        override fun toCss(): String = css
    }

    // ------------------------------------------------------------ AI

    /** The sentence(s) around the selected text, so the AI understands how the word is used. */
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

    /** Switching the model re-asks the last question with the new one. */
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
                app.ai.chat(provider, chat.model, system, history).collect { piece ->
                    answer.append(piece)
                    val text = answer.toString()
                    if (chat.bubbles.isNotEmpty()) chat.bubbles[chat.bubbles.lastIndex] = AiBubble(false, text, text)
                }
                if (answer.isEmpty()) throw AiException("Модель вернула пустой ответ. Попробуй другую модель.")
            } catch (e: CancellationException) {
                if (answer.isEmpty()) dropEmptyAnswer()
                throw e
            } catch (e: Exception) {
                if (answer.isEmpty()) dropEmptyAnswer()
                chat.error = e.message ?: "Не удалось получить ответ"
            } finally {
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

    // ------------------------------------------------------------ page turning

    /** A tap on the narrow brightness strip at the left edge. */
    private fun onStripTap(left: Boolean) {
        val s = settings
        if (s.edgeTaps && !s.scroll) {
            turn(forward = !left || s.leftTapForward)
        } else {
            ui.panelsVisible = true
        }
    }

    private fun go(forward: Boolean, animated: Boolean): Boolean =
        if (forward) navigator.goForward(animated = animated) else navigator.goBackward(animated = animated)

    private fun turn(forward: Boolean) {
        when (settings.animation) {
            PageAnimation.Slide -> go(forward, animated = true)
            PageAnimation.None -> go(forward, animated = false)
            PageAnimation.Fade, PageAnimation.Cover -> animatedTurn(forward, settings.animation)
        }
    }

    /**
     * Fade and cover effects: take a picture of the current page, put it on top,
     * turn the page underneath, then fade or slide the picture away.
     */
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

    // ------------------------------------------------------------ window

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

    /** Leaves room above the text and, at the bottom, for the progress flag. */
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

    // ------------------------------------------------------------ keys, links

    /** Volume keys turn pages when that option is on. */
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

    /** Writes the latest reading position now (in the app scope, so leaving the book does not cancel it). */
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

    override fun onStop() {
        flushProgress()
        super.onStop()
    }

    override fun onDestroy() {
        flushProgress()
        super.onDestroy()
        if (isFinishing) {
            app.closeReader()
            val save = lastSave
            app.appScope.launch {
                save?.join()
                app.sync.autoSync()
            }
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
    }
}
