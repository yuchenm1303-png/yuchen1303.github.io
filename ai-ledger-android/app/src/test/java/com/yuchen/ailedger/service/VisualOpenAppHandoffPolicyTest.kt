package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualOpenAppHandoffPolicyTest {
    @Test
    fun sameTargetLaunchInProgressSuppressesPhysicalRestart() {
        assertTrue(
            VisualOpenAppHandoffPolicy.shouldSuppressPhysicalLaunch(
                runtime = runtime(
                    state = VisualSurfaceState.Launching,
                    selected = "com.example.target",
                ),
                requestedPackage = "com.example.target",
                alreadyForeground = false,
            ),
        )
    }

    @Test
    fun verifiedTargetSuppressesRedundantOpenAppAcrossOneTemporaryForeignFrame() {
        assertTrue(
            VisualOpenAppHandoffPolicy.shouldSuppressPhysicalLaunch(
                runtime = runtime(
                    state = VisualSurfaceState.WorkSurface,
                    selected = "com.example.target",
                    verified = "com.example.target",
                    current = "com.android.systemui",
                ),
                requestedPackage = "com.example.target",
                alreadyForeground = false,
            ),
        )
    }

    @Test
    fun replanningAllowsARealRelaunch() {
        assertFalse(
            VisualOpenAppHandoffPolicy.shouldSuppressPhysicalLaunch(
                runtime = runtime(
                    state = VisualSurfaceState.Replanning,
                    selected = "com.example.target",
                    current = "com.example.other",
                ),
                requestedPackage = "com.example.target",
                alreadyForeground = false,
            ),
        )
    }

    private fun runtime(
        state: VisualSurfaceState,
        selected: String,
        verified: String = "",
        current: String = "",
    ) = VisualAgentRuntimeContext(
        surfaceState = state,
        selectedTargetPackage = selected,
        verifiedTargetPackage = verified,
        currentPackage = current,
        observationId = "observation",
        routeEpoch = 0L,
        surfaceEpoch = 0L,
        guiPlusEligible = state == VisualSurfaceState.WorkSurface && verified.isNotBlank(),
    )
}
