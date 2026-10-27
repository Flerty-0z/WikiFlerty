package com.flerty.wikiflerty.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import com.flerty.wikiflerty.data.NoteColors
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.fieldSurface
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface
import kotlin.math.roundToInt
import org.readium.r2.shared.publication.Locator

/** The text the user has just selected in the book, and where it is on the screen (px). */
data class SelectionInfo(
    val locator: Locator,
    val text: String,
    val top: Float,
    val bottom: Float,
)

/** A note being created or edited. [id] is null for a new note. */
data class NoteDraft(
    val id: String?,
    val locator: Locator?,
    val text: String,
    val note: String,
    val color: Long,
    /** Opened from the list of notes, so "back" returns there. */
    val fromList: Boolean,
)

/** Everything the notes screens can ask the reader to do. */
class NoteActions(
    val onSave: (NoteDraft) -> Unit,
    val onDelete: (String) -> Unit,
    val onGoTo: (Note) -> Unit,
    val onSelectionNote: () -> Unit,
    val onSelectionMarker: () -> Unit,
    val onSelectionCopy: () -> Unit,
    val onSelectionAi: () -> Unit,
    val onSelectionClose: () -> Unit,
)

// ---------------------------------------------------------------- selection menu

@Composable
fun SelectionMenu(markerColor: Long, actions: NoteActions, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .panelSurface(RoundedCornerShape(22.dp), elevation = 18.dp)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MenuAction("Заметка", Modifier.weight(1f), onClick = actions.onSelectionNote) {
            Icon(AppIcons.Note, null, tint = AppColors.Text, modifier = Modifier.size(22.dp))
        }
        MenuAction("ИИ", Modifier.weight(1f), highlighted = true, onClick = actions.onSelectionAi) {
            Icon(AppIcons.Sparkle, null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
        }
        MenuAction("Маркер", Modifier.weight(1f), sound = FeedbackSound.Toggle, onClick = actions.onSelectionMarker) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(markerColor))
                    .border(1.dp, Color(0x55FFFFFF), CircleShape),
            )
        }
        MenuAction("Копия", Modifier.weight(1f), onClick = actions.onSelectionCopy) {
            Icon(AppIcons.Copy, null, tint = AppColors.Text, modifier = Modifier.size(21.dp))
        }
        MenuAction("Закрыть", Modifier.weight(1f), sound = FeedbackSound.Close, onClick = actions.onSelectionClose) {
            Icon(AppIcons.Close, null, tint = AppColors.Text, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MenuAction(
    label: String,
    modifier: Modifier,
    highlighted: Boolean = false,
    sound: FeedbackSound = FeedbackSound.Open,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .height(62.dp)
            .liveClick(shape, sound = sound, pressedScale = 0.9f, onClick = onClick)
            .background(if (highlighted) AppColors.AccentSoft else Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            color = if (highlighted) AppColors.Accent else AppColors.Text,
            fontSize = 12.sp,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- list of the book's notes

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
                "Выдели текст в книге, удерживая палец на слове, и выбери «Заметка» или «Маркер».",
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
    val shape = RoundedCornerShape(16.dp)
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
                    note.note,
                    color = Color(0xFFC9CDD4),
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

// ---------------------------------------------------------------- note editor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditor(
    draft: NoteDraft,
    onSave: (NoteDraft) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(draft.id, draft.text) { mutableStateOf(draft.note) }
    var color by remember(draft.id, draft.text) { mutableStateOf(draft.color) }
    var customOpen by remember { mutableStateOf(false) }
    var hue by remember { mutableFloatStateOf(200f) }
    val presets = NoteColors.presets
    val isCustom = presets.none { it.second == color }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val quoteBorder by animateColorAsState(Color(color), label = "quote")
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Field),
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(quoteBorder))
            Text(
                draft.text,
                color = Color(0xFFD0CEC8),
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
                    .fieldSurface(RoundedCornerShape(14.dp))
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

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Цвет", color = AppColors.Muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { (name, value) ->
                    ColorDot(value, name, selected = color == value) { color = value }
                }
                if (isCustom) ColorDot(color, "Свой цвет", selected = true) { customOpen = true }
                val shape = CircleShape
                Box(
                    Modifier
                        .size(40.dp)
                        .liveClick(shape, sound = FeedbackSound.Toggle) { customOpen = !customOpen }
                        .border(1.dp, Color(0xFF4A4E56), shape)
                        .semantics { contentDescription = "Свой цвет" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (customOpen) "–" else "+", color = AppColors.Muted, fontSize = 20.sp)
                }
            }
            AnimatedVisibility(visible = customOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.55f, 0.95f) })),
                    )
                    Slider(
                        value = hue,
                        onValueChange = {
                            hue = it
                            color = Color.hsv(it % 360f, 0.55f, 0.95f).toArgb().toLong() and 0xFFFFFFFFL
                        },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.hsv(hue % 360f, 0.55f, 0.95f),
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                        ),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val shape = RoundedCornerShape(14.dp)
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
                    color = if (id != null) Color(0xFFF08A8A) else AppColors.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                Modifier
                    .weight(2f)
                    .height(50.dp)
                    .liveClick(shape, sound = FeedbackSound.Toggle) { onSave(draft.copy(note = text, color = color)) }
                    .background(Brush.verticalGradient(listOf(Color(0xFFA3C1FF), AppColors.Accent))),
                contentAlignment = Alignment.Center,
            ) {
                Text("Сохранить", color = AppColors.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ColorDot(value: Long, name: String, selected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(if (selected) AppColors.Text else Color.Transparent, label = "ring")
    Box(
        Modifier
            .size(40.dp)
            .liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.85f, onClick = onClick)
            .border(2.dp, ring, CircleShape)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(value)))
    }
}
