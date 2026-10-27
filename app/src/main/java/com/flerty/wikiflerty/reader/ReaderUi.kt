package com.flerty.wikiflerty.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List as TocListIcon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.ui.AppColors
import java.util.Locale
import kotlin.math.roundToInt
import org.readium.r2.shared.publication.Link

enum class ReaderSheet(val title: String) {
    Options("Опции"),
    Text("Текст"),
    Scroll("Листание"),
    Theme("Тема"),
    Panel("Панель"),
    Toc("Главы"),
}

/** What the reading screen shows right now. Changed by taps and by the book position. */
class ReaderUiState {
    var panelsVisible by mutableStateOf(false)
    var sheet by mutableStateOf<ReaderSheet?>(null)
    var progression by mutableStateOf(0.0)
    var position by mutableStateOf<Int?>(null)
    var totalPositions by mutableIntStateOf(0)
    var chapter by mutableStateOf<String?>(null)

    fun hideAll() {
        sheet = null
        panelsVisible = false
    }
}

data class TocItem(val title: String, val depth: Int, val link: Link)

private val SelectedSegment = Color(0xFF2E3238)
private val FlagBackground = Color(0xFF1B1D21)

/** Taps on a panel must not fall through to the book page underneath. */
private fun Modifier.blockTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

@Composable
fun ReaderOverlay(
    state: ReaderUiState,
    settings: ReaderSettings,
    fonts: List<ReaderFont>,
    title: String,
    cover: ImageBitmap?,
    toc: List<TocItem>,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onTocSelect: (TocItem) -> Unit,
    onBack: () -> Unit,
    onNotes: () -> Unit,
) {
    val progressText = progressText(state, settings)

    BackHandler(enabled = state.sheet != null || state.panelsVisible) {
        when {
            state.sheet == ReaderSheet.Options || state.sheet == ReaderSheet.Toc -> state.sheet = null
            state.sheet != null -> state.sheet = ReaderSheet.Options
            else -> state.panelsVisible = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Tap outside an open sheet closes it.
        if (state.sheet != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { state.sheet = null },
            )
        }

        ProgressFlag(
            chapter = state.chapter,
            progress = progressText,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        )

        AnimatedVisibility(
            visible = state.panelsVisible,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopPanel(title, cover, state.progression, progressText, onBack)
        }

        AnimatedVisibility(
            visible = state.panelsVisible && state.sheet == null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
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
                        QuickButton.Notes -> onNotes()
                    }
                },
                onOptions = { state.sheet = ReaderSheet.Options },
            )
        }

        AnimatedVisibility(
            visible = state.sheet != null,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // Keep showing the last sheet while it animates out.
            val last = remember { arrayOf(ReaderSheet.Options) }
            state.sheet?.let { last[0] = it }
            Sheet(
                sheet = last[0],
                settings = settings,
                fonts = fonts,
                toc = toc,
                onSettingsChange = onSettingsChange,
                onOpen = { state.sheet = it },
                onClose = { state.sheet = null },
                onTocSelect = {
                    state.hideAll()
                    onTocSelect(it)
                },
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
    Row(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FlagBackground)
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

private fun panelModifier(shape: RoundedCornerShape) = Modifier
    .shadow(14.dp, shape)
    .clip(shape)
    .background(AppColors.Panel)
    .border(1.dp, AppColors.PanelBorder, shape)
    .blockTaps()

@Composable
private fun TopPanel(
    title: String,
    cover: ImageBitmap?,
    progression: Double,
    progressText: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = 14.dp)
            .fillMaxWidth()
            .height(76.dp)
            .then(panelModifier(RoundedCornerShape(22.dp)))
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад в библиотеку", onBack)
        Box(
            Modifier
                .size(width = 38.dp, height = 56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF4A5A7A)),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(title.take(1), color = AppColors.Text, fontFamily = FontFamily.Serif, fontSize = 18.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    }
}

@Composable
private fun ThinProgress(progression: Double, modifier: Modifier) {
    val fraction = progression.toFloat().coerceIn(0f, 1f)
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(AppColors.Line)) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(AppColors.Accent))
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
            .padding(start = 12.dp, end = 12.dp, bottom = 44.dp)
            .fillMaxWidth()
            .height(68.dp)
            .then(panelModifier(RoundedCornerShape(22.dp)))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEach { button ->
            PanelButton(button.title, highlighted = false, onClick = { onButton(button) }, modifier = Modifier.weight(1f)) {
                QuickIcon(button, AppColors.Text)
            }
        }
        PanelButton("Опции", highlighted = true, onClick = onOptions, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun PanelButton(
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) AppColors.AccentSoft else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (highlighted) AppColors.Accent else AppColors.Text,
            fontSize = 11.sp,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickIcon(button: QuickButton, tint: Color) {
    when (button) {
        QuickButton.Toc -> Icon(Icons.AutoMirrored.Filled.TocListIcon, null, tint = tint, modifier = Modifier.size(22.dp))
        QuickButton.Font -> Text("Aa", color = tint, fontFamily = FontFamily.Serif, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        QuickButton.Theme -> HalfCircleIcon(tint)
        QuickButton.Scroll -> Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = tint, modifier = Modifier.size(22.dp))
        QuickButton.Notes -> Icon(Icons.Filled.Edit, null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun HalfCircleIcon(tint: Color) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(tint, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
        drawArc(
            color = tint,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
        )
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, filled: Boolean = false) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (filled) Color(0xFF2A2D32) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = AppColors.Text, modifier = Modifier.size(if (filled) 18.dp else 22.dp))
    }
}

// ---------------------------------------------------------------- sheet

@Composable
private fun Sheet(
    sheet: ReaderSheet,
    settings: ReaderSettings,
    fonts: List<ReaderFont>,
    toc: List<TocItem>,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onOpen: (ReaderSheet) -> Unit,
    onClose: () -> Unit,
    onTocSelect: (TocItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 44.dp)
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .then(panelModifier(RoundedCornerShape(26.dp)))
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val isSub = sheet != ReaderSheet.Options && sheet != ReaderSheet.Toc
            if (isSub) RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад к опциям", { onOpen(ReaderSheet.Options) })
            Text(
                sheet.title,
                color = AppColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (isSub) 0.dp else 4.dp),
            )
            RoundIconButton(Icons.Filled.Close, "Закрыть", onClose, filled = true)
        }

        if (sheet == ReaderSheet.Toc) {
            TocList(toc, onTocSelect, Modifier.weight(1f, fill = false))
        } else {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (sheet) {
                    ReaderSheet.Options -> OptionsHome(onOpen)
                    ReaderSheet.Text -> TextOptions(settings, fonts, onSettingsChange)
                    ReaderSheet.Scroll -> ScrollOptions(settings, onSettingsChange)
                    ReaderSheet.Theme -> ThemeOptions(settings, onSettingsChange)
                    ReaderSheet.Panel -> PanelOptions(settings, onSettingsChange)
                    ReaderSheet.Toc -> Unit
                }
            }
        }
    }
}

