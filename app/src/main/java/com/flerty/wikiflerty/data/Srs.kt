package com.flerty.wikiflerty.data

import android.content.Context
import java.io.File
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Where a word is in learning. Times are in milliseconds.
 * [step] ≥ 0: still being learned (short steps of minutes); -1: in long-term review.
 */
data class SrsState(
    val due: Long = 0,
    /** Days until the next review once the word is in long-term review. */
    val interval: Double = 0.0,
    /** How fast the interval grows; lower for hard words. */
    val ease: Double = 2.5,
    val reps: Int = 0,
    val lapses: Int = 0,
    val step: Int = 0,
    val last: Long = 0,
) {
    val isNew: Boolean get() = last == 0L
    val isLearning: Boolean get() = !isNew && step >= 0
    val isReview: Boolean get() = step < 0
    /** Remembered for three weeks or more. */
    val isMastered: Boolean get() = isReview && interval >= 21.0

    fun toJson(): JSONObject = JSONObject().apply {
        put("due", due)
        put("interval", interval)
        put("ease", ease)
        put("reps", reps)
        put("lapses", lapses)
        put("step", step)
        put("last", last)
    }

    companion object {
        fun fromJson(j: JSONObject) = SrsState(
            due = j.optLong("due"),
            interval = j.optDouble("interval", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            ease = j.optDouble("ease", 2.5).takeUnless { it.isNaN() }?.coerceIn(1.3, 3.5) ?: 2.5,
            reps = j.optInt("reps"),
            lapses = j.optInt("lapses"),
            step = j.optInt("step", 0),
            last = j.optLong("last"),
        )
    }
}

enum class Grade { Again, Hard, Good, Easy }

/**
 * Spaced repetition, close to the classic Anki scheme:
 * new words go through short steps (1 and 10 minutes), then come back after 1 day, then after
 * ever longer gaps. A forgotten word is learned again in 10 minutes and its gap shrinks.
 */
object Srs {
    private const val MINUTE = 60_000L
    private const val DAY = 86_400_000L
    private val learnSteps = listOf(1 * MINUTE, 10 * MINUTE)
    private val relearnSteps = listOf(10 * MINUTE)
    private const val MAX_INTERVAL = 3650.0

    fun next(s: SrsState, grade: Grade, now: Long = System.currentTimeMillis()): SrsState {
        val answered = s.copy(reps = s.reps + 1, last = now)
        return if (s.step >= 0) learn(answered, grade, now, relearning = s.lapses > 0 && s.interval > 0) else review(answered, s, grade, now)
    }

    private fun learn(s: SrsState, grade: Grade, now: Long, relearning: Boolean): SrsState {
        val steps = if (relearning) relearnSteps else learnSteps
        val step = s.step.coerceIn(0, steps.lastIndex)
        return when (grade) {
            Grade.Again -> s.copy(step = 0, due = now + steps[0])
            Grade.Hard -> {
                val wait = if (step == 0 && steps.size > 1) (steps[0] + steps[1]) / 2 else (steps[step] * 1.5).toLong()
                s.copy(step = step, due = now + wait)
            }
            Grade.Good -> if (step + 1 < steps.size) {
                s.copy(step = step + 1, due = now + steps[step + 1])
            } else {
                val interval = if (relearning) max(1.0, s.interval) else 1.0
                s.copy(step = -1, interval = interval, due = now + days(interval, s.reps))
            }
            Grade.Easy -> {
                val interval = if (relearning) max(2.0, s.interval * 1.3) else 4.0
                s.copy(step = -1, interval = interval, due = now + days(interval, s.reps))
            }
        }
    }

    private fun review(s: SrsState, before: SrsState, grade: Grade, now: Long): SrsState {
        val old = max(1.0, before.interval)
        // A word reviewed late but still remembered deserves a longer gap.
        val late = max(0.0, (now - before.due).toDouble() / DAY)
        return when (grade) {
            Grade.Again -> s.copy(
                lapses = s.lapses + 1,
                ease = max(1.3, s.ease - 0.2),
                interval = max(1.0, old * 0.5),
                step = 0,
                due = now + relearnSteps[0],
            )
            Grade.Hard -> {
                val interval = cap(max(old + 1, (old + late / 4) * 1.2))
                s.copy(ease = max(1.3, s.ease - 0.15), interval = interval, due = now + days(interval, s.reps))
            }
            Grade.Good -> {
                val interval = cap(max(old + 1, (old + late / 2) * s.ease))
                s.copy(interval = interval, due = now + days(interval, s.reps))
            }
            Grade.Easy -> {
                val interval = cap(max(old + 2, (old + late) * s.ease * 1.3))
                s.copy(ease = min(3.5, s.ease + 0.15), interval = interval, due = now + days(interval, s.reps))
            }
        }
    }

    private fun cap(days: Double) = min(MAX_INTERVAL, days)

    /** A small, stable spread (±5%) so words added together don't all come back on the same day. */
    private fun days(interval: Double, seed: Int): Long {
        val fuzz = if (interval > 2) 1.0 + ((seed * 37 % 11) - 5) / 100.0 else 1.0
        return (interval * fuzz * DAY).roundToLong()
    }

    /** "1 мин", "10 мин", "1 дн", "3 мес" — shown on the answer buttons. */
    fun label(s: SrsState, grade: Grade, now: Long = System.currentTimeMillis()): String = wait(next(s, grade, now).due - now)

    fun wait(ms: Long): String {
        val minutes = max(1, ms / MINUTE)
        val days = ms.toDouble() / DAY
        return when {
            minutes < 60 -> "$minutes мин"
            days < 1 -> "${minutes / 60} ч"
            days < 30 -> "${days.roundToLong().coerceAtLeast(1)} дн"
            days < 365 -> "${(days / 30).roundToLong()} мес"
            else -> String.format(java.util.Locale.US, "%.1f г", days / 365)
        }
    }
}

/** How flashcards are studied, and what was done today. Saved to flashcards.json. */
data class FlashcardPrefs(
    val newPerDay: Int = 15,
    /** "word": word → meaning; "meaning": meaning → word; "mixed": both. */
    val direction: String = "word",
    val autoSpeak: Boolean = false,
    /** Day (yyyy-MM-dd) and number of new words already started that day. */
    val newDay: String = "",
    val newCount: Int = 0,
    /** Answers per day: day → [answers, correct]. */
    val history: Map<String, Pair<Int, Int>> = emptyMap(),
) {
    fun newLeftToday(today: String = LocalDate.now().toString()): Int =
        max(0, newPerDay - if (newDay == today) newCount else 0)

    fun toJson(): JSONObject = JSONObject().apply {
        put("newPerDay", newPerDay)
        put("direction", direction)
        put("autoSpeak", autoSpeak)
        put("newDay", newDay)
        put("newCount", newCount)
        put("history", JSONObject().apply { history.forEach { (d, p) -> put(d, JSONObject().put("n", p.first).put("ok", p.second)) } })
    }

    companion object {
        fun fromJson(j: JSONObject) = FlashcardPrefs(
            newPerDay = j.optInt("newPerDay", 15).coerceIn(1, 200),
            direction = j.optString("direction", "word"),
            autoSpeak = j.optBoolean("autoSpeak", false),
            newDay = j.optString("newDay"),
            newCount = j.optInt("newCount"),
            history = j.optJSONObject("history")?.let { h ->
                h.keys().asSequence().associateWith { d ->
                    val o = h.optJSONObject(d)
                    (o?.optInt("n") ?: 0) to (o?.optInt("ok") ?: 0)
                }
            }.orEmpty(),
        )
    }
}

class FlashcardStore(context: Context) {
    private val file = File(context.filesDir, "flashcards.json")
    private val _prefs = MutableStateFlow(load())
    val prefs: StateFlow<FlashcardPrefs> = _prefs.asStateFlow()

    @Synchronized
    fun update(transform: (FlashcardPrefs) -> FlashcardPrefs) {
        val updated = transform(_prefs.value)
        if (updated == _prefs.value) return
        _prefs.value = updated
        runCatching { file.writeText(updated.toJson().toString()) }
    }

    /** Records one answer (and a new word started, if it was new). */
    fun recordAnswer(correct: Boolean, wasNew: Boolean) = update { p ->
        val today = LocalDate.now().toString()
        val (n, ok) = p.history[today] ?: (0 to 0)
        // Keep the last 400 days only.
        val history = (p.history + (today to (n + 1 to ok + if (correct) 1 else 0))).entries
            .sortedByDescending { it.key }.take(400).associate { it.key to it.value }
        p.copy(
            history = history,
            newDay = if (wasNew) today else p.newDay,
            newCount = if (wasNew) (if (p.newDay == today) p.newCount + 1 else 1) else p.newCount,
        )
    }

    private fun load(): FlashcardPrefs = runCatching {
        if (file.exists()) FlashcardPrefs.fromJson(JSONObject(file.readText())) else FlashcardPrefs()
    }.getOrElse { FlashcardPrefs() }
}
