package com.yuchen.ailedger.data

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.LedgerStateBridge
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.StatSummary
import com.yuchen.ailedger.model.ToolDestination
import com.yuchen.ailedger.model.ToolEntry
import com.yuchen.ailedger.model.latestOpenGlDefaultBorderStyle
import com.yuchen.ailedger.service.NotificationChatStore

interface AssistantRepository {
    fun initialState(): AssistantUiState
}

class ProductionAssistantRepository : AssistantRepository {
    override fun initialState(): AssistantUiState {
        val context = AiLedgerApplication.contextOrNull()
        val restoredMessages = context
            ?.let { NotificationChatStore.load(it).messages }
            .orEmpty()
        context?.let {
            val ledgerStore = LedgerStore(it)
            LedgerStateBridge.update(ledgerStore.loadRecords(), ledgerStore.loadBudget())
        }
        return AssistantUiState(
            glassBorderStyle = latestOpenGlDefaultBorderStyle(),
            messages = restoredMessages.ifEmpty { listOf(welcomeMessage()) },
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
            messages = listOf(welcomeMessage()),
            tools = defaultToolEntries(),
        )
    }
}

private fun welcomeMessage(): ChatMessage {
    return ChatMessage(
        id = "assistant-welcome",
        text = "你好，我是你的 AI 助手。直接输入一句话，我可以帮你整理记账、提醒、导航、识图和应用入口。",
        role = MessageRole.Assistant
    )
}

private fun defaultToolEntries(): List<ToolEntry> = ToolDestination.entries.map { ToolEntry(it) }
