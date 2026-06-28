package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.OpenGLGlassCardLayer
import com.yuchen.ailedger.ui.gl.OpenGLGlassDynamicState
import kotlin.math.abs

private const val LegacyOpenGLSettleEpsilonPx = 0.25f
private const val LegacyOpenGLStableFrameCount = 3
private const val LegacyOpenGLMaxSettleFrames = 180

/**
 * 旧版 OpenGL 的统一宿主结构。
 * 实验室原版样本、设置页顶部状态卡片和首页聊天大玻璃共同经过这里，避免参数一致但
 * 纹理采样、裁剪或几何链不同，产生不同光学外观。
 *
 * Compose 的 graphicsLayer 入场动画不会保证再次触发 onPlaced。这里在宿主首次出现
 * 后按真实 VSync 观察左上角和右下角的根坐标，只有变换连续稳定后才通知当前 Host
 * 补交一次最终帧；不轮询常驻、不恢复逐帧重组，也不依赖固定延时。
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

    LaunchedEffect(coordinates) {
        var observedAttachedFrame = false
        var stableFrameCount = 0
        var lastTopLeft = Offset.Zero
        var lastBottomRight = Offset.Zero

        repeat(LegacyOpenGLMaxSettleFrames) {
            withFrameNanos { }
            val layoutCoordinates = coordinates.coordinates
            if (layoutCoordinates == null || !layoutCoordinates.isAttached) {
                observedAttachedFrame = false
                stableFrameCount = 0
                return@repeat
            }

            val size = layoutCoordinates.size
            val topLeft = layoutCoordinates.localToRoot(Offset.Zero)
            val bottomRight = layoutCoordinates.localToRoot(
                Offset(size.width.toFloat(), size.height.toFloat())
            )
            val transformStable = observedAttachedFrame &&
                topLeft.nearlyEquals(lastTopLeft) &&
                bottomRight.nearlyEquals(lastBottomRight)

            stableFrameCount = if (transformStable) stableFrameCount + 1 else 0
            observedAttachedFrame = true
            lastTopLeft = topLeft
            lastBottomRight = bottomRight

            if (stableFrameCount >= LegacyOpenGLStableFrameCount) {
                coordinates.requestOpenGlFrameSync()
                return@LaunchedEffect
            }
        }

        // 极端长动画或持续变换只在观察窗口结束时补一次，不启动常驻循环。
        if (observedAttachedFrame) coordinates.requestOpenGlFrameSync()
    }

    CompositionLocalProvider(LocalGlassBackdrop provides optimizedBackdrop) {
        Box(
            modifier = modifier
                // 旧 Shader 的抗锯齿带位于几何边界外侧，统一由 Compose 裁剪最终轮廓。
                .clip(previewShape)
                .onPlaced { coordinates.coordinates = it }
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

private fun Offset.nearlyEquals(other: Offset): Boolean =
    abs(x - other.x) <= LegacyOpenGLSettleEpsilonPx &&
        abs(y - other.y) <= LegacyOpenGLSettleEpsilonPx
