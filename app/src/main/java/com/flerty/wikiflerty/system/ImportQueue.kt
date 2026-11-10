package com.flerty.wikiflerty.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ImportQueue(private val app: App) {
    data class Progress(val done: Int, val total: Int, val added: Int, val same: Int, val failed: Int)

    private val file = File(app.filesDir, "import_queue.json")
    private val pending = ArrayDeque<String>()
    private var stats = Progress(0, 0, 0, 0, 0)
    private var job: Job? = null

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    var onFinished: ((Progress) -> Unit)? = null

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        synchronized(pending) {
            val known = pending.toHashSet()
            val fresh = uris.map { it.toString() }.filter { known.add(it) }
            if (pending.isEmpty() && job?.isActive != true) stats = Progress(0, 0, 0, 0, 0)
            pending.addAll(fresh)
            stats = stats.copy(total = stats.total + fresh.size)
            save()
        }
        _progress.value = stats
        start(foreground = true)
    }

    fun resume() {
        synchronized(pending) {
            if (!file.exists()) return
            runCatching {
                val json = JSONObject(file.readText())
                val arr = json.optJSONArray("pending") ?: JSONArray()
                pending.clear()
                for (i in 0 until arr.length()) pending.addLast(arr.getString(i))
                stats = Progress(json.optInt("done"), json.optInt("total"), json.optInt("added"), json.optInt("same"), json.optInt("failed"))
            }
            if (pending.isEmpty()) {
                file.delete()
                return
            }
        }
        _progress.value = stats
        start(foreground = false)
    }

    private fun save() {
        if (pending.isEmpty()) {
            file.delete()
            return
        }
        val json = JSONObject()
            .put("pending", JSONArray(pending.toList()))
            .put("done", stats.done)
            .put("total", stats.total)
            .put("added", stats.added)
            .put("same", stats.same)
            .put("failed", stats.failed)
        runCatching {
            val tmp = File(file.parentFile, "import_queue.json.tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) file.writeText(json.toString())
        }
    }

    private fun start(foreground: Boolean) {
        if (job?.isActive == true) return
        ImportService.start(app, foreground)
        job = app.appScope.launch(Dispatchers.IO) {
            while (true) {
                val next = synchronized(pending) { pending.firstOrNull() } ?: break
                val before = app.library.books.value.size
                val result = try {
                    app.library.import(Uri.parse(next))
                } catch (t: Throwable) {
                    Result.failure(t)
                }
                val after = app.library.books.value.size
                synchronized(pending) {
                    pending.removeFirstOrNull()
                    stats = when {
                        result.isFailure -> stats.copy(done = stats.done + 1, failed = stats.failed + 1)
                        after > before -> stats.copy(done = stats.done + 1, added = stats.added + 1)
                        else -> stats.copy(done = stats.done + 1, same = stats.same + 1)
                    }
                    if (stats.done % 5 == 0 || pending.isEmpty()) save()
                }
                _progress.value = stats
                ImportService.update(app, stats)
                if (result.exceptionOrNull() is OutOfMemoryError) System.gc()
            }
            val final = stats
            synchronized(pending) { save() }
            _progress.value = null
            app.library.flush()
            ImportService.stop(app)
            onFinished?.invoke(final)
        }
    }
}

class ImportService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext as App
        val notification = build(this, app.imports.progress.value)
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(ID, notification)
            }
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    companion object {
        private const val ID = 4107
        private const val CHANNEL = "import"

        fun start(context: Context, foreground: Boolean) {
            if (!foreground) return
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, ImportService::class.java))
            }
        }

        fun update(context: Context, progress: ImportQueue.Progress) {
            runCatching {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                manager.notify(ID, build(context, progress))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ImportService::class.java)) }
            runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(ID) }
        }

        private fun build(context: Context, progress: ImportQueue.Progress?): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26 && manager?.getNotificationChannel(CHANNEL) == null) {
                manager?.createNotificationChannel(NotificationChannel(CHANNEL, "Добавление книг", NotificationManager.IMPORTANCE_LOW))
            }
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            val done = progress?.done ?: 0
            val total = progress?.total ?: 0
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify_book)
                .setContentTitle("Добавляю книги")
                .setContentText(if (total > 0) "$done из $total" else "Подготовка…")
                .setProgress(total.coerceAtLeast(1), done, total == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(open)
                .build()
        }
    }
}
