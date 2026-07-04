package com.yuchen.ailedger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.AgentAnalyticsCloudRepository
import com.yuchen.ailedger.data.AgentAnalyticsCloudSyncSource
import com.yuchen.ailedger.data.AgentAnalyticsOwner
import com.yuchen.ailedger.data.AgentAnalyticsOwnerRuntime
import com.yuchen.ailedger.data.AgentAnalyticsSnapshotReader
import com.yuchen.ailedger.data.AgentSkillInventoryRepository
import com.yuchen.ailedger.data.SupabaseAccountState
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.data.UserProfileRepository
import com.yuchen.ailedger.data.UserProfileState
import com.yuchen.ailedger.data.mergeAgentAnalyticsDaily
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.model.AgentSkillInventory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AgentAnalyticsSyncPhase {
    Checking,
    Guest,
    Syncing,
    Synced,
    Cached,
    LocalOnly,
    Failed,
}

data class AgentAnalyticsSyncUiState(
    val phase: AgentAnalyticsSyncPhase = AgentAnalyticsSyncPhase.Checking,
    val message: String = "正在确认账号与同步状态…",
    val lastSyncedAtMillis: Long = 0L,
    val uploadedDayCount: Int = 0,
    val remoteDayCount: Int = 0,
)

/**
 * 智能体统计页面数据入口。
 *
 * 页面可见时低频刷新一次本机快照，避免用户停留在统计页期间看到旧数据。
 * 登录账号按需同步每日聚合；访客数据始终留在本机且不会自动并入账号。
 */
class AgentAnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val authRepository = SupabaseAuthRepository.get(appContext)
    private val profileRepository by lazy(LazyThreadSafetyMode.NONE) {
        UserProfileRepository.get(appContext)
    }
    private val cloudRepository by lazy(LazyThreadSafetyMode.NONE) {
        AgentAnalyticsCloudRepository.get(appContext)
    }

    val accountState: StateFlow<SupabaseAccountState> = authRepository.state
    val profileState: StateFlow<UserProfileState> = profileRepository.state
    val owner: StateFlow<AgentAnalyticsOwner> = AgentAnalyticsOwnerRuntime.owner.also {
        AgentAnalyticsOwnerRuntime.initialize(appContext)
    }

    private val localSnapshot = MutableStateFlow(AgentAnalyticsSnapshot())
    private val otherDevicesDaily = MutableStateFlow<List<AgentDailyActivity>>(emptyList())
    val state: StateFlow<AgentAnalyticsSnapshot> = combine(
        localSnapshot,
        otherDevicesDaily,
    ) { local, remoteDaily ->
        mergeAgentAnalyticsDaily(local, remoteDaily)
    }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(EMPTY_LOADED_SNAPSHOT)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = AgentAnalyticsSnapshot(),
        )

    private val mutableSyncState = MutableStateFlow(AgentAnalyticsSyncUiState())
    val syncState: StateFlow<AgentAnalyticsSyncUiState> = mutableSyncState.asStateFlow()

    private val mutableSkillInventory = MutableStateFlow(AgentSkillInventory())
    val skillInventory: StateFlow<AgentSkillInventory> = mutableSkillInventory.asStateFlow()

    private var pageVisible = false
    private var ownerCollectionJob: Job? = null
    private var syncJob: Job? = null
    private var manualRefreshJob: Job? = null
    private var skillInventoryRequested = false
    private var skillLoadedOwnerKey: String? = null

    fun onScreenVisible() {
        if (pageVisible) return
        pageVisible = true
        ownerCollectionJob = viewModelScope.launch {
            owner.collectLatest { activeOwner ->
                syncJob?.cancel()
                syncJob = null
                manualRefreshJob?.cancel()
                manualRefreshJob = null
                localSnapshot.value = AgentAnalyticsSnapshot()
                otherDevicesDaily.value = emptyList()
                mutableSkillInventory.value = AgentSkillInventory()
                skillLoadedOwnerKey = null
                mutableSyncState.value = if (activeOwner.isGuest) {
                    guestSyncState()
                } else {
                    AgentAnalyticsSyncUiState(
                        phase = AgentAnalyticsSyncPhase.Checking,
                        message = "正在读取账号统计…",
                    )
                }

                var firstLoad = true
                while (pageVisible && owner.value.storageKey == activeOwner.storageKey) {
                    val local = loadLocalSnapshot(activeOwner)
                    if (!pageVisible || owner.value.storageKey != activeOwner.storageKey) break
                    localSnapshot.value = local

                    if (firstLoad) {
                        if (!activeOwner.isGuest) {
                            startCloudSync(activeOwner, local)
                        } else if (skillInventoryRequested) {
                            loadGuestSkillInventory(activeOwner.storageKey)
                        }
                        firstLoad = false
                    }
                    delay(LOCAL_REFRESH_INTERVAL_MS)
                }
            }
        }
    }

    fun retryCloudSync() {
        if (!pageVisible) return
        val activeOwner = owner.value
        if (activeOwner.isGuest || mutableSyncState.value.phase == AgentAnalyticsSyncPhase.Syncing) return
        manualRefreshJob?.cancel()
        manualRefreshJob = viewModelScope.launch {
            val refreshed = loadLocalSnapshot(activeOwner)
            if (!pageVisible || owner.value.storageKey != activeOwner.storageKey || !refreshed.loaded) return@launch
            localSnapshot.value = refreshed
            startCloudSync(activeOwner, refreshed)
        }
    }

    private suspend fun loadLocalSnapshot(activeOwner: AgentAnalyticsOwner): AgentAnalyticsSnapshot {
        return try {
            withContext(Dispatchers.IO) {
                AgentAnalyticsSnapshotReader.load(appContext, activeOwner)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            EMPTY_LOADED_SNAPSHOT
        }
    }

    private fun startCloudSync(
        activeOwner: AgentAnalyticsOwner,
        local: AgentAnalyticsSnapshot,
    ) {
        if (!local.loaded) return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            mutableSyncState.value = AgentAnalyticsSyncUiState(
                phase = AgentAnalyticsSyncPhase.Syncing,
                message = "正在同步账号每日聚合…",
                lastSyncedAtMillis = mutableSyncState.value.lastSyncedAtMillis,
            )
            val result = try {
                cloudRepository.syncWithStatus(activeOwner, local)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }

            if (!pageVisible || owner.value.storageKey != activeOwner.storageKey) return@launch
            if (result == null) {
                mutableSyncState.value = AgentAnalyticsSyncUiState(
                    phase = AgentAnalyticsSyncPhase.Failed,
                    message = "云端同步暂时不可用，当前继续显示本机统计。",
                )
                return@launch
            }

            otherDevicesDaily.value = result.otherDevicesDaily
            mutableSyncState.value = when (result.source) {
                AgentAnalyticsCloudSyncSource.Network -> AgentAnalyticsSyncUiState(
                    phase = AgentAnalyticsSyncPhase.Synced,
                    message = if (result.uploadedDayCount > 0) {
                        "已上传 ${result.uploadedDayCount} 个变更日，并合并其他设备统计。"
                    } else {
                        "云端已是最新，已合并其他设备统计。"
                    },
                    lastSyncedAtMillis = result.syncedAtMillis,
                    uploadedDayCount = result.uploadedDayCount,
                    remoteDayCount = result.otherDevicesDaily.size,
                )

                AgentAnalyticsCloudSyncSource.Cache -> AgentAnalyticsSyncUiState(
                    phase = AgentAnalyticsSyncPhase.Cached,
                    message = "本机数据没有变化，已复用最近一次同步结果。",
                    lastSyncedAtMillis = result.syncedAtMillis,
                    remoteDayCount = result.otherDevicesDaily.size,
                )

                AgentAnalyticsCloudSyncSource.Skipped -> AgentAnalyticsSyncUiState(
                    phase = AgentAnalyticsSyncPhase.LocalOnly,
                    message = result.errorMessage ?: "当前登录状态不可用，仅显示本机账号统计。",
                    lastSyncedAtMillis = result.syncedAtMillis,
                )

                AgentAnalyticsCloudSyncSource.Failed -> AgentAnalyticsSyncUiState(
                    phase = AgentAnalyticsSyncPhase.Failed,
                    message = result.errorMessage ?: "云端同步暂时不可用，当前继续显示本机统计。",
                    lastSyncedAtMillis = result.syncedAtMillis,
                    remoteDayCount = result.otherDevicesDaily.size,
                )
            }
        }
    }

    fun onScreenHidden() {
        pageVisible = false
        ownerCollectionJob?.cancel()
        ownerCollectionJob = null
        syncJob?.cancel()
        syncJob = null
        manualRefreshJob?.cancel()
        manualRefreshJob = null
    }

    fun ensureSkillInventoryLoaded() {
        skillInventoryRequested = true
        val activeOwner = owner.value
        if (!pageVisible || !activeOwner.isGuest || skillLoadedOwnerKey == activeOwner.storageKey) return
        loadGuestSkillInventory(activeOwner.storageKey)
    }

    private fun loadGuestSkillInventory(ownerKey: String) {
        skillLoadedOwnerKey = ownerKey
        viewModelScope.launch {
            val inventory = try {
                withContext(Dispatchers.IO) {
                    AgentSkillInventoryRepository.get(appContext).loadSnapshot()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                AgentSkillInventory()
            }
            if (
                pageVisible &&
                owner.value.storageKey == ownerKey &&
                owner.value.isGuest
            ) {
                mutableSkillInventory.value = inventory
            }
        }
    }

    private fun guestSyncState() = AgentAnalyticsSyncUiState(
        phase = AgentAnalyticsSyncPhase.Guest,
        message = "访客统计只保存在当前设备；登录后会使用独立账号空间并启用跨设备聚合。",
    )

    override fun onCleared() {
        onScreenHidden()
        super.onCleared()
    }

    private companion object {
        private const val LOCAL_REFRESH_INTERVAL_MS = 2_500L
        val EMPTY_LOADED_SNAPSHOT = AgentAnalyticsSnapshot(loaded = true)
    }
}
