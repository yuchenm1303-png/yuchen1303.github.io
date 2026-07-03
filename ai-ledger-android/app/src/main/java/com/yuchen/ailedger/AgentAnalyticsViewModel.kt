package com.yuchen.ailedger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.AgentAnalyticsCloudRepository
import com.yuchen.ailedger.data.AgentAnalyticsOwner
import com.yuchen.ailedger.data.AgentAnalyticsOwnerRuntime
import com.yuchen.ailedger.data.AgentAnalyticsSnapshotReader
import com.yuchen.ailedger.data.AgentSkillInventoryRepository
import com.yuchen.ailedger.data.mergeAgentAnalyticsDaily
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.model.AgentSkillInventory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

/**
 * 智能体统计页面数据入口。
 *
 * 页面每次可见时只在 IO 线程读取一次不可变快照，不再订阅 Repository 内部的 Room stateIn。
 * 账号变化会取消旧账号读取并重新加载，页面关闭后没有统计数据库观察器或同步协程残留。
 */
class AgentAnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

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

    private val mutableSkillInventory = MutableStateFlow(AgentSkillInventory())
    val skillInventory: StateFlow<AgentSkillInventory> = mutableSkillInventory.asStateFlow()

    private var pageVisible = false
    private var ownerCollectionJob: Job? = null
    private var skillInventoryRequested = false
    private var skillLoadedOwnerKey: String? = null

    fun onScreenVisible() {
        if (pageVisible) return
        pageVisible = true
        ownerCollectionJob = viewModelScope.launch {
            owner.collectLatest { activeOwner ->
                localSnapshot.value = AgentAnalyticsSnapshot()
                otherDevicesDaily.value = emptyList()
                mutableSkillInventory.value = AgentSkillInventory()
                skillLoadedOwnerKey = null

                val local = try {
                    withContext(Dispatchers.IO) {
                        AgentAnalyticsSnapshotReader.load(appContext, activeOwner)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    EMPTY_LOADED_SNAPSHOT
                }

                if (!pageVisible || owner.value.storageKey != activeOwner.storageKey) {
                    return@collectLatest
                }
                localSnapshot.value = local

                if (!activeOwner.isGuest) {
                    val remote = try {
                        AgentAnalyticsCloudRepository.get(appContext).sync(activeOwner, local)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    if (
                        pageVisible &&
                        owner.value.storageKey == activeOwner.storageKey
                    ) {
                        otherDevicesDaily.value = remote
                    }
                } else if (skillInventoryRequested) {
                    loadGuestSkillInventory(activeOwner.storageKey)
                }
            }
        }
    }

    fun onScreenHidden() {
        pageVisible = false
        ownerCollectionJob?.cancel()
        ownerCollectionJob = null
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

    override fun onCleared() {
        onScreenHidden()
        super.onCleared()
    }

    private companion object {
        val EMPTY_LOADED_SNAPSHOT = AgentAnalyticsSnapshot(loaded = true)
    }
}
