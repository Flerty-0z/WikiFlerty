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

enum class FeedbackSound { Tap, Toggle, Open, Close, Star, Success, Freeze, Thaw, Tick, Pick, Place, Check, Uncheck, Pop, Trash, Page, Reveal, Copy }

class Feedback(context: Context, private val settings: StateFlow<ReaderSettings>) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
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
        FeedbackSound.Tick to pool.load(context, R.raw.ui_tick, 1),
        FeedbackSound.Pick to pool.load(context, R.raw.ui_pick, 1),
        FeedbackSound.Place to pool.load(context, R.raw.ui_place, 1),
        FeedbackSound.Check to pool.load(context, R.raw.ui_check, 1),
        FeedbackSound.Uncheck to pool.load(context, R.raw.ui_uncheck, 1),
        FeedbackSound.Pop to pool.load(context, R.raw.ui_pop, 1),
        FeedbackSound.Trash to pool.load(context, R.raw.ui_trash, 1),
        FeedbackSound.Page to pool.load(context, R.raw.ui_page, 1),
        FeedbackSound.Reveal to pool.load(context, R.raw.ui_reveal, 1),
        FeedbackSound.Copy to pool.load(context, R.raw.ui_copy, 1),
    )

    private var lastSound: FeedbackSound? = null
    private var lastTime = 0L

    fun play(sound: FeedbackSound, view: View? = null, rate: Float = 1f) {
        val now = android.os.SystemClock.uptimeMillis()
        if (sound == lastSound && now - lastTime < (if (sound == FeedbackSound.Star || sound == FeedbackSound.Tick) 35 else 80)) return
        lastSound = sound
        lastTime = now
        val s = settings.value
        if (s.sounds) {
            val volume = s.soundVolume.toFloat().coerceIn(0f, 1f)
            val v = when (sound) {
                FeedbackSound.Tap, FeedbackSound.Toggle, FeedbackSound.Open, FeedbackSound.Close, FeedbackSound.Check, FeedbackSound.Uncheck, FeedbackSound.Pick, FeedbackSound.Place -> volume
                FeedbackSound.Tick, FeedbackSound.Pop -> volume * 0.6f
                else -> volume * 0.7f
            }
            ids[sound]?.let { pool.play(it, v, v, 1, 0, rate.coerceIn(0.5f, 2f)) }
        }
        if (s.haptics && view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

val LocalFeedback = staticCompositionLocalOf<Feedback?> { null }
