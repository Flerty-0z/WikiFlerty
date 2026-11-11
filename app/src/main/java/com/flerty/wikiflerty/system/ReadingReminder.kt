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
import androidx.core.app.NotificationCompat
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.R
import com.flerty.wikiflerty.data.ReadStatus
import com.flerty.wikiflerty.data.StreakFreezeStore
import com.flerty.wikiflerty.ui.MainActivity
import com.flerty.wikiflerty.ui.StatsCalc
import com.flerty.wikiflerty.widget.ReadingWidget
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ReadingReminder {
    private const val CHANNEL = "reading_reminder"
    const val ACTION = "com.flerty.wikiflerty.READING_REMINDER"
    private const val REQUEST = 4101
    private const val NOTIFICATION = 4102

    private val _minutes = MutableStateFlow(-2)
    val minutes: StateFlow<Int> = _minutes.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences("reminder", Context.MODE_PRIVATE)

    fun load(context: Context): Int {
        val m = prefs(context).getInt("minutes", -1)
        _minutes.value = m
        return m
    }

    fun set(context: Context, minutes: Int) {
        prefs(context).edit().putInt("minutes", minutes).apply()
        _minutes.value = minutes
        schedule(context)
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(pending(context))
    }

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pending(context)
        alarms.cancel(intent)
        val m = load(context)
        if (m < 0) return
        val now = ZonedDateTime.now()
        var at = now.toLocalDate().atTime(m / 60, m % 60).atZone(now.zone)
        if (!at.isAfter(now.plusSeconds(30))) at = at.plusDays(1)
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), 10 * 60_000L, intent)
    }

    fun notifyIfNeeded(context: Context) {
        val app = context.applicationContext as? App ?: return
        val today = LocalDate.now()
        if (today in StreakFreezeStore.frozen) return
        val days = app.stats.days.value
        val todayMs = days[today.toString()]?.values?.sumOf { it.ms } ?: 0L
        if (todayMs >= 60_000L) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Напоминание почитать", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val streak = StatsCalc.streaks(StatsCalc.perDay(days), today.minusDays(1), frozen = StreakFreezeStore.frozen).first
        val book = app.library.books.value
            .filter { it.status == ReadStatus.Reading && !it.paper && it.lastReadAt != null }
            .maxByOrNull { it.lastReadAt ?: 0L }
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (book != null) open.setAction(ReadingWidget.ACTION_OPEN_BOOK).putExtra(ReadingWidget.EXTRA_BOOK_ID, book.id)
        val tap = PendingIntent.getActivity(context, NOTIFICATION, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (streak > 0) "Серия ${StatsCalc.days(streak)} — не дай ей прерваться" else "Пора почитать"
        val text = book?.let { "Продолжить «${it.title}»" } ?: "Пара страниц — и день засчитан"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify_book)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { manager.notify(NOTIFICATION, notification) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { ReadingReminder.cancel(context) }
    }
}
