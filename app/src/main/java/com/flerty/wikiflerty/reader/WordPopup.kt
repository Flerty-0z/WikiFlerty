package com.flerty.wikiflerty.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.data.DictionaryEntry
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface
import com.flerty.wikiflerty.ui.revealScroll

internal val WordMarkRed = Color(0xFFE5484D)

@Composable
internal fun WordPopup(entry: DictionaryEntry, onClose: () -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    var long by remember { mutableStateOf(false) }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, Motion.tween(260), label = "word-turn")
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .panelSurface(appCorner(22), elevation = 18.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(WordMarkRed.copy(alpha = 0.14f), CircleShape)
                    .border(1.dp, WordMarkRed.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("?", color = WordMarkRed, fontSize = 19.sp, fontWeight = FontWeight.Black) }
            Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.word, color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Из словаря", color = AppColors.Faint, fontSize = 11.5.sp, maxLines = 1)
            }
            androidx.compose.animation.AnimatedVisibility(long || open, enter = fadeIn(Motion.fade(200)), exit = fadeOut(Motion.fade(160))) {
                RoundIconButton(AppIcons.Chevron, if (open) "Свернуть" else "Развернуть", sound = FeedbackSound.Toggle, iconModifier = Modifier.graphicsLayer { rotationZ = 90f + turn }) { open = !open }
            }
            RoundIconButton(AppIcons.Close, "Закрыть", sound = FeedbackSound.Close, onClick = onClose)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(end = 8.dp)
                .liveClick(appCorner(12), sound = FeedbackSound.Toggle, pressedScale = 0.99f) { if (long) open = !open },
        ) {
            com.flerty.wikiflerty.ui.ExpandingText(
                entry.definition.ifBlank { "Без определения" },
                expanded = open,
                lines = 4,
                color = AppColors.Text,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).revealScroll(),
                onOverflow = { if (it) long = true },
            )
        }
        if (entry.context.isNotBlank()) {
            AnimatedContent(open, transitionSpec = { fadeIn(Motion.fade(220)) togetherWith fadeOut(Motion.fade(160)) }, label = "word-context") { shown ->
                if (shown) {
                    Text(
                        "«" + entry.context.trim() + "»",
                        color = AppColors.Muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 2.dp),
                    )
                } else {
                    Box(Modifier.size(0.dp))
                }
            }
        }
    }
}
