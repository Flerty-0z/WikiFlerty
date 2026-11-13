package com.flerty.wikiflerty.ui

import androidx.compose.animation.animateContentSize

import androidx.compose.ui.draw.shadow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Achievement
import com.flerty.wikiflerty.data.AchievementKind
import com.flerty.wikiflerty.data.AchievementProgress
import com.flerty.wikiflerty.data.Achievements
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class MedalStyle(val from: Long, val to: Long, val icon: ImageVector)

private fun medalStyle(kind: AchievementKind): MedalStyle = when (kind) {
    AchievementKind.Streak -> MedalStyle(0xFFF8B26A, 0xFFD9592B, AppIcons.Fire)
    AchievementKind.Books -> MedalStyle(0xFF7C9BFF, 0xFF4C5ED9, AppIcons.Book)
    AchievementKind.Pages -> MedalStyle(0xFF5EEAD4, 0xFF1A9483, AppIcons.Pages)
    AchievementKind.Time -> MedalStyle(0xFFB79CFF, 0xFF7048DD, AppIcons.Clock)
    AchievementKind.Notes -> MedalStyle(0xFFF6D365, 0xFFD9A21B, AppIcons.Note)
    AchievementKind.Words -> MedalStyle(0xFFF7A1C4, 0xFFC44A86, AppIcons.Translate)
}

private fun medalTier(achievement: Achievement): Int {
    val group = Achievements.all.filter { it.kind == achievement.kind }
    val i = group.indexOf(achievement).coerceAtLeast(0)
    return (i * 3 / group.size.coerceAtLeast(1)).coerceIn(0, 2)
}

private val MedalMetals = listOf(
    Triple(Color(0xFFEDB386), Color(0xFF9A5A2E), Color(0xFF6E3C1C)),
    Triple(Color(0xFFF3F6F9), Color(0xFF9AA6B2), Color(0xFF6C7782)),
    Triple(Color(0xFFFFE597), Color(0xFFD9A21C), Color(0xFF9C700C)),
)

private fun medalPath(kind: AchievementKind, w: Float, h: Float): androidx.compose.ui.graphics.Path {
    val cx = w / 2f
    val cy = h / 2f
    val r = minOf(w, h) / 2f
    fun poly(n: Int, start: Double, radius: (Int) -> Float) = androidx.compose.ui.graphics.Path().apply {
        for (k in 0 until n) {
            val ang = Math.toRadians(start + 360.0 * k / n)
            val x = cx + radius(k) * kotlin.math.cos(ang).toFloat()
            val y = cy + radius(k) * kotlin.math.sin(ang).toFloat()
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    return when (kind) {
        AchievementKind.Streak -> poly(6, -90.0) { r * 0.96f }
        AchievementKind.Pages -> poly(10, -90.0) { if (it % 2 == 0) r * 1.0f else r * 0.52f }
        AchievementKind.Words -> poly(8, -67.5) { r * 0.95f }
        AchievementKind.Notes -> poly(4, -90.0) { r * 1.0f }
        AchievementKind.Time -> androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f)) }
        AchievementKind.Books -> androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, h * 0.04f)
            lineTo(w * 0.92f, h * 0.17f)
            cubicTo(w * 0.92f, h * 0.55f, w * 0.78f, h * 0.80f, cx, h * 0.97f)
            cubicTo(w * 0.22f, h * 0.80f, w * 0.08f, h * 0.55f, w * 0.08f, h * 0.17f)
            close()
        }
    }
}

