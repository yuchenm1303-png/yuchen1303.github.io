package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 旧的独立标题控件浮层入口。
 *
 * Agent、浮窗和记忆按钮现在统一由 AgentChatGlassTitleControls 在同一行绘制，
 * 这里保持为空，避免在消息区域重复生成第二排控件。
 */
@Composable
internal fun AgentChatHeaderControlCluster(modifier: Modifier = Modifier) = Unit
