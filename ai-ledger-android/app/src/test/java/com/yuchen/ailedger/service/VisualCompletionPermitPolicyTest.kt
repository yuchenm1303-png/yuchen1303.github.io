package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualCompletionPermitPolicyTest {
    @Test
    fun candidateIsBoundToCurrentSessionAndObservation() {
        val result = VisualCompletionPermitPolicy.candidate(
            candidateStep(),
            expectedSessionId = SESSION_ID,
            expectedObservationId = CANDIDATE_OBSERVATION_ID,
        )

        assertTrue(result.valid)
        assertEquals(CANDIDATE_ID, result.value?.id)
    }

    @Test
    fun completionPermitRequiresFreshObservationAndMatchingCandidate() {
        val candidate = VisualCompletionPermitPolicy.candidate(
            candidateStep(),
            SESSION_ID,
            CANDIDATE_OBSERVATION_ID,
        ).value!!

        val result = VisualCompletionPermitPolicy.permit(
            permitStep(),
            expectedSessionId = SESSION_ID,
            expectedObservationId = PERMIT_OBSERVATION_ID,
            expectedCandidate = candidate,
        )

        assertTrue(result.valid)
        assertEquals("verified", result.reason)
        assertEquals(0.92, result.value?.confidence ?: 0.0, 0.0001)
    }

    @Test
    fun userRevisionInvalidatesOlderCompletionCandidate() {
        val candidate = VisualCompletionPermitPolicy.candidate(
            step = candidateStep(),
            expectedSessionId = SESSION_ID,
            expectedObservationId = CANDIDATE_OBSERVATION_ID,
            candidateTaskRevision = 3,
        ).value!!

        val result = VisualCompletionPermitPolicy.permit(
            step = permitStep(),
            expectedSessionId = SESSION_ID,
            expectedObservationId = PERMIT_OBSERVATION_ID,
            expectedCandidate = candidate,
            currentTaskRevision = 4,
        )

        assertFalse(result.valid)
        assertEquals("completion_candidate_invalidated_by_user_revision", result.reason)
    }

    @Test
    fun lowConfidenceCompletionIsRejected() {
        val candidate = VisualCompletionCandidate(CANDIDATE_ID, SESSION_ID, CANDIDATE_OBSERVATION_ID)
        val args = JSONObject(permitStep().toolArgs.toString()).apply {
            put("completionVerifierConfidence", 0.79)
        }

        val result = VisualCompletionPermitPolicy.permit(
            permitStep().copy(toolArgs = args),
            SESSION_ID,
            PERMIT_OBSERVATION_ID,
            candidate,
        )

        assertFalse(result.valid)
        assertEquals("completion_verifier_confidence_low", result.reason)
    }

    @Test
    fun reusedCandidateFrameIsRejected() {
        val candidate = VisualCompletionCandidate(CANDIDATE_ID, SESSION_ID, CANDIDATE_OBSERVATION_ID)
        val sameFrame = permitStep(currentObservationId = CANDIDATE_OBSERVATION_ID)

        val result = VisualCompletionPermitPolicy.permit(
            sameFrame,
            SESSION_ID,
            CANDIDATE_OBSERVATION_ID,
            candidate,
        )

        assertFalse(result.valid)
        assertEquals("completion_permit_requires_fresh_observation", result.reason)
    }

    @Test
    fun candidateMutationBreaksPermitHash() {
        val candidate = VisualCompletionCandidate("other-candidate", SESSION_ID, CANDIDATE_OBSERVATION_ID)

        val result = VisualCompletionPermitPolicy.permit(
            permitStep(),
            SESSION_ID,
            PERMIT_OBSERVATION_ID,
            candidate,
        )

        assertFalse(result.valid)
        assertEquals("completion_candidate_binding_mismatch", result.reason)
    }

    private fun candidateStep(): CloudAgentStep = CloudAgentStep(
        type = "finish",
        toolArgs = JSONObject().apply {
            put("responseSessionId", SESSION_ID)
            put("responseObservationId", CANDIDATE_OBSERVATION_ID)
            put("completionCandidate", true)
            put("completionCandidateId", CANDIDATE_ID)
            put("completionCandidateSessionId", SESSION_ID)
            put("completionCandidateObservationId", CANDIDATE_OBSERVATION_ID)
        },
    )

    private fun permitStep(currentObservationId: String = PERMIT_OBSERVATION_ID): CloudAgentStep {
        val hash = VisualCompletionPermitPolicy.completionPermitHash(
            sessionId = SESSION_ID,
            candidateId = CANDIDATE_ID,
            candidateObservationId = CANDIDATE_OBSERVATION_ID,
            observationId = currentObservationId,
        )
        return CloudAgentStep(
            type = "finish",
            toolArgs = JSONObject().apply {
                put("responseSessionId", SESSION_ID)
                put("responseObservationId", currentObservationId)
                put("completionPermitId", "completion_permit_$hash")
                put("completionPermitKind", "independent_gui_completion_verification")
                put("completionPermitObservationId", currentObservationId)
                put("completionPermitSessionId", SESSION_ID)
                put("completionPermitActionType", "finish")
                put("completionPermitActionHash", hash)
                put("completionCandidateId", CANDIDATE_ID)
                put("completionCandidateObservationId", CANDIDATE_OBSERVATION_ID)
                put("completionVerifierVerdict", "confirmed")
                put("completionVerifierConfidence", 0.92)
            },
        )
    }

    companion object {
        private const val SESSION_ID = "visual-session-123"
        private const val CANDIDATE_ID = "completion-candidate-123"
        private const val CANDIDATE_OBSERVATION_ID = "observation-candidate"
        private const val PERMIT_OBSERVATION_ID = "observation-verification"
    }
}
