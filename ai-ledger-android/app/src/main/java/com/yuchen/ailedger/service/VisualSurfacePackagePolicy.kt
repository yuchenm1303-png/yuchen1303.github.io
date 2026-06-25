package com.yuchen.ailedger.service

object VisualSurfacePackagePolicy {
    const val ASSISTANT_HOST_PACKAGE = "com.yuchen.ailedger"

    val transientSystemPackages: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
    )

    fun requiresForegroundFallback(packageName: String): Boolean {
        val cleanPackage = packageName.trim()
        return cleanPackage.isBlank() ||
            cleanPackage == ASSISTANT_HOST_PACKAGE ||
            cleanPackage in transientSystemPackages
    }

    fun isConfidentForeignPackage(
        currentPackage: String,
        expectedPackage: String,
    ): Boolean {
        val current = currentPackage.trim()
        val expected = expectedPackage.trim()
        if (current.isBlank() || current == expected) return false
        if (current == ASSISTANT_HOST_PACKAGE) return false
        if (current in transientSystemPackages) return false
        return true
    }
}
