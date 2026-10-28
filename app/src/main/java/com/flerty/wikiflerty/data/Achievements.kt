package com.flerty.wikiflerty.data

import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.ui.StatsCalc
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class AchievementKind { Streak, Books, Pages, Time }

data class Achievement(
    val id: String,
    val kind: AchievementKind,
    val target: Long,
    val title: String,
    val hint: String,
)

data class AchievementProgress(
    val bestStreak: Int = 0,
    val books: Int = 0,
    val pages: Int = 0,
    val ms: Long = 0,
) {
    fun value(kind: AchievementKind): Long = when (kind) {
        AchievementKind.Streak -> bestStreak.toLong()
        AchievementKind.Books -> books.toLong()
        AchievementKind.Pages -> pages.toLong()
        AchievementKind.Time -> ms / HOUR
    }

    fun fraction(a: Achievement): Float = (value(a.kind).toFloat() / a.target).coerceIn(0f, 1f)

    companion object {
        const val HOUR = 3_600_000L

        fun of(days: Map<String, Map<String, DayEntry>>, books: List<Book>): AchievementProgress {
            val perDay = StatsCalc.perDay(days)
            val total = perDay.values.fold(DayEntry()) { a, b -> a + b }
            return AchievementProgress(
                bestStreak = StatsCalc.streaks(perDay).second,
                books = books.count { it.status == ReadStatus.Finished },
                pages = total.pages,
                ms = total.ms,
            )
        }
    }
}

object Achievements {
    val all: List<Achievement> = listOf(
        Achievement("streak3", AchievementKind.Streak, 3, "3 дня подряд", "Читай 3 дня подряд"),
        Achievement("streak7", AchievementKind.Streak, 7, "Неделя подряд", "Читай 7 дней подряд"),
        Achievement("streak30", AchievementKind.Streak, 30, "Месяц подряд", "Читай 30 дней подряд"),
        Achievement("streak100", AchievementKind.Streak, 100, "100 дней", "Читай 100 дней подряд"),
        Achievement("books1", AchievementKind.Books, 1, "Первая книга", "Дочитай первую книгу"),
        Achievement("books5", AchievementKind.Books, 5, "5 книг", "Дочитай 5 книг"),
        Achievement("books10", AchievementKind.Books, 10, "10 книг", "Дочитай 10 книг"),
        Achievement("books25", AchievementKind.Books, 25, "25 книг", "Дочитай 25 книг"),
        Achievement("pages1000", AchievementKind.Pages, 1_000, "1 000 страниц", "Прочитай 1 000 страниц"),
        Achievement("pages10000", AchievementKind.Pages, 10_000, "10 000 страниц", "Прочитай 10 000 страниц"),
        Achievement("hours1", AchievementKind.Time, 1, "Первый час", "Читай в сумме 1 час"),
        Achievement("hours10", AchievementKind.Time, 10, "10 часов", "Читай в сумме 10 часов"),
        Achievement("hours100", AchievementKind.Time, 100, "100 часов", "Читай в сумме 100 часов"),
    )

    fun byId(id: String): Achievement? = all.firstOrNull { it.id == id }
}

class AchievementStore(private val app: App) {
    private val file = File(app.filesDir, "achievements.json")
    private var silent = !file.exists()

    private val _unlocked = MutableStateFlow(load())
    val unlocked: StateFlow<Map<String, Long>> = _unlocked.asStateFlow()

    private val _queue = MutableStateFlow<List<Achievement>>(emptyList())
    val queue: StateFlow<List<Achievement>> = _queue.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(app.stats.days, app.library.books) { days, books -> days to books }
                .collectLatest { (days, books) ->
                    delay(1200)
                    check(AchievementProgress.of(days, books))
                }
        }
    }

    @Synchronized
    private fun check(progress: AchievementProgress) {
        val fresh = Achievements.all.filter { progress.value(it.kind) >= it.target && it.id !in _unlocked.value }
        val quiet = silent
        silent = false
        if (fresh.isEmpty()) return
        val now = System.currentTimeMillis()
        _unlocked.value = _unlocked.value + fresh.associate { it.id to now }
        save()
        if (!quiet) _queue.value = _queue.value + fresh
    }

    @Synchronized
    fun dismiss(firstId: String, count: Int) {
        if (_queue.value.firstOrNull()?.id == firstId) _queue.value = _queue.value.drop(count)
    }

    fun toJson(): JSONObject = JSONObject().apply { _unlocked.value.forEach { (id, at) -> put(id, at) } }

    @Synchronized
    fun merge(json: JSONObject?) {
        if (json == null) return
        val result = _unlocked.value.toMutableMap()
        var changed = false
        json.keys().forEach { id ->
            if (Achievements.byId(id) == null) return@forEach
            val at = json.optLong(id)
            if (at <= 0) return@forEach
            val mine = result[id]
            if (mine == null || at < mine) {
                result[id] = at
                changed = true
            }
        }
        if (changed) {
            _unlocked.value = result
            save()
        }
    }

    private fun load(): Map<String, Long> = runCatching {
        if (!file.exists()) return@runCatching emptyMap()
        val json = JSONObject(file.readText())
        json.keys().asSequence().associateWith { json.optLong(it) }.filterValues { it > 0 }
    }.getOrElse { emptyMap() }

    private fun save() {
        runCatching {
            val tmp = File(file.parentFile, "achievements.json.tmp")
            tmp.writeText(toJson().toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }
}
