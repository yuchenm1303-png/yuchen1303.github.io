package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.service.AgentAnalyticsCloudClient
import com.yuchen.ailedger.service.AgentClientIdentity
import com.yuchen.ailedger.service.AiWorkerClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class AgentAnalyticsCloudSyncSource {
    Network,
    Cache,
    Skipped,
    Failed,
}

internal data class AgentAnalyticsCloudSyncResult(
    val otherDevicesDaily: List<AgentDailyActivity> = emptyList(),
    val source: AgentAnalyticsCloudSyncSource = AgentAnalyticsCloudSyncSource.Skipped,
    val syncedAtMillis: Long = 0L,
    val uploadedDayCount: Int = 0,
    val errorMessage: String? = null,
)

/** 登录账号的每日聚合统计同步，只在统计页可见期间触发。 */
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
    ): List<AgentDailyActivity> = syncWithStatus(owner, local).otherDevicesDaily

    suspend fun syncWithStatus(
        owner: AgentAnalyticsOwner,
        local: AgentAnalyticsSnapshot,
    ): AgentAnalyticsCloudSyncResult = withContext(Dispatchers.IO) {
        if (owner.isGuest || owner.userId.isNullOrBlank() || !local.loaded) {
            return@withContext AgentAnalyticsCloudSyncResult()
        }
        mutex.withLock {
            val accountState = authRepository.state.value
            val session = accountState.session
                ?.takeIf { accountState.isLoggedIn && it.userId == owner.userId && it.isUsable }
                ?: return@withLock AgentAnalyticsCloudSyncResult(
                    source = AgentAnalyticsCloudSyncSource.Skipped,
                    syncedAtMillis = lastSuccessAt(owner),
                    errorMessage = "登录状态不可用，当前仅显示本机数据。",
                )

            val now = System.currentTimeMillis()
            val localLastActivity = local.dailyActivity.maxOfOrNull {
                it.lastActivityAtMillis.coerceAtLeast(0L)
            } ?: 0L
            cache[owner.storageKey]?.let { entry ->
                if (
                    now - entry.fetchedAtMillis < CACHE_TTL_MS &&
                    localLastActivity <= entry.localLastActivityAtMillis
                ) {
                    return@withLock AgentAnalyticsCloudSyncResult(
                        otherDevicesDaily = entry.otherDevicesDaily,
                        source = AgentAnalyticsCloudSyncSource.Cache,
                        syncedAtMillis = entry.fetchedAtMillis,
                    )
                }
            }

            val watermarkKey = watermarkKey(owner)
            val uploadedThrough = preferences.getLong(watermarkKey, 0L).coerceAtLeast(0L)
            val changedDaily = local.dailyActivity.filter {
                it.lastActivityAtMillis.coerceAtLeast(0L) > uploadedThrough
            }
            val remoteDaily = try {
                client.syncDaily(
                    session = session,
                    deviceId = AgentClientIdentity.getOrCreateDeviceId(appContext),
                    changedDaily = changedDaily,
                    sinceDateKey = DEFAULT_SINCE_DATE,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                val cached = cache[owner.storageKey]
                return@withLock AgentAnalyticsCloudSyncResult(
                    otherDevicesDaily = cached?.otherDevicesDaily.orEmpty(),
                    source = AgentAnalyticsCloudSyncSource.Failed,
                    syncedAtMillis = cached?.fetchedAtMillis ?: lastSuccessAt(owner),
                    errorMessage = "云端同步暂时不可用，当前继续显示本机统计。",
                )
            }

            if (authRepository.state.value.userId != owner.userId) {
                return@withLock AgentAnalyticsCloudSyncResult(
                    source = AgentAnalyticsCloudSyncSource.Skipped,
                    errorMessage = "账号已经切换，本次同步结果已忽略。",
                )
            }

            val uploadedMax = changedDaily.maxOfOrNull {
                it.lastActivityAtMillis.coerceAtLeast(0L)
            } ?: uploadedThrough
            val successAt = System.currentTimeMillis()
            preferences.edit()
                .apply {
                    if (uploadedMax > uploadedThrough) putLong(watermarkKey, uploadedMax)
                    putLong(lastSuccessKey(owner), successAt)
                }
                .apply()
            cache[owner.storageKey] = CacheEntry(
                fetchedAtMillis = successAt,
                localLastActivityAtMillis = localLastActivity,
                otherDevicesDaily = remoteDaily,
            )
            AgentAnalyticsCloudSyncResult(
                otherDevicesDaily = remoteDaily,
                source = AgentAnalyticsCloudSyncSource.Network,
                syncedAtMillis = successAt,
                uploadedDayCount = changedDaily.size,
            )
        }
    }

    private fun watermarkKey(owner: AgentAnalyticsOwner): String =
        "uploaded_through_${owner.databaseName.removeSuffix(".db")}".take(120)

    private fun lastSuccessKey(owner: AgentAnalyticsOwner): String =
        "last_success_${owner.databaseName.removeSuffix(".db")}".take(120)

    private fun lastSuccessAt(owner: AgentAnalyticsOwner): Long =
        preferences.getLong(lastSuccessKey(owner), 0L).coerceAtLeast(0L)

    companion object {
        private const val CACHE_TTL_MS = 5L * 60L * 1_000L
        private const val DEFAULT_SINCE_DATE = "2020-01-01"

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
