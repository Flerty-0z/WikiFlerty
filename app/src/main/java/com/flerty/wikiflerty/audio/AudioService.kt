package com.flerty.wikiflerty.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.ui.MainActivity

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class AudioService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val player = (application as App).audio.session
        if (player == null) {
            stopSelf()
            return
        }
        val open = PendingIntent.getActivity(
            this,
            7,
            Intent(this, MainActivity::class.java).setAction(ACTION_OPEN).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setId("audiobook")
            .setSessionActivity(open)
            .build()
            .also { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val audio = (application as App).audio
        if (!audio.state.value.playing) {
            audio.close()
        }
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
        const val ACTION_OPEN = "com.flerty.wikiflerty.OPEN_AUDIOBOOK"
        private var instance: AudioService? = null

        fun release() {
            instance?.let {
                it.releaseSession()
                it.stopSelf()
            }
        }
    }
}
