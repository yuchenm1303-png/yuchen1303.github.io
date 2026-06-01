package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.roundToInt

/**
 * Shell 视口专用入口：把模型卡展开产生的 viewportTopInset 统一吸附到整数像素。
 *
 * 这样 Compose 外框 / 内容层 / OpenGL rectOffset 使用同一个像素边界，
 * 避免模型卡展开收回时上边缘出现 0.x px 的轻微颤动。
 */
@Composable
fun GlassPanel(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    role: GlassRole = GlassRole.Card,
    viewportTopInset: Dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val snappedViewportTopInset = if (role == GlassRole.Shell && viewportTopInset > 0.dp) {
        with(density) { viewportTopInset.toPx().roundToInt().toDp() }
    } else {
        viewportTopInset
    }

    GlassPanel(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = motionIntensity,
        radius = radius,
        modifier = modifier,
        role = role,
        viewportTopInset = snappedViewportTopInset,
        intensity = null,
        content = content
    )
}
