package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSafetyPolicyTest {
    @Test
    fun explicitCloudConfirmationMetadataIsAlwaysEnforced() {
        val explicit = CloudAgentStep(
            type = "tap_xy",
            x = 0.5f,
            y = 0.5f,
            riskLevel = "financial_transaction",
            requiresConfirmation = true,
        )

        assertTrue(AgentSafetyPolicy.requiresConfirmation("任意目标", explicit))
        assertFalse(AgentSafetyPolicy.canAutoExecuteInCurrentStage("任意目标", explicit))
    }

    @Test
    fun riskLevelWordsAloneDoNotTriggerLocalSemanticInference() {
        val labelOnly = CloudAgentStep(
            type = "tap_xy",
            x = 0.5f,
            y = 0.5f,
            targetText = "payment password documentation",
            riskLevel = "high",
            requiresConfirmation = false,
        )

        assertFalse(AgentSafetyPolicy.requiresConfirmation("search for the definition of password", labelOnly))
        assertTrue(AgentSafetyPolicy.canAutoExecuteInCurrentStage("search for the definition of password", labelOnly))
    }

    @Test
    fun ordinaryNavigationRemainsAutoExecutable() {
        val navigation = CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f, riskLevel = "low")

        assertFalse(AgentSafetyPolicy.requiresConfirmation("任意目标", navigation))
        assertTrue(AgentSafetyPolicy.canAutoExecuteInCurrentStage("任意目标", navigation))
    }
}
