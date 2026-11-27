package com.flerty.wikiflerty.system

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.R
import com.flerty.wikiflerty.ui.MainActivity
import java.time.LocalTime

object CommunityNotify {
    const val ACTION_CHECK = "com.flerty.wikiflerty.COMMUNITY_CHECK"
    const val ACTION_OPEN = "com.flerty.wikiflerty.COMMUNITY_OPEN"
    const val EXTRA_ROOM = "room"
    private const val CHANNEL = "community"
    private const val REQUEST = 4201

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, CommunityReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context) {
        val app = context.applicationContext as? App ?: return
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pending(context)
        alarms.cancel(intent)
        if (!app.account.available || app.account.state.value.login == null || !app.community.notifyOn) return
        alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_FIFTEEN_MINUTES, intent)
    }

    fun check(context: Context) {
        val app = context.applicationContext as? App ?: return
        val community = app.community
        if (!community.notifyOn || app.account.state.value.login == null) return
        val now = LocalTime.now()
        if (community.quietNow(now.hour * 60 + now.minute)) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val fresh = runCatching { community.pendingMentions() }.getOrNull().orEmpty()
        if (fresh.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Ответы в сообществе", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val top = fresh.maxBy { it.id }
        val open = Intent(context, MainActivity::class.java).setAction(ACTION_OPEN).putExtra(EXTRA_ROOM, top.room)
        if (top.room.startsWith("b:") && top.title.isNotBlank()) open.putExtra("title", top.title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(context, REQUEST + 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (fresh.size == 1) "${top.from} ответил(а) тебе" else "Ответов тебе: ${fresh.size}"
        val where = top.title.ifBlank { "" }
        val text = (if (where.isNotBlank()) "«$where» · " else "") + top.text
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify_book)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { manager.notify(REQUEST + 2, notification) }
    }
}

class CommunityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CommunityNotify.ACTION_CHECK) {
            runCatching { CommunityNotify.schedule(context) }
            return
        }
        val done = goAsync()
        Thread {
            runCatching { CommunityNotify.check(context) }
            done.finish()
        }.start()
    }
}
