package com.flerty.wikiflerty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
    Row(
        modifier.fillMaxWidth().heightIn(min = 34.dp).padding(start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(AppColors.Accent))
        Spacer(Modifier.width(10.dp))
        if (icon != null) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(AppColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(15.dp)) }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            color = AppColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            CountBadge(count)
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun CountBadge(count: Int) {
    Box(
        Modifier
            .heightIn(min = 20.dp)
            .clip(CircleShape)
            .background(AppColors.AccentSoft)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", color = AppColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
        Text(text, color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

val com.flerty.wikiflerty.data.ReadStatus.chipColor: Color
    get() = when (this) {
        com.flerty.wikiflerty.data.ReadStatus.Reading -> Color(0xFF8AB4F8)
        com.flerty.wikiflerty.data.ReadStatus.Planned -> Color(0xFFF2B36B)
        com.flerty.wikiflerty.data.ReadStatus.Finished -> Color(0xFF7ED3A0)
        com.flerty.wikiflerty.data.ReadStatus.New -> Color(0xFFA9AFBC)
    }
