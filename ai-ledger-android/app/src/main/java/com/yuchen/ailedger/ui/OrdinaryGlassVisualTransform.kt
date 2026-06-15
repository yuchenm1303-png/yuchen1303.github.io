package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal data class OrdinaryGlassVisualTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationY: Float,
    val origin: Offset
)

internal fun ordinaryGlassVisualTransform(
    node: OrdinaryGlassRenderNode
): OrdinaryGlassVisualTransform {
    if (!node.pressable || node.role == GlassRole.Shell) {
        return OrdinaryGlassVisualTransform(
            scaleX = 1f,
            scaleY = 1f,
            translationY = 0f,
            origin = Offset(0.5f, 0.5f)
        )
    }

    val elasticity = node.elasticity.coerceIn(0f, 1f)
    val positivePress = node.pressProgress.coerceAtLeast(0f)
    val rebound = ordinaryVisualSmoothStep(
        (-node.pressProgress / 0.18f).coerceIn(0f, 1f)
    )
    val compression = ordinaryVisualSmoothStep(
        (positivePress / 0.94f).coerceIn(0f, 1f)
    )

    return OrdinaryGlassVisualTransform(
        scaleX = 1f + compression * (0.006f + 0.049f * elasticity) -
            rebound * 0.018f * elasticity,
        scaleY = 1f - compression * (0.010f + 0.064f * elasticity) +
            rebound * 0.030f * elasticity,
        translationY = compression * (0.70f + 3.90f * elasticity) -
            rebound * 1.55f * elasticity,
        origin = Offset(
            node.pressCenter.x.coerceIn(0f, 1f),
            node.pressCenter.y.coerceIn(0f, 1f)
        )
    )
}

internal fun ordinaryGlassTransformedBounds(
    node: OrdinaryGlassRenderNode,
    rect: Rect
): Rect {
    val transform = ordinaryGlassVisualTransform(node)
    val pivotX = rect.width * transform.origin.x
    val pivotY = rect.height * transform.origin.y
    val left = rect.left + pivotX * (1f - transform.scaleX)
    val top = rect.top + transform.translationY + pivotY * (1f - transform.scaleY)
    return Rect(
        left = left,
        top = top,
        right = left + rect.width * transform.scaleX,
        bottom = top + rect.height * transform.scaleY
    )
}

private fun ordinaryVisualSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
