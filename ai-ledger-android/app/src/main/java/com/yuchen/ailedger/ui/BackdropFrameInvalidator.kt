package com.yuchen.ailedger.ui

import android.view.Choreographer

private const val SCROLL_BACKDROP_MIN_INTERVAL_NANOS = 12_000_000L

/**
 * Scroll/backdrop invalidation gate used by the root nested-scroll connection.
 *
 * The old pure-VSYNC coalescer removed duplicate requests inside one frame, but on
 * 90Hz devices it could still dispatch almost every display frame. That made list
 * scrolling compete with OpenGL/backdrop sampling. This gate keeps the useful part
 * of the VSYNC coalescer while restoring a minimum dispatch interval for normal
 * scroll traffic. Forced requests, such as fling boundaries, still flush on the
 * next VSYNC so geometry does not get stuck.
 */
internal class BackdropFrameInvalidator(
    private val ticker: BackdropFrameTicker,
    private val scrollMinIntervalNanos: Long = SCROLL_BACKDROP_MIN_INTERVAL_NANOS
) {
    private val choreographer = Choreographer.getInstance()
    private var frameScheduled = false
    private var normalPending = false
    private var forcePending = false
    private var disposed = false
    private var lastDispatchFrameNanos = 0L

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        frameScheduled = false
        if (disposed) return@FrameCallback
        if (!forcePending && !normalPending) return@FrameCallback

        val force = forcePending
        val elapsedNanos = if (lastDispatchFrameNanos == 0L) {
            Long.MAX_VALUE
        } else {
            frameTimeNanos - lastDispatchFrameNanos
        }
        val canDispatchNormal = elapsedNanos >= scrollMinIntervalNanos

        if (force || canDispatchNormal) {
            forcePending = false
            normalPending = false
            lastDispatchFrameNanos = frameTimeNanos
            ticker.requestFrame(force = force)
        } else {
            scheduleFrame()
        }
    }

    fun request(force: Boolean = false) {
        if (disposed) return
        if (force) {
            forcePending = true
        } else {
            normalPending = true
        }
        scheduleFrame()
    }

    fun dispose() {
        disposed = true
        choreographer.removeFrameCallback(frameCallback)
        frameScheduled = false
        normalPending = false
        forcePending = false
    }

    private fun scheduleFrame() {
        if (!frameScheduled && !disposed) {
            frameScheduled = true
            choreographer.postFrameCallback(frameCallback)
        }
    }
}
