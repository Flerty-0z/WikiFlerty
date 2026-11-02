package com.flerty.wikiflerty.system

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class ReadingWatch(
    val bookId: String,
    val startedAt: Long,
    val accumulated: Long,
    val runningSince: Long?,
    val startProgress: Double,
) {
    val running: Boolean get() = runningSince != null
    fun elapsed(now: Long = System.currentTimeMillis()): Long = accumulated + (runningSince?.let { now - it } ?: 0L)
}

data class ReadingSessionResult(val bookId: String, val ms: Long, val startProgress: Double)

class ReadingTimer(context: Context) {
    private val prefs = context.getSharedPreferences("reading_timer", Context.MODE_PRIVATE)

    private val _watch = MutableStateFlow(load())
    val watch: StateFlow<ReadingWatch?> = _watch.asStateFlow()

    private val _result = MutableStateFlow<ReadingSessionResult?>(null)
    val result: StateFlow<ReadingSessionResult?> = _result.asStateFlow()

    fun start(bookId: String, progress: Double) {
        val now = System.currentTimeMillis()
        _result.value = null
        save(ReadingWatch(bookId, now, 0L, now, progress))
    }

    fun pause() {
        val w = _watch.value ?: return
        if (!w.running) return
        save(w.copy(accumulated = w.elapsed(), runningSince = null))
    }

    fun resume() {
        val w = _watch.value ?: return
        if (w.running) return
        save(w.copy(runningSince = System.currentTimeMillis()))
    }

    fun stop() {
        val w = _watch.value ?: return
        _result.value = ReadingSessionResult(w.bookId, w.elapsed(), w.startProgress)
        save(null)
    }

    fun cancel() = save(null)

    fun dismissResult() {
        _result.value = null
    }

    private fun save(w: ReadingWatch?) {
        _watch.value = w
        prefs.edit().apply {
            if (w == null) {
                remove("watch")
            } else {
                putString(
                    "watch",
                    JSONObject().put("book", w.bookId).put("start", w.startedAt).put("acc", w.accumulated)
                        .put("run", w.runningSince ?: 0L).put("progress", w.startProgress).toString(),
                )
            }
        }.apply()
    }

    private fun load(): ReadingWatch? = runCatching {
        val j = JSONObject(prefs.getString("watch", null) ?: return null)
        ReadingWatch(j.getString("book"), j.getLong("start"), j.getLong("acc"), j.getLong("run").takeIf { it > 0 }, j.optDouble("progress", 0.0))
    }.getOrNull()
}
