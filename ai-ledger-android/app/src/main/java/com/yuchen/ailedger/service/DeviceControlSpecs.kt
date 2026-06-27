package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceControlPermission {
    None,
    WriteSettings,
    EnhancedShell,
    Shizuku,
    ExactPackage,
}

data class DeviceControlSpec(
    val stepType: String,
    val capabilityIds: List<String>,
    val title: String,
    val riskLevel: DeviceControlRiskLevel,
    val permissions: Set<DeviceControlPermission> = emptySet(),
    val requiredArgGroups: List<Set<String>> = emptyList(),
    val rangeArgs: Map<String, ClosedFloatingPointRange<Double>> = emptyMap(),
    val allowedValues: Map<String, Set<String>> = emptyMap(),
    val requiresConfirmation: Boolean = riskLevel == DeviceControlRiskLevel.High || riskLevel == DeviceControlRiskLevel.Critical,
    val normalChatAllowed: Boolean = !requiresConfirmation,
    val stateChanging: Boolean = false,
    val reversible: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("stepType", stepType)
        put("capabilityIds", JSONArray().apply { capabilityIds.forEach(::put) })
        put("title", title)
        put("riskLevel", riskLevel.name.lowercase())
        put("riskLabel", riskLevel.label)
        put("permissions", JSONArray().apply { permissions.map { it.name }.forEach(::put) })
        put("requiredArgGroups", JSONArray().apply {
            requiredArgGroups.forEach { group ->
                put(JSONArray().apply { group.forEach(::put) })
            }
        })
        put("rangeArgs", JSONObject().apply {
            rangeArgs.forEach { (key, range) ->
                put(key, JSONObject().put("min", range.start).put("max", range.endInclusive))
            }
        })
        put("allowedValues", JSONObject().apply {
            allowedValues.forEach { (key, values) ->
                put(key, JSONArray().apply { values.forEach(::put) })
            }
        })
        put("requiresConfirmation", requiresConfirmation)
        put("normalChatAllowed", normalChatAllowed)
        put("stateChanging", stateChanging)
        put("reversible", reversible)
    }
}

data class DeviceControlValidation(
    val ok: Boolean,
    val reason: String = "",
) {
    companion object {
        val Ok = DeviceControlValidation(ok = true)
        fun invalid(reason: String) = DeviceControlValidation(ok = false, reason = reason)
    }
}

