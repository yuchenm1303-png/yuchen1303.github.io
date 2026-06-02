package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.roundToInt

/**
 * Shell 视口专用入口：只负责把模型卡展开产生的 viewportTopInset 统一吸附到整数像素。
 *
 * 不能在这里用 modifier.padding(top = inset) 创建真实槽位，
 * 否则 OpenGL Host 的实际高度会跟着模型卡动画每帧缩小，底边会回到 Surface resize 抖动。
 *
 * 正确做法是：父容器和 OpenGL Surface 保持稳定尺寸，
 * 只把已经像素对齐的 viewportTopInset 传给底层 GlassPanel / OpenGL shader。
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
