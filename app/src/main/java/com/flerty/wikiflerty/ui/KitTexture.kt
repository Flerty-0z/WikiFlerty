package com.flerty.wikiflerty.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun Modifier.kitTexture(amount: Float = 1f, extend: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f)): Modifier = this.drawBehind { with(KitTexture) { drawTexture(amount, extend.toPx()) } }

object KitTexture {
    private const val TILE = 320
    private const val REF_DENSITY = 2.75f

    private class Tex(val bitmap: Bitmap, val shader: BitmapShader)

    private val ready = mutableStateMapOf<String, Tex>()
    private val pending = java.util.Collections.synchronizedSet(HashSet<String>())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val matrix = android.graphics.Matrix()

    val drift = mutableFloatStateOf(0f)

    private fun kind(decor: Decor): Decor? = when (decor) {
        Decor.Library, Decor.Zen, Decor.News, Decor.Sea, Decor.Comic, Decor.Clay, Decor.Frost -> decor
        else -> null
    }

    private fun strength(decor: Decor, light: Boolean): Float = when (decor) {
        Decor.Library -> if (light) 0.85f else 1f
        Decor.Zen -> 1f
        Decor.News -> if (light) 0.9f else 0.7f
        Decor.Sea -> if (light) 0.7f else 1f
        Decor.Comic -> if (light) 0.8f else 0.6f
        Decor.Clay -> 0.9f
        Decor.Frost -> if (light) 0.8f else 1f
        else -> 0f
    }

