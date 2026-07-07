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
    val pressPhase = (node.pressProgress.coerceAtLeast(0f) * 0.48f +
        node.lensProgress.coerceAtLeast(0f) * 0.34f +
        node.sweepProgress.coerceAtLeast(0f) * 0.18f) * timeScale
    val releasePhase = ((-node.pressProgress).coerceAtLeast(0f) * 0.58f +
        node.lensProgress.coerceAtLeast(0f) * 0.24f +
        node.sweepProgress.coerceAtLeast(0f) * 0.18f) * timeScale
    val compression = ordinaryVisualSmoothStep(pressPhase.coerceIn(0f, 2.60f) / 2.60f)
    val release = ordinaryVisualSmoothStep(releasePhase.coerceIn(0f, 2.40f) / 2.40f)
    if (compression == 0f && release == 0f) {
        out.setIdentity()
        return
    }

    val master = ordinaryVisualMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f)
    val grow = (ordinaryVisualMotionPower(value = motion.deformation, uiMax = 1.5f, effectiveMax = 8f) * master)
        .coerceIn(0f, 12f)
    val reboundControl = (ordinaryVisualMotionPower(value = motion.rebound, uiMax = 1.5f, effectiveMax = 8f) * master)
        .coerceIn(0f, 10f)
    val elasticity = node.elasticity.coerceIn(0.16f, 1f)
    val roleBalance = when (node.role) {
        GlassRole.Chip -> 1.62f
        GlassRole.Flex -> 1.42f
        GlassRole.Floating -> 1.16f
        GlassRole.Card -> 0.82f
        GlassRole.Nav -> 0.74f
        GlassRole.Shell -> 0f
    }
    val compactBalance = (0.72f + elasticity * 0.72f).coerceIn(0.78f, 1.44f)
    val sizeBalance = (roleBalance * compactBalance).coerceIn(0.58f, 2.18f)
    val translationBalance = sizeBalance.coerceIn(0.72f, 1.64f)
    val viscosity = (1.38f - speed * 0.050f).coerceIn(0.76f, 1.38f)
    val stickyHold = (node.lensProgress.coerceAtLeast(0f) * 0.018f + node.sweepProgress.coerceAtLeast(0f) * 0.010f)
        .coerceIn(0f, 0.060f)
    val reboundSoftener = (0.18f + reboundControl * 0.018f).coerceIn(0.12f, 0.40f)

    val pressScaleX = compression * sizeBalance * (0.034f + 0.0075f * grow + stickyHold)
    val pressScaleY = compression * sizeBalance * (0.024f + 0.0058f * grow + stickyHold * 0.54f)
    val releaseScaleX = release * sizeBalance * reboundSoftener * (0.0048f + 0.0016f * reboundControl)
    val releaseScaleY = release * sizeBalance * reboundSoftener * (0.0032f + 0.0011f * reboundControl)
    out.scaleX = 1f + pressScaleX - releaseScaleX
    out.scaleY = 1f + pressScaleY - releaseScaleY
    out.translationY = compression * viscosity * translationBalance * (0.96f + 0.22f * grow) +
        release * viscosity * translationBalance * (0.26f + 0.024f * reboundControl)
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
        speed <= 1f -> (0.10f + speed * 0.66f).coerceIn(0.16f, 0.76f)
        else -> (0.76f + (speed - 1f) * 0.58f).coerceIn(0.76f, 4.82f)
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
