package com.flerty.wikiflerty.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.DayEntry
import com.flerty.wikiflerty.data.Freeze
import com.flerty.wikiflerty.data.StreakFreezeStore
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

val IceColor = Color(0xFF7CC6F2)
private val IceDeep = Color(0xFF06202E)

private val MonthGenitive = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
private val WeekShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

fun freezeRange(f: Freeze): String {
    val s = f.start
    val e = f.end
    return when {
        f.days == 1 -> "${s.dayOfMonth} ${MonthGenitive[s.monthValue - 1]}"
        s.month == e.month -> "${s.dayOfMonth}–${e.dayOfMonth} ${MonthGenitive[e.monthValue - 1]}"
        else -> "${s.dayOfMonth} ${MonthGenitive[s.monthValue - 1]} – ${e.dayOfMonth} ${MonthGenitive[e.monthValue - 1]}"
    }
}

@Composable
fun StreakCard(perDay: Map<LocalDate, DayEntry>, today: LocalDate) {
    val app = LocalContext.current.applicationContext as App
    val freezes by app.streakFreeze.freezes.collectAsStateWithLifecycle()
    val (current, best) = StatsCalc.streaks(perDay, today, frozen = StreakFreezeStore.days(freezes))
    val cooldown = app.streakFreeze.cooldownLeft(today)
    val active = freezes.lastOrNull { !it.end.isBefore(today) }
    var sheet by remember { mutableStateOf(false) }
    val shape = appCorner(20)
    val feedback = LocalFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    var snow by remember { mutableIntStateOf(0) }
    var thaw by remember { mutableIntStateOf(0) }
    var hadActive by remember { mutableStateOf(active != null) }
    LaunchedEffect(active != null) {
        val now = active != null
        if (now != hadActive) {
            if (now) {
                snow++
                feedback?.play(FeedbackSound.Freeze, view)
            } else {
                thaw++
                feedback?.play(FeedbackSound.Thaw, view)
            }
        }
        hadActive = now
    }
    val flame = rememberInfiniteTransition(label = "flame")
    val flicker by flame.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "flicker")
    val lit = current > 0 && Motion.enabled

    Column(
        Modifier.fillMaxWidth().snowfall(snow, IceColor).thawDrops(thaw, IceColor).fieldSurface(shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(50.dp)
                    .drawBehind {
                        if (lit) {
                            val w = (kotlin.math.sin(flicker * 2f * Math.PI.toFloat()) + 1f) / 2f
                            drawCircle(Color(0xFFFF9A3C).copy(alpha = 0.10f + 0.12f * w), size.minDimension * (0.56f + 0.06f * w))
                        }
                    }
                    .clip(CircleShape)
                    .background(if (active != null) IceColor.copy(alpha = 0.16f) else AppColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (active != null) AppIcons.Snow else AppIcons.Fire,
                    null,
                    tint = if (active != null) IceColor else AppColors.Accent,
                    modifier = Modifier.size(26.dp).graphicsLayer {
                        if (lit && active == null) {
                            val t = flicker * 2f * Math.PI.toFloat()
                            scaleX = 1f + 0.05f * kotlin.math.sin(t * 2f)
                            scaleY = 1f + 0.08f * kotlin.math.sin(t * 3f + 1f)
                            rotationZ = 4f * kotlin.math.sin(t)
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.9f)
                        }
                    },
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    StatsCalc.days(current),
                    color = AppColors.Text,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 24.sp, stepSize = 0.5.sp),
                )
                Text("Серия · лучшая ${StatsCalc.days(best)}", color = AppColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val iceShape = appCorner(16)
            Box(
                Modifier
                    .size(48.dp)
                    .liveClick(iceShape, sound = FeedbackSound.Open, pressedScale = 0.9f) { sheet = true }
                    .background(IceColor.copy(alpha = if (cooldown == 0) 0.16f else 0.07f), iceShape)
                    .border(1.dp, IceColor.copy(alpha = if (cooldown == 0) 0.55f else 0.22f), iceShape),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Snow, "Заморозка серии", tint = IceColor.copy(alpha = if (cooldown == 0) 1f else 0.5f), modifier = Modifier.size(22.dp)) }
        }
        val line = when {
            active != null && active.start.isAfter(today) -> "Заморозка: ${freezeRange(active)}"
            active != null -> "Заморожено: ${freezeRange(active)}"
            cooldown > 0 -> "Заморозка через ${StatsCalc.days(cooldown)}"
            else -> null
        }
        if (line != null) {
            Row(
                Modifier.fillMaxWidth().clip(appCorner(12)).background(IceColor.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(AppIcons.Snow, null, tint = IceColor, modifier = Modifier.size(14.dp))
                Text(line, color = IceColor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (active != null && cooldown > 0) Text("${cooldown} дн.", color = IceColor.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
            }
        }
    }

    if (sheet) FreezeSheet { sheet = false }
}

@Composable
fun FreezeSheet(onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val freezes by app.streakFreeze.freezes.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val cooldown = app.streakFreeze.cooldownLeft(today)
    AppSheet(onDismiss, title = "Заморозка серии") {
        val close = sheetClose(onDismiss)
        val active = freezes.lastOrNull { !it.end.isBefore(today) }
        if (cooldown > 0) {
            val last = app.streakFreeze.last()
            CooldownView(cooldown, last, onUnfreeze = if (active != null) ({
                app.streakFreeze.unfreeze(today)
                close()
            }) else null)
        } else {
            FreezePicker(today, onFreeze = { start, days ->
                app.streakFreeze.add(start, days)
                close()
            })
        }
    }
}

@Composable
private fun FreezePicker(today: LocalDate, onFreeze: (LocalDate, Int) -> Unit) {
    var days by remember { mutableIntStateOf(1) }
    var start by remember { mutableStateOf(today) }
    FreezeFrame("Сколько дней") {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..StreakFreezeStore.MAX_DAYS).forEach { n ->
            val on = n == days
            val shape = appCorner(14)
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .liveClick(shape, sound = FeedbackSound.Toggle) { days = n }
                    .background(if (on) IceColor.copy(alpha = 0.18f) else Color.Transparent, shape)
                    .border(1.dp, if (on) IceColor else AppColors.Line, shape),
                contentAlignment = Alignment.Center,
            ) { Text("$n", color = if (on) IceColor else AppColors.Muted, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
    }
    FreezeFrame("С какого дня") {
    val first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            WeekShort.forEach { Text(it, color = AppColors.Faint, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        (0 until 3).forEach { w ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { d ->
                    val day = first.plusDays(w * 7L + d)
                    val past = day.isBefore(today)
                    val inRange = !day.isBefore(start) && day.isBefore(start.plusDays(days.toLong()))
                    val shape = appCorner(10)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .then(if (!past) Modifier.liveClick(shape, sound = FeedbackSound.Tap) { start = day } else Modifier)
                            .background(if (inRange) IceColor.copy(alpha = 0.2f) else Color.Transparent, shape)
                            .then(if (day == today) Modifier.border(1.dp, AppColors.Accent, shape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${day.dayOfMonth}",
                            color = when {
                                inRange -> IceColor
                                past -> AppColors.Line
                                else -> AppColors.Muted
                            },
                            fontSize = 13.sp,
                            fontWeight = if (inRange) FontWeight.ExtraBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
    val preview = Freeze(start, days, 0)
    Row(
        Modifier.fillMaxWidth().clip(appCorner(12)).background(IceColor.copy(alpha = 0.08f)).border(1.dp, IceColor.copy(alpha = 0.25f), appCorner(12)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(AppIcons.Snow, null, tint = IceColor, modifier = Modifier.size(14.dp))
        Text("Пауза: ${freezeRange(preview)}", color = IceColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
    }
    IceButton("Заморозить", Modifier.fillMaxWidth()) { onFreeze(start, days) }
}

@Composable
private fun FreezeFrame(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp))
        content()
    }
}

@Composable
private fun CooldownView(left: Int, last: Freeze?, onUnfreeze: (() -> Unit)?) {
    val progress by animateFloatAsState(1f - left / StreakFreezeStore.COOLDOWN_DAYS.toFloat(), Motion.tween(700), label = "cool")
    Column(Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(vertical = 16.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            val track = AppColors.Line
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                drawArc(IceColor, -90f, 360f * progress, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Icon(AppIcons.Snow, null, tint = IceColor, modifier = Modifier.size(36.dp))
        }
        Text(StatsCalc.days(left), color = AppColors.Text, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
        Text("до следующей заморозки", color = AppColors.Muted, fontSize = 13.sp)
        if (last != null) {
            Text(
                "Последняя: ${freezeRange(last)} · ${StatsCalc.days(last.days)}",
                color = IceColor,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 8.dp).clip(appCorner(12)).background(IceColor.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
    if (onUnfreeze != null) {
        val shape = appCorner(16)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .liveClick(shape, sound = FeedbackSound.Close, pressedScale = 0.97f, onClick = onUnfreeze)
                .border(1.dp, IceColor.copy(alpha = 0.6f), shape),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Fire, null, tint = IceColor, modifier = Modifier.size(18.dp))
            Text("Разморозить", color = IceColor, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun IceButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = appCorner(16)
    Row(
        modifier
            .height(52.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, onClick = onClick)
            .background(IceColor, shape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Snow, null, tint = IceDeep, modifier = Modifier.size(18.dp))
        Text(label, color = IceDeep, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
    }
}

@Composable
fun BoxScope.StreakRescue() {
    val app = LocalContext.current.applicationContext as App
    val days by app.stats.days.collectAsStateWithLifecycle()
    val freezes by app.streakFreeze.freezes.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var dismissed by remember { mutableStateOf(app.streakFreeze.rescueDismissed == today.toString()) }
    val streak = remember(days, freezes) { StatsCalc.rescuable(StatsCalc.perDay(days), today) }
    val show = !dismissed && streak >= 2 && app.streakFreeze.available(today)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) {
            kotlinx.coroutines.delay(700)
            visible = true
        } else {
            visible = false
        }
    }
    val dismiss = {
        app.streakFreeze.rescueDismissed = today.toString()
        dismissed = true
    }
    AnimatedVisibility(
        visible = visible && show,
        enter = fadeIn(Motion.fade(260)) + scaleIn(Motion.tween(320), initialScale = 0.9f),
        exit = fadeOut(Motion.fade(180)),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp, start = 16.dp, end = 16.dp),
    ) {
        val shape = appCorner(22)
        Column(
            Modifier
                .fillMaxWidth()
                .panelSurface(shape, elevation = 16.dp)
                .border(1.dp, IceColor.copy(alpha = 0.5f), shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.Fire, null, tint = AppColors.Accent, modifier = Modifier.size(30.dp))
                Icon(AppIcons.Snow, null, tint = IceColor, modifier = Modifier.size(30.dp))
            }
            Text("Спасти серию в ${StatsCalc.days(streak)}?", color = AppColors.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Вчера ты не читал. Заморозь вчерашний день — серия сохранится. Следующая заморозка будет через 30 дней.",
                color = AppColors.Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IceButton("Спасти", Modifier.weight(1f)) {
                    app.streakFreeze.add(today.minusDays(1), 1)
                    dismiss()
                }
                val shape2 = appCorner(16)
                Box(
                    Modifier
                        .height(52.dp)
                        .liveClick(shape2, sound = FeedbackSound.Close) { dismiss() }
                        .border(1.dp, AppColors.Line, shape2)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Не надо", color = AppColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            }
        }
    }
}
