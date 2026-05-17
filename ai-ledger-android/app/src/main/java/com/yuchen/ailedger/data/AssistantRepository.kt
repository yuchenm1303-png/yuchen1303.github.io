package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.StatSummary
import com.yuchen.ailedger.model.ToolEntry

interface AssistantRepository {
    fun initialState(): AssistantUiState
}

class PreviewAssistantRepository : AssistantRepository {
    override fun initialState(): AssistantUiState {
        return AssistantUiState(
            stats = listOf(
                StatSummary("今日支出", "¥47.00"),
                StatSummary("本月结余", "¥52.50")
            ),
            messages = listOf(
                ChatMessage(
                    id = "assistant-welcome",
                    text = "你好，我是你的 AI 助手。你可以让我记账、查账单、设置提醒、打开应用，也可以直接和我聊天。",
                    role = MessageRole.Assistant
                ),
                ChatMessage(
                    id = "user-navigation",
                    text = "导航回家",
                    role = MessageRole.User
                ),
                ChatMessage(
                    id = "assistant-reply",
                    text = "我在，直接和我说就行。",
                    role = MessageRole.Assistant
                )
            ),
            tools = listOf(
                ToolEntry("账单中心", "查看和管理收入支出"),
                ToolEntry("数据统计", "按周、月、年查看趋势"),
                ToolEntry("提醒闹钟", "创建提醒和闹钟"),
                ToolEntry("应用控制", "打开微信、支付宝等应用"),
                ToolEntry("快捷指令", "保存常用任务"),
                ToolEntry("任务记录", "查看助手执行历史")
            )
        )
    }
}
