package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Assistant 页面同窗口覆盖层兼容入口。
 *
 * App.kt 仍会在 Assistant 页面根 Box 中调用本函数。这里不再绘制任何固定坐标按钮，
 * 只承载记忆面板的全页覆盖层；传入的旧固定 offset modifier 必须忽略。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun AgentChatHeaderControlCluster(modifier: Modifier = Modifier) {
    MemoryQuickPanelSameWindowOverlayHost()
}
