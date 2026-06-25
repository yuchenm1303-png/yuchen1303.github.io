package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAgentClientTest {
    @Test
    fun verifiedTargetWorkSurfaceBelongsExclusivelyToGuiPlusAndUsesUnifiedProtocol() {
        val snapshot = testSnapshot(packageName = "com.tencent.mobileqq")
        val runtimeContext = verifiedRuntimeContext(snapshot, "com.tencent.mobileqq")
        val payload = buildVisualAgentPayload(
            goal = "打开 QQ 个人主页",
            snapshot = snapshot,
            recentActions = listOf(
                "open_app:ok",
                "open_app_package_verified:package=com.tencent.mobileqq",
            ),
            visualHistory = listOf(history("one"), history("two"), history("three")),
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
        assertEquals(runtimeContext.observationId, payload.getString("expectedActionObservationId"))
        assertEquals(
            "android_visual_agent_loop_memory_v14_task_contract_harness",
            payload.getJSONObject("agentMemory").getString("schema"),
        )

        val supported = payload.getJSONArray("supportedAgentSteps")
        val supportedTypes = (0 until supported.length()).map { supported.getString(it) }.toSet()
        assertEquals(CloudAgentStep.supportedTypes, supportedTypes)
        assertEquals(VisualAgentProtocol.supportedStepTypes, supportedTypes)
        assertTrue("tap_node" in supportedTypes)
        assertTrue("scroll" in supportedTypes)
        assertTrue("recents" in supportedTypes)
        assertTrue("notifications" in supportedTypes)
        assertTrue("quick_settings" in supportedTypes)

        val app = payload.getJSONArray("appContext").getJSONObject(0)
        assertEquals("QQ", app.getString("label"))
        assertEquals("com.tencent.mobileqq", app.getString("packageName"))
        assertFalse(payload.getJSONObject("appCatalog").has("entries"))
        assertFalse(payload.getJSONObject("deviceContext").has("installedApps"))
        assertEquals(2, payload.getJSONArray("visualHistory").length())
    }

    @Test
    fun taskMemoryIsUploadedToAllThreeFeedbackEnvelopes() {
        val snapshot = testSnapshot(packageName = "com.example.app")
        val contract = VisualTaskContract(
            originalGoal = "查看订单",
            currentMilestoneId = "orders",
            milestones = listOf(
                VisualTaskMilestone("orders", successEvidence = listOf("全部订单")),
            ),
        )
        val failed = VisualFailedHypothesis(
            hypothesisId = "orders-entry",
            milestoneId = "orders",
            pageStateId = "page-a",
            actionSignature = "tap_xy|0.5|0.5",
            actionCluster = "tap_xy|打开订单",
            purpose = "打开订单",
            failureReason = "没有出现订单列表",
        )
        val memory = VisualTaskMemory(
            originalGoal = "查看订单",
            currentMilestoneId = "orders",
            failedHypotheses = listOf(failed),
            blockedActions = listOf(
                VisualBlockedAction(
                    "orders",
                    "page-a",
                    "tap_xy|打开订单",
                    "orders-entry",
                    "same hypothesis",
                ),
            ),
            remainingExplorationBudget = 1,
            lastConfirmedPage = VisualPageState("page-home", "com.example.app", "我的"),
            progressStatus = "stalled",
            replanRequested = true,
            recoveryMode = true,
            legacyMode = false,
            taskContract = contract,
        )
        val payload = buildVisualAgentPayload(
            goal = "查看订单",
            snapshot = snapshot,
            recentActions = listOf(
                "visual_local_retry:action=tap_xy:count=1|replanRequired=true",
            ),
            visualHistory = listOf(
                history("one"),
                history("two"),
                history("three"),
                history("four"),
            ),
            runtimeContext = verifiedRuntimeContext(snapshot, snapshot.packageName),
            taskMemory = memory,
        )

        assertEquals(
            "orders",
            payload.getJSONObject("executionFeedback").getString("currentMilestoneId"),
        )
        assertEquals(
            1,
            payload.getJSONObject("executionFeedback").getJSONArray("failedHypotheses").length(),
        )
        assertEquals(
            "orders",
            payload.getJSONObject("lastToolResponse").getString("currentMilestoneId"),
        )
        val agentMemory = payload.getJSONObject("agentMemory")
        assertEquals(
            "orders",
            agentMemory.getJSONObject("taskMemory").getString("currentMilestoneId"),
        )
        assertEquals(
            "orders",
            agentMemory.getJSONObject("taskContract").getString("currentMilestoneId"),
        )
        assertEquals(
            "orders",
            payload.getJSONObject("taskContract").getString("currentMilestoneId"),
        )
        assertEquals(4, payload.getJSONArray("visualHistory").length())
    }

    @Test
    fun oldBackendModeKeepsPayloadCompatibleWithoutTaskContract() {
        val snapshot = testSnapshot()
        val payload = buildVisualAgentPayload(
            goal = "打开页面",
            snapshot = snapshot,
            recentActions = emptyList(),
            runtimeContext = verifiedRuntimeContext(snapshot, snapshot.packageName),
        )

        assertFalse(payload.has("taskContract"))
        assertTrue(payload.getJSONObject("agentMemory").isNull("taskMemory"))
        assertEquals(1, payload.getInt("actionBatchMax"))
    }

    @Test
    fun localSemanticRetryRequestsGuiReplanWithoutRefreshingDeepSeekRoute() {
        val snapshot = testSnapshot(packageName = "com.jingdong.app.mall")
        val payload = buildVisualAgentPayload(
            goal = "搜索压缩饼干",
            snapshot = snapshot,
            recentActions = listOf(
                "tap_xy|0.5|0.5:ok:result=已点击",
                "visual_local_retry:action=tap_xy|0.5|0.5:count=2|semanticStatus=ambiguous|replanRequired=true",
            ),
            runtimeContext = verifiedRuntimeContext(snapshot, snapshot.packageName),
        )

        assertTrue(payload.getBoolean("localVisualRetryRequested"))
        assertTrue(payload.getBoolean("guiPlusReplanRequested"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
        assertEquals(
            "semantic_ambiguous",
            payload.getJSONObject("executionFeedback").getString("lastVerification"),
        )
    }

    @Test
    fun structuralFailureInvalidatesRouteOnlyOutsideExclusiveGuiSession() {
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
        assertEquals("deepseek", payload.getString("visualDecisionOwner"))
    }

    @Test
    fun appDirectoryIsSerializedOnlyAtTopLevel() {
        val snapshot = testSnapshot(packageName = "com.yuchen.ailedger")
        val payload = buildVisualAgentPayload(
            goal = "打开目标应用",
            snapshot = snapshot,
            recentActions = emptyList(),
            appContext = listOf(
                VisualAgentAppContextItem("QQ", "com.tencent.mobileqq"),
                VisualAgentAppContextItem("同花顺炒股票", "com.hexin.plat.android"),
            ),
            runtimeContext = VisualAgentRuntimeContext(
                surfaceState = VisualSurfaceState.Planning,
                currentPackage = snapshot.packageName,
                observationId = VisualObservationProtocol.observationId(snapshot, 0L, 0L),
            ),
        )

        assertEquals(2, payload.getJSONArray("appContext").length())
        assertFalse(payload.getJSONObject("appCatalog").has("entries"))
        assertFalse(payload.getJSONObject("deviceContext").has("installedApps"))
        val serialized = payload.toString()
        assertEquals(
            1,
            serialized.windowed("com.tencent.mobileqq".length)
                .count { it == "com.tencent.mobileqq" },
        )
        assertEquals(
            1,
            serialized.windowed("com.hexin.plat.android".length)
                .count { it == "com.hexin.plat.android" },
        )
    }

    @Test
    fun inventoryHashChangesWhenCatalogChanges() {
        val snapshot = testSnapshot()
        val runtimeContext = verifiedRuntimeContext(snapshot, snapshot.packageName)
        val base = buildVisualAgentPayload(
            goal = "打开应用",
            snapshot = snapshot,
            recentActions = emptyList(),
            appContext = listOf(
                VisualAgentAppContextItem("QQ", "com.tencent.mobileqq"),
            ),
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
    fun validatorMatchesAdvertisedExecutorProtocol() {
        val snapshot = testSnapshot(packageName = "com.example.app")
        val verified = verifiedRuntimeContext(snapshot, snapshot.packageName)
        listOf("tap_node", "scroll", "recents", "notifications", "quick_settings").forEach { type ->
            val step = when (type) {
                "tap_node" -> CloudAgentStep(type = type, targetText = "目标")
                "scroll" -> CloudAgentStep(type = type, direction = "up")
                else -> CloudAgentStep(type = type)
            }
            assertTrue(
                "$type should validate",
                VisualActionValidator.validate(step, snapshot, verified).ok,
            )
        }
        assertEquals(CloudAgentStep.supportedTypes, VisualAgentProtocol.supportedStepTypes)
    }

    private fun history(label: String) = VisualAgentHistoryItem(
        screenshot = testSnapshot().visual!!,
        assistantOutput = label,
        executionResult = label,
    )

    private fun verifiedRuntimeContext(
        snapshot: AgentScreenSnapshot,
        packageName: String,
    ): VisualAgentRuntimeContext = VisualAgentRuntimeContext(
        surfaceState = VisualSurfaceState.WorkSurface,
        selectedTargetPackage = packageName,
        verifiedTargetPackage = packageName,
        currentPackage = snapshot.packageName,
        observationId = VisualObservationProtocol.observationId(snapshot, 1L, 2L),
        routeEpoch = 1L,
        surfaceEpoch = 2L,
        guiPlusEligible = snapshot.packageName == packageName,
    )

    private fun testSnapshot(
        packageName: String = "com.tencent.mobileqq",
        texts: List<String> = listOf("QQ", "消息"),
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
