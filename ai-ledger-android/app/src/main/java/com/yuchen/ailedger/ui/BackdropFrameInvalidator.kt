package com.yuchen.ailedger.ui

import android.view.Choreographer

/**
 * Coalesces many backdrop invalidation requests into the next VSYNC.
 *
 * This keeps scroll/layout bursts from calling BackdropFrameTicker.requestFrame()
 * repeatedly inside one display frame. It does not change any visual parameter;
 * it only changes when repeated requests are flushed.
 */
internal class BackdropFrameInvalidator(
    private val ticker: BackdropFrameTicker
) {
    private val choreographer = Choreographer.getInstance()
    private var frameScheduled = false
    private var forcePending = false

    private val frameCallback = Choreographer.FrameCallback {
        val force = forcePending
        forcePending = false
        frameScheduled = false
        ticker.requestFrame(force = force)
    }

    fun request(force: Boolean = false) {
        if (force) forcePending = true
        if (!frameScheduled) {
            frameScheduled = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    fun dispose() {
        choreographer.removeFrameCallback(frameCallback)
        frameScheduled = false
        forcePending = false
    }
}
