package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.service.AgentAnalyticsCloudClient
import com.yuchen.ailedger.service.AgentClientIdentity
import com.yuchen.ailedger.service.AiWorkerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 登录账号的每日聚合统计同步。
 *
 * 只由统计详情页在可见期间触发；上传内容不包含任务目标、执行结果、应用名称或动作明细。
 * 同一账号五分钟内重复进入页面直接复用内存结果，避免无意义联网。
 */
internal class AgentAnalyticsCloudRepository private constructor(context: Context) {
    private data class CacheEntry(
        val fetchedAtMillis: Long,
        val localLastActivityAtMillis: Long,
        val otherDevicesDaily: List<AgentDailyActivity>,
    )

    private val appContext = context.applicationContext
    private val authRepository = SupabaseAuthRepository.get(appContext)
    private val client = AgentAnalyticsCloudClient(
        endpoints = listOf(AiWorkerClient.DEFAULT_ENDPOINT),
    )
    private val preferences = appContext.getSharedPreferences(
        "agent_analytics_cloud_sync",
        Context.MODE_PRIVATE,
    )
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()

    suspend fun sync(
        owner: AgentAnalyticsOwner,
        local: AgentAnalyticsSnapshot,
    ): List<AgentDailyActivity> = withContext(Dispatchers.IO) {
        if (owner.isGuest || owner.userId.isNullOrBlank() || !local.loaded) return@withContext emptyList()
        mutex.withLock {
            val accountState = authRepository.state.value
            val session = accountState.session
                ?.takeIf { accountState.isLoggedIn && it.userId == owner.userId && it.isUsable }
                ?: return@withLock emptyList()
            val now = System.currentTimeMillis()
            val localLastActivity = local.dailyActivity.maxOfOrNull {
                it.lastActivityAtMillis.coerceAtLeast(0L)
            } ?: 0L
            cache[owner.storageKey]?.let { entry ->
                if (
                    now - entry.fetchedAtMillis < CACHE_TTL_MS &&
                    localLastActivity <= entry.localLastActivityAtMillis
                ) {
                    return@withLock entry.otherDevicesDaily
                }
            }

            val watermarkKey = watermarkKey(owner)
            val uploadedThrough = preferences.getLong(watermarkKey, 0L).coerceAtLeast(0L)
            val changedDaily = local.dailyActivity.filter {
                it.lastActivityAtMillis.coerceAtLeast(0L) > uploadedThrough
            }
            val sinceDateKey = local.dailyActivity.firstOrNull()?.dateKey
                ?.takeIf { DATE_KEY_REGEX.matches(it) }
                ?: DEFAULT_SINCE_DATE
            val deviceId = AgentClientIdentity.getOrCreateDeviceId(appContext)
            val remoteDaily = runCatching {
                client.syncDaily(
                    session = session,
                    deviceId = deviceId,
                    changedDaily = changedDaily,
                    sinceDateKey = sinceDateKey,
                )
            }.getOrElse {
                return@withLock cache[owner.storageKey]?.otherDevicesDaily.orEmpty()
            }

            if (authRepository.state.value.userId != owner.userId) {
                return@withLock emptyList()
            }
            val uploadedMax = changedDaily.maxOfOrNull {
                it.lastActivityAtMillis.coerceAtLeast(0L)
            } ?: uploadedThrough
            if (uploadedMax > uploadedThrough) {
                preferences.edit().putLong(watermarkKey, uploadedMax).apply()
            }
            cache[owner.storageKey] = CacheEntry(
                fetchedAtMillis = now,
                localLastActivityAtMillis = localLastActivity,
                otherDevicesDaily = remoteDaily,
            )
            remoteDaily
        }
    }

    private fun watermarkKey(owner: AgentAnalyticsOwner): String =
        "uploaded_through_${owner.databaseName.removeSuffix(".db")}".take(120)

    companion object {
        private const val CACHE_TTL_MS = 5L * 60L * 1_000L
        private const val DEFAULT_SINCE_DATE = "2020-01-01"
        private val DATE_KEY_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")

        @Volatile
        private var instance: AgentAnalyticsCloudRepository? = null

        fun get(context: Context): AgentAnalyticsCloudRepository {
            return instance ?: synchronized(this) {
                instance ?: AgentAnalyticsCloudRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
