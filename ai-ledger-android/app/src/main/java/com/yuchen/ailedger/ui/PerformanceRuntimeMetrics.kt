package com.yuchen.ailedger.ui

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 低开销运行时渲染计数器。
 *
 * 仅在 StartupMetrics 启用时记录，正式运行默认完全旁路，避免为了诊断本身引入
 * Compose 状态写入、主线程回调或日志 I/O。
 */
internal object PerformanceRuntimeMetrics {
    private val assistantClockStarts = AtomicLong(0L)
    private val assistantClockStops = AtomicLong(0L)
    private val assistantClockTicks = AtomicLong(0L)
    private val assistantCompositions = AtomicLong(0L)
    private val messageBubbleCompositions = AtomicLong(0L)

    private val openGlRenderRequests = AtomicLong(0L)
    private val openGlFrames = AtomicLong(0L)
    private val openGlTextureUploads = AtomicLong(0L)
    private val openGlTextureUploadBytes = AtomicLong(0L)
    private val openGlSurfacePixels = AtomicLong(0L)
    private val openGlPeakSurfacePixels = AtomicLong(0L)
    private val openGlContextsAlive = AtomicInteger(0)
    private val openGlPeakContextsAlive = AtomicInteger(0)
    private val openGlContextsCreated = AtomicLong(0L)

    fun recordAssistantClockStart() {
        if (!StartupMetrics.isEnabled) return
        assistantClockStarts.incrementAndGet()
    }

    fun recordAssistantClockStop() {
        if (!StartupMetrics.isEnabled) return
        assistantClockStops.incrementAndGet()
    }

    fun recordAssistantClockTick() {
        if (!StartupMetrics.isEnabled) return
        assistantClockTicks.incrementAndGet()
    }

    fun recordAssistantComposition() {
        if (!StartupMetrics.isEnabled) return
        assistantCompositions.incrementAndGet()
    }

    fun recordMessageBubbleComposition() {
        if (!StartupMetrics.isEnabled) return
        messageBubbleCompositions.incrementAndGet()
    }

    fun recordOpenGlRenderRequest() {
        if (!StartupMetrics.isEnabled) return
        openGlRenderRequests.incrementAndGet()
    }

    fun recordOpenGlFrame() {
        if (!StartupMetrics.isEnabled) return
        openGlFrames.incrementAndGet()
    }

    fun recordOpenGlTextureUpload(width: Int, height: Int, bytesPerPixel: Int = 4) {
        if (!StartupMetrics.isEnabled) return
        openGlTextureUploads.incrementAndGet()
        val bytes = width.coerceAtLeast(0).toLong() *
            height.coerceAtLeast(0).toLong() *
            bytesPerPixel.coerceAtLeast(0).toLong()
        openGlTextureUploadBytes.addAndGet(bytes)
    }

    fun recordOpenGlSurface(width: Int, height: Int) {
        if (!StartupMetrics.isEnabled) return
        val pixels = width.coerceAtLeast(0).toLong() * height.coerceAtLeast(0).toLong()
        openGlSurfacePixels.set(pixels)
        updatePeak(openGlPeakSurfacePixels, pixels)
    }

    fun recordOpenGlContextCreated() {
        if (!StartupMetrics.isEnabled) return
        openGlContextsCreated.incrementAndGet()
        val alive = openGlContextsAlive.incrementAndGet()
        updatePeak(openGlPeakContextsAlive, alive)
    }

    fun recordOpenGlContextReleased() {
        if (!StartupMetrics.isEnabled) return
        while (true) {
            val current = openGlContextsAlive.get()
            if (current <= 0) return
            if (openGlContextsAlive.compareAndSet(current, current - 1)) return
        }
    }

    fun snapshot(): PerformanceRuntimeSnapshot = PerformanceRuntimeSnapshot(
        assistantClockStarts = assistantClockStarts.get(),
        assistantClockStops = assistantClockStops.get(),
        assistantClockTicks = assistantClockTicks.get(),
        assistantCompositions = assistantCompositions.get(),
        messageBubbleCompositions = messageBubbleCompositions.get(),
        openGlRenderRequests = openGlRenderRequests.get(),
        openGlFrames = openGlFrames.get(),
        openGlTextureUploads = openGlTextureUploads.get(),
        openGlTextureUploadBytes = openGlTextureUploadBytes.get(),
        openGlSurfacePixels = openGlSurfacePixels.get(),
        openGlPeakSurfacePixels = openGlPeakSurfacePixels.get(),
        openGlContextsAlive = openGlContextsAlive.get(),
        openGlPeakContextsAlive = openGlPeakContextsAlive.get(),
        openGlContextsCreated = openGlContextsCreated.get()
    )

    fun reset() {
        assistantClockStarts.set(0L)
        assistantClockStops.set(0L)
        assistantClockTicks.set(0L)
        assistantCompositions.set(0L)
        messageBubbleCompositions.set(0L)
        openGlRenderRequests.set(0L)
        openGlFrames.set(0L)
        openGlTextureUploads.set(0L)
        openGlTextureUploadBytes.set(0L)
        openGlSurfacePixels.set(0L)
        openGlPeakSurfacePixels.set(0L)
        val aliveContexts = openGlContextsAlive.get()
        openGlPeakContextsAlive.set(aliveContexts)
        openGlContextsCreated.set(0L)
    }

    private fun updatePeak(target: AtomicLong, value: Long) {
        var current = target.get()
        while (value > current && !target.compareAndSet(current, value)) {
            current = target.get()
        }
    }

    private fun updatePeak(target: AtomicInteger, value: Int) {
        var current = target.get()
        while (value > current && !target.compareAndSet(current, value)) {
            current = target.get()
        }
    }
}

internal data class PerformanceRuntimeSnapshot(
    val assistantClockStarts: Long,
    val assistantClockStops: Long,
    val assistantClockTicks: Long,
    val assistantCompositions: Long,
    val messageBubbleCompositions: Long,
    val openGlRenderRequests: Long,
    val openGlFrames: Long,
    val openGlTextureUploads: Long,
    val openGlTextureUploadBytes: Long,
    val openGlSurfacePixels: Long,
    val openGlPeakSurfacePixels: Long,
    val openGlContextsAlive: Int,
    val openGlPeakContextsAlive: Int,
    val openGlContextsCreated: Long
) {
    fun assistantLabel(): String =
        "助手时钟 $assistantClockStarts/$assistantClockStops · tick $assistantClockTicks · 重组 $assistantCompositions/$messageBubbleCompositions"

    fun openGlLabel(): String {
        val uploadMiB = openGlTextureUploadBytes / (1024f * 1024f)
        val surfaceKpx = openGlSurfacePixels / 1000f
        val peakSurfaceKpx = openGlPeakSurfacePixels / 1000f
        return buildString {
            append("OpenGL 请求/帧 $openGlRenderRequests/$openGlFrames")
            append(" · 上传 $openGlTextureUploads 次 ${formatOneDecimal(uploadMiB)} MiB")
            append(" · Surface ${formatOneDecimal(surfaceKpx)}/${formatOneDecimal(peakSurfaceKpx)} Kpx")
            append(" · Context $openGlContextsAlive/$openGlPeakContextsAlive/$openGlContextsCreated")
        }
    }
}

private fun formatOneDecimal(value: Float): String = ((value * 10f).toInt() / 10f).toString()
