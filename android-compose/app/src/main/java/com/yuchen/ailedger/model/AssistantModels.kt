package com.yuchen.ailedger.model

enum class MessageRole { User, Assistant }

data class ChatMessage(
    val id: Long,
    val role: MessageRole,
    val content: String,
    val actionHint: String? = null,
    val command: AssistantCommand? = null,
    val ledgerDraft: LedgerDraft? = null
)

enum class CommandType {
    SetAlarm,
    Navigate,
    OpenApp,
    LedgerDraft,
    Chat
}

data class AssistantCommand(
    val type: CommandType,
    val title: String,
    val description: String,
    val primaryActionLabel: String,
    val payload: Map<String, String> = emptyMap()
)

data class LedgerDraft(
    val title: String,
    val amount: Double,
    val type: String = "expense",
    val category: String = "其他"
)
