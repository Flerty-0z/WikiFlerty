package com.flerty.wikiflerty.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReadingTimerState(val minutes: Int, val endsAt: Long, val finished: Boolean = false)

class ReadingTimer {
    private val _state = MutableStateFlow<ReadingTimerState?>(null)
    val state: StateFlow<ReadingTimerState?> = _state.asStateFlow()

    fun start(minutes: Int) {
        _state.value = ReadingTimerState(minutes, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun extend(minutes: Int) {
        val s = _state.value ?: return start(minutes)
        val base = maxOf(System.currentTimeMillis(), s.endsAt)
        _state.value = s.copy(minutes = s.minutes + minutes, endsAt = base + minutes * 60_000L, finished = false)
    }

    fun finish() {
        _state.value = _state.value?.copy(finished = true)
    }

    fun stop() {
        _state.value = null
    }
}
