package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * 8830b4c 版普通 Compose 玻璃光效的父级单卡绘制版本。
 *
 * 光效强度现在经过尺寸归一化：小玻璃增强触点 bloom 与 rim，大玻璃压低整体
 * 半径、alpha 和边缘扫光，避免同一组 master 参数在大小玻璃之间失衡。
 */
internal fun DrawScope.drawOrdinaryParentWhitePressOptics(item: VisibleOrdinaryGlassItem) {
    val node = item.node
    if (!node.pressable || node.role == GlassRole.Shell) return

    val pressValue = node.pressProgress.coerceAtLeast(0f)
    val lensValue = node.lensProgress.coerceAtLeast(0f)
    val sweepValue = node.sweepProgress.coerceAtLeast(0f)
    val active = maxOf(pressValue, lensValue, sweepValue)
    if (active <= 0.001f) return

    val rect = item.rect
    val w = rect.width.coerceAtLeast(1f)
    val h = rect.height.coerceAtLeast(1f)
    val maxSide = max(w, h)
    val minSide = minOf(w, h).coerceAtLeast(1f)
    val profile = ordinaryGlassSizeAdaptiveProfile(w, h, node.role)
    val center = Offset(
        x = item.motion.pressCenter.x.coerceIn(0f, 1f) * w,
        y = item.motion.pressCenter.y.coerceIn(0f, 1f) * h,
    )
    val radiusPx = node.radius.dp.toPx().coerceAtMost(minSide * 0.5f)
    val cornerRadius = CornerRadius(radiusPx, radiusPx)
    val rimInset = 0.50.dp.toPx().coerceAtMost(minSide * 0.08f)
    val rimRadius = (radiusPx - rimInset).coerceAtLeast(0f)
    val rimSize = Size(
        width = (w - rimInset * 2f).coerceAtLeast(1f),
        height = (h - rimInset * 2f).coerceAtLeast(1f),
    )

    val pressShape = ordinaryParent8830SmoothStep((pressValue + lensValue * 0.62f).coerceIn(0f, 2.65f) / 2.65f)
    val prismGain = item.motion.prism.takeIf { it > 0.001f } ?: 0.68f
    val lightPower = (item.motion.touchLight * profile.lightGain * (0.34f + lensValue * 0.62f)).coerceIn(0f, 42f)
    val chromaPower = (prismGain * profile.sweepGain * (0.18f + pressValue * 0.24f + sweepValue * 0.30f)).coerceIn(0f, 28f)
    val sweepPower = (item.motion.sweepGain * profile.sweepGain * sweepValue).coerceIn(0f, 30f)
    val sweepPhase = (sweepValue / 2.20f).coerceIn(0f, 1.20f)
    val sweepX = -0.42f + sweepPhase * 1.84f
    val bloomBase = maxSide * (0.34f + 0.026f * lightPower.coerceIn(0f, 14f) + 0.22f * pressShape)
    val smallContrastRadius = maxSide * (0.70f + profile.smallT * 0.18f + pressShape * 0.24f)
    val largeMaxRadius = maxSide * (1.42f - profile.largeT * 0.34f)
    val softBloomRadius = max(bloomBase * profile.bloomRadiusGain, smallContrastRadius)
        .coerceAtMost(largeMaxRadius.coerceAtLeast(maxSide * 0.72f))

    val transform = item.transform
    withTransform({
        translate(left = rect.left, top = rect.top + transform.translationY)
        scale(
            scaleX = transform.scaleX,
            scaleY = transform.scaleY,
            pivot = Offset(w * transform.originX, h * transform.originY),
        )
    }) {
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF1FA).copy(alpha = (0.050f * lightPower + 0.010f * chromaPower).coerceIn(0f, 0.82f)),
                    Color(0xFFE8FFFB).copy(alpha = (0.034f * lightPower).coerceIn(0f, 0.52f)),
                    Color(0xFFFFE4C7).copy(alpha = (0.012f * lightPower + 0.012f * chromaPower).coerceIn(0f, 0.30f)),
                    Color(0xFFBDEBFF).copy(alpha = (0.012f * chromaPower).coerceIn(0f, 0.28f)),
                    Color.Transparent
                ),
                center = center,
                radius = softBloomRadius
            ),
            size = Size(w, h),
            cornerRadius = cornerRadius,
            blendMode = BlendMode.Screen
        )

        if (sweepPower > 0.001f || chromaPower > 0.001f) {
            val edgeStroke = (
                0.64.dp.toPx() +
                    0.10.dp.toPx() * sweepPower.coerceIn(0f, 16f) * (1f + profile.smallT * 0.35f)
                ).coerceAtMost((4.8.dp.toPx() * (1f - profile.largeT * 0.34f)).coerceAtLeast(0.72.dp.toPx()))
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFFF78DA).copy(alpha = (0.020f * chromaPower).coerceIn(0f, 0.46f)),
                        Color(0xFFFFE6B8).copy(alpha = (0.026f * lightPower + 0.016f * sweepPower).coerceIn(0f, 0.58f)),
                        Color(0xFF76FFF0).copy(alpha = (0.024f * chromaPower + 0.020f * sweepPower).coerceIn(0f, 0.54f)),
                        Color.Transparent
                    ),
                    start = Offset(w * (sweepX - 0.34f), h * -0.06f),
                    end = Offset(w * (sweepX + 0.40f), h * 1.06f)
                ),
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = CornerRadius(rimRadius, rimRadius),
                style = Stroke(edgeStroke),
                blendMode = BlendMode.Screen
            )
        }
    }
}

private fun ordinaryParent8830SmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
