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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * App 进程级普通聊天状态桥。
 *
 * [AssistantViewModel] 仍是唯一业务状态源；Agent O WebView 只订阅快照并把用户意图转回
 * 现有 ViewModel 方法。该桥不发起网络请求，也不复制附件二进制 payload。
 */
object AssistantFloatingChatBridge {
    private val mutableState = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = mutableState.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var attached = false
    private var viewModel: AssistantViewModel? = null

    fun attach(
        viewModel: AssistantViewModel,
        scope: CoroutineScope,
        appContext: Context,
    ) {
        if (attached && this.viewModel === viewModel) return
        attached = true
        this.viewModel = viewModel

        scope.launch {
            snapshotFlow {
                val state = viewModel.uiState
                // StreamingAssistant 使用末项原位替换的 overlay list；这里实体化一次，确保
                // WebView 收到节流后的每次流式更新，而不是只看到 List 引用不变。
                state.copy(
                    messages = state.messages.toList(),
                    composerAttachments = state.composerAttachments.toList(),
                )
            }.distinctUntilChanged().collect { mutableState.value = it }
        }

        scope.launch {
            AgentOFloatingChatController.enabled.collect { enabled ->
                if (enabled && !AiAgentAccessibilityService.isConnected()) {
                    AgentAccessibilityGuideActivity.open(appContext)
                    AgentOFloatingChatController.setEnabled(false)
                    viewModel.appendAssistantNotice(
                        text = "Agent O 悬浮对话需要先开启无障碍服务。它不需要额外的普通悬浮窗权限。",
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
                "composer.change" -> target.updateComposer(payload.optString("text"))
                "chat.send" -> target.sendUserCommand(payload.optString("text"))
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
