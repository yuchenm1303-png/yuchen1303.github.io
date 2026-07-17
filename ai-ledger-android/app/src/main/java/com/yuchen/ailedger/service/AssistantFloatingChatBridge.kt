package com.yuchen.ailedger.service

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.snapshotFlow
import com.yuchen.ailedger.AgentAccessibilityGuideActivity
import com.yuchen.ailedger.AgentOAttachmentPickerActivity
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ComposerAttachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * App 进程级普通聊天状态桥。
 *
 * [AssistantViewModel] 仍是唯一业务状态源；Agent O WebView 只订阅快照并把用户意图转回
 * 现有 ViewModel 方法。浮窗关闭时不收集聊天状态；开启后只镜像浮窗实际需要的字段，避免
 * 首页视觉参数、工具页和设置页变化进入浮窗链。该桥不发起网络请求，也不复制附件二进制 payload。
 */
object AssistantFloatingChatBridge {
    private val mutableState = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = mutableState.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var attached = false
    private var viewModel: AssistantViewModel? = null
    @Volatile private var localComposerEcho: String? = null

    fun attach(
        viewModel: AssistantViewModel,
        scope: CoroutineScope,
        appContext: Context,
    ) {
        if (attached && this.viewModel === viewModel) return
        attached = true
        this.viewModel = viewModel

        scope.launch {
            AgentOFloatingChatController.enabled.collectLatest { enabled ->
                if (!enabled) {
                    localComposerEcho = null
                    mutableState.value = AssistantUiState()
                    return@collectLatest
                }

                var lastMessagesSource: List<ChatMessage>? = null
                var lastMessagesSize = -1
                var lastMessageTail: ChatMessage? = null
                var stableMessages: List<ChatMessage> = emptyList()

                var lastAttachmentsSource: List<ComposerAttachment>? = null
                var lastAttachmentsSize = -1
                var lastAttachmentTail: ComposerAttachment? = null
                var stableAttachments: List<ComposerAttachment> = emptyList()

                snapshotFlow {
                    val source = viewModel.uiState

                    val messagesSource = source.messages
                    val messageTail = messagesSource.lastOrNull()
                    val messages = if (
                        messagesSource === lastMessagesSource &&
                        messagesSource.size == lastMessagesSize &&
                        messageTail === lastMessageTail
                    ) {
                        stableMessages
                    } else {
                        messagesSource.toList().also {
                            lastMessagesSource = messagesSource
                            lastMessagesSize = messagesSource.size
                            lastMessageTail = messageTail
                            stableMessages = it
                        }
                    }

                    val attachmentsSource = source.composerAttachments
                    val attachmentTail = attachmentsSource.lastOrNull()
                    val attachments = if (
                        attachmentsSource === lastAttachmentsSource &&
                        attachmentsSource.size == lastAttachmentsSize &&
                        attachmentTail === lastAttachmentTail
                    ) {
                        stableAttachments
                    } else {
                        attachmentsSource.toList().also {
                            lastAttachmentsSource = attachmentsSource
                            lastAttachmentsSize = attachmentsSource.size
                            lastAttachmentTail = attachmentTail
                            stableAttachments = it
                        }
                    }

                    val previous = mutableState.value
                    val otherStateChanged = previous.messages !== messages ||
                        previous.composerAttachments !== attachments ||
                        previous.selectedModel != source.selectedModel ||
                        previous.selectedModelLabel != source.selectedModelLabel ||
                        previous.onlineEnabled != source.onlineEnabled ||
                        previous.isSending != source.isSending
                    val composerEcho = localComposerEcho
                    val composerText = if (
                        composerEcho != null &&
                        source.composerText == composerEcho &&
                        !otherStateChanged
                    ) {
                        previous.composerText
                    } else {
                        if (composerEcho != null) localComposerEcho = null
                        source.composerText
                    }

                    AssistantUiState(
                        messages = messages,
                        composerText = composerText,
                        composerAttachments = attachments,
                        selectedModel = source.selectedModel,
                        selectedModelLabel = source.selectedModelLabel,
                        onlineEnabled = source.onlineEnabled,
                        isSending = source.isSending,
                    )
                }
                    .distinctUntilChanged()
                    .collect { mutableState.value = it }
            }
        }

        scope.launch {
            AgentOFloatingChatController.enabled.collect { enabled ->
                if (enabled && !AiAgentAccessibilityService.isConnected()) {
                    // 保留开启请求。用户在系统设置中启用无障碍后，Service 连接会直接启动
                    // Agent O 浮窗，不要求回到首页再次点击开关。
                    AgentAccessibilityGuideActivity.open(appContext)
                    viewModel.appendAssistantNotice(
                        text = "Agent O 悬浮对话需要先开启无障碍服务。授权完成后会自动显示，不需要普通悬浮窗权限。",
                        source = "local_agent",
                    )
                }
            }
        }
    }

    fun dispatch(action: String, payload: JSONObject = JSONObject()): Boolean {
        val target = viewModel ?: return false
        mainHandler.post {
            when (action) {
                "workspace.toggle" -> Unit
                "agent.toggle" -> {
                    val enabled = payload.optBoolean("enabled", !AgentRuntimeController.isEnabled())
                    AgentRuntimeController.setEnabled(enabled)
                }
                "online.toggle" -> {
                    val next = payload.optBoolean("enabled", !target.uiState.onlineEnabled)
                    if (next != target.uiState.onlineEnabled) target.toggleOnline()
                }
                "composer.change" -> {
                    val text = payload.optString("text")
                    localComposerEcho = text
                    target.updateComposer(text)
                }
                "chat.send" -> {
                    localComposerEcho = ""
                    target.sendUserCommand(payload.optString("text"))
                }
                "chat.stop" -> target.stopGenerating()
                "chat.retry" -> payload.optString("messageId").takeIf(String::isNotBlank)?.let(target::retryMessage)
                "chat.clear" -> target.clearChat()
                "attachment.pick" -> AgentOAttachmentPickerActivity.open(target.getApplication<Application>())
                "attachment.remove" -> {
                    val requestedId = payload.optString("attachmentId")
                    val id = requestedId.takeIf(String::isNotBlank)
                        ?: target.uiState.composerAttachments.firstOrNull()?.id
                    if (id != null) target.removeComposerAttachment(id)
                }
                "attachment.selected" -> payload.optString("uri")
                    .takeIf(String::isNotBlank)
                    ?.let(Uri::parse)
                    ?.let(target::onImagePickedForAssistant)
                // Memory / Skill 面板先展示真实空快照，不伪造本地业务；后续接现有仓库。
                "memory.open", "memory.refresh", "memory.manage",
                "skill.open", "skill.refresh", "skill.manage", "skill.run",
                "chat.copy", "panel.collapse" -> Unit
            }
        }
        return true
    }
}
