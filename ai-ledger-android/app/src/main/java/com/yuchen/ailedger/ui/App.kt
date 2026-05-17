package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.model.AppTab

@Composable
fun AiAssistantNativeApp(viewModel: AssistantViewModel = viewModel()) {
    val state = viewModel.uiState

    MaterialTheme {
        Surface(color = Color(0xFF07132D), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                WeatherNightBackground(state.quality)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp)
                ) {
                    when (state.currentTab) {
                        AppTab.Assistant -> AssistantScreen(state)
                        AppTab.Tools -> ToolsScreen(state)
                        AppTab.Settings -> SettingsScreen(
                            state = state,
                            aiEndpoint = viewModel.aiEndpoint,
                            onQualityChange = viewModel::selectQuality
                        )
                    }
                }

                LiquidBottomBar(
                    currentTab = state.currentTab,
                    quality = state.quality,
                    onTabChange = viewModel::selectTab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }
        }
    }
}
