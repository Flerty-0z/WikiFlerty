package com.flerty.wikiflerty.reader

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.graphics.asAndroidBitmap
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
import kotlinx.coroutines.flow.first
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private var lastTurnKey: String? = null
    private var tocItems: List<TocItem> = emptyList()

    private fun chapterFor(href: String, progression: Double?): String? {
        val key = href.substringBefore('#')
        if (key.isNotEmpty()) tocItems.lastOrNull { it.link.href.toString().substringBefore('#') == key }?.title?.takeIf { it.isNotBlank() }?.let { return it }
        val at = progression ?: return null
        return ui.chapterMarks.lastOrNull { it.at <= at + 0.0001 }?.item?.title?.takeIf { it.isNotBlank() }
    }
    private var inkTurns = 0
    private val ui = ReaderUiState()
    private lateinit var navigator: EpubNavigatorFragment
    private lateinit var fonts: List<FontEntry>
    private lateinit var bookArea: View
    private lateinit var snapshot: ImageView
    private lateinit var curl: PageCurlView

    private var systemBars: Insets = Insets.NONE
    private var turning = false
        set(value) {
            field = value
            if (!value) ui.pageTurning = false
        }
    private var selectionJob: Job? = null
    private var actionMode: ActionMode? = null
    private var bookId: String = ""
    private val pageIntervals = ArrayDeque<Long>()
    private var lastPageKey = ""
    private var lastPageAt = 0L
    private var pageCountJob: Job? = null

    private var hrefOrder: List<Pair<String, Int>> = emptyList()
    private val screensByHref = HashMap<String, Int>()
    private var screensKey = ""

    private fun screensLayoutKey(): String {
        val s = settings
        val v = navigator.publicationView
        return listOf("b1", s.font, s.fontSize, s.lineHeight, s.margins, s.bold, s.columns, s.justify, s.hyphens, s.scroll, v.width / 8, v.height / 8).joinToString("|")
    }

    private fun loadScreens(key: String) {
        screensByHref.clear()
        screensKey = key
        val raw = getSharedPreferences("reader_screens", MODE_PRIVATE).getString(bookId, null) ?: return
        val json = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return
        if (json.optString("key") != key) return
        val map = json.optJSONObject("pages") ?: return
        map.keys().forEach { k -> map.optInt(k).takeIf { it > 0 }?.let { screensByHref[k] = it } }
    }

    private fun saveScreens() {
        val map = org.json.JSONObject()
        screensByHref.forEach { (k, v) -> map.put(k, v) }
        val json = org.json.JSONObject().put("key", screensKey).put("pages", map)
        getSharedPreferences("reader_screens", MODE_PRIVATE).edit().putString(bookId, json.toString()).apply()
    }

    private var textWeights: List<Pair<String, Int>>? = null
    private var weightsJob: Job? = null

    private fun loadWeights() {
        if (textWeights != null || weightsJob != null) return
        val pub = publication ?: return
        val book = bookId
        weightsJob = lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                val prefs = getSharedPreferences("reader_textlen", MODE_PRIVATE)
                val cached = prefs.getString(book, null)?.let { raw ->
                    runCatching {
                        val arr = org.json.JSONArray(raw)
                        (0 until arr.length()).map { i -> arr.getJSONArray(i).let { it.getString(0) to it.getInt(1) } }
                    }.getOrNull()
                }
                val order = pub.readingOrder.map { it.url().toString().substringBefore('#') }
                if (cached != null && cached.map { it.first } == order) return@withContext cached
                val list = pub.readingOrder.map { link ->
                    val res = pub.get(link)
                    val bytes = res?.let { r -> runCatching { r.read().getOrNull() }.getOrNull().also { runCatching { r.close() } } }
                    val html = bytes?.toString(Charsets.UTF_8).orEmpty()
                    val images = Regex("<(img|image|svg)\\b", RegexOption.IGNORE_CASE).findAll(html).count()
                    val text = htmlToText(html).count { !it.isWhitespace() }
                    link.url().toString().substringBefore('#') to (text + images * 700)
                }
                val arr = org.json.JSONArray()
                list.forEach { (h, w) -> arr.put(org.json.JSONArray().put(h).put(w)) }
                prefs.edit().putString(book, arr.toString()).apply()
                list
            }
            textWeights = found
            navigator.currentLocator.value.let { updatePageCount(it) }
        }
    }

    private fun bookScreens(href: String, cur: Int, count: Int) {
        val weights = textWeights
        if (weights == null) {
            loadWeights()
            return
        }
        if (hrefOrder.isEmpty()) hrefOrder = weights
        val key = screensLayoutKey()
        if (key != screensKey) loadScreens(key)
        if (screensByHref[href] != count) {
            screensByHref[href] = count
            saveScreens()
        }
        var measuredScreens = 0
        var measuredWeight = 0L
        hrefOrder.forEach { (h, n) -> screensByHref[h]?.let { if (n > 0) { measuredScreens += it; measuredWeight += n } } }
        val ratio = if (measuredWeight > 0) measuredScreens.toDouble() / measuredWeight else return
        var total = 0
        var before = -1
        hrefOrder.forEach { (h, n) ->
            if (h == href) before = total
            total += screensByHref[h] ?: (n * ratio).roundToInt().coerceAtLeast(1)
        }
        if (before < 0 || total <= 0) return
        ui.bookPages = total
        ui.bookPage = (before + cur).coerceIn(1, total)
    }

    private fun updatePageCount(locator: org.readium.r2.shared.publication.Locator) {
        pageCountJob?.cancel()
        pageCountJob = lifecycleScope.launch {
            delay(60)
            val raw = runCatching { navigator.evaluateJavascript(PAGE_COUNT_JS) }.getOrNull() ?: return@launch
            val parts = raw.trim('"').split('|')
            val cur = parts.getOrNull(0)?.toIntOrNull() ?: return@launch
            val count = parts.getOrNull(1)?.toIntOrNull() ?: return@launch
            if (count <= 0) return@launch
            ui.chapterPage = cur.coerceIn(1, count)
            ui.chapterPages = count
            bookScreens(locator.href.toString().substringBefore('#'), cur.coerceIn(1, count), count)
            val key = "${locator.href}#$cur"
            val now = SystemClock.elapsedRealtime()
            if (key != lastPageKey) {
                if (lastPageAt > 0) {
                    val dt = now - lastPageAt
                    if (dt in 3_000L..180_000L) {
                        pageIntervals.addLast(dt)
                        while (pageIntervals.size > 20) pageIntervals.removeFirst()
                    }
                }
                lastPageKey = key
                lastPageAt = now
            }
            ui.minutesLeft = if (pageIntervals.size >= 3) {
                val sorted = pageIntervals.sorted()
                val median = sorted[sorted.size / 2]
                ((count - ui.chapterPage) * median / 60_000.0).let { kotlin.math.ceil(it).toInt() }
            } else {
                null
            }
        }
    }
    private var pdfMap: com.flerty.wikiflerty.data.convert.PdfPageMap? = null
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
                PdfLocators.toText(mark.locator, pdfMap)?.let { navigator.go(it, animated = false) }
                resyncListening()
            },
            onDelete = { mark -> lifecycleScope.launch { app.bookmarks.delete(listOf(mark.id)) } },
            onRename = { mark -> ui.nameMark = mark },
            onName = { id, label -> app.appScope.launch { app.bookmarks.rename(id, label) } },
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
    private var rateBook by androidx.compose.runtime.mutableStateOf(false)
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
            onQuote = ::findQuote,
            onRemember = ::rememberFact,
            heroes = heroBridge,
            history = historyBridge,
        )
    }

    private val historyStore by lazy { com.flerty.wikiflerty.data.AiHistoryStore(this) }
    private val historyState = HistoryState()

    private fun refreshHistory(select: String? = historyState.book) {
        lifecycleScope.launch {
            val (choices, records) = withContext(Dispatchers.IO) {
                val all = historyStore.books().map { it to historyStore.load(it) }.filter { it.second.isNotEmpty() }
                val titles = app.library.books.value.associate { it.id to it.title }
                val choices = all.sortedByDescending { it.second.first().updatedAt }
                    .map { com.flerty.wikiflerty.ui.BookChoice(it.first, titles[it.first], it.second.size) }
                val records = if (select == null) all.flatMap { it.second }.sortedByDescending { it.updatedAt }
                else all.firstOrNull { it.first == select }?.second.orEmpty()
                choices to records
            }
            historyState.book = select
            historyState.choices.clear()
            historyState.choices.addAll(choices)
            historyState.total = choices.sumOf { it.count }
            historyState.records.clear()
            historyState.records.addAll(records)
        }
    }

    private val historyBridge by lazy {
        HistoryBridge(
            state = historyState,
            onLoad = { refreshHistory(bookId) },
            onSelectBook = { refreshHistory(it) },
            onOpen = ::openRecord,
            onDelete = { r ->
                historyState.records.removeAll { it.id == r.id }
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { historyStore.delete(r.bookId.ifBlank { bookId }, r.id) }
                    refreshHistory()
                }
            },
            onClear = {
                val target = historyState.book
                historyState.records.clear()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        if (target == null) historyStore.books().forEach { historyStore.clear(it) } else historyStore.clear(target)
                    }
                    refreshHistory()
                }
            },
        )
    }

    private fun saveHistory() {
        val id = chat.recordId ?: return
        val messages = chat.bubbles.filter { it.fromUser || it.shown.isNotBlank() }.map { com.flerty.wikiflerty.data.AiMessage(it.fromUser, it.shown, it.sent) }
        if (messages.none { !it.fromUser }) return
        val record = com.flerty.wikiflerty.data.AiRecord(
            id = id,
            action = chat.action.id,
            fragment = chat.fragment,
            context = chat.context.take(16_000),
            locatorJson = chat.locator?.toJSON()?.toString(),
            createdAt = chat.createdAt,
            updatedAt = System.currentTimeMillis(),
            messages = messages,
        )
        val book = chat.bookId ?: bookId
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { historyStore.save(book, record) }
            refreshHistory()
        }
    }

    private fun openRecord(record: com.flerty.wikiflerty.data.AiRecord) {
        chat.job?.cancel()
        chat.bubbles.clear()
        chat.bubbles.addAll(record.messages.map { AiBubble(it.fromUser, it.shown, it.sent) })
        chat.action = AiAction.entries.firstOrNull { it.id == record.action } ?: AiAction.Explain
        chat.fragment = record.fragment
        chat.context = record.context
        chat.locator = record.locatorJson?.takeIf { record.bookId.isBlank() || record.bookId == bookId }?.let { runCatching { Locator.fromJSON(org.json.JSONObject(it)) }.getOrNull() }
        chat.recordId = record.id
        chat.bookId = record.bookId.ifBlank { bookId }
        chat.createdAt = record.createdAt
        chat.error = null
        chat.savedToDictionary = false
        chat.savedToNotes = false
        val provider = app.ai.current()
        chat.provider = provider
        chat.model = provider?.let { app.ai.settings.value.modelFor(it) }.orEmpty()
        if (provider == null) chat.error = "Подключи нейросеть, чтобы продолжить чат"
        ui.chatFromHistory = true
        ui.sheet = ReaderSheet.AiChat
    }

    private fun trimmedHistory(list: List<ChatMessage>, limit: Int = 30): List<ChatMessage> {
        if (list.size <= limit) return list
        val tail = list.drop(1).takeLast(limit - 1)
        val start = tail.indexOfFirst { !it.fromUser }
        return listOf(list.first()) + if (start > 0) tail.drop(start) else tail
    }

    private val heroStore by lazy { com.flerty.wikiflerty.data.HeroStore(this) }
    private val heroUi = HeroUiState()
    private var avatarFor: String? = null
    private var heroContext: String? = null

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = avatarFor ?: return@registerForActivityResult
        avatarFor = null
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { heroStore.setAvatar(bookId, id, uri) }
            if (ok) heroUi.avatarVersion++ else Toast.makeText(this@ReaderActivity, "Не удалось открыть картинку", Toast.LENGTH_SHORT).show()
        }
    }

    private val voiceInput = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            ?: return@registerForActivityResult
        heroUi.input = (heroUi.input.trimEnd() + " " + text).trim()
    }

    private val heroBridge by lazy {
        HeroBridge(
            state = heroUi,
            author = { bookAuthor },
            avatarFile = { heroStore.avatar(bookId, it) },
            onFind = ::findHeroes,
            onOpen = ::openHero,
            onAdd = ::addHero,
            onRemove = ::removeHero,
            onAvatar = { hero ->
                avatarFor = hero.id
                runCatching { avatarPicker.launch("image/*") }
            },
            onSpoilers = { value ->
                heroStore.noSpoilers = value
                heroUi.noSpoilers = value
            },
            onSend = ::sendHeroMessage,
            onStop = { heroUi.chat.job?.cancel() },
            onRetry = ::retryHero,
            onVoice = ::startVoice,
            onReset = { hero ->
                heroStore.clearChat(bookId, hero.id)
                if (heroUi.current?.id == hero.id) {
                    heroUi.chat.job?.cancel()
                    heroUi.chat.bubbles.clear()
                    heroUi.current = null
                }
            },
        )
    }

    private fun loadHeroes(id: String) {
        heroUi.heroes.clear()
        heroUi.heroes.addAll(heroStore.load(id))
        heroUi.searched = heroUi.heroes.any { !it.custom }
        heroUi.noSpoilers = heroStore.noSpoilers
    }

    private fun findHeroes() {
        if (heroUi.loading) return
        val provider = app.ai.current()
        if (provider == null) {
            heroUi.error = "Подключи нейросеть, чтобы найти героев"
            return
        }
        heroUi.loading = true
        heroUi.error = null
        lifecycleScope.launch {
            try {
                val text = bookTextSoFar(60_000, whole = !heroUi.noSpoilers)
                if (text.isBlank()) throw AiException("Не удалось прочитать текст книги")
                val model = app.ai.settings.value.modelFor(provider)
                val answer = StringBuilder()
                app.ai.chat(
                    provider,
                    model,
                    "Ты помогаешь читателю разобраться в книге. Отвечай только JSON.",
                    listOf(ChatMessage(true, AiPrompts.heroesPrompt(text))),
                ).collect { answer.append(it) }
                val found = com.flerty.wikiflerty.data.HeroStore.parse(answer.toString())
                if (found.isEmpty()) throw AiException("ИИ не нашёл героев. Попробуй ещё раз или добавь героя вручную.")
                val custom = heroUi.heroes.filter { it.custom }
                val known = heroUi.heroes.filter { !it.custom }.associateBy { it.name.lowercase() }
                val merged = found.map { known[it.name.lowercase()] ?: it } + custom.filter { c -> found.none { it.name.equals(c.name, true) } }
                heroUi.heroes.clear()
                heroUi.heroes.addAll(merged)
                heroStore.save(bookId, merged)
                heroUi.searched = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                heroUi.error = e.message ?: "Не удалось найти героев"
                heroUi.searched = true
            } finally {
                heroUi.loading = false
            }
        }
    }

    private fun addHero(name: String) {
        if (heroUi.heroes.any { it.name.equals(name, true) }) return
        heroUi.heroes.add(com.flerty.wikiflerty.data.Hero(java.util.UUID.randomUUID().toString(), name, "", custom = true))
        heroStore.save(bookId, heroUi.heroes.toList())
    }

    private fun removeHero(hero: com.flerty.wikiflerty.data.Hero) {
        heroUi.heroes.removeAll { it.id == hero.id }
        heroStore.save(bookId, heroUi.heroes.toList())
        heroStore.removeAvatar(bookId, hero.id)
        heroUi.avatarVersion++
    }

    private fun openHero(hero: com.flerty.wikiflerty.data.Hero) {
        val hc = heroUi.chat
        hc.job?.cancel()
        hc.bubbles.clear()
        hc.error = null
        heroUi.input = ""
        heroUi.current = hero
        heroContext = null
        heroUi.place = ui.chapter?.takeIf { it.isNotBlank() }?.let { "до «${it.take(28)}»" } ?: "до ${(ui.progression * 100).roundToInt()}%"
        ui.sheet = ReaderSheet.HeroChat
        val provider = app.ai.current()
        if (provider == null) {
            hc.error = "Подключи нейросеть в настройках ИИ"
            return
        }
        hc.provider = provider
        hc.model = app.ai.settings.value.modelFor(provider)
        val saved = heroStore.loadChat(bookId, hero.id)
        if (saved.any { !it.fromUser && it.shown.isNotBlank() }) {
            hc.bubbles.addAll(saved.map { AiBubble(it.fromUser, it.shown, it.sent) })
            return
        }
        hc.bubbles.add(AiBubble(fromUser = true, shown = "", sent = "Поздоровайся со мной в своей манере — коротко, в 1–3 предложениях — и спроси, о чём я хочу поговорить."))
        sendHero()
    }

    private fun sendHeroMessage(text: String) {
        val hc = heroUi.chat
        if (hc.streaming || hc.provider == null || text.isBlank()) return
        hc.bubbles.add(AiBubble(fromUser = true, shown = text, sent = text))
        sendHero()
    }

    private fun retryHero() {
        val hc = heroUi.chat
        if (hc.streaming) return
        if (hc.bubbles.lastOrNull()?.fromUser == false) hc.bubbles.removeAt(hc.bubbles.lastIndex)
        if (hc.bubbles.isNotEmpty()) sendHero()
    }

    private fun sendHero() {
        val hero = heroUi.current ?: return
        val hc = heroUi.chat
        val provider = hc.provider ?: return
        hc.error = null
        val history = trimmedHistory(hc.bubbles.map { ChatMessage(it.fromUser, it.sent) })
        hc.bubbles.add(AiBubble(fromUser = false, shown = "", sent = ""))
        hc.streaming = true
        hc.job = lifecycleScope.launch {
            val answer = StringBuilder()
            try {
                val text = heroContext ?: bookTextSoFar(if (heroUi.noSpoilers) 40_000 else 16_000).also { heroContext = it }
                val system = AiPrompts.hero(
                    hero.name,
                    hero.role,
                    bookTitle,
                    bookAuthor,
                    hero.id == com.flerty.wikiflerty.data.Hero.AUTHOR_ID,
                    heroUi.noSpoilers,
                    text,
                )
                app.ai.chat(provider, hc.model, system, history).collect { piece ->
                    answer.append(piece)
                    val t = answer.toString()
                    if (hc.bubbles.isNotEmpty()) hc.bubbles[hc.bubbles.lastIndex] = AiBubble(false, t, t)
                }
                if (answer.isEmpty()) throw AiException("Модель вернула пустой ответ. Попробуй другую модель.")
            } catch (e: CancellationException) {
                if (answer.isEmpty()) dropEmpty(hc)
                throw e
            } catch (e: Exception) {
                if (answer.isEmpty()) dropEmpty(hc)
                hc.error = e.message ?: "Не удалось получить ответ"
            } finally {
                hc.streaming = false
                if (answer.isNotEmpty()) {
                    val messages = hc.bubbles.filter { it.fromUser || it.shown.isNotBlank() }
                        .map { com.flerty.wikiflerty.data.AiMessage(it.fromUser, it.shown, it.sent) }
                    val book = bookId
                    val heroId = hero.id
                    app.appScope.launch(Dispatchers.IO) { heroStore.saveChat(book, heroId, messages) }
                }
            }
        }
    }

    private fun dropEmpty(state: AiChatState) {
        val last = state.bubbles.lastOrNull()
        if (last != null && !last.fromUser && last.shown.isEmpty()) state.bubbles.removeAt(state.bubbles.lastIndex)
    }

    private fun startVoice() {
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Говори")
        try {
            voiceInput.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Голосовой ввод на телефоне недоступен", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun bookTextSoFar(maxChars: Int, whole: Boolean = false): String = withContext(Dispatchers.IO) {
        val pub = publication ?: return@withContext ""
        val locator = navigator.currentLocator.value
        val href = locator.href.toString().substringBefore('#')
        val order = pub.readingOrder
        val found = order.indexOfFirst { it.url().toString().substringBefore('#') == href }
        val idx = if (found < 0) order.lastIndex else found
        val end = if (whole) order.lastIndex else idx
        val parts = ArrayList<String>()
        for (i in 0..end) {
            val res = pub.get(order[i]) ?: continue
            val bytes = runCatching { res.read().getOrNull() }.getOrNull()
            runCatching { res.close() }
            val html = bytes?.toString(Charsets.UTF_8) ?: continue
            var text = htmlToText(html)
            if (!whole && i == idx) {
                val p = (locator.locations.progression ?: 1.0).coerceIn(0.0, 1.0)
                text = text.take((text.length * p).toInt() + 400)
            }
            if (text.isNotBlank()) parts += text
        }
        val all = parts.joinToString("\n\n").replace(Regex("\\n{3,}"), "\n\n")
        if (all.length <= maxChars) all else all.take(maxChars / 5) + "\n\n[…]\n\n" + all.takeLast(maxChars * 4 / 5)
    }

    private fun htmlToText(html: String): String {
        val start = html.indexOf("<body", ignoreCase = true)
        val body = if (start >= 0) html.substring(html.indexOf('>', start) + 1) else html
        val cleaned = body.replace(Regex("(?is)<(script|style)[^>]*>.*?</(script|style)>"), " ")
        return android.text.Html.fromHtml(cleaned, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
            .replace('\uFFFC', ' ')
            .replace(Regex("[ \\t]+"), " ")
            .trim()
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
            val seen = runCatching { navigator.evaluateJavascript("window.__wfSel && window.__wfSel.box ? window.__wfSel.box() : \"\"") }.getOrNull()
                ?.trim()?.removeSurrounding("\"")?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 2 }
            if (ui.panelsVisible || ui.sheet != null) ui.hideAll()
            updatePageOffset()
            ui.selection = SelectionInfo(
                locator = selection.locator,
                text = text,
                top = seen?.let { it[0] + pageOffset[1] } ?: ((rect?.top ?: 200f) + offsetY),
                bottom = seen?.let { it[1] + pageOffset[1] } ?: ((rect?.bottom ?: 240f) + offsetY),
            )
        }
    }

    private val selectionScript by lazy {
        val accent = selectionAccent()
        TextSelection.script(
            "rgb(${android.graphics.Color.red(accent)}, ${android.graphics.Color.green(accent)}, ${android.graphics.Color.blue(accent)})",
        )
    }

    @Volatile private var bootScript: String = ""

    private fun refreshBoot() {
        bootScript = runCatching {
            selectionScript + "\n;window.__wfBionic && window.__wfBionic(${settings.bionic});\n" + kitCall() + ";\n" + TextSelection.peopleCall(peopleNow)
        }.getOrDefault("")
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
        ui.handles = SelectionHandles(point("s"), point("e"), json.optInt("n", 0) == 1)
    }

    private fun selectionNextPage() {
        selectionJob?.cancel()
        ui.selection = null
        lifecycleScope.launch {
            val ok = runCatching { navigator.evaluateJavascript("window.__wfSel && window.__wfSel.carry ? window.__wfSel.carry() : false") }.getOrNull()
            if (ok?.contains("true") != true) return@launch
            ui.handles = null
            kotlinx.coroutines.delay(60)
            manualTurn(forward = true)
        }
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
            "sel-turn:1", "sel-turn:-1" -> {
                val forward = type.endsWith(":1")
                navigator.publicationView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                go(forward, animated = false)
                navigator.publicationView.postDelayed({
                    jsQueue.trySend(false to "window.__wfSel && window.__wfSel.turned && window.__wfSel.turned()")
                }, 90)
            }
            "auto-end" -> onAutoEnd()
            "feed-next" -> {
                ui.feedPull = null
                feedJump(true)
            }
            "feed-prev" -> {
                ui.feedPull = null
                feedJump(false)
            }
            else -> when {
                type.startsWith("person:") -> {
                    app.feedback.play(FeedbackSound.Open)
                    showPerson(type.removePrefix("person:"))
                }
                type.startsWith("feed:") -> onFeedPull(type.removePrefix("feed:"))
                type.startsWith("image:") -> openImage(type.removePrefix("image:"))
            }
        }
    }

    private var peopleNow: List<com.flerty.wikiflerty.data.BookCharacter> = emptyList()

    private var translateJob: Job? = null
    private var translateLocator: org.readium.r2.shared.publication.Locator? = null

    private fun cleanWord(text: String): String =
        text.replace(Regex("\\s+"), " ").trim().trim('.', ',', '!', '?', ':', ';', '«', '»', '"', '“', '”', '(', ')', '—', '-', '…')

    private fun startTranslate(text: String, locator: org.readium.r2.shared.publication.Locator?, word: Boolean, target: String = settings.translateTo) {
        val clean = if (word) cleanWord(text).take(120) else text.trim()
        if (clean.isBlank()) return
        translateJob?.cancel()
        translateLocator = locator
        ui.footnote = null
        ui.person = null
        ui.panelsVisible = false
        val known = word && app.dictionary.entries.value.any { it.word.equals(clean, ignoreCase = true) }
        ui.translate = TranslateInfo(clean, target, word, saved = known)
        translateJob = lifecycleScope.launch {
            val result = runCatching { if (word) Translator.define(clean, target) else Translator.translate(clean, target) }
            val current = ui.translate?.takeIf { it.text == clean && it.target == target } ?: return@launch
            result.onSuccess { r ->
                ui.translate = current.copy(loading = false, result = r, error = null)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                ui.translate = current.copy(loading = false, error = "Нет связи с переводчиком")
            }
        }
    }

    private fun saveTranslation() {
        val info = ui.translate ?: return
        val result = info.result ?: return
        if (info.saved || (info.word && result.empty)) return
        ui.translate = info.copy(saved = true)
        val locator = translateLocator
        val word = if (info.word) info.text else info.text.take(300)
        app.appScope.launch {
            app.dictionary.add(word, result.card(), locator?.let { contextAround(it) }.orEmpty().take(400), bookId, bookTitle, locator)
        }
        app.feedback.play(FeedbackSound.Toggle)
    }

    private val translateActions by lazy {
        TranslateActions(
            onTarget = { code ->
                val info = ui.translate
                app.readerSettings.update { it.copy(translateTo = code) }
                if (info != null) startTranslate(info.text, translateLocator, info.word, code)
            },
            onSave = { saveTranslation() },
            onRetry = { ui.translate?.let { startTranslate(it.text, translateLocator, it.word, it.target) } },
            onCopy = {
                ui.translate?.result?.let { r ->
                    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("WikiFlerty", r.text))
                    Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
                }
            },
            onClose = {
                translateJob?.cancel()
                ui.translate = null
            },
        )
    }

    private fun showPerson(id: String) {
        val person = peopleNow.firstOrNull { it.id == id } ?: return
        ui.panelsVisible = false
        ui.footnote = null
        ui.translate = null
        ui.person = person
        app.feedback.play(FeedbackSound.Open)
    }

    private fun applyPeople() {
        if (!::navigator.isInitialized) return
        refreshBoot()
        val call = TextSelection.peopleCall(peopleNow)
        lifecycleScope.launch { runCatching { navigator.evaluateJavascript(call) } }
    }

    private val peopleActions by lazy {
        PeopleActions(
            onSave = { draft ->
                lifecycleScope.launch {
                    val saved = app.characters.save(bookId, draft.id, draft.name, draft.note)
                    if (ui.person?.id == saved.id) ui.person = saved
                    if (ui.sheet == ReaderSheet.People) ui.peopleFocus = saved.id
                }
            },
            onDelete = { id ->
                lifecycleScope.launch {
                    app.characters.delete(listOf(id))
                    if (ui.person?.id == id) ui.person = null
                }
            },
            onUpdate = { changed ->
                lifecycleScope.launch {
                    app.characters.edit(changed.id) { changed }
                }
            },
            onAvatar = { id, uri ->
                lifecycleScope.launch {
                    if (uri == null) app.characters.removeAvatar(id) else app.characters.setAvatar(id, uri)
                }
            },
            onAi = { id -> fillPerson(id) },
        )
    }

    private fun fillPerson(id: String) {
        if (ui.personAi != null) return
        val person = peopleNow.firstOrNull { it.id == id } ?: return
        val provider = app.ai.current()
        if (provider == null) {
            ui.personAiError = "Подключи нейросеть в настройках ИИ"
            return
        }
        ui.personAi = id
        ui.personAiError = null
        lifecycleScope.launch {
            try {
                val text = bookTextSoFar(3_000_000)
                val passages = withContext(Dispatchers.Default) { PersonPassages.pick(text, person.name) }
                if (passages.isBlank()) throw AiException("В прочитанном «${person.name}» пока не встречается")
                val model = app.ai.settings.value.modelFor(provider)
                val answer = StringBuilder()
                app.ai.chat(
                    provider,
                    model,
                    "Ты помогаешь читателю вести досье на персонажей книги. Отвечай только JSON.",
                    listOf(ChatMessage(true, AiPrompts.characterProfile(person.name, bookTitle, bookAuthor, passages))),
                ).collect { answer.append(it) }
                val found = PersonPassages.parse(answer.toString()) ?: throw AiException("ИИ ответил не по форме. Попробуй ещё раз")
                val refresh = person.filled >= 4
                app.characters.edit(id) { c ->
                    fun merge(mine: String, theirs: String) = if (theirs.isNotBlank() && (refresh || mine.isBlank())) theirs else mine
                    c.copy(
                        role = c.role.ifBlank { found.role },
                        note = merge(c.note, found.who),
                        looks = merge(c.looks, found.looks),
                        ties = merge(c.ties, found.ties),
                        fate = merge(c.fate, found.fate),
                        quotes = (c.quotes + found.quotes).distinctBy { it.lowercase() }.take(12),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.personAiError = e.message ?: "Не удалось заполнить профиль"
            } finally {
                ui.personAi = null
            }
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
                PdfLocators.toText(note.locator, pdfMap)?.let { navigator.go(it, animated = false) }
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
            onSelectionMarker = { color ->
                val selection = ui.selection
                if (selection != null) {
                    endSelection()
                    lifecycleScope.launch {
                        app.notes.create(bookId, selection.locator, selection.text, "", color, ui.chapter)
                        if (color != settings.markerColor) app.readerSettings.update { it.copy(markerColor = color) }
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
            onSelectionTranslate = {
                val selection = ui.selection
                if (selection != null) {
                    endSelection()
                    startTranslate(selection.text, selection.locator, word = false)
                }
            },
            onSelectionDictionary = {
                val selection = ui.selection
                if (selection != null) {
                    endSelection()
                    startTranslate(selection.text, selection.locator, word = true)
                }
            },
            onSelectionPerson = {
                val selection = ui.selection
                if (selection != null) {
                    val name = selection.text.replace(Regex("\\s+"), " ").trim().trim('.', ',', '!', '?', ':', ';', '«', '»', '"', '—', '-').take(80)
                    endSelection()
                    val known = peopleNow.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ui.panelsVisible = false
                    ui.person = null
                    if (known != null) {
                        ui.personDraft = PersonDraft(known.id, known.name, known.note)
                    } else if (name.isNotBlank()) {
                        lifecycleScope.launch {
                            val saved = app.characters.save(bookId, null, name, "")
                            ui.personDraft = PersonDraft(saved.id, saved.name, saved.note)
                        }
                    }
                }
            },
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
    private var lastColumnsLayout = ""
    private var lastBionic: Boolean? = null
    private var lastKitLook = ""

    private fun kitCall(): String {
        fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)
        val s = settings
        val page = s.pageTheme
        val accent = pageAccent()
        val deep = android.graphics.Color.rgb(
            (android.graphics.Color.red(accent) * 0.68).toInt(),
            (android.graphics.Color.green(accent) * 0.68).toInt(),
            (android.graphics.Color.blue(accent) * 0.68).toInt(),
        )
        val style = org.json.JSONObject()
            .put("on", bookKit && com.flerty.wikiflerty.ui.AppStyle.decor != com.flerty.wikiflerty.ui.Decor.None)
            .put("kit", com.flerty.wikiflerty.ui.AppStyle.kit.id)
            .put("acc", hex(accent))
            .put("bg", hex(page.background.toInt()))
            .put("fg", hex(page.text.toInt()))
            .put("deep", hex(deep))
        return "window.__wfKit && window.__wfKit($style)"
    }

    private val bookKit: Boolean get() = com.flerty.wikiflerty.ui.AppStyle.tune.bookStyle

    private fun applyKitTexture() {
        val s = settings
        val page = s.pageTheme
        findViewById<PageTextureView>(R.id.page_texture)?.configure(com.flerty.wikiflerty.ui.Decor.None, page.text.toInt(), pageAccent(), page.isLight)
    }

    private var injectJob: Job? = null

    private var injectedHref: String? = null

    private fun injectScripts() {
        refreshBoot()
        injectJob?.cancel()
        injectJob = lifecycleScope.launch {
            for (wait in longArrayOf(0L, 250L, 700L, 1500L)) {
                delay(wait)
                if (!::navigator.isInitialized) continue
                runCatching { navigator.evaluateJavascript(selectionScript) }
                runCatching { navigator.evaluateJavascript("window.__wfBionic && window.__wfBionic(${settings.bionic})") }
                runCatching { navigator.evaluateJavascript(kitCall()) }
                runCatching { navigator.evaluateJavascript(TextSelection.peopleCall(peopleNow)) }
                applyColumns(settings)
            }
        }
    }

    private suspend fun applyColumns(s: ReaderSettings) {
        val two = s.columns == "2" && !s.scroll
        val script = if (two) {
            "(function(){var d=document,s=d.getElementById('wf-cols');if(!s){s=d.createElement('style');s.id='wf-cols';(d.head||d.documentElement).appendChild(s);}" +
                "s.textContent='html body,html body *{overflow-wrap:break-word!important;word-wrap:break-word!important;" + (if (s.hyphens) "-webkit-hyphens:auto!important;hyphens:auto!important;" else "") + "min-width:0!important}" +
                "html body img,html body svg,html body table{max-width:100%!important}';" +
                "window.__wfCols=true;" +
                "window.__wfColsFix=function(){if(!window.__wfCols)return;var h=d.documentElement,r=h.style,c=false;" +
                "if(r.getPropertyValue('--RS__colCount').trim()!=='2'){r.setProperty('--RS__colCount','2');r.setProperty('--RS__colWidth','auto');c=true;}" +
                "var e=[h,d.body].filter(function(x){return x&&getComputedStyle(x).columnCount==='2';})[0]||h;var cs=getComputedStyle(e);" +
                "var p=(parseFloat(cs.paddingLeft)||0)+(parseFloat(cs.paddingRight)||0);" +
                "if(p<24){r.setProperty('--RS__pageGutter','12px');cs=getComputedStyle(e);p=(parseFloat(cs.paddingLeft)||0)+(parseFloat(cs.paddingRight)||0);}" +
                "var g=Math.round(p*100)/100+'px';if(r.getPropertyValue('--RS__colGap').trim()!==g){r.setProperty('--RS__colGap',g);c=true;}" +
                "if(c)window.dispatchEvent(new Event('resize'));};" +
                "if(!window.__wfColsHook){window.__wfColsHook=true;window.addEventListener('resize',function(){setTimeout(function(){window.__wfColsFix&&window.__wfColsFix();},60);});}" +
                "window.__wfColsFix();})()"
        } else {
            "(function(){window.__wfCols=false;var d=document,s=d.getElementById('wf-cols');if(s)s.remove();var r=d.documentElement.style;if(r.getPropertyValue('--RS__colCount')){r.removeProperty('--RS__colCount');r.removeProperty('--RS__colWidth');r.removeProperty('--RS__colGap');r.removeProperty('--RS__pageGutter');window.dispatchEvent(new Event('resize'));}})()"
        }
        runCatching { navigator.evaluateJavascript(script) }
        runCatching { navigator.evaluateJavascript(feedCall()) }
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
        if (!feedJump(true, force = true)) stopAuto()
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
        ui.person = null
        ui.translate = null
        ui.footnote = FootnoteInfo(cleaned, url)
    }

    private val tapListener = object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
            if (ui.footnote != null) {
                ui.footnote = null
                return true
            }
            if (ui.person != null) {
                ui.person = null
                app.feedback.play(FeedbackSound.Close)
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
            if (s.edgeTaps && !s.scroll) {
                val width = navigator.publicationView.width.toFloat()
                if (width > 0f) {
                    val x = event.point.x
                    if (x < width * s.edgeZone) {
                        manualTurn(forward = s.leftTapForward)
                        return true
                    }
                    if (x > width * (1 - s.edgeZone)) {
                        manualTurn(forward = true)
                        return true
                    }
                }
            }
            ui.panelsVisible = true
            app.feedback.play(FeedbackSound.Open)
            return true
        }
    }

    override fun finish() {
        super.finish()
        ReaderTransitions.closing(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ReaderTransitions.install(this)
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
        if (app.library.books.value.firstOrNull { it.id == bookId }?.pdfText == true) {
            pdfMap = com.flerty.wikiflerty.data.convert.PdfPageMap.load(this, bookId)
        }
        bookAuthor = session.publication.metadata.authors.joinToString(", ") { it.name }
        loadHeroes(session.bookId)
        val initial = settings
        refreshBoot()

        supportFragmentManager.fragmentFactory = EpubNavigatorFactory(session.publication)
            .createFragmentFactory(
                initialLocator = session.initialLocator,
                initialPreferences = initial.toEpubPreferences(fonts),
                listener = this,
                paginationListener = object : EpubNavigatorFragment.PaginationListener {
                    override fun onPageLoaded() {
                        injectScripts()
                        preRasterPages()
                        if (!revealed) {
                            revealed = true
                            lifecycleScope.launch {
                                if (::navigator.isInitialized) {
                                    kotlinx.coroutines.withTimeoutOrNull(900) {
                                        runCatching { navigator.evaluateJavascript(selectionScript) }
                                        runCatching { navigator.evaluateJavascript("window.__wfBionic && window.__wfBionic(${settings.bionic})") }
                                        runCatching { navigator.evaluateJavascript(kitCall()) }
                                        runCatching { navigator.evaluateJavascript(TextSelection.peopleCall(peopleNow)) }
                                        delay(160)
                                        runCatching { navigator.evaluateJavascript("1") }
                                    }
                                }
                                revealPages()
                            }
                        }
                    }
                },
                configuration = navigatorConfiguration(),
            )

        super.onCreate(savedInstanceState)
        com.flerty.wikiflerty.ui.HighRefresh.apply(window)
        setContentView(R.layout.activity_reader)
        ui.handleColor = selectionAccent()
        ui.onHandleDrag = { phase, start, x, y -> onHandleDrag(phase, start, x, y) }
        ui.onSelectionNext = { selectionNextPage() }
        startJsQueue()
        ReaderReveal.run = { x, y, apply -> themeReveal(x, y, apply) }
        bookArea = findViewById(R.id.book_area)
        snapshot = findViewById(R.id.page_snapshot)
        curl = PageCurlView(this).also { v ->
            v.elevation = 17 * resources.displayMetrics.density
            v.outlineProvider = null
            (bookArea as android.view.ViewGroup).addView(v, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bookArea.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val d = resources.displayMetrics.density
                val edge = (32 * d).toInt()
                val tall = minOf((200 * d).toInt(), v.height)
                val top = v.height - tall
                v.systemGestureExclusionRects = listOf(Rect(0, top, edge, v.height), Rect(v.width - edge, top, v.width, v.height))
            }
        }
        snapshot.outlineProvider = ViewOutlineProvider.BOUNDS
        snapshot.elevation = 16 * resources.displayMetrics.density

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<View>(R.id.reader_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            systemBars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
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
        lifecycleScope.launch {
            androidx.compose.runtime.snapshotFlow { ui.bookmarked }.collect { showRibbon(it, animate = !turning) }
        }
        if (savedInstanceState == null) {
            navContainer.alpha = 0f
            navContainer.postDelayed({
                if (!revealed) {
                    revealed = true
                    revealPages()
                }
            }, 2500)
        } else {
            revealed = true
        }

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
        val bookArt = app.illustrations.items.map { list -> list.filter { it.bookId == session.bookId } }
        val peopleKey = app.library.books.value.firstOrNull { it.id == session.bookId }?.statsKey
        val bookPeople = app.characters.characters.map { list ->
            list.filter { it.bookId == session.bookId || (peopleKey != null && peopleKey in it.books) }.sortedBy { it.name.lowercase() }
        }
        lifecycleScope.launch {
            bookPeople.distinctUntilChanged().collect { list ->
                peopleNow = list
                ui.person = ui.person?.let { p -> list.firstOrNull { it.id == p.id } }
                applyPeople()
            }
        }

        val book = app.library.books.value.firstOrNull { it.id == session.bookId }
        val title = book?.title ?: session.publication.metadata.title.orEmpty()
        bookTitle = title
        val toc = flattenToc(session.publication.tableOfContents)
        tocItems = toc
        var cover by mutableStateOf<ImageBitmap?>(null)

        if (!com.flerty.wikiflerty.ui.Tips.readerCoachDone(this)) ui.coach = true
        findViewById<ComposeView>(R.id.reader_overlay).setContent {
            CompositionLocalProvider(LocalFeedback provides app.feedback) {
                WikiFlertyTheme {
                    val current by app.readerSettings.settings.collectAsState()
                    val notes by bookNotes.collectAsState(initial = emptyList())
                    val marks by bookMarks.collectAsState(initial = emptyList())
                    val people by bookPeople.collectAsState(initial = emptyList())
                    val art by bookArt.collectAsState(initial = emptyList())
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
                        onEdgeTap = { left, at -> onStripTap(left, at.x, at.y) },
                        onBrightnessPreview = { value -> setWindowBrightness(value) },
                        onMarkFinished = {
                            ui.askFinished = false
                            app.appScope.launch { app.library.setStatus(bookId, ReadStatus.Finished) }
                            rateBook = true
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
                        people = people,
                        peopleActions = peopleActions,
                        translateActions = translateActions,
                        onReturn = ::goReturn,
                        illustrations = art,
                        illustrationActions = illustrationActions,
                        onCoachDone = {
                            ui.coach = false
                            com.flerty.wikiflerty.ui.Tips.markReaderCoach(this@ReaderActivity)
                        },
                        onFootnoteGo = { url ->
                            rememberReturn()
                            lifecycleScope.launch {
                                runCatching { navigator.evaluateJavascript("window.__wfGo && window.__wfGo(${org.json.JSONObject.quote(url)})") }
                            }
                        },
                    )
                    com.flerty.wikiflerty.ui.AchievementToast()
                    if (rateBook) com.flerty.wikiflerty.ui.RatingSheet(session.bookId, celebrate = true) { rateBook = false }
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
            ui.chapterMarks = withContext(Dispatchers.Default) {
                val firstAt = HashMap<String, Float>()
                positions.forEach { loc ->
                    val key = loc.href.toString().substringBefore('#')
                    if (key !in firstAt) firstAt[key] = (loc.locations.totalProgression ?: 0.0).toFloat()
                }
                toc.mapNotNull { item ->
                    val key = item.link.href.toString().substringBefore('#')
                    firstAt[key]?.let { ChapterMark(it, item) }
                }.distinctBy { it.at to it.item.depth }.sortedBy { it.at }
            }
            if (ui.chapter.isNullOrBlank()) ui.chapter = chapterFor(ui.currentHref.orEmpty(), ui.progression)
            val total = ui.totalPositions
            app.appScope.launch { app.library.setPositions(session.bookId, total) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    navigator.currentLocator.collect { locator ->
                        val href = locator.href.toString()
                        if (href != injectedHref) {
                            injectedHref = href
                            injectScripts()
                        }
                        if (href != autoHref) {
                            autoHref = href
                            if (ui.autoActive && !ui.autoPaused && settings.scroll) startAutoScrollJs()
                        }
                        val total = locator.locations.totalProgression
                        if (swipeState == SwipeState.Dragging || curlHide != null) {
                            heldProgress = (total ?: ui.progression) to locator.locations.position
                        } else {
                            heldProgress = null
                            ui.progression = total ?: ui.progression
                            ui.position = locator.locations.position
                        }
                        ui.chapter = locator.title?.takeIf { it.isNotBlank() } ?: chapterFor(href, total)
                        ui.currentHref = href
                        hasImages(href)
                        val turnKey = href + "|" + (locator.locations.progression ?: 0.0)
                        if (turnKey != lastTurnKey) {
                            val first = lastTurnKey == null
                            lastTurnKey = turnKey
                            val every = com.flerty.wikiflerty.ui.EInk.refresh
                            if (!first && com.flerty.wikiflerty.ui.EInk.on && every > 0 && !settings.scroll) {
                                inkTurns++
                                if (inkTurns >= every) {
                                    inkTurns = 0
                                    ui.inkFlash++
                                }
                            }
                        }
                        updatePageCount(locator)
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
                        val layoutKey = "${s.margins}|${s.fontSize}|${s.lineHeight}|${s.font}"
                        val kitKey = "$bookKit|${s.pageTheme.id}"
                        if (kitKey != lastKitLook) {
                            lastKitLook = kitKey
                            applyKitTexture()
                            launch { runCatching { navigator.evaluateJavascript(kitCall()) } }
                        }
                        if (s.bionic != lastBionic) {
                            lastBionic = s.bionic
                            launch {
                                runCatching { navigator.evaluateJavascript("window.__wfBionic && window.__wfBionic(${s.bionic})") }
                                runCatching { navigator.evaluateJavascript(TextSelection.peopleCall(peopleNow)) }
                            }
                        }
                        if (s.columns != lastColumns || s.scroll != lastColumnsScroll || layoutKey != lastColumnsLayout) {
                            lastColumns = s.columns
                            lastColumnsScroll = s.scroll
                            lastColumnsLayout = layoutKey
                            launch {
                                applyColumns(s)
                                delay(400)
                                applyColumns(s)
                            }
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
                        if (settings.ttsFollow && !turning && android.os.SystemClock.uptimeMillis() - manualTurnAt > 1500) navigator.go(locator, animated = false)
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
            val locator = note.locator?.takeUnless { PdfLocators.isPage(it) } ?: return@mapNotNull null
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
        disablePageTurnsWhileScrolling = true
        selectionActionModeCallback = selectionCallback
        registerJavascriptInterface(TextSelection.BRIDGE) {
            TextSelectionBridge(
                onEvent = { type -> runOnUiThread { onSelectionEvent(type) } },
                onFootnote = { text, url -> runOnUiThread { showFootnote(text, url) } },
                onHandles = { data -> runOnUiThread { onHandles(data) } },
                onBoot = { bootScript },
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
        ui.chatFromHistory = false
        chat.job?.cancel()
        chat.bubbles.clear()
        chat.error = null
        chat.savedToDictionary = false
        chat.savedToNotes = false
        chat.action = action
        chat.recordId = java.util.UUID.randomUUID().toString()
        chat.bookId = bookId
        chat.createdAt = System.currentTimeMillis()
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
        ui.chatFromHistory = false
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
        val history = trimmedHistory(chat.bubbles.map { ChatMessage(it.fromUser, it.sent) })
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
                if (answer.isNotEmpty()) saveHistory()
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

    private fun findQuote(quote: String) {
        val words = quote.replace(Regex("[«»\"“”„.,!?;:…()\\[\\]—–-]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        val query = words.take(5).joinToString(" ").ifBlank { return }
        searchActions.state.query = query
        searchActions.onSearch(query)
        ui.sheet = ReaderSheet.Search
    }

    private fun rememberFact(key: String, fact: String) {
        lifecycleScope.launch {
            app.dictionary.add(key, fact, chat.context.take(400), bookId, bookTitle, chat.locator)
            Toast.makeText(this@ReaderActivity, "Добавлено в карточки", Toast.LENGTH_SHORT).show()
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
                val created = app.bookmarks.create(bookId, locator, ui.chapter ?: locator.title, snippet)
                ui.nameMark = created
            }
        }
    }

    private fun quoteTypeface(): android.graphics.Typeface? {
        val entry = FontCatalog.find(fonts, settings.font)
        val file = entry.regular ?: return if (entry.id == "sans") android.graphics.Typeface.SANS_SERIF else android.graphics.Typeface.SERIF
        return runCatching { android.graphics.Typeface.createFromAsset(assets, "fonts/$file") }.getOrNull()
    }

    private fun quoteTheme() = QuoteTheme(com.flerty.wikiflerty.ui.AppStyle.base, com.flerty.wikiflerty.ui.AppStyle.accent.toInt())

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
            if (moved != "true") {
                if (settings.scroll) feedJump(forward, force = true) else go(forward, animated = false)
            }
        }
    }

    private val imageHrefs = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val imageCache = android.util.LruCache<String, ImageBitmap>(24)

    private fun hasImages(href: String) {
        if (imageHrefs.containsKey(href)) return
        val pub = publication ?: return
        imageHrefs[href] = false
        lifecycleScope.launch(Dispatchers.IO) {
            val link = pub.readingOrder.firstOrNull { it.url().toString().substringBefore('#') == href } ?: return@launch
            val res = pub.get(link) ?: return@launch
            val bytes = runCatching { res.read().getOrNull() }.getOrNull()
            runCatching { res.close() }
            val html = bytes?.toString(Charsets.UTF_8) ?: return@launch
            imageHrefs[href] = Regex("<(img|image)\\b", RegexOption.IGNORE_CASE).containsMatchIn(html)
        }
    }

    private fun imageLink(href: String): org.readium.r2.shared.publication.Link? {
        val pub = publication ?: return null
        return (pub.resources + pub.readingOrder).firstOrNull { it.url().toString().substringBefore('#') == href }
    }

    private fun hrefOfImage(src: String): String? {
        val pub = publication ?: return null
        val path = android.net.Uri.decode(runCatching { java.net.URI(src).rawPath }.getOrNull() ?: return null).trimStart('/')
        if (path.isEmpty()) return null
        val links = pub.resources + pub.readingOrder
        val hit = links.map { android.net.Uri.decode(it.url().toString().substringBefore('#')).trimStart('/') to it }
            .filter { (h, _) -> h.isNotEmpty() && (path == h || path.endsWith("/$h")) }
            .maxByOrNull { it.first.length }
        return hit?.second?.url()?.toString()?.substringBefore('#')
    }

    private suspend fun loadImage(href: String, maxSide: Int): ImageBitmap? {
        val key = "$href|$maxSide"
        imageCache.get(key)?.let { return it }
        val link = imageLink(href) ?: return null
        val pub = publication ?: return null
        val image = withContext(Dispatchers.IO) {
            val res = pub.get(link) ?: return@withContext null
            val bytes = runCatching { res.read().getOrNull() }.getOrNull()
            runCatching { res.close() }
            bytes ?: return@withContext null
            runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
            }.getOrNull()
        } ?: return null
        imageCache.put(key, image)
        return image
    }

    private fun shareIllustration(open: IllustrationOpen, strokes: List<com.flerty.wikiflerty.data.InkStroke>) {
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.Default) {
                runCatching {
                    val bmp = open.image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = android.graphics.Canvas(bmp)
                    val w = bmp.width.toFloat()
                    val h = bmp.height.toFloat()
                    strokes.forEach { s ->
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = s.color.toInt()
                            alpha = (255 * 0.9f).toInt()
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = (s.width * w).coerceAtLeast(1f)
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                        }
                        val p = s.points
                        if (p.size == 2) {
                            paint.style = android.graphics.Paint.Style.FILL
                            canvas.drawCircle(p[0] * w, p[1] * h, paint.strokeWidth / 2f, paint)
                        } else if (p.size >= 4) {
                            val path = android.graphics.Path()
                            path.moveTo(p[0] * w, p[1] * h)
                            var i = 2
                            while (i + 2 < p.size) {
                                path.quadTo(p[i] * w, p[i + 1] * h, (p[i] + p[i + 2]) / 2f * w, (p[i + 1] + p[i + 3]) / 2f * h)
                                i += 2
                            }
                            path.lineTo(p[p.size - 2] * w, p[p.size - 1] * h)
                            canvas.drawPath(path, paint)
                        }
                    }
                    val dir = java.io.File(cacheDir, "share").apply { mkdirs() }
                    val file = java.io.File(dir, "illustration.jpg")
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    androidx.core.content.FileProvider.getUriForFile(this@ReaderActivity, "$packageName.files", file)
                }.getOrNull()
            } ?: return@launch
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("image/jpeg")
                .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("illustration", uri)
            runCatching { startActivity(android.content.Intent.createChooser(intent, "Поделиться")) }
        }
    }

    private fun openImage(src: String) {
        if (ui.illustration != null) return
        lifecycleScope.launch {
            val href = hrefOfImage(src) ?: return@launch
            val image = loadImage(href, 2048) ?: return@launch
            if (ui.selection != null) endSelection()
            ui.hideAll()
            app.feedback.play(FeedbackSound.Open)
            ui.illustration = IllustrationOpen(href, image, ui.chapter, navigator.currentLocator.value.toJSON().toString(), fromGallery = false, title = publication?.metadata?.title)
        }
    }

    private val illustrationActions by lazy {
        IllustrationActions(
            load = { href, side -> loadImage(href, side) },
            onFavorite = { open, on ->
                app.feedback.play(FeedbackSound.Toggle)
                lifecycleScope.launch { app.illustrations.upsert(bookId, open.href, open.chapter, open.locatorJson) { it.copy(favorite = on) } }
            },
            onStrokes = { open, strokes ->
                lifecycleScope.launch { app.illustrations.upsert(bookId, open.href, open.chapter, open.locatorJson) { it.copy(strokes = strokes) } }
            },
            onOpen = { item ->
                lifecycleScope.launch {
                    val image = loadImage(item.href, 2048) ?: run {
                        Toast.makeText(this@ReaderActivity, "Не удалось открыть", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    app.feedback.play(FeedbackSound.Open)
                    ui.illustration = IllustrationOpen(item.href, image, item.chapter, item.locatorJson, fromGallery = true, title = publication?.metadata?.title)
                }
            },
            onShare = { open, strokes -> shareIllustration(open, strokes) },
            onGoTo = { open ->
                val locator = open.locatorJson?.let { runCatching { org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(it)) }.getOrNull() }
                ui.illustration = null
                ui.hideAll()
                if (locator != null) {
                    rememberReturn()
                    navigator.go(locator, animated = false)
                }
            },
        )
    }

    private fun onStripTap(left: Boolean, winX: Float, winY: Float) {
        if (ui.selection != null) {
            endSelection()
            return
        }
        if (ui.person != null) {
            ui.person = null
            app.feedback.play(FeedbackSound.Close)
            return
        }
        val images = ui.currentHref?.let { imageHrefs[it] } == true
        if (peopleNow.isEmpty() && !images) {
            stripTurn(left)
            return
        }
        val view = currentWebView() ?: navigator.publicationView
        val loc = IntArray(2).also { view.getLocationInWindow(it) }
        val density = resources.displayMetrics.density
        val x = (winX - loc[0]) / density
        val y = (winY - loc[1]) / density
        lifecycleScope.launch {
            val js = "(function(){var f=window.__wfFlagAt?window.__wfFlagAt($x,$y):\"\";if(f)return \"p:\"+f;var i=window.__wfImgAt?window.__wfImgAt($x,$y):\"\";return i?\"i:\"+i:\"\";})()"
            val raw = runCatching { navigator.evaluateJavascript(js) }.getOrNull()
            val hit = raw?.trim()?.let { if (it.startsWith("\"")) runCatching { org.json.JSONArray("[$it]").getString(0) }.getOrNull() else it }.orEmpty()
            when {
                hit.startsWith("p:") -> {
                    app.feedback.play(FeedbackSound.Open)
                    showPerson(hit.removePrefix("p:"))
                }
                hit.startsWith("i:") -> openImage(hit.removePrefix("i:"))
                else -> stripTurn(left)
            }
        }
    }

    private fun stripTurn(left: Boolean) {
        val s = settings
        if (s.edgeTaps && !s.scroll) {
            manualTurn(forward = !left || s.leftTapForward)
        } else {
            ui.panelsVisible = true
        }
    }

    private fun go(forward: Boolean, animated: Boolean): Boolean {
        lastGoAt = android.os.SystemClock.uptimeMillis()
        return if (forward) navigator.goForward(animated = animated) else navigator.goBackward(animated = animated)
    }

    private var manualTurnAt = 0L

    private fun restartAutoTimer() {
        manualTurnAt = android.os.SystemClock.uptimeMillis()
        if (ui.autoActive && !ui.autoPaused && !settings.scroll) {
            ui.autoProgress = 0f
            runAuto()
        }
    }

    private fun manualTurn(forward: Boolean) {
        restartAutoTimer()
        turn(forward)
    }

    private fun turn(forward: Boolean) {
        if (settings.scroll) {
            scrollPage(forward)
            return
        }
        when (settings.animation) {
            PageAnimation.None -> go(forward, animated = false)
            PageAnimation.Curl, PageAnimation.Fade, PageAnimation.Cover, PageAnimation.Flip, PageAnimation.Depth -> requestTurn(forward, settings.animation)
        }
        resyncListening()
    }

    private var capturing = false
    private var pendingTurn: Boolean? = null
    private var pendingCount = 0
    private var curlStart: Runnable? = null
    private val drainTurn = Runnable { drainPending() }

    private fun requestTurn(forward: Boolean, animation: PageAnimation) {
        if (capturing) {
            if (pendingTurn != forward) pendingCount = 0
            pendingTurn = forward
            pendingCount = (pendingCount + 1).coerceAtMost(4)
            return
        }
        if (turning) finishTurnNow()
        if (animation == PageAnimation.Curl) curlTurn(forward) else animatedTurn(forward, animation)
    }

    private fun finishTurnNow() {
        curlStart?.let { curl.removeCallbacks(it) }
        curlStart = null
        snapshot.animate().setListener(null).cancel()
        val shown = curl.showing || snapshot.visibility == View.VISIBLE || navContainer.elevation != 0f
        turnAnimator?.cancel()
        curl.cancel()
        curlHide?.let { curl.removeCallbacks(it) }
        curlHide = null
        curl.hide()
        swipeReady = false
        resetTurnViews()
        if (shown) overlayHiddenAt = android.os.SystemClock.uptimeMillis()
    }

    private fun schedulePending() {
        bookArea.removeCallbacks(drainTurn)
        if (pendingCount > 0) bookArea.postDelayed(drainTurn, 110)
    }

    private fun drainPending() {
        val dir = pendingTurn ?: return
        if (pendingCount <= 0) {
            pendingTurn = null
            return
        }
        pendingCount--
        if (pendingCount == 0) pendingTurn = null
        val animation = settings.animation
        if (settings.scroll || animation == PageAnimation.None) {
            go(dir, animated = false)
            schedulePending()
            return
        }
        requestTurn(dir, animation)
    }

    private fun animatedTurn(forward: Boolean, animation: PageAnimation) {
        turning = true
        capturing = true
        capturePage { bitmap ->
            capturing = false
            if (bitmap == null) {
                turning = false
                go(forward, animated = false)
                schedulePending()
                return@capturePage
            }
            prepareTurn(bitmap, forward, animation)
            val before = navigator.currentLocator.value
            if (!go(forward, animated = false)) {
                resetTurnViews()
                pendingTurn = null
                pendingCount = 0
                return@capturePage
            }
            val (duration, easing) = turnSpec(animation)
            whenTurned(before) { runTurn(1f, duration, easing) { resetTurnViews() } }
            schedulePending()
        }
    }

    private var turnSeq = 0

    private fun whenTurned(before: org.readium.r2.shared.publication.Locator, onReady: () -> Unit) {
        val seq = ++turnSeq
        val started = android.os.SystemClock.uptimeMillis()
        val view = bookArea
        var moved = 0
        fun check() {
            if (!turning || seq != turnSeq) return
            val waited = android.os.SystemClock.uptimeMillis() - started
            if (navigator.currentLocator.value != before) moved++
            if (moved >= 2 || waited >= 70) onReady() else view.postOnAnimation { check() }
        }
        view.postOnAnimation { check() }
    }

    private var turnAnimator: android.animation.ValueAnimator? = null
    private var turnForward = true
    private var turnKind = PageAnimation.Fade
    private var turnProgress = 0f
    private val navContainer: View by lazy { findViewById(R.id.page_frame) }
    private val ribbon: RibbonView by lazy { findViewById(R.id.page_ribbon) }
    private var ribbonOn = false

    private fun showRibbon(on: Boolean, animate: Boolean) {
        val accent = com.flerty.wikiflerty.ui.AppColors.Accent
        ribbon.setColors(
            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f).compositeOver(accent).toArgb(),
            accent.toArgb(),
        )
        if (on == ribbonOn) return
        ribbonOn = on
        ribbon.animate().setListener(null).cancel()
        val h = ribbon.height.takeIf { it > 0 }?.toFloat() ?: (28 * resources.displayMetrics.density)
        if (!animate || !com.flerty.wikiflerty.ui.Motion.enabled) {
            ribbon.alpha = 1f
            ribbon.translationY = 0f
            ribbon.visibility = if (on) View.VISIBLE else View.INVISIBLE
            return
        }
        if (on) {
            if (ribbon.visibility != View.VISIBLE) {
                ribbon.alpha = 0f
                ribbon.translationY = -h
                ribbon.visibility = View.VISIBLE
            }
            ribbon.animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(DecelerateInterpolator(1.6f)).start()
        } else {
            ribbon.animate().alpha(0f).translationY(-h).setDuration(220).setInterpolator(android.view.animation.AccelerateInterpolator(1.2f))
                .withEndAction {
                    ribbon.visibility = View.INVISIBLE
                    ribbon.alpha = 1f
                    ribbon.translationY = 0f
                }.start()
        }
    }

    private fun turnSpec(animation: PageAnimation): Pair<Long, android.animation.TimeInterpolator> = when (animation) {
        PageAnimation.Fade -> 280L to DecelerateInterpolator(1.4f)
        PageAnimation.Flip -> 430L to android.view.animation.AccelerateInterpolator(1.3f)
        PageAnimation.Depth -> 360L to DecelerateInterpolator(1.4f)
        else -> 320L to DecelerateInterpolator(1.4f)
    }

    private fun prepareTurn(bitmap: Bitmap, forward: Boolean, animation: PageAnimation) {
        turnAnimator?.cancel()
        turnForward = forward
        turnKind = animation
        turnProgress = 0f
        snapshot.animate().setListener(null).cancel()
        snapshot.setImageBitmap(bitmap)
        snapshot.alpha = 1f
        snapshot.translationX = 0f
        snapshot.rotationY = 0f
        snapshot.scaleX = 1f
        snapshot.scaleY = 1f
        val w = bookArea.width.toFloat()
        val camera = 9000 * resources.displayMetrics.density
        if (animation == PageAnimation.Flip) {
            snapshot.pivotX = 0f
            snapshot.pivotY = bookArea.height / 2f
            snapshot.cameraDistance = camera
            navContainer.pivotX = 0f
            navContainer.pivotY = bookArea.height / 2f
            navContainer.cameraDistance = camera
        }
        if (incoming(animation, forward)) {
            preRasterPages()
            navContainer.outlineProvider = ViewOutlineProvider.BOUNDS
            navContainer.elevation = 18 * resources.displayMetrics.density
        }
        snapshot.visibility = View.VISIBLE
        ui.pageTurning = true
        applyTurn(0f)
    }

    private var revealed = false

    private fun revealPages() {
        val view = navContainer
        view.postOnAnimation {
            view.postOnAnimation {
                view.animate().alpha(1f).setDuration(if (com.flerty.wikiflerty.ui.Motion.enabled) 240 else 0).setInterpolator(DecelerateInterpolator(1.4f)).start()
            }
        }
    }

    private fun preRasterPages() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !::bookArea.isInitialized) return
        fun walk(v: View) {
            if (v is android.webkit.WebView) {
                if (!v.settings.offscreenPreRaster) v.settings.offscreenPreRaster = true
                return
            }
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(bookArea)
    }

    private fun incoming(animation: PageAnimation, forward: Boolean) = when (animation) {
        PageAnimation.Cover -> forward
        PageAnimation.Flip, PageAnimation.Depth -> !forward
        else -> false
    }

    private fun applyTurn(p: Float) {
        turnProgress = p
        val w = bookArea.width.toFloat()
        val forward = turnForward
        when (turnKind) {
            PageAnimation.Fade -> snapshot.alpha = 1f - p
            PageAnimation.Flip -> if (forward) {
                snapshot.rotationY = -95f * p
                snapshot.alpha = 1f - 0.4f * p
            } else {
                navContainer.rotationY = -95f * (1f - p)
                navContainer.alpha = 0.6f + 0.4f * p
            }
            PageAnimation.Depth -> if (forward) {
                val k = 1f - 0.16f * p
                snapshot.scaleX = k
                snapshot.scaleY = k
                snapshot.alpha = 1f - p
                snapshot.translationX = -w * 0.12f * p
            } else {
                val k = 0.84f + 0.16f * p
                navContainer.scaleX = k
                navContainer.scaleY = k
                navContainer.alpha = p
                navContainer.translationX = -w * 0.12f * (1f - p)
            }
            PageAnimation.Cover -> if (forward) navContainer.translationX = w * (1f - p) else snapshot.translationX = w * p
            else -> snapshot.translationX = (if (forward) -w else w) * p
        }
    }

    private fun runTurn(to: Float, duration: Long, easing: android.animation.TimeInterpolator, delay: Long = 0, onEnd: () -> Unit) {
        turnAnimator?.cancel()
        turnAnimator = android.animation.ValueAnimator.ofFloat(turnProgress, to).apply {
            this.duration = duration
            startDelay = delay
            interpolator = easing
            addUpdateListener { applyTurn(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var done = false
                override fun onAnimationEnd(animation: android.animation.Animator) = finish()
                override fun onAnimationCancel(animation: android.animation.Animator) = finish()
                private fun finish() {
                    if (done) return
                    done = true
                    turnAnimator = null
                    onEnd()
                }
            })
            start()
        }
    }

    private fun resetTurnViews() {
        navContainer.translationX = 0f
        navContainer.rotationY = 0f
        navContainer.scaleX = 1f
        navContainer.scaleY = 1f
        navContainer.alpha = 1f
        navContainer.elevation = 0f
        hideSnapshot()
    }

    private fun hideSnapshot() {
        snapshot.visibility = View.GONE
        snapshot.setImageDrawable(null)
        snapshot.translationX = 0f
        snapshot.alpha = 1f
        snapshot.rotationY = 0f
        snapshot.scaleX = 1f
        snapshot.scaleY = 1f
        turning = false
    }

    private fun curlTurn(forward: Boolean) {
        turning = true
        capturing = true
        capturePage { bitmap ->
            capturing = false
            if (bitmap == null) {
                turning = false
                go(forward, animated = false)
                schedulePending()
                return@capturePage
            }
            curl.paper = settings.pageTheme.background.toInt()
            snapshot.setImageBitmap(bitmap)
            snapshot.alpha = 1f
            snapshot.rotationY = 0f
            snapshot.visibility = View.VISIBLE
            ui.pageTurning = true
            if (!go(forward, animated = false)) {
                hideSnapshot()
                pendingTurn = null
                pendingCount = 0
                return@capturePage
            }
            val start = Runnable {
                curlStart = null
                curl.play(bitmap, forward, onShown = {
                    snapshot.visibility = View.GONE
                    snapshot.setImageDrawable(null)
                }) {
                    curl.hide()
                    turning = false
                }
            }
            curlStart = start
            curl.postDelayed(start, 60)
            schedulePending()
        }
    }

    private enum class SwipeState { Idle, Watching, Ignored, Dragging }

    private var swipeState = SwipeState.Idle
    private var swipeX0 = 0f
    private var swipeRawX0 = 0f
    private var lastGoAt = 0L
    private var overlayHiddenAt = 0L
    private var swipeY0 = 0f
    private var swipeX = 0f
    private var swipeY = 0f
    private var swipeDown = 0L
    private var swipeForward = true
    private var swipeReady = false
    private var swipeReleased = false
    private var swipeComplete = false
    private var swipeVelocity: android.view.VelocityTracker? = null
    private var curlHide: Runnable? = null
    private var heldProgress: Pair<Double, Int?>? = null

    private fun releaseHeldProgress() {
        val held = heldProgress ?: return
        heldProgress = null
        ui.progression = held.first
        ui.position = held.second
    }

    private fun swipeAllowed(): Boolean {
        val s = settings
        if (s.scroll) return false
        if (s.animation == PageAnimation.None) return false
        return ui.sheet == null && ui.selection == null && ui.footnote == null && ui.illustration == null
    }

    private fun overPanels(ev: android.view.MotionEvent): Boolean {
        if (!ui.panelsVisible) return false
        val loc = IntArray(2).also { window.decorView.getLocationOnScreen(it) }
        val y = ev.rawY - loc[1]
        return y <= ui.topPanelBottom || y >= ui.bottomPanelTop
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (!::curl.isInitialized) return super.dispatchTouchEvent(ev)
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                swipeVelocity?.recycle()
                swipeVelocity = null
                val area = IntArray(2).also { bookArea.getLocationOnScreen(it) }
                val inside = ev.rawX >= area[0] && ev.rawX <= area[0] + bookArea.width && ev.rawY >= area[1] && ev.rawY <= area[1] + bookArea.height
                swipeState = if (inside && swipeAllowed() && !overPanels(ev) && !TouchShields.hit(ev.x, ev.y)) SwipeState.Watching else SwipeState.Ignored
                if (swipeState == SwipeState.Watching) {
                    swipeRawX0 = ev.rawX
                    swipeX0 = ev.rawX - area[0]
                    swipeY0 = ev.rawY - area[1]
                    swipeX = swipeX0
                    swipeY = swipeY0
                    swipeDown = ev.eventTime
                    swipeVelocity = android.view.VelocityTracker.obtain().also { it.addMovement(ev) }
                }
            }
            android.view.MotionEvent.ACTION_POINTER_DOWN -> if (swipeState == SwipeState.Watching) swipeState = SwipeState.Ignored
        }
        when (swipeState) {
            SwipeState.Watching -> {
                swipeVelocity?.addMovement(ev)
                if (ev.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                    val area = IntArray(2).also { bookArea.getLocationOnScreen(it) }
                    val x = ev.rawX - area[0]
                    val y = ev.rawY - area[1]
                    val dx = x - swipeX0
                    val dy = y - swipeY0
                    val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
                    if (kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.1f) {
                        swipeState = SwipeState.Dragging
                        swipeX = x
                        swipeY = y
                        val cancel = android.view.MotionEvent.obtain(ev).apply { action = android.view.MotionEvent.ACTION_CANCEL }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        ui.panelsVisible = false
                        restartAutoTimer()
                        startSwipe(forward = dx < 0)
                        return true
                    } else if (ev.eventTime - swipeDown > android.view.ViewConfiguration.getLongPressTimeout() && kotlin.math.abs(dx) < slop * 2 && kotlin.math.abs(dy) < slop * 2) {
                        swipeState = SwipeState.Ignored
                    }
                }
                if (swipeState == SwipeState.Watching && (ev.actionMasked == android.view.MotionEvent.ACTION_MOVE || ev.actionMasked == android.view.MotionEvent.ACTION_UP)) {
                    val still = android.view.MotionEvent.obtain(ev)
                    still.offsetLocation(swipeRawX0 - ev.rawX, 0f)
                    val handled = super.dispatchTouchEvent(still)
                    still.recycle()
                    if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) swipeState = SwipeState.Idle
                    return handled
                }
            }
            SwipeState.Dragging -> {
                swipeVelocity?.addMovement(ev)
                val area = IntArray(2).also { bookArea.getLocationOnScreen(it) }
                swipeX = ev.rawX - area[0]
                swipeY = ev.rawY - area[1]
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_MOVE -> if (swipeReady) {
                        if (settings.animation == PageAnimation.Curl) curl.drag(swipeCornerX(), swipeCornerY()) else applyTurn(swipeProgress())
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        val tracker = swipeVelocity
                        tracker?.computeCurrentVelocity(1000)
                        val vx = tracker?.xVelocity ?: 0f
                        tracker?.recycle()
                        swipeVelocity = null
                        val dist = if (swipeForward) swipeX0 - swipeX else swipeX - swipeX0
                        val fling = 700 * resources.displayMetrics.density
                        val towards = if (swipeForward) -vx else vx
                        val w = bookArea.width.toFloat().coerceAtLeast(1f)
                        val shown = if (settings.animation == PageAnimation.Curl) {
                            (if (swipeForward) w - swipeCornerX() else swipeCornerX()) / w
                        } else {
                            swipeProgress()
                        }
                        val enough = shown > 0.3f || dist > w * 0.22f
                        swipeComplete = ev.actionMasked == android.view.MotionEvent.ACTION_UP &&
                            (towards > fling || (enough && towards > -fling))
                        swipeReleased = true
                        swipeState = SwipeState.Idle
                        if (swipeComplete || !swipeReady) releaseHeldProgress()
                        if (swipeReady) endSwipe()
                    }
                }
                return true
            }
            else -> Unit
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun swipeCornerX(): Float {
        val w = bookArea.width.toFloat()
        return if (swipeForward) {
            val k = w * 1.15f / maxOf(swipeX0, w * 0.35f)
            (w - (swipeX0 - swipeX).coerceAtLeast(0f) * k).coerceAtLeast(-w)
        } else {
            val k = w * 1.15f / maxOf(w - swipeX0, w * 0.35f)
            ((swipeX - swipeX0).coerceAtLeast(0f) * k).coerceAtMost(2 * w)
        }
    }

    private fun swipeCornerY(): Float {
        val w = bookArea.width.toFloat()
        val h = bookArea.height.toFloat()
        val cx = swipeCornerX()
        val t = (if (swipeForward) (w - cx) else cx) / (2 * w)
        val lift = kotlin.math.sin(Math.PI * t.coerceIn(0f, 1f)).toFloat() * h * 0.12f
        return (h - lift - (swipeY0 - swipeY) * 0.6f).coerceIn(h * 0.35f, h)
    }

    private fun startSwipe(forward: Boolean) {
        swipeForward = forward
        swipeReady = false
        swipeReleased = false
        swipeComplete = false
        bookArea.removeCallbacks(drainTurn)
        pendingTurn = null
        pendingCount = 0
        if (turning) finishTurnNow()
        val animation = settings.animation
        turning = true
        capturing = true
        capturePage { bitmap ->
            capturing = false
            if (bitmap == null) {
                turning = false
                swipeReady = false
                return@capturePage
            }
            if (animation == PageAnimation.Curl) {
                snapshot.setImageBitmap(bitmap)
                snapshot.alpha = 1f
                snapshot.rotationY = 0f
                snapshot.translationX = 0f
                snapshot.visibility = View.VISIBLE
                ui.pageTurning = true
                if (!go(forward, animated = false)) {
                    hideSnapshot()
                    swipeReady = false
                    return@capturePage
                }
                curl.paper = settings.pageTheme.background.toInt()
                curl.begin(bitmap, forward, onShown = {
                    if (swipeState == SwipeState.Dragging || swipeReleased) {
                        snapshot.visibility = View.GONE
                        snapshot.setImageDrawable(null)
                    }
                })
                swipeReady = true
                if (swipeReleased) endSwipe() else curl.drag(swipeCornerX(), swipeCornerY())
            } else {
                prepareTurn(bitmap, forward, animation)
                if (!go(forward, animated = false)) {
                    resetTurnViews()
                    swipeReady = false
                    return@capturePage
                }
                swipeReady = true
                if (swipeReleased) endSwipe() else applyTurn(swipeProgress())
            }
        }
    }

    private fun afterRender(block: () -> Unit) {
        var hide: Runnable? = null
        hide = Runnable {
            curl.postOnAnimation {
                curl.postOnAnimation {
                    if (curlHide === hide) {
                        curlHide = null
                        block()
                        releaseHeldProgress()
                    }
                }
            }
        }
        curlHide = hide
        curl.postDelayed(hide, 150)
    }

    private fun swipeProgress(): Float {
        val w = bookArea.width.toFloat().coerceAtLeast(1f)
        val dist = if (swipeForward) swipeX0 - swipeX else swipeX - swipeX0
        return (dist / (w * 0.85f)).coerceIn(0f, 1f)
    }

    private fun endSwipe() {
        val animation = settings.animation
        val forward = swipeForward
        if (animation != PageAnimation.Curl) {
            swipeReady = false
            val (full, _) = turnSpec(animation)
            val complete = swipeComplete
            val target = if (complete) 1f else 0f
            val share = kotlin.math.abs(target - turnProgress)
            runTurn(target, (90 + full * share).toLong(), DecelerateInterpolator(1.3f)) {
                if (complete) {
                    resetTurnViews()
                    resyncListening()
                } else {
                    go(!forward, animated = false)
                    afterRender {
                        resetTurnViews()
                    }
                }
            }
            return
        }
        swipeReady = false
        curl.release(swipeComplete) { done ->
            if (done) {
                curl.hide()
                turning = false
                resyncListening()
            } else {
                go(!forward, animated = false)
                afterRender {
                    curl.hide()
                    turning = false
                }
            }
        }
    }

    private var revealing = false

    private fun themeReveal(x: Float, y: Float, apply: () -> Unit) {
        val decor = window.decorView as? android.view.ViewGroup
        if (revealing || !com.flerty.wikiflerty.ui.Motion.enabled || decor == null || decor.width == 0 || decor.height == 0) {
            apply()
            return
        }
        revealing = true
        val cx = if (x >= 0f) x else decor.width / 2f
        val cy = if (y >= 0f) y else decor.height / 2f
        val ring = selectionAccent()
        fun show(bitmap: Bitmap?) {
            if (bitmap == null) {
                revealing = false
                apply()
                return
            }
            val cover = RevealCover(this, bitmap, cx, cy, ring)
            decor.addView(cover, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
            cover.post {
                apply()
                cover.postDelayed({
                    cover.start {
                        decor.removeView(cover)
                        bitmap.recycle()
                        revealing = false
                    }
                }, 260)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(window, bitmap, { result -> show(if (result == PixelCopy.SUCCESS) bitmap else null) }, Handler(Looper.getMainLooper()))
            } catch (e: IllegalArgumentException) {
                show(null)
            }
        } else {
            show(runCatching { decor.drawToBitmap() }.getOrNull())
        }
    }

    private fun capturePage(onReady: (Bitmap?) -> Unit) {
        val now = android.os.SystemClock.uptimeMillis()
        val settle = maxOf(0L, 90L - (now - lastGoAt))
        val overlay = now - overlayHiddenAt < 50L
        if (settle > 0L || overlay) {
            val view = bookArea
            val run = Runnable { view.postOnAnimation { view.postOnAnimation { copyPage(onReady) } } }
            if (settle > 0L) view.postDelayed(run, settle) else run.run()
            return
        }
        copyPage(onReady)
    }

    private fun eraseFlag(bitmap: Bitmap, top: Int): Bitmap {
        if (!settings.showFlag || settings.scroll) return bitmap
        val y = (ui.flagTop - top).roundToInt()
        if (y <= 0 || y >= bitmap.height) return bitmap
        runCatching {
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply { color = settings.pageTheme.background.toInt() }
            canvas.drawRect(0f, y.toFloat(), bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        }
        return bitmap
    }

    private fun copyPage(onReady: (Bitmap?) -> Unit) {
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
                    { result -> onReady(if (result == PixelCopy.SUCCESS) eraseFlag(bitmap, location[1]) else null) },
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
        val bars = if (s.fullscreen) Insets.NONE else systemBars
        val top = bars.top + (20 * density).toInt()
        val bottom = bars.bottom + ((if (s.showFlag) 22 + 24 * s.flagSize.coerceIn(0.8, 1.0) else 18.0) * density).toInt()
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
                manualTurn(forward = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
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
            val listening = app.readAloud.listening.value == bookId
            app.stats.add(key, if (listening) 0L else counted, pagesSinceTrack)
            pagesSinceTrack = 0
        }
        lastActivity = now
    }

    private var finishProbe = -1.0

    private fun maybeAskFinished(progression: Double) {
        val before = finishProbe
        finishProbe = progression
        if (askedFinished || progression < 0.985) return
        if (ui.returnTo != null || before < 0 || progression - before > 0.03) return
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
        app.cloud.request(2_000)
    }

    override fun onDestroy() {
        if (ReaderReveal.run != null) ReaderReveal.run = null
        flushProgress()
        super.onDestroy()
        if (isFinishing) {
            if (mySession != null) app.readAloud.stop()
            app.ambient.stopAll()
            app.closeReader()
        }
    }

    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    override fun shouldFollowInternalLink(link: org.readium.r2.shared.publication.Link, context: org.readium.r2.navigator.HyperlinkNavigator.LinkContext?): Boolean {
        rememberReturn()
        return true
    }

    private fun rememberReturn() {
        if (ui.returnTo == null && ::navigator.isInitialized) ui.returnTo = navigator.currentLocator.value
    }

    private fun goReturn() {
        val back = ui.returnTo ?: return
        ui.returnTo = null
        val target = if (back.locations.progression != null) back.copy(text = org.readium.r2.shared.publication.Locator.Text()) else back
        lifecycleScope.launch {
            navigator.go(target, animated = false)
            resyncListening()
        }
    }

    private fun resourceIndex(): Int {
        val pub = publication ?: return -1
        val href = navigator.currentLocator.value.href.toString().substringBefore('#')
        return pub.readingOrder.indexOfFirst { it.url().toString().substringBefore('#') == href }
    }

    private fun pageAccent(): Int {
        val kit = com.flerty.wikiflerty.ui.AppStyle.kit
        val a = com.flerty.wikiflerty.ui.AppStyle.accent
        val custom = a != kit.accent && a != kit.accentLight
        return (if (custom) a else if (settings.pageTheme.isLight) kit.accentLight else kit.accent).toInt()
    }

    private fun feedCall(): String {
        val i = resourceIndex()
        val n = publication?.readingOrder?.size ?: 0
        return "window.__wfFeed && window.__wfFeed(${settings.scroll}, ${i > 0}, ${i in 0 until n - 1})"
    }

    private fun onFeedPull(raw: String) {
        val parts = raw.split(':')
        val dir = parts.getOrNull(0)?.toIntOrNull() ?: 0
        if (dir == 0 || !settings.scroll) {
            ui.feedPull = null
            return
        }
        val k = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
        val has = parts.getOrNull(2) == "1"
        val before = ui.feedPull
        val pull = FeedPull(dir, k, has)
        if (pull.ready && before?.ready != true) {
            navigator.publicationView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
        ui.feedPull = pull
    }

    private var feedAt = 0L
    private val shiftView: ChapterShiftView by lazy { findViewById(R.id.chapter_shift) }

    private fun chapterTitle(link: org.readium.r2.shared.publication.Link): String {
        val pub = publication ?: return ""
        val href = link.url().toString().substringBefore('#')
        return flattenToc(pub.tableOfContents).firstOrNull { it.link.url().toString().substringBefore('#') == href }?.title
            ?: link.title?.trim().orEmpty()
    }

    private fun shiftKind(): ChapterShiftView.Kind = when (com.flerty.wikiflerty.ui.AppStyle.kit) {
        com.flerty.wikiflerty.ui.Kit.Library -> ChapterShiftView.Kind.Library
        com.flerty.wikiflerty.ui.Kit.Zen -> ChapterShiftView.Kind.Zen
        com.flerty.wikiflerty.ui.Kit.Comic -> ChapterShiftView.Kind.Comic
        com.flerty.wikiflerty.ui.Kit.News -> ChapterShiftView.Kind.News
        com.flerty.wikiflerty.ui.Kit.Sea -> ChapterShiftView.Kind.Sea
        com.flerty.wikiflerty.ui.Kit.Clay -> ChapterShiftView.Kind.Clay
        com.flerty.wikiflerty.ui.Kit.NewYear -> ChapterShiftView.Kind.Library
        com.flerty.wikiflerty.ui.Kit.Ink -> ChapterShiftView.Kind.News
    }

    private fun feedJump(forward: Boolean, force: Boolean = false): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && (now - feedAt < 700 || !settings.scroll)) return false
        if (shiftView.active) return true
        val pub = publication ?: return false
        val i = resourceIndex()
        if (i < 0) return false
        val link = pub.readingOrder.getOrNull(if (forward) i + 1 else i - 1) ?: return false
        val base = pub.locatorFromLink(link) ?: return false
        feedAt = now
        val target = if (forward) base.copyWithLocations(progression = 0.0) else base.copyWithLocations(progression = 0.9999)
        val targetHref = link.url().toString().substringBefore('#')
        if (settings.animation == PageAnimation.None || com.flerty.wikiflerty.ui.Motion.ms(100) == 0) {
            lifecycleScope.launch {
                navigator.go(target, animated = false)
                resyncListening()
            }
            return true
        }
        val page = settings.pageTheme
        val title = chapterTitle(link)
        capturePage { bitmap ->
            shiftView.start(bitmap, title, forward, shiftKind(), page.background.toInt(), page.text.toInt(), pageAccent(), navContainer)
            lifecycleScope.launch {
                navigator.go(target, animated = false)
                kotlinx.coroutines.withTimeoutOrNull(1800) {
                    navigator.currentLocator.first { it.href.toString().substringBefore('#') == targetHref }
                }
                delay(260)
                shiftView.reveal()
                resyncListening()
            }
        }
        return true
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
        } catch (_: ActivityNotFoundException) {
        }
    }

    private companion object {
        private const val PAGE_COUNT_JS = "(function(){var e=document.scrollingElement;if(!e)return '';var w=window.innerWidth,h=window.innerHeight;if(e.scrollWidth>w+4){var n=Math.max(1,Math.round(e.scrollWidth/w));var c=Math.min(n,Math.round(Math.abs(e.scrollLeft)/w)+1);return c+'|'+n;}var n2=Math.max(1,Math.ceil((e.scrollHeight-4)/h));var c2=Math.min(n2,Math.floor((e.scrollTop+4)/h)+1);return c2+'|'+n2;})()"

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

private class RevealCover(
    context: android.content.Context,
    private val bitmap: Bitmap,
    private val cx: Float,
    private val cy: Float,
    private val ring: Int,
) : View(context) {
    private var progress = 0f
    private val hole = android.graphics.Path()
    private val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE }

    init {
        isClickable = true
        isFocusable = false
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val far = maxOf(maxOf(kotlin.math.hypot(cx, cy), kotlin.math.hypot(w - cx, cy)), maxOf(kotlin.math.hypot(cx, h - cy), kotlin.math.hypot(w - cx, h - cy)))
        val r = far * progress
        canvas.save()
        if (r > 0f) {
            hole.reset()
            hole.addCircle(cx, cy, r, android.graphics.Path.Direction.CW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) canvas.clipOutPath(hole)
            else @Suppress("DEPRECATION") canvas.clipPath(hole, android.graphics.Region.Op.DIFFERENCE)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.restore()
        if (progress > 0f && progress < 1f) {
            val d = resources.displayMetrics.density
            val fade = 1f - progress
            stroke.color = ring
            stroke.alpha = (140 * fade).toInt()
            stroke.strokeWidth = 2.5f * d
            canvas.drawCircle(cx, cy, r, stroke)
            stroke.alpha = (46 * fade).toInt()
            stroke.strokeWidth = 6f * d
            canvas.drawCircle(cx, cy, r + 6f * d, stroke)
        }
    }

    fun start(done: () -> Unit) {
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = com.flerty.wikiflerty.ui.Motion.ms(640).toLong()
            interpolator = android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = done()
            })
            start()
        }
    }
}
