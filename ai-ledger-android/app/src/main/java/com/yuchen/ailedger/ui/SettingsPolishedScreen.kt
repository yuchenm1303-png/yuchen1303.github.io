package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality

/**
 * 设置页稳定公开入口。
 * 具体实现已按入口、详情和通用组件拆分，避免旧单文件重复保留整套界面代码。
 */
@Composable
fun SettingsPolishedScreen(
    state: AssistantUiState,
    aiEndpoint: String,
    onQualityChange: (RenderQuality) -> Unit,
    onPreviewConversationChange: (Boolean) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onRainbowPrismChange: (RainbowPrismStyle) -> Unit,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit,
) {
    SettingsPolishedScreenOptimized(
        state = state,
        aiEndpoint = aiEndpoint,
        onQualityChange = onQualityChange,
        onPreviewConversationChange = onPreviewConversationChange,
        onGlassPresetChange = onGlassPresetChange,
        onBackgroundThemeChange = onBackgroundThemeChange,
        onGlassIntensityChange = onGlassIntensityChange,
        onMotionIntensityChange = onMotionIntensityChange,
        onRainbowPrismChange = onRainbowPrismChange,
        onBackdropChange = onBackdropChange,
        onBorderChange = onBorderChange,
        onUploadBackgroundClick = onUploadBackgroundClick,
        onClearCustomBackgroundClick = onClearCustomBackgroundClick,
    )
}
