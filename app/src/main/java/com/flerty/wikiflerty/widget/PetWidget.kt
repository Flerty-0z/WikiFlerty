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
import com.flerty.wikiflerty.pet.PetArt
import com.flerty.wikiflerty.pet.PetLevels
import com.flerty.wikiflerty.pet.PetLook
import com.flerty.wikiflerty.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PetWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext as App
        app.appScope.launch(Dispatchers.IO) {
            try {
                app.pet.settle()
                refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_OPEN_PET = "com.flerty.wikiflerty.OPEN_PET"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PetWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context): RemoteViews {
            val app = context.applicationContext as App
            val views = RemoteViews(context.packageName, R.layout.widget_pet)
            val open = Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_PET)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(
                R.id.pet_root,
                PendingIntent.getActivity(context, 50, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            val pet = app.pet.pet.value
            if (pet == null) {
                views.setImageViewBitmap(R.id.pet_image, PetArt.bitmap("cat", PetLook.Happy, 240))
                views.setTextViewText(R.id.pet_name, "Питомец")
                views.setTextViewText(R.id.pet_status, "Заведи питомца в «Итогах»")
                views.setProgressBar(R.id.pet_food, 100, 0, false)
                return views
            }
            val mood = app.pet.mood(pet)
            views.setImageViewBitmap(R.id.pet_image, PetArt.bitmap(pet.species.id, app.pet.look(mood), 240))
            val level = pet.level
            views.setTextViewText(R.id.pet_name, "${pet.name} · ур. $level")
            views.setTextViewText(R.id.pet_status, mood.text)
            val from = PetLevels.total(level)
            val to = PetLevels.total(level + 1)
            views.setProgressBar(R.id.pet_food, (to - from).toInt(), (pet.xp - from).toInt().coerceAtLeast(0), false)
            return views
        }
    }
}
