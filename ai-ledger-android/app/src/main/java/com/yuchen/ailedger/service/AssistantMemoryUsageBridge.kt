package com.yuchen.ailedger.service

import org.json.JSONObject

/**
 * V4 记忆使用记录由云端在 model_injected 与 answer_completed 阶段原子写入。
 * Android 只保留请求诊断 ID 的纯解析能力，不再根据请求前候选重复写入旧 usage。
 */
internal object AssistantMemoryUsageBridge {
    private const val MAX_RECORDED_IDS = 24

    fun recordSuccessfulPayload(payload: JSONObject) = Unit

    internal fun selectedIdsFromPayload(payload: JSONObject): List<String> {
        val array = payload
            .optJSONObject("memoryContextDiagnostics")
            ?.optJSONArray("selectedMemoryIds")
            ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val id = array.optString(index).trim()
                if (id.isNotBlank() && id !in this) add(id)
                if (size >= MAX_RECORDED_IDS) break
            }
        }
    }
}
