package com.flerty.wikiflerty.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.DayEntry
import com.flerty.wikiflerty.data.ReadStatus
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BookStats(val ms: Long, val pages: Int, val days: Int, val firstDay: LocalDate?, val lastDay: LocalDate?) {
    val pagesPerHour: Double? get() = if (ms >= 5 * 60_000L && pages > 0) pages / (ms / 3_600_000.0) else null
}

object StatsCalc {
    private const val STREAK_MS = 60_000L

    fun perDay(days: Map<String, Map<String, DayEntry>>): Map<LocalDate, DayEntry> =
        days.mapNotNull { (day, books) ->
            runCatching { LocalDate.parse(day) }.getOrNull()?.let { it to books.values.fold(DayEntry()) { a, b -> a + b } }
        }.toMap()

    fun book(days: Map<String, Map<String, DayEntry>>, key: String): BookStats {
        var ms = 0L
        var pages = 0
        val dates = mutableListOf<LocalDate>()
        days.forEach { (day, books) ->
            val e = books[key] ?: return@forEach
            ms += e.ms
            pages += e.pages
            if (e.ms >= STREAK_MS) runCatching { LocalDate.parse(day) }.getOrNull()?.let { dates += it }
        }
        return BookStats(ms, pages, dates.size, dates.minOrNull(), dates.maxOrNull())
    }

    fun series(perDay: Map<LocalDate, DayEntry>, count: Int, today: LocalDate = LocalDate.now()): List<Long> =
        (count - 1 downTo 0).map { perDay[today.minusDays(it.toLong())]?.ms ?: 0L }

    fun sumSince(perDay: Map<LocalDate, DayEntry>, from: LocalDate): DayEntry =
        perDay.filterKeys { !it.isBefore(from) }.values.fold(DayEntry()) { a, b -> a + b }

    fun streaks(perDay: Map<LocalDate, DayEntry>, today: LocalDate = LocalDate.now(), minMs: Long = STREAK_MS): Pair<Int, Int> {
        val read = perDay.filterValues { it.ms >= minMs }.keys.sorted()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        read.forEach { d ->
            run = if (prev != null && prev!!.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        val set = read.toSet()
        var day = if (today in set) today else today.minusDays(1)
        var current = 0
        while (day in set) {
            current++
            day = day.minusDays(1)
        }
        return current to best
    }

    fun duration(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            ms <= 0 -> "0 мин"
            minutes < 1 -> "< 1 мин"
            minutes < 60 -> "$minutes мин"
            minutes % 60 == 0L -> "${minutes / 60} ч"
            else -> "${minutes / 60} ч ${minutes % 60} мин"
        }
    }

    fun days(n: Int): String = "$n " + plural(n, "день", "дня", "дней")

    fun plural(n: Int, one: String, few: String, many: String): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11..14 -> many
            m10 == 1 -> one
            m10 in 2..4 -> few
            else -> many
        }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru"))
    fun date(d: LocalDate): String = dateFormat.format(d)
}

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val days by app.stats.days.collectAsStateWithLifecycle()
    val books by app.library.books.collectAsStateWithLifecycle()
    val perDay = remember(days) { StatsCalc.perDay(days) }
    val today = LocalDate.now()

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (perDay.values.none { it.ms > 0 }) {
            Text(
                "Почитай немного — статистика появится сама.",
                color = AppColors.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }

        val prefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
        GoalCard(prefs.dailyGoalMinutes, perDay) { minutes -> app.libraryPrefs.update { it.copy(dailyGoalMinutes = minutes) } }

        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Сегодня", StatsCalc.duration(perDay[today]?.ms ?: 0), Modifier.weight(1f), icon = AppIcons.Clock)
            StatTile("За неделю", StatsCalc.duration(StatsCalc.sumSince(perDay, today.minusDays(6)).ms), Modifier.weight(1f), icon = AppIcons.Calendar)
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("За месяц", StatsCalc.duration(StatsCalc.sumSince(perDay, today.minusDays(29)).ms), Modifier.weight(1f), icon = AppIcons.Calendar)
            StatTile("За год", StatsCalc.duration(StatsCalc.sumSince(perDay, today.withDayOfYear(1)).ms), Modifier.weight(1f), icon = AppIcons.Calendar)
        }

        val (current, best) = StatsCalc.streaks(perDay, today)
        val yearStart = today.withDayOfYear(1).toEpochDay() * 86_400_000L
        val finishedThisYear = books.count { it.status == ReadStatus.Finished && (it.finishedAt ?: 0) >= yearStart }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Серия", StatsCalc.days(current), Modifier.weight(1f), hint = "Лучшая: ${StatsCalc.days(best)}", icon = AppIcons.Fire)
            StatTile(
                "Прочитано",
                "$finishedThisYear " + StatsCalc.plural(finishedThisYear, "книга", "книги", "книг"),
                Modifier.weight(1f),
                hint = "В ${today.year} году",
                icon = AppIcons.Book,
            )
        }

        val total = perDay.values.fold(DayEntry()) { a, b -> a + b }
        val readingDays = perDay.values.count { it.ms >= 60_000 }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Всего", StatsCalc.duration(total.ms), Modifier.weight(1f), hint = "${total.pages} стр.", icon = AppIcons.Pages)
            StatTile(
                "В среднем",
                StatsCalc.duration(if (readingDays > 0) total.ms / readingDays else 0),
                Modifier.weight(1f),
                hint = "в день чтения",
                icon = AppIcons.Chart,
            )
        }

        ChartCard("Последние 30 дней", StatsCalc.series(perDay, 30, today))

        val top = remember(days, books) {
            books.map { it to StatsCalc.book(days, it.statsKey) }.filter { it.second.ms >= 60_000 }.sortedByDescending { it.second.ms }.take(5)
        }
        if (top.isNotEmpty()) {
            SettingsGroup("Больше всего времени") {
                top.forEach { (book, stats) -> TopBookRow(app, book, stats) }
            }
        }
    }
}

