package com.flerty.wikiflerty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: Int? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = 560.dp)
                .heightIn(min = 42.dp)
                .fieldSurface(CircleShape)
                .padding(start = if (icon != null) 6.dp else 16.dp, end = if (action != null || count != null) 8.dp else 16.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(AppColors.AccentSoft),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                title,
                color = AppColors.Text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
                modifier = Modifier.weight(1f, fill = false),
            )
            if (count != null) {
                Spacer(Modifier.width(8.dp))
                CountBadge(count)
            }
            if (action != null) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(1.dp).height(22.dp).background(AppColors.Line))
                Spacer(Modifier.width(4.dp))
                Box(Modifier.widthIn(max = 170.dp)) { action() }
            }
        }
    }
}

@Composable
fun HeaderAction(text: String, icon: ImageVector? = null, color: Color = AppColors.Accent, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .then(if (onClick != null) Modifier.liveClick(CircleShape, sound = FeedbackSound.Open, pressedScale = 0.92f, onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Text(
            text,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp, stepSize = 0.5.sp),
        )
    }
}

@Composable
fun CountBadge(count: Int) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 26.dp, minHeight = 26.dp)
            .clip(CircleShape)
            .background(AppColors.AccentSoft)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun SettingCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .fieldSurface(appCorner(18))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { content() }
}

@Composable
fun DividedColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val line = AppColors.Line.copy(alpha = 0.55f)
    androidx.compose.ui.layout.SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minHeight = 0)
        val items = subcompose("items", content).map { it.measure(loose) }.filter { it.height > 0 }
        val gaps = (items.size - 1).coerceAtLeast(0)
        val lines = subcompose("lines") {
            repeat(gaps) { Box(Modifier.fillMaxWidth().height(1.dp).background(line)) }
        }.map { it.measure(loose.copy(minWidth = 0)) }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else (items.maxOfOrNull { it.width } ?: 0)
        val height = items.sumOf { it.height }
        layout(width, height) {
            var y = 0
            items.forEachIndexed { i, p ->
                p.place(0, y)
                y += p.height
                if (i < gaps) lines[i].place(0, y)
            }
        }
    }
}

@Composable
fun InfoChip(text: String, color: Color = AppColors.Muted, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 24.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.22f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun ActionPill(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 30.dp)
            .liveClick(CircleShape, sound = FeedbackSound.Open, pressedScale = 0.92f, onClick = onClick)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(14.dp))
        Text(
            text,
            color = AppColors.Accent,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
        )
    }
}

val com.flerty.wikiflerty.data.ReadStatus.chipColor: Color
    get() = when (this) {
        com.flerty.wikiflerty.data.ReadStatus.Reading -> Color(0xFF8AB4F8)
        com.flerty.wikiflerty.data.ReadStatus.Planned -> Color(0xFFF2B36B)
        com.flerty.wikiflerty.data.ReadStatus.Finished -> Color(0xFF7ED3A0)
        com.flerty.wikiflerty.data.ReadStatus.New -> Color(0xFFA9AFBC)
    }
