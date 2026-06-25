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

        val runtime = payload.getJSONObject("runtimeExecutionContext")
        assertEquals("android_visual_execution_runtime_v1", runtime.getString("schema"))
        assertEquals("work_surface", runtime.getString("surfaceState"))
        assertEquals("com.tencent.mobileqq", runtime.getString("selectedTargetPackage"))
        assertEquals("com.tencent.mobileqq", runtime.getString("verifiedTargetPackage"))
        assertEquals("com.tencent.mobileqq", runtime.getString("currentPackage"))
        assertTrue(runtime.getBoolean("currentPackageMatchesVerifiedTarget"))
        assertTrue(runtime.getBoolean("guiPlusEligible"))
        assertFalse(runtime.getBoolean("localSemanticDecision"))
        val surfaceContext = payload.getJSONObject("surfaceContext")
        assertEquals("work_surface", surfaceContext.getString("role"))
        assertEquals("com.tencent.mobileqq", surfaceContext.getString("currentPackage"))
        assertTrue(surfaceContext.getBoolean("currentPackageMatchesVerifiedTarget"))

        val app = payload.getJSONArray("appContext").getJSONObject(0)
        assertEquals("QQ", app.getString("label"))
        assertEquals("com.tencent.mobileqq", app.getString("packageName"))
        assertEquals("腾讯QQ", app.getJSONArray("aliases").getString(0))
        val capabilities = app.getJSONArray("capabilities")
        val capabilitySet = (0 until capabilities.length()).map { capabilities.getString(it) }.toSet()
        assertEquals(setOf(AppCapability.NativeApp, "social_chat"), capabilitySet)

        val catalog = payload.getJSONObject("appCatalog")
        assertEquals("android_visual_app_catalog_v4_single_directory", catalog.getString("schema"))
        assertEquals("deepseek", catalog.getString("selectionOwner"))
        assertEquals("appContext", catalog.getString("entriesField"))
        assertTrue(catalog.getBoolean("entriesIncludedOnce"))
        assertFalse(catalog.has("entries"))
        val selectionProtocol = payload.getJSONObject("appSelectionProtocol")
        assertEquals("deepseek", selectionProtocol.getString("semanticOwner"))
        assertEquals("appContext", selectionProtocol.getString("catalogEntriesField"))
        assertFalse(selectionProtocol.getBoolean("androidSelectsApps"))
        assertFalse(selectionProtocol.getBoolean("androidRanksApps"))
        assertFalse(selectionProtocol.getBoolean("androidResolvesUserIntent"))
        assertFalse(selectionProtocol.getBoolean("localKeywordMatching"))

        val uploadedProfile = payload.getJSONObject("deviceProfile")
        assertEquals("TestModel", uploadedProfile.getString("model"))
        assertEquals(35, uploadedProfile.getInt("sdkInt"))

        assertFalse(payload.has("taskContract"))
        assertFalse(payload.has("taskContractPlanning"))

        val supported = payload.getJSONArray("supportedAgentSteps")
        val supportedTypes = (0 until supported.length()).map { supported.getString(it) }.toSet()
        assertEquals(VisualAgentProtocol.supportedStepTypes, supportedTypes)
        assertFalse("tap_node" in supportedTypes)
        assertFalse("scroll" in supportedTypes)
        val supportedDeviceTools = payload.getJSONArray("supportedDeviceTools")
        val supportedDeviceToolTypes = (0 until supportedDeviceTools.length())
            .map { supportedDeviceTools.getString(it) }
            .toSet()
        assertEquals(CloudAgentStep.deviceToolTypes, supportedDeviceToolTypes)

        val memory = payload.getJSONObject("agentMemory")
        assertEquals("android_visual_agent_loop_memory_v13_cloud_route_visual_loop", memory.getString("schema"))
        assertEquals(runtimeContext.observationId, memory.getJSONObject("runtimeExecutionContext").getString("observationId"))
        assertEquals("work_surface", memory.getJSONObject("surfaceContext").getString("role"))
        val deviceContext = payload.getJSONObject("deviceContext")
        assertEquals("android_visual_agent_context_v8_single_app_directory", deviceContext.getString("schema"))
        assertTrue(deviceContext.getJSONObject("currentApp").getBoolean("matchesVerifiedTarget"))
        assertEquals("work_surface", deviceContext.getJSONObject("surfaceContext").getString("role"))
        assertEquals("appContext", deviceContext.getString("appCatalogEntriesField"))
        assertFalse(deviceContext.has("installedApps"))
        assertFalse(deviceContext.has("appCatalog"))
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

        assertFalse(payload.has("controllerHandoff"))
        assertFalse(payload.getJSONObject("runtimeExecutionContext").getBoolean("guiPlusEligible"))
    }

    @Test
    fun transientCrossPackageSurfaceStaysInsideGuiPlusLoop() {
        val snapshot = testSnapshot(packageName = "com.android.permissioncontroller")
        val runtimeContext = VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.WorkSurface,
            selectedTargetPackage = "com.jingdong.app.mall",
            verifiedTargetPackage = "com.jingdong.app.mall",
            currentPackage = snapshot.packageName,
            observationId = VisualObservationProtocol.observationId(snapshot, 2L, 4L),
            routeEpoch = 2L,
            surfaceEpoch = 4L,
            guiPlusEligible = true,
        )
        val payload = buildVisualAgentPayload(
            goal = "在京东搜索压缩饼干",
            snapshot = snapshot,
            recentActions = emptyList(),
            runtimeContext = runtimeContext,
        )

        assertFalse(payload.getBoolean("routeRefreshRequested"))
        assertFalse(payload.getBoolean("invalidateCachedAgentBrainRoute"))
        assertEquals("gui_plus", payload.getString("visualDecisionOwner"))
        assertFalse(payload.getBoolean("allowAgentBrain"))
        assertTrue(payload.getBoolean("exclusiveVisualSession"))
        assertEquals("work_surface", payload.getJSONObject("runtimeExecutionContext").getString("surfaceState"))
        assertFalse(payload.getJSONObject("runtimeExecutionContext").getBoolean("currentPackageMatchesVerifiedTarget"))
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
        assertEquals(0, feedback.getInt("screenChangedCount"))
        assertTrue(feedback.getBoolean("localVisualRetryRequested"))
        assertFalse(feedback.getBoolean("routeRefreshRequested"))
    }

    @Test
    fun screenChangedFeedbackIsCarriedIntoPayloadSignals() {
        val snapshot = testSnapshot(packageName = "com.jingdong.app.mall")
        val payload = buildVisualAgentPayload(
            goal = "打开商品详情",
            snapshot = snapshot,
            recentActions = listOf(
                "tap_xy|0.5|0.5:ok:result=已点击",
                "visual_screen_changed:action=tap_xy|0.5|0.5:reason=navigated",
            ),
            runtimeContext = verifiedRuntimeContext(snapshot, snapshot.packageName),
        )

        val feedback = payload.getJSONObject("executionFeedback")
        assertEquals(1, feedback.getInt("screenChangedCount"))
        val toolResponse = payload.getJSONObject("lastToolResponse")
        assertEquals(1, toolResponse.getInt("screenChangedCount"))
        val loopSignals = payload.getJSONObject("agentMemory").getJSONObject("loopSignals")
        assertEquals(1, loopSignals.getInt("screenChangedCount"))
        assertTrue(loopSignals.getBoolean("currentPackageMatchesVerifiedTarget"))
    }

    @Test
    fun explorationSprawlFeedbackRequestsGuiReplanWithoutRefreshingDeepSeekRoute() {
        val snapshot = testSnapshot(packageName = "com.tencent.mobileqq")
        val payload = buildVisualAgentPayload(
            goal = "缁欑洰鏍囪仈绯讳汉鍙戞秷鎭?",
            snapshot = snapshot,
            recentActions = listOf(
                "tap_xy|0.8|0.2:ok:result=宸茬偣鍑?",
                "visual_exploration_sprawl:tap_xy|0.8|0.2:count=4:screen=changed|failureClass=visual_local|reason=multi_hop_without_convergence",
            ),
            runtimeContext = verifiedRuntimeContext(snapshot, snapshot.packageName),
        )

        assertTrue(payload.getBoolean("localVisualRetryRequested"))
        assertTrue(payload.getBoolean("guiPlusReplanRequested"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
        assertEquals("gui_plus", payload.getString("visualDecisionOwner"))

        val feedback = payload.getJSONObject("executionFeedback")
        assertEquals("visual_exploration_sprawl", feedback.getString("lastVerification"))
        assertEquals(1, feedback.getInt("explorationSprawlCount"))
        assertEquals(1, payload.getJSONObject("lastToolResponse").getInt("explorationSprawlCount"))
        assertEquals(1, payload.getJSONObject("agentMemory").getJSONObject("loopSignals").getInt("explorationSprawlCount"))
    }

    @Test
    fun repeatedSuccessfulStepsSurfaceExplorationBudgetPressure() {
        val snapshot = testSnapshot(packageName = "com.tencent.mobileqq")
        val payload = buildVisualAgentPayload(
            goal = "瀹屾垚澶栭儴搴旂敤椤甸潰浠诲姟",
            snapshot = snapshot,
            recentActions = listOf(
                "tap_xy|0.1|0.1:ok:result=1",
                "tap_xy|0.2|0.2:ok:result=2",
                "tap_xy|0.3|0.3:ok:result=3",
                "tap_xy|0.4|0.4:ok:result=4",
                "tap_xy|0.5|0.5:ok:result=5",
                "tap_xy|0.6|0.6:ok:result=6",
            ),
            runtimeContext = verifiedRuntimeContext(snapshot, snapshot.packageName),
        )

        val feedback = payload.getJSONObject("executionFeedback")
        assertEquals("medium", feedback.getString("explorationPressureLevel"))
        assertEquals(3, feedback.getInt("explorationBudgetRemaining"))
        assertFalse(feedback.getBoolean("explorationBudgetExceeded"))

        val loopSignals = payload.getJSONObject("agentMemory").getJSONObject("loopSignals")
        assertEquals("medium", loopSignals.getString("explorationPressureLevel"))
        assertEquals(3, loopSignals.getInt("explorationBudgetRemaining"))
        assertFalse(loopSignals.getBoolean("explorationBudgetExceeded"))
    }

    @Test
    fun structuralVisualFailureAlsoStaysInsideGuiPlusSession() {
        val snapshot = testSnapshot(packageName = "com.jingdong.app.mall")
        val payload = buildVisualAgentPayload(
            goal = "搜索压缩饼干",
            snapshot = snapshot,
            recentActions = listOf(
                "visual_action_rejected:type=open_app|failureClass=structural_route|reason=gui_plus_cannot_select_app|replanRequired=true",
            ),
            runtimeContext = verifiedRuntimeContext(snapshot, snapshot.packageName),
        )

        assertTrue(payload.getBoolean("guiPlusReplanRequested"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
        assertEquals("gui_plus", payload.getString("visualDecisionOwner"))
        assertFalse(payload.getBoolean("allowAgentBrain"))
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
    fun appCatalogArrayIsSerializedOnlyOnceInTopLevelAppContext() {
        val snapshot = testSnapshot(packageName = "com.yuchen.ailedger")
        val runtimeContext = VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.Planning,
            currentPackage = snapshot.packageName,
            observationId = VisualObservationProtocol.observationId(snapshot, 0L, 0L),
        )
        val payload = buildVisualAgentPayload(
            goal = "打开目标应用",
            snapshot = snapshot,
            recentActions = emptyList(),
            appContext = listOf(
                VisualAgentAppContextItem("QQ", "com.tencent.mobileqq"),
                VisualAgentAppContextItem("同花顺炒股票", "com.hexin.plat.android"),
            ),
            runtimeContext = runtimeContext,
        )

        assertEquals(2, payload.getJSONArray("appContext").length())
        assertFalse(payload.getJSONObject("appCatalog").has("entries"))
        assertFalse(payload.getJSONObject("deviceContext").has("installedApps"))
        assertFalse(payload.getJSONObject("deviceContext").has("appCatalog"))
        val serialized = payload.toString()
        assertEquals(2, serialized.windowed("com.tencent.mobileqq".length).count { it == "com.tencent.mobileqq" })
        assertEquals(2, serialized.windowed("com.hexin.plat.android".length).count { it == "com.hexin.plat.android" })
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
