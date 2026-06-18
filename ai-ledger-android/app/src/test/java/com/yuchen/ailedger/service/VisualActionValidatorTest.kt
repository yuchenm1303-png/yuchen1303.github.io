package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualActionValidatorTest {
    @Test
    fun rejectsInvalidVisualActions() {
        assertFalse(VisualActionValidator.validate(CloudAgentStep(type = "tap_xy", x = 1.2f, y = 0.5f), snapshot()).ok)
        assertFalse(VisualActionValidator.validate(CloudAgentStep(type = "input_text", text = ""), snapshot()).ok)
        assertFalse(VisualActionValidator.validate(CloudAgentStep(type = "open_app"), snapshot()).ok)
    }

    @Test
    fun rejectsDuplicateOpenAppWhenPackageAlreadyForeground() {
        val result = VisualActionValidator.validate(
            CloudAgentStep(type = "open_app", appName = "QQ", packageName = "com.tencent.mobileqq"),
            snapshot(currentApp = "com.tencent.mobileqq"),
        )
        assertFalse(result.ok)
    }

    @Test
    fun snapshotFingerprintDetectsNoProgress() {
        val before = snapshot(texts = listOf("A"), nodeCount = 1)
        val after = snapshot(texts = listOf("A"), nodeCount = 1)
        assertEquals(
            VisualActionValidator.snapshotFingerprint(before),
            VisualActionValidator.snapshotFingerprint(after),
        )
    }

    @Test
    fun tapClusterSignatureGroupsNearbyRepeatedTaps() {
        assertEquals(
            VisualActionValidator.actionClusterSignature(CloudAgentStep(type = "tap_xy", x = 1140f, y = 207f)),
            VisualActionValidator.actionClusterSignature(CloudAgentStep(type = "tap_xy", x = 1149f, y = 183f)),
        )
    }

    @Test
    fun highRiskStillRequiresAndroidConfirmation() {
        assertTrue(
            AgentSafetyPolicy.requiresConfirmation(
                "test",
                CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f, riskLevel = "high"),
            ),
        )
    }

    private fun snapshot(
        currentApp: String = "com.yuchen.ailedger",
        texts: List<String> = emptyList(),
        nodeCount: Int = 0,
    ): AgentScreenSnapshot {
        return AgentScreenSnapshot(
            currentApp = currentApp,
            packageName = currentApp,
            nodeCount = nodeCount,
            capturedNodeCount = nodeCount,
            texts = texts,
            allNodes = emptyList(),
            clickableNodes = emptyList(),
            inputNodes = emptyList(),
            scrollableNodes = emptyList(),
            visual = null,
        )
    }
}
