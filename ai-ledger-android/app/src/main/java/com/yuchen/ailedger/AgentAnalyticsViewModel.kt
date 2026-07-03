package com.yuchen.ailedger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.AgentAnalyticsRepository
import com.yuchen.ailedger.data.AgentSkillInventoryRepository
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 第二阶段统计界面的唯一数据入口，界面不直接读取 Room、诊断目录或操作学习数据库。 */
class AgentAnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val analyticsRepository = AgentAnalyticsRepository.get(application)
    private val skillInventoryRepository = AgentSkillInventoryRepository.get(application)

    val state: StateFlow<AgentAnalyticsSnapshot> = combine(
        analyticsRepository.state,
        skillInventoryRepository.state,
    ) { analytics, skills ->
        analytics.copy(skillInventory = skills)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        initialValue = analyticsRepository.state.value.copy(
            skillInventory = skillInventoryRepository.state.value,
        ),
    )
}
