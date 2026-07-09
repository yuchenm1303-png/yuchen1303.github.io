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
        val runtime = verifiedRuntimeContext(snapshot, snapshot.packageName)
        val payload = buildVisualAgentPayload(
            goal = "打开 QQ 个人主页",
            snapshot = snapshot,
            recentActions = listOf("open_app:ok", "open_app_package_verified:package=com.tencent.mobileqq"),
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
            runtimeContext = runtime,
        )

        assertEquals("visual_agent_step", payload.getString("action"))
        assertEquals("gui_plus_exclusive", payload.getString("visualDecisionOwner"))
        assertEquals("gui_plus_exclusive_visual", payload.getString("decisionOwner"))
        assertTrue(payload.getBoolean("visualAgentDirect"))
        assertTrue(payload.getBoolean("exclusiveVisualSession"))
        assertFalse(payload.getBoolean("allowAgentBrain"))
        assertEquals(runtime.observationId, payload.getString("expectedActionObservationId"))
        assertTrue(payload.has("agentMemory"))
        assertEquals("gui_plus", payload.getJSONObject("agentMemory").getString("visualDecisionOwner"))
        assertEquals("gui_plus", payload.getJSONObject("agentMemory").getJSONObject("visualOwnership").getString("owner"))
        assertEquals(runtime.observationId, payload.getJSONObject("agentMemory").getJSONObject("runtimeExecutionContext").getString("observationId"))
        assertTrue(payload.isNull("taskMemory"))

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
        assertEquals("gui_plus", payload.getJSONObject("appCatalog").getString("selectionOwner"))
        assertFalse(payload.getJSONObject("appCatalog").has("entries"))
        assertFalse(payload.getJSONObject("deviceContext").has("installedApps"))
        assertEquals(3, payload.getJSONArray("visualHistory").length())
    }

    @Test
    fun taskMemoryHasOneCanonicalExecutionLedgerSource() {
        val snapshot = testSnapshot(packageName = "com.example.app")
        val contract = VisualTaskContract(
            originalGoal = "查看订单",
            currentMilestoneId = "orders",
            milestones = listOf(VisualTaskMilestone("orders", successEvidence = listOf("全部订单"))),
        )
        val memory = VisualTaskMemory(
            originalGoal = "查看订单",
            currentMilestoneId = "orders",
            failedHypotheses = listOf(
                VisualFailedHypothesis(
                    hypothesisId = "orders-entry",
                    milestoneId = "orders",
                    pageStateId = "page-a",
                    actionSignature = "tap_xy|0.5|0.5",
                    actionCluster = "tap_xy|打开订单",
                    purpose = "打开订单",
                    failureReason = "没有出现订单列表",
                ),
            ),
            blockedActions = listOf(
                VisualBlockedAction("orders", "page-a", "tap_xy|打开订单", "orders-entry", "same hypothesis"),
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
            recentActions = listOf("visual_local_retry:action=tap_xy:count=1|replanRequired=true"),
            visualHistory = listOf(history("one"), history("two"), history("three"), history("four")),
            runtimeContext = verifiedRuntimeContext(snapshot, snapshot.packageName),
            taskMemory = memory,
        )

        assertEquals("orders", payload.getJSONObject("executionFeedback").getString("currentMilestoneId"))
        val taskMemory = payload.getJSONObject("taskMemory")
        assertEquals("orders", taskMemory.getString("currentMilestoneId"))
        assertEquals("orders", taskMemory.getJSONObject("taskContract").getString("currentMilestoneId"))
        assertFalse(taskMemory.has("failedHypotheses"))
        assertFalse(taskMemory.has("blockedActions"))
        assertFalse(payload.has("lastToolResponse"))
        assertTrue(payload.has("agentMemory"))
        assertEquals("orders", payload.getJSONObject("agentMemory").getJSONObject("taskMemory").getString("currentMilestoneId"))
        assertFalse(payload.has("taskContract"))
        assertEquals(4, payload.getJSONArray("visualHistory").length())
    }

    @Test
    fun payloadWithoutTaskContractUsesNullCanonicalMemory() {
        val snapshot = testSnapshot()
        val payload = buildVisualAgentPayload(
            goal = "打开页面",
            snapshot = snapshot,
            recentActions = emptyList(),
            runtimeContext = verifiedRuntimeContext(snapshot, snapshot.packageName),
        )

        assertFalse(payload.has("taskContract"))
        assertTrue(payload.isNull("taskMemory"))
        assertTrue(payload.getJSONObject("agentMemory").isNull("taskMemory"))
        assertEquals(1, payload.getInt("actionBatchMax"))
    }

    @Test
    fun localSemanticRetryIsOnlyObjectiveHistoryNotAndroidControl() {
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

        assertFalse(payload.has("localVisualRetryRequested"))
        assertFalse(payload.has("guiPlusReplanRequested"))
        assertFalse(payload.has("routeRefreshRequested"))
        assertTrue(payload.getJSONArray("recentAgentActions").getString(1).startsWith("visual_local_retry:"))
        assertFalse(payload.getJSONObject("executionFeedback").getBoolean("localSemanticDecision"))
    }

    @Test
    fun structuralReplanningUsesRuntimeStateWithoutLegacyRouteFlags() {
        val snapshot = testSnapshot(packageName = "com.yuchen.ailedger")
        val runtime = VisualAgentRuntimeContext(
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
            runtimeContext = runtime,
        )

        assertFalse(payload.has("routeRefreshRequested"))
        assertFalse(payload.has("invalidateCachedAgentBrainRoute"))
        assertEquals("gui_plus_exclusive", payload.getString("visualDecisionOwner"))
        assertTrue(payload.getBoolean("exclusiveVisualSession"))
        assertFalse(payload.getBoolean("allowAgentBrain"))
        assertTrue(payload.getJSONObject("executionFeedback").getBoolean("replanRequested"))
        assertTrue(payload.getJSONObject("executionFeedback").getBoolean("structuralRegression"))
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
        assertEquals("gui_plus", payload.getJSONObject("appCatalog").getString("selectionOwner"))
        assertFalse(payload.getJSONObject("appCatalog").has("entries"))
        assertFalse(payload.getJSONObject("deviceContext").has("installedApps"))
        assertFalse(payload.getJSONObject("agentMemory").has("appContext"))
        assertFalse(payload.getJSONObject("agentMemory").has("installedApps"))
    }

    @Test
    fun inventoryHashChangesWhenCatalogChanges() {
        val snapshot = testSnapshot()
        val runtime = verifiedRuntimeContext(snapshot, snapshot.packageName)
        val base = buildVisualAgentPayload(
            goal = "打开应用",
            snapshot = snapshot,
            recentActions = emptyList(),
            appContext = listOf(VisualAgentAppContextItem("QQ", "com.tencent.mobileqq")),
            runtimeContext = runtime,
        )
        val changed = buildVisualAgentPayload(
            goal = "打开应用",
            snapshot = snapshot,
            recentActions = emptyList(),
            appContext = listOf(
                VisualAgentAppContextItem("QQ", "com.tencent.mobileqq"),
                VisualAgentAppContextItem("同花顺炒股票", "com.hexin.plat.android"),
            ),
            runtimeContext = runtime,
        )

        assertNotEquals(base.getString("appInventoryHash"), changed.getString("appInventoryHash"))
    }

    @Test
    fun validatorMatchesAdvertisedExecutorProtocol() {
        val snapshot = testSnapshot(packageName = "com.example.app")
        val verified = verifiedRuntimeContext(snapshot, snapshot.packageName)
        listOf("tap_node", "scroll", "recents", "notifications", "quick_settings").forEach { type ->
            val baseStep = when (type) {
                "tap_node" -> CloudAgentStep(type = type, targetText = "目标")
                "scroll" -> CloudAgentStep(type = type, direction = "up")
                else -> CloudAgentStep(type = type)
            }
            val step = permitted(baseStep, snapshot, verified)
            assertTrue("$type should validate", VisualActionValidator.validate(step, snapshot, verified).ok)
        }
        assertEquals(CloudAgentStep.supportedTypes, VisualAgentProtocol.supportedStepTypes)
    }

    private fun permitted(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        runtime: VisualAgentRuntimeContext,
    ): CloudAgentStep {
        val sessionId = "visual-session-test"
        val kind = "gui_transaction_validated"
        val hash = VisualActionValidator.executionPermitHash(
            sessionId = sessionId,
            observationId = runtime.observationId,
            packageName = snapshot.packageName,
            kind = kind,
            step = step,
            canonicalX = step.x?.toDouble(),
            canonicalY = step.y?.toDouble(),
        )
        return step.copy(
            toolArgs = org.json.JSONObject().apply {
                put("responseSessionId", sessionId)
                put("responseObservationId", runtime.observationId)
                put("executionPermitVersion", "visual_execution_permit_v2")
                put("executionPermitId", "permit_$hash")
                put("executionPermitKind", kind)
                put("executionPermitObservationId", runtime.observationId)
                put("executionPermitSessionId", sessionId)
                put("executionPermitPackageName", snapshot.packageName)
                put("executionPermitActionType", step.type)
                step.x?.let { put("executionPermitX", it) }
                step.y?.let { put("executionPermitY", it) }
                put("executionPermitActionHash", hash)
            },
        )
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
