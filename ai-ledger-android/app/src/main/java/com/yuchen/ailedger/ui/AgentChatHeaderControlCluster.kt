package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.model.AppTab

/**
 * 聊天大玻璃标题区域的独立浮层控件。
 *
 * 这里仅组合普通 Compose 控件，不接入 OpenGL registry，也不参与聊天大玻璃的
 * Host 尺寸、anchor 或 viewportTopInset 链。
 */
@Composable
internal fun AgentChatHeaderControlCluster(modifier: Modifier = Modifier) {
    val assistantViewModel: AssistantViewModel = viewModel()

    Row(
        modifier = modifier.height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AgentChatGlassTitleControls()
        MemoryQuickPanelHost(
            onOpenManager = { assistantViewModel.selectTab(AppTab.Settings) }
        )
    }
}
