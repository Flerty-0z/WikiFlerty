package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SpineWidths = listOf(13, 16, 11, 15, 18, 12, 15, 10, 14, 17, 12, 16)
private val SpineHeights = listOf(0.8f, 0.94f, 0.72f, 0.86f, 1f, 0.78f, 0.9f, 0.68f, 0.84f, 0.96f, 0.74f, 0.88f)

@Composable
internal fun spinePalette(): List<Color> {
    val panel = AppColors.Panel
    return listOf(
        AppColors.Accent,
        AppColors.Text.copy(alpha = 0.32f).compositeOver(panel),
        AppColors.Text.copy(alpha = 0.88f).compositeOver(panel),
        AppColors.AccentDark,
        AppColors.Text.copy(alpha = 0.5f).compositeOver(panel),
        AppColors.Accent.copy(alpha = 0.7f).compositeOver(panel),
    )
}

fun Modifier.dashedFrame(color: Color, radius: Dp = 6.dp): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
    )
}

@Composable
private fun Spine(index: Int, width: Dp, height: Dp, color: Color, delayMs: Long, animate: Boolean) {
    val drop = remember { Animatable(if (animate) 1f else 0f) }
    val shown = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        delay(delayMs)
        launch { shown.animateTo(1f, tween(160)) }
        drop.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 420f))
    }
    Box(
        Modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                translationY = -drop.value * 46.dp.toPx()
                alpha = shown.value
            }
            .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp)),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = height * 0.16f).height(2.dp).background(Color.Black.copy(alpha = 0.16f)))
        if (index % 3 == 0) Box(Modifier.fillMaxWidth().padding(top = height * 0.7f).height(2.dp).background(Color.Black.copy(alpha = 0.12f)))
    }
}

@Composable
fun CloudShelf(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 92.dp,
    free: String? = "свободно",
    freeAccent: Boolean = false,
    seed: Int = 0,
    stagger: Long = 55,
    startDelay: Long = 0,
    animated: Boolean = true,
) {
    val palette = spinePalette()
    val animate = Motion.enabled && animated
    val board = AppColors.Text.copy(alpha = 0.22f).compositeOver(AppColors.Panel)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val total = maxWidth
        val gap = 3.dp
        val target = total * fraction.coerceIn(0f, 1f)
        val widths = ArrayList<Dp>()
        var used = 0.dp
        var i = 0
        while (true) {
            val w = SpineWidths[(i + seed) % SpineWidths.size].dp
            if (used + w > target || used + w > total) break
            widths += w
            used += w + gap
            i++
        }
        if (fraction > 0f && widths.isEmpty()) widths += SpineWidths[seed % SpineWidths.size].dp
        val rest = total - used
        Column {
            Row(Modifier.fillMaxWidth().height(height).padding(horizontal = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(gap)) {
                widths.forEachIndexed { k, w ->
                    androidx.compose.runtime.key(k) {
                        Spine(k, w, height * SpineHeights[(k + seed) % SpineHeights.size], palette[(k + seed) % palette.size], startDelay + k * stagger, animate)
                    }
                }
                if (free != null && rest > 28.dp) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(0.86f)
                            .dashedFrame(if (freeAccent) AppColors.Accent else AppColors.PanelBorder.copy(alpha = 1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (rest > 64.dp) Text(free, color = if (freeAccent) AppColors.Accent else AppColors.Faint, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(4.dp).background(board, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
fun ShelfSyncScene(modifier: Modifier = Modifier) {
    val palette = spinePalette()
    val frame = AppColors.PanelBorder
    val board = AppColors.Text.copy(alpha = 0.22f).compositeOver(AppColors.Panel)
    val t = rememberInfiniteTransition(label = "sync-scene")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "fly")
    val still = !Motion.enabled
    @Composable
    fun Device(label: String, books: List<Pair<Dp, Int>>, extra: Float) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(
                Modifier
                    .size(64.dp, 96.dp)
                    .background(AppColors.Field, RoundedCornerShape(13.dp))
                    .drawBehind {
                        drawRoundRect(frame, cornerRadius = CornerRadius(13.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    }
                    .padding(start = 9.dp, end = 9.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
                    books.forEach { (h, c) -> Box(Modifier.width(8.dp).height(h).background(palette[c], RoundedCornerShape(2.dp))) }
                    if (extra > 0f) Box(Modifier.width(8.dp).height(36.dp).graphicsLayer { alpha = extra; scaleY = 0.6f + 0.4f * extra; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }.background(palette[0], RoundedCornerShape(2.dp)))
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(board, RoundedCornerShape(2.dp)))
            }
            Text(label, color = AppColors.Muted, fontSize = 12.sp)
        }
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Device("телефон", listOf(36.dp to 0, 44.dp to 1, 32.dp to 2), 0f)
        BoxWithConstraints(Modifier.weight(1f).height(96.dp)) {
            val span = maxWidth - 20.dp
            Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).dashedFrame(AppColors.PanelBorder, 0.dp))
            if (!still) {
                val k = FastOutSlowInEasing.transform((p / 0.8f).coerceIn(0f, 1f))
                val a = when {
                    p < 0.1f -> p / 0.1f
                    p > 0.72f -> ((0.8f - p) / 0.08f).coerceIn(0f, 1f)
                    else -> 1f
                }
                Box(
                    Modifier
                        .offset(x = 10.dp + span * k - 4.dp, y = 30.dp)
                        .width(9.dp)
                        .height(34.dp)
                        .graphicsLayer {
                            alpha = a
                            rotationZ = (k - 0.5f) * 24f
                            translationY = -kotlin.math.sin(k * Math.PI).toFloat() * 16.dp.toPx()
                        }
                        .background(palette[0], RoundedCornerShape(2.dp)),
                )
            }
        }
        Device("планшет", listOf(44.dp to 1), if (still) 1f else ((p - 0.78f) / 0.12f).coerceIn(0f, 1f))
    }
}
