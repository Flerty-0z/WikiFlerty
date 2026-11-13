package com.flerty.wikiflerty.ui

import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsGroup(title: String?, content: @Composable () -> Unit) {
    if (title != null) {
        TabbedFrame(title, content = content)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .sheetReveal()
                .fieldSurface(appCorner(18))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) { content() }
    }
}

@Composable
fun SettingsNavRow(icon: ImageVector, title: String, subtitle: String, framed: Boolean = true, onClick: () -> Unit) {
    val shape = appCorner(18)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (framed) Modifier.sheetReveal() else Modifier)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .then(if (framed) Modifier.fieldSurface(shape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(appCorner(13))
                .background(Brush.linearGradient(listOf(AppColors.Accent.copy(alpha = 0.28f), AppColors.Accent.copy(alpha = 0.1f))))
                .border(1.dp, AppColors.Accent.copy(alpha = 0.2f), appCorner(13)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) Text(
                subtitle,
                color = AppColors.Muted,
                maxLines = 1,
                softWrap = false,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
            )
        }
        Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingSwitch(
    label: String,
    checked: Boolean,
    hint: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    val feedback = LocalFeedback.current
    val view = LocalView.current
    val change: (Boolean) -> Unit = {
        feedback?.play(if (it) FeedbackSound.Check else FeedbackSound.Uncheck, view)
        onChange(it)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled) { change(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingIcon(icon)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            FitText(label, AppColors.Text, 15.sp)
            if (hint != null) Text(hint, color = AppColors.Faint, fontSize = 12.sp, lineHeight = 16.sp)
        }
        KitSwitch(checked, change, enabled = enabled)
    }
}

@Composable
fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    steps: Int = 0,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onCommit: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.alpha(if (enabled) 1f else 0.4f).padding(top = 8.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                SettingIcon(icon)
                Spacer(Modifier.width(12.dp))
            }
            Text(label, color = if (icon != null) AppColors.Text else AppColors.Muted, fontSize = if (icon != null) 15.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Text(format(current), color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        KitSlider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onCommit(current) },
            valueRange = range,
            steps = steps,
            enabled = enabled,
        )
    }
}

