package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val Ink = Color(0xFF141210)

private val ClayShine = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.14f))

@Composable
fun KitSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val decor = AppStyle.decor
    val bouncy = decor == Decor.Comic || decor == Decor.Clay
    val p by animateFloatAsState(
        if (checked) 1f else 0f,
        if (!Motion.enabled) tween(0) else if (bouncy) spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow) else tween(Motion.ms(if (decor == Decor.News) 140 else 240)),
        label = "kit-switch",
    )
    val accent = AppColors.Accent
    val onAccent = AppColors.OnAccent
    val line = AppColors.Line
    val faint = AppColors.Faint
    val text = AppColors.Text
    val field = AppColors.Field
    val border = AppColors.PanelBorder
    val light = AppStyle.light
    Canvas(
        modifier
            .size(54.dp, 34.dp)
            .toggleable(checked, remember { MutableInteractionSource() }, null, enabled, Role.Switch, onCheckedChange),
    ) {
        val d = density
        val w = size.width
        val h = size.height
        val k = p.coerceIn(0f, 1f)
        val cy = h / 2
        when (decor) {
            Decor.Library -> {
                val th = 22 * d
                val top = cy - th / 2
                val r = CornerRadius(th / 2)
                drawRoundRect(lerp(field, accent.copy(alpha = 0.38f).compositeOn(field), k), Offset(2 * d, top), Size(w - 4 * d, th), r)
                drawRoundRect(lerp(border, accent, k), Offset(2 * d, top), Size(w - 4 * d, th), r, style = Stroke(1.3f * d))
                drawLine(Color.Black.copy(alpha = 0.25f), Offset(13 * d, top + 3 * d), Offset(w - 13 * d, top + 3 * d), 1 * d, cap = StrokeCap.Round)
                val x = 15 * d + (w - 30 * d) * p
                val kr = 12 * d
                drawCircle(Color.Black.copy(alpha = 0.3f), kr, Offset(x, cy + 2 * d))
                val gold = lerp(Color(0xFF9A8A6A), Color(0xFFE8C46A), k)
                drawCircle(Brush.radialGradient(listOf(lerp(gold, Color.White, 0.45f), gold, lerp(gold, Color.Black, 0.35f)), center = Offset(x - 3 * d, cy - 4 * d), radius = kr * 1.5f), kr, Offset(x, cy))
                drawCircle(lerp(gold, Color.Black, 0.45f), kr, Offset(x, cy), style = Stroke(1 * d))
                drawCircle(lerp(gold, Color.Black, 0.3f), 3.4f * d, Offset(x, cy), style = Stroke(1 * d))
            }
            Decor.Zen -> {
                val x0 = 12 * d
                val x1 = w - 12 * d
                val x = x0 + (x1 - x0) * p
                drawLine(line, Offset(x0, cy), Offset(x1, cy), 2 * d, cap = StrokeCap.Round)
                drawLine(accent, Offset(x0, cy), Offset(x, cy), 2 * d, cap = StrokeCap.Round)
                drawCircle(faint, 2.2f * d, Offset(x0, cy))
                drawCircle(faint, 2.2f * d, Offset(x1, cy))
                val stone = lerp(if (light) Color(0xFF8E8A82) else Color(0xFFB9B4A8), accent, k)
                drawOval(Color.Black.copy(alpha = 0.2f), Offset(x - 11 * d, cy + 6 * d), Size(22 * d, 5 * d))
                drawOval(stone, Offset(x - 12 * d, cy - 9 * d), Size(24 * d, 18 * d))
                drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), startY = cy - 9 * d, endY = cy + 2 * d), Offset(x - 9 * d, cy - 8 * d), Size(16 * d, 8 * d))
            }
            Decor.Comic -> {
                val th = 24 * d
                val top = cy - th / 2
                val r = CornerRadius(th / 2)
                drawRoundRect(Ink, Offset(5 * d, top + 3 * d), Size(w - 8 * d, th), r)
                drawRoundRect(lerp(if (light) Color.White else Color(0xFF3A3A3A), accent, k), Offset(2 * d, top), Size(w - 8 * d, th), r)
                drawRoundRect(Ink, Offset(2 * d, top), Size(w - 8 * d, th), r, style = Stroke(2.4f * d))
                val x = 14 * d + (w - 32 * d) * p
                drawCircle(Color.White, 9 * d, Offset(x, cy))
                drawCircle(Ink, 9 * d, Offset(x, cy), style = Stroke(2.4f * d))
                if (k > 0.5f) {
                    drawLine(Ink, Offset(x, cy - 4 * d), Offset(x, cy + 1 * d), 2.2f * d, cap = StrokeCap.Round)
                    drawCircle(Ink, 1.2f * d, Offset(x, cy + 4.4f * d))
                }
            }
            Decor.News -> {
                val th = 22 * d
                val top = cy - th / 2
                drawRect(lerp(Color.Transparent, accent, k), Offset(3 * d, top), Size(w - 6 * d, th))
                drawRect(text, Offset(3 * d, top), Size(w - 6 * d, th), style = Stroke(1.5f * d))
                val x = 6 * d + (w - 12 * d - 18 * d) * p
                drawRect(lerp(text, onAccent, k), Offset(x, top + 3 * d), Size(18 * d, th - 6 * d))
                for (i in 0 until 3) {
                    val lx = x + (5 + i * 4) * d
                    drawLine(lerp(field, accent, k), Offset(lx, top + 7 * d), Offset(lx, top + th - 7 * d), 1 * d)
                }
            }
            Decor.Sea -> {
                val th = 24 * d
                val top = cy - th / 2
                val r = CornerRadius(th / 2)
                drawRoundRect(Color.White.copy(alpha = if (light) 0.5f else 0.08f), Offset(2 * d, top), Size(w - 4 * d, th), r)
                if (k > 0.02f) {
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(lerp(accent, Color.White, 0.25f), accent)),
                        Offset(2 * d, top),
                        Size((w - 4 * d) * (0.45f + 0.55f * k), th),
                        r,
                        alpha = k,
                    )
                }
                drawRoundRect(lerp(Color.White.copy(alpha = 0.4f), accent, k * 0.6f), Offset(2 * d, top), Size(w - 4 * d, th), r, style = Stroke(1.2f * d))
                val x = 14 * d + (w - 28 * d) * p
                drawCircle(Color.White.copy(alpha = 0.92f), 9.5f * d, Offset(x, cy))
                drawCircle(lerp(Color(0xFF9FD8E8), accent, k), 9.5f * d, Offset(x, cy), style = Stroke(1.4f * d))
                drawCircle(Color.White, 2.6f * d, Offset(x - 3 * d, cy - 3.2f * d))
                drawArc(lerp(Color(0xFF9FD8E8), accent, k).copy(alpha = 0.6f), 20f, 70f, false, Offset(x - 6 * d, cy - 6 * d), Size(12 * d, 12 * d), style = Stroke(1.2f * d, cap = StrokeCap.Round))
            }
            Decor.Clay -> {
                val th = 26 * d
                val top = cy - th / 2
                val r = CornerRadius(th / 2)
                val track = lerp(line, lerp(accent, Color.White, 0.35f), k)
                drawRoundRect(lerp(track, Color.Black, 0.22f), Offset(2 * d, top + 2 * d), Size(w - 4 * d, th), r)
                drawRoundRect(track, Offset(2 * d, top), Size(w - 4 * d, th), r)
                drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent), startY = top, endY = top + 9 * d), Offset(2 * d, top), Size(w - 4 * d, th), r)
                val x = 15 * d + (w - 30 * d) * p
                val stretch = 1f + 0.22f * sin(PI.toFloat() * k)
                val blob = lerp(Color.White, accent, k)
                scale(stretch, 2f - stretch, Offset(x, cy)) {
                    drawCircle(lerp(blob, Color.Black, 0.3f), 11.5f * d, Offset(x, cy + 2.5f * d))
                    drawCircle(blob, 11.5f * d, Offset(x, cy))
                    drawCircle(Brush.linearGradient(ClayShine, start = Offset(x - 10 * d, cy - 10 * d), end = Offset(x + 10 * d, cy + 10 * d)), 11.5f * d, Offset(x, cy))
                    drawOval(Color.White.copy(alpha = 0.6f), Offset(x - 6 * d, cy - 8 * d), Size(7 * d, 4 * d))
                }
            }
            Decor.None -> {
                val th = 26 * d
                val top = cy - th / 2
                val r = CornerRadius(th / 2)
                drawRoundRect(lerp(line, accent, k), Offset(2 * d, top), Size(w - 4 * d, th), r)
                val x = 15 * d + (w - 30 * d) * p
                drawCircle(lerp(faint, onAccent, k), (7 + 3 * k) * d, Offset(x, cy))
            }
        }
    }
}

