package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.service.SupabaseUserSession
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 智能体统计的数据所有者边界。
 *
 * - 未登录数据继续使用原有 agent_analytics.db，保留已经产生的本机历史。
 * - 登录账号使用由 Supabase userId 单向摘要得到的独立数据库文件。
 * - 账号切换只切换数据库引用，不搬运、不合并其他所有者的数据。
 */
data class AgentAnalyticsOwner(
    val storageKey: String,
    val databaseName: String,
    val userId: String? = null,
    val email: String? = null,
    val isGuest: Boolean = true,
)

object AgentAnalyticsOwnerRuntime {
    private const val PREFERENCES_NAME = "agent_analytics_owner"
    private const val KEY_INSTALLATION_ID = "installation_id"
    private const val LEGACY_GUEST_DATABASE = "agent_analytics.db"
    private const val ACCOUNT_DATABASE_PREFIX = "agent_analytics_user_"

    private val lock = Any()
    private val mutableOwner = MutableStateFlow(
        AgentAnalyticsOwner(
            storageKey = "guest:pending",
            databaseName = LEGACY_GUEST_DATABASE,
        ),
    )
    val owner: StateFlow<AgentAnalyticsOwner> = mutableOwner.asStateFlow()

    @Volatile
    private var initialized = false

    fun initialize(context: Context): AgentAnalyticsOwner {
        if (initialized) return mutableOwner.value
        synchronized(lock) {
            if (!initialized) {
                mutableOwner.value = guestOwner(context.applicationContext)
                initialized = true
            }
        }
        return mutableOwner.value
    }

    fun switchAccount(context: Context, session: SupabaseUserSession?): AgentAnalyticsOwner {
        initialize(context)
        val next = session
            ?.takeIf { it.isUsable && it.userId.isNotBlank() }
            ?.let(::accountOwner)
            ?: guestOwner(context.applicationContext)
        if (mutableOwner.value != next) mutableOwner.value = next
        return next
    }

    fun current(context: Context): AgentAnalyticsOwner = initialize(context)

    fun currentStorageKey(context: Context): String = current(context).storageKey

    fun databaseNameForStorageKey(storageKey: String): String {
        val clean = storageKey.trim()
        if (!clean.startsWith("user:")) return LEGACY_GUEST_DATABASE
        val userId = clean.removePrefix("user:").trim()
        return if (userId.isBlank()) {
            LEGACY_GUEST_DATABASE
        } else {
            "$ACCOUNT_DATABASE_PREFIX${sha256(userId).take(24)}.db"
        }
    }

    fun installationId(context: Context): String {
        val preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        preferences.getString(KEY_INSTALLATION_ID, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        synchronized(lock) {
            preferences.getString(KEY_INSTALLATION_ID, null)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
            return UUID.randomUUID().toString().also { generated ->
                preferences.edit().putString(KEY_INSTALLATION_ID, generated).apply()
            }
        }
    }

    private fun guestOwner(context: Context): AgentAnalyticsOwner {
        val installationId = installationId(context)
        return AgentAnalyticsOwner(
            storageKey = "guest:$installationId",
            databaseName = LEGACY_GUEST_DATABASE,
            isGuest = true,
        )
    }

    private fun accountOwner(session: SupabaseUserSession): AgentAnalyticsOwner {
        val userId = session.userId.trim()
        return AgentAnalyticsOwner(
            storageKey = "user:$userId",
            databaseName = databaseNameForStorageKey("user:$userId"),
            userId = userId,
            email = session.email.trim().takeIf { it.isNotBlank() },
            isGuest = false,
        )
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
