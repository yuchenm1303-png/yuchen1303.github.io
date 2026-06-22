package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

data class CloudAgentState(
    val isComplete: Boolean = false,
    val expectedProgress: Boolean = false,
    val isWrong: Boolean = false,
    val confidence: Float = 0f,
    val reason: String = "",
    val nextHint: String = "",
) {
    companion object {
        fun fromJson(root: JSONObject?): CloudAgentState? {
            if (root == null) return null
            val item = root.optJSONObject("agentState")
                ?: root.optJSONObject("state")
                ?: root.optJSONObject("data")?.optJSONObject("agentState")
                ?: root.optJSONObject("result")?.optJSONObject("agentState")
                ?: root.optJSONObject("plan")?.optJSONObject("agentState")
                ?: root.takeIf {
                    it.has("isComplete") || it.has("complete") ||
                        it.has("expectedProgress") || it.has("isWrong")
                }
                ?: return null

            val complete = item.optFlexibleBoolean("isComplete")
                ?: item.optFlexibleBoolean("complete")
                ?: item.optFlexibleBoolean("completed")
                ?: item.optFlexibleBoolean("isExpected")
                ?: false
            val progress = item.optFlexibleBoolean("expectedProgress")
                ?: item.optFlexibleBoolean("progress")
                ?: item.optFlexibleBoolean("isProgress")
                ?: item.optFlexibleBoolean("onRightTrack")
                ?: complete
            val wrong = item.optFlexibleBoolean("isWrong")
                ?: item.optFlexibleBoolean("wrong")
                ?: item.optFlexibleBoolean("wrongPage")
                ?: item.optFlexibleBoolean("offTarget")
                ?: false
            val safeWrong = wrong && !complete && !progress
            val confidence = item.optNullableFloat("confidence")
                ?: item.optNullableFloat("score")
                ?: when {
                    complete || safeWrong -> 0.72f
                    progress -> 0.62f
                    else -> 0.35f
                }
            return CloudAgentState(
                isComplete = complete,
                expectedProgress = progress || complete,
                isWrong = safeWrong,
                confidence = confidence.coerceIn(0f, 1f),
                reason = item.optString("reason").notBlankOrNull()
                    ?: item.optString("explanation").notBlankOrNull()
                    ?: item.optString("rationale").notBlankOrNull()
                    ?: "",
                nextHint = item.optString("nextHint").notBlankOrNull()
                    ?: item.optString("next_hint").notBlankOrNull()
                    ?: item.optString("hint").notBlankOrNull()
                    ?: "",
            )
        }
    }
}

data class CloudAgentPlan(
    val step: CloudAgentStep,
    val state: CloudAgentState? = null,
    val steps: List<CloudAgentStep> = emptyList(),
    val stopConditions: Set<String> = emptySet(),
    val rawModelOutput: String = "",
) {
    val executableSteps: List<CloudAgentStep>
        get() = steps.ifEmpty { listOf(step) }.take(MAX_BATCH_STEPS)

    companion object {
        const val MAX_BATCH_STEPS = 3

        fun fromJson(root: JSONObject?): CloudAgentPlan? {
            val primary = CloudAgentStep.fromJson(root) ?: return null
            val parsedSteps = extractBatchSteps(root)
                .filterNot { it.type == "need_user_help" || it.type == "finish" }
                .distinctBy { it.batchKey() }
                .take(MAX_BATCH_STEPS)
            return CloudAgentPlan(
                step = primary,
                state = CloudAgentState.fromJson(root),
                steps = parsedSteps.ifEmpty { listOf(primary) },
                stopConditions = extractStopConditions(root),
                rawModelOutput = extractRawModelOutput(root),
            )
        }

        private fun extractRawModelOutput(root: JSONObject?): String {
            if (root == null) return ""
            val containers = listOfNotNull(
                root,
                root.optJSONObject("debug"),
                root.optJSONObject("data"),
                root.optJSONObject("result"),
            )
            return containers.firstNotNullOfOrNull { container ->
                container.optString("rawModelOutput").notBlankOrNull()
                    ?: container.optString("guiPlusRawOutput").notBlankOrNull()
                    ?: container.optString("rawOutput").notBlankOrNull()
                    ?: container.optString("raw").notBlankOrNull()
            }?.take(6000).orEmpty()
        }

        private fun extractBatchSteps(root: JSONObject?): List<CloudAgentStep> {
            if (root == null) return emptyList()
            val containers = listOfNotNull(
                root,
                root.optJSONObject("plan"),
                root.optJSONObject("data"),
                root.optJSONObject("result"),
                root.optJSONObject("agentPlan"),
            )
            return containers.flatMap { container ->
                listOf("agentSteps", "steps", "actionBatch", "actions")
                    .flatMap { key -> container.optJSONArray(key).toAgentSteps() }
            }
        }

        private fun extractStopConditions(root: JSONObject?): Set<String> {
            if (root == null) return emptySet()
            val containers = listOfNotNull(
                root,
                root.optJSONObject("plan"),
                root.optJSONObject("data"),
                root.optJSONObject("result"),
                root.optJSONObject("agentPlan"),
            )
            return containers.flatMap { container ->
                listOf("stopConditions", "batchStopConditions", "replanOn")
                    .flatMap { key -> container.optStringSet(key) }
            }.toSet()
        }

        private fun JSONArray?.toAgentSteps(): List<CloudAgentStep> {
            if (this == null) return emptyList()
            return buildList {
                for (index in 0 until length()) {
                    val item = optJSONObject(index) ?: continue
                    CloudAgentStep.fromJson(item)?.let(::add)
                }
            }
        }
    }
}

