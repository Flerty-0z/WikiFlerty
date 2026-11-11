package com.flerty.wikiflerty.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.Layout
import android.view.View
import android.widget.RemoteViews
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.R
import com.flerty.wikiflerty.data.DictionaryEntry
import com.flerty.wikiflerty.data.Grade
import com.flerty.wikiflerty.data.Srs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WordWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        later(context) { refreshAll(context) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when (intent.action) {
            ACTION_FLIP -> {
                prefs.edit().putBoolean(KEY_SHOWN, !prefs.getBoolean(KEY_SHOWN, false)).apply()
                later(context) { refreshAll(context) }
                return
            }
            ACTION_YES, ACTION_NO -> {
                val id = intent.getStringExtra(EXTRA_ENTRY).orEmpty()
                val remembered = intent.action == ACTION_YES
                later(context) {
                    val app = context.applicationContext as App
                    val entry = app.dictionary.entries.value.firstOrNull { it.id == id }
                    if (entry != null) {
                        val before = entry.srs
                        app.dictionary.saveReview(entry.id, Srs.next(before, if (remembered) Grade.Good else Grade.Again))
                        runCatching { app.flashcards.recordAnswer(remembered, wasNew = before.isNew) }
                    }
                    prefs.edit().putBoolean(KEY_SHOWN, false).putString(KEY_LAST, id).apply()
                    refreshAll(context)
                }
                return
            }
        }
        super.onReceive(context, intent)
    }

    private fun later(context: Context, block: suspend () -> Unit) {
        val pending = goAsync()
        val app = context.applicationContext as App
        app.appScope.launch(Dispatchers.IO) {
            try {
                runCatching { block() }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FLIP = "com.flerty.wikiflerty.WORD_FLIP"
        const val ACTION_YES = "com.flerty.wikiflerty.WORD_YES"
        const val ACTION_NO = "com.flerty.wikiflerty.WORD_NO"
        private const val EXTRA_ENTRY = "entry"
        private const val PREFS = "word_widget"
        private const val KEY_SHOWN = "shown"
        private const val KEY_LAST = "last"

        private val stopWords = setOf(
            "это", "и", "в", "на", "не", "что", "как", "он", "она", "они", "мы", "вы", "я", "ты", "но", "да", "же", "бы",
            "ли", "то", "так", "уже", "ещё", "еще", "вот", "там", "тут", "был", "была", "было", "были", "есть", "его", "её",
            "ее", "их", "мне", "меня", "тебя", "себя", "для", "при", "про", "под", "над", "из", "за", "от", "до", "по", "со",
            "the", "a", "an", "and", "or", "of", "to", "in", "is", "it",
        )

        fun refreshSoon(app: App) {
            app.appScope.launch(Dispatchers.IO) { runCatching { refreshAll(app) } }
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WordWidget::class.java))
            if (ids.isEmpty()) return
            val entry = runCatching { pick(context) }.getOrNull()
            val skin = WidgetSkin.current(context)
            val art = WidgetArt(context, skin)
            val shown = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOWN, false)
            ids.forEach { id ->
                val (w, h) = WidgetSkin.size(manager, id, 280, 280)
                val views = runCatching { build(context, art, skin, entry, shown, w, h) }
                    .recoverCatching { build(context, art, skin, entry, false, w, h) }
                    .getOrNull()
                if (views != null) runCatching { manager.updateAppWidget(id, views) }
            }
        }

        fun preview(context: Context, art: WidgetArt, skin: WidgetSkin): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_word)
            val c = art.base(280, 280)
            paint(art, skin, c, 280, "Безмятежность", "Состояние полного спокойствия, когда ничто не тревожит", "Евгений Онегин", "", true)
            views.setImageViewBitmap(R.id.word_art, c.bitmap)
            views.setViewVisibility(R.id.word_buttons, View.GONE)
            return views
        }

        private fun usable(e: DictionaryEntry): Boolean {
            val w = e.word.trim().lowercase()
            return w.length >= 3 && w !in stopWords && e.definition.isNotBlank()
        }

        private fun pick(context: Context): DictionaryEntry? {
            val app = context.applicationContext as App
            val all = app.dictionary.entries.value.filter(::usable)
            if (all.isEmpty()) return null
            val now = System.currentTimeMillis()
            val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)
            val due = all.filter { it.srs.due <= now }.sortedWith(compareBy<DictionaryEntry> { it.srs.isNew }.thenBy { it.srs.due })
            return due.firstOrNull { it.id != last } ?: due.firstOrNull() ?: all.minByOrNull { it.srs.due }
        }

        fun meaning(definition: String): String {
            val plain = definition
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("\\*(.+?)\\*"), "$1")
                .replace(Regex("(?m)^\\s*#+\\s*"), "")
                .lines().map { it.trim() }.filter { it.isNotBlank() }
            val first = plain.firstOrNull { line -> line.length > 8 } ?: plain.firstOrNull().orEmpty()
            var text = first.replace(Regex("^(\\d+[.)]|[-•—])\\s*"), "").trim()
            if (text.length > 20) {
                val stop = Regex("[.;](\\s|$)").find(text, 20)
                if (stop != null) text = text.substring(0, stop.range.first)
            }
            text = text.trim().trimEnd(',', ':')
            if (text.isEmpty()) text = definition.trim().take(160)
            return text.replaceFirstChar { it.uppercase() }
        }

        fun sentence(context: String, word: String): String {
            val text = context.replace(Regex("\\s+"), " ").trim()
            val key = word.trim().lowercase()
            if (text.isEmpty() || key.isEmpty()) return ""
            val at = text.lowercase().indexOf(key)
            if (at < 0) return ""
            val stops = ".!?…"
            var s = at
            while (s > 0 && text[s - 1] !in stops) s--
            var e = at + key.length
            while (e < text.length && text[e] !in stops) e++
            while (e < text.length && (text[e] in stops || text[e] in "»”\")")) e++
            return text.substring(s, e).trim().trimStart('»', '”', ')', '—', '-', ' ').trim()
        }

        private fun highlight(text: String, word: String, color: Int): CharSequence {
            val out = SpannableString(text)
            val low = text.lowercase()
            val key = word.trim().lowercase()
            if (key.isEmpty()) return out
            var from = 0
            while (true) {
                val at = low.indexOf(key, from)
                if (at < 0) break
                out.setSpan(ForegroundColorSpan(color), at, at + key.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(StyleSpan(Typeface.BOLD), at, at + key.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                from = at + key.length
            }
            return out
        }

        private fun paint(art: WidgetArt, skin: WidgetSkin, c: WidgetArt.Canvased, hDp: Int, wordText: String, meaningText: String, book: String?, context: String, shown: Boolean) {
            val d = skin.density
            val pad = 18 * d
            art.label(c, "Повтори слово", pad, pad + 4 * d)
            val top = pad + art.labelHeight() + 14 * d
            val textW = c.w - pad * 2
            val buttonsH = 40 * d
            val bottom = c.h - pad - buttonsH - 12 * d
            val tall = hDp >= 220
            val word = art.fit(wordText.trim().replaceFirstChar { it.uppercase() }, art.serifFace, skin.text, textW, ((bottom - top) * 0.4f).coerceAtLeast(30 * d), if (tall) (if (shown) 36f else 42f) else 30f, 18f, weight = 600, spacing = 1.0f)
            art.draw(c, word, pad, top)
            var y = top + word.height + 6 * d
            if (shown) {
                y += 4 * d
                val mean = art.fit(meaningText, art.serifFace, skin.text, textW, (bottom - y).coerceAtLeast(18 * d), if (tall) 19f else 15.5f, 12f, spacing = 1.2f)
                art.draw(c, mean, pad, y)
                y += mean.height + 14 * d
                val ctx = sentence(context, wordText)
                if (ctx.isNotEmpty() && bottom - y > 40 * d) {
                    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WidgetSkin.withAlpha(skin.text, 0.12f); strokeWidth = 1 * d }
                    c.canvas.drawLine(pad, y - 7 * d, pad + 36 * d, y - 7 * d, line)
                    val l = art.fit(ctx, art.serifFace, skin.muted, textW, bottom - y, 13.5f, 11.5f, spacing = 1.2f)
                    art.draw(c, art.layout(highlight(l.text.toString(), wordText, skin.accent), l.paint, textW, 1.2f, l.lineCount), pad, y)
                }
                val gap = 8 * d
                val bw = (textW - gap) / 2f
                val by = c.h - pad - buttonsH
                art.pill(c, RectF(pad, by, pad + bw, by + buttonsH), "↻ Не помню", filled = false)
                art.pill(c, RectF(pad + bw + gap, by, pad + bw * 2 + gap, by + buttonsH), "✓ Помню", filled = true)
            } else {
                book?.takeIf { it.isNotBlank() }?.let { title ->
                    val src = art.layout("из «$title»", art.paint(art.monoFace, 11.5f, skin.faint), textW, maxLines = 1, align = Layout.Alignment.ALIGN_NORMAL)
                    art.draw(c, src, pad, y)
                    y += src.height + 16 * d
                }
                val ctx = sentence(context, wordText)
                if (ctx.isNotEmpty() && bottom - y > 30 * d) {
                    val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WidgetSkin.withAlpha(skin.accent, 0.55f) }
                    val l = art.fit("$ctx", art.serifFace, skin.muted, textW - 12 * d, bottom - y, if (tall) 16f else 14f, 12f, spacing = 1.22f)
                    c.canvas.drawRoundRect(RectF(pad, y + 2 * d, pad + 2.5f * d, y + l.height - 2 * d), 2 * d, 2 * d, bar)
                    art.draw(c, art.layout(highlight(l.text.toString(), wordText, skin.accent), l.paint, textW - 12 * d, 1.22f, l.lineCount), pad + 12 * d, y)
                }
                val by = c.h - pad - buttonsH
                art.pill(c, RectF(pad, by, c.w - pad, by + buttonsH), "Показать значение", filled = false)
            }
        }

        private fun build(context: Context, art: WidgetArt, skin: WidgetSkin, entry: DictionaryEntry?, shown: Boolean, wDp: Int, hDp: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_word)
            val c = art.base(wDp, hDp)
            val d = skin.density
            val pad = 18 * d
            val flip = PendingIntent.getBroadcast(context, 500, Intent(context, WordWidget::class.java).setAction(ACTION_FLIP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.word_root, flip)

            if (entry == null) {
                art.label(c, "Повтори слово", pad, pad + 4 * d)
                val top = pad + art.labelHeight() + 14 * d
                val t = art.fit("Выдели незнакомое слово в книге и нажми «Словарь» — оно будет появляться здесь для повторения", art.serifFace, skin.text, c.w - pad * 2, c.h - top - pad, 17f, 12.5f)
                art.draw(c, t, pad, top)
                views.setViewVisibility(R.id.word_buttons, View.GONE)
                views.setImageViewBitmap(R.id.word_art, c.bitmap)
                return views
            }

            paint(art, skin, c, hDp, entry.word, if (shown) meaning(entry.definition) else "", entry.bookTitle, entry.context, shown)
            if (shown) {
                views.setViewVisibility(R.id.word_buttons, View.VISIBLE)
                val no = Intent(context, WordWidget::class.java).setAction(ACTION_NO).putExtra(EXTRA_ENTRY, entry.id)
                val yes = Intent(context, WordWidget::class.java).setAction(ACTION_YES).putExtra(EXTRA_ENTRY, entry.id)
                views.setOnClickPendingIntent(R.id.word_no, PendingIntent.getBroadcast(context, 501, no, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                views.setOnClickPendingIntent(R.id.word_yes, PendingIntent.getBroadcast(context, 502, yes, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            } else {
                views.setViewVisibility(R.id.word_buttons, View.GONE)
            }
            views.setImageViewBitmap(R.id.word_art, c.bitmap)
            return views
        }
    }
}
