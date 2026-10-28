package com.flerty.wikiflerty.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
}

@Composable
fun AchievementMedal(achievement: Achievement, earned: Boolean, fraction: Float, size: Dp, modifier: Modifier = Modifier) {
    val style = medalStyle(achievement.kind)
    val accent = Color(style.to)
    val track = AppColors.Line
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (!earned) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = this.size.minDimension * 0.07f
                val inset = stroke / 2
                val arc = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                if (fraction > 0f) {
                    drawArc(accent, -90f, 360f * fraction, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        val inner = if (earned) size else size * 0.74f
        val shape = CircleShape
        Box(
            Modifier
                .size(inner)
                .clip(shape)
                .then(
                    if (earned) {
                        Modifier
                            .background(Brush.linearGradient(listOf(Color(style.from), Color(style.to))))
                            .border(1.5.dp, Color.White.copy(alpha = 0.32f), shape)
                    } else {
                        Modifier.background(AppColors.Field).border(1.dp, AppColors.PanelBorder, shape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(style.icon, null, tint = if (earned) Color.White else AppColors.Faint, modifier = Modifier.size(inner * 0.46f))
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
    val base = "${number(value)} / ${number(achievement.target)}"
    return if (achievement.kind == AchievementKind.Time) "$base ч" else base
}

@Composable
fun AchievementsSection(progress: AchievementProgress, unlocked: Map<String, Long>) {
    val earnedCount = Achievements.all.count { it.id in unlocked }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Достижения", color = AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$earnedCount из ${Achievements.all.size}", color = AppColors.Faint, fontSize = 12.sp)
        }
        Column(
            Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(horizontal = 6.dp, vertical = 14.dp),
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

val YearGoalChoices = listOf(0, 3, 5, 6, 10, 12, 15, 20, 24, 30, 36, 40, 50, 52, 60, 75, 100)

@Composable
fun YearGoalCard(goal: Int, finished: Int, onGoal: (Int) -> Unit) {
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (goal > 0) {
            val fraction = (finished.toFloat() / goal).coerceIn(0f, 1f)
            val expected = goal * today.dayOfYear.toDouble() / today.lengthOfYear()
            val diff = floor(finished - expected).toInt()
            val pace = when {
                finished >= goal -> "Цель на год выполнена"
                diff >= 1 -> "Опережаешь план на $diff"
                finished + 1 < expected -> "Отстаёшь от плана на ${(expected - finished).toInt()}"
                else -> "Идёшь по плану"
            }
            Row(
                Modifier.padding(top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                YearRing(fraction, finished, goal, 76.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Прочитано $finished из $goal",
                        color = AppColors.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(pace, color = AppColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (finished < goal) {
                        val left = goal - finished
                        Text(
                            "Ещё $left " + StatsCalc.plural(left, "книга", "книги", "книг") + " до конца года",
                            color = AppColors.Faint,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        PickerRow(
            label = "Цель на год",
            icon = AppIcons.Trophy,
            options = YearGoalChoices.map { n ->
                if (n == 0) {
                    PickerOption("0", "Без цели", "Просто считать книги")
                } else {
                    PickerOption(n.toString(), "$n " + StatsCalc.plural(n, "книга", "книги", "книг"), null)
                }
            },
            selectedId = goal.toString(),
            searchable = false,
        ) { id -> onGoal(id.toInt()) }
    }
}

@Composable
private fun YearRing(fraction: Float, finished: Int, goal: Int, size: Dp) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction, Motion.tween(900)) }
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
            Text("$finished", color = AppColors.Text, fontSize = 21.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("из $goal", color = AppColors.Faint, fontSize = 10.sp, lineHeight = 10.sp, maxLines = 1)
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
fun AchievementsButton(earned: Int, total: Int, onClick: () -> Unit) {
    val shape = appCorner(18)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onClick = onClick)
            .fieldSurface(shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
            listOf(AchievementKind.Streak, AchievementKind.Books, AchievementKind.Time).forEach { kind ->
                val style = medalStyle(kind)
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(style.from), Color(style.to))))
                        .border(1.5.dp, AppColors.Field, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Достижения", color = AppColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("$earned из $total", color = AppColors.Muted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.align(Alignment.CenterEnd).size(20.dp))
    }
}

private fun kindTitle(kind: AchievementKind) = when (kind) {
    AchievementKind.Streak -> "Серии"
    AchievementKind.Books -> "Книги"
    AchievementKind.Pages -> "Страницы"
    AchievementKind.Time -> "Время"
}

@Composable
fun AchievementsPage(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val days by app.stats.days.collectAsStateWithLifecycle()
    val books by app.library.books.collectAsStateWithLifecycle()
    val unlocked by app.achievements.unlocked.collectAsStateWithLifecycle()
    val progress = remember(days, books) { AchievementProgress.of(days, books) }
    val all = Achievements.all
    val earned = all.count { it.id in unlocked }
    val next = all.filter { it.id !in unlocked }.maxByOrNull { progress.fraction(it) }
    androidx.activity.compose.BackHandler(onBack = onBack)

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .liveClick(CircleShape, sound = FeedbackSound.Close, pressedScale = 0.88f, onClick = onBack)
                    .fieldSurface(CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Back, "Назад", tint = AppColors.Text, modifier = Modifier.size(20.dp)) }
            Text("Достижения", color = AppColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            CountBadge(earned)
        }

        Row(
            Modifier.fillMaxWidth().fieldSurface(appCorner(22)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TotalRing(earned, all.size, 84.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Получено $earned из ${all.size}",
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    kindTitle(kind),
                    icon = style.icon,
                    action = { InfoChip("${group.count { it.id in unlocked }} / ${group.size}", Color(style.from)) },
                )
                Column(
                    Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(horizontal = 6.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    group.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { achievement ->
                                AchievementBadge(achievement, unlocked[achievement.id], progress, Modifier.weight(1f), withHint = true)
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
