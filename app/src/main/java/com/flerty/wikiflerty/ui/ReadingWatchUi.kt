package com.flerty.wikiflerty.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.system.ReadingSessionResult
import com.flerty.wikiflerty.system.ReadingWatch
import kotlinx.coroutines.delay

private sealed interface WatchView {
    data object Idle : WatchView
    data class Active(val watch: ReadingWatch) : WatchView
    data class Done(val result: ReadingSessionResult) : WatchView
}

@Composable
fun ReadingWatchCard(book: Book) {
    val app = LocalContext.current.applicationContext as App
    val watch by app.readingTimer.watch.collectAsStateWithLifecycle()
    val result by app.readingTimer.result.collectAsStateWithLifecycle()
    val view: WatchView = when {
        watch?.bookId == book.id -> WatchView.Active(watch!!)
        result?.bookId == book.id -> WatchView.Done(result!!)
        else -> WatchView.Idle
    }
    AnimatedContent(
        targetState = view,
        contentKey = { it::class },
        transitionSpec = {
            val enter = fadeIn(Motion.fade(260)) + scaleIn(Motion.tween(320), initialScale = 0.94f)
            val exit = fadeOut(Motion.fade(160)) + scaleOut(Motion.tween(200), targetScale = 0.96f)
            (enter togetherWith exit).using(SizeTransform(clip = false) { _, _ -> Motion.tween(320) })
        },
        label = "reading-watch",
    ) { state ->
        StopwatchShell(active = state is WatchView.Active, moon = state !is WatchView.Done) {
            when (state) {
                WatchView.Idle -> StopwatchIdle(note = null) { app.readingTimer.start(book.id, book.progress) }
                is WatchView.Active -> StopwatchActive(
                    state.watch,
                    elapsed = state.watch::elapsed,
                    running = state.watch.running,
                    onToggle = { if (state.watch.running) app.readingTimer.pause() else app.readingTimer.resume() },
                    onStop = { app.readingTimer.stop() },
                    onCancel = { app.readingTimer.cancel() },
                )
                is WatchView.Done -> WatchResult(app, book, state.result) { app.readingTimer.dismissResult() }
            }
        }
    }
}

@Composable
fun StopwatchShell(active: Boolean, moon: Boolean = true, content: @Composable () -> Unit) {
    val shape = appCorner(18)
    Box(
        Modifier
            .fillMaxWidth()
            .sheetReveal()
            .then(if (active) Modifier.background(AppColors.AccentTint, shape).border(1.dp, AppColors.Accent.copy(alpha = 0.45f), shape) else Modifier.fieldSurface(shape)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(if (moon) androidx.compose.foundation.layout.PaddingValues(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp) else androidx.compose.foundation.layout.PaddingValues(16.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun WatchBadge(running: Boolean, pulse: (() -> Float)?) {
    Box(
        Modifier
            .size(34.dp)
            .clip(appCorner(10))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(AppColors.Accent.copy(alpha = 0.28f), AppColors.Accent.copy(alpha = 0.08f))))
            .border(1.dp, AppColors.Accent.copy(alpha = 0.22f), appCorner(10)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Clock, null, tint = AppColors.Accent, modifier = Modifier.size(17.dp))
        if (running && pulse != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(6.dp)
                    .graphicsLayer { alpha = pulse() }
                    .background(AppColors.Accent, CircleShape),
            )
        }
    }
}

@Composable
private fun CompactDigits(ms: Long, modifier: Modifier, dim: () -> Boolean = { false }) {
    val s = ms / 1000
    val text = "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    Box(modifier.graphicsLayer { alpha = if (dim()) 0.5f else 1f }, contentAlignment = Alignment.CenterStart) {
        RollingClock(text, size = 20.sp)
    }
}

@Composable
private fun FocusMoon(modifier: Modifier = Modifier, size: Int = 44) {
    val context = LocalContext.current
    val on by com.flerty.wikiflerty.system.FocusMode.enabled.collectAsStateWithLifecycle()
    var askAccess by remember { androidx.compose.runtime.mutableStateOf(false) }
    val fill by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, Motion.tween(260), label = "moon")
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(on) {
        if (Motion.enabled) {
            pop.snapTo(0.8f)
            pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f))
        }
    }
    Box(
        modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.88f) {
                if (!on && !com.flerty.wikiflerty.system.FocusMode.hasAccess(context)) {
                    askAccess = true
                } else {
                    com.flerty.wikiflerty.system.FocusMode.setEnabled(context, !on)
                }
            }
            .background(AppColors.Accent.copy(alpha = 0.18f * fill), CircleShape)
            .border(1.dp, if (on) AppColors.Accent.copy(alpha = 0.7f) else AppColors.Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons.Moon,
            if (on) "Фокус включён" else "Фокус выключен",
            tint = if (on) AppColors.Accent else AppColors.Muted,
            modifier = Modifier.size((size * 0.45f).dp).graphicsLayer { rotationZ = -20f * fill },
        )
    }
    if (askAccess) {
        ConfirmSheet(
            title = "Фокус-режим",
            text = "Пока идёт сеанс, телефон будет в режиме «Не беспокоить», а после — вернётся как было.\n\nДля этого нужен доступ: в открывшихся настройках найди WikiFlerty и включи его.",
            confirm = "Открыть",
            danger = false,
            onConfirm = {
                com.flerty.wikiflerty.system.FocusMode.setEnabled(context, true)
                com.flerty.wikiflerty.system.FocusMode.requestAccess(context)
            },
            onDismiss = { askAccess = false },
        )
    }
}