private fun Color.compositeOn(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

private fun thumbSize(decor: Decor): Pair<Dp, Dp> = when (decor) {
    Decor.Library -> 14.dp to 26.dp
    Decor.Zen -> 22.dp to 22.dp
    Decor.Comic -> 26.dp to 26.dp
    Decor.News -> 10.dp to 26.dp
    Decor.Sea -> 24.dp to 24.dp
    Decor.Clay -> 28.dp to 28.dp
    Decor.None -> 20.dp to 20.dp
}

@Composable
fun KitSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 0,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    accent: Color = AppColors.Accent,
) {
    val decor = AppStyle.decor
    val (tw, th) = thumbSize(decor)
    val line = AppColors.Line
    val text = AppColors.Text
    val field = AppColors.Field
    val faint = AppColors.Faint
    val light = AppStyle.light
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(),
        steps = steps,
        valueRange = valueRange,
        thumb = {
            Canvas(Modifier.size(tw, th)) { sliderThumb(decor, accent, text, field, light) }
        },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val frac = fraction(state)
                val half = tw.toPx() / 2
                val x = half + frac * (size.width - tw.toPx())
                sliderTrack(decor, x, accent, line, text, faint, light)
            }
        },
    )
}

private fun fraction(state: SliderState): Float {
    val span = state.valueRange.endInclusive - state.valueRange.start
    return if (span <= 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
}

private fun DrawScope.sliderTrack(decor: Decor, x: Float, accent: Color, line: Color, text: Color, faint: Color, light: Boolean) {
    val d = density
    val w = size.width
    val cy = size.height / 2
    when (decor) {
        Decor.Library -> {
            drawRoundRect(Color.Black.copy(alpha = if (light) 0.18f else 0.4f), Offset(0f, cy - 2.5f * d), Size(w, 5 * d), CornerRadius(2.5f * d))
            drawRoundRect(line, Offset(0f, cy - 2.5f * d), Size(w, 5 * d), CornerRadius(2.5f * d), style = Stroke(0.8f * d))
            drawRoundRect(Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.3f), accent, lerp(accent, Color.Black, 0.25f)), startY = cy - 2.5f * d, endY = cy + 2.5f * d), Offset(0f, cy - 2.5f * d), Size(x, 5 * d), CornerRadius(2.5f * d))
        }
        Decor.Zen -> {
            drawLine(line, Offset(2 * d, cy), Offset(w - 2 * d, cy), 2 * d, cap = StrokeCap.Round)
            drawLine(accent, Offset(2 * d, cy), Offset(x, cy), 2 * d, cap = StrokeCap.Round)
            drawCircle(faint, 2 * d, Offset(2 * d, cy))
            drawCircle(faint, 2 * d, Offset(w - 2 * d, cy))
        }
        Decor.Comic -> {
            val r = CornerRadius(5 * d)
            drawRoundRect(Ink, Offset(2 * d, cy - 3 * d), Size(w - 2 * d, 10 * d), r)
            drawRoundRect(if (light) Color.White else Color(0xFF3A3A3A), Offset(0f, cy - 5 * d), Size(w - 2 * d, 10 * d), r)
            drawRoundRect(accent, Offset(0f, cy - 5 * d), Size(x.coerceAtMost(w - 2 * d), 10 * d), r)
            drawRoundRect(Ink, Offset(0f, cy - 5 * d), Size(w - 2 * d, 10 * d), r, style = Stroke(2.2f * d))
        }
        Decor.News -> {
            drawLine(text, Offset(0f, cy), Offset(w, cy), 1.4f * d)
            for (i in 0..10) {
                val tx = w * i / 10f
                val long = i % 5 == 0
                drawLine(text.copy(alpha = if (long) 0.9f else 0.45f), Offset(tx, cy), Offset(tx, cy + (if (long) 7 else 4) * d), 1 * d)
            }
            drawRect(accent, Offset(0f, cy - 2.5f * d), Size(x, 5 * d))
        }
        Decor.Sea -> {
            val r = CornerRadius(5 * d)
            drawRoundRect(Color.White.copy(alpha = if (light) 0.5f else 0.09f), Offset(0f, cy - 5 * d), Size(w, 10 * d), r)
            drawRoundRect(Brush.horizontalGradient(listOf(lerp(accent, Color.White, 0.35f), accent), endX = x.coerceAtLeast(1f)), Offset(0f, cy - 5 * d), Size(x, 10 * d), r)
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(0f, cy - 5 * d), Size(w, 10 * d), r, style = Stroke(1 * d))
            drawLine(Color.White.copy(alpha = 0.5f), Offset(5 * d, cy - 2.5f * d), Offset((x - 5 * d).coerceAtLeast(5 * d), cy - 2.5f * d), 1.4f * d, cap = StrokeCap.Round)
        }
        Decor.Clay -> {
            val r = CornerRadius(7 * d)
            drawRoundRect(lerp(line, Color.Black, 0.2f), Offset(0f, cy - 5 * d), Size(w, 14 * d), r)
            drawRoundRect(line, Offset(0f, cy - 7 * d), Size(w, 14 * d), r)
            drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.14f), Color.Transparent), startY = cy - 7 * d, endY = cy - 1 * d), Offset(0f, cy - 7 * d), Size(w, 14 * d), r)
            if (x > 4 * d) {
                drawRoundRect(accent, Offset(0f, cy - 7 * d), Size(x, 14 * d), r)
                drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent, Color.Black.copy(alpha = 0.12f)), startY = cy - 7 * d, endY = cy + 7 * d), Offset(0f, cy - 7 * d), Size(x, 14 * d), r)
            }
        }
        Decor.None -> {
            drawRoundRect(line, Offset(0f, cy - 2 * d), Size(w, 4 * d), CornerRadius(2 * d))
            drawRoundRect(accent, Offset(0f, cy - 2 * d), Size(x, 4 * d), CornerRadius(2 * d))
        }
    }
}

