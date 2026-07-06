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

    val motion = ComposeGlassLabState.motionStyle.normalized()
    val speed = motion.speed.coerceIn(0.35f, 2.50f)
    val timeScale = (0.54f + speed * 0.46f).coerceIn(0.70f, 1.70f)
    val positivePress = node.pressProgress.coerceAtLeast(0f) * timeScale
    val rebound = ordinaryVisualSmoothStep(
        ((-node.pressProgress).coerceAtLeast(0f) * timeScale).coerceIn(0f, 2.0f) / 2.0f
    )
    val compression = ordinaryVisualSmoothStep(
        positivePress.coerceIn(0f, 2.20f) / 2.20f
    )
    if (compression == 0f && rebound == 0f) {
        out.setIdentity()
        return
    }

    val master = ordinaryVisualMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f)
    val grow = (ordinaryVisualMotionPower(value = motion.deformation, uiMax = 1.5f, effectiveMax = 8f) * master)
        .coerceIn(0f, 12f)
    val bounce = (ordinaryVisualMotionPower(value = motion.rebound, uiMax = 1.5f, effectiveMax = 8f) * master)
        .coerceIn(0f, 10f)

    out.scaleX = 1f + compression * (0.080f + 0.018f * grow) -
        rebound * (0.018f + 0.007f * bounce)
    out.scaleY = 1f + compression * (0.058f + 0.014f * grow) -
        rebound * (0.014f + 0.006f * bounce)
    out.translationY = compression * (0.45f + 0.22f * grow) -
        rebound * (0.36f + 0.16f * bounce)
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

private fun ordinaryVisualMotionPower(value: Float, uiMax: Float, effectiveMax: Float): Float {
    val clean = value.coerceAtLeast(0f)
    if (clean <= 1f) return clean
    val span = (uiMax - 1f).coerceAtLeast(0.001f)
    val t = ((clean - 1f) / span).coerceIn(0f, 1f)
    return 1f + t * (effectiveMax - 1f)
}

private fun ordinaryVisualSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
