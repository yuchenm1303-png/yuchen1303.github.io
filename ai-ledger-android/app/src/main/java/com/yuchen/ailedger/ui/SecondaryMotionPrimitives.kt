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
 * 二级页面的轴向运动语义。
 *
 * Capsule 只沿页面中心轴轻抬升，避免与内部卡片叠加后形成右下角斜飞感；Push/Pop 只
 * 保留水平方向，用于表达真实层级；Replace 与 Modal 保持居中的纵向运动。直接使用弹簧
 * 原始进度，允许极轻的反向越界，因此不需要额外扫光或整页缩放也能保留灵动感。
 */
internal fun secondaryMotionVisual(
    rawProgress: Float,
    type: SecondaryMotionType,
    direction: SecondaryMotionDirection,
    horizontalTravelPx: Float,
    verticalTravelPx: Float,
): SecondaryMotionVisual {
    val clamped = rawProgress.coerceIn(0f, 1f)
    val springProgress = rawProgress.coerceIn(0f, 1.10f)
    val sign = if (direction == SecondaryMotionDirection.Forward) 1f else -1f
    val alpha = secondaryMotionSmoothStep((clamped * 1.42f).coerceIn(0f, 1f))

    return when (type) {
        SecondaryMotionType.Capsule -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = 0f,
            translationY = verticalTravelPx * (1f - springProgress),
        )

        SecondaryMotionType.Push -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = sign * horizontalTravelPx * (1f - springProgress),
            translationY = 0f,
        )

        SecondaryMotionType.Replace -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = 0f,
            translationY = verticalTravelPx * (1f - springProgress),
        )

        SecondaryMotionType.Modal -> SecondaryMotionVisual(
            alpha = alpha,
            translationX = 0f,
            translationY = verticalTravelPx * (1f - springProgress),
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

internal fun secondaryMotionSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
