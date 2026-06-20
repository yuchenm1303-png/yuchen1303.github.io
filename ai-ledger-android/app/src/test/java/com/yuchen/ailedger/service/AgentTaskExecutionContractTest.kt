package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTaskExecutionContractTest {
    @Test
    fun tradingTaskPrefersNativeAppAndRejectsBrowserFallback() {
        val contract = AgentTaskExecutionContract.fromGoal("打开股票详情页并下单买入")

        assertEquals(AgentSurfacePreference.NativeApp, contract.preferredSurface)
        assertFalse(contract.browserFallbackAllowed)
        assertTrue(contract.highImpactFlow)

        val browserValidation = AppCapabilityRegistry.validateCapabilities(
            contract = contract,
            capabilities = setOf(AppCapability.NativeApp, AppCapability.Browser, AppCapability.UserApp),
            appLabel = "浏览器",
        )
        assertFalse(browserValidation.ok)

        val brokerValidation = AppCapabilityRegistry.validateCapabilities(
            contract = contract,
            capabilities = setOf(AppCapability.NativeApp, AppCapability.UserApp),
            appLabel = "券商应用",
        )
        assertTrue(brokerValidation.ok)
    }

    @Test
    fun developerOptionsTaskRequiresSystemSettingsSurface() {
        val contract = AgentTaskExecutionContract.fromGoal("帮我找到开发人员选项")

        assertEquals(AgentSurfacePreference.SystemSettings, contract.preferredSurface)
        assertFalse(contract.browserFallbackAllowed)
        assertEquals(setOf(AppCapability.SystemSettings), contract.requiredCapabilities)

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
    fun explicitWebTaskAllowsBrowser() {
        val contract = AgentTaskExecutionContract.fromGoal("用浏览器打开网站 https://example.com")

        assertEquals(AgentSurfacePreference.Browser, contract.preferredSurface)
        assertTrue(contract.browserFallbackAllowed)
        assertEquals(setOf(AppCapability.Browser), contract.requiredCapabilities)
    }
}
