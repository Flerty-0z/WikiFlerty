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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.foundation.layout.width
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
    AchievementKind.Streak -> MedalStyle(0xFFFF9C9C, 0xFFC8323E, AppIcons.Fire)
    AchievementKind.Books -> MedalStyle(0xFF9DB2FF, 0xFF4C5ED9, AppIcons.Book)
    AchievementKind.Pages -> MedalStyle(0xFF7FF2DE, 0xFF1A9483, AppIcons.Pages)
    AchievementKind.Time -> MedalStyle(0xFFC9B5FF, 0xFF7048DD, AppIcons.Clock)
    AchievementKind.Notes -> MedalStyle(0xFFF8DC7E, 0xFFB9890F, AppIcons.Note)
    AchievementKind.Words -> MedalStyle(0xFFF9B3CF, 0xFFC44A86, AppIcons.Translate)
    AchievementKind.Rhythm -> MedalStyle(0xFFC7D2FE, 0xFF3F3AB0, AppIcons.Moon)
    AchievementKind.Marathon -> MedalStyle(0xFF8BE3FF, 0xFF0E7490, AppIcons.Speed)
    AchievementKind.Library -> MedalStyle(0xFFE6D3B1, 0xFF8B6B45, AppIcons.Shelf)
    AchievementKind.Discovery -> MedalStyle(0xFFD9F99D, 0xFF4D7C0F, AppIcons.Sparkle)
}

private fun medalTier(achievement: Achievement): Int {
    achievement.tier?.let { return it.coerceIn(0, 2) }
    val group = Achievements.all.filter { it.kind == achievement.kind }
    val i = group.indexOf(achievement).coerceAtLeast(0)
    return (i * 3 / group.size.coerceAtLeast(1)).coerceIn(0, 2)
}