@Composable
private fun TopBookRow(app: App, book: Book, stats: BookStats) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallCover(app.library.coverFile(book), Modifier.size(width = 36.dp, height = 54.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, color = AppColors.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${StatsCalc.duration(stats.ms)} · ${(book.progress * 100).toInt()}%",
                color = AppColors.Muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null, icon: ImageVector? = null) {
    Column(
        modifier.fillMaxHeight().heightIn(min = 76.dp).fieldSurface(appCorner(16)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(15.dp))
            Text(label, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, color = AppColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (hint != null) Text(hint, color = AppColors.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ChartCard(title: String, values: List<Long>) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1L
    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("макс. ${StatsCalc.duration(values.maxOrNull() ?: 0)}", color = AppColors.Faint, fontSize = 11.sp)
        }
        val accent = AppColors.Accent
        val line = AppColors.Line
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = (slot * 0.62f).coerceAtLeast(2f)
            val radius = CornerRadius(barWidth / 2, barWidth / 2)
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - barWidth) / 2
                if (v <= 0) {
                    drawRoundRect(line, Offset(x, size.height - 3f), Size(barWidth, 3f), radius)
                } else {
                    val h = (v.toFloat() / max * size.height).coerceAtLeast(barWidth)
                    val color = if (i == values.lastIndex) accent else accent.copy(alpha = 0.5f)
                    drawRoundRect(color, Offset(x, size.height - h), Size(barWidth, h), radius)
                }
            }
        }
        Row {
            Text("${values.size} дн. назад", color = AppColors.Faint, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("сегодня", color = AppColors.Faint, fontSize = 11.sp)
        }
    }
}

@Composable
fun SmallCover(file: File?, modifier: Modifier, maxWidth: Int = 160) {
    val cover by produceState<ImageBitmap?>(null, file) {
        value = file?.let { withContext(Dispatchers.IO) { CoverCache.load(it, maxWidth) } }
    }
    val shape = appCorner(6)
    Box(modifier.clip(shape).background(AppColors.Field)) {
        cover?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}

val GoalChoices = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90)

@Composable
fun GoalRing(todayMs: Long, goalMinutes: Int, size: androidx.compose.ui.unit.Dp, showText: Boolean = true) {
    val fraction = if (goalMinutes > 0) (todayMs / 60_000f / goalMinutes).coerceIn(0f, 1f) else 0f
    val accent = AppColors.Accent
    val track = AppColors.Line
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = this.size.minDimension * 0.11f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (fraction > 0f) {
                drawArc(accent, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        if (showText) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val big = with(density) { (size * 0.27f).toSp() }
            val small = with(density) { (size * 0.13f).toSp() }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${todayMs / 60_000}", color = AppColors.Text, fontSize = big, lineHeight = big, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (size >= 64.dp) Text("мин", color = AppColors.Faint, fontSize = small, lineHeight = small, maxLines = 1)
            }
        }
    }
}

@Composable
private fun GoalCard(goalMinutes: Int, perDay: Map<LocalDate, DayEntry>, onGoal: (Int) -> Unit) {
    val today = LocalDate.now()
    val todayMs = perDay[today]?.ms ?: 0L
    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (goalMinutes > 0) {
            val done = todayMs >= goalMinutes * 60_000L
            val (streak, _) = StatsCalc.streaks(perDay, today, minMs = goalMinutes * 60_000L)
            Row(
                Modifier.padding(top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GoalRing(todayMs, goalMinutes, 76.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (done) "Цель на сегодня выполнена" else "Ещё ${goalMinutes - (todayMs / 60_000).toInt()} мин до цели",
                        color = AppColors.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Цель: $goalMinutes мин в день", color = AppColors.Muted, fontSize = 13.sp)
                    if (streak > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(AppIcons.Fire, null, tint = AppColors.Accent, modifier = Modifier.size(14.dp))
                            Text("Выполняешь ${StatsCalc.days(streak)} подряд", color = AppColors.Faint, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        PickerRow(
            label = "Цель на день",
            icon = AppIcons.Target,
            options = GoalChoices.map { m ->
                if (m == 0) PickerOption("0", "Без цели", "Просто считать время") else PickerOption(m.toString(), "$m мин", null)
            },
            selectedId = goalMinutes.toString(),
        ) { id -> onGoal(id.toInt()) }
    }
}
