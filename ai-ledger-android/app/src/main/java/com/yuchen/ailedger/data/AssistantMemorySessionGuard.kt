package com.yuchen.ailedger.data

/**
 * 记忆网络请求的账号代际票据。
 *
 * 同一账号刷新 JWT 不会切换代际；退出或切换账号会立即提升代际，使旧请求即使无法被
 * HttpURLConnection 及时取消，也无法把结果提交到新账号状态。
 */
internal data class AssistantMemorySessionTicket(
    val userId: String,
    val generation: Long,
)

internal class AssistantMemorySessionGuard {
    private var currentUserId: String? = null
    private var generation: Long = 0L

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
    fun currentTicket(userId: String): AssistantMemorySessionTicket? {
        val cleanUserId = userId.trim()
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
}
