package com.yuchen.ailedger.data

import java.util.LinkedHashSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val MEMORY_MUTATION_RECEIPT_SCHEMA = "ai_ledger_memory_mutation_receipt_v1"
private const val MEMORY_MUTATION_REFRESH_DEDUP_LIMIT = 96

data class AssistantMemoryMutationCandidate(
    val id: String = "",
    val content: String = "",
    val layer: String = "",
    val namespaceType: String = "",
    val namespaceId: String = "",
    val subjectKey: String = "",
    val conflictKey: String = "",
    val updatedAt: String = "",
    val retrievalScore: Double = 0.0,
)

data class AssistantMemoryMutationReceipt(
    val schema: String = MEMORY_MUTATION_RECEIPT_SCHEMA,
    val operationId: String = "",
    val requestId: String = "",
    val action: String = "none",
    val status: String = "not_requested",
    val applied: Boolean = false,
    val idempotentReplay: Boolean = false,
    val affectedCount: Int = 0,
    val targetIds: List<String> = emptyList(),
    val resultIds: List<String> = emptyList(),
    val summary: String = "",
    val requiresClarification: Boolean = false,
    val candidates: List<AssistantMemoryMutationCandidate> = emptyList(),
    val error: String = "",
) {
    val succeeded: Boolean
        get() = status == "applied" || status == "noop"

    val inventoryMayHaveChanged: Boolean
        get() = status == "applied" && (applied || idempotentReplay)

    val shouldRefreshAfterConflict: Boolean
        get() = status == "conflict" || status == "not_found"

    fun userFacingMessage(): String {
        if (summary.isNotBlank()) return summary
        return when (status) {
            "applied" -> when (action) {
                "upsert" -> "记忆已保存。"
                "delete" -> "记忆已删除。"
                "archive" -> "记忆已归档。"
                "restore" -> "记忆已恢复。"
                "clear" -> "全部长期记忆已清除。"
                else -> "记忆操作已完成。"
            }
            "noop" -> "记忆内容没有变化，无需重复保存。"
            "conflict" -> "这条记忆已在其他设备上发生变化，已重新同步，请确认后再试。"
            "not_found" -> "这条记忆已不存在，列表已重新同步。"
            "confirmation_required" -> "该记忆操作还需要明确确认。"
            "operation_id_conflict" -> "记忆操作标识发生冲突，请重新发起。"
            "blocked_forget_tombstone" -> "这条内容已被明确遗忘，未重新写入。"
            else -> error.ifBlank { "记忆操作未完成，请稍后再试。" }
        }
    }
}

data class AssistantMemoryMutationRuntimeState(
    val latestReceipt: AssistantMemoryMutationReceipt? = null,
    val updatedAtMillis: Long = 0L,
)

/**
 * 接收聊天最终响应里的云端记忆事务回执。
 *
 * 这里只保存真实回执并为同一 operationId 去重刷新信号。
 * 不在 Android 端重新判断自然语言记忆意图，也不修改云端事务结论。
 */
object AssistantMemoryMutationRuntime {
    private val lock = Any()
    private val refreshedOperationIds = LinkedHashSet<String>()
    private val mutableState = MutableStateFlow(AssistantMemoryMutationRuntimeState())
    val state: StateFlow<AssistantMemoryMutationRuntimeState> = mutableState.asStateFlow()

    fun captureResponse(response: JSONObject): AssistantMemoryMutationReceipt? {
        val receipt = response.findAssistantMemoryMutationReceipt() ?: return null
        mutableState.value = AssistantMemoryMutationRuntimeState(
            latestReceipt = receipt,
            updatedAtMillis = System.currentTimeMillis(),
        )
        return receipt
    }

    fun markInventoryRefreshNeeded(receipt: AssistantMemoryMutationReceipt): Boolean {
        if (!receipt.inventoryMayHaveChanged || receipt.operationId.isBlank()) return false
        return markRefreshNeeded(receipt.operationId)
    }

