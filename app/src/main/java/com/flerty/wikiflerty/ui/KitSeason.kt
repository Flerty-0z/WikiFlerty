package com.flerty.wikiflerty.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

enum class Season { Winter, Spring, Summer, Autumn }

enum class DayPart { Morning, Day, Evening, Night }

data class KitMoment(val season: Season, val part: DayPart) {
    companion object {
        fun now(): KitMoment {
            val season = when (LocalDate.now().monthValue) {
                12, 1, 2 -> Season.Winter
                3, 4, 5 -> Season.Spring
                6, 7, 8 -> Season.Summer
                else -> Season.Autumn
            }
            val part = when (LocalTime.now().hour) {
                in 5..10 -> DayPart.Morning
                in 11..16 -> DayPart.Day
                in 17..21 -> DayPart.Evening
                else -> DayPart.Night
            }
            return KitMoment(season, part)
        }
    }
}

@Composable
fun rememberKitMoment(): KitMoment? {
    if (!AppStyle.current.living) return null
    val state = produceState(KitMoment.now()) {
        while (true) {
            delay(60_000)
            value = KitMoment.now()
        }
    }
    return state.value
}

private class Flake(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Float)

private fun flakes(seed: Int, n: Int): List<Flake> {
    val r = Random(seed)
    return List(n) { Flake(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f, 0.5f + r.nextFloat() * 0.8f) }
}

private val InkLine = Color(0xFF141210)
private val LeafColors = listOf(Color(0xFFD9882E), Color(0xFFC0502A), Color(0xFFB8862A), Color(0xFF8E4A22))
private val WeedColors = listOf(Color(0xFF4E8A5A), Color(0xFF6E9A4A), Color(0xFF3E7A6A), Color(0xFF7A8A3A))

@Composable
fun KitSeasonLayer(decor: Decor, light: Boolean, moment: KitMoment, modifier: Modifier = Modifier) {
    if (decor == Decor.None) return
    val time = throttledClock(true, 3600)
    val specks = remember { flakes(97, 16) }
    val stars = remember { flakes(131, 14) }
    Canvas(modifier.graphicsLayer()) {
        val t = time.value
        dayPart(decor, light, moment.part, t, stars)
        seasonParticles(decor, light, moment, t, specks)
    }
}

private fun DrawScope.radial(color: Color, alpha: Float, c: Offset, r: Float) {
    if (alpha <= 0f || r <= 0f) return
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), center = c, radius = r), r, c)
}

private fun DrawScope.crescent(c: Offset, r: Float): Path {
    val full = Path().apply { addOval(Rect(c, r)) }
    val cut = Path().apply { addOval(Rect(Offset(c.x + r * 0.45f, c.y - r * 0.2f), r * 0.86f)) }
    return Path.combine(PathOperation.Difference, full, cut)
}

