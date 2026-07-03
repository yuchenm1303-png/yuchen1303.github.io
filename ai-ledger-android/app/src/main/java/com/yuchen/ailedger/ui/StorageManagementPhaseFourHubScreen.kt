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
    Downloads,
    Junk,
    AppCache,
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
        StoragePhaseFourPage.Downloads -> {
            StorageDownloadCleanupScreen(
                state = state,
                onBack = { page = StoragePhaseFourPage.Main },
            )
            return
        }
        StoragePhaseFourPage.Junk -> {
            StorageJunkCleanupScreen(
                state = state,
                onBack = { page = StoragePhaseFourPage.Main },
            )
            return
        }
        StoragePhaseFourPage.AppCache -> {
            StorageAppCacheCleanupScreen(
                state = state,
                onBack = { page = StoragePhaseFourPage.Main },
            )
            return
        }
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
                        title = "下载与安装包",
                        subtitle = "整理安装包、压缩包、下载残留、长期未整理和大型下载文件",
                        tone = Color(0xFF9CD8FF),
                        onClick = { page = StoragePhaseFourPage.Downloads },
                    ),
                    StorageInlineFeatureEntry(
                        title = "基础垃圾文件",
                        subtitle = "检查零字节文件、空文件夹、旧临时文件、日志和备份",
                        tone = Color(0xFFFFCA72),
                        onClick = { page = StoragePhaseFourPage.Junk },
                    ),
                    StorageInlineFeatureEntry(
                        title = "全机应用缓存",
                        subtitle = "统计应用缓存，并通过 Shizuku/ADB Shell 请求系统安全回收",
                        tone = Color(0xFFFFB47A),
                        onClick = { page = StoragePhaseFourPage.AppCache },
                    ),
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
