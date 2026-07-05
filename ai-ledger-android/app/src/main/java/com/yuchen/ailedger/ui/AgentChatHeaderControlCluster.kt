package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 旧的 App 根节点固定坐标入口只保留兼容空壳。
 *
 * 首页快捷按钮不能从这里绘制，否则会脱离对话 OpenGL 大玻璃，变成独立悬浮按钮。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun AgentChatHeaderControlCluster(modifier: Modifier = Modifier) = Unit
