package com.yuchen.ailedger.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect

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
)

/**
 * 二级页面只保留位移与透明度动画。
 *
 * 不再对整棵玻璃页面做缩放，也不再绘制任何入场光场、扫光或 glint，避免多个玻璃
 * 同时进入离屏合成和重采样。胶囊感只通过不同轴向的位移节奏与轻微反向惯性表达。
 */
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
    val sign = if (direction == SecondaryMotionDirection.Forward) 1f else -1f
    val alpha = (clamped * 1.72f).coerceIn(0f, 1f)

    return when (type) {
        SecondaryMotionType.Capsule -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = sign * horizontalTravelPx * (1f - p) -
                sign * pulse * horizontalTravelPx * 0.045f,
            translationY = verticalTravelPx * (1f - p) -
                pulse * verticalTravelPx * 0.08f,
        )

        SecondaryMotionType.Push -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = sign * horizontalTravelPx * (1f - p),
            translationY = verticalTravelPx * (1f - p),
        )

        SecondaryMotionType.Replace -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = sign * horizontalTravelPx * (1f - p),
            translationY = verticalTravelPx * (1f - p),
        )

        SecondaryMotionType.Modal -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = 0f,
            translationY = verticalTravelPx * (1f - p) -
                pulse * verticalTravelPx * 0.06f,
        )
    }
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
    val x = progress.coerceIn(0f, 1f)
    return 4f * x * (1f - x)
}

internal fun secondaryMotionSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
