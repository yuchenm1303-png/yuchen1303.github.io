package com.yuchen.ailedger.service

import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAgentResponseProtocolTest {
    @Test
    fun matchingObservationIdIsAcceptedAcrossProtocolEnvelopes() {
        val response = JSONObject().apply {
            put("expectedActionObservationId", "obs-123")
            put("verifiedSurfaceProtocol", JSONObject().apply {
                put("observationId", "obs-123")
            })
            put("runtimeExecutionContext", JSONObject().apply {
                put("observationId", "obs-123")
            })
        }

        assertEquals("obs-123", validateVisualAgentResponseObservationId("obs-123", response))
    }

    @Test
    fun nestedResponseEnvelopeIsAccepted() {
        val response = JSONObject().apply {
            put("data", JSONObject().apply {
                put("actionObservationId", "obs-nested")
            })
        }

        assertEquals("obs-nested", validateVisualAgentResponseObservationId("obs-nested", response))
    }

    @Test
    fun missingStaleOrConflictingObservationIdsAreRejected() {
        val missing = runCatching {
            validateVisualAgentResponseObservationId("obs-123", JSONObject())
        }.exceptionOrNull()
        val stale = runCatching {
            validateVisualAgentResponseObservationId(
                "obs-123",
                JSONObject().put("observationId", "obs-old"),
            )
        }.exceptionOrNull()
        val conflicting = runCatching {
            validateVisualAgentResponseObservationId(
                "obs-123",
                JSONObject()
                    .put("expectedActionObservationId", "obs-123")
                    .put("observationId", "obs-other"),
            )
        }.exceptionOrNull()

        assertTrue(missing is IOException)
        assertTrue(stale is IOException)
        assertTrue(conflicting is IOException)
    }
}