@Composable
private fun OptionsHome(onOpen: (ReaderSheet) -> Unit) {
    val tiles = listOf(
        Triple(ReaderSheet.Text, "Шрифт, размер", QuickButton.Font),
        Triple(ReaderSheet.Scroll, "Режим, анимация", QuickButton.Scroll),
        Triple(ReaderSheet.Theme, "Фон страницы", QuickButton.Theme),
        Triple(ReaderSheet.Panel, "Кнопки, прогресс", QuickButton.Toc),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (target, hint, iconOf) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(AppColors.Field)
                            .border(1.dp, AppColors.PanelBorder, RoundedCornerShape(18.dp))
                            .clickable { onOpen(target) }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppColors.AccentSoft),
                            contentAlignment = Alignment.Center,
                        ) { QuickIcon(iconOf, AppColors.Accent) }
                        Column {
                            Text(target.title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(hint, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextOptions(
    settings: ReaderSettings,
    fonts: List<ReaderFont>,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
) {
    Group("Шрифт") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fonts.forEach { font ->
                val selected = settings.font == font.id
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Field)
                        .border(if (selected) 2.dp else 1.dp, if (selected) AppColors.Accent else AppColors.Line, RoundedCornerShape(12.dp))
                        .clickable { onSettingsChange { it.copy(font = font.id) } }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        font.title,
                        color = AppColors.Text,
                        fontSize = 15.sp,
                        fontFamily = when (font) {
                            ReaderFont.Sans, ReaderFont.Book -> FontFamily.SansSerif
                            else -> FontFamily.Serif
                        },
                    )
                }
            }
        }
    }
    StepSlider(
        label = "Размер",
        value = settings.fontSize,
        range = 0.6..2.0,
        step = 0.1,
        format = { "${(it * 100).roundToInt()}%" },
        onCommit = { v -> onSettingsChange { it.copy(fontSize = v) } },
    )
    StepSlider(
        label = "Интервал",
        value = settings.lineHeight,
        range = 1.0..2.0,
        step = 0.1,
        format = { String.format(Locale.US, "%.1f", it) },
        onCommit = { v -> onSettingsChange { it.copy(lineHeight = v) } },
    )
    StepSlider(
        label = "Поля",
        value = settings.margins,
        range = 0.0..3.0,
        step = 0.25,
        format = { String.format(Locale.US, "%.2f", it) },
        onCommit = { v -> onSettingsChange { it.copy(margins = v) } },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleTile("Жирный", settings.bold, Modifier.weight(1f), { onSettingsChange { it.copy(bold = !it.bold) } }) {
            Text("Ж", color = AppColors.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
        ToggleTile("По ширине", settings.justify, Modifier.weight(1f), { onSettingsChange { it.copy(justify = true) } }) {
            AlignIcon(justify = true)
        }
        ToggleTile("По левому краю", !settings.justify, Modifier.weight(1f), { onSettingsChange { it.copy(justify = false) } }) {
            AlignIcon(justify = false)
        }
    }
    SwitchRow("Переносы", settings.hyphens) { v -> onSettingsChange { it.copy(hyphens = v) } }
}

@Composable
private fun ScrollOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    Segmented("Режим", listOf("Страницы", "Лента"), if (settings.scroll) 1 else 0) { i ->
        onSettingsChange { it.copy(scroll = i == 1) }
    }
    Segmented("Анимация", listOf("Сдвиг", "Нет"), if (settings.animatedTurns) 0 else 1) { i ->
        onSettingsChange { it.copy(animatedTurns = i == 0) }
    }
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(AppColors.Field).padding(horizontal = 14.dp)) {
        SwitchRow("Тап по краям", settings.edgeTaps) { v -> onSettingsChange { it.copy(edgeTaps = v) } }
        SwitchRow("Кнопки громкости", settings.volumeKeys) { v -> onSettingsChange { it.copy(volumeKeys = v) } }
    }
}

