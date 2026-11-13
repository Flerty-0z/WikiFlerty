package com.flerty.wikiflerty.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import com.flerty.wikiflerty.ui.AppStyle
import com.flerty.wikiflerty.ui.Decor
import com.flerty.wikiflerty.ui.Kit
import com.flerty.wikiflerty.ui.KitMoment
import com.flerty.wikiflerty.ui.ThemeMode
import com.flerty.wikiflerty.ui.renderKitPanel

class WidgetSkin(
    val kit: Kit,
    val light: Boolean,
    val text: Int,
    val muted: Int,
    val faint: Int,
    val accent: Int,
    val density: Float,
    val bg: Int = Color.BLACK,
) {
    val decor: Decor get() = kit.decor

    fun panel(context: Context, wDp: Int, hDp: Int, veil: Float? = null): Bitmap? = runCatching {
        val season = if (AppStyle.tune.living) KitMoment.now().season else null
        renderKitPanel(context, kit, light, (wDp * density).toInt(), (hDp * density).toInt(), density, season, veil)
    }.getOrNull()

    fun card(wDp: Int, hDp: Int): Bitmap? = runCatching {
        val d = density
        val w = (wDp * d).toInt().coerceAtLeast(10)
        val h = (hDp * d).toInt().coerceAtLeast(10)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = when (decor) {
            Decor.News -> 6 * d
            Decor.Comic -> 18 * d
            else -> 24 * d
        }
        val inset = if (decor == Decor.Comic) 1.5f * d else 0.5f * d
        val r = RectF(inset, inset, w - inset, h - inset)
        val fill = if (light) lighten(bg, 0.45f) else darken(bg, 0.62f)
        p.color = withAlpha(fill, if (light) 0.96f else 0.9f)
        c.drawRoundRect(r, radius, radius, p)
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.6f, withAlpha(Color.WHITE, if (light) 0.25f else 0.05f), withAlpha(Color.WHITE, 0f), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, radius, radius, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        if (decor == Decor.Comic) {
            p.strokeWidth = 2.5f * d
            p.color = 0xFF141210.toInt()
        } else {
            p.strokeWidth = 1 * d
            p.color = withAlpha(accent, if (light) 0.28f else 0.2f)
        }
        c.drawRoundRect(r, radius, radius, p)
        bmp
    }.getOrNull()

    fun ring(sizeDp: Int, value: Float): Bitmap {
        val d = density
        val s = (sizeDp * d).toInt().coerceAtLeast(10)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = 4.5f * d
        val half = s / 2f
        val r = half - stroke / 2f - 1 * d
        p.color = withAlpha(accent, if (light) 0.12f else 0.1f)
        c.drawCircle(half, half, r - stroke / 2f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.color = withAlpha(text, if (light) 0.12f else 0.14f)
        c.drawCircle(half, half, r, p)
        p.color = accent
        p.strokeCap = Paint.Cap.ROUND
        val sweep = (value.coerceIn(0f, 1f) * 360f).coerceAtLeast(8f)
        c.drawArc(RectF(half - r, half - r, half + r, half + r), -90f, sweep, false, p)
        p.style = Paint.Style.FILL
        val t = s * 0.15f
        val tri = android.graphics.Path().apply {
            moveTo(half - t * 0.55f, half - t * 0.75f)
            lineTo(half + t * 0.85f, half)
            lineTo(half - t * 0.55f, half + t * 0.75f)
            close()
        }
        c.drawPath(tri, p)
        return bmp
    }

    fun progress(widthDp: Int, value: Float): Bitmap {
        val d = density
        val w = (widthDp * d).toInt().coerceAtLeast(10)
        val h = (10 * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val v = value.coerceIn(0f, 1f)
        val cy = h / 2f
        val track = withAlpha(text, if (light) 0.14f else 0.16f)
        when (decor) {
            Decor.Library -> {
                p.color = track
                c.drawRoundRect(RectF(0f, cy - 2.5f * d, w.toFloat(), cy + 2.5f * d), 2.5f * d, 2.5f * d, p)
                p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), lighten(accent, 0.35f), darken(accent, 0.75f), Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(0f, cy - 2.5f * d, w * v, cy + 2.5f * d), 2.5f * d, 2.5f * d, p)
                p.shader = null
                p.color = accent
                drawDiamond(c, w * v, cy, 4.2f * d, p)
            }
            Decor.Zen -> {
                p.color = track
                p.strokeWidth = 1.6f * d
                p.strokeCap = Paint.Cap.ROUND
                c.drawLine(2 * d, cy, w - 2 * d, cy, p)
                p.color = accent
                c.drawLine(2 * d, cy, (w * v).coerceAtLeast(2 * d), cy, p)
                c.drawCircle((w * v).coerceIn(4 * d, w - 4 * d), cy, 3.6f * d, p)
            }
            Decor.Comic -> {
                val r = RectF(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d)
                p.color = if (light) Color.WHITE else 0xFF3A3A3A.toInt()
                c.drawRoundRect(r, h / 2f, h / 2f, p)
                p.color = accent
                c.drawRoundRect(RectF(r.left, r.top, r.left + (r.width() * v).coerceAtLeast(r.height()), r.bottom), h / 2f, h / 2f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2 * d
                p.color = 0xFF141210.toInt()
                c.drawRoundRect(r, h / 2f, h / 2f, p)
            }
            Decor.News -> {
                p.color = withAlpha(text, 0.5f)
                c.drawRect(0f, cy - 0.5f * d, w.toFloat(), cy + 0.5f * d, p)
                for (i in 0..10) {
                    val x = (w - 1) * i / 10f
                    c.drawRect(x, cy, x + 0.8f * d, cy + (if (i % 5 == 0) 4.5f else 2.5f) * d, p)
                }
                p.color = accent
                c.drawRect(0f, cy - 2.5f * d, w * v, cy + 1f * d, p)
            }
            Decor.Sea -> {
                p.color = track
                c.drawRoundRect(RectF(0f, cy - 4 * d, w.toFloat(), cy + 4 * d), 4 * d, 4 * d, p)
                p.shader = LinearGradient(0f, 0f, (w * v).coerceAtLeast(1f), 0f, lighten(accent, 0.4f), accent, Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(0f, cy - 4 * d, (w * v).coerceAtLeast(8 * d), cy + 4 * d), 4 * d, 4 * d, p)
                p.shader = null
                p.color = withAlpha(Color.WHITE, 0.5f)
                c.drawRoundRect(RectF(3 * d, cy - 2.5f * d, (w * v - 3 * d).coerceAtLeast(4 * d), cy - 1.3f * d), 1 * d, 1 * d, p)
            }
            Decor.Clay -> {
                p.color = darken(track, 0.6f)
                c.drawRoundRect(RectF(0f, cy - 3 * d, w.toFloat(), cy + 5 * d), 4 * d, 4 * d, p)
                p.color = withAlpha(text, if (light) 0.12f else 0.2f)
                c.drawRoundRect(RectF(0f, cy - 4 * d, w.toFloat(), cy + 4 * d), 4 * d, 4 * d, p)
                p.color = accent
                c.drawRoundRect(RectF(0f, cy - 4 * d, (w * v).coerceAtLeast(8 * d), cy + 4 * d), 4 * d, 4 * d, p)
                p.color = withAlpha(Color.WHITE, 0.45f)
                c.drawRoundRect(RectF(3 * d, cy - 3 * d, (w * v - 3 * d).coerceAtLeast(4 * d), cy - 1.5f * d), 1 * d, 1 * d, p)
            }
            Decor.Frost -> {
                p.color = track
                c.drawRoundRect(RectF(0f, cy - 4 * d, w.toFloat(), cy + 4 * d), 4 * d, 4 * d, p)
                val end = (w * v).coerceAtLeast(8 * d)
                val clip = android.graphics.Path().apply { addRoundRect(RectF(0f, cy - 4 * d, end, cy + 4 * d), 4 * d, 4 * d, android.graphics.Path.Direction.CW) }
                c.save()
                c.clipPath(clip)
                p.color = accent
                c.drawRect(0f, cy - 4 * d, end, cy + 4 * d, p)
                p.color = withAlpha(Color.WHITE, 0.6f)
                var sx = -8 * d
                while (sx < end) {
                    val stripe = android.graphics.Path().apply {
                        moveTo(sx, cy + 4 * d)
                        lineTo(sx + 4 * d, cy + 4 * d)
                        lineTo(sx + 12 * d, cy - 4 * d)
                        lineTo(sx + 8 * d, cy - 4 * d)
                        close()
                    }
                    c.drawPath(stripe, p)
                    sx += 10 * d
                }
                c.restore()
            }
            Decor.None -> {
                p.color = track
                c.drawRoundRect(RectF(0f, cy - 2 * d, w.toFloat(), cy + 2 * d), 2 * d, 2 * d, p)
                p.color = accent
                c.drawRoundRect(RectF(0f, cy - 2 * d, w * v, cy + 2 * d), 2 * d, 2 * d, p)
            }
        }
        return bmp
    }

    fun placeholderCover(title: String, author: String, wDp: Int, hDp: Int): Bitmap {
        val d = density
        val w = (wDp * d).toInt()
        val h = (hDp * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val radius = if (decor == Decor.News) 1 * d else 5 * d
        p.color = darken(accent, if (light) 0.85f else 0.55f)
        c.drawRoundRect(r, radius, radius, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1 * d
        p.color = withAlpha(Color.WHITE, 0.4f)
        c.drawRoundRect(RectF(3 * d, 3 * d, w - 3 * d, h - 3 * d), radius, radius, p)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10.5f * d
            isFakeBoldText = true
        }
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, tp, (w - 10 * d).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(4)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        c.save()
        c.translate(5 * d, (h - layout.height) / 2f)
        layout.draw(c)
        c.restore()
        return bmp
    }

    fun tint(views: RemoteViews, id: Int, color: Int) = views.setTextColor(id, color)

    fun icon(views: RemoteViews, id: Int, color: Int) = views.setInt(id, "setColorFilter", color)

    companion object {
        fun current(context: Context): WidgetSkin {
            val style = AppStyle.current
            val kit = Kit.of(style.kit)
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val light = when (ThemeMode.of(style.mode)) {
                ThemeMode.Light -> true
                ThemeMode.Dark -> false
                ThemeMode.System -> !night
            }
            val base = if (light) kit.light else kit.dark
            val raw = style.accent
            val accent = when {
                light && raw == kit.accent -> kit.accentLight
                !light && raw == kit.accentLight -> kit.accent
                else -> raw
            }
            val density = context.resources.displayMetrics.density.coerceAtMost(2.6f)
            return WidgetSkin(kit, light, base.text.toInt(), base.muted.toInt(), base.faint.toInt(), accent.toInt(), density, base.background.toInt())
        }

        fun size(manager: AppWidgetManager, id: Int, fallbackW: Int, fallbackH: Int): Pair<Int, Int> {
            val o = manager.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: fallbackW
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: fallbackH
            return w.coerceIn(80, 600) to h.coerceIn(60, 500)
        }

        fun refreshAll(context: Context) {
            runCatching { ReadingWidget.refresh(context) }
            runCatching { QuoteWidget.refreshAll(context) }
            runCatching { WordWidget.refreshAll(context) }
        }

        private fun drawDiamond(c: Canvas, x: Float, y: Float, r: Float, p: Paint) {
            val path = android.graphics.Path().apply { moveTo(x, y - r); lineTo(x + r, y); lineTo(x, y + r); lineTo(x - r, y); close() }
            c.drawPath(path, p)
        }

        fun withAlpha(color: Int, a: Float) = Color.argb((255 * a).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
        fun darken(color: Int, k: Float) = Color.rgb((Color.red(color) * k).toInt(), (Color.green(color) * k).toInt(), (Color.blue(color) * k).toInt())
        fun lighten(color: Int, k: Float) = Color.rgb(
            (Color.red(color) + (255 - Color.red(color)) * k).toInt(),
            (Color.green(color) + (255 - Color.green(color)) * k).toInt(),
            (Color.blue(color) + (255 - Color.blue(color)) * k).toInt(),
        )
    }
}
