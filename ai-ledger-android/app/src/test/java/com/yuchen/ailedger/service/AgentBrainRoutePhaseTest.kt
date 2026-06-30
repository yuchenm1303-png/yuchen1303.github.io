package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentBrainRoutePhaseTest {
    private val contract = VisualTaskContract(
        originalGoal = "打开目标应用并执行视觉任务",
        currentMilestoneId = "operate",
        milestones = listOf(
            VisualTaskMilestone(id = "open", title = "打开应用", successEvidence = listOf("目标应用前台")),
            VisualTaskMilestone(id = "operate", title = "执行任务", successEvidence = listOf("目标页面完成")),
        ),
    )

    @Test
    fun missingContractStartsAgentBrainPlanning() {
        assertTrue(shouldUseAgentBrainRoute(taskContract = null, runtimeContext = null))
    }

    @Test
    fun committedContractWithoutWorkSurfaceReplaysHandoffRoute() {
        assertTrue(
            shouldUseAgentBrainRoute(
                taskContract = contract,
                runtimeContext = runtime(VisualSurfaceState.Launching, eligible = false),
            ),
        )
    }

    @Test
    fun verifiedWorkSurfaceEntersGuiPlusDirectly() {
        assertFalse(
            shouldUseAgentBrainRoute(
                taskContract = contract,
                runtimeContext = runtime(VisualSurfaceState.WorkSurface, eligible = true),
            ),
        )
    }

    private fun runtime(state: VisualSurfaceState, eligible: Boolean) = VisualAgentRuntimeContext(
        surfaceState = state,
        selectedTargetPackage = "com.example.target",
        verifiedTargetPackage = if (eligible) "com.example.target" else "",
        currentPackage = "com.example.target",
        observationId = "observation",
        routeEpoch = 0L,
        surfaceEpoch = 0L,
        guiPlusEligible = eligible,
    )
}