@Composable
private fun StatePill(text: String, color: Color, dot: (() -> Float)? = null) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(7.dp).graphicsLayer { alpha = dot() }.background(color, CircleShape))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun RoundControl(icon: ImageVector, description: String, size: Int, filled: Boolean, sound: FeedbackSound, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .liveClick(CircleShape, sound = sound, pressedScale = 0.9f, onClick = onClick)
            .then(if (filled) Modifier.accentHighlight(CircleShape) else Modifier.border(1.dp, AppColors.Accent.copy(alpha = 0.45f), CircleShape)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (filled) accentContent else AppColors.Accent, modifier = Modifier.size((size * 0.4f).dp)) }
}

@Composable
fun StopwatchIdle(note: String? = null, onStart: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WatchBadge(running = false, pulse = null)
        CompactDigits(0L, Modifier.weight(1f).padding(start = 2.dp), dim = { true })
        FocusMoon(size = 36)
        RoundControl(AppIcons.Play, "Засечь время", 40, filled = true, sound = FeedbackSound.Toggle, onClick = onStart)
    }
    if (note != null) Text(note, color = AppColors.Faint, fontSize = 12.sp, maxLines = 1)
}

@Composable
fun StopwatchActive(key: Any, elapsed: (Long) -> Long, running: Boolean, onToggle: () -> Unit, onStop: () -> Unit, onCancel: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(key) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val pulse = rememberInfiniteTransition(label = "watch-pulse").animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WatchBadge(running = running, pulse = { pulse.value })
        CompactDigits(elapsed(now), Modifier.weight(1f).padding(start = 4.dp), dim = { !running && pulse.value < 0.6f })
        FocusMoon(size = 36)
        RoundControl(AppIcons.Stop, "Стоп", 36, filled = false, sound = FeedbackSound.Open, onClick = onStop)
        Box(
            Modifier.size(40.dp).liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.9f, onClick = onToggle).accentHighlight(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = running,
                transitionSpec = { (fadeIn(Motion.fade(180)) + scaleIn(Motion.tween(220), initialScale = 0.5f)) togetherWith (fadeOut(Motion.fade(120)) + scaleOut(Motion.tween(160), targetScale = 0.5f)) },
                label = "watch-icon",
            ) { on ->
                Icon(if (on) AppIcons.Pause else AppIcons.Play, if (on) "Пауза" else "Продолжить", tint = accentContent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun WatchResult(app: App, book: Book, result: ReadingSessionResult, onDone: () -> Unit) {
    val days by app.stats.days.collectAsStateWithLifecycle()
    val stats = remember(days, book.statsKey) { StatsCalc.book(days, book.statsKey) }
    val delta = (book.progress - result.startProgress).coerceAtLeast(0.0)
    val total = book.positions
    val pages = if (total > 0) Math.round(delta * total).toInt() else 0
    val hours = result.ms / 3_600_000.0
    val speed = if (pages > 0 && result.ms >= 60_000) pages / hours else stats.pagesPerHour
    val leftPages = if (total > 0) Math.round((1.0 - book.progress).coerceAtLeast(0.0) * total).toInt() else null
    val leftMs = if (leftPages != null && speed != null && speed > 0) (leftPages / speed * 3_600_000).toLong() else null

    val feedback = LocalFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    var shine by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(result) {
        delay(160)
        shine++
        feedback?.play(FeedbackSound.Success, view)
    }
    Box(Modifier.sparkBurst(shine, listOf(AppColors.Good, AppColors.Accent, StarYellow), count = 16, reach = 0.8f)) {
        StatePill("Итог сеанса", AppColors.Good)
    }
    StatPanel(
        null,
        listOf(
            StatCell("Время", StatsCalc.duration(result.ms).ifBlank { "<1 мин" }, icon = AppIcons.Clock),
            StatCell("Прочитано", if (total > 0) "+$pages стр." else "+${(delta * 100).toInt()}%", icon = AppIcons.Pages),
            StatCell("Скорость", speed?.let { "${it.toInt()} стр/ч" } ?: "—", icon = AppIcons.Speed),
            StatCell(
                "До конца",
                when {
                    leftPages == 0 -> "Прочитано"
                    leftMs != null -> "≈ " + StatsCalc.duration(leftMs)
                    else -> "—"
                },
                hint = leftPages?.takeIf { it > 0 }?.let { "$it стр." },
                icon = AppIcons.Calendar,
                content = if (leftPages == 0) {
                    { StatusPill(com.flerty.wikiflerty.data.ReadStatus.Finished, small = false, framed = true) }
                } else {
                    null
                },
            ),
        ),
    )
    val shape = appCorner(16)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).liveClick(shape, sound = FeedbackSound.Close, onClick = onDone).border(1.dp, AppColors.Line, shape),
        contentAlignment = Alignment.Center,
    ) { ActionLabel("Готово", AppColors.Text) }
}
