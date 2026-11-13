package com.flerty.wikiflerty.reader

import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import com.flerty.wikiflerty.ui.revealScroll
import com.flerty.wikiflerty.ui.Reveal
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.layout
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
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
    val onSelectionMarker: (Long) -> Unit,
    val onSelectionCopy: () -> Unit,
    val onSelectionAi: () -> Unit,
    val onSelectionClose: () -> Unit,
    val onSelectionQuote: () -> Unit = {},
    val onSelectionPerson: () -> Unit = {},
    val onSelectionTranslate: () -> Unit = {},
    val onSelectionDictionary: () -> Unit = {},
)

@Composable
fun SelectionMenu(markerColor: Long, palette: List<Long>, actions: NoteActions, main: List<SelectionButton>, more: List<SelectionButton>, modifier: Modifier = Modifier) {
    var expanded by remember(main, more) { mutableStateOf(false) }
    var colors by remember(main, more) { mutableStateOf(false) }
    val swatches = remember(palette, markerColor) { (palette + markerColor).distinct() }
    val lift = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.layout { m, c ->
                val p = m.measure(c.copy(minHeight = 0))
                layout(p.width, 0) { p.place(0, -p.height - 8.dp.roundToPx()) }
            },
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = colors,
                enter = androidx.compose.animation.slideInVertically(Motion.tween(340, easing = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) { it + lift } +
                    fadeIn(Motion.tween(220)) +
                    androidx.compose.animation.scaleIn(Motion.tween(340), initialScale = 0.9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)),
                exit = androidx.compose.animation.slideOutVertically(Motion.tween(260)) { it + lift } +
                    fadeOut(Motion.tween(200)) +
                    androidx.compose.animation.scaleOut(Motion.tween(260), targetScale = 0.9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)),
            ) {
                MarkerStrip(swatches, markerColor) { color ->
                    colors = false
                    actions.onSelectionMarker(color)
                }
            }
        }
        Box(Modifier.panelSurface(appCorner(28), elevation = 16.dp).padding(5.dp)) {
            androidx.compose.animation.AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    val dir = if (targetState) 1 else -1
                    (androidx.compose.animation.slideInHorizontally(Motion.tween(260)) { dir * it / 5 } + fadeIn(Motion.tween(220, delayMillis = 60)))
                        .togetherWith(androidx.compose.animation.slideOutHorizontally(Motion.tween(220)) { -dir * it / 5 } + fadeOut(Motion.fade(120)))
                        .using(androidx.compose.animation.SizeTransform(clip = true) { _, _ -> Motion.tween(260) })
                },
                label = "selection-menu",
            ) { open ->
                Row(
                    modifier = if (open) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (open) {
                        MenuAction("Назад", sound = FeedbackSound.Close, onClick = { expanded = false; colors = false }) {
                            Icon(AppIcons.Back, null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
                        }
                        more.forEach { SelectionAction(it, markerColor, actions, colors) { colors = !colors } }
                    } else {
                        main.forEach { SelectionAction(it, markerColor, actions, colors) { colors = !colors } }
                        if (more.isNotEmpty()) {
                            MenuAction("Ещё", sound = FeedbackSound.Toggle, onClick = { expanded = true; colors = false }) {
                                Icon(AppIcons.More, null, tint = AppColors.Text, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkerStrip(swatches: List<Long>, current: Long, onPick: (Long) -> Unit) {
    Row(
        Modifier
            .widthIn(max = 320.dp)
            .panelSurface(appCorner(24), elevation = 14.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        swatches.forEach { c ->
            val on = c == current
            val ring by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, Motion.tween(220), label = "marker-ring")
            Box(
                Modifier
                    .size(36.dp)
                    .liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.86f) { onPick(c) }
                    .border((1.5f * ring).dp, AppColors.Text.copy(alpha = ring), CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(1.dp, Color(0x55FFFFFF), CircleShape),
            )
        }
    }
}

@Composable
private fun SelectionAction(button: SelectionButton, markerColor: Long, actions: NoteActions, colorsOpen: Boolean, onMarker: () -> Unit) {
    val click = when (button) {
        SelectionButton.Note -> actions.onSelectionNote
        SelectionButton.Marker -> onMarker
        SelectionButton.Translate -> actions.onSelectionTranslate
        SelectionButton.Dictionary -> actions.onSelectionDictionary
        SelectionButton.Ai -> actions.onSelectionAi
        SelectionButton.Quote -> actions.onSelectionQuote
        SelectionButton.Person -> actions.onSelectionPerson
        SelectionButton.Copy -> actions.onSelectionCopy
    }
    MenuAction(
        button.title,
        highlighted = button == SelectionButton.Ai || (button == SelectionButton.Marker && colorsOpen),
        sound = when (button) {
            SelectionButton.Marker -> FeedbackSound.Toggle
            SelectionButton.Copy -> FeedbackSound.Copy
            else -> FeedbackSound.Open
        },
        onClick = click,
    ) { SelectionIcon(button, markerColor, if (button == SelectionButton.Ai) accentContent else AppColors.Text) }
}

@Composable
internal fun SelectionIcon(button: SelectionButton, markerColor: Long, tint: Color) {
    when (button) {
        SelectionButton.Marker -> Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(markerColor))
                .border(1.dp, Color(0x55FFFFFF), CircleShape),
        )
        else -> Icon(
            when (button) {
                SelectionButton.Note -> AppIcons.Note
                SelectionButton.Translate -> AppIcons.Translate
                SelectionButton.Dictionary -> AppIcons.TextSize
                SelectionButton.Ai -> AppIcons.Sparkle
                SelectionButton.Quote -> AppIcons.Quote
                SelectionButton.Person -> AppIcons.Person
                else -> AppIcons.Copy
            },
            null,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
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
            Reveal {
                NoteCard(note, onOpen = { onOpen(note) }, onEdit = { onEdit(note) })
            }
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
    pages: Int = 0,
    pill: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
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
        var expanded by remember(note.id) { mutableStateOf(false) }
        var overflow by remember(note.id) { mutableStateOf(false) }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            com.flerty.wikiflerty.ui.ExpandingText(
                "«${note.text}»",
                expanded = pill && expanded,
                lines = 3,
                color = AppColors.Text,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.fillMaxWidth(),
                onOverflow = { if (it) overflow = true },
            )
            if (note.note.isNotBlank()) {
                val body = remember(note.note) { com.flerty.wikiflerty.ui.markdown(note.note) }
                com.flerty.wikiflerty.ui.ExpandingText(
                    body,
                    expanded = pill && expanded,
                    lines = 3,
                    color = AppColors.Text.copy(alpha = 0.82f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.fillMaxWidth(),
                    onOverflow = { if (it) overflow = true },
                )
            }
            val place = if (pages > 0) {
                "${(note.progression * pages).toInt().coerceIn(1, pages)}/$pages"
            } else {
                "${(note.progression * 100).roundToInt()}%"
            }
            if (!pill) {
                val meta = listOfNotNull(subtitle, note.chapter?.takeIf { it.isNotBlank() }, place).joinToString(" · ")
                Text(meta, color = AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (footer != null) Box(Modifier.padding(top = 2.dp, end = 8.dp)) { footer() }
            } else {
                if (overflow || expanded) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .liveClick(CircleShape, sound = FeedbackSound.Toggle) { expanded = !expanded }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        androidx.compose.animation.AnimatedContent(
                            expanded,
                            transitionSpec = { androidx.compose.animation.fadeIn(Motion.tween(220)) togetherWith androidx.compose.animation.fadeOut(Motion.tween(160)) },
                            label = "note-more-label",
                        ) { open -> Text(if (open) "Свернуть" else "Развернуть", color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                        val turn by animateFloatAsState(if (expanded) -90f else 90f, Motion.tween(240), label = "note-more")
                        Icon(AppIcons.Chevron, null, tint = AppColors.Accent, modifier = Modifier.size(14.dp).rotate(turn))
                    }
                }
                if (footer != null) Box(Modifier.padding(top = 2.dp, end = 8.dp)) { footer() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(AppColors.Accent.copy(alpha = 0.12f))
                            .padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(if (pages > 0) AppIcons.Pages else AppIcons.Percent, null, tint = AppColors.Accent, modifier = Modifier.size(12.dp))
                        Text(place, color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    val meta = listOfNotNull(subtitle, note.chapter?.takeIf { it.isNotBlank() }).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(meta, color = AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
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

    Column(modifier.revealScroll(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val quoteBorder by animateColorAsState(Color(if (editing != null) edited else color), label = "quote")
        var quoteOpen by remember(draft.text) { mutableStateOf(false) }
        var quoteLong by remember(draft.text) { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(appCorner(2)).background(quoteBorder))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                com.flerty.wikiflerty.ui.ExpandingText(
                    draft.text,
                    expanded = quoteOpen,
                    lines = 2,
                    color = AppColors.Text.copy(alpha = 0.88f),
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.fillMaxWidth(),
                    onOverflow = { if (it) quoteLong = true },
                )
                AnimatedVisibility(quoteLong, enter = fadeIn(Motion.fade(200)), exit = fadeOut(Motion.fade(140))) {
                    val turn by androidx.compose.animation.core.animateFloatAsState(if (quoteOpen) 180f else 0f, Motion.tween(300), label = "quote-turn")
                    Row(
                        Modifier
                            .liveClick(appCorner(10), sound = FeedbackSound.Toggle, pressedScale = 0.95f) { quoteOpen = !quoteOpen }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(AppIcons.Chevron, null, tint = AppColors.Accent, modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = 90f + turn })
                        androidx.compose.animation.AnimatedContent(quoteOpen, label = "quote-label") { open ->
                            Text(if (open) "Свернуть" else "Весь текст", color = AppColors.Accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(color = AppColors.Text, fontSize = 15.sp, lineHeight = 21.sp),
            cursorBrush = SolidColor(AppColors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp)
                .fieldSurface(appCorner(16))
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 26.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text("Твоя мысль об этом месте…", color = AppColors.Faint, fontSize = 15.sp)
                    }
                    inner()
                }
            },
        )
            Text(
                "${text.length}",
                color = AppColors.Faint,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 8.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                            .dashedCircle(if (editing == -1) AppColors.Accent else AppColors.Faint)
                            .semantics { contentDescription = "Добавить цвет" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (editing == -1) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(edited)))
                        } else {
                            Icon(AppIcons.Plus, null, tint = AppColors.Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

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
            val shape = appCorner(16)
            val id = draft.id
            Box(
                Modifier
                    .size(width = 56.dp, height = 52.dp)
                    .liveClick(shape, sound = FeedbackSound.Close) { if (id != null) onDelete(id) else onCancel() }
                    .border(1.dp, if (id != null) AppColors.Bad.copy(alpha = 0.45f) else AppColors.Line, shape)
                    .semantics { contentDescription = if (id != null) "Удалить" else "Отмена" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (id != null) AppIcons.Trash else AppIcons.Close, null, tint = if (id != null) AppColors.Bad else AppColors.Muted, modifier = Modifier.size(20.dp))
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .liveClick(shape, sound = FeedbackSound.Toggle) {
                        val finalColor = if (editing != null) edited else color
                        onSave(draft.copy(note = text, color = finalColor))
                    }
                    .background(Brush.verticalGradient(listOf(AppColors.AccentLight, AppColors.Accent))),
                contentAlignment = Alignment.Center,
            ) {
                com.flerty.wikiflerty.ui.ActionLabel("Сохранить", AppColors.OnAccent, icon = AppIcons.Check)
            }
        }
    }
}

private fun Modifier.dashedCircle(color: Color): Modifier = drawBehind {
    drawCircle(
        color,
        radius = size.minDimension / 2f - 1.dp.toPx(),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.2.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        ),
    )
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
                        .liveClick(shape, sound = FeedbackSound.Trash, onClick = onDelete)
                        .border(1.dp, Color(0x55F08A8A), shape),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.flerty.wikiflerty.ui.ActionLabel("Удалить", AppColors.Bad, fontSize = 14.sp, iconSize = 21.dp, icon = AppIcons.Trash)
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
                com.flerty.wikiflerty.ui.ActionLabel(if (isNew) "Добавить" else "Готово", AppColors.Accent)
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
