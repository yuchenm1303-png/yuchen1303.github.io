package com.yuchen.ailedger.service

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
    val trustedPackageTtlMs: Long = 15_000L,
)

data class VisualTargetPackageVerification(
    val verified: Boolean,
    val stableSamples: Int,
    val lastSnapshot: AgentScreenSnapshot?,
    val lastObservation: ScreenObservation?,
)

private data class ResolvedObservationPackage(
    val observation: ScreenObservation,
    val rawPackage: String,
    val source: String,
    val overlayPackage: String = "",
    val inheritedTrustedBase: Boolean = false,
)

class VisualObservationCoordinator(
    private val captureSource: VisualObservationCaptureSource,
    private val foregroundPackageReader: ForegroundPackageReader,
    private val overlayController: VisualCaptureOverlayController = RuntimeVisualCaptureOverlayController,
    private val timing: VisualObservationTiming = VisualObservationTiming(),
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    private val trustedPackageLock = Any()
    private var lastTrustedPackage: String = ""
    private var lastTrustedPackageAt: Long = 0L

    suspend fun captureTrustedObservation(
        forceVisual: Boolean,
        expectedPackage: String,
        settleMs: Long = if (forceVisual) timing.fullVisualSettleMs else timing.nonVisualSettleMs,
    ): ScreenObservation {
        val captured = captureOnce(forceVisual = forceVisual, settleMs = settleMs)
        val resolution = resolveObservationPackage(captured, expectedPackage)
        val resolved = resolution.observation

        // 只复用本轮已经得到的观察结果，不触发额外截图或节点扫描。
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.let { diagnostics ->
            diagnostics.recordObservation(
                forceVisual = forceVisual,
                expectedPackage = expectedPackage,
                observation = resolved,
            )
            diagnostics.recordDiagnosticEvent(
                type = "observation_evidence",
                details = JSONObject().apply {
                    put("forceVisual", forceVisual)
                    put("expectedPackage", expectedPackage)
                    put("rawPackage", resolution.rawPackage)
                    put("resolvedPackage", resolved.packageName)
                    put("packageEvidenceSource", resolution.source)
                    put("overlayPackage", resolution.overlayPackage)
                    put("inheritedTrustedBase", resolution.inheritedTrustedBase)
                    put("windowTitle", resolved.windowTitle)
                    put("nodeCount", resolved.nodeCount)
                    put("capturedNodeCount", resolved.capturedNodeCount)
                    put("textItems", JSONArray(resolved.textItems.take(36)))
                    put("allItems", JSONArray().apply {
                        resolved.allItems.take(48).forEach { node -> put(node.toDiagnosticJson()) }
                    })
                    put("clickableItems", JSONArray().apply {
                        resolved.clickableItems.take(24).forEach { node -> put(node.toDiagnosticJson()) }
                    })
                    put("inputItems", JSONArray().apply {
                        resolved.inputItems.take(8).forEach { node -> put(node.toDiagnosticJson()) }
                    })
                    put("scrollableItems", JSONArray().apply {
                        resolved.scrollableItems.take(8).forEach { node -> put(node.toDiagnosticJson()) }
                    })
                },
            )
        }
        return resolved
    }

    private suspend fun resolveObservationPackage(
        observation: ScreenObservation,
        expectedPackage: String,
    ): ResolvedObservationPackage {
        val rawPackage = observation.packageName.trim()
        if (!VisualSurfacePackagePolicy.requiresForegroundFallback(rawPackage)) {
            rememberTrustedPackage(rawPackage)
            return ResolvedObservationPackage(
                observation = observation.copy(packageName = rawPackage),
                rawPackage = rawPackage,
                source = "accessibility_capture",
            )
        }

        val evidence = if (expectedPackage.isNotBlank()) {
            val shellProbe = withContext(Dispatchers.IO) { foregroundPackageReader.probe() }
            ForegroundPackageEvidenceResolver.resolve(
                accessibilityPackage = rawPackage,
                shellProbe = shellProbe,
            )
        } else {
            null
        }
        val probedPackage = evidence?.packageName.orEmpty().trim()
        if (!VisualSurfacePackagePolicy.requiresForegroundFallback(probedPackage)) {
            rememberTrustedPackage(probedPackage)
            return ResolvedObservationPackage(
                observation = observation.copy(
                    packageName = probedPackage,
                    windowTitle = observation.windowTitle.withPackageEvidence(
                        "foreground=${evidence?.source?.wireValue.orEmpty()}",
                        rawPackage.takeIf(String::isNotBlank)?.let { "overlay=$it" }.orEmpty(),
                    ),
                ),
                rawPackage = rawPackage,
                source = evidence?.source?.wireValue.orEmpty().ifBlank { "foreground_probe" },
                overlayPackage = rawPackage.takeIf(String::isNotBlank).orEmpty(),
            )
        }

        val trustedBase = trustedPackageFor(expectedPackage)
        if (trustedBase.isNotBlank()) {
            return ResolvedObservationPackage(
                observation = observation.copy(
                    packageName = trustedBase,
                    windowTitle = observation.windowTitle.withPackageEvidence(
                        "foreground=trusted_base",
                        rawPackage.takeIf(String::isNotBlank)?.let { "overlay=$it" }.orEmpty(),
                    ),
                ),
                rawPackage = rawPackage,
                source = "trusted_base_cache",
                overlayPackage = rawPackage.takeIf(String::isNotBlank).orEmpty(),
                inheritedTrustedBase = true,
            )
        }

        return ResolvedObservationPackage(
            observation = observation,
            rawPackage = rawPackage,
            source = if (rawPackage.isBlank()) "unresolved" else "transient_unresolved",
            overlayPackage = rawPackage,
        )
    }

    private fun rememberTrustedPackage(packageName: String) {
        val cleanPackage = packageName.trim()
        if (VisualSurfacePackagePolicy.requiresForegroundFallback(cleanPackage)) return
        synchronized(trustedPackageLock) {
            lastTrustedPackage = cleanPackage
            lastTrustedPackageAt = elapsedRealtime()
        }
    }

    private fun trustedPackageFor(expectedPackage: String): String {
        val expected = expectedPackage.trim()
        if (expected.isBlank()) return ""
        val now = elapsedRealtime()
        return synchronized(trustedPackageLock) {
            val age = now - lastTrustedPackageAt
            lastTrustedPackage.takeIf {
                it == expected && age >= 0L && age <= timing.trustedPackageTtlMs.coerceAtLeast(0L)
            }.orEmpty()
        }
    }

    suspend fun awaitStableTargetPackage(
        expectedPackage: String,
        isStopped: () -> Boolean,
    ): VisualTargetPackageVerification {
        if (expectedPackage.isBlank()) {
            return reportPackageVerification(
                expectedPackage = expectedPackage,
                result = VisualTargetPackageVerification(false, 0, null, null),
                reason = "expected_package_blank",
            )
        }

        val requiredSamples = timing.requiredStableSamples.coerceAtLeast(2)
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
                if (stableSamples >= requiredSamples) {
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
                        return reportPackageVerification(
                            expectedPackage = expectedPackage,
                            result = VisualTargetPackageVerification(
                                verified = true,
                                stableSamples = stableSamples,
                                lastSnapshot = visualSnapshot,
                                lastObservation = visualObservation,
                            ),
                            reason = "stable_target_with_visual_frame",
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

        val reason = when {
            isStopped() -> "task_stopped"
            lastSnapshot?.packageName == expectedPackage && stableSamples in 1 until requiredSamples ->
                "stable_samples_incomplete"
            VisualSurfacePackagePolicy.requiresForegroundFallback(lastSnapshot?.packageName.orEmpty()) ->
                "transient_surface"
            else -> "target_not_stable"
        }
        return reportPackageVerification(
            expectedPackage = expectedPackage,
            result = VisualTargetPackageVerification(
                verified = false,
                stableSamples = stableSamples,
                lastSnapshot = lastSnapshot,
                lastObservation = null,
            ),
            reason = reason,
        )
    }

    private fun reportPackageVerification(
        expectedPackage: String,
        result: VisualTargetPackageVerification,
        reason: String,
    ): VisualTargetPackageVerification {
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "open_app_verification",
            details = JSONObject().apply {
                put("verified", result.verified)
                put("reason", reason)
                put("expectedPackage", expectedPackage)
                put("actualPackage", result.lastSnapshot?.packageName.orEmpty())
                put("stableSamples", result.stableSamples)
                put("requiredStableSamples", timing.requiredStableSamples.coerceAtLeast(2))
                put("hasVisualFrame", result.lastSnapshot?.visual?.hasImage == true)
                put("observationUpdatedAt", result.lastObservation?.updatedAt ?: 0L)
            },
        )
        return result
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

private fun String.withPackageEvidence(vararg evidence: String): String {
    return (listOf(this) + evidence)
        .filter(String::isNotBlank)
        .joinToString(" · ")
        .take(120)
}

private fun ObservedScreenNode.toDiagnosticJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("text", text)
    put("className", className)
    put("bounds", bounds)
    put("clickable", clickable)
    put("editable", editable)
    put("scrollable", scrollable)
}
