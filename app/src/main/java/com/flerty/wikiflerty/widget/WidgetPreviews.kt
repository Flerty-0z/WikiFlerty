package com.flerty.wikiflerty.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews

object WidgetPreviews {
    private const val PREFS = "widget_previews"
    private const val KEY = "key"

    fun publish(context: Context) {
        if (Build.VERSION.SDK_INT < 35) return
        val base = WidgetSkin.current(context)
        val key = "${base.kit.name}:${base.light}:${base.accent}"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY, null) == key) return
        val skin = WidgetSkin(base.kit, base.light, base.text, base.muted, base.faint, base.accent, base.density.coerceAtMost(1.75f), base.bg)
        val art = WidgetArt(context, skin)
        val manager = AppWidgetManager.getInstance(context)
        val all = listOf<Pair<Class<*>, () -> RemoteViews>>(
            ReadingWidget::class.java to { ReadingWidget.preview(context, art, skin) },
            QuoteWidget::class.java to { QuoteWidget.preview(context, art, skin) },
            WordWidget::class.java to { WordWidget.preview(context, art, skin) },
        )
        var ok = true
        all.forEach { (cls, make) ->
            val done = runCatching {
                manager.setWidgetPreview(ComponentName(context, cls), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, make())
            }.getOrDefault(false)
            ok = ok && done
        }
        if (ok) prefs.edit().putString(KEY, key).apply()
    }
}
