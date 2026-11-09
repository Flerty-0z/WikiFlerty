package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.ReadStatus
import com.flerty.wikiflerty.data.StreakFreezeStore
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

enum class PropRole { Today, Streak, Books }

class ShelfLife(
    val today: Boolean,
    val streak: Int,
    val frozen: Boolean,
    val books: Int,
    val sad: Boolean,
    val evening: Boolean,
) {
    val streakStage: Int = when {
        streak >= 100 -> 5
        streak >= 30 -> 4
        streak >= 14 -> 3
        streak >= 7 -> 2
        streak >= 3 -> 1
        else -> 0
    }

    val bookStage: Int = when {
        books >= 50 -> 5
        books >= 25 -> 4
        books >= 10 -> 3
        books >= 5 -> 2
        books >= 1 -> 1
        else -> 0
    }

    fun stage(role: PropRole): Int = when (role) {
        PropRole.Streak -> streakStage
        PropRole.Books -> bookStage
        PropRole.Today -> if (today) 1 else 0
    }

    companion object {
        val Calm = ShelfLife(today = true, streak = 14, frozen = false, books = 10, sad = false, evening = false)
    }
}

class ShelfGrowth(private val prefs: android.content.SharedPreferences) {
    private val anims = PropRole.entries.associateWith { Animatable(1f) }
    private val running = mutableSetOf<PropRole>()

    fun value(role: PropRole): Float = anims.getValue(role).value

    suspend fun check(role: PropRole, stage: Int, onStart: () -> Unit) {
        if (role == PropRole.Today) return
        val key = "seen_${role.name}"
        val seen = prefs.getInt(key, -1)
        if (seen < 0 || stage < seen) {
            prefs.edit().putInt(key, stage).apply()
            return
        }
        if (stage == seen || role in running) return
        running += role
        try {
            delay(650)
            val anim = anims.getValue(role)
            anim.snapTo(0f)
            onStart()
            anim.animateTo(1f, tween(Motion.ms(1700).coerceAtLeast(1), easing = LinearEasing))
            prefs.edit().putInt(key, stage).apply()
        } finally {
            anims.getValue(role).snapTo(1f)
            running -= role
        }
    }
}

class ShelfState(val life: ShelfLife, val growth: ShelfGrowth?)

val LocalShelfState = staticCompositionLocalOf { ShelfState(ShelfLife.Calm, null) }

@Composable
fun rememberShelfState(): ShelfState {
    val app = LocalContext.current.applicationContext as App
    val days by app.stats.days.collectAsStateWithLifecycle()
    val freezes by app.streakFreeze.freezes.collectAsStateWithLifecycle()
    val books by app.library.books.collectAsStateWithLifecycle()
    val growth = remember { ShelfGrowth(app.getSharedPreferences("shelf_life", android.content.Context.MODE_PRIVATE)) }
    val today = LocalDate.now()
    val life = remember(days, freezes, books, today) {
        val perDay = StatsCalc.perDay(days)
        val ice = StreakFreezeStore.days(freezes)
        val streak = StatsCalc.streaks(perDay, today, frozen = ice).first
        val readToday = (perDay[today]?.ms ?: 0L) >= 60_000L
        ShelfLife(
            today = readToday,
            streak = streak,
            frozen = today in ice || today.minusDays(1) in ice,
            books = books.count { it.status == ReadStatus.Finished },
            sad = streak == 0 && perDay.values.any { it.ms >= 60_000L },
            evening = !readToday && LocalTime.now().hour >= 19,
        )
    }
    return remember(life) { ShelfState(life, growth) }
}
