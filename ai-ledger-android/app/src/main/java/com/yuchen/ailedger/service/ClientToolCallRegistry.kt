package com.yuchen.ailedger.service

import java.util.WeakHashMap

/**
 * Short-lived mechanical correlation between a parsed cloud clientToolCall and its typed step.
 * The executor still receives only canonical tool arguments; the envelope is consumed solely when
 * Android sends the verified result back to the same Final Chat Model.
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
