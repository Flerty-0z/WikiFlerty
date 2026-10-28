package com.flerty.wikiflerty.system

import android.app.Activity
import android.app.Application
import android.media.MediaPlayer
import android.os.Bundle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.reader.ReaderActivity
import com.flerty.wikiflerty.ui.ReadingSound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BackgroundAudio(private val app: App) {
    private var player: MediaPlayer? = null
    private var prepared = false
    private var volume = 0f
    private var started = 0
    private var readers = 0
    private var fade: Job? = null

    fun install() {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
                if (activity is ReaderActivity) readers++
                update()
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (activity is ReaderActivity) readers = (readers - 1).coerceAtLeast(0)
                update()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        app.appScope.launch(Dispatchers.Main) {
            combine(app.style.style, app.readAloud.session) { style, session ->
                Triple(style.videoVolume, style.readingSound, session != null)
            }.collect { update() }
        }
    }

    fun attach(mediaPlayer: MediaPlayer) {
        fade?.cancel()
        player = mediaPlayer
        prepared = false
        volume = 0f
    }

    fun onPrepared(mediaPlayer: MediaPlayer) {
        if (mediaPlayer !== player) return
        prepared = true
        volume = 0f
        runCatching { mediaPlayer.setVolume(0f, 0f) }
        update()
    }

    fun onReleased(mediaPlayer: MediaPlayer) {
        if (mediaPlayer !== player) return
        fade?.cancel()
        prepared = false
    }

    fun detach(mediaPlayer: MediaPlayer) {
        if (mediaPlayer !== player) return
        fade?.cancel()
        player = null
        prepared = false
    }

    private fun target(): Pair<Boolean, Float> {
        if (started <= 0) return false to 0f
        val style = app.style.style.value
        val base = if (app.readAloud.session.value != null) 0f else style.videoVolume
        if (readers > 0) {
            val level = base * ReadingSound.of(style.readingSound).factor
            return (level > 0.001f) to level
        }
        return true to base
    }

    private fun update() {
        val mediaPlayer = player ?: return
        if (!prepared) return
        val (play, level) = target()
        fade?.cancel()
        fade = app.appScope.launch(Dispatchers.Main) {
            if (play && !runCatching { mediaPlayer.isPlaying }.getOrDefault(true)) {
                runCatching {
                    mediaPlayer.setVolume(volume, volume)
                    mediaPlayer.start()
                }
            }
            val from = volume
            val steps = 20
            if (from != level) {
                for (i in 1..steps) {
                    volume = from + (level - from) * i / steps
                    runCatching { mediaPlayer.setVolume(volume, volume) }
                    delay(FADE_MS / steps)
                }
            }
            volume = level
            runCatching { mediaPlayer.setVolume(level, level) }
            if (!play) runCatching { if (mediaPlayer.isPlaying) mediaPlayer.pause() }
        }
    }

    private companion object {
        const val FADE_MS = 900L
    }
}
