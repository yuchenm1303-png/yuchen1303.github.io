package com.yuchen.ailedger.data

import android.content.Context
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        context?.let(LedgerStartupRestore::schedule)
        return AssistantUiState(
            glassBorderStyle = latestOpenGlDefaultBorderStyle(),
            messages = restoredMessages.ifEmpty { listOf(createWelcomeMessage()) },
            tools = defaultToolEntries()
        )
    }
}

/**
 * 账本并不参与首页首帧。历史 JSON 解析和 SharedPreferences 首次装载放到 IO，完成后再在
 * 主线程一次性发布到 Compose bridge，避免 ViewModel 构造期间阻塞首次组合。
 */
private object LedgerStartupRestore {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun schedule(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            val store = LedgerStore(appContext)
            val records = store.loadRecords()
            val budget = store.loadBudget()
            withContext(Dispatchers.Main.immediate) {
                LedgerStateBridge.update(records, budget)
            }
        }
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
            messages = listOf(createWelcomeMessage()),
            tools = defaultToolEntries(),
        )
    }
}

internal fun createWelcomeMessage(id: String = "assistant-welcome"): ChatMessage {
    return ChatMessage(
        id = id,
        text = WELCOME_MESSAGE_TEXTS.random(),
        role = MessageRole.Assistant
    )
}

private fun defaultToolEntries(): List<ToolEntry> = ToolDestination.entries.map { ToolEntry(it) }
