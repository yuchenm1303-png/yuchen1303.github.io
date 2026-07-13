package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.service.AgentRuntimeController

@Composable
internal fun UnifiedParentModelStackSelector(
    state: ModelSelectorUiState,
    expanded: Boolean,
    modifier: Modifier,
    onToggleExpanded: () -> Unit,
    onSelected: (ChatModel) -> Unit
) {
    // The model stack only consumes selectedModel and isSending. Keeping the
    // projected object stable prevents online/glass setting changes from
    // recomposing the whole animated card stack.
    val visualState = remember(state.selectedModel, state.isSending) {
        state.asModelStackAssistantUiState()
    }
    val currentOnToggleExpanded = rememberUpdatedState(onToggleExpanded)
    val currentOnSelected = rememberUpdatedState(onSelected)
    val stableOnToggleExpanded = remember {
        { currentOnToggleExpanded.value() }
    }
    val stableOnSelected = remember {
        { model: ChatModel -> currentOnSelected.value(model) }
    }

    UnifiedParentModelStackSelector(
        state = visualState,
        expanded = expanded,
        modifier = modifier,
        onToggleExpanded = stableOnToggleExpanded,
        onSelected = stableOnSelected
    )
}

/**
 * 保留原右上角水滴玻璃的唯一绘制入口，只交换业务职责。
 *
 * 旧调用点仍传入联网回调以保持 Assistant 首页布局链不动；这里不再消费该回调，
 * 而是直接绑定视觉智能体状态。水滴的 OpenGL 材质、尺寸和按压动画均沿用原组件。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun NetworkDropletCapsule(
    state: ModelSelectorUiState,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val agentEnabled by AgentRuntimeController.enabled.collectAsState()
    val progress by AgentRuntimeController.progress.collectAsState()
    val interactionRequired = progress.running ||
        progress.pendingConfirmation != null ||
        progress.pendingUserInput != null ||
        progress.userTakeoverPaused
    val visuallyActive = agentEnabled || interactionRequired
    val visualState = remember(state, visuallyActive) {
        state.asAssistantUiState().copy(onlineEnabled = visuallyActive)
    }

    NetworkDropletCapsule(
        state = visualState,
        modifier = modifier,
        // 智能浮球入口与普通聊天发送状态解耦。
        enabled = true,
        onClick = { AgentRuntimeController.setEnabled(!agentEnabled) },
        label = "Agent O",
    )
}

private fun ModelSelectorUiState.asModelStackAssistantUiState(): AssistantUiState = AssistantUiState(
    selectedModel = selectedModel,
    selectedModelLabel = selectedModelLabel,
    onlineEnabled = false,
    isSending = isSending,
    quality = quality,
    glassIntensity = glassIntensity,
    motionIntensity = motionIntensity,
    modelCardGlassStyle = modelCardGlassStyle
)

private fun ModelSelectorUiState.asAssistantUiState(): AssistantUiState = AssistantUiState(
    selectedModel = selectedModel,
    selectedModelLabel = selectedModelLabel,
    onlineEnabled = onlineEnabled,
    isSending = isSending,
    quality = quality,
    glassIntensity = glassIntensity,
    motionIntensity = motionIntensity,
    modelCardGlassStyle = modelCardGlassStyle
)
