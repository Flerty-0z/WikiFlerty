package com.flerty.wikiflerty.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

class QuoteWidget : AppWidgetProvider() {

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

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
            val ids = manager.getAppWidgetIds(ComponentName(context, QuoteWidget::class.java))
            if (ids.isEmpty()) return
            val skin = WidgetSkin.current(context)
            val art = WidgetArt(context, skin)
            ids.forEach { id ->
                val (w, h) = WidgetSkin.size(manager, id, 280, 280)
                manager.updateAppWidget(id, build(context, art, skin, w, h))
            }
        }

        private fun candidates(context: Context): List<Note> {
            val app = context.applicationContext as App
            val all = app.notes.notes.value.filter { it.text.isNotBlank() }.sortedBy { it.createdAt }
            return all.filter { n -> n.text.trim().split(Regex("\\s+")).size >= 4 && n.text.trim().length >= 24 }
        }

        private fun build(context: Context, art: WidgetArt, skin: WidgetSkin, wDp: Int, hDp: Int): RemoteViews {
            val app = context.applicationContext as App
            val views = RemoteViews(context.packageName, R.layout.widget_quote)
            val c = art.base(wDp, hDp)
            val d = skin.density
            val pad = 18 * d
            art.label(c, "Цитата дня", pad, pad + 4 * d)
            val top = pad + art.labelHeight() + 14 * d
            val footH = 34 * d
            val textW = c.w - pad * 2
            val textH = c.h - top - footH - pad
            val list = candidates(context)
            val offset = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_OFFSET, 0)
            val start = if (list.isEmpty()) 0 else ((LocalDate.now().toEpochDay() + offset) % list.size).toInt().let { if (it < 0) it + list.size else it }
            fun clean(n: Note) = n.text.trim().replace(Regex("\\s+"), " ").trim('«', '»', '"', '“', '”')
            val note = list.indices.asSequence().map { list[(start + it) % list.size] }
                .firstOrNull { art.fits("«${clean(it)}»", art.serifFace, skin.text, textW, textH, 13.5f) }
                ?: list.minByOrNull { it.text.length }

            val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (note != null) open.setAction(ACTION_OPEN_NOTE).putExtra(EXTRA_NOTE_ID, note.id)
            val openPending = PendingIntent.getActivity(context, 40, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.quote_root, openPending)
            val next = Intent(context, QuoteWidget::class.java).setAction(ACTION_NEXT)
            views.setOnClickPendingIntent(R.id.quote_next, PendingIntent.getBroadcast(context, 42, next, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            skin.icon(views, R.id.quote_next, skin.muted)

            if (note == null) {
                val t = art.fit("Выдели в книге фразу, которая зацепила, — и она будет появляться здесь", art.serifFace, skin.text, textW, textH + footH, 17f, 13f)
                art.draw(c, t, pad, top)
                views.setViewVisibility(R.id.quote_next, View.GONE)
                views.setImageViewBitmap(R.id.quote_art, c.bitmap)
                return views
            }
            views.setViewVisibility(R.id.quote_next, if (list.size > 1) View.VISIBLE else View.GONE)
            val quote = art.fit("«${clean(note)}»", art.serifFace, skin.text, textW, textH, if (hDp >= 220) 23f else 19f, 13.5f, spacing = 1.2f)
            art.draw(c, quote, pad, top + ((textH - quote.height) / 2f).coerceAtLeast(0f))
            val book = app.library.books.value.firstOrNull { it.id == note.bookId }
            val title = book?.title ?: app.syncedTitle(note.bookId) ?: "Удалённая книга"
            val place = if (book != null && book.positions > 0) {
                "стр. ${(note.progression * book.positions).roundToInt().coerceIn(1, book.positions)}"
            } else {
                "${(note.progression * 100).roundToInt()}%"
            }
            val footY = c.h - pad - footH
            val line = android.graphics.Paint().apply { color = WidgetSkin.withAlpha(skin.text, 0.1f); strokeWidth = 1 * d }
            c.canvas.drawLine(pad, footY, c.w - pad, footY, line)
            val foot = art.layout("$title · $place", art.paint(art.monoFace, 11f, skin.faint), textW, maxLines = 1)
            art.draw(c, foot, pad, footY + (footH - foot.height) / 2f + 2 * d)
            views.setImageViewBitmap(R.id.quote_art, c.bitmap)
            return views
        }
    }
}

