package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
private fun runOnce(trigger: Int, durationMs: Int): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger <= 0 || !Motion.enabled) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    return progress
}

private fun span(p: Float, from: Float, to: Float): Float = ((p - from) / (to - from)).coerceIn(0f, 1f)

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

private fun backOut(t: Float): Float {
    val c = 1.9f
    val u = t - 1f
    return 1f + (c + 1f) * u * u * u + c * u * u
}

private class FrostTwig(val a: Offset, val b: Offset, val start: Float, val end: Float, val width: Float, val depth: Int)

private class FrostFlake(val c: Offset, val r: Float, val turn: Float, val at: Float, val glint: Float)

private class MeltDrop(val x: Float, val at: Float, val r: Float, val speed: Float, val wobble: Float)

private class BloomRay(val angle: Float, val length: Float, val width: Float, val delay: Float)

private class BloomStar(val angle: Float, val distance: Float, val size: Float, val delay: Float, val color: Int, val twinkle: Float)

private class BloomGlitter(val x: Float, val delay: Float, val fall: Float, val sway: Float, val size: Float, val color: Int)

private fun growTwigs(out: MutableList<FrostTwig>, r: Random, from: Offset, angle: Float, length: Float, t0: Float, speed: Float, depth: Int, unit: Float) {
    var at = from
    var dir = angle
    var t = t0
    val parts = if (depth == 0) 4 else 2
    val piece = length / parts
    repeat(parts) { k ->
        dir += (r.nextFloat() - 0.5f) * 0.32f
        val next = Offset(at.x + cos(dir) * piece, at.y + sin(dir) * piece)
        val dt = piece / speed
        out += FrostTwig(at, next, t, t + dt, (if (depth == 0) 1.5f else if (depth == 1) 1.05f else 0.75f) * unit, depth)
        if (depth < 2) {
            val side = if ((k + depth) % 2 == 0) 1f else -1f
            val branch = PI.toFloat() / 3f
            growTwigs(out, r, next, dir + side * branch, piece * (0.85f - depth * 0.2f), t + dt, speed * 0.9f, depth + 1, unit)
            if (depth == 0 && r.nextFloat() < 0.6f) {
                growTwigs(out, r, next, dir - side * branch, piece * 0.55f, t + dt * 1.2f, speed * 0.85f, depth + 2, unit)
            }
        }
        at = next
        t += dt
    }
}

private fun DrawScope.crystal(c: Offset, r: Float, turn: Float, color: Color, width: Float) {
    rotate(turn, c) {
        for (k in 0 until 6) {
            val a = k * PI.toFloat() / 3f
            val tip = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
            drawLine(color, c, tip, width, StrokeCap.Round)
            for (f in floatArrayOf(0.45f, 0.72f)) {
                val m = Offset(c.x + cos(a) * r * f, c.y + sin(a) * r * f)
                val l = r * (if (f < 0.6f) 0.32f else 0.22f)
                for (s in floatArrayOf(-1f, 1f)) {
                    val b = a + s * PI.toFloat() / 3f
                    drawLine(color, m, Offset(m.x + cos(b) * l, m.y + sin(b) * l), width * 0.8f, StrokeCap.Round)
                }
            }
        }
        drawCircle(color, width * 1.1f, c)
    }
}

private fun DrawScope.glint(c: Offset, r: Float, color: Color) {
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

private fun cardPath(size: Size, radius: Float): Path = Path().apply {
    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius, radius)))
}

