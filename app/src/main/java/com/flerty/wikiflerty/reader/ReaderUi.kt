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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

/** Space between a floating panel and the screen edge, the same at the top and the bottom. */
private val PanelGap = 52.dp
private const val FlagHeight = 24
/** Widest a floating panel or sheet gets (tablets). */
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
    NoteEditor("Заметка", 3),
    AiMenu("ИИ", 0),
    AiChat("ИИ", 0),
    AiSetup("Нейросети", 1),
}

/** What the reading screen shows right now. Changed by taps and by the book position. */
class ReaderUiState {
    var panelsVisible by mutableStateOf(false)
    var sheet by mutableStateOf<ReaderSheet?>(null)
    var progression by mutableStateOf(0.0)
    var position by mutableStateOf<Int?>(null)
    var totalPositions by mutableIntStateOf(0)
    var chapter by mutableStateOf<String?>(null)
    /** Shown while the brightness is being changed by a swipe, 0..100. */
    var brightnessHint by mutableStateOf<Int?>(null)
    /** Text selected in the book; shows the selection menu. */
    var selection by mutableStateOf<SelectionInfo?>(null)
    /** The note open in the editor sheet. */
    var editor by mutableStateOf<NoteDraft?>(null)
    /** "Нейросети" was opened from the AI menu, so "back" returns there. */
    var aiSetupFromMenu by mutableStateOf(false)
    /** Reading aloud: is it running for this book, is it playing, a problem to show, the sleep timer. */
    var ttsActive by mutableStateOf(false)
    var ttsPlaying by mutableStateOf(false)
    var ttsProblem by mutableStateOf<com.flerty.wikiflerty.tts.ReadAloud.Problem?>(null)
    var ttsSleep by mutableStateOf<com.flerty.wikiflerty.tts.SleepTimer?>(null)
    /** The end of the book is reached: offer to mark it as read. */
    var askFinished by mutableStateOf(false)

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

private val SelectedSegment = Color(0xFF30343B)
private val FlagBackground = Color(0xE61B1D21)

/** Taps on a panel must not fall through to the book page underneath. */
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
) {
    val progressText = progressText(state, settings)
    val feedback = LocalFeedback.current
    val view = LocalView.current

    BackHandler(enabled = state.sheet != null || state.panelsVisible || state.selection != null) {
        val sheet = state.sheet
        when {
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
        // Swipe up/down at the left edge changes the brightness; a tap there still turns the page.
        if (settings.brightnessSwipe && !settings.scroll && state.sheet == null) {
            BrightnessStrip(
                settings = settings,
                state = state,
                onEdgeTap = { onEdgeTap(true) },
                onPreview = onBrightnessPreview,
                onCommit = { value -> onSettingsChange { it.copy(brightness = value) } },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        // Tap outside an open sheet closes it.
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
            ProgressFlag(
                chapter = state.chapter,
                progress = progressText,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = FlagBottom),
            )
        }

        AnimatedVisibility(
            visible = state.ttsActive && state.sheet == null && !state.panelsVisible,
            enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = PanelGap),
        ) {
            TtsBar(state.ttsPlaying, settings.ttsSpeed, tts)
        }

        val problem = state.ttsProblem
        AnimatedVisibility(
            visible = problem != null && state.sheet == null,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = PanelGap + 80.dp),
        ) {
            val last = remember { arrayOf<com.flerty.wikiflerty.tts.ReadAloud.Problem?>(null) }
            problem?.let { last[0] = it }
            last[0]?.let { TtsProblemPrompt(it, onInstall = { state.ttsProblem = null; onInstallVoices() }, onDismiss = { state.ttsProblem = null }) }
        }

        AnimatedVisibility(
            visible = state.askFinished && state.sheet == null && !state.panelsVisible,
            enter = fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 2 },
            exit = fadeOut(tween(180)),
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
            enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { -it / 2 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopPanel(title, cover, state.progression, progressText, onBack, onListen = { state.sheet = ReaderSheet.Listen })
        }

        AnimatedVisibility(
            visible = state.panelsVisible && state.sheet == null,
            enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 2 },
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
                        QuickButton.Listen -> state.sheet = ReaderSheet.Listen
                    }
                },
                onOptions = { state.sheet = ReaderSheet.Options },
            )
        }

        AnimatedVisibility(
            visible = state.sheet != null,
            enter = fadeIn(tween(200)) + slideInVertically(tween(280)) { it / 3 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // Keep showing the last sheet while it animates out.
            val last = remember { arrayOf(ReaderSheet.Options) }
            state.sheet?.let { last[0] = it }
            SheetFrame {
                AnimatedContent(
                    targetState = last[0],
                    transitionSpec = {
                        val deeper = targetState.depth > initialState.depth
                        val dir = if (deeper) 1 else -1
                        (slideInHorizontally(tween(260)) { dir * it / 5 } + fadeIn(tween(220)))
                            .togetherWith(slideOutHorizontally(tween(220)) { -dir * it / 5 } + fadeOut(tween(160)))
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
                        sheet = sheet,
                        settings = settings,
                        fonts = fonts,
                        toc = toc,
                        notes = notes,
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

    // Menu for the selected text, just above it (or below if there is no room).
    val selection = state.selection
    AnimatedVisibility(
        visible = selection != null && state.sheet == null,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(160)),
    ) {
        val last = remember { arrayOf<SelectionInfo?>(null) }
        selection?.let { last[0] = it }
        val info = last[0] ?: return@AnimatedVisibility
        val density = androidx.compose.ui.platform.LocalDensity.current
        val menuHeight = with(density) { 58.dp.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        val y = if (info.top - menuHeight - gap > with(density) { 24.dp.toPx() }) {
            info.top - menuHeight - gap
        } else {
            info.bottom + gap
        }
        SelectionMenu(
            markerColor = settings.markerColor,
            actions = noteActions,
            modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(0, y.roundToInt()) },
        )
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

// ---------------------------------------------------------------- panels

@Composable
private fun ProgressFlag(chapter: String?, progress: String, modifier: Modifier) {
    val shape = appCorner(12)
    Row(
        modifier = modifier
            .height(FlagHeight.dp)
            .clip(shape)
            .background(FlagBackground)
            .border(1.dp, Color(0xFF2C2F34), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!chapter.isNullOrBlank()) {
            Text(
                chapter,
                color = AppColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
            Box(Modifier.size(3.dp).clip(CircleShape).background(Color(0xFF5A5E66)))
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
    onBack: () -> Unit,
    onListen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = PanelGap)
            // On tablets panels stay phone-sized and centered; on phones nothing changes.
            .widthIn(max = PanelMaxWidth)
            .fillMaxWidth()
            .height(76.dp)
            .panelSurface(appCorner(22))
            .blockTaps()
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoundIconButton(AppIcons.Back, "Назад в библиотеку", sound = FeedbackSound.Close, onClick = onBack)
        Box(
            Modifier
                .size(width = 38.dp, height = 56.dp)
                .clip(appCorner(6))
                .background(Color(0xFF4A5A7A))
                .border(1.dp, Color(0x33FFFFFF), appCorner(6)),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(title.take(1), color = AppColors.Text, fontFamily = FontFamily.Serif, fontSize = 18.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                title,
                color = AppColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThinProgress(progression, Modifier.weight(1f))
                Text(progressText, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1)
            }
        }
        RoundIconButton(AppIcons.Headphones, "Чтение вслух", sound = FeedbackSound.Open, onClick = onListen)
    }
}

@Composable
private fun ThinProgress(progression: Double, modifier: Modifier) {
    val fraction = progression.toFloat().coerceIn(0f, 1f)
    Box(modifier.height(4.dp).clip(appCorner(2)).background(AppColors.Line)) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(appCorner(2))
                    .background(Brush.horizontalGradient(listOf(AppColors.AccentDark, AppColors.Accent))),
            )
        }
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
            // On tablets panels stay phone-sized and centered; on phones nothing changes.
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
        // A thin line between the quick buttons and «Опции».
        Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(34.dp).background(AppColors.Line))
        PanelButton("Опции", sound = FeedbackSound.Open, onClick = onOptions, modifier = Modifier.weight(1f)) {
            Icon(AppIcons.Options, contentDescription = null, tint = AppColors.Text, modifier = Modifier.size(22.dp))
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
    Column(
        modifier = modifier
            .height(56.dp)
            .liveClick(shape, sound = sound, pressedScale = 0.9f, onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(3.dp))
        // Long words shrink a little instead of running into the next button.
        BasicText(
            label,
            style = TextStyle(color = AppColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuickIcon(button: QuickButton, tint: Color) {
    when (button) {
        QuickButton.Font -> Icon(AppIcons.TextSize, null, tint = tint, modifier = Modifier.size(23.dp))
        else -> Icon(quickIconVector(button), null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

private fun quickIconVector(button: QuickButton): ImageVector = when (button) {
    QuickButton.Toc -> AppIcons.Toc
    QuickButton.Font -> AppIcons.Toc
    QuickButton.Theme -> AppIcons.Theme
    QuickButton.Scroll -> AppIcons.Pages
    QuickButton.Screen -> AppIcons.Screen
    QuickButton.Notes -> AppIcons.Note
    QuickButton.Listen -> AppIcons.Headphones
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
            .background(if (filled) Color(0xFF2C2F35) else Color.Transparent),
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

// ---------------------------------------------------------------- sheet

@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = PanelGap)
            // On tablets panels stay phone-sized and centered; on phones nothing changes.
            .widthIn(max = PanelMaxWidth)
            .fillMaxWidth()
            .imePadding()
            .heightIn(max = 580.dp)
            .panelSurface(appCorner(26), elevation = 20.dp)
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
) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Handle bar
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(appCorner(2))
                .background(Color(0xFF3A3E45)),
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
            Text(
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
            ReaderSheet.Toc -> TocList(toc, onTocSelect, Modifier.weight(1f, fill = false))
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
            ReaderSheet.Notes -> BookNotesList(
                notes = notes,
                onOpen = noteActions.onGoTo,
                onEdit = onEditNote,
                modifier = Modifier.weight(1f, fill = false),
            )
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
                    ReaderSheet.Scroll -> ScrollOptions(settings, onSettingsChange)
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
    data class Tile(val target: ReaderSheet, val hint: String, val icon: ImageVector?)
    val tiles = listOf(
        Tile(ReaderSheet.Text, "Шрифт, размер", AppIcons.TextSize),
        Tile(ReaderSheet.Scroll, "Режим, анимация", AppIcons.Pages),
        Tile(ReaderSheet.Theme, "Фон страницы", AppIcons.Theme),
        Tile(ReaderSheet.Panel, "Кнопки, прогресс", AppIcons.Panel),
        Tile(ReaderSheet.Gestures, "Тапы, свайпы", AppIcons.Gesture),
        Tile(ReaderSheet.Screen, "Яркость, поворот", AppIcons.Screen),
        Tile(ReaderSheet.Listen, "Голос, скорость", AppIcons.Headphones),
        Tile(ReaderSheet.Sound, "Звуки, вибрация", AppIcons.Sound),
        Tile(ReaderSheet.AiSetup, "Нейросети, модели", AppIcons.Sparkle),
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
                                .size(36.dp)
                                .clip(appCorner(11))
                                .background(Brush.linearGradient(listOf(AppColors.Accent.copy(alpha = 0.28f), AppColors.Accent.copy(alpha = 0.1f))))
                                .border(1.dp, AppColors.Accent.copy(alpha = 0.2f), appCorner(11)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (tile.icon == null) {
                                Text("Aa", color = AppColors.Accent, fontFamily = FontFamily.Serif, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Icon(tile.icon, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text(tile.target.title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(tile.hint, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Segmented(
        "Колонки",
        listOf("Авто", "Одна", "Две"),
        when (settings.columns) {
            "1" -> 1
            "2" -> 2
            else -> 0
        },
        icon = AppIcons.Pages,
    ) { i -> onSettingsChange { it.copy(columns = listOf("auto", "1", "2")[i]) } }
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
private fun ScrollOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
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
}

@Composable
private fun ThemeOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    Group("Фон страницы") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageTheme.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { theme ->
                        val selected = settings.pageTheme == theme
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

// ---------------------------------------------------------------- small controls

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
        Modifier.size(24.dp).clip(shape).background(bg).border(2.dp, if (checked) AppColors.Accent else Color(0xFF4A4E56), shape),
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
                        .then(if (isSelected) Modifier.border(1.dp, Color(0xFF41454D), shape) else Modifier),
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

/** "Книга дочитана?" with a button to mark it as read. */
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
