package com.flerty.wikiflerty.studio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File

class StudioRecorder : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var fd: ParcelFileDescriptor? = null
    private var target: Uri? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        foreground()
        if (data == null || !begin(code, data)) {
            StudioDriver.toast(this, "Не удалось начать запись экрана")
            finish()
            return START_NOT_STICKY
        }
        recording = true
        return START_NOT_STICKY
    }

    private fun foreground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Съёмка сценария", NotificationManager.IMPORTANCE_LOW))
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("WikiFlerty Studio")
            .setContentText("Идёт запись сценария")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(7, n)
    }

    private fun size(): Triple<Int, Int, Int> {
        val dm = resources.displayMetrics
        val p = android.graphics.Point()
        val d = (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        d.getRealSize(p)
        val w = p.x
        val h = p.y
        val scale = minOf(1f, 1080f / w)
        val ow = ((w * scale).toInt() / 2) * 2
        val oh = ((h * scale).toInt() / 2) * 2
        return Triple(ow, oh, dm.densityDpi)
    }

    private fun output(): ParcelFileDescriptor? {
        val name = "wikiflerty-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) + ".mp4"
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/WikiFlerty Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            target = uri
            contentResolver.openFileDescriptor(uri, "w")
        } else {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "").apply { mkdirs() }
            val file = File(dir, name)
            target = Uri.fromFile(file)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE)
        }
    }

    private fun begin(code: Int, data: Intent): Boolean = runCatching {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = mpm.getMediaProjection(code, data) ?: return false
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Handler(Looper.getMainLooper()).post { finish() }
            }
        }, Handler(Looper.getMainLooper()))
        val (w, h, dpi) = size()
        val file = output() ?: return false
        fd = file
        var started = false
        for (fps in intArrayOf(60, 30)) {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            try {
                r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                r.setVideoSize(w, h)
                r.setVideoFrameRate(fps)
                r.setVideoEncodingBitRate(if (fps == 60) 24_000_000 else 16_000_000)
                r.setOutputFile(file.fileDescriptor)
                r.prepare()
                recorder = r
                started = true
                break
            } catch (e: Exception) {
                runCatching { r.reset() }
                runCatching { r.release() }
            }
        }
        if (!started) return false
        display = p.createVirtualDisplay("studio", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder!!.surface, null, null)
        recorder!!.start()
        true
    }.getOrElse { false }

    private fun finish() {
        if (!recording && recorder == null && projection == null) {
            stopSelf()
            return
        }
        recording = false
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { display?.release() }
        display = null
        runCatching { projection?.stop() }
        projection = null
        runCatching { fd?.close() }
        fd = null
        target?.let { uri ->
            if (Build.VERSION.SDK_INT >= 29 && uri.scheme == "content") {
                runCatching { contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) }
            }
            StudioDriver.toast(this, "Ролик сохранён в «Фильмы / WikiFlerty Studio»")
        }
        target = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    companion object {
        private const val CHANNEL = "studio"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val ACTION_STOP = "stop"

        @Volatile
        var recording = false
            private set

        fun start(context: Context, code: Int, data: Intent) {
            val i = Intent(context, StudioRecorder::class.java).putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            if (!recording) return
            context.startService(Intent(context, StudioRecorder::class.java).setAction(ACTION_STOP))
        }
    }
}
