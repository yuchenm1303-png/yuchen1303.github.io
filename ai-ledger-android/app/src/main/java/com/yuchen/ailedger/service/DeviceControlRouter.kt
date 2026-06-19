package com.yuchen.ailedger.service

import org.json.JSONObject

/**
 * Deterministic gate between cloud intent JSON and local device tools.
 *
 * The model may propose a device-control capability, but Android owns the final
 * mapping, argument shape, risk defaults, and supported-tool allowlist.
 */
object DeviceControlRouter {
    private val capabilityToStepType = mapOf(
        "device.health" to "device_status",
        "device.status" to "device_status",
        "shell.probe" to "shizuku_status",
        "shell.status" to "shizuku_status",
        "shizuku.status" to "shizuku_status",
        "shizuku.permission.request" to "request_shizuku_permission",
        "shell.shizuku_permission" to "request_shizuku_permission",
        "settings.open" to "open_system_settings",
        "app.open" to "open_app",
        "app.settings" to "open_app_settings",
        "system.brightness.set" to "set_brightness",
        "system.brightness" to "set_brightness",
        "system.screen_timeout.set" to "set_screen_timeout",
        "system.screen_timeout" to "set_screen_timeout",
        "system.auto_rotate.set" to "set_auto_rotate",
        "system.media_volume.set" to "set_media_volume",
        "network.wifi.set" to "set_wifi_enabled",
        "network.wifi_toggle" to "set_wifi_enabled",
        "network.bluetooth.set" to "set_bluetooth_enabled",
        "network.bluetooth_toggle" to "set_bluetooth_enabled",
        "network.mobile_data.set" to "set_mobile_data_enabled",
        "network.mobile_data_toggle" to "set_mobile_data_enabled",
        "system.dark_mode.set" to "set_dark_mode",
        "system.dark_mode" to "set_dark_mode",
        "system.animation_scale.set" to "set_animation_scale",
        "system.settings_global_write" to "set_animation_scale",
        "app.force_stop" to "force_stop_app",
        "app.clear_data" to "clear_app_data",
        "app.uninstall" to "uninstall_app",
        "app.disable" to "disable_app",
        "app.enable" to "enable_app",
    )

    private val highRiskTypes = setOf(
        "set_animation_scale",
        "force_stop_app",
        "clear_app_data",
        "uninstall_app",
        "disable_app",
        "enable_app",
    )

    private val normalChatExcludedTypes = highRiskTypes + setOf(
        "request_shizuku_permission",
    )

    fun fromAgentActionJson(agentAction: JSONObject?): CloudAgentStep? {
        if (agentAction == null) return null
        val explicit = listOf("deviceControlAction", "device_control_action", "agentStep", "step")
            .firstNotNullOfOrNull { key -> agentAction.optJSONObject(key) }
        val source = explicit ?: agentAction
        return fromDeviceControlJson(source, fallbackReason = agentAction.optString("reason"))
    }

    fun fromDeviceControlJson(raw: JSONObject?, fallbackReason: String? = null): CloudAgentStep? {
        if (raw == null) return null
        CloudAgentStep.fromJson(raw)?.takeIf { it.type in CloudAgentStep.deviceToolTypes }?.let { return it }

        val rawCapability = raw.firstNonBlank("capability", "tool", "type", "action", "name")
            ?: return null
        val stepType = normalizeCapability(rawCapability) ?: return null
        val args = raw.optJSONObject("arguments")
            ?: raw.optJSONObject("args")
            ?: raw.optJSONObject("params")
            ?: JSONObject()
        val merged = JSONObject(args.toString())
        copyIfPresent(raw, merged, "appName", "app", "application", "packageName", "package", "pkg")
        copyIfPresent(raw, merged, "target", "targetText", "page", "kind", "setting")
        copyIfPresent(raw, merged, "enabled", "enable", "on", "state", "mode", "value")
        copyIfPresent(raw, merged, "percent", "brightness", "volume", "deltaPercent", "scale")
        copyIfPresent(raw, merged, "seconds", "minutes", "timeoutMs")

        val risk = raw.firstNonBlank("riskLevel", "risk")
            ?: if (stepType in highRiskTypes) "high" else "low"
        val requiresConfirmation = raw.optFlexibleBooleanCompat("requiresConfirmation")
            ?: raw.optFlexibleBooleanCompat("confirm")
            ?: (stepType in highRiskTypes)

        return CloudAgentStep(
            type = stepType,
            targetText = raw.firstNonBlank("targetText", "target", "title", "label")
                ?: args.firstNonBlank("targetText", "target", "title", "label"),
            text = raw.firstNonBlank("text", "value")
                ?: args.firstNonBlank("text", "value"),
            reason = raw.firstNonBlank("reason", "rationale")
                ?: fallbackReason?.trim()?.takeIf { it.isNotBlank() },
            riskLevel = risk.lowercase().replace('-', '_'),
            requiresConfirmation = requiresConfirmation,
            appName = raw.firstNonBlank("appName", "app", "application")
                ?: args.firstNonBlank("appName", "app", "application", "label", "name"),
            packageName = raw.firstNonBlank("packageName", "package", "pkg")
                ?: args.firstNonBlank("packageName", "package", "pkg"),
            toolArgs = merged.takeIf { it.length() > 0 },
        )
    }

    fun supportedCapabilities(): List<String> = capabilityToStepType.keys.sorted()

    fun normalChatSupportedCapabilities(): List<String> {
        return capabilityToStepType
            .filterValues { stepType -> stepType !in normalChatExcludedTypes }
            .keys
            .sorted()
    }

    fun normalChatSupportedStepTypes(): List<String> {
        return capabilityToStepType
            .values
            .filterNot { stepType -> stepType in normalChatExcludedTypes }
            .distinct()
            .sorted()
    }

    private fun normalizeCapability(raw: String): String? {
        val key = raw.trim().lowercase().replace('-', '_')
        capabilityToStepType[key.replace('_', '.')]?.let { return it }
        capabilityToStepType[key]?.let { return it }
        return CloudAgentStep.fromJson(JSONObject().put("type", key))
            ?.takeIf { it.type in CloudAgentStep.deviceToolTypes }
            ?.type
    }

    private fun copyIfPresent(source: JSONObject, target: JSONObject, vararg keys: String) {
        for (key in keys) {
            if (!target.has(key) && source.has(key) && !source.isNull(key)) target.put(key, source.opt(key))
        }
    }
}

private fun JSONObject.firstNonBlank(vararg names: String): String? {
    for (name in names) {
        val value = optString(name).trim()
        if (value.isNotBlank()) return value
    }
    return null
}

private fun JSONObject.optFlexibleBooleanCompat(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    return when (val raw = opt(name)) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> when (raw.lowercase().trim()) {
            "true", "yes", "1", "on", "enable", "enabled", "open", "开启", "打开", "启用" -> true
            "false", "no", "0", "off", "disable", "disabled", "close", "关闭", "禁用" -> false
            else -> null
        }
        else -> null
    }
}
