package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.AssistantMemoryRepository
import org.json.JSONObject

internal object AssistantMemoryUsageBridge {
    private const val MAX_RECORDED_IDS = 24

    fun recordSuccessfulPayload(payload: JSONObject) {
        val ids = selectedIdsFromPayload(payload)
        if (ids.isEmpty()) return
        val context = AiLedgerApplication.contextOrNull() ?: return
        AssistantMemoryRepository.get(context).recordSuccessfulUsage(ids)
    }

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
