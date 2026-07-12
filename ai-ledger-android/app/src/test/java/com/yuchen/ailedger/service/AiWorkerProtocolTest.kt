package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkerProtocolTest {
    @Test
    fun clientToolContinuationReadWindowCoversProjectCompiler() {
        assertEquals(15_000, AI_WORKER_DEFAULT_CONNECT_TIMEOUT_MS)
        assertEquals(240_000, AI_WORKER_DEFAULT_READ_TIMEOUT_MS)
        assertTrue(AI_WORKER_DEFAULT_READ_TIMEOUT_MS > 210_000)
    }
}
