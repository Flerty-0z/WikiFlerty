package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val PropWidth = 60.dp
private val PropHeight = 76.dp

fun propCount(decor: Decor): Int = when (decor) {
    Decor.Library, Decor.Zen, Decor.Comic, Decor.News, Decor.Clay -> 3
    Decor.Sea -> 4
    Decor.None -> 0
}

@Composable
private fun propClock(): State<Float> = throttledClock(true, 600)

@Composable
fun ShelfProp(decor: Decor, index: Int, modifier: Modifier = Modifier) {
    val n = propCount(decor)
    if (n == 0) return
    val kind = ((index % n) + n) % n
    val time = propClock()
    val light = AppStyle.light
    val text = rememberTextMeasurer()
    Canvas(modifier.size(PropWidth, PropHeight)) {
        val t = time.value
        val d = density
        val w = size.width
        val h = size.height
        when (decor) {
            Decor.Library -> when (kind) {
                0 -> candle(t, d, w, h)
                1 -> hourglass(t, d, w, h)
                else -> inkwell(d, w, h)
            }
            Decor.Zen -> when (kind) {
                0 -> bonsai(d, w, h)
                1 -> stoneLantern(t, d, w, h)
                else -> teaCup(t, d, w, h, light)
            }
            Decor.Comic -> when (kind) {
                0 -> mascot(t, d, w, h)
                1 -> magazines(d, w, h, text)
                else -> bomb(t, d, w, h)
            }
            Decor.News -> when (kind) {
                0 -> coffee(t, d, w, h, light)
                1 -> glasses(d, w, h, light)
                else -> paper(d, w, h, light)
            }
            Decor.Sea -> when (kind) {
                0 -> shell(t, d, w, h)
                1 -> starfish(d, w, h)
                2 -> chest(t, d, w, h)
                else -> coral(t, d, w, h)
            }
            Decor.Clay -> when (kind) {
                0 -> snail(t, d, w, h)
                1 -> mushroom(d, w, h)
                else -> cactus(t, d, w, h)
            }
            Decor.None -> Unit
        }
    }
}

private fun DrawScope.glowAt(color: Color, alpha: Float, c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = c, radius = r), r, c)
}

private fun DrawScope.candle(t: Float, d: Float, w: Float, h: Float) {
    val cx = w / 2
    val base = h
    val flick = 0.85f + 0.1f * sin(t * 9.7f) + 0.05f * sin(t * 23.3f)
    glowAt(Color(0xFFFFC46B), 0.45f * flick, Offset(cx, base - 34 * d), 30 * d * flick)
    drawOval(Color(0xFFB8873F), Offset(cx - 16 * d, base - 6 * d), Size(32 * d, 6 * d))
    drawRect(Color(0xFF8A6A3A), Offset(cx - 3 * d, base - 10 * d), Size(6 * d, 6 * d))
    drawRoundRect(Color(0xFFF0E4C8), Offset(cx - 7 * d, base - 34 * d), Size(14 * d, 26 * d), CornerRadius(2 * d))
    drawRect(Color(0xFFD9CBAA), Offset(cx + 3 * d, base - 34 * d), Size(4 * d, 26 * d))
    drawLine(Color(0xFF3A2A12), Offset(cx, base - 34 * d), Offset(cx, base - 38 * d), 1.2f * d)
    val sway = sin(t * 6.1f) * 1.2f * d
    val flame = Path().apply {
        moveTo(cx + sway, base - 50 * d * flick)
        cubicTo(cx - 6 * d, base - 42 * d, cx - 4 * d, base - 37 * d, cx, base - 37 * d)
        cubicTo(cx + 4 * d, base - 37 * d, cx + 6 * d, base - 42 * d, cx + sway, base - 50 * d * flick)
    }
    drawPath(flame, Color(0xFFFFCC66))
    drawCircle(Color(0xFFFFF4CC), 2 * d, Offset(cx, base - 40 * d))
}

