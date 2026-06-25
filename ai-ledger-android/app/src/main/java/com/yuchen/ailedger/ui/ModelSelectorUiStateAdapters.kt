package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
