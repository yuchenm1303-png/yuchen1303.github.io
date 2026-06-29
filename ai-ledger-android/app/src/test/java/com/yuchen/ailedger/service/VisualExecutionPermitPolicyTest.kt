package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualExecutionPermitPolicyTest {
    @Test
    fun unifiedGateAcceptsValidCloudPermit() {
        val fixture = permittedTap()

        val result = VisualActionValidator.validatePermit(fixture.step, fixture.snapshot, fixture.runtime)

        assertTrue(result.valid)
        assertEquals("verified", result.reason)
    }

    @Test
    fun unifiedGateRejectsMissingPermit() {
        val fixture = permittedTap()

        val result = VisualActionValidator.validatePermit(
            fixture.step.copy(toolArgs = null),
            fixture.snapshot,
            fixture.runtime,
        )

        assertFalse(result.valid)
        assertEquals("missing_permit_args", result.reason)
    }

    @Test
    fun observationMismatchIsRejected() {
        val fixture = permittedTap()
        val args = JSONObject(fixture.step.toolArgs.toString()).apply {
            put("responseObservationId", "other-observation")
        }

        val result = VisualActionValidator.validatePermit(
            fixture.step.copy(toolArgs = args),
            fixture.snapshot,
            fixture.runtime,
        )

        assertFalse(result.valid)
        assertEquals("permit_observation_mismatch", result.reason)
    }

    @Test
    fun sessionMismatchIsRejected() {
        val fixture = permittedTap()
        val args = JSONObject(fixture.step.toolArgs.toString()).apply {
            put("responseSessionId", "other-session")
        }

        val result = VisualActionValidator.validatePermit(
            fixture.step.copy(toolArgs = args),
            fixture.snapshot,
            fixture.runtime,
        )

        assertFalse(result.valid)
        assertEquals("permit_session_mismatch", result.reason)
    }

    @Test
    fun packageMismatchIsRejected() {
        val fixture = permittedTap()
        val args = JSONObject(fixture.step.toolArgs.toString()).apply {
            put("executionPermitPackageName", "com.other.app")
        }

        val result = VisualActionValidator.validatePermit(
            fixture.step.copy(toolArgs = args),
            fixture.snapshot,
            fixture.runtime,
        )

        assertFalse(result.valid)
        assertEquals("permit_package_mismatch", result.reason)
    }

    @Test
    fun coordinateMutationIsRejected() {
        val fixture = permittedTap()

        val result = VisualActionValidator.validatePermit(
            fixture.step.copy(x = 0.51f),
            fixture.snapshot,
            fixture.runtime,
        )

        assertFalse(result.valid)
        assertEquals("permit_coordinate_mismatch", result.reason)
    }

    @Test
    fun forgedHashIsRejected() {
        val fixture = permittedTap()
        val args = JSONObject(fixture.step.toolArgs.toString()).apply {
            put("executionPermitActionHash", "forged")
            put("executionPermitId", "permit_forged")
        }

        val result = VisualActionValidator.validatePermit(
            fixture.step.copy(toolArgs = args),
            fixture.snapshot,
            fixture.runtime,
        )

        assertFalse(result.valid)
        assertEquals("permit_hash_mismatch", result.reason)
    }

    private fun permittedTap(): PermitFixture {
        val sessionId = "visual-session-123"
        val observationId = "observation-456"
        val packageName = "com.example.app"
        val kind = "gui_transaction_validated"
        val x = 0.5
        val y = 0.25
        val baseStep = CloudAgentStep(
            type = "tap_xy",
            targetText = "行情",
            x = x.toFloat(),
            y = y.toFloat(),
        )
        val hash = VisualActionValidator.executionPermitHash(
            sessionId = sessionId,
            observationId = observationId,
            packageName = packageName,
            kind = kind,
            step = baseStep,
            canonicalX = x,
            canonicalY = y,
        )
        val step = baseStep.copy(
            toolArgs = JSONObject().apply {
                put("responseObservationId", observationId)
                put("responseSessionId", sessionId)
                put("executionPermitVersion", "visual_execution_permit_v2")
                put("executionPermitId", "permit_$hash")
                put("executionPermitKind", kind)
                put("executionPermitObservationId", observationId)
                put("executionPermitSessionId", sessionId)
                put("executionPermitPackageName", packageName)
                put("executionPermitActionType", "tap_xy")
                put("executionPermitX", x)
                put("executionPermitY", y)
                put("executionPermitActionHash", hash)
            },
        )
        val snapshot = AgentScreenSnapshot(
            currentApp = packageName,
            packageName = packageName,
            nodeCount = 0,
            capturedNodeCount = 0,
            texts = emptyList(),
            allNodes = emptyList(),
            clickableNodes = emptyList(),
            inputNodes = emptyList(),
            scrollableNodes = emptyList(),
        )
        val runtime = VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.WorkSurface,
            selectedTargetPackage = packageName,
            verifiedTargetPackage = packageName,
            currentPackage = packageName,
            observationId = observationId,
            guiPlusEligible = true,
        )
        return PermitFixture(step, snapshot, runtime)
    }

    private data class PermitFixture(
        val step: CloudAgentStep,
        val snapshot: AgentScreenSnapshot,
        val runtime: VisualAgentRuntimeContext,
    )
}
