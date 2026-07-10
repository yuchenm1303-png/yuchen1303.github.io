package com.yuchen.ailedger.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

private const val SECONDARY_HORIZONTAL_UNBOUNDED_CLIP_PX = 1_000_000f

internal enum class SecondaryMotionType {
    Capsule,
    Push,
    Replace,
    Modal,
}

internal enum class SecondaryMotionDirection {
    Forward,
    Backward,
}

internal data class SecondaryMotionVisual(
    val alpha: Float,
    val translationX: Float,
    val translationY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val transformOrigin: TransformOrigin,
)

internal fun secondaryMotionVisual(
    rawProgress: Float,
    type: SecondaryMotionType,
    direction: SecondaryMotionDirection,
    horizontalTravelPx: Float,
    verticalTravelPx: Float,
): SecondaryMotionVisual {
    val clamped = rawProgress.coerceIn(0f, 1f)
    val p = secondaryMotionSmoothStep(clamped)
    val pulse = secondaryMotionArc(p)
    val overshoot = (rawProgress - 1f).coerceIn(0f, 0.12f)
    val sign = if (direction == SecondaryMotionDirection.Forward) 1f else -1f
    val alpha = (clamped * 1.85f).coerceIn(0f, 1f)

    return when (type) {
        SecondaryMotionType.Capsule -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = sign * horizontalTravelPx * (1f - p) -
                sign * pulse * horizontalTravelPx * 0.08f,
            translationY = verticalTravelPx * (1f - p) -
                pulse * verticalTravelPx * 0.16f,
            scaleX = 0.972f + 0.028f * p + 0.010f * pulse - 0.055f * overshoot,
            scaleY = 0.952f + 0.048f * p - 0.007f * pulse + 0.038f * overshoot,
            transformOrigin = TransformOrigin(
                pivotFractionX = if (direction == SecondaryMotionDirection.Forward) 0.20f else 0.80f,
                pivotFractionY = 0.48f,
            ),
        )

        SecondaryMotionType.Push -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = sign * horizontalTravelPx * (1f - p),
            translationY = verticalTravelPx * (1f - p),
            scaleX = 0.988f + 0.012f * p + 0.003f * pulse - 0.030f * overshoot,
            scaleY = 0.982f + 0.018f * p - 0.002f * pulse + 0.020f * overshoot,
            transformOrigin = TransformOrigin(
                pivotFractionX = if (direction == SecondaryMotionDirection.Forward) 0.28f else 0.72f,
                pivotFractionY = 0.50f,
            ),
        )

        SecondaryMotionType.Replace -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = sign * horizontalTravelPx * (1f - p),
            translationY = verticalTravelPx * (1f - p),
            scaleX = 0.995f + 0.005f * p,
            scaleY = 0.990f + 0.010f * p,
            transformOrigin = TransformOrigin.Center,
        )

        SecondaryMotionType.Modal -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = 0f,
            translationY = verticalTravelPx * (1f - p) -
                pulse * verticalTravelPx * 0.10f,
            scaleX = 0.930f + 0.070f * p + 0.008f * pulse - 0.045f * overshoot,
            scaleY = 0.910f + 0.090f * p - 0.005f * pulse + 0.032f * overshoot,
            transformOrigin = TransformOrigin(0.50f, 0.56f),
        )
    }
}

