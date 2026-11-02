package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Piece(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Int,
    val delay: Float,
    val spin: Float,
    val sway: Float,
    val x: Float,
)

private fun pieces(seed: Int, count: Int, colors: Int): List<Piece> {
    val r = Random(seed)
    return List(count) {
        Piece(
            angle = r.nextFloat() * 2f * PI.toFloat(),
            speed = 0.55f + r.nextFloat() * 0.6f,
            size = 0.6f + r.nextFloat() * 0.8f,
            color = r.nextInt(colors),
            delay = r.nextFloat() * 0.2f,
            spin = (r.nextFloat() - 0.5f) * 900f,
            sway = r.nextFloat() * 2f * PI.toFloat(),
            x = r.nextFloat(),
        )
    }
}

private fun DrawScope.sparkle(center: Offset, radius: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(p, color)
}

@Composable
private fun rememberRun(trigger: Int, durationMs: Int, linear: Boolean = false): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger <= 0 || !Motion.enabled) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = if (linear) LinearEasing else LinearOutSlowInEasing))
    }
    return progress
}

@Composable
fun Modifier.sparkBurst(trigger: Int, colors: List<Color>, count: Int = 12, reach: Float = 1.1f, durationMs: Int = 650): Modifier {
    val progress = rememberRun(trigger, durationMs)
    val list = remember(trigger, count) { pieces(trigger * 31 + 7, count, colors.size) }
    return drawWithContent {
        drawContent()
        val p = progress.value
        if (p >= 1f) return@drawWithContent
        val base = size.minDimension
        val c = center
        drawCircle(colors.first().copy(alpha = (1f - p) * 0.18f), base * (0.4f + p * 0.5f), c)
        list.forEach { s ->
            val t = ((p - s.delay) / (1f - s.delay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val d = base * (0.3f + reach * s.speed * t)
            val o = Offset(c.x + cos(s.angle) * d, c.y + sin(s.angle) * d)
            val a = (1f - t) * (1f - t)
            val r = base * 0.06f * s.size * (1f - t * 0.5f)
            val color = colors[s.color]
            if (s.size > 1f) sparkle(o, r * 1.8f, color.copy(alpha = a)) else drawCircle(color.copy(alpha = a), r, o)
        }
    }
}

@Composable
fun Modifier.confetti(trigger: Int, colors: List<Color>, count: Int = 70, durationMs: Int = 2600): Modifier {
    val progress = rememberRun(trigger, durationMs, linear = true)
    val list = remember(trigger) { pieces(trigger * 17 + 3, count, colors.size) }
    val unit = LocalDensity.current.density
    return drawWithContent {
        drawContent()
        val p = progress.value
        if (p >= 1f) return@drawWithContent
        val origin = Offset(size.width / 2f, size.height / 2f)
        list.forEach { s ->
            val t = ((p - s.delay * 0.5f) / (1f - s.delay * 0.5f)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val time = t * 2.4f
            val vx = cos(s.angle) * 260f * s.speed * unit
            val vy = -(220f + 260f * s.speed) * unit * (0.6f + 0.4f * kotlin.math.abs(sin(s.angle)))
            val g = 420f * unit
            val drag = 1f / (1f + time * 0.9f)
            val x = origin.x + vx * time * drag + sin(time * 5f + s.sway) * 10f * unit
            val y = origin.y + vy * time * drag + 0.5f * g * time * time
            val a = if (t > 0.75f) (1f - t) / 0.25f else 1f
            val w = 7f * unit * s.size
            val h = 4f * unit * s.size
            rotate(s.spin * t + s.sway * 57f, Offset(x, y)) {
                drawRect(colors[s.color].copy(alpha = a), Offset(x - w / 2f, y - h / 2f), Size(w, h))
            }
        }
    }
}

@Composable
fun Modifier.snowfall(trigger: Int, color: Color, count: Int = 34, durationMs: Int = 2400): Modifier {
    val progress = rememberRun(trigger, durationMs, linear = true)
    val list = remember(trigger) { pieces(trigger * 13 + 5, count, 1) }
    val unit = LocalDensity.current.density
    return drawWithContent {
        drawContent()
        val p = progress.value
        if (p >= 1f) return@drawWithContent
        list.forEach { s ->
            val t = ((p - s.delay) / (1f - s.delay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val x = s.x * size.width + sin(t * 6f + s.sway) * 9f * unit
            val y = -10f * unit + (size.height + 20f * unit) * t * (0.75f + s.speed * 0.35f)
            val a = (if (t < 0.15f) t / 0.15f else if (t > 0.8f) (1f - t) / 0.2f else 1f) * 0.9f
            val r = 2.2f * unit * s.size
            if (s.size > 1.1f) {
                rotate(t * 120f + s.spin * 0.1f, Offset(x, y)) {
                    for (k in 0 until 3) {
                        val ang = k * PI.toFloat() / 3f
                        val dx = cos(ang) * r * 1.8f
                        val dy = sin(ang) * r * 1.8f
                        drawLine(color.copy(alpha = a), Offset(x - dx, y - dy), Offset(x + dx, y + dy), strokeWidth = 1.2f * unit)
                    }
                }
            } else {
                drawCircle(color.copy(alpha = a), r, Offset(x, y))
            }
        }
    }
}

@Composable
fun Modifier.thawDrops(trigger: Int, color: Color, count: Int = 18, durationMs: Int = 1800): Modifier {
    val progress = rememberRun(trigger, durationMs, linear = true)
    val list = remember(trigger) { pieces(trigger * 19 + 11, count, 1) }
    val unit = LocalDensity.current.density
    return drawWithContent {
        drawContent()
        val p = progress.value
        if (p >= 1f) return@drawWithContent
        drawRect(color.copy(alpha = (1f - p) * 0.12f))
        list.forEach { s ->
            val t = ((p - s.delay * 1.5f) / (1f - s.delay * 1.5f)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val x = s.x * size.width
            val y = size.height * 0.1f + size.height * 0.9f * t * t * (0.7f + s.speed * 0.3f)
            val a = (1f - t) * 0.85f
            val r = 2.4f * unit * s.size
            drawCircle(color.copy(alpha = a), r, Offset(x, y))
            drawOval(color.copy(alpha = a * 0.6f), Offset(x - r * 0.6f, y - r * 3.2f), Size(r * 1.2f, r * 3f))
        }
    }
}

@Composable
fun Modifier.riseIn(key: Any?, index: Int = 0): Modifier {
    val tag = key.toString()
    var played by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val p = remember { Animatable(if (Motion.enabled && played != tag) 0f else 1f) }
    LaunchedEffect(tag) {
        if (!Motion.enabled || played == tag) {
            p.snapTo(1f)
            return@LaunchedEffect
        }
        played = tag
        p.snapTo(0f)
        if (index in 1..9) kotlinx.coroutines.delay(index * 35L)
        p.animateTo(1f, Motion.tween(380))
    }
    return graphicsLayer {
        val v = p.value
        alpha = v
        translationY = (1f - v) * 18f * density
        scaleX = 0.96f + 0.04f * v
        scaleY = 0.96f + 0.04f * v
    }
}



@Composable
fun FitText(
    text: String,
    color: Color,
    maxSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    weight: androidx.compose.ui.text.font.FontWeight? = null,
    minSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit(maxSize.value * 0.72f, androidx.compose.ui.unit.TextUnitType.Sp),
) {
    androidx.compose.material3.Text(
        text,
        color = color,
        fontWeight = weight,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 0.25.sp2()),
    )
}

private fun Double.sp2(): androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
