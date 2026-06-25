package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudAgentStepContractTest {
    @Test
    fun protocolUsesSingleSourceOfTruth() {
        assertEquals(CloudAgentStep.supportedTypes, VisualAgentProtocol.supportedStepTypes)
    }
}
