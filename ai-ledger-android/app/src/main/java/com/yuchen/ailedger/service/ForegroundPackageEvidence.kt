package com.yuchen.ailedger.service

enum class ForegroundPackageEvidenceSource(val wireValue: String) {
    Accessibility("accessibility"),
    ActivityManager("activity_manager"),
    WindowManager("window_manager"),
    Unavailable("unavailable"),
}

data class ForegroundPackageProbeResult(
    val packageName: String = "",
    val source: ForegroundPackageEvidenceSource = ForegroundPackageEvidenceSource.Unavailable,
    val available: Boolean = false,
    val detail: String = "",
)

object ForegroundPackageEvidenceResolver {
    fun resolve(
        accessibilityPackage: String,
        shellProbe: ForegroundPackageProbeResult,
        assistantHostPackage: String = VisualExecutionSessionState.ASSISTANT_HOST_PACKAGE,
        transientPackages: Set<String> = transientSystemPackages,
    ): ForegroundPackageProbeResult {
        val accessibility = accessibilityPackage.trim()
        if (
            accessibility.isNotBlank() &&
            accessibility != assistantHostPackage &&
            accessibility !in transientPackages
        ) {
            return ForegroundPackageProbeResult(
                packageName = accessibility,
                source = ForegroundPackageEvidenceSource.Accessibility,
                available = true,
                detail = "accessibility_window_package",
            )
        }

        val shellPackage = shellProbe.packageName.trim()
        if (shellProbe.available && shellPackage.isNotBlank()) {
            return shellProbe.copy(packageName = shellPackage)
        }

        return ForegroundPackageProbeResult(
            packageName = accessibility,
            source = if (accessibility.isNotBlank()) {
                ForegroundPackageEvidenceSource.Accessibility
            } else {
                ForegroundPackageEvidenceSource.Unavailable
            },
            available = accessibility.isNotBlank(),
            detail = shellProbe.detail.ifBlank { "trusted_foreground_package_unavailable" },
        )
    }

    fun needsShellFallback(
        accessibilityPackage: String,
        assistantHostPackage: String = VisualExecutionSessionState.ASSISTANT_HOST_PACKAGE,
        transientPackages: Set<String> = transientSystemPackages,
    ): Boolean {
        val clean = accessibilityPackage.trim()
        return clean.isBlank() || clean == assistantHostPackage || clean in transientPackages
    }

    private val transientSystemPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
    )
}

class ForegroundPackageProbe(
    private val shellBridge: DeviceShellBridge,
) {
    fun probe(): ForegroundPackageProbeResult {
        val accessibilityHint = ScreenObservationStore.recentWindowPackageHint()
        if (
            accessibilityHint != null &&
            !ForegroundPackageEvidenceResolver.needsShellFallback(accessibilityHint.packageName)
        ) {
            return ForegroundPackageProbeResult(
                packageName = accessibilityHint.packageName,
                source = ForegroundPackageEvidenceSource.Accessibility,
                available = true,
                detail = "accessibility_working_window_event",
            )
        }

        val activity = shellBridge.runTrustedReadOnlyEnhancedCommand(
            title = "读取前台 Activity",
            command = ACTIVITY_FOREGROUND_COMMAND,
            timeoutMs = FOREGROUND_PROBE_TIMEOUT_MS,
        )
        ForegroundPackageOutputParser.parse(activity.output)?.let { packageName ->
            return ForegroundPackageProbeResult(
                packageName = packageName,
                source = ForegroundPackageEvidenceSource.ActivityManager,
                available = true,
                detail = "dumpsys_activity",
            )
        }
        if (activity.error == "trusted_enhanced_shell_unavailable") {
            return ForegroundPackageProbeResult(
                available = false,
                detail = activity.error,
            )
        }

        val window = shellBridge.runTrustedReadOnlyEnhancedCommand(
            title = "读取前台窗口",
            command = WINDOW_FOREGROUND_COMMAND,
            timeoutMs = FOREGROUND_PROBE_TIMEOUT_MS,
        )
        ForegroundPackageOutputParser.parse(window.output)?.let { packageName ->
            return ForegroundPackageProbeResult(
                packageName = packageName,
                source = ForegroundPackageEvidenceSource.WindowManager,
                available = true,
                detail = "dumpsys_window",
            )
        }

        return ForegroundPackageProbeResult(
            available = false,
            detail = listOf(activity.error, window.error)
                .firstOrNull { it.isNotBlank() }
                ?.take(160)
                ?: "foreground_package_not_reported",
        )
    }

    companion object {
        private const val FOREGROUND_PROBE_TIMEOUT_MS = 700L
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
