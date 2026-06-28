package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.OpenGLGlassCardLayer
import com.yuchen.ailedger.ui.gl.OpenGLGlassDynamicState

/**
 * 旧版 OpenGL 的统一宿主结构。
 * 实验室原版样本和设置页顶部状态卡片必须共同经过这里，避免参数一致但
 * 纹理采样、裁剪或几何链不同，产生不同光学外观。
 */
@Composable
fun LegacyOpenGLGlassPreviewShell(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    coordinateSource: GlassCoordinateSource? = null,
    pressProgress: Float = 0f,
    pressCenter: Offset = Offset(0.5f, 0.5f),
    viewportTopInsetPx: Float = 0f,
    dynamicState: OpenGLGlassDynamicState? = null,
    clipOpenGlHost: Boolean = true,
    content: @Composable () -> Unit
) {
    val ownedCoordinates = remember { GlassCoordinateSource() }
    val coordinates = coordinateSource ?: ownedCoordinates
    val sourceBackdrop = LocalGlassBackdrop.current
    val optimizedBackdrop = remember(sourceBackdrop) {
        sourceBackdrop?.copy(
            borderStyle = sourceBackdrop.borderStyle.copy(
                // 旧 Shader 会执行中心样本 + 8 个偏移样本。
                // 当前背景纹理已经预模糊，再开启偏移采样会形成清晰的九重背景。
                openGlSampleRadiusScale = 0f
            )
        )
    }
    val previewShape = remember(radius) { RoundedCornerShape(radius.dp) }

    CompositionLocalProvider(LocalGlassBackdrop provides optimizedBackdrop) {
        val placementModifier = modifier.onPlaced { coordinates.coordinates = it }
        if (clipOpenGlHost) {
            Box(
                modifier = placementModifier
                    // 聊天大 Shell 保持原有 Compose 最终轮廓裁剪。
                    .clip(previewShape)
            ) {
                OpenGLGlassCardLayer(
                    radius = radius,
                    glassIntensity = glassIntensity,
                    coordinateSource = coordinates,
                    modifier = Modifier.matchParentSize(),
                    pressProgress = pressProgress,
                    pressCenter = pressCenter,
                    viewportTopInsetPx = viewportTopInsetPx,
                    dynamicState = dynamicState,
                )
                content()
            }
        } else {
            Box(modifier = placementModifier) {
                // 设置页短 Shell 不把 TextureView 放入 Compose clip 的离屏 RenderNode。
                // 圆角和透明区仍由原 Shader 输出，内容层继续保持相同轮廓裁剪。
                OpenGLGlassCardLayer(
                    radius = radius,
                    glassIntensity = glassIntensity,
                    coordinateSource = coordinates,
                    modifier = Modifier.matchParentSize(),
                    pressProgress = pressProgress,
                    pressCenter = pressCenter,
                    viewportTopInsetPx = viewportTopInsetPx,
                    dynamicState = dynamicState,
                )
                Box(Modifier.matchParentSize().clip(previewShape)) {
                    content()
                }
            }
        }
    }
}
