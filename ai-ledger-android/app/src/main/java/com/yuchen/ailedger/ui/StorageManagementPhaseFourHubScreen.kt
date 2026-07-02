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
        StoragePhaseFourPage.Organization -> {
            StorageMediaOrganizationScreen(
                state = state,
                onBack = { page = StoragePhaseFourPage.Main },
            )
            return
        }
        StoragePhaseFourPage.FolderIndex -> {
            StorageFolderIndexScreen(
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

    StorageManagementHubScreen(
        state = state,
        onBack = onBack,
        additionalFeatureEntries = listOf(
            StorageInlineFeatureEntry(
                title = "精细整理",
                subtitle = "相似照片、截图、连拍、画质候选与分类整理",
                tone = Color(0xFF8DF9EA),
                onClick = { page = StoragePhaseFourPage.Organization },
            ),
            StorageInlineFeatureEntry(
                title = "目录索引",
                subtitle = "对授权大目录分批扫描并保存恢复断点",
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
}
