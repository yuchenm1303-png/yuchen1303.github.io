package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.model.RenderQuality

enum class OpenGlShellMood {
    Hero,
    Summary,
    List,
    Settings
}

/**
 * 非聊天核心 Shell 的卡片统一入口。
 *
 * 这些功能页、股票页和设置页卡片不再提升为 OpenGL Shell，统一转为白色雾面玻璃。
 * 真正的大玻璃容器仍由 GlassPanel(role = GlassRole.Shell) 的专用调用链负责，
 * 聊天框 OpenGL Host / viewportTopInset 链路不经过这里。
 */
@Composable
fun OpenGlShellGlass(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    mood: OpenGlShellMood = OpenGlShellMood.Hero,
    forceOpenGl: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    WhiteFrostGlassCard(
        modifier = modifier,
        radius = radius,
        onClick = onClick,
        frostAlpha = (0.115f + glassIntensity.coerceIn(0.70f, 1.30f) * 0.030f).coerceIn(0.12f, 0.18f),
    ) {
        content()
    }
}
