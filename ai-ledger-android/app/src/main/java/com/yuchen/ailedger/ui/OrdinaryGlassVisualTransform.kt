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
    val positivePress = node.pressProgress.coerceAtLeast(0f)
    val releasePress = (-node.pressProgress).coerceAtLeast(0f)
    val lens = node.lensProgress.coerceAtLeast(0f)
    val sweep = node.sweepProgress.coerceAtLeast(0f)
    val pressPhase = (positivePress * 0.34f + lens * 0.40f + sweep * 0.26f) * timeScale
    val clickCarryPhase = (lens * 0.66f + sweep * 0.50f + positivePress * 0.18f) * timeScale
    val releasePhase = (releasePress * 0.30f + lens * 0.42f + sweep * 0.34f) * timeScale
    val pressCore = ordinaryVisualSmoothStep(pressPhase.coerceIn(0f, 2.55f) / 2.55f)
    val clickCarry = ordinaryVisualSmoothStep(clickCarryPhase.coerceIn(0f, 2.05f) / 2.05f)
    val release = ordinaryVisualSmoothStep(releasePhase.coerceIn(0f, 2.50f) / 2.50f)
    val compression = (1f - (1f - pressCore) * (1f - clickCarry * 1.10f)).coerceIn(0f, 1.28f)
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
        GlassRole.Chip -> 2.46f
        GlassRole.Flex -> 2.18f
        GlassRole.Floating -> 1.34f
        GlassRole.Card -> 0.78f
        GlassRole.Nav -> 0.66f
        GlassRole.Shell -> 0f
    }
    val compactBalance = (0.46f + elasticity * 2.04f).coerceIn(0.72f, 2.50f)
    val sizeBalance = (roleBalance * compactBalance).coerceIn(0.42f, 3.20f)
    val translationBalance = sizeBalance.coerceIn(0.82f, 2.18f)
    val viscosity = (1.44f - speed * 0.050f).coerceIn(0.82f, 1.44f)
    val tapBridge = (clickCarry * (1f - pressCore * 0.24f)).coerceIn(0f, 1.22f)
    val tapPop = ordinaryVisualSmoothStep((tapBridge * 1.18f).coerceIn(0f, 1f))
    val stickyHold = (lens * 0.026f + sweep * 0.016f + tapBridge * 0.030f)
        .coerceIn(0f, 0.120f)
    val reboundSoftener = (0.15f + reboundControl * 0.014f).coerceIn(0.10f, 0.32f)
    val releaseSettling = release * (1f - tapBridge * 0.76f).coerceIn(0.12f, 1f)
    val visualCompression = (compression + tapBridge * 0.34f + tapPop * 0.22f).coerceIn(0f, 1.55f)

    val horizontalDamp = when (node.role) {
        GlassRole.Card, GlassRole.Nav -> 0.62f
        GlassRole.Floating -> 0.78f
        else -> 1.00f
    }
    val verticalGain = when (node.role) {
        GlassRole.Card, GlassRole.Nav -> 1.72f
        GlassRole.Floating -> 1.36f
        else -> 1.18f
    }
    val pressScaleX = visualCompression * sizeBalance * horizontalDamp * (0.014f + 0.0038f * grow + stickyHold * 0.34f)
    val pressScaleY = visualCompression * sizeBalance * verticalGain * (0.044f + 0.0105f * grow + stickyHold * 0.90f)
    val releaseScaleX = releaseSettling * sizeBalance * horizontalDamp * reboundSoftener * (0.0032f + 0.0011f * reboundControl)
    val releaseScaleY = releaseSettling * sizeBalance * verticalGain * reboundSoftener * (0.0058f + 0.0018f * reboundControl)
    out.scaleX = 1f + pressScaleX - releaseScaleX
    out.scaleY = 1f + pressScaleY - releaseScaleY
    out.translationY = visualCompression * viscosity * translationBalance * (1.12f + 0.28f * grow) +
        release * viscosity * translationBalance * (0.30f + 0.022f * reboundControl) +
        tapBridge * viscosity * translationBalance * (0.34f + 0.040f * grow)
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
