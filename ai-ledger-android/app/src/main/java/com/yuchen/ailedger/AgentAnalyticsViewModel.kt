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
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentAnalyticsTotals
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.model.AgentSkillInventory
import java.time.LocalDate
import java.time.ZoneId
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

fun String.agentAnalyticsDiagnosticLine(): String = replace('\n', ' ').replace('\r', ' ').take(240)

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

data class AgentAnalyticsDiagnosticsUiState(
    val lastLocalLoadAtMillis: Long = 0L,
    val lastLocalLoadDurationMs: Long = 0L,
    val lastLocalLoadSuccess: Boolean = false,
    val lastLocalLoadMessage: String = "尚未读取本机统计。",
    val lastLocalLoadErrorType: String = "",
    val ownerStorageKey: String = "",
    val ownerDatabaseName: String = "",
    val ownerIsGuest: Boolean = true,
    val localDailyCount: Int = 0,
    val localTaskCount: Int = 0,
    val localModelCount: Int = 0,
    val localCapabilityCount: Int = 0,
    val localTotalTokens: Long = 0L,
    val localModelCalls: Long = 0L,
    val localCompletedTasks: Long = 0L,
    val mergedDailyCount: Int = 0,
    val mergedTotalTokens: Long = 0L,
) {
    val hasLocalLoadFailure: Boolean
        get() = lastLocalLoadAtMillis > 0L && !lastLocalLoadSuccess
}

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
    private val mutableDiagnostics = MutableStateFlow(AgentAnalyticsDiagnosticsUiState())
    val diagnostics: StateFlow<AgentAnalyticsDiagnosticsUiState> = mutableDiagnostics.asStateFlow()

    val state: StateFlow<AgentAnalyticsSnapshot> = combine(
        localSnapshot,
        otherDevicesDaily,
    ) { local, remoteDaily ->
        composeVisibleSnapshot(local, remoteDaily).also { visible ->
            updateMergedDiagnostics(visible)
        }
    }
        .catch { error ->
            if (error is CancellationException) throw error
            mutableDiagnostics.value = mutableDiagnostics.value.copy(
                lastLocalLoadSuccess = false,
                lastLocalLoadMessage = "合成统计快照失败：${error.message?.agentAnalyticsDiagnosticLine().orEmpty().ifBlank { "未知错误" }}",
                lastLocalLoadErrorType = error::class.java.simpleName,
            )
            emit(UNLOADED_SNAPSHOT)
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
                mutableDiagnostics.value = AgentAnalyticsDiagnosticsUiState(
                    ownerStorageKey = activeOwner.storageKey,
                    ownerDatabaseName = activeOwner.databaseName,
                    ownerIsGuest = activeOwner.isGuest,
                    lastLocalLoadMessage = "已切换统计空间，等待读取本机快照。",
                )
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
        val started = System.currentTimeMillis()
        return try {
            val snapshot = withContext(Dispatchers.IO) {
                AgentAnalyticsSnapshotReader.load(appContext, activeOwner)
            }
            mutableDiagnostics.value = mutableDiagnostics.value.copy(
                lastLocalLoadAtMillis = System.currentTimeMillis(),
                lastLocalLoadDurationMs = System.currentTimeMillis() - started,
                lastLocalLoadSuccess = true,
                lastLocalLoadMessage = "本机统计读取成功。",
                lastLocalLoadErrorType = "",
                ownerStorageKey = activeOwner.storageKey,
                ownerDatabaseName = activeOwner.databaseName,
                ownerIsGuest = activeOwner.isGuest,
                localDailyCount = snapshot.dailyActivity.size,
                localTaskCount = snapshot.recentTasks.size,
                localModelCount = snapshot.modelUsage.size,
                localCapabilityCount = snapshot.capabilityUsage.size,
                localTotalTokens = snapshot.totals.totalTokens,
                localModelCalls = snapshot.totals.modelCalls,
                localCompletedTasks = snapshot.totals.completedTasks,
            )
            snapshot
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableDiagnostics.value = mutableDiagnostics.value.copy(
                lastLocalLoadAtMillis = System.currentTimeMillis(),
                lastLocalLoadDurationMs = System.currentTimeMillis() - started,
                lastLocalLoadSuccess = false,
                lastLocalLoadMessage = error.message?.agentAnalyticsDiagnosticLine()
                    ?.takeIf { it.isNotBlank() }
                    ?: "本机统计读取失败。",
                lastLocalLoadErrorType = error::class.java.name,
                ownerStorageKey = activeOwner.storageKey,
                ownerDatabaseName = activeOwner.databaseName,
                ownerIsGuest = activeOwner.isGuest,
                localDailyCount = 0,
                localTaskCount = 0,
                localModelCount = 0,
                localCapabilityCount = 0,
                localTotalTokens = 0L,
                localModelCalls = 0L,
                localCompletedTasks = 0L,
            )
            if (!activeOwner.isGuest) {
                mutableSyncState.value = AgentAnalyticsSyncUiState(
                    phase = AgentAnalyticsSyncPhase.LocalOnly,
                    message = "本机统计读取失败，已暂停云端同步，点右上角诊断复制详情继续排查。",
                    lastSyncedAtMillis = mutableSyncState.value.lastSyncedAtMillis,
                )
            }
            UNLOADED_SNAPSHOT
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

    private fun composeVisibleSnapshot(
        local: AgentAnalyticsSnapshot,
        remoteDaily: List<AgentDailyActivity>,
    ): AgentAnalyticsSnapshot {
        if (!local.loaded || remoteDaily.isEmpty()) return local
        val mergedDaily = mergeDailyActivity(local.dailyActivity, remoteDaily)
        return local.copy(
            dailyActivity = mergedDaily,
            totals = buildTotals(mergedDaily, local.totals.longestTaskDurationMs),
            loaded = true,
        )
    }

    private fun mergeDailyActivity(
        localDaily: List<AgentDailyActivity>,
        remoteDaily: List<AgentDailyActivity>,
    ): List<AgentDailyActivity> {
        val byDate = linkedMapOf<String, AgentDailyActivity>()
        (localDaily + remoteDaily).forEach { day ->
            val key = day.dateKey.trim()
            if (key.isBlank()) return@forEach
            val normalized = day.copy(dateKey = key)
            byDate[key] = byDate[key]?.let { existing -> mergeDaily(existing, normalized) } ?: normalized
        }
        return byDate.values.sortedBy(AgentDailyActivity::dateKey)
    }

    private fun mergeDaily(left: AgentDailyActivity, right: AgentDailyActivity): AgentDailyActivity = left.copy(
        firstActivityAtMillis = earliestPositive(left.firstActivityAtMillis, right.firstActivityAtMillis),
        lastActivityAtMillis = maxOf(left.lastActivityAtMillis, right.lastActivityAtMillis).coerceAtLeast(0L),
        chatCalls = safeAdd(left.chatCalls, right.chatCalls),
        chatFailures = safeAdd(left.chatFailures, right.chatFailures),
        agentTasks = safeAdd(left.agentTasks, right.agentTasks),
        completedTasks = safeAdd(left.completedTasks, right.completedTasks),
        autonomousCompletedTasks = safeAdd(left.autonomousCompletedTasks, right.autonomousCompletedTasks),
        assistedCompletedTasks = safeAdd(left.assistedCompletedTasks, right.assistedCompletedTasks),
        failedTasks = safeAdd(left.failedTasks, right.failedTasks),
        pausedTasks = safeAdd(left.pausedTasks, right.pausedTasks),
        cancelledTasks = safeAdd(left.cancelledTasks, right.cancelledTasks),
        budgetExceededTasks = safeAdd(left.budgetExceededTasks, right.budgetExceededTasks),
        modelCalls = safeAdd(left.modelCalls, right.modelCalls),
        modelFailures = safeAdd(left.modelFailures, right.modelFailures),
        agentModelTurns = safeAdd(left.agentModelTurns, right.agentModelTurns),
        inputTokens = safeAdd(left.inputTokens, right.inputTokens),
        outputTokens = safeAdd(left.outputTokens, right.outputTokens),
        reasoningTokens = safeAdd(left.reasoningTokens, right.reasoningTokens),
        cachedInputTokens = safeAdd(left.cachedInputTokens, right.cachedInputTokens),
        totalTokens = safeAdd(left.totalTokens, right.totalTokens),
        providerTokens = safeAdd(left.providerTokens, right.providerTokens),
        estimatedTokens = safeAdd(left.estimatedTokens, right.estimatedTokens),
        modelLatencyMs = safeAdd(left.modelLatencyMs, right.modelLatencyMs),
        requestBytes = safeAdd(left.requestBytes, right.requestBytes),
        responseBytes = safeAdd(left.responseBytes, right.responseBytes),
        taskDurationMs = safeAdd(left.taskDurationMs, right.taskDurationMs),
        executedActions = safeAdd(left.executedActions, right.executedActions),
        successfulActions = safeAdd(left.successfulActions, right.successfulActions),
        failedActions = safeAdd(left.failedActions, right.failedActions),
        observations = safeAdd(left.observations, right.observations),
        reobservations = safeAdd(left.reobservations, right.reobservations),
        rejectedPlans = safeAdd(left.rejectedPlans, right.rejectedPlans),
        executionFailures = safeAdd(left.executionFailures, right.executionFailures),
        confirmationRequests = safeAdd(left.confirmationRequests, right.confirmationRequests),
        confirmationsAccepted = safeAdd(left.confirmationsAccepted, right.confirmationsAccepted),
        userInputRequests = safeAdd(left.userInputRequests, right.userInputRequests),
        userInputsSubmitted = safeAdd(left.userInputsSubmitted, right.userInputsSubmitted),
        userTakeovers = safeAdd(left.userTakeovers, right.userTakeovers),
        takeoverResumes = safeAdd(left.takeoverResumes, right.takeoverResumes),
        webSearches = safeAdd(left.webSearches, right.webSearches),
        imageRequests = safeAdd(left.imageRequests, right.imageRequests),
    )

    private fun buildTotals(
        daily: List<AgentDailyActivity>,
        localLongestTaskDurationMs: Long,
    ): AgentAnalyticsTotals {
        val completed = daily.safeSum(AgentDailyActivity::completedTasks)
        val autonomous = daily.safeSum(AgentDailyActivity::autonomousCompletedTasks)
        val assisted = daily.safeSum(AgentDailyActivity::assistedCompletedTasks)
        val terminalTasks = daily.safeSum { day ->
            safeAdd(
                safeAdd(day.completedTasks, day.failedTasks),
                safeAdd(
                    safeAdd(day.pausedTasks, day.cancelledTasks),
                    day.budgetExceededTasks,
                ),
            )
        }
        val streaks = calculateStreaks(daily)
        return AgentAnalyticsTotals(
            totalTokens = daily.safeSum(AgentDailyActivity::totalTokens),
            providerTokens = daily.safeSum(AgentDailyActivity::providerTokens),
            estimatedTokens = daily.safeSum(AgentDailyActivity::estimatedTokens),
            peakDailyTokens = daily.maxOfOrNull(AgentDailyActivity::totalTokens)?.coerceAtLeast(0L) ?: 0L,
            chatCalls = daily.safeSum(AgentDailyActivity::chatCalls),
            agentTasks = daily.safeSum(AgentDailyActivity::agentTasks),
            completedTasks = completed,
            autonomousCompletedTasks = autonomous,
            assistedCompletedTasks = assisted,
            taskSuccessRate = if (terminalTasks > 0L) completed.toFloat() / terminalTasks.toFloat() else 0f,
            autonomousCompletionRate = if (completed > 0L) autonomous.toFloat() / completed.toFloat() else 0f,
            executedActions = daily.safeSum(AgentDailyActivity::executedActions),
            agentModelTurns = daily.safeSum(AgentDailyActivity::agentModelTurns),
            modelCalls = daily.safeSum(AgentDailyActivity::modelCalls),
            totalTaskDurationMs = daily.safeSum(AgentDailyActivity::taskDurationMs),
            longestTaskDurationMs = localLongestTaskDurationMs.coerceAtLeast(0L),
            currentActiveStreakDays = streaks.first,
            longestActiveStreakDays = streaks.second,
        )
    }

    private fun calculateStreaks(daily: List<AgentDailyActivity>): Pair<Int, Int> {
        val activeDates = daily.asSequence()
            .filter {
                it.totalTokens > 0L ||
                    it.chatCalls > 0L ||
                    it.agentTasks > 0L ||
                    it.executedActions > 0L
            }
            .mapNotNull { runCatching { LocalDate.parse(it.dateKey) }.getOrNull() }
            .distinct()
            .sorted()
            .toList()
        if (activeDates.isEmpty()) return 0 to 0

        var longest = 1
        var running = 1
        for (index in 1 until activeDates.size) {
            running = if (activeDates[index - 1].plusDays(1L) == activeDates[index]) running + 1 else 1
            longest = maxOf(longest, running)
        }

        val today = LocalDate.now(ZoneId.systemDefault())
        val latest = activeDates.last()
        if (latest != today && latest != today.minusDays(1L)) return 0 to longest

        var current = 1
        for (index in activeDates.lastIndex downTo 1) {
            if (activeDates[index - 1].plusDays(1L) != activeDates[index]) break
            current += 1
        }
        return current to longest
    }

    private fun updateMergedDiagnostics(snapshot: AgentAnalyticsSnapshot) {
        mutableDiagnostics.value = mutableDiagnostics.value.copy(
            mergedDailyCount = snapshot.dailyActivity.size,
            mergedTotalTokens = snapshot.totals.totalTokens,
        )
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
        val UNLOADED_SNAPSHOT = AgentAnalyticsSnapshot(loaded = false)

        private fun earliestPositive(left: Long, right: Long): Long = when {
            left <= 0L -> right.coerceAtLeast(0L)
            right <= 0L -> left.coerceAtLeast(0L)
            else -> minOf(left, right)
        }

        private fun safeAdd(left: Long, right: Long): Long {
            val safeLeft = left.coerceAtLeast(0L)
            val safeRight = right.coerceAtLeast(0L)
            return if (Long.MAX_VALUE - safeLeft < safeRight) Long.MAX_VALUE else safeLeft + safeRight
        }

        private inline fun <T> List<T>.safeSum(selector: (T) -> Long): Long {
            var total = 0L
            forEach { total = safeAdd(total, selector(it)) }
            return total
        }
    }
}
