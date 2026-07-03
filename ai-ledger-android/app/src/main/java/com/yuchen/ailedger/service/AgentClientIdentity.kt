package com.yuchen.ailedger.service

import android.content.Context
import java.util.UUID

internal object AgentClientIdentity {
    private const val PREFERENCES_NAME = "ai_agent_client_identity"
    private const val DEVICE_ID_KEY = "device_id"
    private const val DEVICE_ID_PREFIX = "android-install-"
    private const val SESSION_ID_PREFIX = "visual-session-"

    fun getOrCreateDeviceId(context: Context): String {
        val preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val existing = preferences.getString(DEVICE_ID_KEY, null)
            ?.trim()
            ?.takeIf { it.startsWith(DEVICE_ID_PREFIX) && it.length <= 120 }
        if (existing != null) return existing

        val created = DEVICE_ID_PREFIX + UUID.randomUUID().toString()
        preferences.edit().putString(DEVICE_ID_KEY, created).apply()
        return created
    }

    fun newVisualSessionId(): String {
        return VisualTaskInvocationRuntime.currentSessionIdOrNull()
            ?: SESSION_ID_PREFIX + UUID.randomUUID().toString()
    }
}

internal data class VisualTaskInvocation(
    val sessionId: String,
    val goal: String,
    val clientToolCall: CloudClientToolCall? = null,
) {
    val taskInvocationId: String
        get() = clientToolCall?.id?.trim()?.takeIf(String::isNotBlank) ?: sessionId
}

internal object VisualTaskInvocationRuntime {
    private const val MAX_PENDING_CALLS = 12
    private const val PENDING_CALL_TTL_MS = 300_000L
    private const val SESSION_ID_PREFIX = "visual-session-"

    private data class PendingCall(
        val call: CloudClientToolCall,
        val registeredAt: Long,
    )

    private val lock = Any()
    private val pendingVisualCalls = ArrayDeque<PendingCall>()

    @Volatile
    private var activeInvocation: VisualTaskInvocation? = null

    fun register(call: CloudClientToolCall) {
        if (call.name != "computer_run_task" || call.id.isBlank()) return
        synchronized(lock) {
            pruneLocked(System.currentTimeMillis())
            pendingVisualCalls.removeAll { it.call.id == call.id }
            pendingVisualCalls.addLast(PendingCall(call, System.currentTimeMillis()))
            while (pendingVisualCalls.size > MAX_PENDING_CALLS) pendingVisualCalls.removeFirst()
        }
    }

    fun begin(goal: String): VisualTaskInvocation {
        val cleanGoal = goal.trim()
        val call = synchronized(lock) {
            pruneLocked(System.currentTimeMillis())
            val items = pendingVisualCalls.toList()
            val selected = items.lastOrNull { it.call.visualGoal() == cleanGoal }
                ?: items.lastOrNull()
            selected?.also { pendingVisualCalls.remove(it) }?.call
        }
        val sessionId = call?.id?.trim()?.takeIf(String::isNotBlank)
            ?: SESSION_ID_PREFIX + UUID.randomUUID().toString()
        return VisualTaskInvocation(
            sessionId = sessionId.take(120),
            goal = cleanGoal,
            clientToolCall = call,
        ).also { activeInvocation = it }
    }

    fun currentSessionIdOrNull(): String? = activeInvocation?.sessionId

    fun clear(invocation: VisualTaskInvocation) {
        synchronized(lock) {
            if (activeInvocation?.sessionId == invocation.sessionId) activeInvocation = null
        }
    }

    private fun pruneLocked(now: Long) {
        while (pendingVisualCalls.isNotEmpty()) {
            if (now - pendingVisualCalls.first().registeredAt <= PENDING_CALL_TTL_MS) break
            pendingVisualCalls.removeFirst()
        }
    }

    private fun CloudClientToolCall.visualGoal(): String {
        return originalUserGoal?.trim()?.takeIf(String::isNotBlank)
            ?: arguments.optString("goal").trim()
    }
}
