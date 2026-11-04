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
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val stars = remember { specks(11, 110) }
    val petals = remember { specks(23, 14) }
    val embers = remember { specks(37, 16) }
    val fibers = remember { specks(41, 70) }
    val light = base.light
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(base.background))
            when (decor) {
                Decor.Library -> libraryStatic(light)
                Decor.Cosmos -> cosmosStatic(light, accent)
                Decor.Zen -> fibers.forEach { f ->
                    val x = f.x * size.width
                    val y = f.y * size.height
                    val len = (6f + f.size * 18f) * density
                    drawLine(Color(base.text).copy(alpha = 0.035f), Offset(x, y), Offset(x + len, y + len * 0.15f), 0.8f * density)
                }
                Decor.Terminal -> terminalStatic(light, accent)
                Decor.Cabin -> cabinStatic(light, Color(base.text))
                Decor.Comic -> comicStatic(light, Color(base.text), accent)
                Decor.None -> Unit
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
                    Decor.Cosmos -> stars.forEach { s ->
                        val y = ((s.y * size.height + t * 3f * density * s.speed) % size.height)
                        val a = 0.25f + 0.75f * (0.5f + 0.5f * sin(t * 1.3f * s.speed + s.phase))
                        val r = (0.6f + s.size * 1.4f) * density
                        drawCircle((if (light) accent else Color.White).copy(alpha = a * if (light) 0.35f else 0.85f), r, Offset(s.x * size.width, y))
                    }
                    Decor.Zen -> petals.forEach { p ->
                        val span = size.height + 60f * density
                        val y = ((p.y * span + t * 18f * density * p.speed) % span) - 30f * density
                        val x = p.x * size.width + sin(t * 0.6f * p.speed + p.phase) * 28f * density
                        rotate(t * 30f * p.speed + p.phase * 57f, Offset(x, y)) {
                            drawOval(Color(0xFFF4B6C2).copy(alpha = if (light) 0.75f else 0.5f), Offset(x - 5f * density, y - 3.5f * density), Size(10f * density, 7f * density))
                        }
                    }
                    Decor.Terminal -> {
                        val band = 120f * density
                        val y = (t * 70f * density) % (size.height + band) - band
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = if (light) 0.05f else 0.08f), Color.Transparent), startY = y, endY = y + band),
                            Offset(0f, y),
                            Size(size.width, band),
                        )
                    }
                    Decor.Cabin -> {
                        val flicker = 0.24f + 0.06f * sin(t * 5.1f) + 0.04f * sin(t * 11.7f)
                        glow(Color(0xFFFF8A3C), flicker * if (light) 0.5f else 1f, Offset(size.width * 0.5f, size.height * 1.05f), size.width * 0.9f)
                        embers.forEach { e ->
                            val life = ((t * 0.12f * e.speed + e.phase) % 1f)
                            val y = size.height * (1.02f - life * 0.75f)
                            val x = e.x * size.width + sin(t * 1.2f + e.phase) * 14f * density
                            drawCircle(Color(0xFFFFB25E).copy(alpha = (1f - life) * if (light) 0.35f else 0.7f), (1.2f + e.size * 1.6f) * density, Offset(x, y))
                        }
                    }
                    Decor.Comic -> {
                        val c = Offset(size.width * 0.9f, size.height * 0.1f)
                        rotate(t * 4f, c) { burst(c, size.width * 0.2f, accent.copy(alpha = if (light) 0.16f else 0.12f)) }
                    }
                    Decor.None -> Unit
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

private fun DrawScope.cosmosStatic(light: Boolean, accent: Color) {
    glow(if (light) Color(0xFFB8C4FF) else Color(0xFF466EFF), if (light) 0.5f else 0.35f, Offset(size.width * 0.15f, size.height * 0.18f), size.width * 0.8f)
    glow(if (light) Color(0xFFE0D0FF) else Color(0xFF9262FF), if (light) 0.5f else 0.3f, Offset(size.width * 0.9f, size.height * 0.75f), size.width * 0.8f)
    val c = Offset(size.width * 0.95f, size.height * 0.07f)
    val r = size.width * 0.24f
    drawCircle(Brush.radialGradient(listOf(if (light) Color(0xFFC9BEFF) else Color(0xFF9A8BFF), if (light) Color(0xFF8C7AE6) else Color(0xFF2A1E6E)), center = Offset(c.x - r * 0.3f, c.y - r * 0.3f), radius = r * 1.3f), r, c)
    rotate(-18f, c) {
        drawOval(accent.copy(alpha = 0.35f), Offset(c.x - r * 1.7f, c.y - r * 0.22f), Size(r * 3.4f, r * 0.44f), style = Stroke(1.8f * density))
    }
}

private fun DrawScope.terminalStatic(light: Boolean, accent: Color) {
    val grid = 24.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(accent.copy(alpha = 0.04f), Offset(x, 0f), Offset(x, size.height), 1f)
        x += grid
    }
    var y = 0f
    while (y < size.height) {
        drawLine(accent.copy(alpha = 0.04f), Offset(0f, y), Offset(size.width, y), 1f)
        y += grid
    }
    val line = 3.dp.toPx()
    y = 0f
    while (y < size.height) {
        drawRect(Color.Black.copy(alpha = if (light) 0.03f else 0.22f), Offset(0f, y), Size(size.width, line / 3f))
        y += line
    }
}

private fun DrawScope.cabinStatic(light: Boolean, text: Color) {
    val step = 12.dp.toPx()
    val color = text.copy(alpha = if (light) 0.05f else 0.04f)
    var y = step / 2
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else step / 2
        while (x < size.width) {
            drawLine(color, Offset(x - step * 0.25f, y - step * 0.2f), Offset(x, y + step * 0.15f), 1.4f * density)
            drawLine(color, Offset(x + step * 0.25f, y - step * 0.2f), Offset(x, y + step * 0.15f), 1.4f * density)
            x += step
        }
        y += step * 0.7f
        row++
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
