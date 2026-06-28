package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 无参数入口用于聊天标题栏的最终控件组合。
 *
 * 旧的一参数实现继续负责 Agent 与浮窗两个成熟控件；记忆按钮只在这里追加，
 * 因而三者处于同一个 22dp 高度 Row 内，不会再生成独立第二排。
 */
@Composable
internal fun AgentChatGlassTitleControls() {
    Row(
        modifier = Modifier.height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AgentChatGlassTitleControls(modifier = Modifier)
        MemoryQuickPanelHost()
    }
}
