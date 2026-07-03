package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AgentAnalyticsViewModel
import com.yuchen.ailedger.model.AssistantUiState

@Composable
internal fun AgentAnalyticsRoute(
    appState: AssistantUiState,
    onBack: () -> Unit,
) {
    val analyticsViewModel: AgentAnalyticsViewModel = viewModel()
    AgentAnalyticsScreen(
        appState = appState,
        viewModel = analyticsViewModel,
        onBack = onBack,
    )
}
