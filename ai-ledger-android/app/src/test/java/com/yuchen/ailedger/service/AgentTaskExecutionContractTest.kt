package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun systemSettingsContractRejectsOrdinaryApp() {
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
    fun surfaceWireValuesAreNormalized() {
        assertEquals(AgentSurfacePreference.NativeApp, AgentSurfacePreference.fromWireValue("native-app"))
        assertEquals(AgentSurfacePreference.SystemSettings, AgentSurfacePreference.fromWireValue("settings"))
        assertEquals(AgentSurfacePreference.Browser, AgentSurfacePreference.fromWireValue("web"))
        assertEquals(AgentSurfacePreference.Any, AgentSurfacePreference.fromWireValue("unknown"))
    }
}
