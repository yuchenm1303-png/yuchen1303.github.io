package com.yuchen.ailedger.service

import org.json.JSONObject

/**
 * Deterministic gate between cloud intent JSON and local device tools.
 *
 * The model may propose a device-control capability, but Android owns the final
 * mapping, argument shape, risk defaults, and supported-tool allowlist.
 */
object DeviceControlRouter {
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

        val rawCapability = raw.deviceControlFirstNonBlank("capability", "tool", "type", "action", "name")
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
        copyIfPresent(
            raw,
            merged,
            "percent",
            "brightness",
            "volume",
            "value",
            "deltaPercent",
            "delta",
            "brightnessDelta",
            "volumeDelta",
            "changePercent",
            "adjustBy",
            "operation",
            "scale",
        )
        copyIfPresent(raw, merged, "seconds", "minutes", "timeoutMs")

        val risk = raw.deviceControlFirstNonBlank("riskLevel", "risk")
            ?: DeviceControlSpecs.riskFor(stepType)
        val requiresConfirmation = raw.optFlexibleBooleanCompat("requiresConfirmation")
            ?: raw.optFlexibleBooleanCompat("confirm")
            ?: (DeviceControlSpecs.specFor(stepType)?.requiresConfirmation == true)

        return CloudAgentStep(
            type = stepType,
            targetText = raw.deviceControlFirstNonBlank("targetText", "target", "title", "label")
                ?: args.deviceControlFirstNonBlank("targetText", "target", "title", "label"),
            text = raw.deviceControlFirstNonBlank("text", "value")
                ?: args.deviceControlFirstNonBlank("text", "value"),
            reason = raw.deviceControlFirstNonBlank("reason", "rationale")
                ?: fallbackReason?.trim()?.takeIf { it.isNotBlank() },
            riskLevel = risk.lowercase().replace('-', '_'),
            requiresConfirmation = requiresConfirmation,
            appName = raw.deviceControlFirstNonBlank("appName", "app", "application")
                ?: args.deviceControlFirstNonBlank("appName", "app", "application", "label", "name"),
            packageName = raw.deviceControlFirstNonBlank("packageName", "package", "pkg")
                ?: args.deviceControlFirstNonBlank("packageName", "package", "pkg"),
            toolArgs = merged.takeIf { it.length() > 0 },
        )
    }

    fun supportedCapabilities(): List<String> = DeviceControlSpecs.supportedCapabilities()

    fun normalChatSupportedCapabilities(): List<String> = DeviceControlSpecs.normalChatSupportedCapabilities()

    fun normalChatSupportedStepTypes(): List<String> = DeviceControlSpecs.normalChatSupportedStepTypes()

    private fun normalizeCapability(raw: String): String? {
        DeviceControlSpecs.normalizeCapability(raw)?.let { return it }
        val key = raw.trim().lowercase().replace('-', '_')
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

private fun JSONObject.deviceControlFirstNonBlank(vararg names: String): String? {
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
