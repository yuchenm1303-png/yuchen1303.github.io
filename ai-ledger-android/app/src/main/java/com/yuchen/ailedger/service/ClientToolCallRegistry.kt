package com.yuchen.ailedger.service

import java.util.WeakHashMap

/**
 * Short-lived mechanical correlation between a parsed cloud clientToolCall and its Android
 * executor. No user-language interpretation or local routing happens here.
 */
internal object ClientToolCallRegistry {
    private val calls = WeakHashMap<CloudAgentStep, CloudClientToolCall>()
    private val visualCalls = ArrayDeque<CloudClientToolCall>()

    fun attach(step: CloudAgentStep, call: CloudClientToolCall) {
        synchronized(calls) { calls[step] = call }
    }

    fun consume(step: CloudAgentStep?): CloudClientToolCall? {
        if (step == null) return null
        return synchronized(calls) { calls.remove(step) }
    }

    fun attachVisual(call: CloudClientToolCall) {
        if (call.name != "computer_run_task" || call.id.isBlank()) return
        synchronized(visualCalls) {
            visualCalls.removeAll { it.id == call.id }
            visualCalls.addLast(call)
            while (visualCalls.size > 12) visualCalls.removeFirst()
        }
    }

    fun consumeVisual(goal: String): CloudClientToolCall? {
        val cleanGoal = goal.trim()
        return synchronized(visualCalls) {
            val selected = visualCalls.lastOrNull { call ->
                call.arguments.optString("goal").trim() == cleanGoal ||
                    call.originalUserGoal?.trim() == cleanGoal
            } ?: visualCalls.lastOrNull()
            selected?.also { visualCalls.remove(it) }
        }
    }
}
