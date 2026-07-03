package com.yuchen.ailedger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.AgentAnalyticsRepository
import com.yuchen.ailedger.data.AgentSkillInventoryRepository
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentSkillInventory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 智能体统计页面数据入口。
 *
 * 主活动数据只在页面订阅时读取；操作学习数据库仅在用户首次切换到“能力”页签后
 * 执行一次 IO 聚合查询，不注册常驻数据库观察器。
 */
class AgentAnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val analyticsRepository = AgentAnalyticsRepository.get(application)

    val state: StateFlow<AgentAnalyticsSnapshot> = analyticsRepository.state

    private val mutableSkillInventory = MutableStateFlow(AgentSkillInventory())
    val skillInventory: StateFlow<AgentSkillInventory> = mutableSkillInventory.asStateFlow()

    private var skillLoadStarted = false

    fun ensureSkillInventoryLoaded() {
        if (skillLoadStarted) return
        skillLoadStarted = true
        viewModelScope.launch {
            mutableSkillInventory.value = AgentSkillInventoryRepository
                .get(getApplication())
                .loadSnapshot()
        }
    }
}
