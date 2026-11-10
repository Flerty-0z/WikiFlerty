package com.flerty.wikiflerty.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.ReadStatus
import com.flerty.wikiflerty.reader.RoundIconButton
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YearData(
    val year: Int,
    val ms: Long,
    val pages: Int,
    val readingDays: Int,
    val daysInYear: Int,
    val finished: List<Book>,
    val months: List<Long>,
    val bestStreak: Int,
    val weekday: Int,
    val topBook: Book?,
    val topBookMs: Long,
    val author: String?,
    val authorCount: Int,
    val quote: String?,
    val quoteBook: String?,
    val words: Int,
    val notes: Int,
) {
    val hours: Int get() = (ms / 3_600_000).toInt()
    val minutes: Int get() = ((ms / 60_000) % 60).toInt()
    val bestMonth: Int get() = months.indices.maxByOrNull { months[it] } ?: 0
    val empty: Boolean get() = ms < 10 * 60_000L && finished.isEmpty()

    companion object {
        fun compute(app: App, year: Int): YearData {
            val zone = ZoneId.systemDefault()
            val days = app.stats.days.value
            val books = app.library.books.value
            val perDay = StatsCalc.perDay(days).filterKeys { it.year == year }
            val ms = perDay.values.sumOf { it.ms }
            val pages = perDay.values.sumOf { it.pages }
            val months = MutableList(12) { 0L }
            val weekdays = MutableList(7) { 0L }
            perDay.forEach { (d, e) ->
                months[d.monthValue - 1] += e.ms
                weekdays[d.dayOfWeek.value - 1] += e.ms
            }
            val read = perDay.filterValues { it.ms >= 60_000 }.keys.sorted()
            var best = 0
            var run = 0
            var prev: LocalDate? = null
            read.forEach { d ->
                run = if (prev != null && prev!!.plusDays(1) == d) run + 1 else 1
                best = maxOf(best, run)
                prev = d
            }
            val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
            val finished = books.filter { it.status == ReadStatus.Finished && (it.finishedAt ?: 0L) in start until end }.sortedBy { it.finishedAt }
            val perBook = HashMap<String, Long>()
            days.forEach { (day, entries) ->
                val d = runCatching { LocalDate.parse(day) }.getOrNull() ?: return@forEach
                if (d.year != year) return@forEach
                entries.forEach { (key, e) -> perBook[key] = (perBook[key] ?: 0L) + e.ms }
            }
            val top = perBook.maxByOrNull { it.value }
            val topBook = top?.let { t -> books.firstOrNull { it.statsKey == t.key } }
            val authorPick = finished.filter { it.author.isNotBlank() }.groupBy { it.author.trim() }.maxByOrNull { it.value.size }
                ?: books.filter { it.author.isNotBlank() && (perBook[it.statsKey] ?: 0L) > 0 }.groupBy { it.author.trim() }
                    .maxByOrNull { g -> g.value.sumOf { perBook[it.statsKey] ?: 0L } }
            val notes = app.notes.notes.value.filter { it.createdAt in start until end }
            val quoteNote = notes.filter { it.text.trim().length in 40..260 }.let { list ->
                if (list.isEmpty()) null else list[(year * 31 + list.size) % list.size]
            }
            val words = app.dictionary.entries.value.count { it.createdAt in start until end }
            return YearData(
                year = year,
                ms = ms,
                pages = pages,
                readingDays = read.size,
                daysInYear = LocalDate.now().let { if (it.year == year) it.dayOfYear else LocalDate.of(year, 12, 31).dayOfYear },
                finished = finished,
                months = months,
                bestStreak = best,
                weekday = weekdays.indices.maxByOrNull { weekdays[it] } ?: 5,
                topBook = topBook,
                topBookMs = top?.value ?: 0L,
                author = authorPick?.key,
                authorCount = authorPick?.value?.size ?: 0,
                quote = quoteNote?.text?.trim()?.replace(Regex("\\s+"), " "),
                quoteBook = quoteNote?.let { n -> books.firstOrNull { it.id == n.bookId }?.title },
                words = words,
                notes = notes.size,
            )
        }
    }
}

private val MonthNames = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
private val MonthLetters = listOf("Я", "Ф", "М", "А", "М", "И", "И", "А", "С", "О", "Н", "Д")
private val Weekdays = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")

