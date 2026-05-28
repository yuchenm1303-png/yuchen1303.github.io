package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatModel

@Composable
internal fun ModelCardGlassLabPreview(
    state: AssistantUiState,
    modifier: Modifier = Modifier
) {
    val previewState = state.copy(
        selectedModel = ChatModel.Kimi,
        selectedModelLabel = ChatModel.Kimi.label,
        isSending = false
    )
    UnifiedParentModelStackSelector(
        state = previewState,
        expanded = true,
        modifier = modifier,
        onToggleExpanded = {},
        onSelected = {}
    )
}
