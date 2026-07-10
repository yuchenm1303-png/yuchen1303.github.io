package com.yuchen.ailedger.service

fun interface ForegroundPackageReader {
    fun probe(): ForegroundPackageProbeResult
}

class ForegroundPackageProbe(
    private val shellBridge: DeviceShellBridge,
) : ForegroundPackageReader {
    override fun probe(): ForegroundPackageProbeResult {
        recentAccessibilityHint()?.let { return it }

        val activity = readActivityPackage()
        activity.result?.let { return it }
        if (activity.error == "trusted_enhanced_shell_unavailable") {
            return ForegroundPackageProbeResult(
                available = false,
                detail = activity.error,
            )
        }

        val window = readWindowPackage()
        window.result?.let { return it }

        // 前台切换动画、窗口提交和无障碍 Working 模式刚开启时，Activity/Window 可能在同一瞬间
        // 都返回空。只在两种直接证据均失败时做一次短延迟 Activity 复核，避免把短暂无包名
        // 误判成页面已经变化；不会持续轮询，也不会继承旧包名或放宽旧截图校验。
        Thread.sleep(FOREGROUND_RETRY_DELAY_MS)
        recentAccessibilityHint()?.let { return it.copy(detail = "accessibility_retry_window_event") }
        val retryActivity = readActivityPackage()
        retryActivity.result?.let { result ->
            return result.copy(detail = "dumpsys_activity_retry")
        }

        return ForegroundPackageProbeResult(
            available = false,
            detail = listOf(activity.error, window.error, retryActivity.error)
                .firstOrNull { it.isNotBlank() }
                ?.take(160)
                ?: "foreground_package_not_reported_after_retry",
        )
    }

    private fun recentAccessibilityHint(): ForegroundPackageProbeResult? {
        val hint = ScreenObservationStore.recentWindowPackageHint() ?: return null
        if (ForegroundPackageEvidenceResolver.needsShellFallback(hint.packageName)) return null
        return ForegroundPackageProbeResult(
            packageName = hint.packageName,
            source = ForegroundPackageEvidenceSource.Accessibility,
            available = true,
            detail = "accessibility_working_window_event",
        )
    }

    private fun readActivityPackage(): ProbeAttempt {
        val command = shellBridge.runTrustedReadOnlyEnhancedCommand(
            title = "读取前台 Activity",
            command = ACTIVITY_FOREGROUND_COMMAND,
            timeoutMs = FOREGROUND_PROBE_TIMEOUT_MS,
        )
        val result = ForegroundPackageOutputParser.parse(command.output)?.let { packageName ->
            ForegroundPackageProbeResult(
                packageName = packageName,
                source = ForegroundPackageEvidenceSource.ActivityManager,
                available = true,
                detail = "dumpsys_activity",
            )
        }
        return ProbeAttempt(result = result, error = command.error)
    }

    private fun readWindowPackage(): ProbeAttempt {
        val command = shellBridge.runTrustedReadOnlyEnhancedCommand(
            title = "读取前台窗口",
            command = WINDOW_FOREGROUND_COMMAND,
            timeoutMs = FOREGROUND_PROBE_TIMEOUT_MS,
        )
        val result = ForegroundPackageOutputParser.parse(command.output)?.let { packageName ->
            ForegroundPackageProbeResult(
                packageName = packageName,
                source = ForegroundPackageEvidenceSource.WindowManager,
                available = true,
                detail = "dumpsys_window",
            )
        }
        return ProbeAttempt(result = result, error = command.error)
    }

    private data class ProbeAttempt(
        val result: ForegroundPackageProbeResult?,
        val error: String,
    )

    companion object {
        private const val FOREGROUND_PROBE_TIMEOUT_MS = 700L
        private const val FOREGROUND_RETRY_DELAY_MS = 70L
        private const val ACTIVITY_FOREGROUND_COMMAND =
            "dumpsys activity activities | grep -m 1 -E 'mResumedActivity|topResumedActivity|ResumedActivity'"
        private const val WINDOW_FOREGROUND_COMMAND =
            "dumpsys window windows | grep -m 1 -E 'mCurrentFocus|mFocusedApp'"
    }
}

object ForegroundPackageOutputParser {
    private val componentPattern = Regex(
        """(?:^|[\s{])([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/(?:[A-Za-z0-9_.$]+)""",
    )

    fun parse(output: String): String? {
        return output
            .lineSequence()
            .mapNotNull { line -> componentPattern.find(line)?.groupValues?.getOrNull(1) }
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}
