package com.flerty.wikiflerty.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.R
import com.flerty.wikiflerty.data.DictionaryEntry
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WordWidget : AppWidgetProvider() {
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

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FLIP -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val key = KEY_FLIP + id
                    val entryId = pick(context)?.id.orEmpty()
                    if (prefs.getString(key, null) == entryId) prefs.edit().remove(key).apply() else prefs.edit().putString(key, entryId).apply()
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
                return
            }
            ACTION_NEXT -> {
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
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_FLIP = "com.flerty.wikiflerty.WORD_FLIP"
        const val ACTION_NEXT = "com.flerty.wikiflerty.WORD_NEXT"
        private const val PREFS = "word_widget"
        private const val KEY_OFFSET = "offset"
        private const val KEY_FLIP = "flip_"

        fun refreshSoon(app: App) {
            app.appScope.launch(Dispatchers.IO) { runCatching { refreshAll(app) } }
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WordWidget::class.java))
            if (ids.isEmpty()) return
            val entry = pick(context)
            val skin = WidgetSkin.current(context)
            ids.forEach { id ->
                val (w, h) = WidgetSkin.size(manager, id, 180, 120)
                manager.updateAppWidget(id, build(context, id, entry, skin, w, h))
            }
        }

        private fun pick(context: Context): DictionaryEntry? {
            val app = context.applicationContext as App
            val entries = app.dictionary.entries.value.filter { it.word.isNotBlank() && it.definition.isNotBlank() }.sortedBy { it.createdAt }
            if (entries.isEmpty()) return null
            val offset = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_OFFSET, 0)
            val index = ((LocalDate.now().toEpochDay() * 7 + offset) % entries.size).toInt().let { if (it < 0) it + entries.size else it }
            return entries[index]
        }

        private fun clean(markdown: String): String =
            markdown.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1").replace(Regex("\\*(.+?)\\*"), "$1").replace(Regex("\\n{2,}"), "\\n").trim()

        private fun build(context: Context, id: Int, entry: DictionaryEntry?, skin: WidgetSkin, wDp: Int, hDp: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_word)
            skin.panel(context, wDp, hDp)?.let {
                views.setImageViewBitmap(R.id.word_bg, it)
                views.setInt(R.id.word_root, "setBackgroundColor", 0)
            }
            skin.tint(views, R.id.word_label, skin.accent)
            skin.tint(views, R.id.word_text, skin.text)
            skin.tint(views, R.id.word_hint, skin.faint)
            skin.tint(views, R.id.word_back_title, skin.accent)
            skin.tint(views, R.id.word_definition, skin.text)
            skin.tint(views, R.id.word_source, skin.faint)
            skin.icon(views, R.id.word_next, skin.muted)
            val flipped = entry != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FLIP + id, null) == entry.id
            views.setDisplayedChild(R.id.word_flipper, if (flipped) 1 else 0)

            val flip = Intent(context, WordWidget::class.java).setAction(ACTION_FLIP).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            val flipPending = PendingIntent.getBroadcast(context, 500 + id, flip, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.word_flipper, flipPending)
            views.setOnClickPendingIntent(R.id.word_root, flipPending)
            val next = Intent(context, WordWidget::class.java).setAction(ACTION_NEXT)
            views.setOnClickPendingIntent(R.id.word_next, PendingIntent.getBroadcast(context, 499, next, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            if (entry == null) {
                views.setTextViewText(R.id.word_text, "Словарь пуст")
                views.setTextViewText(R.id.word_hint, "Выдели слово в книге → «Словарь»")
                views.setTextViewText(R.id.word_back_title, "")
                views.setTextViewText(R.id.word_definition, "Слова, которые ты добавишь, будут появляться здесь каждый день.")
                views.setTextViewText(R.id.word_source, "")
                return views
            }
            views.setTextViewText(R.id.word_text, entry.word)
            val room = (wDp - 36).coerceAtLeast(60)
            val fit = room / (entry.word.length.coerceAtLeast(3) * 0.62f)
            val maxSize = if (hDp >= 150) 34f else 28f
            views.setTextViewTextSize(R.id.word_text, android.util.TypedValue.COMPLEX_UNIT_SP, fit.coerceIn(13f, maxSize))
            val definition = clean(entry.definition)
            val preview = definition.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            val roomy = hDp >= 130 || wDp >= 250
            views.setViewVisibility(R.id.word_preview, if (roomy && preview.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewText(R.id.word_preview, preview)
            skin.tint(views, R.id.word_preview, skin.muted)
            views.setTextViewText(R.id.word_hint, context.getString(R.string.widget_word_hint))
            views.setTextViewText(R.id.word_back_title, entry.word)
            views.setTextViewText(R.id.word_definition, definition)
            views.setTextViewText(R.id.word_source, entry.bookTitle.orEmpty())
            return views
        }
    }
}
