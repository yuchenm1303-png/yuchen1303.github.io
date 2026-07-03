package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.yuchen.ailedger.model.AssistantUiState

private enum class StoragePhaseFourPage {
    Main,
    Intelligence,
    Organization,
    FolderIndex,
    DeviceOptimization,
}

@Composable
fun StorageManagementPhaseFourHubScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    var page by remember { mutableStateOf(StoragePhaseFourPage.Main) }
    when (page) {
        StoragePhaseFourPage.Intelligence -> {
            StorageIntelligenceCompleteScreen(
                state = state,
                onBack = { page = StoragePhaseFourPage.Main },
            )
            return
        }
        StoragePhaseFourPage.Organization -> {
            StorageMediaOrganizationScreen(
                state = state,
                onBack = { page = StoragePhaseFourPage.Main },
            )
            return
        }
        StoragePhaseFourPage.FolderIndex -> {
            StorageFolderIndexCompleteScreen(
                state = state,
                onBack = { page = StoragePhaseFourPage.Main },
            )
            return
        }
        StoragePhaseFourPage.DeviceOptimization -> {
            StorageDeviceOptimizationScreen(
                state = state,
                onBack = { page = StoragePhaseFourPage.Main },
            )
            return
        }
        StoragePhaseFourPage.Main -> Unit
    }

    StorageManagementScreen(
        state = state,
        onBack = onBack,
        inlineFeatureContent = {
            StorageInlineFeatureSection(
                state = state,
                entries = listOf(
                    StorageInlineFeatureEntry(
                        title = "智能分析",
                        subtitle = "完整检查重复文件、长期未修改文件与清理记录",
                        tone = Color(0xFF8DF9EA),
                        onClick = { page = StoragePhaseFourPage.Intelligence },
                    ),
                    StorageInlineFeatureEntry(
                        title = "精细整理",
                        subtitle = "相似照片、截图、连拍、画质候选与分类整理",
                        tone = Color(0xFF8DF9EA),
                        onClick = { page = StoragePhaseFourPage.Organization },
                    ),
                    StorageInlineFeatureEntry(
                        title = "目录索引",
                        subtitle = "完整扫描授权目录并保存恢复断点",
                        tone = Color(0xFF9CD8FF),
                        onClick = { page = StoragePhaseFourPage.FolderIndex },
                    ),
                    StorageInlineFeatureEntry(
                        title = "设备优化",
                        subtitle = "应用占用、长期未用建议、趋势与权限健康",
                        tone = Color(0xFF83F3B8),
                        onClick = { page = StoragePhaseFourPage.DeviceOptimization },
                    ),
                ),
            )
        },
    )
}