@Composable
fun YearBanner(onOpen: () -> Unit) {
    val year = LocalDate.now().year
    val shape = appCorner(20)
    val accent = AppColors.Accent
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onOpen)
            .clip(shape)
            .kitFrame(),
    ) {
        KitBackdrop(AppStyle.decor, AppStyle.base, accent, Modifier.matchParentSize(), animated = false)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(AppColors.Panel.copy(alpha = 0.92f), AppColors.Panel.copy(alpha = 0.35f)))))
        Row(Modifier.matchParentSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Итоги $year", color = AppColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Твой год в книгах", color = AppColors.Muted, fontSize = 13.sp, maxLines = 1)
            }
            Box(Modifier.size(46.dp).clip(androidx.compose.foundation.shape.CircleShape).accentHighlight(androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Play, "Смотреть", tint = accentContent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun YearReviewDialog(onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val year = LocalDate.now().year
    val data by produceState<YearData?>(null) { value = withContext(Dispatchers.Default) { YearData.compute(app, year) } }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        KeepHostBars()
        val d = data
        Box(Modifier.fillMaxSize().background(AppColors.Background)) {
            KitBackdrop(AppStyle.decor, AppStyle.base, AppColors.Accent, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppColors.Background.copy(alpha = 0.25f), AppColors.Background.copy(alpha = 0.7f), AppColors.Background.copy(alpha = 0.9f)))))
            if (d != null) YearStories(d, onDismiss)
        }
    }
}

@Composable
private fun YearStories(data: YearData, onClose: () -> Unit) {
    val pages = remember(data) {
        buildList {
            add(0)
            if (!data.empty) {
                add(1)
                if (data.finished.isNotEmpty()) add(2)
                add(3)
                add(4)
                if (data.topBook != null) add(5)
                if (data.quote != null || data.words > 0) add(6)
            }
            add(7)
        }
    }
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val last = index == pages.lastIndex
    LaunchedEffect(index, paused) {
        if (paused) return@LaunchedEffect
        if (last) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.animateTo(1f, tween(((1f - progress.value) * 6500).toInt().coerceAtLeast(1), easing = LinearEasing))
        progress.snapTo(0f)
        index = (index + 1).coerceAtMost(pages.lastIndex)
    }
    val scope = rememberCoroutineScope()
    fun go(delta: Int) {
        val next = (index + delta).coerceIn(0, pages.lastIndex)
        if (next == index && delta < 0) {
            scope.launch { progress.snapTo(0f) }
            return
        }
        scope.launch { progress.snapTo(0f) }
        index = next
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(pages) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = System.currentTimeMillis()
                    paused = true
                    val up = waitForUpOrCancellation()
                    paused = false
                    if (up != null && System.currentTimeMillis() - start < 300) {
                        if (down.position.x < size.width * 0.33f) go(-1) else if (!last) go(1)
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pages.indices.forEach { i ->
                        val fill = when {
                            i < index -> 1f
                            i == index -> progress.value
                            else -> 0f
                        }
                        Box(Modifier.weight(1f).height(3.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(AppColors.Text.copy(alpha = 0.22f))) {
                            Box(Modifier.fillMaxWidth(fill).height(3.dp).background(AppColors.Accent))
                        }
                    }
                }
                RoundIconButton(AppIcons.Close, "Закрыть", sound = FeedbackSound.Close, onClick = onClose)
            }
            AnimatedContent(
                targetState = pages[index],
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    (fadeIn(Motion.fade(320)) + scaleIn(Motion.tween(420), initialScale = 0.94f)) togetherWith (fadeOut(Motion.fade(200)) + scaleOut(Motion.tween(260), targetScale = 1.04f))
                },
                label = "year-page",
            ) { page ->
                Box(Modifier.fillMaxSize().padding(horizontal = 26.dp), contentAlignment = Alignment.Center) {
                    when (page) {
                        0 -> IntroPage(data)
                        1 -> TimePage(data)
                        2 -> BooksPage(data)
                        3 -> MonthsPage(data)
                        4 -> StreakPage(data)
                        5 -> TopBookPage(data)
                        6 -> QuotePage(data)
                        else -> SummaryPage(data)
                    }
                }
            }
        }
    }
}

@Composable
private fun Rise(delayMs: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(Motion.ms(delayMs).toLong())
        a.animateTo(1f, tween(Motion.ms(620).coerceAtLeast(1), easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 40.dp.toPx()
    }) { content() }
}