private fun DrawScope.hourglass(t: Float, d: Float, w: Float, h: Float) {
    val x = w / 2 - 13 * d
    val y = h - 44 * d
    val gold = Color(0xFFB8873F)
    drawRect(gold, Offset(x, y), Size(26 * d, 4 * d))
    drawRect(gold, Offset(x, y + 40 * d), Size(26 * d, 4 * d))
    drawRect(gold.copy(alpha = 0.8f), Offset(x + 1 * d, y + 4 * d), Size(2 * d, 36 * d))
    drawRect(gold.copy(alpha = 0.8f), Offset(x + 23 * d, y + 4 * d), Size(2 * d, 36 * d))
    val glass = Path().apply {
        moveTo(x + 4 * d, y + 4 * d); lineTo(x + 22 * d, y + 4 * d); lineTo(x + 14 * d, y + 22 * d)
        lineTo(x + 22 * d, y + 40 * d); lineTo(x + 4 * d, y + 40 * d); lineTo(x + 12 * d, y + 22 * d); close()
    }
    drawPath(glass, Color(0x33E9DCC0))
    drawPath(glass, Color(0xFFD8B46A), style = Stroke(1 * d))
    val k = (t * 0.02f) % 1f
    val top = Path().apply {
        moveTo(x + 6 * d + 6 * d * k, y + 8 * d + 12 * d * k); lineTo(x + 20 * d - 6 * d * k, y + 8 * d + 12 * d * k); lineTo(x + 13 * d, y + 21 * d); close()
    }
    drawPath(top, Color(0xFFE0B04A))
    val bottom = Path().apply {
        val hh = 4 * d + 12 * d * k
        moveTo(x + 6 * d, y + 40 * d); lineTo(x + 20 * d, y + 40 * d); lineTo(x + 13 * d, y + 40 * d - hh); close()
    }
    drawPath(bottom, Color(0xFFE0B04A))
    if (Motion.enabled) drawLine(Color(0xFFE0B04A), Offset(x + 13 * d, y + 21 * d), Offset(x + 13 * d, y + 38 * d), 1 * d)
}

private fun DrawScope.inkwell(d: Float, w: Float, h: Float) {
    val x = w / 2 - 14 * d
    val y = h - 24 * d
    val body = Path().apply {
        moveTo(x + 3 * d, y + 6 * d); lineTo(x + 25 * d, y + 6 * d); lineTo(x + 28 * d, y + 24 * d); lineTo(x, y + 24 * d); close()
    }
    drawPath(body, Color(0xFF1B1B2B))
    drawPath(body, Color(0xFFB8873F), style = Stroke(1.2f * d))
    drawRect(Color(0xFFB8873F), Offset(x + 8 * d, y), Size(12 * d, 7 * d))
    val quill = Path().apply {
        moveTo(x + 15 * d, y + 2 * d)
        cubicTo(x + 26 * d, y - 22 * d, x + 40 * d, y - 40 * d, x + 44 * d, y - 48 * d)
        cubicTo(x + 34 * d, y - 30 * d, x + 26 * d, y - 12 * d, x + 17 * d, y + 3 * d)
        close()
    }
    drawPath(quill, Color(0xFFEDE3CF))
    drawPath(quill, Color(0xFFB9A684), style = Stroke(0.8f * d))
    drawLine(Color(0xFFB9A684), Offset(x + 16 * d, y + 2 * d), Offset(x + 42 * d, y - 44 * d), 0.8f * d)
}

private fun DrawScope.bonsai(d: Float, w: Float, h: Float) {
    val x = w / 2 - 18 * d
    val y = h - 52 * d
    val pot = Path().apply { moveTo(x + 2 * d, y + 40 * d); lineTo(x + 34 * d, y + 40 * d); lineTo(x + 30 * d, y + 52 * d); lineTo(x + 6 * d, y + 52 * d); close() }
    drawPath(pot, Color(0xFF5A6A8A))
    drawRect(Color(0xFF4A5A78), Offset(x, y + 38 * d), Size(36 * d, 4 * d))
    val trunk = Path().apply { moveTo(x + 18 * d, y + 40 * d); cubicTo(x + 14 * d, y + 30 * d, x + 24 * d, y + 22 * d, x + 18 * d, y + 14 * d) }
    drawPath(trunk, Color(0xFF4A3A30), style = Stroke(3.5f * d, cap = StrokeCap.Round))
    drawOval(Color(0xFF6E8A48), Offset(x, y + 6 * d), Size(26 * d, 15 * d))
    drawOval(Color(0xFF7D9A52), Offset(x + 16 * d, y + 2 * d), Size(22 * d, 13 * d))
    drawOval(Color(0xFF8AAA5C), Offset(x + 8 * d, y - 4 * d), Size(18 * d, 11 * d))
}

