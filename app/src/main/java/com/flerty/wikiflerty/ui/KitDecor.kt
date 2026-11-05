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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
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
private fun clock(animated: Boolean): State<Float> = throttledClock(animated, 3600)

@Composable
fun KitBackdrop(decor: Decor, base: UiBase, accent: Color, modifier: Modifier = Modifier, animated: Boolean = true) {
    val time = clock(animated && decor != Decor.None)
    val petals = remember { specks(23, 14) }
    val fibers = remember { specks(41, 70) }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val columns = remember { specks(53, 120) }
    val bubbles = remember { specks(83, 26) }
    val light = base.light
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            drawRect(Color(base.background))
            when (decor) {
                Decor.Library -> {
                    libraryStatic(light)
                    libraryExtras(light)
                }
                Decor.Zen -> {
                    fibers.forEach { f ->
                        val x = f.x * size.width
                        val y = f.y * size.height
                        val len = (6f + f.size * 18f) * density
                        drawLine(Color(base.text).copy(alpha = 0.035f), Offset(x, y), Offset(x + len, y + len * 0.15f), 0.8f * density)
                    }
                    zenExtras(light)
                }
                Decor.Comic -> {
                    comicStatic(light, Color(base.text), accent)
                    comicExtras(light, measurer)
                }
                Decor.News -> newsExtras(light)
                Decor.Sea -> {
                    drawRect(Brush.verticalGradient(if (light) listOf(Color(0xFFBFF0EA), Color(base.background), Color(0xFFF6EFE0)) else listOf(Color(0xFF05495A), Color(base.background), Color(0xFF01131C))))
                    seaExtras(light)
                }
                Decor.Clay -> clayExtras(light, accent)
                else -> Unit
            }
        }
        if (decor != Decor.None) {
            Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                val t = time.value
                when (decor) {
                    Decor.Library -> {
                        val flicker = 0.26f + 0.05f * sin(t * 7.3f) + 0.03f * sin(t * 13.1f)
                        glow(Color(0xFFF2A764), flicker * if (light) 0.6f else 1f, Offset(size.width * 0.86f, size.height * 0.13f), size.width * 0.55f)
                        dust(t, light, columns)
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
                    Decor.Sea -> {
                        seaLive(t, light, bubbles)
                        seaLife(t, light)
                    }
                    Decor.Clay -> clayLive(t, light)
                    else -> Unit
                }
            }
        }
    }
}

private object GlowCache {
    var color = Color.Unspecified
    var center = Offset.Zero
    var radius = 0f
    var brush: Brush? = null
}

private fun DrawScope.glow(color: Color, alpha: Float, center: Offset, radius: Float) {
    val c = GlowCache
    if (c.brush == null || c.color != color || c.center != center || c.radius != radius) {
        c.color = color
        c.center = center
        c.radius = radius
        c.brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius)
    }
    drawCircle(c.brush!!, radius, center, alpha = alpha.coerceIn(0f, 1f))
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

private fun DrawScope.libraryExtras(light: Boolean) {
    val d = density
    val w = size.width
    val wood = if (light) Color(0xFF9A6A3C) else Color(0xFF3A230F)
    val trim = if (light) Color(0xFF7A4E28) else Color(0xFF6B4A24)
    val top = size.height * 0.105f
    drawRect(wood, Offset(0f, top), Size(w, 10 * d))
    drawLine(trim, Offset(0f, top + 10 * d), Offset(w, top + 10 * d), 2 * d)
    var x = 4 * d
    while (x < w) {
        val p = Path().apply { moveTo(x, top + 10 * d); quadraticTo(x + 8 * d, top + 20 * d, x + 16 * d, top + 10 * d) }
        drawPath(p, trim, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * d))
        x += 20 * d
    }
    val lx = w * 0.86f
    val ly = top + 10 * d
    drawLine(Color(0xFF8A6A3A), Offset(lx, ly), Offset(lx, ly + 16 * d), 1.5f * d)
    val body = Path().apply {
        moveTo(lx - 11 * d, ly + 16 * d); lineTo(lx + 11 * d, ly + 16 * d); lineTo(lx + 8 * d, ly + 40 * d); lineTo(lx - 8 * d, ly + 40 * d); close()
    }
    drawPath(body, Color(0xFF3A2A12))
    drawPath(body, Color(0xFFB8873F), style = androidx.compose.ui.graphics.drawscope.Stroke(1.3f * d))
    drawCircle(Color(0xFFFFD98A), 5 * d, Offset(lx, ly + 29 * d))
    drawRect(Color(0xFFB8873F), Offset(lx - 13 * d, ly + 13 * d), Size(26 * d, 3 * d))
}

private fun DrawScope.dust(t: Float, light: Boolean, motes: List<Speck>) {
    val cx = size.width * 0.86f
    val cy = size.height * 0.13f
    val r = size.width * 0.45f
    for (k in 0 until 14) {
        val m = motes[k]
        val a = m.phase + t * 0.05f * m.speed
        val dist = r * (0.2f + 0.8f * m.size)
        val x = cx + cos(a) * dist * 0.8f
        val y = cy + sin(a * 0.7f) * dist * 0.6f + ((t * 4f * m.speed) % 40f) * density
        drawCircle(Color(0xFFFFE2A8).copy(alpha = (if (light) 0.35f else 0.5f) * (0.5f + 0.5f * sin(t * m.speed + m.phase))), (0.7f + m.size) * density, Offset(x, y))
    }
}

