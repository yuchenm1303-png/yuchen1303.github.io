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
        assistantHostPackage: String = VisualSurfacePackagePolicy.ASSISTANT_HOST_PACKAGE,
        transientPackages: Set<String> = VisualSurfacePackagePolicy.transientSystemPackages,
    ): ForegroundPackageProbeResult {
        val accessibility = accessibilityPackage.trim()
        if (!needsForegroundFallback(accessibility, assistantHostPackage, transientPackages)) {
            return ForegroundPackageProbeResult(
                packageName = accessibility,
                source = ForegroundPackageEvidenceSource.Accessibility,
                available = true,
                detail = "accessibility_window_package",
            )
        }

        val shellPackage = shellProbe.packageName.trim()
        if (shellProbe.available && !needsForegroundFallback(shellPackage, assistantHostPackage, transientPackages)) {
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
        assistantHostPackage: String = VisualSurfacePackagePolicy.ASSISTANT_HOST_PACKAGE,
        transientPackages: Set<String> = VisualSurfacePackagePolicy.transientSystemPackages,
    ): Boolean = needsForegroundFallback(
        accessibilityPackage.trim(),
        assistantHostPackage,
        transientPackages,
    )

    private fun needsForegroundFallback(
        packageName: String,
        assistantHostPackage: String,
        transientPackages: Set<String>,
    ): Boolean {
        val clean = packageName.trim()
        return VisualSurfacePackagePolicy.isUnresolvedPackage(clean) ||
            clean == assistantHostPackage ||
            clean in transientPackages ||
            VisualSurfacePackagePolicy.isTransientSystemPackage(clean)
    }
}
