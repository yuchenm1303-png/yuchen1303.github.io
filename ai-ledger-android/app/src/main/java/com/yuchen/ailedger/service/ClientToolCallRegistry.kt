package com.yuchen.ailedger.service

import java.util.WeakHashMap

internal object ClientToolCallRegistry {
    private const val VISUAL_CALL_TTL_MS = 30_000L

    private data class PendingVisualCall(
        val call: CloudClientToolCall,
        val registeredAt: Long,
    )

    private val calls = WeakHashMap<CloudAgentStep, CloudClientToolCall>()
    private val visualLock = Any()
    private var pendingVisualCall: PendingVisualCall? = null

    fun attach(step: CloudAgentStep, call: CloudClientToolCall) {
        synchronized(calls) { calls[step] = call }
    }

    fun consume(step: CloudAgentStep?): CloudClientToolCall? {
        if (step == null) return null
        return synchronized(calls) { calls.remove(step) }
    }

    fun attachVisual(call: CloudClientToolCall) {
        if (call.name != "computer_run_task" || call.id.isBlank()) return
        synchronized(visualLock) {
            pendingVisualCall = PendingVisualCall(call, System.currentTimeMillis())
        }
    }

    fun consumeVisual(): CloudClientToolCall? = synchronized(visualLock) {
        val pending = pendingVisualCall
        pendingVisualCall = null
        pending
            ?.takeIf { System.currentTimeMillis() - it.registeredAt <= VISUAL_CALL_TTL_MS }
            ?.call
    }

    fun clearVisual(callId: String? = null) {
        synchronized(visualLock) {
            val pending = pendingVisualCall ?: return
            if (callId.isNullOrBlank() || pending.call.id == callId) pendingVisualCall = null
        }
    }
}
