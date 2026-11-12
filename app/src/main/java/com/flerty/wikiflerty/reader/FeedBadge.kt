package com.flerty.wikiflerty.reader

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.flerty.wikiflerty.ui.AppStyle
import com.flerty.wikiflerty.ui.Kit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class FeedPull(val dir: Int, val k: Float, val has: Boolean) {
    val ready: Boolean get() = has && k >= 1f
}

@Composable
internal fun FeedBadge(pull: FeedPull, settings: ReaderSettings, modifier: Modifier = Modifier) {
    val page = settings.pageTheme
    val bg = Color(page.background)
    val fg = Color(page.text)
    val kit = AppStyle.kit
    val custom = AppStyle.current.accent != kit.accent && AppStyle.current.accent != kit.accentLight
    val accent = Color(if (custom) AppStyle.current.accent else if (page.isLight) kit.accentLight else kit.accent)
    val target = if (pull.has) pull.k.coerceIn(0f, 1f) else (pull.k * 0.25f).coerceIn(0f, 0.25f)
    val k by animateFloatAsState(target, spring(stiffness = Spring.StiffnessMediumLow), label = "feed-k")
    val ready by animateFloatAsState(if (pull.ready) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "feed-ready")
    val loop = rememberInfiniteTransition(label = "feed-loop")
    val time by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "feed-time")
    val down = pull.dir > 0
    val wide = kit == Kit.News || kit == Kit.Clay
    Canvas(modifier.size(if (wide) 84.dp else 44.dp, 44.dp)) {
        when (kit) {
            Kit.Library -> library(k, ready, down, bg, fg, accent)
            Kit.Zen -> zen(k, ready, bg, fg, accent)
            Kit.Comic -> comic(k, ready, time, down, accent)
            Kit.News -> news(k, ready, bg, fg, accent)
            Kit.Sea -> sea(k, ready, time, bg, accent)
            Kit.Clay -> clay(k, ready, bg, accent)
            Kit.NewYear -> frost(k, ready, time, bg, accent)
            Kit.Ink -> news(k, ready, bg, fg, accent)
        }
    }
}

