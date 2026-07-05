package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Assistant 页面同窗口覆盖层兼容入口。
 *
 * App.kt 仍会在 Assistant 页面根 Box 中调用本函数。这里不绘制任何固定坐标按钮，
 * 只承载快捷面板弹窗；按钮本体必须挂在对话 OpenGL 大玻璃已有标题栏控件链里。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun AgentChatHeaderControlCluster(modifier: Modifier = Modifier) {
    MemoryQuickPanelSameWindowOverlayHost()
    SkillQuickPanelSameWindowOverlayHost()
}
