package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentOrchestratorTest {
    private val installedApps = listOf(
        InstalledAppEntry(label = "设置", packageName = "com.android.settings"),
        InstalledAppEntry(label = "浏览器", packageName = "com.example.browser"),
        InstalledAppEntry(label = "QQ", packageName = "com.tencent.mobileqq"),
        InstalledAppEntry(label = "同花顺炒股票", packageName = "com.hexin.plat.android"),
    )

    @Test
    fun visualModesUseVisualLoop() {
        assertEquals(AgentOrchestratorRoute.VisualLoop, AgentOrchestrator.routeFor(AgentExecutionMode.VisualForce))
        assertEquals(AgentOrchestratorRoute.VisualLoop, AgentOrchestrator.routeFor(AgentExecutionMode.ExplicitAgent))
    }

    @Test
    fun normalChatDeviceToolKeepsLegacyRunner() {
        assertEquals(AgentOrchestratorRoute.LegacyRunner, AgentOrchestrator.routeFor(AgentExecutionMode.NormalChatDeviceTool))
    }

    @Test
    fun onlyVisualForceDependsOnHomeAgentSwitch() {
        assertTrue(VisualLoopRunner.requiresAgentSwitch(AgentExecutionMode.VisualForce))
        assertFalse(VisualLoopRunner.requiresAgentSwitch(AgentExecutionMode.ExplicitAgent))
        assertFalse(VisualLoopRunner.requiresAgentSwitch(AgentExecutionMode.NormalChatDeviceTool))
    }

    @Test
    fun plannerMissingTaskContractIsRejectedForRetry() {
        val result = AgentOrchestrator.evaluateControllerPlannerStep(
            step = CloudAgentStep(
                type = "open_app",
                appName = "设置",
                packageName = "com.android.settings",
            ),
            installedApps = installedApps,
            assistantPackageName = "com.yuchen.ailedger",
        )

        assertFalse(result.accepted)
        assertTrue(result.rejectionReason.contains("结构化任务契约"))
    }

    @Test
    fun controllerRejectsWaitHomeAndTapActions() {
        listOf("wait", "home", "tap_xy").forEach { type ->
            val result = AgentOrchestrator.evaluateControllerPlannerStep(
                step = CloudAgentStep(type = type),
                installedApps = installedApps,
                assistantPackageName = "com.yuchen.ailedger",
            )
            assertFalse(result.accepted)
            assertTrue(result.rejectionReason.contains("禁止"))
        }
    }

    @Test
    fun plannerCannotSelectUninstalledPackage() {
        val result = AgentOrchestrator.evaluateControllerPlannerStep(
            step = plannerOpenApp(
                appName = "设置",
                packageName = "com.unknown.settings",
                preferredSurface = "system_settings",
                requiredCapabilities = "system_settings",
            ),
            installedApps = installedApps,
            assistantPackageName = "com.yuchen.ailedger",
        )

        assertFalse(result.accepted)
        assertTrue(result.rejectionReason.contains("真实可启动应用目录"))
    }

    @Test
    fun packageNameIsMachineIdentityAndLabelIsCanonicalized() {
        val result = AgentOrchestrator.evaluateControllerPlannerStep(
            step = plannerOpenApp(
                appName = "同花顺",
                packageName = "com.hexin.plat.android",
                preferredSurface = "native_app",
                requiredCapabilities = "native_app",
            ),
            installedApps = installedApps,
            assistantPackageName = "com.yuchen.ailedger",
        )

        assertTrue(result.accepted)
        assertEquals("同花顺炒股票", result.step?.appName)
        assertEquals("com.hexin.plat.android", result.step?.packageName)
    }

    @Test
    fun appNameMayBeMissingWhenPackageNameIsCanonical() {
        val result = AgentOrchestrator.evaluateControllerPlannerStep(
            step = plannerOpenApp(
                appName = "",
                packageName = "com.tencent.mobileqq",
                preferredSurface = "native_app",
                requiredCapabilities = "native_app",
            ),
            installedApps = installedApps,
            assistantPackageName = "com.yuchen.ailedger",
        )

        assertTrue(result.accepted)
        assertEquals("QQ", result.step?.appName)
        assertEquals("com.tencent.mobileqq", result.step?.packageName)
    }

    @Test
    fun cloudPlannerCanSelectQqForAnInternalQqPageTask() {
        val result = AgentOrchestrator.evaluateControllerPlannerStep(
            step = plannerOpenApp(
                appName = "QQ",
                packageName = "com.tencent.mobileqq",
                preferredSurface = "native_app",
                requiredCapabilities = "native_app",
                reason = "用户要求进入 QQ 内部设置页，应先打开 QQ，再由视觉循环继续导航。",
            ),
            installedApps = installedApps,
            assistantPackageName = "com.yuchen.ailedger",
        )

        assertTrue(result.accepted)
        assertEquals("QQ", result.step?.appName)
        assertEquals("com.tencent.mobileqq", result.step?.packageName)
        assertEquals(AgentSurfacePreference.NativeApp, result.contract?.preferredSurface)
        assertFalse(result.contract?.browserFallbackAllowed ?: true)
    }

    @Test
    fun validPlannerOpenAppCarriesParsedContract() {
        val result = AgentOrchestrator.evaluateControllerPlannerStep(
            step = plannerOpenApp(
                appName = "设置",
                packageName = "com.android.settings",
                preferredSurface = "system_settings",
                requiredCapabilities = "system_settings",
            ),
            installedApps = installedApps,
            assistantPackageName = "com.yuchen.ailedger",
        )

        assertTrue(result.accepted)
        assertEquals("设置", result.step?.appName)
        assertEquals("com.android.settings", result.step?.packageName)
        assertEquals(AgentSurfacePreference.SystemSettings, result.contract?.preferredSurface)
        assertFalse(result.contract?.browserFallbackAllowed ?: true)
    }

    private fun plannerOpenApp(
        appName: String,
        packageName: String,
        preferredSurface: String,
        requiredCapabilities: String,
        reason: String = "云端已根据完整用户指令选择目标应用",
    ): CloudAgentStep {
        val fields = JSONObject().apply {
            put("preferredSurface", preferredSurface)
            put("browserFallbackAllowed", false)
            put("requiredCapabilities", requiredCapabilities)
            put("requirePostActionVerification", true)
            put("taskContractReason", reason)
        }
        return requireNotNull(CloudAgentStep.fromJson(JSONObject().apply {
            put("type", "open_app")
            if (appName.isNotBlank()) put("appName", appName)
            put("packageName", packageName)
            put("arguments", fields)
            put("args", fields)
            put("preferredSurface", preferredSurface)
            put("browserFallbackAllowed", false)
            put("requiredCapabilities", requiredCapabilities)
            put("requirePostActionVerification", true)
            put("taskContractReason", reason)
            put("reason", reason)
        }))
    }
}
