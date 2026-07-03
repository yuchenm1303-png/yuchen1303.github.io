package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun clientToolCallRejectsBrightnessAliasBeforeExecution() {
        val step = DeviceControlRouter.fromClientToolCall(
            JSONObject().apply {
                put("schema", AI_WORKER_CLIENT_TOOL_CALL_SCHEMA)
                put("id", "brightness-alias")
                put("name", "device_control")
                put("arguments", JSONObject().apply {
                    put("action", "system.brightness.set")
                    put("args", JSONObject().put("brightness", 40))
                })
            },
        )

        assertNull(step)
    }

    @Test
    fun clientToolCallRejectsBooleanAndTimeoutAliasesBeforeExecution() {
        val wifi = DeviceControlRouter.fromClientToolCall(
            JSONObject().apply {
                put("schema", AI_WORKER_CLIENT_TOOL_CALL_SCHEMA)
                put("id", "wifi-alias")
                put("name", "device_control")
                put("arguments", JSONObject().apply {
                    put("action", "network.wifi_toggle")
                    put("args", JSONObject().put("on", "enabled"))
                })
            },
        )
        val timeout = DeviceControlRouter.fromClientToolCall(
            JSONObject().apply {
                put("schema", AI_WORKER_CLIENT_TOOL_CALL_SCHEMA)
                put("id", "timeout-alias")
                put("name", "device_control")
                put("arguments", JSONObject().apply {
                    put("action", "system.screen_timeout.set")
                    put("args", JSONObject().put("minutes", 2))
                })
            },
        )

        assertNull(wifi)
        assertNull(timeout)
    }

    @Test
    fun routerAcceptsCanonicalRootArguments() {
        val brightness = DeviceControlRouter.fromDeviceControlJson(
            JSONObject()
                .put("capability", "system.brightness.set")
                .put("percent", 40),
        )
        val wifi = DeviceControlRouter.fromDeviceControlJson(
            JSONObject()
                .put("capability", "network.wifi_toggle")
                .put("enabled", true),
        )
        val timeout = DeviceControlRouter.fromDeviceControlJson(
            JSONObject()
                .put("capability", "system.screen_timeout.set")
                .put("timeoutMs", 120_000),
        )

        assertTrue(DeviceControlSpecs.validate(brightness!!).ok)
        assertTrue(DeviceControlSpecs.validate(wifi!!).ok)
        assertTrue(DeviceControlSpecs.validate(timeout!!).ok)
    }
}
