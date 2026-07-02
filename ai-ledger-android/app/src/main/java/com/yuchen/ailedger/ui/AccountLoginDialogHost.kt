package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import com.yuchen.ailedger.model.AssistantUiState

@Composable
internal fun AccountLoginDialogHost(
    visible: Boolean,
    state: AssistantUiState,
    onDismiss: () -> Unit,
) {
    AccountLoginModalHost(
        visible = visible,
        state = state,
        onDismiss = onDismiss,
    )
}
