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
    fun allowsDuplicateOpenAppToReachCloudReplanningLoop() {
        val result = VisualActionValidator.validate(
            CloudAgentStep(type = "open_app", appName = "QQ", packageName = "com.tencent.mobileqq"),
            snapshot(currentApp = "com.tencent.mobileqq"),
        )
        assertTrue(result.ok)
    }

    @Test
    fun allowsFocusedDirectInputBecauseGuiPlusOwnsVisualTargetingOnVerifiedSurface() {
        val snapshot = snapshot(
            currentApp = TARGET_PACKAGE,
            inputNodes = listOf(inputNode("search", "搜索"), inputNode("message", "消息")),
        )
        val result = VisualActionValidator.validate(
            CloudAgentStep(
                type = "input_text",
                text = "测试",
                inputMode = "focused_direct",
                requiresInputNode = false,
                expectsFocusedInput = true,
            ),
            snapshot,
            verifiedRuntimeContext(snapshot),
        )
        assertTrue(result.ok)
    }

    @Test
    fun allowsFocusedDirectInputForUniqueOrMatchedInputOnVerifiedSurface() {
        val uniqueSnapshot = snapshot(
            currentApp = TARGET_PACKAGE,
            inputNodes = listOf(inputNode("search", "搜索")),
        )
        val uniqueResult = VisualActionValidator.validate(
            CloudAgentStep(
                type = "input_text",
                text = "测试",
                inputMode = "focused_direct",
                requiresInputNode = false,
            ),
            uniqueSnapshot,
            verifiedRuntimeContext(uniqueSnapshot),
        )
        assertTrue(uniqueResult.ok)

        val matchedSnapshot = snapshot(
            currentApp = TARGET_PACKAGE,
            inputNodes = listOf(inputNode("search", "搜索"), inputNode("message", "消息")),
        )
        val matchedResult = VisualActionValidator.validate(
            CloudAgentStep(
                type = "input_text",
                text = "测试",
                targetNodeId = "message",
                inputMode = "focused_direct",
                requiresInputNode = false,
            ),
            matchedSnapshot,
            verifiedRuntimeContext(matchedSnapshot),
        )
        assertTrue(matchedResult.ok)
    }

    @Test
    fun allowsInputWithoutLocalNodeSemanticMatchingOnVerifiedSurface() {
        val snapshot = snapshot(
            currentApp = TARGET_PACKAGE,
            inputNodes = listOf(inputNode("search", "搜索"), inputNode("message", "消息")),
        )
        val result = VisualActionValidator.validate(
            CloudAgentStep(type = "input_text", text = "测试", requiresInputNode = true),
            snapshot,
            verifiedRuntimeContext(snapshot),
        )
        assertTrue(result.ok)
    }

    @Test
    fun rejectsGuiInputBeforeTargetSurfaceIsVerified() {
        val snapshot = snapshot(
            currentApp = TARGET_PACKAGE,
            inputNodes = listOf(inputNode("search", "搜索")),
        )
        val result = VisualActionValidator.validate(
            CloudAgentStep(type = "input_text", text = "测试", requiresInputNode = false),
            snapshot,
            VisualAgentRuntimeContext(
                surfaceState = VisualSurfaceState.Planning,
                currentPackage = TARGET_PACKAGE,
                observationId = "planning-observation",
            ),
        )
        assertFalse(result.ok)
        assertEquals(VisualFailureClass.StructuralRoute, result.failureClass)
    }

    @Test
    fun allowsDeepSeekInternalToolBeforeVisualSurfaceIsVerified() {
        val result = VisualActionValidator.validate(
            CloudAgentStep(
                type = "set_brightness",
                toolArgs = org.json.JSONObject().put("percent", 40),
            ),
            snapshot(),
            VisualAgentRuntimeContext(
                surfaceState = VisualSurfaceState.Planning,
                currentPackage = "com.yuchen.ailedger",
                observationId = "planning-observation",
            ),
        )

        assertTrue(result.ok)
    }

    @Test
    fun rejectsInternalToolAfterGuiPlusOwnsVisualLoop() {
        val snapshot = snapshot(currentApp = TARGET_PACKAGE)
        val result = VisualActionValidator.validate(
            CloudAgentStep(type = "set_brightness"),
            snapshot,
            verifiedRuntimeContext(snapshot),
        )

        assertFalse(result.ok)
        assertEquals(VisualFailureClass.StructuralRoute, result.failureClass)
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
    fun completionFingerprintIgnoresScreenshotEncodingNoise() {
        val first = snapshot(
            currentApp = "com.tencent.mobileqq",
            texts = listOf("个人主页", "编辑资料"),
            nodeCount = 2,
            visualBase64 = "YWJj",
        )
        val second = snapshot(
            currentApp = "com.tencent.mobileqq",
            texts = listOf("个人主页", "编辑资料"),
            nodeCount = 2,
            visualBase64 = "eHl6",
        )
        assertEquals(
            VisualActionValidator.completionFingerprint(first),
            VisualActionValidator.completionFingerprint(second),
        )
    }

    @Test
    fun completionFingerprintChangesWithSemanticScreenContent() {
        val profile = snapshot(
            currentApp = "com.tencent.mobileqq",
            texts = listOf("个人主页", "编辑资料"),
            nodeCount = 2,
        )
        val settings = snapshot(
            currentApp = "com.tencent.mobileqq",
            texts = listOf("设置", "账号与安全"),
            nodeCount = 2,
        )
        assertNotEquals(
            VisualActionValidator.completionFingerprint(profile),
            VisualActionValidator.completionFingerprint(settings),
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
    fun explicitModelConfirmationStillRequiresAndroidAuthorization() {
        assertTrue(
            AgentSafetyPolicy.requiresConfirmation(
                "test",
                CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f, requiresConfirmation = true),
            ),
        )
    }

    @Test
    fun AndroidDoesNotInferConfirmationFromRiskWordsOrLabels() {
        assertFalse(
            AgentSafetyPolicy.requiresConfirmation(
                "search for the definition of password",
                CloudAgentStep(
                    type = "tap_xy",
                    x = 0.5f,
                    y = 0.5f,
                    targetText = "payment password documentation",
                    riskLevel = "high",
                ),
            ),
        )
    }

    private fun verifiedRuntimeContext(snapshot: AgentScreenSnapshot): VisualAgentRuntimeContext {
        return VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.WorkSurface,
            selectedTargetPackage = snapshot.packageName,
            verifiedTargetPackage = snapshot.packageName,
            currentPackage = snapshot.packageName,
            observationId = VisualObservationProtocol.observationId(snapshot, routeEpoch = 1L, surfaceEpoch = 1L),
            routeEpoch = 1L,
            surfaceEpoch = 1L,
            guiPlusEligible = true,
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

    companion object {
        private const val TARGET_PACKAGE = "com.example.target"
    }
}
