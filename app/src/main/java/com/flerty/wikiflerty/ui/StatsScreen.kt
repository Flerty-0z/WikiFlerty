package com.flerty.wikiflerty.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.runtime.setValue

import androidx.compose.animation.togetherWith

import com.flerty.wikiflerty.data.Achievements

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
    var achievements by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.animation.AnimatedContent(
        targetState = achievements,
        transitionSpec = {
            val dir = if (targetState) 1 else -1
            (androidx.compose.animation.fadeIn(Motion.fade(220)) +
                androidx.compose.animation.slideInHorizontally(Motion.tween(280)) { dir * it / 5 })
                .togetherWith(
                    androidx.compose.animation.fadeOut(Motion.fade(160)) +
                        androidx.compose.animation.slideOutHorizontally(Motion.tween(240)) { -dir * it / 6 },
                )
        },
        label = "statsPage",
    ) { page ->
        if (page) {
            AchievementsPage(modifier) { achievements = false }
        } else {
            StatsMain(modifier) { achievements = true }
        }
    }
}

@Composable
private fun StatsMain(modifier: Modifier, onAchievements: () -> Unit) {
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

        val yearStart = today.withDayOfYear(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val finishedThisYear = books.count { it.status == ReadStatus.Finished && (it.finishedAt ?: 0) >= yearStart }
        YearGoalCard(prefs.yearGoalBooks, finishedThisYear) { n -> app.libraryPrefs.update { it.copy(yearGoalBooks = n) } }

        MinutesCard(perDay, today)

        YearHeatmapCard(days, perDay, books)

        StatusCard(books)

        val (current, best) = StatsCalc.streaks(perDay, today)
        val total = perDay.values.fold(DayEntry()) { a, b -> a + b }
        val readingDays = perDay.values.count { it.ms >= 60_000 }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Сегодня", StatsCalc.duration(perDay[today]?.ms ?: 0), Modifier.weight(1f), icon = AppIcons.Clock)
            StatTile("Серия", StatsCalc.days(current), Modifier.weight(1f), hint = "Лучшая: ${StatsCalc.days(best)}", icon = AppIcons.Fire)
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("За неделю", StatsCalc.duration(StatsCalc.sumSince(perDay, today.minusDays(6)).ms), Modifier.weight(1f), icon = AppIcons.Calendar)
            StatTile("За месяц", StatsCalc.duration(StatsCalc.sumSince(perDay, today.minusDays(29)).ms), Modifier.weight(1f), icon = AppIcons.Calendar)
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("За год", StatsCalc.duration(StatsCalc.sumSince(perDay, today.withDayOfYear(1)).ms), Modifier.weight(1f), icon = AppIcons.Calendar)
            StatTile(
                "Прочитано",
                "$finishedThisYear " + StatsCalc.plural(finishedThisYear, "книга", "книги", "книг"),
                Modifier.weight(1f),
                hint = "В ${today.year} году",
                icon = AppIcons.Book,
            )
        }
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

        val unlocked by app.achievements.unlocked.collectAsStateWithLifecycle()
        val progress = remember(days, books) { com.flerty.wikiflerty.data.AchievementProgress.of(days, books) }
        AchievementsButton(Achievements.all.count { it.id in unlocked }, Achievements.all.size, onAchievements)

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
        modifier.fillMaxHeight().heightIn(min = 76.dp).fieldSurface(appCorner(18)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(14.dp))
                }
            }
            Text(label, color = AppColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            value,
            color = AppColors.Text,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 20.sp, stepSize = 0.5.sp),
        )
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
    val target = if (goalMinutes > 0) (todayMs / 60_000f / goalMinutes).coerceIn(0f, 1f) else 0f
    val sweep = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(target) { sweep.animateTo(target, Motion.tween(900, 120, GrowEasing)) }
    val fraction = sweep.value
    val minutes = animatedCount((todayMs / 60_000).toInt())
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
                Text("$minutes", color = AppColors.Text, fontSize = big, lineHeight = big, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (size >= 64.dp) Text("мин", color = AppColors.Faint, fontSize = small, lineHeight = small, maxLines = 1)
            }
        }
    }
}

