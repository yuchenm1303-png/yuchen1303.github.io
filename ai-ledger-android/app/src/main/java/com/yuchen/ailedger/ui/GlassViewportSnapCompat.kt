package com.yuchen.ailedger.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
 * 当前额外恢复一件原版结构里的关键约束：
 * Shell 稳定 Surface 外层重新绑定同一套圆角裁剪边界。
 * 这不改变 Host 尺寸，只让 OpenGL 本体、Compose 外框和底角裁剪重新回到同一条圆角边界，
 * 避免稳定大 Surface 内部偏移绘制造成底角细亮毛刺。
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

    val shellBoundaryModifier = if (role == GlassRole.Shell) {
        modifier.graphicsLayer {
            clip = true
            shape = RoundedCornerShape(radius.dp)
        }
    } else {
        modifier
    }

    GlassPanel(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = motionIntensity,
        radius = radius,
        modifier = shellBoundaryModifier,
        role = role,
        viewportTopInset = snappedViewportTopInset,
        intensity = null,
        content = content
    )
}
