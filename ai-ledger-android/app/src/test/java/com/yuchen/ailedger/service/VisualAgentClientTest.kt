package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAgentClientTest {
    @Test
    fun verifiedTargetWorkSurfaceBelongsExclusivelyToGuiPlus() {
        val snapshot = testSnapshot(packageName = "com.tencent.mobileqq")
        val runtimeContext = verifiedRuntimeContext(snapshot, "com.tencent.mobileqq")
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
            recentActions = listOf("open_app:ok", "open_app_package_verified:package=com.tencent.mobileqq"),
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
            runtimeContext = runtimeContext,
        )

        assertEquals("visual_agent_step", payload.getString("action"))
        assertEquals("gui_plus", payload.getString("visualDecisionOwner"))
        assertTrue(payload.getBoolean("visualAgentDirect"))
        assertTrue(payload.getBoolean("exclusiveVisualSession"))
        assertFalse(payload.getBoolean("allowAgentBrain"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
        assertEquals("work_surface", payload.getString("surfaceState"))
        assertEquals("com.tencent.mobileqq", payload.getString("verifiedTargetPackage"))
        assertEquals(runtimeContext.observationId, payload.getString("observationId"))
        assertEquals(runtimeContext.observationId, payload.getString("expectedActionObservationId"))

        val ownership = payload.getJSONObject("visualOwnership")
        assertEquals("android_two_brain_ownership_v3_verified_target", ownership.getString("schema"))
        assertEquals("deepseek", ownership.getString("entryOwner"))
        assertEquals("deepseek", ownership.getString("appSelectionOwner"))
        assertEquals("gui_plus", ownership.getString("visualOwner"))
        assertEquals("gui_plus", ownership.getString("currentOwner"))
        assertTrue(ownership.getBoolean("guiPlusEligible"))
        assertTrue(ownership.getBoolean("targetPackageBound"))
        assertFalse(ownership.getBoolean("allowAgentBrain"))

        val runtime = payload.getJSONObject("runtimeExecutionContext")
        assertEquals("android_visual_execution_runtime_v1", runtime.getString("schema"))
        assertEquals("work_surface", runtime.getString("surfaceState"))
        assertEquals("com.tencent.mobileqq", runtime.getString("selectedTargetPackage"))
        assertEquals("com.tencent.mobileqq", runtime.getString("verifiedTargetPackage"))
        assertEquals("com.tencent.mobileqq", runtime.getString("currentPackage"))
        assertTrue(runtime.getBoolean("currentPackageMatchesVerifiedTarget"))
        assertTrue(runtime.getBoolean("guiPlusEligible"))
        assertFalse(runtime.getBoolean("localSemanticDecision"))

        val handoff = payload.getJSONObject("controllerHandoff")
        assertFalse(handoff.getBoolean("controllerHandoffActive"))
        assertTrue(handoff.getBoolean("guiPlusEligible"))
        assertEquals("none", handoff.getString("requiredHandoffAction"))
        assertEquals(runtimeContext.observationId, handoff.getString("observationId"))

        val app = payload.getJSONArray("appContext").getJSONObject(0)
        assertEquals("QQ", app.getString("label"))
        assertEquals("com.tencent.mobileqq", app.getString("packageName"))
        assertEquals("腾讯QQ", app.getJSONArray("aliases").getString(0))
        val capabilities = app.getJSONArray("capabilities")
        val capabilitySet = (0 until capabilities.length()).map { capabilities.getString(it) }.toSet()
        assertEquals(setOf(AppCapability.NativeApp, "social_chat"), capabilitySet)

        val catalog = payload.getJSONObject("appCatalog")
        assertEquals("android_visual_app_catalog_v3_cloud_selected", catalog.getString("schema"))
        assertEquals("deepseek", catalog.getString("selectionOwner"))
        val selectionProtocol = payload.getJSONObject("appSelectionProtocol")
        assertEquals("deepseek", selectionProtocol.getString("semanticOwner"))
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
        assertEquals("deepseek", planning.getString("semanticOwner"))
        assertEquals("android_package_identity_and_safety_only", planning.getString("validationOwner"))
        assertEquals("agentStep.arguments", planning.getString("returnLocation"))

        val supported = payload.getJSONArray("supportedAgentSteps")
        val supportedTypes = (0 until supported.length()).map { supported.getString(it) }.toSet()
        assertEquals(VisualAgentProtocol.supportedStepTypes, supportedTypes)
        assertFalse("tap_node" in supportedTypes)
        assertFalse("scroll" in supportedTypes)

        val memory = payload.getJSONObject("agentMemory")
        assertEquals("android_visual_agent_loop_memory_v12_verified_surface", memory.getString("schema"))
        assertEquals(runtimeContext.observationId, memory.getJSONObject("runtimeExecutionContext").getString("observationId"))
        val deviceContext = payload.getJSONObject("deviceContext")
        assertEquals("android_visual_agent_context_v6_verified_target", deviceContext.getString("schema"))
        assertTrue(deviceContext.getJSONObject("currentApp").getBoolean("matchesVerifiedTarget"))
    }

    @Test
    fun planningContextRequiresDeepSeekEvenWhenAnotherAppIsForeground() {
        val snapshot = testSnapshot(packageName = "com.jingdong.app.mall")
        val runtimeContext = VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.Planning,
            currentPackage = snapshot.packageName,
            observationId = VisualObservationProtocol.observationId(snapshot, 0L, 0L),
        )
        val payload = buildVisualAgentPayload(
            goal = "在京东搜索压缩饼干",
            snapshot = snapshot,
            recentActions = emptyList(),
            appContext = listOf(VisualAgentAppContextItem("京东", "com.jingdong.app.mall")),
            runtimeContext = runtimeContext,
        )

        assertEquals("deepseek", payload.getString("visualDecisionOwner"))
        assertFalse(payload.getBoolean("visualAgentDirect"))
        assertFalse(payload.getBoolean("exclusiveVisualSession"))
        assertTrue(payload.getBoolean("allowAgentBrain"))
        assertEquals("planning", payload.getString("surfaceState"))

        val handoff = payload.getJSONObject("controllerHandoff")
        assertTrue(handoff.getBoolean("controllerHandoffActive"))
        assertFalse(handoff.getBoolean("guiPlusEligible"))
        assertEquals("open_app_exact_package", handoff.getString("requiredHandoffAction"))
    }

    @Test
    fun interruptedTargetSurfaceImmediatelyReturnsControlToDeepSeek() {
        val snapshot = testSnapshot(packageName = "com.android.permissioncontroller")
        val runtimeContext = VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.Interrupted,
            selectedTargetPackage = "com.jingdong.app.mall",
            verifiedTargetPackage = "com.jingdong.app.mall",
            currentPackage = snapshot.packageName,
            observationId = VisualObservationProtocol.observationId(snapshot, 2L, 4L),
            routeEpoch = 2L,
            surfaceEpoch = 4L,
            guiPlusEligible = false,
        )
        val payload = buildVisualAgentPayload(
            goal = "在京东搜索压缩饼干",
            snapshot = snapshot,
            recentActions = emptyList(),
            runtimeContext = runtimeContext,
        )

        assertTrue(payload.getBoolean("routeRefreshRequested"))
        assertTrue(payload.getBoolean("invalidateCachedAgentBrainRoute"))
        assertEquals("deepseek", payload.getString("visualDecisionOwner"))
        assertTrue(payload.getBoolean("allowAgentBrain"))
        assertFalse(payload.getBoolean("exclusiveVisualSession"))
        assertEquals("deepseek_replan", payload.getJSONObject("controllerHandoff").getString("requiredHandoffAction"))
    }

    @Test
    fun localVisualRetryStaysInsideGuiPlusSession() {
        val snapshot = testSnapshot(packageName = "com.jingdong.app.mall")
        val runtimeContext = verifiedRuntimeContext(snapshot, "com.jingdong.app.mall")
        val payload = buildVisualAgentPayload(
            goal = "搜索压缩饼干",
            snapshot = snapshot,
            recentActions = listOf(
                "tap_xy|0.5|0.5:retry:result=点击暂未生效",
                "visual_local_retry:action=tap_xy|0.5|0.5:count=1|reason=screen_unchanged",
            ),
            runtimeContext = runtimeContext,
        )

        assertTrue(payload.getBoolean("localVisualRetryRequested"))
        assertTrue(payload.getBoolean("guiPlusReplanRequested"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
        assertFalse(payload.getBoolean("invalidateCachedAgentBrainRoute"))
        assertEquals("gui_plus", payload.getString("visualDecisionOwner"))
        assertFalse(payload.getBoolean("allowAgentBrain"))
        assertTrue(payload.getBoolean("exclusiveVisualSession"))

        val feedback = payload.getJSONObject("executionFeedback")
        assertEquals("visual_local_retry", feedback.getString("lastVerification"))
        assertTrue(feedback.getBoolean("localVisualRetryRequested"))
        assertFalse(feedback.getBoolean("routeRefreshRequested"))
    }

    @Test
    fun structuralFailureInvalidatesCachedDeepSeekRoute() {
        val snapshot = testSnapshot(packageName = "com.yuchen.ailedger")
        val runtimeContext = VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.Replanning,
            currentPackage = snapshot.packageName,
            observationId = VisualObservationProtocol.observationId(snapshot, 1L, 3L),
            routeEpoch = 1L,
            surfaceEpoch = 3L,
        )
        val payload = buildVisualAgentPayload(
            goal = "打开京东",
            snapshot = snapshot,
            recentActions = listOf(
                "visual_action_rejected:type=wait|failureClass=structural_route|reason=target_surface_required|replanRequired=true",
            ),
            runtimeContext = runtimeContext,
        )

        assertTrue(payload.getBoolean("routeRefreshRequested"))
        assertTrue(payload.getBoolean("invalidateCachedAgentBrainRoute"))
        assertFalse(payload.getBoolean("localVisualRetryRequested"))
        assertEquals("deepseek", payload.getString("visualDecisionOwner"))
        assertEquals("structural_route_failure", payload.getJSONObject("executionFeedback").getString("lastVerification"))
    }

    @Test
    fun finishVerificationDoesNotResetGlobalRoute() {
        val snapshot = testSnapshot(packageName = "com.tencent.mobileqq")
        val runtimeContext = verifiedRuntimeContext(snapshot, "com.tencent.mobileqq")
        val payload = buildVisualAgentPayload(
            goal = "进入个人主页",
            snapshot = snapshot,
            recentActions = listOf(
                "finish_verification_pending:package=com.tencent.mobileqq:fingerprint=abc:observationId=obs:reason=已进入个人主页",
            ),
            runtimeContext = runtimeContext,
        )

        assertTrue(payload.getBoolean("finishVerificationRequested"))
        assertTrue(payload.getBoolean("guiPlusReplanRequested"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
        assertFalse(payload.getBoolean("invalidateCachedAgentBrainRoute"))
        assertEquals("gui_plus", payload.getString("visualDecisionOwner"))
    }

    @Test
    fun appInventoryHashChangesWhenCatalogChanges() {
        val snapshot = testSnapshot()
        val runtimeContext = verifiedRuntimeContext(snapshot, snapshot.packageName)
        val base = buildVisualAgentPayload(
            goal = "打开应用",
            snapshot = snapshot,
            recentActions = emptyList(),
            appContext = listOf(VisualAgentAppContextItem("QQ", "com.tencent.mobileqq")),
            runtimeContext = runtimeContext,
        )
        val changed = buildVisualAgentPayload(
            goal = "打开应用",
            snapshot = snapshot,
            recentActions = emptyList(),
            appContext = listOf(
                VisualAgentAppContextItem("QQ", "com.tencent.mobileqq"),
                VisualAgentAppContextItem("同花顺炒股票", "com.hexin.plat.android"),
            ),
            runtimeContext = runtimeContext,
        )

        assertNotEquals(base.getString("appInventoryHash"), changed.getString("appInventoryHash"))
    }

    @Test
    fun validatorRequiresVerifiedTargetBeforeGuiActionsOrFinish() {
        val snapshot = testSnapshot(packageName = "com.jingdong.app.mall")
        val planningContext = VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.Planning,
            currentPackage = snapshot.packageName,
            observationId = "planning-observation",
        )

        val tapValidation = VisualActionValidator.validate(
            CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f),
            snapshot,
            planningContext,
        )
        assertFalse(tapValidation.ok)
        assertEquals(VisualFailureClass.StructuralRoute, tapValidation.failureClass)

        val finishValidation = VisualActionValidator.validate(
            CloudAgentStep(type = "finish", reason = "完成"),
            snapshot,
            planningContext,
        )
        assertFalse(finishValidation.ok)

        val openValidation = VisualActionValidator.validate(
            CloudAgentStep(type = "open_app", packageName = "com.jingdong.app.mall"),
            snapshot,
            planningContext,
        )
        assertTrue(openValidation.ok)

        val verifiedContext = verifiedRuntimeContext(snapshot, snapshot.packageName)
        val verifiedTap = VisualActionValidator.validate(
            CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f),
            snapshot,
            verifiedContext,
        )
        assertTrue(verifiedTap.ok)
        val verifiedFinish = VisualActionValidator.validate(
            CloudAgentStep(type = "finish", reason = "完成"),
            snapshot,
            verifiedContext,
        )
        assertTrue(verifiedFinish.ok)
    }

    @Test
    fun payloadCarriesStructuredMultiTurnInteractionWithoutTruncatingNormalReply() {
        val longReply = buildString {
            append("预算 80 到 120 元，希望容量 500ml 左右，材质优先陶瓷，")
            append("不要吸管杯，最好是京东自营，评价高一些。")
            append("补充说明：")
            append("A".repeat(700))
        }
        val snapshot = testSnapshot(packageName = "com.jingdong.app.mall")
        val payload = buildVisualAgentPayload(
            goal = "在京东挑一个合适的水杯",
            snapshot = snapshot,
            recentActions = listOf(
                "guiPlusQuestion:你对价格、容量或材质有什么偏好吗？",
                "userReply:$longReply",
            ),
            agentSessionId = "visual-session-dialogue",
            runtimeContext = verifiedRuntimeContext(snapshot, snapshot.packageName),
        )

        val interaction = payload.getJSONArray("interactionHistory")
        assertEquals(2, interaction.length())
        assertEquals("assistant", interaction.getJSONObject(0).getString("role"))
        assertEquals("user", interaction.getJSONObject(1).getString("role"))
        assertEquals(longReply, interaction.getJSONObject(1).getString("content"))
    }

    private fun verifiedRuntimeContext(
        snapshot: AgentScreenSnapshot,
        packageName: String,
    ): VisualAgentRuntimeContext {
        return VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.WorkSurface,
            selectedTargetPackage = packageName,
            verifiedTargetPackage = packageName,
            currentPackage = snapshot.packageName,
            observationId = VisualObservationProtocol.observationId(snapshot, 1L, 2L),
            routeEpoch = 1L,
            surfaceEpoch = 2L,
            guiPlusEligible = snapshot.packageName == packageName,
        )
    }

    private fun testSnapshot(
        packageName: String = "com.tencent.mobileqq",
        texts: List<String> = listOf("QQ", "消息"),
        nodeCount: Int = 2,
    ): AgentScreenSnapshot {
        return AgentScreenSnapshot(
            currentApp = packageName,
            packageName = packageName,
            nodeCount = nodeCount,
            capturedNodeCount = nodeCount,
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
}
