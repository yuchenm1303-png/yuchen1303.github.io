package com.yuchen.ailedger.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableStateListOf

object StartupMetrics {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val startMs = SystemClock.elapsedRealtime()
    private val eventNames = linkedSetOf<String>()
    private val _events = mutableStateListOf<StartupMetricEvent>()

    val events: List<StartupMetricEvent> get() = _events

    fun mark(name: String) {
        val now = SystemClock.elapsedRealtime()
        val event = StartupMetricEvent(
            name = name,
            elapsedMs = now - startMs,
            deltaMs = if (_events.isEmpty()) now - startMs else now - startMs - _events.last().elapsedMs
        )
        if (Looper.myLooper() == Looper.getMainLooper()) {
            appendEvent(event)
        } else {
            mainHandler.post { appendEvent(event) }
        }
    }

    fun markOnce(name: String) {
        synchronized(eventNames) {
            if (!eventNames.add(name)) return
        }
        mark(name)
    }

    fun resetForNewRun() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            eventNames.clear()
            _events.clear()
            mark("手动重置")
        } else {
            mainHandler.post { resetForNewRun() }
        }
    }

    private fun appendEvent(event: StartupMetricEvent) {
        _events.add(event)
        if (_events.size > 48) _events.removeAt(0)
    }
}

data class StartupMetricEvent(
    val name: String,
    val elapsedMs: Long,
    val deltaMs: Long
) {
    fun compactLabel(): String = "+${deltaMs}ms / ${elapsedMs}ms"
}
