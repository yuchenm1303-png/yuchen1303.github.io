package com.yuchen.ailedger.ui.gl

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.LocalGlassBackdrop
import com.yuchen.ailedger.ui.SampledWeatherEdgeRefraction
import com.yuchen.ailedger.ui.SampledWeatherGlassBackdrop

/**
 * Temporary compatibility bridge for the card-bound glass entry.
 *
 * Codex 的性能优化把原来的 ui/gl/OpenGLGlassCardLayer.kt 整个删掉了，
 * 但 Glass.kt 仍然会 import 并调用这个入口，导致 CI 出现 unresolved reference。
 *
 * 这里先恢复同名入口，保证编译通过，并让 Shell/Card 至少重新显示采样背景玻璃。
 * 后续如果继续打磨 OpenGL shader，可以再把完整 EGL/TextureView 版本替换回这里。
 */
@Composable
fun OpenGLGlassCardLayer(
    radius: Int,
    glassIntensity: Float,
    coordinateSource: GlassCoordinateSource? = null,
    modifier: Modifier = Modifier
) {
    val backdrop = LocalGlassBackdrop.current ?: return
    val source = coordinateSource ?: return
    Box(modifier = modifier) {
        SampledWeatherGlassBackdrop(
            modifier = Modifier.matchParentSize(),
            radius = radius,
            coordinateSource = source,
            quality = backdrop.quality,
            motionIntensity = backdrop.motionIntensity,
            theme = backdrop.theme,
            blurRadiusDp = 118,
            liftAlpha = 0.96f * glassIntensity.coerceIn(0.70f, 1.25f)
        )
        SampledWeatherEdgeRefraction(
            modifier = Modifier.matchParentSize(),
            radius = radius,
            coordinateSource = source,
            quality = backdrop.quality,
            motionIntensity = backdrop.motionIntensity,
            theme = backdrop.theme,
            strength = 0.22f
        )
    }
}
