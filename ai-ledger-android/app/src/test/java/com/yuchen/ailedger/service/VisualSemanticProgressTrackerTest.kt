package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualSemanticProgressTrackerTest {
    @Test
    fun expectedEvidenceMarksRealProgress() {
        val tracker = VisualSemanticProgressTracker()
        val before = snapshot(texts = listOf("首页", "搜索"))
        val after = snapshot(texts = listOf("搜索股票", "长电科技"))
        tracker.onVerifiedSurface(before)
        val step = stepWithIntent(
            expectedEvidence = listOf("长电科技"),
            purpose = "打开长电科技搜索结果",
        )

        val result = tracker.evaluate(step, before, after, TARGET_PACKAGE)

        assertEquals(VisualSemanticProgressStatus.Advanced, result.status)
        assertEquals(listOf("长电科技"), result.expectedEvidenceMatched)
        assertFalse(result.requiresStrategyChange)
        assertFalse(result.shouldPauseForUser)
    }

    @Test
    fun changedScreenWithoutExpectedEvidenceIsAmbiguousNotProgress() {
        val tracker = VisualSemanticProgressTracker()
        val before = snapshot(texts = listOf("首页", "搜索"))
        val after = snapshot(texts = listOf("活动中心", "热门推荐"))
        tracker.onVerifiedSurface(before)
        val step = CloudAgentStep(
            type = "tap_xy",
            x = 0.8f,
            y = 0.2f,
            reason = "尝试右上角入口",
        )

        val result = tracker.evaluate(step, before, after, TARGET_PACKAGE)

        assertEquals(VisualSemanticProgressStatus.Ambiguous, result.status)
        assertTrue(result.pageChanged)
        assertTrue(result.requiresStrategyChange)
    }

    @Test
    fun unchangedScreenFailsAndBlocksSameHypothesis() {
        val tracker = VisualSemanticProgressTracker()
        val before = snapshot(texts = listOf("首页", "搜索"))
        tracker.onVerifiedSurface(before)
        val step = CloudAgentStep(
            type = "tap_xy",
            x = 0.5f,
            y = 0.5f,
            targetText = "搜索",
            reason = "打开搜索",
        )

        val result = tracker.evaluate(step, before, before, TARGET_PACKAGE)

        assertEquals(VisualSemanticProgressStatus.Stalled, result.status)
        assertNotNull(tracker.blockedHypothesisReason(step, before))
        assertEquals(1, result.failedHypothesisCount)
    }

    @Test
    fun foreignPackageIsStructuralRegression() {
        val tracker = VisualSemanticProgressTracker()
        val before = snapshot(texts = listOf("搜索股票"))
        val after = snapshot(
            packageName = "com.example.unrelated",
            texts = listOf("无关页面"),
        )
        tracker.onVerifiedSurface(before)
        val step = CloudAgentStep(
            type = "tap_xy",
            x = 0.5f,
            y = 0.5f,
            reason = "打开结果",
        )

        val result = tracker.evaluate(step, before, after, TARGET_PACKAGE)

        assertEquals(VisualSemanticProgressStatus.Regressed, result.status)
        assertTrue(result.structuralRegression)
        assertTrue(result.toFeedbackLine(step).contains("failureClass=structural_route"))
    }

    @Test
    fun failureEvidenceOverridesVisualChange() {
        val tracker = VisualSemanticProgressTracker()
        val before = snapshot(texts = listOf("搜索结果"))
        val after = snapshot(texts = listOf("页面加载失败", "重试"))
        tracker.onVerifiedSurface(before)
        val step = stepWithIntent(
            expectedEvidence = listOf("详情"),
            failureEvidence = listOf("页面加载失败"),
            purpose = "打开详情",
        )

        val result = tracker.evaluate(step, before, after, TARGET_PACKAGE)

        assertEquals(VisualSemanticProgressStatus.Regressed, result.status)
        assertEquals(listOf("页面加载失败"), result.failureEvidenceMatched)
        assertFalse(result.structuralRegression)
    }

    private fun stepWithIntent(
        expectedEvidence: List<String>,
        failureEvidence: List<String> = emptyList(),
        purpose: String,
    ): CloudAgentStep {
        val intent = JSONObject().apply {
            put("milestoneId", "open_detail")
            put("purpose", purpose)
            put("expectedEvidence", JSONArray(expectedEvidence))
            put("failureEvidence", JSONArray(failureEvidence))
            put("exploratory", false)
            put("reversible", true)
        }
        return CloudAgentStep(
            type = "tap_xy",
            x = 0.5f,
            y = 0.5f,
            reason = purpose,
            toolArgs = JSONObject().put("actionIntent", intent),
        )
    }

    private fun snapshot(
        packageName: String = TARGET_PACKAGE,
        texts: List<String>,
    ): AgentScreenSnapshot {
        return AgentScreenSnapshot(
            currentApp = packageName,
            packageName = packageName,
            nodeCount = texts.size,
            capturedNodeCount = texts.size,
            texts = texts,
            allNodes = emptyList(),
            clickableNodes = emptyList(),
            inputNodes = emptyList(),
            scrollableNodes = emptyList(),
            visual = AgentScreenVisual(
                available = true,
                mimeType = "image/jpeg",
                width = 720,
                height = 1280,
                displayWidth = 1080,
                displayHeight = 2400,
                base64Jpeg = "YWJj",
                source = "test",
                reason = "test",
            ),
        )
    }

    companion object {
        private const val TARGET_PACKAGE = "com.hexin.plat.android"
    }
}
