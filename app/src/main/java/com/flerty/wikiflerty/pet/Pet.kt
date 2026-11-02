package com.flerty.wikiflerty.pet

import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.ReadStatus
import com.flerty.wikiflerty.data.StreakFreezeStore
import com.flerty.wikiflerty.ui.StatsCalc
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class PetSpecies(val id: String, val title: String, val hint: String) {
    Cat("cat", "Кот", "Ласковый и любопытный"),
    Dog("dog", "Собачка", "Верный и весёлый"),
    Owl("owl", "Сова", "Мудрая ночная птица"),
    Parrot("parrot", "Попугай", "Яркий и болтливый"),
    Raccoon("raccoon", "Енот", "Хитрый умник"),
    Bear("bear", "Медвежонок", "Уютный соня");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Cat
    }
}

data class PetCounters(
    val pages: Long = 0,
    val books: Int = 0,
    val notes: Int = 0,
    val words: Int = 0,
    val achievements: Int = 0,
    val goalDays: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().put("pages", pages).put("books", books).put("notes", notes)
        .put("words", words).put("achievements", achievements).put("goalDays", goalDays)

    companion object {
        fun fromJson(j: JSONObject?) = if (j == null) PetCounters() else PetCounters(
            j.optLong("pages"), j.optInt("books"), j.optInt("notes"), j.optInt("words"), j.optInt("achievements"), j.optInt("goalDays"),
        )
    }
}

data class PetLog(val time: Long, val label: String, val xp: Int)

data class Pet(
    val species: PetSpecies,
    val name: String,
    val adoptedAt: Long,
    val xp: Long = 0,
    val food: Float = 80f,
    val mood: Float = 80f,
    val energy: Float = 90f,
    val updatedAt: Long = System.currentTimeMillis(),
    val counters: PetCounters = PetCounters(),
    val chapters: Set<String> = emptySet(),
    val log: List<PetLog> = emptyList(),
) {
    val level: Int get() = PetLevels.level(xp)

    fun toJson(): JSONObject = JSONObject()
        .put("species", species.id)
        .put("name", name)
        .put("adoptedAt", adoptedAt)
        .put("xp", xp)
        .put("food", food.toDouble())
        .put("mood", mood.toDouble())
        .put("energy", energy.toDouble())
        .put("updatedAt", updatedAt)
        .put("counters", counters.toJson())
        .put("chapters", JSONArray(chapters.toList().takeLast(3000)))
        .put("log", JSONArray().apply { log.forEach { put(JSONObject().put("t", it.time).put("l", it.label).put("x", it.xp)) } })

    companion object {
        fun fromJson(j: JSONObject): Pet = Pet(
            species = PetSpecies.of(j.optString("species")),
            name = j.optString("name").ifBlank { "Друг" },
            adoptedAt = j.optLong("adoptedAt", System.currentTimeMillis()),
            xp = j.optLong("xp"),
            food = j.optDouble("food", 80.0).toFloat(),
            mood = j.optDouble("mood", 80.0).toFloat(),
            energy = j.optDouble("energy", 90.0).toFloat(),
            updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
            counters = PetCounters.fromJson(j.optJSONObject("counters")),
            chapters = j.optJSONArray("chapters")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }.orEmpty(),
            log = j.optJSONArray("log")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { PetLog(it.optLong("t"), it.optString("l"), it.optInt("x")) } }
            }.orEmpty(),
        )
    }
}

object PetLevels {
    fun total(level: Int): Long = (30L * level + 15L * level * (level + 1) / 2)

    fun level(xp: Long): Int {
        var l = 0
        while (total(l + 1) <= xp) l++
        return l
    }
}

enum class PetMood(val text: String) {
    Vacation("В отпуске вместе с тобой 🏖"),
    Sleep("Спит"),
    Tired("Устал — пора отдохнуть"),
    Hungry("Голоден — почитай немного!"),
    Sad("Грустит — загляни в заметки или карточки"),
    Happy("Счастлив"),
}

