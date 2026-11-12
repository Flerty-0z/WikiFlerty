package com.flerty.wikiflerty.reader

import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.drawscope.translate
import com.flerty.wikiflerty.ui.revealScroll
import com.flerty.wikiflerty.ui.Reveal
import androidx.compose.ui.draw.shadow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.flerty.wikiflerty.ui.AppStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.composed
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.KitSwitch
import com.flerty.wikiflerty.ui.KitSlider
import com.flerty.wikiflerty.ui.KitSpinner
import com.flerty.wikiflerty.ai.AiAction
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.PickerOption
import com.flerty.wikiflerty.ui.PickerRow
import com.flerty.wikiflerty.ui.SettingIcon
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.accentContent
import com.flerty.wikiflerty.ui.accentHighlight
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.LocalFeedback
import com.flerty.wikiflerty.ui.fieldSurface
import com.flerty.wikiflerty.ui.sheetReveal
import com.flerty.wikiflerty.ui.revealViewport
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface
import java.util.Locale
import kotlin.math.roundToInt
import org.readium.r2.shared.publication.Link

private val PanelGap = 52.dp
private const val FlagHeight = 24
internal val PanelMaxWidth = 620.dp

private val FlagBottom = ((52 - FlagHeight) / 2).dp
private val MiniBottom = FlagBottom + FlagHeight.dp + 8.dp

enum class ReaderSheet(val title: String, val depth: Int) {
    Options("Опции", 0),
    Toc("Главы", 0),
    Text("Текст", 1),
    Scroll("Листание", 1),
    Theme("Тема", 1),
    Panel("Панель", 1),
    Gestures("Жесты", 1),
    Screen("Экран", 1),
    Sound("Звук", 1),
    Atmosphere("Атмосфера", 1),
    Selection("Выделение", 1),
    Listen("Чтение вслух", 1),
    Fonts("Шрифт", 2),
    Notes("Заметки", 0),
    Bookmarks("Закладки", 0),
    People("Персонажи", 0),
    Quote("Цитата", 0),
    NoteEditor("Заметка", 3),
    AiMenu("ИИ", 0),
    AiChat("ИИ", 0),
    AiSetup("Нейросети", 1),
    Search("Поиск", 0),
    Jump("Переход", 0),
    AiChapter("ИИ по главе", 1),
    Heroes("Герои книги", 1),
    HeroChat("Герой", 1),
    AiHistory("История", 1),
    Flag("Флажок", 2),
}

data class FlagInfo(
    val chapter: String? = null,
    val chapterPage: Int = 0,
    val chapterPages: Int = 0,
    val bookPage: Int = 0,
    val bookPages: Int = 0,
    val progression: Double = 0.0,
    val minutesLeft: Int? = null,
)

class ReaderUiState {
    var panelsVisible by mutableStateOf(false)
    var sheet by mutableStateOf<ReaderSheet?>(null)
    var progression by mutableStateOf(0.0)
    var position by mutableStateOf<Int?>(null)
    var totalPositions by mutableIntStateOf(0)
    var chapterMarks by mutableStateOf<List<ChapterMark>>(emptyList())
    var chapter by mutableStateOf<String?>(null)
    var brightnessHint by mutableStateOf<Int?>(null)
    var selection by mutableStateOf<SelectionInfo?>(null)
    var handles by mutableStateOf<SelectionHandles?>(null)
    var flagTop by mutableStateOf(0f)
    var flagHeight by mutableStateOf(0)
    var handleColor by mutableIntStateOf(0xFF8FB3FF.toInt())
    var onHandleDrag: ((phase: Int, start: Boolean, x: Float, y: Float) -> Unit)? = null
    var onSelectionNext: (() -> Unit)? = null
    var footnote by mutableStateOf<FootnoteInfo?>(null)
    var autoActive by mutableStateOf(false)
    var autoPaused by mutableStateOf(false)
    var autoProgress by mutableFloatStateOf(0f)
    var autoCollapsed by mutableStateOf(false)
    var ttsCollapsed by mutableStateOf(false)
    var editor by mutableStateOf<NoteDraft?>(null)
    var aiSetupFromMenu by mutableStateOf(false)
    var ttsActive by mutableStateOf(false)
    var ttsPlaying by mutableStateOf(false)
    var ttsProblem by mutableStateOf<com.flerty.wikiflerty.tts.ReadAloud.Problem?>(null)
    var ttsSleep by mutableStateOf<com.flerty.wikiflerty.tts.SleepTimer?>(null)
    var askFinished by mutableStateOf(false)
    var chatFromHistory by mutableStateOf(false)
    var bookmarked by mutableStateOf(false)
    var pageTurning by mutableStateOf(false)
    var quote by mutableStateOf<QuoteDraft?>(null)
    var person by mutableStateOf<com.flerty.wikiflerty.data.BookCharacter?>(null)
    var personDraft by mutableStateOf<PersonDraft?>(null)
    var peopleFocus by mutableStateOf<String?>(null)
    var translate by mutableStateOf<TranslateInfo?>(null)
    var returnTo by mutableStateOf<org.readium.r2.shared.publication.Locator?>(null)
    var feedPull by mutableStateOf<FeedPull?>(null)
    var coach by mutableStateOf(false)
    var chapterPage by mutableIntStateOf(0)
    var chapterPages by mutableIntStateOf(0)
    var bookPage by mutableIntStateOf(0)
    var bookPages by mutableIntStateOf(0)
    var minutesLeft by mutableStateOf<Int?>(null)
    var currentHref by mutableStateOf<String?>(null)
    var nameMark by mutableStateOf<com.flerty.wikiflerty.data.Bookmark?>(null)

    fun flagInfo() = FlagInfo(chapter, chapterPage, chapterPages, bookPage, bookPages, progression, minutesLeft)
    var topPanelBottom = 0f
    var bottomPanelTop = Float.MAX_VALUE

    fun hideAll() {
        sheet = null
        panelsVisible = false
    }

    fun openEditor(draft: NoteDraft) {
        editor = draft
        panelsVisible = false
        sheet = ReaderSheet.NoteEditor
    }
}

data class TocItem(val title: String, val depth: Int, val link: Link)

data class ChapterMark(val at: Float, val item: TocItem)

data class SelectionHandles(val start: androidx.compose.ui.geometry.Offset?, val end: androidx.compose.ui.geometry.Offset?, val more: Boolean = false)

@Composable
private fun SelectionHandleLayer(state: ReaderUiState, handleStyle: String) {
    val h = state.handles ?: return
    if (state.sheet != null) return
    val color = Color(state.handleColor)
    val style = HandleStyle.of(handleStyle)
    h.start?.let { HandleDot(it, true, color, state, style) }
    h.end?.let { HandleDot(it, false, color, state, style) }
    val end = h.end
    val last = remember { arrayOf<androidx.compose.ui.geometry.Offset?>(null) }
    if (end != null && h.more) last[0] = end
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenW = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenH = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val pillW = remember { floatArrayOf(with(density) { 104.dp.toPx() }) }
    val pillH = with(density) { 34.dp.toPx() }
    androidx.compose.animation.AnimatedVisibility(
        visible = h.more && end != null,
        enter = fadeIn(Motion.fade(180)) + scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 520f), initialScale = 0.7f),
        exit = fadeOut(Motion.fade(140)) + scaleOut(Motion.tween(160), targetScale = 0.8f),
        modifier = Modifier.offset {
            val p = last[0] ?: androidx.compose.ui.geometry.Offset.Zero
            val floor = (if (state.flagTop > 0f) state.flagTop else screenH - 64.dp.toPx()) - 6.dp.toPx()
            val below = p.y + 26.dp.toPx()
            val y = if (below + pillH <= floor) below else floor - pillH
            val edge = 12.dp.toPx()
            val x = (p.x + 16.dp.toPx() - pillW[0]).coerceIn(edge, maxOf(edge, screenW - pillW[0] - edge))
            androidx.compose.ui.unit.IntOffset(x.roundToInt(), y.roundToInt())
        },
    ) {
        val nextFeedback = com.flerty.wikiflerty.ui.LocalFeedback.current
        SelectionNextBar(color, Modifier.onSizeChanged { pillW[0] = it.width.toFloat() }) {
            nextFeedback?.play(FeedbackSound.Page)
            state.onSelectionNext?.invoke()
        }
    }
}

