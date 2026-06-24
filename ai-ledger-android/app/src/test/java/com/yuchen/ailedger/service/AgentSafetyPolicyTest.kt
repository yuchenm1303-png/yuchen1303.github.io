package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSafetyPolicyTest {
    @Test
    fun structuredHighRiskMetadataAlwaysRequiresConfirmation() {
        val high = CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f, riskLevel = "high")
        val financial = CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f, riskLevel = "financial_transaction")
        val explicit = CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f, requiresConfirmation = true)

        assertTrue(AgentSafetyPolicy.requiresConfirmation("任意目标", high))
        assertTrue(AgentSafetyPolicy.requiresConfirmation("任意目标", financial))
        assertTrue(AgentSafetyPolicy.requiresConfirmation("任意目标", explicit))
        assertFalse(AgentSafetyPolicy.canAutoExecuteInCurrentStage("任意目标", high))
    }

    @Test
    fun ordinaryNavigationRemainsAutoExecutableWithoutLocalSemanticGuessing() {
        val navigation = CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f, riskLevel = "low")

        assertFalse(AgentSafetyPolicy.requiresConfirmation("任意目标", navigation))
        assertTrue(AgentSafetyPolicy.canAutoExecuteInCurrentStage("任意目标", navigation))
    }
}
