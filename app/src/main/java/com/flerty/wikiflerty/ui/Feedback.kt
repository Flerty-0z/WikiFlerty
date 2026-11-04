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

enum class FeedbackSound { Tap, Toggle, Open, Close, Star, Success, Freeze, Thaw }

class Feedback(context: Context, private val settings: StateFlow<ReaderSettings>) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
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
        FeedbackSound.Success to pool.load(context, R.raw.ui_success, 1),
        FeedbackSound.Freeze to pool.load(context, R.raw.ui_freeze, 1),
        FeedbackSound.Thaw to pool.load(context, R.raw.ui_thaw, 1),
    )

    private val turns = mapOf(
        com.flerty.wikiflerty.reader.PageAnimation.Curl to pool.load(context, R.raw.turn_curl, 1),
        com.flerty.wikiflerty.reader.PageAnimation.Flip to pool.load(context, R.raw.turn_flip, 1),
        com.flerty.wikiflerty.reader.PageAnimation.Cover to pool.load(context, R.raw.turn_cover, 1),
        com.flerty.wikiflerty.reader.PageAnimation.Depth to pool.load(context, R.raw.turn_depth, 1),
        com.flerty.wikiflerty.reader.PageAnimation.Fade to pool.load(context, R.raw.turn_fade, 1),
        com.flerty.wikiflerty.reader.PageAnimation.Slide to pool.load(context, R.raw.turn_slide, 1),
    )
    private var lastTurn = 0L
    private val jitter = java.util.Random()

    fun playTurn(animation: com.flerty.wikiflerty.reader.PageAnimation) {
        val s = settings.value
        if (!s.turnSounds) return
        val id = turns[animation] ?: return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTurn < 90) return
        lastTurn = now
        val v = (0.25f + s.soundVolume.toFloat() * 1.2f).coerceIn(0.2f, 0.9f) * (0.9f + jitter.nextFloat() * 0.1f)
        pool.play(id, v, v, 1, 0, 0.95f + jitter.nextFloat() * 0.1f)
    }

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
            val v = if (sound == FeedbackSound.Tap || sound == FeedbackSound.Toggle || sound == FeedbackSound.Open || sound == FeedbackSound.Close) volume else volume * 0.7f
            ids[sound]?.let { pool.play(it, v, v, 1, 0, rate.coerceIn(0.5f, 2f)) }
        }
        if (s.haptics && view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

val LocalFeedback = staticCompositionLocalOf<Feedback?> { null }