private fun DrawScope.stoneLantern(t: Float, d: Float, w: Float, h: Float) {
    val x = w / 2 - 16 * d
    val y = h - 48 * d
    val glow = 0.3f + 0.08f * sin(t * 2.3f)
    glowAt(Color(0xFFFFD98A), glow, Offset(x + 16 * d, y + 22 * d), 26 * d)
    drawRect(Color(0xFFA8A294), Offset(x + 8 * d, y + 40 * d), Size(16 * d, 8 * d))
    drawRect(Color(0xFFB8B2A4), Offset(x + 11 * d, y + 28 * d), Size(10 * d, 12 * d))
    drawRect(Color(0xFFC8C2B4), Offset(x + 4 * d, y + 15 * d), Size(24 * d, 13 * d))
    drawRect(Color(0xFFFFD98A), Offset(x + 11 * d, y + 18 * d), Size(10 * d, 7 * d))
    val roof = Path().apply { moveTo(x, y + 15 * d); lineTo(x + 16 * d, y + 4 * d); lineTo(x + 32 * d, y + 15 * d); close() }
    drawPath(roof, Color(0xFF9A9484))
    drawCircle(Color(0xFF9A9484), 2.5f * d, Offset(x + 16 * d, y + 3 * d))
}

private fun DrawScope.steam(t: Float, d: Float, x: Float, y: Float, color: Color) {
    if (!Motion.enabled) return
    for (k in 0 until 2) {
        val phase = (t * 0.6f + k * 0.5f) % 1f
        val p = Path()
        val sx = x + k * 8 * d
        p.moveTo(sx, y)
        p.cubicTo(sx - 4 * d, y - 6 * d, sx + 4 * d, y - 10 * d, sx + sin(t * 2 + k) * 2 * d, y - 18 * d * (0.6f + 0.4f * phase))
        drawPath(p, color.copy(alpha = (1f - phase) * 0.7f), style = Stroke(1.4f * d, cap = StrokeCap.Round))
    }
}

private fun DrawScope.teaCup(t: Float, d: Float, w: Float, h: Float, light: Boolean) {
    val x = w / 2 - 15 * d
    val y = h - 24 * d
    val cup = Path().apply {
        moveTo(x, y + 4 * d); lineTo(x + 30 * d, y + 4 * d)
        cubicTo(x + 30 * d, y + 20 * d, x + 22 * d, y + 24 * d, x + 15 * d, y + 24 * d)
        cubicTo(x + 8 * d, y + 24 * d, x, y + 20 * d, x, y + 4 * d)
    }
    drawPath(cup, Color(0xFFE8E0D0))
    drawPath(cup, Color(0xFF8A7A5A), style = Stroke(1 * d))
    drawRect(Color(0xFF7D9A52).copy(alpha = 0.8f), Offset(x + 2 * d, y + 5 * d), Size(26 * d, 3 * d))
    steam(t, d, x + 10 * d, y, if (light) Color(0xFF9A9384) else Color(0xFFE8E0D0))
}

private fun DrawScope.mascot(t: Float, d: Float, w: Float, h: Float) {
    val x = w / 2 - 20 * d
    val y = h - 50 * d
    val ink = Color(0xFF111111)
    val hop = if (Motion.enabled) maxOf(0f, sin(t * 1.7f)).let { if (it > 0.97f) (it - 0.97f) * 120f else 0f } * d else 0f
    drawOval(ink.copy(alpha = 0.25f), Offset(x + 4 * d, h - 4 * d), Size(32 * d, 4 * d))
    translate(0f, -hop) {
        val body = Path().apply {
            moveTo(x + 4 * d, y + 46 * d); lineTo(x + 4 * d, y + 26 * d)
            cubicTo(x + 4 * d, y + 4 * d, x + 36 * d, y + 4 * d, x + 36 * d, y + 26 * d); lineTo(x + 36 * d, y + 46 * d); close()
        }
        drawPath(body, Color(0xFF7AD3FF))
        drawPath(body, ink, style = Stroke(2.5f * d))
        val blink = Motion.enabled && (t % 4f) > 3.85f
        for (ex in listOf(14f, 26f)) {
            if (blink) {
                drawLine(ink, Offset(x + (ex - 4) * d, y + 24 * d), Offset(x + (ex + 4) * d, y + 24 * d), 2 * d)
            } else {
                drawCircle(Color.White, 5 * d, Offset(x + ex * d, y + 24 * d))
                drawCircle(ink, 5 * d, Offset(x + ex * d, y + 24 * d), style = Stroke(2 * d))
                drawCircle(ink, 2 * d, Offset(x + (ex + 1) * d, y + 25 * d))
            }
        }
        val smile = Path().apply { moveTo(x + 14 * d, y + 34 * d); quadraticTo(x + 20 * d, y + 39 * d, x + 26 * d, y + 34 * d) }
        drawPath(smile, ink, style = Stroke(2 * d, cap = StrokeCap.Round))
        val horn = Path().apply { moveTo(x + 18 * d, y + 9 * d); lineTo(x + 17 * d, y); lineTo(x + 24 * d, y + 7 * d); close() }
        drawPath(horn, Color(0xFFFF3B3B))
        drawPath(horn, ink, style = Stroke(1.5f * d))
    }
}

