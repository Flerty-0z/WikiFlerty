package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.DayEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private val GoalYellow = Color(0xFFF2C14E)
private val WeekLetters = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
fun GoalHeader(icon: ImageVector, title: String, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
        }
        RollingText(title, Modifier.weight(1f)) {
            Text(it, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Box(
            Modifier.size(36.dp).liveClick(CircleShape, sound = FeedbackSound.Open, pressedScale = 0.88f, onClick = onEdit).fieldSurface(CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Pencil, "Изменить", tint = AppColors.Muted, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
fun WeekGoalBars(perDay: Map<LocalDate, DayEntry>, today: LocalDate, goal: Int, inPages: Boolean) {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val values = (0 until 7).map { i ->
        val e = perDay[monday.plusDays(i.toLong())]
        if (inPages) (e?.pages ?: 0).toFloat() else ((e?.ms ?: 0L) / 60_000f)
    }
    val top = maxOf(goal * 1.3f, values.maxOrNull() ?: 0f, 1f)
    val grow = remember { Animatable(if (Motion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, Motion.tween(820, 120, GrowEasing)) }
    val scale = remember { Animatable(top) }
    LaunchedEffect(top) { scale.animateTo(top, Motion.tween(420)) }
    val accent = AppColors.Accent
    val track = AppColors.Line
    val todayIndex = (today.dayOfWeek.value - 1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(92.dp)) {
            val slot = size.width / 7f
            val bw = slot * 0.54f
            val r = CornerRadius(bw * 0.32f, bw * 0.32f)
            val h = size.height
            val goalY = h - h * (goal / scale.value)
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - bw) / 2f
                drawRoundRect(track.copy(alpha = 0.35f), Offset(x, 0f), Size(bw, h), r)
                if (i > todayIndex) return@forEachIndexed
                val bh = (h * (v / scale.value) * grow.value.coerceAtMost(1.05f)).coerceIn(if (v > 0f) bw * 0.5f else 0f, h)
                if (bh > 0f) {
                    val reached = v >= goal
                    drawRoundRect(if (reached) accent else accent.copy(alpha = 0.34f), Offset(x, h - bh), Size(bw, bh), r)
                }
            }
            val dash = 6.dp.toPx()
            drawLine(
                GoalYellow.copy(alpha = grow.value.coerceIn(0f, 1f)),
                Offset(0f, goalY),
                Offset(size.width, goalY),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.8f)),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            WeekLetters.forEachIndexed { i, l ->
                Text(
                    l,
                    color = if (i == todayIndex) AppColors.Accent else AppColors.Faint,
                    fontSize = 11.sp,
                    fontWeight = if (i == todayIndex) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun YearShelf(finished: Int, goal: Int, inPages: Boolean, bookIds: List<String>) {
    val perSlot = !inPages && goal <= 30
    val slots = if (perSlot) goal else 10
    val filled = if (perSlot) finished.coerceAtMost(slots).toFloat() else (finished.toFloat() / goal * 10f).coerceIn(0f, 10f)
    val rows = (slots + 9) / 10
    val cols = if (rows == 1) slots.coerceAtLeast(5) else 10
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0 until rows).forEach { row ->
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                    (0 until cols).forEach { c ->
                        val i = row * 10 + c
                        Box(Modifier.weight(1f)) {
                            if (i < slots) {
                                val part = (filled - i).coerceIn(0f, 1f)
                                ShelfSlot(i, part, if (perSlot && part >= 1f) bookIds.getOrNull(i) else null)
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(4.dp).clip(appCorner(2)).background(AppColors.Accent.copy(alpha = 0.28f)))
            }
        }
    }
}

@Composable
private fun ShelfSlot(index: Int, part: Float, bookId: String?) {
    val appear = remember { Animatable(if (Motion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.tween(460, 140 + index * 45, GrowEasing)) }
    val shape = appCorner(4)
    val line = AppColors.PanelBorder
    val accent = AppColors.Accent
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.64f)
            .graphicsLayer {
                val v = appear.value
                alpha = v.coerceIn(0f, 1f)
                translationY = (1f - v) * 10.dp.toPx()
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
            .drawBehind {
                if (part < 1f) {
                    val w = 1.dp.toPx()
                    drawRoundRect(
                        line,
                        Offset(w / 2, w / 2),
                        Size(size.width - w, size.height - w),
                        CornerRadius(4.dp.toPx()),
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                }
                if (part > 0f && part < 1f) {
                    val fh = size.height * part * appear.value.coerceIn(0f, 1f)
                    drawRoundRect(accent.copy(alpha = 0.55f), Offset(0f, size.height - fh), Size(size.width, fh), CornerRadius(4.dp.toPx()))
                }
            },
    ) {
        if (part >= 1f) {
            Box(Modifier.fillMaxSize().clip(shape).background(accent)) {
                if (bookId != null) SlotCover(bookId)
            }
        }
    }
}

@Composable
private fun SlotCover(bookId: String) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val book = remember(books, bookId) { books.firstOrNull { it.id == bookId } }
    val file = remember(book) { book?.let { app.library.coverFile(it) } }
    val cover by produceState(CoverCache.peek(file, 120), file) {
        value = file?.let { withContext(Dispatchers.IO) { CoverCache.load(it, 120) } }
    }
    cover?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
}
