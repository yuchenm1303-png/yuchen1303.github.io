package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualIntelligenceUpgradeTest {
    @Test
    fun malformedMobileUseHelpIsRejectedForSafeReplan() {
        val validation = VisualActionValidator.validate(
            step = CloudAgentStep(type = "need_user_help", reason = "未知 mobile_use action=completed。"),
            snapshot = snapshot("page-a"),
            runtime = workSurfaceRuntime(),
        )
        assertFalse(validation.ok)
        assertEquals(VisualFailureClass.VisualLocal, validation.failureClass)
        assertTrue(validation.message.contains("protocolRepairRequired=true"))
        assertTrue(validation.message.contains("mobile_use protocol"))
    }

    @Test
    fun genuineGuiPlusInteractionStillReachesUser() {
        val validation = VisualActionValidator.validate(
            step = CloudAgentStep(type = "need_user_help", reason = "请用户确认收货地址后继续。"),
            snapshot = snapshot("page-a"),
            runtime = workSurfaceRuntime(),
        )
        assertTrue(validation.ok)
    }

    @Test
    fun inputTextNeverAppearsInActionSignature() {
        val signature = VisualActionValidator.actionSignature(
            CloudAgentStep(
                type = "input_text",
                text = "private-input-value",
                milestoneId = "m1",
                hypothesisId = "fill_private_field",
            ),
        )

        assertFalse(signature.contains("private-input-value"))
        assertTrue(signature.contains("input_text"))
        assertTrue(signature.contains("fill_private_field"))
    }

    @Test
    fun repeatedCloudHypothesisFailureBecomesStructuredBlock() {
        val tracker = VisualSemanticProgressTracker(originalGoal = "完成测试任务")
        tracker.updateTaskContract(
            VisualTaskContract(
                originalGoal = "完成测试任务",
                currentMilestoneId = "m1",
                milestones = listOf(VisualTaskMilestone(id = "m1", title = "进入下一页")),
                explorationBudgetPerMilestone = 2,
            ),
        )
        val step = CloudAgentStep(
            type = "wait",
            purpose = "等待页面跳转",
            milestoneId = "m1",
            hypothesisId = "loading_transition",
            exploratory = true,
            legacyIntent = false,
        )
        val page = snapshot("page-a")

        val first = tracker.evaluate(step, page, page, "com.example.app")
        assertEquals(1, first.failedHypothesisCount)
        assertEquals(1, first.explorationBudgetRemaining)
        assertFalse(first.requiresReplan)
        assertNull(tracker.blockedHypothesisReason(step, page))

        val second = tracker.evaluate(step, page, page, "com.example.app")
        assertEquals(1, second.failedHypothesisCount)
        assertEquals(0, second.explorationBudgetRemaining)
        assertTrue(second.requiresStrategyChange)
        assertTrue(second.requiresReplan)
        assertNotNull(tracker.blockedHypothesisReason(step, page))
        assertEquals(2, second.taskMemory.failedHypotheses.single().count)
        assertEquals(1, second.taskMemory.blockedActions.size)
    }

    @Test
    fun nonExploratoryFailureDoesNotConsumeExplorationBudget() {
        val tracker = VisualSemanticProgressTracker(originalGoal = "完成测试任务")
        tracker.updateTaskContract(
            VisualTaskContract(
                originalGoal = "完成测试任务",
                currentMilestoneId = "m1",
                milestones = listOf(VisualTaskMilestone(id = "m1")),
                explorationBudgetPerMilestone = 3,
            ),
        )
        val step = CloudAgentStep(
            type = "wait",
            purpose = "确认稳定状态",
            milestoneId = "m1",
            hypothesisId = "stable_state",
            exploratory = false,
            legacyIntent = false,
        )
        val page = snapshot("page-a")

        val result = tracker.evaluate(step, page, page, "com.example.app")

        assertEquals(3, result.explorationBudgetRemaining)
        assertEquals(1, result.failedHypothesisCount)
    }

    @Test
    fun laterContractCannotEraseCompletedMilestonesOrOriginalGoal() {
        val tracker = VisualSemanticProgressTracker(originalGoal = "原始任务")
        tracker.updateTaskContract(
            VisualTaskContract(
                originalGoal = "原始任务",
                currentMilestoneId = "m1",
                milestones = listOf(VisualTaskMilestone(id = "m1", title = "第一步", completed = true)),
                completedMilestoneIds = listOf("m1"),
            ),
        )
        tracker.updateTaskContract(
            VisualTaskContract(
                originalGoal = "漂移后的任务",
                currentMilestoneId = "m2",
                milestones = listOf(VisualTaskMilestone(id = "m2", title = "第二步")),
            ),
        )

        val memory = tracker.memorySnapshot()
        assertEquals("原始任务", memory.originalGoal)
        assertTrue("m1" in memory.completedMilestoneIds)
        assertTrue(memory.taskContract!!.milestones.any { it.id == "m1" && it.completed })
        assertTrue(memory.taskContract!!.milestones.any { it.id == "m2" })
        assertEquals("m2", memory.currentMilestoneId)
    }

    private fun workSurfaceRuntime() = VisualAgentRuntimeContext(
        surfaceState = VisualSurfaceState.WorkSurface,
        selectedTargetPackage = "com.example.app",
        verifiedTargetPackage = "com.example.app",
        currentPackage = "com.example.app",
        observationId = "observation-test",
        guiPlusEligible = true,
    )

    private fun snapshot(text: String): AgentScreenSnapshot {
        val node = AgentScreenNode(
            id = text,
            text = text,
            className = "android.widget.TextView",
            bounds = "0,0,200,100",
            clickable = false,
            editable = false,
            scrollable = false,
        )
        return AgentScreenSnapshot(
            currentApp = "com.example.app",
            packageName = "com.example.app",
            nodeCount = 1,
            capturedNodeCount = 1,
            texts = listOf(text),
            allNodes = listOf(node),
            clickableNodes = emptyList(),
            inputNodes = emptyList(),
            scrollableNodes = emptyList(),
        )
    }
}