private fun DrawScope.magazines(d: Float, w: Float, h: Float, text: androidx.compose.ui.text.TextMeasurer) {
    val x = w / 2 - 23 * d
    val ink = Color(0xFF111111)
    val colors = listOf(Color(0xFFFF3B3B), Color(0xFF7AD3FF), Color(0xFFFFCC00))
    colors.forEachIndexed { i, c ->
        val y = h - (11 + i * 10) * d
        val off = if (i == 1) 3 * d else 0f
        drawRect(c, Offset(x + off, y), Size(44 * d, 10 * d))
        drawRect(ink, Offset(x + off, y), Size(44 * d, 10 * d), style = Stroke(2 * d))
    }
    val c = Offset(x + 40 * d, h - 38 * d)
    rotate(15f, c) {
        drawCircle(Color.White, 9 * d, c)
        drawCircle(ink, 9 * d, c, style = Stroke(2 * d))
        val layout = text.measure("5¢", TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF3B3B)))
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
    }
}

private fun DrawScope.bomb(t: Float, d: Float, w: Float, h: Float) {
    val ink = Color(0xFF111111)
    val c = Offset(w / 2 - 2 * d, h - 15 * d)
    drawCircle(Color(0xFF222222), 14 * d, c)
    drawCircle(ink, 14 * d, c, style = Stroke(2.5f * d))
    drawCircle(Color.White.copy(alpha = 0.55f), 3 * d, Offset(c.x - 5 * d, c.y - 5 * d))
    drawRect(Color(0xFF555555), Offset(c.x - 4 * d, c.y - 20 * d), Size(8 * d, 6 * d))
    drawRect(ink, Offset(c.x - 4 * d, c.y - 20 * d), Size(8 * d, 6 * d), style = Stroke(2 * d))
    val fuse = Path().apply { moveTo(c.x, c.y - 20 * d); cubicTo(c.x + 2 * d, c.y - 28 * d, c.x + 10 * d, c.y - 28 * d, c.x + 12 * d, c.y - 34 * d) }
    drawPath(fuse, Color(0xFF8A5A1C), style = Stroke(2 * d))
    val sp = Offset(c.x + 12 * d, c.y - 35 * d)
    val pulse = if (Motion.enabled) 0.7f + 0.3f * sin(t * 18f) else 1f
    for (k in 0 until 8) {
        val a = PI.toFloat() * k / 4 + t * 3f
        val r1 = 2 * d
        val r2 = (if (k % 2 == 0) 8f else 5f) * d * pulse
        drawLine(if (k % 2 == 0) Color(0xFFFFCC00) else Color(0xFFFF3B3B), Offset(sp.x + cos(a) * r1, sp.y + sin(a) * r1), Offset(sp.x + cos(a) * r2, sp.y + sin(a) * r2), 1.6f * d, cap = StrokeCap.Round)
    }
}

private fun DrawScope.coffee(t: Float, d: Float, w: Float, h: Float, light: Boolean) {
    val x = w / 2 - 18 * d
    val ink = if (light) Color(0xFF141414) else Color(0xFFECE6D8)
    drawOval(if (light) Color(0xFFD8D0BC) else Color(0xFF3A3A3A), Offset(x - 2 * d, h - 7 * d), Size(40 * d, 7 * d))
    drawOval(ink, Offset(x - 2 * d, h - 7 * d), Size(40 * d, 7 * d), style = Stroke(1.2f * d))
    val y = h - 30 * d
    val cup = Path().apply {
        moveTo(x + 4 * d, y + 4 * d); lineTo(x + 32 * d, y + 4 * d); lineTo(x + 32 * d, y + 16 * d)
        cubicTo(x + 32 * d, y + 24 * d, x + 26 * d, y + 26 * d, x + 18 * d, y + 26 * d)
        cubicTo(x + 10 * d, y + 26 * d, x + 4 * d, y + 24 * d, x + 4 * d, y + 16 * d); close()
    }
    drawPath(cup, if (light) Color.White else Color(0xFF2A2A2A))
    drawPath(cup, ink, style = Stroke(1.4f * d))
    val handle = Path().apply { moveTo(x + 32 * d, y + 8 * d); cubicTo(x + 41 * d, y + 8 * d, x + 41 * d, y + 19 * d, x + 32 * d, y + 19 * d) }
    drawPath(handle, ink, style = Stroke(1.4f * d))
    drawOval(Color(0xFF5A3A20), Offset(x + 5 * d, y + 2 * d), Size(26 * d, 5 * d))
    steam(t, d, x + 13 * d, y - 1 * d, ink.copy(alpha = 0.6f))
}

