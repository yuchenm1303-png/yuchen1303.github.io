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
    private var currentAccessToken: String? = null
    private var generation: Long = 0L

    @Synchronized
    fun updateSession(session: SupabaseUserSession?): AssistantMemorySessionTicket? {
        val cleanUserId = session?.userId?.trim()?.takeIf(String::isNotBlank)
        if (currentUserId != cleanUserId) {
            currentUserId = cleanUserId
            generation += 1L
        }
        currentAccessToken = session
            ?.takeIf { it.userId.trim() == cleanUserId && it.isUsable }
            ?.accessToken
            ?.trim()
            ?.takeIf(String::isNotBlank)
        return cleanUserId?.let { AssistantMemorySessionTicket(it, generation) }
    }

    /** 仅用于无真实 Supabase 会话的单元测试。 */
    @Synchronized
    fun updateUser(userId: String?): AssistantMemorySessionTicket? {
        val cleanUserId = userId?.trim()?.takeIf(String::isNotBlank)
        if (currentUserId != cleanUserId) {
            currentUserId = cleanUserId
            generation += 1L
        }
        currentAccessToken = null
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

    @Synchronized
    fun accessTokenFor(ticket: AssistantMemorySessionTicket): String? {
        return currentAccessToken.takeIf {
            ticket.userId == currentUserId && ticket.generation == generation && !it.isNullOrBlank()
        }
    }

    fun diagnosticsScope(ticket: AssistantMemorySessionTicket): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(ticket.userId.toByteArray(Charsets.UTF_8))
        return bytes.take(12).joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

/** 兼容原有局部调用，真实权威统一委托给全 App Runtime。 */
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

internal enum class AssistantMemoryRequestSource {
    Chat,
    Management,
}

internal data class AssistantMemoryRequestContext(
    val token: String,
    val ticket: AssistantMemorySessionTicket?,
    val userAccessToken: String?,
    val source: AssistantMemoryRequestSource,
)

internal object AssistantMemoryRequestContextRuntime {
    private val currentThreadContext = ThreadLocal<AssistantMemoryRequestContext?>()

    fun stageCurrentThread(
        source: AssistantMemoryRequestSource = AssistantMemoryRequestSource.Chat,
    ): AssistantMemoryRequestContext {
        val ticket = AssistantAccountSessionRuntime.currentTicket()
        val context = AssistantMemoryRequestContext(
            token = UUID.randomUUID().toString(),
            ticket = ticket,
            userAccessToken = ticket?.let(AssistantAccountSessionRuntime::accessTokenFor),
            source = source,
        )
        currentThreadContext.set(context)
        return context
    }

    fun stageForSession(
        session: SupabaseUserSession,
        source: AssistantMemoryRequestSource,
    ): AssistantMemoryRequestContext {
        val ticket = AssistantAccountSessionRuntime.currentTicket(session.userId)
        val context = AssistantMemoryRequestContext(
            token = UUID.randomUUID().toString(),
            ticket = ticket,
            userAccessToken = session.accessToken.trim().takeIf(String::isNotBlank),
            source = source,
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
