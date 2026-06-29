package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualSemanticProgressTrackerTest {
    @Test
    fun frameChangeIsRecordedButNeverClassifiedAsProgress() {
        val tracker = VisualSemanticProgressTracker(originalGoal = "进入联系人")
        val result = tracker.evaluate(
            semanticStep(
                type = "tap_xy",
                purpose = "进入联系人页",
                milestone = "contacts",
                expected = listOf("新的朋友"),
                hypothesis = "contacts-tab",
            ),
            snapshot("com.tencent.mobileqq", listOf("消息", "联系人")),
            snapshot("com.tencent.mobileqq", listOf("联系人", "新的朋友", "群聊")),
            "com.tencent.mobileqq",
        )

        assertEquals(VisualSemanticProgressStatus.Observed, result.status)
        assertTrue(result.pageChanged)
        assertTrue(result.expectedEvidenceMatched.isEmpty())
        assertTrue(result.failureEvidenceMatched.isEmpty())
        assertTrue(result.newEvidence.isEmpty())
        assertEquals(0, result.failedHypothesisCount)
        assertFalse(result.requiresReplan)
        assertFalse(result.reobserveRecommended)
        assertTrue(result.reason.contains("GUI Plus exclusively decides"))
    }

    @Test
    fun unchangedFrameCreatesNoFailureHypothesisOrLocalRetry() {
        val tracker = VisualSemanticProgressTracker()
        val screen = snapshot("com.example.app", listOf("列表", "入口"))
        val step = semanticStep(
            type = "tap_xy",
            purpose = "打开入口",
            milestone = "open-entry",
            expected = listOf("详情"),
            hypothesis = "entry-row",
        )

        val result = tracker.evaluate(step, screen, screen, "com.example.app")
        val memory = tracker.memorySnapshot(screen)

        assertEquals(VisualSemanticProgressStatus.Observed, result.status)
        assertFalse(result.pageChanged)
        assertNull(tracker.blockedHypothesisReason(step.copy(x = 0.53f), screen))
        assertEquals(0, result.failedHypothesisCount)
        assertTrue(memory.failedHypotheses.isEmpty())
        assertTrue(memory.blockedActions.isEmpty())
        assertFalse(result.requiresReplan)
        assertFalse(result.reobserveRecommended)
        assertFalse(result.shouldPauseForUser)
    }

    @Test
    fun dynamicFramesNeverConsumeCloudExplorationBudget() {
        val tracker = VisualSemanticProgressTracker(originalGoal = "查看行情")
        tracker.updateTaskContract(
            VisualTaskContract(
                originalGoal = "查看行情",
                currentMilestoneId = "chart",
                milestones = listOf(VisualTaskMilestone(id = "chart")),
                explorationBudgetPerMilestone = 3,
            ),
        )
        val step = semanticStep(
            type = "wait",
            purpose = "观察日K",
            milestone = "chart",
            expected = emptyList(),
            hypothesis = "chart-refresh",
        )

        repeat(5) { index ->
            tracker.evaluate(
                step,
                snapshot("com.hexin.plat.android", listOf("09:${30 + index}", "日K")),
                snapshot("com.hexin.plat.android", listOf("09:${31 + index}", "日K")),
                "com.hexin.plat.android",
            )
        }

        val memory = tracker.memorySnapshot()
        assertEquals(3, memory.remainingExplorationBudget)
        assertTrue(memory.failedHypotheses.isEmpty())
        assertTrue(memory.blockedActions.isEmpty())
        assertFalse(memory.replanRequested)
    }

    @Test
    fun oneForeignPackageFrameDoesNotBecomeLocalSemanticRegression() {
        val tracker = VisualSemanticProgressTracker()
        val result = tracker.evaluate(
            semanticStep(
                type = "tap_xy",
                purpose = "打开目标详情",
                milestone = "details",
                expected = listOf("详情"),
                hypothesis = "detail",
            ),
            snapshot("com.example.app", listOf("入口")),
            snapshot("com.unrelated.app", listOf("无关页面")),
            "com.example.app",
        )

        assertEquals(VisualSemanticProgressStatus.Observed, result.status)
        assertTrue(result.packageChanged)
        assertFalse(result.structuralRegression)
        assertFalse(result.requiresReplan)
        assertFalse(result.toFeedbackLine(CloudAgentStep(type = "tap_xy")).contains("failureClass=structural_route"))
    }

    @Test
    fun stateMachineConfirmedForeignTransitionStillRequiresStructuralReplan() {
        val tracker = VisualSemanticProgressTracker()
        val result = tracker.evaluate(
            semanticStep(
                type = "tap_xy",
                purpose = "打开目标详情",
                milestone = "details",
                expected = listOf("详情"),
                hypothesis = "detail",
            ),
            snapshot("com.example.app", listOf("入口")),
            snapshot("com.unrelated.app", listOf("无关页面")),
            "com.example.app",
            structuralRegressionConfirmed = true,
        )

        assertEquals(VisualSemanticProgressStatus.Regressed, result.status)
        assertTrue(result.structuralRegression)
        assertTrue(result.requiresReplan)
        assertTrue(result.toFeedbackLine(CloudAgentStep(type = "tap_xy")).contains("failureClass=structural_route"))
    }

    @Test
    fun taskContractIsStoredWithoutLocalEvidenceInterpretation() {
        val tracker = VisualSemanticProgressTracker(originalGoal = "查看订单")
        tracker.updateTaskContract(
            VisualTaskContract(
                originalGoal = "查看订单",
                currentMilestoneId = "orders",
                milestones = listOf(
                    VisualTaskMilestone("orders", successEvidence = listOf("全部订单")),
                ),
            ),
        )
        val page = snapshot("com.example.app", listOf("我的", "全部订单"))
        tracker.onVerifiedSurface(page)
        val result = tracker.evaluate(
            semanticStep(
                type = "tap_xy",
                purpose = "打开订单",
                milestone = "orders",
                expected = listOf("全部订单"),
                hypothesis = "orders-entry",
            ),
            page,
            page,
            "com.example.app",
        )

        val memory = tracker.memorySnapshot(page)
        assertEquals("orders", memory.currentMilestoneId)
        assertTrue(memory.confirmedFacts.any { it.startsWith("verified_surface:") })
        assertFalse(memory.confirmedFacts.any { it.contains("全部订单") })
        assertTrue(memory.failedHypotheses.isEmpty())
        assertTrue(memory.blockedActions.isEmpty())
        assertTrue(result.expectedEvidenceMatched.isEmpty())
        assertTrue(result.failureEvidenceMatched.isEmpty())
        assertNotNull(memory.lastConfirmedPage)
        assertEquals("", memory.lastConfirmedPage?.summary)
        assertEquals("查看订单", memory.originalGoal)
        assertFalse(memory.replanRequested)
    }

    @Test
    fun feedbackReportsFrameChangeAsTelemetryNotProgress() {
        val tracker = VisualSemanticProgressTracker()
        val before = snapshot("com.example.app", listOf("09:30", "日K"))
        val after = snapshot("com.example.app", listOf("09:31", "日K"))
        val step = CloudAgentStep(type = "wait", targetText = "观察")

        val feedback = tracker.evaluate(step, before, after, "com.example.app").toFeedbackLine(step)

        assertTrue(feedback.startsWith("visual_execution_observed:"))
        assertTrue(feedback.contains("executionObserved=true"))
        assertTrue(feedback.contains("frameChanged=true"))
        assertTrue(feedback.contains("semanticDecisionOwner=gui_plus"))
        assertTrue(feedback.contains("localSemanticDecision=false"))
        assertFalse(feedback.contains("screenChanged="))
        assertFalse(feedback.contains("expectedMatched="))
        assertFalse(feedback.contains("newEvidence="))
    }

    private fun semanticStep(
        type: String,
        purpose: String,
        milestone: String,
        expected: List<String>,
        hypothesis: String,
        x: Float = 0.5f,
        y: Float = 0.5f,
    ): CloudAgentStep = CloudAgentStep(
        type = type,
        purpose = purpose,
        milestoneId = milestone,
        expectedEvidence = expected,
        exploratory = false,
        reversible = true,
        hypothesisId = hypothesis,
        legacyIntent = false,
        x = if (type == "tap_xy") x else null,
        y = if (type == "tap_xy") y else null,
        direction = if (type == "swipe") "up" else null,
    )

    private fun snapshot(
        packageName: String,
        texts: List<String>,
    ): AgentScreenSnapshot = AgentScreenSnapshot(
        currentApp = packageName,
        packageName = packageName,
        nodeCount = texts.size,
        capturedNodeCount = texts.size,
        texts = texts,
        allNodes = emptyList(),
        clickableNodes = emptyList(),
        inputNodes = emptyList(),
        scrollableNodes = emptyList(),
        visual = null,
    )
}
