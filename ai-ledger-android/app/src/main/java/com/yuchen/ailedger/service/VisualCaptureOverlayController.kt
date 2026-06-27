package com.yuchen.ailedger.service

import android.os.Handler
import android.os.Looper

interface VisualCaptureOverlayController {
    fun beginCapture()
    fun endCapture()
}

object RuntimeVisualCaptureOverlayController : VisualCaptureOverlayController {
    private val releaseHandler = Handler(Looper.getMainLooper())

    override fun beginCapture() {
        AgentRuntimeController.beginCleanVisualCapture()
    }

    override fun endCapture() {
        releaseHandler.postDelayed(
            { AgentRuntimeController.endCleanVisualCapture() },
            VISUAL_CAPTURE_BURST_GRACE_MS,
        )
    }

    private const val VISUAL_CAPTURE_BURST_GRACE_MS = 120L
}
