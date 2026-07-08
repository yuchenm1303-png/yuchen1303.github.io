package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.Rect

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
    item: VisibleOrdinaryGlassItem,
    out: OrdinaryGlassVisualTransform
) {
    val node = item.node
    val motion = item.motion
    if (!node.pressable || node.role == GlassRole.Shell || motion.isIdle()) {
        out.setIdentity()
        return
    }

    val measured = node.coordinates.coordinates?.size
    val w = measured?.width?.coerceAtLeast(1)?.toFloat() ?: item.rect.width.coerceAtLeast(1f)
    val h = measured?.height?.coerceAtLeast(1)?.toFloat() ?: item.rect.height.coerceAtLeast(1f)
    val resolved = ordinaryGlassResolvedTransform(
        widthPx = w,
        heightPx = h,
        role = node.role,
        pressProgress = node.pressProgress.coerceAtLeast(0f),
        reboundProgress = (-node.pressProgress).coerceAtLeast(0f),
        grow = motion.grow,
        bounce = motion.rebound,
    )

    out.scaleX = resolved.scaleX
    out.scaleY = resolved.scaleY
    out.translationY = resolved.translationY
    out.originX = motion.pressCenter.x.coerceIn(0f, 1f)
    out.originY = motion.pressCenter.y.coerceIn(0f, 1f)
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
