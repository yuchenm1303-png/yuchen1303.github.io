package com.yuchen.ailedger.data

import org.json.JSONObject

private object AssistantMemoryDiagnosticsAccountGate {
    private val lock = Any()
    private var activeTicket: AssistantMemorySessionTicket? = null

    fun switchAccount(
        diagnostics: AssistantMemoryDiagnostics,
        ticket: AssistantMemorySessionTicket?,
    ) {
        val normalized = ticket?.takeIf(AssistantAccountSessionRuntime::isCurrent)
        val changed = synchronized(lock) {
            if (activeTicket == normalized) return@synchronized false
            activeTicket = normalized
            true
        }
        if (changed) diagnostics.clear()
    }

    fun accepts(ticket: AssistantMemorySessionTicket): Boolean = synchronized(lock) {
        activeTicket == ticket && AssistantAccountSessionRuntime.isCurrent(ticket)
    }
}

internal fun AssistantMemoryDiagnostics.switchAccount(ticket: AssistantMemorySessionTicket?) {
    AssistantMemoryDiagnosticsAccountGate.switchAccount(this, ticket)
}

internal fun AssistantMemoryDiagnostics.record(
    ticket: AssistantMemorySessionTicket,
    payload: JSONObject,
    response: JSONObject?,
    failure: Throwable? = null,
) {
    if (!AssistantMemoryDiagnosticsAccountGate.accepts(ticket)) return
    record(payload = payload, response = response, failure = failure)
}