private fun DrawScope.sliderThumb(decor: Decor, accent: Color, text: Color, field: Color, light: Boolean) {
    val d = density
    val w = size.width
    val h = size.height
    val c = Offset(w / 2, h / 2)
    when (decor) {
        Decor.Library -> {
            val gold = Color(0xFFE8C46A)
            drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(1 * d, 3 * d), Size(w - 2 * d, h - 3 * d), CornerRadius(4 * d))
            drawRoundRect(Brush.horizontalGradient(listOf(lerp(gold, Color.Black, 0.3f), lerp(gold, Color.White, 0.4f), gold, lerp(gold, Color.Black, 0.35f))), Offset(1 * d, 1 * d), Size(w - 2 * d, h - 3 * d), CornerRadius(4 * d))
            drawRoundRect(Color(0xFF6E5220), Offset(1 * d, 1 * d), Size(w - 2 * d, h - 3 * d), CornerRadius(4 * d), style = Stroke(0.9f * d))
            for (i in -1..1) drawLine(Color(0xFF6E5220).copy(alpha = 0.7f), Offset(4 * d, c.y - 1 * d + i * 4 * d), Offset(w - 4 * d, c.y - 1 * d + i * 4 * d), 0.9f * d)
        }
        Decor.Zen -> {
            drawCircle(field, 9.5f * d, c)
            drawArc(accent, -100f, 325f, false, Offset(c.x - 9 * d, c.y - 9 * d), Size(18 * d, 18 * d), style = Stroke(3.2f * d, cap = StrokeCap.Round))
            drawCircle(accent, 2.6f * d, c)
        }
        Decor.Comic -> {
            drawCircle(Ink, 10.5f * d, Offset(c.x + 2 * d, c.y + 2 * d))
            drawCircle(Color.White, 10.5f * d, c)
            drawCircle(Ink, 10.5f * d, c, style = Stroke(2.4f * d))
            drawCircle(accent, 4.2f * d, c)
            drawCircle(Ink, 4.2f * d, c, style = Stroke(1.6f * d))
        }
        Decor.News -> {
            drawRect(text, Offset(w / 2 - 3 * d, 2 * d), Size(6 * d, h - 4 * d))
            val tip = Path().apply { moveTo(w / 2 - 5 * d, 0f); lineTo(w / 2 + 5 * d, 0f); lineTo(w / 2, 6 * d); close() }
            drawPath(tip, accent)
        }
        Decor.Sea -> {
            drawCircle(Color.White.copy(alpha = 0.95f), 10.5f * d, c)
            drawCircle(accent, 10.5f * d, c, style = Stroke(1.6f * d))
            drawCircle(accent.copy(alpha = 0.2f), 7 * d, c)
            drawCircle(Color.White, 2.8f * d, Offset(c.x - 3.4f * d, c.y - 3.6f * d))
            drawArc(accent.copy(alpha = 0.7f), 15f, 75f, false, Offset(c.x - 7 * d, c.y - 7 * d), Size(14 * d, 14 * d), style = Stroke(1.3f * d, cap = StrokeCap.Round))
        }
        Decor.Clay -> {
            val blob = lerp(accent, Color.White, 0.25f)
            drawCircle(lerp(accent, Color.Black, 0.32f), 12 * d, Offset(c.x, c.y + 2.5f * d))
            drawCircle(blob, 12 * d, c)
            drawCircle(Brush.linearGradient(ClayShine, start = Offset(c.x - 10 * d, c.y - 10 * d), end = Offset(c.x + 10 * d, c.y + 10 * d)), 12 * d, c)
            drawOval(Color.White.copy(alpha = 0.65f), Offset(c.x - 7 * d, c.y - 8.5f * d), Size(8 * d, 4.5f * d))
        }
        Decor.None -> {
            drawCircle(text, 9 * d, c)
        }
    }
}

