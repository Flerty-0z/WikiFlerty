package com.flerty.wikiflerty.ui

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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(title, color = AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .fieldSurface(appCorner(18))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) { content() }
    }
}

@Composable
fun SettingsNavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = appCorner(18)
    Row(
        Modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .fieldSurface(shape)
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
            Text(
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
        feedback?.play(FeedbackSound.Toggle, view)
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
            Text(label, color = AppColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint != null) Text(hint, color = AppColors.Faint, fontSize = 12.sp, lineHeight = 16.sp)
        }
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
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onCommit(current) },
            valueRange = range,
            steps = steps,
            enabled = enabled,
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

data class PickerOption(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val font: FontFamily? = null,
    val icon: ImageVector? = null,
    val swatch: Color? = null,
)

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
private fun OptionLead(option: PickerOption, tint: Color) {
    when {
        option.swatch != null -> Box(
            Modifier.size(22.dp).clip(CircleShape).background(option.swatch).border(1.dp, AppColors.Line, CircleShape),
        )
        option.icon != null -> Icon(option.icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
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
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .liveClick(appCorner(12), sound = FeedbackSound.Open, pressedScale = 0.98f) { open = true }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingIcon(icon)
            Spacer(Modifier.width(12.dp))
        }
        Text(label, color = AppColors.Text, fontSize = 15.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(end = 12.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (selected?.swatch != null) {
                OptionLead(selected, AppColors.Accent)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                selected?.title ?: selectedId,
                color = AppColors.Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = selected?.font,
                maxLines = 1,
                softWrap = false,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Icon(AppIcons.Chevron, null, tint = AppColors.Accent, modifier = Modifier.padding(start = 2.dp).size(18.dp).rotate(90f))
    }
    if (open) {
        PickerSheet(label, options, selectedId, searchable, onDismiss = { open = false }) {
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
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val shown = options.filter { query.isBlank() || it.title.contains(query, true) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (options.indexOfFirst { it.id == selectedId } - 2).coerceAtLeast(0),
    )
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = AppColors.Panel,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = (26 * AppStyle.current.corners).dp,
            topEnd = (26 * AppStyle.current.corners).dp,
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            SheetHeader(title, close)
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
            LazyColumn(Modifier.heightIn(max = 520.dp), state = listState) {
                items(shown, key = { it.id }) { option ->
                    val isSelected = option.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .liveClick(appCorner(14), sound = FeedbackSound.Toggle, pressedScale = 0.98f) {
                                scope.launch { sheetState.hide() }.invokeOnCompletion { onSelect(option.id) }
                            }
                            .background(if (isSelected) AppColors.AccentTint else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (option.swatch != null || option.icon != null) {
                            OptionLead(option, if (isSelected) AppColors.Accent else AppColors.Muted)
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                option.title,
                                color = if (isSelected) AppColors.Accent else AppColors.Text,
                                fontSize = 16.sp,
                                fontFamily = option.font,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (option.subtitle != null) {
                                Text(option.subtitle, color = AppColors.Faint, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = option.font)
                            }
                        }
                        if (isSelected) Icon(AppIcons.Check, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
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
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = AppColors.Text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        Box(
            Modifier.size(38.dp).liveClick(CircleShape, sound = FeedbackSound.Close, onClick = onClose).background(AppColors.Field),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Close, "Закрыть", tint = AppColors.Muted, modifier = Modifier.size(18.dp)) }
    }
}
