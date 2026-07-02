package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.AssistantMemoryDiagnostics
import com.yuchen.ailedger.data.AssistantMemoryMutationRuntime
import com.yuchen.ailedger.data.AssistantMemoryRepository
import org.json.JSONObject

/**
 * 云端负责真实 usage 与 mutation 记账；Android 只保存逐轮排障信息并同步管理页库存。
 *
 * HttpURLConnection 解析响应与成功回调发生在同一工作线程，因此用 ThreadLocal 暂存
 * 本轮最终 JSON，避免并发聊天之间互相串记录。记忆事务刷新会切到 Repository 自己的 IO scope，
 * 不阻塞当前响应解析线程。
 */
internal object AssistantMemoryUsageBridge {
    private val responseForCurrentThread = ThreadLocal<JSONObject?>()

    fun captureResponseJson(data: JSONObject) {
        val mutationReceipt = AssistantMemoryMutationRuntime.captureResponse(data)
        val appContext = AiLedgerApplication.contextOrNull()
        if (
            mutationReceipt != null &&
            appContext != null &&
            AssistantMemoryMutationRuntime.markInventoryRefreshNeeded(mutationReceipt)
        ) {
            AssistantMemoryRepository.get(appContext).refreshAfterCloudMutation(mutationReceipt)
        }

        val response = sequenceOf(
            data,
            data.optJSONObject("response"),
            data.optJSONObject("final"),
            data.optJSONObject("data"),
            data.optJSONObject("result"),
        ).filterNotNull().firstOrNull(::containsMemoryMetadata) ?: return
        responseForCurrentThread.set(response)
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

    private fun containsMemoryMetadata(data: JSONObject): Boolean {
        return data.has("memoryStatus") ||
            data.has("memoryUsed") ||
            data.has("memoryRequestId") ||
            data.has("memoryTrace")
    }
}