@Composable
fun KitSpinner(modifier: Modifier = Modifier.size(20.dp), color: Color = AppColors.Accent) {
    val decor = AppStyle.decor
    if (decor == Decor.None || !Motion.enabled) {
        CircularProgressIndicator(modifier, color = color, strokeWidth = 2.dp)
        return
    }
    val loop = rememberInfiniteTransition(label = "kit-spinner")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "kit-spinner-t")
    Canvas(modifier) {
        val s = size.minDimension
        val c = center
        val two = 2f * PI.toFloat()
        when (decor) {
            Decor.Library -> {
                val r = s * 0.36f
                rotate(t * 360f, c) {
                    val sq = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }
                    drawPath(sq, color, style = Stroke(s * 0.09f, join = StrokeJoin.Round))
                }
                rotate(-t * 360f, c) {
                    val q = r * (0.36f + 0.12f * sin(t * two * 2))
                    val inner = Path().apply { moveTo(c.x, c.y - q); lineTo(c.x + q, c.y); lineTo(c.x, c.y + q); lineTo(c.x - q, c.y); close() }
                    drawPath(inner, color)
                }
                for (k in 0 until 4) {
                    val a = t * two + k * two / 4
                    drawCircle(color.copy(alpha = 0.7f), s * 0.045f, Offset(c.x + cos(a) * s * 0.46f, c.y + sin(a) * s * 0.46f))
                }
            }
            Decor.Zen -> {
                val r = s * 0.38f
                val sweep = 50f + 260f * abs(sin(PI.toFloat() * t))
                drawArc(color, t * 720f - 90f, sweep, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(s * (0.08f + 0.07f * abs(sin(PI.toFloat() * t))), cap = StrokeCap.Round))
            }
            Decor.Comic -> {
                val step = (t * 8).toInt() / 8f
                val pulse = 0.85f + 0.15f * abs(sin(t * two * 2))
                val star = Path()
                for (k in 0 until 16) {
                    val rr = (if (k % 2 == 0) s * 0.48f else s * 0.28f) * pulse
                    val a = two * k / 16f + step * two / 2f
                    if (k == 0) star.moveTo(c.x + cos(a) * rr, c.y + sin(a) * rr) else star.lineTo(c.x + cos(a) * rr, c.y + sin(a) * rr)
                }
                star.close()
                drawPath(star, color)
                drawPath(star, Ink, style = Stroke(s * 0.075f, join = StrokeJoin.Round))
            }
            Decor.News -> {
                val bw = s * 0.2f
                for (k in 0 until 3) {
                    val hh = s * (0.3f + 0.6f * abs(sin(t * two + k * 0.9f)))
                    drawRect(color, Offset(c.x - s * 0.4f + k * s * 0.3f, c.y + s * 0.45f - hh), Size(bw, hh))
                }
                drawRect(color, Offset(c.x - s * 0.46f, c.y + s * 0.45f), Size(s * 0.92f, s * 0.05f))
            }
            Decor.Sea -> {
                for (k in 0 until 3) {
                    val ph = (t + k / 3f) % 1f
                    val r = s * (0.1f + 0.08f * ((k + 1) % 3))
                    val x = c.x + (k - 1) * s * 0.28f + sin(ph * two) * s * 0.05f
                    val y = c.y + s * 0.42f - ph * s * 0.84f
                    val a = sin(PI.toFloat() * ph)
                    drawCircle(color.copy(alpha = a), r, Offset(x, y), style = Stroke(s * 0.065f))
                    drawCircle(color.copy(alpha = a * 0.9f), r * 0.22f, Offset(x - r * 0.35f, y - r * 0.35f))
                }
            }
            Decor.Clay -> {
                val b = abs(sin(t * two))
                val y = c.y + s * 0.24f - b * s * 0.44f
                val squash = if (b < 0.25f) 1f + (0.25f - b) * 1.2f else 1f - (b - 0.25f) * 0.12f
                drawOval(Color.Black.copy(alpha = 0.18f * (1f - b * 0.6f)), Offset(c.x - s * 0.3f * (1f - b * 0.4f), c.y + s * 0.42f), Size(s * 0.6f * (1f - b * 0.4f), s * 0.08f))
                val r = s * 0.24f
                scale(squash, 2f - squash, Offset(c.x, y + r)) {
                    drawCircle(color, r, Offset(c.x, y))
                    drawCircle(Brush.linearGradient(ClayShine, start = Offset(c.x - r, y - r), end = Offset(c.x + r, y + r)), r, Offset(c.x, y))
                    drawOval(Color.White.copy(alpha = 0.6f), Offset(c.x - r * 0.6f, y - r * 0.7f), Size(r * 0.6f, r * 0.35f))
                }
            }
            Decor.None -> Unit
        }
    }
}

