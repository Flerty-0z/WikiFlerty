package com.flerty.wikiflerty.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.flerty.wikiflerty.ui.UiBase
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class QuoteFormat(val id: String, val title: String, val width: Int, val height: Int) {
    Square("square", "Квадрат", 1080, 1080),
    Story("story", "Сторис", 1080, 1920),
}

enum class QuoteLook(val id: String, val title: String) {
    App("app", "Тема"),
    Accent("accent", "Акцент"),
    Paper("paper", "Бумага"),
    Night("night", "Ночь"),
}

data class QuoteDraft(
    val text: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val typeface: Typeface?,
)

class QuoteTheme(
    val base: UiBase,
    val accent: Int,
)

object QuoteCard {
    private const val MAX_CHARS = 700

    fun cleanText(raw: String): String {
        val text = raw.replace(Regex("[ \\t\\u00A0]+"), " ")
            .replace(Regex("\\s*\\n\\s*\\n\\s*"), "\n\n")
            .replace(Regex("(?<!\\n)\\n(?!\\n)"), " ")
            .trim()
        return if (text.length > MAX_CHARS) text.take(MAX_CHARS).trimEnd() + "…" else text
    }

    fun loadCover(path: String?, maxWidth: Int = 360): Bitmap? {
        val file = path?.let { File(it) }?.takeIf { it.exists() } ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    fun render(draft: QuoteDraft, look: QuoteLook, format: QuoteFormat, theme: QuoteTheme, cover: Bitmap?, scale: Float = 1f): Bitmap {
        val w = (format.width * scale).roundToInt()
        val h = (format.height * scale).roundToInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = palette(look, theme)
        paintBackground(canvas, w.toFloat(), h.toFloat(), look, theme, palette)

        val unit = w / 1080f
        val pad = 92f * unit
        val story = format == QuoteFormat.Story

        val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            textSize = 230f * unit
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val markTop = if (story) pad * 2.2f else pad * 0.9f
        val markMetrics = markPaint.fontMetrics
        canvas.drawText("“", pad - 8f * unit, markTop - markMetrics.ascent * 0.62f, markPaint)

        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            alpha = 150
            textSize = 30f * unit
            letterSpacing = 0.08f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("WikiFlerty", w - pad, markTop + 40f * unit, brand)

        val coverH = 176f * unit
        val coverW = coverH * 0.68f
        val footerBottom = h - (if (story) pad * 2.4f else pad)
        val footerTop = footerBottom - coverH
        val textTop = markTop + 112f * unit
        val textBottom = footerTop - 70f * unit
        val textWidth = (w - pad * 2).roundToInt()

        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.text
            typeface = draft.typeface ?: Typeface.SERIF
        }
        val text = cleanText(draft.text)
        val available = textBottom - textTop
        var size = (if (story) 74f else 66f) * unit
        val minSize = 30f * unit
        var layout = quoteLayout(text, quotePaint, size, textWidth, Int.MAX_VALUE)
        while (layout.height > available && size > minSize) {
            size -= 2f * unit
            layout = quoteLayout(text, quotePaint, size, textWidth, Int.MAX_VALUE)
        }
        if (layout.height > available) {
            val lineHeight = layout.height.toFloat() / max(1, layout.lineCount)
            val lines = max(1, (available / lineHeight).toInt())
            layout = quoteLayout(text, quotePaint, size, textWidth, lines)
        }
        val blockTop = textTop + max(0f, (available - layout.height) / (if (story) 2f else 2.6f))
        canvas.save()
        canvas.translate(pad, blockTop)
        layout.draw(canvas)
        canvas.restore()

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            alpha = 150
            strokeWidth = 4f * unit
            strokeCap = Paint.Cap.ROUND
        }
        val lineY = footerTop - 36f * unit
        canvas.drawLine(pad, lineY, pad + 90f * unit, lineY, line)

