package com.flerty.wikiflerty.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf
import com.flerty.wikiflerty.R
import com.flerty.wikiflerty.reader.ReaderSettings
import kotlinx.coroutines.flow.StateFlow

enum class FeedbackSound { Tap, Toggle, Open, Close, Star }

class Feedback(context: Context, private val settings: StateFlow<ReaderSettings>) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = mapOf(
        FeedbackSound.Tap to pool.load(context, R.raw.ui_tap, 1),
        FeedbackSound.Toggle to pool.load(context, R.raw.ui_toggle, 1),
        FeedbackSound.Open to pool.load(context, R.raw.ui_open, 1),
        FeedbackSound.Close to pool.load(context, R.raw.ui_close, 1),
        FeedbackSound.Star to pool.load(context, R.raw.ui_star, 1),
    )

    private var lastSound: FeedbackSound? = null
    private var lastTime = 0L

    fun play(sound: FeedbackSound, view: View? = null, rate: Float = 1f) {
        val now = android.os.SystemClock.uptimeMillis()
        if (sound == lastSound && now - lastTime < (if (sound == FeedbackSound.Star) 40 else 80)) return
        lastSound = sound
        lastTime = now
        val s = settings.value
        if (s.sounds) {
            val volume = s.soundVolume.toFloat().coerceIn(0f, 1f)
            val v = if (sound == FeedbackSound.Star) volume * 0.7f else volume
            ids[sound]?.let { pool.play(it, v, v, 1, 0, rate.coerceIn(0.5f, 2f)) }
        }
        if (s.haptics && view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

val LocalFeedback = staticCompositionLocalOf<Feedback?> { null }
