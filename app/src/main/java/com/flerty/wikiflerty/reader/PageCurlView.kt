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

    private val edgeShader = LinearGradient(0f, 0f, 1f, 0f, 0x33000000, 0x00000000, Shader.TileMode.CLAMP)
    private val dropShader = LinearGradient(0f, 0f, 1f, 0f, 0x55000000, 0x00000000, Shader.TileMode.CLAMP)
    private val foldShader = LinearGradient(
        0f, 0f, 1f, 0f,
        intArrayOf(0x44000000, 0x10FFFFFF, 0x22000000),
        floatArrayOf(0f, 0.35f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val shaderMatrix = Matrix()
    private val corners = FloatArray(8)
    private val keepPts = FloatArray(12)
    private val liftPts = FloatArray(12)
    private var keepCount = 0
    private var liftCount = 0
    private val values = FloatArray(9)

    private fun aim(shader: LinearGradient, x: Float, y: Float, dx: Float, dy: Float, length: Float) {
        shaderMatrix.reset()
        shaderMatrix.setScale(length, length)
        shaderMatrix.postRotate(Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat())
        shaderMatrix.postTranslate(x, y)
        shader.setLocalMatrix(shaderMatrix)
        shade.shader = shader
    }

    private fun split(nx: Float, ny: Float, d: Float) {
        keepCount = 0
        liftCount = 0
        for (i in 0 until 4) {
            val ax = corners[2 * i]
            val ay = corners[2 * i + 1]
            val j = (i + 1) % 4
            val bx = corners[2 * j]
            val by = corners[2 * j + 1]
            val fa = nx * ax + ny * ay - d
            val fb = nx * bx + ny * by - d
            if (fa <= 0f) { keepPts[2 * keepCount] = ax; keepPts[2 * keepCount + 1] = ay; keepCount++ }
            if (fa >= 0f) { liftPts[2 * liftCount] = ax; liftPts[2 * liftCount + 1] = ay; liftCount++ }
            if ((fa < 0f && fb > 0f) || (fa > 0f && fb < 0f)) {
                val k = fa / (fa - fb)
                val ix = ax + (bx - ax) * k
                val iy = ay + (by - ay) * k
                keepPts[2 * keepCount] = ix; keepPts[2 * keepCount + 1] = iy; keepCount++
                liftPts[2 * liftCount] = ix; liftPts[2 * liftCount + 1] = iy; liftCount++
            }
        }
    }

    private fun polygon(points: FloatArray, count: Int, path: Path) {
        path.rewind()
        if (count == 0) return
        path.moveTo(points[0], points[1])
        for (i in 1 until count) path.lineTo(points[2 * i], points[2 * i + 1])
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        src.set(0, 0, bmp.width, bmp.height)
        dst.set(0f, 0f, w, h)

        val cx = if (forward) w else 0f
        val dx = cx - px
        val dy = h - py
        val len = hypot(dx, dy)
        if (len < 1f) {
            canvas.drawBitmap(bmp, src, dst, bitmapPaint)
            return
        }
        val nx = dx / len
        val ny = dy / len
        val mx = (cx + px) / 2
        val my = (h + py) / 2
        val d = nx * mx + ny * my

        corners[0] = 0f; corners[1] = 0f
        corners[2] = w; corners[3] = 0f
        corners[4] = w; corners[5] = h
        corners[6] = 0f; corners[7] = h
        split(nx, ny, d)
        polygon(keepPts, keepCount, kept)

        canvas.save()
        canvas.clipPath(kept)
        canvas.drawBitmap(bmp, src, dst, bitmapPaint)
        aim(edgeShader, mx, my, -nx, -ny, 0.08f * w)
        canvas.drawRect(0f, 0f, w, h, shade)
        canvas.restore()

        if (liftCount < 3) return
        values[0] = 1 - 2 * nx * nx; values[1] = -2 * nx * ny; values[2] = 2 * d * nx
        values[3] = -2 * nx * ny; values[4] = 1 - 2 * ny * ny; values[5] = 2 * d * ny
        values[6] = 0f; values[7] = 0f; values[8] = 1f
        reflect.setValues(values)
        reflect.mapPoints(liftPts, 0, liftPts, 0, liftCount)
        polygon(liftPts, liftCount, flap)

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
        aim(dropShader, mx, my, nx, ny, minOf(len * 0.5f, 0.14f * w))
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
        aim(foldShader, mx, my, -nx, -ny, minOf(len * 0.5f, 0.35f * w))
        canvas.drawPath(flap, shade)
        canvas.restore()
    }
}
