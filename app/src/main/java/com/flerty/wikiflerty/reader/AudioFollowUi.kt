package com.flerty.wikiflerty.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface

@Composable
fun AudioFollowPill(playing: Boolean, onHere: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val transition = rememberInfiniteTransition(label = "audioPill")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(Motion.loop<Float>(900), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "pulse",
    )
    Row(
        Modifier.panelSurface(RoundedCornerShape(22.dp), elevation = 8.dp).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .liveClick(CircleShape, sound = FeedbackSound.Go, pressedScale = 0.85f, onClick = onHere)
                .drawWithContent {
                    drawContent()
                    drawCircle(AppColors.Accent.copy(alpha = if (playing) pulse else 0.3f), size.minDimension / 2f - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Headphones, "Слушать с этой страницы", tint = AppColors.Accent, modifier = Modifier.size(17.dp))
        }
        Box(
            Modifier.size(36.dp).liveClick(CircleShape, sound = FeedbackSound.SkipBack, pressedScale = 0.85f) { app.audio.skip(-15_000) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Rewind, "Назад на 15 секунд", tint = AppColors.Text, modifier = Modifier.size(18.dp))
            Text("15", color = AppColors.Muted, fontSize = 7.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
        }
        Box(
            Modifier.size(36.dp).liveClick(CircleShape, sound = if (playing) FeedbackSound.Pause else FeedbackSound.Play, pressedScale = 0.85f) { app.audio.toggle() },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(playing, transitionSpec = { (fadeIn(Motion.fade(160)) + scaleIn(Motion.tween(200), initialScale = 0.7f)) togetherWith fadeOut(Motion.fade(120)) }, label = "pillPlay") { on ->
                Icon(if (on) AppIcons.Pause else AppIcons.Play, if (on) "Пауза" else "Слушать", tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}
