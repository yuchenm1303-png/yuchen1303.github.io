package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatModel

@Composable
internal fun ModelCardGlassLabPreview(
    state: AssistantUiState,
    modifier: Modifier = Modifier
) {
    var previewModel by remember { mutableStateOf(ChatModel.Kimi) }
    val previewState = state.copy(
        selectedModel = previewModel,
        selectedModelLabel = previewModel.label,
        isSending = false
    )
    UnifiedParentModelStackSelector(
        state = previewState,
        expanded = true,
        expansionProgress = 1f,
        modifier = modifier,
        onToggleExpanded = {},
        onSelected = { previewModel = it }
    )
}
