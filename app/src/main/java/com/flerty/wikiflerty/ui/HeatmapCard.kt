package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.DayEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")
private val MonthShort = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")
private val DayShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val MonthGen = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

private fun dayTitle(d: LocalDate): String = "${DayShort[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${MonthGen[d.monthValue - 1]}"

private fun level(ms: Long): Int {
    val m = ms / 60_000
    return when {
        ms < 60_000 -> 0
        m < 10 -> 1
        m < 20 -> 2
        m < 40 -> 3
        else -> 4
    }
}

@Composable
private fun cellColor(level: Int): Color = when (level) {
    0 -> AppColors.Line.copy(alpha = 0.7f)
    1 -> AppColors.Accent.copy(alpha = 0.3f)
    2 -> AppColors.Accent.copy(alpha = 0.52f)
    3 -> AppColors.Accent.copy(alpha = 0.78f)
    else -> AppColors.AccentLight
}

@Composable
fun YearHeatmapCard(days: Map<String, Map<String, DayEntry>>, perDay: Map<LocalDate, DayEntry>, books: List<Book>) {
    val today = LocalDate.now()
    val years = remember(perDay) { (perDay.filterValues { it.ms > 0 }.keys.map { it.year } + today.year).distinct().sorted() }
    var year by rememberSaveable { mutableStateOf(today.year) }
    var selected by remember { mutableStateOf(today) }
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.flerty.wikiflerty.App
    val freezes by app.streakFreeze.freezes.collectAsStateWithLifecycle()
    val frozen = remember(freezes) { com.flerty.wikiflerty.data.StreakFreezeStore.days(freezes) }

    TabbedFrame("Год чтения", icon = AppIcons.Calendar, inset = false, trailing = {
            Row(
                Modifier.clip(CircleShape).border(1.dp, AppColors.Line, CircleShape).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val prev = years.lastOrNull { it < year }
                val next = years.firstOrNull { it > year }
                Box(
                    Modifier.size(26.dp).clip(CircleShape).liveClick(CircleShape, sound = FeedbackSound.Toggle) { if (prev != null) year = prev },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Back, "Прошлый год", tint = if (prev != null) AppColors.Text else AppColors.Line, modifier = Modifier.size(14.dp)) }
                Text("$year", color = AppColors.Text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 2.dp))
                Box(
                    Modifier.size(26.dp).clip(CircleShape).liveClick(CircleShape, sound = FeedbackSound.Toggle) { if (next != null) year = next },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Back, "Следующий год", tint = if (next != null) AppColors.Text else AppColors.Line, modifier = Modifier.size(14.dp).rotate(180f)) }
            }
    }) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        AnimatedContent(
            targetState = year,
            transitionSpec = { fadeIn(Motion.fade(260)).togetherWith(fadeOut(Motion.fade(160))) },
            label = "year",
        ) { y ->
            YearGrid(y, perDay, frozen, selected) { selected = it }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.weight(1f))
            if (frozen.any { it.year == year }) {
                Icon(AppIcons.Snow, null, tint = IceColor, modifier = Modifier.size(11.dp))
                Text("пауза", color = IceColor, fontSize = 10.sp, modifier = Modifier.padding(end = 6.dp))
            }
            Icon(AppIcons.Forward, "Меньше", tint = AppColors.Faint, modifier = Modifier.padding(end = 2.dp).size(13.dp).rotate(90f))
            (0..4).forEach { Box(Modifier.size(10.dp).clip(appCorner(3)).background(cellColor(it))) }
            Icon(AppIcons.Forward, "Больше", tint = AppColors.Faint, modifier = Modifier.padding(start = 2.dp).size(13.dp).rotate(-90f))
        }

        DayDetail(selected, days, perDay, books, selected in frozen)

        val inYear = perDay.filter { it.key.year == year && it.value.ms >= 60_000 }
        val readDays = inYear.size
        val best = remember(inYear) {
            var b = 0
            var run = 0
            var prev: LocalDate? = null
            inYear.keys.sorted().forEach { d ->
                run = if (prev?.plusDays(1) == d) run + 1 else 1
                b = maxOf(b, run)
                prev = d
            }
            b
        }
        val strongest = remember(inYear) {
            inYear.entries.groupBy { it.key.dayOfWeek }.mapValues { e -> e.value.sumOf { it.value.ms } }.maxByOrNull { it.value }?.key
        }
        StatPanel(
            null,
            listOf(
                StatCell("Дней чтения", "$readDays", icon = AppIcons.Calendar),
                StatCell("Лучшая серия", StatsCalc.days(best), icon = AppIcons.Fire),
                StatCell("Сильнее всего", strongest?.let { DayShort[it.value - 1] } ?: "—", icon = AppIcons.Chart),
            ),
            columns = 3,
        )
    }
    }
}

