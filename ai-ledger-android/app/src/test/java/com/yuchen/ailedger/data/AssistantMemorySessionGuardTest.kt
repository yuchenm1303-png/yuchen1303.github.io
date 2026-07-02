package com.yuchen.ailedger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemorySessionGuardTest {
    @Test
    fun sameUserKeepsGenerationAcrossTokenRefreshes() {
        val guard = AssistantMemorySessionGuard()
        val first = guard.updateUser("user-a")
        val refreshed = guard.updateUser("user-a")

        assertNotNull(first)
        assertEquals(first, refreshed)
        assertTrue(guard.isCurrent(requireNotNull(first)))
    }

    @Test
    fun accountSwitchInvalidatesOlderTicket() {
        val guard = AssistantMemorySessionGuard()
        val first = requireNotNull(guard.updateUser("user-a"))
        val second = requireNotNull(guard.updateUser("user-b"))

        assertFalse(guard.isCurrent(first))
        assertTrue(guard.isCurrent(second))
        assertEquals("user-b", second.userId)
    }

    @Test
    fun logoutInvalidatesCurrentTicket() {
        val guard = AssistantMemorySessionGuard()
        val ticket = requireNotNull(guard.updateUser("user-a"))

        assertNull(guard.updateUser(null))
        assertFalse(guard.isCurrent(ticket))
        assertNull(guard.currentTicket("user-a"))
    }
}
