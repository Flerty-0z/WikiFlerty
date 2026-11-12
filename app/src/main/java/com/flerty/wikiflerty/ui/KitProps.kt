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
import androidx.compose.ui.graphics.drawscope.scale
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
    Decor.Sea, Decor.Frost -> 4
    Decor.None -> 0
}

@Composable
private fun propClock(): State<Float> = throttledClock(true, 600, shelf = true)

@Composable
fun ShelfProp(decor: Decor, index: Int, modifier: Modifier = Modifier) {
    val n = propCount(decor)
    if (n == 0) return
    val kind = ((index % n) + n) % n
    val time = propClock()
    val light = AppStyle.light
    val text = rememberTextMeasurer()
    val shelf = LocalShelfState.current
    val life = shelf.life
    val role = propRole(decor, kind)
    val stage = life.stage(role)
    val view = androidx.compose.ui.platform.LocalView.current
    val growth = shelf.growth
    if (growth != null) {
        androidx.compose.runtime.LaunchedEffect(role, stage) {
            growth.check(role, stage) { view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM) }
        }
    }
    val accent = AppColors.Accent
    Canvas(modifier.size(PropWidth, PropHeight)) {
        val t = time.value
        val d = density
        val w = size.width
        val h = size.height
        val g = growth?.value(role) ?: 1f
        val growing = g < 1f
        val frost = role == PropRole.Streak && life.frozen
        val sad = role == PropRole.Streak && life.sad && !life.frozen
        if (growing) growGlow(g, d, w, h, accent)
        val pop = if (growing) 1f + 0.16f * sin(PI.toFloat() * g) * (1f - g * 0.4f) else 1f
        scale(pop, pop, Offset(w / 2, h)) {
            val layered = frost || sad
            if (layered) drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(-w, -h, w * 2, h * 2), androidx.compose.ui.graphics.Paint())
            when (decor) {
                Decor.Library -> when (kind) {
                    0 -> candle(t, d, w, h, life.today)
                    1 -> hourglass(t, d, w, h, stage)
                    else -> inkwell(t, d, w, h, stage)
                }
                Decor.Zen -> when (kind) {
                    0 -> bonsai(t, d, w, h, stage, sad)
                    1 -> stoneLantern(t, d, w, h, life.today)
                    else -> teaCup(t, d, w, h, light, stage)
                }
                Decor.Comic -> when (kind) {
                    0 -> mascot(t, d, w, h, stage, life.today)
                    1 -> magazines(d, w, h, text, stage)
                    else -> bomb(t, d, w, h, life.today, life.evening)
                }
                Decor.News -> when (kind) {
                    0 -> coffee(t, d, w, h, light, life.today)
                    1 -> glasses(t, d, w, h, light, stage)
                    else -> paper(d, w, h, light, stage)
                }
                Decor.Sea -> when (kind) {
                    0 -> shell(t, d, w, h, stage)
                    1 -> starfish(t, d, w, h, life.today)
                    2 -> chest(t, d, w, h, stage)
                    else -> coral(t, d, w, h, stage)
                }
                Decor.Clay -> when (kind) {
                    0 -> snail(t, d, w, h, life.today)
                    1 -> mushroom(d, w, h, stage)
                    else -> cactus(t, d, w, h, stage, sad)
                }
                Decor.Frost -> when (kind) {
                    0 -> xmasTree(t, d, w, h, stage, sad)
                    1 -> snowman(t, d, w, h, life.today, accent)
                    2 -> gifts(d, w, h, stage)
                    else -> cocoa(t, d, w, h, life.today)
                }
                Decor.None -> Unit
            }
            if (layered) {
                if (frost) drawRect(Color(0xFFCFEFFF).copy(alpha = 0.5f), Offset(-w, -h), Size(w * 3, h * 3), blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                if (sad) drawRect(Color(0xFF8C8A86).copy(alpha = 0.42f), Offset(-w, -h), Size(w * 3, h * 3), blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                drawContext.canvas.restore()
            }
            if (frost) frostDecor(t, d, w, h)
        }
        if (growing) growSparks(g, d, w, h, accent)
    }
}

private fun propRole(decor: Decor, kind: Int): PropRole = when (decor) {
    Decor.Library -> listOf(PropRole.Today, PropRole.Streak, PropRole.Books)[kind.coerceIn(0, 2)]
    Decor.Zen -> listOf(PropRole.Streak, PropRole.Today, PropRole.Books)[kind.coerceIn(0, 2)]
    Decor.Comic -> listOf(PropRole.Streak, PropRole.Books, PropRole.Today)[kind.coerceIn(0, 2)]
    Decor.News -> listOf(PropRole.Today, PropRole.Streak, PropRole.Books)[kind.coerceIn(0, 2)]
    Decor.Sea -> listOf(PropRole.Streak, PropRole.Today, PropRole.Books, PropRole.Streak)[kind.coerceIn(0, 3)]
    Decor.Clay -> listOf(PropRole.Today, PropRole.Books, PropRole.Streak)[kind.coerceIn(0, 2)]
    Decor.Frost -> listOf(PropRole.Streak, PropRole.Today, PropRole.Books, PropRole.Today)[kind.coerceIn(0, 3)]
    Decor.None -> PropRole.Today
}

private fun DrawScope.growGlow(g: Float, d: Float, w: Float, h: Float, accent: Color) {
    val a = sin(PI.toFloat() * g).coerceAtLeast(0f)
    glowAt(accent, 0.55f * a, Offset(w / 2, h - 30 * d), (34 + 18 * g) * d)
    glowAt(Color.White, 0.35f * a * (1f - g), Offset(w / 2, h - 30 * d), 20 * d)
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.growSparks(g: Float, d: Float, w: Float, h: Float, accent: Color) {
    val c = Offset(w / 2, h - 32 * d)
    val fade = (1f - g).coerceIn(0f, 1f)
    for (k in 0 until 10) {
        val a = PI.toFloat() * 2f * k / 10f + k * 0.37f
        val dist = (10 + 36 * g + (k % 3) * 6) * d
        val p = Offset(c.x + cos(a) * dist, c.y + sin(a) * dist * 0.85f - 10 * d * g)
        val r = (if (k % 2 == 0) 4.2f else 2.8f) * d * (0.5f + fade)
        sparkle(p, r, (if (k % 3 == 0) Color.White else accent).copy(alpha = fade))
    }
}

private fun DrawScope.snowflake(c: Offset, r: Float, color: Color) {
    for (k in 0 until 3) {
        val a = PI.toFloat() * k / 3f
        drawLine(color, Offset(c.x - cos(a) * r, c.y - sin(a) * r), Offset(c.x + cos(a) * r, c.y + sin(a) * r), 0.9f * density, cap = StrokeCap.Round)
    }
}

private fun DrawScope.frostDecor(t: Float, d: Float, w: Float, h: Float) {
    val ice = Color(0xFFE6F7FF)
    val spots = listOf(Offset(0.2f, 0.32f), Offset(0.78f, 0.24f), Offset(0.62f, 0.55f), Offset(0.3f, 0.7f), Offset(0.86f, 0.78f))
    spots.forEachIndexed { i, s ->
        val tw = if (Motion.enabled) 0.55f + 0.45f * sin(t * 1.6f + i * 1.3f) else 0.8f
        snowflake(Offset(w * s.x, h * s.y), (3f + (i % 2) * 1.5f) * d, ice.copy(alpha = 0.9f * tw))
    }
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x33BFE8FF)), startY = h - 14 * d, endY = h), Offset(0f, h - 14 * d), Size(w, 14 * d))
}

private fun DrawScope.glowAt(color: Color, alpha: Float, c: Offset, r: Float) {
    if (alpha <= 0f || r <= 0f) return
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = c, radius = r), r, c)
}

private fun DrawScope.smoke(t: Float, d: Float, x: Float, y: Float, color: Color) {
    if (!Motion.enabled) {
        drawLine(color.copy(alpha = 0.4f), Offset(x, y), Offset(x + 2 * d, y - 10 * d), 1.2f * d, cap = StrokeCap.Round)
        return
    }
    val phase = (t * 0.35f) % 1f
    val p = Path().apply {
        moveTo(x, y)
        cubicTo(x - 5 * d, y - 6 * d, x + 5 * d, y - 12 * d, x + sin(t * 1.3f) * 3 * d, y - 22 * d * (0.5f + 0.5f * phase))
    }
    drawPath(p, color.copy(alpha = (1f - phase) * 0.55f), style = Stroke(1.6f * d, cap = StrokeCap.Round))
}