@Composable
private fun Label(text: String) = Text(text.uppercase(), color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, textAlign = TextAlign.Center)

@Composable
private fun Big(value: Int, suffix: String = "") {
    val shown = animatedCount(value)
    Text("$shown$suffix", color = AppColors.Text, fontSize = 76.sp, lineHeight = 80.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1)
}

@Composable
private fun Caption(text: String, size: Int = 17) = Text(text, color = AppColors.Muted, fontSize = size.sp, lineHeight = (size * 1.35f).sp, textAlign = TextAlign.Center)

@Composable
private fun IntroPage(data: YearData) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Rise(0) { Caption("Твой", 22) }
        Rise(200) {
            val y = animatedCount(data.year)
            Text("$y", color = AppColors.Accent, fontSize = 104.sp, lineHeight = 108.sp, fontWeight = FontWeight.Black)
        }
        Rise(450) { Caption(if (data.empty) "Год только начинается. Почитай ещё — и здесь появится твоя история." else "год в книгах", 20) }
    }
}

@Composable
private fun TimePage(data: YearData) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Rise(0) { Label("Ты читал") }
        Rise(150) { Big(data.hours, " ч") }
        Rise(300) { Caption("и ещё ${data.minutes} мин · ${data.pages} стр.") }
        Rise(700) {
            val films = (data.ms / (2 * 3_600_000L)).toInt()
            Caption(
                when {
                    data.hours >= 48 -> "Это ${data.hours / 24} ${StatsCalc.plural(data.hours / 24, "сутки", "суток", "суток")} без перерыва"
                    films >= 2 -> "Это как $films ${StatsCalc.plural(films, "фильм", "фильма", "фильмов")} подряд"
                    else -> "Каждая минута — в копилку"
                },
                15,
            )
        }
        Rise(950) { Caption("Читал ${StatsCalc.days(data.readingDays)} из ${data.daysInYear}", 15) }
    }
}

