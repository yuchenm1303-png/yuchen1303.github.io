package com.yuchen.ailedger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.yuchen.ailedger.data.AgentAnalyticsRepository
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import kotlinx.coroutines.flow.StateFlow

/** 第二阶段统计界面的唯一数据入口，界面不直接读取 Room 或诊断目录。 */
class AgentAnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AgentAnalyticsRepository.get(application)

    val state: StateFlow<AgentAnalyticsSnapshot> = repository.state
}