private fun DrawScope.dayPart(decor: Decor, light: Boolean, part: DayPart, t: Float, stars: List<Flake>) {
    val d = density
    val w = size.width
    val h = size.height
    when (part) {
        DayPart.Day -> Unit
        DayPart.Morning -> {
            if (decor != Decor.News) radial(Color(0xFFFFE2B0), if (light) 0.3f else 0.12f, Offset(w * 0.08f, h * 0.02f), w * 0.9f)
        }
        DayPart.Evening -> {
            when (decor) {
                Decor.News -> Unit
                Decor.Sea -> drawRect(Brush.verticalGradient(listOf(Color(0xFFFF9A6A).copy(alpha = if (light) 0.16f else 0.1f), Color.Transparent), endY = h * 0.5f))
                Decor.Library -> radial(Color(0xFFFFB060), if (light) 0.16f else 0.14f, Offset(w * 0.86f, h * 0.13f), w * 0.8f)
                else -> {
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFF8E5C).copy(alpha = if (light) 0.14f else 0.09f)), startY = h * 0.35f))
                    radial(Color(0xFFFFB27A), if (light) 0.22f else 0.12f, Offset(w * 0.95f, h * 0.7f), w * 0.7f)
                }
            }
        }
        DayPart.Night -> {
            val wash = Color(0xFF0A1430)
            when (decor) {
                Decor.News -> Unit
                Decor.Library -> {
                    drawRect(Brush.radialGradient(listOf(Color.Transparent, wash.copy(alpha = if (light) 0.14f else 0.3f)), center = Offset(w * 0.8f, h * 0.15f), radius = max(w, h)))
                    radial(Color(0xFFFFB866), if (light) 0.2f else 0.2f, Offset(w * 0.86f, h * 0.13f), w * 0.7f)
                }
                Decor.Sea -> {
                    drawRect(wash.copy(alpha = if (light) 0.12f else 0.26f))
                    stars.forEachIndexed { i, s ->
                        val glow = max(0f, sin(t * 0.9f * s.speed + s.phase))
                        val x = s.x * w + sin(t * 0.2f * s.speed + s.phase) * 16 * d
                        val y = (0.15f + 0.75f * s.y) * h + cos(t * 0.17f + s.phase) * 12 * d
                        val c = if (i % 3 == 0) Color(0xFF9CF7E0) else Color(0xFF6FE8FF)
                        radial(c, 0.5f * glow, Offset(x, y), (7 + 6 * s.size) * d)
                        drawCircle(c.copy(alpha = 0.9f * glow), (1 + s.size) * d, Offset(x, y))
                    }
                }
                else -> {
                    drawRect(wash.copy(alpha = if (light) 0.1f else 0.22f))
                    val starColor = if (light) Color(0xFF5A6A9A) else Color(0xFFFFF4D0)
                    stars.forEach { s ->
                        val tw = 0.45f + 0.55f * max(0f, sin(t * 1.1f * s.speed + s.phase))
                        val p = Offset(s.x * w, s.y * h * 0.42f)
                        val r = (0.9f + s.size * 1.4f) * d
                        when (decor) {
                            Decor.Comic -> {
                                star4(p, r * 3.2f, Color(0xFFFFCC00))
                                star4Stroke(p, r * 3.2f, InkLine, 1.2f * d)
                            }
                            Decor.Clay -> {
                                drawCircle(Color(0xFFFFE9A8).copy(alpha = tw), r * 2f, p)
                                drawCircle(Color.White.copy(alpha = tw * 0.8f), r * 0.7f, Offset(p.x - r * 0.5f, p.y - r * 0.5f))
                            }
                            else -> drawCircle(starColor.copy(alpha = tw * (if (light) 0.6f else 0.9f)), r, p)
                        }
                    }
                    val mc = Offset(w * 0.82f, h * 0.08f)
                    val mr = 15 * d
                    when (decor) {
                        Decor.Zen -> {
                            radial(Color(0xFFFFF4D0), if (light) 0.35f else 0.3f, mc, mr * 3.2f)
                            drawCircle(if (light) Color(0xFFF6EFD8) else Color(0xFFF2ECD0), mr, mc)
                            drawCircle(Color(0xFFD8CFB0).copy(alpha = 0.5f), mr * 0.22f, Offset(mc.x - mr * 0.3f, mc.y - mr * 0.2f))
                            drawCircle(Color(0xFFD8CFB0).copy(alpha = 0.4f), mr * 0.15f, Offset(mc.x + mr * 0.35f, mc.y + mr * 0.3f))
                        }
                        Decor.Comic -> {
                            val moon = crescent(mc, mr)
                            drawPath(moon, Color(0xFFFFE066))
                            drawPath(moon, InkLine, style = Stroke(2.2f * d, join = StrokeJoin.Round))
                        }
                        Decor.Clay -> {
                            drawCircle(Color(0xFFD8C27A), mr, Offset(mc.x, mc.y + 2.5f * d))
                            drawCircle(Color(0xFFFFE9A8), mr, mc)
                            drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.1f)), start = Offset(mc.x - mr, mc.y - mr), end = Offset(mc.x + mr, mc.y + mr)), mr, mc)
                            val ink = Color(0xFF3B2F5C)
                            for (ex in listOf(-0.35f, 0.35f)) {
                                val lid = Path().apply { moveTo(mc.x + (ex - 0.18f) * mr, mc.y - 0.05f * mr); quadraticTo(mc.x + ex * mr, mc.y + 0.15f * mr, mc.x + (ex + 0.18f) * mr, mc.y - 0.05f * mr) }
                                drawPath(lid, ink, style = Stroke(1.3f * d, cap = StrokeCap.Round))
                            }
                            val smile = Path().apply { moveTo(mc.x - 0.16f * mr, mc.y + 0.36f * mr); quadraticTo(mc.x, mc.y + 0.5f * mr, mc.x + 0.16f * mr, mc.y + 0.36f * mr) }
                            drawPath(smile, ink, style = Stroke(1.2f * d, cap = StrokeCap.Round))
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

private fun DrawScope.star4Path(c: Offset, r: Float): Path = Path().apply {
    moveTo(c.x, c.y - r)
    quadraticTo(c.x, c.y, c.x + r, c.y)
    quadraticTo(c.x, c.y, c.x, c.y + r)
    quadraticTo(c.x, c.y, c.x - r, c.y)
    quadraticTo(c.x, c.y, c.x, c.y - r)
    close()
}

private fun DrawScope.star4(c: Offset, r: Float, color: Color) = drawPath(star4Path(c, r), color)

private fun DrawScope.star4Stroke(c: Offset, r: Float, color: Color, width: Float) = drawPath(star4Path(c, r), color, style = Stroke(width, join = StrokeJoin.Round))

private fun leafPath(c: Offset, r: Float): Path = Path().apply {
    moveTo(c.x - r, c.y)
    quadraticTo(c.x - r * 0.2f, c.y - r * 0.75f, c.x + r, c.y)
    quadraticTo(c.x - r * 0.2f, c.y + r * 0.75f, c.x - r, c.y)
    close()
}

private fun DrawScope.leaf(c: Offset, r: Float, angle: Float, color: Color, decor: Decor) {
    rotate(angle, c) {
        val p = leafPath(c, r)
        drawPath(p, color)
        when (decor) {
            Decor.Comic -> drawPath(p, InkLine, style = Stroke(1.6f * density, join = StrokeJoin.Round))
            Decor.Clay -> drawPath(p, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent, Color.Black.copy(alpha = 0.12f)), start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)))
            else -> Unit
        }
        drawLine((if (decor == Decor.Comic) InkLine else Color.Black).copy(alpha = if (decor == Decor.Comic) 0.9f else 0.22f), Offset(c.x - r * 0.9f, c.y), Offset(c.x + r * 0.8f, c.y), 0.8f * density)
    }
}

