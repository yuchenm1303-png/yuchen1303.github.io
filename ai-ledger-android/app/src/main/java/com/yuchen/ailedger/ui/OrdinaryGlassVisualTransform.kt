package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.Rect

/**
 * 父级批绘制复用的可变变换容器。
 * 每个可见池槽只创建一次，滚动和按压帧内不再为每张玻璃分配临时 data class。
 */
internal class OrdinaryGlassVisualTransform {
    var scaleX: Float = 1f
    var scaleY: Float = 1f
    var translationY: Float = 0f
    var originX: Float = 0.5f
    var originY: Float = 0.5f

    fun setIdentity() {
        scaleX = 1f
        scaleY = 1f
        translationY = 0f
        originX = 0.5f
        originY = 0.5f
    }

    fun isIdentity(): Boolean =
        scaleX == 1f && scaleY == 1f && translationY == 0f
}

internal fun updateOrdinaryGlassVisualTransform(
    node: OrdinaryGlassRenderNode,
    out: OrdinaryGlassVisualTransform
) {
    if (!node.pressable || node.role == GlassRole.Shell) {
        out.setIdentity()
        return
    }

    val elasticity = node.elasticity.coerceIn(0f, 1f)
    val positivePress = node.pressProgress.coerceAtLeast(0f)
    val rebound = ordinaryVisualSmoothStep(
        (-node.pressProgress / 0.18f).coerceIn(0f, 1f)
    )
    val compression = ordinaryVisualSmoothStep(
        (positivePress / 0.94f).coerceIn(0f, 1f)
    )
    if (compression == 0f && rebound == 0f) {
        out.setIdentity()
        return
    }

    out.scaleX = 1f + compression * (0.006f + 0.049f * elasticity) -
        rebound * 0.018f * elasticity
    out.scaleY = 1f - compression * (0.010f + 0.064f * elasticity) +
        rebound * 0.030f * elasticity
    out.translationY = compression * (0.70f + 3.90f * elasticity) -
        rebound * 1.55f * elasticity
    out.originX = node.pressCenter.x.coerceIn(0f, 1f)
    out.originY = node.pressCenter.y.coerceIn(0f, 1f)
}

internal fun ordinaryGlassTransformedBounds(
    transform: OrdinaryGlassVisualTransform,
    rect: Rect
): Rect {
    if (transform.isIdentity()) return rect

    val pivotX = rect.width * transform.originX
    val pivotY = rect.height * transform.originY
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
