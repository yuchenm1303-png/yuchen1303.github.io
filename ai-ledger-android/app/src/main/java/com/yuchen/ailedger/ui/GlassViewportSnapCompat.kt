package com.yuchen.ailedger.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shell 视口专用入口：把模型卡展开产生的 viewportTopInset 统一吸附到整数像素。
 *
 * 不能在这里用 modifier.padding(top = inset) 创建真实槽位，
 * 否则 OpenGL Host 的实际高度会跟着模型卡动画每帧缩小，底边会回到 Surface resize 抖动。
 *
 * 这里额外做一个很窄的 1px 稳定死区：模型卡视觉高度仍然可以用弹簧回弹，
 * 但聊天玻璃上边界不会跟着弹簧尾部在相邻像素之间来回跳。
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
    val stabilizer = remember { ShellViewportInsetStabilizer() }
    val rawInsetPx = if (role == GlassRole.Shell && viewportTopInset > 0.dp) {
        with(density) { viewportTopInset.toPx().roundToInt() }
    } else {
        0
    }
    val stableInsetPx = if (role == GlassRole.Shell) {
        stabilizer.update(rawInsetPx)
    } else {
        rawInsetPx
    }
    val snappedViewportTopInset = if (stableInsetPx > 0) {
        // 加极小 0.01px 偏置，避免后续 toPx().toInt() 因浮点误差少 1px。
        with(density) { (stableInsetPx + 0.01f).toDp() }
    } else {
        0.dp
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

private class ShellViewportInsetStabilizer {
    private var stablePx = 0
    private var lastRawPx = 0
    private var sameRawFrames = 0

    fun update(rawPx: Int): Int {
        val safeRaw = rawPx.coerceAtLeast(0)
        if (safeRaw == lastRawPx) {
            sameRawFrames += 1
        } else {
            sameRawFrames = 0
        }

        stablePx = when {
            safeRaw == 0 -> 0
            stablePx == 0 -> safeRaw
            abs(safeRaw - stablePx) <= 1 -> {
                // 弹簧尾部常见的 112/113px 来回跳直接压住；
                // 如果真实最终值稳定停在相邻像素上，连续几帧后再接收。
                if (sameRawFrames >= 3) safeRaw else stablePx
            }
            else -> safeRaw
        }

        lastRawPx = safeRaw
        return stablePx
    }
}