@Composable
fun AchievementMedal(achievement: Achievement, earned: Boolean, fraction: Float, size: Dp, modifier: Modifier = Modifier) {
    val style = medalStyle(achievement.kind)
    val (light, mid, dark) = MedalMetals[medalTier(achievement)]
    val ribbons = size >= 40.dp
    val field = AppColors.Field
    val border = AppColors.PanelBorder
    val accent = Color(style.to)
    Box(modifier.size(width = size, height = if (ribbons) size * 1.14f else size), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.matchParentSize()) {
            val s = this.size.width
            if (ribbons && earned) {
                val top = s * 0.70f
                val bottom = this.size.height
                val left = androidx.compose.ui.graphics.Path().apply {
                    moveTo(s * 0.30f, top); lineTo(s * 0.47f, top); lineTo(s * 0.40f, bottom); lineTo(s * 0.32f, bottom - s * 0.07f); lineTo(s * 0.22f, bottom - s * 0.02f); close()
                }
                val right = androidx.compose.ui.graphics.Path().apply {
                    moveTo(s * 0.70f, top); lineTo(s * 0.53f, top); lineTo(s * 0.60f, bottom); lineTo(s * 0.68f, bottom - s * 0.07f); lineTo(s * 0.78f, bottom - s * 0.02f); close()
                }
                drawPath(left, Color(0xFFC0405A))
                drawPath(right, Color(0xFF8E2C42))
            }
            val path = medalPath(achievement.kind, s, s)
            if (earned) {
                drawPath(path, Brush.linearGradient(listOf(light, mid), Offset.Zero, Offset(s, s)))
                drawPath(path, dark, style = Stroke(width = s * 0.05f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                clipPath(path) {
                    rotate(-28f, Offset(s * 0.34f, s * 0.26f)) {
                        drawOval(Color.White.copy(alpha = 0.38f), Offset(s * 0.16f, s * 0.19f), Size(s * 0.36f, s * 0.13f))
                    }
                }
            } else {
                drawPath(path, field)
                drawPath(path, border, style = Stroke(width = s * 0.035f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                if (fraction > 0f) {
                    clipPath(path) {
                        drawRect(accent.copy(alpha = 0.16f), Offset(0f, s * (1f - fraction)), Size(s, s * fraction))
                    }
                }
            }
        }
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(
                if (earned) style.icon else AppIcons.Lock,
                null,
                tint = if (earned) Color(0xFF3A2410).copy(alpha = 0.85f) else AppColors.Faint.copy(alpha = 0.7f),
                modifier = Modifier.size(size * if (achievement.kind == AchievementKind.Pages) 0.30f else 0.36f),
            )
        }
    }
}

private val ruLocale = Locale("ru")
private val shortDate = DateTimeFormatter.ofPattern("d MMM", ruLocale)
private val longDate = DateTimeFormatter.ofPattern("d MMM yyyy", ruLocale)

private fun earnedDate(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return if (date.year == LocalDate.now().year) shortDate.format(date) else longDate.format(date)
}

private fun number(n: Long): String = String.format(ruLocale, "%,d", n)

private fun progressLabel(achievement: Achievement, progress: AchievementProgress): String {
    val value = progress.value(achievement.kind).coerceAtMost(achievement.target)
    val base = "${number(value)}/${number(achievement.target)}"
    return if (achievement.kind == AchievementKind.Time) "$base ч" else base
}

@Composable
fun AchievementsSection(progress: AchievementProgress, unlocked: Map<String, Long>) {
    val earnedCount = Achievements.all.count { it.id in unlocked }
    TabbedFrame("Достижения", icon = AppIcons.Trophy, inset = false, trailing = { InfoChip("$earnedCount/${Achievements.all.size}", AppColors.Accent) }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Achievements.all.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { achievement ->
                        AchievementBadge(achievement, unlocked[achievement.id], progress, Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AchievementBadge(
    achievement: Achievement,
    earnedAt: Long?,
    progress: AchievementProgress,
    modifier: Modifier,
    withHint: Boolean = false,
) {
    val earned = earnedAt != null
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AchievementMedal(achievement, earned, progress.fraction(achievement), if (withHint) 62.dp else 56.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            achievement.title,
            color = if (earned) AppColors.Text else AppColors.Muted,
            fontWeight = if (earned) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp, stepSize = 0.5.sp),
        )
        Text(
            if (earnedAt != null) earnedDate(earnedAt) else progressLabel(achievement, progress),
            color = AppColors.Faint,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
        )
        if (withHint) {
            Text(
                achievement.hint,
                color = AppColors.Faint.copy(alpha = 0.8f),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 10.sp, stepSize = 0.5.sp),
            )
        }
    }
}

val YearGoalChoices = listOf(0, 5, 8, 12, 16, 20)

@Composable
fun YearGoalCard(goal: Int, inPages: Boolean, finishedBooks: Int, pagesRead: Int, framed: Boolean = true, bookIds: List<String> = emptyList(), onGoal: (Int, Boolean) -> Unit) {
    val finished = if (inPages) pagesRead else finishedBooks
    var lastGoal by remember { mutableStateOf(goal) }
    if (goal > 0) lastGoal = goal
    var custom by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().then(if (framed) Modifier.fieldSurface(appCorner(18)) else Modifier).padding(horizontal = 14.dp, vertical = 12.dp)) {
        GoalHeader(AppIcons.Trophy, if (goal > 0) "Год · $finished из $goal" + (if (inPages) " стр." else "") else "Год · без цели") { custom = true }
        GoalReveal(goal > 0) {
            Box(Modifier.padding(top = 12.dp)) { YearShelf(finished, lastGoal, inPages, bookIds) }
        }
    }
    if (custom) {
        val units = listOf(InputUnit("книг", 1..1000, 10), InputUnit("стр", 1..100000, 3000))
        NumberInputSheet(
            "Цель на год",
            "книг",
            goal.takeIf { it > 0 } ?: units[if (inPages) 1 else 0].default,
            1..1000,
            onDismiss = { custom = false },
            onClear = if (goal > 0) ({ onGoal(0, inPages) }) else null,
            units = units,
            unit = if (inPages) 1 else 0,
            onDoneUnit = { v, u -> onGoal(v, u == 1) },
        ) { }
    }
}

@Composable
private fun YearRing(fraction: Float, finished: Int, goal: Int, size: Dp) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction, Motion.tween(900, 120, GrowEasing)) }
    val shownFinished = animatedCount(finished)
    val shownGoal = animatedCount(goal)
    val accent = AppColors.Accent
    val light = AppColors.AccentLight
    val track = AppColors.Line
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = this.size.minDimension * 0.11f
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            val sweep = 360f * anim.value
            if (sweep > 0f) {
                drawArc(
                    Brush.linearGradient(listOf(light, accent)),
                    -90f,
                    sweep,
                    false,
                    Offset(inset, inset),
                    arc,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FitText("$shownFinished", AppColors.Text, 21.sp, Modifier.padding(horizontal = 9.dp), weight = FontWeight.SemiBold)
            Text("/$shownGoal", color = AppColors.Faint, fontSize = 10.sp, lineHeight = 10.sp, maxLines = 1)
        }
    }
}