    private fun markRefreshNeeded(operationId: String): Boolean = synchronized(lock) {
        if (!refreshedOperationIds.add(operationId)) return@synchronized false
        while (refreshedOperationIds.size > MEMORY_MUTATION_REFRESH_DEDUP_LIMIT) {
            val iterator = refreshedOperationIds.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
        true
    }
}

internal fun JSONObject.findAssistantMemoryMutationReceipt(): AssistantMemoryMutationReceipt? {
    val envelopes = sequenceOf(
        this,
        optJSONObject("response"),
        optJSONObject("final"),
        optJSONObject("data"),
        optJSONObject("result"),
    ).filterNotNull()

    for (envelope in envelopes) {
        envelope.optJSONObject("memoryMutation")
            ?.toAssistantMemoryMutationReceiptOrNull(envelope)
            ?.let { return it }

        if (
            envelope.has("memoryMutationStatus") ||
            envelope.has("memoryMutationOperationId") ||
            envelope.has("memoryMutationApplied")
        ) {
            return JSONObject().apply {
                put("schema", MEMORY_MUTATION_RECEIPT_SCHEMA)
                put("operationId", envelope.optString("memoryMutationOperationId"))
                put("requestId", envelope.optString("memoryRequestId"))
                put("action", envelope.optString("memoryMutationAction"))
                put("status", envelope.optString("memoryMutationStatus"))
                put("applied", envelope.optBoolean("memoryMutationApplied", false))
                put("idempotentReplay", envelope.optBoolean("memoryMutationIdempotentReplay", false))
                put("affectedCount", envelope.optInt("memoryMutationAffectedCount", 0))
                put("requiresClarification", envelope.optBoolean("memoryMutationRequiresClarification", false))
                put("error", envelope.optString("memoryMutationError"))
            }.toAssistantMemoryMutationReceiptOrNull(envelope)
        }
    }
    return null
}

internal fun JSONObject.toAssistantMemoryMutationReceiptOrNull(
    envelope: JSONObject? = null,
): AssistantMemoryMutationReceipt? {
    val action = optString("action").trim().lowercase().replace('-', '_')
    val status = optString("status").trim().lowercase().replace('-', '_')
    val operationId = optString("operationId")
        .ifBlank { optString("operation_id") }
        .trim()
        .take(180)
    val hasReceiptSignal = operationId.isNotBlank() || action.isNotBlank() || status.isNotBlank()
    if (!hasReceiptSignal) return null

    return AssistantMemoryMutationReceipt(
        schema = optString("schema").trim().ifBlank { MEMORY_MUTATION_RECEIPT_SCHEMA },
        operationId = operationId,
        requestId = optString("requestId")
            .ifBlank { optString("request_id") }
            .ifBlank { envelope?.optString("memoryRequestId").orEmpty() }
            .trim()
            .take(180),
        action = action.ifBlank { "none" },
        status = status.ifBlank { "unknown" },
        applied = optBoolean("applied", false),
        idempotentReplay = optBoolean("idempotentReplay", optBoolean("idempotent_replay", false)),
        affectedCount = optInt("affectedCount", optInt("affected_count", 0)).coerceAtLeast(0),
        targetIds = (optJSONArray("targetIds") ?: optJSONArray("target_ids")).toStringList(24, 80),
        resultIds = (optJSONArray("resultIds") ?: optJSONArray("result_ids")).toStringList(24, 80),
        summary = optString("summary").trim().take(500),
        requiresClarification = optBoolean(
            "requiresClarification",
            envelope?.optBoolean("memoryMutationRequiresClarification", false) == true,
        ),
        candidates = optJSONArray("candidates").toMutationCandidates(),
        error = optString("error")
            .ifBlank { envelope?.optString("memoryMutationError").orEmpty() }
            .trim()
            .take(300),
    )
}

private fun JSONArray?.toStringList(limit: Int, maxChars: Int): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim().take(maxChars)
            if (value.isNotBlank() && value !in this) add(value)
            if (size >= limit) break
        }
    }
}

private fun JSONArray?.toMutationCandidates(): List<AssistantMemoryMutationCandidate> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val id = item.optString("id").trim().take(80)
            if (id.isBlank()) continue
            add(
                AssistantMemoryMutationCandidate(
                    id = id,
                    content = item.optString("content").trim().take(320),
                    layer = item.optString("layer").trim().take(60),
                    namespaceType = item.optString("namespaceType")
                        .ifBlank { item.optString("namespace_type") }
                        .trim()
                        .take(60),
                    namespaceId = item.optString("namespaceId")
                        .ifBlank { item.optString("namespace_id") }
                        .trim()
                        .take(180),
                    subjectKey = item.optString("subjectKey")
                        .ifBlank { item.optString("subject_key") }
                        .trim()
                        .take(160),
                    conflictKey = item.optString("conflictKey")
                        .ifBlank { item.optString("conflict_key") }
                        .trim()
                        .take(160),
                    updatedAt = item.optString("updatedAt")
                        .ifBlank { item.optString("updated_at") }
                        .trim()
                        .take(64),
                    retrievalScore = item.optDouble("retrievalScore", item.optDouble("retrieval_score", 0.0))
                        .takeIf(Double::isFinite)
                        ?.coerceAtLeast(0.0)
                        ?: 0.0,
                ),
            )
            if (size >= 8) break
        }
    }
}
