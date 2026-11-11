package com.flerty.wikiflerty.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

class WidgetArt(context: Context, private val skin: WidgetSkin) {
    private val d = skin.density
    private val serif: Typeface = font(context, "Literata.ttf") ?: Typeface.SERIF
    private val mono: Typeface = font(context, "JetBrainsMono.ttf") ?: Typeface.MONOSPACE

    class Canvased(val bitmap: Bitmap, val canvas: Canvas, val w: Float, val h: Float)

    fun base(wDp: Int, hDp: Int): Canvased {
        val card = skin.card(wDp, hDp) ?: Bitmap.createBitmap((wDp * d).toInt().coerceAtLeast(10), (hDp * d).toInt().coerceAtLeast(10), Bitmap.Config.ARGB_8888)
        val bmp = if (card.isMutable) card else card.copy(Bitmap.Config.ARGB_8888, true)
        return Canvased(bmp, Canvas(bmp), bmp.width.toFloat(), bmp.height.toFloat())
    }

    fun label(c: Canvased, text: String, x: Float, y: Float) {
        val p = paint(mono, 10.5f, skin.accent, 600)
        p.letterSpacing = 0.1f
        c.canvas.drawText(text.uppercase(), x, y - p.ascent(), p)
    }

    fun labelHeight(): Float = paint(mono, 10.5f, skin.accent, 600).let { it.descent() - it.ascent() }

    fun paint(face: Typeface, sp: Float, color: Int, weight: Int = 400): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = sp * d
        this.color = color
        if (weight > 400) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fontVariationSettings = "'wght' $weight"
            } else {
                isFakeBoldText = true
            }
        }
    }

    fun layout(text: CharSequence, p: TextPaint, width: Float, spacing: Float = 1.12f, maxLines: Int = Int.MAX_VALUE, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    fun fits(text: String, face: Typeface, color: Int, width: Float, height: Float, minSp: Float, weight: Int = 400, spacing: Float = 1.15f): Boolean {
        val l = layout(text, paint(face, minSp, color, weight), width, spacing)
        return l.height <= height
    }

    fun fit(text: String, face: Typeface, color: Int, width: Float, height: Float, maxSp: Float, minSp: Float, weight: Int = 400, spacing: Float = 1.15f): StaticLayout {
        var sp = maxSp
        while (sp >= minSp) {
            val p = paint(face, sp, color, weight)
            val l = layout(text, p, width, spacing)
            if (l.height <= height) return l
            sp -= 0.5f
        }
        val p = paint(face, minSp, color, weight)
        val full = layout(text, p, width, spacing)
        val lineH = full.height.toFloat() / full.lineCount.coerceAtLeast(1)
        val lines = (height / lineH).toInt().coerceAtLeast(1)
        if (lines >= full.lineCount) return full
        var end = full.getLineEnd(lines - 1).coerceAtMost(text.length)
        var cut = text.substring(0, end).trimEnd()
        val space = cut.lastIndexOf(' ')
        if (space > cut.length / 2) cut = cut.substring(0, space)
        cut = cut.trimEnd(' ', ',', ';', ':', '—', '-', '«') + "…"
        var shown = layout(cut, p, width, spacing, lines)
        while (shown.lineCount > lines && cut.length > 4) {
            end = cut.lastIndexOf(' ', cut.length - 3).takeIf { it > 0 } ?: (cut.length - 4)
            cut = cut.substring(0, end).trimEnd() + "…"
            shown = layout(cut, p, width, spacing, lines)
        }
        return shown
    }

    fun draw(c: Canvased, l: StaticLayout, x: Float, y: Float) {
        c.canvas.save()
        c.canvas.translate(x, y)
        l.draw(c.canvas)
        c.canvas.restore()
    }

    fun cover(c: Canvased, cover: Bitmap, x: Float, y: Float, w: Float, h: Float, radius: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = maxOf(w / cover.width, h / cover.height)
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate(x + (w - cover.width * scale) / 2f, y + (h - cover.height * scale) / 2f)
        }
        shader.setLocalMatrix(m)
        p.shader = shader
        c.canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, p)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1 * d
            color = WidgetSkin.withAlpha(android.graphics.Color.WHITE, 0.12f)
        }
        c.canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, edge)
    }

    fun pill(c: Canvased, rect: RectF, text: String, filled: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = rect.height() / 2f
        if (filled) {
            p.color = skin.accent
            c.canvas.drawRoundRect(rect, r, r, p)
        } else {
            p.color = WidgetSkin.withAlpha(skin.text, if (skin.light) 0.06f else 0.08f)
            c.canvas.drawRoundRect(rect, r, r, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1 * d
            p.color = WidgetSkin.withAlpha(skin.accent, 0.35f)
            c.canvas.drawRoundRect(rect, r, r, p)
        }
        val ink = if (filled) onAccent() else skin.text
        val tp = paint(mono, 12.5f, ink, 600)
        val tw = tp.measureText(text)
        c.canvas.drawText(text, rect.centerX() - tw / 2f, rect.centerY() - (tp.ascent() + tp.descent()) / 2f, tp)
    }

    private fun onAccent(): Int {
        val a = skin.accent
        val lum = (0.299 * android.graphics.Color.red(a) + 0.587 * android.graphics.Color.green(a) + 0.114 * android.graphics.Color.blue(a)) / 255.0
        return if (lum > 0.55) 0xFF1B130C.toInt() else android.graphics.Color.WHITE
    }

    val serifFace: Typeface get() = serif
    val monoFace: Typeface get() = mono

    companion object {
        private val cache = HashMap<String, Typeface?>()

        private fun font(context: Context, name: String): Typeface? = synchronized(cache) {
            cache.getOrPut(name) { runCatching { Typeface.createFromAsset(context.assets, "fonts/$name") }.getOrNull() }
        }
    }
}
