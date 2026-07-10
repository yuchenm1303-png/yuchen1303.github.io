package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousComputerUseTest {
    @Test
    fun freshVisualFramesRemainEligibleAcrossArbitraryPackageChanges() {
        val machine = VisualExecutionStateMachine().apply {
            beginLaunch(QQ_PACKAGE)
            markTargetVerified(QQ_PACKAGE)
        }
        val session = VisualExecutionSessionState(stateMachine = machine)

        val qq = session.runtimeContext(snapshot(QQ_PACKAGE))
        val launcher = session.runtimeContext(snapshot(LAUNCHER_PACKAGE))
        val tonghuashun = session.runtimeContext(snapshot(TONGHUASHUN_PACKAGE))

        assertTrue(qq.guiPlusEligible)
        assertTrue(launcher.guiPlusEligible)
        assertTrue(tonghuashun.guiPlusEligible)
        assertEquals(VisualSurfaceState.Planning, launcher.surfaceState)
        assertEquals(VisualSurfaceState.Planning, tonghuashun.surfaceState)
        assertEquals(LAUNCHER_PACKAGE, launcher.currentPackage)
        assertEquals(LAUNCHER_PACKAGE, launcher.selectedTargetPackage)
        assertEquals(LAUNCHER_PACKAGE, launcher.verifiedTargetPackage)
        assertEquals(TONGHUASHUN_PACKAGE, tonghuashun.currentPackage)
        assertFalse(session.requiresForeignConfirmation(snapshot(LAUNCHER_PACKAGE)))
        assertEquals(0L, launcher.routeEpoch)
        assertEquals(0L, tonghuashun.routeEpoch)
    }

    @Test
    fun currentObservationPermitExecutesEvenWhenLegacyTargetWasAnotherApp() {
        val current = snapshot(LAUNCHER_PACKAGE)
        val runtime = VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.Planning,
            selectedTargetPackage = QQ_PACKAGE,
            verifiedTargetPackage = QQ_PACKAGE,
            currentPackage = LAUNCHER_PACKAGE,
            observationId = VisualObservationProtocol.observationId(current, 0L, 0L),
            routeEpoch = 0L,
            surfaceEpoch = 0L,
            guiPlusEligible = true,
        )
        val base = CloudAgentStep(type = "tap_xy", x = 0.608f, y = 0.634f)
        val sessionId = "continuous-session"
        val kind = "gui_transaction_validated"
        val hash = VisualActionValidator.executionPermitHash(
            sessionId = sessionId,
            observationId = runtime.observationId,
            packageName = LAUNCHER_PACKAGE,
            kind = kind,
            step = base,
            canonicalX = base.x?.toDouble(),
            canonicalY = base.y?.toDouble(),
        )
        val step = base.copy(
            toolArgs = JSONObject().apply {
                put("responseSessionId", sessionId)
                put("responseObservationId", runtime.observationId)
                put("executionPermitVersion", "visual_execution_permit_v2")
                put("executionPermitId", "permit_$hash")
                put("executionPermitKind", kind)
                put("executionPermitObservationId", runtime.observationId)
                put("executionPermitSessionId", sessionId)
                put("executionPermitPackageName", LAUNCHER_PACKAGE)
                put("executionPermitActionType", base.type)
                put("executionPermitX", base.x)
                put("executionPermitY", base.y)
                put("executionPermitActionHash", hash)
            },
        )

        val result = VisualActionValidator.validate(step, current, runtime)

        assertTrue(result.message, result.ok)
    }

    @Test
    fun openAppIsAFirstClassVisualTransitionWithoutWorkSurfacePermit() {
        val current = snapshot(QQ_PACKAGE)
        val runtime = VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.WorkSurface,
            selectedTargetPackage = QQ_PACKAGE,
            verifiedTargetPackage = QQ_PACKAGE,
            currentPackage = QQ_PACKAGE,
            observationId = VisualObservationProtocol.observationId(current, 1L, 1L),
            routeEpoch = 1L,
            surfaceEpoch = 1L,
            guiPlusEligible = true,
        )

        val result = VisualActionValidator.validate(
            CloudAgentStep(
                type = "open_app",
                appName = "同花顺",
                packageName = TONGHUASHUN_PACKAGE,
            ),
            current,
            runtime,
        )

        assertTrue(result.message, result.ok)
    }

    @Test
    fun payloadUsesObservationBoundContinuousSurfaceWithoutStructuralRegression() {
        val current = snapshot(LAUNCHER_PACKAGE)
        val runtime = VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.Planning,
            selectedTargetPackage = LAUNCHER_PACKAGE,
            verifiedTargetPackage = LAUNCHER_PACKAGE,
            currentPackage = LAUNCHER_PACKAGE,
            observationId = VisualObservationProtocol.observationId(current, 0L, 0L),
            guiPlusEligible = true,
        )

        val payload = buildVisualAgentPayload(
            goal = "打开同花顺",
            snapshot = current,
            recentActions = emptyList(),
            appContext = listOf(
                VisualAgentAppContextItem("QQ", QQ_PACKAGE),
                VisualAgentAppContextItem("同花顺", TONGHUASHUN_PACKAGE),
            ),
            runtimeContext = runtime,
        )
        val runtimePayload = payload.getJSONObject("runtimeExecutionContext")
        val feedback = payload.getJSONObject("executionFeedback")

        assertTrue(runtimePayload.getBoolean("guiPlusEligible"))
        assertEquals("observation_bound_continuous", runtimePayload.getString("packageBindingMode"))
        assertEquals("continuous_computer_use", runtimePayload.getString("surfaceRole"))
        assertFalse(runtimePayload.getBoolean("packageSemanticGate"))
        assertFalse(feedback.getBoolean("structuralRegression"))
        assertEquals(2, payload.getJSONArray("appContext").length())
    }

    @Test
    fun exactGuiPlusOpenCallIsRecoveredFromCanonicalCatalog() {
        val plan = CloudAgentPlan(
            step = CloudAgentStep(
                type = "need_user_help",
                targetText = "QQ",
                reason = "Android client does not support GUI Plus action: open_app",
            ),
            rawModelOutput = """
                <tool_call>
                {"name":"mobile_use","arguments":{"action":"open","text":"QQ"}}
                </tool_call>
            """.trimIndent(),
        )

        val repaired = GuiPlusOpenAppProtocolRepair.repair(
            plan,
            listOf(
                VisualAgentAppContextItem("QQ", QQ_PACKAGE, aliases = listOf("腾讯QQ")),
                VisualAgentAppContextItem("同花顺", TONGHUASHUN_PACKAGE),
            ),
        )

        assertEquals("open_app", repaired.step.type)
        assertEquals("QQ", repaired.step.appName)
        assertEquals(QQ_PACKAGE, repaired.step.packageName)
    }

    @Test
    fun ambiguousGuiPlusOpenCallIsNeverGuessedLocally() {
        val plan = CloudAgentPlan(
            step = CloudAgentStep(
                type = "need_user_help",
                targetText = "金融",
                reason = "Android client does not support GUI Plus action: open_app",
            ),
            rawModelOutput = """
                <tool_call>
                {"name":"mobile_use","arguments":{"action":"open","text":"金融"}}
                </tool_call>
            """.trimIndent(),
        )

        val repaired = GuiPlusOpenAppProtocolRepair.repair(
            plan,
            listOf(
                VisualAgentAppContextItem("同花顺", TONGHUASHUN_PACKAGE, aliases = listOf("金融")),
                VisualAgentAppContextItem("股票", "com.example.stock", aliases = listOf("金融")),
            ),
        )

        assertEquals("need_user_help", repaired.step.type)
    }

    private fun snapshot(packageName: String): AgentScreenSnapshot = AgentScreenSnapshot(
        currentApp = packageName,
        packageName = packageName,
        nodeCount = 0,
        capturedNodeCount = 0,
        texts = emptyList(),
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
            base64Jpeg = "YWJjZGVmZ2hpamtsbW5vcA==",
            source = "test",
            reason = "continuous-computer-use-test",
        ),
    )

    companion object {
        private const val QQ_PACKAGE = "com.tencent.mobileqq"
        private const val LAUNCHER_PACKAGE = "com.huawei.android.launcher"
        private const val TONGHUASHUN_PACKAGE = "com.hexin.plat.android"
    }
}
