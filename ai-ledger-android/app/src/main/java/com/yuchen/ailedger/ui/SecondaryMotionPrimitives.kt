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
 * 二级页面的轻量轴向运动语义。
 *
 * 页面本身只承担很小的空间位移。灵动感由共享时间轴上的克制 Back-Out 曲线产生，不再
 * 依赖多个并行动画、整页缩放或光效。Capsule 沿中心轴抬升，Push/Pop 只表达水平层级，
 * Replace 与 Modal 保持居中。
 */
internal fun secondaryMotionVisual(
    rawProgress: Float,
    type: SecondaryMotionType,
    direction: SecondaryMotionDirection,
    horizontalTravelPx: Float,
    verticalTravelPx: Float,
): SecondaryMotionVisual {
    val clamped = rawProgress.coerceIn(0f, 1f)
    val sign = if (direction == SecondaryMotionDirection.Forward) 1f else -1f
    val progress = when (type) {
        SecondaryMotionType.Capsule -> secondaryMotionBackOut(clamped, overshoot = 0.34f)
        SecondaryMotionType.Push -> secondaryMotionBackOut(clamped, overshoot = 0.22f)
        SecondaryMotionType.Replace -> secondaryMotionBackOut(clamped, overshoot = 0.10f)
        SecondaryMotionType.Modal -> secondaryMotionBackOut(clamped, overshoot = 0.30f)
    }
    val alpha = when (type) {
        SecondaryMotionType.Capsule,
        SecondaryMotionType.Push -> 1f

        SecondaryMotionType.Replace -> secondaryMotionSmoothStep((clamped * 1.90f).coerceIn(0f, 1f))
        SecondaryMotionType.Modal -> secondaryMotionSmoothStep((clamped * 1.65f).coerceIn(0f, 1f))
    }

    return when (type) {
        SecondaryMotionType.Capsule -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = 0f,
            translationY = verticalTravelPx * (1f - progress),
        )

        SecondaryMotionType.Push -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = sign * horizontalTravelPx * (1f - progress),
            translationY = 0f,
        )

        SecondaryMotionType.Replace -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = 0f,
            translationY = verticalTravelPx * (1f - progress),
        )

        SecondaryMotionType.Modal -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = 0f,
            translationY = verticalTravelPx * (1f - progress),
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

/**
 * 低幅度 Back-Out。overshoot 只控制落位后的极小反向越界，不改变布局尺寸。
 */
internal fun secondaryMotionBackOut(value: Float, overshoot: Float): Float {
    val x = value.coerceIn(0f, 1f) - 1f
    return 1f + (overshoot + 1f) * x * x * x + overshoot * x * x
}

internal fun secondaryMotionSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
