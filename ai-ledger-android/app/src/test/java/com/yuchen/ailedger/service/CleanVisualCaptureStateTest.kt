package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanVisualCaptureStateTest {
    @Test
    fun nestedLeaseReusesOriginalSettleClock() {
        var now = 100L
        val state = CleanVisualCaptureState { now }

        assertTrue(state.acquire())
        now = 180L
        assertFalse(state.acquire())
        assertEquals(70L, state.settleRemaining(150L))

        assertFalse(state.release())
        now = 260L
        assertEquals(0L, state.settleRemaining(150L))
        assertTrue(state.release())
        assertFalse(state.active)
    }

    @Test
    fun resetClearsAllNestedLeases() {
        var now = 0L
        val state = CleanVisualCaptureState { now }
        state.acquire()
        state.acquire()
        now = 50L

        assertTrue(state.reset())
        assertFalse(state.active)
        assertEquals(90L, state.settleRemaining(90L))
    }

    @Test
    fun leaseClosesOnlyOnce() {
        var releases = 0
        val lease = CleanVisualCaptureLease { releases += 1 }

        lease.close()
        lease.close()

        assertEquals(1, releases)
    }
}
