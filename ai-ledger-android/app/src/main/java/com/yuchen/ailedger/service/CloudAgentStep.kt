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
                    it.has("isComplete") || it.has("complete") || it.has("isExpected") || it.has("expectedProgress") || it.has("isWrong")
                }
                ?: return null

            val complete = item.optFlexibleBoolean("isComplete")
                ?: item.optFlexibleBoolean("complete")
                ?: item.optFlexibleBoolean("completed")
                ?: item.optFlexibleBoolean("isExpected")
                ?: item.optFlexibleBoolean("expected")
                ?: false
            val progress = item.optFlexibleBoolean("expectedProgress")
                ?: item.optFlexibleBoolean("progress")
                ?: item.optFlexibleBoolean("isProgress")
                ?: item.optFlexibleBoolean("onRightTrack")
                ?: item.optFlexibleBoolean("closerToGoal")
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
                reason = item.optString("reason")
                    .notBlankOrNull()
                    ?: item.optString("explanation").notBlankOrNull()
                    ?: item.optString("rationale").notBlankOrNull()
                    ?: "",
                nextHint = item.optString("nextHint")
                    .notBlankOrNull()
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
            )
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
            val keys = listOf("agentSteps", "steps", "actionBatch", "actions")
            return containers.flatMap { container ->
                keys.flatMap { key -> container.optJSONArray(key).toAgentSteps() }
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
                listOf("stopConditions", "batchStopConditions", "replanOn").flatMap { key ->
                    container.optStringSet(key)
                }
            }.toSet()
        }

        private fun JSONArray?.toAgentSteps(): List<CloudAgentStep> {
            if (this == null) return emptyList()
            val result = mutableListOf<CloudAgentStep>()
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                CloudAgentStep.fromJson(item)?.let { result += it }
            }
            return result
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
) {
    val typeLabel: String
        get() = when (type) {
            "open_app" -> "打开应用"
            "tap_node" -> "点击节点"
            "tap_xy" -> "点击坐标"
            "input_text" -> "输入文字"
            "scroll" -> "滚动屏幕"
            "swipe" -> "滑动屏幕"
            "back" -> "返回"
            "home" -> "回到桌面"
            "recents" -> "打开最近任务"
            "notifications" -> "下拉通知栏"
            "quick_settings" -> "打开快捷设置"
            "wait" -> "等待"
            "finish" -> "任务完成"
            "need_user_help" -> "需要用户协助"
            else -> type
        }

    val shouldUseFocusedDirectInput: Boolean
        get() = inputMode?.lowercase()?.replace('-', '_') in focusedInputModes || useFocusedInput || expectsFocusedInput || !requiresInputNode

    companion object {
        private val focusedInputModes = setOf(
            "focused_direct",
            "focused",
            "direct",
            "keyboard",
            "ime",
            "active_input",
            "current_focus",
        )

        val supportedTypes = setOf(
            "open_app",
            "tap_node",
            "tap_xy",
            "input_text",
            "scroll",
            "swipe",
            "back",
            "home",
            "recents",
            "notifications",
            "quick_settings",
            "wait",
            "finish",
            "need_user_help",
        )

        fun fromJson(root: JSONObject?): CloudAgentStep? {
            val item = root?.optJSONObject("agentStep")
                ?: root?.optJSONObject("step")
                ?: root?.optJSONObject("agentAction")?.optJSONObject("step")
                ?: root?.optJSONObject("data")?.optJSONObject("agentStep")
                ?: root?.optJSONObject("data")?.optJSONObject("step")
                ?: root?.optJSONObject("result")?.optJSONObject("agentStep")
                ?: root?.takeIf { it.has("type") || it.has("action") }
                ?: return null
            val rawType = item.optString("type").notBlankOrNull()
                ?: item.optString("action").notBlankOrNull()
                ?: return null
            val normalizedType = rawType.lowercase().replace('-', '_')
            if (normalizedType !in supportedTypes) return null
            val parsedAppName = item.optString("appName").notBlankOrNull()
                ?: item.optString("app").notBlankOrNull()
            val parsedPackageName = item.optString("packageName").notBlankOrNull()
                ?: item.optString("package").notBlankOrNull()
            val parsedInputMode = item.optString("inputMode").notBlankOrNull()
                ?: item.optString("input_mode").notBlankOrNull()
                ?: item.optString("inputStrategy").notBlankOrNull()
                ?: item.optString("input_strategy").notBlankOrNull()
            val inputModeKey = parsedInputMode?.lowercase()?.replace('-', '_')
            val explicitUseFocusedInput = item.optFlexibleBoolean("useFocusedInput")
                ?: item.optFlexibleBoolean("use_focused_input")
                ?: item.optFlexibleBoolean("focusedDirect")
                ?: item.optFlexibleBoolean("focused_direct")
            val parsedExpectsFocusedInput = item.optFlexibleBoolean("expectsFocusedInput")
                ?: item.optFlexibleBoolean("expects_focused_input")
                ?: item.optFlexibleBoolean("focusedInput")
                ?: item.optFlexibleBoolean("focused_input")
                ?: false
            val inferredFocusedDirect = inputModeKey in focusedInputModes || explicitUseFocusedInput == true || parsedExpectsFocusedInput
            val parsedRequiresInputNode = item.optFlexibleBoolean("requiresInputNode")
                ?: item.optFlexibleBoolean("requires_input_node")
                ?: item.optFlexibleBoolean("inputNodeRequired")
                ?: item.optFlexibleBoolean("input_node_required")
                ?: !inferredFocusedDirect
            return CloudAgentStep(
                type = normalizedType,
                targetNodeId = item.optString("targetNodeId").notBlankOrNull()
                    ?: item.optString("nodeId").notBlankOrNull()
                    ?: item.optString("targetId").notBlankOrNull(),
                targetText = item.optString("targetText").notBlankOrNull()
                    ?: item.optString("label").notBlankOrNull(),
                text = item.optString("text").notBlankOrNull()
                    ?: item.optString("inputText").notBlankOrNull(),
                direction = item.optString("direction").notBlankOrNull()?.lowercase(),
                reason = item.optString("reason").notBlankOrNull()
                    ?: item.optString("rationale").notBlankOrNull(),
                riskLevel = item.optString("riskLevel").notBlankOrNull()?.lowercase()?.replace('-', '_') ?: "low",
                requiresConfirmation = item.optBoolean("requiresConfirmation", false),
                appName = parsedAppName,
                packageName = if (normalizedType == "open_app" && !parsedAppName.isNullOrBlank()) null else parsedPackageName,
                x = item.optNullableFloat("x"),
                y = item.optNullableFloat("y"),
                durationMs = item.optNullableLong("durationMs") ?: item.optNullableLong("delayMs"),
                inputMode = parsedInputMode,
                requiresInputNode = parsedRequiresInputNode,
                expectsFocusedInput = parsedExpectsFocusedInput,
                useFocusedInput = explicitUseFocusedInput ?: inferredFocusedDirect,
            )
        }
    }
}

private fun CloudAgentStep.batchKey(): String = buildString {
    append(type)
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
}

private fun JSONObject.optStringSet(name: String): Set<String> {
    if (!has(name) || isNull(name)) return emptySet()
    val raw = opt(name)
    return when (raw) {
        is JSONArray -> mutableSetOf<String>().apply {
            for (i in 0 until raw.length()) {
                raw.optString(i).notBlankOrNull()?.let { add(it.lowercase().replace('-', '_')) }
            }
        }
        is String -> raw.split(',', ';', '|')
            .mapNotNull { it.notBlankOrNull()?.lowercase()?.replace('-', '_') }
            .toSet()
        else -> emptySet()
    }
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

private fun JSONObject.optFlexibleBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    val raw = opt(name)
    return when (raw) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> when (raw.lowercase().trim()) {
            "true", "yes", "1", "expected", "complete", "completed", "progress", "success", "wrong" -> true
            "false", "no", "0", "uncertain", "unknown", "" -> false
            else -> null
        }
        else -> null
    }
}
