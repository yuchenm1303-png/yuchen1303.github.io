package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.AiLedgerApplication

/**
 * 聊天大玻璃内部的唯一标题控件行。
 *
 * Workspace、Agent、联网、记忆、Skill 与新建对话入口共享聊天 Shell 的位移和形变；
 * 快捷面板仍使用同窗口覆盖层，不创建额外窗口，也不接入 OpenGL registry。
 */
@Composable
internal fun AgentChatMemoryTitleControls(modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as AiLedgerApplication
    val assistantViewModel = application.assistantViewModel
    val assistantState = assistantViewModel.uiState

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .offset(x = (-34).dp, y = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WorkspaceTetrahedronTitleIcon()
        AgentChatGlassTitleControls()
        MemoryQuickPanelButtonHost()
        SkillQuickPanelButtonHost()
        Spacer(Modifier.weight(1f))
        NewConversationTitleButton(
            enabled = assistantState.messages.isNotEmpty(),
            onClick = assistantViewModel::clearChat,
            modifier = Modifier.offset(x = 23.dp),
        )
    }
}

@Composable
private fun NewConversationTitleButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 0.64f else 0.26f
    Text(
        text = "新建",
        color = Color.White.copy(alpha = alpha),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.075f else 0.035f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        maxLines = 1,
    )
}