private fun DrawScope.glasses(d: Float, w: Float, h: Float, light: Boolean) {
    val ink = if (light) Color(0xFF141414) else Color(0xFFECE6D8)
    val y = h - 10 * d
    val cx = w / 2
    drawCircle(ink, 9 * d, Offset(cx - 11 * d, y), style = Stroke(2 * d))
    drawCircle(ink, 9 * d, Offset(cx + 11 * d, y), style = Stroke(2 * d))
    drawCircle(Color.White.copy(alpha = 0.12f), 8 * d, Offset(cx - 11 * d, y))
    drawCircle(Color.White.copy(alpha = 0.12f), 8 * d, Offset(cx + 11 * d, y))
    val bridge = Path().apply { moveTo(cx - 2.5f * d, y - 1 * d); quadraticTo(cx, y - 4 * d, cx + 2.5f * d, y - 1 * d) }
    drawPath(bridge, ink, style = Stroke(2 * d))
    drawLine(ink, Offset(cx - 20 * d, y - 2 * d), Offset(cx - 27 * d, y - 7 * d), 1.6f * d)
    drawLine(ink, Offset(cx + 20 * d, y - 2 * d), Offset(cx + 27 * d, y - 7 * d), 1.6f * d)
}

private fun DrawScope.paper(d: Float, w: Float, h: Float, light: Boolean) {
    val ink = if (light) Color(0xFF141414) else Color(0xFFECE6D8)
    val bg = if (light) Color(0xFFFBF8F1) else Color(0xFF242424)
    val pw = 54 * d
    val ph = 30 * d
    val tl = Offset(w / 2 - pw / 2, h - ph - 2 * d)
    rotate(-6f, Offset(w / 2, h - ph / 2)) {
        drawRect(bg, tl, Size(pw, ph))
        drawRect(ink, tl, Size(pw, ph), style = Stroke(1.2f * d))
        drawRect(ink, Offset(tl.x + 4 * d, tl.y + 4 * d), Size(pw - 8 * d, 5 * d))
        for (c in 0 until 2) for (r in 0 until 4) {
            drawRect(ink.copy(alpha = 0.5f), Offset(tl.x + 4 * d + c * 24 * d, tl.y + 12 * d + r * 4 * d), Size(21 * d, 1.6f * d))
        }
    }
}

private fun DrawScope.shell(t: Float, d: Float, w: Float, h: Float) {
    val x = w / 2 - 20 * d
    val y = h - 30 * d
    val base = Path().apply { moveTo(x, y + 28 * d); cubicTo(x, y + 10 * d, x + 40 * d, y + 10 * d, x + 40 * d, y + 28 * d); close() }
    drawPath(base, Color(0xFFF4C6D6))
    drawPath(base, Color(0xFFC88AA0), style = Stroke(1 * d))
    val open = if (Motion.enabled) 1f + 0.12f * maxOf(0f, sin(t * 0.8f)) else 1f
    val lid = Path().apply {
        moveTo(x + 2 * d, y + 28 * d)
        cubicTo(x + 4 * d, y + 4 * d / open, x + 36 * d, y + 2 * d / open, x + 38 * d, y + 16 * d)
    }
    drawPath(lid, Color(0xFFF8D6E2))
    drawPath(lid, Color(0xFFC88AA0), style = Stroke(1 * d))
    for (k in 0 until 5) drawLine(Color(0xFFC88AA0), Offset(x + 20 * d, y + 28 * d), Offset(x + (6 + k * 7) * d, y + 14 * d), 0.8f * d)
    glowAt(Color.White, 0.5f, Offset(x + 20 * d, y + 22 * d), 9 * d)
    drawCircle(Color(0xFFFFFAF0), 5 * d, Offset(x + 20 * d, y + 22 * d))
    drawCircle(Color.White, 1.6f * d, Offset(x + 18 * d, y + 20 * d))
}

