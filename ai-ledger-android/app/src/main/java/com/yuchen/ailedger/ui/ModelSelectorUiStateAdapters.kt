package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatModel

@Composable
internal fun UnifiedParentModelStackSelector(
    state: ModelSelectorUiState,
    expanded: Boolean,
    modifier: Modifier,
    onToggleExpanded: () -> Unit,
    onSelected: (ChatModel) -> Unit
) {
    val visualState = remember(state) { state.asAssistantUiState() }
    UnifiedParentModelStackSelector(
        state = visualState,
        expanded = expanded,
        modifier = modifier,
        onToggleExpanded = onToggleExpanded,
        onSelected = onSelected
    )
}

@Composable
internal fun NetworkDropletCapsule(
    state: ModelSelectorUiState,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val visualState = remember(state) { state.asAssistantUiState() }
    NetworkDropletCapsule(
        state = visualState,
        modifier = modifier,
        enabled = enabled,
        onClick = onClick
    )
}

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
