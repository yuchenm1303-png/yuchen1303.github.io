package com.yuchen.ailedger.service

import org.json.JSONObject
import kotlin.math.abs

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
        val parsed = CloudAgentStep.fromJson(raw)
            ?.takeIf { it.type in CloudAgentStep.deviceToolTypes }
        if (parsed != null && DeviceControlSpecs.validate(parsed).ok) return parsed

        val rawCapability = raw.deviceControlFirstNonBlank("capability", "tool", "type", "action", "name")
            ?: return null
        val stepType = normalizeCapability(rawCapability) ?: return null
        val args = raw.optJSONObject("arguments")
            ?: raw.optJSONObject("args")
            ?: raw.optJSONObject("params")
            ?: JSONObject()
        val merged = canonicalizeArguments(stepType, raw, args)

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

    /**
     * Compatibility aliases are accepted only at this routing boundary. Before validation and
     * execution they are folded into the canonical schema declared by [DeviceControlSpecs].
     * Unknown fields are deliberately preserved so the validator can still reject them.
     */
    private fun canonicalizeArguments(
        stepType: String,
        raw: JSONObject,
        nestedArgs: JSONObject,
    ): JSONObject {
        val merged = JSONObject(nestedArgs.toString())
        copyIfPresent(
            raw,
            merged,
            "appName", "app", "application", "packageName", "package", "pkg",
            "target", "targetText", "page", "kind", "setting",
            "enabled", "enable", "on", "state", "mode", "value",
            "percent", "brightness", "volume", "deltaPercent", "delta",
            "brightnessDelta", "volumeDelta", "changePercent", "adjustBy", "operation",
            "scale", "seconds", "second", "sec", "minutes", "minute", "min",
            "timeoutMs", "screenTimeoutMs",
        )

        when (stepType) {
            "open_system_settings" -> merged.promoteString(
                target = "page",
                aliases = arrayOf("kind", "setting", "target"),
            ) { value -> value.lowercase().replace('-', '_').replace(' ', '_') }

            "open_app",
            "force_stop_app",
            "clear_app_data",
            "uninstall_app",
            "disable_app",
            "enable_app" -> merged.canonicalizePackageAliases()

            "open_app_settings" -> {
                merged.canonicalizePackageAliases()
                merged.promoteString(
                    target = "page",
                    aliases = arrayOf("kind", "setting", "target"),
                ) { value -> value.lowercase().replace('-', '_').replace(' ', '_') }
            }

            "set_brightness" -> merged.canonicalizePercentControl(
                absoluteAliases = arrayOf("brightness", "value"),
                deltaAliases = arrayOf("delta", "brightnessDelta", "changePercent", "adjustBy"),
            )

            "set_media_volume" -> merged.canonicalizePercentControl(
                absoluteAliases = arrayOf("volume", "value"),
                deltaAliases = arrayOf("delta", "volumeDelta", "changePercent", "adjustBy"),
            )

            "set_screen_timeout" -> merged.canonicalizeTimeout()

            "set_auto_rotate",
            "set_wifi_enabled",
            "set_bluetooth_enabled",
            "set_mobile_data_enabled" -> merged.canonicalizeBoolean(
                target = "enabled",
                aliases = arrayOf("enable", "on", "state", "value", "mode"),
            )

            "set_dark_mode" -> merged.canonicalizeDarkMode()

            "set_animation_scale" -> merged.promoteValue("scale", "value")
        }

        merged.removeAliases(
            "app", "application", "package", "pkg",
            "target", "targetText", "kind", "setting",
            "enable", "on", "state", "value",
            "brightness", "volume", "delta", "brightnessDelta", "volumeDelta",
            "changePercent", "adjustBy", "operation",
            "seconds", "second", "sec", "minutes", "minute", "min", "screenTimeoutMs",
        )
        return merged
    }

    private fun copyIfPresent(source: JSONObject, target: JSONObject, vararg keys: String) {
        for (key in keys) {
            if (!target.has(key) && source.has(key) && !source.isNull(key)) target.put(key, source.opt(key))
        }
    }
}

private fun JSONObject.canonicalizePackageAliases() {
    promoteValue("packageName", "package", "pkg")
    promoteValue("appName", "app", "application")
}

