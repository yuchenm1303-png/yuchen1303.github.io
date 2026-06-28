package com.yuchen.ailedger.service

object VisualSurfacePackagePolicy {
    const val ASSISTANT_HOST_PACKAGE = "com.yuchen.ailedger"

    val transientSystemPackages: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
    )

    private val transientSystemPackageSuffixes: Set<String> = setOf(
        ".permissioncontroller",
        ".packageinstaller",
    )

    private val unresolvedPackageMarkers: Set<String> = setOf(
        "unknown",
        "none",
        "unavailable",
    )

    fun isUnresolvedPackage(packageName: String): Boolean {
        val cleanPackage = packageName.trim().lowercase()
        return cleanPackage.isBlank() || cleanPackage in unresolvedPackageMarkers
    }

    fun isTransientSystemPackage(packageName: String): Boolean {
        val cleanPackage = packageName.trim().lowercase()
        return cleanPackage in transientSystemPackages ||
            transientSystemPackageSuffixes.any { suffix -> cleanPackage.endsWith(suffix) }
    }

    fun requiresForegroundFallback(packageName: String): Boolean {
        val cleanPackage = packageName.trim()
        return isUnresolvedPackage(cleanPackage) ||
            cleanPackage == ASSISTANT_HOST_PACKAGE ||
            isTransientSystemPackage(cleanPackage)
    }

    fun isConfidentForeignPackage(
        currentPackage: String,
        expectedPackage: String,
    ): Boolean {
        val current = currentPackage.trim()
        val expected = expectedPackage.trim()
        if (expected.isBlank() || current == expected) return false
        if (requiresForegroundFallback(current)) return false
        return true
    }
}
