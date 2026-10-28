package com.flerty.wikiflerty.reader

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
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
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
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface
import java.util.Locale
import kotlin.math.roundToInt
import org.readium.r2.shared.publication.Link

private val PanelGap = 52.dp
private const val FlagHeight = 24
internal val PanelMaxWidth = 620.dp

private val FlagBottom = ((52 - FlagHeight) / 2).dp

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
    Listen("Чтение вслух", 1),
    Fonts("Шрифт", 2),
    Notes("Заметки", 0),
    Bookmarks("Закладки", 0),
    Quote("Цитата", 0),
    NoteEditor("Заметка", 3),
    AiMenu("ИИ", 0),
    AiChat("ИИ", 0),
    AiSetup("Нейросети", 1),
    Search("Поиск", 0),
    Jump("Переход", 0),
    AiChapter("ИИ по главе", 1),
}

class ReaderUiState {
    var panelsVisible by mutableStateOf(false)
    var sheet by mutableStateOf<ReaderSheet?>(null)
    var progression by mutableStateOf(0.0)
    var position by mutableStateOf<Int?>(null)
    var totalPositions by mutableIntStateOf(0)
    var chapter by mutableStateOf<String?>(null)
    var brightnessHint by mutableStateOf<Int?>(null)
    var selection by mutableStateOf<SelectionInfo?>(null)
    var footnote by mutableStateOf<FootnoteInfo?>(null)
    var autoActive by mutableStateOf(false)
    var autoPaused by mutableStateOf(false)
    var autoProgress by mutableFloatStateOf(0f)
    var autoCollapsed by mutableStateOf(false)
    var editor by mutableStateOf<NoteDraft?>(null)
    var aiSetupFromMenu by mutableStateOf(false)
    var ttsActive by mutableStateOf(false)
    var ttsPlaying by mutableStateOf(false)
    var ttsProblem by mutableStateOf<com.flerty.wikiflerty.tts.ReadAloud.Problem?>(null)
    var ttsSleep by mutableStateOf<com.flerty.wikiflerty.tts.SleepTimer?>(null)
    var askFinished by mutableStateOf(false)
    var bookmarked by mutableStateOf(false)
    var quote by mutableStateOf<QuoteDraft?>(null)

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

private val SelectedSegment get() = AppColors.AccentTintStrong
private val FlagBackground = Color(0xE61B1D21)

private fun Modifier.blockTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

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
    onEdgeTap: (left: Boolean) -> Unit,
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
) {
    val progressText = progressText(state, settings)
    val feedback = LocalFeedback.current
    val view = LocalView.current

    BackHandler(enabled = state.sheet != null || state.panelsVisible || state.selection != null || state.footnote != null) {
        val sheet = state.sheet
        when {
            state.footnote != null -> state.footnote = null
            state.selection != null && sheet == null -> noteActions.onSelectionClose()
            sheet == null -> state.panelsVisible = false
            sheet == ReaderSheet.Fonts -> state.sheet = ReaderSheet.Text
            sheet == ReaderSheet.NoteEditor -> state.sheet = if (state.editor?.fromList == true) ReaderSheet.Notes else null
            sheet == ReaderSheet.AiSetup -> state.sheet = if (state.aiSetupFromMenu) ReaderSheet.AiMenu else ReaderSheet.Options
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
                onEdgeTap = { onEdgeTap(true) },
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

        AnimatedVisibility(
            visible = state.bookmarked && !state.panelsVisible,
            enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(320)) { -it },
            exit = fadeOut(Motion.fade(160)) + slideOutVertically(Motion.tween(220)) { -it },
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 22.dp),
        ) {
            PageRibbon()
        }

        if (settings.showFlag) {
            ProgressFlag(
                chapter = state.chapter,
                progress = progressText,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = FlagBottom),
            )
        }

        AnimatedVisibility(
            visible = state.ttsActive && state.sheet == null && !state.panelsVisible,
            enter = fadeIn(Motion.fade(220)) + slideInVertically(Motion.tween(260)) { it / 2 },
            exit = fadeOut(Motion.fade(180)) + slideOutVertically(Motion.tween(220)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = PanelGap),
        ) {
            TtsBar(state.ttsPlaying, settings.ttsSpeed, tts)
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
            last[0]?.let { TtsProblemPrompt(it, onInstall = { state.ttsProblem = null; onInstallVoices() }, onDismiss = { state.ttsProblem = null }) }
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
            enter = fadeIn(Motion.fade(220)) + slideInVertically(Motion.tween(260)) { -it / 2 },
            exit = fadeOut(Motion.fade(180)) + slideOutVertically(Motion.tween(220)) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopPanel(
                title = title,
                cover = cover,
                progression = state.progression,
                progressText = progressText,
                totalPositions = state.totalPositions,
                onBack = onBack,
                onListen = { state.sheet = ReaderSheet.Listen },
                onSearch = { state.sheet = ReaderSheet.Search },
                onSeek = onSeek,
                bookmarked = state.bookmarked,
                onBookmark = bookmarkActions.onToggle,
                onJump = { state.sheet = ReaderSheet.Jump },
            )
        }

        AnimatedVisibility(
            visible = state.panelsVisible && state.sheet == null,
            enter = fadeIn(Motion.fade(220)) + slideInVertically(Motion.tween(260)) { it / 2 },
            exit = fadeOut(Motion.fade(180)) + slideOutVertically(Motion.tween(220)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
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
                        QuickButton.Listen -> state.sheet = ReaderSheet.Listen
                        QuickButton.Search -> state.sheet = ReaderSheet.Search
                        QuickButton.Auto -> auto.onStart()
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
                        sheet = sheet,
                        settings = settings,
                        fonts = fonts,
                        toc = toc,
                        notes = notes,
                        bookmarks = bookmarks,
                        bookmarkActions = bookmarkActions,
                        quote = state.quote,
                        quoteActions = quoteActions,
                        editor = state.editor,
                        noteActions = noteActions,
                        ai = ai,
                        aiSetupFromMenu = state.aiSetupFromMenu,
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
        modifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.systemBars).padding(start = 16.dp, bottom = 8.dp),
    ) {
        AutoMini(
            paused = state.autoPaused,
            progress = if (settings.scroll) null else state.autoProgress,
            onExpand = { state.autoCollapsed = false },
        )
    }

    AnimatedVisibility(
        visible = state.footnote != null && state.sheet == null,
        enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(260)) { it / 3 },
        exit = fadeOut(Motion.fade(160)) + slideOutVertically(Motion.tween(200)) { it / 3 },
        modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars).padding(bottom = 16.dp),
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

    val selection = state.selection
    AnimatedVisibility(
        visible = selection != null && state.sheet == null,
        enter = fadeIn(Motion.fade(220)),
        exit = fadeOut(Motion.fade(160)),
    ) {
        val last = remember { arrayOf<SelectionInfo?>(null) }
        selection?.let { last[0] = it }
        val info = last[0] ?: return@AnimatedVisibility
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val menuHeight = with(density) { 58.dp.toPx() }
            val gap = with(density) { 12.dp.toPx() }
            val minY = with(density) { 24.dp.toPx() }
            val maxY = with(density) { maxHeight.toPx() } - menuHeight - with(density) { 72.dp.toPx() }
            val above = info.top - menuHeight - gap
            val below = info.bottom + gap
            val y = when {
                above >= minY -> above
                below <= maxY -> below
                else -> maxY
            }.coerceIn(minY, maxOf(minY, maxY))
            SelectionMenu(
                markerColor = settings.markerColor,
                parts = info.parts,
                actions = noteActions,
                modifier = Modifier.fillMaxWidth().offset { androidx.compose.ui.unit.IntOffset(0, y.roundToInt()) },
            )
        }
    }
    }
}

