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
 * 内部光效严格做成一个连续光场：触点只改变场的重心、亮度和扩散半径，
 * 不再叠加独立圆球、椭圆块或局部雾芯，避免看见“几块光拼起来”。
 */
internal fun DrawScope.drawOrdinaryParentWhitePressOptics(item: VisibleOrdinaryGlassItem) {
    val node = item.node
    if (!node.pressable || node.role == GlassRole.Shell) return

    val dynamicPress = node.pressProgress.coerceAtLeast(0f)
    val releaseValue = (-node.pressProgress).coerceAtLeast(0f)
    if (releaseValue > 0.001f && dynamicPress <= 0.001f) return

    val lensValue = node.lensProgress.coerceAtLeast(0f)
    val sweepValue = node.sweepProgress.coerceAtLeast(0f)
    val afterValue = sweepValue * 0.30f
    val active = maxOf(dynamicPress, lensValue, sweepValue, afterValue)
    if (active <= 0.001f) return

    val rect = item.rect
    val w = rect.width.coerceAtLeast(1f)
    val h = rect.height.coerceAtLeast(1f)
    val maxSide = max(w, h)
    val center = Offset(
        x = node.pressCenter.x.coerceIn(0f, 1f) * w,
        y = node.pressCenter.y.coerceIn(0f, 1f) * h,
    )

    val motion = ComposeGlassLabState.motionStyle.normalized()
    val speed = motion.speed.coerceIn(0.08f, 8f)
    val timeScale = whiteOpticsSpeedToScale(speed)
    val master = whiteOpticsMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f)
    val touchLight = whiteOpticsMotionPower(value = motion.touchLight, uiMax = 1.8f, effectiveMax = 16f) * master
    val sweepGain = whiteOpticsMotionPower(value = motion.sweep, uiMax = 1.5f, effectiveMax = 16f) * master
    val afterglow = whiteOpticsMotionPower(value = motion.afterglow, uiMax = 1.5f, effectiveMax = 12f) * master
    val elasticityBoost = node.elasticity.coerceIn(0.08f, 1f)
    val roleLightBalance = when (node.role) {
        GlassRole.Chip -> 1.48f
        GlassRole.Flex -> 1.34f
        GlassRole.Floating -> 1.14f
        GlassRole.Card -> 0.92f
        GlassRole.Nav -> 0.88f
        GlassRole.Shell -> 0f
    }
    val compactLightBalance = (roleLightBalance * (0.88f + elasticityBoost * 0.36f)).coerceIn(0.76f, 1.86f)

    val pressShape = whiteOpticsSmoothStep(
        ((dynamicPress + lensValue * 0.48f) * timeScale).coerceIn(0f, 3.4f) / 3.4f
    )
    val diffusionPhase = whiteOpticsSmoothStep(
        ((sweepValue * timeScale) / 3.60f).coerceIn(0f, 1f)
    )
    val releasePhase = 0f

    val lightPower = (touchLight * compactLightBalance * (0.20f + lensValue * 0.44f + dynamicPress * 0.10f + afterValue * 0.08f) * elasticityBoost)
        .coerceIn(0f, 58f)
    val wavePower = (sweepGain * compactLightBalance * (0.10f + sweepValue * 0.44f) * elasticityBoost)
        .coerceIn(0f, 42f)
    val afterPower = (afterglow * compactLightBalance * afterValue * 0.42f * elasticityBoost)
        .coerceIn(0f, 24f)

    val fieldPower = maxOf(lightPower, wavePower * 0.72f, afterPower * 0.70f)
    if (fieldPower <= 0.001f) return

    val fieldCenter = Offset(
        x = (w * 0.50f + (center.x - w * 0.50f) * 0.18f).coerceIn(-w * 0.20f, w * 1.20f),
        y = h * (1.06f - pressShape * 0.12f + releasePhase * 0.04f) + (center.y - h * 0.50f) * 0.08f,
    )
    val fieldRadius = maxSide * (1.34f + diffusionPhase * 0.62f + pressShape * 0.20f + releasePhase * 0.14f)
    val broadAlpha = (0.0068f * fieldPower).coerceIn(0f, 0.30f)
    val innerAlpha = (0.0092f * fieldPower).coerceIn(0f, 0.36f)
    val milkAlpha = (0.0048f * fieldPower).coerceIn(0f, 0.20f)

    val sweepPhase = ((sweepValue * timeScale) / 3.60f).coerceIn(0f, 1.26f)
    val sweepX = -0.30f + sweepPhase * 1.54f
    val rimInset = 0.62.dp.toPx()
    val radiusPx = node.radius.dp.toPx()
    val cornerRadius = CornerRadius(radiusPx, radiusPx)
    val rimRadius = (radiusPx - rimInset).coerceAtLeast(0f)
    val rimCorner = CornerRadius(rimRadius, rimRadius)
    val rimSize = Size(
        width = (w - rimInset * 2f).coerceAtLeast(1f),
        height = (h - rimInset * 2f).coerceAtLeast(1f),
    )
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
                    Color.White.copy(alpha = innerAlpha),
                    Color(0xFFF6FFFF).copy(alpha = broadAlpha),
                    Color(0xFFE8FFFF).copy(alpha = broadAlpha * 0.62f),
                    Color.Transparent,
                ),
                center = fieldCenter,
                radius = fieldRadius,
            ),
            size = Size(w, h),
            cornerRadius = cornerRadius,
            blendMode = BlendMode.Plus,
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = milkAlpha * 0.34f),
                    Color(0xFFF7FFFF).copy(alpha = milkAlpha * (0.78f + pressShape * 0.20f)),
                    Color(0xFFE6FFFF).copy(alpha = milkAlpha * (1.18f + diffusionPhase * 0.30f)),
                    Color.Transparent,
                ),
                startY = -h * 0.18f,
                endY = h * 1.18f,
            ),
            size = Size(w, h),
            cornerRadius = cornerRadius,
            blendMode = BlendMode.Screen,
        )

        if (wavePower > 0.001f) {
            val waveAlpha = (0.0038f * wavePower * (1f - diffusionPhase * 0.36f)).coerceIn(0f, 0.13f)
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = waveAlpha * 0.42f),
                        Color(0xFFF2FFFF).copy(alpha = waveAlpha),
                        Color(0xFFE8FFFF).copy(alpha = waveAlpha * 0.54f),
                        Color.Transparent,
                    ),
                    center = fieldCenter,
                    radius = fieldRadius * (1.12f + diffusionPhase * 0.42f),
                ),
                size = Size(w, h),
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Plus,
            )
        }

        val rimPower = maxOf(wavePower, lightPower * 0.16f, afterPower * 0.22f).coerceIn(0f, 36f)
        if (rimPower > 0.001f) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = (0.009f * rimPower).coerceIn(0f, 0.24f)),
                        Color(0xFFF4FFFF).copy(alpha = (0.014f * rimPower).coerceIn(0f, 0.30f)),
                        Color(0xFFE7FFFF).copy(alpha = (0.010f * rimPower).coerceIn(0f, 0.22f)),
                        Color.Transparent,
                    ),
                    start = Offset(w * (sweepX - 0.32f), h * -0.10f),
                    end = Offset(w * (sweepX + 0.38f), h * 1.10f),
                ),
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = rimCorner,
                style = Stroke(width = 0.58.dp.toPx()),
                blendMode = BlendMode.Plus,
            )
            drawRoundRect(
                color = Color.White.copy(alpha = (0.0038f * rimPower).coerceIn(0f, 0.10f)),
                topLeft = Offset(rimInset * 1.55f, rimInset * 1.55f),
                size = Size(
                    width = (w - rimInset * 3.10f).coerceAtLeast(1f),
                    height = (h - rimInset * 3.10f).coerceAtLeast(1f),
                ),
                cornerRadius = CornerRadius(
                    (rimRadius - rimInset * 0.55f).coerceAtLeast(0f),
                    (rimRadius - rimInset * 0.55f).coerceAtLeast(0f),
                ),
                style = Stroke(width = 0.46.dp.toPx()),
                blendMode = BlendMode.Screen,
            )
        }
    }
}

private fun whiteOpticsSpeedToScale(speed: Float): Float =
    when {
        speed <= 1f -> (0.10f + speed * 0.66f).coerceIn(0.16f, 0.76f)
        else -> (0.76f + (speed - 1f) * 0.58f).coerceIn(0.76f, 4.82f)
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
