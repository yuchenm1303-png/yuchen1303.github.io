package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.AssistantUiState

@Composable
fun OperationLearningScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    val learningViewModel: OperationLearningViewModel = viewModel()

    // 只有真正进入操作学习详情页才展开完整工作流图谱；功能首页仅保留轻量摘要。
    LaunchedEffect(learningViewModel) {
        learningViewModel.refresh()
    }

    OperationLearningFlowScreen(
        state = state,
        onBack = onBack,
        learningViewModel = learningViewModel,
    )
}
