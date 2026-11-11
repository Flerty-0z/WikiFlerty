package com.flerty.wikiflerty.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.R
import com.flerty.wikiflerty.ui.MainActivity
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReadingWidget : AppWidgetProvider() {
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext as App
        app.appScope.launch(Dispatchers.IO) {
            try {
                update(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_OPEN_BOOK = "com.flerty.wikiflerty.OPEN_BOOK"
        const val EXTRA_BOOK_ID = "bookId"

        fun refreshSoon(app: App) {
            app.appScope.launch(Dispatchers.IO) { runCatching { refresh(app) } }
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ReadingWidget::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = context.applicationContext as App
            val book = app.library.books.value
                .filter { it.lastReadAt != null && !it.paper }
                .maxByOrNull { it.lastReadAt ?: 0L }
            val skin = WidgetSkin.current(context)
            val coverFile = book?.let { app.library.coverFile(it) }?.takeIf { it.exists() }
            val cover = coverFile?.let { loadCover(it.path) }
                ?: book?.let { skin.placeholderCover(it.title, it.author, 64, 96) }
            val todayMs = app.stats.days.value[LocalDate.now().toString()].orEmpty().values.sumOf { it.ms }
            val goal = app.libraryPrefs.prefs.value.dailyGoalMinutes
            val minutes = todayMs / 60_000
            val pagesToday = app.stats.days.value[LocalDate.now().toString()].orEmpty().values.sumOf { it.pages }
            val today = when {
                goal > 0 && app.libraryPrefs.prefs.value.dailyGoalPages -> "сегодня $pagesToday/$goal стр."
                goal > 0 -> "сегодня $minutes/$goal мин"
                else -> "сегодня $minutes мин"
            }
            val art = WidgetArt(context, skin)
            ids.forEach { id ->
                val (wDp, hDp) = WidgetSkin.size(manager, id, 280, 120)
                val views = RemoteViews(context.packageName, R.layout.widget_reading)
                val c = art.base(wDp, hDp)
                val d = skin.density
                val pad = 16 * d
                val ringSize = 58
                val ringPx = ringSize * d
                if (book == null) {
                    art.label(c, "WikiFlerty", pad, pad)
                    val text = art.fit("Открой книгу — она появится здесь, чтобы продолжить с того же места", art.serifFace, skin.text, c.w - pad * 2, c.h - pad * 2 - art.labelHeight() - 8 * d, 17f, 12f)
                    art.draw(c, text, pad, pad + art.labelHeight() + 8 * d)
                } else {
                    val percent = (book.progress * 100).roundToInt().coerceIn(0, 100)
                    val coverH = (c.h - pad * 2).coerceAtMost(96 * d)
                    val coverW = coverH * 0.68f
                    val coverY = (c.h - coverH) / 2f
                    if (cover != null) art.cover(c, cover, pad, coverY, coverW, coverH, 6 * d)
                    val textX = pad + (if (cover != null) coverW + 12 * d else 0f)
                    val ringX = c.w - pad - ringPx
                    val textW = (ringX - 10 * d - textX).coerceAtLeast(40 * d)
                    val labelH = art.labelHeight()
                    val info = art.layout("$percent% · $today", art.paint(art.monoFace, 11f, skin.faint), textW, maxLines = 1)
                    val titleRoom = c.h - pad * 2 - labelH - 6 * d - info.height - 6 * d
                    val title = art.fit(book.title, art.monoFace, skin.text, textW, titleRoom.coerceAtLeast(16 * d), 15.5f, 12f, weight = 700, spacing = 1.08f)
                    val block = labelH + 6 * d + title.height + 6 * d + info.height
                    var y = (c.h - block) / 2f
                    art.label(c, "Продолжить", textX, y)
                    y += labelH + 6 * d
                    art.draw(c, title, textX, y)
                    y += title.height + 6 * d
                    art.draw(c, info, textX, y)
                    val ring = skin.ring(ringSize, book.progress.toFloat())
                    c.canvas.drawBitmap(ring, ringX, (c.h - ring.height) / 2f, null)
                }
                views.setImageViewBitmap(R.id.widget_art, c.bitmap)
                val intent = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (book != null) intent.setAction(ACTION_OPEN_BOOK).putExtra(EXTRA_BOOK_ID, book.id)
                val pending = PendingIntent.getActivity(context, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                manager.updateAppWidget(id, views)
            }
        }

        private fun loadCover(path: String): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outHeight / sample > 360) sample *= 2
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            if (bitmap.height <= 240) bitmap else Bitmap.createScaledBitmap(bitmap, (bitmap.width * 240f / bitmap.height).toInt().coerceAtLeast(1), 240, true)
        }.getOrNull()
    }
}
