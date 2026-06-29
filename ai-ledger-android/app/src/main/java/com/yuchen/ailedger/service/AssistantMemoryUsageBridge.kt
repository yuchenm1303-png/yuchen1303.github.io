package com.yuchen.ailedger.service

import org.json.JSONObject

/**
 * V4 记忆使用记录完全由云端根据真实注入阶段写入。
 * Android 不解析选中 ID、不推断使用结果，也不向旧链重复记账。
 */
internal object AssistantMemoryUsageBridge {
    fun recordSuccessfulPayload(@Suppress("UNUSED_PARAMETER") payload: JSONObject) = Unit
}
