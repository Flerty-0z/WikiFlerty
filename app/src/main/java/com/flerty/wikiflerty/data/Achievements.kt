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

enum class AchievementKind { Streak, Books, Pages, Time, Notes, Words, Rhythm, Marathon, Library, Discovery }

data class Achievement(
    val id: String,
    val kind: AchievementKind,
    val target: Long,
    val title: String,
    val hint: String,
    val metric: String = kind.name,
    val tier: Int? = null,
    val unit: String? = null,
)

data class AchievementProgress(val metrics: Map<String, Long> = emptyMap()) {
    fun value(a: Achievement): Long = metrics[a.metric] ?: 0L

    fun fraction(a: Achievement): Float = (value(a).toFloat() / a.target).coerceIn(0f, 1f)

    companion object {
        const val HOUR = 3_600_000L

        fun of(
            days: Map<String, Map<String, DayEntry>>,
            books: List<Book>,
            notes: Int = 0,
            words: Int = 0,
            events: Set<String> = emptySet(),
            mornings: Int = 0,
            nights: Int = 0,
            freezes: Int = 0,
        ): AchievementProgress {
            val perDay = StatsCalc.perDay(days)
            val total = perDay.values.fold(DayEntry()) { a, b -> a + b }
            val m = HashMap<String, Long>()
            m[AchievementKind.Streak.name] = StatsCalc.streaks(perDay).second.toLong()
            val finished = books.filter { it.status == ReadStatus.Finished }
            m[AchievementKind.Books.name] = finished.size.toLong()
            m[AchievementKind.Pages.name] = total.pages.toLong()
            m[AchievementKind.Time.name] = total.ms / HOUR
            m[AchievementKind.Notes.name] = notes.toLong()
            m[AchievementKind.Words.name] = words.toLong()
            events.forEach { m["ev:$it"] = 1L }
            fun flag(key: String, on: Boolean) { if (on) m[key] = 1L }
            flag("d:tag", books.any { b -> b.tags.any { it.isNotBlank() } })
            flag("d:shelf", books.any { it.shelves.isNotEmpty() })
            flag("d:half", books.any { it.progress >= 0.5 || it.status == ReadStatus.Finished })
            flag("d:review", books.any { it.review.isNotBlank() })
            flag("d:edit", books.any { it.edited })
            flag("d:freeze", freezes > 0)
            m["morning"] = mornings.toLong()
            m["night"] = nights.toLong()
            val read = perDay.filterValues { it.ms > 0 }.keys.sorted()
            flag("comeback", read.zipWithNext().any { (a, b) -> java.time.temporal.ChronoUnit.DAYS.between(a, b) >= 15 })
            for (minutes in listOf(10, 30, 60)) {
                var best = 0
                var run = 0
                var last: java.time.LocalDate? = null
                perDay.filterValues { it.ms >= minutes * 60_000L }.keys.sorted().forEach { d ->
                    run = if (last != null && last!!.plusDays(1) == d) run + 1 else 1
                    if (run > best) best = run
                    last = d
                }
                m["run$minutes"] = best.toLong()
            }
            val byMonth = read.groupBy { java.time.YearMonth.from(it) }
            m["monthDays"] = (byMonth.values.maxOfOrNull { it.size } ?: 0).toLong()
            flag("perfectMonth", byMonth.any { (ym, list) -> list.size == ym.lengthOfMonth() })
            m["yearDays"] = (read.groupBy { it.year }.values.maxOfOrNull { it.size } ?: 0).toLong()
            m["dayHours"] = (perDay.values.maxOfOrNull { it.ms } ?: 0L) / HOUR
            m["dayPages"] = (perDay.values.maxOfOrNull { it.pages } ?: 0).toLong()
            val zone = java.time.ZoneId.systemDefault()
            m["monthBooks"] = (finished.mapNotNull { b -> b.finishedAt?.let { java.time.YearMonth.from(java.time.Instant.ofEpochMilli(it).atZone(zone)) } }
                .groupingBy { it }.eachCount().values.maxOrNull() ?: 0).toLong()
            m["brick"] = finished.count { it.positions >= 800 }.toLong()
            m["shelfSize"] = books.size.toLong()
            m["authors"] = finished.map { it.author.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().size.toLong()
            m["seriesDone"] = finished.flatMap { b -> b.series.map { it.name.trim().lowercase() }.filter { it.isNotEmpty() }.distinct() }
                .groupingBy { it }.eachCount().count { it.value >= 3 }.toLong()
            m["rated"] = books.count { it.rating != null }.toLong()
            return AchievementProgress(m)
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
        unit = "ч",
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

    private fun rhythm(id: String, metric: String, target: Long, tier: Int, title: String, hint: String) =
        Achievement(id, AchievementKind.Rhythm, target, title, hint, metric, tier)

    private fun marathon(id: String, metric: String, target: Long, tier: Int, title: String, hint: String, unit: String? = null) =
        Achievement(id, AchievementKind.Marathon, target, title, hint, metric, tier, unit)

    private fun library(id: String, metric: String, target: Long, tier: Int, title: String, hint: String) =
        Achievement(id, AchievementKind.Library, target, title, hint, metric, tier)

    private fun first(id: String, metric: String, title: String, hint: String) =
        Achievement("first_$id", AchievementKind.Discovery, 1, title, hint, metric, 1)

    private val rhythm = listOf(
        rhythm("morning5", "morning", 5, 0, "Жаворонок", "Читай до 8 утра в 5 разных дней"),
        rhythm("night5", "night", 5, 0, "Сова", "Читай после 23:00 в 5 разных дней"),
        rhythm("run10", "run10", 7, 0, "Неделя по 10 минут", "Читай 7 дней подряд хотя бы по 10 минут"),
        rhythm("comeback", "comeback", 1, 1, "С возвращением", "Вернись к чтению после двух недель перерыва"),
        rhythm("run30", "run30", 7, 1, "Неделя по 30 минут", "Читай 7 дней подряд хотя бы по 30 минут"),
        rhythm("month20", "monthDays", 20, 1, "20 дней за месяц", "Читай 20 дней в одном месяце"),
        rhythm("year200", "yearDays", 200, 1, "200 дней за год", "Читай 200 дней за один год"),
        rhythm("morning20", "morning", 20, 2, "Ранняя пташка", "Читай до 8 утра в 20 разных дней"),
        rhythm("night20", "night", 20, 2, "Ночной читатель", "Читай после 23:00 в 20 разных дней"),
        rhythm("run60", "run60", 7, 2, "Неделя по часу", "Читай 7 дней подряд хотя бы по часу"),
        rhythm("perfectMonth", "perfectMonth", 1, 2, "Идеальный месяц", "Читай каждый день календарного месяца"),
        rhythm("year300", "yearDays", 300, 2, "300 дней за год", "Читай 300 дней за один год"),
    )

    private val marathon = listOf(
        marathon("day1h", "dayHours", 1, 0, "Час за день", "Прочитай час за один день", "ч"),
        marathon("day100p", "dayPages", 100, 0, "100 страниц за день", "Прочитай 100 страниц за один день"),
        marathon("day2h", "dayHours", 2, 1, "Два часа за день", "Прочитай два часа за один день", "ч"),
        marathon("month3", "monthBooks", 3, 1, "3 книги за месяц", "Дочитай 3 книги в одном месяце"),
        marathon("brick", "brick", 1, 1, "Кирпич", "Дочитай книгу от 800 страниц"),
        marathon("day4h", "dayHours", 4, 2, "Четыре часа за день", "Прочитай четыре часа за один день", "ч"),
        marathon("day300p", "dayPages", 300, 2, "300 страниц за день", "Прочитай 300 страниц за один день"),
        marathon("month5", "monthBooks", 5, 2, "5 книг за месяц", "Дочитай 5 книг в одном месяце"),
    )

    private val library = listOf(
        library("shelf1", "shelfSize", 1, 0, "Новоселье", "Добавь первую книгу"),
        library("shelf10", "shelfSize", 10, 0, "10 книг на полке", "Собери 10 книг в библиотеке"),
        library("authors5", "authors", 5, 0, "5 авторов", "Дочитай книги 5 разных авторов"),
        library("rated5", "rated", 5, 0, "Критик", "Оцени 5 книг"),
        library("shelf50", "shelfSize", 50, 1, "50 книг на полке", "Собери 50 книг в библиотеке"),
        library("authors15", "authors", 15, 1, "15 авторов", "Дочитай книги 15 разных авторов"),
        library("series3", "seriesDone", 1, 1, "Верность циклу", "Дочитай 3 книги одного цикла"),
        library("rated25", "rated", 25, 1, "Строгий критик", "Оцени 25 книг"),
        library("shelf100", "shelfSize", 100, 1, "100 книг на полке", "Собери 100 книг в библиотеке"),
        library("authors30", "authors", 30, 2, "30 авторов", "Дочитай книги 30 разных авторов"),
        library("shelf250", "shelfSize", 250, 2, "Своя библиотека", "Собери 250 книг в библиотеке"),
    )

    private val discovery = listOf(
        first("half", "d:half", "Экватор", "Дочитай книгу до середины"),
        first("tts", "ev:tts", "Голос книги", "Включи чтение вслух"),
        first("search", "ev:search", "Поиск", "Найди фразу поиском по книге"),
        first("quote", "ev:quote", "Цитата", "Поделись цитатой картинкой"),
        first("tag", "d:tag", "Первый тег", "Добавь книге тег"),
        first("shelf", "d:shelf", "Своя полка", "Поставь книгу на свою полку"),
        first("props", "ev:props", "Декоратор", "Передвинь предмет на полке"),
        first("random", "ev:random", "Бросок кубика", "Выбери случайную книгу кубиком"),
        first("timer", "ev:timer", "Секундомер", "Запусти таймер сеанса чтения"),
        first("cover", "ev:cover", "Новая обложка", "Смени обложку книги"),
        first("edit", "d:edit", "Редактор", "Измени данные о книге"),
        first("review", "d:review", "Отзыв", "Напиши отзыв о книге"),
        first("cards", "ev:cards", "Карточки", "Повтори слова на карточках"),
        first("ai", "ev:ai", "Собеседник", "Спроси ИИ о книге"),
        first("kit", "ev:kit", "Новый облик", "Смени шаблон оформления"),
        first("background", "ev:background", "Свой фон", "Поставь свою картинку или видео на фон"),
        first("ambient", "ev:ambient", "Атмосфера", "Включи звуки для атмосферы"),
        first("freeze", "d:freeze", "Заморозка", "Заморозь серию"),
    )

    val all: List<Achievement> =
        listOf(3L, 5L, 7L, 14L, 21L, 30L, 50L, 75L, 100L, 150L, 200L, 365L).map(::streak) +
            listOf(1L, 3L, 5L, 10L, 15L, 20L, 30L, 50L, 75L, 100L).map(::books) +
            listOf(100L, 250L, 500L, 1_000L, 2_500L, 5_000L, 10_000L, 25_000L, 50_000L).map(::pages) +
            listOf(1L, 5L, 10L, 25L, 50L, 100L, 250L, 500L).map(::hours) +
            listOf(1L, 10L, 25L, 50L, 100L, 250L, 500L).map(::notes) +
            listOf(1L, 10L, 25L, 50L, 100L, 250L, 500L).map(::words) +
            rhythm + marathon + library + discovery

    fun byId(id: String): Achievement? = all.firstOrNull { it.id == id }
}

class AchievementStore(private val app: App) {
    private val file = File(app.filesDir, "achievements.json")
    private var silent = !file.exists()

    private val _unlocked = MutableStateFlow(load())
    val unlocked: StateFlow<Map<String, Long>> = _unlocked.asStateFlow()

    private val _queue = MutableStateFlow<List<Achievement>>(emptyList())
    val queue: StateFlow<List<Achievement>> = _queue.asStateFlow()

    private val prefs = app.getSharedPreferences("achievement_events", android.content.Context.MODE_PRIVATE)
    private val _events = MutableStateFlow(prefs.getStringSet("events", emptySet()).orEmpty().toSet())
    private val _mornings = MutableStateFlow(prefs.getStringSet("mornings", emptySet()).orEmpty().toSet())
    private val _nights = MutableStateFlow(prefs.getStringSet("nights", emptySet()).orEmpty().toSet())
    private val _progress = MutableStateFlow(AchievementProgress())
    val progress: StateFlow<AchievementProgress> = _progress.asStateFlow()

    fun seenAt(): Long {
        val saved = prefs.getLong("seen", 0L)
        if (saved > 0L) return saved
        val now = System.currentTimeMillis()
        prefs.edit().putLong("seen", now).apply()
        return now
    }

    fun markSeen() {
        prefs.edit().putLong("seen", System.currentTimeMillis()).apply()
    }

    @Synchronized
    fun event(key: String) {
        if (key in _events.value) return
        _events.value = _events.value + key
        prefs.edit().putStringSet("events", _events.value).apply()
    }

    @Synchronized
    private fun mark(morning: Boolean, day: String) {
        val flow = if (morning) _mornings else _nights
        if (day in flow.value) return
        flow.value = flow.value + day
        prefs.edit().putStringSet(if (morning) "mornings" else "nights", flow.value).apply()
    }

    private fun watchClock(scope: CoroutineScope) {
        scope.launch {
            var last: Long? = null
            app.stats.days.collect { days ->
                val today = java.time.LocalDate.now().toString()
                val ms = days[today]?.values?.sumOf { it.ms } ?: 0L
                val before = last
                last = ms
                if (before == null || ms <= before) return@collect
                val now = java.time.LocalDateTime.now()
                val hour = now.hour
                when {
                    hour in 4..7 -> mark(true, now.toLocalDate().toString())
                    hour >= 23 || hour < 4 -> mark(false, now.minusHours(4).toLocalDate().toString())
                }
            }
        }
        scope.launch {
            var kit: String? = null
            app.style.style.collect { st ->
                val was = kit
                kit = st.kit
                if (was != null && was != st.kit) event("kit")
                if (st.background != com.flerty.wikiflerty.ui.BackgroundKind.None.id) event("background")
            }
        }
        scope.launch {
            app.ambient.mix.collect { mix -> if (mix.isNotEmpty()) event("ambient") }
        }
    }

    fun start(scope: CoroutineScope) {
        watchClock(scope)
        scope.launch {
            val base = combine(app.stats.days, app.library.books, app.notes.notes, app.dictionary.entries) { days, books, notes, words ->
                listOf<Any>(days, books, notes.size, words.size)
            }
            val extra = combine(_events, _mornings, _nights, app.streakFreeze.freezes) { events, mornings, nights, freezes ->
                listOf<Any>(events, mornings.size, nights.size, freezes.size)
            }
            combine(base, extra) { b, e ->
                @Suppress("UNCHECKED_CAST")
                AchievementProgress.of(
                    b[0] as Map<String, Map<String, DayEntry>>,
                    b[1] as List<Book>,
                    b[2] as Int,
                    b[3] as Int,
                    e[0] as Set<String>,
                    e[1] as Int,
                    e[2] as Int,
                    e[3] as Int,
                )
            }
                .collectLatest { progress ->
                    _progress.value = progress
                    delay(1200)
                    check(progress)
                }
        }
    }

    @Synchronized
    private fun check(progress: AchievementProgress) {
        val fresh = Achievements.all.filter { progress.value(it) >= it.target && it.id !in _unlocked.value }
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
