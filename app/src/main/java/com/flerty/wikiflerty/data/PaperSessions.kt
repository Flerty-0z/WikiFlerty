package com.flerty.wikiflerty.data

import com.flerty.wikiflerty.App
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.ceil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class PaperSession(
    val id: String,
    val book: String,
    val fromPage: Int,
    val toPage: Int,
    val startAt: Long,
    val durationMs: Long,
    val note: String = "",
) {
    val pages: Int get() = (toPage - fromPage).coerceAtLeast(0)
    val day: LocalDate get() = Instant.ofEpochMilli(startAt).atZone(ZoneId.systemDefault()).toLocalDate()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("book", book).put("from", fromPage).put("to", toPage)
        .put("start", startAt).put("ms", durationMs).put("note", note)

    companion object {
        fun fromJson(j: JSONObject) = PaperSession(
            j.getString("id"), j.getString("book"), j.optInt("from"), j.optInt("to"),
            j.optLong("start"), j.optLong("ms"), j.optString("note"),
        )
    }
}

data class PaperPace(
    val pagesPerHour: Double?,
    val totalMs: Long,
    val pagesRead: Int,
    val leftPages: Int,
    val leftMs: Long?,
    val leftDays: Int?,
    val finishDate: LocalDate?,
)

data class Stopwatch(val bookId: String, val startedAt: Long, val accumulated: Long, val runningSince: Long?) {
    fun elapsed(now: Long = System.currentTimeMillis()): Long = accumulated + (runningSince?.let { now - it } ?: 0L)
    val running: Boolean get() = runningSince != null
}

class PaperSessionStore(private val app: App) {
    private val file = File(app.filesDir, "paper_sessions.json")
    private val prefs = app.getSharedPreferences("paper", android.content.Context.MODE_PRIVATE)
    private val _sessions = MutableStateFlow(load())
    val sessions: StateFlow<List<PaperSession>> = _sessions.asStateFlow()

    private val _stopwatch = MutableStateFlow(loadStopwatch())
    val stopwatch: StateFlow<Stopwatch?> = _stopwatch.asStateFlow()

    fun of(book: Book): List<PaperSession> = _sessions.value.filter { it.book == book.statsKey }.sortedByDescending { it.startAt }

    fun currentPage(book: Book): Int = _sessions.value.filter { it.book == book.statsKey }.maxOfOrNull { it.toPage } ?: 0

    fun add(book: Book, fromPage: Int, toPage: Int, startAt: Long, durationMs: Long, note: String) {
        val s = PaperSession(UUID.randomUUID().toString(), book.statsKey, fromPage, toPage, startAt, durationMs.coerceAtLeast(0), note.trim())
        set(_sessions.value + s)
        app.stats.adjust(s.day, s.book, s.durationMs, s.pages)
        sync(book)
    }

    fun update(book: Book, session: PaperSession) {
        val old = _sessions.value.firstOrNull { it.id == session.id } ?: return
        set(_sessions.value.map { if (it.id == session.id) session else it })
        app.stats.adjust(old.day, old.book, -old.durationMs, -old.pages)
        app.stats.adjust(session.day, session.book, session.durationMs, session.pages)
        sync(book)
    }

    fun delete(book: Book, session: PaperSession) {
        set(_sessions.value.filterNot { it.id == session.id })
        app.stats.adjust(session.day, session.book, -session.durationMs, -session.pages)
        sync(book)
    }

    fun removeBook(book: Book) {
        val gone = _sessions.value.filter { it.book == book.statsKey }
        if (gone.isEmpty()) return
        set(_sessions.value - gone.toSet())
        if (_stopwatch.value?.bookId == book.id) stopStopwatch()
    }

    private fun sync(book: Book) {
        val page = currentPage(book)
        app.appScope.launch { app.library.setPaperPage(book.id, page) }
    }

    fun pace(book: Book, today: LocalDate = LocalDate.now()): PaperPace {
        val list = _sessions.value.filter { it.book == book.statsKey }.sortedBy { it.startAt }
        val timed = list.filter { it.durationMs >= 60_000 && it.pages > 0 }.takeLast(8)
        var weight = 0.0
        var sum = 0.0
        timed.forEachIndexed { i, s ->
            val w = (i + 1).toDouble()
            sum += w * s.pages / (s.durationMs / 3_600_000.0)
            weight += w
        }
        val speed = if (weight > 0) sum / weight else null
        val total = book.positions.coerceAtLeast(1)
        val current = list.maxOfOrNull { it.toPage } ?: 0
        val left = (total - current).coerceAtLeast(0)
        val leftMs = speed?.takeIf { it > 0 }?.let { (left / it * 3_600_000).toLong() }
        val days = list.map { it.day }.distinct().size
        val totalMs = list.sumOf { it.durationMs }
        val perDay = if (days > 0) totalMs / days else 0L
        val leftDays = if (leftMs != null && perDay >= 60_000) ceil(leftMs.toDouble() / perDay).toInt().coerceAtLeast(if (left > 0) 1 else 0) else null
        return PaperPace(
            pagesPerHour = speed,
            totalMs = totalMs,
            pagesRead = current,
            leftPages = left,
            leftMs = leftMs,
            leftDays = leftDays,
            finishDate = leftDays?.takeIf { left > 0 }?.let { today.plusDays(it.toLong()) },
        )
    }

    fun startStopwatch(bookId: String) {
        val now = System.currentTimeMillis()
        val current = _stopwatch.value
        if (current != null && current.bookId == bookId) {
            if (!current.running) setStopwatch(current.copy(runningSince = now))
            return
        }
        setStopwatch(Stopwatch(bookId, now, 0, now))
    }

    fun pauseStopwatch() {
        val s = _stopwatch.value ?: return
        if (!s.running) return
        setStopwatch(s.copy(accumulated = s.elapsed(), runningSince = null))
    }

    fun stopStopwatch() = setStopwatch(null)

    private fun setStopwatch(s: Stopwatch?) {
        _stopwatch.value = s
        prefs.edit().apply {
            if (s == null) {
                remove("sw")
            } else {
                putString("sw", JSONObject().put("book", s.bookId).put("start", s.startedAt).put("acc", s.accumulated).put("run", s.runningSince ?: 0L).toString())
            }
        }.apply()
    }

    private fun loadStopwatch(): Stopwatch? = runCatching {
        val j = JSONObject(prefs.getString("sw", null) ?: return null)
        Stopwatch(j.getString("book"), j.getLong("start"), j.getLong("acc"), j.getLong("run").takeIf { it > 0 })
    }.getOrNull()

    fun toJson(): JSONArray = JSONArray().apply { _sessions.value.forEach { put(it.toJson()) } }

    fun merge(arr: JSONArray?) {
        if (arr == null) return
        val mine = _sessions.value.associateBy { it.id }
        val incoming = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { j -> runCatching { PaperSession.fromJson(j) }.getOrNull() } }
            .filter { it.id !in mine }
        if (incoming.isNotEmpty()) set(_sessions.value + incoming)
    }

    private fun set(list: List<PaperSession>) {
        _sessions.value = list
        runCatching {
            val tmp = File(file.parentFile, "paper_sessions.json.tmp")
            tmp.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
            if (!tmp.renameTo(file)) file.writeText(tmp.readText())
        }
    }

    private fun load(): List<PaperSession> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { j -> runCatching { PaperSession.fromJson(j) }.getOrNull() } }
    }.getOrElse { emptyList() }
}