    private fun shader(decor: Decor, light: Boolean): BitmapShader? {
        val key = "${decor.name}:$light"
        ready[key]?.let { return it.shader }
        if (pending.add(key)) {
            scope.launch {
                val bmp = runCatching { make(decor, light) }.getOrNull()
                if (bmp != null) {
                    val tex = Tex(bmp, BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT))
                    kotlinx.coroutines.withContext(Dispatchers.Main) { ready[key] = tex }
                }
            }
        }
        return null
    }

    internal fun DrawScope.drawTexture(amount: Float, extend: Float = 0f) {
        if (EInk.on) return
        val decor = kind(AppStyle.kit.decor) ?: return
        val light = AppStyle.light
        val s = shader(decor, light) ?: return
        val scale = density / REF_DENSITY
        val shift = if (decor == Decor.Sea) drift.floatValue else 0f
        matrix.setScale(scale, scale)
        if (shift != 0f) matrix.postTranslate(shift * TILE * scale, shift * TILE * scale * 0.38f)
        s.setLocalMatrix(matrix)
        drawRect(ShaderBrush(s), size = androidx.compose.ui.geometry.Size(size.width, size.height + extend), alpha = (strength(decor, light) * amount).coerceIn(0f, 1f))
    }

    @Composable
    fun Ticker() {
        val decor = AppStyle.kit.decor
        if (decor != Decor.Sea || !Motion.enabled || EInk.on) return
        LaunchedEffect(decor) {
            var last = System.nanoTime()
            while (true) {
                delay(80)
                val now = System.nanoTime()
                if (!SharedClock.covered) drift.floatValue = (drift.floatValue + (now - last) / 1e9f / 90f) % 1f
                last = now
            }
        }
    }

    private class Rng(seed: Long) {
        private var s = seed
        fun next(): Float {
            s = (s * 6364136223846793005L + 1442695040888963407L)
            return ((s ushr 40) and 0xFFFFFF).toFloat() / 0x1000000
        }
    }

    private fun ink(light: Boolean, dark: Int, bright: Int): Int = if (light) dark else bright

    private fun Canvas.wrapped(x: Float, y: Float, reach: Float, draw: Canvas.(Float, Float) -> Unit) {
        for (ox in -1..1) for (oy in -1..1) {
            val px = x + ox * TILE
            val py = y + oy * TILE
            if (px + reach < 0 || px - reach > TILE || py + reach < 0 || py - reach > TILE) continue
            draw(px, py)
        }
    }

    private fun alphaOf(color: Int, a: Float): Int = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    private fun make(decor: Decor, light: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = Rng(decor.ordinal * 7919L + if (light) 1 else 0)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (decor) {
            Decor.Library -> paper(c, r, p, light)
            Decor.Zen -> washi(c, r, p, light)
            Decor.News -> newsprint(c, r, p, light)
            Decor.Sea -> caustic(bmp, r, light)
            Decor.Comic -> halftone(c, p, light)
            Decor.Clay -> clay(c, r, p, light)
            Decor.Frost -> frost(c, r, p, light)
            else -> Unit
        }
        return bmp
    }

    private fun speckle(c: Canvas, r: Rng, p: Paint, color: Int, count: Int, maxAlpha: Float, size: Float = 1.6f) {
        p.style = Paint.Style.FILL
        repeat(count) {
            p.color = alphaOf(color, r.next() * maxAlpha)
            val x = r.next() * TILE
            val y = r.next() * TILE
            c.drawRect(x, y, x + size, y + size, p)
        }
    }

    private fun blot(c: Canvas, r: Rng, p: Paint, color: Int, count: Int, minR: Float, maxR: Float, minA: Float, maxA: Float) {
        p.style = Paint.Style.FILL
        repeat(count) {
            val x = r.next() * TILE
            val y = r.next() * TILE
            val rad = minR + r.next() * (maxR - minR)
            val a = minA + r.next() * (maxA - minA)
            c.wrapped(x, y, rad) { px, py ->
                p.shader = RadialGradient(px, py, rad, alphaOf(color, a), alphaOf(color, 0f), Shader.TileMode.CLAMP)
                drawCircle(px, py, rad, p)
            }
        }
        p.shader = null
    }

    private fun paper(c: Canvas, r: Rng, p: Paint, light: Boolean) {
        val fiber = ink(light, 0xFF5A3A16.toInt(), 0xFFFFEFD2.toInt())
        speckle(c, r, p, fiber, 9000, if (light) 0.10f else 0.11f)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val path = Path()
        repeat(520) {
            val x = r.next() * TILE
            val y = r.next() * TILE
            val a = r.next() * PI.toFloat()
            val len = 7f + r.next() * 26f
            p.strokeWidth = 0.8f + r.next() * 0.7f
            p.color = alphaOf(fiber, (if (light) 0.07f else 0.08f) + r.next() * 0.10f)
            c.wrapped(x, y, len) { px, py ->
                path.reset()
                path.moveTo(px, py)
                path.quadTo(px + cos(a) * len / 2 + (r.next() - 0.5f) * 7f, py + sin(a) * len / 2 + (r.next() - 0.5f) * 7f, px + cos(a) * len, py + sin(a) * len)
                drawPath(path, p)
            }
        }
        blot(c, r, p, ink(light, 0xFF8A5A1E.toInt(), 0xFF000000.toInt()), 12, 12f, 38f, 0.10f, 0.20f)
    }

    private fun washi(c: Canvas, r: Rng, p: Paint, light: Boolean) {
        val fiber = ink(light, 0xFF3A3020.toInt(), 0xFFF4EBD8.toInt())
        speckle(c, r, p, fiber, 5000, 0.05f)
        blot(c, r, p, ink(light, 0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 9, 50f, 100f, 0.25f, 0.4f)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val path = Path()
        repeat(110) {
            val x = r.next() * TILE
            val y = r.next() * TILE
            val a = r.next() * PI.toFloat() * 2
            val len = 70f + r.next() * 150f
            p.strokeWidth = 0.6f + r.next() * 0.8f
            p.color = alphaOf(fiber, 0.05f + r.next() * 0.07f)
            val bend = 0.4f + r.next() * 0.5f
            c.wrapped(x, y, len) { px, py ->
                val bx = px + cos(a) * len
                val by = py + sin(a) * len
                path.reset()
                path.moveTo(px, py)
                path.cubicTo(px + cos(a + bend) * len * 0.4f, py + sin(a + bend) * len * 0.4f, bx - cos(a - bend) * len * 0.4f, by - sin(a - bend) * len * 0.4f, bx, by)
                drawPath(path, p)
            }
        }
    }

    private fun newsprint(c: Canvas, r: Rng, p: Paint, light: Boolean) {
        val grain = ink(light, 0xFF1A1A1A.toInt(), 0xFFF2ECDD.toInt())
        speckle(c, r, p, grain, 20000, if (light) 0.10f else 0.08f)
        p.style = Paint.Style.FILL
        repeat(70) {
            p.color = alphaOf(grain, 0.025f + r.next() * 0.02f)
            val y = r.next() * TILE
            c.drawRect(0f, y, TILE.toFloat(), y + 0.9f, p)
        }
        blot(c, r, p, grain, 4, 20f, 46f, 0.04f, 0.07f)
    }

    private fun caustic(bmp: Bitmap, r: Rng, light: Boolean) {
        val n = 48
        val sx = FloatArray(n) { r.next() * TILE }
        val sy = FloatArray(n) { r.next() * TILE }
        val px = IntArray(TILE * TILE)
        val tint = if (light) 0x00FFFFFF else 0x00C8FFF8
        val peak = if (light) 0.55f else 0.24f
        val tau = 2 * PI.toFloat()
        for (y0 in 0 until TILE) {
            for (x0 in 0 until TILE) {
                val x = x0 + 5f * sin(y0 * tau / TILE * 3)
                val y = y0 + 5f * sin(x0 * tau / TILE * 2)
                var d1 = Float.MAX_VALUE
                var d2 = Float.MAX_VALUE
                for (i in 0 until n) {
                    for (ox in -1..1) {
                        val dx = x - (sx[i] + ox * TILE)
                        if (dx > 90f || dx < -90f) continue
                        for (oy in -1..1) {
                            val dy = y - (sy[i] + oy * TILE)
                            val d = dx * dx + dy * dy
                            if (d < d1) {
                                d2 = d1
                                d1 = d
                            } else if (d < d2) {
                                d2 = d
                            }
                        }
                    }
                }
                val e = sqrt(d2) - sqrt(d1)
                val v = (1f - e / 4.4f).coerceAtLeast(0f)
                val a = v * v * v * peak
                px[y0 * TILE + x0] = ((a * 255).toInt() shl 24) or tint
            }
        }
        bmp.setPixels(px, 0, TILE, 0, 0, TILE, TILE)
    }

    private fun halftone(c: Canvas, p: Paint, light: Boolean) {
        val dot = ink(light, 0xFF111111.toInt(), 0xFFFFFFFF.toInt())
        p.style = Paint.Style.FILL
        p.color = alphaOf(dot, if (light) 0.10f else 0.07f)
        val step = 10f
        val tau = 2 * PI.toFloat()
        var row = 0
        var y = 0f
        while (y < TILE) {
            var x = if (row % 2 == 0) 0f else step / 2
            while (x < TILE) {
                val f = 0.5f + 0.25f * sin(x / TILE * tau) + 0.25f * cos(y / TILE * tau * 2)
                val rad = 0.4f + f * f * 2.2f
                c.wrapped(x, y, rad) { px, py -> drawCircle(px, py, rad, p) }
                x += step
            }
            y += step * 0.866f
            row++
        }
    }

    private fun clay(c: Canvas, r: Rng, p: Paint, light: Boolean) {
        val shade = ink(light, 0xFF5A3D7A.toInt(), 0xFFFFFFFF.toInt())
        speckle(c, r, p, shade, 3000, 0.05f, 2.2f)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val oval = android.graphics.RectF()
        repeat(3) {
            val cx = r.next() * TILE
            val cy = r.next() * TILE
            val rot = r.next() * 180f
            val rings = 7 + (r.next() * 4).toInt()
            c.wrapped(cx, cy, 70f) { px, py ->
                save()
                rotate(rot, px, py)
                for (k in 1..rings) {
                    val rw = k * 6.5f
                    val rh = k * 4.6f
                    oval.set(px - rw, py - rh, px + rw, py + rh)
                    p.strokeWidth = 1.6f
                    p.color = alphaOf(shade, 0.045f + 0.02f * (k % 2))
                    drawArc(oval, 200f + k * 7f, 250f - k * 9f, false, p)
                }
                restore()
            }
        }
        p.style = Paint.Style.FILL
        repeat(26) {
            val x = r.next() * TILE
            val y = r.next() * TILE
            val rad = 2.5f + r.next() * 4f
            c.wrapped(x, y, rad + 2) { px, py ->
                p.color = alphaOf(0xFF000000.toInt(), if (light) 0.05f else 0.12f)
                drawCircle(px + 0.8f, py + 0.8f, rad, p)
                p.color = alphaOf(0xFFFFFFFF.toInt(), if (light) 0.5f else 0.06f)
                drawCircle(px - 0.6f, py - 0.6f, rad * 0.8f, p)
            }
        }
    }

    private fun frost(c: Canvas, r: Rng, p: Paint, light: Boolean) {
        val ice = ink(light, 0xFF7FA6C2.toInt(), 0xFFE8F6FF.toInt())
        speckle(c, r, p, ice, 3000, if (light) 0.10f else 0.07f)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        repeat(26) {
            val x = r.next() * TILE
            val y = r.next() * TILE
            val rad = 3f + r.next() * 7f
            val rot = r.next() * 60f
            p.strokeWidth = 0.9f
            p.color = alphaOf(ice, (if (light) 0.16f else 0.10f) + r.next() * 0.08f)
            c.wrapped(x, y, rad) { px, py ->
                for (k in 0 until 6) {
                    val a = Math.toRadians((rot + k * 60f).toDouble()).toFloat()
                    val ex = px + cos(a) * rad
                    val ey = py + sin(a) * rad
                    drawLine(px, py, ex, ey, p)
                    val mx = px + cos(a) * rad * 0.55f
                    val my = py + sin(a) * rad * 0.55f
                    val b = rad * 0.28f
                    drawLine(mx, my, mx + cos(a + 0.7f) * b, my + sin(a + 0.7f) * b, p)
                    drawLine(mx, my, mx + cos(a - 0.7f) * b, my + sin(a - 0.7f) * b, p)
                }
            }
        }
        p.style = Paint.Style.FILL
        repeat(40) {
            p.color = alphaOf(0xFFFFFFFF.toInt(), 0.25f + r.next() * 0.35f)
            c.drawCircle(r.next() * TILE, r.next() * TILE, 0.7f + r.next() * 0.8f, p)
        }
    }
}
