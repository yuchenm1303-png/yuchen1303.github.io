package com.yuchen.ailedger.service

import org.json.JSONObject

data class CloudAgentStep(
    val type: String,
    val targetNodeId: String? = null,
    val targetText: String? = null,
    val text: String? = null,
    val direction: String? = null,
    val reason: String? = null,
    val riskLevel: String = "low",
    val requiresConfirmation: Boolean = false,
) {
    val typeLabel: String
        get() = when (type) {
            "tap_node" -> "点击节点"
            "input_text" -> "输入文字"
            "scroll" -> "滚动屏幕"
            "back" -> "返回"
            "finish" -> "任务完成"
            "need_user_help" -> "需要用户协助"
            else -> type
        }

    companion object {
        val supportedTypes = setOf(
            "tap_node",
            "input_text",
            "scroll",
            "back",
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
                direction = item.optString("direction").notBlankOrNull(),
                reason = item.optString("reason").notBlankOrNull()
                    ?: item.optString("rationale").notBlankOrNull(),
                riskLevel = item.optString("riskLevel").notBlankOrNull()?.lowercase()?.replace('-', '_') ?: "low",
                requiresConfirmation = item.optBoolean("requiresConfirmation", false),
            )
        }
    }
}

private fun String?.notBlankOrNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }
