package com.yuchen.ailedger.service

import org.json.JSONObject

/**
 * V4 记忆使用记录由云端在 model_injected 与 answer_completed 阶段原子写入。
 * Android 不再根据请求前的本地候选重复记账，避免 V3/V4 双轨和计数漂移。
 */
internal object AssistantMemoryUsageBridge {
    fun recordSuccessfulPayload(payload: JSONObject) = Unit

    internal fun selectedIdsFromPayload(payload: JSONObject): List<String> = emptyList()
}
