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

data class Freeze(val start: LocalDate, val days: Int, val createdAt: Long, val cancelled: Boolean = false) {
    val end: LocalDate get() = start.plusDays(days - 1L)
    val createdDay: LocalDate get() = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()

    fun covers(day: LocalDate) = !day.isBefore(start) && !day.isAfter(end)

    fun toJson(): JSONObject = JSONObject().put("start", start.toString()).put("days", days).put("createdAt", createdAt).put("cancelled", cancelled)

    companion object {
        fun fromJson(j: JSONObject): Freeze? = runCatching {
            Freeze(LocalDate.parse(j.getString("start")), j.getInt("days").coerceIn(1, StreakFreezeStore.MAX_DAYS), j.getLong("createdAt"), j.optBoolean("cancelled", false))
        }.getOrNull()
    }
}

class StreakFreezeStore(context: Context) {
    private val file = File(context.filesDir, "freezes.json")
    private val prefs = context.getSharedPreferences("freezes", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(load())
    private val _freezes = MutableStateFlow(_all.value.filter { !it.cancelled })
    val freezes: StateFlow<List<Freeze>> = _freezes.asStateFlow()

    init {
        frozen = days(_freezes.value)
    }

    fun last(): Freeze? = _all.value.maxByOrNull { it.createdAt }

    fun cooldownLeft(today: LocalDate = LocalDate.now()): Int {
        val used = maxOf(last()?.createdAt ?: 0L, prefs.getLong("used_at", 0L))
        if (used <= 0L) return 0
        val day = Instant.ofEpochMilli(used).atZone(ZoneId.systemDefault()).toLocalDate()
        return (COOLDOWN_DAYS - ChronoUnit.DAYS.between(day, today)).toInt().coerceAtLeast(0)
    }

    fun available(today: LocalDate = LocalDate.now()) = cooldownLeft(today) == 0

    @Synchronized
    fun add(start: LocalDate, days: Int): Boolean {
        if (!available()) return false
        val now = System.currentTimeMillis()
        prefs.edit().putLong("used_at", now).apply()
        set(_all.value + Freeze(start, days.coerceIn(1, MAX_DAYS), now))
        return true
    }

    @Synchronized
    fun cancel(freeze: Freeze) {
        set(_all.value.map { if (it == freeze) it.copy(cancelled = true) else it })
    }

    @Synchronized
    fun unfreeze(today: LocalDate = LocalDate.now()) {
        val current = _all.value.filter { !it.cancelled && !it.end.isBefore(today) }
        if (current.isEmpty()) return
        set(_all.value.map { if (it in current) it.copy(cancelled = true) else it })
    }

    var rescueDismissed: String?
        get() = prefs.getString("rescue_dismissed", null)
        set(value) = prefs.edit().putString("rescue_dismissed", value).apply()

    fun toJson(): JSONArray = JSONArray().apply { _all.value.forEach { put(it.toJson()) } }

    @Synchronized
    fun merge(arr: JSONArray?): Boolean {
        if (arr == null) return false
        val incoming = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Freeze::fromJson) }
        val byKey = LinkedHashMap<Pair<Long, LocalDate>, Freeze>()
        (_all.value + incoming).forEach { f ->
            val key = f.createdAt to f.start
            val prev = byKey[key]
            byKey[key] = if (prev == null) f else prev.copy(cancelled = prev.cancelled || f.cancelled)
        }
        val merged = byKey.values.toList()
        if (merged.toSet() == _all.value.toSet()) return false
        set(merged)
        return true
    }

    private fun set(list: List<Freeze>) {
        val sorted = list.sortedBy { it.start }
        _all.value = sorted
        _freezes.value = sorted.filter { !it.cancelled }
        frozen = days(_freezes.value)
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
            list.filter { !it.cancelled }.flatMap { f -> (0 until f.days).map { f.start.plusDays(it.toLong()) } }.toSet()
    }
}
