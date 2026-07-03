package com.yuchen.ailedger.service

import org.junit.Assert.assertNull
import org.junit.Test

class VisualAgentPayloadTest {
    @Test
    fun pendingVisualCallIsSingleUse() {
        ClientToolCallRegistry.clearVisual()
        assertNull(ClientToolCallRegistry.consumeVisual())
    }
}