private fun DrawScope.snow(c: Offset, r: Float, alpha: Float, decor: Decor, light: Boolean) {
    when (decor) {
        Decor.Comic -> {
            for (k in 0 until 3) {
                val a = PI.toFloat() * k / 3f
                val o = Offset(cos(a) * r * 1.5f, sin(a) * r * 1.5f)
                drawLine(InkLine, c - o, c + o, 3.2f * density, cap = StrokeCap.Round)
            }
            for (k in 0 until 3) {
                val a = PI.toFloat() * k / 3f
                val o = Offset(cos(a) * r * 1.5f, sin(a) * r * 1.5f)
                drawLine(Color.White, c - o, c + o, 1.4f * density, cap = StrokeCap.Round)
            }
        }
        Decor.Clay -> {
            drawCircle(Color(0xFFB8C4E0).copy(alpha = alpha), r * 1.3f, Offset(c.x, c.y + r * 0.35f))
            drawCircle(Color.White.copy(alpha = alpha), r * 1.3f, c)
            drawCircle(Color(0xFFE8EEFF).copy(alpha = alpha), r * 0.5f, Offset(c.x + r * 0.35f, c.y + r * 0.35f))
        }
        Decor.News -> drawRect((if (light) Color(0xFF141414) else Color(0xFFECE6D8)).copy(alpha = alpha * 0.35f), Offset(c.x - r * 0.5f, c.y - r * 0.5f), Size(r, r))
        else -> {
            val color = if (light) Color(0xFF8EA6C2) else Color.White
            radial(color, alpha * 0.35f, c, r * 2.6f)
            drawCircle(color.copy(alpha = alpha * (if (light) 0.75f else 0.85f)), r, c)
        }
    }
}

