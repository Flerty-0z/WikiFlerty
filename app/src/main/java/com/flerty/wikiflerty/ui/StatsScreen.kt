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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
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

    fun streaks(
        perDay: Map<LocalDate, DayEntry>,
        today: LocalDate = LocalDate.now(),
        minMs: Long = STREAK_MS,
        frozen: Set<LocalDate> = com.flerty.wikiflerty.data.StreakFreezeStore.frozen,
    ): Pair<Int, Int> {
        val read = perDay.filterValues { it.ms >= minMs }.keys.toSet()
        val ice = frozen.filter { !it.isAfter(today) }.toSet()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        (read + ice).sorted().forEach { d ->
            val counts = if (d in read) 1 else 0
            run = if (prev != null && prev!!.plusDays(1) == d) run + counts else counts
            best = maxOf(best, run)
            prev = d
        }
        val start = if (today in read || today in ice) today else today.minusDays(1)
        return streakEnding(read, ice, start) to best
    }

    fun streakEnding(read: Set<LocalDate>, ice: Set<LocalDate>, end: LocalDate): Int {
        var day = end
        var current = 0
        while (day in read || day in ice) {
            if (day in read) current++
            day = day.minusDays(1)
        }
        return current
    }

    fun rescuable(perDay: Map<LocalDate, DayEntry>, today: LocalDate = LocalDate.now()): Int {
        val read = perDay.filterValues { it.ms >= STREAK_MS }.keys.toSet()
        val ice = com.flerty.wikiflerty.data.StreakFreezeStore.frozen
        val yesterday = today.minusDays(1)
        if (yesterday in read || yesterday in ice) return 0
        return streakEnding(read, ice, yesterday.minusDays(1))
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
    var achievements by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(0) }
    val app = LocalContext.current.applicationContext as App
    androidx.compose.animation.AnimatedContent(
        targetState = achievements,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (androidx.compose.animation.fadeIn(Motion.fade(220)) +
                androidx.compose.animation.slideInHorizontally(Motion.tween(280)) { dir * it / 5 })
                .togetherWith(
                    androidx.compose.animation.fadeOut(Motion.fade(160)) +
                        androidx.compose.animation.slideOutHorizontally(Motion.tween(240)) { -dir * it / 6 },
                )
        },
        label = "statsPage",
    ) { page ->
        when (page) {
            1 -> AchievementsPage(modifier) { achievements = 0 }
            else -> StatsMain(modifier) { achievements = 1 }
        }
    }
}

@Composable
private fun StatsMain(modifier: Modifier, onAchievements: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val days by app.stats.days.collectAsStateWithLifecycle()
    val books by app.library.books.collectAsStateWithLifecycle()
    val prefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    val freezes by app.streakFreeze.freezes.collectAsStateWithLifecycle()
    val unlocked by app.achievements.unlocked.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val perDay by produceState(StatsCalc.perDay(days), days) {
        value = withContext(Dispatchers.Default) { StatsCalc.perDay(days) }
    }
    val top by produceState(emptyList<Pair<Book, BookStats>>(), days, books) {
        value = withContext(Dispatchers.Default) {
            books.map { it to StatsCalc.book(days, it.statsKey) }.filter { it.second.ms >= 60_000 }.sortedByDescending { it.second.ms }.take(5)
        }
    }
    val yearStart = today.withDayOfYear(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val finishedThisYear = books.count { it.status == ReadStatus.Finished && (it.finishedAt ?: 0) >= yearStart }
    val frozen = remember(freezes) { com.flerty.wikiflerty.data.StreakFreezeStore.days(freezes) }
    val best = remember(perDay, frozen) { StatsCalc.streaks(perDay, today, frozen = frozen).second }
    val total = remember(perDay) { perDay.values.fold(DayEntry()) { a, b -> a + b } }
    val readingDays = remember(perDay) { perDay.values.count { it.ms >= 60_000 } }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (perDay.values.none { it.ms > 0 }) {
            revealItem(key = "empty") {
                Text(
                    "Почитай немного — статистика появится сама.",
                    color = AppColors.Muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
        revealItem(key = "goals") {
            Column(Modifier.fillMaxWidth().fieldSurface(appCorner(18))) {
                GoalCard(prefs.dailyGoalMinutes, perDay, framed = false) { minutes -> app.libraryPrefs.update { it.copy(dailyGoalMinutes = minutes) } }
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(AppColors.PanelBorder))
                YearGoalCard(prefs.yearGoalBooks, finishedThisYear, framed = false) { n -> app.libraryPrefs.update { it.copy(yearGoalBooks = n) } }
            }
        }
        revealItem(key = "streak") { StreakCard(perDay, today) }
        revealItem(key = "minutes") { MinutesCard(perDay, today) }
        revealItem(key = "heatmap") { YearHeatmapCard(days, perDay, books) }
        revealItem(key = "status") { StatusCard(books) }
        if (books.any { (it.rating ?: 0f) > 0f }) revealItem(key = "ratings") { RatingsCard(books, yearStart) }
        revealItem(key = "tiles") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Сегодня", StatsCalc.duration(perDay[today]?.ms ?: 0), Modifier.weight(1f), icon = AppIcons.Clock)
                    StatTile("Дней чтения", "$readingDays", Modifier.weight(1f), hint = "Рекорд: ${StatsCalc.days(best)}", icon = AppIcons.Fire)
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
            }
        }
        revealItem(key = "achievements") {
            AchievementsButton(Achievements.all.count { it.id in unlocked }, Achievements.all.size, onAchievements)
        }
        if (top.isNotEmpty()) {
            revealItem(key = "top") {
                SettingsGroup("Больше всего времени") {
                    top.forEach { (book, stats) -> TopBookRow(app, book, stats) }
                }
            }
        }
    }
}

