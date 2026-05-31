package com.yuchen.ailedger

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.AssistantPreferencesStore
import com.yuchen.ailedger.data.AssistantRepository
import com.yuchen.ailedger.data.CustomBackgroundStore
import com.yuchen.ailedger.data.ProductionAssistantRepository
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BUILTIN_THEME_BACKGROUND_PATH
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.LedgerRecord
import com.yuchen.ailedger.model.LedgerRecordType
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.model.ModelCardGlassStyle
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.service.AiChatResponse
import com.yuchen.ailedger.service.AiWorkerClient
import com.yuchen.ailedger.service.MobileCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AssistantViewModel(
    application: Application,
    private val repository: AssistantRepository,
    private val preferencesStore: AssistantPreferencesStore,
    private val aiWorkerClient: AiWorkerClient,
    private val customBackgroundStore: CustomBackgroundStore
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, ProductionAssistantRepository(), AssistantPreferencesStore(application), AiWorkerClient(), CustomBackgroundStore(application))

    var uiState by mutableStateOf(repository.initialState())
        private set

    private var activeSendJob: Job? = null
    private var activePendingMessageId: String? = null

    init {
        viewModelScope.launch {
            preferencesStore.preferencesFlow.collect { preferences ->
                uiState = uiState.copy(
                    quality = preferences.quality,
                    showPreviewConversation = preferences.showPreviewConversation,
                    glassPreset = preferences.glassPreset,
                    backgroundTheme = preferences.backgroundTheme,
                    customBackgroundPath = preferences.customBackgroundPath,
                    glassIntensity = preferences.glassIntensity,
                    motionIntensity = preferences.motionIntensity,
                    rainbowPrismStyle = preferences.rainbowPrismStyle
                )
            }
        }
    }

    val aiEndpoint: String get() = aiWorkerClient.endpoint

    fun selectTab(tab: AppTab) { uiState = uiState.copy(currentTab = tab) }
    fun openTool(title: String) { uiState = uiState.copy(selectedToolTitle = title) }
    fun closeTool() { uiState = uiState.copy(selectedToolTitle = null) }
    fun updateLedgerDraftTitle(value: String) { uiState = uiState.copy(ledgerDraftTitle = value) }
    fun updateLedgerDraftAmount(value: String) { uiState = uiState.copy(ledgerDraftAmount = value.filter { it.isDigit() || it == '.' }.take(10)) }
    fun selectLedgerDraftType(type: LedgerRecordType) { uiState = uiState.copy(ledgerDraftType = type) }
    fun selectLedgerCategory(category: String) { uiState = uiState.copy(ledgerDraftCategory = category) }
    fun updateLedgerBudget(value: String) { uiState = uiState.copy(ledgerBudgetText = value.filter { it.isDigit() || it == '.' }.take(10)) }

    fun addLedgerRecord() {
        val amount = uiState.ledgerDraftAmount.toFloatOrNull() ?: return
        val title = uiState.ledgerDraftTitle.trim().ifBlank { if (uiState.ledgerDraftType == LedgerRecordType.Income) "未命名收入" else "未命名支出" }
        if (amount <= 0f) return
        val record = LedgerRecord("record-${System.currentTimeMillis()}", title.take(24), amount, uiState.ledgerDraftType, uiState.ledgerDraftCategory, "今天")
        uiState = uiState.copy(ledgerRecords = listOf(record) + uiState.ledgerRecords, ledgerDraftTitle = "", ledgerDraftAmount = "")
        appendAssistantNotice("已添加账单：${record.title} ${formatCurrency(record.amount)}。", source = "local_ledger")
    }

    fun deleteLedgerRecord(id: String) { uiState = uiState.copy(ledgerRecords = uiState.ledgerRecords.filterNot { it.id == id }) }
    fun updateComposer(text: String) {
        if (text == uiState.composerText) return
        uiState = uiState.copy(composerText = text)
    }
    fun submitComposer() {
        val text = uiState.composerText.trim()
        if (text.isBlank() || uiState.isSending) return
        sendUserCommand(text)
    }

    fun sendUserCommand(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank() || uiState.isSending) return
        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(id = "user-$now", text = cleanText, role = MessageRole.User)
        val pendingMessage = ChatMessage(id = "assistant-${now + 1}", text = "正在思考…", role = MessageRole.Assistant, status = MessageStatus.Sending, source = "cloud_ai", modelLabel = uiState.selectedModel.label)
        val requestMessages = uiState.messages + userMessage
        uiState = uiState.copy(messages = requestMessages + pendingMessage, composerText = "", isSending = true)
        sendPendingRequest(requestMessages, pendingMessage)
    }

    fun previewMobileCommand(userText: String, command: MobileCommand) {
        val cleanText = userText.trim()
        if (cleanText.isBlank() || uiState.isSending) return
        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(id = "user-$now", text = cleanText, role = MessageRole.User)
        val assistantMessage = ChatMessage(
            id = "assistant-${now + 1}",
            text = buildString {
                append(commandReplyPrefix(command))
                append("\n\n")
                append("动作：${command.title}\n")
                append("详情：${command.summary}\n\n")
                append("回复“确认”执行，或回复“取消”。")
            },
            role = MessageRole.Assistant,
            source = "local_mobile",
            modelLabel = "待确认"
        )
        uiState = uiState.copy(
            messages = uiState.messages + userMessage + assistantMessage,
            composerText = "",
            isSending = false
        )
    }

    fun cancelMobileCommand(userText: String, command: MobileCommand) {
        val cleanText = userText.trim()
        if (cleanText.isBlank() || uiState.isSending) return
        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(id = "user-$now", text = cleanText, role = MessageRole.User)
        val assistantMessage = ChatMessage(
            id = "assistant-${now + 1}",
            text = "已取消这个手机动作：${command.title} · ${command.summary}。",
            role = MessageRole.Assistant,
            source = "local_mobile",
            modelLabel = "已取消"
        )
        uiState = uiState.copy(
            messages = uiState.messages + userMessage + assistantMessage,
            composerText = "",
            isSending = false
        )
    }

    fun acceptExecutedMobileCommand(userText: String, command: MobileCommand, ok: Boolean, resultMessage: String) {
        val cleanText = userText.trim()
        if (cleanText.isBlank() || uiState.isSending) return
        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(id = "user-$now", text = cleanText, role = MessageRole.User)
        val assistantText = buildString {
            append(commandReplyPrefix(command))
            append("\n\n")
            append(if (ok) "执行结果：$resultMessage" else "执行失败：$resultMessage")
        }
        val assistantMessage = ChatMessage(
            id = "assistant-${now + 1}",
            text = assistantText,
            role = MessageRole.Assistant,
            source = "local_mobile",
            modelLabel = "手机动作"
        )
        uiState = uiState.copy(
            messages = uiState.messages + userMessage + assistantMessage,
            composerText = "",
            isSending = false
        )
    }

    private fun commandReplyPrefix(command: MobileCommand): String = when (command) {
        is MobileCommand.SetAlarm -> "我理解为要${command.summary}设置闹钟。"
        is MobileCommand.OpenApp -> "我理解为要打开“${command.appName}”。"
        is MobileCommand.Navigate -> "我理解为要导航到“${command.destination}”。"
    }

    fun retryMessage(messageId: String) {
        if (uiState.isSending) return
        val assistantIndex = uiState.messages.indexOfFirst { it.id == messageId && it.role == MessageRole.Assistant }
        if (assistantIndex <= 0) return
        val previousUser = uiState.messages.take(assistantIndex).lastOrNull { it.role == MessageRole.User && it.text.isNotBlank() } ?: return
        val requestMessages = uiState.messages.take(assistantIndex)
        val pendingMessage = ChatMessage(id = "assistant-${System.currentTimeMillis()}", text = "正在重新生成…", role = MessageRole.Assistant, status = MessageStatus.Sending, source = "cloud_ai", modelLabel = uiState.selectedModel.label)
        uiState = uiState.copy(messages = requestMessages + pendingMessage, composerText = "", isSending = true)
        sendPendingRequest(requestMessages = requestMessages.ifEmpty { listOf(previousUser) }, pendingMessage = pendingMessage)
    }

    fun stopGenerating() {
        if (!uiState.isSending) return
        val pendingId = activePendingMessageId
        activeSendJob?.cancel(CancellationException("user stopped generation"))
        if (pendingId != null) markMessageStopped(pendingId)
        activeSendJob = null
        activePendingMessageId = null
        uiState = uiState.copy(isSending = false)
    }

    private fun sendPendingRequest(requestMessages: List<ChatMessage>, pendingMessage: ChatMessage) {
        activeSendJob?.cancel()
        val selectedModel = uiState.selectedModel
        val onlineEnabled = uiState.onlineEnabled
        activePendingMessageId = pendingMessage.id
        activeSendJob = viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    aiWorkerClient.sendChat(requestMessages, selectedModel, onlineEnabled)
                }
                if (activePendingMessageId == pendingMessage.id) {
                    replaceMessage(
                        pendingMessage.id,
                        pendingMessage.copy(
                            text = decorateReply(response, onlineEnabled),
                            status = MessageStatus.Sent,
                            source = response.source,
                            model = response.model,
                            modelLabel = response.modelLabel ?: selectedModel.label,
                            version = response.version,
                            errorText = null,
                            webSources = response.webSources,
                            structuredData = response.structuredData,
                            searchUsed = response.searchUsed,
                            searchProvider = response.searchProvider
                        )
                    )
                }
            } catch (error: CancellationException) {
                if (activePendingMessageId == pendingMessage.id) markMessageStopped(pendingMessage.id)
            } catch (error: Throwable) {
                if (activePendingMessageId == pendingMessage.id) {
                    val friendly = error.message?.takeIf { it.isNotBlank() } ?: "云端 AI 请求失败，请检查网络或 Worker 配置。"
                    replaceMessage(pendingMessage.id, pendingMessage.copy(text = friendly, status = MessageStatus.Failed, source = "cloud_fetch_failed", modelLabel = selectedModel.label, errorText = friendly))
                }
            } finally {
                if (activePendingMessageId == pendingMessage.id) {
                    activeSendJob = null
                    activePendingMessageId = null
                    uiState = uiState.copy(isSending = false)
                }
            }
        }
    }

    private fun markMessageStopped(id: String) {
        val current = uiState.messages.firstOrNull { it.id == id } ?: return
        replaceMessage(
            id,
            current.copy(
                text = "已暂停生成。",
                status = MessageStatus.Sent,
                source = "local",
                modelLabel = "已暂停",
                errorText = null
            )
        )
    }

    fun insertCommandDraft(text: String) { uiState = uiState.copy(composerText = text) }
    fun cycleModel() {
        if (uiState.isSending) return
        val next = when (uiState.selectedModel) {
            ChatModel.Auto -> ChatModel.Workers
            ChatModel.Workers -> ChatModel.Gemini
            ChatModel.Gemini -> ChatModel.Kimi
            ChatModel.Kimi -> ChatModel.Mistral
            ChatModel.Mistral -> ChatModel.Auto
            else -> ChatModel.Auto
        }
        selectModel(next)
    }
    fun selectModel(model: ChatModel) {
        if (uiState.isSending) return
        uiState = uiState.copy(selectedModel = model, selectedModelLabel = model.label)
        appendAssistantNotice("已切换为 ${model.label}。", source = "local")
    }
    fun toggleOnline() {
        if (uiState.isSending) return
        val enabled = !uiState.onlineEnabled
        uiState = uiState.copy(onlineEnabled = enabled)
        appendAssistantNotice(if (enabled) "已开启联网开关。下一步会随请求传给 Worker。" else "已关闭联网开关。", source = "local")
    }
    fun clearChat() { if (!uiState.isSending) uiState = uiState.copy(messages = emptyList(), composerText = "", isSending = false) }
    fun onImagePickedForAssistant(uri: Uri?) { if (uri != null) appendAssistantNotice("已选择图片。下一步可以把它接入识图接口，第一阶段先保留图片入口。", source = "local") }
    fun appendAssistantNotice(text: String, source: String? = null) {
        uiState = uiState.copy(messages = uiState.messages + ChatMessage(id = "assistant-${System.currentTimeMillis()}", text = text, role = MessageRole.Assistant, source = source, modelLabel = sourceLabel(source)))
    }
    private fun replaceMessage(id: String, next: ChatMessage) { uiState = uiState.copy(messages = uiState.messages.map { if (it.id == id) next else it }) }
    private fun sourceLabel(source: String?): String? = when (source) { "local" -> "本地"; "local_ledger" -> "本地记账"; "local_mobile" -> "手机动作"; "cloud_fetch_failed" -> "云端连接失败"; else -> null }
    private fun formatCurrency(value: Float): String = "¥${String.format("%.2f", value)}"

    private fun decorateReply(response: AiChatResponse, onlineEnabled: Boolean): String {
        val sections = mutableListOf(response.reply.trim())

        response.structuredData?.let { data ->
            val metrics = data.metrics.take(6).joinToString("\n") { metric ->
                val unit = metric.unit?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                val detail = metric.detail?.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
                "- ${metric.label}: ${metric.value}$unit$detail"
            }
            val header = listOfNotNull(data.title, data.subtitle, data.timestamp).joinToString(" · ")
            val block = buildString {
                append("实时数据：")
                append(header.ifBlank { data.type })
                if (metrics.isNotBlank()) append("\n").append(metrics)
                data.rawText?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
            }
            sections += block
        }

        if (response.webSources.isNotEmpty()) {
            val provider = response.searchProvider?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            val sources = response.webSources.take(4).mapIndexed { index, source ->
                val domain = source.domain.ifBlank { source.url.substringAfter("://").substringBefore('/') }
                val title = source.title.ifBlank { domain.ifBlank { "来源 ${index + 1}" } }
                val date = source.publishedAt?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                "${index + 1}. $title${if (domain.isNotBlank()) " · $domain" else ""}$date"
            }.joinToString("\n")
            sections += "联网来源$provider:\n$sources"
        }

        if (onlineEnabled && response.structuredData == null && response.webSources.isEmpty()) {
            sections += "联网诊断：App 已发送联网请求，但后端没有返回 structuredData 或 sources[]。请确认阿里云函数已部署 web-data-v2 版本，并且普通搜索已配置 TAVILY_API_KEY。"
        }

        return sections.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    fun selectQuality(quality: RenderQuality) { uiState = uiState.copy(quality = quality); viewModelScope.launch { preferencesStore.setRenderQuality(quality) } }
    fun setShowPreviewConversation(showPreviewConversation: Boolean) { uiState = uiState.copy(showPreviewConversation = showPreviewConversation); viewModelScope.launch { preferencesStore.setShowPreviewConversation(showPreviewConversation) } }
    fun setBackgroundTheme(backgroundTheme: BackgroundTheme) {
        uiState = uiState.copy(backgroundTheme = backgroundTheme, customBackgroundPath = BUILTIN_THEME_BACKGROUND_PATH)
        viewModelScope.launch { preferencesStore.setBackgroundTheme(backgroundTheme); preferencesStore.setCustomBackgroundPath(null) }
    }
    fun importCustomBackground(uri: Uri) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) { customBackgroundStore.saveFromUri(uri) }
            uiState = uiState.copy(customBackgroundPath = savedPath)
            preferencesStore.setCustomBackgroundPath(savedPath)
        }
    }
    fun clearCustomBackground() {
        uiState = uiState.copy(customBackgroundPath = null)
        viewModelScope.launch { withContext(Dispatchers.IO) { customBackgroundStore.clearCustomBackground() }; preferencesStore.setCustomBackgroundPath(null) }
    }
    fun setBackdropDebugParams(params: BackdropDebugParams) { uiState = uiState.copy(backdropParams = params) }
    fun updateBackdropDebugParams(block: (BackdropDebugParams) -> BackdropDebugParams) { setBackdropDebugParams(block(uiState.backdropParams)) }
    fun setGlassBorderStyle(style: GlassBorderStyle) { uiState = uiState.copy(glassBorderStyle = style) }
    fun updateGlassBorderStyle(block: (GlassBorderStyle) -> GlassBorderStyle) { setGlassBorderStyle(block(uiState.glassBorderStyle)) }
    fun setModelCardGlassStyle(style: ModelCardGlassStyle) { uiState = uiState.copy(modelCardGlassStyle = style) }

    fun setRainbowPrismStyle(style: RainbowPrismStyle) {
        val minValue = minOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f)
        val maxValue = maxOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f)
        val clamped = RainbowPrismStyle(
            overall = style.overall.coerceIn(0f, 2f),
            edgeHighlight = style.edgeHighlight.coerceIn(0f, 2f),
            sweepMin = minValue,
            sweepMax = maxValue,
            rainbowHalo = style.rainbowHalo.coerceIn(0f, 2f)
        )
        uiState = uiState.copy(rainbowPrismStyle = clamped)
        viewModelScope.launch { preferencesStore.setRainbowPrismStyle(clamped) }
    }

    fun setGlassIntensity(value: Float) {
        val clamped = value.coerceIn(0.6f, 1.4f)
        uiState = uiState.copy(glassIntensity = clamped, glassPreset = detectPreset(clamped, uiState.motionIntensity))
        viewModelScope.launch { preferencesStore.setGlassIntensity(clamped) }
    }
    fun setMotionIntensity(value: Float) {
        val clamped = value.coerceIn(0f, 1.4f)
        uiState = uiState.copy(motionIntensity = clamped, glassPreset = detectPreset(uiState.glassIntensity, clamped))
        viewModelScope.launch { preferencesStore.setMotionIntensity(clamped) }
    }
    fun setGlassPreset(preset: GlassPreset) {
        uiState = uiState.copy(glassPreset = preset, glassIntensity = preset.glassIntensity, motionIntensity = preset.motionIntensity)
        viewModelScope.launch { preferencesStore.setGlassPreset(preset); preferencesStore.setGlassIntensity(preset.glassIntensity); preferencesStore.setMotionIntensity(preset.motionIntensity) }
    }
    private fun detectPreset(glass: Float, motion: Float): GlassPreset = GlassPreset.entries.minByOrNull { val dg = glass - it.glassIntensity; val dm = motion - it.motionIntensity; dg * dg + dm * dm } ?: GlassPreset.Liquid
}
