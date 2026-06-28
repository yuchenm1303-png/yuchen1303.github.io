package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 聊天标题栏记忆入口。
 *
 * Agent 与浮窗仍由原标题栏实现负责；这里只补充右侧文件夹按钮，避免重复绘制
 * 既有两个控件。偏移量与当前聊天标题栏基准位置对齐，不参与 OpenGL Host、
 * anchor 或 viewportTopInset 链。
 */
@Composable
internal fun AgentChatHeaderControlCluster(modifier: Modifier = Modifier) {
    MemoryQuickPanelHost(
        modifier = modifier.offset(x = 154.dp, y = (-42).dp)
    )
}
