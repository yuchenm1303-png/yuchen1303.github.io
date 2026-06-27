package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 记忆快捷入口仍处于网页视觉实验阶段。
 * 当前只恢复既有标题控件入口，避免重复绘制 Agent 与浮窗控件。
 */
@Composable
internal fun AgentChatHeaderControlCluster(modifier: Modifier = Modifier) {
    AgentChatHeaderOverlay(modifier)
}