private val MetalRims = listOf(Color(0xFFC99A78), Color(0xFFDCE3EA), Color(0xFFFFD86B))
private val MetalNames = listOf("Бронза", "Серебро", "Золото")

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
        AchievementKind.Rhythm -> poly(5, -90.0) { r * 0.98f }
        AchievementKind.Marathon -> poly(7, -90.0) { r * 0.97f }
        AchievementKind.Library -> poly(4, -45.0) { r * 1.18f }
        AchievementKind.Discovery -> poly(16, -90.0) { if (it % 2 == 0) r * 1.0f else r * 0.84f }
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
fun AchievementMedal(achievement: Achievement, earned: Boolean, fraction: Float, size: Dp, modifier: Modifier = Modifier, focus: Boolean = false, glow: Boolean = false) {
    val style = medalStyle(achievement.kind)
    val rim = MetalRims[medalTier(achievement)]
    val top = Color(style.from)
    val bottom = Color(style.to)
    val field = AppColors.Field
    val border = AppColors.PanelBorder
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = this.size.width
            val path = medalPath(achievement.kind, s, s)
            if (earned) {
                if (glow) drawCircle(Brush.radialGradient(listOf(top.copy(alpha = 0.45f), top.copy(alpha = 0f)), center, s * 0.62f), s * 0.62f, center)
                drawPath(path, Brush.linearGradient(listOf(top, bottom), Offset.Zero, Offset(s * 0.4f, s)))
                drawPath(path, rim, style = Stroke(width = s * 0.055f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                scale(0.8f, 0.8f, center) {
                    drawPath(path, Color.White.copy(alpha = 0.28f), style = Stroke(width = s * 0.02f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                clipPath(path) {
                    rotate(-24f, Offset(s * 0.36f, s * 0.24f)) {
                        drawOval(Color.White.copy(alpha = 0.32f), Offset(s * 0.16f, s * 0.14f), Size(s * 0.4f, s * 0.12f))
                    }
                }
            } else {
                drawPath(path, field)
                if (fraction > 0f) {
                    clipPath(path) {
                        drawRect(bottom.copy(alpha = 0.22f), Offset(0f, s * (1f - fraction)), Size(s, s * fraction))
                    }
                }
                if (focus) {
                    drawPath(
                        path,
                        top,
                        style = Stroke(
                            width = s * 0.04f,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(s * 0.07f, s * 0.055f)),
                        ),
                    )
                } else {
                    drawPath(path, border, style = Stroke(width = s * 0.035f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
        }
        Icon(
            if (earned || focus) style.icon else AppIcons.Lock,
            null,
            tint = when {
                earned -> Color.White.copy(alpha = 0.95f)
                focus -> top.copy(alpha = 0.9f)
                else -> AppColors.Faint.copy(alpha = 0.7f)
            },
            modifier = Modifier.size(size * if (achievement.kind == AchievementKind.Pages || achievement.kind == AchievementKind.Rhythm) 0.30f else 0.34f),
        )
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
    val value = progress.value(achievement).coerceAtMost(achievement.target)
    if (achievement.target == 1L) return if (value >= 1) "готово" else "ещё нет"
    val base = "${number(value)}/${number(achievement.target)}"
    return achievement.unit?.let { "$base $it" } ?: base
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
        var shine by remember(achievement.id) { androidx.compose.runtime.mutableIntStateOf(0) }
        LaunchedEffect(achievement.id) {
            delay(200)
            shine++
        }
        val pop = rememberPop(shine)
        Box(
            Modifier
                .size(52.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    rotationY = (1f - pop.value) * 160f
                }
                .victoryBloom(shine, listOf(Color(style.from), StarYellow, Color.White), reachDp = 58f, durationMs = 1900),
            contentAlignment = Alignment.Center,
        ) {
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
    val app = LocalContext.current.applicationContext as App
    val shape = appCorner(20)
    val seenAt = remember(unlocked) { app.achievements.seenAt() }
    val fresh = unlocked.values.any { it > seenAt }
    val glow = rememberInfiniteTransition(label = "ach-new")
    val beat by glow.animateFloat(0.35f, 1f, infiniteRepeatable(androidx.compose.animation.core.tween(1100), RepeatMode.Reverse), label = "beat")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onClick = onClick)
            .panelSurface(shape, 8.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val recent = Achievements.all.filter { it.id in unlocked }.sortedByDescending { unlocked[it.id] ?: 0L }.take(3)
        val shown = recent.ifEmpty { listOf(AchievementKind.Streak, AchievementKind.Books, AchievementKind.Pages).mapNotNull { k -> Achievements.all.firstOrNull { it.kind == k } } }
        Row(horizontalArrangement = Arrangement.spacedBy((-13).dp), verticalAlignment = Alignment.CenterVertically) {
            shown.forEachIndexed { i, a ->
                val on = a.id in unlocked
                val newest = i == 0 && fresh && on
                AchievementMedal(
                    a,
                    earned = on,
                    fraction = 0f,
                    size = if (i == 0) 38.dp else 32.dp,
                    glow = on && (i == 0),
                    modifier = Modifier
                        .zIndex((shown.size - i).toFloat())
                        .scrollReveal(i * 70)
                        .graphicsLayer {
                            if (newest) {
                                val k = 1f + 0.06f * beat
                                scaleX = k
                                scaleY = k
                            }
                            rotationZ = (i - 1) * -8f
                        },
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Достижения",
                    color = AppColors.Text,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 17.sp, stepSize = 0.5.sp),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (fresh) {
                    Box(Modifier.size(7.dp).graphicsLayer { alpha = 0.5f + 0.5f * beat }.clip(CircleShape).background(AppColors.Accent))
                }
            }
            ProgressLine(if (total > 0) earned.toFloat() / total else 0f, AppColors.Accent)
        }
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
    AchievementKind.Rhythm -> "Ритм"
    AchievementKind.Marathon -> "Марафоны"
    AchievementKind.Library -> "Библиотека"
    AchievementKind.Discovery -> "Открытия"
}

private val KindOrder = listOf(
    AchievementKind.Streak, AchievementKind.Books, AchievementKind.Pages, AchievementKind.Time, AchievementKind.Rhythm,
    AchievementKind.Marathon, AchievementKind.Notes, AchievementKind.Words, AchievementKind.Library, AchievementKind.Discovery,
)

@Composable
fun AchievementsPage(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val unlocked by app.achievements.unlocked.collectAsStateWithLifecycle()
    val progress by app.achievements.progress.collectAsStateWithLifecycle()
    val seenAt = remember { app.achievements.seenAt() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { app.achievements.markSeen() } }
    val all = Achievements.all
    val earned = all.count { it.id in unlocked }
    val nexts = remember(progress, unlocked) {
        KindOrder.mapNotNull { k -> all.firstOrNull { it.kind == k && it.id !in unlocked } }
            .sortedByDescending { progress.fraction(it) }
    }
    val hero = nexts.firstOrNull()
    val near = nexts.drop(1).filter { progress.fraction(it) > 0f }.take(3)
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
            TotalRing(earned, all.size, 88.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (hero != null) {
                    Text("СЛЕДУЮЩЕЕ", color = AppColors.Faint, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AchievementMedal(hero, earned = false, fraction = progress.fraction(hero), size = 30.dp, focus = true)
                        Text(
                            hero.title,
                            color = AppColors.Text,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 16.sp, stepSize = 0.5.sp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ProgressLine(progress.fraction(hero), Color(medalStyle(hero.kind).to))
                    Text(progressLabel(hero, progress) + " · " + hero.hint, color = AppColors.Muted, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("Все значки собраны", color = AppColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        if (near.isNotEmpty()) {
            Text("ПОЧТИ У ЦЕЛИ", color = AppColors.Faint, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                near.forEachIndexed { i, a ->
                    Column(
                        Modifier.weight(1f).scrollReveal(i * 70).fieldSurface(appCorner(16)).padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AchievementMedal(a, earned = false, fraction = progress.fraction(a), size = 40.dp, focus = true)
                        Text(
                            a.title,
                            color = AppColors.Text,
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
                        )
                        Text("${(progress.fraction(a) * 100).toInt()}%", color = Color(medalStyle(a.kind).from), fontSize = 11.sp, maxLines = 1)
                    }
                }
                repeat(3 - near.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        KindOrder.forEach { kind ->
            val group = all.filter { it.kind == kind }
            if (group.isEmpty()) return@forEach
            val style = medalStyle(kind)
            val next = group.firstOrNull { it.id !in unlocked }
            var picked by remember(kind) { mutableStateOf<Achievement?>(null) }
            val focusIndex = group.indexOf(next).coerceAtLeast(0)
            val strip = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (focusIndex - 2).coerceAtLeast(0))
            TabbedFrame(kindTitle(kind), icon = style.icon, inset = false, modifier = Modifier.scrollReveal(), trailing = { InfoChip("${group.count { it.id in unlocked }}/${group.size}", Color(style.from)) }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.lazy.LazyRow(
                        state = strip,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        items(group.size, key = { group[it].id }) { i ->
                            val a = group[i]
                            ShowcaseMedal(a, unlocked[a.id], progress, focus = a == next, fresh = (unlocked[a.id] ?: 0L) > seenAt, order = i, selected = picked == a) {
                                picked = if (picked == a) null else a
                            }
                        }
                    }
                    val shown = picked ?: next
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (shown != null) {
                            val at = unlocked[shown.id]
                            Text(
                                if (picked != null) shown.hint else "Дальше: ${shown.title}",
                                color = AppColors.Faint,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (at != null) "${MetalNames[medalTier(shown)]} · ${earnedDate(at)}" else progressLabel(shown, progress),
                                color = if (at != null) MetalRims[medalTier(shown)] else Color(style.from),
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        } else {
                            Text("Все получены", color = Color(style.from), fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowcaseMedal(
    achievement: Achievement,
    earnedAt: Long?,
    progress: AchievementProgress,
    focus: Boolean,
    fresh: Boolean,
    order: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val earned = earnedAt != null
    var bloom by remember(achievement.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(achievement.id, fresh) {
        if (!fresh) return@LaunchedEffect
        delay(350L + order * 140L)
        bloom++
    }
    val pop = rememberPop(bloom)
    val shape = appCorner(14)
    val edge by androidx.compose.animation.animateColorAsState(if (selected) Color(medalStyle(achievement.kind).from).copy(alpha = 0.6f) else Color.Transparent, Motion.fade(180), label = "medal-edge")
    Column(
        Modifier
            .width(66.dp)
            .clip(shape)
            .border(1.dp, edge, shape)
            .liveClick(shape, sound = FeedbackSound.Tap, pressedScale = 0.92f, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AchievementMedal(
            achievement,
            earned,
            progress.fraction(achievement),
            50.dp,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    rotationY = (1f - pop.value) * 180f
                }
                .victoryBloom(bloom, listOf(Color(medalStyle(achievement.kind).from), StarYellow, Color.White), reachDp = 54f, durationMs = 1700),
            focus = focus,
            glow = earned,
        )
        Text(
            achievement.title,
            color = if (earned) AppColors.Muted else AppColors.Faint,
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$earned", color = AppColors.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text("из $total", color = AppColors.Faint, fontSize = 10.5.sp, maxLines = 1)
        }
    }
}
