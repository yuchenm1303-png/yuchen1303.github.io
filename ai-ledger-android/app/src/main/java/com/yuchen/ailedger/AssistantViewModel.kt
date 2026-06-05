package com.yuchen.ailedger

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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
import com.yuchen.ailedger.model.ChatAttachment
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.ComposerAttachment
import com.yuchen.ailedger.model.ComposerAttachmentStatus
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.LedgerRecord
import com.yuchen.ailedger.model.LedgerRecordType
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.model.ModelCardGlassStyle
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.service.AgentTaskRunResult
import com.yuchen.ailedger.service.AgentTaskRunner
import com.yuchen.ailedger.service.AiAgentAccessibilityService
import com.yuchen.ailedger.service.AiChatResponse
import com.yuchen.ailedger.service.AiWorkerClient
import com.yuchen.ailedger.service.CloudAgentAction
import com.yuchen.ailedger.service.CloudMobileAction
import com.yuchen.ailedger.service.CloudPreferenceUpdate
import com.yuchen.ailedger.service.InstalledAppIndex
import com.yuchen.ailedger.service.MobileCommand
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ASSISTANT_IMAGE_MAX_DIMENSION = 1280
private const val ASSISTANT_IMAGE_JPEG_QUALITY = 84
private const val ASSISTANT_IMAGE_MAX_BYTES = 6 * 1024 * 1024
private const val VISUAL_ATTACHMENT_STATUS_PREFIX = "视觉附件 · "

