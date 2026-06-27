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

private val WELCOME_MESSAGE_TEXTS = listOf(
    "你好，我是你的 AI 助手[[AI_LEDGER_INLINE_STICKER:soft_smile]]。你可以直接告诉我需要处理的事情。",
    "欢迎使用 AI 助手[[AI_LEDGER_INLINE_STICKER:confirm_yes]]。请告诉我你希望完成什么。",
    "我已准备就绪[[AI_LEDGER_INLINE_STICKER:confident_ready]]。可以开始处理你的问题或任务。",
    "你好，有什么需要协助的吗[[AI_LEDGER_INLINE_STICKER:soft_smile]]？",
    "请直接描述你的需求[[AI_LEDGER_INLINE_STICKER:got_it_point]]。我会尽可能清晰地为你处理。",
    "欢迎回来[[AI_LEDGER_INLINE_STICKER:soft_smile]]。今天需要我协助处理什么？"
)

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
        text = WELCOME_MESSAGE_TEXTS.random(),
        role = MessageRole.Assistant
    )
}

private fun defaultToolEntries(): List<ToolEntry> = ToolDestination.entries.map { ToolEntry(it) }
