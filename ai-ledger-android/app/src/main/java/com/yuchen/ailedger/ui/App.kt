package com.yuchen.ailedger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.RenderQuality

@Composable
fun AiAssistantNativeApp(viewModel: AssistantViewModel = viewModel()) {
    val state = viewModel.uiState
    val backdropOrigin = remember { BackdropCoordinateSource() }
    val backdropTicker = remember { BackdropFrameTicker() }
    val glassRegistry = remember { GlassItemRegistry() }
    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importCustomBackground(uri)
    }
    val blurredBackdrop = rememberBlurredBackdropBitmap(
        theme = state.backgroundTheme,
        quality = state.quality,
        params = state.backdropParams,
        customBackgroundPath = state.customBackgroundPath
    )

    LaunchedEffect(Unit) {
        while (true) {
            val frameTime = withFrameNanos { it }
            backdropTicker.frameNanos = frameTime
        }
    }

    MaterialTheme {
        Surface(color = Color(0xFF07132D), modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalGlassBackdrop provides GlassBackdropSpec(
                    quality = state.quality,
                    motionIntensity = state.motionIntensity,
                    theme = state.backgroundTheme,
                    params = state.backdropParams,
                    borderStyle = state.glassBorderStyle
                ),
                LocalBlurredBackdrop provides blurredBackdrop,
                LocalBackdropOrigin provides backdropOrigin,
                LocalBackdropFrameTicker provides backdropTicker,
                LocalGlassItemRegistry provides glassRegistry
            ) {
                Box(Modifier.fillMaxSize()) {
                    WeatherNightBackground(
                        quality = state.quality,
                        motionIntensity = state.motionIntensity,
                        theme = state.backgroundTheme,
                        params = state.backdropParams,
                        customBackgroundPath = state.customBackgroundPath,
                        modifier = Modifier
                            .fillMaxSize()
                            .onPlaced { backdropOrigin.coordinates = it }
                    )

                    UnifiedGlassBackdropLayer(Modifier.fillMaxSize())

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

                    if (state.currentTab == AppTab.Settings) {
                        GlassDebugFloatingPanel(
                            state = state,
                            onBackdropChange = viewModel::setBackdropDebugParams,
                            onBorderChange = viewModel::setGlassBorderStyle,
                            onUploadBackgroundClick = {
                                backgroundPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onClearCustomBackgroundClick = viewModel::clearCustomBackground,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 84.dp)
                        )
                    }

                    BottomDockSeparationMist(
                        quality = state.quality,
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
private fun BottomDockSeparationMist(quality: RenderQuality, modifier: Modifier = Modifier) {
    val blur = if (quality.enableMotion) 14.dp else 0.dp
    val height = if (quality.enableMotion) 112.dp else 76.dp
    val bottomAlpha = if (quality.enableMotion) 0x88 else 0x5C
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .blur(blur)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x1A08142C),
                        Color(0x5208142C),
                        Color(red = 0x03, green = 0x08, blue = 0x17, alpha = bottomAlpha)
                    )
                )
            )
    )
}
