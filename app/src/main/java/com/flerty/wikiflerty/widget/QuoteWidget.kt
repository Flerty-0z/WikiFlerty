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
import com.flerty.wikiflerty.data.Note
import com.flerty.wikiflerty.ui.MainActivity
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class QuoteWidget : AppWidgetProvider() {
    open val small: Boolean = false

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext as App
        app.appScope.launch(Dispatchers.IO) {
            try {
                refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_NEXT) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_OFFSET, prefs.getInt(KEY_OFFSET, 0) + 1).apply()
            val pending = goAsync()
            val app = context.applicationContext as App
            app.appScope.launch(Dispatchers.IO) {
                try {
                    refreshAll(context)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_NEXT = "com.flerty.wikiflerty.QUOTE_NEXT"
        const val ACTION_OPEN_NOTE = "com.flerty.wikiflerty.OPEN_NOTE"
        const val EXTRA_NOTE_ID = "noteId"
        private const val PREFS = "quote_widget"
        private const val KEY_OFFSET = "offset"

        fun refreshSoon(app: App) {
            app.appScope.launch(Dispatchers.IO) { runCatching { refreshAll(app) } }
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val big = manager.getAppWidgetIds(ComponentName(context, QuoteWidget::class.java))
            val little = manager.getAppWidgetIds(ComponentName(context, QuoteWidgetSmall::class.java))
            if (big.isEmpty() && little.isEmpty()) return
            val note = pick(context)
            if (big.isNotEmpty()) {
                val views = build(context, note, small = false)
                big.forEach { manager.updateAppWidget(it, views) }
            }
            if (little.isNotEmpty()) {
                val views = build(context, note, small = true)
                little.forEach { manager.updateAppWidget(it, views) }
            }
        }

        private fun pick(context: Context): Note? {
            val app = context.applicationContext as App
            val notes = app.notes.notes.value.filter { it.text.isNotBlank() }.sortedBy { it.createdAt }
            if (notes.isEmpty()) return null
            val offset = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_OFFSET, 0)
            val index = ((LocalDate.now().toEpochDay() + offset) % notes.size).toInt().let { if (it < 0) it + notes.size else it }
            return notes[index]
        }

        private fun build(context: Context, note: Note?, small: Boolean): RemoteViews {
            val app = context.applicationContext as App
            val views = RemoteViews(context.packageName, if (small) R.layout.widget_quote_small else R.layout.widget_quote)
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (note != null) open.setAction(ACTION_OPEN_NOTE).putExtra(EXTRA_NOTE_ID, note.id)
            val openPending = PendingIntent.getActivity(
                context,
                if (small) 41 else 40,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.quote_root, openPending)

            if (note == null) {
                views.setTextViewText(R.id.quote_text, "Выдели в книге цитату или сделай заметку — она появится здесь.")
                views.setTextViewText(R.id.quote_book, "WikiFlerty")
                if (!small) {
                    views.setTextViewText(R.id.quote_meta, "")
                    views.setViewVisibility(R.id.quote_cover, View.GONE)
                    views.setViewVisibility(R.id.quote_open, View.GONE)
                    views.setViewVisibility(R.id.quote_next, View.GONE)
                }
                return views
            }

            val book = app.library.books.value.firstOrNull { it.id == note.bookId }
            val quote = note.text.trim().replace(Regex("\\s+"), " ").let { if (it.length > 320) it.take(320).trimEnd() + "…" else it }
            views.setTextViewText(R.id.quote_text, "«$quote»")
            views.setTextViewText(R.id.quote_book, book?.title ?: "Удалённая книга")
            if (!small) {
                val place = if (book != null && book.positions > 0) {
                    "${(note.progression * book.positions).roundToInt().coerceIn(1, book.positions)}/${book.positions}"
                } else {
                    "${(note.progression * 100).roundToInt()}%"
                }
                views.setTextViewText(R.id.quote_meta, listOfNotNull(book?.author?.takeIf { it.isNotBlank() }, place).joinToString(" · "))
                views.setViewVisibility(R.id.quote_open, View.VISIBLE)
                views.setViewVisibility(R.id.quote_next, View.VISIBLE)
                views.setOnClickPendingIntent(R.id.quote_open, openPending)
                val next = Intent(context, QuoteWidget::class.java).setAction(ACTION_NEXT)
                views.setOnClickPendingIntent(
                    R.id.quote_next,
                    PendingIntent.getBroadcast(context, 42, next, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                )
                val cover = book?.let { app.library.coverFile(it) }?.takeIf { it.exists() }?.let { loadCover(it.path) }
                views.setViewVisibility(R.id.quote_cover, View.VISIBLE)
                if (cover != null) views.setImageViewBitmap(R.id.quote_cover, cover) else views.setImageViewBitmap(R.id.quote_cover, null)
            }
            return views
        }

        private fun loadCover(path: String): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outHeight / sample > 240) sample *= 2
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            if (bitmap.height <= 120) bitmap else Bitmap.createScaledBitmap(bitmap, (bitmap.width * 120f / bitmap.height).toInt().coerceAtLeast(1), 120, true)
        }.getOrNull()
    }
}

class QuoteWidgetSmall : QuoteWidget() {
    override val small: Boolean = true
}
