package com.yuchen.ailedger.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface VisualObservationCaptureSource {
    suspend fun capture(forceVisual: Boolean): ScreenObservation
}

object AccessibilityVisualObservationCaptureSource : VisualObservationCaptureSource {
    override suspend fun capture(forceVisual: Boolean): ScreenObservation {
        return withContext(Dispatchers.Default) {
            AiAgentAccessibilityService.captureFreshSnapshot(forceVisual = forceVisual)
        }
    }
}