@Composable
fun KitLinearProgress(modifier: Modifier = Modifier, color: Color = AppColors.Accent) {
    val decor = AppStyle.decor
    val line = AppColors.Line
    val text = AppColors.Text
    val loop = rememberInfiniteTransition(label = "kit-linear")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "kit-linear-t")
    val hgt = when (decor) {
        Decor.Comic -> 8.dp
        Decor.Clay -> 8.dp
        Decor.Sea -> 8.dp
        Decor.Zen -> 6.dp
        else -> 4.dp
    }
    Canvas(modifier.height(hgt)) {
        val d = density
        val w = size.width
        val h = size.height
        val cy = h / 2
        val seg = w * 0.34f
        val x = -seg + (w + seg) * t
        when (decor) {
            Decor.Library -> {
                drawRect(line, Offset(0f, cy - 1 * d), Size(w, 2 * d))
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color, lerp(color, Color.White, 0.5f), color, Color.Transparent), startX = x, endX = x + seg), Offset(x, cy - 2 * d), Size(seg, 4 * d))
            }
            Decor.Zen -> {
                drawLine(line, Offset(0f, cy), Offset(w, cy), 1.5f * d)
                val px = w * t
                drawLine(color, Offset((px - seg * 0.6f).coerceAtLeast(0f), cy), Offset(px, cy), 1.5f * d, cap = StrokeCap.Round)
                rotate(t * 720f, Offset(px, cy)) { drawOval(color, Offset(px - 4 * d, cy - 2.5f * d), Size(8 * d, 5 * d)) }
            }
            Decor.Comic -> {
                drawRect(color, Offset(0f, 0f), Size(w, h))
                val stripe = 14 * d
                var sx = -stripe * 2 + (t * stripe * 2)
                while (sx < w + stripe) {
                    val p = Path().apply { moveTo(sx, h); lineTo(sx + stripe * 0.5f, 0f); lineTo(sx + stripe, 0f); lineTo(sx + stripe * 0.5f, h); close() }
                    drawPath(p, Ink)
                    sx += stripe
                }
                drawRect(Ink, Offset(0f, 0f), Size(w, h), style = Stroke(2 * d))
            }
            Decor.News -> {
                drawRect(text.copy(alpha = 0.25f), Offset(0f, cy - 0.5f * d), Size(w, 1 * d))
                val block = 10 * d
                var bx = x
                while (bx < x + seg) {
                    if (bx > -block && bx < w) drawRect(color, Offset(bx, 0f), Size(block * 0.7f, h))
                    bx += block
                }
            }
            Decor.Sea -> {
                val p = Path()
                var px = 0f
                p.moveTo(0f, cy)
                while (px <= w) {
                    p.lineTo(px, cy + sin(px / (12 * d) + t * 2f * PI.toFloat()) * (h * 0.32f))
                    px += 3 * d
                }
                drawPath(p, line, style = Stroke(1.5f * d, cap = StrokeCap.Round))
                drawPath(p, Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent), startX = x, endX = x + seg), style = Stroke(2.4f * d, cap = StrokeCap.Round))
            }
            Decor.Clay -> {
                val r = CornerRadius(h / 2)
                drawRoundRect(line, Offset(0f, 0f), Size(w, h), r)
                val stretch = seg * (0.7f + 0.3f * abs(sin(t * 2f * PI.toFloat())))
                val left = x.coerceIn(0f, w)
                val right = (x + stretch).coerceIn(0f, w)
                if (right - left > h) {
                    drawRoundRect(color, Offset(left, 0f), Size(right - left, h), r)
                    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.12f))), Offset(left, 0f), Size(right - left, h), r)
                }
            }
            Decor.None -> {
                drawRect(line, Offset(0f, 0f), Size(w, h))
                drawRect(color, Offset(x.coerceAtLeast(0f), 0f), Size((seg).coerceAtMost(w - x.coerceAtLeast(0f)).coerceAtLeast(0f), h))
            }
        }
    }
}
