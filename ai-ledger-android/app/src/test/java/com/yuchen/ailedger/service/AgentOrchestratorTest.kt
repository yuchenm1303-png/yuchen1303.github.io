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
    fun explicitInstalledAppNameStillResolvesDirectly() {
        val result = AgentOrchestrator.resolveExplicitControllerTarget(
            goal = "请打开系统设置",
            apps = installedApps,
            aliasesForPackage = { app -> if (app.packageName == "com.android.settings") listOf("系统设置") else emptyList() },
            excludedPackages = setOf("com.yuchen.ailedger"),
        )

        assertEquals(ExplicitAppResolutionStatus.Exact, result.status)
        assertEquals("com.android.settings", result.app?.packageName)
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
    fun plannerCannotSelectUninstalledPackageOrMismatchedLabel() {
        val unknownPackage = AgentOrchestrator.evaluateControllerPlannerStep(
            step = plannerOpenApp(appName = "设置", packageName = "com.unknown.settings"),
            installedApps = installedApps,
            assistantPackageName = "com.yuchen.ailedger",
        )
        assertFalse(unknownPackage.accepted)
        assertTrue(unknownPackage.rejectionReason.contains("不在真实已安装应用清单"))

        val mismatchedName = AgentOrchestrator.evaluateControllerPlannerStep(
            step = plannerOpenApp(appName = "浏览器", packageName = "com.android.settings"),
            installedApps = installedApps,
            assistantPackageName = "com.yuchen.ailedger",
        )
        assertFalse(mismatchedName.accepted)
        assertTrue(mismatchedName.rejectionReason.contains("应用名与包名不一致"))
    }

    @Test
    fun validPlannerOpenAppCarriesParsedContract() {
        val result = AgentOrchestrator.evaluateControllerPlannerStep(
            step = plannerOpenApp(appName = "设置", packageName = "com.android.settings"),
            installedApps = installedApps,
            assistantPackageName = "com.yuchen.ailedger",
        )

        assertTrue(result.accepted)
        assertEquals("设置", result.step?.appName)
        assertEquals("com.android.settings", result.step?.packageName)
        assertEquals(AgentSurfacePreference.SystemSettings, result.contract?.preferredSurface)
        assertFalse(result.contract?.browserFallbackAllowed ?: true)
    }

    private fun plannerOpenApp(appName: String, packageName: String): CloudAgentStep {
        val fields = JSONObject().apply {
            put("preferredSurface", "system_settings")
            put("browserFallbackAllowed", false)
            put("requiredCapabilities", "system_settings")
            put("requirePostActionVerification", true)
            put("taskContractReason", "系统设置任务")
        }
        return requireNotNull(CloudAgentStep.fromJson(JSONObject().apply {
            put("type", "open_app")
            put("appName", appName)
            put("packageName", packageName)
            put("arguments", fields)
            put("args", fields)
            put("preferredSurface", "system_settings")
            put("browserFallbackAllowed", false)
            put("requiredCapabilities", "system_settings")
            put("requirePostActionVerification", true)
            put("taskContractReason", "系统设置任务")
        }))
    }
}
