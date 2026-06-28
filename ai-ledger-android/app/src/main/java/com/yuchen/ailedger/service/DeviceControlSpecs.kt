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
    val allowedArgNames: Set<String> = emptySet(),
    val rangeArgs: Map<String, ClosedFloatingPointRange<Double>> = emptyMap(),
    val allowedValues: Map<String, Set<String>> = emptyMap(),
    val booleanArgs: Set<String> = emptySet(),
    val integerArgs: Set<String> = emptySet(),
    val mutuallyExclusiveArgGroups: List<Set<String>> = emptyList(),
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
            requiredArgGroups.forEach { group -> put(JSONArray().apply { group.forEach(::put) }) }
        })
        put("allowedArgNames", JSONArray().apply { allowedArgNames.forEach(::put) })
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
        put("booleanArgs", JSONArray().apply { booleanArgs.forEach(::put) })
        put("integerArgs", JSONArray().apply { integerArgs.forEach(::put) })
        put("mutuallyExclusiveArgGroups", JSONArray().apply {
            mutuallyExclusiveArgGroups.forEach { group -> put(JSONArray().apply { group.forEach(::put) }) }
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

/**
 * Pure Android execution contract for cloud-selected internal controls.
 *
 * DeepSeek is the only semantic planner. Android accepts canonical tool + args only, rejects
 * aliases and unknown fields, and performs schema, permission, package and risk-boundary checks.
 */
object DeviceControlSpecs {
    private val packageArgs = setOf("packageName")

    val all: List<DeviceControlSpec> = listOf(
        DeviceControlSpec(
            stepType = "device_status",
            capabilityIds = listOf("device.health", "device.status"),
            title = "手机体检",
            riskLevel = DeviceControlRiskLevel.Low,
        ),
        DeviceControlSpec(
            stepType = "shizuku_status",
            capabilityIds = listOf("shell.probe", "shell.status", "shizuku.status"),
            title = "Shell/Shizuku 增强模式探测",
            riskLevel = DeviceControlRiskLevel.Low,
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
            requiredArgGroups = listOf(setOf("page")),
            allowedArgNames = setOf("page"),
            allowedValues = mapOf(
                "page" to setOf(
                    "system", "wifi", "bluetooth", "battery", "display", "notification",
                    "accessibility", "apps", "storage", "sound", "location", "data", "developer", "dnd",
                ),
            ),
        ),
        DeviceControlSpec(
            stepType = "open_app",
            capabilityIds = listOf("app.open"),
            title = "打开应用",
            riskLevel = DeviceControlRiskLevel.Low,
            permissions = setOf(DeviceControlPermission.ExactPackage),
            requiredArgGroups = listOf(setOf("packageName")),
            allowedArgNames = packageArgs,
        ),
        DeviceControlSpec(
            stepType = "open_app_settings",
            capabilityIds = listOf("app.settings"),
            title = "打开 App 专属系统设置",
            riskLevel = DeviceControlRiskLevel.Low,
            permissions = setOf(DeviceControlPermission.ExactPackage),
            requiredArgGroups = listOf(setOf("packageName"), setOf("page")),
            allowedArgNames = packageArgs + "page",
            allowedValues = mapOf("page" to setOf("details", "notification", "permission", "battery")),
        ),
        DeviceControlSpec(
            stepType = "set_brightness",
            capabilityIds = listOf("system.brightness", "system.brightness.set"),
            title = "调节屏幕亮度",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.WriteSettings),
            requiredArgGroups = listOf(setOf("percent", "deltaPercent")),
            allowedArgNames = setOf("percent", "deltaPercent"),
            rangeArgs = mapOf("percent" to 0.0..100.0, "deltaPercent" to -100.0..100.0),
            mutuallyExclusiveArgGroups = listOf(setOf("percent", "deltaPercent")),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_screen_timeout",
            capabilityIds = listOf("system.screen_timeout", "system.screen_timeout.set"),
            title = "设置自动锁屏时间",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.WriteSettings),
            requiredArgGroups = listOf(setOf("timeoutMs")),
            allowedArgNames = setOf("timeoutMs"),
            rangeArgs = mapOf("timeoutMs" to 5_000.0..1_800_000.0),
            integerArgs = setOf("timeoutMs"),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_auto_rotate",
            capabilityIds = listOf("system.auto_rotate", "system.auto_rotate.set"),
            title = "设置自动旋转",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.WriteSettings),
            requiredArgGroups = listOf(setOf("enabled")),
            allowedArgNames = setOf("enabled"),
            booleanArgs = setOf("enabled"),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_media_volume",
            capabilityIds = listOf("system.media_volume", "system.media_volume.set"),
            title = "设置媒体音量",
            riskLevel = DeviceControlRiskLevel.Low,
            requiredArgGroups = listOf(setOf("percent", "deltaPercent")),
            allowedArgNames = setOf("percent", "deltaPercent"),
            rangeArgs = mapOf("percent" to 0.0..100.0, "deltaPercent" to -100.0..100.0),
            mutuallyExclusiveArgGroups = listOf(setOf("percent", "deltaPercent")),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_wifi_enabled",
            capabilityIds = listOf("network.wifi_toggle", "network.wifi.set"),
            title = "开启/关闭 Wi-Fi",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.EnhancedShell),
            requiredArgGroups = listOf(setOf("enabled")),
            allowedArgNames = setOf("enabled"),
            booleanArgs = setOf("enabled"),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_bluetooth_enabled",
            capabilityIds = listOf("network.bluetooth_toggle", "network.bluetooth.set"),
            title = "开启/关闭蓝牙",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.EnhancedShell),
            requiredArgGroups = listOf(setOf("enabled")),
            allowedArgNames = setOf("enabled"),
            booleanArgs = setOf("enabled"),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_mobile_data_enabled",
            capabilityIds = listOf("network.mobile_data_toggle", "network.mobile_data.set"),
            title = "开启/关闭移动数据",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.EnhancedShell),
            requiredArgGroups = listOf(setOf("enabled")),
            allowedArgNames = setOf("enabled"),
            booleanArgs = setOf("enabled"),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_dark_mode",
            capabilityIds = listOf("system.dark_mode", "system.dark_mode.set"),
            title = "设置深色模式",
            riskLevel = DeviceControlRiskLevel.Medium,
            permissions = setOf(DeviceControlPermission.EnhancedShell),
            requiredArgGroups = listOf(setOf("mode")),
            allowedArgNames = setOf("mode"),
            allowedValues = mapOf("mode" to setOf("yes", "no", "auto")),
            stateChanging = true,
        ),
        DeviceControlSpec(
            stepType = "set_animation_scale",
            capabilityIds = listOf("system.animation_scale.set", "system.settings_global_write"),
            title = "设置动画缩放",
            riskLevel = DeviceControlRiskLevel.High,
            permissions = setOf(DeviceControlPermission.EnhancedShell),
            requiredArgGroups = listOf(setOf("scale")),
            allowedArgNames = setOf("scale"),
            rangeArgs = mapOf("scale" to 0.0..10.0),
            stateChanging = true,
        ),
        privilegedAppSpec("force_stop_app", listOf("app.force_stop"), "强停应用", DeviceControlRiskLevel.High, reversible = false),
        privilegedAppSpec("clear_app_data", listOf("app.clear_data"), "清除应用数据", DeviceControlRiskLevel.Critical, reversible = false),
        privilegedAppSpec("uninstall_app", listOf("app.uninstall"), "卸载当前用户应用", DeviceControlRiskLevel.Critical, reversible = false),
        privilegedAppSpec("disable_app", listOf("app.disable", "system.app_disable"), "禁用应用", DeviceControlRiskLevel.Critical),
        privilegedAppSpec("enable_app", listOf("app.enable", "system.app_enable"), "启用应用", DeviceControlRiskLevel.Critical),
    )

    val byStepType: Map<String, DeviceControlSpec> = all.associateBy { it.stepType }
    val capabilityToStepType: Map<String, String> = buildMap {
        all.forEach { spec -> spec.capabilityIds.forEach { capability -> put(normalizeKey(capability), spec.stepType) } }
    }

    fun specFor(stepType: String): DeviceControlSpec? = byStepType[stepType]

    fun normalizeCapability(raw: String): String? {
        val key = normalizeKey(raw)
        capabilityToStepType[key]?.let { return it }
        capabilityToStepType[key.replace('_', '.')]?.let { return it }
        return null
    }

    fun supportedCapabilities(): List<String> = capabilityToStepType.keys.sorted()

    fun normalChatSupportedCapabilities(): List<String> = all
        .filter { it.normalChatAllowed }
        .flatMap { it.capabilityIds }
        .map(::normalizeKey)
        .sorted()

    fun normalChatSupportedStepTypes(): List<String> = all
        .filter { it.normalChatAllowed }
        .map { it.stepType }
        .distinct()
        .sorted()

    fun requiresConfirmation(step: CloudAgentStep): Boolean =
        step.requiresConfirmation || byStepType[step.type]?.requiresConfirmation == true

    fun riskFor(stepType: String): String = byStepType[stepType]?.riskLevel?.name?.lowercase() ?: "low"

    fun validate(step: CloudAgentStep): DeviceControlValidation {
        val spec = byStepType[step.type]
            ?: return DeviceControlValidation.invalid("unsupported_device_tool:${step.type}")
        val args = step.toolArgs ?: JSONObject()

        val unknownArgs = args.keys().asSequence().filterNot { it in spec.allowedArgNames }.toList()
        if (unknownArgs.isNotEmpty()) {
            return DeviceControlValidation.invalid("non_canonical_args:${unknownArgs.sorted().joinToString("|")}")
        }

        if (DeviceControlPermission.ExactPackage in spec.permissions) {
            val topLevelPackage = step.packageName?.trim().orEmpty()
            val argsPackage = args.canonicalString("packageName")
            if (topLevelPackage.isNotBlank() && argsPackage.isNotBlank() && topLevelPackage != argsPackage) {
                return DeviceControlValidation.invalid("conflicting_package_name")
            }
            val packageName = topLevelPackage.ifBlank { argsPackage }
            if (!isSafePackageName(packageName)) {
                return DeviceControlValidation.invalid("missing_or_invalid_package_name")
            }
        }

        for (group in spec.requiredArgGroups) {
            if (!group.any { hasCanonicalArg(step, args, it) }) {
                return DeviceControlValidation.invalid("missing_required_args:${group.sorted().joinToString("|")}")
            }
        }

        for (group in spec.mutuallyExclusiveArgGroups) {
            if (group.count { hasCanonicalArg(step, args, it) } > 1) {
                return DeviceControlValidation.invalid("mutually_exclusive_args:${group.sorted().joinToString("|")}")
            }
        }

        for ((name, range) in spec.rangeArgs) {
            if (!args.hasCanonicalValue(name)) continue
            val number = args.canonicalNumber(name)
                ?: return DeviceControlValidation.invalid("arg_not_number:$name")
            if (!number.isFinite()) return DeviceControlValidation.invalid("arg_not_finite:$name")
            if (number !in range) return DeviceControlValidation.invalid("arg_out_of_range:$name")
        }

        for (name in spec.integerArgs) {
            if (!args.hasCanonicalValue(name)) continue
            val number = args.canonicalNumber(name)
                ?: return DeviceControlValidation.invalid("arg_not_number:$name")
            if (number % 1.0 != 0.0) return DeviceControlValidation.invalid("arg_not_integer:$name")
        }

        for (name in spec.booleanArgs) {
            if (!args.hasCanonicalValue(name)) continue
            if (args.opt(name) !is Boolean) return DeviceControlValidation.invalid("arg_not_boolean:$name")
        }

        for ((name, values) in spec.allowedValues) {
            if (!args.hasCanonicalValue(name)) continue
            val raw = args.opt(name) as? String
                ?: return DeviceControlValidation.invalid("arg_not_string:$name")
            if (raw !in values) return DeviceControlValidation.invalid("arg_not_allowed:$name")
        }

        return DeviceControlValidation.Ok
    }

    fun isSafePackageName(packageName: String): Boolean =
        packageName.matches(Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+"""))

    private fun privilegedAppSpec(
        stepType: String,
        capabilityIds: List<String>,
        title: String,
        riskLevel: DeviceControlRiskLevel,
        reversible: Boolean = true,
    ) = DeviceControlSpec(
        stepType = stepType,
        capabilityIds = capabilityIds,
        title = title,
        riskLevel = riskLevel,
        permissions = setOf(DeviceControlPermission.EnhancedShell, DeviceControlPermission.ExactPackage),
        requiredArgGroups = listOf(setOf("packageName")),
        allowedArgNames = packageArgs,
        stateChanging = true,
        reversible = reversible,
    )

    private fun hasCanonicalArg(step: CloudAgentStep, args: JSONObject, name: String): Boolean = when (name) {
        "packageName" -> step.packageName?.trim().orEmpty().isNotBlank() || args.hasCanonicalValue(name)
        else -> args.hasCanonicalValue(name)
    }

    private fun normalizeKey(value: String): String = value.trim().lowercase().replace('-', '_')
}

private fun JSONObject.hasCanonicalValue(name: String): Boolean =
    has(name) && !isNull(name) && when (val value = opt(name)) {
        is String -> value.isNotBlank()
        else -> value != null
    }

private fun JSONObject.canonicalNumber(name: String): Double? = when (val value = opt(name)) {
    is Number -> value.toDouble()
    else -> null
}

private fun JSONObject.canonicalString(name: String): String =
    (opt(name) as? String)?.trim().orEmpty()