class AssistantViewModel(
    application: Application,
    private val repository: AssistantRepository,
    private val preferencesStore: AssistantPreferencesStore,
    private val aiWorkerClient: AiWorkerClient,
    private val customBackgroundStore: CustomBackgroundStore
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        ProductionAssistantRepository(),
        AssistantPreferencesStore(application),
        AiWorkerClient(),
        CustomBackgroundStore(application)
    )

    var uiState by mutableStateOf(repository.initialState())
        private set

    private var activeSendJob: Job? = null
    private var activePendingMessageId: String? = null
    private val localIdSeed = AtomicLong(System.currentTimeMillis())

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
                    rainbowPrismStyle = preferences.rainbowPrismStyle,
                    navigationHomeAddress = preferences.navigationHomeAddress,
                    navigationSchoolAddress = preferences.navigationSchoolAddress,
                    navigationCompanyAddress = preferences.navigationCompanyAddress,
                    navigationDormAddress = preferences.navigationDormAddress
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
        val record = LedgerRecord(nextLocalId("record"), title.take(24), amount, uiState.ledgerDraftType, uiState.ledgerDraftCategory, "今天")
        uiState = uiState.copy(ledgerRecords = listOf(record) + uiState.ledgerRecords, ledgerDraftTitle = "", ledgerDraftAmount = "")
        appendAssistantNotice("已添加账单：${record.title} ${formatCurrency(record.amount)}。", source = "local_ledger")
    }

    fun deleteLedgerRecord(id: String) { uiState = uiState.copy(ledgerRecords = uiState.ledgerRecords.filterNot { it.id == id }) }

    fun updateComposer(text: String) {
        if (text == uiState.composerText) return
        uiState = uiState.copy(composerText = text)
    }

    fun submitComposer() { sendUserCommand(uiState.composerText) }

    fun sendUserCommand(text: String) {
        val visibleText = text.trim()
        val cleanText = stripVisualAttachmentStatus(visibleText)
        val hasPreparingAttachment = uiState.composerAttachments.any { it.status == ComposerAttachmentStatus.Preparing || it.status == ComposerAttachmentStatus.Uploading }
        val attachments = uiState.composerAttachments.mapNotNull { it.takeIf { item -> item.isReady }?.toChatAttachment() }
        if (uiState.isSending || hasPreparingAttachment) return
        if (cleanText.isBlank() && attachments.isEmpty()) return
        if (attachments.isEmpty() && isAgentEntryCommand(cleanText)) { previewAgentObservation(cleanText); return }
        if (attachments.isEmpty() && shouldPlanAsAgentTask(cleanText)) { requestAgentNextStep(cleanText); return }

        val baseText = cleanText.ifBlank { "请根据视觉附件进行分析。" }
        val userText = if (attachments.isNotEmpty()) listOf(baseText, attachmentMessageFlag(attachments)).joinToString("\n\n") else baseText
        val hasImages = attachments.any { it.mimeType.startsWith("image/") }
        val userMessage = ChatMessage(id = nextLocalId("user"), text = userText, role = MessageRole.User, attachments = attachments)
        val pendingMessage = ChatMessage(id = nextLocalId("assistant"), text = if (hasImages) "正在理解视觉附件…" else "正在思考…", role = MessageRole.Assistant, status = MessageStatus.Sending, source = "cloud_ai", modelLabel = if (hasImages) "Qwen 视觉理解" else uiState.selectedModel.label)
        val requestMessages = uiState.messages + userMessage
        uiState = uiState.copy(messages = requestMessages + pendingMessage, composerText = "", composerAttachments = emptyList(), isSending = true)
        sendPendingRequest(requestMessages, pendingMessage)
    }

    fun previewAgentObservation(userText: String) {
        val cleanText = userText.trim().ifBlank { "观察当前屏幕" }
        if (uiState.isSending) return
        val userMessage = ChatMessage(id = nextLocalId("user"), text = cleanText, role = MessageRole.User)
        val assistantMessage = buildAgentObservationMessage(nextLocalId("assistant"))
        uiState = uiState.copy(messages = uiState.messages + userMessage + assistantMessage, composerText = "", isSending = false)
    }

    fun requestAgentNextStep(goal: String) {
        val cleanGoal = goal.trim().take(240)
        if (cleanGoal.isBlank() || uiState.isSending) return
        val userMessage = ChatMessage(id = nextLocalId("user"), text = cleanGoal, role = MessageRole.User)
        val pendingMessage = ChatMessage(
            id = nextLocalId("assistant"),
            text = "正在执行手机智能体任务…",
            role = MessageRole.Assistant,
            status = MessageStatus.Sending,
            source = "local_agent",
            modelLabel = "手机智能体"
        )
        uiState = uiState.copy(messages = uiState.messages + userMessage + pendingMessage, composerText = "", isSending = true)
        activeSendJob?.cancel()
        activePendingMessageId = pendingMessage.id
        activeSendJob = viewModelScope.launch {
            try {
                val message = buildAgentTaskMessage(pendingMessage.id, cleanGoal)
                if (activePendingMessageId == pendingMessage.id) replaceMessage(pendingMessage.id, message)
            } catch (error: CancellationException) {
                if (activePendingMessageId == pendingMessage.id) markMessageStopped(pendingMessage.id)
            } catch (error: Throwable) {
                val friendly = error.message?.takeIf { it.isNotBlank() } ?: "智能体执行失败，请稍后重试。"
                if (activePendingMessageId == pendingMessage.id) replaceMessage(pendingMessage.id, pendingMessage.copy(text = friendly, status = MessageStatus.Failed, source = "local_agent", modelLabel = "执行失败", errorText = friendly))
            } finally {
                if (activePendingMessageId == pendingMessage.id) {
                    activeSendJob = null
                    activePendingMessageId = null
                    uiState = uiState.copy(isSending = false)
                }
            }
        }
    }

    private suspend fun buildAgentTaskMessage(id: String, goal: String): ChatMessage {
        if (!AiAgentAccessibilityService.isConnected()) return buildAgentGuideMessage(id)
        val appIndex = InstalledAppIndex(getApplication<Application>())
        val result = AgentTaskRunner(aiWorkerClient, appIndex).run(goal = goal, modelPreference = uiState.selectedModel, maxSteps = 8)
        return buildAgentRunMessage(id, goal, result)
    }

    private fun buildAgentRunMessage(id: String, goal: String, result: AgentTaskRunResult): ChatMessage {
        val status = when {
            result.completed -> "已完成"
            result.stoppedForConfirmation -> "等待确认"
            else -> "已暂停"
        }
        val text = buildString {
            append("手机智能体任务执行\n\n")
            append("目标：$goal\n")
            append("状态：$status\n")
            append("结果：${result.message}\n")
            if (result.logs.isNotEmpty()) {
                append("\n执行记录：\n")
                result.logs.forEach { log ->
                    append("${log.index}. ${log.step.typeLabel}")
                    log.step.appName?.let { append(" · $it") }
                    log.step.targetNodeId?.let { append(" · 节点 $it") }
                    log.step.targetText?.let { append(" · $it") }
                    log.step.text?.let { append(" · 输入“$it”") }
                    log.step.direction?.let { append(" · $it") }
                    append("\n")
                    log.step.reason?.let { append("   原因：$it\n") }
                    log.execution?.let { append("   执行：${it.message}\n") }
                }
            }
            if (result.stoppedForConfirmation) append("\n该动作涉及风险或需要确认，已停止自动执行。")
        }
        return ChatMessage(id = id, text = text, role = MessageRole.Assistant, status = MessageStatus.Sent, source = "local_agent", modelLabel = "手机智能体")
    }

    private fun buildAgentObservationMessage(id: String): ChatMessage {
        val observation = AiAgentAccessibilityService.captureFreshSnapshot()
        if (!observation.enabled || !observation.serviceConnected) return buildAgentGuideMessage(id)
        val textPreview = observation.textItems.take(6).joinToString(" / ").ifBlank { "暂无文字" }
        val assistantText = buildString {
            append("手机智能体观察卡\n\n")
            append("状态：已连接\n")
            append("当前应用：${observation.packageName.ifBlank { "未知" }}\n")
            append("可读节点：${observation.nodeCount} 个\n")
            append("文字：${observation.textItems.size} 条\n")
            append("按钮：${observation.clickableItems.size} 个\n")
            append("输入框：${observation.inputItems.size} 个\n")
            append("可滚动区域：${observation.scrollableItems.size} 个\n")
            append("屏幕文字预览：$textPreview\n\n")
            append("当前为按需快照模式：只有请求观察或规划时才抓取节点，不会持续后台扫描。\n")
            append("你现在可以直接说：帮我打开微信找到文件传输助手。")
        }
        return ChatMessage(id = id, text = assistantText, role = MessageRole.Assistant, source = "local_agent", modelLabel = "手机智能体")
    }

    private fun buildAgentGuideMessage(id: String): ChatMessage {
        AgentAccessibilityGuideActivity.open(getApplication<Application>())
        val guideText = buildString {
            append("手机智能体开启引导\n\n")
            append("状态：未开启\n")
            append("我已为你弹出开启引导。请点击弹窗里的“去开启”，然后在系统无障碍设置里打开“AI助手”。\n\n")
            append("开启后回到 App，可以直接测试：帮我打开微信找到文件传输助手。")
        }
        return ChatMessage(id = id, text = guideText, role = MessageRole.Assistant, source = "local_agent", modelLabel = "需要开启")
    }

    fun previewMobileCommand(userText: String, command: MobileCommand) {
        val cleanText = userText.trim()
        if (cleanText.isBlank() || uiState.isSending) return
        val userMessage = ChatMessage(id = nextLocalId("user"), text = cleanText, role = MessageRole.User)
        val assistantMessage = buildMobileCommandPreviewMessage(nextLocalId("assistant"), command)
        uiState = uiState.copy(messages = uiState.messages + userMessage + assistantMessage, composerText = "", isSending = false)
    }

    fun cancelMobileCommand(userText: String, command: MobileCommand) {
        val cleanText = userText.trim()
        if (cleanText.isBlank() || uiState.isSending) return
        val userMessage = ChatMessage(id = nextLocalId("user"), text = cleanText, role = MessageRole.User)
        val assistantMessage = ChatMessage(id = nextLocalId("assistant"), text = "已取消这个手机动作：${command.title} · ${command.summary}。", role = MessageRole.Assistant, source = "local_mobile", modelLabel = "已取消")
        uiState = uiState.copy(messages = uiState.messages + userMessage + assistantMessage, composerText = "", isSending = false)
    }

    fun acceptExecutedMobileCommand(userText: String, command: MobileCommand, ok: Boolean, resultMessage: String) {
        val cleanText = userText.trim()
        if (cleanText.isBlank() || uiState.isSending) return
        val userMessage = ChatMessage(id = nextLocalId("user"), text = cleanText, role = MessageRole.User)
        val assistantText = commandReplyPrefix(command) + "\n\n" + if (ok) "执行结果：$resultMessage" else "执行失败：$resultMessage"
        val assistantMessage = ChatMessage(id = nextLocalId("assistant"), text = assistantText, role = MessageRole.Assistant, source = "local_mobile", modelLabel = "手机动作")
        uiState = uiState.copy(messages = uiState.messages + userMessage + assistantMessage, composerText = "", isSending = false)
    }

    fun updateNavigationAddress(slot: String, address: String) {
        val cleanAddress = address.trim().take(80)
        uiState = when (slot) {
            "home" -> uiState.copy(navigationHomeAddress = cleanAddress)
            "school" -> uiState.copy(navigationSchoolAddress = cleanAddress)
            "company" -> uiState.copy(navigationCompanyAddress = cleanAddress)
            "dorm" -> uiState.copy(navigationDormAddress = cleanAddress)
            else -> uiState
        }
        viewModelScope.launch { preferencesStore.setNavigationAddress(slot, cleanAddress) }
    }

    fun saveNavigationAddress(userText: String, slot: String, address: String, label: String) {
        val cleanText = userText.trim()
        val cleanAddress = address.trim().take(80)
        if (cleanText.isBlank() || cleanAddress.isBlank() || uiState.isSending) return
        val userMessage = ChatMessage(id = nextLocalId("user"), text = cleanText, role = MessageRole.User)
        val assistantMessage = ChatMessage(id = nextLocalId("assistant"), text = "已保存导航偏好。\n\n动作：保存常用地址\n详情：$label · $cleanAddress", role = MessageRole.Assistant, source = "local_mobile", modelLabel = "导航偏好")
        uiState = uiState.copy(messages = uiState.messages + userMessage + assistantMessage, composerText = "", isSending = false)
        updateNavigationAddress(slot, cleanAddress)
    }

    private fun buildMobileCommandPreviewMessage(id: String, command: MobileCommand): ChatMessage {
        return ChatMessage(id = id, text = commandReplyPrefix(command) + "\n\n动作：${command.title}\n详情：${command.summary}\n\n回复“确认”执行，或回复“取消”。", role = MessageRole.Assistant, source = "local_mobile", modelLabel = "待确认")
    }

    private fun commandReplyPrefix(command: MobileCommand): String = when (command) {
        is MobileCommand.SetAlarm -> "我理解为要${command.summary}设置闹钟。"
        is MobileCommand.OpenApp -> "我理解为要打开“${command.appName}”。"
        is MobileCommand.Navigate -> "我理解为要导航到“${command.destination}”。"
    }

    private fun CloudMobileAction.toMobileCommand(): MobileCommand? = when (type) {
        "set_alarm" -> {
            val safeHour = hour?.takeIf { it in 0..23 } ?: return null
            val safeMinute = minute?.takeIf { it in 0..59 } ?: 0
            MobileCommand.SetAlarm(hour = safeHour, minute = safeMinute, label = label?.takeIf { it.isNotBlank() } ?: "AI 助手提醒", dateLabel = "今天")
        }
        "open_app" -> {
            val name = appName?.takeIf { it.isNotBlank() } ?: title?.takeIf { it.isNotBlank() } ?: return null
            MobileCommand.OpenApp(appName = name, packageName = packageName)
        }
        "navigate" -> {
            val target = destination?.takeIf { it.isNotBlank() } ?: return null
            MobileCommand.Navigate(destination = target, mode = "driving")
        }
        else -> null
    }

    private fun CloudAgentAction.canExecuteLocally(): Boolean = capability == "observe_screen"
    private fun applyCloudPreferenceUpdate(update: CloudPreferenceUpdate) { if (update.type == "navigation_address") updateNavigationAddress(update.slot, update.value) }

    fun retryMessage(messageId: String) {
        if (uiState.isSending) return
        val assistantIndex = uiState.messages.indexOfFirst { it.id == messageId && it.role == MessageRole.Assistant }
        if (assistantIndex <= 0) return
        val previousUser = uiState.messages.take(assistantIndex).lastOrNull { it.role == MessageRole.User && (it.text.isNotBlank() || it.hasImageAttachments) } ?: return
        val requestMessages = uiState.messages.take(assistantIndex)
        val pendingMessage = ChatMessage(id = nextLocalId("assistant"), text = "正在重新生成…", role = MessageRole.Assistant, status = MessageStatus.Sending, source = "cloud_ai", modelLabel = uiState.selectedModel.label)
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
        val onlineEnabled = uiState.onlineEnabled || shouldAutoEnableOnline(requestMessages)
        activePendingMessageId = pendingMessage.id
        activeSendJob = viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { aiWorkerClient.sendChat(requestMessages, selectedModel, onlineEnabled) }
                if (activePendingMessageId == pendingMessage.id) {
                    response.preferenceUpdate?.let { applyCloudPreferenceUpdate(it) }
                    val cloudAgentAction = response.agentAction
                    val cloudCommand = response.mobileAction?.toMobileCommand()
                    when {
                        cloudAgentAction?.canExecuteLocally() == true -> {
                            val goal = requestMessages.lastOrNull { it.role == MessageRole.User }?.text?.trim().orEmpty()
                            if (shouldPlanAsAgentTask(goal)) replaceMessage(pendingMessage.id, buildAgentTaskMessage(pendingMessage.id, goal))
                            else replaceMessage(pendingMessage.id, buildAgentObservationMessage(pendingMessage.id))
                        }
                        cloudCommand != null -> replaceMessage(pendingMessage.id, buildMobileCommandPreviewMessage(pendingMessage.id, cloudCommand))
                        else -> replaceMessage(pendingMessage.id, pendingMessage.copy(text = decorateReply(response, onlineEnabled), status = MessageStatus.Sent, source = response.source, model = response.model, modelLabel = response.modelLabel ?: selectedModel.label, version = response.version, errorText = null, webSources = response.webSources, structuredData = response.structuredData, searchUsed = response.searchUsed, searchProvider = response.searchProvider))
                    }
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
        replaceMessage(id, current.copy(text = "已暂停生成。", status = MessageStatus.Sent, source = "local", modelLabel = "已暂停", errorText = null))
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

    fun clearChat() { if (!uiState.isSending) uiState = uiState.copy(messages = emptyList(), composerText = "", composerAttachments = emptyList(), isSending = false) }

    fun removeComposerAttachment(id: String) {
        if (uiState.isSending) return
        uiState = uiState.copy(composerAttachments = uiState.composerAttachments.filterNot { it.id == id })
    }

    fun onImagePickedForAssistant(uri: Uri?) {
        if (uri == null || uiState.isSending) return
        val attachmentId = nextLocalId("composer-image")
        val draft = ComposerAttachment(id = attachmentId, localUri = uri.toString(), previewUri = uri.toString(), fileName = uri.lastPathSegment?.takeLast(48), progress = 0.12f, status = ComposerAttachmentStatus.Preparing)
        uiState = uiState.copy(composerAttachments = uiState.composerAttachments + draft)
        viewModelScope.launch {
            try {
                updateComposerAttachment(attachmentId) { it.copy(progress = 0.42f, status = ComposerAttachmentStatus.Preparing) }
                val attachment = withContext(Dispatchers.IO) { encodeAssistantImageAttachment(uri, attachmentId) }
                updateComposerAttachment(attachmentId) { it.copy(mimeType = attachment.mimeType, fileName = attachment.fileName, width = attachment.width, height = attachment.height, sizeBytes = attachment.sizeBytes, base64Data = attachment.base64Data, previewUri = attachment.previewUri, progress = 1f, status = ComposerAttachmentStatus.Ready, errorText = null) }
            } catch (error: Throwable) {
                val friendly = error.message?.takeIf { it.isNotBlank() } ?: "图片处理失败，请换一张图片再试。"
                updateComposerAttachment(attachmentId) { it.copy(progress = 1f, status = ComposerAttachmentStatus.Failed, errorText = friendly) }
            }
        }
    }

    private fun updateComposerAttachment(id: String, block: (ComposerAttachment) -> ComposerAttachment) {
        uiState = uiState.copy(composerAttachments = uiState.composerAttachments.map { item -> if (item.id == id) block(item) else item })
    }

    private fun stripVisualAttachmentStatus(text: String): String = if (text.startsWith(VISUAL_ATTACHMENT_STATUS_PREFIX)) "" else text

    private fun attachmentMessageFlag(attachments: List<ChatAttachment>): String {
        val first = attachments.firstOrNull()
        val meta = first?.let { attachmentMeta(it) } ?: "图片 ${attachments.size} 张"
        return listOf("视觉附件", "图片 ${attachments.size} 张", meta).distinct().joinToString(" · ")
    }

    private fun attachmentMeta(attachment: ChatAttachment): String {
        val dimensions = if (attachment.width != null && attachment.height != null) "${attachment.width}×${attachment.height}" else "图片"
        val size = attachment.sizeBytes?.let { "${max(1, it / 1024)} KB" } ?: "已压缩"
        return "$dimensions · $size"
    }

    private fun encodeAssistantImageAttachment(uri: Uri, attachmentId: String = nextLocalId("image")): ChatAttachment {
        val resolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val canOpen = resolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds); true } ?: false
        if (!canOpen) throw IOException("无法读取图片")
        val rawWidth = bounds.outWidth.takeIf { it > 0 } ?: throw IOException("无法解析图片宽度")
        val rawHeight = bounds.outHeight.takeIf { it > 0 } ?: throw IOException("无法解析图片高度")
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = imageSampleSize(rawWidth, rawHeight, ASSISTANT_IMAGE_MAX_DIMENSION); inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: throw IOException("图片解码失败")
        val scaled = decoded.scaleToMaxDimension(ASSISTANT_IMAGE_MAX_DIMENSION)
        if (scaled !== decoded) decoded.recycle()
        val width = scaled.width
        val height = scaled.height
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, ASSISTANT_IMAGE_JPEG_QUALITY, output)
        scaled.recycle()
        val bytes = output.toByteArray()
        if (bytes.size > ASSISTANT_IMAGE_MAX_BYTES) throw IOException("图片压缩后仍然过大，请裁剪后再试。")
        return ChatAttachment(id = attachmentId, mimeType = "image/jpeg", base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP), fileName = uri.lastPathSegment?.takeLast(48), width = width, height = height, sizeBytes = bytes.size, previewUri = uri.toString())
    }

    private fun imageSampleSize(width: Int, height: Int, maxDimension: Int): Int { var sample = 1; while (max(width / sample, height / sample) > maxDimension * 2) sample *= 2; return sample.coerceAtLeast(1) }
    private fun Bitmap.scaleToMaxDimension(maxDimension: Int): Bitmap { val longSide = max(width, height); if (longSide <= maxDimension) return this; val scale = maxDimension.toFloat() / longSide.toFloat(); return Bitmap.createScaledBitmap(this, (width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), true) }

    fun appendAssistantNotice(text: String, source: String? = null) { uiState = uiState.copy(messages = uiState.messages + ChatMessage(id = nextLocalId("assistant"), text = text, role = MessageRole.Assistant, source = source, modelLabel = sourceLabel(source))) }
    private fun replaceMessage(id: String, next: ChatMessage) { uiState = uiState.copy(messages = uiState.messages.map { if (it.id == id) next else it }) }
    private fun sourceLabel(source: String?): String? = when (source) { "local" -> "本地"; "local_ledger" -> "本地记账"; "local_mobile" -> "手机动作"; "local_agent" -> "手机智能体"; "cloud_fetch_failed" -> "云端连接失败"; else -> null }
    private fun formatCurrency(value: Float): String = "¥${String.format("%.2f", value)}"

    private fun shouldAutoEnableOnline(messages: List<ChatMessage>): Boolean {
        if (messages.any { it.hasImageAttachments }) return false
        val latestUserText = messages.lastOrNull { it.role == MessageRole.User }?.text?.trim().orEmpty()
        if (latestUserText.isBlank() || hasNoOnlineIntent(latestUserText)) return false
        val realtimePattern = Regex(pattern = "(今天|明天|现在|当前|实时|最新|新闻|热点|天气|气温|温度|下雨|降雨|降水|带伞|冷不冷|热不热|适合出门|汇率|兑换|美元|人民币|日元|欧元|英镑|港币|股价|股票|行情|美股|港股|A股|a股|纳斯达克|道琼斯|标普|查一下|查查|搜索|联网|网上|官网|价格|多少钱|比赛|赛程|排名|榜单)", option = RegexOption.IGNORE_CASE)
        return realtimePattern.containsMatchIn(latestUserText)
    }

    private fun shouldPlanAsAgentTask(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false
        if (isAgentEntryCommand(clean)) return false
        if (clean.contains("手机智能体")) return true
        val taskWords = listOf("帮我", "替我", "自动", "去", "找到", "查找", "搜索", "进入", "点击", "输入", "打开", "滑动", "返回")
        val hasTaskIntent = taskWords.any { clean.contains(it, ignoreCase = true) }
        if (!hasTaskIntent) return false
        val mentionsInstalledApp = runCatching { InstalledAppIndex(getApplication<Application>()).findBestApp(clean) != null }.getOrDefault(false)
        val knownAppWords = listOf("微信", "QQ", "哔哩", "B站", "小红书", "抖音", "淘宝", "京东", "支付宝", "高德", "百度地图", "浏览器")
        return mentionsInstalledApp || knownAppWords.any { clean.contains(it, ignoreCase = true) }
    }

    private fun isAgentEntryCommand(text: String): Boolean {
        val clean = text.trim()
        return clean in setOf("打开智能体", "开启智能体", "启动智能体", "观察屏幕", "观察当前屏幕", "看屏幕", "看一下屏幕", "看一下当前屏幕")
    }

    private fun hasNoOnlineIntent(text: String): Boolean = Regex(pattern = "(不用联网|不要联网|别联网|不需要联网|无需联网|不要搜索|别搜索|不用搜索|不要查网页|不用查网页)", option = RegexOption.IGNORE_CASE).containsMatchIn(text)

    private fun decorateReply(response: AiChatResponse, onlineEnabled: Boolean): String {
        val sections = mutableListOf(response.reply.trim())
        response.preferenceUpdate?.let { update -> if (update.type == "navigation_address") sections += "已保存导航偏好：${update.label} · ${update.value}" }
        response.structuredData?.let { data ->
            val metrics = data.metrics.take(6).joinToString("\n") { metric -> val unit = metric.unit?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty(); val detail = metric.detail?.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty(); "- ${metric.label}: ${metric.value}$unit$detail" }
            val header = listOfNotNull(data.title, data.subtitle, data.timestamp).joinToString(" · ")
            sections += buildString { append("实时数据："); append(header.ifBlank { data.type }); if (metrics.isNotBlank()) append("\n").append(metrics); data.rawText?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) } }
        }
        if (response.webSources.isNotEmpty()) {
            val provider = response.searchProvider?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            val sources = response.webSources.take(4).mapIndexed { index, source -> val domain = source.domain.ifBlank { source.url.substringAfter("://").substringBefore('/') }; val title = source.title.ifBlank { domain.ifBlank { "来源 ${index + 1}" } }; val date = source.publishedAt?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(); "${index + 1}. $title${if (domain.isNotBlank()) " · $domain" else ""}$date" }.joinToString("\n")
            sections += "联网来源$provider:\n$sources"
        }
        if (onlineEnabled && response.structuredData == null && response.webSources.isEmpty()) sections += "联网诊断：App 已发送联网请求，但后端没有返回 structuredData 或 sources[]。请确认阿里云函数已部署 web-data-v2 版本，并且普通搜索已配置 TAVILY_API_KEY。"
        return sections.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    fun selectQuality(quality: RenderQuality) { uiState = uiState.copy(quality = quality); viewModelScope.launch { preferencesStore.setRenderQuality(quality) } }
    fun setShowPreviewConversation(showPreviewConversation: Boolean) { uiState = uiState.copy(showPreviewConversation = showPreviewConversation); viewModelScope.launch { preferencesStore.setShowPreviewConversation(showPreviewConversation) } }
    fun setBackgroundTheme(backgroundTheme: BackgroundTheme) { uiState = uiState.copy(backgroundTheme = backgroundTheme, customBackgroundPath = BUILTIN_THEME_BACKGROUND_PATH); viewModelScope.launch { preferencesStore.setBackgroundTheme(backgroundTheme); preferencesStore.setCustomBackgroundPath(null) } }
    fun importCustomBackground(uri: Uri) { viewModelScope.launch { val savedPath = withContext(Dispatchers.IO) { customBackgroundStore.saveFromUri(uri) }; uiState = uiState.copy(customBackgroundPath = savedPath); preferencesStore.setCustomBackgroundPath(savedPath) } }
    fun clearCustomBackground() { uiState = uiState.copy(customBackgroundPath = null); viewModelScope.launch { withContext(Dispatchers.IO) { customBackgroundStore.clearCustomBackground() }; preferencesStore.setCustomBackgroundPath(null) } }
    fun setBackdropDebugParams(params: BackdropDebugParams) { uiState = uiState.copy(backdropParams = params) }
    fun updateBackdropDebugParams(block: (BackdropDebugParams) -> BackdropDebugParams) { setBackdropDebugParams(block(uiState.backdropParams)) }
    fun setGlassBorderStyle(style: GlassBorderStyle) { uiState = uiState.copy(glassBorderStyle = style) }
    fun updateGlassBorderStyle(block: (GlassBorderStyle) -> GlassBorderStyle) { setGlassBorderStyle(block(uiState.glassBorderStyle)) }
    fun setModelCardGlassStyle(style: ModelCardGlassStyle) { uiState = uiState.copy(modelCardGlassStyle = style) }
    fun setRainbowPrismStyle(style: RainbowPrismStyle) { val minValue = minOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f); val maxValue = maxOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f); val clamped = RainbowPrismStyle(overall = style.overall.coerceIn(0f, 2f), edgeHighlight = style.edgeHighlight.coerceIn(0f, 2f), sweepMin = minValue, sweepMax = maxValue, rainbowHalo = style.rainbowHalo.coerceIn(0f, 2f)); uiState = uiState.copy(rainbowPrismStyle = clamped); viewModelScope.launch { preferencesStore.setRainbowPrismStyle(clamped) } }
    fun setGlassIntensity(value: Float) { val clamped = value.coerceIn(0.6f, 1.4f); uiState = uiState.copy(glassIntensity = clamped, glassPreset = detectPreset(clamped, uiState.motionIntensity)); viewModelScope.launch { preferencesStore.setGlassIntensity(clamped) } }
    fun setMotionIntensity(value: Float) { val clamped = value.coerceIn(0f, 1.4f); uiState = uiState.copy(motionIntensity = clamped, glassPreset = detectPreset(uiState.glassIntensity, clamped)); viewModelScope.launch { preferencesStore.setMotionIntensity(clamped) } }
    fun setGlassPreset(preset: GlassPreset) { uiState = uiState.copy(glassPreset = preset, glassIntensity = preset.glassIntensity, motionIntensity = preset.motionIntensity); viewModelScope.launch { preferencesStore.setGlassPreset(preset); preferencesStore.setGlassIntensity(preset.glassIntensity); preferencesStore.setMotionIntensity(preset.motionIntensity) } }
    private fun detectPreset(glass: Float, motion: Float): GlassPreset = GlassPreset.entries.minByOrNull { val dg = glass - it.glassIntensity; val dm = motion - it.motionIntensity; dg * dg + dm * dm } ?: GlassPreset.Liquid
    private fun nextLocalId(prefix: String): String = "$prefix-${localIdSeed.incrementAndGet()}"
}