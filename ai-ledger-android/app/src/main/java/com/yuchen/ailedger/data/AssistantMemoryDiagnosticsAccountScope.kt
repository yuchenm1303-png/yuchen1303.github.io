package com.yuchen.ailedger.data

import org.json.JSONObject

private object AssistantMemoryDiagnosticsAccountGate {
    private val lock = Any()
    private var initialized = false
    private var activeTicket: AssistantMemorySessionTicket? = null

    fun switchAccount(
        diagnostics: AssistantMemoryDiagnostics,
        ticket: AssistantMemorySessionTicket?,
    ) {
        val normalized = ticket?.takeIf(AssistantAccountSessionRuntime::isCurrent)
        val changed = synchronized(lock) {
            if (initialized && activeTicket == normalized) return@synchronized false
            initialized = true
            activeTicket = normalized
            true
        }
        // 首次初始化即使是未登录状态也必须清空旧进程遗留记录，防止退出后重启仍看到上一账号诊断。
        if (changed) diagnostics.clear()
    }

    fun accepts(ticket: AssistantMemorySessionTicket): Boolean = synchronized(lock) {
        initialized && activeTicket == ticket && AssistantAccountSessionRuntime.isCurrent(ticket)
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
