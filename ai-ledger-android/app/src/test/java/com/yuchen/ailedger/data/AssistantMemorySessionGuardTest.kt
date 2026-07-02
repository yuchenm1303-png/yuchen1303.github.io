package com.yuchen.ailedger.data

import com.yuchen.ailedger.service.SupabaseUserSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssistantMemorySessionGuardTest {
    @Before
    fun resetBefore() {
        AssistantAccountSessionRuntime.updateUser(null)
        AssistantMemoryRequestContextRuntime.clearCurrentThread()
    }

    @After
    fun resetAfter() {
        AssistantAccountSessionRuntime.updateUser(null)
        AssistantMemoryRequestContextRuntime.clearCurrentThread()
    }

    @Test
    fun sameUserKeepsGenerationAcrossTokenRefreshes() {
        val first = AssistantAccountSessionRuntime.updateUser("user-a")
        val refreshed = AssistantAccountSessionRuntime.updateUser("user-a")

        assertNotNull(first)
        assertEquals(first, refreshed)
        assertTrue(AssistantAccountSessionRuntime.isCurrent(requireNotNull(first)))
    }

    @Test
    fun accountSwitchInvalidatesOlderTicket() {
        val first = requireNotNull(AssistantAccountSessionRuntime.updateUser("user-a"))
        val second = requireNotNull(AssistantAccountSessionRuntime.updateUser("user-b"))

        assertFalse(AssistantAccountSessionRuntime.isCurrent(first))
        assertTrue(AssistantAccountSessionRuntime.isCurrent(second))
        assertEquals("user-b", second.userId)
    }

    @Test
    fun logoutInvalidatesCurrentTicket() {
        val ticket = requireNotNull(AssistantAccountSessionRuntime.updateUser("user-a"))

        assertNull(AssistantAccountSessionRuntime.updateUser(null))
        assertFalse(AssistantAccountSessionRuntime.isCurrent(ticket))
        assertNull(AssistantAccountSessionRuntime.currentTicket("user-a"))
    }

    @Test
    fun oldFinallyCannotReleaseNewAccountsOperationOwner() {
        val gate = AssistantOperationGate()
        val ticketA = requireNotNull(AssistantAccountSessionRuntime.updateUser("user-a"))
        val ownerA = requireNotNull(gate.tryAcquire(ticketA))

        val ticketB = requireNotNull(AssistantAccountSessionRuntime.updateUser("user-b"))
        gate.invalidateOwnersNotMatching(ticketB)
        val ownerB = requireNotNull(gate.tryAcquire(ticketB))

        gate.release(ownerA)
        assertNull(gate.tryAcquire(ticketB))

        gate.release(ownerB)
        assertNotNull(gate.tryAcquire(ticketB))
    }

    @Test
    fun requestContextBindsTheAccessTokenFromRequestCreationTime() {
        val sessionA = SupabaseUserSession(
            userId = "user-a",
            email = "a@example.com",
            accessToken = "token-a",
            refreshToken = "refresh-a",
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + 3600L,
        )
        val ticketA = requireNotNull(AssistantAccountSessionRuntime.updateSession(sessionA))
        val context = AssistantMemoryRequestContextRuntime.stageCurrentThread()

        AssistantAccountSessionRuntime.updateUser("user-b")

        assertEquals(ticketA, context.ticket)
        assertEquals("token-a", context.userAccessToken)
        assertFalse(AssistantAccountSessionRuntime.isCurrent(ticketA))
    }

    @Test
    fun requestContextKeepsInitiatingAccountTicket() {
        val ticketA = requireNotNull(AssistantAccountSessionRuntime.updateUser("user-a"))
        val context = AssistantMemoryRequestContextRuntime.stageCurrentThread()
        assertEquals(ticketA, context.ticket)

        AssistantAccountSessionRuntime.updateUser("user-b")
        val consumed = AssistantMemoryRequestContextRuntime.consumeCurrentThread()
        assertEquals(ticketA, consumed?.ticket)
        assertFalse(AssistantAccountSessionRuntime.isCurrent(requireNotNull(consumed?.ticket)))
    }
}
