package com.flerty.wikiflerty.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.flerty.wikiflerty.ui.Decor
import kotlin.random.Random

class PageTextureView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var decor = Decor.None
    private var fg = Color.BLACK
    private var accent = Color.RED
    private var light = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density

    init {
        isClickable = false
        isFocusable = false
    }

    fun configure(decor: Decor, text: Int, accent: Int, light: Boolean) {
        if (decor == this.decor && text == fg && accent == this.accent && light == this.light) return
        this.decor = decor
        this.fg = text
        this.accent = accent
        this.light = light
        visibility = if (decor == Decor.None) GONE else VISIBLE
        invalidate()
    }

    private fun tint(color: Int, alpha: Float): Int =
        Color.argb((255 * alpha).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        paint.shader = null
        paint.style = Paint.Style.FILL
        when (decor) {
            Decor.Library -> {
                val edge = if (light) Color.argb(34, 90, 60, 20) else Color.argb(70, 0, 0, 0)
                paint.shader = RadialGradient(w / 2, h / 2, maxOf(w, h) * 0.72f, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, edge), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.shader = null
                fibers(canvas, w, h, 46, 0.035f, 11)
            }
            Decor.Zen -> fibers(canvas, w, h, 90, 0.04f, 23)
            Decor.Comic -> {
                dots(canvas, w, h, w, 0f, 0.13f)
                dots(canvas, w, h, 0f, h, 0.1f)
            }
            Decor.News -> {
                val r = Random(7)
                paint.color = tint(fg, if (light) 0.06f else 0.05f)
                for (i in 0 until 900) canvas.drawCircle(r.nextFloat() * w, r.nextFloat() * h, (0.4f + r.nextFloat() * 0.5f) * d, paint)
                paint.color = tint(fg, 0.28f)
                canvas.drawRect(14 * d, 2 * d, w - 14 * d, 3 * d, paint)
                canvas.drawRect(14 * d, 5 * d, w - 14 * d, 5.5f * d, paint)
            }
            Decor.Sea -> {
                val a = if (light) 0.07f else 0.09f
                for ((cx, cy, r) in listOf(Triple(0.2f, 0.1f, 0.6f), Triple(0.85f, 0.3f, 0.5f), Triple(0.4f, 0.75f, 0.55f))) {
                    paint.shader = RadialGradient(w * cx, h * cy, w * r, tint(accent, a), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w, h, paint)
                }
                paint.shader = LinearGradient(0f, h * 0.7f, 0f, h, Color.TRANSPARENT, tint(if (light) accent else Color.BLACK, 0.1f), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, h * 0.7f, w, h, paint)
                paint.shader = null
            }
            Decor.Clay -> {
                val blobs = listOf(
                    Triple(Triple(0f, 0f, 0.42f), accent, 0.1f),
                    Triple(Triple(1f, 0.08f, 0.36f), 0xFFB48CFF.toInt(), 0.09f),
                    Triple(Triple(0.95f, 1f, 0.44f), 0xFF8AD09A.toInt(), 0.09f),
                    Triple(Triple(0.05f, 0.95f, 0.34f), 0xFFFFD27A.toInt(), 0.08f),
                )
                blobs.forEach { (pos, color, alpha) ->
                    paint.shader = RadialGradient(w * pos.first, h * pos.second, w * pos.third, tint(color, alpha), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w, h, paint)
                }
                paint.shader = null
            }
            Decor.None -> Unit
        }
    }

    private fun fibers(canvas: Canvas, w: Float, h: Float, count: Int, alpha: Float, seed: Int) {
        val r = Random(seed)
        paint.color = tint(fg, alpha)
        paint.strokeWidth = 0.8f * d
        for (i in 0 until count) {
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h
            val len = (6f + r.nextFloat() * 20f) * d
            canvas.drawLine(x, y, x + len, y + len * (r.nextFloat() - 0.5f) * 0.4f, paint)
        }
    }

    private fun dots(canvas: Canvas, w: Float, h: Float, cx: Float, cy: Float, alpha: Float) {
        val step = 12 * d
        val reach = w * 0.55f
        paint.color = tint(accent, alpha)
        var y = 0f
        var row = 0
        while (y < h) {
            var x = if (row % 2 == 0) 0f else step / 2
            while (x < w) {
                val dist = kotlin.math.hypot((x - cx).toDouble(), (y - cy).toDouble()).toFloat()
                if (dist < reach) {
                    val k = 1f - dist / reach
                    canvas.drawCircle(x, y, k * k * 4.6f * d, paint)
                }
                x += step
            }
            y += step * 0.866f
            row++
        }
    }
}
