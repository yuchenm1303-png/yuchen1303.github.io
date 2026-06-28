package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualOpenAppHandoffPolicyTest {
    @Test
    fun sameTargetLaunchInProgressSuppressesPhysicalRestart() {
        assertTrue(
            VisualOpenAppHandoffPolicy.shouldSuppressPhysicalLaunch(
                runtime = runtime(VisualSurfaceState.Launching, "com.example.target"),
                requestedPackage = "com.example.target",
                alreadyForeground = false,
            ),
        )
    }

    @Test
    fun verifiedTargetSuppressesRedundantOpenApp() {
        assertTrue(
            VisualOpenAppHandoffPolicy.shouldSuppressPhysicalLaunch(
                runtime = runtime(
                    VisualSurfaceState.WorkSurface,
                    "com.example.target",
                    "com.example.target",
                ),
                requestedPackage = "com.example.target",
                alreadyForeground = false,
            ),
        )
    }

    @Test
    fun replanningAllowsRealRelaunch() {
        assertFalse(
            VisualOpenAppHandoffPolicy.shouldSuppressPhysicalLaunch(
                runtime = runtime(VisualSurfaceState.Replanning, "com.example.target"),
                requestedPackage = "com.example.target",
                alreadyForeground = false,
            ),
        )
    }

    private fun runtime(
        state: VisualSurfaceState,
        selected: String,
        verified: String = "",
    ) = VisualAgentRuntimeContext(
        surfaceState = state,
        selectedTargetPackage = selected,
        verifiedTargetPackage = verified,
        currentPackage = "",
        observationId = "observation",
        routeEpoch = 0L,
        surfaceEpoch = 0L,
        guiPlusEligible = state == VisualSurfaceState.WorkSurface && verified.isNotBlank(),
    )
}