@Composable
fun SettingSegmented(label: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) Text(label, color = AppColors.Muted, fontSize = 13.sp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(appCorner(14))
                .background(AppColors.Background.copy(alpha = 0.6f))
                .border(1.dp, AppColors.PanelBorder, appCorner(14))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { i, option ->
                val isSelected = i == selected
                val bg by animateColorAsState(if (isSelected) AppColors.AccentTintStrong else Color.Transparent, label = "seg")
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .liveClick(appCorner(10), sound = FeedbackSound.Toggle, pressedScale = 0.96f) { onSelect(i) }
                        .background(bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option,
                        color = if (isSelected) AppColors.Text else AppColors.Muted,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

data class PickerOption(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val font: FontFamily? = null,
    val icon: ImageVector? = null,
    val swatch: Color? = null,
    val fontLoader: (() -> FontFamily?)? = null,
) {
    val typeface: FontFamily? get() = font ?: fontLoader?.invoke()
}

@Composable
fun SettingIcon(icon: ImageVector, size: androidx.compose.ui.unit.Dp = 32.dp) {
    Box(
        Modifier
            .size(size)
            .clip(appCorner(10))
            .background(Brush.linearGradient(listOf(AppColors.Accent.copy(alpha = 0.24f), AppColors.Accent.copy(alpha = 0.08f))))
            .border(1.dp, AppColors.Accent.copy(alpha = 0.16f), appCorner(10)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(size * 0.56f)) }
}

@Composable
fun HintNote(text: String, icon: ImageVector = AppIcons.Info, modifier: Modifier = Modifier, framed: Boolean = true) {
    val shape = appCorner(14)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (framed) Modifier.fieldSurface(shape).padding(horizontal = 12.dp, vertical = 11.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingIcon(icon, 30.dp)
        Text(text, color = AppColors.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ListDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AppColors.PanelBorder))
}

@Composable
fun SheetOptionRow(option: PickerOption, selected: Boolean, fallbackIcon: ImageVector? = null, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .liveClick(appCorner(12), sound = FeedbackSound.Toggle, pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OptionTile(option, fallbackIcon)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                option.title,
                color = if (selected) AppColors.Accent else AppColors.Text,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = option.typeface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (option.subtitle != null) {
                Text(option.subtitle, color = AppColors.Faint, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = option.typeface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) Text(trailing, color = AppColors.Faint, fontSize = 14.sp, maxLines = 1)
        Box(Modifier.padding(start = 6.dp).size(22.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(AppIcons.Check, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun OptionTile(option: PickerOption, fallbackIcon: ImageVector?, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val shape = appCorner(10)
    val tile = Modifier
        .size(size)
        .clip(shape)
        .background(Brush.linearGradient(listOf(AppColors.Accent.copy(alpha = 0.24f), AppColors.Accent.copy(alpha = 0.08f))))
        .border(1.dp, AppColors.Accent.copy(alpha = 0.16f), shape)
    when {
        option.swatch != null -> Box(tile, contentAlignment = Alignment.Center) {
            Box(Modifier.size(size * 0.56f).clip(CircleShape).background(option.swatch).border(1.dp, AppColors.Line, CircleShape))
        }
        option.icon != null -> SettingIcon(option.icon, size)
        option.font != null || option.fontLoader != null -> Box(tile, contentAlignment = Alignment.Center) {
            Text("Аа", color = AppColors.Accent, fontSize = (size.value * 0.4f).sp, fontFamily = option.typeface, maxLines = 1, softWrap = false)
        }
        fallbackIcon != null -> SettingIcon(fallbackIcon, size)
        else -> Box(tile, contentAlignment = Alignment.Center) {
            Text(option.title.take(1).uppercase(), color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun ChoiceBlock(
    label: String,
    value: String,
    icon: ImageVector?,
    lead: (@Composable () -> Unit)? = null,
    typeface: androidx.compose.ui.text.font.FontFamily? = null,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                SettingIcon(icon)
                Spacer(Modifier.width(12.dp))
            }
            Text(label, color = AppColors.Text, fontSize = 15.sp, lineHeight = 19.sp, maxLines = 2, modifier = Modifier.weight(1f))
        }
        ListDivider(Modifier.padding(top = 10.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).liveClick(appCorner(12), sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) {
                    if (lead != null) Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) { lead() }
                }
            } else if (lead != null) {
                lead()
                Spacer(Modifier.width(10.dp))
            }
            Text(
                value,
                color = AppColors.Accent,
                fontWeight = FontWeight.SemiBold,
                fontFamily = typeface,
                maxLines = 1,
                softWrap = false,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 16.sp, stepSize = 0.5.sp),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .padding(start = 10.dp)
                    .size(34.dp)
                    .clip(appCorner(11))
                    .background(AppColors.AccentSoft)
                    .border(1.dp, AppColors.Accent.copy(alpha = 0.25f), appCorner(11)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Chevron, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp).rotate(90f))
            }
        }
    }
}

@Composable
fun ValueRow(label: String, value: String, icon: ImageVector? = null, onClick: () -> Unit) {
    ChoiceBlock(label, value, icon, onClick = onClick)
}

@Composable
fun PickerRow(
    label: String,
    options: List<PickerOption>,
    selectedId: String,
    searchable: Boolean = options.size > 12,
    icon: ImageVector? = null,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    ChoiceBlock(
        label,
        selected?.title ?: selectedId,
        icon,
        lead = if (selected != null) ({ OptionTile(selected, icon, 26.dp) }) else null,
        typeface = selected?.typeface,
    ) { open = true }
    if (open) {
        PickerSheet(label, options, selectedId, searchable, icon = icon, onDismiss = { open = false }) {
            onSelect(it)
            open = false
        }
    }
}

@Composable
fun PickerSheet(
    title: String,
    options: List<PickerOption>,
    selectedId: String,
    searchable: Boolean,
    icon: ImageVector? = null,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = options.filter { query.isBlank() || it.title.contains(query, true) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (options.indexOfFirst { it.id == selectedId } - 2).coerceAtLeast(0),
    )
    val picked = remember { arrayOfNulls<String>(1) }
    val corner = (26 * AppStyle.current.corners).dp
    SheetWindow(onDismiss = { picked[0]?.let(onSelect) ?: onDismiss() }) { control ->
        val close = control.close
        Column(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = corner, topEnd = corner))
                .background(AppColors.Panel)
                .height(sheetHeight())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(top = 16.dp, bottom = 12.dp),
        ) {
            SheetHeader(title, close)
            Spacer(Modifier.height(12.dp))
            if (searchable) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = AppColors.Text, fontSize = 15.sp),
                    cursorBrush = SolidColor(AppColors.Accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .fieldSurface(appCorner(23))
                        .padding(horizontal = 16.dp),
                    decorationBox = { inner ->
                        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Search, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
                            Box(Modifier.padding(start = 10.dp).weight(1f)) {
                                if (query.isEmpty()) Text("Поиск", color = AppColors.Faint, fontSize = 15.sp)
                                inner()
                            }
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().fieldSurface(appCorner(18)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                LazyColumn(state = listState) {
                    itemsIndexed(shown, key = { _, it -> it.id }) { index, option ->
                        Column {
                            if (index > 0) ListDivider()
                            Reveal {
                                SheetOptionRow(option, option.id == selectedId, icon) {
                                    picked[0] = option.id
                                    close()
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun ColorPicker(initial: Long, onChange: (Long) -> Unit) {
    val start = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) }
    }
    var hue by remember(initial) { mutableFloatStateOf(start[0]) }
    var saturation by remember(initial) { mutableFloatStateOf(start[1]) }
    var value by remember(initial) { mutableFloatStateOf(start[2]) }
    fun emit() = onChange(Color.hsv(hue % 360f, saturation, value).toArgb().toLong() and 0xFFFFFFFFL)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GradientTrack("Оттенок", hue, 0f..360f, (0..6).map { Color.hsv((it * 60f) % 360f, 0.6f, 0.95f) }) { hue = it; emit() }
        GradientTrack("Насыщенность", saturation, 0.05f..1f, listOf(Color.hsv(hue % 360f, 0.05f, value), Color.hsv(hue % 360f, 1f, value))) {
            saturation = it
            emit()
        }
        GradientTrack("Яркость", value, 0.3f..1f, listOf(Color.hsv(hue % 360f, saturation, 0.3f), Color.hsv(hue % 360f, saturation, 1f))) {
            value = it
            emit()
        }
    }
}

@Composable
private fun GradientTrack(label: String, value: Float, range: ClosedFloatingPointRange<Float>, colors: List<Color>, onChange: (Float) -> Unit) {
    Column {
        Text(label, color = AppColors.Muted, fontSize = 12.sp)
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(colors)),
            )
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}

fun percent(value: Float): String = "${(value * 100).roundToInt()}%"

@Composable
fun SheetHeader(title: String, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
            HeaderLabel(title, Modifier.padding(horizontal = 46.dp))
            Box(
                Modifier.align(Alignment.CenterEnd).size(38.dp).liveClick(CircleShape, sound = FeedbackSound.Close, onClick = onClose).background(AppColors.Field),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Close, "Закрыть", tint = AppColors.Muted, modifier = Modifier.size(18.dp)) }
        }
        SheetDivider()
    }
}

const val CUSTOM_ID = "__custom__"

data class InputUnit(val label: String, val range: IntRange, val default: Int)

@Composable
fun NumberInputSheet(
    title: String,
    suffix: String,
    initial: Int,
    range: IntRange,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
    units: List<InputUnit>? = null,
    unit: Int = 0,
    onDoneUnit: ((Int, Int) -> Unit)? = null,
    onDone: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    var unitIndex by remember { mutableStateOf(unit) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val value = text.toIntOrNull()
    val range = units?.getOrNull(unitIndex)?.range ?: range
    val ok = value != null && value in range
    AppSheet(onDismiss, title = title) {
        val close = sheetClose(onDismiss)
        Row(
            Modifier.fillMaxWidth().height(64.dp).fieldSurface(appCorner(18)).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
                singleLine = true,
                textStyle = TextStyle(color = AppColors.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(AppColors.Accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            if (units == null) {
                Text(suffix, color = AppColors.Muted, fontSize = 17.sp, maxLines = 1)
            } else {
                Row(
                    Modifier.clip(appCorner(12)).background(AppColors.Line.copy(alpha = 0.35f)).border(1.dp, AppColors.PanelBorder, appCorner(12)).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    units.forEachIndexed { i, u ->
                        val selected = i == unitIndex
                        val bg by animateColorAsState(if (selected) AppColors.Accent else Color.Transparent, label = "unit")
                        Box(
                            Modifier
                                .height(36.dp)
                                .widthIn(min = 52.dp)
                                .liveClick(appCorner(9), sound = FeedbackSound.Toggle) {
                                    if (i != unitIndex) {
                                        val old = units[unitIndex]
                                        val v = text.toIntOrNull()
                                        if (v == null || v == old.default || v !in u.range) text = u.default.toString()
                                        unitIndex = i
                                    }
                                }
                                .background(bg, appCorner(9))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(u.label, color = if (selected) AppColors.OnAccent else AppColors.Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    }
                }
            }
        }
        Text(
            if (ok || text.isEmpty()) "От ${range.first} до ${range.last}" else "Нужно число от ${range.first} до ${range.last}",
            color = if (ok || text.isEmpty()) AppColors.Faint else AppColors.Bad,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
        val shape = appCorner(16)
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .liveClick(shape, sound = FeedbackSound.Toggle, enabled = ok) {
                    if (onDoneUnit != null) onDoneUnit(value!!, unitIndex) else onDone(value!!)
                    close()
                }
                .then(if (ok) Modifier.accentHighlight(shape) else Modifier.border(1.dp, AppColors.Line, shape)),
            contentAlignment = Alignment.Center,
        ) { ActionLabel("Сохранить", if (ok) accentContent else AppColors.Faint) }
        if (onClear != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .liveClick(shape, sound = FeedbackSound.Close) {
                        onClear()
                        close()
                    }
                    .border(1.dp, AppColors.Line, shape),
                contentAlignment = Alignment.Center,
            ) { ActionLabel("Без цели", AppColors.Muted, fontSize = 14.sp, iconSize = 20.dp) }
        }
    }
}

@Composable
fun TextInputSheet(
    title: String,
    initial: String,
    hint: String,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    number: Boolean = false,
    maxLength: Int = 200,
    code: Boolean = false,
    multiline: Boolean = false,
    onDone: (String) -> Unit,
) {
    var text by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial, androidx.compose.ui.text.TextRange(initial.length))) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val corner = (26 * AppStyle.current.corners).dp
    val hideInput = remember { arrayOfNulls<() -> Unit>(1) }
    SheetWindow(onDismiss, onClosing = { hideInput[0]?.invoke() }) { control ->
        val close = control.close
        val innerFocus = androidx.compose.ui.platform.LocalFocusManager.current
        val innerKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        hideInput[0] = {
            innerFocus.clearFocus(force = true)
            innerKeyboard?.hide()
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching {
                focus.requestFocus()
                innerKeyboard?.show()
            }
        }
        val save = {
            onDone(text.text.trim())
            close()
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = corner, topEnd = corner))
                .background(AppColors.Panel)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(title, close)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).fieldSurface(appCorner(18)).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null && !multiline) {
                    Icon(icon, null, tint = AppColors.Muted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart) {
                    if (text.text.isEmpty()) Text(hint, color = AppColors.Faint, fontSize = if (multiline) 16.sp else 20.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    BasicTextField(
                        value = text,
                        onValueChange = { v ->
                            val clean = when {
                                number -> v.text.filter { it.isDigit() }
                                multiline -> v.text
                                else -> v.text.replace("\n", " ")
                            }
                            text = v.copy(text = clean.take(maxLength))
                        },
                        singleLine = !multiline,
                        minLines = if (multiline) 4 else 1,
                        maxLines = if (multiline) 9 else 1,
                        textStyle = if (multiline) TextStyle(color = AppColors.Text, fontSize = 16.sp, lineHeight = 22.sp) else TextStyle(color = AppColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(AppColors.Accent),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = if (number) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text,
                            capitalization = when {
                                number -> androidx.compose.ui.text.input.KeyboardCapitalization.None
                                code -> androidx.compose.ui.text.input.KeyboardCapitalization.Characters
                                else -> androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
                            },
                            autoCorrectEnabled = !code,
                            imeAction = if (multiline) androidx.compose.ui.text.input.ImeAction.Default else androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            val shape = appCorner(16)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .liveClick(shape, sound = FeedbackSound.Toggle) { save() }
                    .accentHighlight(shape),
                contentAlignment = Alignment.Center,
            ) { ActionLabel("Сохранить", accentContent) }
        }
    }
}

fun actionIcon(label: String): ImageVector? {
    val l = label.trim().lowercase()
    fun starts(vararg p: String) = p.any { l.startsWith(it) }
    return when {
        starts("удалить", "очистить", "стереть") -> AppIcons.Trash
        starts("сохранить в галерею") -> AppIcons.Image
        starts("сохранить", "готово", "применить", "отлично", "понятно", "принять") -> AppIcons.Check
        starts("создать", "добавить", "ещё", "новая", "новый") -> AppIcons.Plus
        starts("отмена", "не сейчас", "закрыть", "без цели", "пропустить") -> AppIcons.Close
        starts("отправить", "экспорт", "telegram") -> AppIcons.Send
        starts("импорт", "установить", "скачать", "rustore", "обновить") -> AppIcons.Download
        starts("повторить", "сбросить", "пройти", "заново", "вернуть") -> AppIcons.Rotate
        starts("позже") -> AppIcons.Clock
        starts("копировать") -> AppIcons.Copy
        starts("поделиться") -> AppIcons.Share
        starts("подключить") -> AppIcons.Cloud
        starts("проверить") -> AppIcons.Search
        starts("сделать основн") -> AppIcons.StarFilled
        starts("выбрать папку", "папка") -> AppIcons.Folder
        starts("выбрать другое видео", "видео") -> AppIcons.Video
        starts("выбрать", "картинк") -> AppIcons.Image
        starts("qr") -> AppIcons.Qr
        starts("показать") -> AppIcons.Eye
        starts("открыть", "к словарю", "читать") -> AppIcons.Book
        starts("назад") -> AppIcons.Back
        starts("далее", "дальше") -> AppIcons.Forward
        starts("начать", "продолжить", "слушать") -> AppIcons.Play
        starts("пауза") -> AppIcons.Pause
        starts("стоп", "остановить") -> AppIcons.Stop
        starts("оценить") -> AppIcons.Star
        starts("поддержать") -> AppIcons.Heart
        starts("в словарь") -> AppIcons.Translate
        starts("в заметк") -> AppIcons.Note
        starts("войти") -> AppIcons.Lock
        else -> null
    }
}

@Composable
fun ActionLabel(text: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit = 15.sp, weight: FontWeight = FontWeight.SemiBold, iconSize: androidx.compose.ui.unit.Dp = 22.dp, icon: ImageVector? = actionIcon(text)) {
    if (text.startsWith("✓ ")) {
        ActionLabel(text.removePrefix("✓ ").trim(), color, fontSize, weight, iconSize, AppIcons.Check)
        return
    }
    if (icon == null) {
        Text(text, color = color, fontSize = fontSize, fontWeight = weight, maxLines = 1)
        return
    }
    val wide = with(androidx.compose.ui.platform.LocalDensity.current) { 88.dp.roundToPx() }
    androidx.compose.ui.layout.Layout(
        content = {
            Icon(icon, text, tint = color, modifier = Modifier.size(iconSize))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(iconSize - 3.dp))
                Text(text, color = color, fontSize = fontSize, fontWeight = weight, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(iconSize - 5.dp))
                Text(text, color = color, fontSize = fontSize * 0.84f, fontWeight = weight, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp))
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val free = loose.copy(maxWidth = androidx.compose.ui.unit.Constraints.Infinity)
        val roomy = constraints.hasBoundedWidth && constraints.maxWidth >= wide
        val full = measurables[1].measure(free)
        val chosen = when {
            roomy && full.width <= constraints.maxWidth -> full
            roomy -> measurables[2].measure(free).takeIf { it.width <= constraints.maxWidth } ?: measurables[0].measure(loose)
            else -> measurables[0].measure(loose)
        }
        val w = chosen.width.coerceIn(constraints.minWidth, constraints.maxWidth.coerceAtLeast(constraints.minWidth))
        val h = chosen.height.coerceIn(constraints.minHeight, constraints.maxHeight.coerceAtLeast(constraints.minHeight))
        layout(w, h) { chosen.place((w - chosen.width) / 2, (h - chosen.height) / 2) }
    }
}