enum class PetEvent(val label: String, val xp: Int, val mood: Float) {
    Chapter("Глава прочитана", 15, 6f),
    TeacherGood("Верный ответ учителю", 5, 5f),
    TeacherPart("Почти верный ответ", 2, 2f),
    Card("Карточка повторена", 1, 1f),
}

class PetManager(private val app: App) {
    private val file = File(app.filesDir, "pet.json")
    private val _pet = MutableStateFlow(load())
    val pet: StateFlow<Pet?> = _pet.asStateFlow()

    val open = MutableStateFlow(false)

    private val _peek = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val peek = _peek.asSharedFlow()

    @OptIn(FlowPreview::class)
    fun start() {
        app.appScope.launch {
            combine(app.stats.days, app.library.books, app.notes.notes, app.dictionary.entries, app.achievements.unlocked) { _, _, _, _, _ -> Unit }
                .debounce(1500)
                .collect { settle() }
        }
        app.appScope.launch {
            while (true) {
                delay(15 * 60_000L)
                settle()
            }
        }
    }

    fun adopt(species: PetSpecies, name: String) {
        if (_pet.value != null) return
        val now = System.currentTimeMillis()
        val pet = Pet(species, name.trim().ifBlank { species.title }, now, updatedAt = now)
        _pet.value = pet.copy(counters = counters(pet), log = listOf(PetLog(now, "Появилось яйцо 🥚", 0)))
        save()
    }

    fun rename(name: String) {
        val p = _pet.value ?: return
        if (name.isBlank()) return
        _pet.value = p.copy(name = name.trim())
        save()
    }

    fun event(kind: PetEvent, key: String? = null) {
        val p = _pet.value ?: return
        if (key != null && key in p.chapters) return
        val gained = (kind.xp * multiplier()).toInt()
        _pet.value = p.copy(
            xp = p.xp + gained,
            mood = (p.mood + kind.mood).coerceIn(0f, 100f),
            chapters = if (key != null) p.chapters + key else p.chapters,
            log = (listOf(PetLog(System.currentTimeMillis(), kind.label, gained)) + p.log).take(40),
        )
        save()
        if (kind == PetEvent.Chapter) _peek.tryEmit(gained)
    }

    private fun multiplier(): Float {
        val perDay = StatsCalc.perDay(app.stats.days.value)
        val streak = StatsCalc.streaks(perDay).first
        return 1f + minOf(streak, 25) * 0.02f
    }

