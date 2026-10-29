package com.flerty.wikiflerty.ui

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

    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(20)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Год чтения", color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f))
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
        }

        AnimatedContent(
            targetState = year,
            transitionSpec = { fadeIn(Motion.fade(260)).togetherWith(fadeOut(Motion.fade(160))) },
            label = "year",
        ) { y ->
            YearGrid(y, perDay, selected) { selected = it }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Нажми на день", color = AppColors.Faint, fontSize = 10.5.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text("меньше", color = AppColors.Faint, fontSize = 10.sp, modifier = Modifier.padding(end = 2.dp))
            (0..4).forEach { Box(Modifier.size(10.dp).clip(appCorner(3)).background(cellColor(it))) }
            Text("больше", color = AppColors.Faint, fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp))
        }

        DayDetail(selected, days, perDay, books)

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
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Дней чтения", "$readDays", Modifier.weight(1f))
            MiniStat("Лучшая серия", StatsCalc.days(best), Modifier.weight(1f))
            MiniStat("Сильнее всего", strongest?.let { DayShort[it.value - 1] } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun YearGrid(year: Int, perDay: Map<LocalDate, DayEntry>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
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
        Row(Modifier.weight(1f).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            weeks.forEachIndexed { wi, start ->
                val shown = ((appear.value * (weeks.size + 8)) - wi).coerceIn(0f, 1f)
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.graphicsLayer {
                        alpha = shown
                        scaleY = 0.6f + 0.4f * shown
                    },
                ) {
                    val monthStart = (0..6).map { start.plusDays(it.toLong()) }.firstOrNull { it.dayOfMonth == 1 && it.year == year }
                        ?: if (wi == 0) LocalDate.of(year, 1, 1) else null
                    Box(Modifier.height(14.dp).width(12.dp), contentAlignment = Alignment.CenterStart) {
                        if (monthStart != null) {
                            Text(
                                MonthShort[monthStart.monthValue - 1],
                                color = AppColors.Faint,
                                fontSize = 8.5.sp,
                                lineHeight = 9.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.wrapContentHeight(unbounded = true).wrapContentWidth(Alignment.Start, unbounded = true),
                            )
                        }
                    }
                    (0..6).forEach { d ->
                        val day = start.plusDays(d.toLong())
                        val inYear = day.year == year
                        val future = day.isAfter(today)
                        val lv = level(perDay[day]?.ms ?: 0)
                        val isSel = day == selected
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(appCorner(3))
                                .background(
                                    when {
                                        !inYear -> Color.Transparent
                                        future -> AppColors.Line.copy(alpha = 0.25f)
                                        else -> cellColor(lv)
                                    },
                                )
                                .then(if (isSel) Modifier.border(1.5.dp, AppColors.Text, appCorner(3)) else Modifier)
                                .then(
                                    if (inYear && !future) {
                                        Modifier.liveClick(appCorner(3), sound = FeedbackSound.Tap, pressedScale = 0.8f) { onSelect(day) }
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetail(day: LocalDate, days: Map<String, Map<String, DayEntry>>, perDay: Map<LocalDate, DayEntry>, books: List<Book>) {
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
                Box(Modifier.width(5.dp).height(18.dp).clip(CircleShape).background(cellColor(level(entry?.ms ?: 0)).let { if (minutes == 0L) AppColors.Line else it }))
                Text(
                    dayTitle(day) + when {
                        minutes > 0 -> " · $minutes мин"
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
