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
    val speed = motion.speed.coerceIn(0.08f, 8f)
    val timeScale = ordinaryVisualSpeedToScale(speed)
    val positivePress = node.pressProgress.coerceAtLeast(0f) * timeScale
    val releasePress = (-node.pressProgress).coerceAtLeast(0f) * timeScale
    val compression = ordinaryVisualSmoothStep(positivePress.coerceIn(0f, 2.20f) / 2.20f)
    val release = ordinaryVisualSmoothStep(releasePress.coerceIn(0f, 2.0f) / 2.0f)
    if (compression == 0f && release == 0f) {
        out.setIdentity()
        return
    }

    val master = ordinaryVisualMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f)
    val grow = (ordinaryVisualMotionPower(value = motion.deformation, uiMax = 1.5f, effectiveMax = 8f) * master)
        .coerceIn(0f, 12f)
    val reboundControl = (ordinaryVisualMotionPower(value = motion.rebound, uiMax = 1.5f, effectiveMax = 8f) * master)
        .coerceIn(0f, 10f)
    val viscosity = (1.30f - speed * 0.055f).coerceIn(0.72f, 1.32f)
    val reboundSoftener = (0.26f + reboundControl * 0.026f).coerceIn(0.18f, 0.54f)

    out.scaleX = 1f + compression * (0.058f + 0.013f * grow) -
        release * reboundSoftener * (0.010f + 0.003f * reboundControl)
    out.scaleY = 1f + compression * (0.040f + 0.010f * grow) -
        release * reboundSoftener * (0.007f + 0.002f * reboundControl)
    out.translationY = compression * viscosity * (0.78f + 0.20f * grow) +
        release * viscosity * (0.18f + 0.030f * reboundControl)
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

private fun ordinaryVisualSpeedToScale(speed: Float): Float =
    when {
        speed <= 1f -> (0.16f + speed * 0.84f).coerceIn(0.22f, 1f)
        else -> (1f + (speed - 1f) * 0.62f).coerceIn(1f, 5.35f)
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
