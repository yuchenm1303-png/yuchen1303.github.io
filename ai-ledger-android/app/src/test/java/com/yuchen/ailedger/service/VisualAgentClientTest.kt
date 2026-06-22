package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAgentClientTest {
    @Test
    fun payloadUploadsAppFactsAndDeclaresCloudSelectionOwnership() {
        val snapshot = testSnapshot()
        val taskContract = AgentTaskExecutionContract(
            preferredSurface = AgentSurfacePreference.NativeApp,
            browserFallbackAllowed = false,
            requiredCapabilities = setOf(AppCapability.NativeApp, "social_chat"),
            requirePostActionVerification = true,
            highImpactFlow = true,
            reason = "需要原生社交应用",
        )
        val payload = buildVisualAgentPayload(
            goal = "打开 QQ 个人主页",
            snapshot = snapshot,
            recentActions = listOf("open_app:ok"),
            visualHistory = listOf(
                VisualAgentHistoryItem(
                    screenshot = snapshot.visual!!,
                    assistantOutput = "opened",
                    executionResult = "ok",
                ),
            ),
            appContext = listOf(
                VisualAgentAppContextItem(
                    label = "QQ",
                    packageName = "com.tencent.mobileqq",
                    aliases = listOf("腾讯QQ"),
                    capabilities = listOf(AppCapability.NativeApp, "social_chat"),
                ),
            ),
            deviceId = "android-install-test",
            agentSessionId = "visual-session-test",
            executionMode = AgentExecutionMode.ExplicitAgent,
            deviceProfile = AgentDeviceProfile(
                manufacturer = "Test",
                brand = "TestBrand",
                model = "TestModel",
                release = "15",
                sdkInt = 35,
                display = "test-build",
            ),
            taskContract = taskContract,
            taskContractRequired = true,
        )

        assertEquals("visual_agent_step", payload.getString("action"))
        assertEquals("explicit_agent", payload.getString("executionMode"))
        assertEquals("visual-session-test", payload.getString("agentSessionId"))
        assertEquals("android-install-test", payload.getString("deviceId"))
        assertEquals("gui_plus", payload.getString("decisionOwner"))
        assertEquals("gui_plus", payload.getString("visualDecisionOwner"))
        assertTrue(payload.getBoolean("visualAgentDirect"))
        assertTrue(payload.getBoolean("exclusiveVisualSession"))
        assertTrue(payload.getBoolean("allowAgentBrain"))
        assertFalse(payload.getBoolean("allowRoutePlanner"))
        assertFalse(payload.getBoolean("allowSemanticJudge"))
        assertFalse(payload.getBoolean("allowTaskContractJudge"))
        assertEquals("gui_plus_dialogue_v1", payload.getString("interactionProtocol"))
        assertTrue(payload.has("interactionHistory"))
        assertTrue(payload.has("screenSnapshot"))
        assertTrue(payload.has("recentAgentActions"))
        assertTrue(payload.has("executionFeedback"))
        assertTrue(payload.has("lastToolResponse"))
        assertTrue(payload.has("toolResponse"))
        assertTrue(payload.has("screenshot"))

        val ownership = payload.getJSONObject("visualOwnership")
        assertEquals("android_two_brain_ownership_v2_cloud_first", ownership.getString("schema"))
        assertEquals("deepseek", ownership.getString("entryOwner"))
        assertEquals("deepseek", ownership.getString("appSelectionOwner"))
        assertEquals("gui_plus", ownership.getString("visualOwner"))
        assertEquals("gui_plus", ownership.getString("currentOwner"))
        assertTrue(ownership.getBoolean("guiPlusEligible"))
        assertTrue(ownership.getBoolean("exclusiveAfterTargetAppVerified"))
        assertTrue(ownership.getBoolean("allowAgentBrain"))

        val snapshotJson = payload.getJSONObject("screenSnapshot")
        assertFalse(snapshotJson.getJSONObject("visual").has("base64Jpeg"))

        val historyItem = payload.getJSONArray("visualHistory").getJSONObject(0)
        assertFalse(historyItem.has("screenshot"))

        val app = payload.getJSONArray("appContext").getJSONObject(0)
        assertEquals("QQ", app.getString("label"))
        assertEquals("com.tencent.mobileqq", app.getString("packageName"))
        assertEquals("com.tencent.mobileqq", app.getString("appRef"))
        assertEquals("package_name", app.getString("identityType"))
        assertEquals("腾讯QQ", app.getJSONArray("aliases").getString(0))
        val capabilities = app.getJSONArray("capabilities")
        val capabilitySet = (0 until capabilities.length()).map { capabilities.getString(it) }.toSet()
        assertEquals(setOf(AppCapability.NativeApp, "social_chat"), capabilitySet)

        assertEquals("package_name_v2", payload.getString("appIdentityProtocol"))
        assertTrue(payload.getString("appInventoryHash").isNotBlank())
        val catalog = payload.getJSONObject("appCatalog")
        assertEquals("android_visual_app_catalog_v3_cloud_selected", catalog.getString("schema"))
        assertEquals("packageName", catalog.getString("identityField"))
        assertEquals("display_only", catalog.getString("appNameRole"))
        assertEquals("deepseek", catalog.getString("selectionOwner"))
        assertEquals(payload.getString("appInventoryHash"), catalog.getString("inventoryHash"))
        assertEquals("com.tencent.mobileqq", catalog.getJSONArray("entries").getJSONObject(0).getString("appRef"))

        val selectionProtocol = payload.getJSONObject("appSelectionProtocol")
        assertEquals("android_app_selection_protocol_v3_cloud_first", selectionProtocol.getString("schema"))
        assertEquals("deepseek", selectionProtocol.getString("semanticOwner"))
        assertEquals("packageName", selectionProtocol.getString("machineIdentity"))
        assertTrue(selectionProtocol.getBoolean("androidCanonicalizesAppName"))
        assertFalse(selectionProtocol.getBoolean("androidSelectsApps"))
        assertFalse(selectionProtocol.getBoolean("androidRanksApps"))
        assertFalse(selectionProtocol.getBoolean("androidResolvesUserIntent"))
        assertFalse(selectionProtocol.getBoolean("localKeywordMatching"))

        val uploadedProfile = payload.getJSONObject("deviceProfile")
        assertEquals("TestModel", uploadedProfile.getString("model"))
        assertEquals(35, uploadedProfile.getInt("sdkInt"))

        val uploadedContract = payload.getJSONObject("taskContract")
        assertEquals("native_app", uploadedContract.getString("preferredSurface"))
        assertFalse(uploadedContract.getBoolean("browserFallbackAllowed"))
        assertTrue(uploadedContract.getBoolean("requirePostActionVerification"))
        assertTrue(payload.getBoolean("taskContractRequired"))
        val planning = payload.getJSONObject("taskContractPlanning")
        assertTrue(planning.getBoolean("required"))
        assertEquals("gui_plus", planning.getString("semanticOwner"))
        assertEquals("android", planning.getString("validationOwner"))
        assertEquals("agentStep.arguments", planning.getString("returnLocation"))

        val supported = payload.getJSONArray("supportedAgentSteps")
        val supportedTypes = (0 until supported.length()).map { supported.getString(it) }.toSet()
        assertEquals(VisualAgentProtocol.supportedStepTypes, supportedTypes)
        assertFalse("tap_node" in supportedTypes)
        assertFalse("scroll" in supportedTypes)

        val memory = payload.getJSONObject("agentMemory")
        assertEquals("android_visual_agent_loop_memory_v11_cloud_first", memory.getString("schema"))
        assertEquals("gui_plus_dialogue_v1", memory.getString("interactionProtocol"))
        assertTrue(memory.has("interactionHistory"))
        assertTrue(memory.has("lastToolResponse"))
        assertEquals("TestModel", memory.getJSONObject("deviceProfile").getString("model"))
        assertEquals("native_app", memory.getJSONObject("taskContract").getString("preferredSurface"))
        assertEquals(payload.getString("appInventoryHash"), memory.getString("appInventoryHash"))
        assertFalse(payload.getBoolean("visualReplanRequested"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
        assertFalse(payload.getBoolean("invalidateCachedAgentBrainRoute"))

        val deviceContext = payload.getJSONObject("deviceContext")
        assertEquals("android_visual_agent_context_v5_cloud_first", deviceContext.getString("schema"))
        assertEquals("TestModel", deviceContext.getJSONObject("deviceProfile").getString("model"))
        assertEquals("native_app", deviceContext.getJSONObject("taskContract").getString("preferredSurface"))
        assertEquals(payload.getString("appInventoryHash"), deviceContext.getString("appInventoryHash"))
    }

    @Test
    fun appInventoryHashChangesWhenCatalogChanges() {
        val base = buildVisualAgentPayload(
            goal = "打开应用",
            snapshot = testSnapshot(),
            recentActions = emptyList(),
            appContext = listOf(
                VisualAgentAppContextItem("QQ", "com.tencent.mobileqq"),
            ),
        )
        val changed = buildVisualAgentPayload(
            goal = "打开应用",
            snapshot = testSnapshot(),
            recentActions = emptyList(),
            appContext = listOf(
                VisualAgentAppContextItem("QQ", "com.tencent.mobileqq"),
                VisualAgentAppContextItem("同花顺炒股票", "com.hexin.plat.android"),
            ),
        )

        assertNotEquals(base.getString("appInventoryHash"), changed.getString("appInventoryHash"))
    }

    @Test
    fun assistantHostRequiresDeepSeekHandoffBeforeGuiPlus() {
        val payload = buildVisualAgentPayload(
            goal = "帮我在京东下单压缩饼干",
            snapshot = testSnapshot(packageName = "com.yuchen.ailedger"),
            recentActions = emptyList(),
            appContext = listOf(
                VisualAgentAppContextItem(
                    label = "京东",
                    packageName = "com.jingdong.app.mall",
                ),
            ),
            agentSessionId = "visual-session-controller",
        )

        assertEquals("controller", payload.getString("surfaceRole"))
        assertEquals("deepseek", payload.getString("visualDecisionOwner"))
        assertFalse(payload.getBoolean("visualAgentDirect"))
        assertFalse(payload.getBoolean("exclusiveVisualSession"))

        val handoff = payload.getJSONObject("controllerHandoff")
        assertTrue(handoff.getBoolean("isAssistantHost"))
        assertTrue(handoff.getBoolean("isFirstVisualTurn"))
        assertTrue(handoff.getBoolean("controllerHandoffActive"))
        assertTrue(handoff.getBoolean("controllerPlanningRequired"))
        assertFalse(handoff.getBoolean("guiPlusEligible"))
        assertEquals("open_app_exact_package", handoff.getString("requiredHandoffAction"))
        assertTrue(handoff.getBoolean("directCrossAppLaunchSupported"))
        assertFalse(handoff.getBoolean("homeTransitionRequired"))

        val ownership = payload.getJSONObject("visualOwnership")
        assertEquals("deepseek", ownership.getString("currentOwner"))
        assertFalse(ownership.getBoolean("guiPlusEligible"))

        val deviceContext = payload.getJSONObject("deviceContext")
        assertEquals("controller", deviceContext.getJSONObject("currentApp").getString("surfaceRole"))
        assertEquals(
            "controller",
            deviceContext.getJSONObject("surfaceContext").getString("role"),
        )
    }

    @Test
    fun externalAppFrameEnablesExclusiveGuiPlusExecution() {
        val payload = buildVisualAgentPayload(
            goal = "搜索压缩饼干",
            snapshot = testSnapshot(packageName = "com.jingdong.app.mall"),
            recentActions = listOf("open_app:ok:target=京东"),
            agentSessionId = "visual-session-work",
        )

        assertEquals("work_surface", payload.getString("surfaceRole"))
        assertEquals("gui_plus", payload.getString("visualDecisionOwner"))
        assertTrue(payload.getBoolean("visualAgentDirect"))
        assertTrue(payload.getBoolean("exclusiveVisualSession"))

        val handoff = payload.getJSONObject("controllerHandoff")
        assertFalse(handoff.getBoolean("isAssistantHost"))
        assertFalse(handoff.getBoolean("controllerHandoffActive"))
        assertFalse(handoff.getBoolean("controllerPlanningRequired"))
        assertTrue(handoff.getBoolean("guiPlusEligible"))
        assertEquals("none", handoff.getString("requiredHandoffAction"))
        assertTrue(handoff.getBoolean("directCrossAppLaunchSupported"))
        assertFalse(handoff.getBoolean("homeTransitionRequired"))
    }

    @Test
    fun controllerSurfaceRejectsGuiActionsUntilDeepSeekSelectsExactPackage() {
        val controllerSnapshot = testSnapshot(packageName = "com.yuchen.ailedger")

        val waitValidation = VisualActionValidator.validate(
            CloudAgentStep(type = "wait"),
            controllerSnapshot,
        )
        assertFalse(waitValidation.ok)
        assertTrue(waitValidation.message.contains("DeepSeek"))

        val tapValidation = VisualActionValidator.validate(
            CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f),
            controllerSnapshot,
        )
        assertFalse(tapValidation.ok)

        val openValidation = VisualActionValidator.validate(
            CloudAgentStep(type = "open_app", packageName = "com.jingdong.app.mall"),
            controllerSnapshot,
        )
        assertTrue(openValidation.ok)

        val targetAppValidation = VisualActionValidator.validate(
            CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f),
            testSnapshot(packageName = "com.jingdong.app.mall"),
        )
        assertTrue(targetAppValidation.ok)
    }

    @Test
    fun payloadCarriesStructuredMultiTurnInteractionWithoutShortReplyTruncation() {
        val longReply = buildString {
            append("预算 80 到 120 元，希望容量 500ml 左右，材质优先陶瓷，")
            append("不要吸管杯，最好是京东自营，评价高一些。")
            append("补充说明：")
            append("A".repeat(700))
        }
        val payload = buildVisualAgentPayload(
            goal = "在京东挑一个合适的水杯",
            snapshot = testSnapshot(),
            recentActions = listOf(
                "guiPlusQuestion:你对价格、容量或材质有什么偏好吗？",
                "userReply:$longReply",
            ),
            agentSessionId = "visual-session-dialogue",
        )

        val interaction = payload.getJSONArray("interactionHistory")
        assertEquals(2, interaction.length())
        assertEquals("assistant", interaction.getJSONObject(0).getString("role"))
        assertEquals("user", interaction.getJSONObject(1).getString("role"))
        assertEquals(longReply, interaction.getJSONObject(1).getString("content"))
        assertEquals(2, payload.getInt("interactionTurnCount"))

        val recent = payload.getJSONArray("recentAgentActions")
        assertTrue(recent.getString(1).endsWith(longReply))

        val memoryInteraction = payload.getJSONObject("agentMemory").getJSONArray("interactionHistory")
        assertEquals(longReply, memoryInteraction.getJSONObject(1).getString("content"))
    }

    @Test
    fun failureOrNoProgressRequestsDeepSeekRouteRefresh() {
        val snapshot = testSnapshot()
        val payload = buildVisualAgentPayload(
            goal = "打开 QQ 个人主页",
            snapshot = snapshot,
            recentActions = listOf(
                "tap_xy|0.5|0.5:failed:点击失败",
                "visual_no_progress:tap_xy|0.5|0.5:count=1:screen=unchanged",
            ),
            agentSessionId = "visual-session-feedback",
        )

        assertTrue(payload.getBoolean("visualReplanRequested"))
        assertTrue(payload.getBoolean("guiPlusReplanRequested"))
        assertTrue(payload.getBoolean("routeRefreshRequested"))
        assertTrue(payload.getBoolean("invalidateCachedAgentBrainRoute"))

        val feedback = payload.getJSONObject("executionFeedback")
        assertFalse(feedback.getBoolean("lastResultOk"))
        assertEquals("visual_no_screen_change", feedback.getString("lastVerification"))
        assertEquals(1, feedback.getInt("noProgressCount"))
        assertEquals("tap_xy|0.5|0.5", feedback.getString("lastActionSignature"))
        assertTrue(feedback.getJSONArray("blockedActionSignatures").length() > 0)
        assertTrue(feedback.getBoolean("visualReplanRequested"))
        assertTrue(feedback.getBoolean("guiPlusReplanRequested"))
        assertTrue(feedback.getBoolean("routeRefreshRequested"))
        assertTrue(feedback.getBoolean("invalidateCachedAgentBrainRoute"))

        val toolResponse = payload.getJSONObject("lastToolResponse")
        assertEquals("tool_response", toolResponse.getString("type"))
        assertEquals("mobile_use", toolResponse.getString("toolName"))
        assertFalse(toolResponse.getBoolean("success"))
        assertTrue(toolResponse.getBoolean("routeRefreshRequested"))

        val memory = payload.getJSONObject("agentMemory")
        val signals = memory.getJSONObject("loopSignals")
        assertEquals(1, signals.getInt("noProgressCount"))
        assertTrue(signals.getBoolean("visualReplanRequested"))
        assertTrue(signals.getBoolean("guiPlusReplanRequested"))
        assertTrue(signals.getBoolean("routeRefreshRequested"))
        assertTrue(signals.getBoolean("invalidateCachedAgentBrainRoute"))
        assertTrue(memory.getJSONArray("verificationEvents").length() > 0)
        assertTrue(memory.getJSONArray("blockedActionSignatures").length() > 0)
    }

    @Test
    fun controllerProtocolRejectionRequestsDeepSeekRouteRefresh() {
        val payload = buildVisualAgentPayload(
            goal = "打开京东搜索商品",
            snapshot = testSnapshot(packageName = "com.yuchen.ailedger"),
            recentActions = listOf(
                "visual_action_rejected:type=wait|reason=DeepSeek must select an exact installed package|replanRequired=true",
            ),
            agentSessionId = "visual-session-controller-reject",
        )

        assertTrue(payload.getBoolean("routeRefreshRequested"))
        assertTrue(payload.getBoolean("invalidateCachedAgentBrainRoute"))
        assertEquals("wait", payload.getJSONObject("executionFeedback").getString("lastActionSignature"))
        assertFalse(payload.getJSONObject("lastToolResponse").getBoolean("success"))
    }

    @Test
    fun finishCandidateRequestsFreshScreenVerificationWithoutRouteReset() {
        val payload = buildVisualAgentPayload(
            goal = "进入个人主页",
            snapshot = testSnapshot(),
            recentActions = listOf(
                "finish_verification_pending:package=com.tencent.mobileqq:fingerprint=abc:reason=已进入个人主页",
            ),
            agentSessionId = "visual-session-finish",
        )

        assertTrue(payload.getBoolean("finishVerificationRequested"))
        assertTrue(payload.getBoolean("visualReplanRequested"))
        assertTrue(payload.getBoolean("guiPlusReplanRequested"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
        assertFalse(payload.getBoolean("invalidateCachedAgentBrainRoute"))

        val feedback = payload.getJSONObject("executionFeedback")
        assertEquals("finish_verification_pending", feedback.getString("lastVerification"))
        assertTrue(feedback.getBoolean("finishVerificationRequested"))
        assertTrue(feedback.getBoolean("visualReplanRequested"))
        assertFalse(feedback.getBoolean("routeRefreshRequested"))

        val toolResponse = payload.getJSONObject("lastToolResponse")
        assertEquals("finish", toolResponse.getString("actionSignature"))
        assertTrue(toolResponse.getBoolean("finishVerificationRequested"))
        assertFalse(toolResponse.getBoolean("routeRefreshRequested"))
    }

    @Test
    fun successfulScreenChangeClearsStaleNoProgressBlock() {
        val payload = buildVisualAgentPayload(
            goal = "打开 QQ 个人主页",
            snapshot = testSnapshot(),
            recentActions = listOf(
                "tap_xy|0.5|0.5:ok:result=已点击",
                "visual_no_progress:tap_xy|0.5|0.5:count=1:screen=unchanged",
                "back:ok:result=已返回",
                "visual_screen_changed:back:screen=changed",
            ),
            agentSessionId = "visual-session-recovered",
        )

        val feedback = payload.getJSONObject("executionFeedback")
        assertTrue(feedback.getBoolean("lastResultOk"))
        assertEquals("visual_screen_changed", feedback.getString("lastVerification"))
        assertEquals(0, feedback.getInt("noProgressCount"))
        assertEquals(0, feedback.getJSONArray("blockedActionSignatures").length())
        assertFalse(payload.getBoolean("visualReplanRequested"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
        assertFalse(payload.getBoolean("invalidateCachedAgentBrainRoute"))
    }

    @Test
    fun repeatedSameActionIsCountedForPlannerGuard() {
        val payload = buildVisualAgentPayload(
            goal = "进入个人主页",
            snapshot = testSnapshot(),
            recentActions = listOf(
                "tap_xy|0.5|0.5:ok:result=已点击",
                "tap_xy|0.5|0.5:ok:result=已点击",
            ),
            agentSessionId = "visual-session-repeat",
        )

        val feedback = payload.getJSONObject("executionFeedback")
        assertEquals(2, feedback.getInt("sameActionCount"))
        assertEquals("tap_xy|0.5|0.5", feedback.getString("lastActionSignature"))
        assertTrue(feedback.getBoolean("lastResultOk"))
    }

    private fun testSnapshot(packageName: String = "com.tencent.mobileqq"): AgentScreenSnapshot {
        return AgentScreenSnapshot(
            currentApp = packageName,
            packageName = packageName,
            nodeCount = 2,
            capturedNodeCount = 2,
            texts = listOf("QQ", "消息"),
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
}
