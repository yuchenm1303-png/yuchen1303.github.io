package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEntryRetryPolicyTest {
    @Test
    fun deepSeekEntryFailureRetriesBeforeGuiInteraction() {
        val result = AgentTaskRunResult(
            completed = false,
            stoppedForConfirmation = false,
            message = "visual_agent_step failed: DeepSeek 主脑路由失败：DeepSeek AgentBrain Route Step timeout",
            logs = listOf(
                AgentTaskStepLog(
                    index = 1,
                    app = "com.yuchen.ailedger",
                    step = CloudAgentStep(type = "open_app", packageName = "com.hexin.plat.android"),
                    execution = AgentExecutionResult(true, "已打开同花顺"),
                ),
            ),
        )

        assertTrue(AgentEntryRetryPolicy.shouldRetry(result))
    }

    @Test
    fun routeFailureDoesNotReplayAfterTapOrInput() {
        val tapped = AgentTaskRunResult(
            completed = false,
            stoppedForConfirmation = false,
            message = "visual_agent_step failed: agent_brain_route_failed timeout",
            logs = listOf(
                AgentTaskStepLog(
                    index = 1,
                    app = "com.hexin.plat.android",
                    step = CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f),
                    execution = AgentExecutionResult(true, "已点击"),
                ),
            ),
        )

        assertFalse(AgentEntryRetryPolicy.shouldRetry(tapped))
    }

    @Test
    fun unrelatedFailureIsNotRetried() {
        val result = AgentTaskRunResult(
            completed = false,
            stoppedForConfirmation = false,
            message = "无障碍服务未连接",
            logs = emptyList(),
        )

        assertFalse(AgentEntryRetryPolicy.shouldRetry(result))
    }
}
