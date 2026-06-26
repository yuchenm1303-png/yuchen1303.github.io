package com.yuchen.ailedger

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

internal sealed interface NativeAgentStepPollResult {
    object Missing : NativeAgentStepPollResult
    object Pending : NativeAgentStepPollResult
    data class Ready(val payload: String) : NativeAgentStepPollResult
}

/** Small bounded store for the WebView bridge's asynchronous agent-step protocol. */
internal class NativeAgentStepResultStore(
    private val maxEntries: Int = 16,
) {
    private val lock = Any()
    private val sequence = AtomicLong(0L)
    private val entries = linkedMapOf<String, String?>()
    private val order = ArrayDeque<String>()

    fun createRequestId(preferred: String?): String {
        val clean = preferred.orEmpty()
            .trim()
            .replace(REQUEST_ID_INVALID_CHARS, "-")
            .trim('-')
            .take(MAX_REQUEST_ID_CHARS)
        return clean.ifBlank { "agent-step-${System.currentTimeMillis()}-${sequence.incrementAndGet()}" }
    }

    fun start(requestId: String) {
        synchronized(lock) {
            entries.remove(requestId)
            order.remove(requestId)
            entries[requestId] = null
            order.addLast(requestId)
            trimLocked()
        }
    }

    fun complete(requestId: String, payload: String) {
        synchronized(lock) {
            if (!entries.containsKey(requestId)) {
                entries[requestId] = payload
                order.addLast(requestId)
                trimLocked()
                return
            }
            entries[requestId] = payload
        }
    }

    fun poll(requestId: String, consumeReady: Boolean = true): NativeAgentStepPollResult {
        synchronized(lock) {
            if (!entries.containsKey(requestId)) return NativeAgentStepPollResult.Missing
            val payload = entries[requestId] ?: return NativeAgentStepPollResult.Pending
            if (consumeReady) {
                entries.remove(requestId)
                order.remove(requestId)
            }
            return NativeAgentStepPollResult.Ready(payload)
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            order.clear()
        }
    }

    private fun trimLocked() {
        while (order.size > maxEntries.coerceAtLeast(1)) {
            entries.remove(order.removeFirst())
        }
    }

    companion object {
        private const val MAX_REQUEST_ID_CHARS = 80
        private val REQUEST_ID_INVALID_CHARS = Regex("[^A-Za-z0-9._:-]+")
    }
}