data class CloudAgentStep(
    val type: String,
    val targetNodeId: String? = null,
    val targetText: String? = null,
    val text: String? = null,
    val direction: String? = null,
    val reason: String? = null,
    val riskLevel: String = "low",
    val requiresConfirmation: Boolean = false,
    val appName: String? = null,
    val packageName: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val durationMs: Long? = null,
    val inputMode: String? = null,
    val requiresInputNode: Boolean = true,
    val expectsFocusedInput: Boolean = false,
    val useFocusedInput: Boolean = false,
    val toolArgs: JSONObject? = null,
) {
    val typeLabel: String
        get() = TYPE_LABELS[type] ?: type

    val shouldUseFocusedDirectInput: Boolean
        get() = inputMode?.lowercase()?.replace('-', '_') in focusedInputModes ||
            useFocusedInput || expectsFocusedInput || !requiresInputNode

    fun argString(vararg names: String): String? {
        val args = toolArgs ?: return null
        for (name in names) {
            val value = args.optString(name).trim()
            if (value.isNotBlank()) return value
        }
        return null
    }

    fun argFloat(vararg names: String): Float? {
        val args = toolArgs ?: return null
        for (name in names) {
            if (!args.has(name) || args.isNull(name)) continue
            val value = runCatching { args.getDouble(name).toFloat() }.getOrNull()
                ?: args.optString(name).trim().removeSuffix("%").toFloatOrNull()
            if (value != null && value.isFinite()) return value
        }
        return null
    }

    fun argLong(vararg names: String): Long? {
        val args = toolArgs ?: return null
        for (name in names) {
            if (!args.has(name) || args.isNull(name)) continue
            val value = runCatching { args.getLong(name) }.getOrNull()
                ?: args.optString(name).trim().toLongOrNull()
            if (value != null) return value
        }
        return null
    }

    companion object {
        private val focusedInputModes = setOf(
            "focused_direct", "focused", "direct", "keyboard", "ime",
            "active_input", "current_focus",
        )

        private val TYPE_LABELS = mapOf(
            "open_app" to "打开应用",
            "tap_node" to "点击节点",
            "tap_xy" to "点击坐标",
            "input_text" to "输入文字",
            "scroll" to "滚动屏幕",
            "swipe" to "滑动屏幕",
            "back" to "返回",
            "home" to "回到桌面",
            "recents" to "打开最近任务",
            "notifications" to "下拉通知栏",
            "quick_settings" to "打开快捷设置",
            "wait" to "等待",
            "finish" to "任务完成",
            "need_user_help" to "需要用户协助",
            "open_system_settings" to "打开系统设置",
            "open_app_settings" to "打开应用设置",
            "set_brightness" to "调节亮度",
            "set_screen_timeout" to "设置息屏时间",
            "set_auto_rotate" to "设置自动旋转",
            "set_media_volume" to "设置媒体音量",
            "set_wifi_enabled" to "设置 Wi‑Fi",
            "set_bluetooth_enabled" to "设置蓝牙",
            "set_mobile_data_enabled" to "设置移动数据",
            "set_dark_mode" to "设置深色模式",
            "device_status" to "设备状态",
            "shizuku_status" to "Shizuku 状态",
            "request_shizuku_permission" to "请求 Shizuku 授权",
            "set_animation_scale" to "设置动画缩放",
            "force_stop_app" to "强停应用",
            "clear_app_data" to "清除应用数据",
            "uninstall_app" to "卸载应用",
            "disable_app" to "禁用应用",
            "enable_app" to "启用应用",
            "ledger_add_record" to "新增账单",
            "ledger_set_budget" to "设置账单预算",
            "ledger_query_summary" to "查询账单汇总",
            "ledger_list_records" to "查询账单明细",
        )

        val systemDeviceToolTypes = setOf(
            "open_app", "open_system_settings", "open_app_settings",
            "set_brightness", "set_screen_timeout", "set_auto_rotate",
            "set_media_volume", "set_wifi_enabled", "set_bluetooth_enabled",
            "set_mobile_data_enabled", "set_dark_mode", "device_status",
            "shizuku_status", "request_shizuku_permission", "set_animation_scale",
            "force_stop_app", "clear_app_data", "uninstall_app",
            "disable_app", "enable_app",
        )

        val ledgerToolTypes = setOf(
            "ledger_add_record",
            "ledger_set_budget",
            "ledger_query_summary",
            "ledger_list_records",
        )

        val deviceToolTypes = systemDeviceToolTypes + ledgerToolTypes
        val internalToolTypes = deviceToolTypes

        val supportedTypes = setOf(
            "tap_node", "tap_xy", "input_text", "scroll", "swipe",
            "back", "home", "recents", "notifications", "quick_settings",
            "wait", "finish", "need_user_help",
        ) + internalToolTypes

        fun fromJson(root: JSONObject?): CloudAgentStep? {
            val item = root?.optJSONObject("agentStep")
                ?: root?.optJSONObject("step")
                ?: root?.optJSONObject("agentAction")?.optJSONObject("step")
                ?: root?.optJSONObject("data")?.optJSONObject("agentStep")
                ?: root?.optJSONObject("data")?.optJSONObject("step")
                ?: root?.optJSONObject("result")?.optJSONObject("agentStep")
                ?: root?.takeIf { it.has("type") || it.has("action") || it.has("tool") || it.has("name") }
                ?: return null
            val args = item.mergedToolArgs()
            val rawType = item.firstNonBlank("type", "action", "tool", "name") ?: return null
            val normalizedType = normalizeStepType(rawType) ?: return null
            val parsedInputMode = item.firstNonBlank(
                "inputMode", "input_mode", "inputStrategy", "input_strategy",
            ) ?: args.firstNonBlank("inputMode", "input_mode", "inputStrategy", "input_strategy")
            val inputModeKey = parsedInputMode?.lowercase()?.replace('-', '_')
            val explicitFocused = item.optFlexibleBoolean("useFocusedInput")
                ?: item.optFlexibleBoolean("use_focused_input")
                ?: item.optFlexibleBoolean("focusedDirect")
                ?: item.optFlexibleBoolean("focused_direct")
            val expectsFocused = item.optFlexibleBoolean("expectsFocusedInput")
                ?: item.optFlexibleBoolean("expects_focused_input")
                ?: item.optFlexibleBoolean("focusedInput")
                ?: item.optFlexibleBoolean("focused_input")
                ?: false
            val inferredFocused = inputModeKey in focusedInputModes ||
                explicitFocused == true || expectsFocused
            val requiresNode = item.optFlexibleBoolean("requiresInputNode")
                ?: item.optFlexibleBoolean("requires_input_node")
                ?: item.optFlexibleBoolean("inputNodeRequired")
                ?: item.optFlexibleBoolean("input_node_required")
                ?: !inferredFocused

            val targetText = item.firstNonBlank("targetText", "label", "title", "target")
                ?: args.firstNonBlank("targetText", "target", "label", "title", "page", "kind")
            val parsedText = item.firstNonBlank("text", "inputText", "value")
                ?: args.firstNonBlank("text", "inputText", "value", "query", "content")
            val appName = item.firstNonBlank("appName", "app", "application")
                ?: args.firstNonBlank("appName", "app", "application", "label", "name")
                ?: if (normalizedType == "open_app") targetText ?: parsedText else null
            val packageName = item.firstNonBlank("packageName", "package", "pkg", "appRef", "app_ref")
                ?: args.firstNonBlank("packageName", "package", "pkg", "appRef", "app_ref")

            return CloudAgentStep(
                type = normalizedType,
                targetNodeId = item.firstNonBlank("targetNodeId", "nodeId", "targetId")
                    ?: args.firstNonBlank("targetNodeId", "nodeId", "targetId"),
                targetText = targetText,
                text = parsedText,
                direction = item.firstNonBlank("direction")?.lowercase()
                    ?: args.firstNonBlank("direction")?.lowercase(),
                reason = item.firstNonBlank("reason", "rationale")
                    ?: args.firstNonBlank("reason", "rationale"),
                riskLevel = item.firstNonBlank("riskLevel", "risk")
                    ?.lowercase()?.replace('-', '_')
                    ?: args.firstNonBlank("riskLevel", "risk")
                        ?.lowercase()?.replace('-', '_')
                    ?: "low",
                requiresConfirmation = item.optFlexibleBoolean("requiresConfirmation")
                    ?: item.optFlexibleBoolean("confirm")
                    ?: false,
                appName = appName,
                packageName = packageName,
                x = item.optTapCoordinateComponent(0) ?: args.optTapCoordinateComponent(0),
                y = item.optTapCoordinateComponent(1) ?: args.optTapCoordinateComponent(1),
                durationMs = item.optNullableLong("durationMs")
                    ?: item.optNullableLong("delayMs")
                    ?: item.optNullableLong("waitMs")
                    ?: args.optNullableLong("durationMs")
                    ?: args.optNullableLong("delayMs")
                    ?: args.optNullableLong("waitMs"),
                inputMode = parsedInputMode,
                requiresInputNode = requiresNode,
                expectsFocusedInput = expectsFocused,
                useFocusedInput = explicitFocused ?: inferredFocused,
                toolArgs = args.takeIf { it.length() > 0 },
            )
        }

        private fun normalizeStepType(rawType: String): String? {
            val key = rawType.lowercase().trim().replace('-', '_')
            val normalized = TYPE_ALIASES[key] ?: key
            return normalized.takeIf { it in supportedTypes }
        }

        private val TYPE_ALIASES = mapOf(
            "open" to "open_app", "launch" to "open_app",
            "launch_app" to "open_app", "open_application" to "open_app",
            "tap" to "tap_xy", "click" to "tap_xy", "press" to "tap_xy",
            "point" to "tap_xy", "tap_point" to "tap_xy",
            "click_xy" to "tap_xy", "coordinate_click" to "tap_xy",
            "coordinate_tap" to "tap_xy",
            "input" to "input_text", "type" to "input_text",
            "enter_text" to "input_text", "text" to "input_text",
            "done" to "finish", "complete" to "finish", "completed" to "finish",
            "ask_user" to "need_user_help", "need_help" to "need_user_help",
            "clarify" to "need_user_help",
            "settings" to "open_system_settings", "open_settings" to "open_system_settings",
            "system_settings" to "open_system_settings",
            "app_settings" to "open_app_settings", "app_info" to "open_app_settings",
            "open_app_detail" to "open_app_settings",
            "brightness" to "set_brightness", "screen_brightness" to "set_brightness",
            "screen_timeout" to "set_screen_timeout", "sleep_timeout" to "set_screen_timeout",
            "auto_rotate" to "set_auto_rotate", "rotation" to "set_auto_rotate",
            "accelerometer_rotation" to "set_auto_rotate",
            "media_volume" to "set_media_volume", "volume" to "set_media_volume",
            "set_volume" to "set_media_volume", "music_volume" to "set_media_volume",
            "wifi" to "set_wifi_enabled", "wi_fi" to "set_wifi_enabled",
            "set_wifi" to "set_wifi_enabled", "wifi_enabled" to "set_wifi_enabled",
            "bluetooth" to "set_bluetooth_enabled",
            "set_bluetooth" to "set_bluetooth_enabled",
            "bluetooth_enabled" to "set_bluetooth_enabled",
            "mobile_data" to "set_mobile_data_enabled",
            "cellular_data" to "set_mobile_data_enabled",
            "data_enabled" to "set_mobile_data_enabled", "set_data" to "set_mobile_data_enabled",
            "dark_mode" to "set_dark_mode", "night_mode" to "set_dark_mode",
            "ui_mode" to "set_dark_mode",
            "health" to "device_status", "device_health" to "device_status",
            "shell_status" to "shizuku_status", "enhanced_status" to "shizuku_status",
            "shizuku" to "shizuku_status",
            "shizuku_permission" to "request_shizuku_permission",
            "request_shizuku" to "request_shizuku_permission",
            "animation_scale" to "set_animation_scale",
            "force_stop" to "force_stop_app",
            "force_stop_application" to "force_stop_app",
            "clear_data" to "clear_app_data", "uninstall" to "uninstall_app",
            "disable" to "disable_app", "enable" to "enable_app",
            "add_ledger_record" to "ledger_add_record",
            "create_ledger_record" to "ledger_add_record",
            "ledger_record_add" to "ledger_add_record",
            "ledger_add" to "ledger_add_record",
            "set_ledger_budget" to "ledger_set_budget",
            "ledger_budget_set" to "ledger_set_budget",
            "budget_set" to "ledger_set_budget",
            "query_ledger_summary" to "ledger_query_summary",
            "ledger_summary" to "ledger_query_summary",
            "ledger_query" to "ledger_query_summary",
            "list_ledger_records" to "ledger_list_records",
            "ledger_records" to "ledger_list_records",
            "ledger_list" to "ledger_list_records",
        )
    }
}

