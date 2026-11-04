package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Speck(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Float)

private fun specks(seed: Int, n: Int): List<Speck> {
    val r = Random(seed)
    return List(n) { Speck(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f, 0.4f + r.nextFloat()) }
}

@Composable
private fun clock(animated: Boolean): State<Float> {
    if (!animated || !Motion.enabled) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "kit").animateFloat(
        0f,
        3600f,
        infiniteRepeatable(tween(3_600_000, easing = LinearEasing), RepeatMode.Restart),
        label = "kit-time",
    )
}

@Composable
fun KitBackdrop(decor: Decor, base: UiBase, accent: Color, modifier: Modifier = Modifier, animated: Boolean = true) {
    val time = clock(animated && decor != Decor.None)
    val petals = remember { specks(23, 14) }
    val fibers = remember { specks(41, 70) }
    val columns = remember { specks(53, 120) }
    val washes = remember { specks(61, 6) }
    val light = base.light
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(base.background))
            when (decor) {
                Decor.Library -> libraryStatic(light)
                Decor.Zen -> fibers.forEach { f ->
                    val x = f.x * size.width
                    val y = f.y * size.height
                    val len = (6f + f.size * 18f) * density
                    drawLine(Color(base.text).copy(alpha = 0.035f), Offset(x, y), Offset(x + len, y + len * 0.15f), 0.8f * density)
                }
                Decor.Comic -> comicStatic(light, Color(base.text), accent)
                Decor.Wash -> washPaper(light)
                else -> Unit
            }
        }
        if (decor != Decor.None) {
            Canvas(Modifier.fillMaxSize()) {
                val t = time.value
                when (decor) {
                    Decor.Library -> {
                        val flicker = 0.26f + 0.05f * sin(t * 7.3f) + 0.03f * sin(t * 13.1f)
                        glow(Color(0xFFF2A764), flicker * if (light) 0.6f else 1f, Offset(size.width * 0.92f, size.height * 0.04f), size.width * 0.55f)
                    }
                    Decor.Zen -> petals.forEach { p ->
                        val span = size.height + 60f * density
                        val y = ((p.y * span + t * 18f * density * p.speed) % span) - 30f * density
                        val x = p.x * size.width + sin(t * 0.6f * p.speed + p.phase) * 28f * density
                        rotate(t * 30f * p.speed + p.phase * 57f, Offset(x, y)) {
                            drawOval(Color(0xFFF4B6C2).copy(alpha = if (light) 0.75f else 0.5f), Offset(x - 5f * density, y - 3.5f * density), Size(10f * density, 7f * density))
                        }
                    }
                    Decor.Comic -> {
                        val c = Offset(size.width * 0.9f, size.height * 0.1f)
                        rotate(t * 4f, c) { burst(c, size.width * 0.2f, accent.copy(alpha = if (light) 0.16f else 0.12f)) }
                    }
                    Decor.News -> newsLive(t, light, Color(base.text), accent, columns)
                    Decor.Wash -> washLive(t, light, washes)
                    else -> Unit
                }
            }
        }
    }
}

private fun DrawScope.glow(color: Color, alpha: Float, center: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceIn(0f, 1f)), Color.Transparent), center = center, radius = radius), radius, center)
}

private fun DrawScope.libraryStatic(light: Boolean) {
    val step = 64.dp.toPx()
    var x = 0f
    var i = 0
    while (x < size.width) {
        drawRect(if (light) Color(if (i % 2 == 0) 0x10603A14 else 0x08603A14) else Color(if (i % 2 == 0) 0x14FFD9A0 else 0x08FFD9A0), Offset(x, 0f), Size(step, size.height))
        drawLine(if (light) Color(0x22603A14) else Color(0x33000000), Offset(x, 0f), Offset(x, size.height), 1.5f * density)
        x += step
        i++
    }
}

private fun DrawScope.comicStatic(light: Boolean, text: Color, accent: Color) {
    val step = 10.dp.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            val k = ((x / size.width) * 0.5f + (y / size.height) * 0.5f)
            drawCircle(text.copy(alpha = if (light) 0.08f else 0.07f), (0.6f + 1.6f * k) * density, Offset(x, y))
            x += step
        }
        y += step
    }
}

