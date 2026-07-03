package com.yuchen.ailedger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.AgentAnalyticsCloudRepository
import com.yuchen.ailedger.data.AgentAnalyticsOwner
import com.yuchen.ailedger.data.AgentAnalyticsOwnerRuntime
import com.yuchen.ailedger.data.AgentAnalyticsRepository
import com.yuchen.ailedger.data.AgentSkillInventoryRepository
import com.yuchen.ailedger.data.mergeAgentAnalyticsDaily
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.model.AgentSkillInventory
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 智能体统计页面数据入口。
 *
 * Room 观察与云同步都只在页面可见期间运行。账号变化会立即取消旧账号收集，先清空页面快照，
 * 再连接新账号数据库，避免短暂显示上一个账号的数据。
 */
class AgentAnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val cloudRepository = AgentAnalyticsCloudRepository.get(appContext)

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
    }.stateIn(
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

                coroutineScope {
                    val repository = AgentAnalyticsRepository.get(appContext, activeOwner.storageKey)
                    launch {
                        repository.state.collectLatest { snapshot ->
                            localSnapshot.value = snapshot
                        }
                    }
                    if (!activeOwner.isGuest) {
                        launch {
                            val local = repository.state.first { it.loaded }
                            val remote = cloudRepository.sync(activeOwner, local)
                            if (
                                pageVisible &&
                                owner.value.storageKey == activeOwner.storageKey
                            ) {
                                otherDevicesDaily.value = remote
                            }
                        }
                    } else if (skillInventoryRequested) {
                        loadGuestSkillInventory(activeOwner.storageKey)
                    }
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
            val inventory = AgentSkillInventoryRepository.get(appContext).loadSnapshot()
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
}
