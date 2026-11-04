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
    val pixels = remember { specks(71, 46) }
    val bubbles = remember { specks(83, 26) }
    val doodle = remember { DoodleCache() }
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
                Decor.Note -> noteStatic(light, Color(base.line))
                Decor.Sea -> drawRect(Brush.verticalGradient(if (light) listOf(Color(0xFFBFF0EA), Color(base.background), Color(0xFFF6EFE0)) else listOf(Color(0xFF05495A), Color(base.background), Color(0xFF01131C))))
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
                    Decor.Pixel -> pixelLive(t, light, pixels)
                    Decor.Sea -> seaLive(t, light, bubbles)
                    Decor.Clay -> clayLive(t, light)
                    Decor.Note -> noteLive(t, light, Color(base.text), accent, doodle)
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

private fun DrawScope.pixelLive(t: Float, light: Boolean, stars: List<Speck>) {
    val px = 2.dp.toPx()
    if (light) {
        for (k in 0 until 4) {
            val s = stars[k]
            val speed = (6f + 6f * s.speed) * density
            val span = size.width + 80f * density
            val raw = (s.x * span + t * speed) % span - 40f * density
            val x = (raw / (px * 2)).toInt() * px * 2
            val y = (s.y * 0.45f * size.height / (px * 2)).toInt() * px * 2
            val u = px * 2 * (2 + (s.size * 2).toInt())
            val c = Color.White.copy(alpha = 0.9f)
            drawRect(c, Offset(x, y), Size(u * 3, u))
            drawRect(c, Offset(x + u, y - u), Size(u, u))
            drawRect(c, Offset(x + u * 2, y - u * 0.5f), Size(u * 0.5f, u * 0.5f))
        }
        for (k in 4 until 16) {
            val s = stars[k]
            val on = ((t * 1.2f * s.speed + s.phase).toInt() % 3) != 0
            if (!on) continue
            val x = (s.x * size.width / (px * 2)).toInt() * px * 2
            val y = (s.y * size.height / (px * 2)).toInt() * px * 2
            drawRect(Color(0xFF9AB06A).copy(alpha = 0.35f), Offset(x, y), Size(px * 2, px * 2))
        }
    } else {
        stars.forEach { s ->
            val phase = ((t * 1.6f * s.speed + s.phase * 3f).toInt() % 4)
            val a = when (phase) { 0 -> 0.2f; 1 -> 0.55f; 2 -> 0.9f; else -> 0.55f }
            val x = (s.x * size.width / px).toInt() * px
            val y = (s.y * size.height / px).toInt() * px
            val big = s.size > 0.82f
            val c = Color.White.copy(alpha = a)
            if (big) {
                drawRect(c, Offset(x, y - px), Size(px, px * 3))
                drawRect(c, Offset(x - px, y), Size(px * 3, px))
            } else {
                drawRect(c, Offset(x, y), Size(px, px))
            }
        }
    }
}

private fun DrawScope.seaLive(t: Float, light: Boolean, bubbles: List<Speck>) {
    val rayAlpha = if (light) 0.32f else 0.12f
    listOf(0.12f to 26f, 0.4f to 14f, 0.62f to 34f, 0.86f to 12f).forEachIndexed { i, (fx, w) ->
        val sway = sin(t * 0.12f + i * 1.7f) * 18f * density
        val a = rayAlpha * (0.65f + 0.35f * sin(t * 0.3f + i * 2.1f))
        val top = fx * size.width + sway
        val wp = w * density
        val len = size.height * 0.75f
        val p = Path().apply {
            moveTo(top, 0f)
            lineTo(top + wp, 0f)
            lineTo(top + wp - len * 0.25f + wp * 0.8f, len)
            lineTo(top - len * 0.25f - wp * 0.8f, len)
            close()
        }
        drawPath(p, Brush.verticalGradient(listOf(Color.White.copy(alpha = a), Color.White.copy(alpha = 0f)), startY = 0f, endY = len))
    }
    val weed = if (light) Color(0xFF2E9C6A) else Color(0xFF1F7A5A)
    listOf(-0.01f to 0.3f, 0.04f to 0.2f, 0.95f to 0.24f, 1.0f to 0.17f).forEachIndexed { i, (fx, fh) ->
        val x0 = fx * size.width
        val h = fh * size.height
        val sw = sin(t * 0.7f + i * 1.3f) * 10f * density
        val p = Path().apply {
            moveTo(x0, size.height)
            cubicTo(x0 - 14f * density + sw * 0.3f, size.height - h * 0.3f, x0 + 14f * density + sw * 0.7f, size.height - h * 0.6f, x0 + sw, size.height - h)
        }
        drawPath(p, weed.copy(alpha = 0.55f), style = androidx.compose.ui.graphics.drawscope.Stroke(5f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
    val ring = if (light) Color(0xFF0D3B44).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.3f)
    bubbles.forEach { b ->
        val span = size.height + 40f * density
        val life = ((b.y + t * 0.03f * b.speed) % 1f)
        val y = size.height + 20f * density - life * span
        val x = b.x * size.width + sin(t * 1.4f * b.speed + b.phase) * 6f * density
        val r = (2f + b.size * 5f) * density
        drawCircle(ring, r, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.1f * density))
        drawCircle(Color.White.copy(alpha = 0.35f), r * 0.3f, Offset(x - r * 0.35f, y - r * 0.35f))
    }
    for (k in 0 until 5) {
        val cx = (k / 4f) * size.width + sin(t * 0.25f + k) * 30f * density
        val cy = size.height * (0.88f + 0.04f * sin(t * 0.33f + k * 1.9f))
        val r = size.width * 0.22f
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (light) 0.22f else 0.06f), Color.Transparent), center = Offset(cx, cy), radius = r), r, Offset(cx, cy))
    }
}