        val coverRect = RectF(pad, footerTop, pad + coverW, footerBottom)
        val radius = 14f * unit
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
        canvas.drawRoundRect(RectF(coverRect.left + 6f * unit, coverRect.top + 10f * unit, coverRect.right + 6f * unit, coverRect.bottom + 10f * unit), radius, radius, shadow)
        if (cover != null) {
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(coverRect, radius, radius, Path.Direction.CW) })
            canvas.drawBitmap(cover, centerCrop(cover, coverRect), coverRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(coverRect.left, coverRect.top, coverRect.right, coverRect.bottom, palette.accent, darken(palette.accent, 0.45f), Shader.TileMode.CLAMP)
            }
            canvas.drawRoundRect(coverRect, radius, radius, fill)
            val letter = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 64f * unit
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val glyph = draft.title.trim().take(1).uppercase().ifEmpty { "W" }
            canvas.drawText(glyph, coverRect.centerX(), coverRect.centerY() - (letter.ascent() + letter.descent()) / 2, letter)
        }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * unit
            color = 0x33FFFFFF
        }
        canvas.drawRoundRect(coverRect, radius, radius, edge)

        val infoLeft = coverRect.right + 34f * unit
        val infoWidth = w - pad - infoLeft
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.text
            textSize = 42f * unit
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            textSize = 34f * unit
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val titleLayout = StaticLayout.Builder.obtain(draft.title, 0, draft.title.length, titlePaint, infoWidth.roundToInt().coerceAtLeast(1))
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.08f)
            .setIncludePad(false)
            .build()
        val author = TextUtils.ellipsize(draft.author, authorPaint, infoWidth, TextUtils.TruncateAt.END).toString()
        val authorHeight = if (author.isNotBlank()) authorPaint.fontSpacing + 8f * unit else 0f
        val infoHeight = titleLayout.height + authorHeight
        val infoTop = coverRect.centerY() - infoHeight / 2
        canvas.save()
        canvas.translate(infoLeft, infoTop)
        titleLayout.draw(canvas)
        canvas.restore()
        if (author.isNotBlank()) {
            canvas.drawText(author, infoLeft, infoTop + titleLayout.height + 8f * unit - authorPaint.ascent(), authorPaint)
        }
        return bitmap
    }

    private fun quoteLayout(text: String, paint: TextPaint, size: Float, width: Int, maxLines: Int): StaticLayout {
        paint.textSize = size
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.28f)
            .setIncludePad(false)
        if (maxLines != Int.MAX_VALUE) {
            builder.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
        }
        return builder.build()
    }

    private fun centerCrop(src: Bitmap, dst: RectF): Rect {
        val srcRatio = src.width.toFloat() / src.height
        val dstRatio = dst.width() / dst.height()
        return if (srcRatio > dstRatio) {
            val cw = (src.height * dstRatio).roundToInt()
            val x = (src.width - cw) / 2
            Rect(x, 0, x + cw, src.height)
        } else {
            val ch = (src.width / dstRatio).roundToInt()
            val y = (src.height - ch) / 2
            Rect(0, y, src.width, y + ch)
        }
    }

    private class Palette(val text: Int, val muted: Int, val accent: Int)

    private fun palette(look: QuoteLook, theme: QuoteTheme): Palette = when (look) {
        QuoteLook.App -> Palette(0xFFECEDEF.toInt(), 0xFFA3A7AF.toInt(), theme.accent)
        QuoteLook.Accent -> Palette(0xFFFFFFFF.toInt(), 0xCCFFFFFF.toInt(), lighten(theme.accent, 0.45f))
        QuoteLook.Paper -> Palette(0xFF2B2620.toInt(), 0xFF7A6F60.toInt(), darken(theme.accent, 0.42f))
        QuoteLook.Night -> Palette(0xFFE4E5E8.toInt(), 0xFF8A8E96.toInt(), theme.accent)
    }

    private fun paintBackground(canvas: Canvas, w: Float, h: Float, look: QuoteLook, theme: QuoteTheme, palette: Palette) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (look) {
            QuoteLook.App -> {
                canvas.drawColor(theme.base.background.toInt())
                val ambience = theme.base.ambience
                val top = ambience?.topColor
                if (ambience != null && top != null) {
                    val color = top.toInt()
                    paint.shader = LinearGradient(0f, 0f, 0f, h * ambience.topHeight, withAlpha(color, ambience.topAlpha), withAlpha(color, 0f), Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w, h, paint)
                    paint.shader = null
                }
                val spots = ambience?.spots.orEmpty().ifEmpty {
                    listOf(
                        com.flerty.wikiflerty.ui.GlowSpot(theme.accent.toLong() and 0xFFFFFFFFL, 0.34f, 0.15f, 0.08f, 0.70f),
                        com.flerty.wikiflerty.ui.GlowSpot(theme.accent.toLong() and 0xFFFFFFFFL, 0.18f, 0.92f, 0.86f, 0.62f),
                    )
                }
                spots.forEach { spot -> glow(canvas, paint, spot.color.toInt(), spot.alpha, w * spot.x, h * spot.y, w * spot.radius) }
                if (ambience?.dots == true) {
                    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x12FFFFFF }
                    val step = w / 40f
                    var y = step / 2
                    while (y < h) {
                        var x = step / 2
                        while (x < w) {
                            canvas.drawCircle(x, y, w / 720f, dot)
                            x += step
                        }
                        y += step
                    }
                }
            }
            QuoteLook.Accent -> {
                paint.shader = LinearGradient(0f, 0f, w, h, darken(theme.accent, 0.25f), darken(theme.accent, 0.78f), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.shader = null
                glow(canvas, paint, 0xFFFFFFFF.toInt(), 0.16f, w * 0.12f, h * 0.06f, w * 0.75f)
                glow(canvas, paint, theme.accent, 0.40f, w * 0.95f, h * 0.95f, w * 0.7f)
            }
            QuoteLook.Paper -> {
                paint.shader = LinearGradient(0f, 0f, 0f, h, 0xFFF6EFDF.toInt(), 0xFFEADFC7.toInt(), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.shader = null
                glow(canvas, paint, theme.accent, 0.10f, w * 0.9f, h * 0.1f, w * 0.6f)
                val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = w / 540f
                    color = palette.accent
                    alpha = 70
                }
                val inset = w * 0.035f
                canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), w * 0.02f, w * 0.02f, frame)
            }
            QuoteLook.Night -> {
                canvas.drawColor(0xFF07080A.toInt())
                glow(canvas, paint, theme.accent, 0.20f, w * 0.85f, h * 0.92f, w * 0.75f)
                glow(canvas, paint, theme.accent, 0.08f, w * 0.05f, h * 0.05f, w * 0.5f)
            }
        }
    }

    private fun glow(canvas: Canvas, paint: Paint, color: Int, alpha: Float, cx: Float, cy: Float, radius: Float) {
        if (radius <= 0f) return
        paint.shader = RadialGradient(cx, cy, radius, withAlpha(color, alpha), withAlpha(color, 0f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)

    private fun darken(color: Int, amount: Float): Int = mix(color, 0xFF000000.toInt(), amount)

    private fun lighten(color: Int, amount: Float): Int = mix(color, 0xFFFFFFFF.toInt(), amount)

    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - k) + ((b shr shift) and 0xFF) * k).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun previewScale(format: QuoteFormat): Float = min(1f, 720f / max(format.width, format.height))
}