@Composable
private fun HandleDot(point: androidx.compose.ui.geometry.Offset, start: Boolean, color: Color, state: ReaderUiState, style: HandleStyle) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val box = with(density) { 44.dp.toPx() }
    val dot = with(density) { 20.dp.toPx() }
    val pad = (box - dot) / 2f
    val screen = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val edge = with(density) { 10.dp.toPx() }
    val rawLeft = if (start) point.x - dot - pad else point.x - pad
    val left = if (style == HandleStyle.Drop) rawLeft.coerceIn(edge - pad, screen - edge - pad - dot) else rawLeft
    val top = point.y - with(density) { 2.dp.toPx() }
    val coords = remember { arrayOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    Box(
        Modifier
            .offset { androidx.compose.ui.unit.IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(44.dp)
            .onGloballyPositioned { coords[0] = it }
            .pointerInput(start) {
                fun abs(p: androidx.compose.ui.geometry.Offset) = coords[0]?.localToRoot(p) ?: p
                detectDragGestures(
                    onDragStart = { o -> abs(o).let { state.onHandleDrag?.invoke(0, start, it.x, it.y) } },
                    onDragEnd = { state.onHandleDrag?.invoke(2, start, 0f, 0f) },
                    onDragCancel = { state.onHandleDrag?.invoke(2, start, 0f, 0f) },
                    onDrag = { change, _ ->
                        change.consume()
                        abs(change.position).let { state.onHandleDrag?.invoke(1, start, it.x, it.y) }
                    },
                )
            },
    ) {
        androidx.compose.foundation.Canvas(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .size(20.dp),
        ) {
            drawSelectionHandle(style, start, color, androidx.compose.ui.geometry.Offset(if (start) size.width else 0f, 0f), 1.dp.toPx())
        }
    }
}

private val SelectedSegment get() = AppColors.AccentTintStrong
private val FlagBackground get() = AppColors.Panel.copy(alpha = 0.94f)

object ReaderReveal {
    var run: ((Float, Float, () -> Unit) -> Unit)? = null
}

object TouchShields {
    private val rects = java.util.concurrent.ConcurrentHashMap<Any, androidx.compose.ui.geometry.Rect>()
    fun put(key: Any, rect: androidx.compose.ui.geometry.Rect) { rects[key] = rect }
    fun remove(key: Any) { rects.remove(key) }
    fun hit(x: Float, y: Float): Boolean = rects.values.any { it.contains(androidx.compose.ui.geometry.Offset(x, y)) }
}

private fun Modifier.blockTaps(): Modifier = composed {
    val key = remember { Any() }
    androidx.compose.runtime.DisposableEffect(key) { onDispose { TouchShields.remove(key) } }
    this
        .onGloballyPositioned { c -> if (c.isAttached) TouchShields.put(key, c.boundsInWindow()) }
        .pointerInput(Unit) { detectTapGestures { } }
}

@Composable
fun ReaderOverlay(
    state: ReaderUiState,
    settings: ReaderSettings,
    fonts: List<FontEntry>,
    title: String,
    cover: ImageBitmap?,
    toc: List<TocItem>,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onTocSelect: (TocItem) -> Unit,
    onBack: () -> Unit,
    notes: List<com.flerty.wikiflerty.data.Note>,
    noteActions: NoteActions,
    ai: AiBridge,
    onEdgeTap: (left: Boolean, at: Offset) -> Unit,
    onBrightnessPreview: (Double) -> Unit,
    onMarkFinished: () -> Unit = {},
    tts: TtsActions,
    bookLanguage: String?,
    onInstallVoices: () -> Unit,
    onSeek: (Double) -> Unit = {},
    search: SearchActions,
    onFootnoteGo: (String) -> Unit = {},
    auto: AutoActions,
    bookmarks: List<com.flerty.wikiflerty.data.Bookmark> = emptyList(),
    bookmarkActions: BookmarkActions,
    quoteActions: QuoteActions,
    people: List<com.flerty.wikiflerty.data.BookCharacter> = emptyList(),
    peopleActions: PeopleActions? = null,
    translateActions: TranslateActions? = null,
    onReturn: () -> Unit = {},
    onCoachDone: () -> Unit = {},
) {
    val progressText = progressText(state, settings)
    val feedback = LocalFeedback.current
    val view = LocalView.current

    BackHandler(enabled = state.sheet != null || state.panelsVisible || state.selection != null || state.footnote != null || state.person != null || state.translate != null) {
        val sheet = state.sheet
        when {
            state.footnote != null -> state.footnote = null
            state.translate != null && sheet == null -> translateActions?.onClose?.invoke() ?: run { state.translate = null }
            state.person != null && sheet == null -> state.person = null
            state.selection != null && sheet == null -> noteActions.onSelectionClose()
            sheet == null -> state.panelsVisible = false
            sheet == ReaderSheet.Fonts -> state.sheet = ReaderSheet.Text
            sheet == ReaderSheet.NoteEditor -> state.sheet = if (state.editor?.fromList == true) ReaderSheet.Notes else null
            sheet == ReaderSheet.AiSetup -> state.sheet = if (state.aiSetupFromMenu) ReaderSheet.AiMenu else ReaderSheet.Options
            sheet == ReaderSheet.Heroes -> state.sheet = ReaderSheet.AiChapter
            sheet == ReaderSheet.HeroChat -> state.sheet = ReaderSheet.Heroes
            sheet == ReaderSheet.AiHistory -> state.sheet = ReaderSheet.AiChapter
            sheet == ReaderSheet.AiChat && state.chatFromHistory -> state.sheet = ReaderSheet.AiHistory
            sheet.depth == 0 -> state.sheet = null
            else -> state.sheet = ReaderSheet.Options
        }
        feedback?.play(FeedbackSound.Close, view)
    }

    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        if (settings.brightnessSwipe && !settings.scroll && state.sheet == null && state.selection == null) {
            BrightnessStrip(
                settings = settings,
                state = state,
                onEdgeTap = { at -> onEdgeTap(true, at) },
                onPreview = onBrightnessPreview,
                onCommit = { value -> onSettingsChange { it.copy(brightness = value) } },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        if (state.sheet != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        feedback?.play(FeedbackSound.Close, view)
                        state.sheet = null
                    },
            )
        }


        if (settings.showFlag) {
            val flagAlpha by animateFloatAsState(if (state.feedPull?.dir == 1) 0f else 1f, Motion.tween(160), label = "flag-hide")
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .onGloballyPositioned { c ->
                        state.flagTop = c.positionInRoot().y
                        state.flagHeight = c.size.height
                    }
                    .graphicsLayer { alpha = flagAlpha },
            ) {
                ProgressFlag(
                    info = state.flagInfo(),
                    settings = settings,
                    onClick = { state.sheet = ReaderSheet.Flag },
                    modifier = Modifier.align(
                        when (settings.flagAlign) {
                            "left" -> Alignment.CenterStart
                            "right" -> Alignment.CenterEnd
                            else -> Alignment.Center
                        },
                    ),
                )
            }
        }

        val lastPull = remember { arrayOf(FeedPull(1, 0f, true)) }
        state.feedPull?.let { lastPull[0] = it }
        AnimatedVisibility(
            visible = state.feedPull != null && state.sheet == null,
            enter = fadeIn(Motion.fade(140)) + scaleIn(Motion.tween(220), initialScale = 0.6f),
            exit = fadeOut(Motion.fade(160)) + scaleOut(Motion.tween(200), targetScale = 0.7f),
            modifier = Modifier
                .align(if (lastPull[0].dir > 0) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(top = 6.dp, bottom = 4.dp),
        ) {
            FeedBadge(lastPull[0], settings)
        }

        LaunchedEffect(state.ttsActive) {
            if (!state.ttsActive) state.ttsCollapsed = false
        }
        AnimatedVisibility(
            visible = state.ttsActive && !state.ttsCollapsed && state.sheet == null && !state.panelsVisible,
            enter = fadeIn(Motion.fade(220)) + slideInVertically(Motion.tween(260)) { it / 2 },
            exit = fadeOut(Motion.fade(180)) + slideOutVertically(Motion.tween(220)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = PanelGap),
        ) {
            TtsBar(state.ttsPlaying, settings.ttsSpeed, tts, onHide = { state.ttsCollapsed = true })
        }
        AnimatedVisibility(
            visible = state.ttsActive && state.ttsCollapsed && state.sheet == null && !state.panelsVisible,
            enter = fadeIn(Motion.fade(200)) + slideInHorizontally(Motion.tween(260)) { -it },
            exit = fadeOut(Motion.fade(160)) + slideOutHorizontally(Motion.tween(220)) { -it },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = MiniBottom),
        ) {
            TtsMini(state.ttsPlaying) { state.ttsCollapsed = false }
        }

        val problem = state.ttsProblem
        AnimatedVisibility(
            visible = problem != null && state.sheet == null,
            enter = fadeIn(Motion.fade(220)),
            exit = fadeOut(Motion.fade(160)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = PanelGap + 80.dp),
        ) {
            val last = remember { arrayOf<com.flerty.wikiflerty.tts.ReadAloud.Problem?>(null) }
            problem?.let { last[0] = it }
            last[0]?.let { TtsProblemPrompt(it, onInstall = { state.ttsProblem = null; onInstallVoices() }, onDismiss = { state.ttsProblem = null }, onVoices = { state.ttsProblem = null; state.sheet = ReaderSheet.Listen }) }
        }

        AnimatedVisibility(
            visible = state.askFinished && state.sheet == null && !state.panelsVisible,
            enter = fadeIn(Motion.fade(260)) + slideInVertically(Motion.tween(300)) { it / 2 },
            exit = fadeOut(Motion.fade(180)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = FlagBottom + 44.dp, start = 16.dp, end = 16.dp),
        ) {
            FinishedPrompt(onMark = onMarkFinished, onDismiss = { state.askFinished = false })
        }


        AnimatedVisibility(
            visible = state.brightnessHint != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            BrightnessHint(state.brightnessHint ?: 0)
        }

        AnimatedVisibility(
            visible = state.panelsVisible,
            enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it },
            exit = fadeOut(Motion.fade(200)) + slideOutVertically(Motion.tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it },
            modifier = Modifier.align(Alignment.TopCenter).onGloballyPositioned { state.topPanelBottom = it.boundsInWindow().bottom },
        ) {
            TopPanel(
                title = title,
                cover = cover,
                chapter = state.chapter,
                progression = state.progression,
                progressText = progressText,
                totalPositions = state.totalPositions,
                marks = state.chapterMarks,
                onBack = onBack,
                onListen = { state.sheet = ReaderSheet.Listen },
                onSearch = { state.sheet = ReaderSheet.Search },
                onToc = { state.sheet = ReaderSheet.Toc },
                onSeek = onSeek,
                onChapter = { dir ->
                    val p = state.progression.toFloat()
                    val marks = state.chapterMarks
                    val target = if (dir > 0) {
                        marks.firstOrNull { it.at > p + 0.0005f }
                    } else {
                        val current = marks.lastOrNull { it.at <= p + 0.0005f }
                        if (current != null && p - current.at > 0.004f) current else marks.lastOrNull { it.at < (current?.at ?: p) - 0.0001f }
                    }
                    target?.let { onTocSelect(it.item) }
                },
                bookmarked = state.bookmarked,
                onBookmark = bookmarkActions.onToggle,
                onJump = { state.sheet = ReaderSheet.Jump },
            )
        }

        AnimatedVisibility(
            visible = state.panelsVisible && state.sheet == null,
            enter = fadeIn(Motion.fade(220)) + slideInVertically(Motion.tween(260)) { it / 2 },
            exit = fadeOut(Motion.fade(180)) + slideOutVertically(Motion.tween(220)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).onGloballyPositioned { state.bottomPanelTop = it.boundsInWindow().top },
        ) {
            BottomPanel(
                buttons = settings.quickButtons.mapNotNull { QuickButton.of(it) },
                onButton = { button ->
                    when (button) {
                        QuickButton.Toc -> state.sheet = ReaderSheet.Toc
                        QuickButton.Font -> state.sheet = ReaderSheet.Text
                        QuickButton.Theme -> state.sheet = ReaderSheet.Theme
                        QuickButton.Scroll -> state.sheet = ReaderSheet.Scroll
                        QuickButton.Screen -> state.sheet = ReaderSheet.Screen
                        QuickButton.Notes -> state.sheet = ReaderSheet.Notes
                        QuickButton.Bookmarks -> state.sheet = ReaderSheet.Bookmarks
                        QuickButton.People -> {
                            state.peopleFocus = null
                            state.sheet = ReaderSheet.People
                        }
                        QuickButton.Listen -> state.sheet = ReaderSheet.Listen
                        QuickButton.Search -> state.sheet = ReaderSheet.Search
                        QuickButton.Auto -> auto.onStart()
                        QuickButton.AiRecap, QuickButton.AiQuiz, QuickButton.AiTeacher -> if (ai.manager.current() == null) {
                            state.sheet = ReaderSheet.AiChapter
                        } else {
                            ai.onChapter(
                                when (button) {
                                    QuickButton.AiRecap -> AiAction.Recap
                                    QuickButton.AiQuiz -> AiAction.Quiz
                                    else -> AiAction.Teacher
                                },
                            )
                            state.sheet = ReaderSheet.AiChat
                        }
                        QuickButton.AiHeroes -> state.sheet = if (ai.manager.current() == null) ReaderSheet.AiChapter else ReaderSheet.Heroes
                        QuickButton.AiHistory -> state.sheet = ReaderSheet.AiHistory
                    }
                },
                onOptions = { state.sheet = ReaderSheet.Options },
            )
        }

        AnimatedVisibility(
            visible = state.sheet != null,
            enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(280)) { it / 3 },
            exit = fadeOut(Motion.fade(160)) + slideOutVertically(Motion.tween(220)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val last = remember { arrayOf(ReaderSheet.Options) }
            state.sheet?.let { last[0] = it }
            SheetFrame {
                AnimatedContent(
                    targetState = last[0],
                    contentKey = { if (it in NavSheets) ReaderSheet.Toc else it },
                    transitionSpec = {
                        val deeper = targetState.depth > initialState.depth
                        val dir = if (deeper) 1 else -1
                        (slideInHorizontally(Motion.tween(260)) { dir * it / 5 } + fadeIn(Motion.fade(220)))
                            .togetherWith(slideOutHorizontally(Motion.tween(220)) { -dir * it / 5 } + fadeOut(Motion.fade(160)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "sheet",
                ) { sheet ->
                    SheetContent(
                        tts = tts,
                        ttsActive = state.ttsActive,
                        ttsPlaying = state.ttsPlaying,
                        ttsSleep = state.ttsSleep,
                        bookLanguage = bookLanguage,
                        search = search,
                        auto = auto,
                        jump = JumpInfo(state.progression, state.position, state.totalPositions, onSeek),
                        flag = state.flagInfo(),
                        currentHref = state.currentHref,
                        chapter = state.chapter,
                        sheet = sheet,
                        settings = settings,
                        fonts = fonts,
                        toc = toc,
                        notes = notes,
                        bookmarks = bookmarks,
                        bookmarkActions = bookmarkActions,
                        people = people,
                        peopleFocus = state.peopleFocus,
                        onPerson = { state.personDraft = it },
                        quote = state.quote,
                        quoteActions = quoteActions,
                        editor = state.editor,
                        noteActions = noteActions,
                        ai = ai,
                        aiSetupFromMenu = state.aiSetupFromMenu,
                        chatFromHistory = state.chatFromHistory,
                        onSettingsChange = onSettingsChange,
                        onOpen = {
                            if (it == ReaderSheet.AiSetup) state.aiSetupFromMenu = state.sheet == ReaderSheet.AiMenu
                            state.sheet = it
                        },
                        onClose = { state.sheet = null },
                        onEditNote = { note ->
                            state.editor = NoteDraft(note.id, note.locator, note.text, note.note, note.color, fromList = true)
                            state.sheet = ReaderSheet.NoteEditor
                        },
                        onTocSelect = {
                            state.hideAll()
                            onTocSelect(it)
                        },
                    )
                }
            }
        }
    }

    val searchState = search.state
    AnimatedVisibility(
        visible = searchState.current >= 0 && searchState.results.isNotEmpty() && state.sheet == null &&
            state.selection == null && state.footnote == null && !state.panelsVisible && !state.autoActive,
        enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(240)) { it / 2 },
        exit = fadeOut(Motion.fade(160)),
        modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars).padding(bottom = 56.dp),
    ) {
        SearchNavBar(search, onReopen = { state.sheet = ReaderSheet.Search }, modifier = Modifier)
    }

    AnimatedVisibility(
        visible = state.returnTo != null && state.sheet == null && state.selection == null && state.footnote == null &&
            state.translate == null && state.person == null && !state.panelsVisible,
        enter = fadeIn(Motion.fade(200)) + scaleIn(Motion.tween(260), initialScale = 0.85f),
        exit = fadeOut(Motion.fade(160)) + scaleOut(Motion.tween(200), targetScale = 0.9f),
        modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.systemBars).padding(
            end = 16.dp,
            bottom = if (settings.showFlag && state.flagHeight > 0) {
                maxOf(MiniBottom, with(androidx.compose.ui.platform.LocalDensity.current) { state.flagHeight.toDp() } + 26.dp)
            } else {
                MiniBottom
            },
        ),
    ) {
        ReturnPill(onReturn = onReturn, onDismiss = { state.returnTo = null })
    }

    AnimatedVisibility(
        visible = state.autoActive && !state.autoCollapsed && state.sheet == null && state.footnote == null && state.selection == null,
        enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(240)) { it / 2 },
        exit = fadeOut(Motion.fade(160)),
        modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars).padding(bottom = 56.dp),
    ) {
        AutoBar(
            paused = state.autoPaused,
            progress = if (settings.scroll) null else state.autoProgress,
            label = AutoScroll.label(settings.autoSpeed, settings.scroll),
            onToggle = auto.onToggle,
            onSlower = { onSettingsChange { it.copy(autoSpeed = (it.autoSpeed - 1).coerceAtLeast(1)) } },
            onFaster = { onSettingsChange { it.copy(autoSpeed = (it.autoSpeed + 1).coerceAtMost(10)) } },
            onStop = auto.onStop,
            onCollapse = { state.autoCollapsed = true },
        )
    }

    AnimatedVisibility(
        visible = state.autoActive && state.autoCollapsed && state.sheet == null && state.footnote == null && state.selection == null,
        enter = fadeIn(Motion.fade(200)) + slideInHorizontally(Motion.tween(260)) { -it },
        exit = fadeOut(Motion.fade(160)) + slideOutHorizontally(Motion.tween(220)) { -it },
        modifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.systemBars).padding(start = 16.dp, bottom = MiniBottom),
    ) {
        AutoMini(
            paused = state.autoPaused,
            progress = if (settings.scroll) null else state.autoProgress,
            onExpand = { state.autoCollapsed = false },
        )
    }

    val popupLift = if (settings.showFlag && state.flagHeight > 0) {
        with(androidx.compose.ui.platform.LocalDensity.current) { state.flagHeight.toDp() } + 22.dp
    } else {
        16.dp
    }
    AnimatedVisibility(
        visible = state.footnote != null && state.sheet == null,
        enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(260)) { it / 3 },
        exit = fadeOut(Motion.fade(160)) + slideOutVertically(Motion.tween(200)) { it / 3 },
        modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars).padding(bottom = popupLift),
    ) {
        val last = remember { arrayOf<FootnoteInfo?>(null) }
        state.footnote?.let { last[0] = it }
        val note = last[0]
        if (note != null) {
            FootnotePopup(
                note = note,
                onGo = {
                    state.footnote = null
                    onFootnoteGo(note.url)
                },
                onClose = { state.footnote = null },
                modifier = Modifier.blockTaps(),
            )
        }
    }

    AnimatedVisibility(
        visible = state.person != null && state.sheet == null,
        enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(260)) { it / 3 },
        exit = fadeOut(Motion.fade(160)) + slideOutVertically(Motion.tween(200)) { it / 3 },
        modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars).padding(bottom = popupLift),
    ) {
        val last = remember { arrayOf<com.flerty.wikiflerty.data.BookCharacter?>(null) }
        state.person?.let { last[0] = it }
        val person = last[0]
        if (person != null) {
            PersonPopup(
                person = person,
                onEdit = { state.personDraft = PersonDraft(person.id, person.name, person.note) },
                onClose = { state.person = null },
                modifier = Modifier.blockTaps(),
            )
        }
    }

    AnimatedVisibility(
        visible = state.translate != null && state.sheet == null && translateActions != null,
        enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(260)) { it / 3 },
        exit = fadeOut(Motion.fade(160)) + slideOutVertically(Motion.tween(200)) { it / 3 },
        modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars).padding(bottom = popupLift),
    ) {
        val last = remember { arrayOf<TranslateInfo?>(null) }
        state.translate?.let { last[0] = it }
        val info = last[0]
        if (info != null && translateActions != null) {
            TranslatePopup(info, translateActions, Modifier.blockTaps())
        }
    }

    state.nameMark?.let { mark ->
        androidx.compose.runtime.key(mark.id) {
            com.flerty.wikiflerty.ui.TextInputSheet(
                "Закладка",
                mark.label,
                "Название закладки",
                onDismiss = { state.nameMark = null },
                icon = AppIcons.Bookmark,
                maxLength = 80,
            ) { text -> bookmarkActions.onName(mark.id, text.trim()) }
        }
    }

    val draft = state.personDraft
    if (draft != null && peopleActions != null) {
        androidx.compose.runtime.key(draft) {
            PersonSheet(draft, onDismiss = { state.personDraft = null }, actions = peopleActions)
        }
    }

    SelectionHandleLayer(state, settings.handleStyle)

    val selection = state.selection
    AnimatedVisibility(
        visible = selection != null && state.sheet == null,
        enter = fadeIn(Motion.fade(220)),
        exit = fadeOut(Motion.fade(160)),
    ) {
        val last = remember { arrayOf<SelectionInfo?>(null) }
        selection?.let { last[0] = it }
        val info = last[0] ?: return@AnimatedVisibility
        val kept = remember { arrayOf<SelectionHandles?>(null) }
        if (selection != null) state.handles?.let { kept[0] = it }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val menuHeight = with(density) { 58.dp.toPx() }
            val gap = with(density) { 12.dp.toPx() }
            val minY = with(density) { 24.dp.toPx() }
            val maxY = with(density) { maxHeight.toPx() } - menuHeight - with(density) { 72.dp.toPx() }
            val handles = kept[0]
            val line = with(density) { 30.dp.toPx() }
            val drop = with(density) { 26.dp.toPx() }
            val hs = handles?.start
            val he = handles?.end
            val top = when {
                hs != null -> hs.y - line
                he != null -> he.y - line
                else -> info.top
            }
            val bottom = when {
                he != null -> he.y + drop
                hs != null -> hs.y + drop
                else -> info.bottom
            }
            val above = top - menuHeight - gap
            val below = bottom + gap
            val y = when {
                hs == null && he != null && below <= maxY -> below
                above >= minY -> above
                below <= maxY -> below
                else -> maxY
            }.coerceIn(minY, maxOf(minY, maxY))
            val layout = remember(settings.selectionButtons, settings.selectionMore) { settings.selectionLayout() }
            val menuY = remember { androidx.compose.animation.core.Animatable(y) }
            val menuFeedback = com.flerty.wikiflerty.ui.LocalFeedback.current
            var placed by remember { mutableStateOf(false) }
            val known = handles != null
            LaunchedEffect(y, known) {
                if (!placed) {
                    if (!known) kotlinx.coroutines.delay(180)
                    menuY.snapTo(y)
                    placed = true
                    menuFeedback?.play(FeedbackSound.Pop)
                } else if (selection != null) {
                    menuY.animateTo(y, androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 420f))
                }
            }
            val shown by androidx.compose.animation.core.animateFloatAsState(if (placed) 1f else 0f, Motion.fade(160), label = "menu-in")
            SelectionMenu(
                markerColor = settings.markerColor,
                actions = noteActions,
                main = layout.first,
                more = layout.second,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { androidx.compose.ui.unit.IntOffset(0, menuY.value.roundToInt()) }
                    .graphicsLayer {
                        alpha = shown
                        val s = 0.96f + 0.04f * shown
                        scaleX = s
                        scaleY = s
                    },
            )
        }
    }
    }

    AnimatedVisibility(
        visible = state.coach,
        enter = fadeIn(Motion.fade(300)),
        exit = fadeOut(Motion.fade(220)),
    ) {
        ReaderCoach(settings.scroll, settings.leftTapForward) { onCoachDone() }
    }
}