@Composable
private fun RatingsCard(books: List<Book>, yearStart: Long) {
    val rated = books.filter { (it.rating ?: 0f) > 0f }
    if (rated.isEmpty()) return
    val avg = rated.map { it.rating!! }.average().toFloat()
    val best = rated.filter { (it.finishedAt ?: it.ratedAt) >= yearStart }.maxWithOrNull(compareBy<Book> { it.rating }.thenBy { it.ratedAt })
    SettingsGroup("Оценки") {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Средняя", "★ ${ratingText(Math.round(avg * 10) / 10f)}", Modifier.weight(1f), icon = AppIcons.StarFilled)
                StatTile("Оценено", "${rated.size}", Modifier.weight(1f), icon = AppIcons.Check)
            }
            if (best != null) {
                Text("Лучшая книга года", color = AppColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                BookTag(best.id, best.title) { RatingBadge(best.rating ?: 0f, Modifier.padding(end = 4.dp)) }
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
            FitText(label, AppColors.Muted, 12.sp, weight = FontWeight.SemiBold)
        }
        Text(
            value,
            color = AppColors.Text,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 20.sp, stepSize = 0.5.sp),
        )
        if (hint != null) Text(hint, color = AppColors.Faint, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

val GoalChoices = listOf(0, 10, 20, 30, 45, 60)

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
private fun GoalCard(goalMinutes: Int, perDay: Map<LocalDate, DayEntry>, framed: Boolean = true, onGoal: (Int) -> Unit) {
    val today = LocalDate.now()
    val todayMs = perDay[today]?.ms ?: 0L
    var lastGoal by remember { mutableStateOf(goalMinutes) }
    if (goalMinutes > 0) lastGoal = goalMinutes
    Column(
        Modifier.fillMaxWidth().then(if (framed) Modifier.fieldSurface(appCorner(18)) else Modifier).animateContentSize(Motion.tween(380)).padding(horizontal = 14.dp, vertical = 10.dp),
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
        var custom by remember { mutableStateOf(false) }
        ValueRow("Цель на день", if (goalMinutes > 0) "$goalMinutes мин" else "Без цели", AppIcons.Target) { custom = true }
        if (custom) {
            NumberInputSheet(
                "Цель на день",
                "мин",
                goalMinutes.takeIf { it > 0 } ?: 25,
                1..1440,
                onDismiss = { custom = false },
                onClear = if (goalMinutes > 0) ({ onGoal(0) }) else null,
            ) { onGoal(it) }
        }
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
    TabbedFrame("Минуты по дням", icon = AppIcons.Chart) {
        Box(Modifier.padding(top = 8.dp, bottom = 10.dp)) { MinutesBars(StatsCalc.series(perDay, 7, today), today) }
    }
}


@Composable
private fun MinutesBars(values: List<Long>, today: LocalDate) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1L
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(184.dp)) {
        val track = maxHeight - 56.dp
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEachIndexed { i, v ->
                val last = i == values.lastIndex
                val day = today.minusDays((values.size - 1 - i).toLong())
                val target = if (v > 0) (v.toFloat() / max).coerceIn(0.08f, 1f) else 0f
                val grow = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(target) {
                    grow.animateTo(target, Motion.tween(700, 55 * i + 80, GrowEasing))
                }
                val g = grow.value.coerceIn(0f, 1.1f)
                val shape = appCorner(12)
                val edge = if (last) AppColors.Accent.copy(alpha = 0.7f) else AppColors.PanelBorder
                androidx.compose.foundation.layout.Column(
                    Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(AppColors.Field)
                        .border(1.dp, edge, shape),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (v > 0) {
                        Box(
                            Modifier.fillMaxWidth().height(26.dp).graphicsLayer { alpha = (g / target.coerceAtLeast(0.01f)).coerceIn(0f, 1f) },
                            contentAlignment = Alignment.Center,
                        ) {
                            FitText("${v / 60_000}", if (last) AppColors.Accent else AppColors.Text, 12.sp, Modifier.padding(horizontal = 3.dp), weight = FontWeight.ExtraBold)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(track * g.coerceIn(0f, 1f))
                                .background(if (last) AppColors.Accent else AppColors.Accent.copy(alpha = 0.45f)),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .then(if (v > 0) Modifier else Modifier.background(AppColors.Line.copy(alpha = 0.35f))),
                        contentAlignment = Alignment.Center,
                    ) {
                        FitText(
                            DayLetters[day.dayOfWeek.value - 1],
                            if (last) AppColors.Accent else AppColors.Muted,
                            11.sp,
                            Modifier.padding(horizontal = 2.dp),
                            weight = if (last) FontWeight.ExtraBold else FontWeight.SemiBold,
                        )
                    }
                }
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
