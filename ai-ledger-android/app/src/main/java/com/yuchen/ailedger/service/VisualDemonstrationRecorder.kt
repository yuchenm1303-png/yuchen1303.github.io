package com.yuchen.ailedger.service

import android.util.Base64
import com.yuchen.ailedger.data.VisualDemonstrationSession
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 事件对齐的视觉演示采样器。
 *
 * 无障碍事件只作为“用户刚做了动作”的时间锚点；本地不读取节点、不保存选择器、
 * 不推断步骤。截图仍是唯一视觉证据，云端根据动作前后关键帧理解 Skill。
 */
class VisualDemonstrationRecorder(
    private val session: VisualDemonstrationSession,
    private val allowedPackages: Set<String>,
    private val onFrameCountChanged: (Int) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val captureMutex = Mutex()
    private val eventCounter = AtomicInteger(0)

    @Volatile
    private var stopped = false

    @Volatile
    private var lastCaptureStartedAtMillis = 0L

    @Volatile
    private var lastEventKey = ""

    @Volatile
    private var lastEventAtMillis = 0L

    @Volatile
    private var latestPackageHint: String = allowedPackages.firstOrNull().orEmpty()

    suspend fun runCaptureLoop() {
        captureFrame(
            captureKind = "initial",
            eventType = "session_start",
            eventIndex = 0,
            eventOccurredAtMillis = System.currentTimeMillis(),
            packageHint = latestPackageHint,
        )
        while (!stopped && session.frameCount < VisualDemonstrationSession.MAX_FRAMES - FINAL_FRAME_RESERVE) {
            delay(HEARTBEAT_INTERVAL_MS)
            if (!stopped) {
                captureFrame(
                    captureKind = "heartbeat",
                    eventType = "time_passed",
                    eventIndex = 0,
                    eventOccurredAtMillis = System.currentTimeMillis(),
                    packageHint = latestPackageHint,
                )
            }
        }
    }

    fun requestActionCapture(
        eventType: String,
        packageName: String,
        occurredAtMillis: Long = System.currentTimeMillis(),
    ) {
        if (stopped || packageName !in allowedPackages) return
        latestPackageHint = packageName
        val normalizedType = eventType.ifBlank { "user_action" }
        val key = "$packageName|$normalizedType"
        val throttleMs = if (normalizedType == "text_changed") TEXT_ACTION_THROTTLE_MS else ACTION_THROTTLE_MS
        if (key == lastEventKey && occurredAtMillis - lastEventAtMillis < throttleMs) return
        lastEventKey = key
        lastEventAtMillis = occurredAtMillis

        val index = eventCounter.incrementAndGet()
        scheduleEventFrame(
            delayMs = POST_ACTION_CAPTURE_DELAY_MS,
            captureKind = "after_action",
            eventType = normalizedType,
            eventIndex = index,
            occurredAtMillis = occurredAtMillis,
            packageName = packageName,
        )
        scheduleEventFrame(
            delayMs = SETTLE_CAPTURE_DELAY_MS,
            captureKind = "action_settle",
            eventType = normalizedType,
            eventIndex = index,
            occurredAtMillis = occurredAtMillis,
            packageName = packageName,
        )
    }

    suspend fun captureFinalFrame() = withContext(Dispatchers.Default) {
        if (!stopped) {
            captureFrame(
                captureKind = "final",
                eventType = "session_finish",
                eventIndex = eventCounter.get() + 1,
                eventOccurredAtMillis = System.currentTimeMillis(),
                packageHint = latestPackageHint,
            )
        }
        Unit
    }

    fun stop() {
        stopped = true
        scope.cancel()
    }

    private fun scheduleEventFrame(
        delayMs: Long,
        captureKind: String,
        eventType: String,
        eventIndex: Int,
        occurredAtMillis: Long,
        packageName: String,
    ): Job = scope.launch {
        delay(delayMs)
        if (!stopped) {
            captureFrame(
                captureKind = captureKind,
                eventType = eventType,
                eventIndex = eventIndex,
                eventOccurredAtMillis = occurredAtMillis,
                packageHint = packageName,
            )
        }
    }

    private suspend fun captureFrame(
        captureKind: String,
        eventType: String,
        eventIndex: Int,
        eventOccurredAtMillis: Long,
        packageHint: String,
    ): Boolean = captureMutex.withLock {
        if (stopped || session.frameCount >= VisualDemonstrationSession.MAX_FRAMES - FINAL_FRAME_RESERVE) {
            return@withLock false
        }
        val now = System.currentTimeMillis()
        val minGap = if (captureKind == "final") 0L else MIN_CAPTURE_GAP_MS
        val waitMs = minGap - (now - lastCaptureStartedAtMillis)
        if (waitMs > 0L) delay(waitMs)
        lastCaptureStartedAtMillis = System.currentTimeMillis()

        val observation = runCatching {
            AiAgentAccessibilityService.captureFreshVisualSnapshot(
                preferredPackage = packageHint.takeIf(String::isNotBlank),
                reason = "record_$captureKind",
            )
        }.getOrNull() ?: return@withLock false
        val snapshot = observation.toAgentScreenSnapshot()
        val visual = snapshot.visual?.takeIf { it.hasImage } ?: return@withLock false
        val packageName = snapshot.packageName.trim().ifBlank { packageHint }
        if (packageName.isBlank() || packageName !in allowedPackages) return@withLock false
        latestPackageHint = packageName

        val bytes = runCatching { Base64.decode(visual.base64Jpeg, Base64.DEFAULT) }.getOrNull()
            ?: return@withLock false
        val appended = withContext(Dispatchers.IO) {
            runCatching {
                session.appendFrame(
                    capturedAtMillis = System.currentTimeMillis(),
                    packageName = packageName,
                    mimeType = visual.mimeType.ifBlank { "image/jpeg" },
                    width = visual.width,
                    height = visual.height,
                    displayWidth = visual.displayWidth,
                    displayHeight = visual.displayHeight,
                    bytes = bytes,
                    captureKind = captureKind,
                    eventType = eventType,
                    eventIndex = eventIndex,
                    eventOccurredAtMillis = eventOccurredAtMillis,
                )
            }.getOrDefault(false)
        }
        if (appended) onFrameCountChanged(session.frameCount)
        appended
    }

    companion object {
        private const val FINAL_FRAME_RESERVE = 1
        private const val HEARTBEAT_INTERVAL_MS = 6_500L
        private const val POST_ACTION_CAPTURE_DELAY_MS = 320L
        private const val SETTLE_CAPTURE_DELAY_MS = 920L
        private const val MIN_CAPTURE_GAP_MS = 420L
        private const val ACTION_THROTTLE_MS = 120L
        private const val TEXT_ACTION_THROTTLE_MS = 520L
    }
}