private fun frostField(seed: Int, size: Size, unit: Float, core: Offset): List<FrostTwig> {
    val r = Random(seed)
    val out = ArrayList<FrostTwig>()
    val h = size.height
    val reach = (h * 0.95f).coerceAtMost(size.width * 0.4f)
    val speed = reach / 0.45f
    val roots = listOf(
        Offset(0f, 0f) to PI.toFloat() * 0.25f,
        Offset(size.width, 0f) to PI.toFloat() * 0.75f,
        Offset(0f, h) to -PI.toFloat() * 0.25f,
        Offset(size.width, h) to -PI.toFloat() * 0.75f,
        Offset(size.width * 0.5f, 0f) to PI.toFloat() * 0.5f,
        Offset(size.width * 0.5f, h) to -PI.toFloat() * 0.5f,
    )
    roots.forEachIndexed { i, (o, a) ->
        growTwigs(out, r, o, a + (r.nextFloat() - 0.5f) * 0.4f, reach * (if (i < 4) 1f else 0.6f), 0.04f + r.nextFloat() * 0.08f, speed, 0, unit)
    }
    for (k in 0 until 6) {
        val a = k * PI.toFloat() / 3f + 0.3f
        growTwigs(out, r, core, a, h * 0.42f, 0.02f, speed * 0.8f, 1, unit)
    }
    return out
}

