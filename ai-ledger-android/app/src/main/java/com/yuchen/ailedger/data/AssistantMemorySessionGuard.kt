package com.yuchen.ailedger.data

import com.yuchen.ailedger.service.SupabaseUserSession
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

internal data class AssistantMemorySessionTicket(
    val userId: String,
    val generation: Long,
)

internal object AssistantAccountSessionRuntime {
    private var currentUserId: String? = null
    private var generation: Long = 0L

    @Synchronized
    fun updateSession(session: SupabaseUserSession?): AssistantMemorySessionTicket? {
        val cleanUserId = session?.userId?.trim()?.takeIf(String::isNotBlank)
        if (currentUserId != cleanUserId) {
            currentUserId = cleanUserId
            generation += 1L
        }
        return cleanUserId?.let { AssistantMemorySessionTicket(it, generation) }
    }

    @Synchronized
    fun updateUser(userId: String?): AssistantMemorySessionTicket? {
        val cleanUserId = userId?.trim()?.takeIf(String::isNotBlank)
        if (currentUserId != cleanUserId) {
            currentUserId = cleanUserId
            generation += 1L
        }
        return cleanUserId?.let { AssistantMemorySessionTicket(it, generation) }
    }

    @Synchronized
    fun currentTicket(userId: String? = currentUserId): AssistantMemorySessionTicket? {
        val cleanUserId = userId?.trim().orEmpty()
        return if (cleanUserId.isNotBlank() && cleanUserId == currentUserId) {
            AssistantMemorySessionTicket(cleanUserId, generation)
        } else {
            null
        }
    }

    @Synchronized
    fun isCurrent(ticket: AssistantMemorySessionTicket): Boolean {
        return ticket.userId == currentUserId && ticket.generation == generation
    }

    fun diagnosticsScope(ticket: AssistantMemorySessionTicket): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(ticket.userId.toByteArray(Charsets.UTF_8))
        return bytes.take(12).joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

internal class AssistantMemorySessionGuard {
    fun updateUser(userId: String?): AssistantMemorySessionTicket? =
        AssistantAccountSessionRuntime.updateUser(userId)

    fun currentTicket(userId: String): AssistantMemorySessionTicket? =
        AssistantAccountSessionRuntime.currentTicket(userId)

    fun isCurrent(ticket: AssistantMemorySessionTicket): Boolean =
        AssistantAccountSessionRuntime.isCurrent(ticket)
}

internal data class AssistantOperationOwner(
    val ticket: AssistantMemorySessionTicket,
    val operationId: String,
)

internal class AssistantOperationGate {
    private val owner = AtomicReference<AssistantOperationOwner?>(null)

    fun tryAcquire(ticket: AssistantMemorySessionTicket): AssistantOperationOwner? {
        val next = AssistantOperationOwner(
            ticket = ticket,
            operationId = UUID.randomUUID().toString(),
        )
        return next.takeIf { owner.compareAndSet(null, next) }
    }

    fun invalidateOwnersNotMatching(ticket: AssistantMemorySessionTicket?) {
        while (true) {
            val current = owner.get() ?: return
            if (ticket != null && current.ticket == ticket) return
            if (owner.compareAndSet(current, null)) return
        }
    }

    fun release(expected: AssistantOperationOwner) {
        owner.compareAndSet(expected, null)
    }
}

internal data class AssistantMemoryRequestContext(
    val token: String,
    val ticket: AssistantMemorySessionTicket?,
)

internal object AssistantMemoryRequestContextRuntime {
    private val currentThreadContext = ThreadLocal<AssistantMemoryRequestContext?>()

    fun stageCurrentThread(): AssistantMemoryRequestContext {
        val context = AssistantMemoryRequestContext(
            token = UUID.randomUUID().toString(),
            ticket = AssistantAccountSessionRuntime.currentTicket(),
        )
        currentThreadContext.set(context)
        return context
    }

    fun peekCurrentThread(): AssistantMemoryRequestContext? = currentThreadContext.get()

    fun consumeCurrentThread(): AssistantMemoryRequestContext? {
        val context = currentThreadContext.get()
        currentThreadContext.remove()
        return context
    }

    fun clearCurrentThread() {
        currentThreadContext.remove()
    }
}