@Composable
fun AchievementToast(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val queue by app.achievements.queue.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val head = queue.firstOrNull()
    val count = if (queue.size >= 3) queue.size else 1
    var shown by remember { mutableStateOf<Pair<Achievement, Int>?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(head?.id, count) {
        if (head == null) return@LaunchedEffect
        shown = head to count
        delay(250)
        visible = true
        feedback?.play(FeedbackSound.Open, view)
        delay(3600)
        visible = false
        delay(Motion.ms(380).toLong())
        app.achievements.dismiss(head.id, count)
    }

    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.fade(220)) + slideInVertically(Motion.spring()) { -it } + scaleIn(Motion.spring(), initialScale = 0.85f),
            exit = fadeOut(Motion.fade(200)) + slideOutVertically(Motion.tween(260)) { -it / 2 },
        ) {
            val item = shown
            if (item != null) {
                ToastCard(item.first, item.second) {
                    val current = item.first
                    scope.launch {
                        visible = false
                        delay(Motion.ms(300).toLong())
                        app.achievements.dismiss(current.id, item.second)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToastCard(achievement: Achievement, count: Int, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "medalGlow")
    val glow by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(androidx.compose.animation.core.tween(900), RepeatMode.Reverse),
        label = "glow",
    )
    val style = medalStyle(achievement.kind)
    val shape = appCorner(24)
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .widthIn(max = 420.dp)
            .panelSurface(shape, elevation = 18.dp)
            .liveClick(shape, sound = FeedbackSound.Close, pressedScale = 0.96f, onClick = onClick)
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1f + glow * 0.2f
                        scaleY = 1f + glow * 0.2f
                        alpha = glow
                    }
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(style.from), Color(style.from).copy(alpha = 0f)))),
            )
            AchievementMedal(achievement, earned = true, fraction = 1f, size = 42.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                if (count > 1) "Новые достижения" else "Новое достижение",
                color = AppColors.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                if (count > 1) "Получено значков: $count" else achievement.title,
                color = AppColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (count == 1) {
                Text(achievement.hint, color = AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun AchievementsButton(earned: Int, total: Int, unlocked: Map<String, Long>, onClick: () -> Unit) {
    val shape = appCorner(20)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onClick = onClick)
            .panelSurface(shape, 8.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val recent = Achievements.all.filter { it.id in unlocked }.sortedByDescending { unlocked[it.id] ?: 0L }.take(3)
        val shown = recent.ifEmpty { listOf(AchievementKind.Streak, AchievementKind.Books, AchievementKind.Pages).mapNotNull { k -> Achievements.all.firstOrNull { it.kind == k } } }
        Row(horizontalArrangement = Arrangement.spacedBy((-14).dp), verticalAlignment = Alignment.CenterVertically) {
            shown.forEachIndexed { i, a ->
                AchievementMedal(a, earned = a.id in unlocked, fraction = 0f, size = 32.dp, modifier = Modifier.zIndex((shown.size - i).toFloat()).scrollReveal(i * 70))
            }
        }
        Text(
            "Достижения",
            color = AppColors.Text,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 17.sp, stepSize = 0.5.sp),
            modifier = Modifier.weight(1f),
        )
        Text("$earned / $total", color = AppColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
    }
}

private fun kindTitle(kind: AchievementKind) = when (kind) {
    AchievementKind.Streak -> "Серии"
    AchievementKind.Books -> "Книги"
    AchievementKind.Pages -> "Страницы"
    AchievementKind.Time -> "Время"
    AchievementKind.Notes -> "Заметки"
    AchievementKind.Words -> "Слова"
}

@Composable
fun AchievementsPage(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val days by app.stats.days.collectAsStateWithLifecycle()
    val books by app.library.books.collectAsStateWithLifecycle()
    val unlocked by app.achievements.unlocked.collectAsStateWithLifecycle()
    val noteList by app.notes.notes.collectAsStateWithLifecycle()
    val wordList by app.dictionary.entries.collectAsStateWithLifecycle()
    val progress = remember(days, books, noteList.size, wordList.size) { AchievementProgress.of(days, books, noteList.size, wordList.size) }
    val all = Achievements.all
    val earned = all.count { it.id in unlocked }
    val next = all.filter { it.id !in unlocked }.maxByOrNull { progress.fraction(it) }
    androidx.activity.compose.BackHandler(onBack = onBack)

    Column(
        modifier.revealScroll().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageBar("Достижения", onBack, icon = AppIcons.Trophy, trailing = { CountBadge(earned) })

        Row(
            Modifier.fillMaxWidth().scrollReveal().fieldSurface(appCorner(22)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TotalRing(earned, all.size, 84.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Получено $earned/${all.size}",
                    color = AppColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 16.sp, stepSize = 0.5.sp),
                )
                if (next != null) {
                    Text("Ближе всего:", color = AppColors.Faint, fontSize = 12.sp, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AchievementMedal(next, earned = false, fraction = progress.fraction(next), size = 26.dp)
                        Text(
                            next.title,
                            color = AppColors.Text,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(progressLabel(next, progress), color = AppColors.Muted, fontSize = 12.sp, maxLines = 1)
                    }
                    ProgressLine(progress.fraction(next), Color(medalStyle(next.kind).to))
                } else {
                    Text("Все значки собраны", color = AppColors.Accent, fontSize = 13.sp, maxLines = 1)
                }
            }
        }

        AchievementKind.entries.forEach { kind ->
            val group = all.filter { it.kind == kind }
            if (group.isEmpty()) return@forEach
            val style = medalStyle(kind)
            TabbedFrame(kindTitle(kind), icon = style.icon, inset = false, modifier = Modifier.scrollReveal(), trailing = { InfoChip("${group.count { it.id in unlocked }}/${group.size}", Color(style.from)) }) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    group.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEachIndexed { i, achievement ->
                                AchievementBadge(achievement, unlocked[achievement.id], progress, Modifier.weight(1f).scrollReveal(80 + i * 90))
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(fraction: Float, color: Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction, Motion.tween(800)) }
    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(AppColors.Line)) {
        Box(Modifier.fillMaxWidth(anim.value.coerceAtLeast(0.02f)).height(5.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun TotalRing(earned: Int, total: Int, size: Dp) {
    val fraction = if (total > 0) earned.toFloat() / total else 0f
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction, Motion.tween(900)) }
    val accent = AppColors.Accent
    val light = AppColors.AccentLight
    val track = AppColors.Line
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = this.size.minDimension * 0.1f
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            val sweep = 360f * anim.value
            if (sweep > 0f) {
                drawArc(Brush.linearGradient(listOf(light, accent)), -90f, sweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Icon(AppIcons.Trophy, null, tint = if (earned > 0) accent else AppColors.Faint, modifier = Modifier.size(size * 0.36f))
    }
}
