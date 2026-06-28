package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 旧版页面固定坐标入口，仅保留兼容调用。
 *
 * 标题控件已经迁入聊天 Shell 内部，由 AgentChatMemoryTitleControls 统一绘制；
 * 此处必须保持为空，避免在模型卡区域再次生成独立浮层。
 */
@Composable
internal fun AgentChatHeaderControlCluster(modifier: Modifier = Modifier) = Unit