@Composable
private fun BooksPage(data: YearData) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Rise(0) { Label("Прочитано") }
        Rise(150) { Big(data.finished.size) }
        Rise(250) { Caption(StatsCalc.plural(data.finished.size, "книга", "книги", "книг"), 18) }
        val shown = data.finished.takeLast(5)
        Box(Modifier.height(170.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            shown.forEachIndexed { i, b ->
                val center = (shown.size - 1) / 2f
                val off = (i - center)
                Rise(500 + i * 140) {
                    Box(Modifier.offset(x = (off * 46).dp).graphicsLayer { rotationZ = off * 7f; translationY = (kotlin.math.abs(off) * 8).dp.toPx() }) {
                        BookCover(b.id, 92.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthsPage(data: YearData) {
    val best = data.bestMonth
    val max = data.months.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(Motion.ms(300).toLong())
        grow.animateTo(1f, tween(Motion.ms(1300).coerceAtLeast(1), easing = FastOutSlowInEasing))
    }
    val accent = AppColors.Accent
    val line = AppColors.Text.copy(alpha = 0.18f)
    val faint = AppColors.Faint
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Rise(0) { Label("Самый книжный месяц") }
        Rise(150) { Text(MonthNames[best], color = AppColors.Text, fontSize = 46.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center) }
        Rise(250) { Caption(StatsCalc.duration(data.months[best])) }
        Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
            val n = 12
            val gap = 6.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            val label = 18.dp.toPx()
            val area = size.height - label
            for (i in 0 until n) {
                val stagger = ((grow.value * 1.4f) - i * 0.033f).coerceIn(0f, 1f)
                val hgt = (data.months[i].toFloat() / max) * area * stagger
                val x = i * (bw + gap)
                drawRoundRect(line, Offset(x, 0f), Size(bw, area), CornerRadius(bw / 3))
                if (hgt > 0f) drawRoundRect(if (i == best) accent else accent.copy(alpha = 0.45f), Offset(x, area - hgt), Size(bw, hgt), CornerRadius(bw / 3))
            }
        }
        Row(Modifier.fillMaxWidth().offset(y = (-14).dp)) {
            MonthLetters.forEachIndexed { i, l ->
                Text(l, color = if (i == best) accent else faint, fontSize = 11.sp, fontWeight = if (i == best) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StreakPage(data: YearData) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Rise(0) { Icon(AppIcons.Fire, null, tint = AppColors.Accent, modifier = Modifier.size(54.dp)) }
        Rise(120) { Label("Лучшая серия") }
        Rise(250) { Big(data.bestStreak) }
        Rise(350) { Caption(StatsCalc.plural(data.bestStreak, "день подряд", "дня подряд", "дней подряд"), 18) }
        Spacer(Modifier.height(18.dp))
        Rise(750) { Label("Любимый день") }
        Rise(900) { Text(Weekdays[data.weekday], color = AppColors.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun TopBookPage(data: YearData) {
    val book = data.topBook ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Rise(0) { Label("Книга года") }
        Rise(200) { BookCover(book.id, 140.dp) }
        Rise(450) { Text(book.title, color = AppColors.Text, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        Rise(600) { Caption("${StatsCalc.duration(data.topBookMs)} вместе", 15) }
        if (data.author != null) {
            Spacer(Modifier.height(6.dp))
            Rise(850) { Label("Любимый автор") }
            Rise(1000) { Text(data.author, color = AppColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2) }
        }
    }
}

@Composable
private fun QuotePage(data: YearData) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (data.quote != null) {
            Rise(0) { Label("Цитата года") }
            Rise(200) {
                Column(
                    Modifier.widthIn(max = 420.dp).panelSurface(appCorner(20), elevation = 10.dp).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("“", color = AppColors.Accent, fontSize = 48.sp, lineHeight = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    Text(data.quote, color = AppColors.Text, fontSize = 18.sp, lineHeight = 26.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
                    if (data.quoteBook != null) Text("— ${data.quoteBook}", color = AppColors.Muted, fontSize = 13.sp, maxLines = 2)
                }
            }
        }
        if (data.words > 0 || data.notes > 0) {
            Rise(700) {
                Caption(
                    listOfNotNull(
                        data.notes.takeIf { it > 0 }?.let { "$it ${StatsCalc.plural(it, "заметка", "заметки", "заметок")}" },
                        data.words.takeIf { it > 0 }?.let { "$it ${StatsCalc.plural(it, "слово", "слова", "слов")} в словаре" },
                    ).joinToString(" · "),
                    15,
                )
            }
        }
    }
}

@Composable
private fun SummaryPage(data: YearData) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Rise(0) {
            Box(
                Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) { SummaryCard(data) }
        }
        Rise(350) {
            AppButton("Поделиться", primary = true, modifier = Modifier.width(220.dp)) {
                scope.launch {
                    runCatching {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        shareYear(context, bitmap, data.year)
                    }.onFailure { Toast.makeText(context, "Не удалось поделиться", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(data: YearData) {
    val shape = appCorner(24)
    Box(Modifier.fillMaxWidth().clip(shape).kitFrame()) {
        KitBackdrop(AppStyle.decor, AppStyle.base, AppColors.Accent, Modifier.matchParentSize(), animated = false)
        Box(Modifier.matchParentSize().background(AppColors.Panel.copy(alpha = 0.72f)))
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${data.year}", color = AppColors.Accent, fontSize = 44.sp, lineHeight = 46.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(10.dp))
                Text("мой год\nв книгах", color = AppColors.Muted, fontSize = 13.sp, lineHeight = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            val cells = listOf(
                Triple(AppIcons.Book, "${data.finished.size}", StatsCalc.plural(data.finished.size, "книга", "книги", "книг")),
                Triple(AppIcons.Clock, "${data.hours} ч", "чтения"),
                Triple(AppIcons.Pages, "${data.pages}", "страниц"),
                Triple(AppIcons.Fire, "${data.bestStreak}", "дней подряд"),
            )
            cells.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (icon, value, label) ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
                            Column {
                                Text(value, color = AppColors.Text, fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(label, color = AppColors.Faint, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            val covers = data.finished.takeLast(6)
            if (covers.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { covers.forEach { BookCover(it.id, 40.dp) } }
            }
            if (data.topBook != null) {
                Text("Книга года: ${data.topBook.title}", color = AppColors.Text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("WikiFlerty", color = AppColors.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

private suspend fun shareYear(context: Context, bitmap: Bitmap, year: Int) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "year-$year.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
    val intent = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newRawUri("year", uri)
    context.startActivity(Intent.createChooser(intent, "Поделиться итогами").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