private fun DrawScope.seasonParticles(decor: Decor, light: Boolean, moment: KitMoment, t: Float, specks: List<Flake>) {
    val d = density
    val w = size.width
    val h = size.height
    val span = h + 60 * d
    fun fall(s: Flake, v: Float, sway: Float): Offset {
        val y = ((s.y * span + t * v * d * s.speed) % span) - 30 * d
        val x = s.x * w + sin(t * 0.5f * s.speed + s.phase) * sway * d
        return Offset(x, y)
    }
    when (moment.season) {
        Season.Winter -> specks.forEach { s ->
            val p = fall(s, if (decor == Decor.Sea) 7f else 15f, 14f)
            snow(p, (1.6f + s.size * 2.2f) * d, 0.55f + 0.45f * s.size, decor, light)
        }
        Season.Autumn -> when (decor) {
            Decor.News -> specks.forEach { s ->
                val y = ((s.y * span + t * 260f * d * s.speed) % span) - 30 * d
                val x = s.x * w * 1.2f - y * 0.18f
                val ink = if (light) Color(0xFF141414) else Color(0xFFECE6D8)
                drawLine(ink.copy(alpha = 0.12f + 0.1f * s.size), Offset(x, y), Offset(x - 3 * d, y + 16 * d), 1 * d, cap = StrokeCap.Round)
            }
            Decor.Zen -> Unit
            else -> specks.take(10).forEachIndexed { i, s ->
                val p = fall(s, if (decor == Decor.Sea) 8f else 19f, 30f)
                val colors = if (decor == Decor.Sea) WeedColors else LeafColors
                leaf(p, (5f + s.size * 4f) * d, t * 34f * s.speed + s.phase * 57f, colors[i % colors.size].copy(alpha = if (light) 0.9f else 0.8f), decor)
            }
        }
        Season.Spring -> when (decor) {
            Decor.Zen, Decor.News -> Unit
            else -> specks.take(9).forEachIndexed { i, s ->
                val p = fall(s, if (decor == Decor.Sea) 7f else 16f, 26f)
                val color = if (decor == Decor.Sea) Color(0xFF9CF0E0) else if (i % 3 == 0) Color(0xFFFFFFFF) else Color(0xFFF7B8C8)
                val r = (3.5f + s.size * 2.5f) * d
                rotate(t * 30f * s.speed + s.phase * 57f, p) {
                    drawOval(color.copy(alpha = if (light) 0.85f else 0.6f), Offset(p.x - r, p.y - r * 0.65f), Size(r * 2, r * 1.3f))
                    if (decor == Decor.Comic) drawOval(InkLine, Offset(p.x - r, p.y - r * 0.65f), Size(r * 2, r * 1.3f), style = Stroke(1.4f * d))
                }
            }
        }
        Season.Summer -> {
            if (decor == Decor.News) return
            val dusk = moment.part == DayPart.Evening || moment.part == DayPart.Night
            specks.take(if (dusk) 10 else 12).forEachIndexed { i, s ->
                if (dusk && decor != Decor.Sea) {
                    val x = s.x * w + sin(t * 0.3f * s.speed + s.phase) * 40 * d + sin(t * 0.11f + s.phase * 2) * 20 * d
                    val y = (0.2f + 0.65f * s.y) * h + cos(t * 0.25f * s.speed + s.phase) * 30 * d
                    val glow = max(0f, sin(t * 1.2f * s.speed + s.phase)).let { it * it }
                    val c = Color(0xFFE8FF8A)
                    radial(c, 0.55f * glow, Offset(x, y), 11 * d)
                    drawCircle(c.copy(alpha = 0.95f * glow), 1.6f * d, Offset(x, y))
                } else {
                    val y = h - ((s.y * span + t * 6f * d * s.speed) % span) + 30 * d
                    val x = s.x * w + sin(t * 0.4f * s.speed + s.phase) * 18 * d
                    val tw = 0.4f + 0.6f * max(0f, sin(t * 0.9f * s.speed + s.phase))
                    val c = if (decor == Decor.Sea) Color(0xFFBFF7FF) else if (light) Color(0xFFE0A84A) else Color(0xFFFFE9A8)
                    drawCircle(c.copy(alpha = tw * (if (light) 0.5f else 0.55f)), (0.9f + s.size * 1.3f) * d, Offset(x, y))
                }
            }
        }
    }
}

