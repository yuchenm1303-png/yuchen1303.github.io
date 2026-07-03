package com.yuchen.ailedger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.AgentAnalyticsOwner
import com.yuchen.ailedger.data.AgentAnalyticsOwnerRuntime
import com.yuchen.ailedger.data.AgentAnalyticsRepository
import com.yuchen.ailedger.data.AgentSkillInventoryRepository
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentSkillInventory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 智能体统计页面数据入口。
 *
 * 账号变化时只切换到对应 Room StateFlow，不复制数据、不合并访客历史。
 * 操作学习 Skill 目前仍是设备级资产，因此只允许访客空间读取，避免账号间泄露计数。
 */
class AgentAnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

    val owner: StateFlow<AgentAnalyticsOwner> = AgentAnalyticsOwnerRuntime.owner.also {
        AgentAnalyticsOwnerRuntime.initialize(appContext)
    }

    val state: StateFlow<AgentAnalyticsSnapshot> = owner
        .flatMapLatest { activeOwner ->
            AgentAnalyticsRepository.get(appContext, activeOwner.storageKey).state
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = AgentAnalyticsSnapshot(),
        )

    private val mutableSkillInventory = MutableStateFlow(AgentSkillInventory())
    val skillInventory: StateFlow<AgentSkillInventory> = mutableSkillInventory.asStateFlow()

    private var skillInventoryRequested = false
    private var skillLoadedOwnerKey: String? = null

    init {
        viewModelScope.launch {
            owner.collectLatest { activeOwner ->
                mutableSkillInventory.value = AgentSkillInventory()
                skillLoadedOwnerKey = null
                if (skillInventoryRequested && activeOwner.isGuest) {
                    loadGuestSkillInventory(activeOwner.storageKey)
                }
            }
        }
    }

    fun ensureSkillInventoryLoaded() {
        skillInventoryRequested = true
        val activeOwner = owner.value
        if (!activeOwner.isGuest || skillLoadedOwnerKey == activeOwner.storageKey) return
        loadGuestSkillInventory(activeOwner.storageKey)
    }

    private fun loadGuestSkillInventory(ownerKey: String) {
        skillLoadedOwnerKey = ownerKey
        viewModelScope.launch {
            val inventory = AgentSkillInventoryRepository.get(appContext).loadSnapshot()
            if (owner.value.storageKey == ownerKey && owner.value.isGuest) {
                mutableSkillInventory.value = inventory
            }
        }
    }
}
