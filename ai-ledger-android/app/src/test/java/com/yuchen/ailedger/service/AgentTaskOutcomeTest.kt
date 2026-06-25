package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTaskOutcomeTest {
    @Test
    fun failedOutcomeIsNotPresentedAsPaused() {
        val presentation = AgentTaskOutcome.Failed("route failed").toTerminalPresentation()

        assertEquals("执行失败", presentation.status)
        assertEquals("任务异常", presentation.currentAction)
        assertEquals("失败", presentation.logPrefix)
    }

    @Test
    fun budgetOutcomeHasIndependentPresentation() {
        val presentation = AgentTaskOutcome.BudgetExceeded("budget reached").toTerminalPresentation()

        assertEquals("已达上限", presentation.status)
        assertEquals("达到执行上限", presentation.currentAction)
        assertEquals("上限", presentation.logPrefix)
    }

    @Test
    fun cancelledOutcomeKeepsManualStopSemantics() {
        val presentation = AgentTaskOutcome.Cancelled("").toTerminalPresentation()

        assertEquals("已手动停止", presentation.status)
        assertEquals("用户手动停止了本次智能体任务。", presentation.defaultMessage)
    }

    @Test
    fun routeFailureFromLegacyBooleanApiResolvesToFailed() {
        val outcome = AgentTaskOutcomeResolver.resolveLegacyCompletion(
            completed = false,
            message = "visual_agent_step failed: DeepSeek AgentBrain Route Step",
        )

        assertTrue(outcome is AgentTaskOutcome.Failed)
    }

    @Test
    fun explicitPauseMessageStillResolvesToPaused() {
        val outcome = AgentTaskOutcomeResolver.resolveLegacyCompletion(
            completed = false,
            message = "当前动作未收敛，智能体已暂停等待用户接管。",
        )

        assertTrue(outcome is AgentTaskOutcome.Paused)
    }

    @Test
    fun budgetMessageDoesNotFallBackToPaused() {
        val outcome = AgentTaskOutcomeResolver.resolveLegacyCompletion(
            completed = false,
            message = "Visual loop reached planning budget.",
        )

        assertTrue(outcome is AgentTaskOutcome.BudgetExceeded)
    }
}