@Composable
private fun ReturnPill(onReturn: () -> Unit, onDismiss: () -> Unit) {
    val shape = appCorner(22)
    Row(
        Modifier.height(44.dp).panelSurface(shape, elevation = 12.dp).padding(start = 4.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(38.dp)
                .liveClick(appCorner(19), sound = FeedbackSound.Open, onClick = onReturn)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(AppIcons.Back, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            Text("К тексту", color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Box(
            Modifier.size(36.dp).liveClick(CircleShape, sound = FeedbackSound.Close, pressedScale = 0.85f, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Close, "Скрыть", tint = AppColors.Muted, modifier = Modifier.size(15.dp)) }
    }
}

private fun progressText(state: ReaderUiState, settings: ReaderSettings): String {
    val position = state.position
    if (settings.progressInPages && state.bookPages > 0) return "${state.bookPage}/${state.bookPages}"
    return if (settings.progressInPages && position != null && state.totalPositions > 0) {
        "$position/${state.totalPositions}"
    } else {
        "${(state.progression * 100).roundToInt().coerceIn(0, 100)}%"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProgressFlag(info: FlagInfo, settings: ReaderSettings, onClick: (() -> Unit)?, modifier: Modifier) {
    val k = settings.flagSize.toFloat().coerceIn(0.8f, 1.4f)
    val shape = appCorner(12)
    val bg = settings.flagBackground
    val shadow = if (bg) null else androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(0f, 1f), 3f)
    val style = androidx.compose.ui.text.TextStyle(fontSize = (12 * k).sp, shadow = shadow)
    val context = androidx.compose.ui.platform.LocalContext.current
    var clock by remember { mutableStateOf("") }
    if (settings.flagClock) {
        LaunchedEffect(Unit) {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            while (true) {
                clock = java.time.LocalTime.now().format(fmt)
                kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }
    var battery by remember { mutableIntStateOf(-1) }
    if (settings.flagBattery) {
        androidx.compose.runtime.DisposableEffect(Unit) {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                    val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
                    if (level >= 0 && scale > 0) battery = level * 100 / scale
                }
            }
            val sticky = context.registerReceiver(receiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            receiver.onReceive(context, sticky)
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        }
    }
    val items = buildList {
        val book = settings.flagBookPages && info.bookPages > 0
        val chap = settings.flagChapterPages && info.chapterPages > 0
        when {
            book && chap -> add("${info.bookPage}/${info.bookPages} (${info.chapterPage}/${info.chapterPages})")
            book -> add("${info.bookPage}/${info.bookPages}")
            chap -> add("${info.chapterPage}/${info.chapterPages}")
        }
        if (settings.flagPercent) add("${(info.progression * 100).roundToInt().coerceIn(0, 100)}%")
        if (settings.flagTimeLeft) info.minutesLeft?.let { add(if (it < 1) "<1 мин" else "≈$it мин") }
        if (settings.flagClock && clock.isNotEmpty()) add(clock)
    }
    val showChapter = settings.flagChapter && !info.chapter.isNullOrBlank()
    val showBattery = settings.flagBattery && battery >= 0
    if (!showChapter && items.isEmpty() && !showBattery) return
    val surface = Modifier
        .clip(shape)
        .then(if (bg) Modifier.background(FlagBackground).border(1.dp, AppColors.PanelBorder, shape) else Modifier)
        .then(if (onClick != null) Modifier.liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.94f, onClick = onClick) else Modifier)
    val dot = @Composable { Box(Modifier.size((3 * k).dp).clip(CircleShape).background(AppColors.Faint)) }
    val stats: @Composable (Boolean) -> Unit = { lead ->
        items.forEachIndexed { i, text ->
            if (i > 0 || lead) dot()
            Text(text, color = AppColors.Text, style = style, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
        if (showBattery) {
            if (lead || items.isNotEmpty()) dot()
            val tint = AppColors.Text
            val low = AppColors.Bad
            androidx.compose.foundation.Canvas(Modifier.size(width = (16 * k).dp, height = (9 * k).dp)) {
                val sw = 1.2f * density
                val bodyW = size.width - 2.5f * density
                drawRoundRect(tint, size = androidx.compose.ui.geometry.Size(bodyW, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * density), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                drawRect(tint, topLeft = androidx.compose.ui.geometry.Offset(bodyW + 0.5f * density, size.height * 0.3f), size = androidx.compose.ui.geometry.Size(1.8f * density, size.height * 0.4f))
                val fillW = (bodyW - 2 * sw - 2f) * (battery / 100f)
                drawRect(if (battery <= 15) low else tint, topLeft = androidx.compose.ui.geometry.Offset(sw + 1f, sw + 1f), size = androidx.compose.ui.geometry.Size(fillW.coerceAtLeast(0f), size.height - 2 * sw - 2f))
            }
            Text("$battery%", color = AppColors.Text, style = style, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
    val chapterText = @Composable { size: Float, mod: Modifier ->
        Text(
            info.chapter.orEmpty(),
            color = AppColors.Muted,
            style = style.copy(fontSize = (size * k).sp),
            maxLines = 1,
            softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = mod.basicMarquee(
                iterations = Int.MAX_VALUE,
                repeatDelayMillis = 1800,
                initialDelayMillis = 1800,
                spacing = MarqueeSpacing(28.dp),
                velocity = 22.dp,
            ),
        )
    }
    if (showChapter && (items.isNotEmpty() || showBattery)) {
        Column(
            modifier = modifier
                .then(surface)
                .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                .padding(horizontal = (12 * k).dp, vertical = (4 * k).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            chapterText(10.5f, Modifier.fillMaxWidth())
            Box(
                Modifier
                    .padding(vertical = (3 * k).dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(Color.Transparent, AppColors.PanelBorder, AppColors.PanelBorder, Color.Transparent),
                        ),
                    ),
            )
            Row(
                Modifier.height((16 * k).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((7 * k).dp),
            ) { stats(false) }
        }
        return
    }
    Row(
        modifier = modifier
            .height((24 * k).dp)
            .then(surface)
            .padding(horizontal = (10 * k).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((7 * k).dp),
    ) {
        if (showChapter) chapterText(12f, Modifier.weight(1f, fill = false))
        stats(showChapter)
    }
}

@Composable
private fun FlagOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit, info: FlagInfo) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(appCorner(16))
            .background(AppColors.Background)
            .border(1.dp, AppColors.PanelBorder, appCorner(16))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = when (settings.flagAlign) {
            "left" -> Alignment.CenterStart
            "right" -> Alignment.CenterEnd
            else -> Alignment.Center
        },
    ) {
        if (settings.showFlag) {
            ProgressFlag(info, settings, onClick = null, modifier = Modifier)
        } else {
            Text("Флажок скрыт", color = AppColors.Faint, fontSize = 13.sp)
        }
    }
    SwitchRow("Флажок внизу", settings.showFlag, icon = AppIcons.Flag) { v -> onSettingsChange { it.copy(showFlag = v) } }
    Group("Что показывать", icon = AppIcons.Sliders) {
        Column(Modifier.clip(appCorner(16)).background(AppColors.Field).padding(horizontal = 14.dp)) {
            val on = settings.showFlag
            SwitchRow("Название главы", settings.flagChapter, enabled = on, icon = AppIcons.Toc) { v -> onSettingsChange { it.copy(flagChapter = v) } }
            SwitchRow("Страница в главе", settings.flagChapterPages, enabled = on, icon = AppIcons.Pages) { v -> onSettingsChange { it.copy(flagChapterPages = v) } }
            SwitchRow("Страница в книге", settings.flagBookPages, enabled = on, icon = AppIcons.Book) { v -> onSettingsChange { it.copy(flagBookPages = v) } }
            SwitchRow("Проценты", settings.flagPercent, enabled = on, icon = AppIcons.Percent) { v -> onSettingsChange { it.copy(flagPercent = v) } }
            SwitchRow("Время до конца главы", settings.flagTimeLeft, enabled = on, icon = AppIcons.Target) { v -> onSettingsChange { it.copy(flagTimeLeft = v) } }
            SwitchRow("Часы", settings.flagClock, enabled = on, icon = AppIcons.Clock) { v -> onSettingsChange { it.copy(flagClock = v) } }
            SwitchRow("Заряд", settings.flagBattery, enabled = on, icon = AppIcons.Bulb) { v -> onSettingsChange { it.copy(flagBattery = v) } }
        }
    }
    Segmented(
        "Положение",
        listOf("Слева", "Центр", "Справа"),
        when (settings.flagAlign) {
            "left" -> 0
            "right" -> 2
            else -> 1
        },
        icon = AppIcons.Margins,
    ) { i -> onSettingsChange { it.copy(flagAlign = listOf("left", "center", "right")[i]) } }
    StepSlider(
        label = "Размер",
        icon = AppIcons.TextSize,
        value = settings.flagSize,
        range = 0.8..1.4,
        step = 0.1,
        enabled = settings.showFlag,
        format = { "${(it * 100).roundToInt()}%" },
        onCommit = { v -> onSettingsChange { it.copy(flagSize = v) } },
    )
    SwitchRow("Подложка", settings.flagBackground, enabled = settings.showFlag, icon = AppIcons.Panel) { v -> onSettingsChange { it.copy(flagBackground = v) } }
}

@Composable
internal fun TopPanel(
    title: String,
    cover: ImageBitmap?,
    chapter: String?,
    progression: Double,
    progressText: String,
    totalPositions: Int,
    marks: List<ChapterMark>,
    onBack: () -> Unit,
    onListen: () -> Unit,
    onSearch: () -> Unit,
    onToc: () -> Unit,
    onSeek: (Double) -> Unit,
    onChapter: (Int) -> Unit,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onJump: () -> Unit,
) {
    var preview by remember { mutableStateOf<Double?>(null) }
    var menu by remember { mutableStateOf(false) }
    val r = (22 * com.flerty.wikiflerty.ui.AppStyle.current.corners).dp
    val shape = RoundedCornerShape(bottomStart = r, bottomEnd = r)
    val page = { f: Double -> if (totalPositions > 0) "${(f * (totalPositions - 1)).roundToInt() + 1}/$totalPositions" else "${(f * 100).roundToInt()}%" }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .panelSurface(shape)
                .blockTaps()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(58.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(AppIcons.Back, "Назад в библиотеку", sound = FeedbackSound.Close, onClick = onBack)
                Row(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .liveClick(appCorner(12), sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onToc)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 32.dp, height = 46.dp)
                            .clip(appCorner(5))
                            .background(AppColors.Line)
                            .border(1.dp, Color(0x33FFFFFF), appCorner(5)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cover != null) {
                            Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(title.take(1), color = AppColors.Text, fontFamily = FontFamily.Serif, fontSize = 16.sp)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        MarqueeText(title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        val sub = listOfNotNull(chapter?.takeIf { it.isNotBlank() }, progressText.takeIf { it.isNotBlank() }).joinToString(" · ")
                        if (sub.isNotEmpty()) {
                            Text(sub, color = AppColors.Muted, fontSize = 11.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
                BookmarkButton(bookmarked, onBookmark)
                RoundIconButton(AppIcons.Search, "Поиск по книге", sound = FeedbackSound.Open, onClick = onSearch)
                Box {
                    RoundIconButton(AppIcons.More, "Ещё", sound = FeedbackSound.Open) { menu = true }
                    androidx.compose.material3.DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        shape = appCorner(16),
                        containerColor = AppColors.Panel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.PanelBorder),
                    ) {
                        listOf(
                            Triple(AppIcons.Toc, "Оглавление", onToc),
                            Triple(AppIcons.Pages, "Перейти к странице", onJump),
                            Triple(AppIcons.Headphones, "Слушать", onListen),
                        ).forEach { (icon, label, action) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label, color = AppColors.Text, fontSize = 14.sp) },
                                leadingIcon = { Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    menu = false
                                    action()
                                },
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.size(34.dp).liveClick(CircleShape, sound = FeedbackSound.Page, pressedScale = 0.85f) { onChapter(-1) },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.SkipPrev, "Прошлая глава", tint = AppColors.Muted, modifier = Modifier.size(17.dp)) }
                SeekBar(
                    progression = progression,
                    onPreview = { preview = it },
                    onSeek = onSeek,
                    marks = marks.filter { it.item.depth == 0 }.map { it.at },
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(34.dp).liveClick(CircleShape, sound = FeedbackSound.Page, pressedScale = 0.85f) { onChapter(1) },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.SkipNext, "Следующая глава", tint = AppColors.Muted, modifier = Modifier.size(17.dp)) }
                Text(
                    "${((preview ?: progression) * 100).roundToInt().coerceIn(0, 100)}%",
                    color = if (preview != null) AppColors.Accent else AppColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = if (preview != null) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier
                        .widthIn(min = 40.dp)
                        .liveClick(appCorner(8), sound = FeedbackSound.Open, pressedScale = 0.9f, onClick = onJump)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
        val shownPreview = remember { doubleArrayOf(0.0) }
        preview?.let { shownPreview[0] = it }
        AnimatedVisibility(
            visible = preview != null,
            enter = fadeIn(Motion.fade(140)) + scaleIn(Motion.tween(180), initialScale = 0.9f),
            exit = fadeOut(Motion.fade(160)),
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = 48.dp),
        ) {
            val f = shownPreview[0]
            val name = marks.lastOrNull { it.at <= f.toFloat() + 0.0001f }?.item?.title
            Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .panelSurface(appCorner(14), elevation = 10.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!name.isNullOrBlank()) {
                    Text(name, color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text("стр. ${page(f)}", color = AppColors.Accent, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    Text(
        text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            repeatDelayMillis = 1800,
            initialDelayMillis = 1800,
            spacing = MarqueeSpacing(36.dp),
            velocity = 26.dp,
        ),
    )
}

@Composable
private fun SeekBar(
    progression: Double,
    onPreview: (Double?) -> Unit,
    onSeek: (Double) -> Unit,
    modifier: Modifier,
    marks: List<Float> = emptyList(),
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    var pending by remember { mutableStateOf<Float?>(null) }
    val preview by rememberUpdatedState(onPreview)
    val seekTo by rememberUpdatedState(onSeek)
    val seekFeedback = com.flerty.wikiflerty.ui.LocalFeedback.current
    val shown = remember { androidx.compose.animation.core.Animatable(progression.toFloat().coerceIn(0f, 1f)) }
    val target = progression.toFloat().coerceIn(0f, 1f)
    LaunchedEffect(target, pending, drag) {
        val wanted = pending
        when {
            drag != null -> Unit
            wanted != null -> {
                if (kotlin.math.abs(target - wanted) <= 0.025f) {
                    pending = null
                    shown.snapTo(wanted)
                    shown.animateTo(target, com.flerty.wikiflerty.ui.Motion.tween(180))
                } else {
                    kotlinx.coroutines.delay(2600)
                    pending = null
                    shown.snapTo(wanted)
                    shown.animateTo(target, com.flerty.wikiflerty.ui.Motion.tween(260))
                }
            }
            else -> shown.animateTo(target, com.flerty.wikiflerty.ui.Motion.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    val seek: (Double) -> Unit = { f ->
        pending = f.toFloat()
        seekTo(f)
    }
    val fraction = drag ?: pending ?: shown.value
    val active = drag != null
    val thumb by androidx.compose.animation.core.animateDpAsState(if (active) 16.dp else 11.dp, label = "thumb")
    BoxWithConstraints(
        modifier
            .height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val f = (o.x / size.width).coerceIn(0f, 1f)
                    seek(f.toDouble())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        drag = (o.x / size.width).coerceIn(0f, 1f)
                        preview(drag?.toDouble())
                    },
                    onDragEnd = {
                        drag?.let { seek(it.toDouble()) }
                        drag = null
                        preview(null)
                    },
                    onDragCancel = {
                        drag = null
                        preview(null)
                    },
                ) { change, _ ->
                    change.consume()
                    val before = drag
                    val now = (change.position.x / size.width).coerceIn(0f, 1f)
                    drag = now
                    if (before != null && (before * 20f).toInt() != (now * 20f).toInt()) seekFeedback?.play(FeedbackSound.Tick, rate = 0.85f + now * 0.4f)
                    preview(drag?.toDouble())
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val width = maxWidth
        if (marks.isNotEmpty()) {
            val tick = AppColors.Faint
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                marks.forEach { m ->
                    if (m > 0.004f && m < 0.996f) {
                        val x = size.width * m
                        drawLine(tick, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1.2f * density)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(if (active) 6.dp else 4.dp).clip(appCorner(3)).background(AppColors.Line)) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(appCorner(3))
                        .background(Brush.horizontalGradient(listOf(AppColors.AccentDark, AppColors.Accent))),
                )
            }
        }
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset((width * fraction - thumb / 2).coerceIn(0.dp, maxOf(0.dp, width - thumb)).roundToPx(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(AppColors.Accent)
                .border(2.dp, Color(0x55FFFFFF), CircleShape),
        )
    }
}

@Composable
internal fun BottomPanel(
    buttons: List<QuickButton>,
    onButton: (QuickButton) -> Unit,
    onOptions: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = PanelGap)
            .widthIn(max = PanelMaxWidth)
            .fillMaxWidth()
            .height(68.dp)
            .panelSurface(appCorner(22))
            .blockTaps()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEach { button ->
            PanelButton(
                label = button.title,
                sound = FeedbackSound.Open,
                onClick = { onButton(button) },
                modifier = Modifier.weight(1f),
            ) { QuickIcon(button, AppColors.Text) }
        }
        Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(34.dp).background(AppColors.Line))
        PanelButton("Опции", sound = FeedbackSound.Open, onClick = onOptions, modifier = Modifier.weight(1f)) {
            Icon(AppIcons.Options, contentDescription = null, tint = AppColors.Text, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PanelButton(
    label: String,
    sound: FeedbackSound,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: @Composable () -> Unit,
) {
    val shape = appCorner(16)
    Box(
        modifier = modifier
            .height(52.dp)
            .liveClick(shape, sound = sound, pressedScale = 0.88f, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { icon() }
    }
}

@Composable
private fun QuickIcon(button: QuickButton, tint: Color) {
    when (button) {
        QuickButton.Font -> Icon(AppIcons.TextSize, null, tint = tint, modifier = Modifier.size(25.dp))
        else -> Icon(quickIconVector(button), null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

private fun quickIconVector(button: QuickButton): ImageVector = when (button) {
    QuickButton.Toc -> AppIcons.Toc
    QuickButton.Font -> AppIcons.Toc
    QuickButton.Theme -> AppIcons.Theme
    QuickButton.Scroll -> AppIcons.Pages
    QuickButton.Screen -> AppIcons.Screen
    QuickButton.Notes -> AppIcons.Note
    QuickButton.Bookmarks -> AppIcons.Bookmark
    QuickButton.People -> AppIcons.Person
    QuickButton.Listen -> AppIcons.Headphones
    QuickButton.Search -> AppIcons.Search
    QuickButton.Auto -> AppIcons.Play
    QuickButton.AiRecap -> AppIcons.Book
    QuickButton.AiQuiz -> AppIcons.Check
    QuickButton.AiTeacher -> AppIcons.Pencil
    QuickButton.AiHeroes -> AppIcons.Voice
    QuickButton.AiHistory -> AppIcons.Clock
}

@Composable
internal fun RoundIconButton(
    icon: ImageVector,
    description: String,
    filled: Boolean = false,
    sound: FeedbackSound = FeedbackSound.Tap,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .liveClick(CircleShape, sound = sound, pressedScale = 0.88f, onClick = onClick)
            .background(if (filled) AppColors.Line else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = AppColors.Text, modifier = Modifier.size(if (filled) 18.dp else 22.dp))
    }
}

@Composable
private fun BrightnessStrip(
    settings: ReaderSettings,
    state: ReaderUiState,
    onEdgeTap: (Offset) -> Unit,
    onPreview: (Double) -> Unit,
    onCommit: (Double) -> Unit,
    modifier: Modifier,
) {
    val current by rememberUpdatedState(settings.brightness)
    val tap by rememberUpdatedState(onEdgeTap)
    var value by remember { mutableFloatStateOf(0.5f) }
    val origin = remember { floatArrayOf(0f, 0f) }
    Box(
        modifier
            .fillMaxHeight()
            .width(28.dp)
            .onGloballyPositioned { c -> c.positionInWindow().let { origin[0] = it.x; origin[1] = it.y } }
            .pointerInput(Unit) { detectTapGestures(onTap = { p -> tap(Offset(origin[0] + p.x, origin[1] + p.y)) }) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        value = if (current < 0) 0.5f else current.toFloat()
                        state.brightnessHint = (value * 100).roundToInt()
                    },
                    onDragEnd = {
                        state.brightnessHint = null
                        onCommit(value.toDouble())
                    },
                    onDragCancel = { state.brightnessHint = null },
                ) { change, dy ->
                    change.consume()
                    value = (value - dy / (size.height * 0.8f)).coerceIn(0.03f, 1f)
                    state.brightnessHint = (value * 100).roundToInt()
                    onPreview(value.toDouble())
                }
            },
    )
}

@Composable
private fun BrightnessHint(percent: Int) {
    Row(
        Modifier
            .panelSurface(appCorner(20))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(AppIcons.Sun, null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
        Text("$percent%", color = AppColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SheetFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = PanelGap)
            .widthIn(max = PanelMaxWidth)
            .fillMaxWidth()
            .imePadding()
            .height(com.flerty.wikiflerty.ui.sheetHeight())
            .shadow(20.dp, appCorner(28), ambientColor = Color(0x66503010), spotColor = Color(0x66503010))
            .clip(appCorner(28))
            .background(AppColors.Background)
            .blockTaps(),
    ) {
        content()
    }
}

@Composable
private fun SheetContent(
    sheet: ReaderSheet,
    settings: ReaderSettings,
    fonts: List<FontEntry>,
    toc: List<TocItem>,
    notes: List<com.flerty.wikiflerty.data.Note>,
    bookmarks: List<com.flerty.wikiflerty.data.Bookmark>,
    bookmarkActions: BookmarkActions,
    people: List<com.flerty.wikiflerty.data.BookCharacter>,
    peopleFocus: String?,
    onPerson: (PersonDraft) -> Unit,
    quote: QuoteDraft?,
    quoteActions: QuoteActions,
    editor: NoteDraft?,
    noteActions: NoteActions,
    ai: AiBridge,
    aiSetupFromMenu: Boolean,
    chatFromHistory: Boolean = false,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onOpen: (ReaderSheet) -> Unit,
    onClose: () -> Unit,
    onEditNote: (com.flerty.wikiflerty.data.Note) -> Unit,
    onTocSelect: (TocItem) -> Unit,
    tts: TtsActions,
    ttsActive: Boolean,
    ttsPlaying: Boolean,
    ttsSleep: com.flerty.wikiflerty.tts.SleepTimer?,
    bookLanguage: String?,
    search: SearchActions,
    auto: AutoActions,
    jump: JumpInfo,
    flag: FlagInfo = FlagInfo(),
    currentHref: String? = null,
    chapter: String? = null,
) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(appCorner(2))
                .background(AppColors.Line),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val parent = when {
                sheet == ReaderSheet.Fonts -> ReaderSheet.Text
                sheet == ReaderSheet.Flag -> ReaderSheet.Panel
                sheet == ReaderSheet.NoteEditor -> if (editor?.fromList == true) ReaderSheet.Notes else null
                sheet == ReaderSheet.AiSetup -> if (aiSetupFromMenu) ReaderSheet.AiMenu else ReaderSheet.Options
                sheet == ReaderSheet.Heroes -> ReaderSheet.AiChapter
                sheet == ReaderSheet.HeroChat -> ReaderSheet.Heroes
                sheet == ReaderSheet.AiHistory -> ReaderSheet.AiChapter
                sheet == ReaderSheet.AiChat && chatFromHistory -> ReaderSheet.AiHistory
                sheet.depth > 0 -> ReaderSheet.Options
                else -> null
            }
            if (parent != null) {
                RoundIconButton(AppIcons.Back, "Назад", sound = FeedbackSound.Close) { onOpen(parent) }
            } else if (sheet !in NavSheets) {
                Spacer(Modifier.size(44.dp))
            }
            if (sheet in NavSheets) {
                NavTabs(sheet, onSelect = onOpen, modifier = Modifier.weight(1f).padding(end = 6.dp))
            } else Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val title = when {
                    sheet == ReaderSheet.NoteEditor && editor?.id == null -> "Новая заметка"
                    sheet == ReaderSheet.Notes && notes.isNotEmpty() -> "Заметки · ${notes.size}"
                    sheet == ReaderSheet.AiChat -> ai.chat.action.title
                    sheet == ReaderSheet.HeroChat -> ai.heroes?.state?.current?.name ?: sheet.title
                    else -> sheet.title
                }
                com.flerty.wikiflerty.ui.HeaderLabel(
                    title,
                    icon = when (sheet) {
                        ReaderSheet.HeroChat -> AppIcons.Voice
                        ReaderSheet.AiChat -> AppIcons.Sparkle
                        else -> null
                    },
                )
            }
            RoundIconButton(AppIcons.Close, "Закрыть", filled = true, sound = FeedbackSound.Close, onClick = onClose)
        }
        com.flerty.wikiflerty.ui.SheetDivider()

        when (sheet) {
            ReaderSheet.Toc, ReaderSheet.Notes, ReaderSheet.Bookmarks, ReaderSheet.People -> AnimatedContent(
                targetState = sheet,
                transitionSpec = {
                    val dir = if (NavSheets.indexOf(targetState) > NavSheets.indexOf(initialState)) 1 else -1
                    (slideInHorizontally(Motion.tween(280)) { dir * it / 4 } + fadeIn(Motion.fade(220)))
                        .togetherWith(slideOutHorizontally(Motion.tween(240)) { -dir * it / 4 } + fadeOut(Motion.fade(160)))
                        .using(SizeTransform(clip = true))
                },
                modifier = Modifier.weight(1f, fill = false),
                label = "nav",
            ) { tab ->
                when (tab) {
                    ReaderSheet.Notes -> BookNotesList(notes = notes, onOpen = noteActions.onGoTo, onEdit = onEditNote)
                    ReaderSheet.Bookmarks -> BookmarkList(bookmarks, bookmarkActions)
                    ReaderSheet.People -> PeopleList(
                        people = people,
                        focus = peopleFocus,
                        onAdd = { onPerson(PersonDraft(null, "", "")) },
                        onEdit = { onPerson(PersonDraft(it.id, it.name, it.note)) },
                    )
                    else -> TocList(toc, onTocSelect, Modifier, currentHref, chapter)
                }
            }
            ReaderSheet.Quote -> if (quote != null) {
                QuoteContent(quote, quoteActions, Modifier.weight(1f, fill = false))
            }
            ReaderSheet.Search -> SearchContent(search, Modifier.weight(1f, fill = false))
            ReaderSheet.Jump -> JumpContent(jump, onClose)
            ReaderSheet.AiChapter -> AiChapterContent(
                modifier = Modifier.weight(1f, fill = false),
                hasAi = ai.manager.current() != null,
                onRecap = { ai.onChapter(AiAction.Recap); onOpen(ReaderSheet.AiChat) },
                onQuiz = { ai.onChapter(AiAction.Quiz); onOpen(ReaderSheet.AiChat) },
                onTeacher = { ai.onChapter(AiAction.Teacher); onOpen(ReaderSheet.AiChat) },
                onHeroes = { onOpen(ReaderSheet.Heroes) },
                onHistory = { onOpen(ReaderSheet.AiHistory) },
                onSetup = { onOpen(ReaderSheet.AiSetup) },
            )
            ReaderSheet.Heroes -> ai.heroes?.let { HeroesContent(it, Modifier.weight(1f, fill = false)) }
            ReaderSheet.HeroChat -> ai.heroes?.let { HeroChatContent(it, Modifier.weight(1f)) }
            ReaderSheet.AiHistory -> ai.history?.let { AiHistoryContent(it, Modifier.weight(1f, fill = false)) }
            ReaderSheet.Fonts -> FontList(fonts, settings, onSettingsChange, Modifier.weight(1f, fill = false))
            ReaderSheet.AiMenu -> AiMenuContent(
                bridge = ai,
                onSetup = { onOpen(ReaderSheet.AiSetup) },
                onStarted = { onOpen(ReaderSheet.AiChat) },
                modifier = Modifier.weight(1f, fill = false),
                onHistory = { onOpen(ReaderSheet.AiHistory) },
            )
            ReaderSheet.AiChat -> AiChatContent(ai, Modifier.weight(1f))
            ReaderSheet.AiSetup -> Column(Modifier.weight(1f, fill = false).revealScroll()) {
                com.flerty.wikiflerty.ui.AiSettingsContent(ai.manager)
            }
            ReaderSheet.NoteEditor -> if (editor != null) {
                NoteEditor(
                    draft = editor,
                    palette = settings.noteColors,
                    onPaletteChange = { colors -> onSettingsChange { it.copy(noteColors = colors) } },
                    onSave = noteActions.onSave,
                    onDelete = noteActions.onDelete,
                    onCancel = onClose,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            else -> {
                val viewport = remember(sheet) { com.flerty.wikiflerty.ui.RevealViewport() }
                androidx.compose.runtime.CompositionLocalProvider(com.flerty.wikiflerty.ui.LocalRevealViewport provides viewport) {
            Column(
                modifier = Modifier.weight(1f, fill = false).revealViewport(viewport).revealScroll(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (sheet) {
                    ReaderSheet.Options -> OptionsHome(onOpen)
                    ReaderSheet.Text -> OptionFrame { TextOptions(settings, fonts, onSettingsChange, onOpen) }
                    ReaderSheet.Scroll -> OptionFrame { ScrollOptions(settings, onSettingsChange, auto.onStart) }
                    ReaderSheet.Theme -> OptionFrame { ThemeOptions(settings, onSettingsChange) }
                    ReaderSheet.Panel -> OptionFrame { PanelOptions(settings, onSettingsChange, onOpen) }
                    ReaderSheet.Flag -> OptionFrame { FlagOptions(settings, onSettingsChange, flag) }
                    ReaderSheet.Gestures -> OptionFrame { GestureOptions(settings, onSettingsChange) }
                    ReaderSheet.Screen -> OptionFrame { ScreenOptions(settings, onSettingsChange) }
                    ReaderSheet.Sound -> OptionFrame { SoundOptions(settings, onSettingsChange) }
                    ReaderSheet.Atmosphere -> AmbienceOptions(title = "Звуки фона")
                    ReaderSheet.Selection -> OptionFrame { SelectionOptions(settings, onSettingsChange) }
                    ReaderSheet.Listen -> OptionFrame { TtsOptions(settings, bookLanguage, ttsActive, ttsPlaying, ttsSleep, tts, onSettingsChange) }
                    else -> Unit
                }
            }
                }
            }
        }
    }
}

@Composable
private fun OptionsHome(onOpen: (ReaderSheet) -> Unit) {
    data class Tile(val target: ReaderSheet, val hint: String, val icon: ImageVector, val from: Long, val to: Long, val dark: Boolean = false)
    val base = listOf(
        Tile(ReaderSheet.Text, "Шрифт, размер", AppIcons.TextSize, 0xFF6E8EFF, 0xFF4C63D9),
        Tile(ReaderSheet.Scroll, "Режим, анимация", AppIcons.Pages, 0xFFA78BFA, 0xFF7653E0),
        Tile(ReaderSheet.Theme, "Фон страницы", AppIcons.Theme, 0xFFF6C770, 0xFFD9922B, dark = true),
        Tile(ReaderSheet.Panel, "Кнопки, прогресс", AppIcons.Panel, 0xFF5EEAD4, 0xFF1FA392, dark = true),
        Tile(ReaderSheet.Selection, "Меню, перевод", AppIcons.Select, 0xFFFDA4AF, 0xFFE0475F),
        Tile(ReaderSheet.Gestures, "Тапы, свайпы", AppIcons.Gesture, 0xFFF49AC1, 0xFFD45A93),
        Tile(ReaderSheet.Screen, "Яркость, поворот", AppIcons.Screen, 0xFFF7D774, 0xFFD9A92B, dark = true),
        Tile(ReaderSheet.Listen, "Голос, скорость", AppIcons.Headphones, 0xFF8BD3A5, 0xFF3E9E66, dark = true),
        Tile(ReaderSheet.Sound, "Кнопки, вибрация", AppIcons.Sound, 0xFFF6A26B, 0xFFD9692B),
        Tile(ReaderSheet.Atmosphere, "Дождь, камин", AppIcons.Fire, 0xFFFCA5A5, 0xFFB45309),
        Tile(ReaderSheet.AiChapter, "Пересказ, тест", AppIcons.Book, 0xFF7DD3FC, 0xFF3B82C4),
        Tile(ReaderSheet.AiSetup, "Нейросети, модели", AppIcons.Sparkle, 0xFF8AB4F8, 0xFF7653E0),
    )
    OptionTiles(base.map { OptionTile(it.target.title, it.hint, it.icon, it.from, it.to, it.dark) { onOpen(it.target) } })
}

internal class OptionTile(val title: String, val hint: String, val icon: ImageVector, val from: Long, val to: Long, val dark: Boolean = false, val onClick: () -> Unit)

@Composable
internal fun OptionTiles(tiles: List<OptionTile>) {
    Column(
        Modifier.fillMaxWidth().sheetReveal().fieldSurface(appCorner(22)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile ->
                    val shape = appCorner(18)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 96.dp)
                            .liveClick(shape, sound = FeedbackSound.Open, onClick = tile.onClick)
                            .fieldSurface(shape)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(appCorner(12))
                                .background(Brush.linearGradient(listOf(Color(tile.from), Color(tile.to)))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(tile.icon, null, tint = if (tile.dark) Color(0xFF1A1405) else Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                tile.title,
                                color = AppColors.Text,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
                            )
                            Text(
                                tile.hint,
                                color = AppColors.Muted,
                                maxLines = 1,
                                softWrap = false,
                                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TextOptions(
    settings: ReaderSettings,
    fonts: List<FontEntry>,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onOpen: (ReaderSheet) -> Unit,
) {
    val context = LocalContext.current
    val entry = FontCatalog.find(fonts, settings.font)
    val shape = appCorner(16)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f) { onOpen(ReaderSheet.Fonts) }
            .padding(start = 2.dp, end = 0.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Шрифт", color = AppColors.Muted, fontSize = 12.sp)
            Text(
                entry.title,
                color = AppColors.Text,
                fontSize = 19.sp,
                fontFamily = FontCatalog.preview(context, entry),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("Выбрать", color = AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Icon(AppIcons.Chevron, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
    }
    StepSlider(
        label = "Размер",
        icon = AppIcons.TextSize,
        value = settings.fontSize,
        range = 0.6..2.0,
        step = 0.1,
        format = { "${(it * 100).roundToInt()}%" },
        onCommit = { v -> onSettingsChange { it.copy(fontSize = v) } },
    )
    StepSlider(
        label = "Интервал",
        icon = AppIcons.LineHeight,
        value = settings.lineHeight,
        range = 1.0..2.0,
        step = 0.1,
        format = { String.format(Locale.US, "%.1f", it) },
        onCommit = { v -> onSettingsChange { it.copy(lineHeight = v) } },
    )
    StepSlider(
        label = "Поля",
        icon = AppIcons.Margins,
        value = settings.margins,
        range = 0.0..3.0,
        step = 0.25,
        format = { String.format(Locale.US, "%.2f", it) },
        onCommit = { v -> onSettingsChange { it.copy(margins = v) } },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleTile("Жирный", settings.bold, Modifier.weight(1f), onClick = { onSettingsChange { it.copy(bold = !it.bold) } }) {
            Text("Ж", color = AppColors.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
        ToggleTile(
            "Курсив",
            settings.italic && entry.supportsItalic,
            Modifier.weight(1f),
            enabled = entry.supportsItalic,
            onClick = { onSettingsChange { it.copy(italic = !it.italic) } },
        ) {
            Text("К", color = AppColors.Text, fontSize = 17.sp, fontFamily = FontFamily.Serif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        }
        ToggleTile("По ширине", settings.justify, Modifier.weight(1f), onClick = { onSettingsChange { it.copy(justify = true) } }) {
            AlignIcon(justify = true)
        }
        ToggleTile("По левому краю", !settings.justify, Modifier.weight(1f), onClick = { onSettingsChange { it.copy(justify = false) } }) {
            AlignIcon(justify = false)
        }
    }
    com.flerty.wikiflerty.ui.Appear(!entry.supportsItalic) {
        Text("У этого шрифта нет курсива", color = AppColors.Faint, fontSize = 12.sp)
    }
    }
    SwitchRow("Переносы", settings.hyphens, icon = AppIcons.Wrap) { v -> onSettingsChange { it.copy(hyphens = v) } }
    SwitchRow("Бионическое чтение", settings.bionic, icon = AppIcons.Eye) { v -> onSettingsChange { it.copy(bionic = v) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Column(Modifier.alpha(if (settings.scroll) 0.45f else 1f)) {
        Segmented(
            "Колонки",
            listOf("Авто", "Одна", "Две"),
            when (settings.columns) {
                "1" -> 1
                "2" -> 2
                else -> 0
            },
            icon = AppIcons.Pages,
        ) { i -> if (!settings.scroll) onSettingsChange { it.copy(columns = listOf("auto", "1", "2")[i]) } }
    }
    com.flerty.wikiflerty.ui.Appear(settings.scroll) {
        com.flerty.wikiflerty.ui.HintNote("Колонки работают в режиме «Страницы». Сейчас включена лента — переключить можно в «Листании»")
    }
    }
}

@Composable
private fun FontList(
    fonts: List<FontEntry>,
    settings: ReaderSettings,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(fonts, key = { it.id }) { entry ->
                Reveal {
                    val selected = settings.font == entry.id
                    val shape = appCorner(14)
                    val border by animateColorAsState(if (selected) AppColors.Accent else AppColors.PanelBorder, label = "font")
                    val family = FontCatalog.preview(context, entry)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.98f) {
                                onSettingsChange { it.copy(font = entry.id) }
                            }
                            .background(if (selected) AppColors.AccentTint else AppColors.Field)
                            .border(if (selected) 2.dp else 1.dp, border, shape)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entry.title, color = AppColors.Text, fontSize = 18.sp, fontFamily = family, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (entry.css == null) "Шрифт, который выбрал издатель книги" else "Съешь же ещё этих мягких французских булок",
                                color = AppColors.Muted,
                                fontSize = 13.sp,
                                fontFamily = family,
                                maxLines = 1,
                                softWrap = false,
                                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp, stepSize = 0.25.sp),
                            )
                        }
                        AnimatedVisibility(visible = selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                            Box(
                                Modifier.padding(start = 8.dp).size(26.dp).clip(CircleShape).background(AppColors.Accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(AppIcons.Check, null, tint = AppColors.OnAccent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollOptions(
    settings: ReaderSettings,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onAutoStart: (() -> Unit)? = null,
) {
    Segmented("Режим", listOf("Страницы", "Лента"), if (settings.scroll) 1 else 0, icon = AppIcons.Scroll) { i ->
        onSettingsChange { it.copy(scroll = i == 1) }
    }
    PickerRow(
        label = "Анимация листания",
        icon = AppIcons.Animation,
        options = PageAnimation.entries.map { a ->
            PickerOption(
                a.id,
                a.title,
                when (a) {
                    PageAnimation.Slide -> "Страница уезжает в сторону"
                    PageAnimation.Curl -> "Угол загибается, как в бумажной книге"
                    PageAnimation.Flip -> "Страница поворачивается вокруг корешка"
                    PageAnimation.Depth -> "Страница уходит вглубь и тает"
                    PageAnimation.Fade -> "Мягкое растворение"
                    PageAnimation.Cover -> "Новая страница накрывает старую"
                    PageAnimation.None -> "Мгновенно, без эффекта"
                },
                icon = when (a) {
                    PageAnimation.Slide -> AppIcons.Forward
                    PageAnimation.Curl -> AppIcons.Book
                    PageAnimation.Flip -> AppIcons.Rotate
                    PageAnimation.Depth -> AppIcons.Screen
                    PageAnimation.Fade -> AppIcons.Drop
                    PageAnimation.Cover -> AppIcons.Pages
                    PageAnimation.None -> AppIcons.Close
                },
            )
        },
        selectedId = settings.animation.id,
    ) { id -> onSettingsChange { it.copy(pageAnimation = id) } }
    SwitchRow("Кнопки громкости", settings.volumeKeys, icon = AppIcons.VolumeKeys) { v -> onSettingsChange { it.copy(volumeKeys = v) } }
    StepSlider(
        label = "Автопрокрутка",
        value = settings.autoSpeed.toDouble(),
        range = 1.0..10.0,
        step = 1.0,
        format = { AutoScroll.label(it.roundToInt(), settings.scroll) },
        icon = AppIcons.Play,
    ) { v -> onSettingsChange { it.copy(autoSpeed = v.roundToInt()) } }
    if (onAutoStart != null) {
        val shape = appCorner(14)
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .liveClick(shape, sound = FeedbackSound.Open, onClick = onAutoStart)
                .accentHighlight(shape),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.Play, null, tint = accentContent, modifier = Modifier.size(20.dp))
                Text("Автопрокрутка", color = accentContent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ThemeOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    SwitchRow("Стиль шаблона в книге", settings.kitStyle, icon = AppIcons.Palette) { v -> onSettingsChange { it.copy(kitStyle = v) } }
    Group(if (settings.autoNight) "Днём" else "Фон страницы") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageTheme.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { theme ->
                        val selected = settings.dayTheme == theme
                        val shape = appCorner(16)
                        val spot = remember { floatArrayOf(-1f, -1f) }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(86.dp)
                                .onGloballyPositioned { c -> c.positionInWindow().let { spot[0] = it.x + c.size.width / 2f; spot[1] = it.y + c.size.height / 2f } }
                                .liveClick(shape, sound = FeedbackSound.Toggle) {
                                    if (selected) return@liveClick
                                    val change = { onSettingsChange { it.copy(theme = theme.id) } }
                                    val reveal = ReaderReveal.run
                                    if (reveal != null && !settings.isNightNow) reveal(spot[0], spot[1], change) else change()
                                }
                                .background(Color(theme.background))
                                .border(if (selected) 2.dp else 1.dp, if (selected) AppColors.Accent else AppColors.Line, shape),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("Aa", color = Color(theme.text), fontFamily = FontFamily.Serif, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                            Text(theme.title, color = Color(theme.text), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    SwitchRow("Ночная тема по расписанию", settings.autoNight, icon = AppIcons.Moon) { v -> onSettingsChange { it.copy(autoNight = v) } }
    com.flerty.wikiflerty.ui.Appear(settings.autoNight, spacing = 12.dp) {
        val times = (0 until 48).map { it * 30 }.map { m -> PickerOption(m.toString(), "%02d:%02d".format(m / 60, m % 60)) }
        PickerRow(
            label = "Ночная тема",
            icon = AppIcons.Theme,
            options = PageTheme.entries.map { PickerOption(it.id, it.title, swatch = Color(it.background)) },
            selectedId = settings.nightTheme,
        ) { id -> onSettingsChange { it.copy(nightTheme = id) } }
        com.flerty.wikiflerty.ui.ListDivider()
        PickerRow(
            label = "Включать в",
            icon = AppIcons.Moon,
            options = times,
            selectedId = (settings.nightFrom / 30 * 30).toString(),
            searchable = false,
        ) { id -> onSettingsChange { it.copy(nightFrom = id.toInt()) } }
        com.flerty.wikiflerty.ui.ListDivider()
        PickerRow(
            label = "Выключать в",
            icon = AppIcons.Sun,
            options = times,
            selectedId = (settings.nightTo / 30 * 30).toString(),
            searchable = false,
        ) { id -> onSettingsChange { it.copy(nightTo = id.toInt()) } }
    }
}

@Composable
private fun PanelOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit, onOpen: (ReaderSheet) -> Unit) {
    Segmented("Прогресс", listOf("Проценты", "Страницы"), if (settings.progressInPages) 1 else 0, icon = AppIcons.Percent) { i ->
        onSettingsChange { it.copy(progressInPages = i == 1) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .liveClick(appCorner(14), sound = FeedbackSound.Open, pressedScale = 0.98f) { onOpen(ReaderSheet.Flag) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.flerty.wikiflerty.ui.SettingIcon(AppIcons.Flag, 30.dp)
        Spacer(Modifier.width(12.dp))
        Text("Флажок внизу", color = AppColors.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(if (settings.showFlag) "Вкл" else "Выкл", color = AppColors.Muted, fontSize = 13.sp)
        Icon(AppIcons.Chevron, null, tint = AppColors.Faint, modifier = Modifier.padding(start = 6.dp).size(16.dp))
    }
    Group("Кнопки панели") {
        QuickButtonList(QuickButton.entries.filter { !it.ai }, settings, onSettingsChange)
    }
    Group("ИИ в панели", icon = AppIcons.Sparkle) {
        QuickButtonList(QuickButton.entries.filter { it.ai }, settings, onSettingsChange)
    }
}

@Composable
private fun SelectionOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    val (main, more) = settings.selectionLayout()
    Group("Капли выделения", icon = AppIcons.Select) {
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        ) {
            items(HandleStyle.entries.size) { i ->
                val style = HandleStyle.entries[i]
                HandleStyleTile(style, selected = style.id == settings.handleStyle) { onSettingsChange { it.copy(handleStyle = style.id) } }
            }
        }
    }
    Group("В меню · ${main.size}/${SelectionButton.MAX}", icon = AppIcons.Select) {
        SelectionButtonList(main, settings.markerColor, toMain = false, canMove = true,
            onUp = { b -> onSettingsChange { s -> val (m, o) = s.selectionLayout(); s.withSelection(m.moveUp(b), o) } },
            onMove = { b -> onSettingsChange { s -> val (m, o) = s.selectionLayout(); s.withSelection(m - b, listOf(b) + o) } },
        )
    }
    Group("Под «⋯»", icon = AppIcons.More) {
        SelectionButtonList(more, settings.markerColor, toMain = true, canMove = main.size < SelectionButton.MAX,
            onUp = { b -> onSettingsChange { s -> val (m, o) = s.selectionLayout(); s.withSelection(m, o.moveUp(b)) } },
            onMove = { b -> onSettingsChange { s -> val (m, o) = s.selectionLayout(); if (m.size >= SelectionButton.MAX) s else s.withSelection(m + b, o - b) } },
        )
    }
    PickerRow(
        label = "Перевод на",
        icon = AppIcons.Translate,
        options = Translator.languages.map { PickerOption(it.code, it.title) },
        selectedId = settings.translateTo,
    ) { code -> onSettingsChange { it.copy(translateTo = code) } }
}

@Composable
private fun HandleStyleTile(style: HandleStyle, selected: Boolean, onClick: () -> Unit) {
    val shape = appCorner(14)
    val edge by androidx.compose.animation.animateColorAsState(if (selected) AppColors.Accent else AppColors.PanelBorder, Motion.fade(200), label = "handle-edge")
    val fill by androidx.compose.animation.animateColorAsState(if (selected) AppColors.AccentTintStrong else AppColors.Field, Motion.fade(200), label = "handle-fill")
    val lift by animateFloatAsState(if (selected) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 500f), label = "handle-lift")
    val ink = AppColors.Faint
    val mark = Color(0xFF8FB3FF)
    Column(
        Modifier
            .width(96.dp)
            .clip(shape)
            .background(fill)
            .border(if (selected) 1.5.dp else 1.dp, edge, shape)
            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.94f, onClick = onClick)
            .padding(top = 10.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(width = 80.dp, height = 46.dp).graphicsLayer { val k = 1f + 0.06f * lift; scaleX = k; scaleY = k }) {
            val u = density * 0.8f
            val lineH = 9f * u
            val y1 = 8f * u
            val y2 = y1 + 15f * u
            drawRoundRect(ink.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(4f * u, y1), androidx.compose.ui.geometry.Size(size.width - 8f * u, lineH), androidx.compose.ui.geometry.CornerRadius(2f * u))
            drawRoundRect(ink.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(4f * u, y2), androidx.compose.ui.geometry.Size(size.width * 0.7f, lineH), androidx.compose.ui.geometry.CornerRadius(2f * u))
            val sx = size.width * 0.32f
            val ex = size.width * 0.55f
            drawRect(mark.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(sx, y1 - 1.5f * u), androidx.compose.ui.geometry.Size(size.width - 4f * u - sx, lineH + 3f * u))
            drawRect(mark.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(4f * u, y2 - 1.5f * u), androidx.compose.ui.geometry.Size(ex - 4f * u, lineH + 3f * u))
            drawSelectionHandle(style, true, mark, androidx.compose.ui.geometry.Offset(sx, y1 + lineH + 1.5f * u), u)
            drawSelectionHandle(style, false, mark, androidx.compose.ui.geometry.Offset(ex, y2 + lineH + 1.5f * u), u)
        }
        Text(style.title, color = if (selected) AppColors.Text else AppColors.Muted, fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

private fun List<SelectionButton>.moveUp(b: SelectionButton): List<SelectionButton> {
    val i = indexOf(b)
    if (i <= 0) return this
    return toMutableList().apply { removeAt(i); add(i - 1, b) }
}

@Composable
private fun SelectionButtonList(
    buttons: List<SelectionButton>,
    markerColor: Long,
    toMain: Boolean,
    canMove: Boolean,
    onUp: (SelectionButton) -> Unit,
    onMove: (SelectionButton) -> Unit,
) {
    Column(Modifier.clip(appCorner(16)).background(AppColors.Field).animateContentSize(Motion.tween(260))) {
        if (buttons.isEmpty()) {
            Text("Пусто", color = AppColors.Faint, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp))
        }
        buttons.forEachIndexed { index, button ->
            key(button) {
                if (index > 0) com.flerty.wikiflerty.ui.ListDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().height(50.dp).padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { SelectionIcon(button, markerColor, AppColors.Text) }
                    Spacer(Modifier.width(12.dp))
                    Text(button.title, color = AppColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val upAlpha by androidx.compose.animation.core.animateFloatAsState(if (index > 0) 1f else 0f, Motion.tween(200), label = "up")
                    Box(
                        Modifier
                            .size(40.dp)
                            .graphicsLayer { alpha = upAlpha }
                            .liveClick(CircleShape, sound = FeedbackSound.Toggle, enabled = index > 0, pressedScale = 0.85f) { onUp(button) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.Chevron, "Выше", tint = AppColors.Muted, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = -90f }) }
                    val moveAlpha by androidx.compose.animation.core.animateFloatAsState(if (canMove) 1f else 0.3f, Motion.tween(200), label = "move")
                    Box(
                        Modifier
                            .size(40.dp)
                            .graphicsLayer { alpha = moveAlpha }
                            .liveClick(CircleShape, sound = FeedbackSound.Toggle, enabled = canMove, pressedScale = 0.85f) { onMove(button) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (toMain) AppIcons.Plus else AppIcons.More, if (toMain) "В меню" else "Под «⋯»", tint = AppColors.Accent, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun QuickButtonList(buttons: List<QuickButton>, settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
        Column(Modifier.clip(appCorner(16)).background(AppColors.Field)) {
            buttons.forEachIndexed { index, button ->
                if (index > 0) com.flerty.wikiflerty.ui.ListDivider()
                val checked = button.id in settings.quickButtons
                val toggle = {
                    onSettingsChange { s ->
                        val list = if (checked) s.quickButtons - button.id else s.quickButtons + button.id
                        if (list.size > QuickButton.MAX) s else s.copy(quickButtons = list)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .liveClick(appCorner(0), sound = FeedbackSound.Toggle, pressedScale = 0.98f) { toggle() }
                        .padding(start = 14.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        QuickIcon(button, if (checked) AppColors.Text else AppColors.Faint)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        button.title,
                        color = if (checked) AppColors.Text else AppColors.Faint,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    CheckMark(checked)
                }
            }
        }
}

@Composable
internal fun GestureOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    SwitchRow("Тап по краям листает", settings.edgeTaps, icon = AppIcons.Gesture) { v -> onSettingsChange { it.copy(edgeTaps = v) } }
    StepSlider(
        label = "Ширина зон у краёв",
        icon = AppIcons.EdgeZones,
        value = settings.edgeZone,
        range = 0.15..0.40,
        step = 0.05,
        enabled = settings.edgeTaps,
        format = { "${(it * 100).roundToInt()}%" },
        onCommit = { v -> onSettingsChange { it.copy(edgeZone = v) } },
    )
    SwitchRow("Левый край тоже вперёд", settings.leftTapForward, enabled = settings.edgeTaps, icon = AppIcons.Forward) { v ->
        onSettingsChange { it.copy(leftTapForward = v) }
    }
    SwitchRow("Яркость свайпом слева", settings.brightnessSwipe, icon = AppIcons.Sun) { v -> onSettingsChange { it.copy(brightnessSwipe = v) } }
}

@Composable
internal fun ScreenOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    val system = settings.brightness < 0
    SwitchRow("Яркость как в системе", system, icon = AppIcons.Screen) { v ->
        onSettingsChange { it.copy(brightness = if (v) -1.0 else 0.6) }
    }
    StepSlider(
        label = "Яркость",
        icon = AppIcons.Sun,
        value = if (system) 0.6 else settings.brightness,
        range = 0.05..1.0,
        step = 0.05,
        enabled = !system,
        format = { "${(it * 100).roundToInt()}%" },
        onCommit = { v -> onSettingsChange { it.copy(brightness = v) } },
    )
    PickerRow(
        label = "Поворот экрана",
        icon = AppIcons.Rotate,
        options = ScreenOrientation.entries.map { o ->
            PickerOption(
                o.id,
                o.title,
                when (o) {
                    ScreenOrientation.Auto -> "Как повернёшь телефон"
                    ScreenOrientation.Portrait -> "Всегда вертикально"
                    ScreenOrientation.Landscape -> "Всегда горизонтально"
                },
                icon = when (o) {
                    ScreenOrientation.Auto -> AppIcons.Rotate
                    ScreenOrientation.Portrait -> AppIcons.VolumeKeys
                    ScreenOrientation.Landscape -> AppIcons.Pages
                },
            )
        },
        selectedId = ScreenOrientation.of(settings.orientation).id,
    ) { id -> onSettingsChange { it.copy(orientation = id) } }
    Column(Modifier.clip(appCorner(16)).background(AppColors.Field).padding(horizontal = 14.dp)) {
        SwitchRow("Не гасить экран", settings.keepScreenOn, icon = AppIcons.Bulb) { v -> onSettingsChange { it.copy(keepScreenOn = v) } }
        SwitchRow("Полный экран", settings.fullscreen, icon = AppIcons.Fullscreen) { v -> onSettingsChange { it.copy(fullscreen = v) } }
    }
}

@Composable
internal fun SoundOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    val feedback = LocalFeedback.current
    val view = LocalView.current
    SwitchRow("Звуки кнопок", settings.sounds, icon = AppIcons.Sound) { v -> onSettingsChange { it.copy(sounds = v) } }
    StepSlider(
        label = "Громкость",
        icon = AppIcons.Sound,
        value = settings.soundVolume,
        range = 0.1..1.0,
        step = 0.05,
        enabled = settings.sounds,
        format = { "${(it * 100).roundToInt()}%" },
        onCommit = { v ->
            onSettingsChange { it.copy(soundVolume = v) }
            feedback?.play(FeedbackSound.Tap, null)
        },
    )
    SwitchRow("Лёгкая вибрация", settings.haptics, icon = AppIcons.Vibration) { v ->
        onSettingsChange { it.copy(haptics = v) }
        if (v) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }
}

@Composable
private fun TocList(toc: List<TocItem>, onSelect: (TocItem) -> Unit, modifier: Modifier, currentHref: String? = null, chapter: String? = null) {
    if (toc.isEmpty()) {
        Text("В этой книге нет оглавления", color = AppColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    val current = remember(toc, currentHref, chapter) {
        val res = currentHref?.substringBefore('#')?.trimStart('/')
        val byHref = if (res == null) -1 else toc.indexOfLast { it.link.href.toString().substringBefore('#').trimStart('/') == res }
        val byTitle = if (chapter.isNullOrBlank()) -1 else toc.indexOfLast { it.title == chapter.trim() }
        when {
            byTitle >= 0 && (byHref < 0 || toc[byTitle].link.href.toString().substringBefore('#').trimStart('/') == res) -> byTitle
            byHref >= 0 -> byHref
            else -> byTitle
        }
    }
    val list = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
    LazyColumn(modifier, state = list) {
        itemsIndexed(toc) { index, item ->
            Column {
            if (index > 0) com.flerty.wikiflerty.ui.ListDivider()
            Reveal {
                val shape = appCorner(12)
                val active = index == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(vertical = 3.dp)
                        .clip(shape)
                        .background(if (active) AppColors.AccentTint else Color.Transparent)
                        .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { onSelect(item) }
                        .padding(start = (8 + item.depth * 16).dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (active) {
                        Box(Modifier.padding(end = 10.dp).size(width = 3.dp, height = 18.dp).clip(appCorner(2)).background(AppColors.Accent))
                    }
                    Text(
                        item.title,
                        color = when {
                            active -> AppColors.Accent
                            item.depth == 0 -> AppColors.Text
                            else -> AppColors.Muted
                        },
                        fontSize = if (item.depth == 0) 15.sp else 14.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            }
        }
    }
}

@Composable
internal fun Group(label: String, icon: ImageVector? = null, content: @Composable () -> Unit) {
    if (LocalInOptionFrame.current) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingIcon(icon ?: com.flerty.wikiflerty.ui.titleIcon(label), 30.dp)
                Spacer(Modifier.width(12.dp))
                Text(label, color = AppColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            content()
        }
        return
    }
    com.flerty.wikiflerty.ui.TabbedFrame(label, icon = icon) {
        Column(Modifier.padding(vertical = 8.dp)) { content() }
    }
}

internal val LocalInOptionFrame = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
internal fun OptionFrame(content: @Composable () -> Unit) {
    val lines = remember { mutableListOf<Float>() }
    val line = AppColors.PanelBorder
    androidx.compose.runtime.CompositionLocalProvider(LocalInOptionFrame provides true) {
        androidx.compose.ui.layout.Layout(
            content = content,
            modifier = Modifier
                .fillMaxWidth()
                .sheetReveal()
                .fieldSurface(appCorner(18))
                .drawWithContent {
                    drawContent()
                    val inset = 14.dp.toPx()
                    lines.forEach { y ->
                        drawLine(line, androidx.compose.ui.geometry.Offset(inset, y), androidx.compose.ui.geometry.Offset(size.width - inset, y), 1.dp.toPx())
                    }
                },
        ) { measurables, constraints ->
            val side = 14.dp.roundToPx()
            val edge = 10.dp.roundToPx()
            val gap = 12.dp.roundToPx()
            val width = constraints.maxWidth
            val inner = constraints.copy(minWidth = 0, maxWidth = (width - side * 2).coerceAtLeast(0), minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
            val placeables = measurables.map { it.measure(inner) }.filter { it.height > 0 }
            val ys = mutableListOf<Int>()
            val dividers = mutableListOf<Float>()
            var y = edge
            placeables.forEachIndexed { i, p ->
                if (i > 0) {
                    y += gap
                    dividers += y.toFloat()
                    y += gap
                }
                ys += y
                y += p.height
            }
            y += edge
            lines.clear()
            lines.addAll(dividers)
            layout(width, y) { placeables.forEachIndexed { i, p -> p.place(side, ys[i]) } }
        }
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, color = AppColors.Faint, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
private fun CheckMark(checked: Boolean) {
    val bg by animateColorAsState(if (checked) AppColors.Accent else Color.Transparent, label = "check")
    val shape = appCorner(7)
    Box(
        Modifier.size(24.dp).clip(shape).background(bg).border(2.dp, if (checked) AppColors.Accent else AppColors.Faint, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(AppIcons.Check, null, tint = AppColors.OnAccent, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun StepSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    format: (Double) -> String,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onCommit: (Double) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val steps = ((range.endInclusive - range.start) / step).roundToInt() - 1
    Column(Modifier.alpha(if (enabled) 1f else 0.4f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                SettingIcon(icon, 30.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(label, color = if (icon != null) AppColors.Text else AppColors.Muted, fontSize = if (icon != null) 15.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Text(format(current.toDouble()), color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        KitSlider(
            value = current,
            onValueChange = { current = it },
            enabled = enabled,
            onValueChangeFinished = {
                val snapped = Math.round(current / step) * step
                onCommit(snapped.coerceIn(range.start, range.endInclusive))
            },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            steps = steps.coerceAtLeast(0),
        )
    }
}

@Composable
internal fun Segmented(label: String, options: List<String>, selected: Int, icon: ImageVector? = null, onSelect: (Int) -> Unit) {
    Group(label, icon) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(appCorner(14))
                .background(AppColors.Field)
                .border(1.dp, AppColors.PanelBorder, appCorner(14))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { i, option ->
                val isSelected = i == selected
                val bg by animateColorAsState(if (isSelected) SelectedSegment else Color.Transparent, label = "segment")
                val shape = appCorner(10)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.96f) { onSelect(i) }
                        .background(bg)
                        .then(if (isSelected) Modifier.border(1.dp, AppColors.PanelBorder, shape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option,
                        color = if (isSelected) AppColors.Text else AppColors.Muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    val feedback = LocalFeedback.current
    val view = LocalView.current
    val change: (Boolean) -> Unit = { v ->
        feedback?.play(FeedbackSound.Toggle, view)
        onChange(v)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled) { change(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingIcon(icon, 30.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(label, color = AppColors.Text, fontSize = 15.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 6.dp).padding(end = 8.dp))
        KitSwitch(checked, change, enabled = enabled)
    }
}

@Composable
private fun ToggleTile(
    description: String,
    selected: Boolean,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = appCorner(12)
    val border by animateColorAsState(if (selected) AppColors.Accent else AppColors.Line, label = "toggle")
    Box(
        modifier = modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .liveClick(shape, sound = FeedbackSound.Toggle, enabled = enabled, pressedScale = 0.92f, onClick = onClick)
            .background(if (selected) AppColors.AccentTint else AppColors.Field)
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun AlignIcon(justify: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        listOf(0.2f, 0.4f, 0.6f, 0.8f).forEachIndexed { i, y ->
            val end = if (justify || i % 2 == 0) size.width else size.width * 0.6f
            drawLine(
                color = AppColors.Text,
                start = androidx.compose.ui.geometry.Offset(0f, size.height * y),
                end = androidx.compose.ui.geometry.Offset(end, size.height * y),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun FinishedPrompt(onMark: () -> Unit, onDismiss: () -> Unit) {
    val shape = appCorner(22)
    Row(
        Modifier.blockTaps().panelSurface(shape).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Книга дочитана?", color = AppColors.Text, fontSize = 14.sp)
        val button = appCorner(16)
        Box(
            Modifier
                .heightIn(min = 38.dp)
                .liveClick(button, sound = FeedbackSound.Toggle, onClick = onMark)
                .accentHighlight(button)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Прочитано", color = accentContent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            Modifier.size(38.dp).liveClick(CircleShape, sound = FeedbackSound.Close, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Close, "Закрыть", tint = AppColors.Muted, modifier = Modifier.size(18.dp)) }
    }
}

class AutoActions(
    val onStart: () -> Unit,
    val onToggle: () -> Unit,
    val onStop: () -> Unit,
)

@Composable
private fun AutoBar(
    paused: Boolean,
    progress: Float?,
    label: String,
    onToggle: () -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onStop: () -> Unit,
    onCollapse: () -> Unit,
) {
    Column(
        Modifier
            .panelSurface(appCorner(24), elevation = 12.dp)
            .blockTaps()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(if (paused) AppIcons.Play else AppIcons.Pause, if (paused) "Продолжить" else "Пауза", filled = true, sound = FeedbackSound.Toggle, onClick = onToggle)
        AutoStepButton("−", "Медленнее", onSlower)
        Text(label, color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
        AutoStepButton("+", "Быстрее", onFaster)
        RoundIconButton(AppIcons.EyeOff, "Скрыть панель", sound = FeedbackSound.Close, onClick = onCollapse)
        RoundIconButton(AppIcons.Close, "Остановить", sound = FeedbackSound.Close, onClick = onStop)
    }
    if (progress != null) {
        Box(
            Modifier
                .padding(start = 14.dp, end = 14.dp, bottom = 4.dp)
                .width(180.dp)
                .height(3.dp)
                .clip(appCorner(2))
                .background(AppColors.Line),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(AppColors.Accent),
            )
        }
    }
    }
}

@Composable
private fun AutoStepButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .liveClick(CircleShape, sound = FeedbackSound.Tap, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = AppColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AutoMini(paused: Boolean, progress: Float?, onExpand: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .alpha(0.9f)
            .panelSurface(CircleShape, elevation = 8.dp)
            .liveClick(CircleShape, sound = FeedbackSound.Open, pressedScale = 0.85f, onClick = onExpand)
            .semantics { contentDescription = "Показать автопрокрутку" },
        contentAlignment = Alignment.Center,
    ) {
        if (progress != null) {
            androidx.compose.material3.CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(30.dp),
                color = AppColors.Accent,
                trackColor = AppColors.Line,
                strokeWidth = 2.dp,
                gapSize = 0.dp,
            )
        }
        Icon(if (paused) AppIcons.Play else AppIcons.Pause, null, tint = AppColors.Text, modifier = Modifier.size(13.dp))
    }
}

class JumpInfo(val progression: Double, val position: Int?, val total: Int, val onSeek: (Double) -> Unit)

@Composable
private fun JumpContent(jump: JumpInfo, onClose: () -> Unit) {
    var pages by remember { mutableStateOf(jump.total > 1) }
    var input by remember { mutableStateOf("") }
    val value = input.replace(',', '.').toDoubleOrNull()
    val target: Double? = when {
        value == null -> null
        pages -> if (jump.total > 1 && value >= 1 && value <= jump.total) (value - 1) / (jump.total - 1) else null
        else -> if (value in 0.0..100.0) value / 100.0 else null
    }
    val go: () -> Unit = {
        if (target != null) {
            jump.onSeek(target)
            onClose()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (jump.total > 1) {
            Segmented("Куда", listOf("Страница", "Процент"), if (pages) 0 else 1, icon = AppIcons.Pages) { i ->
                pages = i == 0
                input = ""
            }
        }
        Text(
            if (pages) "Сейчас: ${jump.position ?: 1}/${jump.total}" else "Сейчас: ${(jump.progression * 100).roundToInt()}%",
            color = AppColors.Muted,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.foundation.text.BasicTextField(
                value = input,
                onValueChange = { v -> input = v.filter { it.isDigit() || (!pages && (it == '.' || it == ',')) }.take(6) },
                singleLine = true,
                textStyle = TextStyle(color = AppColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.Accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (pages) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Decimal,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Go,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { go() }),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(appCorner(14))
                    .background(AppColors.Field)
                    .border(1.dp, if (input.isNotEmpty() && target == null) AppColors.Bad else AppColors.Line, appCorner(14))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (input.isEmpty()) {
                            Text(if (pages) "1–${jump.total}" else "0–100 %", color = AppColors.Faint, fontSize = 20.sp)
                        }
                        inner()
                    }
                },
            )
            Box(
                Modifier
                    .size(56.dp)
                    .liveClick(appCorner(14), sound = FeedbackSound.Open, enabled = target != null, onClick = go)
                    .background(if (target != null) AppColors.Accent else AppColors.Line, appCorner(14)),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Forward, "Перейти", tint = if (target != null) AppColors.OnAccent else AppColors.Faint, modifier = Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun AiChapterContent(modifier: Modifier, hasAi: Boolean, onRecap: () -> Unit, onQuiz: () -> Unit, onTeacher: () -> Unit, onHeroes: () -> Unit, onHistory: () -> Unit, onSetup: () -> Unit) {
    Column(modifier.revealScroll(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (hasAi) {
            AiChapterButton(AppIcons.Book, "Пересказ главы", "Что было до этого места", onRecap)
            AiChapterButton(AppIcons.Check, "Тест по главе", "5 вопросов, ответы проверит ИИ", onQuiz)
            AiChapterButton(AppIcons.Pencil, "ИИ-Учитель", "Отвечай своими словами, ИИ разберёт", onTeacher)
            AiChapterButton(AppIcons.Voice, "Герои книги", "Поговори с персонажем или автором", onHeroes)
            AiChapterButton(AppIcons.Clock, "История", "Прошлые чаты с ИИ по этой книге", onHistory)
            Text(
                "ИИ видит только прочитанную часть главы",
                color = AppColors.Faint,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        } else {
            AiChapterButton(AppIcons.Sparkle, "Подключить нейросеть", "Нужен ключ любой нейросети", onSetup)
        }
    }
}

@Composable
private fun AiChapterButton(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    val shape = appCorner(16)
    Row(
        Modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onClick = onClick)
            .fieldSurface(shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(appCorner(12)).background(AppColors.AccentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                hint,
                color = AppColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
            )
        }
        Icon(AppIcons.Forward, null, tint = AppColors.Faint, modifier = Modifier.size(18.dp))
    }
}


@Composable
private fun ReaderCoach(scroll: Boolean, leftForward: Boolean, onDone: () -> Unit) {
    val loop = androidx.compose.animation.core.rememberInfiniteTransition(label = "coach")
    val pulse by loop.animateFloat(
        0f,
        1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400), androidx.compose.animation.core.RepeatMode.Restart),
        label = "coach-pulse",
    )
    val accent = AppColors.Accent
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.74f))
            .pointerInput(Unit) { detectTapGestures { onDone() } },
    ) {
        if (!scroll) {
            Row(Modifier.fillMaxSize()) {
                listOf(
                    (if (leftForward) "Вперёд" else "Назад") to AppIcons.Back,
                    "Меню" to AppIcons.Options,
                    "Вперёд" to AppIcons.Chevron,
                ).forEachIndexed { i, (label, icon) ->
                    Box(
                        Modifier
                            .weight(if (i == 1) 1.2f else 1f)
                            .fillMaxHeight()
                            .then(if (i == 1) Modifier.background(Color.White.copy(alpha = 0.04f)) else Modifier)
                            .drawBehind {
                                if (i > 0) drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, size.height * 0.2f), Offset(0f, size.height * 0.8f), 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(Modifier.size(56.dp).graphicsLayer { scaleX = 1f + pulse * 0.5f; scaleY = 1f + pulse * 0.5f; alpha = 1f - pulse }.clip(CircleShape).background(accent.copy(alpha = 0.5f)))
                                Box(Modifier.size(48.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                                    Icon(icon, null, tint = AppColors.OnAccent, modifier = Modifier.size(22.dp))
                                }
                            }
                            Text("Тап", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(56.dp).graphicsLayer { scaleX = 1f + pulse * 0.5f; scaleY = 1f + pulse * 0.5f; alpha = 1f - pulse }.clip(CircleShape).background(accent.copy(alpha = 0.5f)))
                    Box(Modifier.size(48.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Options, null, tint = AppColors.OnAccent, modifier = Modifier.size(22.dp))
                    }
                }
                Text("Тап — меню", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Листай пальцем вверх. В конце главы потяни дальше — откроется следующая", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars).padding(start = 24.dp, end = 24.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.clip(appCorner(16)).background(Color.White.copy(alpha = 0.1f)).border(1.dp, Color.White.copy(alpha = 0.18f), appCorner(16)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(AppIcons.Select, null, tint = accent, modifier = Modifier.size(22.dp))
                Text("Зажми слово — заметка, перевод, словарь, персонаж", color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Text("Нажми, чтобы начать", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SelectionNextBar(handle: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val decor = com.flerty.wikiflerty.ui.AppStyle.kit.decor
    val accent = com.flerty.wikiflerty.ui.AppColors.Accent
    val text = com.flerty.wikiflerty.ui.AppColors.Text
    val panel = com.flerty.wikiflerty.ui.AppColors.Panel
    val onAccent = com.flerty.wikiflerty.ui.AppColors.OnAccent
    val ink = Color(0xFF141210)
    val gold = Color(0xFFE7C77E)
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "next")
    val nudge by pulse.animateFloat(
        0f,
        1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "next-nudge",
    )
    val move = if (Motion.enabled) nudge else 0f
    val label = if (decor == com.flerty.wikiflerty.ui.Decor.News || decor == com.flerty.wikiflerty.ui.Decor.Comic) "ДАЛЕЕ" else "Далее"
    val labelColor = when (decor) {
        com.flerty.wikiflerty.ui.Decor.Library -> gold
        com.flerty.wikiflerty.ui.Decor.Zen, com.flerty.wikiflerty.ui.Decor.News -> text
        com.flerty.wikiflerty.ui.Decor.Comic -> ink
        com.flerty.wikiflerty.ui.Decor.Sea, com.flerty.wikiflerty.ui.Decor.Clay -> Color.White
        com.flerty.wikiflerty.ui.Decor.None -> Color.White
    }
    val shape = when (decor) {
        com.flerty.wikiflerty.ui.Decor.News -> RoundedCornerShape(2.dp)
        com.flerty.wikiflerty.ui.Decor.Library -> RoundedCornerShape(12.dp)
        com.flerty.wikiflerty.ui.Decor.Comic -> RoundedCornerShape(10.dp)
        com.flerty.wikiflerty.ui.Decor.Zen -> RoundedCornerShape(4.dp)
        else -> RoundedCornerShape(50)
    }
    Box(
        modifier
            .height(34.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.92f, onClick = onClick)
            .semantics { contentDescription = "Продолжить выделение на следующей странице" }
            .drawBehind {
                val d = density
                val w = size.width
                val h = size.height
                when (decor) {
                    com.flerty.wikiflerty.ui.Decor.Library -> {
                        drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(0f, 2 * d), size, androidx.compose.ui.geometry.CornerRadius(12 * d))
                        drawRoundRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF4A2C1A), Color(0xFF2B190E))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12 * d))
                        drawRoundRect(gold.copy(alpha = 0.85f), Offset(2.5f * d, 2.5f * d), androidx.compose.ui.geometry.Size(w - 5 * d, h - 5 * d), androidx.compose.ui.geometry.CornerRadius(9.5f * d), style = androidx.compose.ui.graphics.drawscope.Stroke(1.1f * d))
                        drawRoundRect(gold.copy(alpha = 0.3f), Offset(5 * d, 5 * d), androidx.compose.ui.geometry.Size(w - 10 * d, h - 10 * d), androidx.compose.ui.geometry.CornerRadius(7 * d), style = androidx.compose.ui.graphics.drawscope.Stroke(0.6f * d))
                    }
                    com.flerty.wikiflerty.ui.Decor.Zen -> {
                        drawRoundRect(panel.copy(alpha = 0.96f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4 * d))
                        val stroke = androidx.compose.ui.graphics.Path().apply {
                            moveTo(10 * d, h - 7 * d)
                            cubicTo(w * 0.25f, h - 9 * d, w * 0.4f, h - 5 * d, w - 46 * d, h - 8 * d)
                        }
                        drawPath(stroke, text.copy(alpha = 0.7f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.4f * d, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        drawRect(accent, Offset(w - 38 * d, 5 * d), androidx.compose.ui.geometry.Size(30 * d, h - 10 * d))
                    }
                    com.flerty.wikiflerty.ui.Decor.Comic -> {
                        drawRoundRect(ink, Offset(3 * d, 3 * d), size, androidx.compose.ui.geometry.CornerRadius(10 * d))
                        drawRoundRect(Color(0xFFFFD43B), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10 * d))
                        drawRoundRect(ink, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10 * d), style = androidx.compose.ui.graphics.drawscope.Stroke(2.6f * d))
                    }
                    com.flerty.wikiflerty.ui.Decor.News -> {
                        drawRect(panel)
                        drawRect(text, Offset(0f, 0f), androidx.compose.ui.geometry.Size(w, 2.4f * d))
                        drawRect(text, Offset(0f, 4.6f * d), androidx.compose.ui.geometry.Size(w, 0.8f * d))
                        drawRect(text, Offset(0f, h - 0.8f * d), androidx.compose.ui.geometry.Size(w, 0.8f * d))
                    }
                    com.flerty.wikiflerty.ui.Decor.Sea -> {
                        drawRoundRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.3f))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
                        drawRoundRect(Color.White.copy(alpha = 0.55f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(1.3f * d))
                        drawRoundRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent), endY = h * 0.5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
                        drawCircle(Color.White.copy(alpha = 0.5f), 2f * d, Offset(10 * d, 9 * d - move * 2 * d))
                        drawCircle(Color.White.copy(alpha = 0.35f), 1.3f * d, Offset(15 * d, 13 * d - move * 1.5f * d))
                    }
                    com.flerty.wikiflerty.ui.Decor.Clay -> {
                        drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(0f, 4 * d), size, androidx.compose.ui.geometry.CornerRadius(h / 2))
                        drawRoundRect(accent, cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
                        drawRoundRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.12f))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
                    }
                    com.flerty.wikiflerty.ui.Decor.None -> {
                        drawRoundRect(Color.Black.copy(alpha = 0.2f), Offset(0f, 2 * d), size, androidx.compose.ui.geometry.CornerRadius(h / 2))
                        drawRoundRect(handle, cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = labelColor,
                fontSize = 13.sp,
                fontWeight = if (decor == com.flerty.wikiflerty.ui.Decor.Comic) FontWeight.ExtraBold else FontWeight.SemiBold,
                letterSpacing = if (decor == com.flerty.wikiflerty.ui.Decor.News) 1.sp else 0.sp,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(if (decor == com.flerty.wikiflerty.ui.Decor.Zen) 10.dp else 4.dp))
            val arrowColor = when (decor) {
                com.flerty.wikiflerty.ui.Decor.Zen -> Color.White
                else -> labelColor
            }
            androidx.compose.foundation.Canvas(Modifier.size(width = 28.dp, height = 14.dp).padding(horizontal = 5.dp).graphicsLayer { translationX = move * 2.dp.toPx() }) {
                val d = density
                val y = size.height / 2f
                val head = size.height * 0.42f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(1 * d, y); lineTo(size.width - 1 * d, y)
                    moveTo(size.width - 1 * d - head, y - head); lineTo(size.width - 1 * d, y); lineTo(size.width - 1 * d - head, y + head)
                }
                val sw = if (decor == com.flerty.wikiflerty.ui.Decor.Comic || decor == com.flerty.wikiflerty.ui.Decor.Clay) 3f * d else 2.2f * d
                drawPath(path, arrowColor, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}