private val ClayBlobs = listOf(
    floatArrayOf(0.92f, 0.06f, 0.36f),
    floatArrayOf(0.02f, 0.72f, 0.4f),
    floatArrayOf(0.86f, 0.9f, 0.28f),
    floatArrayOf(0.2f, 0.25f, 0.18f),
)
private val ClayTones = listOf(0xFFFFD27A, 0xFF8FD9FF, 0xFFB9F0C4, 0xFFFFB3C7)

private fun DrawScope.clayLive(t: Float, light: Boolean) {
    ClayBlobs.forEachIndexed { i, b ->
        val c = Color(ClayTones[i]).copy(alpha = if (light) 0.6f else 0.26f)
        val base = size.width * b[2]
        val bw = base * (1f + 0.07f * sin(t * 0.5f + i * 1.3f))
        val bh = base * (1f + 0.07f * cos(t * 0.43f + i * 0.9f))
        val center = Offset(size.width * b[0], size.height * b[1] + sin(t * 0.3f + i) * 6f * density)
        val tl = Offset(center.x - bw / 2, center.y - bh / 2)
        drawOval(c, tl, Size(bw, bh))
        drawOval(
            Brush.radialGradient(listOf(Color.White.copy(alpha = if (light) 0.6f else 0.18f), Color.Transparent), center = Offset(center.x - bw * 0.18f, center.y - bh * 0.2f), radius = bw * 0.45f),
            tl,
            Size(bw, bh),
        )
        drawOval(
            Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (light) 0.08f else 0.2f)), center = Offset(center.x - bw * 0.1f, center.y - bh * 0.1f), radius = bw * 0.6f),
            tl,
            Size(bw, bh),
        )
    }
}

private fun DrawScope.noteStatic(light: Boolean, line: Color) {
    val step = 14.dp.toPx()
    if (light) {
        var x = 0f
        while (x < size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f * density)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f * density)
            y += step
        }
        val m = 30.dp.toPx()
        drawLine(Color(0xFFEF8A8A), Offset(m, 0f), Offset(m, size.height), 1.5f * density)
    } else {
        listOf(0.2f to 0.15f, 0.8f to 0.4f, 0.35f to 0.8f).forEach { (fx, fy) ->
            val c = Offset(size.width * fx, size.height * fy)
            val r = size.width * 0.5f
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent), center = c, radius = r), r, c)
        }
        var y = size.height * 0.1f
        val r = kotlin.random.Random(7)
        while (y < size.height) {
            val x = r.nextFloat() * size.width
            drawLine(Color.White.copy(alpha = 0.03f), Offset(x, y), Offset(x + (40f + r.nextFloat() * 80f) * density, y + (r.nextFloat() - 0.5f) * 8f * density), 6f * density)
            y += 60f * density
        }
    }
}

private class DoodleCache {
    var w = 0f
    var h = 0f
    var paths: List<Path> = emptyList()
    var lengths: List<Float> = emptyList()
    val measure = androidx.compose.ui.graphics.PathMeasure()
    val out = Path()
}

private fun DrawScope.doodles(c: DoodleCache) {
    if (c.w == size.width && c.h == size.height && c.paths.isNotEmpty()) return
    val d = density
    val star = Path().apply {
        val cx = size.width - 34f * d
        val cy = 96f * d
        val r1 = 16f * d
        val r2 = 7f * d
        for (k in 0 until 10) {
            val a = -PI / 2 + PI * k / 5
            val r = if (k % 2 == 0) r1 else r2
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    val arrow = Path().apply {
        val x = 14f * d
        val y = size.height * 0.42f
        moveTo(x, y)
        cubicTo(x - 9f * d, y + 18f * d, x + 9f * d, y + 30f * d, x, y + 50f * d)
        lineTo(x - 6f * d, y + 43f * d)
        lineTo(x, y + 50f * d)
        lineTo(x + 6f * d, y + 43f * d)
    }
    val spiral = Path().apply {
        val cx = size.width - 30f * d
        val cy = size.height * 0.68f
        for (k in 0..60) {
            val a = k * 0.32f
            val r = (1.5f + k * 0.28f) * d
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    val wave = Path().apply {
        val y = size.height * 0.9f
        moveTo(40f * d, y)
        var x = 40f * d
        var up = true
        while (x < 130f * d) {
            quadraticTo(x + 7f * d, if (up) y - 8f * d else y + 8f * d, x + 14f * d, y)
            x += 14f * d
            up = !up
        }
    }
    c.paths = listOf(star, arrow, spiral, wave)
    c.lengths = c.paths.map { p -> c.measure.setPath(p, false); c.measure.length }
    c.w = size.width
    c.h = size.height
}

private fun DrawScope.noteLive(t: Float, light: Boolean, ink: Color, accent: Color, cache: DoodleCache) {
    doodles(cache)
    cache.paths.forEachIndexed { i, p ->
        val cycle = (t / 14f + i * 0.27f) % 1f
        val progress = (cycle / 0.35f).coerceIn(0f, 1f)
        val alpha = when {
            cycle < 0.75f -> 1f
            else -> (1f - (cycle - 0.75f) / 0.25f).coerceIn(0f, 1f)
        }
        if (alpha <= 0f || progress <= 0f) return@forEachIndexed
        val color = (if (i == 0) accent else ink).copy(alpha = alpha * if (light) 0.55f else 0.5f)
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(if (light) 1.8f * density else 2.6f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        if (progress >= 1f) {
            drawPath(p, color, style = stroke)
        } else {
            cache.out.reset()
            cache.measure.setPath(p, false)
            cache.measure.getSegment(0f, cache.lengths[i] * progress, cache.out, true)
            drawPath(cache.out, color, style = stroke)
        }
    }
}
