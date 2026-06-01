package com.yuchen.ailedger.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.roundToInt

/**
 * Shell 视口专用入口。
 *
 * 这里统一处理两件只属于首页聊天大玻璃的事情：
 * 1. 把模型卡展开产生的 viewportTopInset 吸附到整数像素，避免 0.x px 微颤。
 * 2. 在 OpenGL 本体上方恢复 Compose 边缘流光层，让最外圈边缘统一由 Compose 画，
 *    避免 OpenGL 圆角抗锯齿和 Compose 外框重叠时产生底角细亮边。
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

    val transition = rememberInfiniteTransition(label = "opengl-shell-edge-light-clock")
    val shimmerPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "opengl-shell-edge-light-phase"
    )

    GlassPanel(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = motionIntensity,
        radius = radius,
        modifier = modifier,
        role = role,
        viewportTopInset = snappedViewportTopInset,
        intensity = null
    ) {
        if (role == GlassRole.Shell) {
            Box(Modifier.matchParentSize()) {
                content()
                Box(
                    Modifier
                        .matchParentSize()
                        .openGlShellEdgeLightOverlay(
                            quality = quality,
                            radius = radius,
                            shimmerPhase = shimmerPhase,
                            motionIntensity = motionIntensity,
                            glassIntensity = glassIntensity
                        )
                )
            }
        } else {
            content()
        }
    }
}

private fun Modifier.openGlShellEdgeLightOverlay(
    quality: RenderQuality,
    radius: Int,
    shimmerPhase: Float,
    motionIntensity: Float,
    glassIntensity: Float
): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val safeRadius = radius.dp.toPx().coerceAtLeast(2f)
    val corner = CornerRadius(safeRadius, safeRadius)
    val intensity = glassIntensity.coerceIn(0.60f, 1.35f)
    val motion = motionIntensity.coerceIn(0f, 1f)

    val rimInset = 0.72.dp.toPx()
    val innerInset = 1.70.dp.toPx()
    val outerSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))
    val innerSize = Size((w - innerInset * 2f).coerceAtLeast(1f), (h - innerInset * 2f).coerceAtLeast(1f))

    val mainRim = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.120f * intensity),
            Color(0xFF8DF9EA).copy(alpha = 0.050f * intensity),
            Color.Transparent,
            Color.Black.copy(alpha = 0.070f * intensity),
            Color(0xFFFF8FE7).copy(alpha = 0.060f * intensity),
            Color.White.copy(alpha = 0.080f * intensity)
        ),
        start = Offset(0f, 0f),
        end = Offset(w, h)
    )

    val topHairline = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.190f * intensity),
            Color(0xFFBDFEFF).copy(alpha = 0.070f * intensity),
            Color.Transparent
        ),
        start = Offset(w * 0.06f, 0f),
        end = Offset(w * 0.78f, h * 0.22f)
    )

    val movingGlint = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color(0xFFFF74EC).copy(alpha = 0.145f * intensity * motion),
            Color.White.copy(alpha = 0.205f * intensity * motion),
            Color(0xFF63FFF2).copy(alpha = 0.155f * intensity * motion),
            Color.Transparent
        ),
        start = Offset(w * (shimmerPhase - 0.36f), -h * 0.05f),
        end = Offset(w * (shimmerPhase + 0.22f), h * 0.30f)
    )

    val cornerCatchLeft = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.125f * intensity),
            Color(0xFF8DF9EA).copy(alpha = 0.040f * intensity),
            Color.Transparent
        ),
        center = Offset(w * 0.050f, h * 0.035f),
        radius = w * 0.30f
    )

    val cornerCatchRight = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.070f * intensity),
            Color(0xFFFF8FE7).copy(alpha = 0.035f * intensity),
            Color.Transparent
        ),
        center = Offset(w * 0.970f, h * 0.040f),
        radius = w * 0.24f
    )

    val bottomCornerScrubLeft = Brush.radialGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.100f),
            Color.Black.copy(alpha = 0.030f),
            Color.Transparent
        ),
        center = Offset(0f, h),
        radius = w * 0.22f
    )

    val bottomCornerScrubRight = Brush.radialGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.100f),
            Color.Black.copy(alpha = 0.030f),
            Color.Transparent
        ),
        center = Offset(w, h),
        radius = w * 0.22f
    )

    onDrawBehind {
        // 最外层边缘统一交给 Compose 画，恢复旧版流光光带。
        drawRoundRect(
            brush = mainRim,
            topLeft = Offset(rimInset, rimInset),
            size = outerSize,
            cornerRadius = corner,
            style = Stroke(0.46.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = topHairline,
            topLeft = Offset(innerInset, innerInset),
            size = innerSize,
            cornerRadius = corner,
            style = Stroke(0.22.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        if (quality.enableMotion && motion > 0.02f) {
            drawRoundRect(
                brush = movingGlint,
                topLeft = Offset(rimInset, rimInset),
                size = outerSize,
                cornerRadius = corner,
                style = Stroke(0.62.dp.toPx()),
                blendMode = BlendMode.Plus
            )
        }
        drawRoundRect(
            brush = cornerCatchLeft,
            topLeft = Offset(rimInset, rimInset),
            size = outerSize,
            cornerRadius = corner,
            style = Stroke(0.38.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = cornerCatchRight,
            topLeft = Offset(rimInset, rimInset),
            size = outerSize,
            cornerRadius = corner,
            style = Stroke(0.30.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        // 轻微压暗两个底角最外 1px 左右，消掉 OpenGL 圆角边缘与 Compose 外框重叠形成的细亮毛刺。
        drawRoundRect(
            brush = bottomCornerScrubLeft,
            topLeft = Offset(rimInset, rimInset),
            size = outerSize,
            cornerRadius = corner,
            style = Stroke(1.10.dp.toPx()),
            blendMode = BlendMode.Multiply
        )
        drawRoundRect(
            brush = bottomCornerScrubRight,
            topLeft = Offset(rimInset, rimInset),
            size = outerSize,
            cornerRadius = corner,
            style = Stroke(1.10.dp.toPx()),
            blendMode = BlendMode.Multiply
        )
    }
}
