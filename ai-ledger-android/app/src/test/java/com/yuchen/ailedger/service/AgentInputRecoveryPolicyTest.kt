package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentInputRecoveryPolicyTest {
    @Test
    fun focusedDirectInputFailureReturnsVisualRetryInsteadOfUserPromptFailure() {
        val step = CloudAgentStep.fromJson(
            JSONObject().apply {
                put("type", "input_text")
                put("text", "纳斯达克指数")
                put("inputMode", "focused_direct")
                put("requiresInputNode", false)
                put("expectsFocusedInput", true)
                put("useFocusedInput", true)
            },
        )!!

        val result = AgentInputRecoveryPolicy.onInputFailure(step, candidateCount = 0)

        assertTrue(step.shouldUseFocusedDirectInput)
        assertTrue(result.ok)
        assertTrue(result.shouldContinue)
        assertTrue(result.message.contains("visual_action_retry:type=input_text"))
        assertTrue(result.message.contains("focused_input_unavailable"))
    }

    @Test
    fun nodeRequiredInputFailureRemainsHardFailure() {
        val step = CloudAgentStep(
            type = "input_text",
            text = "测试",
            requiresInputNode = true,
        )

        val result = AgentInputRecoveryPolicy.onInputFailure(step, candidateCount = 2)

        assertFalse(step.shouldUseFocusedDirectInput)
        assertFalse(result.ok)
        assertFalse(result.shouldContinue)
        assertTrue(result.message.contains("SET_TEXT"))
    }
}
