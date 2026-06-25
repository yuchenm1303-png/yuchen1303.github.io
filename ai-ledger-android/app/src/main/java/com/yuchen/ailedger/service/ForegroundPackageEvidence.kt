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
