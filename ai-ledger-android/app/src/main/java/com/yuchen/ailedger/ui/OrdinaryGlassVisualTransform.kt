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
    val compactness = ((150f - minSide) / 104f).coerceIn(0f, 1f)

    val motion = ComposeGlassLabState.motionStyle.normalized()
    val speed = motion.speed.coerceIn(0.08f, 8f)
    val timeScale = ordinaryVisualSpeedToScale(speed)
    val positive = node.pressProgress.coerceAtLeast(0f)
    val negative = (-node.pressProgress).coerceAtLeast(0f)
    val lens = node.lensProgress.coerceAtLeast(0f)
    val sweep = node.sweepProgress.coerceAtLeast(0f)

    val holdPhase = (positive * 0.38f + lens * 0.34f + sweep * 0.18f) * timeScale
    val tapPhase = (lens * 0.74f + sweep * 0.54f + positive * 0.12f) * timeScale
    val returnPhase = (negative * 0.32f + lens * 0.44f + sweep * 0.30f) * timeScale
    val hold = ordinaryVisualSmoothStep(holdPhase.coerceIn(0f, 2.70f) / 2.70f)
    val tap = ordinaryVisualSmoothStep(tapPhase.coerceIn(0f, 2.10f) / 2.10f)
    val settle = ordinaryVisualSmoothStep(returnPhase.coerceIn(0f, 2.55f) / 2.55f)
    if (hold == 0f && tap == 0f && settle == 0f) {
        out.setIdentity()
        return
    }

    val master = ordinaryVisualMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f)
    val grow = (ordinaryVisualMotionPower(value = motion.deformation, uiMax = 1.5f, effectiveMax = 8f) * master).coerceIn(0f, 12f)
    val rebound = (ordinaryVisualMotionPower(value = motion.rebound, uiMax = 1.5f, effectiveMax = 8f) * master).coerceIn(0f, 10f)
    val elasticity = node.elasticity.coerceIn(0.16f, 1f)
    val roleBoost = when (node.role) {
        GlassRole.Chip -> 1.42f
        GlassRole.Flex -> 1.30f
        GlassRole.Floating -> 1.08f
        GlassRole.Card -> 0.92f
        GlassRole.Nav -> 0.86f
        GlassRole.Shell -> 0f
    }
    val compactBoost = (1f + compactness * 0.92f).coerceIn(1f, 1.92f)
    val sizeBoost = (roleBoost * compactBoost * (0.84f + elasticity * 0.34f)).coerceIn(0.62f, 2.65f)
    val viscosity = (1.42f - speed * 0.050f).coerceIn(0.82f, 1.42f)

    val tapPop = ordinaryVisualSmoothStep((tap * 1.22f).coerceIn(0f, 1f))
    val tapCarry = (tap * (1f - hold * 0.18f)).coerceIn(0f, 1.18f)
    val sticky = (lens * 0.020f + sweep * 0.014f + tapCarry * 0.025f).coerceIn(0f, 0.10f)
    val body = (hold * 0.82f + tapPop * 0.74f + tapCarry * 0.22f).coerceIn(0f, 1.42f)
    val settleBody = settle * (1f - tapCarry * 0.56f).coerceIn(0.22f, 1f)
    val reboundSoft = (0.14f + rebound * 0.014f).coerceIn(0.10f, 0.34f)

    val basePx = minSide * sizeBoost * (0.030f + 0.0066f * grow + sticky * 0.42f)
    val tapPx = minSide * sizeBoost * tapPop * (0.020f + 0.0048f * grow)
    val settlePx = minSide * sizeBoost * settleBody * reboundSoft * (0.012f + 0.0026f * rebound)
    val growPx = basePx * body + tapPx
    val xPx = growPx * (1f - elongated * 0.54f)
    val yPx = growPx * (1f + elongated * 0.22f)
    val xBackPx = settlePx * (1f - elongated * 0.46f)
    val yBackPx = settlePx * (1f + elongated * 0.18f)

    out.scaleX = 1f + (xPx - xBackPx) / w
    out.scaleY = 1f + (yPx - yBackPx) / h
    out.translationY = (basePx * body * 0.40f + tapPx * 0.54f + settlePx * 0.32f) * viscosity
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
