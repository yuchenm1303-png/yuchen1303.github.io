package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Box(modifier = Modifier.fillMaxSize()) {
        StorageManagementHubScreen(
            state = state,
            onBack = onBack,
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 112.dp),
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StoragePhaseEntry(
                    state = state,
                    text = "精细整理",
                    tone = Color(0xFF8DF9EA),
                    modifier = Modifier.weight(1f),
                ) { page = StoragePhaseFourPage.Organization }
                StoragePhaseEntry(
                    state = state,
                    text = "目录索引",
                    tone = Color(0xFF9CD8FF),
                    modifier = Modifier.weight(1f),
                ) { page = StoragePhaseFourPage.FolderIndex }
                StoragePhaseEntry(
                    state = state,
                    text = "设备优化",
                    tone = Color(0xFF83F3B8),
                    modifier = Modifier.weight(1f),
                ) { page = StoragePhaseFourPage.DeviceOptimization }
            }
        }
    }
}

@Composable
private fun StoragePhaseEntry(
    state: AssistantUiState,
    text: String,
    tone: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = modifier.height(42.dp),
        role = GlassRole.Floating,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "$text ›",
                color = tone.copy(alpha = 0.94f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}
