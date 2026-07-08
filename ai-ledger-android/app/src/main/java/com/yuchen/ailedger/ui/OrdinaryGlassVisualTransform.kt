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

    val p = ordinaryVisualSmoothStep(node.pressProgress.coerceAtLeast(0f).coerceIn(0f, 1.72f) / 1.72f)
    val r = ordinaryVisualSmoothStep((-node.pressProgress).coerceAtLeast(0f).coerceIn(0f, 1.40f) / 1.40f)
    val grow = motion.grow.coerceIn(0f, 10f)
    val bounce = motion.rebound.coerceIn(0f, 8f)

    out.scaleX = 1f + p * (0.050f + 0.013f * grow) - r * (0.012f + 0.005f * bounce)
    out.scaleY = 1f + p * (0.038f + 0.010f * grow) - r * (0.010f + 0.004f * bounce)
    out.translationY = p * (0.28f + 0.16f * grow) - r * (0.22f + 0.10f * bounce)
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

private fun ordinaryVisualSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
