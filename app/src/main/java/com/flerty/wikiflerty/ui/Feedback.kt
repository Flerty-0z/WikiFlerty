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

enum class FeedbackSound { Tap, Toggle, Open, Close }

/**
 * Soft, quiet button sounds and a light vibration tick.
 * Both can be turned off in Опции → Звук.
 */
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
    )

    fun play(sound: FeedbackSound, view: View? = null) {
        val s = settings.value
        if (s.sounds) {
            val volume = s.soundVolume.toFloat().coerceIn(0f, 1f)
            ids[sound]?.let { pool.play(it, volume, volume, 1, 0, 1f) }
        }
        if (s.haptics && view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

/** Gives every screen access to the button sounds. */
val LocalFeedback = staticCompositionLocalOf<Feedback?> { null }
