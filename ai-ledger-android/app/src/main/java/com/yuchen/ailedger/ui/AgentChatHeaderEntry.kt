package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.service.AgentRuntimeController

@Composable
internal fun AgentChatHeaderOverlay(modifier: Modifier = Modifier) = Unit

/**
 * 聊天大玻璃标题栏中的既有 Agent 与联网控制。
 *
 * Agent 无限符继续控制视觉智能体；原“浮窗”小开关原位改为联网开关，直接复用
 * [AssistantViewModel] 的唯一 onlineEnabled 状态与 toggleOnline()，不新增按钮、不接入
 * OpenGL registry，也不再申请普通应用悬浮窗权限。
 */
@Composable
internal fun AgentChatGlassTitleControls(
    modifier: Modifier = Modifier,
    assistantViewModel: AssistantViewModel = viewModel(),
) {
    val agentEnabled by AgentRuntimeController.enabled.collectAsState()
    val progress by AgentRuntimeController.progress.collectAsState()
    val assistantState = assistantViewModel.uiState

    Row(
        modifier = modifier.height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AgentInfinityWebCapsule(
            enabled = agentEnabled,
            progress = progress,
            onClick = { AgentRuntimeController.setEnabled(!agentEnabled) }
        )
        AgentHeaderSwitchPill(
            label = "联网",
            enabled = assistantState.onlineEnabled,
            activeColors = listOf(Color(0xEE8DFFF4), Color(0xCC9B73FF), Color(0xAA4FB6FF)),
            onClick = assistantViewModel::toggleOnline,
        )
    }
}
