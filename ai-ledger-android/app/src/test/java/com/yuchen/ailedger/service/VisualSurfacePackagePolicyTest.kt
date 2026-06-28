package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualSurfacePackagePolicyTest {
    @Test
    fun googleAndOemPermissionControllersAreTransientSurfaces() {
        assertTrue(
            VisualSurfacePackagePolicy.requiresForegroundFallback(
                "com.google.android.permissioncontroller",
            ),
        )
        assertTrue(
            VisualSurfacePackagePolicy.requiresForegroundFallback(
                "com.vendor.security.permissioncontroller",
            ),
        )
    }

    @Test
    fun ordinaryThirdPartyPackageRemainsConfidentForeignEvidence() {
        assertTrue(
            VisualSurfacePackagePolicy.isConfidentForeignPackage(
                currentPackage = "com.example.other",
                expectedPackage = "com.example.target",
            ),
        )
        assertFalse(
            VisualSurfacePackagePolicy.isConfidentForeignPackage(
                currentPackage = "com.google.android.permissioncontroller",
                expectedPackage = "com.example.target",
            ),
        )
    }
}