private fun DrawScope.burst(center: Offset, radius: Float, color: Color) {
    val path = Path()
    val n = 14
    for (k in 0 until n * 2) {
        val ang = PI * k / n
        val r = if (k % 2 == 0) radius else radius * 0.55f
        val x = center.x + (r * cos(ang)).toFloat()
        val y = center.y + (r * sin(ang)).toFloat()
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private val WashColors = listOf(0xFF9FC9FF, 0xFFFFB3C7, 0xFFFFE08A, 0xFFB9F0C4, 0xFFD4B8FF, 0xFFFFC89A)

private fun DrawScope.newsLive(t: Float, light: Boolean, ink: Color, accent: Color, lines: List<Speck>) {
    val col = size.width / 3f
    val gap = 14.dp.toPx()
    val row = 11.dp.toPx()
    val rows = (size.height / row).toInt()
    val h = 3.dp.toPx()
    for (c in 0 until 3) {
        if (c > 0) drawLine(ink.copy(alpha = if (light) 0.07f else 0.06f), Offset(col * c, 0f), Offset(col * c, size.height), 1f * density)
        for (r in 0 until rows) {
            val s = lines[(c * 37 + r) % lines.size]
            val pulse = 0.5f + 0.5f * sin(t * 0.35f * s.speed + s.phase + r * 0.21f)
            val a = (if (light) 0.025f else 0.03f) + pulse * (if (light) 0.04f else 0.035f)
            val w = (col - gap) * (0.55f + 0.45f * s.size)
            drawRect(ink.copy(alpha = a), Offset(col * c + gap / 2, r * row + row / 2), Size(w, h))
        }
    }
    for (k in 0 until 3) {
        val cycle = t * 0.04f + k / 3f
        val life = cycle % 1f
        val n = cycle.toInt() * 3 + k
        val x = ((n * 0.6180339f) % 1f) * size.width
        val y = ((n * 0.4142135f) % 1f) * size.height
        val r = (18f + 46f * life) * density
        val color = if (k == 0) accent else ink
        drawCircle(color.copy(alpha = (1f - life) * life * (if (light) 0.16f else 0.12f)), r, Offset(x, y))
    }
}

private fun DrawScope.washPaper(light: Boolean) {
    val step = 5.dp.toPx()
    var y = 0f
    var k = 0
    val c = if (light) Color(0x08503A20) else Color(0x08FFFFFF)
    while (y < size.height) {
        var x = if (k % 2 == 0) 0f else step / 2
        while (x < size.width) {
            drawCircle(c, 0.9f * density, Offset(x, y))
            x += step * 2
        }
        y += step
        k++
    }
}

private fun DrawScope.washLive(t: Float, light: Boolean, washes: List<Speck>) {
    washes.forEachIndexed { i, w ->
        val cx = (w.x + 0.12f * sin(t * 0.05f * w.speed + w.phase)) * size.width
        val cy = (w.y + 0.08f * cos(t * 0.04f * w.speed + w.phase)) * size.height
        val r = size.width * (0.45f + 0.18f * w.size) * (1f + 0.08f * sin(t * 0.2f + w.phase))
        val color = Color(WashColors[i % WashColors.size])
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = if (light) 0.42f else 0.2f), color.copy(alpha = 0f)), center = Offset(cx, cy), radius = r), r, Offset(cx, cy))
    }
    for (k in 0 until 2) {
        val cycle = t * 0.09f + k * 0.5f
        val life = cycle % 1f
        val n = cycle.toInt() * 2 + k
        val x = (0.15f + ((n * 0.6180339f) % 1f) * 0.7f) * size.width
        val y = (0.15f + ((n * 0.7548776f) % 1f) * 0.7f) * size.height
        val ring = (6f + 70f * life) * density
        val col = Color(WashColors[n % WashColors.size])
        drawCircle(col.copy(alpha = (1f - life) * (if (light) 0.55f else 0.4f)), ring, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * density))
        if (life < 0.15f) drawCircle(col.copy(alpha = (0.15f - life) * 4f), 4f * density, Offset(x, y))
    }
}