@Composable
fun Modifier.frostBloom(trigger: Int, ice: Color, cornerDp: Float, core: Offset = Offset(41f, 41f), durationMs: Int = 2800): Modifier {
    val progress = runOnce(trigger, durationMs)
    val unit = LocalDensity.current.density
    return drawWithCache {
        val coreAt = Offset(core.x * unit, core.y * unit)
        val twigs = frostField(trigger * 41 + 9, size, unit, coreAt)
        val r = Random(trigger * 7 + 3)
        val flakes = List(9) {
            FrostFlake(
                Offset(size.width * (0.12f + r.nextFloat() * 0.8f), size.height * (0.15f + r.nextFloat() * 0.7f)),
                (5f + r.nextFloat() * 7f) * unit,
                r.nextFloat() * 60f,
                0.18f + r.nextFloat() * 0.35f,
                0.45f + r.nextFloat() * 0.35f,
            )
        }
        val clip = cardPath(size, cornerDp * unit)
        onDrawWithContent {
            drawContent()
            val p = progress.value
            if (p >= 1f) return@onDrawWithContent
            val fadeOut = 1f - smooth(span(p, 0.78f, 1f))
            clipPath(clip) {
                val haze = smooth(span(p, 0f, 0.5f)) * fadeOut
                listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height)).forEach { o ->
                    val rad = size.height * (0.4f + 0.9f * haze)
                    drawCircle(Brush.radialGradient(listOf(ice.copy(alpha = 0.26f * haze), Color.Transparent), o, rad), rad, o)
                }
                drawRect(ice.copy(alpha = 0.07f * haze))
                val wave = span(p, 0f, 0.35f)
                if (wave in 0.001f..0.999f) {
                    val rad = size.width * 0.9f * smooth(wave)
                    drawCircle(Color.White.copy(alpha = 0.35f * (1f - wave)), size.height * 0.5f * (1f - wave), coreAt)
                    drawCircle(ice.copy(alpha = 0.55f * (1f - wave)), rad, coreAt, style = Stroke(width = (3.2f * (1f - wave) + 0.6f) * unit))
                    drawCircle(Color.White.copy(alpha = 0.35f * (1f - wave)), rad * 0.94f, coreAt, style = Stroke(width = 0.8f * unit))
                }
                val grow = span(p, 0f, 0.7f)
                twigs.forEach { tw ->
                    val k = ((grow - tw.start) / (tw.end - tw.start)).coerceIn(0f, 1f)
                    if (k <= 0f) return@forEach
                    val tip = Offset(tw.a.x + (tw.b.x - tw.a.x) * k, tw.a.y + (tw.b.y - tw.a.y) * k)
                    val a = (0.75f - tw.depth * 0.15f) * fadeOut
                    drawLine(ice.copy(alpha = a), tw.a, tip, tw.width, StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = a * 0.55f), tw.a, tip, tw.width * 0.4f, StrokeCap.Round)
                    if (k < 1f) {
                        drawCircle(Color.White.copy(alpha = 0.9f * fadeOut), tw.width * 1.4f, tip)
                        drawCircle(ice.copy(alpha = 0.35f * fadeOut), tw.width * 3.2f, tip)
                    }
                }
                flakes.forEach { f ->
                    val t = span(p, f.at, f.at + 0.22f)
                    if (t <= 0f) return@forEach
                    val s = backOut(t)
                    val a = smooth(t) * fadeOut
                    crystal(f.c, f.r * s, f.turn + (1f - t) * 90f, ice.copy(alpha = 0.85f * a), 1.1f * unit)
                    crystal(f.c, f.r * s * 0.98f, f.turn + (1f - t) * 90f, Color.White.copy(alpha = 0.45f * a), 0.45f * unit)
                    val g = span(p, f.glint, f.glint + 0.12f)
                    if (g in 0.001f..0.999f) {
                        val pulse = sin(g * PI.toFloat())
                        glint(f.c, f.r * (0.9f + 0.9f * pulse), Color.White.copy(alpha = 0.9f * pulse * fadeOut))
                    }
                }
                val sweep = span(p, 0.38f, 0.82f)
                if (sweep in 0.001f..0.999f) {
                    val x = -size.width * 0.3f + size.width * 1.6f * smooth(sweep)
                    val band = size.height * 0.9f
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.22f), Color.Transparent),
                            start = Offset(x - band, 0f),
                            end = Offset(x + band * 0.4f, size.height),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun Modifier.thawMelt(trigger: Int, ice: Color, warm: Color, cornerDp: Float, core: Offset = Offset(41f, 41f), durationMs: Int = 2600): Modifier {
    val progress = runOnce(trigger, durationMs)
    val unit = LocalDensity.current.density
    return drawWithCache {
        val coreAt = Offset(core.x * unit, core.y * unit)
        val twigs = frostField(trigger * 53 + 17, size, unit, coreAt)
        val r = Random(trigger * 11 + 5)
        val drops = List(11) {
            MeltDrop(
                size.width * (0.08f + r.nextFloat() * 0.84f),
                0.12f + r.nextFloat() * 0.45f,
                (2.2f + r.nextFloat() * 2.2f) * unit,
                0.8f + r.nextFloat() * 0.6f,
                r.nextFloat() * 6.28f,
            )
        }
        val clip = cardPath(size, cornerDp * unit)
        onDrawWithContent {
            drawContent()
            val p = progress.value
            if (p >= 1f) return@onDrawWithContent
            val end = 1f - smooth(span(p, 0.82f, 1f))
            clipPath(clip) {
                val melt = smooth(span(p, 0f, 0.55f))
                drawRect(ice.copy(alpha = 0.10f * (1f - melt)))
                twigs.forEach { tw ->
                    val keep = (1f - melt * (1.3f - tw.start)).coerceIn(0f, 1f)
                    if (keep <= 0f) return@forEach
                    val tip = Offset(tw.a.x + (tw.b.x - tw.a.x) * keep, tw.a.y + (tw.b.y - tw.a.y) * keep)
                    val a = (0.6f - tw.depth * 0.12f) * keep
                    drawLine(ice.copy(alpha = a), tw.a, tip, tw.width * (0.6f + 0.4f * keep), StrokeCap.Round)
                }
                val heat = span(p, 0f, 0.6f)
                val glow = sin(heat * PI.toFloat()) * end
                val gr = size.height * (0.5f + 1.6f * smooth(heat))
                drawCircle(Brush.radialGradient(listOf(warm.copy(alpha = 0.28f * glow), warm.copy(alpha = 0.08f * glow), Color.Transparent), coreAt, gr), gr, coreAt)
                for (k in 0 until 5) {
                    val t = span(p, 0.08f + k * 0.07f, 0.6f + k * 0.07f)
                    if (t <= 0f || t >= 1f) continue
                    val x = coreAt.x + sin(t * 5f + k * 1.7f) * 6f * unit + (k - 2) * 4f * unit
                    val y = coreAt.y - t * size.height * 0.9f
                    drawCircle(Color.White.copy(alpha = 0.16f * sin(t * PI.toFloat()) * end), (3f + t * 9f) * unit, Offset(x, y))
                }
                drops.forEach { d ->
                    val t = span(p, d.at, d.at + 0.4f / d.speed)
                    if (t <= 0f) return@forEach
                    val form = smooth(span(t, 0f, 0.2f))
                    val fall = span(t, 0.2f, 1f)
                    val travel = size.height + d.r * 2f
                    val y = d.r + travel * fall * fall
                    val x = d.x + sin(fall * 7f + d.wobble) * 1.6f * unit
                    val bottom = size.height - d.r
                    if (y < bottom) {
                        val trail = (fall * size.height * 0.5f).coerceAtMost(y)
                        if (trail > 1f) {
                            drawLine(
                                Brush.verticalGradient(listOf(Color.Transparent, ice.copy(alpha = 0.45f * end)), y - trail, y),
                                Offset(x, y - trail),
                                Offset(x, y),
                                d.r * 0.55f,
                                StrokeCap.Round,
                            )
                        }
                        val rr = d.r * form
                        drawOval(ice.copy(alpha = 0.85f * end), Offset(x - rr, y - rr * (1f + fall * 0.5f)), Size(rr * 2f, rr * (2f + fall)))
                        drawCircle(Color.White.copy(alpha = 0.85f * end), rr * 0.35f, Offset(x - rr * 0.35f, y - rr * 0.35f))
                    } else {
                        val s = ((y - bottom) / (size.height * 0.5f)).coerceIn(0f, 1f)
                        if (s >= 1f) return@forEach
                        val fade = (1f - s) * end
                        drawOval(ice.copy(alpha = 0.6f * fade), Offset(x - d.r * (1f + s * 5f), bottom - d.r * 0.5f * (1f + s)), Size(d.r * 2f * (1f + s * 5f), d.r * (1f + s)), style = Stroke(width = 0.9f * unit))
                        for (k in 0 until 4) {
                            val a = PI.toFloat() * (1.15f + k * 0.23f)
                            val dist = d.r * 6f * s
                            val lift = sin(s * PI.toFloat()) * d.r * 4f
                            drawCircle(ice.copy(alpha = 0.8f * fade), d.r * 0.35f * (1f - s * 0.5f), Offset(x + cos(a) * dist, bottom - lift - sin(a) * dist * 0.3f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Modifier.victoryBloom(trigger: Int, colors: List<Color>, reachDp: Float = 130f, durationMs: Int = 2600): Modifier {
    val progress = runOnce(trigger, durationMs)
    val unit = LocalDensity.current.density
    return drawWithCache {
        val r = Random(trigger * 29 + 13)
        val rays = List(14) { k -> BloomRay(k * 2f * PI.toFloat() / 14f + r.nextFloat() * 0.2f, 0.6f + r.nextFloat() * 0.5f, 0.05f + r.nextFloat() * 0.05f, r.nextFloat() * 0.12f) }
        val stars = List(16) { k ->
            BloomStar(k * 2f * PI.toFloat() / 16f + (r.nextFloat() - 0.5f) * 0.35f, 0.45f + r.nextFloat() * 0.6f, 0.7f + r.nextFloat() * 0.8f, r.nextFloat() * 0.15f, r.nextInt(colors.size), r.nextFloat() * 6.28f)
        }
        val glitter = List(26) { BloomGlitter(r.nextFloat() * 2f - 1f, 0.2f + r.nextFloat() * 0.35f, 0.5f + r.nextFloat() * 0.6f, r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 0.8f, r.nextInt(colors.size)) }
        val reach = reachDp * unit
        val pill = cardPath(size, size.height / 2f)
        onDrawWithContent {
            val p = progress.value
            if (p >= 1f) {
                drawContent()
                return@onDrawWithContent
            }
            val c = center
            val end = 1f - smooth(span(p, 0.75f, 1f))
            val rayT = span(p, 0.02f, 0.7f)
            rotate(p * 40f, c) {
                rays.forEach { ray ->
                    val t = span(rayT, ray.delay, 1f)
                    if (t <= 0f) return@forEach
                    val len = reach * ray.length * smooth(span(t, 0f, 0.5f))
                    val a = sin(t * PI.toFloat()) * 0.22f
                    val w = reach * ray.width
                    val dir = Offset(cos(ray.angle), sin(ray.angle))
                    val side = Offset(-dir.y, dir.x)
                    val tip = Offset(c.x + dir.x * len, c.y + dir.y * len)
                    val path = Path().apply {
                        moveTo(c.x + side.x * w * 0.15f, c.y + side.y * w * 0.15f)
                        lineTo(tip.x + side.x * w, tip.y + side.y * w)
                        lineTo(tip.x - side.x * w, tip.y - side.y * w)
                        lineTo(c.x - side.x * w * 0.15f, c.y - side.y * w * 0.15f)
                        close()
                    }
                    drawPath(path, Brush.radialGradient(listOf(colors.first().copy(alpha = a), Color.Transparent), c, len.coerceAtLeast(1f)))
                }
            }
            for (k in 0 until 2) {
                val t = span(p, k * 0.1f, 0.45f + k * 0.1f)
                if (t <= 0f || t >= 1f) continue
                val rad = reach * (0.2f + 0.9f * smooth(t))
                drawCircle(colors[k % colors.size].copy(alpha = 0.5f * (1f - t)), rad, c, style = Stroke(width = (3.5f * (1f - t) + 0.5f) * unit))
            }
            val flash = span(p, 0f, 0.25f)
            if (flash < 1f) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f * (1f - flash)), Color.Transparent), c, reach * 0.5f), reach * 0.5f, c)
            glitter.forEach { g ->
                val t = span(p, g.delay, 1f)
                if (t <= 0f) return@forEach
                val x = c.x + g.x * reach * 0.9f + sin(t * 6f + g.sway) * 6f * unit
                val y = c.y - reach * 0.35f + reach * 0.9f * t * g.fall
                val a = sin(span(t, 0f, 1f) * PI.toFloat()) * end
                val tw = 0.6f + 0.4f * sin(t * 18f + g.sway)
                glint(Offset(x, y), 2.6f * unit * g.size * tw, colors[g.color].copy(alpha = 0.85f * a))
            }
            drawContent()
            val shine = span(p, 0.15f, 0.55f)
            if (shine in 0.001f..0.999f) {
                clipPath(pill) {
                    val x = -size.width * 0.3f + size.width * 1.6f * smooth(shine)
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                            start = Offset(x - size.height, 0f),
                            end = Offset(x + size.height * 0.3f, size.height),
                        ),
                    )
                }
            }
            stars.forEach { s ->
                val t = span(p, s.delay, 0.85f)
                if (t <= 0f || t >= 1f) return@forEach
                val travel = 1f - (1f - t) * (1f - t) * (1f - t)
                val d = reach * (0.15f + s.distance * travel)
                val o = Offset(c.x + cos(s.angle) * d, c.y + sin(s.angle) * d + t * t * 14f * unit)
                val a = (1f - t) * (if (t < 0.1f) t / 0.1f else 1f)
                val pulse = 0.75f + 0.25f * sin(t * 20f + s.twinkle)
                val rad = 5.5f * unit * s.size * pulse * (1f - t * 0.4f)
                val color = colors[s.color]
                drawCircle(color.copy(alpha = a * 0.35f), rad * 1.9f, o)
                glint(o, rad * 1.6f, color.copy(alpha = a))
                glint(o, rad * 0.7f, Color.White.copy(alpha = a * 0.9f))
            }
        }
    }
}

@Composable
fun rememberPop(trigger: Int): Animatable<Float, AnimationVector1D> {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger <= 0 || !Motion.enabled) return@LaunchedEffect
        pop.snapTo(0.55f)
        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.38f, stiffness = 420f))
    }
    return pop
}
