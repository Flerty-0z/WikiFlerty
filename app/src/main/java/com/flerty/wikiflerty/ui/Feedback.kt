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

enum class FeedbackSound { Tap, Toggle, Open, Close, Star, Success, Freeze, Thaw, Tick, Pick, Place, Check, Uncheck, Pop, Trash, Page, Reveal, Copy, YearSwipe, YearRise, YearCount, YearFinale, Back, Nav, Segment, On, Off, Sheet, Confirm, Play, Pause, Bookmark, Search, Add, Deny }

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

    private val variants: Map<FeedbackSound, List<Int>> = mapOf(
        FeedbackSound.Tap to listOf(R.raw.ui_tap_1, R.raw.ui_tap_2).map { pool.load(context, it, 1) },
        FeedbackSound.Nav to listOf(R.raw.ui_nav_1, R.raw.ui_nav_2).map { pool.load(context, it, 1) },
    )
    private val lastVariant = HashMap<FeedbackSound, Int>()

    private val ids = mapOf(
        FeedbackSound.Back to pool.load(context, R.raw.ui_back, 1),
        FeedbackSound.Nav to pool.load(context, R.raw.ui_nav, 1),
        FeedbackSound.Segment to pool.load(context, R.raw.ui_segment, 1),
        FeedbackSound.On to pool.load(context, R.raw.ui_on, 1),
        FeedbackSound.Off to pool.load(context, R.raw.ui_off, 1),
        FeedbackSound.Sheet to pool.load(context, R.raw.ui_sheet, 1),
        FeedbackSound.Confirm to pool.load(context, R.raw.ui_confirm, 1),
        FeedbackSound.Play to pool.load(context, R.raw.ui_play, 1),
        FeedbackSound.Pause to pool.load(context, R.raw.ui_pause, 1),
        FeedbackSound.Bookmark to pool.load(context, R.raw.ui_bookmark, 1),
        FeedbackSound.Search to pool.load(context, R.raw.ui_search, 1),
        FeedbackSound.Add to pool.load(context, R.raw.ui_add, 1),
        FeedbackSound.Deny to pool.load(context, R.raw.ui_deny, 1),
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
        FeedbackSound.YearSwipe to pool.load(context, R.raw.year_swipe, 1),
        FeedbackSound.YearRise to pool.load(context, R.raw.year_rise, 1),
        FeedbackSound.YearCount to pool.load(context, R.raw.year_count, 1),
        FeedbackSound.YearFinale to pool.load(context, R.raw.year_finale, 1),
    )

    private val propRes: Map<Decor, List<Pair<Int, Int>>> = mapOf(
        Decor.Library to listOf(R.raw.prop_library_0_pick to R.raw.prop_library_0_place, R.raw.prop_library_1_pick to R.raw.prop_library_1_place, R.raw.prop_library_2_pick to R.raw.prop_library_2_place),
        Decor.Zen to listOf(R.raw.prop_zen_0_pick to R.raw.prop_zen_0_place, R.raw.prop_zen_1_pick to R.raw.prop_zen_1_place, R.raw.prop_zen_2_pick to R.raw.prop_zen_2_place),
        Decor.Comic to listOf(R.raw.prop_comic_0_pick to R.raw.prop_comic_0_place, R.raw.prop_comic_1_pick to R.raw.prop_comic_1_place, R.raw.prop_comic_2_pick to R.raw.prop_comic_2_place),
        Decor.News to listOf(R.raw.prop_news_0_pick to R.raw.prop_news_0_place, R.raw.prop_news_1_pick to R.raw.prop_news_1_place, R.raw.prop_news_2_pick to R.raw.prop_news_2_place),
        Decor.Sea to listOf(R.raw.prop_sea_0_pick to R.raw.prop_sea_0_place, R.raw.prop_sea_1_pick to R.raw.prop_sea_1_place, R.raw.prop_sea_2_pick to R.raw.prop_sea_2_place, R.raw.prop_sea_3_pick to R.raw.prop_sea_3_place),
        Decor.Clay to listOf(R.raw.prop_clay_0_pick to R.raw.prop_clay_0_place, R.raw.prop_clay_1_pick to R.raw.prop_clay_1_place, R.raw.prop_clay_2_pick to R.raw.prop_clay_2_place),
        Decor.Frost to listOf(R.raw.prop_frost_0_pick to R.raw.prop_frost_0_place, R.raw.prop_frost_1_pick to R.raw.prop_frost_1_place, R.raw.prop_frost_2_pick to R.raw.prop_frost_2_place, R.raw.prop_frost_3_pick to R.raw.prop_frost_3_place),
    )
    private val propIds = HashMap<Int, Int>()
    private val appContext = context.applicationContext

    fun preloadProps(decor: Decor) {
        propRes[decor]?.forEach { (a, b) ->
            if (a !in propIds) propIds[a] = pool.load(appContext, a, 1)
            if (b !in propIds) propIds[b] = pool.load(appContext, b, 1)
        }
    }

    fun prop(decor: Decor, kind: Int, place: Boolean) {
        val s = settings.value
        if (!s.sounds) return
        val list = propRes[decor] ?: return
        if (list.isEmpty()) return
        val pair = list[((kind % list.size) + list.size) % list.size]
        val res = if (place) pair.second else pair.first
        val id = propIds[res] ?: pool.load(appContext, res, 1).also { propIds[res] = it }
        val v = s.soundVolume.toFloat().coerceIn(0f, 1f)
        pool.play(id, v, v, 1, 0, 0.97f + (Math.random() * 0.06).toFloat())
    }

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
                FeedbackSound.Tap, FeedbackSound.Toggle, FeedbackSound.Open, FeedbackSound.Close, FeedbackSound.Check, FeedbackSound.Uncheck, FeedbackSound.Pick, FeedbackSound.Place,
                FeedbackSound.Back, FeedbackSound.Nav, FeedbackSound.Segment, FeedbackSound.On, FeedbackSound.Off, FeedbackSound.Sheet, FeedbackSound.Confirm,
                FeedbackSound.Play, FeedbackSound.Pause, FeedbackSound.Bookmark, FeedbackSound.Search, FeedbackSound.Add, FeedbackSound.Deny -> volume
                FeedbackSound.Tick, FeedbackSound.Pop -> volume * 0.6f
                FeedbackSound.YearRise -> volume * 0.42f
                FeedbackSound.YearSwipe, FeedbackSound.YearCount -> volume * 0.55f
                else -> volume * 0.7f
            }
            val choices = listOfNotNull(ids[sound]) + variants[sound].orEmpty()
            if (choices.isNotEmpty()) {
                val last = lastVariant[sound] ?: -1
                var i = (Math.random() * choices.size).toInt().coerceIn(0, choices.size - 1)
                if (choices.size > 1 && i == last) i = (i + 1) % choices.size
                lastVariant[sound] = i
                val wobble = if (sound == FeedbackSound.Tap || sound == FeedbackSound.Nav || sound == FeedbackSound.Segment) 0.98f + (Math.random() * 0.04).toFloat() else 1f
                pool.play(choices[i], v, v, 1, 0, (rate * wobble).coerceIn(0.5f, 2f))
            }
        }
        if (s.haptics && view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

val LocalFeedback = staticCompositionLocalOf<Feedback?> { null }