private fun DrawScope.starfish(d: Float, w: Float, h: Float) {
    val c = Offset(w / 2, h - 16 * d)
    val p = Path()
    for (k in 0 until 10) {
        val r = (if (k % 2 == 0) 17f else 7f) * d
        val a = PI.toFloat() * k / 5 - PI.toFloat() / 2
        val pt = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
        if (k == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    p.close()
    drawPath(p, Color(0xFFFF8A5C))
    drawPath(p, Color(0xFFD0603A), style = Stroke(1 * d))
    drawCircle(Color(0xFFFFD0B0), 2.2f * d, c)
    for (k in 0 until 5) {
        val a = PI.toFloat() * 2 * k / 5 - PI.toFloat() / 2
        drawCircle(Color(0xFFFFD0B0), 1.2f * d, Offset(c.x + cos(a) * 9 * d, c.y + sin(a) * 9 * d))
    }
}

private fun DrawScope.chest(t: Float, d: Float, w: Float, h: Float) {
    val x = w / 2 - 21 * d
    val y = h - 32 * d
    val gold = Color(0xFFD8B46A)
    drawRect(Color(0xFF7A4A24), Offset(x, y + 13 * d), Size(42 * d, 19 * d))
    drawRect(gold, Offset(x, y + 13 * d), Size(42 * d, 19 * d), style = Stroke(1.4f * d))
    val lid = Path().apply { moveTo(x, y + 13 * d); cubicTo(x, y, x + 42 * d, y, x + 42 * d, y + 13 * d); close() }
    drawPath(lid, Color(0xFF8A5A30))
    drawPath(lid, gold, style = Stroke(1.4f * d))
    drawRect(gold, Offset(x + 18 * d, y + 10 * d), Size(6 * d, 9 * d))
    val tw = if (Motion.enabled) 0.5f + 0.5f * sin(t * 3.1f) else 0.7f
    drawCircle(Color(0xFFFFE9A8).copy(alpha = tw), 2.2f * d, Offset(x + 30 * d, y + 2 * d))
    drawCircle(Color(0xFFFFE9A8).copy(alpha = 1f - tw * 0.6f), 1.6f * d, Offset(x + 10 * d, y + 5 * d))
}

private fun DrawScope.coral(t: Float, d: Float, w: Float, h: Float) {
    val c = Color(0xFFFF6B8A)
    val x = w / 2 - 14 * d
    val sway = if (Motion.enabled) sin(t * 1.1f) * 2 * d else 0f
    val s = Stroke(4 * d, cap = StrokeCap.Round)
    fun branch(sx: Float, sy: Float, ex: Float, ey: Float) {
        val p = Path().apply { moveTo(sx, sy); quadraticTo((sx + ex) / 2 + sway / 2, (sy + ey) / 2, ex + sway, ey) }
        drawPath(p, c, style = s)
    }
    branch(x + 14 * d, h, x + 14 * d, h - 26 * d)
    branch(x + 14 * d, h - 14 * d, x + 2 * d, h - 38 * d)
    branch(x + 14 * d, h - 20 * d, x + 28 * d, h - 44 * d)
    branch(x + 8 * d, h - 26 * d, x + 8 * d, h - 46 * d)
    drawOval(Color(0xFFE85A78), Offset(x + 4 * d, h - 4 * d), Size(20 * d, 4 * d))
}

private val Shine = listOf(Color.White.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.12f))

private fun DrawScope.clayBlob(color: Color, tl: Offset, sz: Size) {
    drawOval(color, tl, sz)
    drawOval(Brush.linearGradient(Shine, start = tl, end = Offset(tl.x + sz.width, tl.y + sz.height)), tl, sz)
}

private fun DrawScope.clayEyes(t: Float, d: Float, l: Offset, r: Offset) {
    val ink = Color(0xFF3B2F5C)
    val blink = Motion.enabled && (t % 5f) > 4.85f
    for (e in listOf(l, r)) {
        if (blink) drawLine(ink, Offset(e.x - 2 * d, e.y), Offset(e.x + 2 * d, e.y), 1.6f * d, cap = StrokeCap.Round)
        else drawCircle(ink, 2 * d, e)
    }
}

private fun DrawScope.snail(t: Float, d: Float, w: Float, h: Float) {
    val x = w / 2 - 26 * d
    val crawl = if (Motion.enabled) sin(t * 0.7f) * 1.5f * d else 0f
    translate(crawl, 0f) {
        val body = Path().apply {
            moveTo(x, h); lineTo(x + 46 * d, h)
            cubicTo(x + 46 * d, h - 6 * d, x + 42 * d, h - 9 * d, x + 36 * d, h - 9 * d)
            lineTo(x + 10 * d, h - 9 * d); cubicTo(x + 4 * d, h - 9 * d, x, h - 6 * d, x, h); close()
        }
        drawPath(body, Color(0xFFB9F0C4))
        drawPath(body, Brush.verticalGradient(Shine, startY = h - 9 * d, endY = h))
        clayBlob(Color(0xFFFFD27A), Offset(x + 10 * d, h - 36 * d), Size(28 * d, 28 * d))
        val sp = Path().apply {
            val c = Offset(x + 24 * d, h - 22 * d)
            for (k in 0..30) {
                val a = k * 0.42f
                val r = (1 + k * 0.32f) * d
                val px = c.x + cos(a) * r
                val py = c.y + sin(a) * r
                if (k == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        drawPath(sp, Color(0xFFE0A84A), style = Stroke(2 * d, cap = StrokeCap.Round))
        drawLine(Color(0xFF8AD09A), Offset(x + 42 * d, h - 8 * d), Offset(x + 44 * d, h - 22 * d), 2.5f * d, cap = StrokeCap.Round)
        drawLine(Color(0xFF8AD09A), Offset(x + 45 * d, h - 8 * d), Offset(x + 51 * d, h - 20 * d), 2.5f * d, cap = StrokeCap.Round)
        clayEyes(t, d, Offset(x + 44 * d, h - 23 * d), Offset(x + 51 * d, h - 21 * d))
    }
}

private fun DrawScope.mushroom(d: Float, w: Float, h: Float) {
    val x = w / 2 - 17 * d
    drawRoundRect(Color(0xFFFBF8FF), Offset(x + 11 * d, h - 20 * d), Size(12 * d, 20 * d), CornerRadius(5 * d))
    val cap = Path().apply { moveTo(x, h - 18 * d); cubicTo(x, h - 36 * d, x + 34 * d, h - 36 * d, x + 34 * d, h - 18 * d); close() }
    drawPath(cap, Color(0xFFFF7A7A))
    drawPath(cap, Brush.linearGradient(Shine, start = Offset(x, h - 36 * d), end = Offset(x + 34 * d, h - 18 * d)))
    drawCircle(Color.White, 3 * d, Offset(x + 10 * d, h - 26 * d))
    drawCircle(Color.White, 2.5f * d, Offset(x + 22 * d, h - 29 * d))
    drawCircle(Color.White, 2 * d, Offset(x + 27 * d, h - 22 * d))
}

private fun DrawScope.cactus(t: Float, d: Float, w: Float, h: Float) {
    val x = w / 2 - 18 * d
    val y = h - 52 * d
    val pot = Path().apply { moveTo(x + 4 * d, y + 36 * d); lineTo(x + 32 * d, y + 36 * d); lineTo(x + 29 * d, y + 52 * d); lineTo(x + 7 * d, y + 52 * d); close() }
    drawPath(pot, Color(0xFFFFC89A))
    drawPath(pot, Brush.verticalGradient(Shine, startY = y + 36 * d, endY = y + 52 * d))
    val green = Color(0xFF8AD09A)
    drawRoundRect(green, Offset(x + 2 * d, y + 16 * d), Size(9 * d, 14 * d), CornerRadius(4.5f * d))
    drawRoundRect(green, Offset(x + 25 * d, y + 12 * d), Size(9 * d, 14 * d), CornerRadius(4.5f * d))
    drawRoundRect(green, Offset(x + 11 * d, y + 6 * d), Size(14 * d, 32 * d), CornerRadius(7 * d))
    drawRoundRect(Brush.linearGradient(Shine, start = Offset(x + 11 * d, y + 6 * d), end = Offset(x + 25 * d, y + 38 * d)), Offset(x + 11 * d, y + 6 * d), Size(14 * d, 32 * d), CornerRadius(7 * d))
    drawCircle(Color(0xFFFF8FB6), 4 * d, Offset(x + 18 * d, y + 5 * d))
    clayEyes(t, d, Offset(x + 15 * d, y + 19 * d), Offset(x + 21 * d, y + 19 * d))
    val smile = Path().apply { moveTo(x + 15 * d, y + 24 * d); quadraticTo(x + 18 * d, y + 27 * d, x + 21 * d, y + 24 * d) }
    drawPath(smile, Color(0xFF3B2F5C), style = Stroke(1.2f * d, cap = StrokeCap.Round))
}

fun Modifier.kitFrame(): Modifier = when (AppStyle.kit.decor) {
    Decor.Library -> this.drawWithContent {
        drawContent()
        val gold = Color(0xFFE0B04A).copy(alpha = if (AppStyle.light) 0.75f else 0.6f)
        val a = 14.dp.toPx()
        val m = 5.dp.toPx()
        val s = Stroke(1.4f * density)
        val corners = listOf(Offset(m, m) to (1f to 1f), Offset(size.width - m, m) to (-1f to 1f), Offset(m, size.height - m) to (1f to -1f), Offset(size.width - m, size.height - m) to (-1f to -1f))
        corners.forEach { (p, dir) ->
            val path = Path().apply { moveTo(p.x, p.y + a * dir.second); lineTo(p.x, p.y); lineTo(p.x + a * dir.first, p.y) }
            drawPath(path, gold, style = s)
            drawCircle(gold, 2 * density, p)
        }
    }
    else -> this
}

@Composable
fun KitDivider() {
    val decor = AppStyle.kit.decor
    val line = AppColors.PanelBorder
    val accent = AppColors.Accent
    val ink = AppColors.Text
    Canvas(Modifier.fillMaxWidth().height(if (decor == Decor.None) 1.dp else 8.dp)) {
        val w = size.width
        val cy = size.height / 2
        when (decor) {
            Decor.Library -> {
                val gold = Color(0xFFE0B04A).copy(alpha = 0.7f)
                drawLine(line, Offset(0f, cy), Offset(w, cy), 1f * density)
                drawLine(gold, Offset(w * 0.3f, cy), Offset(w * 0.7f, cy), 1f * density)
                val dm = Path().apply { moveTo(w / 2, cy - 3.5f * density); lineTo(w / 2 + 3.5f * density, cy); lineTo(w / 2, cy + 3.5f * density); lineTo(w / 2 - 3.5f * density, cy); close() }
                drawPath(dm, gold)
            }
            Decor.Zen -> {
                val p = Path().apply {
                    moveTo(w * 0.06f, cy)
                    cubicTo(w * 0.3f, cy - 2f * density, w * 0.6f, cy + 2f * density, w * 0.94f, cy - 1f * density)
                    lineTo(w * 0.94f, cy)
                    cubicTo(w * 0.6f, cy + 3.2f * density, w * 0.3f, cy - 0.4f * density, w * 0.06f, cy + 1.2f * density)
                    close()
                }
                drawPath(p, ink.copy(alpha = 0.55f))
            }
            Decor.Comic -> drawRect(ink, Offset(0f, cy - 1.25f * density), Size(w, 2.5f * density))
            Decor.News -> {
                drawRect(ink, Offset(0f, cy - 2.5f * density), Size(w, 2 * density))
                drawRect(ink, Offset(0f, cy + 1.5f * density), Size(w, 1 * density))
            }
            Decor.Sea -> {
                val p = Path()
                var x = 0f
                p.moveTo(0f, cy)
                val step = 14 * density
                var up = true
                while (x < w) {
                    p.quadraticTo(x + step / 2, if (up) cy - 3 * density else cy + 3 * density, x + step, cy)
                    x += step
                    up = !up
                }
                drawPath(p, accent.copy(alpha = 0.55f), style = Stroke(1.4f * density))
            }
            Decor.Clay -> {
                val p = Path()
                var x = 6 * density
                p.moveTo(x, cy)
                val step = 12 * density
                var up = true
                while (x < w - 6 * density) {
                    p.quadraticTo(x + step / 2, if (up) cy - 2.5f * density else cy + 2.5f * density, x + step, cy)
                    x += step
                    up = !up
                }
                drawPath(p, accent.copy(alpha = 0.75f), style = Stroke(3.5f * density, cap = StrokeCap.Round))
            }
            Decor.None -> drawRect(line)
        }
    }
}

@Composable
fun KitSeal(modifier: Modifier = Modifier) {
    if (AppStyle.kit.decor != Decor.Zen) return
    val text = rememberTextMeasurer()
    Box(modifier.size(26.dp)) {
        Canvas(Modifier.size(26.dp)) {
            drawRoundRect(Color(0xFFC0392B), cornerRadius = CornerRadius(4 * density))
            drawRoundRect(Color.White.copy(alpha = 0.25f), topLeft = Offset(2 * density, 2 * density), size = Size(size.width - 4 * density, size.height - 4 * density), cornerRadius = CornerRadius(3 * density), style = Stroke(0.8f * density))
            val layout = text.measure("読", TextStyle(fontSize = 13.sp, color = Color(0xFFFBF8F1)))
            drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f))
        }
    }
}