@Composable
private fun ThemeOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    Group("Фон страницы") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PageTheme.entries.forEach { theme ->
                val selected = settings.pageTheme == theme
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(theme.background))
                        .border(if (selected) 2.dp else 1.dp, if (selected) AppColors.Accent else AppColors.Line, RoundedCornerShape(14.dp))
                        .clickable { onSettingsChange { it.copy(theme = theme.id) } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Aa", color = Color(theme.text), fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(theme.title, color = Color(theme.text), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PanelOptions(settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    Segmented("Прогресс", listOf("Проценты", "Страницы"), if (settings.progressInPages) 1 else 0) { i ->
        onSettingsChange { it.copy(progressInPages = i == 1) }
    }
    Group("Кнопки панели") {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(AppColors.Field)) {
            QuickButton.entries.forEach { button ->
                val checked = button.id in settings.quickButtons
                val toggle = {
                    onSettingsChange { s ->
                        val list = if (checked) s.quickButtons - button.id else s.quickButtons + button.id
                        // Room for at most 5 buttons plus "Опции".
                        if (list.size > 5) s else s.copy(quickButtons = list)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).clickable { toggle() }.padding(start = 14.dp, end = 4.dp),
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
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { toggle() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AppColors.Accent,
                            checkmarkColor = AppColors.OnAccent,
                            uncheckedColor = Color(0xFF4A4E56),
                        ),
                    )
                }
            }
        }
    }
    Text(
        "Кнопки стоят в том порядке, в каком ты их отметил. «Опции» всегда на панели.",
        color = AppColors.Faint,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

@Composable
private fun TocList(toc: List<TocItem>, onSelect: (TocItem) -> Unit, modifier: Modifier) {
    if (toc.isEmpty()) {
        Text("В этой книге нет оглавления", color = AppColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    LazyColumn(modifier) {
        items(toc) { item ->
            Text(
                item.title,
                color = if (item.depth == 0) AppColors.Text else AppColors.Muted,
                fontSize = if (item.depth == 0) 15.sp else 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(item) }
                    .padding(start = (8 + item.depth * 16).dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- small controls

@Composable
private fun Group(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = AppColors.Muted, fontSize = 13.sp)
        content()
    }
}

@Composable
private fun StepSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    format: (Double) -> String,
    onCommit: (Double) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val steps = ((range.endInclusive - range.start) / step).roundToInt() - 1
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            Text(label, color = AppColors.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(format(current.toDouble()), color = AppColors.Text, fontSize = 13.sp)
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = {
                val snapped = (Math.round(current / step) * step)
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
            ),
        )
    }
}

@Composable
private fun Segmented(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Group(label) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Field).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { i, option ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (i == selected) SelectedSegment else Color.Transparent)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option,
                        color = if (i == selected) AppColors.Text else AppColors.Muted,
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
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppColors.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
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
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Field)
            .border(if (selected) 2.dp else 1.dp, if (selected) AppColors.Accent else AppColors.Line, RoundedCornerShape(12.dp))
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun AlignIcon(justify: Boolean) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val ys = listOf(0.2f, 0.4f, 0.6f, 0.8f)
        ys.forEachIndexed { i, y ->
            val end = if (justify || i % 2 == 0) size.width else size.width * 0.6f
            drawLine(
                color = AppColors.Text,
                start = Offset(0f, size.height * y),
                end = Offset(end, size.height * y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