@Composable
private fun GoalCard(goalMinutes: Int, perDay: Map<LocalDate, DayEntry>, onGoal: (Int) -> Unit) {
    val today = LocalDate.now()
    val todayMs = perDay[today]?.ms ?: 0L
    var lastGoal by remember { mutableStateOf(goalMinutes) }
    if (goalMinutes > 0) lastGoal = goalMinutes
    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(18)).animateContentSize(Motion.tween(380)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        GoalReveal(goalMinutes > 0) {
            val goal = lastGoal
            val done = todayMs >= goal * 60_000L
            val (streak, _) = StatsCalc.streaks(perDay, today, minMs = goal * 60_000L)
            Row(
                Modifier.padding(top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(rememberStagger(0)) { GoalRing(todayMs, goal, 76.dp) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    RollingText(
                        if (done) "Цель на сегодня выполнена" else "Ещё ${goal - (todayMs / 60_000).toInt()} мин до цели",
                        rememberStagger(1),
                    ) { Text(it, color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    RollingText("Цель: $goal мин в день", rememberStagger(2)) {
                        Text(it, color = AppColors.Muted, fontSize = 13.sp, maxLines = 1)
                    }
                    if (streak > 0) {
                        Row(rememberStagger(3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(AppIcons.Fire, null, tint = AppColors.Accent, modifier = Modifier.size(14.dp))
                            Text("Выполняешь ${StatsCalc.days(streak)} подряд", color = AppColors.Faint, fontSize = 12.sp, maxLines = 1)
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

val GrowEasing = androidx.compose.animation.core.CubicBezierEasing(0.3f, 1.25f, 0.55f, 1f)

@Composable
fun GoalReveal(visible: Boolean, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.expandVertically(Motion.tween(420), expandFrom = Alignment.Top) +
            androidx.compose.animation.fadeIn(Motion.fade(320)) +
            androidx.compose.animation.scaleIn(Motion.tween(460, 0, GrowEasing), initialScale = 0.9f),
        exit = androidx.compose.animation.shrinkVertically(Motion.tween(320), shrinkTowards = Alignment.Top) +
            androidx.compose.animation.fadeOut(Motion.fade(200)) +
            androidx.compose.animation.scaleOut(Motion.tween(300), targetScale = 0.94f),
    ) { content() }
}

@Composable
fun rememberStagger(index: Int): Modifier {
    val progress = remember { androidx.compose.animation.core.Animatable(if (Motion.enabled) 0f else 1f) }
    val shift = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(Unit) { progress.animateTo(1f, Motion.tween(460, 90 + index * 80, GrowEasing)) }
    return Modifier.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * shift
    }
}

@Composable
fun RollingText(text: String, modifier: Modifier = Modifier, content: @Composable (String) -> Unit) {
    androidx.compose.animation.AnimatedContent(
        targetState = text,
        transitionSpec = {
            (androidx.compose.animation.slideInVertically(Motion.tween(360, 0, GrowEasing)) { it / 2 } + androidx.compose.animation.fadeIn(Motion.fade(260)))
                .togetherWith(androidx.compose.animation.slideOutVertically(Motion.tween(260)) { -it / 2 } + androidx.compose.animation.fadeOut(Motion.fade(160)))
                .using(androidx.compose.animation.SizeTransform(clip = false))
        },
        label = "rolling",
        modifier = modifier,
    ) { content(it) }
}

@Composable
fun animatedCount(target: Int): Int {
    val value = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(target) { value.animateTo(target.toFloat(), Motion.tween(800, 120)) }
    return value.value.toInt()
}

private val DayLetters = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
private fun MinutesCard(perDay: Map<LocalDate, DayEntry>, today: LocalDate) {
    var month by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(20)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Минуты по дням", color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1)
            PeriodToggle(month) { month = it }
        }
        androidx.compose.animation.AnimatedContent(
            targetState = month,
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                (androidx.compose.animation.fadeIn(Motion.fade(260)) +
                    androidx.compose.animation.slideInHorizontally(Motion.tween(420, 0, GrowEasing)) { dir * it / 6 })
                    .togetherWith(
                        androidx.compose.animation.fadeOut(Motion.fade(160)) +
                            androidx.compose.animation.slideOutHorizontally(Motion.tween(300)) { -dir * it / 6 },
                    )
                    .using(androidx.compose.animation.SizeTransform(clip = false))
            },
            label = "period",
        ) { isMonth ->
            MinutesBars(StatsCalc.series(perDay, if (isMonth) 30 else 7, today), isMonth, today)
        }
    }
}

@Composable
private fun PeriodToggle(month: Boolean, onChange: (Boolean) -> Unit) {
    val bounds = remember { androidx.compose.runtime.mutableStateMapOf<Boolean, Pair<Float, Float>>() }
    val selected = bounds[month]
    val x by androidx.compose.animation.core.animateFloatAsState(selected?.first ?: 0f, Motion.tween(380, 0, GrowEasing), label = "toggleX")
    val w by androidx.compose.animation.core.animateFloatAsState(selected?.second ?: 0f, Motion.tween(380, 0, GrowEasing), label = "toggleW")
    val accent = AppColors.Accent
    Row(
        Modifier
            .clip(CircleShape)
            .background(AppColors.Line.copy(alpha = 0.6f))
            .drawBehind {
                if (w > 0f) {
                    drawRoundRect(
                        accent,
                        topLeft = Offset(x, 3.dp.toPx()),
                        size = Size(w, size.height - 6.dp.toPx()),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                }
            }
            .padding(3.dp),
    ) {
        listOf(false to "Неделя", true to "30 дней").forEach { (m, title) ->
            val color by androidx.compose.animation.animateColorAsState(
                if (m == month) AppColors.OnAccent else AppColors.Muted,
                Motion.fade(260),
                label = "toggleText",
            )
            Text(
                title,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .onGloballyPositioned { c ->
                        bounds[m] = c.positionInParent().x to c.size.width.toFloat()
                    }
                    .clip(CircleShape)
                    .liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.93f) { onChange(m) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun MinutesBars(values: List<Long>, month: Boolean, today: LocalDate) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(130.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(if (month) 3.dp else 10.dp)) {
            values.forEachIndexed { i, v ->
                val last = i == values.lastIndex
                val target = if (v > 0) (v.toFloat() / max).coerceIn(0.06f, 1f) else 0f
                val grow = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(target) {
                    grow.animateTo(target, Motion.tween(if (month) 560 else 700, (if (month) 14 else 55) * i + 80, GrowEasing))
                }
                val g = grow.value.coerceIn(0f, 1.1f)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!month && v > 0) {
                        Text(
                            "${v / 60_000}",
                            color = if (last) AppColors.Accent else AppColors.Muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer { alpha = (g / target.coerceAtLeast(0.01f)).coerceIn(0f, 1f) },
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(if (target > 0f) Modifier.fillMaxHeight((g * 0.82f).coerceIn(0.02f, 0.9f)) else Modifier.height(6.dp))
                            .clip(appCorner(if (month) 3 else 6))
                            .background(
                                if (v <= 0) {
                                    AppColors.Line
                                } else if (last) {
                                    AppColors.Accent
                                } else {
                                    AppColors.Accent.copy(alpha = 0.45f)
                                },
                            ),
                    )
                }
            }
        }
        if (!month) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                values.indices.forEach { i ->
                    val day = today.minusDays((values.size - 1 - i).toLong())
                    val last = i == values.lastIndex
                    Text(
                        DayLetters[day.dayOfWeek.value - 1],
                        color = if (last) AppColors.Accent else AppColors.Faint,
                        fontSize = 11.sp,
                        fontWeight = if (last) FontWeight.ExtraBold else FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Row {
                Text("30 дн. назад", color = AppColors.Faint, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("сегодня", color = AppColors.Faint, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StatusCard(books: List<Book>) {
    if (books.isEmpty()) return
    val parts = listOf(
        Triple("Читаю", books.count { it.status == ReadStatus.Reading }, AppColors.Accent),
        Triple("В планах", books.count { it.status == ReadStatus.Planned }, Color(0xFFF2C14E)),
        Triple("Прочитано", books.count { it.status == ReadStatus.Finished }, AppColors.Good),
        Triple("Новые", books.count { it.status == ReadStatus.New }, AppColors.Line),
    )
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().fieldSurface(appCorner(20)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Canvas(Modifier.size(84.dp)) {
            val stroke = this.size.minDimension * 0.2f
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            var start = -90f
            parts.forEach { (_, n, color) ->
                if (n > 0) {
                    val sweep = 360f * n / total
                    drawArc(color, start, sweep, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    start += sweep
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parts.forEach { (title, n, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                    Text("$title · $n", color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
