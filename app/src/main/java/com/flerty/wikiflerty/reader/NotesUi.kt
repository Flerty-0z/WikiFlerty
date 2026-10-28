package com.flerty.wikiflerty.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.data.Note
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.accentContent
import com.flerty.wikiflerty.ui.accentHighlight
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.fieldSurface
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface
import kotlin.math.roundToInt
import org.readium.r2.shared.publication.Locator

data class SelectionInfo(
    val locator: Locator,
    val text: String,
    val top: Float,
    val bottom: Float,
    val parts: Int = 1,
)

data class NoteDraft(
    val id: String?,
    val locator: Locator?,
    val text: String,
    val note: String,
    val color: Long,
    val fromList: Boolean,
)

class NoteActions(
    val onSave: (NoteDraft) -> Unit,
    val onDelete: (String) -> Unit,
    val onGoTo: (Note) -> Unit,
    val onSelectionNote: () -> Unit,
    val onSelectionMarker: () -> Unit,
    val onSelectionCopy: () -> Unit,
    val onSelectionAi: () -> Unit,
    val onSelectionClose: () -> Unit,
    val onSelectionQuote: () -> Unit = {},
)

@Composable
fun SelectionMenu(markerColor: Long, actions: NoteActions, modifier: Modifier = Modifier, parts: Int = 1) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (parts > 1) {
            com.flerty.wikiflerty.ui.InfoChip(
                "Из $parts глав",
                AppColors.Accent,
                AppIcons.Book,
                Modifier.align(Alignment.TopCenter).offset(y = (-30).dp).panelSurface(androidx.compose.foundation.shape.CircleShape, elevation = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .panelSurface(appCorner(28), elevation = 16.dp)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            MenuAction("Заметка", onClick = actions.onSelectionNote) {
                Icon(AppIcons.Note, null, tint = AppColors.Text, modifier = Modifier.size(22.dp))
            }
            MenuAction("Спросить ИИ", highlighted = true, onClick = actions.onSelectionAi) {
                Icon(AppIcons.Sparkle, null, tint = accentContent, modifier = Modifier.size(22.dp))
            }
            MenuAction("Маркер", sound = FeedbackSound.Toggle, onClick = actions.onSelectionMarker) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(markerColor))
                        .border(1.dp, Color(0x55FFFFFF), CircleShape),
                )
            }
            MenuAction("Цитата", onClick = actions.onSelectionQuote) {
                Icon(AppIcons.Quote, null, tint = AppColors.Text, modifier = Modifier.size(20.dp))
            }
            MenuAction("Копировать", onClick = actions.onSelectionCopy) {
                Icon(AppIcons.Copy, null, tint = AppColors.Text, modifier = Modifier.size(21.dp))
            }
            MenuAction("Закрыть", sound = FeedbackSound.Close, onClick = actions.onSelectionClose) {
                Icon(AppIcons.Close, null, tint = AppColors.Muted, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun MenuAction(
    label: String,
    highlighted: Boolean = false,
    sound: FeedbackSound = FeedbackSound.Open,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val shape = appCorner(24)
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 46.dp)
            .liveClick(shape, sound = sound, pressedScale = 0.88f, onClick = onClick)
            .then(if (highlighted) Modifier.accentHighlight(shape) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
fun BookNotesList(
    notes: List<Note>,
    onOpen: (Note) -> Unit,
    onEdit: (Note) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notes.isEmpty()) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Пока нет заметок", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Выдели текст → «Заметка» или «Маркер».",
                color = AppColors.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(notes, key = { it.id }) { note ->
            NoteCard(note, onOpen = { onOpen(note) }, onEdit = { onEdit(note) })
        }
    }
}

@Composable
fun NoteCard(
    note: Note,
    onOpen: () -> Unit,
    onEdit: (() -> Unit)?,
    subtitle: String? = null,
    editIcon: androidx.compose.ui.graphics.vector.ImageVector = AppIcons.Pencil,
    editLabel: String = "Изменить заметку",
) {
    val shape = appCorner(16)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onOpen)
            .fieldSurface(shape),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(Color(note.color)))
        Column(
            Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "«${note.text}»",
                color = AppColors.Text,
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.note.isNotBlank()) {
                Text(
                    com.flerty.wikiflerty.ui.markdown(note.note),
                    color = AppColors.Text.copy(alpha = 0.82f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = listOfNotNull(
                subtitle,
                note.chapter?.takeIf { it.isNotBlank() },
                "${(note.progression * 100).roundToInt()}%",
            ).joinToString(" · ")
            Text(meta, color = AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onEdit != null) {
            Box(
                Modifier
                    .padding(top = 6.dp, end = 6.dp)
                    .size(40.dp)
                    .liveClick(CircleShape, onClick = onEdit)
                    .semantics { contentDescription = editLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(editIcon, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditor(
    draft: NoteDraft,
    palette: List<Long>,
    onPaletteChange: (List<Long>) -> Unit,
    onSave: (NoteDraft) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(draft.id, draft.text) { mutableStateOf(draft.note) }
    var color by remember(draft.id, draft.text) { mutableStateOf(draft.color) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0.55f) }
    var brightness by remember { mutableFloatStateOf(0.95f) }
    val edited = hsvColor(hue, saturation, brightness)

    fun startEdit(index: Int) {
        val base = if (index >= 0) palette[index] else hsvColor((palette.size * 47f) % 360f, 0.55f, 0.95f)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(base.toInt(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        editing = index
    }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val quoteBorder by animateColorAsState(Color(if (editing != null) edited else color), label = "quote")
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(appCorner(12))
                .background(AppColors.Field),
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(quoteBorder))
            Text(
                draft.text,
                color = AppColors.Text.copy(alpha = 0.85f),
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Описание", color = AppColors.Muted, fontSize = 13.sp)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = AppColors.Text, fontSize = 15.sp, lineHeight = 21.sp),
                cursorBrush = SolidColor(AppColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .fieldSurface(appCorner(14))
                    .padding(12.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text("Твоя мысль об этом месте…", color = AppColors.Faint, fontSize = 15.sp)
                        }
                        inner()
                    }
                },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Цвет", color = AppColors.Muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (color !in palette) {
                    PaletteDot(color, "Текущий цвет", selected = editing == null, showSettings = false) { editing = null }
                }
                palette.forEachIndexed { index, value ->
                    val selected = color == value && editing == null || editing == index
                    PaletteDot(
                        value = if (editing == index) edited else value,
                        name = "Цвет ${index + 1}",
                        selected = selected,
                        showSettings = selected,
                    ) {
                        when {
                            editing == index -> editing = null
                            color == value && editing == null -> startEdit(index)
                            else -> {
                                color = value
                                editing = null
                            }
                        }
                    }
                }
                if (palette.size < MAX_PALETTE) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .liveClick(CircleShape, sound = FeedbackSound.Toggle) { if (editing == -1) editing = null else startEdit(-1) }
                            .border(1.dp, if (editing == -1) AppColors.Accent else AppColors.Faint, CircleShape)
                            .semantics { contentDescription = "Добавить цвет" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (editing == -1) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(edited)))
                        } else {
                            Text("+", color = AppColors.Muted, fontSize = 20.sp)
                        }
                    }
                }
            }
            Text(
                "Повторное нажатие — изменить цвет.",
                color = AppColors.Faint,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )

            AnimatedVisibility(
                visible = editing != null,
                enter = expandVertically(Motion.tween(260)) + fadeIn(Motion.fade(220)),
                exit = shrinkVertically(Motion.tween(220)) + fadeOut(Motion.fade(160)),
            ) {
                ColorSettings(
                    isNew = editing == -1,
                    canDelete = editing != null && editing != -1 && palette.size > 1,
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onHue = { hue = it },
                    onSaturation = { saturation = it },
                    onBrightness = { brightness = it },
                    onDelete = {
                        val index = editing ?: return@ColorSettings
                        if (index < 0) return@ColorSettings
                        val removed = palette[index]
                        val updated = palette.filterIndexed { i, _ -> i != index }
                        onPaletteChange(updated)
                        if (color == removed) color = updated.first()
                        editing = null
                    },
                    onDone = {
                        val index = editing ?: return@ColorSettings
                        if (index < 0) {
                            onPaletteChange(palette + edited)
                        } else {
                            val old = palette[index]
                            onPaletteChange(palette.mapIndexed { i, v -> if (i == index) edited else v })
                            if (color != old) {
                                color = edited
                            }
                        }
                        color = edited
                        editing = null
                    },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val shape = appCorner(14)
            val id = draft.id
            Box(
                Modifier
                    .weight(1f)
                    .height(50.dp)
                    .liveClick(shape, sound = FeedbackSound.Close) { if (id != null) onDelete(id) else onCancel() }
                    .border(1.dp, AppColors.Line, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (id != null) "Удалить" else "Отмена",
                    color = if (id != null) AppColors.Bad else AppColors.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                Modifier
                    .weight(2f)
                    .height(50.dp)
                    .liveClick(shape, sound = FeedbackSound.Toggle) {
                        val finalColor = if (editing != null) edited else color
                        onSave(draft.copy(note = text, color = finalColor))
                    }
                    .background(Brush.verticalGradient(listOf(AppColors.AccentLight, AppColors.Accent))),
                contentAlignment = Alignment.Center,
            ) {
                Text("Сохранить", color = AppColors.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private const val MAX_PALETTE = 12

private fun hsvColor(hue: Float, saturation: Float, value: Float): Long =
    Color.hsv(((hue % 360f) + 360f) % 360f, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)).toArgb().toLong() and 0xFFFFFFFFL

@Composable
private fun ColorSettings(
    isNew: Boolean,
    canDelete: Boolean,
    hue: Float,
    saturation: Float,
    brightness: Float,
    onHue: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    val current = Color(hsvColor(hue, saturation, brightness))
    Column(
        Modifier
            .fillMaxWidth()
            .fieldSurface(appCorner(18))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(current)
                    .border(2.dp, Color(0x33FFFFFF), CircleShape),
            )
            Text(
                if (isNew) "Новый цвет" else "Настройка цвета",
                color = AppColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        GradientSlider(
            label = "Оттенок",
            value = hue,
            range = 0f..360f,
            colors = (0..6).map { Color.hsv((it * 60f) % 360f, saturation.coerceAtLeast(0.35f), brightness.coerceAtLeast(0.6f)) },
            thumb = current,
            onChange = onHue,
        )
        GradientSlider(
            label = "Насыщенность",
            value = saturation,
            range = 0.1f..1f,
            colors = listOf(Color.hsv(hue % 360f, 0.1f, brightness), Color.hsv(hue % 360f, 1f, brightness)),
            thumb = current,
            onChange = onSaturation,
        )
        GradientSlider(
            label = "Яркость",
            value = brightness,
            range = 0.35f..1f,
            colors = listOf(Color.hsv(hue % 360f, saturation, 0.35f), Color.hsv(hue % 360f, saturation, 1f)),
            thumb = current,
            onChange = onBrightness,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shape = appCorner(12)
            if (canDelete) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .liveClick(shape, sound = FeedbackSound.Close, onClick = onDelete)
                        .border(1.dp, Color(0x55F08A8A), shape),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Trash, "Удалить цвет", tint = AppColors.Bad, modifier = Modifier.size(22.dp))
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .liveClick(shape, sound = FeedbackSound.Toggle, onClick = onDone)
                    .background(AppColors.AccentTintStrong)
                    .border(1.dp, AppColors.Accent.copy(alpha = 0.33f), shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (isNew) "Добавить" else "Готово", color = AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    colors: List<Color>,
    thumb: Color,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, color = AppColors.Muted, fontSize = 12.sp)
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(10.dp)
                    .clip(appCorner(5))
                    .background(Brush.horizontalGradient(colors)),
            )
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = thumb,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun PaletteDot(value: Long, name: String, selected: Boolean, showSettings: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(if (selected) AppColors.Text else Color.Transparent, label = "ring")
    Box(
        Modifier
            .size(40.dp)
            .liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.85f, onClick = onClick)
            .border(2.dp, ring, CircleShape)
            .semantics { contentDescription = if (showSettings) "$name, настроить" else name },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(value)), contentAlignment = Alignment.Center) {
            AnimatedVisibility(visible = showSettings, enter = fadeIn(Motion.fade(220)), exit = fadeOut(Motion.fade(160))) {
                Icon(AppIcons.Options, null, tint = Color(0x8C000000), modifier = Modifier.size(16.dp))
            }
        }
    }
}
