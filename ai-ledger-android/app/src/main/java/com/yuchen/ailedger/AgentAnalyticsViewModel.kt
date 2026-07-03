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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

/**
 * 智能体统计页面数据入口。
 *
 * Room 观察与云同步都只在页面可见期间运行。账号变化会立即取消旧账号收集，先清空页面快照，
 * 再连接新账号数据库，避免短暂显示上一个账号的数据。
 *
 * 统计属于旁路能力：数据库校验、历史文件、账号同步或 Skill 聚合出现异常时只降级为空快照，
 * 绝不能把异常抛到主线程导致整个应用退出。
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
            try {
                owner.collectLatest { activeOwner ->
                    localSnapshot.value = AgentAnalyticsSnapshot()
                    otherDevicesDaily.value = emptyList()
                    mutableSkillInventory.value = AgentSkillInventory()
                    skillLoadedOwnerKey = null

                    supervisorScope {
                        val repository = try {
                            withContext(Dispatchers.IO) {
                                AgentAnalyticsRepository.get(appContext, activeOwner.storageKey)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            localSnapshot.value = EMPTY_LOADED_SNAPSHOT
                            return@supervisorScope
                        }

                        launch {
                            repository.state
                                .catch { error ->
                                    if (error is CancellationException) throw error
                                    emit(EMPTY_LOADED_SNAPSHOT)
                                }
                                .collectLatest { snapshot ->
                                    if (
                                        pageVisible &&
                                        owner.value.storageKey == activeOwner.storageKey
                                    ) {
                                        localSnapshot.value = snapshot
                                    }
                                }
                        }

                        if (!activeOwner.isGuest) {
                            launch {
                                try {
                                    val local = repository.state
                                        .catch { error ->
                                            if (error is CancellationException) throw error
                                            emit(EMPTY_LOADED_SNAPSHOT)
                                        }
                                        .first { it.loaded }
                                    val remote = AgentAnalyticsCloudRepository
                                        .get(appContext)
                                        .sync(activeOwner, local)
                                    if (
                                        pageVisible &&
                                        owner.value.storageKey == activeOwner.storageKey
                                    ) {
                                        otherDevicesDaily.value = remote
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Throwable) {
                                    // 云端统计是可选增强，失败时继续展示本机账号数据。
                                }
                            }
                        } else if (skillInventoryRequested) {
                            loadGuestSkillInventory(activeOwner.storageKey)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                localSnapshot.value = EMPTY_LOADED_SNAPSHOT
                otherDevicesDaily.value = emptyList()
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
