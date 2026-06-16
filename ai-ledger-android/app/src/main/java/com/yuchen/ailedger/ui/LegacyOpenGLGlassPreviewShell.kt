package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.OpenGLGlassCardLayer

@Composable
fun LegacyOpenGLGlassPreviewShell(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val coordinates = remember { GlassCoordinateSource() }
    val sourceBackdrop = LocalGlassBackdrop.current
    val optimizedBackdrop = remember(sourceBackdrop) {
        sourceBackdrop?.copy(
            borderStyle = sourceBackdrop.borderStyle.copy(
                // 背景本身已经是预模糊纹理；关闭旧 Shader 的 1+8 偏移采样，
                // 避免大半径下出现九层背景重影。
                openGlSampleRadiusScale = 0f
            )
        )
    }
    val previewShape = remember(radius) { RoundedCornerShape(radius.dp) }

    CompositionLocalProvider(LocalGlassBackdrop provides optimizedBackdrop) {
        Box(
            modifier = modifier
                // 旧 Shader 的抗锯齿带位于几何边界外侧；由 Compose 同半径裁剪
                // 接管最终边缘覆盖，去除 TextureView 外扩毛刺。
                .clip(previewShape)
                .onPlaced { coordinates.coordinates = it }
        ) {
            OpenGLGlassCardLayer(
                radius = radius,
                glassIntensity = glassIntensity,
                coordinateSource = coordinates,
                modifier = Modifier.matchParentSize()
            )
            content()
        }
    }
}
