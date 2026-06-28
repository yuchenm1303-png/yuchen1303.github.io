package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 聊天大玻璃内部的唯一标题控件行。
 *
 * Agent、浮窗沿用既有实现；记忆入口作为同一 Row 的普通 Compose 子节点追加，
 * 因而三者共享聊天 Shell 的位移与形变，但记忆按钮和弹层不参与 OpenGL 绘制链。
 */
@Composable
internal fun AgentChatMemoryTitleControls(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AgentChatGlassTitleControls()
        MemoryQuickPanelDialogHost()
    }
}
