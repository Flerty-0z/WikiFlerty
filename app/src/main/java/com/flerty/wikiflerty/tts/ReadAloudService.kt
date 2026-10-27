package com.flerty.wikiflerty.tts

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.reader.ReaderActivity

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class ReadAloudService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val app = application as App
        val navigator = app.readAloud.session.value?.navigator
        if (navigator == null) {
            stopSelf()
            return
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ReaderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, navigator.asMedia3Player())
            .setSessionActivity(open)
            .build()
            .also { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        (application as App).readAloud.stop()
        super.onTaskRemoved(rootIntent)
    }

    private fun releaseSession() {
        mediaSession?.let {
            removeSession(it)
            it.release()
        }
        mediaSession = null
    }

    override fun onDestroy() {
        releaseSession()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private var instance: ReadAloudService? = null

        fun release() {
            instance?.let {
                it.releaseSession()
                it.stopSelf()
            }
        }
    }
}
