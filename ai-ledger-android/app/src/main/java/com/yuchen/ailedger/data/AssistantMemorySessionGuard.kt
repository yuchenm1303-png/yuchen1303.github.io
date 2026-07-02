package com.yuchen.ailedger.data

import com.yuchen.ailedger.service.SupabaseUserSession
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * 全 App 统一的账号代际票据。
 *
 * 同一账号的 JWT 刷新不会切换代际；退出或切换账号会立刻提升代际。
 * 所有聊天回执、记忆管理请求、诊断记录和异步状态提交都必须携带同一张票据，
 * 旧请求即使晚到也不能写入新账号状态。
 */
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
            ?.takeIf { it.isUsable && it.userId.trim() == cleanUserId }
            ?.accessToken
            ?.trim()
            ?.takeIf(String::isNotBlank)
        return cleanUserId?.let { AssistantMemorySessionTicket(it, generation) }
    }

    /** 仅供无真实会话的单元测试与显式退出使用。生产代码应调用 updateSession。 */
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

/**
 * 兼容原有单元测试与局部调用；真实代际权威统一委托给全 App Runtime。
 */
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

/**
 * 带账号所有权的异步操作门。
 *
 * 账号切换时可以立即放行新账号；旧请求 finally 只能 CAS 释放自己的 owner，
 * 不可能把新账号正在执行的操作错误解锁。
 */
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
    val userAccessToken: String?,
)

/**
 * 普通聊天构建、网络解析和成功/失败回调均在同一同步调用线程完成。
 * 用请求级 ThreadLocal 把“发起时账号”绑定到最终记忆回执，不把票据发送到云端。
 */
internal object AssistantMemoryRequestContextRuntime {
    private val currentThreadContext = ThreadLocal<AssistantMemoryRequestContext?>()

    fun stageCurrentThread(): AssistantMemoryRequestContext {
        val ticket = AssistantAccountSessionRuntime.currentTicket()
        val context = AssistantMemoryRequestContext(
            token = UUID.randomUUID().toString(),
            ticket = ticket,
            userAccessToken = ticket?.let(AssistantAccountSessionRuntime::accessTokenFor),
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
