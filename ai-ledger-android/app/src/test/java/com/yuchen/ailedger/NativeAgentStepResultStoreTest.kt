package com.yuchen.ailedger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAgentStepResultStoreTest {
    @Test
    fun pendingResultBecomesReadyAndIsConsumedOnce() {
        val store = NativeAgentStepResultStore(maxEntries = 4)
        val requestId = store.createRequestId("order step 1")

        store.start(requestId)
        assertEquals(NativeAgentStepPollResult.Pending, store.poll(requestId))

        store.complete(requestId, "{\"ok\":true}")
        val ready = store.poll(requestId)
        assertTrue(ready is NativeAgentStepPollResult.Ready)
        assertEquals("{\"ok\":true}", (ready as NativeAgentStepPollResult.Ready).payload)
        assertEquals(NativeAgentStepPollResult.Missing, store.poll(requestId))
    }

    @Test
    fun storeKeepsOnlyBoundedNewestRequests() {
        val store = NativeAgentStepResultStore(maxEntries = 2)
        store.start("one")
        store.start("two")
        store.start("three")

        assertEquals(NativeAgentStepPollResult.Missing, store.poll("one"))
        assertEquals(NativeAgentStepPollResult.Pending, store.poll("two"))
        assertEquals(NativeAgentStepPollResult.Pending, store.poll("three"))
    }

    @Test
    fun requestIdIsSanitized() {
        val store = NativeAgentStepResultStore()
        assertEquals("order-step-1", store.createRequestId(" order step 1 "))
    }
}
