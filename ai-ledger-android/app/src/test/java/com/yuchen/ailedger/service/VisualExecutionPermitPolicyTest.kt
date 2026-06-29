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

    @Test
    fun deployedV177PermitVectorMatchesAndroid() {
        val sessionId = "agent_3fae489c1b9f4e294cf44d4403b6280a"
        val observationId = "f046c6fe7c6da21956eea4e0"
        val packageName = "com.hexin.plat.android"
        val kind = "independent_gui_visual_grounding"
        val step = CloudAgentStep(
            type = "tap_xy",
            targetText = "点击屏幕顶部显示“太极实业最新要闻”的搜索栏，以激活搜索输入框。",
            x = 0.34f,
            y = 0.072f,
        )

        val hash = VisualActionValidator.executionPermitHash(
            sessionId = sessionId,
            observationId = observationId,
            packageName = packageName,
            kind = kind,
            step = step,
            canonicalX = 0.34,
            canonicalY = 0.072,
        )

        assertEquals("48ca39e5afd8210a1a8ba045", hash)
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
        assertEquals("89e9abcea78137f88bc00916", hash)
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
