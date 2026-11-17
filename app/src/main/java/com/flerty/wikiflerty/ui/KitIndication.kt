package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object KitIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = KitPressNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = 11
}

private class Splash(val at: Offset, val seed: Int, val decor: Decor) {
    var t = 0f
}

private class KitPressNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val splashes = ArrayList<Splash>(3)
    private val dent = Animatable(0f)
    private var dentAt = Offset.Unspecified
    private var seed = 0

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> press(i.pressPosition)
                    is PressInteraction.Release, is PressInteraction.Cancel -> release()
                }
            }
        }
    }

    private fun active(): Decor? {
        if (EInk.on || !Motion.enabled) return null
        return when (val d = AppStyle.kit.decor) {
            Decor.Library, Decor.Zen, Decor.Comic, Decor.News, Decor.Sea, Decor.Clay, Decor.Frost -> d
            else -> null
        }
    }

    private fun press(at: Offset) {
        val decor = active() ?: return
        if (decor == Decor.Clay) {
            dentAt = at
            coroutineScope.launch { dent.animateTo(1f, tween(140)) { invalidateDraw() } }
            return
        }
        val s = Splash(at, ++seed, decor)
        if (splashes.size >= 3) splashes.removeAt(0)
        splashes.add(s)
        coroutineScope.launch {
            val a = Animatable(0f)
            a.animateTo(1f, tween(duration(decor), easing = LinearEasing)) {
                s.t = value
                invalidateDraw()
            }
            splashes.remove(s)
            invalidateDraw()
        }
    }

    private fun release() {
        if (dent.targetValue == 0f && dent.value == 0f) return
        coroutineScope.launch { dent.animateTo(0f, tween(320)) { invalidateDraw() } }
    }

    private fun duration(decor: Decor): Int = when (decor) {
        Decor.Library -> 900
        Decor.Zen -> 650
        Decor.Comic -> 380
        Decor.News -> 520
        Decor.Sea -> 950
        Decor.Frost -> 850
        else -> 500
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (splashes.isEmpty() && dent.value <= 0.001f) return
        clipRect { effects() }
    }

    private fun DrawScope.effects() {
        if (dent.value > 0.001f && dentAt.isSpecified) clayDent(dentAt, dent.value)
        for (s in splashes) {
            when (s.decor) {
                Decor.Library -> dust(s)
                Decor.Zen -> ink(s)
                Decor.Comic -> burst(s)
                Decor.News -> halftone(s)
                Decor.Sea -> bubbles(s)
                Decor.Frost -> snow(s)
                else -> Unit
            }
        }
    }

    private fun DrawScope.reach(at: Offset): Float {
        val far = maxOf(
            hypot(at.x, at.y),
            hypot(size.width - at.x, at.y),
            hypot(at.x, size.height - at.y),
            hypot(size.width - at.x, size.height - at.y),
        )
        return far.coerceIn(24f * density, 170f * density)
    }

    private fun rnd(seed: Int, i: Int): Float {
        var x = seed * 374761393 + i * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
    }

    private fun ease(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

    private fun DrawScope.dust(s: Splash) {
        val t = s.t
        val r = reach(s.at)
        val gold = if (AppStyle.light) Color(0xFF9C6A1C) else Color(0xFFE0B04A)
        val glowA = (1f - t) * (if (AppStyle.light) 0.16f else 0.20f)
        drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = glowA), Color.Transparent), s.at, r * (0.35f + 0.5f * ease(t))), r * (0.35f + 0.5f * ease(t)), s.at)
        for (i in 0 until 9) {
            val a = rnd(s.seed, i) * 2 * PI.toFloat()
            val d = r * (0.12f + 0.45f * rnd(s.seed, i + 20)) * ease(t)
            val lift = 26f * density * t * (0.4f + rnd(s.seed, i + 40))
            val p = Offset(s.at.x + cos(a) * d, s.at.y + sin(a) * d * 0.6f - lift)
            val fade = (if (t < 0.15f) t / 0.15f else 1f - (t - 0.15f) / 0.85f).coerceIn(0f, 1f)
            drawCircle(gold.copy(alpha = 0.75f * fade), (0.9f + 1.4f * rnd(s.seed, i + 60)) * density, p)
        }
    }

    private fun DrawScope.ink(s: Splash) {
        val t = s.t
        val r = reach(s.at) * 0.75f
        val color = if (AppStyle.light) Color(0xFF1E1E1E) else Color(0xFFF4ECDD)
        val grow = ease(t)
        val alpha = (if (AppStyle.light) 0.12f else 0.10f) * (1f - t * t)
        val path = Path()
        val n = 28
        for (k in 0..n) {
            val a = k / n.toFloat() * 2 * PI.toFloat()
            val wob = 1f + 0.10f * sin(a * 3 + s.seed) + 0.06f * sin(a * 7 + s.seed * 2)
            val rr = r * grow * wob
            val p = Offset(s.at.x + cos(a) * rr, s.at.y + sin(a) * rr)
            if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, color.copy(alpha = alpha))
        for (i in 0 until 5) {
            val a = rnd(s.seed, i) * 2 * PI.toFloat()
            val d = r * grow * (1.05f + 0.25f * rnd(s.seed, i + 10))
            drawCircle(color.copy(alpha = alpha * 1.4f), (1.2f + 1.8f * rnd(s.seed, i + 30)) * density * grow, Offset(s.at.x + cos(a) * d, s.at.y + sin(a) * d))
        }
    }

    private fun DrawScope.burst(s: Splash) {
        val t = s.t
        val r = reach(s.at) * 0.6f
        val accent = AppColors.Accent
        val inkC = if (AppStyle.light) Color(0xFF111111) else Color.White
        val fade = 1f - t
        val star = Path()
        val spikes = 10
        val outer = r * (0.35f + 0.65f * ease(t))
        for (k in 0 until spikes * 2) {
            val a = k / (spikes * 2f) * 2 * PI.toFloat() + s.seed
            val rr = if (k % 2 == 0) outer else outer * 0.55f
            val p = Offset(s.at.x + cos(a) * rr, s.at.y + sin(a) * rr)
            if (k == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
        }
        star.close()
        drawPath(star, accent.copy(alpha = 0.22f * fade))
        for (k in 0 until 8) {
            val a = (k / 8f + rnd(s.seed, k) * 0.05f) * 2 * PI.toFloat()
            val from = outer * 0.9f
            val to = outer * (1.15f + 0.2f * rnd(s.seed, k + 9))
            drawLine(
                inkC.copy(alpha = 0.35f * fade),
                Offset(s.at.x + cos(a) * from, s.at.y + sin(a) * from),
                Offset(s.at.x + cos(a) * to, s.at.y + sin(a) * to),
                strokeWidth = 2f * density,
                cap = StrokeCap.Round,
            )
        }
    }

    private fun DrawScope.halftone(s: Splash) {
        val t = s.t
        val r = reach(s.at)
        val inkC = if (AppStyle.light) Color(0xFF141414) else Color(0xFFECE6D8)
        val front = r * ease(t)
        val band = 22f * density
        val step = 7f * density
        val x0 = ((s.at.x - front - band) / step).toInt() - 1
        val x1 = ((s.at.x + front + band) / step).toInt() + 1
        val y0 = ((s.at.y - front - band) / step).toInt() - 1
        val y1 = ((s.at.y + front + band) / step).toInt() + 1
        val fade = 1f - t
        for (gx in x0..x1) for (gy in y0..y1) {
            val c = Offset(gx * step + if (gy % 2 == 0) 0f else step / 2, gy * step)
            val d = (c - s.at).getDistance()
            val k = 1f - kotlin.math.abs(d - front) / band
            if (k <= 0f) continue
            drawCircle(inkC.copy(alpha = 0.32f * fade), (0.5f + 1.6f * k) * density, c)
        }
    }

    private fun DrawScope.bubbles(s: Splash) {
        val t = s.t
        val r = reach(s.at)
        val white = Color.White
        val ringR = r * 0.6f * ease(t)
        drawCircle(white.copy(alpha = 0.30f * (1f - t)), ringR, s.at, style = Stroke(1.6f * density))
        for (i in 0 until 6) {
            val delay = rnd(s.seed, i) * 0.3f
            val lt = ((t - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (lt <= 0f) continue
            val sway = sin(lt * 6f + i) * 6f * density
            val x = s.at.x + (rnd(s.seed, i + 10) - 0.5f) * r * 0.5f + sway
            val y = s.at.y - lt * r * (0.5f + 0.4f * rnd(s.seed, i + 20))
            val rad = (2f + 3.5f * rnd(s.seed, i + 30)) * density * (0.6f + 0.4f * lt)
            val a = (1f - lt) * 0.7f
            drawCircle(white.copy(alpha = a * 0.25f), rad, Offset(x, y))
            drawCircle(white.copy(alpha = a), rad, Offset(x, y), style = Stroke(1f * density))
            drawCircle(white.copy(alpha = a), rad * 0.28f, Offset(x - rad * 0.35f, y - rad * 0.35f))
        }
    }

    private fun DrawScope.snow(s: Splash) {
        val t = s.t
        val r = reach(s.at)
        val ice = if (AppStyle.light) Color(0xFF8DB7D6) else Color.White
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f * (1f - t)), Color.Transparent), s.at, r * 0.5f), r * 0.5f, s.at)
        for (i in 0 until 10) {
            val a = rnd(s.seed, i) * 2 * PI.toFloat()
            val d = r * (0.15f + 0.4f * rnd(s.seed, i + 10)) * ease(t)
            val fall = 18f * density * t * t
            val p = Offset(s.at.x + cos(a) * d, s.at.y + sin(a) * d + fall)
            val fade = (1f - t).coerceIn(0f, 1f)
            val rad = (1.4f + 2.4f * rnd(s.seed, i + 20)) * density
            if (i % 3 == 0) {
                for (k in 0 until 3) {
                    val aa = k * PI.toFloat() / 3 + t * 2
                    drawLine(ice.copy(alpha = 0.85f * fade), Offset(p.x - cos(aa) * rad, p.y - sin(aa) * rad), Offset(p.x + cos(aa) * rad, p.y + sin(aa) * rad), strokeWidth = 0.9f * density, cap = StrokeCap.Round)
                }
            } else {
                drawCircle(ice.copy(alpha = 0.8f * fade), rad * 0.45f, p)
            }
        }
    }

    private fun DrawScope.clayDent(at: Offset, v: Float) {
        val r = reach(at) * 0.55f
        val dark = Color.Black.copy(alpha = (if (AppStyle.light) 0.07f else 0.16f) * v)
        val lightC = Color.White.copy(alpha = (if (AppStyle.light) 0.45f else 0.08f) * v)
        drawCircle(Brush.radialGradient(listOf(dark, Color.Transparent), at, r), r, at)
        drawCircle(Brush.radialGradient(listOf(Color.Transparent, lightC, Color.Transparent), Offset(at.x + r * 0.08f, at.y + r * 0.1f), r * 1.1f), r * 1.1f, Offset(at.x + r * 0.08f, at.y + r * 0.1f))
    }
}
