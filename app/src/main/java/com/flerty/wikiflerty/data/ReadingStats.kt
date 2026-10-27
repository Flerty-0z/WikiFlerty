package com.flerty.wikiflerty.data

import android.content.Context
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Reading done on one day in one book. */
data class DayEntry(val ms: Long = 0, val pages: Int = 0) {
    operator fun plus(o: DayEntry) = DayEntry(ms + o.ms, pages + o.pages)
}

/**
 * How long and how much was read, day by day and book by book.
 * Saved to stats.json as { day: { bookKey: {ms, pages} } }, where the book key is
 * the book's fingerprint, so the history stays when a book is removed and comes back.
 */
class ReadingStatsStore(context: Context) {
    private val file = File(context.filesDir, "stats.json")
    private val _days = MutableStateFlow(load())
    val days: StateFlow<Map<String, Map<String, DayEntry>>> = _days.asStateFlow()
    private var dirty = false

    @Synchronized
    fun add(bookKey: String, ms: Long, pages: Int) {
        if (ms <= 0 && pages <= 0) return
        val today = LocalDate.now().toString()
        val day = _days.value[today].orEmpty()
        val entry = (day[bookKey] ?: DayEntry()) + DayEntry(ms.coerceAtLeast(0), pages.coerceAtLeast(0))
        _days.value = _days.value + (today to (day + (bookKey to entry)))
        dirty = true
    }

    /** Writes to disk if something changed. Call from a background thread. */
    @Synchronized
    fun save() {
        if (!dirty) return
        dirty = false
        runCatching {
            val tmp = File(file.parentFile, "stats.json.tmp")
            tmp.writeText(toJson().toString())
            if (!tmp.renameTo(file)) file.writeText(tmp.readText()).also { tmp.delete() }
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        _days.value.forEach { (day, books) ->
            put(day, JSONObject().apply {
                books.forEach { (key, e) -> put(key, JSONObject().put("ms", e.ms).put("pages", e.pages)) }
            })
        }
    }

    /** Merges stats from a backup: for each day and book the bigger record wins, so importing twice changes nothing. */
    @Synchronized
    fun merge(j: JSONObject?): Boolean {
        if (j == null) return false
        val result = _days.value.mapValues { it.value.toMutableMap() }.toMutableMap()
        var changed = false
        parse(j).forEach { (day, books) ->
            val mine = result.getOrPut(day) { mutableMapOf() }
            books.forEach { (key, e) ->
                val old = mine[key]
                if (old == null || e.ms > old.ms) {
                    mine[key] = DayEntry(maxOf(e.ms, old?.ms ?: 0), maxOf(e.pages, old?.pages ?: 0))
                    changed = true
                }
            }
        }
        if (changed) {
            _days.value = result
            dirty = true
            save()
        }
        return changed
    }

    private fun load(): Map<String, Map<String, DayEntry>> = runCatching {
        if (file.exists()) parse(JSONObject(file.readText())) else emptyMap()
    }.getOrElse { emptyMap() }

    private fun parse(j: JSONObject): Map<String, Map<String, DayEntry>> =
        j.keys().asSequence().associateWith { day ->
            val books = j.optJSONObject(day) ?: return@associateWith emptyMap()
            books.keys().asSequence().associateWith { key ->
                val e = books.optJSONObject(key)
                DayEntry(e?.optLong("ms") ?: 0, e?.optInt("pages") ?: 0)
            }
        }
}
