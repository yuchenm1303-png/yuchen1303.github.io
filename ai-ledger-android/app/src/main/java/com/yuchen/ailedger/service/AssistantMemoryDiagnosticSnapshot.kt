package com.yuchen.ailedger.service

import org.json.JSONObject

private val MEMORY_DIAGNOSTIC_PAYLOAD_FIELDS = arrayOf(
    "requestId",
    "message",
    "prompt",
    "model",
    "memoryMode",
    "memoryEnabled",
    "memorySchema",
)

private val MEMORY_DIAGNOSTIC_RESPONSE_FIELDS = arrayOf(
    "memoryTrace",
    "memoryStageTimings",
    "memoryMutation",
    "memoryMutationStageTimings",
    "memorySelectedItems",
    "memoryRequestId",
    "memoryError",
    "reply",
    "response",
    "model",
    "version",
    "memoryStatus",
    "memoryUsed",
    "memorySource",
    "memoryDegraded",
    "memoryItemCount",
    "memoryGateStatus",
    "memoryBudgetLevel",
    "memoryRetrievalStatus",
    "memoryEmbeddingStatus",
    "memoryRerankStatus",
    "memoryCandidateCount",
    "memoryAnchorCandidateCount",
    "memoryDynamicCandidateCount",
    "memoryFilteredHistoryCount",
    "memoryFilteredSensitiveCount",
    "memoryTotalMs",
    "memoryMutationRequested",
    "memoryMutationHandled",
    "memoryMutationAction",
    "memoryMutationStatus",
    "memoryMutationApplied",
    "memoryMutationOperationId",
    "memoryMutationAffectedCount",
    "memoryMutationIdempotentReplay",
    "memoryMutationRequiresClarification",
    "memoryMutationTrigger",
    "memoryMutationRouterStatus",
    "memoryMutationTotalMs",
    "memoryMutationError",
)

internal fun compactMemoryDiagnosticPayload(source: JSONObject): JSONObject =
    source.copyMemoryDiagnosticFields(MEMORY_DIAGNOSTIC_PAYLOAD_FIELDS)

internal fun compactMemoryDiagnosticResponse(source: JSONObject): JSONObject =
    source.copyMemoryDiagnosticFields(MEMORY_DIAGNOSTIC_RESPONSE_FIELDS)

private fun JSONObject.copyMemoryDiagnosticFields(fields: Array<String>): JSONObject = JSONObject().apply {
    for (field in fields) {
        if (this@copyMemoryDiagnosticFields.has(field)) {
            put(field, this@copyMemoryDiagnosticFields.opt(field))
        }
    }
}
