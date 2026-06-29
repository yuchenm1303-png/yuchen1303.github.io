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
 * 旧版 OpenGL Shell 的生产宿主。
 *
 * 这里只负责旧 Renderer 所需的背景覆盖、最终轮廓裁剪和单个 OpenGL Host。外层已经
 * 持有坐标源时可关闭本层 placement 上报，避免同一个 Shell 在相邻两层重复执行
 * onPlaced；独立预览仍可由宿主自己维护坐标。
 */
@Composable
fun LegacyOpenGLShellHost(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    coordinateSource: GlassCoordinateSource? = null,
    manageCoordinatePlacement: Boolean = coordinateSource == null,
    pressProgress: Float = 0f,
    pressCenter: Offset = Offset(0.5f, 0.5f),
    viewportTopInsetPx: Float = 0f,
    dynamicState: OpenGLGlassDynamicState? = null,
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
    val startupBackdrop = OpenGlStartupBackdropBridge.backdrop ?: LocalBlurredBackdrop.current
    val shellShape = remember(radius) { RoundedCornerShape(radius.dp) }
    val placementModifier = if (manageCoordinatePlacement) {
        Modifier.onPlaced { coordinates.coordinates = it }
    } else {
        Modifier
    }

    CompositionLocalProvider(
        LocalGlassBackdrop provides optimizedBackdrop,
        // 仅在旧版 Shell 宿主内部覆盖；普通玻璃仍读取完整三档 LocalBlurredBackdrop。
        LocalBlurredBackdrop provides startupBackdrop
    ) {
        Box(
            modifier = modifier
                // 旧 Shader 的抗锯齿带位于几何边界外侧，统一由 Compose 裁剪最终轮廓。
                .clip(shellShape)
                .then(placementModifier)
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
    }
}

/**
 * 旧预览入口保留给现有实验室调用。生产页面应使用 [LegacyOpenGLShellHost]，避免把
 * “Preview” 命名继续扩散到聊天和设置页的正式渲染链。
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
    content: @Composable () -> Unit
) {
    LegacyOpenGLShellHost(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = motionIntensity,
        radius = radius,
        modifier = modifier,
        coordinateSource = coordinateSource,
        manageCoordinatePlacement = true,
        pressProgress = pressProgress,
        pressCenter = pressCenter,
        viewportTopInsetPx = viewportTopInsetPx,
        dynamicState = dynamicState,
        content = content,
    )
}
