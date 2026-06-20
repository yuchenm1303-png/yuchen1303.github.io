package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTaskExecutionContractTest {
    @Test
    fun nativeAppContractRejectsBrowserSelection() {
        val contract = AgentTaskExecutionContract(
            preferredSurface = AgentSurfacePreference.NativeApp,
            browserFallbackAllowed = false,
            requiredCapabilities = setOf(AppCapability.NativeApp),
            highImpactFlow = true,
        )

        val browserValidation = AppCapabilityRegistry.validateCapabilities(
            contract = contract,
            capabilities = setOf(AppCapability.NativeApp, AppCapability.Browser, AppCapability.UserApp),
            appLabel = "浏览器",
        )
        assertFalse(browserValidation.ok)

        val nativeValidation = AppCapabilityRegistry.validateCapabilities(
            contract = contract,
            capabilities = setOf(AppCapability.NativeApp, AppCapability.UserApp),
            appLabel = "原生应用",
        )
        assertTrue(nativeValidation.ok)
    }

    @Test
    fun systemSettingsContractRejectsOrdinaryAppAndAcceptsSettings() {
        val contract = AgentTaskExecutionContract(
            preferredSurface = AgentSurfacePreference.SystemSettings,
            browserFallbackAllowed = false,
            requiredCapabilities = setOf(AppCapability.SystemSettings),
        )

        val wrongApp = AppCapabilityRegistry.validateCapabilities(
            contract = contract,
            capabilities = setOf(AppCapability.NativeApp, AppCapability.UserApp),
            appLabel = "普通应用",
        )
        assertFalse(wrongApp.ok)

        val settingsApp = AppCapabilityRegistry.validateCapabilities(
            contract = contract,
            capabilities = setOf(AppCapability.NativeApp, AppCapability.SystemSettings, AppCapability.SystemApp),
            appLabel = "设置",
        )
        assertTrue(settingsApp.ok)
    }

    @Test
    fun controllerRequestLeavesSemanticDecisionToPlanner() {
        val request = AgentTaskExecutionContract.controllerRequest()

        assertEquals(AgentSurfacePreference.Any, request.preferredSurface)
        assertTrue(request.browserFallbackAllowed)
        assertTrue(request.requiredCapabilities.isEmpty())
        assertTrue(request.requirePostActionVerification)
    }

    @Test
    fun fromPlannerStepParsesStructuredModelArguments() {
        val contractFields = JSONObject().apply {
            put("preferredSurface", "system_settings")
            put("browserFallbackAllowed", false)
            put("requiredCapabilities", "system_settings,native_app")
            put("requirePostActionVerification", true)
            put("taskContractReason", "需要进入真实系统设置并验证结果")
        }
        val step = CloudAgentStep.fromJson(JSONObject().apply {
            put("type", "open_app")
            put("appName", "设置")
            put("packageName", "com.android.settings")
            put("arguments", contractFields)
            put("args", contractFields)
            put("preferredSurface", "system_settings")
            put("browserFallbackAllowed", false)
            put("requiredCapabilities", "system_settings,native_app")
            put("requirePostActionVerification", true)
            put("taskContractReason", "需要进入真实系统设置并验证结果")
        })

        assertNotNull(step)
        val contract = AgentTaskExecutionContract.fromPlannerStep(step)
        assertNotNull(contract)
        requireNotNull(contract)
        assertEquals(AgentSurfacePreference.SystemSettings, contract.preferredSurface)
        assertFalse(contract.browserFallbackAllowed)
        assertEquals(setOf(AppCapability.SystemSettings, AppCapability.NativeApp), contract.requiredCapabilities)
        assertTrue(contract.requirePostActionVerification)
        assertEquals("需要进入真实系统设置并验证结果", contract.reason)
    }

    @Test
    fun plannerStepWithoutContractRequiresRetryInsteadOfLocalGuess() {
        val step = CloudAgentStep(
            type = "open_app",
            appName = "设置",
            packageName = "com.android.settings",
        )

        assertNull(AgentTaskExecutionContract.fromPlannerStep(step))
    }

    @Test
    fun surfaceWireValuesAreNormalized() {
        assertEquals(AgentSurfacePreference.NativeApp, AgentSurfacePreference.fromWireValue("native-app"))
        assertEquals(AgentSurfacePreference.SystemSettings, AgentSurfacePreference.fromWireValue("settings"))
        assertEquals(AgentSurfacePreference.Browser, AgentSurfacePreference.fromWireValue("web"))
        assertEquals(AgentSurfacePreference.Any, AgentSurfacePreference.fromWireValue("unknown"))
    }
}
