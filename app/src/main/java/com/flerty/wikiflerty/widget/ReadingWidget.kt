package com.flerty.wikiflerty.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
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
                ?: book?.let { skin.placeholderCover(it.title, it.author, 56, 82) }
            ids.forEach { id ->
                val (wDp, hDp) = WidgetSkin.size(manager, id, 250, 110)
                val views = RemoteViews(context.packageName, R.layout.widget_reading)
                skin.panel(context, wDp, hDp)?.let {
                    views.setImageViewBitmap(R.id.widget_bg, it)
                    views.setInt(R.id.widget_root, "setBackgroundColor", 0)
                }
                skin.tint(views, R.id.widget_label, skin.accent)
                skin.tint(views, R.id.widget_title, skin.text)
                skin.tint(views, R.id.widget_author, skin.muted)
                skin.tint(views, R.id.widget_info, skin.faint)

                val intent = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (book != null) intent.setAction(ACTION_OPEN_BOOK).putExtra(EXTRA_BOOK_ID, book.id)
                val pending = PendingIntent.getActivity(
                    context,
                    7,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_root, pending)

                val todayMs = app.stats.days.value[LocalDate.now().toString()].orEmpty().values.sumOf { it.ms }
                val goal = app.libraryPrefs.prefs.value.dailyGoalMinutes
                val minutes = todayMs / 60_000
                val pagesToday = app.stats.days.value[LocalDate.now().toString()].orEmpty().values.sumOf { it.pages }
                val today = when {
                    goal > 0 && app.libraryPrefs.prefs.value.dailyGoalPages -> "Сегодня $pagesToday/$goal стр."
                    goal > 0 -> "Сегодня $minutes/$goal мин"
                    else -> "Сегодня $minutes мин"
                }

                if (book == null) {
                    views.setTextViewText(R.id.widget_label, "WikiFlerty")
                    views.setTextViewText(R.id.widget_title, "Открой книгу, чтобы она появилась здесь")
                    views.setTextViewText(R.id.widget_author, "")
                    views.setViewVisibility(R.id.widget_cover, View.GONE)
                    views.setViewVisibility(R.id.widget_progress, View.GONE)
                    views.setTextViewText(R.id.widget_info, today)
                } else {
                    val percent = (book.progress * 100).roundToInt().coerceIn(0, 100)
                    views.setTextViewText(R.id.widget_label, "Продолжить чтение")
                    views.setTextViewText(R.id.widget_title, book.title)
                    views.setTextViewText(R.id.widget_author, book.author)
                    views.setViewVisibility(R.id.widget_author, if (book.author.isBlank()) View.GONE else View.VISIBLE)
                    views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
                    views.setImageViewBitmap(R.id.widget_progress, skin.progress((wDp - 32 - 68).coerceAtLeast(60), book.progress.toFloat()))
                    views.setTextViewText(R.id.widget_info, "$percent% · $today")
                    if (cover != null) {
                        views.setViewVisibility(R.id.widget_cover, View.VISIBLE)
                        views.setImageViewBitmap(R.id.widget_cover, cover)
                    } else {
                        views.setViewVisibility(R.id.widget_cover, View.GONE)
                    }
                }
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
