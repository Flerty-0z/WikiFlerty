package com.flerty.wikiflerty.data

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Freeze(val start: LocalDate, val days: Int, val createdAt: Long) {
    val end: LocalDate get() = start.plusDays(days - 1L)
    val createdDay: LocalDate get() = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()

    fun covers(day: LocalDate) = !day.isBefore(start) && !day.isAfter(end)

    fun toJson(): JSONObject = JSONObject().put("start", start.toString()).put("days", days).put("createdAt", createdAt)

    companion object {
        fun fromJson(j: JSONObject): Freeze? = runCatching {
            Freeze(LocalDate.parse(j.getString("start")), j.getInt("days").coerceIn(1, StreakFreezeStore.MAX_DAYS), j.getLong("createdAt"))
        }.getOrNull()
    }
}

class StreakFreezeStore(context: Context) {
    private val file = File(context.filesDir, "freezes.json")
    private val prefs = context.getSharedPreferences("freezes", Context.MODE_PRIVATE)
    private val _freezes = MutableStateFlow(load())
    val freezes: StateFlow<List<Freeze>> = _freezes.asStateFlow()

    init {
        frozen = days(_freezes.value)
    }

    fun last(): Freeze? = _freezes.value.maxByOrNull { it.createdAt }

    fun cooldownLeft(today: LocalDate = LocalDate.now()): Int {
        val last = last() ?: return 0
        return (COOLDOWN_DAYS - ChronoUnit.DAYS.between(last.createdDay, today)).toInt().coerceAtLeast(0)
    }

    fun available(today: LocalDate = LocalDate.now()) = cooldownLeft(today) == 0

    @Synchronized
    fun add(start: LocalDate, days: Int): Boolean {
        if (!available()) return false
        set(_freezes.value + Freeze(start, days.coerceIn(1, MAX_DAYS), System.currentTimeMillis()))
        return true
    }

    @Synchronized
    fun cancel(freeze: Freeze) {
        if (!freeze.start.isAfter(LocalDate.now())) return
        set(_freezes.value - freeze)
    }

    var rescueDismissed: String?
        get() = prefs.getString("rescue_dismissed", null)
        set(value) = prefs.edit().putString("rescue_dismissed", value).apply()

    fun toJson(): JSONArray = JSONArray().apply { _freezes.value.forEach { put(it.toJson()) } }

    @Synchronized
    fun merge(arr: JSONArray?): Boolean {
        if (arr == null) return false
        val incoming = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Freeze::fromJson) }
        val merged = (_freezes.value + incoming).distinctBy { it.createdAt to it.start }
        if (merged.size == _freezes.value.size) return false
        set(merged)
        return true
    }

    private fun set(list: List<Freeze>) {
        val sorted = list.sortedBy { it.start }
        _freezes.value = sorted
        frozen = days(sorted)
        runCatching { file.writeText(JSONArray().apply { sorted.forEach { put(it.toJson()) } }.toString()) }
    }

    private fun load(): List<Freeze> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Freeze::fromJson) }
    }.getOrElse { emptyList() }

    companion object {
        const val MAX_DAYS = 7
        const val COOLDOWN_DAYS = 30L

        @Volatile
        var frozen: Set<LocalDate> = emptySet()
            private set

        fun days(list: List<Freeze>): Set<LocalDate> =
            list.flatMap { f -> (0 until f.days).map { f.start.plusDays(it.toLong()) } }.toSet()
    }
}
