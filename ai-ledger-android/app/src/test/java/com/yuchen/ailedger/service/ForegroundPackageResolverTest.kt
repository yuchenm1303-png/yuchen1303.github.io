package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundPackageResolverTest {
    @Test
    fun shellExactPackageRecoversCloudSelectedTargetFromOverlayRoot() {
        val result = ForegroundPackageResolutionPolicy.resolve(
            observedPackage = VisualExecutionSessionState.ASSISTANT_HOST_PACKAGE,
            expectedPackage = "com.hexin.plat.android",
            foregroundProcessPackages = emptySet(),
            shellForegroundPackages = setOf("com.hexin.plat.android"),
        )

        assertTrue(result.matchedExpectedPackage)
        assertEquals("com.hexin.plat.android", result.packageName)
        assertEquals("shell_foreground_exact", result.source)
    }

    @Test
    fun foregroundProcessCanRecoverOnlyWhenAccessibilityEvidenceIsWeak() {
        val weakRoot = ForegroundPackageResolutionPolicy.resolve(
            observedPackage = "unknown",
            expectedPackage = "com.hexin.plat.android",
            foregroundProcessPackages = setOf("com.hexin.plat.android"),
            shellForegroundPackages = emptySet(),
        )
        val conflictingRealApp = ForegroundPackageResolutionPolicy.resolve(
            observedPackage = "com.tencent.mobileqq",
            expectedPackage = "com.hexin.plat.android",
            foregroundProcessPackages = setOf("com.hexin.plat.android"),
            shellForegroundPackages = emptySet(),
        )

        assertTrue(weakRoot.matchedExpectedPackage)
        assertEquals("com.hexin.plat.android", weakRoot.packageName)
        assertFalse(conflictingRealApp.matchedExpectedPackage)
        assertEquals("com.tencent.mobileqq", conflictingRealApp.packageName)
    }

    @Test
    fun dumpsysParserReadsResumedAndFocusedPackages() {
        val packages = ForegroundPackageResolver.parseForegroundPackages(
            """
            mResumedActivity: ActivityRecord{123 u0 com.hexin.plat.android/.MainActivity t88}
            mCurrentFocus=Window{abc u0 com.android.systemui/com.android.systemui.statusbar.phone.CentralSurfaces}
            """.trimIndent(),
        )

        assertTrue("com.hexin.plat.android" in packages)
        assertTrue("com.android.systemui" in packages)
    }
}
