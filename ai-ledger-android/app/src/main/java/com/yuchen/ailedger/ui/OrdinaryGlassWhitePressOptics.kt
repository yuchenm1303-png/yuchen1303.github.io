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
import kotlin.math.min

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
    val releaseValue = (-node.pressProgress).coerceAtLeast(0f)
    val afterValue = maxOf(node.sweepProgress * 0.46f, releaseValue * 0.58f).coerceAtLeast(0f)
    val active = maxOf(dynamicPress, lensValue, sweepValue, afterValue)
    if (active <= 0.001f) return

    val rect = item.rect
    val w = rect.width.coerceAtLeast(1f)
    val h = rect.height.coerceAtLeast(1f)
    val maxSide = max(w, h)
    val minSide = min(w, h).coerceAtLeast(1f)
    val aspect = (w / h).coerceIn(0.35f, 4.80f)
    val center = Offset(
        x = node.pressCenter.x.coerceIn(0f, 1f) * w,
        y = node.pressCenter.y.coerceIn(0f, 1f) * h
    )

    val motion = ComposeGlassLabState.motionStyle.normalized()
    val speed = motion.speed.coerceIn(0.35f, 2.50f)
    val master = whiteOpticsMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f)
    val touchLight = whiteOpticsMotionPower(value = motion.touchLight, uiMax = 1.8f, effectiveMax = 16f) * master
    val sweepGain = whiteOpticsMotionPower(value = motion.sweep, uiMax = 1.5f, effectiveMax = 16f) * master
    val afterglow = whiteOpticsMotionPower(value = motion.afterglow, uiMax = 1.5f, effectiveMax = 12f) * master
    val elasticityBoost = node.elasticity.coerceIn(0.08f, 1f)

    val pressShape = whiteOpticsSmoothStep((dynamicPress + lensValue * 0.52f).coerceIn(0f, 3.4f) / 3.4f)
    val lightPower = (touchLight * (0.28f + lensValue * 0.54f + afterValue * 0.10f) * elasticityBoost)
        .coerceIn(0f, 56f)
    val sweepPower = (sweepGain * (0.10f + sweepValue * 0.72f) * elasticityBoost)
        .coerceIn(0f, 48f)
    val afterPower = (afterglow * (afterValue * 0.72f + lensValue * 0.16f) * elasticityBoost)
        .coerceIn(0f, 42f)

    val sweepPhase = ((sweepValue / 3.60f) * (0.70f + speed * 0.38f)).coerceIn(0f, 1.38f)
    val ripplePhase = whiteOpticsSmoothStep(sweepPhase.coerceIn(0f, 1f))
    val secondRipplePhase = whiteOpticsSmoothStep(((sweepPhase - 0.20f) / 0.80f).coerceIn(0f, 1f))
    val sweepX = -0.46f + sweepPhase * 1.92f
    val rimInset = 0.58.dp.toPx()
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
        // 大面积低透明内光：不再画清晰白球，而是让整块玻璃内部被一片软雾点亮。
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.014f * lightPower).coerceIn(0f, 0.30f)),
                    Color(0xFFEFFFFF).copy(alpha = (0.022f * lightPower).coerceIn(0f, 0.34f)),
                    Color(0xFFE8FFFF).copy(alpha = (0.016f * lightPower).coerceIn(0f, 0.24f)),
                    Color.Transparent
                ),
                center = Offset(
                    x = center.x + (0.10f - node.pressCenter.x * 0.12f) * w,
                    y = center.y - 0.06f * h
                ),
                radius = maxSide * (0.62f + 0.20f * pressShape)
            ),
            size = Size(w, h),
            cornerRadius = cornerRadius,
            blendMode = BlendMode.Plus
        )

        // 椭圆化液态光场 A：跟随触点但被横向拉开，避免出现圆形灯泡感。
        withTransform({
            scale(
                scaleX = (1.18f + aspect.coerceAtMost(2.20f) * 0.24f),
                scaleY = 0.72f,
                pivot = center
            )
        }) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.010f * lightPower).coerceIn(0f, 0.20f)),
                        Color(0xFFEFFFFF).copy(alpha = (0.020f * lightPower).coerceIn(0f, 0.28f)),
                        Color(0xFFDFFFFD).copy(alpha = (0.014f * lightPower).coerceIn(0f, 0.20f)),
                        Color.Transparent
                    ),
                    center = center,
                    radius = minSide * (0.78f + 0.26f * pressShape)
                ),
                size = Size(w, h),
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Plus
            )
        }

        // 液态光场 B：错位柔光，让高亮区域没有单一几何中心。
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.008f * lightPower).coerceIn(0f, 0.14f)),
                    Color(0xFFEEFDFB).copy(alpha = (0.015f * lightPower).coerceIn(0f, 0.18f)),
                    Color.Transparent
                ),
                center = Offset(
                    x = (center.x * 0.64f + w * 0.36f).coerceIn(0f, w),
                    y = (center.y * 0.70f + h * 0.24f).coerceIn(0f, h)
                ),
                radius = maxSide * (0.48f + 0.18f * pressShape)
            ),
            size = Size(w, h),
            cornerRadius = cornerRadius,
            blendMode = BlendMode.Screen
        )

        // 触点只保留极轻的雾芯，不允许形成清晰白色圆球。
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.010f * lightPower).coerceIn(0f, 0.18f)),
                    Color(0xFFEFFFFF).copy(alpha = (0.012f * lightPower).coerceIn(0f, 0.16f)),
                    Color.Transparent
                ),
                center = center,
                radius = minSide * (0.34f + 0.12f * pressShape)
            ),
            size = Size(w, h),
            cornerRadius = cornerRadius,
            blendMode = BlendMode.Plus
        )

        if (sweepPower > 0.001f || lightPower > 0.001f) {
            val ripplePower = maxOf(sweepPower, lightPower * 0.34f).coerceIn(0f, 44f)
            drawLiquidRipple(
                center = center,
                size = Size(w, h),
                cornerRadius = cornerRadius,
                radius = maxSide * (0.18f + ripplePhase * 0.74f),
                power = ripplePower * (1f - ripplePhase * 0.54f),
                thicknessScale = 1.0f,
            )
            if (secondRipplePhase > 0.001f) {
                drawLiquidRipple(
                    center = center,
                    size = Size(w, h),
                    cornerRadius = cornerRadius,
                    radius = maxSide * (0.24f + secondRipplePhase * 0.66f),
                    power = ripplePower * 0.48f * (1f - secondRipplePhase * 0.66f),
                    thicknessScale = 0.72f,
                )
            }
        }

        // 贴边细光：主线固定很细，扫光只改变亮度和位置，不再把整圈边框撑宽。
        if (sweepPower > 0.001f || lightPower > 0.001f) {
            val rimPower = maxOf(sweepPower, lightPower * 0.20f).coerceIn(0f, 42f)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = (0.015f * rimPower).coerceIn(0f, 0.42f)),
                        Color(0xFFEFFFFF).copy(alpha = (0.022f * rimPower).coerceIn(0f, 0.46f)),
                        Color(0xFFE6FFFF).copy(alpha = (0.020f * rimPower).coerceIn(0f, 0.38f)),
                        Color.Transparent
                    ),
                    start = Offset(w * (sweepX - 0.36f), h * -0.08f),
                    end = Offset(w * (sweepX + 0.44f), h * 1.08f)
                ),
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = rimCorner,
                style = Stroke(
                    width = (0.54.dp.toPx() + 0.020.dp.toPx() * rimPower.coerceIn(0f, 24f))
                        .coerceAtMost(1.30.dp.toPx())
                ),
                blendMode = BlendMode.Plus
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.010f * rimPower).coerceIn(0f, 0.22f)),
                        Color.Transparent,
                        Color(0xFFEFFFFF).copy(alpha = (0.006f * rimPower).coerceIn(0f, 0.12f))
                    ),
                    startY = 0f,
                    endY = h
                ),
                topLeft = Offset(rimInset * 1.35f, rimInset * 1.35f),
                size = Size(
                    width = (w - rimInset * 2.70f).coerceAtLeast(1f),
                    height = (h - rimInset * 2.70f).coerceAtLeast(1f)
                ),
                cornerRadius = CornerRadius(
                    (rimRadius - rimInset * 0.35f).coerceAtLeast(0f),
                    (rimRadius - rimInset * 0.35f).coerceAtLeast(0f)
                ),
                style = Stroke(width = 0.72.dp.toPx()),
                blendMode = BlendMode.Screen
            )
        }

        if (afterPower > 0.001f) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.010f * afterPower).coerceIn(0f, 0.20f)),
                        Color(0xFFEFFFFF).copy(alpha = (0.016f * afterPower).coerceIn(0f, 0.26f)),
                        Color(0xFFDFFFFD).copy(alpha = (0.010f * afterPower).coerceIn(0f, 0.16f)),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.50f, h * 0.42f),
                    radius = maxSide * (0.54f + afterPower.coerceIn(0f, 10f) * 0.050f)
                ),
                size = Size(w, h),
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Screen
            )
        }
    }
}

private fun DrawScope.drawLiquidRipple(
    center: Offset,
    size: Size,
    cornerRadius: CornerRadius,
    radius: Float,
    power: Float,
    thicknessScale: Float,
) {
    if (power <= 0.001f || radius <= 1f) return
    val ringAlpha = (0.014f * power).coerceIn(0f, 0.34f)
    val innerAlpha = (0.010f * power).coerceIn(0f, 0.20f)
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.White.copy(alpha = innerAlpha),
                Color(0xFFEFFFFF).copy(alpha = ringAlpha),
                Color(0xFFDFFFFD).copy(alpha = ringAlpha * 0.54f),
                Color.Transparent,
                Color.Transparent
            ),
            center = center,
            radius = radius * (1f + 0.050f * thicknessScale)
        ),
        size = size,
        cornerRadius = cornerRadius,
        blendMode = BlendMode.Plus
    )
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
