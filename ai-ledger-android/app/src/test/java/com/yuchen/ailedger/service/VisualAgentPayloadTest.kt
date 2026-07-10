package com.yuchen.ailedger.service

import org.junit.Assert.assertNull
import org.junit.Test

class VisualAgentPayloadTest {
    @Test
    fun visualClientToolCallQueueStartsEmpty() {
        val client = AiWorkerClient(
            AiWorkerConfig(
                endpoint = "https://example.com",
                fallbackEndpoints = emptyList(),
                clientId = "visual-payload-test",
            )
        )

        client.clearVisualClientToolCalls()
        assertNull(client.consumeVisualClientToolCall())
    }
}
