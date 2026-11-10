package com.flerty.wikiflerty.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import com.flerty.wikiflerty.ui.Motion
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class ChapterShiftView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Kind { Library, Zen, Comic, News, Sea, Clay }

    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    private var kind = Kind.Library
    private var old: Bitmap? = null
    private var oldShader: BitmapShader? = null
    private var title = ""
    private var forward = true
    private var bg = Color.BLACK
    private var fg = Color.WHITE
    private var accent = Color.YELLOW
    private var page: View? = null

    private var exit = 0f
    private var enter = 0f
    private var entering = false
    private var exitDone = false
    private var revealAsked = false
    private var exitEndAt = 0L
    private var startAt = 0L
    private var animator: ValueAnimator? = null
    private var layoutText: StaticLayout? = null
    private var layoutWidth = -1

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val smallText = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val crest = Path()
    private val rect = RectF()

    private val soft: Interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val ease: Interpolator = PathInterpolator(0.33f, 0f, 0.15f, 1f)

    init {
        visibility = GONE
    }

    val active: Boolean get() = visibility == VISIBLE

    fun start(bitmap: Bitmap?, title: String, forward: Boolean, kind: Kind, background: Int, text: Int, accent: Int, page: View) {
        finish()
        this.kind = kind
        this.old = bitmap
        this.oldShader = bitmap?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        this.title = title.ifBlank { "Глава" }
        this.forward = forward
        this.bg = background
        this.fg = text
        this.accent = accent
        this.page = page
        layoutText = null
        layoutWidth = -1
        exit = 0f
        enter = 0f
        entering = false
        exitDone = false
        revealAsked = false
        startAt = SystemClock.uptimeMillis()
        visibility = VISIBLE
        tick()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.ms(exitMs()).toLong()
            interpolator = when (kind) {
                Kind.Comic -> AccelerateInterpolator(1.4f)
                Kind.Zen, Kind.Sea -> ease
                else -> soft
            }
            addUpdateListener {
                exit = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    exit = 1f
                    exitDone = true
                    exitEndAt = SystemClock.uptimeMillis()
                    if (revealAsked) scheduleEnter()
                }
            })
            start()
        }
    }

    fun reveal() {
        if (!active || revealAsked) return
        revealAsked = true
        if (exitDone) scheduleEnter()
    }

    fun finish() {
        animator?.cancel()
        animator = null
        removeCallbacks(enterRunnable)
        page?.apply {
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
        }
        page = null
        old = null
        oldShader = null
        visibility = GONE
    }

    private val enterRunnable = Runnable { runEnter() }

    private fun scheduleEnter() {
        val waited = SystemClock.uptimeMillis() - exitEndAt
        val hold = max(0L, HOLD_MS - waited)
        removeCallbacks(enterRunnable)
        postDelayed(enterRunnable, hold)
    }

    private fun runEnter() {
        if (!active) return
        entering = true
        val pageCurve: Interpolator = when (kind) {
            Kind.Comic -> OvershootInterpolator(1.3f)
            Kind.Clay -> OvershootInterpolator(1.6f)
            else -> DecelerateInterpolator(1.6f)
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.ms(enterMs()).toLong()
            interpolator = when (kind) {
                Kind.Comic, Kind.Clay -> null
                Kind.Zen, Kind.Sea -> ease
                else -> soft
            } ?: android.view.animation.LinearInterpolator()
            addUpdateListener {
                val raw = it.animatedFraction
                enter = when (kind) {
                    Kind.Comic, Kind.Clay -> soft.getInterpolation(raw)
                    else -> it.animatedValue as Float
                }
                applyPage(pageCurve.getInterpolation(raw))
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) finish()
                }
            })
            start()
        }
    }

    private fun applyPage(v: Float) {
        val p = page ?: return
        val sign = if (forward) 1f else -1f
        val h = height.toFloat()
        val rest = 1f - v
        when (kind) {
            Kind.Library -> p.translationY = sign * rest * h * 0.16f
            Kind.Zen -> {
                val s = 1.02f - 0.02f * v
                p.scaleX = s
                p.scaleY = s
            }
            Kind.Comic -> p.translationY = sign * rest * h * 0.3f
            Kind.News -> Unit
            Kind.Sea -> p.translationY = sign * rest * 22 * d
            Kind.Clay -> {
                val s = 0.92f + 0.08f * v
                p.scaleX = s
                p.scaleY = s
                p.translationY = sign * rest * 46 * d
            }
        }
    }

    private fun exitMs() = when (kind) {
        Kind.Library -> 440
        Kind.Zen -> 560
        Kind.Comic -> 340
        Kind.News -> 480
        Kind.Sea -> 620
        Kind.Clay -> 460
    }

    private fun enterMs() = when (kind) {
        Kind.Library -> 480
        Kind.Zen -> 640
        Kind.Comic -> 520
        Kind.News -> 480
        Kind.Sea -> 660
        Kind.Clay -> 560
    }

    private val HOLD_MS = 5000L

    private fun tick() {
        if (!active) return
        if (kind == Kind.Sea || kind == Kind.Zen || (exitDone && !entering)) invalidate()
        postOnAnimation { tick() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!active) return false
        if (event.actionMasked == MotionEvent.ACTION_UP && exitDone && revealAsked && !entering) {
            removeCallbacks(enterRunnable)
            runEnter()
        }
        return true
    }

    private fun drawHold(canvas: Canvas, w: Float, h: Float) {
        if (!exitDone || !revealAsked) return
        val p = if (entering) 1f else ((SystemClock.uptimeMillis() - exitEndAt).toFloat() / HOLD_MS).coerceIn(0f, 1f)
        val fade = if (entering) (1f - enter * 2f).coerceIn(0f, 1f) else 1f
        if (fade <= 0f) return
        val textH = layoutText?.height?.toFloat() ?: 0f
        val y = h / 2f + textH / 2f + 64 * d
        val half = 34 * d
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeWidth = 2.5f * d
        stroke.color = Color.argb((60 * fade).toInt(), Color.red(fg), Color.green(fg), Color.blue(fg))
        canvas.drawLine(w / 2f - half, y, w / 2f + half, y, stroke)
        stroke.color = Color.argb((255 * fade).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawLine(w / 2f - half, y, w / 2f - half + 2 * half * p, y, stroke)
        stroke.strokeCap = Paint.Cap.BUTT
    }

    private fun clock(): Float = (SystemClock.uptimeMillis() - startAt) / 1000f

    private fun titleIn(): Float {
        val t = ((exit - 0.3f) / 0.7f).coerceIn(0f, 1f)
        return ease.getInterpolation(t)
    }

    private fun prepareText(w: Int) {
        val target = min(w * 0.78f, 440 * d).toInt().coerceAtLeast(1)
        if (layoutText != null && layoutWidth == target) return
        layoutWidth = target
        textPaint.color = fg
        textPaint.letterSpacing = 0f
        val shown = when (kind) {
            Kind.News -> title.uppercase()
            else -> title
        }
        when (kind) {
            Kind.Library -> {
                textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                textPaint.textSize = 23 * sp
            }
            Kind.Zen -> {
                textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                textPaint.textSize = 21 * sp
                textPaint.letterSpacing = 0.08f
            }
            Kind.Comic -> {
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = 20 * sp
            }
            Kind.News -> {
                textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                textPaint.textSize = 22 * sp
                textPaint.letterSpacing = 0.02f
            }
            Kind.Sea -> {
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textPaint.textSize = 21 * sp
            }
            Kind.Clay -> {
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = 19 * sp
            }
        }
        val inner = when (kind) {
            Kind.Comic, Kind.Clay -> (target - 40 * d).toInt().coerceAtLeast(1)
            else -> target
        }
        layoutText = StaticLayout.Builder.obtain(shown, 0, shown.length, textPaint, inner)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.12f)
            .setMaxLines(3)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    override fun onDraw(canvas: Canvas) {
        if (!active) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        prepareText(width)
        when (kind) {
            Kind.Library -> drawLibrary(canvas, w, h)
            Kind.Zen -> drawZen(canvas, w, h)
            Kind.Comic -> drawComic(canvas, w, h)
            Kind.News -> drawNews(canvas, w, h)
            Kind.Sea -> drawSea(canvas, w, h)
            Kind.Clay -> drawClay(canvas, w, h)
        }
        drawHold(canvas, w, h)
    }

    private fun drawSnapshot(canvas: Canvas, w: Float, h: Float) {
        val b = old ?: return
        rect.set(0f, 0f, w, h)
        canvas.drawBitmap(b, null, rect, bitmapPaint)
    }

    private fun edgeShadow(canvas: Canvas, w: Float, y: Float, down: Boolean, strength: Float) {
        if (strength <= 0f) return
        val size = 22 * d
        val top = if (down) y else y - size
        val bottom = if (down) y + size else y
        val dark = Color.argb((70 * strength).toInt().coerceIn(0, 255), 0, 0, 0)
        fill.shader = LinearGradient(0f, if (down) top else bottom, 0f, if (down) bottom else top, dark, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, top, w, bottom, fill)
        fill.shader = null
    }

    private fun withAlpha(color: Int, a: Float): Int =
        Color.argb((Color.alpha(color) * a.coerceIn(0f, 1f)).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    private fun contrast(color: Int): Int {
        val l = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return if (l > 0.6) Color.rgb(24, 20, 16) else Color.WHITE
    }

    private fun drawTitleText(canvas: Canvas, cx: Float, cy: Float, alpha: Float, color: Int = fg) {
        val layout = layoutText ?: return
        textPaint.color = withAlpha(color, alpha)
        canvas.save()
        canvas.translate(cx - layout.width / 2f, cy - layout.height / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawLibrary(canvas: Canvas, w: Float, h: Float) {
        val sign = if (forward) 1f else -1f
        canvas.save()
        if (entering) canvas.translate(0f, -sign * enter * h)
        canvas.drawColor(bg)
        val t = if (entering) 1f else titleIn()
        val cx = w / 2f
        val cy = h * 0.44f
        val lh = layoutText?.height?.toFloat() ?: 0f
        val lineY = cy - lh / 2f - 26 * d
        val reach = 74 * d * t
        stroke.color = withAlpha(accent, 0.85f * t)
        stroke.strokeWidth = 1.3f * d
        canvas.drawLine(cx - 12 * d - reach, lineY, cx - 12 * d, lineY, stroke)
        canvas.drawLine(cx + 12 * d, lineY, cx + 12 * d + reach, lineY, stroke)
        fill.color = withAlpha(accent, t)
        canvas.save()
        canvas.rotate(45f, cx, lineY)
        val s = 4.2f * d * t
        canvas.drawRect(cx - s, lineY - s, cx + s, lineY + s, fill)
        canvas.restore()
        drawTitleText(canvas, cx, cy + (1f - t) * 10 * d, t)
        val lowY = cy + lh / 2f + 22 * d
        val small = 34 * d * t
        stroke.color = withAlpha(accent, 0.5f * t)
        canvas.drawLine(cx - small, lowY, cx + small, lowY, stroke)
        if (entering) {
            if (forward) edgeShadow(canvas, w, h, true, 1f) else edgeShadow(canvas, w, 0f, false, 1f)
        }
        canvas.restore()
        if (!entering && old != null) {
            val y = -sign * exit * h
            canvas.save()
            canvas.translate(0f, y)
            drawSnapshot(canvas, w, h)
            canvas.restore()
            if (forward) edgeShadow(canvas, w, h + y, true, exit) else edgeShadow(canvas, w, y, false, exit)
        }
    }

    private fun drawZen(canvas: Canvas, w: Float, h: Float) {
        if (!entering) {
            val b = old
            if (b != null) {
                val s = 1f - 0.02f * exit
                canvas.save()
                canvas.scale(s, s, w / 2f, h / 2f)
                drawSnapshot(canvas, w, h)
                canvas.restore()
            }
        }
        val veil = if (entering) 1f - enter else exit
        val t = if (entering) (1f - enter * 1.6f).coerceIn(0f, 1f) else titleIn()
        val count = canvas.saveLayerAlpha(0f, 0f, w, h, (255 * veil.coerceIn(0f, 1f)).toInt())
        canvas.drawColor(bg)
        val cx = w / 2f
        val cy = h * 0.46f
        val lh = layoutText?.height?.toFloat() ?: 0f
        val r = 30 * d
        val ey = cy - lh / 2f - r - 22 * d
        val breathe = 1f + 0.02f * sin(clock() * 2.2f)
        stroke.color = withAlpha(accent, 0.9f * t)
        stroke.strokeWidth = 3.2f * d
        stroke.strokeCap = Paint.Cap.ROUND
        rect.set(cx - r * breathe, ey - r * breathe, cx + r * breathe, ey + r * breathe)
        val sweep = 320f * (if (entering) 1f else titleIn())
        canvas.drawArc(rect, -110f, sweep, false, stroke)
        stroke.strokeCap = Paint.Cap.BUTT
        drawTitleText(canvas, cx, cy + (1f - t) * 6 * d, t)
        canvas.restoreToCount(count)
    }

    private fun drawHalftone(canvas: Canvas, w: Float, h: Float, alpha: Float) {
        val step = 15 * d
        fill.color = withAlpha(accent, 0.22f * alpha)
        var y = step / 2f
        var row = 0
        while (y < h + step) {
            var x = if (row % 2 == 0) step / 2f else step
            while (x < w + step) {
                val k = ((x / w) * 0.5f + (y / h) * 0.5f)
                val r = (k * k) * 4.2f * d
                if (r > 0.4f * d) canvas.drawCircle(x, y, r, fill)
                x += step
            }
            y += step * 0.866f
            row++
        }
    }

    private fun drawComic(canvas: Canvas, w: Float, h: Float) {
        val sign = if (forward) 1f else -1f
        canvas.save()
        if (entering) canvas.translate(0f, -sign * enter * h)
        canvas.drawColor(bg)
        drawHalftone(canvas, w, h, 1f)
        val raw = if (entering) 1f else ((exit - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val pop = if (raw <= 0f) 0f else OvershootInterpolator(2.4f).getInterpolation(raw)
        val layout = layoutText
        if (layout != null && pop > 0f) {
            val cx = w / 2f
            val cy = h * 0.45f
            val bw = layout.width + 40 * d
            val bh = layout.height + 28 * d
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(-2.5f)
            canvas.scale(pop, pop)
            val ink = Color.rgb(20, 18, 16)
            fill.color = ink
            rect.set(-bw / 2f + 6 * d, -bh / 2f + 6 * d, bw / 2f + 6 * d, bh / 2f + 6 * d)
            canvas.drawRect(rect, fill)
            fill.color = accent
            rect.set(-bw / 2f, -bh / 2f, bw / 2f, bh / 2f)
            canvas.drawRect(rect, fill)
            stroke.color = ink
            stroke.strokeWidth = 3f * d
            canvas.drawRect(rect, stroke)
            textPaint.color = contrast(accent)
            canvas.translate(-layout.width / 2f, -layout.height / 2f)
            layout.draw(canvas)
            canvas.restore()
        }
        canvas.restore()
        if (!entering && old != null) {
            canvas.save()
            canvas.translate(0f, -sign * exit * h * 1.05f)
            canvas.rotate(-sign * 4f * exit, w / 2f, h / 2f)
            drawSnapshot(canvas, w, h)
            canvas.restore()
        }
    }

    private fun drawNewsCurtain(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(bg)
        val cx = w / 2f
        val cy = h * 0.45f
        val layout = layoutText ?: return
        val lw = max(layout.width.toFloat(), 120 * d).coerceAtMost(w * 0.84f)
        val top = cy - layout.height / 2f - 20 * d
        val bottom = cy + layout.height / 2f + 18 * d
        fill.color = fg
        canvas.drawRect(cx - lw / 2f, top - 3 * d, cx + lw / 2f, top, fill)
        canvas.drawRect(cx - lw / 2f, top + 3 * d, cx + lw / 2f, top + 3.8f * d, fill)
        canvas.drawRect(cx - lw / 2f, bottom, cx + lw / 2f, bottom + 1.2f * d, fill)
        smallText.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        smallText.textSize = 11 * sp
        smallText.letterSpacing = 0.32f
        smallText.color = accent
        smallText.textAlign = Paint.Align.CENTER
        canvas.drawText(if (forward) "ДАЛЕЕ" else "РАНЕЕ", cx, top - 12 * d, smallText)
        drawTitleText(canvas, cx, cy, 1f)
    }

    private fun drawNews(canvas: Canvas, w: Float, h: Float) {
        val v = if (entering) enter else exit
        val line = if (forward) v * (h + 12 * d) - 6 * d else h + 6 * d - v * (h + 12 * d)
        if (!entering) {
            drawSnapshot(canvas, w, h)
            canvas.save()
            if (forward) canvas.clipRect(0f, 0f, w, line) else canvas.clipRect(0f, line, w, h)
            drawNewsCurtain(canvas, w, h)
            canvas.restore()
        } else {
            canvas.save()
            if (forward) canvas.clipRect(0f, line, w, h) else canvas.clipRect(0f, 0f, w, line)
            drawNewsCurtain(canvas, w, h)
            canvas.restore()
        }
        if (v in 0.001f..0.999f) {
            fill.color = accent
            canvas.drawRect(0f, line - 2.5f * d, w, line + 2.5f * d, fill)
            fill.color = fg
            val thin = if (forward) line - 6.5f * d else line + 5.5f * d
            canvas.drawRect(0f, thin, w, thin + 1f * d, fill)
        }
    }

    private fun wave(y: Float, w: Float, below: Boolean, h: Float) {
        val amp = 10 * d
        val len = w / 1.4f
        val phase = clock() * 3.4f
        path.reset()
        crest.reset()
        val edge = if (below) h + amp * 2 else -amp * 2
        path.moveTo(0f, edge)
        var x = 0f
        val step = 6 * d
        while (x <= w + step) {
            val yy = y + amp * sin(phase + (x / len) * 2f * PI.toFloat()) + amp * 0.35f * sin(phase * 1.7f + (x / len) * 5f)
            path.lineTo(x, yy)
            if (x == 0f) crest.moveTo(x, yy) else crest.lineTo(x, yy)
            x += step
        }
        path.lineTo(w + step, edge)
        path.close()
    }

    private fun drawSeaCurtain(canvas: Canvas, w: Float, h: Float, titleAlpha: Float) {
        fill.shader = LinearGradient(0f, 0f, 0f, h, bg, mix(bg, accent, 0.16f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        val c = clock()
        stroke.strokeWidth = 1.4f * d
        for (i in 0 until 9) {
            val speed = 0.18f + (i * 37 % 11) / 40f
            val offset = (i * 53 % 17) / 17f
            val k = ((c * speed + offset) % 1.15f)
            val y = h - k * h
            val x = w * ((i * 29 % 13) / 13f * 0.86f + 0.07f) + sin(c * 1.8f + i) * 8 * d
            val r = (3 + i % 4 * 1.6f) * d
            stroke.color = withAlpha(accent, 0.38f * (1f - k / 1.15f))
            canvas.drawCircle(x, y, r, stroke)
        }
        drawTitleText(canvas, w / 2f, h * 0.45f + (1f - titleAlpha) * 8 * d, titleAlpha)
    }

    private fun drawSea(canvas: Canvas, w: Float, h: Float) {
        val amp = 10 * d
        val v = if (entering) enter else exit
        val span = h + amp * 4
        val y = if (forward) h + amp * 2 - v * span else -amp * 2 + v * span
        val t = if (entering) 1f else titleIn()
        if (!entering) {
            drawSnapshot(canvas, w, h)
            wave(y, w, below = forward, h = h)
        } else {
            wave(y, w, below = !forward, h = h)
        }
        canvas.save()
        canvas.clipPath(path)
        drawSeaCurtain(canvas, w, h, t)
        canvas.restore()
        if (v in 0.001f..0.999f) {
            stroke.color = withAlpha(accent, 0.75f)
            stroke.strokeWidth = 2.2f * d
            canvas.drawPath(crest, stroke)
        }
    }

    private fun drawClayCard(canvas: Canvas, w: Float, h: Float, scale: Float, dy: Float, radius: Float, shadow: Float, content: (Canvas) -> Unit) {
        canvas.save()
        canvas.translate(0f, dy)
        canvas.scale(scale, scale, w / 2f, h / 2f)
        if (shadow > 0f) {
            fill.color = Color.argb((70 * shadow).toInt().coerceIn(0, 255), 0, 0, 0)
            rect.set(0f, 10 * d, w, h + 10 * d)
            canvas.drawRoundRect(rect, radius, radius, fill)
        }
        rect.set(0f, 0f, w, h)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(path)
        content(canvas)
        canvas.restore()
    }

    private fun drawClayTitle(canvas: Canvas, w: Float, h: Float, raw: Float) {
        val layout = layoutText ?: return
        if (raw <= 0f) return
        val pop = OvershootInterpolator(2f).getInterpolation(raw)
        val cx = w / 2f
        val cy = h * 0.45f
        val bw = layout.width + 40 * d
        val bh = layout.height + 30 * d
        val r = min(bh / 2f, 28 * d)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(pop, pop)
        fill.color = mix(accent, Color.BLACK, 0.28f)
        rect.set(-bw / 2f, -bh / 2f + 6 * d, bw / 2f, bh / 2f + 6 * d)
        canvas.drawRoundRect(rect, r, r, fill)
        fill.color = accent
        rect.set(-bw / 2f, -bh / 2f, bw / 2f, bh / 2f)
        canvas.drawRoundRect(rect, r, r, fill)
        fill.color = withAlpha(Color.WHITE, 0.22f)
        rect.set(-bw / 2f + 10 * d, -bh / 2f + 5 * d, bw / 2f - 10 * d, -bh / 2f + 11 * d)
        canvas.drawRoundRect(rect, 3 * d, 3 * d, fill)
        textPaint.color = contrast(accent)
        canvas.translate(-layout.width / 2f, -layout.height / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawClay(canvas: Canvas, w: Float, h: Float) {
        val sign = if (forward) 1f else -1f
        if (!entering) {
            canvas.drawColor(bg)
            drawClayTitle(canvas, w, h, ((exit - 0.3f) / 0.7f).coerceIn(0f, 1f))
            val b = old
            if (b != null) {
                val k = min(1f, exit * 3f)
                drawClayCard(canvas, w, h, 1f - 0.12f * exit, -sign * exit * h * 1.08f, 30 * d * k, k) { c ->
                    rect.set(0f, 0f, w, h)
                    c.drawBitmap(b, null, rect, bitmapPaint)
                }
            }
        } else {
            val k = min(1f, enter * 3f)
            drawClayCard(canvas, w, h, 1f - 0.1f * enter, -sign * enter * h * 1.08f, 30 * d * k, k) { c ->
                c.drawColor(bg)
                drawClayTitle(c, w, h, 1f)
            }
        }
    }
}
