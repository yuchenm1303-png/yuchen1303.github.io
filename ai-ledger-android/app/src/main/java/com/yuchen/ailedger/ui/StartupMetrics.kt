package com.yuchen.ailedger.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlin.math.abs

private const val FRAME_STATS_PUBLISH_INTERVAL_MS = 1000L
private const val FRAME_STATS_FORCE_PUBLISH_EVERY_N_FRAMES = 120
private const val FPS_CHANGE_THRESHOLD = 0.7f
private const val FRAME_MS_CHANGE_THRESHOLD = 0.4f

object StartupMetrics {
    @Volatile
    private var enabled = false

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val startMs = SystemClock.elapsedRealtime()
    private val eventNames = linkedSetOf<String>()
    private val _events = mutableStateListOf<StartupMetricEvent>()
    private var frameMonitorStarted = false
    private var lastFrameNanos = 0L
    private var frameCount = 0
    private var longFrameCount = 0
    private var jankFrameCount = 0
    private var totalFrameMs = 0f
    private var maxFrameMs = 0f
    private var fpsWindowStartMs = 0L
    private var fpsWindowFrames = 0
    private var recentFps = 0f
    private val _frameStats = mutableStateOf(StartupFrameStats())
    private val _warmupState = mutableStateOf("首页加载中")

    val events: List<StartupMetricEvent> get() = _events
    val frameStats: StartupFrameStats get() = _frameStats.value
    val warmupState: String get() = _warmupState.value

    fun configure(enabled: Boolean) {
        this.enabled = enabled
    }

    fun mark(name: String) {
        if (!enabled) return
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
        if (!enabled) return
        synchronized(eventNames) {
            if (!eventNames.add(name)) return
        }
        mark(name)
    }

    fun setWarmupState(state: String) {
        if (!enabled) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (_warmupState.value != state) {
                _warmupState.value = state
                mark("预热状态：$state")
            }
        } else {
            mainHandler.post { setWarmupState(state) }
        }
    }

    fun startFrameMonitor() {
        if (!enabled) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startFrameMonitor() }
            return
        }
        if (frameMonitorStarted) return
        frameMonitorStarted = true
        fpsWindowStartMs = SystemClock.elapsedRealtime()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun resetFrameStats() {
        if (!enabled) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { resetFrameStats() }
            return
        }
        lastFrameNanos = 0L
        frameCount = 0
        longFrameCount = 0
        jankFrameCount = 0
        totalFrameMs = 0f
        maxFrameMs = 0f
        fpsWindowStartMs = SystemClock.elapsedRealtime()
        fpsWindowFrames = 0
        recentFps = 0f
        _frameStats.value = StartupFrameStats()
        mark("帧统计已重置")
    }

    fun resetForNewRun() {
        if (!enabled) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            synchronized(eventNames) { eventNames.clear() }
            _events.clear()
            _warmupState.value = "手动重置"
            resetFrameStats()
            mark("手动重置")
        } else {
            mainHandler.post { resetForNewRun() }
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!enabled) {
                frameMonitorStarted = false
                return
            }
            if (lastFrameNanos != 0L) {
                val frameMs = (frameTimeNanos - lastFrameNanos) / 1_000_000f
                if (frameMs in 0.1f..1000f) {
                    frameCount += 1
                    fpsWindowFrames += 1
                    totalFrameMs += frameMs
                    if (frameMs > maxFrameMs) maxFrameMs = frameMs
                    if (frameMs >= 24f) longFrameCount += 1
                    if (frameMs >= 33f) jankFrameCount += 1
                    val now = SystemClock.elapsedRealtime()
                    if (now - fpsWindowStartMs >= FRAME_STATS_PUBLISH_INTERVAL_MS) {
                        recentFps = fpsWindowFrames * 1000f / (now - fpsWindowStartMs).coerceAtLeast(1L)
                        fpsWindowStartMs = now
                        fpsWindowFrames = 0
                        publishFrameStats(force = false)
                    } else if (frameCount % FRAME_STATS_FORCE_PUBLISH_EVERY_N_FRAMES == 0) {
                        publishFrameStats(force = true)
                    }
                }
            }
            lastFrameNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun publishFrameStats(force: Boolean) {
        if (!enabled) return
        val avg = if (frameCount > 0) totalFrameMs / frameCount else 0f
        val next = StartupFrameStats(
            frameCount = frameCount,
            currentFps = recentFps,
            avgFrameMs = avg,
            maxFrameMs = maxFrameMs,
            longFrameCount = longFrameCount,
            jankFrameCount = jankFrameCount
        )
        if (force || next.shouldPublishOver(_frameStats.value)) {
            _frameStats.value = next
        }
    }

    private fun appendEvent(event: StartupMetricEvent) {
        if (!enabled) return
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

data class StartupFrameStats(
    val frameCount: Int = 0,
    val currentFps: Float = 0f,
    val avgFrameMs: Float = 0f,
    val maxFrameMs: Float = 0f,
    val longFrameCount: Int = 0,
    val jankFrameCount: Int = 0
) {
    fun compactLabel(): String {
        val fps = (currentFps * 10).toInt() / 10f
        val avg = (avgFrameMs * 10).toInt() / 10f
        val max = (maxFrameMs * 10).toInt() / 10f
        return "FPS ${fps} / 帧 ${avg}ms / max ${max}ms / 长帧 $longFrameCount / 卡顿 $jankFrameCount"
    }

    fun shouldPublishOver(previous: StartupFrameStats): Boolean {
        return frameCount == 0 ||
            previous.frameCount == 0 ||
            abs(currentFps - previous.currentFps) >= FPS_CHANGE_THRESHOLD ||
            abs(avgFrameMs - previous.avgFrameMs) >= FRAME_MS_CHANGE_THRESHOLD ||
            abs(maxFrameMs - previous.maxFrameMs) >= FRAME_MS_CHANGE_THRESHOLD ||
            longFrameCount != previous.longFrameCount ||
            jankFrameCount != previous.jankFrameCount
    }
}
