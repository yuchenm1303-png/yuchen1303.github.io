package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.padding
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
 * 这里不绘制任何边缘流光或底角遮罩。
 * 更重要的是，这里不再把 viewportTopInset 传给 OpenGL 内部做 rectOffset 偏移；
 * 而是用 Compose 的 padding 创建真实的可见玻璃槽位。
 *
 * 这样 OpenGL Host、Compose 外框、内容层重新共用同一个实际边界，
 * 避免父级内部偏移绘制带来的底角毛刺和上边缘微抖。
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

    val slotModifier = if (role == GlassRole.Shell && snappedViewportTopInset > 0.dp) {
        modifier.padding(top = snappedViewportTopInset)
    } else {
        modifier
    }

    GlassPanel(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = motionIntensity,
        radius = radius,
        modifier = slotModifier,
        role = role,
        viewportTopInset = 0.dp,
        intensity = null,
        content = content
    )
}
