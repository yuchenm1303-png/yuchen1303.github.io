package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualSemanticProgressTrackerTest {
    @Test
    fun expectedEvidenceMarksRealProgress() {
        val tracker = VisualSemanticProgressTracker(originalGoal = "进入联系人")
        val step = semanticStep(
            type = "tap_xy",
            purpose = "进入联系人页",
            milestone = "contacts",
            expected = listOf("新的朋友"),
            hypothesis = "contacts-tab",
        )
        val result = tracker.evaluate(
            step,
            snapshot("com.tencent.mobileqq", listOf("消息", "联系人")),
            snapshot("com.tencent.mobileqq", listOf("联系人", "新的朋友", "群聊")),
            "com.tencent.mobileqq",
        )

        assertEquals(VisualSemanticProgressStatus.Advanced, result.status)
        assertEquals(listOf("新的朋友"), result.expectedEvidenceMatched)
        assertFalse(result.requiresReplan)
    }

    @Test
    fun pageChangeAloneIsAmbiguousAndOnlyRequestsReobservation() {
        val tracker = VisualSemanticProgressTracker()
        val step = semanticStep(
            type = "tap_xy",
            purpose = "进入目标详情",
            milestone = "details",
            expected = listOf("详情页标题"),
            hypothesis = "detail-row",
        )
        val result = tracker.evaluate(
            step,
            snapshot("com.example.app", listOf("列表", "入口")),
            snapshot("com.example.app", listOf("推荐", "热门")),
            "com.example.app",
        )

        assertEquals(VisualSemanticProgressStatus.Ambiguous, result.status)
        assertTrue(result.reobserveRecommended)
        assertFalse(result.requiresReplan)
    }

    @Test
    fun unchangedScreenBlocksSamePurposeEvenWhenCoordinateMovesSlightly() {
        val tracker = VisualSemanticProgressTracker()
        val before = snapshot("com.example.app", listOf("列表", "入口"))
        val first = semanticStep(
            type = "tap_xy",
            purpose = "打开入口",
            milestone = "open-entry",
            expected = listOf("详情"),
            hypothesis = "entry-row",
            x = 0.50f,
            y = 0.50f,
        )
        val result = tracker.evaluate(first, before, before, "com.example.app")
        assertEquals(VisualSemanticProgressStatus.Stalled, result.status)

        val nearby = first.copy(x = 0.53f, y = 0.51f)
        assertNotNull(tracker.blockedHypothesisReason(nearby, before))
    }

    @Test
    fun explicitExplorationWithoutPurposeIsRejectedBeforeExecution() {
        val tracker = VisualSemanticProgressTracker()
        val step = CloudAgentStep(
            type = "swipe",
            direction = "up",
            exploratory = true,
            expectedEvidence = listOf("下一页"),
            legacyIntent = false,
        )

        val reason = tracker.blockedHypothesisReason(step, snapshot("com.example.app", listOf("列表")))
        assertNotNull(reason)
        assertTrue(reason!!.contains("purpose"))
    }

    @Test
    fun exploratorySwipeRequiresExpectedEvidence() {
        val tracker = VisualSemanticProgressTracker()
        val step = semanticStep(
            type = "swipe",
            purpose = "寻找更多结果",
            milestone = "results",
            expected = emptyList(),
            hypothesis = "more-results",
            exploratory = true,
        )

        assertNotNull(tracker.blockedHypothesisReason(step, snapshot("com.example.app", listOf("列表"))))
    }

    @Test
    fun twoExplorationFailuresForceReplanInsteadOfMoreRandomActions() {
        val tracker = VisualSemanticProgressTracker()
        val before = snapshot("com.example.app", listOf("列表"))
        val first = semanticStep(
            type = "swipe",
            purpose = "寻找订单入口",
            milestone = "orders",
            expected = listOf("订单"),
            hypothesis = "orders-lower",
            exploratory = true,
        )
        val firstResult = tracker.evaluate(
            first,
            before,
            snapshot("com.example.app", listOf("推荐")),
            "com.example.app",
        )
        assertFalse(firstResult.requiresReplan)

        val second = first.copy(hypothesisId = "orders-upper", direction = "down")
        val secondResult = tracker.evaluate(
            second,
            snapshot("com.example.app", listOf("推荐")),
            snapshot("com.example.app", listOf("活动")),
            "com.example.app",
        )
        assertTrue(secondResult.requiresReplan)
        assertEquals(0, secondResult.explorationBudgetRemaining)
        assertFalse(secondResult.shouldPauseForUser)
    }

    @Test
    fun permissionControllerTransitionIsAmbiguousNotTaskProgress() {
        val tracker = VisualSemanticProgressTracker()
        val step = semanticStep(
            type = "tap_xy",
            purpose = "打开相册",
            milestone = "gallery",
            expected = listOf("相册"),
            hypothesis = "gallery-button",
        )
        val result = tracker.evaluate(
            step,
            snapshot("com.example.app", listOf("选择图片")),
            snapshot("com.android.permissioncontroller", listOf("允许访问照片")),
            "com.example.app",
        )

        assertEquals(VisualSemanticProgressStatus.Ambiguous, result.status)
        assertFalse(result.structuralRegression)
    }

    @Test
    fun foreignAppIsRegressionAndRequestsReplan() {
        val tracker = VisualSemanticProgressTracker()
        val step = semanticStep(
            type = "tap_xy",
            purpose = "打开目标详情",
            milestone = "details",
            expected = listOf("详情"),
            hypothesis = "detail",
        )
        val result = tracker.evaluate(
            step,
            snapshot("com.example.app", listOf("入口")),
            snapshot("com.unrelated.app", listOf("无关页面")),
            "com.example.app",
        )

        assertEquals(VisualSemanticProgressStatus.Regressed, result.status)
        assertTrue(result.structuralRegression)
        assertTrue(result.requiresReplan)
    }

    @Test
    fun taskMemoryContainsMilestoneFailuresBlockedActionsAndConfirmedPage() {
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
        val page = snapshot("com.example.app", listOf("我的"))
        tracker.onVerifiedSurface(page)
        val step = semanticStep(
            type = "tap_xy",
            purpose = "打开订单",
            milestone = "orders",
            expected = listOf("全部订单"),
            hypothesis = "orders-entry",
        )
        tracker.evaluate(step, page, page, "com.example.app")
        assertNotNull(tracker.blockedHypothesisReason(step.copy(x = 0.52f), page))

        val memory = tracker.memorySnapshot(page)
        assertEquals("orders", memory.currentMilestoneId)
        assertTrue(memory.failedHypotheses.isNotEmpty())
        assertTrue(memory.blockedActions.isNotEmpty())
        assertNotNull(memory.lastConfirmedPage)
        assertEquals("查看订单", memory.originalGoal)
    }

    @Test
    fun legacyBackendStillRunsWithConservativeBudget() {
        val tracker = VisualSemanticProgressTracker()
        val legacySwipe = CloudAgentStep(type = "swipe", direction = "up", reason = "继续查找")
        val before = snapshot("com.example.app", listOf("列表"))
        assertNull(tracker.blockedHypothesisReason(legacySwipe, before))
        val result = tracker.evaluate(
            legacySwipe,
            before,
            snapshot("com.example.app", listOf("推荐")),
            "com.example.app",
        )
        assertTrue(result.requiresReplan)
        assertEquals(0, result.explorationBudgetRemaining)
    }

    private fun semanticStep(
        type: String,
        purpose: String,
        milestone: String,
        expected: List<String>,
        hypothesis: String,
        exploratory: Boolean = false,
        x: Float = 0.5f,
        y: Float = 0.5f,
    ): CloudAgentStep = CloudAgentStep(
        type = type,
        purpose = purpose,
        milestoneId = milestone,
        expectedEvidence = expected,
        exploratory = exploratory,
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
