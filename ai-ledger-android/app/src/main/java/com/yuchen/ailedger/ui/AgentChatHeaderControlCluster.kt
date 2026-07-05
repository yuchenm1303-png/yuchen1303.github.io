package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Assistant 页面同窗口覆盖层兼容入口。
 *
 * 面板本体复用 AnchoredQuickPanel；按钮优先由对话大玻璃内部挂载，旧 modifier 只作为兼容兜底。
 */
@Composable
internal fun AgentChatHeaderControlCluster(modifier: Modifier = Modifier) {
    Box(modifier = Modifier) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkillQuickPanelButtonHost()
            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        }
        MemoryQuickPanelSameWindowOverlayHost()
        SkillQuickPanelSameWindowOverlayHost()
    }
}
