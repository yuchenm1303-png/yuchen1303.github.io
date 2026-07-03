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
                        title = "智能文件分析",
                        subtitle = "检查完全重复文件、长期未修改大文件与清理记录",
                        tone = Color(0xFF8DF9EA),
                        onClick = { page = StoragePhaseFourPage.Intelligence },
                    ),
                    StorageInlineFeatureEntry(
                        title = "照片与授权目录",
                        subtitle = "整理相似照片、截图、连拍、画质候选和授权目录文件",
                        tone = Color(0xFF8DF9EA),
                        onClick = { page = StoragePhaseFourPage.Organization },
                    ),
                    StorageInlineFeatureEntry(
                        title = "授权目录索引",
                        subtitle = "完整扫描你选择的目录，并保存可恢复的扫描断点",
                        tone = Color(0xFF9CD8FF),
                        onClick = { page = StoragePhaseFourPage.FolderIndex },
                    ),
                    StorageInlineFeatureEntry(
                        title = "应用与设备空间",
                        subtitle = "查看应用占用、长期未用建议、空间趋势和权限状态",
                        tone = Color(0xFF83F3B8),
                        onClick = { page = StoragePhaseFourPage.DeviceOptimization },
                    ),
                ),
            )
        },
    )
}
