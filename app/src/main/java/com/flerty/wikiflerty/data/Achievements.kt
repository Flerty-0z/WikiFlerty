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

enum class AchievementKind { Streak, Books, Pages, Time, Notes, Words }

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
    val notes: Int = 0,
    val words: Int = 0,
) {
    fun value(kind: AchievementKind): Long = when (kind) {
        AchievementKind.Streak -> bestStreak.toLong()
        AchievementKind.Books -> books.toLong()
        AchievementKind.Pages -> pages.toLong()
        AchievementKind.Time -> ms / HOUR
        AchievementKind.Notes -> notes.toLong()
        AchievementKind.Words -> words.toLong()
    }

    fun fraction(a: Achievement): Float = (value(a.kind).toFloat() / a.target).coerceIn(0f, 1f)

    companion object {
        const val HOUR = 3_600_000L

        fun of(days: Map<String, Map<String, DayEntry>>, books: List<Book>, notes: Int = 0, words: Int = 0): AchievementProgress {
            val perDay = StatsCalc.perDay(days)
            val total = perDay.values.fold(DayEntry()) { a, b -> a + b }
            return AchievementProgress(
                bestStreak = StatsCalc.streaks(perDay).second,
                books = books.count { it.status == ReadStatus.Finished },
                pages = total.pages,
                ms = total.ms,
                notes = notes,
                words = words,
            )
        }
    }
}

object Achievements {
    private fun n(v: Long): String = String.format(java.util.Locale.forLanguageTag("ru"), "%,d", v).replace('\u00A0', ' ')

    private fun streak(d: Long) = Achievement(
        "streak$d",
        AchievementKind.Streak,
        d,
        when (d) {
            7L -> "Неделя подряд"
            14L -> "Две недели"
            30L -> "Месяц подряд"
            365L -> "Год подряд"
            else -> "${n(d)} " + StatsCalc.plural(d.toInt(), "день", "дня", "дней") + " подряд"
        },
        "Читай ${n(d)} " + StatsCalc.plural(d.toInt(), "день", "дня", "дней") + " подряд",
    )

    private fun books(c: Long) = Achievement(
        "books$c",
        AchievementKind.Books,
        c,
        if (c == 1L) "Первая книга" else "${n(c)} " + StatsCalc.plural(c.toInt(), "книга", "книги", "книг"),
        "Дочитай ${n(c)} " + StatsCalc.plural(c.toInt(), "книгу", "книги", "книг"),
    )

    private fun pages(c: Long) = Achievement(
        "pages$c",
        AchievementKind.Pages,
        c,
        "${n(c)} " + StatsCalc.plural(c.toInt(), "страница", "страницы", "страниц"),
        "Прочитай ${n(c)} " + StatsCalc.plural(c.toInt(), "страницу", "страницы", "страниц"),
    )

    private fun hours(h: Long) = Achievement(
        "hours$h",
        AchievementKind.Time,
        h,
        if (h == 1L) "Первый час" else "${n(h)} " + StatsCalc.plural(h.toInt(), "час", "часа", "часов"),
        "Читай в сумме ${n(h)} " + StatsCalc.plural(h.toInt(), "час", "часа", "часов"),
    )

    private fun notes(c: Long) = Achievement(
        "notes$c",
        AchievementKind.Notes,
        c,
        if (c == 1L) "Первая заметка" else "${n(c)} " + StatsCalc.plural(c.toInt(), "заметка", "заметки", "заметок"),
        "Сделай ${n(c)} " + StatsCalc.plural(c.toInt(), "заметку", "заметки", "заметок"),
    )

    private fun words(c: Long) = Achievement(
        "words$c",
        AchievementKind.Words,
        c,
        if (c == 1L) "Первое слово" else "${n(c)} " + StatsCalc.plural(c.toInt(), "слово", "слова", "слов"),
        "Добавь в словарь ${n(c)} " + StatsCalc.plural(c.toInt(), "слово", "слова", "слов"),
    )

    val all: List<Achievement> =
        listOf(3L, 5L, 7L, 14L, 21L, 30L, 50L, 75L, 100L, 150L, 200L, 365L).map(::streak) +
            listOf(1L, 3L, 5L, 10L, 15L, 20L, 30L, 50L, 75L, 100L).map(::books) +
            listOf(100L, 250L, 500L, 1_000L, 2_500L, 5_000L, 10_000L, 25_000L, 50_000L).map(::pages) +
            listOf(1L, 5L, 10L, 25L, 50L, 100L, 250L, 500L).map(::hours) +
            listOf(1L, 10L, 25L, 50L, 100L, 250L, 500L).map(::notes) +
            listOf(1L, 10L, 25L, 50L, 100L, 250L, 500L).map(::words)

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
            combine(app.stats.days, app.library.books, app.notes.notes, app.dictionary.entries) { days, books, notes, words ->
                AchievementProgress.of(days, books, notes.size, words.size)
            }
                .collectLatest { progress ->
                    delay(1200)
                    check(progress)
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
