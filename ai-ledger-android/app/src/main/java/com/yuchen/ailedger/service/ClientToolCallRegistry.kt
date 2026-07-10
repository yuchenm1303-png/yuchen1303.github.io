package com.yuchen.ailedger.service

import java.util.WeakHashMap

internal object ClientToolCallRegistry {
    private const val VISUAL_CALL_TTL_MS = 30_000L
    private const val MAX_PENDING_VISUAL_CALLS = 8

    private data class PendingVisualCall(
        val call: CloudClientToolCall,
        val goalKey: String,
        val registeredAt: Long,
    )

    private val calls = WeakHashMap<CloudAgentStep, CloudClientToolCall>()
    private val visualLock = Any()
    private val pendingVisualCalls = mutableListOf<PendingVisualCall>()

    fun attach(step: CloudAgentStep, call: CloudClientToolCall) {
        synchronized(calls) { calls[step] = call }
    }

    fun consume(step: CloudAgentStep?): CloudClientToolCall? {
        if (step == null) return null
        return synchronized(calls) { calls.remove(step) }
    }

    fun attachVisual(call: CloudClientToolCall) {
        if (call.name != "computer_run_task" || call.id.isBlank()) return
        val now = System.currentTimeMillis()
        val pending = PendingVisualCall(
            call = call,
            goalKey = call.visualGoalKey(),
            registeredAt = now,
        )
        synchronized(visualLock) {
            pruneExpiredVisualCalls(now)
            pendingVisualCalls.removeAll { it.call.id == call.id }
            pendingVisualCalls += pending
            while (pendingVisualCalls.size > MAX_PENDING_VISUAL_CALLS) {
                pendingVisualCalls.removeAt(0)
            }
        }
    }

    /**
     * Consumes only the visual call that belongs to the current user goal. When several calls are
     * pending and no exact goal match exists, nothing is consumed: executing no call is safer than
     * attaching a result to another Final Model workspace.
     */
    fun consumeVisual(goal: String? = null): CloudClientToolCall? = synchronized(visualLock) {
        val now = System.currentTimeMillis()
        pruneExpiredVisualCalls(now)
        val requestedGoalKey = normalizeVisualGoal(goal.orEmpty())
        val matchIndex = when {
            requestedGoalKey.isNotBlank() -> pendingVisualCalls.indexOfLast { it.goalKey == requestedGoalKey }
            pendingVisualCalls.size == 1 -> 0
            else -> -1
        }
        if (matchIndex < 0) return@synchronized null

        val selected = pendingVisualCalls.removeAt(matchIndex)
        if (selected.goalKey.isNotBlank()) {
            pendingVisualCalls.removeAll { it.goalKey == selected.goalKey }
        }
        selected.call
    }

    fun clearVisual(callId: String? = null) {
        synchronized(visualLock) {
            if (callId.isNullOrBlank()) {
                pendingVisualCalls.clear()
            } else {
                pendingVisualCalls.removeAll { it.call.id == callId }
            }
        }
    }

    private fun pruneExpiredVisualCalls(now: Long) {
        pendingVisualCalls.removeAll { now - it.registeredAt > VISUAL_CALL_TTL_MS }
    }

    private fun CloudClientToolCall.visualGoalKey(): String {
        val toolGoal = arguments.optString("goal").trim()
        return normalizeVisualGoal(toolGoal.ifBlank { originalUserGoal.orEmpty() })
    }

    private fun normalizeVisualGoal(value: String): String {
        return value
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), "")
            .take(300)
    }
}
