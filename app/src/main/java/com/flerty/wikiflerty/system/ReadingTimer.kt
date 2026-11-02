package com.flerty.wikiflerty.system

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReadingSessionResult(val bookId: String, val ms: Long, val startProgress: Double)

class ReadingTimer(context: Context) {
    private val prefs = context.getSharedPreferences("reading_timer", Context.MODE_PRIVATE)
    private val _armed = MutableStateFlow(prefs.getBoolean("armed", false))
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    private val _result = MutableStateFlow<ReadingSessionResult?>(null)
    val result: StateFlow<ReadingSessionResult?> = _result.asStateFlow()

    private var bookId: String? = null
    private var startedAt = 0L
    private var startProgress = 0.0

    fun setArmed(value: Boolean) {
        _armed.value = value
        prefs.edit().putBoolean("armed", value).apply()
    }

    fun begin(id: String, progress: Double) {
        if (!_armed.value) {
            bookId = null
            return
        }
        bookId = id
        startedAt = System.currentTimeMillis()
        startProgress = progress
    }

    fun end() {
        val id = bookId ?: return
        bookId = null
        val ms = System.currentTimeMillis() - startedAt
        if (ms >= 10_000) _result.value = ReadingSessionResult(id, ms, startProgress)
    }

    fun dismissResult() {
        _result.value = null
    }
}
