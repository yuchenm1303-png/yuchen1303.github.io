package com.yuchen.ailedger.service

import com.yuchen.ailedger.data.AssistantMemoryDiagnostics
import org.json.JSONObject

/**
 * 云端负责真实 usage 记账；Android 只保存可复制的逐轮排障信息。
 *
 * HttpURLConnection 解析响应与成功回调发生在同一工作线程，因此用 ThreadLocal 暂存
 * 本轮最终 JSON，避免并发聊天之间互相串记录。这里只接受包含记忆元数据的响应，
 * 不保存登录令牌、请求头、图片或完整消息列表。
 */
internal object AssistantMemoryUsageBridge {
    private val responseForCurrentThread = ThreadLocal<JSONObject?>()

    fun captureResponseJson(data: JSONObject) {
        if (
            data.has("memoryStatus") ||
            data.has("memoryUsed") ||
            data.has("memoryRequestId") ||
            data.has("memoryTrace")
        ) {
            responseForCurrentThread.set(data)
        }
    }

    fun recordSuccessfulPayload(payload: JSONObject) {
        val response = responseForCurrentThread.get()
        responseForCurrentThread.remove()
        AssistantMemoryDiagnostics.record(payload = payload, response = response)
    }

    fun recordFailedPayload(payload: JSONObject, error: Throwable) {
        responseForCurrentThread.remove()
        AssistantMemoryDiagnostics.record(payload = payload, response = null, failure = error)
    }
}
