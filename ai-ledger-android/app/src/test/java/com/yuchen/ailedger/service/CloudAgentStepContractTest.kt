package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAgentStepContractTest {
    @Test
    fun protocolUsesSingleSourceOfTruth() {
        assertEquals(CloudAgentStep.supportedTypes, VisualAgentProtocol.supportedStepTypes)
    }

    @Test
    fun legacyPayloadParsesWithoutSemanticFields() {
        val json = JSONObject()
        json.put("type", "tap_xy")
        json.put("x", 0.4)
        json.put("y", 0.7)
        val step = CloudAgentStep.fromJson(json)!!
        assertTrue(step.legacyIntent)
        assertEquals(emptyList<String>(), step.expectedEvidence)
    }
}
