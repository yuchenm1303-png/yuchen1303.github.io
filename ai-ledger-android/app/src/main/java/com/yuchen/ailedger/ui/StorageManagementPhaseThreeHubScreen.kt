package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

@Composable
fun StorageManagementPhaseThreeHubScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    var showOrganization by remember { mutableStateOf(false) }
    if (showOrganization) {
        StorageMediaOrganizationScreen(
            state = state,
            onBack = { showOrganization = false },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        StorageManagementHubScreen(
            state = state,
            onBack = onBack,
        )
        PressableGlass(
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            radius = 999,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 118.dp)
                .width(116.dp)
                .height(42.dp),
            role = GlassRole.Floating,
            onClick = { showOrganization = true },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "精细整理 ›",
                    color = Color(0xFF8DF9EA).copy(alpha = 0.94f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}
