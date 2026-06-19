package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun blocksFocusedDirectInputWhenMultipleInputsHaveNoConfirmedTarget() {
        val result = VisualActionValidator.validate(
            CloudAgentStep(
                type = "input_text",
                text = "测试",
                inputMode = "focused_direct",
                requiresInputNode = false,
                expectsFocusedInput = true,
            ),
            snapshot(inputNodes = listOf(inputNode("search", "搜索"), inputNode("message", "消息"))),
        )
        assertFalse(result.ok)
    }

    @Test
    fun allowsFocusedDirectInputForUniqueOrMatchedInput() {
        val uniqueResult = VisualActionValidator.validate(
            CloudAgentStep(
                type = "input_text",
                text = "测试",
                inputMode = "focused_direct",
                requiresInputNode = false,
            ),
            snapshot(inputNodes = listOf(inputNode("search", "搜索"))),
        )
        assertTrue(uniqueResult.ok)

        val matchedResult = VisualActionValidator.validate(
            CloudAgentStep(
                type = "input_text",
                text = "测试",
                targetNodeId = "message",
                inputMode = "focused_direct",
                requiresInputNode = false,
            ),
            snapshot(inputNodes = listOf(inputNode("search", "搜索"), inputNode("message", "消息"))),
        )
        assertTrue(matchedResult.ok)
    }

    @Test
    fun rejectsNodeInputWithoutUniqueOrMatchedInput() {
        val result = VisualActionValidator.validate(
            CloudAgentStep(type = "input_text", text = "测试", requiresInputNode = true),
            snapshot(inputNodes = listOf(inputNode("search", "搜索"), inputNode("message", "消息"))),
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
    fun sparseVisualFingerprintDetectsCanvasOrWebViewProgress() {
        val before = snapshot(nodeCount = 0, visualBase64 = "QUFBQUFBQUFB")
        val after = snapshot(nodeCount = 0, visualBase64 = "QkJCQkJCQkJC")
        assertNotEquals(
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

    private fun inputNode(id: String, text: String): AgentScreenNode {
        return AgentScreenNode(
            id = id,
            text = text,
            className = "EditText",
            bounds = "0,0,100,40",
            clickable = true,
            editable = true,
            scrollable = false,
        )
    }

    private fun snapshot(
        currentApp: String = "com.yuchen.ailedger",
        texts: List<String> = emptyList(),
        nodeCount: Int = 0,
        visualBase64: String = "",
        inputNodes: List<AgentScreenNode> = emptyList(),
    ): AgentScreenSnapshot {
        return AgentScreenSnapshot(
            currentApp = currentApp,
            packageName = currentApp,
            nodeCount = nodeCount,
            capturedNodeCount = nodeCount,
            texts = texts,
            allNodes = inputNodes,
            clickableNodes = emptyList(),
            inputNodes = inputNodes,
            scrollableNodes = emptyList(),
            visual = visualBase64.takeIf { it.isNotBlank() }?.let {
                AgentScreenVisual(
                    available = true,
                    mimeType = "image/jpeg",
                    width = 720,
                    height = 1280,
                    displayWidth = 1080,
                    displayHeight = 2400,
                    base64Jpeg = it,
                    source = "test",
                    reason = "test",
                )
            },
        )
    }
}
