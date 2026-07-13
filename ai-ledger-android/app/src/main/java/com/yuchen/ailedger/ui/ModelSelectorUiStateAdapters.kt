package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.service.AgentOFloatingChatController

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
 * 保留原右上角水滴玻璃的唯一绘制入口，只把业务职责切换为 Agent O 普通聊天悬浮窗。
 *
 * 无限符号 Agent 开关仍由聊天大玻璃标题栏控制视觉智能体；两者完全独立。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun NetworkDropletCapsule(
    state: ModelSelectorUiState,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val agentOEnabled by AgentOFloatingChatController.enabled.collectAsState()
    val visualState = remember(state, agentOEnabled) {
        state.asAssistantUiState().copy(onlineEnabled = agentOEnabled)
    }

    NetworkDropletCapsule(
        state = visualState,
        modifier = modifier,
        enabled = true,
        onClick = AgentOFloatingChatController::toggle,
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
