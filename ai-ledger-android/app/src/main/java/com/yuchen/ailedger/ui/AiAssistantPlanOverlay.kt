package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.ToolDestination

/**
 * MainActivity 使用的无参入口。原有 App 根布局保持不变，只在用户打开“计划”工具时
 * 叠加独立的原生 Compose 页面，避免触碰聊天 OpenGL Host 和消息绘制链。
 */
@Composable
fun AiAssistantNativeApp() {
    val assistantViewModel: AssistantViewModel = viewModel()
    Box(Modifier.fillMaxSize()) {
        AiAssistantNativeApp(viewModel = assistantViewModel)

        val state = assistantViewModel.uiState
        if (state.currentTab == AppTab.Tools && state.selectedTool == ToolDestination.Reminder) {
            val blocker = remember { MutableInteractionSource() }
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .zIndex(4000f)
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFB07132D),
                                    Color(0xF80B1834),
                                    Color(0xFC071024),
                                ),
                            ),
                        )
                        .clickable(
                            interactionSource = blocker,
                            indication = null,
                            onClick = {},
                        )
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp),
                ) {
                    PlanCenterScreen(onBack = assistantViewModel::closeTool)
                }
            }
        }
    }
}
