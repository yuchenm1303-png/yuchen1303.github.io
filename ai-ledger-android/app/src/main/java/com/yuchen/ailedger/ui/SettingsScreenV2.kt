package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RenderQuality

@Composable
fun SettingsScreenV2(
    state: AssistantUiState,
    aiEndpoint: String,
    onQualityChange: (RenderQuality) -> Unit,
    onPreviewConversationChange: (Boolean) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit
) {
    SettingsPolishedScreen(
        state = state,
        aiEndpoint = aiEndpoint,
        onQualityChange = onQualityChange,
        onPreviewConversationChange = onPreviewConversationChange,
        onGlassPresetChange = onGlassPresetChange,
        onBackgroundThemeChange = onBackgroundThemeChange,
        onGlassIntensityChange = onGlassIntensityChange,
        onMotionIntensityChange = onMotionIntensityChange,
        onBackdropChange = onBackdropChange,
        onBorderChange = onBorderChange,
        onUploadBackgroundClick = onUploadBackgroundClick,
        onClearCustomBackgroundClick = onClearCustomBackgroundClick
    )
}