private fun DrawScope.zenExtras(light: Boolean) {
    val d = density
    val w = size.width
    val h = size.height
    drawCircle(Color(0xFFE0675A).copy(alpha = if (light) 0.8f else 0.45f), w * 0.1f, Offset(w * 0.14f, h * 0.2f))
    val sand = if (light) Color(0xFFDED4C2) else Color(0xFF34322B)
    var y = h * 0.8f
    while (y < h) {
        val p = Path().apply {
            moveTo(0f, y)
            var x = 0f
            while (x < w) {
                quadraticTo(x + w / 8, y - 5 * d, x + w / 4, y)
                x += w / 4
            }
        }
        drawPath(p, sand, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f * d))
        y += 10 * d
    }
    drawOval(if (light) Color(0xFFB8B0A0) else Color(0xFF55524A), Offset(w * 0.7f, h * 0.86f), Size(w * 0.16f, w * 0.08f))
    drawOval(if (light) Color(0xFFC9C1B1) else Color(0xFF47443D), Offset(w * 0.6f, h * 0.9f), Size(w * 0.09f, w * 0.045f))
    val bark = if (light) Color(0xFF4A3A30) else Color(0xFF6B5A4A)
    val branch = Path().apply {
        moveTo(w, h * 0.17f)
        cubicTo(w * 0.85f, h * 0.2f, w * 0.78f, h * 0.24f, w * 0.64f, h * 0.26f)
        moveTo(w * 0.84f, h * 0.19f)
        cubicTo(w * 0.8f, h * 0.16f, w * 0.75f, h * 0.15f, w * 0.71f, h * 0.14f)
    }
    drawPath(branch, bark, style = androidx.compose.ui.graphics.drawscope.Stroke(3.5f * d, cap = StrokeCap.Round))
    listOf(0.66f to 0.255f, 0.71f to 0.14f, 0.75f to 0.22f, 0.8f to 0.18f, 0.9f to 0.175f, 0.76f to 0.15f, 0.69f to 0.24f).forEach { (fx, fy) ->
        drawOval(Color(0xFFF4B6C2), Offset(w * fx - 5.5f * d, h * fy - 4 * d), Size(11 * d, 8 * d))
        drawCircle(Color(0xFFE98AA0), 1.8f * d, Offset(w * fx, h * fy))
    }
}

private fun DrawScope.comicExtras(light: Boolean, measurer: androidx.compose.ui.text.TextMeasurer) {
    val d = density
    val w = size.width
    val h = size.height
    val ink = Color(0xFF111111)
    val c = Offset(w * 0.9f, h * 0.1f)
    rotate(-10f, c) {
        val layout = measurer.measure("БАМ!", androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF3B3B).copy(alpha = if (light) 0.55f else 0.45f)))
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
    }
    val bx = w * 0.06f
    val by = h * 0.8f
    rotate(-8f, Offset(bx + 48 * d, by)) {
        val alpha = if (light) 0.7f else 0.35f
        drawRoundRect(Color.White.copy(alpha = alpha), Offset(bx, by - 16 * d), Size(96 * d, 30 * d), androidx.compose.ui.geometry.CornerRadius(14 * d))
        drawRoundRect(ink.copy(alpha = alpha), Offset(bx, by - 16 * d), Size(96 * d, 30 * d), androidx.compose.ui.geometry.CornerRadius(14 * d), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f * d))
        val tail = Path().apply { moveTo(bx + 22 * d, by + 13 * d); lineTo(bx + 16 * d, by + 26 * d); lineTo(bx + 32 * d, by + 14 * d) }
        drawPath(tail, Color.White.copy(alpha = alpha))
        drawPath(tail, ink.copy(alpha = alpha), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f * d))
        val layout = measurer.measure("ВЖУХ!", androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, color = ink.copy(alpha = alpha)))
        drawText(layout, topLeft = Offset(bx + 48 * d - layout.size.width / 2f, by - 1 * d - layout.size.height / 2f))
    }
}

private fun DrawScope.newsExtras(light: Boolean) {
    val d = density
    val c = Offset(size.width * 0.78f, size.height * 0.82f)
    val stain = if (light) Color(0xFF6B4A24) else Color(0xFFB08A5A)
    drawCircle(stain.copy(alpha = 0.13f), 34 * d, c, style = androidx.compose.ui.graphics.drawscope.Stroke(5 * d))
    drawCircle(stain.copy(alpha = 0.08f), 30 * d, Offset(c.x + 4 * d, c.y - 2 * d), style = androidx.compose.ui.graphics.drawscope.Stroke(2 * d))
    drawCircle(stain.copy(alpha = 0.06f), 6 * d, Offset(c.x - 40 * d, c.y + 22 * d))
}

