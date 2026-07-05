package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import com.yuchen.ailedger.model.AssistantUiState

/**
 * 保留旧入口名称以避免破坏既有功能路由，实际内容已经升级为第四阶段总入口。
 */
@Composable
fun StorageManagementPhaseThreeHubScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    SecondaryRouteEntrance(motionIntensity = state.motionIntensity) {
        StorageManagementPhaseFourHubScreen(
            state = state,
            onBack = onBack,
        )
    }
}
