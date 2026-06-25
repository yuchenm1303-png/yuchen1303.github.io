package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualExecutionStateMachineTest {
    @Test
    fun exactPackageSampleCannotCompleteLaunchImplicitly() {
        val machine = VisualExecutionStateMachine()

        machine.beginLaunch(" com.example.target ")
        machine.synchronizeWith("com.example.target")

        assertEquals(VisualSurfaceState.Launching, machine.surfaceState)
        assertEquals("com.example.target", machine.selectedTargetPackage)
        assertEquals("", machine.verifiedTargetPackage)
        assertEquals(0L, machine.routeEpoch)
        assertEquals(1L, machine.surfaceEpoch)
        assertFalse(machine.isVerifiedWorkSurface("com.example.target"))
        assertFalse(machine.requiresVisualObservation())
    }

    @Test
    fun explicitVerificationCompletesSelectedLaunch() {
        val machine = VisualExecutionStateMachine()

        machine.beginLaunch("com.example.target")
        val verified = machine.markTargetVerified("com.example.target")

        assertEquals("com.example.target", verified)
        assertEquals(VisualSurfaceState.WorkSurface, machine.surfaceState)
        assertEquals("com.example.target", machine.verifiedTargetPackage)
        assertEquals(2L, machine.surfaceEpoch)
        assertTrue(machine.isVerifiedWorkSurface("com.example.target"))
        assertTrue(machine.requiresVisualObservation())
    }

    @Test
    fun verificationCannotReplaceTheSelectedTarget() {
        val machine = VisualExecutionStateMachine()

        machine.beginLaunch("com.example.target")
        val verified = machine.markTargetVerified("com.example.other")

        assertNull(verified)
        assertEquals(VisualSurfaceState.Launching, machine.surfaceState)
        assertEquals("com.example.target", machine.selectedTargetPackage)
        assertEquals("", machine.verifiedTargetPackage)
    }

    @Test
    fun replanningCannotSelfVerifyFromOneMatchingPackageSample() {
        val machine = VisualExecutionStateMachine()
        machine.beginLaunch("com.example.target")
        machine.markTargetVerified("com.example.target")
        machine.markStructuralReplan()

        machine.synchronizeWith("com.example.target")

        assertEquals(VisualSurfaceState.Replanning, machine.surfaceState)
        assertEquals("", machine.verifiedTargetPackage)
        assertFalse(machine.isVerifiedWorkSurface("com.example.target"))
    }

    @Test
    fun foreignPackageRevokesVerifiedWorkSurfaceOnce() {
        val machine = VisualExecutionStateMachine()
        machine.beginLaunch("com.example.target")
        machine.markTargetVerified("com.example.target")

        machine.synchronizeWith("com.example.other")
        machine.synchronizeWith("com.example.other")

        assertEquals(VisualSurfaceState.Replanning, machine.surfaceState)
        assertEquals("", machine.verifiedTargetPackage)
        assertEquals(1L, machine.routeEpoch)
        assertFalse(machine.isVerifiedWorkSurface("com.example.target"))
    }

    @Test
    fun transientAndAssistantPackagesDoNotRevokeSurface() {
        val transientPackages = listOf(
            VisualExecutionStateMachine.ASSISTANT_HOST_PACKAGE,
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
        )

        transientPackages.forEach { packageName ->
            val machine = VisualExecutionStateMachine()
            machine.beginLaunch("com.example.target")
            machine.markTargetVerified("com.example.target")

            machine.synchronizeWith(packageName)

            assertEquals(VisualSurfaceState.WorkSurface, machine.surfaceState)
            assertEquals("com.example.target", machine.verifiedTargetPackage)
        }
    }
}
