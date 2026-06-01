package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatModel

@Composable
fun AssistantScreenV2(
    state: AssistantUiState,
    bottomPadding: Dp = 68.dp,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onStopGenerating: () -> Unit,
    onDraftCommand: (String) -> Unit,
    onModelSelected: (ChatModel) -> Unit,
    onPickImage: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleOnline: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
        Text("AI Assistant")
        Text("Temporary recovery screen")
    }
    state.hashCode()
    onComposerChange.hashCode()
    onSend.hashCode()
    onStopGenerating.hashCode()
    onDraftCommand.hashCode()
    onModelSelected.hashCode()
    onPickImage.hashCode()
    onOpenTools.hashCode()
    onOpenSettings.hashCode()
    onToggleOnline.hashCode()
    onCopyMessage.hashCode()
    onRetryMessage.hashCode()
}