private fun JSONObject.canonicalizePercentControl(
    absoluteAliases: Array<String>,
    deltaAliases: Array<String>,
) {
    if (!hasUsableValue("deltaPercent")) {
        firstUsableValue(*deltaAliases)?.let { put("deltaPercent", it) }
    }

    val operation = optString("operation").trim().lowercase().replace('-', '_')
    if (!hasUsableValue("deltaPercent") && operation.isNotBlank()) {
        val source = firstUsableValue("value", *absoluteAliases)
        val number = source as? Number
        when (operation) {
            "increase", "raise", "up", "add", "plus", "增大", "增加", "调高" ->
                if (number != null) put("deltaPercent", abs(number.toDouble()))
            "decrease", "lower", "down", "subtract", "minus", "减小", "降低", "调低" ->
                if (number != null) put("deltaPercent", -abs(number.toDouble()))
        }
    }

    if (!hasUsableValue("percent") && !hasUsableValue("deltaPercent")) {
        firstUsableValue(*absoluteAliases)?.let { put("percent", it) }
    }
}

private fun JSONObject.canonicalizeTimeout() {
    if (!hasUsableValue("timeoutMs")) {
        val direct = firstUsableValue("screenTimeoutMs")
        val seconds = firstUsableValue("seconds", "second", "sec")
        val minutes = firstUsableValue("minutes", "minute", "min")
        when {
            direct != null -> put("timeoutMs", direct)
            seconds is Number -> put("timeoutMs", seconds.toDouble() * 1_000.0)
            minutes is Number -> put("timeoutMs", minutes.toDouble() * 60_000.0)
            seconds != null -> put("timeoutMs", seconds)
            minutes != null -> put("timeoutMs", minutes)
        }
    }
}

private fun JSONObject.canonicalizeBoolean(target: String, aliases: Array<String>) {
    if (!hasUsableValue(target)) {
        val value = (listOf(target) + aliases).firstNotNullOfOrNull { name ->
            optFlexibleBooleanCompat(name)
        }
        if (value != null) put(target, value)
    }
}

private fun JSONObject.canonicalizeDarkMode() {
    val names = arrayOf("mode", "state", "value", "enabled", "on")
    if (!hasUsableValue("mode")) {
        val raw = names.firstNotNullOfOrNull { name ->
            if (has(name) && !isNull(name)) opt(name) else null
        }
        val canonical = when (raw) {
            is Boolean -> if (raw) "yes" else "no"
            is Number -> if (raw.toInt() != 0) "yes" else "no"
            is String -> when (raw.trim().lowercase()) {
                "yes", "on", "true", "1", "enable", "enabled", "open", "开启", "打开", "启用" -> "yes"
                "no", "off", "false", "0", "disable", "disabled", "close", "关闭", "禁用" -> "no"
                "auto", "automatic", "system", "follow_system", "跟随系统" -> "auto"
                else -> raw.trim().lowercase()
            }
            else -> null
        }
        if (canonical != null) put("mode", canonical)
    } else {
        val existing = opt("mode")
        if (existing is String) {
            val normalized = when (existing.trim().lowercase()) {
                "on", "true", "1", "enable", "enabled", "open", "开启", "打开", "启用" -> "yes"
                "off", "false", "0", "disable", "disabled", "close", "关闭", "禁用" -> "no"
                "automatic", "system", "follow_system", "跟随系统" -> "auto"
                else -> existing.trim().lowercase()
            }
            put("mode", normalized)
        }
    }
}

private fun JSONObject.promoteValue(target: String, vararg aliases: String) {
    if (!hasUsableValue(target)) {
        firstUsableValue(*aliases)?.let { put(target, it) }
    }
}

private inline fun JSONObject.promoteString(
    target: String,
    aliases: Array<String>,
    transform: (String) -> String,
) {
    val source = when {
        hasUsableValue(target) -> optString(target)
        else -> aliases.firstNotNullOfOrNull { alias ->
            optString(alias).trim().takeIf { it.isNotBlank() }
        }
    }
    if (!source.isNullOrBlank()) put(target, transform(source.trim()))
}

private fun JSONObject.firstUsableValue(vararg names: String): Any? {
    for (name in names) {
        if (hasUsableValue(name)) return opt(name)
    }
    return null
}

private fun JSONObject.hasUsableValue(name: String): Boolean =
    has(name) && !isNull(name) && when (val value = opt(name)) {
        is String -> value.isNotBlank()
        else -> value != null
    }

private fun JSONObject.removeAliases(vararg names: String) {
    names.forEach { remove(it) }
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
