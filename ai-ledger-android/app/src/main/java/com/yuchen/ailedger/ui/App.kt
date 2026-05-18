package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
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
            CompositionLocalProvider(
                LocalGlassBackdrop provides GlassBackdropSpec(
                    quality = state.quality,
                    motionIntensity = state.motionIntensity,
                    theme = state.backgroundTheme
                )
            ) {
                Box(Modifier.fillMaxSize()) {
                    WeatherNightBackground(
                        quality = state.quality,
                        motionIntensity = state.motionIntensity,
                        theme = state.backgroundTheme
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp)
                    ) {
                        when (state.currentTab) {
                            AppTab.Assistant -> AssistantScreen(state)
                            AppTab.Tools -> ToolsScreen(state)
                            AppTab.Settings -> SettingsScreen(
                                state = state,
                                aiEndpoint = viewModel.aiEndpoint,
                                onQualityChange = viewModel::selectQuality,
                                onPreviewConversationChange = viewModel::setShowPreviewConversation,
                                onGlassPresetChange = viewModel::setGlassPreset,
                                onBackgroundThemeChange = viewModel::setBackgroundTheme,
                                onGlassIntensityChange = viewModel::setGlassIntensity,
                                onMotionIntensityChange = viewModel::setMotionIntensity
                            )
                        }
                    }

                    BottomDockSeparationMist(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    )

                    CompactLiquidBottomBar(
                        currentTab = state.currentTab,
                        quality = state.quality,
                        glassIntensity = state.glassIntensity,
                        motionIntensity = state.motionIntensity,
                        onTabChange = viewModel::selectTab,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomDockSeparationMist(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(128.dp)
            .blur(22.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x2408142C),
                        Color(0x7208142C),
                        Color(0xAA030817)
                    )
                )
            )
    )
}
