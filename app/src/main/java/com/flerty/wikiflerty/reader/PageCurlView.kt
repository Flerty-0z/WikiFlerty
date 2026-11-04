package com.flerty.wikiflerty.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

class PageCurlView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("page-curl", android.os.Process.THREAD_PRIORITY_DISPLAY).also { it.start() }
    private val render = Handler(thread.looper)
    private var choreographer: Choreographer? = null

    @Volatile var paper: Int = Color.WHITE
    @Volatile var showing = false
        private set

    private var surfaceReady = false
    private var w = 0f
    private var h = 0f
    private var bitmap: Bitmap? = null
    private var forward = true
    private var px = 0f
    private var py = 0f
    private var mode = 0
    private var startNanos = -1L
    private var duration = 0L
    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var frameQueued = false
    private var firstFrame: (() -> Unit)? = null
    private var finish: (() -> Unit)? = null
    private val autoEase = AccelerateDecelerateInterpolator()
    private val releaseEase = DecelerateInterpolator(1.3f)
    private var generation = 0

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
        isClickable = false
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
        render.post { choreographer = Choreographer.getInstance() }
    }

    val running: Boolean get() = showing

    override fun surfaceCreated(holder: SurfaceHolder) {
        render.post {
            surfaceReady = true
            present(clear = bitmap == null)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        render.post {
            w = width.toFloat()
            h = height.toFloat()
            present(clear = bitmap == null)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val done = java.util.concurrent.CountDownLatch(1)
        render.post {
            surfaceReady = false
            if (mode != 0) completeNow()
            done.countDown()
        }
        runCatching { done.await(300, java.util.concurrent.TimeUnit.MILLISECONDS) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        render.post { thread.quitSafely() }
    }

    private fun autoPoint(t: Float) {
        px = if (forward) w - t * (2 * w + 40f) else t * (2 * w + 40f)
        py = h - sin(PI * t).toFloat() * h * 0.16f - t * h * 0.04f
    }

    fun play(page: Bitmap, forward: Boolean, duration: Long = 520, onShown: (() -> Unit)? = null, onEnd: () -> Unit) {
        showing = true
        render.post {
            generation++
            completePending(false)
            bitmap = page
            this.forward = forward
            this.duration = duration
            mode = 1
            startNanos = -1L
            autoPoint(0f)
            firstFrame = onShown
            finish = { main.post(onEnd) }
            schedule()
        }
    }

    fun begin(page: Bitmap, forward: Boolean, onShown: (() -> Unit)? = null) {
        showing = true
        render.post {
            generation++
            completePending(false)
            bitmap = page
            this.forward = forward
            mode = 0
            px = if (forward) w else 0f
            py = h
            firstFrame = onShown
            schedule()
        }
    }

    fun drag(x: Float, y: Float) {
        render.post {
            if (bitmap == null || mode != 0) return@post
            px = x
            py = y
            schedule()
        }
    }

    fun release(complete: Boolean, onEnd: (Boolean) -> Unit) {
        render.post {
            if (bitmap == null) {
                main.post { onEnd(complete) }
                return@post
            }
            fromX = px
            fromY = py
            toX = if (complete) (if (forward) -w - 40f else 2 * w + 40f) else (if (forward) w else 0f)
            toY = if (complete) h * 0.96f else h
            val share = (kotlin.math.abs(toX - fromX) / (2 * w + 40f)).coerceIn(0f, 1f)
            duration = (150 + 300 * share).toLong()
            mode = 2
            startNanos = -1L
            finish = { main.post { onEnd(complete) } }
            schedule()
        }
    }

    fun hide() {
        showing = false
        render.post {
            generation++
            firstFrame?.let { cb -> firstFrame = null; main.post(cb) }
            finish = null
            mode = 0
            bitmap = null
            present(clear = true)
        }
    }

    fun cancel() {
        render.post { if (mode != 0) completeNow() }
    }

    private fun completePending(run: Boolean) {
        val f = finish
        finish = null
        if (run) f?.invoke()
    }

    private fun completeNow() {
        if (mode == 1) autoPoint(1f)
        if (mode == 2) {
            px = toX
            py = toY
        }
        mode = 0
        completePending(true)
    }

    private val frame = Choreographer.FrameCallback { nanos -> onFrame(nanos) }

    private fun schedule() {
        if (frameQueued) return
        val c = choreographer
        if (c == null || !surfaceReady) {
            present(clear = false)
            if (!surfaceReady && mode != 0) completeNow()
            return
        }
        frameQueued = true
        c.postFrameCallback(frame)
    }

    private fun onFrame(nanos: Long) {
        frameQueued = false
        if (mode != 0) {
            if (startNanos < 0) startNanos = nanos
            val t = ((nanos - startNanos) / 1_000_000f / duration.coerceAtLeast(1)).coerceIn(0f, 1f)
            if (mode == 1) {
                autoPoint(autoEase.getInterpolation(t))
            } else {
                val k = releaseEase.getInterpolation(t)
                px = fromX + (toX - fromX) * k
                py = fromY + (toY - fromY) * k
            }
            present(clear = false)
            if (t >= 1f) {
                mode = 0
                completePending(true)
            } else {
                schedule()
            }
        } else {
            present(clear = false)
        }
    }

    private fun present(clear: Boolean) {
        if (!surfaceReady) return
        val surface = holder.surface
        if (!surface.isValid) return
        val canvas = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) surface.lockHardwareCanvas() else surface.lockCanvas(null)
        }.getOrNull() ?: return
        try {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            if (!clear && bitmap != null) drawCurl(canvas)
        } finally {
            runCatching { surface.unlockCanvasAndPost(canvas) }
        }
        if (!clear && bitmap != null) {
            firstFrame?.let { cb ->
                firstFrame = null
                val gen = generation
                render.post { if (gen == generation) main.post(cb) }
            }
        }
    }

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

    private fun drawCurl(canvas: Canvas) {
        val bmp = bitmap ?: return
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
