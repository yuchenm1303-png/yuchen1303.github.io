package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundPackageEvidenceTest {
    @Test
    fun usableAccessibilityPackageAlwaysWins() {
        val result = ForegroundPackageEvidenceResolver.resolve(
            accessibilityPackage = "com.example.accessibility",
            shellProbe = ForegroundPackageProbeResult(
                packageName = "com.example.shell",
                source = ForegroundPackageEvidenceSource.ActivityManager,
                available = true,
            ),
        )

        assertEquals("com.example.accessibility", result.packageName)
        assertEquals(ForegroundPackageEvidenceSource.Accessibility, result.source)
        assertTrue(result.available)
    }

    @Test
    fun assistantTransientAndUnresolvedSurfacesUseTrustedShellFallback() {
        val shell = ForegroundPackageProbeResult(
            packageName = "com.example.target",
            source = ForegroundPackageEvidenceSource.WindowManager,
            available = true,
        )

        listOf(
            VisualExecutionSessionState.ASSISTANT_HOST_PACKAGE,
            "",
            "unknown",
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.vendor.security.permissioncontroller",
        ).forEach { accessibilityPackage ->
            val result = ForegroundPackageEvidenceResolver.resolve(accessibilityPackage, shell)
            assertEquals("com.example.target", result.packageName)
            assertEquals(ForegroundPackageEvidenceSource.WindowManager, result.source)
            assertTrue(ForegroundPackageEvidenceResolver.needsShellFallback(accessibilityPackage))
        }
    }

    @Test
    fun transientShellResultCannotMasqueradeAsContentApp() {
        val result = ForegroundPackageEvidenceResolver.resolve(
            accessibilityPackage = "unknown",
            shellProbe = ForegroundPackageProbeResult(
                packageName = "com.google.android.permissioncontroller",
                source = ForegroundPackageEvidenceSource.ActivityManager,
                available = true,
            ),
        )

        assertEquals("unknown", result.packageName)
        assertEquals(ForegroundPackageEvidenceSource.Accessibility, result.source)
    }

    @Test
    fun unavailableSourcesNeverInventPackage() {
        val result = ForegroundPackageEvidenceResolver.resolve(
            accessibilityPackage = "",
            shellProbe = ForegroundPackageProbeResult(
                available = false,
                detail = "unavailable",
            ),
        )

        assertEquals("", result.packageName)
        assertFalse(result.available)
        assertEquals(ForegroundPackageEvidenceSource.Unavailable, result.source)
    }

    @Test
    fun parserExtractsExactPackageFromActivityAndWindowLines() {
        assertEquals(
            "com.example.target",
            ForegroundPackageOutputParser.parse(
                "mResumedActivity: ActivityRecord{abc u0 com.example.target/.MainActivity t42}",
            ),
        )
        assertEquals(
            "com.example.window",
            ForegroundPackageOutputParser.parse(
                "mCurrentFocus=Window{abc u0 com.example.window/com.example.window.HomeActivity}",
            ),
        )
    }

    @Test
    fun parserRejectsLabelsWithoutAndroidComponent() {
        assertNull(ForegroundPackageOutputParser.parse("Current app: Example Target"))
        assertNull(ForegroundPackageOutputParser.parse("com.example.target"))
    }
}
