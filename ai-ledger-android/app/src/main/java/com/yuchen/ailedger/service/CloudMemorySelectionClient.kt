package com.yuchen.ailedger.service

import com.yuchen.ailedger.data.AssistantMemoryState
import java.net.SocketTimeoutException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.min

private const val CLOUD_MEMORY_MAX_REDUCTION_ROUNDS = 6
private const val CLOUD_MEMORY_MAX_PARALLEL_CALLS = 4

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
            reduceWithCloudModels(prompt, candidates)
        } catch (error: Throwable) {
            val cause = generateSequence(error) { it.cause }.firstOrNull { it is SocketTimeoutException }
            CloudMemorySelectionResult(
                status = "unavailable",
                errorCode = if (cause != null) "cloud_selector_timeout" else "cloud_selector_failed",
            )
        }
    }

    private fun reduceWithCloudModels(
        userText: String,
        originalCandidates: List<CloudMemoryCandidate>,
    ): CloudMemorySelectionResult {
        var current = originalCandidates.distinctBy { it.transportId }
        var suppressed = 0
        var round = 1

        while (round <= CLOUD_MEMORY_MAX_REDUCTION_ROUNDS) {
            val batches = buildCloudMemoryBatches(current)
            if (batches.isEmpty()) {
                return CloudMemorySelectionResult("empty", suppressedCount = suppressed)
            }
            if (batches.size == 1) {
                val finalResult = CloudMemorySelectionTransport.select(
                    userText = userText,
                    candidates = batches.single(),
                    phase = "final_selection_round_$round",
                    selectionLimit = CLOUD_MEMORY_FINAL_SELECTION_LIMIT,
                )
                return if (!finalResult.successful) {
                    finalResult
                } else {
                    finalResult.copy(suppressedCount = suppressed + finalResult.suppressedCount)
                }
            }

            val results = selectBatchesInParallel(
                userText = userText,
                batches = batches,
                round = round,
            )
            val failed = results.firstOrNull { !it.successful }
            if (failed != null) return failed
            suppressed += results.sumOf { it.suppressedCount }

            val reduced = results
                .flatMap { it.selections }
                .map { it.candidate }
                .distinctBy { it.transportId }
            if (reduced.isEmpty()) {
                return CloudMemorySelectionResult("empty", suppressedCount = suppressed)
            }
            if (reduced.size >= current.size) {
                return CloudMemorySelectionResult(
                    status = "unavailable",
                    errorCode = "cloud_selector_non_convergent",
                )
            }
            current = reduced
            round += 1
        }

        return CloudMemorySelectionResult(
            status = "unavailable",
            errorCode = "cloud_selector_round_limit",
        )
    }

    private fun selectBatchesInParallel(
        userText: String,
        batches: List<List<CloudMemoryCandidate>>,
        round: Int,
    ): List<CloudMemorySelectionResult> {
        val workerCount = min(CLOUD_MEMORY_MAX_PARALLEL_CALLS, batches.size).coerceAtLeast(1)
        val executor = Executors.newFixedThreadPool(workerCount)
        return try {
            val tasks = batches.mapIndexed { index, batch ->
                Callable {
                    CloudMemorySelectionTransport.select(
                        userText = userText,
                        candidates = batch,
                        phase = "reduction_round_${round}_batch_${index + 1}_of_${batches.size}",
                        selectionLimit = CLOUD_MEMORY_REDUCTION_SELECTION_LIMIT,
                    )
                }
            }
            executor.invokeAll(tasks).map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }
}