private fun CloudAgentStep.batchKey(): String = buildString {
    append(type)
    append('|')
    append(packageName.orEmpty())
    append('|')
    append(appName.orEmpty())
    append('|')
    append(targetNodeId.orEmpty())
    append('|')
    append(targetText.orEmpty())
    append('|')
    append(text.orEmpty())
    append('|')
    append(x?.toString().orEmpty())
    append('|')
    append(y?.toString().orEmpty())
    append('|')
    append(toolArgs?.toString().orEmpty())
}

private fun JSONObject.optStringSet(name: String): Set<String> {
    if (!has(name) || isNull(name)) return emptySet()
    return when (val raw = opt(name)) {
        is JSONArray -> buildSet {
            for (index in 0 until raw.length()) {
                raw.optString(index).notBlankOrNull()
                    ?.lowercase()?.replace('-', '_')?.let(::add)
            }
        }
        is String -> raw.split(',', ';', '|')
            .mapNotNull { it.notBlankOrNull()?.lowercase()?.replace('-', '_') }
            .toSet()
        else -> emptySet()
    }
}

private fun JSONObject.mergedToolArgs(): JSONObject {
    val source = optJSONObject("args") ?: optJSONObject("arguments") ?: JSONObject()
    val merged = runCatching { JSONObject(source.toString()) }.getOrDefault(JSONObject())
    val keys = listOf(
        "appName", "app", "application", "packageName", "package", "pkg", "appRef", "app_ref",
        "targetText", "target", "label", "title", "page", "kind",
        "percent", "brightness", "volume", "value", "seconds", "minutes",
        "timeoutMs", "durationMs", "scale", "enabled", "enable", "on",
        "state", "mode", "operation", "delta", "deltaPercent", "changePercent",
        "adjustBy", "text", "query", "reason", "risk", "riskLevel",
        "direction", "inputMode",
        "amount", "budget", "recordType", "transactionType", "entryType",
        "category", "date", "dateLabel", "range", "period", "timeRange",
        "month", "startDate", "endDate", "limit", "count", "description",
    )
    for (key in keys) {
        if (!merged.has(key) && has(key)) merged.put(key, opt(key))
    }
    return merged
}