private fun DrawScope.chevron(color: Color, down: Boolean, size: Float, width: Float) {
    val c = center
    val h = size * 0.5f
    val sign = if (down) 1f else -1f
    val p = Path().apply {
        moveTo(c.x - size, c.y - h * sign)
        lineTo(c.x, c.y + h * sign)
        lineTo(c.x + size, c.y - h * sign)
    }
    drawPath(p, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.library(k: Float, ready: Float, down: Boolean, bg: Color, fg: Color, accent: Color) {
    val r = size.minDimension / 2f - 3.dp.toPx()
    val s = 1f + 0.12f * ready
    scale(s) {
        drawCircle(Color.Black.copy(alpha = 0.22f), r + 1.dp.toPx(), center + Offset(0f, 2.dp.toPx()))
        drawCircle(lerp(bg, accent, ready.coerceIn(0f, 1f)), r)
        drawCircle(accent.copy(alpha = 0.3f), r, style = Stroke(1.dp.toPx()))
        drawArc(
            accent,
            -90f,
            360f * k,
            false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round),
        )
        val d = 3.2.dp.toPx() * ready.coerceIn(0f, 1f)
        if (d > 0.3f) {
            rotate(45f) { drawRect(bg, Offset(center.x - d, center.y - d), Size(d * 2, d * 2)) }
        }
        chevron(lerp(fg.copy(alpha = 0.7f), Color.Transparent, ready.coerceIn(0f, 1f)), down, 5.dp.toPx(), 1.8.dp.toPx())
    }
}

private fun DrawScope.zen(k: Float, ready: Float, bg: Color, fg: Color, accent: Color) {
    val r = size.minDimension / 2f - 5.dp.toPx()
    drawCircle(bg, r + 4.dp.toPx())
    drawCircle(fg.copy(alpha = 0.08f), r + 4.dp.toPx(), style = Stroke(1.dp.toPx()))
    drawArc(
        accent,
        -110f,
        330f * k,
        false,
        topLeft = Offset(center.x - r, center.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(3.6.dp.toPx() * (0.7f + 0.3f * k), cap = StrokeCap.Round),
    )
    val dot = 6.dp.toPx() * ready.coerceIn(0f, 1.3f)
    if (dot > 0.2f) drawCircle(accent, dot)
}

private fun DrawScope.frost(k: Float, ready: Float, time: Float, bg: Color, accent: Color) {
    val r = size.minDimension / 2f - 4.dp.toPx()
    drawCircle(bg, r + 3.dp.toPx())
    drawCircle(accent.copy(alpha = 0.25f), r + 3.dp.toPx(), style = Stroke(1.dp.toPx()))
    val arm = r * (0.35f + 0.5f * k)
    val spin = time * 60f * ready
    for (i in 0 until 6) {
        val a = (i * 60f + spin) * PI.toFloat() / 180f
        val dir = Offset(kotlin.math.cos(a), sin(a))
        val tip = center + dir * arm
        drawLine(accent, center, tip, 2.dp.toPx(), cap = StrokeCap.Round)
        if (k > 0.45f) {
            val mid = center + dir * (arm * 0.62f)
            val side = arm * 0.3f * ((k - 0.45f) / 0.55f).coerceIn(0f, 1f)
            for (s in listOf(-1f, 1f)) {
                val b = a + s * 0.9f
                drawLine(accent, mid, mid + Offset(kotlin.math.cos(b), sin(b)) * side, 1.6.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
    val dot = 3.dp.toPx() * (0.6f + 0.6f * ready)
    drawCircle(Color.White, dot)
    drawCircle(accent, dot, style = Stroke(1.2.dp.toPx()))
}

private fun DrawScope.comic(k: Float, ready: Float, time: Float, down: Boolean, accent: Color) {
    val ink = Color(0xFF141210)
    val points = 12
    val outer = size.minDimension / 2f - 2.dp.toPx()
    val grow = 0.55f + 0.45f * k + 0.12f * ready
    val wobble = if (ready > 0.5f) sin(time * 2f * PI.toFloat() * 3f) * 7f else -4f
    fun star(dx: Float, dy: Float): Path = Path().apply {
        for (i in 0 until points * 2) {
            val a = (i / (points * 2f)) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val rr = if (i % 2 == 0) outer else outer * 0.68f
            val x = center.x + dx + cos(a) * rr
            val y = center.y + dy + sin(a) * rr
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    rotate(wobble) {
        scale(grow) {
            drawPath(star(2.5.dp.toPx(), 2.5.dp.toPx()), ink)
            drawPath(star(0f, 0f), accent)
            drawPath(star(0f, 0f), ink, style = Stroke(2.2.dp.toPx(), join = StrokeJoin.Round))
            chevron(ink, down, 4.5.dp.toPx(), 2.6.dp.toPx())
        }
    }
}

private fun DrawScope.news(k: Float, ready: Float, bg: Color, fg: Color, accent: Color) {
    val w = size.width - 6.dp.toPx()
    val h = 16.dp.toPx() + 4.dp.toPx() * ready
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    drawRect(Color.Black.copy(alpha = 0.2f), Offset(left + 2.dp.toPx(), top + 2.dp.toPx()), Size(w, h))
    drawRect(bg, Offset(left, top), Size(w, h))
    drawRect(lerp(accent, fg, 0f), Offset(left, top), Size(w * k, h))
    drawRect(fg, Offset(left, top - 3.dp.toPx()), Size(w, 3.dp.toPx() + 1.5.dp.toPx() * ready))
    drawRect(fg, Offset(left, top + h), Size(w, 1.dp.toPx()))
    drawRect(fg, Offset(left, top), Size(w, h), style = Stroke(1.dp.toPx()))
    val tick = 2.dp.toPx()
    for (i in 1 until 4) {
        val x = left + w * i / 4f
        drawLine(fg.copy(alpha = 0.35f), Offset(x, top + h - tick * 2), Offset(x, top + h), 1.dp.toPx())
    }
}

private fun DrawScope.sea(k: Float, ready: Float, time: Float, bg: Color, accent: Color) {
    val r = size.minDimension / 2f - 4.dp.toPx()
    val s = 1f + 0.1f * ready
    if (ready > 0.05f) {
        val ring = r + 6.dp.toPx() * time
        drawCircle(accent.copy(alpha = 0.45f * (1f - time) * ready.coerceIn(0f, 1f)), ring * s, style = Stroke(2.dp.toPx()))
    }
    scale(s) {
        drawCircle(bg, r)
        val level = center.y + r - 2f * r * k
        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
        clipPath(circle) {
            val wave = Path().apply {
                moveTo(0f, size.height)
                val amp = 2.4.dp.toPx() * (1f - k * 0.5f)
                var x = 0f
                lineTo(0f, level)
                while (x <= size.width) {
                    lineTo(x, level + amp * sin(time * 2f * PI.toFloat() + x / (r * 0.55f)))
                    x += 2.dp.toPx()
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(wave, accent.copy(alpha = 0.75f))
            drawCircle(Color.White.copy(alpha = 0.35f), 2.dp.toPx(), Offset(center.x - r * 0.35f, center.y - r * 0.4f))
        }
        drawCircle(accent, r, style = Stroke(1.6.dp.toPx()))
    }
}

private fun DrawScope.clay(k: Float, ready: Float, bg: Color, accent: Color) {
    val squishX = 1f + 0.08f * k * (1f - ready) + 0.06f * ready
    val squishY = 1f - 0.16f * k * (1f - ready) + 0.06f * ready
    val w = size.width - 10.dp.toPx()
    val h = 22.dp.toPx()
    val r = CornerRadius(h / 2f, h / 2f)
    val deep = lerp(accent, Color.Black, 0.3f)
    scale(squishX, squishY) {
        translate((size.width - w) / 2f, (size.height - h) / 2f - 2.dp.toPx()) {
            drawRoundRect(deep, Offset(0f, 4.dp.toPx()), Size(w, h), r)
            drawRoundRect(lerp(bg, accent, 0.25f), Offset.Zero, Size(w, h), r)
            val fill = (w * k).coerceAtLeast(h * k.coerceIn(0f, 1f))
            if (fill > 1f) drawRoundRect(accent, Offset.Zero, Size(fill, h), r)
            drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(h * 0.4f, 3.dp.toPx()), Size((w - h * 0.8f).coerceAtLeast(0f), 3.dp.toPx()), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
        }
    }
}