internal fun Modifier.secondaryTransitionOptics(
    progress: () -> Float,
    motionIntensity: Float,
    type: SecondaryMotionType,
    direction: SecondaryMotionDirection,
): Modifier = drawWithContent {
    drawContent()

    val raw = progress()
    val p = raw.coerceIn(0f, 1f)
    if (p >= 0.9995f || motionIntensity <= 0.05f) return@drawWithContent

    val directionSign = if (direction == SecondaryMotionDirection.Forward) 1f else -1f
    val travelPulse = secondaryMotionArc(p)
    val launchPulse = 1f - secondaryMotionSmoothStep((p / 0.26f).coerceIn(0f, 1f))
    val arrivalPulse = secondaryMotionWindow(p, 0.54f, 0.96f)
    val typeGain = when (type) {
        SecondaryMotionType.Capsule -> 1f
        SecondaryMotionType.Push -> 0.72f
        SecondaryMotionType.Replace -> 0.36f
        SecondaryMotionType.Modal -> 0.78f
    }
    val energy = (
        launchPulse * 0.22f +
            travelPulse * 0.58f +
            arrivalPulse * 0.70f
        ) * typeGain * motionIntensity
    if (energy <= 0.001f) return@drawWithContent

    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val topClip = if (type == SecondaryMotionType.Modal) h else min(h, 238.dp.toPx())
    val centerX = if (direction == SecondaryMotionDirection.Forward) {
        w * (0.12f + 0.76f * p)
    } else {
        w * (0.88f - 0.76f * p)
    }
    val centerY = if (type == SecondaryMotionType.Modal) h * 0.43f else topClip * 0.38f
    val center = Offset(centerX, centerY)
    val radius = maxOf(w, topClip) *
        (0.24f + travelPulse * 0.10f + arrivalPulse * 0.06f)

    clipRect(
        left = 0f,
        top = 0f,
        right = w,
        bottom = topClip,
    ) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.115f * energy),
                    Color(0xFFDFFFFB).copy(alpha = 0.072f * energy),
                    Color(0xFFFFE7F8).copy(alpha = 0.030f * energy),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            size = Size(w, topClip),
            blendMode = BlendMode.Screen,
        )

        val sweepWidth = w * (0.18f + 0.08f * travelPulse)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFD8F1).copy(alpha = 0.040f * energy),
                    Color(0xFFFFF3C4).copy(alpha = 0.052f * energy),
                    Color(0xFFBFFFF8).copy(alpha = 0.066f * energy),
                    Color(0xFFB8C7FF).copy(alpha = 0.036f * energy),
                    Color.Transparent,
                ),
                start = Offset(
                    x = centerX - directionSign * sweepWidth,
                    y = -topClip * 0.10f,
                ),
                end = Offset(
                    x = centerX + directionSign * sweepWidth,
                    y = topClip * 1.06f,
                ),
            ),
            size = Size(w, topClip),
            blendMode = BlendMode.Screen,
        )

        if (type != SecondaryMotionType.Modal) {
            val inset = 8.dp.toPx()
            val capsuleHeight = min(
                104.dp.toPx(),
                (topClip - inset * 2f).coerceAtLeast(1f),
            )
            val rimEnergy = arrivalPulse * typeGain * motionIntensity
            if (rimEnergy > 0.001f) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFFD6F2).copy(alpha = 0.12f * rimEnergy),
                            Color(0xFFFFF1BC).copy(alpha = 0.15f * rimEnergy),
                            Color(0xFF9FFFF4).copy(alpha = 0.17f * rimEnergy),
                            Color.Transparent,
                        ),
                        start = Offset(centerX - w * 0.24f, 0f),
                        end = Offset(centerX + w * 0.24f, capsuleHeight),
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size(
                        width = (w - inset * 2f).coerceAtLeast(1f),
                        height = capsuleHeight,
                    ),
                    cornerRadius = CornerRadius(
                        x = min(capsuleHeight * 0.5f, 34.dp.toPx()),
                        y = min(capsuleHeight * 0.5f, 34.dp.toPx()),
                    ),
                    style = Stroke(width = 0.72.dp.toPx()),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}

internal fun Modifier.secondaryItemGlint(
    progress: () -> Float,
    motionIntensity: Float,
    tone: Color,
): Modifier = drawWithContent {
    drawContent()

    val p = progress().coerceIn(0f, 1f)
    if (p >= 0.9995f || motionIntensity <= 0.05f) return@drawWithContent

    val pulse = secondaryMotionWindow(p, 0.12f, 0.88f) * motionIntensity
    if (pulse <= 0.001f) return@drawWithContent

    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.060f * pulse),
                tone.copy(alpha = 0.045f * pulse),
                Color.Transparent,
            ),
            center = Offset(
                x = w * (0.20f + 0.62f * p),
                y = h * 0.42f,
            ),
            radius = maxOf(w, h) * 0.34f,
        ),
        blendMode = BlendMode.Screen,
    )
}

internal fun Modifier.clipSecondaryPageVertically(): Modifier = drawWithContent {
    clipRect(
        left = -SECONDARY_HORIZONTAL_UNBOUNDED_CLIP_PX,
        top = 0f,
        right = SECONDARY_HORIZONTAL_UNBOUNDED_CLIP_PX,
        bottom = size.height,
    ) {
        this@drawWithContent.drawContent()
    }
}

internal fun secondaryMotionArc(progress: Float): Float {
    return sin(PI.toFloat() * progress.coerceIn(0f, 1f)).coerceAtLeast(0f)
}

internal fun secondaryMotionWindow(value: Float, start: Float, end: Float): Float {
    if (value <= start || value >= end) return 0f
    val t = ((value - start) / (end - start).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
    return secondaryMotionArc(t)
}

internal fun secondaryMotionSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