val LocalKitMoment = androidx.compose.runtime.staticCompositionLocalOf<KitMoment?> { null }

fun DrawScope.plankSeason(decor: Decor, season: Season, light: Boolean, seed: Int, plank: Float) {
    val d = density
    val w = size.width
    val r = Random(seed)
    when (season) {
        Season.Winter -> {
            val white = if (decor == Decor.Comic || !light) Color.White else Color(0xFFF7FAFF)
            val shade = Color(0xFFAEC2DC)
            val cap = Path().apply {
                moveTo(0f, 4 * d)
                var x = 0f
                lineTo(0f, 0f)
                while (x < w) {
                    val step = (14f + r.nextFloat() * 22f) * d
                    val rise = (2.5f + r.nextFloat() * 4.5f) * d
                    val nx = (x + step).coerceAtMost(w)
                    quadraticTo((x + nx) / 2, -rise * 2f, nx, 0f)
                    x = nx
                }
                lineTo(w, 4 * d)
                close()
            }
            drawPath(cap, white)
            when (decor) {
                Decor.Comic -> drawPath(cap, InkLine, style = Stroke(2 * d, join = StrokeJoin.Round))
                Decor.Clay -> drawPath(cap, Brush.verticalGradient(listOf(Color.White, Color(0xFFD8E2F4)), startY = -8 * d, endY = 4 * d))
                else -> drawLine(shade.copy(alpha = 0.55f), Offset(2 * d, 4 * d), Offset(w - 2 * d, 4 * d), 1 * d)
            }
            if (decor != Decor.News && decor != Decor.Sea) {
                val bottom = plank
                val count = (w / (70 * d)).toInt().coerceIn(2, 7)
                for (i in 0 until count) {
                    val x = (0.06f + r.nextFloat() * 0.88f) * w
                    val len = (5f + r.nextFloat() * 9f) * d
                    val wd = (2f + r.nextFloat() * 1.6f) * d
                    val ice = Path().apply { moveTo(x - wd, bottom); lineTo(x + wd, bottom); lineTo(x, bottom + len); close() }
                    drawPath(ice, Brush.verticalGradient(listOf(Color(0xFFEAF6FF), Color(0xFFBFE0F7).copy(alpha = 0.75f)), startY = bottom, endY = bottom + len))
                    if (decor == Decor.Comic) drawPath(ice, InkLine, style = Stroke(1.4f * d, join = StrokeJoin.Round))
                }
            }
        }
        Season.Autumn -> {
            if (decor == Decor.News || decor == Decor.Sea) return
            val count = (w / (90 * d)).toInt().coerceIn(2, 5)
            for (i in 0 until count) {
                val x = (0.05f + r.nextFloat() * 0.9f) * w
                leaf(Offset(x, -1.5f * d), (5f + r.nextFloat() * 3f) * d, -25f + r.nextFloat() * 50f, LeafColors[r.nextInt(LeafColors.size)], decor)
            }
        }
        Season.Spring -> {
            if (decor == Decor.News || decor == Decor.Sea) return
            val count = (w / (60 * d)).toInt().coerceIn(3, 8)
            for (i in 0 until count) {
                val x = (0.04f + r.nextFloat() * 0.92f) * w
                val pr = (2.6f + r.nextFloat() * 1.8f) * d
                val c = Offset(x, -pr * 0.3f)
                rotate(-30f + r.nextFloat() * 60f, c) {
                    drawOval(if (i % 3 == 0) Color.White else Color(0xFFF7B8C8), Offset(c.x - pr, c.y - pr * 0.6f), Size(pr * 2, pr * 1.2f))
                    if (decor == Decor.Comic) drawOval(InkLine, Offset(c.x - pr, c.y - pr * 0.6f), Size(pr * 2, pr * 1.2f), style = Stroke(1.2f * d))
                }
            }
        }
        Season.Summer -> Unit
    }
}
