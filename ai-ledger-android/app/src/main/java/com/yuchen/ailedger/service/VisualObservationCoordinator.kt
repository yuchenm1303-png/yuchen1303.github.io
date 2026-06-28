package com.yuchen.ailedger.service

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

interface VisualCaptureOverlayController {
    fun beginCapture()
    fun endCapture()
}

object RuntimeVisualCaptureOverlayController : VisualCaptureOverlayController {
    override fun beginCapture() {
        AgentRuntimeController.beginCleanVisualCapture()
    }

    override fun endCapture() {
        AgentRuntimeController.endCleanVisualCapture()
    }
}

data class VisualObservationTiming(
    // 无障碍截图层仍保留 150ms 的悬浮窗隐藏等待；这里只保留页面自身稳定所需的 110ms。
    // 两层合计 260ms，维持原来的页面稳定基准，同时移除额外重复的 150ms。
    val fullVisualSettleMs: Long = 110L,
    val nonVisualSettleMs: Long = 160L,
    val packageProbeSettleMs: Long = 160L,
    val openAppInitialSettleMs: Long = 260L,
    val openAppVerifyPollMs: Long = 140L,
    val openAppVerifyTimeoutMs: Long = 4_200L,
    val requiredStableSamples: Int = 2,
)

data class VisualTargetPackageVerification(
    val verified: Boolean,
    val stableSamples: Int,
    val lastSnapshot: AgentScreenSnapshot?,
    val lastObservation: ScreenObservation?,
)

class VisualObservationCoordinator(
    private val captureSource: VisualObservationCaptureSource,
    private val foregroundPackageReader: ForegroundPackageReader,
    private val overlayController: VisualCaptureOverlayController = RuntimeVisualCaptureOverlayController,
    private val timing: VisualObservationTiming = VisualObservationTiming(),
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun captureTrustedObservation(
        forceVisual: Boolean,
        expectedPackage: String,
        settleMs: Long = if (forceVisual) timing.fullVisualSettleMs else timing.nonVisualSettleMs,
    ): ScreenObservation {
        val observation = captureOnce(forceVisual = forceVisual, settleMs = settleMs)
        if (
            expectedPackage.isBlank() ||
            !ForegroundPackageEvidenceResolver.needsShellFallback(observation.packageName)
        ) {
            return observation
        }

        val shellProbe = withContext(Dispatchers.IO) { foregroundPackageReader.probe() }
        val evidence = ForegroundPackageEvidenceResolver.resolve(
            accessibilityPackage = observation.packageName,
            shellProbe = shellProbe,
        )
        if (evidence.packageName.isBlank() || evidence.packageName == observation.packageName) {
            return observation
        }

        return observation.copy(
            packageName = evidence.packageName,
            windowTitle = listOf(
                observation.windowTitle,
                "foreground=${evidence.source.wireValue}",
            ).filter(String::isNotBlank)
                .joinToString(" · ")
                .take(120),
        )
    }

    suspend fun awaitStableTargetPackage(
        expectedPackage: String,
        isStopped: () -> Boolean,
    ): VisualTargetPackageVerification {
        if (expectedPackage.isBlank()) {
            return VisualTargetPackageVerification(false, 0, null, null)
        }

        val deadline = elapsedRealtime() + timing.openAppVerifyTimeoutMs
        var stableSamples = 0
        var lastSnapshot: AgentScreenSnapshot? = null
        sleep(timing.openAppInitialSettleMs)

        while (!isStopped() && elapsedRealtime() < deadline) {
            val probeObservation = captureTrustedObservation(
                forceVisual = false,
                expectedPackage = expectedPackage,
                settleMs = timing.packageProbeSettleMs,
            )
            val probeSnapshot = probeObservation.toAgentScreenSnapshot()
            lastSnapshot = probeSnapshot

            if (probeSnapshot.packageName == expectedPackage) {
                stableSamples += 1
                if (stableSamples >= timing.requiredStableSamples.coerceAtLeast(2)) {
                    val visualObservation = captureTrustedObservation(
                        forceVisual = true,
                        expectedPackage = expectedPackage,
                    )
                    val visualSnapshot = visualObservation.toAgentScreenSnapshot()
                    lastSnapshot = visualSnapshot
                    if (
                        visualSnapshot.packageName == expectedPackage &&
                        visualSnapshot.visual?.hasImage == true
                    ) {
                        return VisualTargetPackageVerification(
                            verified = true,
                            stableSamples = stableSamples,
                            lastSnapshot = visualSnapshot,
                            lastObservation = visualObservation,
                        )
                    }
                    if (!VisualSurfacePackagePolicy.requiresForegroundFallback(visualSnapshot.packageName)) {
                        stableSamples = 0
                    }
                }
            } else if (!VisualSurfacePackagePolicy.requiresForegroundFallback(probeSnapshot.packageName)) {
                stableSamples = 0
            }
            sleep(timing.openAppVerifyPollMs)
        }

        return VisualTargetPackageVerification(
            verified = false,
            stableSamples = stableSamples,
            lastSnapshot = lastSnapshot,
            lastObservation = null,
        )
    }

    private suspend fun captureOnce(
        forceVisual: Boolean,
        settleMs: Long,
    ): ScreenObservation {
        // 轻量节点读取和前台包探测不会采集屏幕像素，不能因此反复隐藏 HUD。
        // 真正的视觉截图仍使用 clean-visual lease；动作执行链也会在外层持有同一 lease，
        // 因而一次“动作前校验—执行—动作后截图”只形成一个连续的不可见窗口。
        if (!forceVisual) {
            sleep(settleMs)
            return captureSource.capture(forceVisual = false)
        }

        overlayController.beginCapture()
        return try {
            sleep(settleMs)
            captureSource.capture(forceVisual = true)
        } finally {
            overlayController.endCapture()
        }
    }

    private suspend fun sleep(durationMs: Long) {
        if (durationMs > 0L) sleeper(durationMs)
    }
}
