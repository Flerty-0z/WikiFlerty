package com.flerty.wikiflerty.reader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

class PageCurlView(context: Context) : View(context) {
    private var bitmap: Bitmap? = null
    private var forward = true
    private var px = 0f
    private var py = 0f
    private var animator: ValueAnimator? = null
    var paper: Int = Color.WHITE

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 38 }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val kept = Path()
    private val flap = Path()
    private val src = Rect()
    private val dst = RectF()
    private val reflect = Matrix()

    init {
        visibility = GONE
        isClickable = false
    }

    val running: Boolean get() = animator?.isRunning == true

    private fun autoPoint(t: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        px = if (forward) w - t * (2 * w + 40f) else t * (2 * w + 40f)
        py = h - sin(PI * t).toFloat() * h * 0.16f - t * h * 0.04f
    }

    fun play(page: Bitmap, forward: Boolean, duration: Long = 520, onEnd: () -> Unit) {
        animator?.cancel()
        bitmap = page
        this.forward = forward
        autoPoint(0f)
        alpha = 1f
        visibility = VISIBLE
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                autoPoint(it.animatedValue as Float)
                invalidate()
            }
            addListener(endListener { hide(); onEnd() })
            start()
        }
    }

    fun begin(page: Bitmap, forward: Boolean) {
        animator?.cancel()
        bitmap = page
        this.forward = forward
        px = if (forward) width.toFloat() else 0f
        py = height.toFloat()
        alpha = 1f
        visibility = VISIBLE
        invalidate()
    }

    fun drag(x: Float, y: Float) {
        if (bitmap == null) return
        px = x
        py = y
        invalidate()
    }

    fun release(complete: Boolean, onEnd: (Boolean) -> Unit) {
        animator?.cancel()
        val w = width.toFloat()
        val h = height.toFloat()
        val fromX = px
        val fromY = py
        val toX = if (complete) (if (forward) -w - 40f else 2 * w + 40f) else (if (forward) w else 0f)
        val toY = if (complete) h * 0.96f else h
        val share = (kotlin.math.abs(toX - fromX) / (2 * w + 40f)).coerceIn(0f, 1f)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (150 + 300 * share).toLong()
            interpolator = android.view.animation.DecelerateInterpolator(1.3f)
            addUpdateListener {
                val k = it.animatedValue as Float
                px = fromX + (toX - fromX) * k
                py = fromY + (toY - fromY) * k
                invalidate()
            }
            addListener(endListener { onEnd(complete) })
            start()
        }
    }

    fun hide() {
        visibility = GONE
        bitmap = null
    }

    private fun endListener(block: () -> Unit) = object : android.animation.AnimatorListenerAdapter() {
        private var done = false
        override fun onAnimationEnd(animation: android.animation.Animator) = finish()
        override fun onAnimationCancel(animation: android.animation.Animator) = finish()
        private fun finish() {
            if (done) return
            done = true
            block()
        }
    }

    fun cancel() = animator?.cancel()

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        src.set(0, 0, bmp.width, bmp.height)
        dst.set(0f, 0f, w, h)

        val corner = PointF(if (forward) w else 0f, h)
        val dx = corner.x - px
        val dy = corner.y - py
        val len = hypot(dx, dy)
        if (len < 1f) {
            canvas.drawBitmap(bmp, src, dst, bitmapPaint)
            return
        }
        val nx = dx / len
        val ny = dy / len
        val mx = (corner.x + px) / 2
        val my = (corner.y + py) / 2
        val d = nx * mx + ny * my

        val rect = listOf(PointF(0f, 0f), PointF(w, 0f), PointF(w, h), PointF(0f, h))
        val keep = clip(rect) { p -> nx * p.x + ny * p.y - d <= 0f }
        val lift = clip(rect) { p -> nx * p.x + ny * p.y - d >= 0f }
        toPath(keep, kept)

        canvas.save()
        canvas.clipPath(kept)
        canvas.drawBitmap(bmp, src, dst, bitmapPaint)
        val edge = 0.08f * w
        shade.shader = LinearGradient(mx, my, mx - nx * edge, my - ny * edge, 0x33000000, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, shade)
        canvas.restore()

        if (lift.size < 3) return
        reflect.setValues(
            floatArrayOf(
                1 - 2 * nx * nx, -2 * nx * ny, 2 * d * nx,
                -2 * nx * ny, 1 - 2 * ny * ny, 2 * d * ny,
                0f, 0f, 1f,
            ),
        )
        val pts = FloatArray(lift.size * 2)
        lift.forEachIndexed { i, p -> pts[2 * i] = p.x; pts[2 * i + 1] = p.y }
        reflect.mapPoints(pts)
        flap.reset()
        flap.moveTo(pts[0], pts[1])
        for (i in 1 until lift.size) flap.lineTo(pts[2 * i], pts[2 * i + 1])
        flap.close()

        canvas.save()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutPath(kept)
            canvas.clipOutPath(flap)
        } else {
            @Suppress("DEPRECATION")
            canvas.clipPath(kept, Region.Op.DIFFERENCE)
            @Suppress("DEPRECATION")
            canvas.clipPath(flap, Region.Op.DIFFERENCE)
        }
        val reach = minOf(len * 0.5f, 0.14f * w)
        shade.shader = LinearGradient(mx, my, mx + nx * reach, my + ny * reach, 0x55000000, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, shade)
        canvas.restore()

        canvas.save()
        canvas.clipPath(flap)
        fill.color = paper
        canvas.drawPath(flap, fill)
        canvas.save()
        canvas.concat(reflect)
        canvas.drawBitmap(bmp, src, dst, backPaint)
        canvas.restore()
        val depth = minOf(len * 0.5f, 0.35f * w)
        shade.shader = LinearGradient(
            mx, my, mx - nx * depth, my - ny * depth,
            intArrayOf(0x44000000, 0x10FFFFFF, 0x22000000),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(flap, shade)
        canvas.restore()
    }

    private fun clip(poly: List<PointF>, inside: (PointF) -> Boolean): List<PointF> {
        val out = mutableListOf<PointF>()
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val ia = inside(a)
            val ib = inside(b)
            if (ia) out += a
            if (ia != ib) out += intersect(a, b)
        }
        return out
    }

    private fun intersect(a: PointF, b: PointF): PointF {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = if (forward) w else 0f
        val dx = cx - px
        val dy = h - py
        val len = hypot(dx, dy)
        val nx = dx / len
        val ny = dy / len
        val d = nx * (cx + px) / 2 + ny * (h + py) / 2
        val fa = nx * a.x + ny * a.y - d
        val fb = nx * b.x + ny * b.y - d
        val k = fa / (fa - fb)
        return PointF(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k)
    }

    private fun toPath(points: List<PointF>, path: Path) {
        path.reset()
        if (points.isEmpty()) return
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
        path.close()
    }
}