    private fun counters(p: Pet): PetCounters {
        val start = Instant.ofEpochMilli(p.adoptedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val days = app.stats.days.value
        val pages = days.filterKeys { it >= start }.values.sumOf { day -> day.values.sumOf { it.pages.toLong() } }
        val books = app.library.books.value.count { it.status == ReadStatus.Finished && (it.finishedAt ?: 0) >= p.adoptedAt }
        val notes = app.notes.notes.value.count { it.createdAt >= p.adoptedAt }
        val words = app.dictionary.entries.value.count { it.createdAt >= p.adoptedAt }
        val achievements = app.achievements.unlocked.value.values.count { it >= p.adoptedAt }
        val goal = app.libraryPrefs.prefs.value.dailyGoalMinutes
        val goalDays = if (goal <= 0) 0 else StatsCalc.perDay(days).count { (d, e) -> d.toString() >= start && e.ms >= goal * 60_000L }
        return PetCounters(pages, books, notes, words, achievements, goalDays)
    }

    @Synchronized
    fun settle() {
        val p = _pet.value ?: return
        val now = System.currentTimeMillis()
        val c = counters(p)
        val old = p.counters
        val dPages = (c.pages - old.pages).coerceAtLeast(0)
        val dBooks = (c.books - old.books).coerceAtLeast(0)
        val dNotes = (c.notes - old.notes).coerceAtLeast(0)
        val dWords = (c.words - old.words).coerceAtLeast(0)
        val dAch = (c.achievements - old.achievements).coerceAtLeast(0)
        val dGoal = (c.goalDays - old.goalDays).coerceAtLeast(0)
        val mult = multiplier()
        val entries = mutableListOf<PetLog>()
        fun add(n: Long, per: Int, label: String, boost: Boolean = false): Long {
            if (n <= 0) return 0
            val xp = (n * per * if (boost) mult else 1f).toLong()
            entries += PetLog(now, label, xp.toInt())
            return xp
        }
        var gained = 0L
        gained += add(dPages, 1, "Прочитано $dPages стр.", boost = true)
        gained += add(dBooks.toLong(), 100, "Книга дочитана 🎉")
        gained += add(dNotes.toLong(), 3, "Заметки: $dNotes")
        gained += add(dWords.toLong(), 2, "Слова в словарь: $dWords")
        gained += add(dAch.toLong(), 25, "Новое достижение 🏆")
        gained += add(dGoal.toLong(), 20, "Цель дня выполнена")

        val hours = ((now - p.updatedAt) / 3_600_000f).coerceIn(0f, 24f * 14)
        val vacation = LocalDate.now() in StreakFreezeStore.frozen
        val decay = if (vacation) 0f else hours
        val hour = LocalDateTime.now().hour
        val night = hour >= 23 || hour < 7
        var food = p.food - decay * 100f / 48f + dPages * 2f + dBooks * 20f
        var mood = p.mood - decay * 100f / 72f + dNotes * 8f + dWords * 4f + dAch * 10f + dBooks * 30f + dGoal * 10f + dPages * 0.3f
        var energy = p.energy + hours * (if (night) 10f else 4f) - (if (hour in 1..4) dPages * 1.5f else 0f)
        food = food.coerceIn(0f, 100f)
        if (food < 20f) mood = mood.coerceAtMost(60f)
        mood = mood.coerceIn(0f, 100f)
        energy = energy.coerceIn(0f, 100f)

        val before = p.level
        val next = p.copy(
            xp = p.xp + gained,
            food = food,
            mood = mood,
            energy = energy,
            updatedAt = now,
            counters = PetCounters(
                maxOf(c.pages, old.pages), maxOf(c.books, old.books), maxOf(c.notes, old.notes),
                maxOf(c.words, old.words), maxOf(c.achievements, old.achievements), maxOf(c.goalDays, old.goalDays),
            ),
            log = (entries.reversed() + levelUps(before, PetLevels.level(p.xp + gained), now) + p.log).take(40),
        )
        _pet.value = next
        save()
    }

    private fun levelUps(from: Int, to: Int, now: Long): List<PetLog> {
        if (to <= from) return emptyList()
        return listOf(PetLog(now, "Уровень $to ⭐", 0))
    }

    fun mood(p: Pet, now: LocalDateTime = LocalDateTime.now()): PetMood = when {
        now.toLocalDate() in StreakFreezeStore.frozen -> PetMood.Vacation
        now.hour >= 23 || now.hour < 7 -> PetMood.Sleep
        p.energy < 25f -> PetMood.Tired
        p.food < 25f -> PetMood.Hungry
        p.mood < 30f -> PetMood.Sad
        else -> PetMood.Happy
    }

    fun look(mood: PetMood): PetLook = when (mood) {
        PetMood.Sleep -> PetLook.Sleep
        PetMood.Tired -> PetLook.Tired
        PetMood.Hungry, PetMood.Sad -> PetLook.Sad
        else -> PetLook.Happy
    }

    fun toJson(): JSONObject? = _pet.value?.toJson()

    fun restore(j: JSONObject?) {
        if (j == null) return
        val remote = runCatching { Pet.fromJson(j) }.getOrNull() ?: return
        val local = _pet.value
        if (local == null || remote.xp > local.xp) {
            _pet.value = remote
            save()
        }
    }

    private fun save() {
        val p = _pet.value ?: return
        app.appScope.launch(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.parentFile, "pet.json.tmp")
                tmp.writeText(p.toJson().toString())
                if (!tmp.renameTo(file)) file.writeText(tmp.readText())
            }
            com.flerty.wikiflerty.widget.PetWidget.refreshAll(app)
        }
    }

    private fun load(): Pet? = runCatching {
        if (file.exists()) Pet.fromJson(JSONObject(file.readText())) else null
    }.getOrNull()
}