private fun JSONObject.firstNonBlank(vararg names: String): String? {
    for (name in names) {
        val value = optString(name).notBlankOrNull()
        if (value != null) return value
    }
    return null
}

private fun String?.notBlankOrNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

private fun JSONObject.optNullableFloat(name: String): Float? {
    if (!has(name) || isNull(name)) return null
    return runCatching { optDouble(name).toFloat() }.getOrNull()
}

private fun JSONObject.optNullableLong(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    return runCatching { optLong(name) }.getOrNull()
}

private fun JSONObject.optTapCoordinateComponent(index: Int): Float? {
    val directNames = if (index == 0) {
        listOf("x", "centerX", "tapX", "targetX", "cx")
    } else {
        listOf("y", "centerY", "tapY", "targetY", "cy")
    }
    for (name in directNames) {
        optNullableFloat(name)?.let { return it }
    }
    val containers = listOf(
        "coordinate", "coordinates", "coord", "coords",
        "point", "position", "center", "tapPoint", "xy",
    )
    for (name in containers) {
        optJSONArray(name)?.let { array ->
            if (array.length() > index) {
                runCatching { array.optDouble(index).toFloat() }.getOrNull()?.let { return it }
            }
        }
        optJSONObject(name)?.let { obj ->
            for (directName in directNames) {
                obj.optNullableFloat(directName)?.let { return it }
            }
            obj.optNullableFloat(index.toString())?.let { return it }
        }
    }
    return null
}

private fun JSONObject.optFlexibleBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    return when (val raw = opt(name)) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> when (raw.lowercase().trim()) {
            "true", "yes", "1", "expected", "complete", "completed",
            "progress", "success", "wrong", "on", "enable", "enabled" -> true
            "false", "no", "0", "uncertain", "unknown", "",
            "off", "disable", "disabled" -> false
            else -> null
        }
        else -> null
    }
}
