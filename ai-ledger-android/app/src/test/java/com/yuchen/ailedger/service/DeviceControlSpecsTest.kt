package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceControlSpecsTest {
    @Test
    fun capabilityAliasesResolveThroughSingleSpecTable() {
        assertEquals("set_wifi_enabled", DeviceControlSpecs.normalizeCapability("network.wifi_toggle"))
        assertEquals("set_brightness", DeviceControlSpecs.normalizeCapability("system.brightness.set"))
        assertEquals("force_stop_app", DeviceControlSpecs.normalizeCapability("app.force_stop"))
        assertTrue(DeviceControlRouter.supportedCapabilities().contains("network.wifi_toggle"))
    }

    @Test
    fun highRiskDeviceToolsRequireConfirmationByDefault() {
        val step = CloudAgentStep(
            type = "clear_app_data",
            packageName = "com.example.app",
            toolArgs = JSONObject().put("packageName", "com.example.app"),
        )

        assertTrue(DeviceControlSpecs.requiresConfirmation(step))
        assertTrue(AgentSafetyPolicy.requiresConfirmation("清理应用数据", step))
    }

    @Test
    fun nonDeviceRiskWordsStillDoNotTriggerLocalSemanticRiskInference() {
        val step = CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f, riskLevel = "critical")

        assertFalse(AgentSafetyPolicy.requiresConfirmation("阅读 critical 这个单词", step))
    }

    @Test
    fun validationRejectsMissingPackageAndUnsafePackageNames() {
        val missing = DeviceControlSpecs.validate(CloudAgentStep(type = "open_app"))
        assertFalse(missing.ok)
        assertEquals("missing_or_invalid_package_name", missing.reason)

        val unsafe = DeviceControlSpecs.validate(
            CloudAgentStep(
                type = "force_stop_app",
                packageName = "com.example.app;reboot",
                toolArgs = JSONObject().put("packageName", "com.example.app;reboot"),
            ),
        )
        assertFalse(unsafe.ok)
        assertEquals("missing_or_invalid_package_name", unsafe.reason)
    }

    @Test
    fun validationRejectsOutOfRangePercent() {
        val result = DeviceControlSpecs.validate(
            CloudAgentStep(
                type = "set_brightness",
                toolArgs = JSONObject().put("percent", 180),
            ),
        )

        assertFalse(result.ok)
        assertEquals("arg_out_of_range:percent", result.reason)
    }

    @Test
    fun validationAcceptsWellFormedMediumControl() {
        val result = DeviceControlSpecs.validate(
            CloudAgentStep(
                type = "set_media_volume",
                toolArgs = JSONObject().put("percent", 35),
            ),
        )

        assertTrue(result.ok)
    }
}
