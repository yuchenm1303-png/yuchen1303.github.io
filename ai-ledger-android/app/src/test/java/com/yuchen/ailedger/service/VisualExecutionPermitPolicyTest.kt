package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualExecutionPermitPolicyTest {
    @Test
    fun validPermitIsAccepted() {
        val step = permittedTap()

        val result = VisualExecutionPermitPolicy.validateTap(step)

        assertTrue(result.valid)
        assertEquals("verified", result.reason)
    }

    @Test
    fun observationMismatchIsRejected() {
        val step = permittedTap().copy(
            toolArgs = permittedTap().toolArgs!!.apply {
                put("responseObservationId", "other-observation")
            },
        )

        val result = VisualExecutionPermitPolicy.validateTap(step)

        assertFalse(result.valid)
        assertEquals("permit_observation_mismatch", result.reason)
    }

    @Test
    fun sessionMismatchIsRejected() {
        val step = permittedTap().copy(
            toolArgs = permittedTap().toolArgs!!.apply {
                put("responseSessionId", "other-session")
            },
        )

        val result = VisualExecutionPermitPolicy.validateTap(step)

        assertFalse(result.valid)
        assertEquals("permit_session_mismatch", result.reason)
    }

    @Test
    fun coordinateMutationIsRejected() {
        val step = permittedTap().copy(x = 0.51f)

        val result = VisualExecutionPermitPolicy.validateTap(step)

        assertFalse(result.valid)
        assertEquals("permit_coordinate_mismatch", result.reason)
    }

    @Test
    fun forgedHashIsRejected() {
        val base = permittedTap()
        val args = JSONObject(base.toolArgs.toString()).apply {
            put("executionPermitActionHash", "forged")
            put("executionPermitId", "permit_forged")
        }

        val result = VisualExecutionPermitPolicy.validateTap(base.copy(toolArgs = args))

        assertFalse(result.valid)
        assertEquals("permit_hash_mismatch", result.reason)
    }

    private fun permittedTap(
        x: Float = 0.5f,
        y: Float = 0.25f,
        kind: String = "android_structural_clickable_anchor",
    ): CloudAgentStep {
        val sessionId = "visual-session-123"
        val observationId = "observation-456"
        val hash = VisualExecutionPermitPolicy.tapPermitHash(sessionId, observationId, x, y, kind)
        return CloudAgentStep(
            type = "tap_xy",
            x = x,
            y = y,
            toolArgs = JSONObject().apply {
                put("responseObservationId", observationId)
                put("responseSessionId", sessionId)
                put("executionPermitId", "permit_$hash")
                put("executionPermitKind", kind)
                put("executionPermitObservationId", observationId)
                put("executionPermitSessionId", sessionId)
                put("executionPermitActionType", "tap_xy")
                put("executionPermitX", x)
                put("executionPermitY", y)
                put("executionPermitActionHash", hash)
            },
        )
    }
}