private fun progressText(state: ReaderUiState, settings: ReaderSettings): String {
    val position = state.position
    return if (settings.progressInPages && position != null && state.totalPositions > 0) {
        "стр. $position из ${state.totalPositions}"
    } else {
        "${(state.progression * 100).roundToInt().coerceIn(0, 100)}%"
    }
}

@Composable
private fun ProgressFlag(chapter: String?, progress: String, modifier: Modifier) {
    val shape = appCorner(12)
    Row(
        modifier = modifier
            .height(FlagHeight.dp)
            .clip(shape)
            .background(FlagBackground)
            .border(1.dp, AppColors.PanelBorder, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!chapter.isNullOrBlank()) {
            MarqueeText(
                chapter,
                color = AppColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.widthIn(max = 200.dp),
            )
            Box(Modifier.size(3.dp).clip(CircleShape).background(AppColors.Faint))
        }
        Text(progress, color = AppColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TopPanel(
    title: String,
    cover: ImageBitmap?,
    progression: Double,
    progressText: String,
    totalPositions: Int,
    onBack: () -> Unit,
    onListen: () -> Unit,
    onSearch: () -> Unit,
    onSeek: (Double) -> Unit,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onJump: () -> Unit,
) {
    var preview by remember { mutableStateOf<Double?>(null) }
    Row(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = PanelGap)
            .widthIn(max = PanelMaxWidth)
            .fillMaxWidth()
            .height(76.dp)
            .panelSurface(appCorner(22))
            .blockTaps()
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RoundIconButton(AppIcons.Back, "Назад в библиотеку", sound = FeedbackSound.Close, onClick = onBack)
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
        Column(Modifier.weight(1f).padding(start = 8.dp, end = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MarqueeText(title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeekBar(
                    progression = progression,
                    onPreview = { preview = it },
                    onSeek = onSeek,
                    modifier = Modifier.weight(1f),
                )
                val label = preview?.let { f ->
                    if (totalPositions > 0) "стр. ${(f * (totalPositions - 1)).roundToInt() + 1}" else "${(f * 100).roundToInt()}%"
                } ?: progressText
                Text(
                    label,
                    color = if (preview != null) AppColors.Accent else AppColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = if (preview != null) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier
                        .liveClick(appCorner(8), sound = FeedbackSound.Open, pressedScale = 0.9f, onClick = onJump)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
        BookmarkButton(bookmarked, onBookmark)
        RoundIconButton(AppIcons.Search, "Поиск по книге", sound = FeedbackSound.Open, onClick = onSearch)
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
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val preview by rememberUpdatedState(onPreview)
    val seek by rememberUpdatedState(onSeek)
    val fraction = drag ?: progression.toFloat().coerceIn(0f, 1f)
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
                    drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    preview(drag?.toDouble())
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val width = maxWidth
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
                .offset(x = (width * fraction - thumb / 2).coerceIn(0.dp, maxOf(0.dp, width - thumb)))
                .size(thumb)
                .clip(CircleShape)
                .background(AppColors.Accent)
                .border(2.dp, Color(0x55FFFFFF), CircleShape),
        )
    }
}

@Composable
private fun BottomPanel(
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
    QuickButton.Listen -> AppIcons.Headphones
    QuickButton.Search -> AppIcons.Search
    QuickButton.Auto -> AppIcons.Play
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
    onEdgeTap: () -> Unit,
    onPreview: (Double) -> Unit,
    onCommit: (Double) -> Unit,
    modifier: Modifier,
) {
    val current by rememberUpdatedState(settings.brightness)
    val tap by rememberUpdatedState(onEdgeTap)
    var value by remember { mutableFloatStateOf(0.5f) }
    Box(
        modifier
            .fillMaxHeight()
            .width(28.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { tap() }) }
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
private fun SheetFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = PanelGap)
            .widthIn(max = PanelMaxWidth)
            .fillMaxWidth()
            .imePadding()
            .heightIn(max = 580.dp)
            .panelSurface(appCorner(26), elevation = 20.dp)
            .drawBehind {
                val glow = AppStyle.base.ambience?.spots?.firstOrNull()?.color?.let { Color(it) } ?: AppColors.Accent
                val center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, 0f)
                drawCircle(
                    Brush.radialGradient(listOf(glow.copy(alpha = 0.22f), glow.copy(alpha = 0f)), center = center, radius = size.width * 0.8f),
                    radius = size.width * 0.8f,
                    center = center,
                )
            }
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
    quote: QuoteDraft?,
    quoteActions: QuoteActions,
    editor: NoteDraft?,
    noteActions: NoteActions,
    ai: AiBridge,
    aiSetupFromMenu: Boolean,
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
                sheet == ReaderSheet.NoteEditor -> if (editor?.fromList == true) ReaderSheet.Notes else null
                sheet == ReaderSheet.AiSetup -> if (aiSetupFromMenu) ReaderSheet.AiMenu else ReaderSheet.Options
                sheet.depth > 0 -> ReaderSheet.Options
                else -> null
            }
            if (parent != null) {
                RoundIconButton(AppIcons.Back, "Назад", sound = FeedbackSound.Close) { onOpen(parent) }
            }
            if (sheet in NavSheets) {
                NavTabs(sheet, onSelect = onOpen, modifier = Modifier.weight(1f).padding(end = 6.dp))
            } else Text(
                when {
                    sheet == ReaderSheet.NoteEditor && editor?.id == null -> "Новая заметка"
                    sheet == ReaderSheet.Notes && notes.isNotEmpty() -> "Заметки · ${notes.size}"
                    sheet == ReaderSheet.AiChat -> ai.chat.action.title
                    else -> sheet.title
                },
                color = AppColors.Text,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (parent != null) 0.dp else 4.dp),
            )
            RoundIconButton(AppIcons.Close, "Закрыть", filled = true, sound = FeedbackSound.Close, onClick = onClose)
        }

        when (sheet) {
            ReaderSheet.Toc, ReaderSheet.Notes, ReaderSheet.Bookmarks -> AnimatedContent(
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
                    else -> TocList(toc, onTocSelect, Modifier)
                }
            }
            ReaderSheet.Quote -> if (quote != null) {
                QuoteContent(quote, quoteActions, Modifier.weight(1f, fill = false))
            }
            ReaderSheet.Search -> SearchContent(search, Modifier.weight(1f, fill = false))
            ReaderSheet.Jump -> JumpContent(jump, onClose)
            ReaderSheet.AiChapter -> AiChapterContent(
                hasAi = ai.manager.current() != null,
                onRecap = { ai.onChapter(AiAction.Recap); onOpen(ReaderSheet.AiChat) },
                onQuiz = { ai.onChapter(AiAction.Quiz); onOpen(ReaderSheet.AiChat) },
                onSetup = { onOpen(ReaderSheet.AiSetup) },
            )
            ReaderSheet.Fonts -> FontList(fonts, settings, onSettingsChange, Modifier.weight(1f, fill = false))
            ReaderSheet.AiMenu -> AiMenuContent(
                bridge = ai,
                onSetup = { onOpen(ReaderSheet.AiSetup) },
                onStarted = { onOpen(ReaderSheet.AiChat) },
                modifier = Modifier.weight(1f, fill = false),
            )
            ReaderSheet.AiChat -> AiChatContent(ai, Modifier.weight(1f))
            ReaderSheet.AiSetup -> Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
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
            else -> Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (sheet) {
                    ReaderSheet.Options -> OptionsHome(onOpen)
                    ReaderSheet.Text -> TextOptions(settings, fonts, onSettingsChange, onOpen)
                    ReaderSheet.Scroll -> ScrollOptions(settings, onSettingsChange, auto.onStart)
                    ReaderSheet.Theme -> ThemeOptions(settings, onSettingsChange)
                    ReaderSheet.Panel -> PanelOptions(settings, onSettingsChange)
                    ReaderSheet.Gestures -> GestureOptions(settings, onSettingsChange)
                    ReaderSheet.Screen -> ScreenOptions(settings, onSettingsChange)
                    ReaderSheet.Sound -> SoundOptions(settings, onSettingsChange)
                    ReaderSheet.Listen -> TtsOptions(settings, bookLanguage, ttsActive, ttsPlaying, ttsSleep, tts, onSettingsChange)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun OptionsHome(onOpen: (ReaderSheet) -> Unit) {
    data class Tile(val target: ReaderSheet, val hint: String, val icon: ImageVector, val from: Long, val to: Long, val dark: Boolean = false)
    val tiles = listOf(
        Tile(ReaderSheet.Text, "Шрифт, размер", AppIcons.TextSize, 0xFF6E8EFF, 0xFF4C63D9),
        Tile(ReaderSheet.Scroll, "Режим, анимация", AppIcons.Pages, 0xFFA78BFA, 0xFF7653E0),
        Tile(ReaderSheet.Theme, "Фон страницы", AppIcons.Theme, 0xFFF6C770, 0xFFD9922B, dark = true),
        Tile(ReaderSheet.Panel, "Кнопки, прогресс", AppIcons.Panel, 0xFF5EEAD4, 0xFF1FA392, dark = true),
        Tile(ReaderSheet.Gestures, "Тапы, свайпы", AppIcons.Gesture, 0xFFF49AC1, 0xFFD45A93),
        Tile(ReaderSheet.Screen, "Яркость, поворот", AppIcons.Screen, 0xFFF7D774, 0xFFD9A92B, dark = true),
        Tile(ReaderSheet.Listen, "Голос, скорость", AppIcons.Headphones, 0xFF8BD3A5, 0xFF3E9E66, dark = true),
        Tile(ReaderSheet.Sound, "Звуки, вибрация", AppIcons.Sound, 0xFFF6A26B, 0xFFD9692B),
        Tile(ReaderSheet.AiChapter, "Пересказ, тест", AppIcons.Book, 0xFF7DD3FC, 0xFF3B82C4),
        Tile(ReaderSheet.AiSetup, "Нейросети, модели", AppIcons.Sparkle, 0xFF8AB4F8, 0xFF7653E0),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile ->
                    val shape = appCorner(18)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 96.dp)
                            .liveClick(shape, sound = FeedbackSound.Open) { onOpen(tile.target) }
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
                            Text(tile.target.title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
            .fieldSurface(shape)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
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
    if (!entry.supportsItalic) {
        Text("У этого шрифта нет курсива", color = AppColors.Faint, fontSize = 12.sp)
    }
    SwitchRow("Переносы", settings.hyphens, icon = AppIcons.Wrap) { v -> onSettingsChange { it.copy(hyphens = v) } }
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
    if (settings.scroll) {
        Text(
            "Колонки работают в режиме «Страницы». Сейчас включена лента — переключить можно в «Листании»",
            color = AppColors.Faint,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
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
    var filter by remember { mutableStateOf<FontCategory?>(null) }
    val shown = fonts.filter { filter == null || it.category == filter }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val chips = listOf<FontCategory?>(null) + FontCategory.entries.filter { c -> fonts.any { it.category == c } }
            chips.forEach { category ->
                val selected = filter == category
                val shape = appCorner(18)
                val bg by animateColorAsState(if (selected) AppColors.Accent else AppColors.Field, label = "chip")
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .liveClick(shape, sound = FeedbackSound.Toggle) { filter = category }
                        .background(bg)
                        .border(1.dp, if (selected) AppColors.Accent else AppColors.PanelBorder, shape)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        category?.title ?: "Все",
                        color = if (selected) AppColors.OnAccent else AppColors.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.id }) { entry ->
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
                            overflow = TextOverflow.Ellipsis,
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
                    PageAnimation.Fade -> "Мягкое растворение"
                    PageAnimation.Cover -> "Новая страница накрывает старую"
                    PageAnimation.None -> "Мгновенно, без эффекта"
                },
                icon = when (a) {
                    PageAnimation.Slide -> AppIcons.Forward
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
            Text("Запустить автопрокрутку", color = accentContent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun ThemeOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    Group(if (settings.autoNight) "Днём" else "Фон страницы") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageTheme.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { theme ->
                        val selected = settings.dayTheme == theme
                        val shape = appCorner(16)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(86.dp)
                                .liveClick(shape, sound = FeedbackSound.Toggle) { onSettingsChange { it.copy(theme = theme.id) } }
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
    if (settings.autoNight) {
        val times = (0 until 48).map { it * 30 }.map { m -> PickerOption(m.toString(), "%02d:%02d".format(m / 60, m % 60)) }
        PickerRow(
            label = "Ночная тема",
            icon = AppIcons.Theme,
            options = PageTheme.entries.map { PickerOption(it.id, it.title, swatch = Color(it.background)) },
            selectedId = settings.nightTheme,
        ) { id -> onSettingsChange { it.copy(nightTheme = id) } }
        PickerRow(
            label = "Включать в",
            icon = AppIcons.Moon,
            options = times,
            selectedId = (settings.nightFrom / 30 * 30).toString(),
            searchable = false,
        ) { id -> onSettingsChange { it.copy(nightFrom = id.toInt()) } }
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
private fun PanelOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    Segmented("Прогресс", listOf("Проценты", "Страницы"), if (settings.progressInPages) 1 else 0, icon = AppIcons.Percent) { i ->
        onSettingsChange { it.copy(progressInPages = i == 1) }
    }
    SwitchRow("Флажок внизу", settings.showFlag, icon = AppIcons.Flag) { v -> onSettingsChange { it.copy(showFlag = v) } }
    Group("Кнопки панели") {
        Column(Modifier.clip(appCorner(16)).background(AppColors.Field)) {
            QuickButton.entries.forEach { button ->
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
    Hint("До пяти кнопок, в порядке выбора.")
}

@Composable
private fun GestureOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
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
private fun ScreenOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
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
private fun SoundOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
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
private fun TocList(toc: List<TocItem>, onSelect: (TocItem) -> Unit, modifier: Modifier) {
    if (toc.isEmpty()) {
        Text("В этой книге нет оглавления", color = AppColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    LazyColumn(modifier) {
        items(toc) { item ->
            val shape = appCorner(12)
            Text(
                item.title,
                color = if (item.depth == 0) AppColors.Text else AppColors.Muted,
                fontSize = if (item.depth == 0) 15.sp else 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { onSelect(item) }
                    .padding(start = (8 + item.depth * 16).dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
internal fun Group(label: String, icon: ImageVector? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingIcon(icon, 30.dp)
                Spacer(Modifier.width(12.dp))
                Text(label, color = AppColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(label, color = AppColors.Muted, fontSize = 13.sp)
        }
        content()
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
        Slider(
            value = current,
            onValueChange = { current = it },
            enabled = enabled,
            onValueChangeFinished = {
                val snapped = Math.round(current / step) * step
                onCommit(snapped.coerceIn(range.start, range.endInclusive))
            },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            steps = steps.coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = AppColors.Text,
                activeTrackColor = AppColors.Accent,
                inactiveTrackColor = AppColors.Line,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = AppColors.Faint,
                disabledActiveTrackColor = AppColors.Line,
                disabledInactiveTrackColor = AppColors.Line,
            ),
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
        Text(label, color = AppColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(
            checked = checked,
            onCheckedChange = change,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AppColors.Accent,
                checkedThumbColor = AppColors.OnAccent,
                uncheckedTrackColor = AppColors.Line,
                uncheckedThumbColor = AppColors.Faint,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
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
            if (pages) "Сейчас: стр. ${jump.position ?: 1} из ${jump.total}" else "Сейчас: ${(jump.progression * 100).roundToInt()}%",
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
private fun AiChapterContent(hasAi: Boolean, onRecap: () -> Unit, onQuiz: () -> Unit, onSetup: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (hasAi) {
            AiChapterButton(AppIcons.Book, "Пересказ главы", "Что было до этого места", onRecap)
            AiChapterButton(AppIcons.Check, "Тест по главе", "5 вопросов, ответы проверит ИИ", onQuiz)
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
