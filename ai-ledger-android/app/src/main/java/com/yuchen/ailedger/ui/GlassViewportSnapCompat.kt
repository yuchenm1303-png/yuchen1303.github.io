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
 * 注意：这里不绘制任何边缘流光或底角遮罩。
 * OpenGL 玻璃的边缘光效必须回到 Glass.kt 原本的 shellPressSurfaceOptics / ordinaryPressSurfaceOptics，
 * 否则会和 OpenGL 圆角抗锯齿边界叠出底角细亮边。
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
