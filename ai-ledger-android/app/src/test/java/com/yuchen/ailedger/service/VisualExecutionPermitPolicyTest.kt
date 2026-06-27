package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualExecutionPermitPolicyTest {
    @Test
    fun validPermitIsAccepted() {
        val result = VisualExecutionPermitPolicy.validateTap(permittedTap())

        assertTrue(result.valid)
        assertEquals("verified", result.reason)
    }

    @Test
    fun sixDecimalBackendCoordinateSurvivesFloatParsing() {
        val result = VisualExecutionPermitPolicy.validateTap(
            permittedTap(
                permitX = 0.826107,
                permitY = 0.173893,
            ),
        )

        assertTrue(result.valid)
        assertEquals("verified", result.reason)
    }

    @Test
    fun observationMismatchIsRejected() {
        val base = permittedTap()
        val args = JSONObject(base.toolArgs.toString()).apply {
            put("responseObservationId", "other-observation")
        }

        val result = VisualExecutionPermitPolicy.validateTap(base.copy(toolArgs = args))

        assertFalse(result.valid)
        assertEquals("permit_observation_mismatch", result.reason)
    }

    @Test
    fun sessionMismatchIsRejected() {
        val base = permittedTap()
        val args = JSONObject(base.toolArgs.toString()).apply {
            put("responseSessionId", "other-session")
        }

        val result = VisualExecutionPermitPolicy.validateTap(base.copy(toolArgs = args))

        assertFalse(result.valid)
        assertEquals("permit_session_mismatch", result.reason)
    }

    @Test
    fun coordinateMutationIsRejected() {
        val result = VisualExecutionPermitPolicy.validateTap(permittedTap().copy(x = 0.51f))

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
        permitX: Double = 0.5,
        permitY: Double = 0.25,
        kind: String = "android_structural_clickable_anchor",
    ): CloudAgentStep {
        val sessionId = "visual-session-123"
        val observationId = "observation-456"
        val hash = VisualExecutionPermitPolicy.tapPermitHash(
            sessionId,
            observationId,
            permitX,
            permitY,
            kind,
        )
        return CloudAgentStep(
            type = "tap_xy",
            x = permitX.toFloat(),
            y = permitY.toFloat(),
            toolArgs = JSONObject().apply {
                put("responseObservationId", observationId)
                put("responseSessionId", sessionId)
                put("executionPermitId", "permit_$hash")
                put("executionPermitKind", kind)
                put("executionPermitObservationId", observationId)
                put("executionPermitSessionId", sessionId)
                put("executionPermitActionType", "tap_xy")
                put("executionPermitX", permitX)
                put("executionPermitY", permitY)
                put("executionPermitActionHash", hash)
            },
        )
    }
}
