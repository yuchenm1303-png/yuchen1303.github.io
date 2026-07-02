package com.yuchen.ailedger.service

import com.yuchen.ailedger.data.AssistantAccountSessionRuntime
import com.yuchen.ailedger.data.AssistantMemoryRequestContextRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class AiWorkerRequestIdentityTest {
    @Before
    fun resetBefore() {
        AssistantMemoryRequestContextRuntime.clearCurrentThread()
        AssistantAccountSessionRuntime.updateUser(null)
    }

    @After
    fun resetAfter() {
        AssistantMemoryRequestContextRuntime.clearCurrentThread()
        AssistantAccountSessionRuntime.updateUser(null)
    }

    @Test
    fun anonymousRequestDoesNotAdoptAccountLoggedInAfterRequestCreation() {
        AssistantMemoryRequestContextRuntime.stageCurrentThread()
        AssistantAccountSessionRuntime.updateSession(session("user-b", "token-b"))

        val headers = AiWorkerRequestIdentity.headers(appClientToken = "app-token")

        assertFalse(headers.containsKey("Authorization"))
        assertEquals("app-token", headers["X-AI-Ledger-Token"])
    }

    @Test
    fun existingRequestKeepsItsOriginalAccountTokenAcrossAccountSwitch() {
        AssistantAccountSessionRuntime.updateSession(session("user-a", "token-a"))
        AssistantMemoryRequestContextRuntime.stageCurrentThread()
        AssistantAccountSessionRuntime.updateSession(session("user-b", "token-b"))

        val headers = AiWorkerRequestIdentity.headers(appClientToken = "app-token")

        assertEquals("Bearer token-a", headers["Authorization"])
    }

    private fun session(userId: String, accessToken: String) = SupabaseUserSession(
        userId = userId,
        email = "$userId@example.com",
        accessToken = accessToken,
        refreshToken = "refresh-$userId",
        expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + 3600L,
    )
}
