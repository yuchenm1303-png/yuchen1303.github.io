package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.LedgerRecord
import com.yuchen.ailedger.model.LedgerRecordType
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.StatSummary
import com.yuchen.ailedger.model.ToolEntry
import com.yuchen.ailedger.model.latestOpenGlDefaultBorderStyle

interface AssistantRepository {
    fun initialState(): AssistantUiState
}

class ProductionAssistantRepository : AssistantRepository {
    override fun initialState(): AssistantUiState {
        return AssistantUiState(
            glassBorderStyle = latestOpenGlDefaultBorderStyle(),
            messages = listOf(
                ChatMessage(
                    id = "assistant-welcome",
                    text = "你好，我是你的 AI 助手。直接输入一句话，我可以帮你整理记账、提醒、导航、识图和应用入口。",
                    role = MessageRole.Assistant
                )
            ),
            tools = defaultToolEntries()
        )
    }
}

class PreviewAssistantRepository : AssistantRepository {
    override fun initialState(): AssistantUiState {
        return AssistantUiState(
            glassBorderStyle = latestOpenGlDefaultBorderStyle(),
            stats = listOf(
                StatSummary("今日支出", "¥47.00"),
                StatSummary("本月结余", "¥52.50")
            ),
            messages = listOf(
                ChatMessage(
                    id = "assistant-welcome",
                    text = "你好，我是你的 AI 助手。直接输入一句话，我可以帮你整理记账、提醒、导航、识图和应用入口。",
                    role = MessageRole.Assistant
                )
            ),
            tools = defaultToolEntries(),
            ledgerRecords = listOf(
                LedgerRecord("record-coffee", "咖啡", 12f, LedgerRecordType.Expense, "饮品", "今天"),
                LedgerRecord("record-lunch", "午饭", 18f, LedgerRecordType.Expense, "餐饮", "今天"),
                LedgerRecord("record-metro", "地铁", 4f, LedgerRecordType.Expense, "交通", "昨天"),
                LedgerRecord("record-parttime", "兼职收入", 120f, LedgerRecordType.Income, "工资", "昨天")
            )
        )
    }
}

private fun defaultToolEntries(): List<ToolEntry> = listOf(
    ToolEntry("股票行情", "A股看盘、分时、盘口和资金"),
    ToolEntry("账单中心", "查看和管理收入支出"),
    ToolEntry("数据统计", "按周、月、年查看趋势"),
    ToolEntry("提醒闹钟", "创建提醒和闹钟"),
    ToolEntry("应用控制", "打开常用应用入口"),
    ToolEntry("快捷指令", "保存常用任务"),
    ToolEntry("任务记录", "查看助手执行历史")
)