@Composable
private fun YearGrid(year: Int, perDay: Map<LocalDate, DayEntry>, frozen: Set<LocalDate>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val first = LocalDate.of(year, 1, 1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val last = LocalDate.of(year, 12, 31)
    val weeks = remember(year) {
        generateSequence(first) { it.plusWeeks(1) }.takeWhile { !it.isAfter(last) }.toList()
    }
    val scroll = rememberScrollState()
    val appear = remember(year) { Animatable(if (Motion.enabled) 0f else 1f) }
    LaunchedEffect(year) {
        if (year == today.year) {
            val idx = weeks.indexOfLast { !it.isAfter(today) }.coerceAtLeast(0)
            androidx.compose.runtime.withFrameNanos { }
            val ratio = (idx + 1).toFloat() / weeks.size
            scroll.scrollTo((scroll.maxValue * ratio.coerceIn(0f, 1f)).toInt().coerceAtMost(scroll.maxValue))
        } else {
            scroll.scrollTo(0)
        }
        appear.animateTo(1f, Motion.tween(900, 80, GrowEasing))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.height(14.dp))
            DayShort.forEachIndexed { i, d ->
                Box(Modifier.height(12.dp).width(16.dp), contentAlignment = Alignment.CenterStart) {
                    if (i % 2 == 0) {
                        Text(
                            d,
                            color = AppColors.Faint,
                            fontSize = 8.5.sp,
                            lineHeight = 9.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentHeight(unbounded = true).wrapContentWidth(Alignment.Start, unbounded = true),
                        )
                    }
                }
            }
        }
        val cells = (0..4).map { cellColor(it) }
        val futureColor = AppColors.Line.copy(alpha = 0.25f)
        val selColor = AppColors.Text
        val labelStyle = androidx.compose.ui.text.TextStyle(color = AppColors.Faint, fontSize = 8.5.sp)
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val months = remember(year, measurer) {
            weeks.mapIndexedNotNull { wi, start ->
                val m = (0..6).map { start.plusDays(it.toLong()) }.firstOrNull { it.dayOfMonth == 1 && it.year == year }
                    ?: if (wi == 0) LocalDate.of(year, 1, 1) else null
                m?.let { wi to measurer.measure(MonthShort[it.monthValue - 1], labelStyle) }
            }
        }
        val feedback = LocalFeedback.current
        val view = androidx.compose.ui.platform.LocalView.current
        val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "today").animateFloat(
            0f,
            1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.LinearEasing)),
            label = "today-pulse",
        )
        val pulseColor = AppColors.Accent
        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
            androidx.compose.foundation.Canvas(
                Modifier
                    .width(15.dp * weeks.size - 3.dp)
                    .height(14.dp + 15.dp * 7 - 3.dp)
                    .pointerInput(year) {
                        detectTapGestures { pos ->
                            val step = 15.dp.toPx()
                            val top = 14.dp.toPx()
                            val wi = (pos.x / step).toInt()
                            val di = ((pos.y - top) / step).toInt()
                            if (pos.y < top || wi !in weeks.indices || di !in 0..6) return@detectTapGestures
                            val day = weeks[wi].plusDays(di.toLong())
                            if (day.year == year && !day.isAfter(today)) {
                                feedback?.play(FeedbackSound.Tap, view)
                                onSelect(day)
                            }
                        }
                    },
            ) {
                val step = 15.dp.toPx()
                val cell = 12.dp.toPx()
                val top = 14.dp.toPx()
                val r = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx() * AppStyle.current.corners.coerceAtMost(1.3f))
                val shown = appear.value * (weeks.size + 8)
                months.forEach { (wi, layout) ->
                    val a = (shown - wi).coerceIn(0f, 1f)
                    if (a > 0f) drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(wi * step, 0f), alpha = a)
                }
                weeks.forEachIndexed { wi, start ->
                    val a = (shown - wi).coerceIn(0f, 1f)
                    if (a <= 0f) return@forEachIndexed
                    val sy = 0.6f + 0.4f * a
                    for (d in 0..6) {
                        val day = start.plusDays(d.toLong())
                        if (day.year != year) continue
                        val future = day.isAfter(today)
                        val lv = level(perDay[day]?.ms ?: 0)
                        val color = when {
                            day in frozen && lv == 0 -> IceColor.copy(alpha = if (future) 0.3f else 0.6f)
                            future -> futureColor
                            else -> cells[lv]
                        }
                        val h = cell * sy
                        val x = wi * step
                        val y = top + d * step + (cell - h) / 2
                        drawRoundRect(color.copy(alpha = color.alpha * a), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(cell, h), r)
                        if (day == today && Motion.enabled && a >= 1f) {
                            val p = pulse.value
                            val grow = 4.dp.toPx() * p
                            drawRoundRect(
                                pulseColor.copy(alpha = 0.55f * (1f - p)),
                                androidx.compose.ui.geometry.Offset(x - grow, y - grow),
                                androidx.compose.ui.geometry.Size(cell + grow * 2, h + grow * 2),
                                androidx.compose.ui.geometry.CornerRadius(r.x + grow, r.y + grow),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
                            )
                        }
                        if (day == selected) {
                            val sw = 1.5.dp.toPx()
                            drawRoundRect(
                                selColor,
                                androidx.compose.ui.geometry.Offset(x + sw / 2, y + sw / 2),
                                androidx.compose.ui.geometry.Size(cell - sw, h - sw),
                                r,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(sw),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetail(day: LocalDate, days: Map<String, Map<String, DayEntry>>, perDay: Map<LocalDate, DayEntry>, books: List<Book>, frozen: Boolean) {
    val entry = perDay[day]
    val minutes = (entry?.ms ?: 0) / 60_000
    val perBook = days[day.toString()].orEmpty().filterValues { it.ms > 0 }.entries.sortedByDescending { it.value.ms }
    val read = perBook.mapNotNull { (key, e) ->
        val b = books.firstOrNull { it.statsKey == key } ?: return@mapNotNull null
        b to e
    }
    AnimatedContent(
        targetState = day,
        transitionSpec = { fadeIn(Motion.fade(200)).togetherWith(fadeOut(Motion.fade(120))) },
        label = "day",
    ) { _ ->
        Column(
            Modifier.fillMaxWidth().clip(appCorner(12)).background(AppColors.Background).border(1.dp, AppColors.PanelBorder, appCorner(12)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(5.dp).height(18.dp).clip(CircleShape).background(cellColor(level(entry?.ms ?: 0)).let { if (minutes == 0L) (if (frozen) IceColor else AppColors.Line) else it }))
                Text(
                    dayTitle(day) + when {
                        minutes > 0 -> " · $minutes мин"
                        frozen -> " · заморозка ❄"
                        read.isEmpty() -> " · без чтения"
                        else -> ""
                    },
                    color = AppColors.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
            read.take(3).forEach { (book, e) ->
                BookTag(book.id, book.title, coverWidth = 28.dp) {
                    if (e.pages > 0) {
                        Text(
                            "${e.pages} стр.",
                            color = AppColors.Muted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.clip(CircleShape).background(AppColors.Line).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().clip(appCorner(14)).background(AppColors.Background).border(1.dp, AppColors.PanelBorder, appCorner(14)).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = AppColors.Faint, fontSize = 10.5.sp, maxLines = 1, softWrap = false, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 10.5.sp, stepSize = 0.5.sp))
        Text(value, color = AppColors.Text, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 20.sp, stepSize = 0.5.sp))
    }
}
