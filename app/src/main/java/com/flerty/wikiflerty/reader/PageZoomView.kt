package com.flerty.wikiflerty.reader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot

class PageZoomView(context: Context) : View(context) {
    var onShown: () -> Unit = {}
    var onHidden: () -> Unit = {}

    private var bitmap: Bitmap? = null
    private val box = RectF()
    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private var fade = 0f
    private var animator: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    var owns = false
        private set
    var shown = false
        private set
    private var probing = false
    private var token = 0

    private var lastX = 0f
    private var lastY = 0f
    private var lastSpan = 0f
    private var lastCount = 0
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var multi = false
    private var tapAt = 0L
    private var tapX = 0f
    private var tapY = 0f
    private val pendingClose = Runnable { close() }

    init {
        visibility = GONE
        isClickable = false
    }

    fun begin(ev: MotionEvent): Int {
        animator?.cancel()
        removeCallbacks(pendingClose)
        owns = true
        multi = true
        moved = true
        if (!shown) {
            scale = 1f
            tx = 0f
            ty = 0f
            fade = 0f
            probing = true
        }
        measure(ev, -1)
        return ++token
    }

    fun fail(ticket: Int) {
        if (ticket != token) return
        probing = false
    }

    fun show(ticket: Int, image: Bitmap, rect: RectF) {
        if (ticket != token || !probing) return
        probing = false
        if (!owns && scale < 1.15f) return
        bitmap = image
        val k = minOf(rect.width() / image.width, rect.height() / image.height)
        val w = image.width * k
        val h = image.height * k
        box.set(rect.centerX() - w / 2, rect.centerY() - h / 2, rect.centerX() + w / 2, rect.centerY() + h / 2)
        shown = true
        visibility = VISIBLE
        updateFade()
        invalidate()
        onShown()
        if (!owns) settle()
    }

    fun touch(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                owns = true
                moved = false
                multi = false
                downX = ev.x
                downY = ev.y
                measure(ev, -1)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multi = true
                moved = true
                measure(ev, -1)
            }
            MotionEvent.ACTION_POINTER_UP -> measure(ev, ev.actionIndex)
            MotionEvent.ACTION_MOVE -> {
                val count = ev.pointerCount
                val (fx, fy, span) = focus(ev, -1)
                if (count != lastCount) {
                    measure(ev, -1)
                    return
                }
                if (count >= 2 && lastSpan > 0f) {
                    val next = (scale * span / lastSpan).coerceIn(0.6f, 8f)
                    val k = next / scale
                    tx = fx - (lastX - tx) * k
                    ty = fy - (lastY - ty) * k
                    scale = next
                } else if (count == 1 && shown && scale > 1.01f) {
                    tx += fx - lastX
                    ty += fy - lastY
                }
                if (hypot(ev.x - downX, ev.y - downY) > slop) moved = true
                lastX = fx
                lastY = fy
                lastSpan = span
                updateFade()
                if (shown) invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                owns = false
                if (!shown) return
                if (ev.actionMasked == MotionEvent.ACTION_UP && !moved && !multi) {
                    tap(ev.x, ev.y)
                    return
                }
                if (scale < 1.12f) close() else settle()
            }
        }
    }

    private fun tap(x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - tapAt < 300 && hypot(x - tapX, y - tapY) < slop * 4) {
            tapAt = 0L
            removeCallbacks(pendingClose)
            if (scale > 1.6f) close() else zoomAt(2.6f, x, y)
            return
        }
        tapAt = now
        tapX = x
        tapY = y
        removeCallbacks(pendingClose)
        postDelayed(pendingClose, 300)
    }

    fun close() {
        removeCallbacks(pendingClose)
        if (!shown) return
        animateTo(1f, 0f, 0f) {
            shown = false
            bitmap = null
            fade = 0f
            visibility = GONE
            onHidden()
        }
    }

    fun dismissNow() {
        animator?.cancel()
        removeCallbacks(pendingClose)
        shown = false
        probing = false
        owns = false
        bitmap = null
        visibility = GONE
        onHidden()
    }

    private fun zoomAt(target: Float, x: Float, y: Float) {
        val k = target / scale
        val nx = x - (x - tx) * k
        val ny = y - (y - ty) * k
        val (cx, cy) = clamp(target, nx, ny)
        animateTo(target, cx, cy)
    }

    private fun settle() {
        val s = scale.coerceIn(1f, 8f)
        val (cx, cy) = clamp(s, tx, ty)
        animateTo(s, cx, cy)
    }

    private fun clamp(s: Float, x: Float, y: Float): Pair<Float, Float> {
        val w = width.toFloat()
        val h = height.toFloat()
        val dw = box.width() * s
        val dh = box.height() * s
        val left = x + box.left * s
        val top = y + box.top * s
        val nl = if (dw >= w) left.coerceIn(w - dw, 0f) else (w - dw) / 2
        val nt = if (dh >= h) top.coerceIn(h - dh, 0f) else (h - dh) / 2
        return (nl - box.left * s) to (nt - box.top * s)
    }

    private fun animateTo(s: Float, x: Float, y: Float, end: (() -> Unit)? = null) {
        animator?.cancel()
        val s0 = scale
        val x0 = tx
        val y0 = ty
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val p = it.animatedValue as Float
                scale = s0 + (s - s0) * p
                tx = x0 + (x - x0) * p
                ty = y0 + (y - y0) * p
                updateFade()
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) end?.invoke()
                }
            })
            start()
        }
    }

    private fun updateFade() {
        fade = ((scale - 1f) / 0.5f).coerceIn(0f, 1f)
    }

    private fun focus(ev: MotionEvent, skip: Int): Triple<Float, Float, Float> {
        var sx = 0f
        var sy = 0f
        var n = 0
        for (i in 0 until ev.pointerCount) {
            if (i == skip) continue
            sx += ev.getX(i)
            sy += ev.getY(i)
            n++
        }
        if (n == 0) return Triple(lastX, lastY, 0f)
        val fx = sx / n
        val fy = sy / n
        var d = 0f
        for (i in 0 until ev.pointerCount) {
            if (i == skip) continue
            d += hypot(ev.getX(i) - fx, ev.getY(i) - fy)
        }
        return Triple(fx, fy, d / n)
    }

    private fun measure(ev: MotionEvent, skip: Int) {
        val (fx, fy, span) = focus(ev, skip)
        lastX = fx
        lastY = fy
        lastSpan = span
        lastCount = ev.pointerCount - if (skip >= 0) 1 else 0
    }

    override fun onDraw(canvas: Canvas) {
        val image = bitmap ?: return
        if (!shown) return
        canvas.drawColor(Color.argb((fade * 235).toInt(), 0, 0, 0))
        canvas.save()
        canvas.translate(tx, ty)
        canvas.scale(scale, scale)
        canvas.drawBitmap(image, null, box, paint)
        canvas.restore()
    }
}
