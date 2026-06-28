package com.yuchen.ailedger.service

import com.yuchen.ailedger.data.AssistantMemoryState
import java.net.SocketTimeoutException

internal object CloudMemorySelectionClient {
    fun select(
        userText: String,
        customInstructions: String?,
        memoryState: AssistantMemoryState,
        nowMillis: Long = System.currentTimeMillis(),
    ): CloudMemorySelectionResult {
        val prompt = userText.trim()
        if (prompt.isBlank()) return CloudMemorySelectionResult("empty")
        val candidates = buildCloudMemoryCandidates(customInstructions, memoryState, nowMillis)
        if (candidates.isEmpty()) return CloudMemorySelectionResult("empty")

        return try {
            val batches = buildCloudMemoryBatches(candidates)
            val firstPass = mutableListOf<CloudSelectedMemory>()
            var suppressed = 0
            batches.forEachIndexed { index, batch ->
                val result = CloudMemorySelectionTransport.select(
                    userText = prompt,
                    candidates = batch,
                    phase = "candidate_batch_${index + 1}_of_${batches.size}",
                )
                if (!result.successful) return result
                firstPass += result.selections
                suppressed += result.suppressedCount
            }

            val selected = firstPass.distinctBy { it.candidate.transportId }
            if (selected.isEmpty()) {
                return CloudMemorySelectionResult("empty", suppressedCount = suppressed)
            }
            if (batches.size == 1) {
                return CloudMemorySelectionResult(
                    status = "selected",
                    selections = selected,
                    suppressedCount = suppressed,
                )
            }

            val consolidated = CloudMemorySelectionTransport.select(
                userText = prompt,
                candidates = selected.map { it.candidate },
                phase = "global_consolidation",
            )
            if (!consolidated.successful) {
                CloudMemorySelectionResult(
                    status = "unavailable",
                    errorCode = consolidated.errorCode.ifBlank { "cloud_consolidation_failed" },
                )
            } else {
                CloudMemorySelectionResult(
                    status = if (consolidated.selections.isEmpty()) "empty" else "selected",
                    selections = consolidated.selections,
                    suppressedCount = suppressed + consolidated.suppressedCount,
                )
            }
        } catch (error: Throwable) {
            CloudMemorySelectionResult(
                status = "unavailable",
                errorCode = if (error is SocketTimeoutException) {
                    "cloud_selector_timeout"
                } else {
                    "cloud_selector_failed"
                },
            )
        }
    }
}
