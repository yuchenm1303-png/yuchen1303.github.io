package com.yuchen.ailedger

import org.json.JSONArray
import org.json.JSONTokener

data class NativeChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val action: String = "chat",
    val mobileTitle: String = "",
    val mobileSummary: String = "",
)

fun initialNativeChatMessages(): List<NativeChatMessage> = listOf(
    NativeChatMessage(
        id = "welcome",
        role = "assistant",
        content = "你好，我是你的 AI 助手。你可以让我记账、设置提醒、打开应用和导航。",
    ),
)

fun parseNativeChatMessages(rawEvaluateResult: String?): List<NativeChatMessage> {
    if (rawEvaluateResult.isNullOrBlank() || rawEvaluateResult == "null") return initialNativeChatMessages()
    val storageText = runCatching {
        val value = JSONTokener(rawEvaluateResult).nextValue()
        value as? String ?: rawEvaluateResult
    }.getOrElse { rawEvaluateResult }

    val parsed = runCatching { JSONArray(storageText) }.getOrNull() ?: return initialNativeChatMessages()
    val messages = buildList {
        for (index in 0 until parsed.length()) {
            val item = parsed.optJSONObject(index) ?: continue
            val role = item.optString("role", "assistant")
            if (role != "user" && role != "assistant") continue
            val mobileCommand = item.optJSONObject("mobileCommand")
            add(
                NativeChatMessage(
                    id = item.optString("id", "msg-$index"),
                    role = role,
                    content = item.optString("content", ""),
                    action = item.optString("action", "chat"),
                    mobileTitle = mobileCommand?.optString("title", "") ?: "",
                    mobileSummary = mobileCommand?.optString("summary", "") ?: "",
                ),
            )
        }
    }
    return messages.ifEmpty { initialNativeChatMessages() }
}
