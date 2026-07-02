package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import com.yuchen.ailedger.model.AssistantUiState

@Composable
fun OperationLearningScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    OperationLearningFlowScreen(
        state = state,
        onBack = onBack,
    )
}