private fun DrawScope.seaExtras(light: Boolean) {
    val d = density
    val w = size.width
    val h = size.height
    val sandTop = h * 0.92f
    val sand = Path().apply {
        moveTo(0f, sandTop)
        quadraticTo(w * 0.2f, sandTop - 14 * d, w * 0.45f, sandTop - 2 * d)
        quadraticTo(w * 0.75f, sandTop + 8 * d, w, sandTop - 6 * d)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(sand, Color(0xFFC9B48A).copy(alpha = if (light) 0.5f else 0.3f))
    val ax = w * 0.8f
    val ay = h * 0.86f
    rotate(-20f, Offset(ax, ay)) {
        val st = androidx.compose.ui.graphics.drawscope.Stroke(4 * d, cap = StrokeCap.Round)
        val col = if (light) Color(0xFF6A8A8A).copy(alpha = 0.6f) else Color(0xFF4A6A70).copy(alpha = 0.7f)
        drawLine(col, Offset(ax, ay), Offset(ax, ay + 46 * d), 4 * d, cap = StrokeCap.Round)
        val p = Path().apply { moveTo(ax - 14 * d, ay + 36 * d); quadraticTo(ax, ay + 56 * d, ax + 14 * d, ay + 36 * d) }
        drawPath(p, col, style = st)
        drawLine(col, Offset(ax - 8 * d, ay + 6 * d), Offset(ax + 8 * d, ay + 6 * d), 4 * d, cap = StrokeCap.Round)
        drawCircle(col, 4 * d, Offset(ax, ay - 4 * d), style = androidx.compose.ui.graphics.drawscope.Stroke(3 * d))
    }
    for (k in 0 until 6) {
        drawOval(Color(0xFF8A8A7A).copy(alpha = 0.35f), Offset(w * (0.1f + k * 0.13f), sandTop + (6 + (k % 3) * 5) * d), Size((6 + k % 3 * 3) * d, 4 * d))
    }
}

private fun DrawScope.seaLife(t: Float, light: Boolean) {
    val d = density
    val w = size.width
    val h = size.height
    val span = w + 80 * d
    val fx = ((t * 14f * d) % span) - 40 * d
    val fy = h * 0.34f + sin(t * 0.8f) * 10 * d
    val fish = Color(0xFFFFB25E).copy(alpha = if (light) 0.7f else 0.55f)
    val body = Path().apply {
        moveTo(fx, fy); cubicTo(fx - 10 * d, fy - 8 * d, fx - 24 * d, fy - 8 * d, fx - 30 * d, fy)
        cubicTo(fx - 24 * d, fy + 8 * d, fx - 10 * d, fy + 8 * d, fx, fy); close()
        moveTo(fx - 30 * d, fy); lineTo(fx - 40 * d, fy - 7 * d); lineTo(fx - 40 * d, fy + 7 * d); close()
    }
    drawPath(body, fish)
    drawCircle(Color(0xFF032A38), 1.4f * d, Offset(fx - 7 * d, fy - 1 * d))
    val jx = w * 0.84f + sin(t * 0.3f) * 12 * d
    val jy = h * 0.5f + sin(t * 0.5f) * 16 * d
    val pulse = 1f + 0.08f * sin(t * 2.2f)
    val jelly = Color(0xFFC8A8FF).copy(alpha = if (light) 0.55f else 0.45f)
    val bell = Path().apply {
        moveTo(jx - 12 * d * pulse, jy); quadraticTo(jx - 12 * d * pulse, jy - 16 * d, jx, jy - 16 * d)
        quadraticTo(jx + 12 * d * pulse, jy - 16 * d, jx + 12 * d * pulse, jy); close()
    }
    drawPath(bell, jelly)
    for (k in 0 until 4) {
        val tx = jx + (-8 + k * 5) * d
        val p = Path().apply { moveTo(tx, jy); quadraticTo(tx + sin(t * 2f + k) * 4 * d, jy + 11 * d, tx, jy + 22 * d) }
        drawPath(p, jelly, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f * d))
    }
}

private fun DrawScope.clayExtras(light: Boolean, accent: Color) {
    val d = density
    val w = size.width
    val h = size.height
    val ring = (if (light) Color(0xFF6F6390) else Color(0xFFBDB3DE)).copy(alpha = 0.16f)
    listOf(Offset(w * 0.78f, h * 0.86f), Offset(w * 0.22f, h * 0.3f)).forEach { c ->
        for (k in 0 until 4) drawOval(ring, Offset(c.x - (4 + k * 3) * d, c.y - (5 + k * 3.5f) * d), Size((8 + k * 6) * d, (10 + k * 7) * d), style = androidx.compose.ui.graphics.drawscope.Stroke(1 * d))
    }
    val rope = Path().apply {
        var x = w * 0.45f
        val y = h * 0.94f
        moveTo(x, y)
        var up = true
        while (x < w * 0.8f) {
            quadraticTo(x + 5 * d, if (up) y - 10 * d else y + 10 * d, x + 10 * d, y)
            x += 10 * d
            up = !up
        }
    }
    drawPath(rope, accent.copy(alpha = if (light) 0.45f else 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(7 * d, cap = StrokeCap.Round))
}
