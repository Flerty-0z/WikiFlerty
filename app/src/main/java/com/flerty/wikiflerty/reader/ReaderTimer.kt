package com.flerty.wikiflerty.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.RollingClock
import com.flerty.wikiflerty.ui.StatsCalc
import com.flerty.wikiflerty.ui.accentContent
import com.flerty.wikiflerty.ui.accentHighlight
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.liveClick
import kotlinx.coroutines.delay

@Composable
internal fun ReaderTimerChip(bookId: String, hidden: Boolean, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val watch by app.readingTimer.watch.collectAsStateWithLifecycle()
    val folded by app.readingTimer.folded.collectAsStateWithLifecycle()
    val result by app.readingTimer.result.collectAsStateWithLifecycle()
    val mine = watch?.takeIf { it.bookId == bookId }
    var summary by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(result) {
        val r = result?.takeIf { it.bookId == bookId } ?: return@LaunchedEffect
        summary = "Сеанс: " + StatsCalc.duration(r.ms).ifBlank { "<1 мин" }
        delay(3500)
        summary = null
    }
    val pulse = rememberInfiniteTransition(label = "timer-pulse").animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot")
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(top = 6.dp),
    ) {
        AnimatedVisibility(
            visible = !hidden && (mine != null || summary != null),
            enter = fadeIn(Motion.fade(220)) + slideInVertically(Motion.tween(300)) { -it },
            exit = fadeOut(Motion.fade(160)) + slideOutVertically(Motion.tween(240)) { -it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.fillMaxWidth().height(40.dp)) {
                val w = mine
                if (w == null) {
                    val text = summary
                    if (text != null) {
                        Box(Modifier.align(Alignment.Center).timerFrame(appCorner(14)).padding(horizontal = 14.dp, vertical = 9.dp)) {
                            Text(text, color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    return@Box
                }
                AnimatedVisibility(
                    visible = !folded,
                    enter = fadeIn(Motion.fade(220)) + slideInHorizontally(Motion.tween(320)) { it },
                    exit = fadeOut(Motion.fade(160)) + slideOutHorizontally(Motion.tween(280)) { it },
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    TimerBody(
                        elapsed = w::elapsed,
                        key = w.startedAt,
                        running = w.running,
                        pulse = { pulse.value },
                        onToggle = { if (w.running) app.readingTimer.pause() else app.readingTimer.resume() },
                        onStop = { app.readingTimer.stop() },
                        onFold = { app.readingTimer.setFolded(true) },
                    )
                }
                AnimatedVisibility(
                    visible = folded,
                    enter = fadeIn(Motion.fade(220)) + slideInHorizontally(Motion.tween(320)) { it },
                    exit = fadeOut(Motion.fade(140)) + slideOutHorizontally(Motion.tween(240)) { it },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    val shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                    Box(
                        Modifier
                            .width(34.dp)
                            .height(40.dp)
                            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.9f) { app.readingTimer.setFolded(false) }
                            .timerFrame(shape)
                            .semantics { contentDescription = "Показать таймер" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Chevron, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 180f })
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 7.dp)
                                .size(6.dp)
                                .graphicsLayer { alpha = if (w.running) pulse.value else 0.35f }
                                .background(AppColors.Accent, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.timerFrame(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.background(AppColors.AccentTint, shape).border(1.dp, AppColors.Accent.copy(alpha = 0.45f), shape)

@Composable
private fun TimerBody(
    elapsed: (Long) -> Long,
    key: Any,
    running: Boolean,
    pulse: () -> Float,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onFold: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(key) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val s = elapsed(now) / 1000
    val text = "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    Row(
        Modifier
            .height(40.dp)
            .timerFrame(appCorner(14))
            .padding(start = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(AppColors.Accent.copy(alpha = 0.16f), appCorner(8))
                .border(1.dp, AppColors.Accent.copy(alpha = 0.22f), appCorner(8)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Clock, null, tint = AppColors.Accent, modifier = Modifier.size(14.dp))
            if (running) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(5.dp)
                        .graphicsLayer { alpha = pulse() }
                        .background(AppColors.Accent, CircleShape),
                )
            }
        }
        Box(Modifier.graphicsLayer { alpha = if (!running && pulse() < 0.6f) 0.5f else 1f }) {
            RollingClock(text, size = 16.sp)
        }
        TimerButton(AppIcons.Stop, "Стоп", filled = false, sound = FeedbackSound.Open, onClick = onStop)
        Box(
            Modifier
                .size(30.dp)
                .liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.9f, onClick = onToggle)
                .accentHighlight(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = running,
                transitionSpec = {
                    (fadeIn(Motion.fade(180)) + scaleIn(Motion.tween(220), initialScale = 0.5f)) togetherWith
                        (fadeOut(Motion.fade(120)) + scaleOut(Motion.tween(160), targetScale = 0.5f)) using SizeTransform(clip = false)
                },
                label = "timer-icon",
            ) { on ->
                Icon(if (on) AppIcons.Pause else AppIcons.Play, if (on) "Пауза" else "Продолжить", tint = accentContent, modifier = Modifier.size(14.dp))
            }
        }
        Box(
            Modifier
                .size(30.dp)
                .liveClick(CircleShape, sound = FeedbackSound.Close, pressedScale = 0.9f, onClick = onFold)
                .semantics { contentDescription = "Свернуть таймер" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TimerButton(icon: ImageVector, description: String, filled: Boolean, sound: FeedbackSound, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .liveClick(CircleShape, sound = sound, pressedScale = 0.9f, onClick = onClick)
            .then(if (filled) Modifier.accentHighlight(CircleShape) else Modifier.border(1.dp, AppColors.Accent.copy(alpha = 0.45f), CircleShape)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (filled) accentContent else AppColors.Accent, modifier = Modifier.size(12.dp)) }
}
