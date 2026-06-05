package com.yuchen.ailedger.service

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
)

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

    companion object {
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
                appName = item.optString("appName").notBlankOrNull()
                    ?: item.optString("app").notBlankOrNull(),
                packageName = item.optString("packageName").notBlankOrNull()
                    ?: item.optString("package").notBlankOrNull(),
                x = item.optNullableFloat("x"),
                y = item.optNullableFloat("y"),
                durationMs = item.optNullableLong("durationMs") ?: item.optNullableLong("delayMs"),
            )
        }
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