private fun DrawScope.sleepZ(t: Float, d: Float, x: Float, y: Float, color: Color) {
    for (k in 0 until 2) {
        val phase = if (Motion.enabled) ((t * 0.45f + k * 0.5f) % 1f) else k * 0.5f
        val s = (3f + k * 1.2f) * d
        val cx = x + k * 6 * d + phase * 3 * d
        val cy = y - phase * 14 * d - k * 4 * d
        val col = color.copy(alpha = (1f - phase) * 0.9f)
        val z = Path().apply {
            moveTo(cx - s, cy - s); lineTo(cx + s, cy - s); lineTo(cx - s, cy + s); lineTo(cx + s, cy + s)
        }
        drawPath(z, col, style = Stroke(1.3f * d, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

private fun DrawScope.candle(t: Float, d: Float, w: Float, h: Float, lit: Boolean) {
    val cx = w / 2
    val base = h
    val flick = 0.85f + 0.1f * sin(t * 9.7f) + 0.05f * sin(t * 23.3f)
    if (lit) glowAt(Color(0xFFFFC46B), 0.45f * flick, Offset(cx, base - 34 * d), 30 * d * flick)
    drawOval(Color(0xFFB8873F), Offset(cx - 16 * d, base - 6 * d), Size(32 * d, 6 * d))
    drawRect(Color(0xFF8A6A3A), Offset(cx - 3 * d, base - 10 * d), Size(6 * d, 6 * d))
    drawRoundRect(Color(0xFFF0E4C8), Offset(cx - 7 * d, base - 34 * d), Size(14 * d, 26 * d), CornerRadius(2 * d))
    drawRect(Color(0xFFD9CBAA), Offset(cx + 3 * d, base - 34 * d), Size(4 * d, 26 * d))
    drawRoundRect(Color(0xFFF7EEDA), Offset(cx - 5 * d, base - 34 * d), Size(3 * d, 9 * d), CornerRadius(1.5f * d))
    drawLine(Color(0xFF3A2A12), Offset(cx, base - 34 * d), Offset(cx, base - 38 * d), 1.2f * d)
    if (!lit) {
        drawCircle(Color(0xFF6A3A1A), 1 * d, Offset(cx, base - 38 * d))
        smoke(t, d, cx, base - 39 * d, Color(0xFFB0A898))
        return
    }
    val sway = sin(t * 6.1f) * 1.2f * d
    val flame = Path().apply {
        moveTo(cx + sway, base - 50 * d * flick)
        cubicTo(cx - 6 * d, base - 42 * d, cx - 4 * d, base - 37 * d, cx, base - 37 * d)
        cubicTo(cx + 4 * d, base - 37 * d, cx + 6 * d, base - 42 * d, cx + sway, base - 50 * d * flick)
    }
    drawPath(flame, Color(0xFFFFCC66))
    drawCircle(Color(0xFFFFF4CC), 2 * d, Offset(cx, base - 40 * d))
}

private fun DrawScope.hourglass(t: Float, d: Float, w: Float, h: Float, stage: Int) {
    val x = w / 2 - 13 * d
    val y = h - 44 * d
    val f = stage / 5f
    val gold = Color(0xFFB8873F)
    val bright = Color(0xFFE8C46A)
    if (stage >= 4) glowAt(Color(0xFFFFD36A), if (stage >= 5) 0.42f + 0.08f * sin(t * 2f) else 0.3f, Offset(w / 2, y + 22 * d), 30 * d)
    val frame = if (stage >= 3) bright else gold
    val post = if (stage >= 3) 3 * d else 2 * d
    drawRect(frame, Offset(x - (if (stage >= 3) 1 * d else 0f), y), Size(26 * d + (if (stage >= 3) 2 * d else 0f), 4 * d))
    drawRect(frame, Offset(x - (if (stage >= 3) 1 * d else 0f), y + 40 * d), Size(26 * d + (if (stage >= 3) 2 * d else 0f), 4 * d))
    drawRect(frame.copy(alpha = 0.85f), Offset(x + 1 * d, y + 4 * d), Size(post, 36 * d))
    drawRect(frame.copy(alpha = 0.85f), Offset(x + 25 * d - post, y + 4 * d), Size(post, 36 * d))
    if (stage >= 3) {
        drawCircle(frame, 2 * d, Offset(x + 2 * d, y - 1 * d))
        drawCircle(frame, 2 * d, Offset(x + 24 * d, y - 1 * d))
    }
    val glass = Path().apply {
        moveTo(x + 4 * d, y + 4 * d); lineTo(x + 22 * d, y + 4 * d); lineTo(x + 14 * d, y + 22 * d)
        lineTo(x + 22 * d, y + 40 * d); lineTo(x + 4 * d, y + 40 * d); lineTo(x + 12 * d, y + 22 * d); close()
    }
    drawPath(glass, Color(0x33E9DCC0))
    val sand = androidx.compose.ui.graphics.lerp(Color(0xFFC9A66B), Color(0xFFFFD36A), f)
    val cx = x + 13 * d
    val topFill = 1f - f * 0.85f
    val level = y + 21 * d - 15 * d * topFill
    val half = { yy: Float -> 9 * d - 8 * d * ((yy - (y + 4 * d)) / (18 * d)) }
    if (topFill > 0.05f) {
        val top = Path().apply {
            moveTo(cx - half(level), level); lineTo(cx + half(level), level); lineTo(cx, y + 21 * d); close()
        }
        drawPath(top, sand)
    }
    val hh = 3 * d + 13 * d * f
    val bottom = Path().apply {
        moveTo(x + 5 * d, y + 40 * d); lineTo(x + 21 * d, y + 40 * d); lineTo(cx, y + 40 * d - hh); close()
    }
    drawPath(bottom, sand)
    if (Motion.enabled && topFill > 0.05f) drawLine(sand, Offset(cx, y + 21 * d), Offset(cx, y + 40 * d - hh), 1 * d)
    drawPath(glass, Color(0xFFD8B46A), style = Stroke(1 * d))
    if (stage >= 5) sparkle(Offset(x + 24 * d, y + 6 * d), (2.5f + 1.5f * sin(t * 3f).coerceAtLeast(0f)) * d, Color.White.copy(alpha = 0.9f))
}

private fun DrawScope.quill(x: Float, y: Float, d: Float, color: Color, rib: Color) {
    val q = Path().apply {
        moveTo(x + 15 * d, y + 2 * d)
        cubicTo(x + 26 * d, y - 22 * d, x + 40 * d, y - 40 * d, x + 44 * d, y - 48 * d)
        cubicTo(x + 34 * d, y - 30 * d, x + 26 * d, y - 12 * d, x + 17 * d, y + 3 * d)
        close()
    }
    drawPath(q, color)
    drawPath(q, rib, style = Stroke(0.8f * d))
    drawLine(rib, Offset(x + 16 * d, y + 2 * d), Offset(x + 42 * d, y - 44 * d), 0.8f * d)
}

private fun DrawScope.inkwell(t: Float, d: Float, w: Float, h: Float, stage: Int) {
    val x = w / 2 - 14 * d
    val y = h - 24 * d
    val mouth = Offset(x + 15 * d, y + 2 * d)
    if (stage >= 4) {
        rotate(-22f, mouth) { quill(x, y, d, Color(0xFFCFD8E8), Color(0xFF8A9AB4)) }
    }
    if (stage >= 2) {
        rotate(-11f, mouth) { quill(x, y, d, Color(0xFFE8DCC4), Color(0xFFAA9670)) }
    }
    val gold = stage >= 5
    quill(x, y, d, if (gold) Color(0xFFF3D27A) else Color(0xFFEDE3CF), if (gold) Color(0xFFB8873F) else Color(0xFFB9A684))
    val body = Path().apply {
        moveTo(x + 3 * d, y + 6 * d); lineTo(x + 25 * d, y + 6 * d); lineTo(x + 28 * d, y + 24 * d); lineTo(x, y + 24 * d); close()
    }
    drawPath(body, Color(0xFF1B1B2B))
    drawPath(body, if (stage >= 3) Color(0xFFE8C46A) else Color(0xFFB8873F), style = Stroke((if (stage >= 3) 1.8f else 1.2f) * d))
    drawRect(if (stage >= 3) Color(0xFFE8C46A) else Color(0xFFB8873F), Offset(x + 8 * d, y), Size(12 * d, 7 * d))
    drawLine(Color.White.copy(alpha = 0.25f), Offset(x + 5 * d, y + 9 * d), Offset(x + 4 * d, y + 20 * d), 1.4f * d, cap = StrokeCap.Round)
    if (stage >= 3) {
        drawRoundRect(Color(0xFFEDE3CF), Offset(x + 27 * d, h - 7 * d), Size(16 * d, 7 * d), CornerRadius(3.5f * d))
        drawOval(Color(0xFFD9CBAA), Offset(x + 40 * d, h - 7 * d), Size(4 * d, 7 * d))
        drawCircle(Color(0xFFB03030), 2.4f * d, Offset(x + 34 * d, h - 3.5f * d))
    }
    if (gold) sparkle(Offset(x + 42 * d, y - 44 * d), (2f + 1.5f * sin(t * 2.6f).coerceAtLeast(0f)) * d, Color.White)
}

private fun DrawScope.bonsai(t: Float, d: Float, w: Float, h: Float, stage: Int, sad: Boolean) {
    val x = w / 2 - 18 * d
    val y = h - 52 * d
    val pot = Path().apply { moveTo(x + 2 * d, y + 40 * d); lineTo(x + 34 * d, y + 40 * d); lineTo(x + 30 * d, y + 52 * d); lineTo(x + 6 * d, y + 52 * d); close() }
    drawPath(pot, Color(0xFF5A6A8A))
    drawRect(Color(0xFF4A5A78), Offset(x, y + 38 * d), Size(36 * d, 4 * d))
    drawRect(Color(0xFF3E3428), Offset(x + 3 * d, y + 37 * d), Size(30 * d, 2 * d))
    val leaf = if (sad) listOf(Color(0xFF8A8A52), Color(0xFF9A9458), Color(0xFFA8A060)) else listOf(Color(0xFF6E8A48), Color(0xFF7D9A52), Color(0xFF8AAA5C))
    if (stage == 0) {
        drawLine(Color(0xFF6E8A48), Offset(x + 18 * d, y + 38 * d), Offset(x + 18 * d, y + 28 * d), 1.8f * d, cap = StrokeCap.Round)
        rotate(-30f, Offset(x + 18 * d, y + 29 * d)) { drawOval(leaf[1], Offset(x + 10 * d, y + 26 * d), Size(9 * d, 5 * d)) }
        rotate(30f, Offset(x + 18 * d, y + 29 * d)) { drawOval(leaf[2], Offset(x + 18 * d, y + 26 * d), Size(9 * d, 5 * d)) }
        if (sad) drawOval(leaf[0], Offset(x + 24 * d, y + 36 * d), Size(6 * d, 3 * d))
        return
    }
    val thick = (2.2f + stage * 0.5f).coerceAtMost(4f) * d
    val trunk = Path().apply { moveTo(x + 18 * d, y + 40 * d); cubicTo(x + 14 * d, y + 30 * d, x + 24 * d, y + 22 * d, x + 18 * d, y + 14 * d) }
    drawPath(trunk, Color(0xFF4A3A30), style = Stroke(thick, cap = StrokeCap.Round))
    if (stage >= 2) {
        val branch = Path().apply { moveTo(x + 19 * d, y + 26 * d); quadraticTo(x + 26 * d, y + 22 * d, x + 28 * d, y + 12 * d) }
        drawPath(branch, Color(0xFF4A3A30), style = Stroke(thick * 0.6f, cap = StrokeCap.Round))
    }
    drawOval(leaf[0], Offset(x, y + 6 * d), Size(26 * d, 15 * d))
    if (stage >= 2) drawOval(leaf[1], Offset(x + 16 * d, y + 2 * d), Size(22 * d, 13 * d))
    if (stage >= 3) drawOval(leaf[2], Offset(x + 8 * d, y - 4 * d), Size(18 * d, 11 * d))
    if (stage >= 4) {
        val pink = Color(0xFFFFB7CF)
        listOf(Offset(4f, 12f), Offset(12f, 8f), Offset(20f, 14f), Offset(24f, 6f), Offset(31f, 9f), Offset(14f, -1f), Offset(9f, 17f), Offset(34f, 12f)).forEach { o ->
            drawCircle(pink, 2.1f * d, Offset(x + o.x * d, y + o.y * d))
            drawCircle(Color.White.copy(alpha = 0.7f), 0.8f * d, Offset(x + o.x * d, y + o.y * d))
        }
    }
    if (stage >= 5 && Motion.enabled) {
        for (k in 0 until 3) {
            val p = (t * 0.18f + k / 3f) % 1f
            val px = x + (8 + k * 11) * d + sin(t * 1.4f + k) * 5 * d
            val py = y + 8 * d + p * 34 * d
            rotate(p * 300f, Offset(px, py)) { drawOval(Color(0xFFFFB7CF).copy(alpha = 1f - p * 0.6f), Offset(px - 2 * d, py - 1.2f * d), Size(4 * d, 2.4f * d)) }
        }
    }
}

private fun DrawScope.stoneLantern(t: Float, d: Float, w: Float, h: Float, lit: Boolean) {
    val x = w / 2 - 16 * d
    val y = h - 48 * d
    if (lit) glowAt(Color(0xFFFFD98A), 0.3f + 0.08f * sin(t * 2.3f), Offset(x + 16 * d, y + 22 * d), 26 * d)
    drawRect(Color(0xFFA8A294), Offset(x + 8 * d, y + 40 * d), Size(16 * d, 8 * d))
    drawRect(Color(0xFFB8B2A4), Offset(x + 11 * d, y + 28 * d), Size(10 * d, 12 * d))
    drawRect(Color(0xFFC8C2B4), Offset(x + 4 * d, y + 15 * d), Size(24 * d, 13 * d))
    drawRect(if (lit) Color(0xFFFFD98A) else Color(0xFF4E4A42), Offset(x + 11 * d, y + 18 * d), Size(10 * d, 7 * d))
    if (lit) drawCircle(Color(0xFFFFF4D0), 1.8f * d, Offset(x + 16 * d, y + 22 * d))
    val roof = Path().apply { moveTo(x, y + 15 * d); lineTo(x + 16 * d, y + 4 * d); lineTo(x + 32 * d, y + 15 * d); close() }
    drawPath(roof, Color(0xFF9A9484))
    drawCircle(Color(0xFF9A9484), 2.5f * d, Offset(x + 16 * d, y + 3 * d))
    drawOval(Color(0xFF7D9A52).copy(alpha = 0.7f), Offset(x + 6 * d, y + 45 * d), Size(7 * d, 3 * d))
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

private fun DrawScope.teaCup(t: Float, d: Float, w: Float, h: Float, light: Boolean, stage: Int) {
    val shift = if (stage >= 2) 9 * d else 0f
    val base = if (stage >= 3) h - 4 * d else h
    if (stage >= 3) {
        drawRoundRect(Color(0xFF8A5A3A), Offset(w / 2 - 28 * d, h - 4 * d), Size(56 * d, 4 * d), CornerRadius(2 * d))
        drawRect(Color(0xFF6E4630), Offset(w / 2 - 26 * d, h - 1.5f * d), Size(52 * d, 1.5f * d))
    }
    if (stage >= 2) {
        val px = w / 2 - 18 * d
        val py = base - 15 * d
        drawLine(Color(0xFFD8CFBC), Offset(px - 8 * d, py - 2 * d), Offset(px - 15 * d, py - 9 * d), 3 * d, cap = StrokeCap.Round)
        drawOval(Color(0xFFD8CFBC), Offset(px - 10 * d, py - 9 * d), Size(20 * d, 18 * d))
        drawOval(Color(0xFF8A7A5A), Offset(px - 10 * d, py - 9 * d), Size(20 * d, 18 * d), style = Stroke(1 * d))
        drawRoundRect(Color(0xFF8A7A5A), Offset(px - 4 * d, py - 12 * d), Size(8 * d, 3 * d), CornerRadius(1.5f * d))
        drawCircle(Color(0xFF8A7A5A), 1.6f * d, Offset(px, py - 13 * d))
        drawRect(Color(0xFF7D9A52).copy(alpha = 0.7f), Offset(px - 9 * d, py + 1 * d), Size(18 * d, 2 * d))
    }
    val x = w / 2 - 15 * d + shift * 0.8f
    val y = base - 24 * d
    val cup = Path().apply {
        moveTo(x, y + 4 * d); lineTo(x + 30 * d - shift * 0.4f, y + 4 * d)
        val r = x + 30 * d - shift * 0.4f
        val mid = (x + r) / 2
        cubicTo(r, y + 20 * d, mid + 7 * d, y + 24 * d, mid, y + 24 * d)
        cubicTo(mid - 7 * d, y + 24 * d, x, y + 20 * d, x, y + 4 * d)
    }
    drawPath(cup, Color(0xFFE8E0D0))
    drawPath(cup, Color(0xFF8A7A5A), style = Stroke(1 * d))
    drawRect(Color(0xFF7D9A52).copy(alpha = 0.8f), Offset(x + 2 * d, y + 5 * d), Size(26 * d - shift * 0.4f, 3 * d))
    val steamColor = if (stage >= 5) Color(0xFFF3D27A) else if (light) Color(0xFF9A9384) else Color(0xFFE8E0D0)
    steam(t, d, x + 9 * d, y, steamColor)
    if (stage >= 4) {
        drawRoundRect(Color(0xFFFFB7CF), Offset(w / 2 - 27 * d, base - 5 * d), Size(7 * d, 5 * d), CornerRadius(2 * d))
        drawRoundRect(Color(0xFFB9D88A), Offset(w / 2 - 20 * d, base - 5 * d), Size(7 * d, 5 * d), CornerRadius(2 * d))
    }
}

private fun DrawScope.mascot(t: Float, d: Float, w: Float, h: Float, stage: Int, awake: Boolean) {
    val x = w / 2 - 20 * d
    val y = h - 50 * d
    val ink = Color(0xFF111111)
    val jumpy = if (stage >= 3) 1.2f else 1.7f
    val hop = if (Motion.enabled && awake) maxOf(0f, sin(t * jumpy * 1.7f)).let { if (it > 0.97f) (it - 0.97f) * (120f + stage * 20f) else 0f } * d else 0f
    drawOval(ink.copy(alpha = 0.25f), Offset(x + 4 * d, h - 4 * d), Size(32 * d, 4 * d))
    translate(0f, -hop) {
        if (stage >= 2) {
            val sway = if (Motion.enabled) sin(t * 2.4f) * 2 * d else 0f
            val cape = Path().apply {
                moveTo(x + 6 * d, y + 18 * d); lineTo(x + 34 * d, y + 18 * d)
                lineTo(x + 40 * d + sway, y + 48 * d); lineTo(x + 20 * d, y + 44 * d); lineTo(x + sway, y + 48 * d); close()
            }
            drawPath(cape, Color(0xFFFF3B3B))
            drawPath(cape, ink, style = Stroke(2 * d))
        }
        val body = Path().apply {
            moveTo(x + 4 * d, y + 46 * d); lineTo(x + 4 * d, y + 26 * d)
            cubicTo(x + 4 * d, y + 4 * d, x + 36 * d, y + 4 * d, x + 36 * d, y + 26 * d); lineTo(x + 36 * d, y + 46 * d); close()
        }
        drawPath(body, Color(0xFF7AD3FF))
        drawPath(body, ink, style = Stroke(2.5f * d))
        if (awake) {
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
        } else {
            for (ex in listOf(14f, 26f)) {
                val lid = Path().apply { moveTo(x + (ex - 4) * d, y + 24 * d); quadraticTo(x + ex * d, y + 27 * d, x + (ex + 4) * d, y + 24 * d) }
                drawPath(lid, ink, style = Stroke(2 * d, cap = StrokeCap.Round))
            }
            drawOval(ink, Offset(x + 18 * d, y + 33 * d), Size(4 * d, 3 * d))
            sleepZ(t, d, x + 34 * d, y + 6 * d, ink)
        }
        if (stage >= 1) {
            val c = Offset(x + 29 * d, y + 39 * d)
            val star = Path()
            for (k in 0 until 10) {
                val r = (if (k % 2 == 0) 4.5f else 2f) * d
                val a = PI.toFloat() * k / 5 - PI.toFloat() / 2
                if (k == 0) star.moveTo(c.x + cos(a) * r, c.y + sin(a) * r) else star.lineTo(c.x + cos(a) * r, c.y + sin(a) * r)
            }
            star.close()
            drawPath(star, Color(0xFFFFCC00))
            drawPath(star, ink, style = Stroke(1 * d))
        }
        if (stage >= 4) {
            val crown = Path().apply {
                moveTo(x + 10 * d, y + 9 * d); lineTo(x + 10 * d, y - 1 * d); lineTo(x + 15 * d, y + 4 * d); lineTo(x + 20 * d, y - 4 * d)
                lineTo(x + 25 * d, y + 4 * d); lineTo(x + 30 * d, y - 1 * d); lineTo(x + 30 * d, y + 9 * d); close()
            }
            drawPath(crown, Color(0xFFFFCC00))
            drawPath(crown, ink, style = Stroke(1.6f * d))
            drawCircle(Color(0xFFFF3B3B), 1.8f * d, Offset(x + 20 * d, y + 5 * d))
            if (stage >= 5) sparkle(Offset(x + 34 * d, y - 2 * d), (2.5f + 1.5f * sin(t * 3.3f).coerceAtLeast(0f)) * d, Color.White)
        } else {
            val horn = Path().apply { moveTo(x + 18 * d, y + 9 * d); lineTo(x + 17 * d, y); lineTo(x + 24 * d, y + 7 * d); close() }
            drawPath(horn, Color(0xFFFF3B3B))
            drawPath(horn, ink, style = Stroke(1.5f * d))
        }
    }
}

private fun DrawScope.magazines(d: Float, w: Float, h: Float, text: androidx.compose.ui.text.TextMeasurer, stage: Int) {
    val x = w / 2 - 23 * d
    val ink = Color(0xFF111111)
    val colors = listOf(Color(0xFFFF3B3B), Color(0xFF7AD3FF), Color(0xFFFFCC00), Color(0xFFFF8FB6), Color(0xFF7DE38B), Color(0xFFB38CFF))
    val count = 1 + stage.coerceIn(0, 5)
    for (i in 0 until count) {
        val y = h - (10 + i * 9) * d
        val off = listOf(0f, 3f, -2f, 2f, -1f, 3f)[i] * d
        drawRect(colors[i], Offset(x + off, y), Size(44 * d, 9 * d))
        drawRect(ink, Offset(x + off, y), Size(44 * d, 9 * d), style = Stroke(1.8f * d))
        drawLine(Color.White.copy(alpha = 0.55f), Offset(x + off + 4 * d, y + 3 * d), Offset(x + off + 18 * d, y + 3 * d), 1.4f * d)
    }
    val c = Offset(x + 40 * d, h - (count * 9 + 8) * d)
    rotate(15f, c) {
        drawCircle(Color.White, 9 * d, c)
        drawCircle(ink, 9 * d, c, style = Stroke(2 * d))
        val layout = text.measure(if (stage >= 5) "★" else "5¢", TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF3B3B)))
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
    }
}

private fun DrawScope.bomb(t: Float, d: Float, w: Float, h: Float, safe: Boolean, evening: Boolean) {
    val ink = Color(0xFF111111)
    val shake = if (!safe && evening && Motion.enabled) sin(t * 38f) * 1.2f * d else 0f
    val c = Offset(w / 2 - 2 * d + shake, h - 15 * d)
    drawOval(ink.copy(alpha = 0.25f), Offset(w / 2 - 14 * d, h - 3 * d), Size(26 * d, 3 * d))
    drawCircle(Color(0xFF222222), 14 * d, c)
    drawCircle(ink, 14 * d, c, style = Stroke(2.5f * d))
    drawCircle(Color.White.copy(alpha = 0.55f), 3 * d, Offset(c.x - 5 * d, c.y - 5 * d))
    drawRect(Color(0xFF555555), Offset(c.x - 4 * d, c.y - 20 * d), Size(8 * d, 6 * d))
    drawRect(ink, Offset(c.x - 4 * d, c.y - 20 * d), Size(8 * d, 6 * d), style = Stroke(2 * d))
    if (safe) {
        val fuse = Path().apply { moveTo(c.x, c.y - 20 * d); cubicTo(c.x + 1 * d, c.y - 23 * d, c.x + 4 * d, c.y - 24 * d, c.x + 5 * d, c.y - 25 * d) }
        drawPath(fuse, Color(0xFF3A2A1A), style = Stroke(2 * d, cap = StrokeCap.Round))
        smoke(t, d, c.x + 5 * d, c.y - 26 * d, Color(0xFF9A9A9A))
        val eye = Color.White
        drawLine(eye, Offset(c.x - 6 * d, c.y + 1 * d), Offset(c.x - 2 * d, c.y + 1 * d), 1.6f * d, cap = StrokeCap.Round)
        drawLine(eye, Offset(c.x + 3 * d, c.y + 1 * d), Offset(c.x + 7 * d, c.y + 1 * d), 1.6f * d, cap = StrokeCap.Round)
        val smile = Path().apply { moveTo(c.x - 4 * d, c.y + 5 * d); quadraticTo(c.x + 0.5f * d, c.y + 8 * d, c.x + 5 * d, c.y + 5 * d) }
        drawPath(smile, eye, style = Stroke(1.4f * d, cap = StrokeCap.Round))
        return
    }
    val fuse = Path().apply { moveTo(c.x, c.y - 20 * d); cubicTo(c.x + 2 * d, c.y - 28 * d, c.x + 10 * d, c.y - 28 * d, c.x + 12 * d, c.y - 34 * d) }
    drawPath(fuse, Color(0xFF8A5A1C), style = Stroke(2 * d))
    val sp = Offset(c.x + 12 * d, c.y - 35 * d)
    val boost = if (evening) 1.45f else 1f
    val pulse = if (Motion.enabled) 0.7f + 0.3f * sin(t * (if (evening) 26f else 18f)) else 1f
    if (evening) glowAt(Color(0xFFFFCC00), 0.45f * pulse, sp, 12 * d)
    for (k in 0 until 8) {
        val a = PI.toFloat() * k / 4 + t * 3f
        val r1 = 2 * d
        val r2 = (if (k % 2 == 0) 8f else 5f) * d * pulse * boost
        drawLine(if (k % 2 == 0) Color(0xFFFFCC00) else Color(0xFFFF3B3B), Offset(sp.x + cos(a) * r1, sp.y + sin(a) * r1), Offset(sp.x + cos(a) * r2, sp.y + sin(a) * r2), 1.6f * d, cap = StrokeCap.Round)
    }
}

private fun DrawScope.coffee(t: Float, d: Float, w: Float, h: Float, light: Boolean, hot: Boolean) {
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
    drawOval(if (hot) Color(0xFF5A3A20) else Color(0xFF3A281A), Offset(x + 5 * d, y + 2 * d), Size(26 * d, 5 * d))
    if (hot) {
        drawOval(Color(0xFFC8A070).copy(alpha = 0.7f), Offset(x + 13 * d, y + 3 * d), Size(10 * d, 2.5f * d))
        steam(t, d, x + 13 * d, y - 1 * d, ink.copy(alpha = 0.6f))
    } else {
        drawOval(Color(0xFF6A4A30).copy(alpha = 0.6f), Offset(x + 9 * d, y + 3 * d), Size(6 * d, 1.5f * d))
    }
}

private fun DrawScope.glasses(t: Float, d: Float, w: Float, h: Float, light: Boolean, stage: Int) {
    val base = if (light) Color(0xFF141414) else Color(0xFFECE6D8)
    val ink = if (stage >= 3) Color(0xFFD8B46A) else base
    val y = h - 10 * d
    val cx = w / 2
    val width = (if (stage >= 3) 2.6f else 2f) * d
    if (stage >= 5) {
        val chain = Path().apply { moveTo(cx - 27 * d, y - 7 * d); cubicTo(cx - 30 * d, y + 8 * d, cx + 30 * d, y + 8 * d, cx + 27 * d, y - 7 * d) }
        drawPath(chain, Color(0xFFD8B46A), style = Stroke(1 * d, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2 * d, 1.5f * d))))
    }
    drawCircle(Color.White.copy(alpha = 0.12f), 8 * d, Offset(cx - 11 * d, y))
    drawCircle(Color.White.copy(alpha = 0.12f), 8 * d, Offset(cx + 11 * d, y))
    drawCircle(ink, 9 * d, Offset(cx - 11 * d, y), style = Stroke(width))
    drawCircle(ink, 9 * d, Offset(cx + 11 * d, y), style = Stroke(width))
    val bridge = Path().apply { moveTo(cx - 2.5f * d, y - 1 * d); quadraticTo(cx, y - 4 * d, cx + 2.5f * d, y - 1 * d) }
    drawPath(bridge, ink, style = Stroke(width))
    drawLine(ink, Offset(cx - 20 * d, y - 2 * d), Offset(cx - 27 * d, y - 7 * d), 1.6f * d)
    drawLine(ink, Offset(cx + 20 * d, y - 2 * d), Offset(cx + 27 * d, y - 7 * d), 1.6f * d)
    if (stage >= 1) {
        val sweep = if (stage >= 4 && Motion.enabled) ((t * 0.5f) % 1.6f) - 0.3f else 0.2f
        for (lens in listOf(-11f, 11f)) {
            val lx = cx + lens * d
            val off = sweep * 12 * d - 6 * d
            drawLine(Color.White.copy(alpha = 0.55f), Offset(lx - 4 * d + off, y + 4 * d), Offset(lx + 2 * d + off, y - 4 * d), 1.6f * d, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.paper(d: Float, w: Float, h: Float, light: Boolean, stage: Int) {
    val ink = if (light) Color(0xFF141414) else Color(0xFFECE6D8)
    val bg = if (light) Color(0xFFFBF8F1) else Color(0xFF242424)
    val pw = 54 * d
    val ph = 30 * d
    val under = stage.coerceIn(0, 4)
    for (i in under downTo 1) {
        val tl = Offset(w / 2 - pw / 2 + (if (i % 2 == 0) 2f else -2f) * d, h - ph - 2 * d + i * 0f - i * 2.6f * d)
        rotate(if (i % 2 == 0) 3f else -2f, Offset(w / 2, tl.y + ph / 2)) {
            drawRect(bg, tl, Size(pw, ph))
            drawRect(ink.copy(alpha = 0.7f), tl, Size(pw, ph), style = Stroke(1 * d))
        }
    }
    val lift = under * 2.6f * d
    val tl = Offset(w / 2 - pw / 2, h - ph - 2 * d - lift)
    rotate(-6f, Offset(w / 2, tl.y + ph / 2)) {
        drawRect(bg, tl, Size(pw, ph))
        drawRect(ink, tl, Size(pw, ph), style = Stroke(1.2f * d))
        drawRect(ink, Offset(tl.x + 4 * d, tl.y + 4 * d), Size(pw - 8 * d, 5 * d))
        for (c in 0 until 2) for (r in 0 until 4) {
            drawRect(ink.copy(alpha = 0.5f), Offset(tl.x + 4 * d + c * 24 * d, tl.y + 12 * d + r * 4 * d), Size(21 * d, 1.6f * d))
        }
    }
    if (stage >= 5) {
        val red = Color(0xFFC8102E)
        drawRect(red, Offset(w / 2 + 8 * d, h - ph - 6 * d - lift), Size(4 * d, ph + lift + 4 * d))
        drawCircle(red, 3.5f * d, Offset(w / 2 + 6 * d, h - ph - 6 * d - lift))
        drawCircle(red, 3.5f * d, Offset(w / 2 + 14 * d, h - ph - 6 * d - lift))
    }
}

private fun DrawScope.shell(t: Float, d: Float, w: Float, h: Float, stage: Int) {
    val x = w / 2 - 20 * d
    val y = h - 30 * d
    val base = Path().apply { moveTo(x, y + 28 * d); cubicTo(x, y + 10 * d, x + 40 * d, y + 10 * d, x + 40 * d, y + 28 * d); close() }
    drawPath(base, Color(0xFFF4C6D6))
    drawPath(base, Color(0xFFC88AA0), style = Stroke(1 * d))
    val pearl = stage >= 1
    val open = (if (pearl) 1f + 0.08f * stage else 0.85f) * (if (Motion.enabled) 1f + 0.08f * maxOf(0f, sin(t * 0.8f)) else 1f)
    if (pearl) {
        val r = (3f + stage * 0.7f) * d
        val pc = Offset(x + 20 * d, y + 24 * d - r * 0.3f)
        glowAt(Color.White, 0.25f + 0.08f * stage + (if (stage >= 4) 0.12f * sin(t * 2.2f) else 0f), pc, r * 2.6f)
        drawCircle(Color(0xFFFFFAF0), r, pc)
        drawCircle(Color(0xFFF0E6D8), r, pc, style = Stroke(0.6f * d))
        drawCircle(Color.White, r * 0.35f, Offset(pc.x - r * 0.35f, pc.y - r * 0.35f))
    }
    val lid = Path().apply {
        moveTo(x + 2 * d, y + 28 * d)
        cubicTo(x + 4 * d, y + 4 * d / open, x + 36 * d, y + 2 * d / open, x + 38 * d, y + 16 * d)
    }
    drawPath(lid, Color(0xFFF8D6E2))
    drawPath(lid, Color(0xFFC88AA0), style = Stroke(1 * d))
    for (k in 0 until 5) drawLine(Color(0xFFC88AA0), Offset(x + 20 * d, y + 28 * d), Offset(x + (6 + k * 7) * d, y + 14 * d), 0.8f * d)
}

private fun DrawScope.starfish(t: Float, d: Float, w: Float, h: Float, bright: Boolean) {
    val c = Offset(w / 2, h - 16 * d)
    if (bright) glowAt(Color(0xFFFFB27A), 0.3f + 0.08f * sin(t * 1.7f), c, 24 * d)
    val p = Path()
    for (k in 0 until 10) {
        val r = (if (k % 2 == 0) 17f else 7f) * d
        val a = PI.toFloat() * k / 5 - PI.toFloat() / 2
        val pt = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
        if (k == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    p.close()
    drawPath(p, if (bright) Color(0xFFFF8A5C) else Color(0xFFC9A796))
    drawPath(p, if (bright) Color(0xFFD0603A) else Color(0xFF9E8070), style = Stroke(1 * d))
    drawCircle(Color(0xFFFFD0B0), 2.2f * d, c)
    for (k in 0 until 5) {
        val a = PI.toFloat() * 2 * k / 5 - PI.toFloat() / 2
        val tw = if (bright && Motion.enabled) 0.5f + 0.5f * sin(t * 3f + k * 1.2f) else 0.6f
        drawCircle(Color(0xFFFFE6D0).copy(alpha = tw), 1.3f * d, Offset(c.x + cos(a) * 9 * d, c.y + sin(a) * 9 * d))
    }
}

private fun DrawScope.chest(t: Float, d: Float, w: Float, h: Float, stage: Int) {
    val x = w / 2 - 21 * d
    val y = h - 32 * d
    val gold = Color(0xFFD8B46A)
    val coin = Color(0xFFFFD36A)
    val lift = stage * 2.2f * d
    if (stage >= 1) glowAt(Color(0xFFFFE9A8), 0.18f + 0.07f * stage, Offset(x + 21 * d, y + 12 * d), (18 + stage * 4) * d)
    drawRect(Color(0xFF7A4A24), Offset(x, y + 13 * d), Size(42 * d, 19 * d))
    drawRect(Color(0xFF5E3818), Offset(x, y + 20 * d), Size(42 * d, 2 * d))
    drawRect(gold, Offset(x, y + 13 * d), Size(42 * d, 19 * d), style = Stroke(1.4f * d))
    if (stage >= 1) {
        drawRect(Color(0xFFFFE08A), Offset(x + 2 * d, y + 13 * d - lift), Size(38 * d, lift))
        for (k in 0 until (stage * 2).coerceAtMost(8)) {
            val cx = x + (6 + (k * 37 % 30)) * d
            val cy = y + 12 * d - (k % 3) * 1.4f * d
            drawOval(coin, Offset(cx - 2.6f * d, cy - 1.2f * d), Size(5.2f * d, 2.4f * d))
        }
    }
    val lid = Path().apply { moveTo(x, y + 13 * d - lift); cubicTo(x, y - lift, x + 42 * d, y - lift, x + 42 * d, y + 13 * d - lift); close() }
    drawPath(lid, Color(0xFF8A5A30))
    drawPath(lid, gold, style = Stroke(1.4f * d))
    drawRect(gold, Offset(x + 18 * d, y + 10 * d - (if (stage >= 1) lift else 0f)), Size(6 * d, 9 * d))
    if (stage >= 2) {
        for (k in 0 until (stage - 1).coerceAtMost(4)) {
            val side = if (k % 2 == 0) -1f else 1f
            val cx = w / 2 + side * (24 + k * 3) * d
            drawOval(coin, Offset(cx - 3 * d, h - 3 * d), Size(6 * d, 3 * d))
            drawOval(Color(0xFFB8873F), Offset(cx - 3 * d, h - 3 * d), Size(6 * d, 3 * d), style = Stroke(0.6f * d))
        }
    }
    if (stage >= 5) {
        drawCircle(Color(0xFFFF4A6A), 2.6f * d, Offset(x + 12 * d, y + 9 * d - lift * 0.5f))
        drawCircle(Color(0xFF4AC8FF), 2.4f * d, Offset(x + 30 * d, y + 8 * d - lift * 0.5f))
    }
    val tw = if (Motion.enabled) 0.5f + 0.5f * sin(t * 3.1f) else 0.7f
    drawCircle(Color(0xFFFFE9A8).copy(alpha = tw), 2.2f * d, Offset(x + 30 * d, y + 2 * d - lift))
    drawCircle(Color(0xFFFFE9A8).copy(alpha = 1f - tw * 0.6f), 1.6f * d, Offset(x + 10 * d, y + 5 * d - lift))
}

private fun DrawScope.coral(t: Float, d: Float, w: Float, h: Float, stage: Int) {
    val c = androidx.compose.ui.graphics.lerp(Color(0xFFE88AA0), Color(0xFFFF5C84), stage / 5f)
    val x = w / 2 - 14 * d
    val sway = if (Motion.enabled) sin(t * 1.1f) * 2 * d else 0f
    if (stage >= 5) glowAt(Color(0xFFFF8AB0), 0.32f + 0.08f * sin(t * 1.9f), Offset(w / 2, h - 28 * d), 34 * d)
    val s = Stroke((3f + stage * 0.25f) * d, cap = StrokeCap.Round)
    fun branch(sx: Float, sy: Float, ex: Float, ey: Float) {
        val p = Path().apply { moveTo(sx, sy); quadraticTo((sx + ex) / 2 + sway / 2, (sy + ey) / 2, ex + sway, ey) }
        drawPath(p, c, style = s)
    }
    val branches = listOf(
        floatArrayOf(14f, 0f, 14f, -26f),
        floatArrayOf(14f, -14f, 2f, -38f),
        floatArrayOf(14f, -20f, 28f, -44f),
        floatArrayOf(8f, -26f, 8f, -46f),
        floatArrayOf(22f, -32f, 34f, -52f),
        floatArrayOf(2f, -32f, -6f, -50f),
    )
    val tall = if (stage == 0) 0.7f else 1f
    branches.take(1 + stage).forEach { b ->
        branch(x + b[0] * d, h + b[1] * d * tall, x + b[2] * d, h + b[3] * d * tall)
    }
    if (stage >= 4) {
        branches.take(1 + stage).forEachIndexed { i, b ->
            val tw = if (Motion.enabled) 0.5f + 0.5f * sin(t * 2f + i) else 0.8f
            drawCircle(Color.White.copy(alpha = 0.7f * tw), 1.4f * d, Offset(x + b[2] * d + sway, h + b[3] * d))
        }
    }
    drawOval(Color(0xFFE85A78), Offset(x + 4 * d, h - 4 * d), Size(20 * d, 4 * d))
}

private val Shine = listOf(Color.White.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.12f))

private fun DrawScope.clayBlob(color: Color, tl: Offset, sz: Size) {
    drawOval(color, tl, sz)
    drawOval(Brush.linearGradient(Shine, start = tl, end = Offset(tl.x + sz.width, tl.y + sz.height)), tl, sz)
}

private fun DrawScope.clayEyes(t: Float, d: Float, l: Offset, r: Offset, sleepy: Boolean = false) {
    val ink = Color(0xFF3B2F5C)
    val blink = Motion.enabled && (t % 5f) > 4.85f
    for (e in listOf(l, r)) {
        when {
            sleepy -> {
                val lid = Path().apply { moveTo(e.x - 2.2f * d, e.y - 0.5f * d); quadraticTo(e.x, e.y + 1.6f * d, e.x + 2.2f * d, e.y - 0.5f * d) }
                drawPath(lid, ink, style = Stroke(1.4f * d, cap = StrokeCap.Round))
            }
            blink -> drawLine(ink, Offset(e.x - 2 * d, e.y), Offset(e.x + 2 * d, e.y), 1.6f * d, cap = StrokeCap.Round)
            else -> drawCircle(ink, 2 * d, e)
        }
    }
}

private fun DrawScope.snailShell(t: Float, d: Float, x: Float, h: Float) {
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
}

private fun DrawScope.snail(t: Float, d: Float, w: Float, h: Float, awake: Boolean) {
    val x = w / 2 - 26 * d
    if (!awake) {
        val foot = Path().apply {
            moveTo(x + 8 * d, h); lineTo(x + 40 * d, h)
            cubicTo(x + 40 * d, h - 5 * d, x + 36 * d, h - 7 * d, x + 32 * d, h - 7 * d)
            lineTo(x + 14 * d, h - 7 * d); cubicTo(x + 10 * d, h - 7 * d, x + 8 * d, h - 5 * d, x + 8 * d, h); close()
        }
        drawPath(foot, Color(0xFFB9F0C4))
        snailShell(t, d, x, h)
        drawCircle(Color(0xFF3B2F5C).copy(alpha = 0.25f), 3 * d, Offset(x + 34 * d, h - 12 * d))
        sleepZ(t, d, x + 40 * d, h - 38 * d, Color(0xFF3B2F5C))
        return
    }
    val crawl = if (Motion.enabled) sin(t * 0.7f) * 1.5f * d else 0f
    translate(crawl, 0f) {
        val body = Path().apply {
            moveTo(x, h); lineTo(x + 46 * d, h)
            cubicTo(x + 46 * d, h - 6 * d, x + 42 * d, h - 9 * d, x + 36 * d, h - 9 * d)
            lineTo(x + 10 * d, h - 9 * d); cubicTo(x + 4 * d, h - 9 * d, x, h - 6 * d, x, h); close()
        }
        drawPath(body, Color(0xFFB9F0C4))
        drawPath(body, Brush.verticalGradient(Shine, startY = h - 9 * d, endY = h))
        snailShell(t, d, x, h)
        drawLine(Color(0xFF8AD09A), Offset(x + 42 * d, h - 8 * d), Offset(x + 44 * d, h - 22 * d), 2.5f * d, cap = StrokeCap.Round)
        drawLine(Color(0xFF8AD09A), Offset(x + 45 * d, h - 8 * d), Offset(x + 51 * d, h - 20 * d), 2.5f * d, cap = StrokeCap.Round)
        clayEyes(t, d, Offset(x + 44 * d, h - 23 * d), Offset(x + 51 * d, h - 21 * d))
    }
}

private fun DrawScope.oneMushroom(d: Float, cx: Float, h: Float, scale: Float, cap: Color) {
    val sw = 12 * d * scale
    val sh = 20 * d * scale
    val cw = 34 * d * scale
    drawRoundRect(Color(0xFFFBF8FF), Offset(cx - sw / 2, h - sh), Size(sw, sh), CornerRadius(5 * d * scale))
    val top = h - 18 * d * scale
    val p = Path().apply { moveTo(cx - cw / 2, top); cubicTo(cx - cw / 2, top - 18 * d * scale, cx + cw / 2, top - 18 * d * scale, cx + cw / 2, top); close() }
    drawPath(p, cap)
    drawPath(p, Brush.linearGradient(Shine, start = Offset(cx - cw / 2, top - 18 * d * scale), end = Offset(cx + cw / 2, top)))
    drawCircle(Color.White, 3 * d * scale, Offset(cx - 7 * d * scale, top - 8 * d * scale))
    drawCircle(Color.White, 2.5f * d * scale, Offset(cx + 5 * d * scale, top - 11 * d * scale))
    drawCircle(Color.White, 2 * d * scale, Offset(cx + 10 * d * scale, top - 4 * d * scale))
}

private fun DrawScope.mushroom(d: Float, w: Float, h: Float, stage: Int) {
    val cx = w / 2
    if (stage >= 3) oneMushroom(d, cx + 19 * d, h, 0.5f, Color(0xFFFFA8C8))
    if (stage >= 1) oneMushroom(d, cx - 18 * d, h, 0.62f, if (stage >= 5) Color(0xFFFFD36A) else Color(0xFFB48CFF))
    oneMushroom(d, cx, h, 1f, Color(0xFFFF7A7A))
    if (stage >= 2) {
        drawLine(Color(0xFF8AD09A), Offset(cx + 10 * d, h), Offset(cx + 12 * d, h - 7 * d), 1.6f * d, cap = StrokeCap.Round)
        drawOval(Color(0xFF8AD09A), Offset(cx + 12 * d, h - 9 * d), Size(5 * d, 3 * d))
    }
    if (stage >= 4) {
        drawCircle(Color(0xFFFFE07A), 1.6f * d, Offset(cx - 26 * d, h - 3 * d))
        drawCircle(Color(0xFFFFFFFF), 1.2f * d, Offset(cx + 27 * d, h - 2 * d))
    }
}

private fun DrawScope.flower(c: Offset, r: Float, petal: Color) {
    for (k in 0 until 5) {
        val a = PI.toFloat() * 2 * k / 5
        drawCircle(petal, r, Offset(c.x + cos(a) * r, c.y + sin(a) * r))
    }
    drawCircle(Color(0xFFFFE07A), r * 0.8f, c)
}

private fun DrawScope.cactus(t: Float, d: Float, w: Float, h: Float, stage: Int, sad: Boolean) {
    val x = w / 2 - 18 * d
    val y = h - 52 * d
    val pot = Path().apply { moveTo(x + 4 * d, y + 36 * d); lineTo(x + 32 * d, y + 36 * d); lineTo(x + 29 * d, y + 52 * d); lineTo(x + 7 * d, y + 52 * d); close() }
    drawPath(pot, Color(0xFFFFC89A))
    drawPath(pot, Brush.verticalGradient(Shine, startY = y + 36 * d, endY = y + 52 * d))
    val green = if (sad) Color(0xFFA8C29A) else Color(0xFF8AD09A)
    val grow = when (stage) { 0 -> 12f; 1 -> 6f; 2 -> 3f; else -> 0f } * d
    val top = y + 6 * d + grow
    if (stage >= 1) drawRoundRect(green, Offset(x + 2 * d, y + 16 * d + grow * 0.5f), Size(9 * d, 14 * d), CornerRadius(4.5f * d))
    if (stage >= 2) drawRoundRect(green, Offset(x + 25 * d, y + 12 * d + grow * 0.5f), Size(9 * d, 14 * d), CornerRadius(4.5f * d))
    drawRoundRect(green, Offset(x + 11 * d, top), Size(14 * d, y + 38 * d - top), CornerRadius(7 * d))
    drawRoundRect(Brush.linearGradient(Shine, start = Offset(x + 11 * d, top), end = Offset(x + 25 * d, y + 38 * d)), Offset(x + 11 * d, top), Size(14 * d, y + 38 * d - top), CornerRadius(7 * d))
    when {
        stage >= 4 -> {
            flower(Offset(x + 18 * d, top - 1 * d), 2.6f * d, Color(0xFFFF8FB6))
            if (stage >= 5) {
                flower(Offset(x + 6.5f * d, y + 15 * d), 1.9f * d, Color(0xFFFFB7CF))
                flower(Offset(x + 29.5f * d, y + 11 * d), 1.9f * d, Color(0xFFB48CFF))
            }
        }
        stage >= 3 -> drawCircle(Color(0xFFFF8FB6), 3 * d, Offset(x + 18 * d, top - 1 * d))
    }
    val ey = top + 13 * d
    clayEyes(t, d, Offset(x + 15 * d, ey), Offset(x + 21 * d, ey), sleepy = sad)
    val smile = Path().apply {
        if (sad) { moveTo(x + 15.5f * d, ey + 6 * d); quadraticTo(x + 18 * d, ey + 4 * d, x + 20.5f * d, ey + 6 * d) }
        else { moveTo(x + 15 * d, ey + 5 * d); quadraticTo(x + 18 * d, ey + 8 * d, x + 21 * d, ey + 5 * d) }
    }
    drawPath(smile, Color(0xFF3B2F5C), style = Stroke(1.2f * d, cap = StrokeCap.Round))
}

private fun DrawScope.xmasTree(t: Float, d: Float, w: Float, h: Float, stage: Int, sad: Boolean) {
    val cx = w / 2
    val tones = if (sad) listOf(Color(0xFF4A6656), Color(0xFF58786A), Color(0xFF668A78)) else listOf(Color(0xFF1F6B45), Color(0xFF2A8454), Color(0xFF379A63))
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(cx - 17 * d, h - 4 * d), Size(34 * d, 5 * d))
    drawRect(Color(0xFF7A4E2A), Offset(cx - 3 * d, h - 10 * d), Size(6 * d, 9 * d))
    drawRect(Color(0xFF5E3A1C), Offset(cx + 1 * d, h - 10 * d), Size(2 * d, 9 * d))
    val tiers = listOf(Triple(h - 9 * d, 20f, 22f), Triple(h - 23 * d, 16f, 20f), Triple(h - 36 * d, 12f, 18f))
    tiers.forEachIndexed { i, tier ->
        val by = tier.first
        val half = tier.second * d
        val top = by - tier.third * d
        val p = Path().apply {
            moveTo(cx - half, by)
            quadraticTo(cx, by + 4 * d, cx + half, by)
            lineTo(cx, top)
            close()
        }
        drawPath(p, tones[i])
        val shade = Path().apply {
            moveTo(cx, top)
            lineTo(cx + half, by)
            quadraticTo(cx + half * 0.5f, by + 3 * d, cx + 2 * d, by + 2 * d)
            close()
        }
        drawPath(shade, Color.Black.copy(alpha = 0.12f))
        drawOval(Color.White.copy(alpha = 0.92f), Offset(cx - half * 0.78f, by - 5 * d), Size(half * 0.5f, 3.6f * d))
        drawOval(Color.White.copy(alpha = 0.85f), Offset(cx + half * 0.2f, by - 7.5f * d), Size(half * 0.42f, 3.2f * d))
    }
    val spots = listOf(Offset(-9f, 13f), Offset(8f, 16f), Offset(-5f, 28f), Offset(6f, 30f), Offset(0f, 42f), Offset(-13f, 11f))
    val shown = (stage + 1).coerceIn(1, spots.size)
    for (i in 0 until shown) {
        val c = Offset(cx + spots[i].x * d, h - spots[i].y * d)
        if (sad) {
            drawCircle(Color(0xFF9AA8A0), 2.2f * d, c)
        } else {
            val color = FrostLights[i % FrostLights.size]
            val lit = if (Motion.enabled) 0.6f + 0.4f * sin(t * 3f + i * 1.7f) else 1f
            drawCircle(color.copy(alpha = 0.28f * lit), 5 * d, c)
            drawCircle(color, 2.3f * d, c)
            drawCircle(Color.White.copy(alpha = 0.8f), 0.8f * d, Offset(c.x - 0.7f * d, c.y - 0.8f * d))
        }
    }
    if (stage >= 2) {
        val star = Offset(cx, h - 56 * d)
        val gold = if (sad) Color(0xFFB8AE8A) else Color(0xFFFFD166)
        if (stage >= 4 && !sad) glowAt(gold, 0.5f, star, 13 * d)
        sparkle(star, 6 * d, gold)
        drawCircle(Color.White.copy(alpha = 0.85f), 1.2f * d, star)
    } else {
        drawOval(Color.White.copy(alpha = 0.95f), Offset(cx - 3.5f * d, h - 56 * d), Size(7 * d, 5 * d))
    }
}

private fun DrawScope.snowman(t: Float, d: Float, w: Float, h: Float, awake: Boolean, accent: Color) {
    val cx = w / 2
    val white = Color(0xFFFBFDFF)
    val shade = Color(0xFFC9D9E8)
    val coal = Color(0xFF2B2F36)
    drawOval(Color.Black.copy(alpha = 0.16f), Offset(cx - 16 * d, h - 4 * d), Size(32 * d, 5 * d))
    val balls = listOf(Triple(h - 13 * d, 13f, 0f), Triple(h - 31 * d, 9.5f, 0f), Triple(h - 45 * d, 7f, 0f))
    val wave = if (awake && Motion.enabled) sin(t * 4f) * 0.35f else 0f
    val twig = Color(0xFF6B4322)
    val leftTip = Offset(cx - 20 * d, h - 36 * d)
    drawLine(twig, Offset(cx - 8 * d, h - 32 * d), leftTip, 1.4f * d, cap = StrokeCap.Round)
    drawLine(twig, Offset(cx - 16 * d, h - 35 * d), Offset(cx - 18 * d, h - 40 * d), 1.1f * d, cap = StrokeCap.Round)
    val ang = (if (awake) -0.95f else 0.25f) + wave
    val rightTip = Offset(cx + 8 * d + cos(ang) * 13 * d, h - 32 * d + sin(ang) * 13 * d)
    drawLine(twig, Offset(cx + 8 * d, h - 32 * d), rightTip, 1.4f * d, cap = StrokeCap.Round)
    drawLine(twig, rightTip, Offset(rightTip.x + cos(ang - 0.7f) * 4 * d, rightTip.y + sin(ang - 0.7f) * 4 * d), 1.1f * d, cap = StrokeCap.Round)
    balls.forEach { b ->
        val c = Offset(cx, b.first)
        val r = b.second * d
        drawCircle(shade, r, c)
        drawCircle(white, r * 0.92f, Offset(c.x - r * 0.1f, c.y - r * 0.1f))
    }
    drawCircle(coal, 1.2f * d, Offset(cx, h - 34 * d))
    drawCircle(coal, 1.2f * d, Offset(cx, h - 28.5f * d))
    val sway = if (awake && Motion.enabled) sin(t * 2.2f) * 1.5f * d else 0f
    drawRoundRect(accent, Offset(cx - 8 * d, h - 41.5f * d), Size(16 * d, 4.2f * d), CornerRadius(2 * d))
    val tail = Path().apply {
        moveTo(cx + 3 * d, h - 38.5f * d)
        lineTo(cx + 7 * d, h - 38.5f * d)
        lineTo(cx + 8 * d + sway, h - 29 * d)
        lineTo(cx + 4 * d + sway, h - 29 * d)
        close()
    }
    drawPath(tail, accent)
    drawLine(Color.White.copy(alpha = 0.7f), Offset(cx + 4.5f * d + sway, h - 31 * d), Offset(cx + 7.5f * d + sway, h - 31 * d), 0.9f * d)
    drawRoundRect(coal, Offset(cx - 5.5f * d, h - 60 * d), Size(11 * d, 9 * d), CornerRadius(1.2f * d))
    drawRoundRect(coal, Offset(cx - 8 * d, h - 52.5f * d), Size(16 * d, 2.4f * d), CornerRadius(1.2f * d))
    drawRect(accent, Offset(cx - 5.5f * d, h - 55 * d), Size(11 * d, 2 * d))
    val ey = h - 47 * d
    if (awake) {
        drawCircle(coal, 1.1f * d, Offset(cx - 2.6f * d, ey))
        drawCircle(coal, 1.1f * d, Offset(cx + 2.6f * d, ey))
        val smile = Path().apply { moveTo(cx - 3 * d, ey + 4 * d); quadraticTo(cx, ey + 6.4f * d, cx + 3 * d, ey + 4 * d) }
        drawPath(smile, coal, style = Stroke(1f * d, cap = StrokeCap.Round))
    } else {
        for (side in listOf(-1f, 1f)) {
            val lid = Path().apply { moveTo(cx + side * 2.6f * d - 1.4f * d, ey); quadraticTo(cx + side * 2.6f * d, ey + 1.4f * d, cx + side * 2.6f * d + 1.4f * d, ey) }
            drawPath(lid, coal, style = Stroke(1f * d, cap = StrokeCap.Round))
        }
        sleepZ(t, d, cx + 11 * d, h - 54 * d, Color(0xFF8EA6C2))
    }
    val nose = Path().apply { moveTo(cx, ey + 1.2f * d); lineTo(cx + 6.5f * d, ey + 2.4f * d); lineTo(cx, ey + 3.4f * d); close() }
    drawPath(nose, Color(0xFFFF9A3C))
}

private fun DrawScope.giftBox(x: Float, y: Float, bw: Float, bh: Float, d: Float, color: Color, ribbon: Color) {
    drawRoundRect(color, Offset(x, y), Size(bw, bh), CornerRadius(1.6f * d))
    drawRect(Color.Black.copy(alpha = 0.14f), Offset(x + bw * 0.62f, y), Size(bw * 0.38f, bh))
    drawRoundRect(color, Offset(x - 1.2f * d, y - 1 * d), Size(bw + 2.4f * d, 4.6f * d), CornerRadius(1.4f * d))
    drawRect(Color.White.copy(alpha = 0.22f), Offset(x - 1.2f * d, y - 1 * d), Size(bw + 2.4f * d, 1.3f * d))
    drawRect(ribbon, Offset(x + bw / 2 - 1.6f * d, y - 1 * d), Size(3.2f * d, bh + 1 * d))
    val top = Offset(x + bw / 2, y - 1 * d)
    for (side in listOf(-1f, 1f)) {
        val loop = Path().apply {
            moveTo(top.x, top.y)
            cubicTo(top.x + side * 2 * d, top.y - 6 * d, top.x + side * 7.5f * d, top.y - 5 * d, top.x + side * 5 * d, top.y - 1 * d)
            close()
        }
        drawPath(loop, ribbon)
    }
    drawCircle(ribbon, 1.5f * d, top)
}

private fun DrawScope.gifts(d: Float, w: Float, h: Float, stage: Int) {
    val cx = w / 2
    val gold = Color(0xFFFFD166)
    drawOval(Color.Black.copy(alpha = 0.16f), Offset(cx - 22 * d, h - 4 * d), Size(44 * d, 5 * d))
    if (stage <= 0) {
        giftBox(cx - 9 * d, h - 15 * d, 18 * d, 14 * d, d, Color(0xFFD9434A), gold)
        return
    }
    if (stage >= 2) giftBox(cx + 3 * d, h - 16 * d, 17 * d, 15 * d, d, Color(0xFF2F8F5B), Color(0xFFFBFDFF))
    giftBox(cx - 20 * d, h - 22 * d, 23 * d, 21 * d, d, Color(0xFFD9434A), gold)
    if (stage >= 3) giftBox(cx - 15 * d, h - 35 * d, 14 * d, 11 * d, d, Color(0xFF3F7FC2), Color(0xFFFBFDFF))
    if (stage >= 4) giftBox(cx + 6 * d, h - 27 * d, 11 * d, 9 * d, d, gold, Color(0xFFD9434A))
    if (stage >= 5) {
        sparkle(Offset(cx + 2 * d, h - 42 * d), 4 * d, gold)
        sparkle(Offset(cx + 16 * d, h - 36 * d), 2.6f * d, Color.White)
    }
}

private fun DrawScope.cocoa(t: Float, d: Float, w: Float, h: Float, hot: Boolean) {
    val cx = w / 2
    val x = cx - 15 * d
    val y = h - 27 * d
    val red = Color(0xFFD9434A)
    drawOval(Color.Black.copy(alpha = 0.16f), Offset(cx - 18 * d, h - 4 * d), Size(36 * d, 5 * d))
    val cane = Path().apply {
        moveTo(x + 20 * d, y + 6 * d)
        lineTo(x + 24 * d, y - 12 * d)
        cubicTo(x + 25 * d, y - 19 * d, x + 33 * d, y - 18 * d, x + 31 * d, y - 11 * d)
    }
    drawPath(cane, Color.White, style = Stroke(3.4f * d, cap = StrokeCap.Round))
    drawPath(cane, red, style = Stroke(3.4f * d, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3 * d, 4 * d), 2 * d)))
    val handle = Path().apply { moveTo(x + 27 * d, y + 7 * d); cubicTo(x + 37 * d, y + 7 * d, x + 37 * d, y + 20 * d, x + 26 * d, y + 20 * d) }
    drawPath(handle, red, style = Stroke(3.2f * d, cap = StrokeCap.Round))
    val cup = Path().apply {
        moveTo(x + 1 * d, y + 3 * d); lineTo(x + 29 * d, y + 3 * d); lineTo(x + 28 * d, y + 18 * d)
        cubicTo(x + 27 * d, y + 25 * d, x + 22 * d, y + 26 * d, x + 15 * d, y + 26 * d)
        cubicTo(x + 8 * d, y + 26 * d, x + 3 * d, y + 25 * d, x + 2 * d, y + 18 * d); close()
    }
    drawPath(cup, red)
    drawPath(cup, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.Black.copy(alpha = 0.2f)), startX = x, endX = x + 30 * d))
    drawRect(Color.White.copy(alpha = 0.92f), Offset(x + 2.4f * d, y + 12 * d), Size(25.4f * d, 6 * d))
    for (i in 0 until 4) flake6(Offset(x + (6 + i * 6) * d, y + 15 * d), 2 * d, red, 0.8f * d, barbs = false)
    drawOval(Color(0xFFF2E9DC), Offset(x, y), Size(30 * d, 6.4f * d))
    drawOval(if (hot) Color(0xFF6B3F22) else Color(0xFF4A2E1C), Offset(x + 2 * d, y + 1 * d), Size(26 * d, 4.4f * d))
    if (hot) {
        drawRoundRect(Color.White, Offset(x + 6 * d, y - 0.6f * d), Size(6 * d, 4.4f * d), CornerRadius(1.6f * d))
        drawRoundRect(Color(0xFFFFE3EA), Offset(x + 13 * d, y + 0.2f * d), Size(5.4f * d, 4 * d), CornerRadius(1.6f * d))
        steam(t, d, x + 9 * d, y - 3 * d, Color.White.copy(alpha = 0.75f))
    }
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
            Decor.Frost -> {
                drawLine(line, Offset(0f, cy), Offset(w / 2 - 10 * density, cy), 1f * density)
                drawLine(line, Offset(w / 2 + 10 * density, cy), Offset(w, cy), 1f * density)
                flake6(Offset(w / 2, cy), 3.6f * density, accent.copy(alpha = 0.8f), 1f * density, barbs = false)
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
