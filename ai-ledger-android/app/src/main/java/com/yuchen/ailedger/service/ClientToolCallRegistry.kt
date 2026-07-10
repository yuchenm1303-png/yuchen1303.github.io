package com.yuchen.ailedger.service

import java.util.WeakHashMap

/**
 * Keeps only the short-lived association required by structured device-control steps.
 * Visual computer calls are bound to the exact AiWorkerClient response and never enter a process-global registry.
 */
internal object ClientToolCallRegistry {
    private val calls = WeakHashMap<CloudAgentStep, CloudClientToolCall>()

    fun attach(step: CloudAgentStep, call: CloudClientToolCall) {
        synchronized(calls) { calls[step] = call }
    }

    fun consume(step: CloudAgentStep?): CloudClientToolCall? {
        if (step == null) return null
        return synchronized(calls) { calls.remove(step) }
    }
}
