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
    node: OrdinaryGlassRenderNode,
    out: OrdinaryGlassVisualTransform
) {
    if (!node.pressable || node.role == GlassRole.Shell) {
        out.setIdentity()
        return
    }

    val measured = node.coordinates.coordinates?.size
    val w = measured?.width?.coerceAtLeast(1)?.toFloat() ?: 1f
    val h = measured?.height?.coerceAtLeast(1)?.toFloat() ?: 1f
    val minSide = minOf(w, h).coerceAtLeast(1f)
    val maxSide = maxOf(w, h).coerceAtLeast(1f)
    val aspect = (maxSide / minSide).coerceAtLeast(1f)
    val elongated = ((aspect - 1f) / 3.2f).coerceIn(0f, 1f)
    val compactness = ((156f - minSide) / 108f).coerceIn(0f, 1f)

    val motion = ComposeGlassLabState.motionStyle.normalized()
    val tuning = ComposeGlassLabState.capsuleTuning.normalized()
    val speed = motion.speed.coerceIn(0.08f, 8f)
    val timeScale = ordinaryVisualSpeedToScale(speed)
    val positive = node.pressProgress.coerceAtLeast(0f)
    val negative = (-node.pressProgress).coerceAtLeast(0f)
    val lens = node.lensProgress.coerceAtLeast(0f)
    val sweep = node.sweepProgress.coerceAtLeast(0f)

    val positiveDamp = 1f - ordinaryVisualSmootherStep((positive / 1.18f).coerceIn(0f, 1f)) * 0.58f
    val pressEnergy = positive * 0.38f + lens * 0.26f + sweep * 0.12f
    val tapEnergy = lens * 0.76f + sweep * 0.46f + positive * 0.20f
    val releaseEnergy = negative * 0.42f + lens * 0.34f * positiveDamp + sweep * 0.38f * positiveDamp
    val tailEnergy = negative * 0.24f + lens * 0.24f + sweep * 0.46f

    val hold = ordinaryVisualSmootherStep((pressEnergy * timeScale).coerceIn(0f, 2.72f) / 2.72f)
    val tap = ordinaryVisualSmootherStep((tapEnergy * timeScale).coerceIn(0f, 2.08f) / 2.08f)
    val releaseEnvelope = ordinaryVisualSmootherStep((releaseEnergy * timeScale).coerceIn(0f, 2.48f) / 2.48f)
    val opticalTail = ordinaryVisualSmootherStep((tailEnergy * timeScale).coerceIn(0f, 2.56f) / 2.56f)
    val settle = maxOf(releaseEnvelope, opticalTail * 0.58f)
    if (maxOf(hold, tap, settle) <= 0.0001f) {
        out.setIdentity()
        return
    }

    val master = ordinaryVisualMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f)
    val grow = (ordinaryVisualMotionPower(value = motion.deformation, uiMax = 1.5f, effectiveMax = 8f) * master).coerceIn(0f, 12f)
    val rebound = (ordinaryVisualMotionPower(value = motion.rebound, uiMax = 1.5f, effectiveMax = 8f) * master).coerceIn(0f, 10f)
    val elasticity = node.elasticity.coerceIn(0.16f, 1f)
    val roleBoost = when (node.role) {
        GlassRole.Chip -> 1.56f
        GlassRole.Flex -> 1.42f
        GlassRole.Floating -> 1.10f
        GlassRole.Card -> 0.90f
        GlassRole.Nav -> 0.84f
        GlassRole.Shell -> 0f
    }
    val compactBoost = (1f + compactness * tuning.compactBoost).coerceIn(1f, 2.40f)
    val sizeBoost = (roleBoost * compactBoost * (0.82f + elasticity * 0.36f)).coerceIn(0.60f, 3.05f)
    val viscosity = (1.46f - speed * 0.050f).coerceIn(0.84f, 1.46f)

    val tapPop = ordinaryVisualSmootherStep((tap * tuning.tapPop).coerceIn(0f, 1f))
    val tapCarry = (tap * (1f - hold * 0.16f)).coerceIn(0f, 1.26f)
    val sticky = (lens * 0.019f + sweep * 0.014f + opticalTail * 0.018f + tapCarry * tuning.sticky).coerceIn(0f, 0.12f)
    val body = (hold * 0.72f + tapPop * 1.04f + tapCarry * tuning.tapCarry).coerceIn(0f, 1.76f)
    val settleBody = (releaseEnvelope * (1f - tapCarry * 0.54f).coerceIn(0.22f, 1f) + opticalTail * 0.16f).coerceIn(0f, 1.10f)
    val reboundSoft = (0.13f + rebound * 0.012f).coerceIn(0.09f, 0.30f)

    val basePx = minSide * sizeBoost * (tuning.basePx + 0.0058f * grow + sticky * 0.38f)
    val tapPx = minSide * sizeBoost * tapPop * (tuning.tapPx + 0.0074f * grow)
    val settlePx = minSide * sizeBoost * settleBody * reboundSoft * (0.013f + 0.0024f * rebound)
    val growPx = basePx * body + tapPx
    val xPx = growPx * (1f - elongated * tuning.elongatedX)
    val yPx = growPx * (1f + elongated * tuning.elongatedY)
    val xBackPx = settlePx * (1f - elongated * 0.44f)
    val yBackPx = settlePx * (1f + elongated * 0.16f)

    out.scaleX = 1f + (xPx - xBackPx) / w
    out.scaleY = 1f + (yPx - yBackPx) / h
    out.translationY = (basePx * body * 0.36f + tapPx * tuning.sink + settlePx * tuning.settle) * viscosity
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

private fun ordinaryVisualSmootherStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}
