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
 * 普通 Compose 玻璃父级白光按压层。
 *
 * 只服务 GlassRole.Card / Chip / Floating / Nav / Flex 等普通 Compose 玻璃。
 * 这里不注册、不调用、不同步任何 OpenGL 结构；Shell 直接跳过。
 *
 * 这层替代父级旧彩虹/棱彩按压光效，统一在 ParentDraw 的圆角、foldout clip
 * 和 z-order 排除链里绘制，避免本地叠层造成黑框或双层光效。
 */
internal fun DrawScope.drawOrdinaryParentWhitePressOptics(item: VisibleOrdinaryGlassItem) {
    val node = item.node
    if (!node.pressable || node.role == GlassRole.Shell) return

    val dynamicPress = node.pressProgress.coerceAtLeast(0f)
    val lensValue = node.lensProgress.coerceAtLeast(0f)
    val sweepValue = node.sweepProgress.coerceAtLeast(0f)
    val afterValue = (node.sweepProgress * 0.55f).coerceAtLeast(0f)
    val active = maxOf(dynamicPress, lensValue, sweepValue, afterValue)
    if (active <= 0.001f) return

    val rect = item.rect
    val w = rect.width.coerceAtLeast(1f)
    val h = rect.height.coerceAtLeast(1f)
    val maxSide = max(w, h)
    val center = Offset(
        x = node.pressCenter.x.coerceIn(0f, 1f) * w,
        y = node.pressCenter.y.coerceIn(0f, 1f) * h
    )

    val motion = ComposeGlassLabState.motionStyle.normalized()
    val master = whiteOpticsMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f)
    val touchLight = whiteOpticsMotionPower(value = motion.touchLight, uiMax = 1.8f, effectiveMax = 16f) * master
    val sweepGain = whiteOpticsMotionPower(value = motion.sweep, uiMax = 1.5f, effectiveMax = 16f) * master
    val afterglow = whiteOpticsMotionPower(value = motion.afterglow, uiMax = 1.5f, effectiveMax = 12f) * master
    val elasticityBoost = node.elasticity.coerceIn(0.08f, 1f)

    val pressShape = whiteOpticsSmoothStep((dynamicPress + lensValue * 0.60f).coerceIn(0f, 3.4f) / 3.4f)
    val lightPower = (touchLight * (0.42f + lensValue * 0.72f + afterValue * 0.18f) * elasticityBoost)
        .coerceIn(0f, 72f)
    val sweepPower = (sweepGain * (0.16f + sweepValue * 0.92f) * elasticityBoost)
        .coerceIn(0f, 72f)
    val afterPower = (afterglow * (afterValue * 0.85f + lensValue * 0.22f) * elasticityBoost)
        .coerceIn(0f, 54f)

    val sweepPhase = (sweepValue / 3.60f).coerceIn(0f, 1.30f)
    val sweepX = -0.46f + sweepPhase * 1.92f
    val rimInset = 0.42.dp.toPx()
    val radiusPx = node.radius.dp.toPx()
    val cornerRadius = CornerRadius(radiusPx, radiusPx)
    val rimRadius = (radiusPx - rimInset).coerceAtLeast(0f)
    val rimCorner = CornerRadius(rimRadius, rimRadius)
    val rimSize = Size(
        width = (w - rimInset * 2f).coerceAtLeast(1f),
        height = (h - rimInset * 2f).coerceAtLeast(1f)
    )
    val transform = item.transform

    withTransform({
        translate(left = rect.left, top = rect.top + transform.translationY)
        scale(
            scaleX = transform.scaleX,
            scaleY = transform.scaleY,
            pivot = Offset(w * transform.originX, h * transform.originY)
        )
    }) {
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.070f * lightPower).coerceIn(0f, 1f)),
                    Color(0xFFEFFFFF).copy(alpha = (0.052f * lightPower).coerceIn(0f, 0.92f)),
                    Color(0xFFE8FFFF).copy(alpha = (0.034f * lightPower).coerceIn(0f, 0.76f)),
                    Color(0xFFB8FFF9).copy(alpha = (0.018f * lightPower).coerceIn(0f, 0.48f)),
                    Color.Transparent
                ),
                center = center,
                radius = maxSide * (0.26f + 0.030f * lightPower.coerceIn(0f, 18f) + 0.30f * pressShape)
            ),
            size = Size(w, h),
            cornerRadius = cornerRadius,
            blendMode = BlendMode.Plus
        )

        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.036f * lightPower).coerceIn(0f, 0.80f)),
                    Color(0xFFEFFFFF).copy(alpha = (0.020f * lightPower).coerceIn(0f, 0.48f)),
                    Color.Transparent
                ),
                center = center,
                radius = maxSide * (0.10f + 0.018f * lightPower.coerceIn(0f, 20f))
            ),
            size = Size(w, h),
            cornerRadius = cornerRadius,
            blendMode = BlendMode.Plus
        )

        if (sweepPower > 0.001f || lightPower > 0.001f) {
            val rimPower = maxOf(sweepPower, lightPower * 0.24f).coerceIn(0f, 72f)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = (0.030f * rimPower).coerceIn(0f, 0.88f)),
                        Color(0xFFEFFFFF).copy(alpha = (0.034f * rimPower).coerceIn(0f, 0.86f)),
                        Color(0xFFE8FFFF).copy(alpha = (0.038f * rimPower).coerceIn(0f, 0.86f)),
                        Color(0xFFB8FFF9).copy(alpha = (0.020f * rimPower).coerceIn(0f, 0.56f)),
                        Color.Transparent
                    ),
                    start = Offset(w * (sweepX - 0.40f), h * -0.10f),
                    end = Offset(w * (sweepX + 0.48f), h * 1.10f)
                ),
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = rimCorner,
                style = Stroke(
                    width = (0.60.dp.toPx() + 0.16.dp.toPx() * rimPower.coerceIn(0f, 24f))
                        .coerceAtMost(9.0.dp.toPx())
                ),
                blendMode = BlendMode.Plus
            )
        }

        if (afterPower > 0.001f) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.026f * afterPower).coerceIn(0f, 0.78f)),
                        Color(0xFFEFFFFF).copy(alpha = (0.022f * afterPower).coerceIn(0f, 0.64f)),
                        Color(0xFFB8FFF9).copy(alpha = (0.016f * afterPower).coerceIn(0f, 0.46f)),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.50f, h * 0.42f),
                    radius = maxSide * (0.46f + afterPower.coerceIn(0f, 10f) * 0.065f)
                ),
                size = Size(w, h),
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Screen
            )
        }
    }
}

private fun whiteOpticsMotionPower(value: Float, uiMax: Float, effectiveMax: Float): Float {
    val clean = value.coerceAtLeast(0f)
    if (clean <= 1f) return clean
    val span = (uiMax - 1f).coerceAtLeast(0.001f)
    val t = ((clean - 1f) / span).coerceIn(0f, 1f)
    return 1f + t * (effectiveMax - 1f)
}

private fun whiteOpticsSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
