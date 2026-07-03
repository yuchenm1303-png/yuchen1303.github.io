package com.yuchen.ailedger.service

import android.util.Base64
import com.yuchen.ailedger.data.VisualDemonstrationSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 只从权威屏幕截图中采样演示关键帧，不读取、不保存、也不解释无障碍节点。
 * 本地不推断步骤；整段视觉证据在结束后交给云端生成 Skill。
 */
class VisualDemonstrationRecorder(
    private val session: VisualDemonstrationSession,
    private val allowedPackages: Set<String>,
    private val onFrameCountChanged: (Int) -> Unit,
) {
    @Volatile
    private var stopped = false

    suspend fun runCaptureLoop() {
        captureFrame()
        while (!stopped && session.frameCount < VisualDemonstrationSession.MAX_FRAMES - FINAL_FRAME_RESERVE) {
            delay(CAPTURE_INTERVAL_MS)
            if (!stopped) captureFrame()
        }
    }

    suspend fun captureFinalFrame() = withContext(Dispatchers.Default) {
        if (!stopped) captureFrame()
        Unit
    }

    fun stop() {
        stopped = true
    }

    private fun captureFrame(): Boolean {
        val observation = runCatching {
            AiAgentAccessibilityService.captureFreshSnapshot(forceVisual = true)
        }.getOrNull() ?: return false
        val snapshot = observation.toAgentScreenSnapshot()
        val visual = snapshot.visual?.takeIf { it.hasImage } ?: return false
        val packageName = snapshot.packageName.trim()
        if (packageName.isBlank() || packageName !in allowedPackages) return false
        val bytes = runCatching { Base64.decode(visual.base64Jpeg, Base64.DEFAULT) }.getOrNull()
            ?: return false
        val appended = runCatching {
            session.appendFrame(
                capturedAtMillis = System.currentTimeMillis(),
                packageName = packageName,
                mimeType = visual.mimeType.ifBlank { "image/jpeg" },
                width = visual.width,
                height = visual.height,
                displayWidth = visual.displayWidth,
                displayHeight = visual.displayHeight,
                bytes = bytes,
            )
        }.getOrDefault(false)
        if (appended) onFrameCountChanged(session.frameCount)
        return appended
    }

    companion object {
        private const val FINAL_FRAME_RESERVE = 1
        private const val CAPTURE_INTERVAL_MS = 2_250L
    }
}