object DeviceControlSpecs {
    val all: List<DeviceControlSpec> = listOf(
        DeviceControlSpec(
            stepType = "device_status",
            capabilityIds = listOf("device.health", "device.status"),
            title = "手机体检",
            riskLevel = DeviceControlRiskLevel.Low,
            reversible = true,
        ),
        DeviceControlSpec(
            stepType = "shizuku_status",
            capabilityIds = listOf("shell.probe", "shell.status", "shizuku.status"),
            title = "Shell/Shizuku 增强模式探测",
            riskLevel = DeviceControlRiskLevel.Low,
            reversible = true,
        ),
        DeviceControlSpec(
            stepType = "request_shizuku_permission",
            capabilityIds = listOf("shizuku.permission.request", "shell.shizuku_permission"),
            title = "请求 Shizuku 授权",
            riskLevel = DeviceControlRiskLevel.Low,
            permissions = setOf(DeviceControlPermission.Shizuku),
            normalChatAllowed = false,
            stateChanging = true,
            reversible = false,
        ),
        DeviceControlSpec(
            stepType = "open_system_settings",
            capabilityIds = listOf("settings.open"),
            title = "打开系统设置入口",
            riskLevel = DeviceControlRiskLevel.Low,
        ),
        DeviceControlSpec(
            stepType = "open_app",
            capabilityIds = listOf("app.open"),
            title = "打开应用",
            riskLevel = DeviceControlRiskLevel.Low,
            permissions = setOf(DeviceControlPermission.ExactPackage),
            requiredArgGroups = listOf(setOf("packageName", "package", "pkg")),
        ),
        DeviceControlSpec(
            stepType = "open_app_settings",
            capabilityIds = listOf("app.settings"),
            title = "打开 App 专属系统设置",
            riskLevel = DeviceControlRiskLevel.Low,
            permissions = setOf(DeviceControlPermission.ExactPackage),
            requiredArgGroups = listOf(setOf("packageName", "package", "pkg")),
        ),
        DeviceControlSpec(
            stepType = "set_brightness",
            capabilityIds = listOf("system.brightness", "system.brightness.set"),
            title = "调节屏幕亮度",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.WriteSettings),
            requiredArgGroups = listOf(setOf("percent", "brightness", "value", "deltaPercent", "delta", "brightnessDelta", "changePercent", "adjustBy", "operation")),
            rangeArgs = percentRanges("percent", "brightness", "value"),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_screen_timeout",
            capabilityIds = listOf("system.screen_timeout", "system.screen_timeout.set"),
            title = "设置自动锁屏时间",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.WriteSettings),
            requiredArgGroups = listOf(setOf("timeoutMs", "screenTimeoutMs", "seconds", "second", "sec", "minutes", "minute", "min")),
            rangeArgs = mapOf("timeoutMs" to 5_000.0..1_800_000.0, "seconds" to 5.0..1_800.0, "minutes" to 1.0..30.0),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_auto_rotate",
            capabilityIds = listOf("system.auto_rotate", "system.auto_rotate.set"),
            title = "设置自动旋转",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.WriteSettings),
            requiredArgGroups = listOf(setOf("enabled", "enable", "on", "state", "value", "mode")),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_media_volume",
            capabilityIds = listOf("system.media_volume", "system.media_volume.set"),
            title = "设置媒体音量",
            riskLevel = DeviceControlRiskLevel.Low,
            requiredArgGroups = listOf(setOf("percent", "volume", "value", "deltaPercent", "delta", "changePercent", "adjustBy", "operation")),
            rangeArgs = percentRanges("percent", "volume", "value"),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_wifi_enabled",
            capabilityIds = listOf("network.wifi_toggle", "network.wifi.set"),
            title = "开启/关闭 Wi-Fi",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.EnhancedShell),
            requiredArgGroups = listOf(setOf("enabled", "enable", "on", "state", "value", "mode")),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_bluetooth_enabled",
            capabilityIds = listOf("network.bluetooth_toggle", "network.bluetooth.set"),
            title = "开启/关闭蓝牙",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.EnhancedShell),
            requiredArgGroups = listOf(setOf("enabled", "enable", "on", "state", "value", "mode")),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_mobile_data_enabled",
            capabilityIds = listOf("network.mobile_data_toggle", "network.mobile_data.set"),
            title = "开启/关闭移动数据",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.EnhancedShell),
            requiredArgGroups = listOf(setOf("enabled", "enable", "on", "state", "value", "mode")),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_dark_mode",
            capabilityIds = listOf("system.dark_mode", "system.dark_mode.set"),
            title = "设置深色模式",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.EnhancedShell),
            requiredArgGroups = listOf(setOf("mode", "state", "value", "enabled", "on")),
            allowedValues = mapOf("mode" to setOf("yes", "no", "auto", "on", "off", "true", "false", "enable", "disable", "enabled", "disabled", "open", "close")),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_animation_scale",
            capabilityIds = listOf("system.animation_scale.set", "system.settings_global_write"),
            title = "设置动画缩放",
            riskLevel = DeviceControlRiskLevel.High,
            permissions = setOf(DeviceControlPermission.EnhancedShell),
            rangeArgs = mapOf("scale" to 0.0..10.0, "value" to 0.0..10.0),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "force_stop_app",
            capabilityIds = listOf("app.force_stop"),
            title = "强停应用",
            riskLevel = DeviceControlRiskLevel.High,
            permissions = setOf(DeviceControlPermission.EnhancedShell, DeviceControlPermission.ExactPackage),
            requiredArgGroups = listOf(setOf("packageName", "package", "pkg")),
            stateChanging = true,
            reversible = false,
        ),
        DeviceControlSpec(
            stepType = "clear_app_data",
            capabilityIds = listOf("app.clear_data"),
            title = "清除应用数据",
            riskLevel = DeviceControlRiskLevel.Critical,
            permissions = setOf(DeviceControlPermission.EnhancedShell, DeviceControlPermission.ExactPackage),
            requiredArgGroups = listOf(setOf("packageName", "package", "pkg")),
            stateChanging = true,
            reversible = false,
        ),
        DeviceControlSpec(
            stepType = "uninstall_app",
            capabilityIds = listOf("app.uninstall"),
            title = "卸载当前用户应用",
            riskLevel = DeviceControlRiskLevel.Critical,
            permissions = setOf(DeviceControlPermission.EnhancedShell, DeviceControlPermission.ExactPackage),
            requiredArgGroups = listOf(setOf("packageName", "package", "pkg")),
            stateChanging = true,
            reversible = false,
        ),
        DeviceControlSpec(
            stepType = "disable_app",
            capabilityIds = listOf("app.disable", "system.app_disable"),
            title = "禁用应用",
            riskLevel = DeviceControlRiskLevel.Critical,
            permissions = setOf(DeviceControlPermission.EnhancedShell, DeviceControlPermission.ExactPackage),
            requiredArgGroups = listOf(setOf("packageName", "package", "pkg")),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "enable_app",
            capabilityIds = listOf("app.enable", "system.app_enable"),
            title = "启用应用",
            riskLevel = DeviceControlRiskLevel.Critical,
            permissions = setOf(DeviceControlPermission.EnhancedShell, DeviceControlPermission.ExactPackage),
            requiredArgGroups = listOf(setOf("packageName", "package", "pkg")),
            stateChanging = true,
        ),
    )

    val byStepType: Map<String, DeviceControlSpec> = all.associateBy { it.stepType }
    val capabilityToStepType: Map<String, String> = buildMap {
        all.forEach { spec ->
            spec.capabilityIds.forEach { capability ->
                put(normalizeKey(capability), spec.stepType)
            }
        }
    }

    fun specFor(stepType: String): DeviceControlSpec? = byStepType[stepType]

    fun normalizeCapability(raw: String): String? {
        val key = normalizeKey(raw)
        capabilityToStepType[key]?.let { return it }
        capabilityToStepType[key.replace('_', '.')]?.let { return it }
        return null
    }

    fun supportedCapabilities(): List<String> = capabilityToStepType.keys.sorted()

    fun normalChatSupportedCapabilities(): List<String> {
        return all.filter { it.normalChatAllowed }.flatMap { it.capabilityIds }.map(::normalizeKey).sorted()
    }

    fun normalChatSupportedStepTypes(): List<String> {
        return all.filter { it.normalChatAllowed }.map { it.stepType }.distinct().sorted()
    }

    fun requiresConfirmation(step: CloudAgentStep): Boolean {
        return step.requiresConfirmation || byStepType[step.type]?.requiresConfirmation == true
    }

    fun riskFor(stepType: String): String = byStepType[stepType]?.riskLevel?.name?.lowercase() ?: "low"

    fun validate(step: CloudAgentStep): DeviceControlValidation {
        val spec = byStepType[step.type] ?: return DeviceControlValidation.invalid("unsupported_device_tool:${step.type}")
        val args = step.toolArgs ?: JSONObject()

        if (DeviceControlPermission.ExactPackage in spec.permissions) {
            val packageName = (step.packageName ?: args.specFirstNonBlank("packageName", "package", "pkg")).orEmpty()
            if (!isSafePackageName(packageName)) return DeviceControlValidation.invalid("missing_or_invalid_package_name")
        }

        for (group in spec.requiredArgGroups) {
            if (!group.any { hasStepArg(step, args, it) }) {
                return DeviceControlValidation.invalid("missing_required_args:${group.sorted().joinToString("|")}")
            }
        }

        spec.rangeArgs.forEach { (name, range) ->
            val number = numericArg(step, args, name) ?: return@forEach
            if (number !in range) return DeviceControlValidation.invalid("arg_out_of_range:$name")
        }

        spec.allowedValues.forEach { (name, values) ->
            val raw = stringArg(step, args, name)?.lowercase()?.replace('-', '_') ?: return@forEach
            if (raw.isNotBlank() && raw !in values) return DeviceControlValidation.invalid("arg_not_allowed:$name")
        }

        return DeviceControlValidation.Ok
    }

    fun isSafePackageName(packageName: String): Boolean {
        return packageName.matches(Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+"""))
    }

    private fun normalizeKey(value: String): String = value.trim().lowercase().replace('-', '_')

    private fun percentRanges(vararg names: String): Map<String, ClosedFloatingPointRange<Double>> =
        names.associateWith { 0.0..100.0 }

    private fun hasStepArg(step: CloudAgentStep, args: JSONObject, name: String): Boolean {
        return when (name) {
            "packageName" -> !step.packageName.isNullOrBlank() || !args.specFirstNonBlank("packageName", "package", "pkg").isNullOrBlank()
            "text" -> !step.text.isNullOrBlank() || !args.specFirstNonBlank("text", "inputText", "value", "query", "content").isNullOrBlank()
            "target" -> !step.targetText.isNullOrBlank() || !args.specFirstNonBlank("target", "targetText", "page", "kind", "setting").isNullOrBlank()
            else -> !stringArg(step, args, name).isNullOrBlank()
        }
    }

    private fun numericArg(step: CloudAgentStep, args: JSONObject, name: String): Double? {
        val raw = stringArg(step, args, name) ?: return null
        return raw.removeSuffix("%").toDoubleOrNull()
    }

    private fun stringArg(step: CloudAgentStep, args: JSONObject, name: String): String? {
        val fromArgs = if (args.has(name) && !args.isNull(name)) args.optString(name).trim() else ""
        if (fromArgs.isNotBlank()) return fromArgs
        return when (name) {
            "packageName", "package", "pkg" -> step.packageName
            "target", "targetText", "page", "kind", "setting" -> step.targetText
            "text", "inputText", "value", "query", "content" -> step.text
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() }
    }
}

private fun JSONObject.specFirstNonBlank(vararg names: String): String? {
    for (name in names) {
        val value = optString(name).trim()
        if (value.isNotBlank()) return value
    }
    return null
}
