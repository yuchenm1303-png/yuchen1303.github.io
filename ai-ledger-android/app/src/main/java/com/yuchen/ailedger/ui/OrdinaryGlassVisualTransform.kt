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
    val w = measured?.width?.coerceAtLeast(1)?.toFloat() ?: 1f
    val h = measured?.height?.coerceAtLeast(1)?.toFloat() ?: 1f
    val minSide = minOf(w, h).coerceAtLeast(1f)
    val maxSide = maxOf(w, h).coerceAtLeast(1f)
    val aspect = (maxSide / minSide).coerceAtLeast(1f)
    val elongated = ((aspect - 1f) / 3.2f).coerceIn(0f, 1f)
    val compactness = ((156f - minSide) / 108f).coerceIn(0f, 1f)
    val tuning = ComposeGlassLabState.capsuleTuning.normalized()

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
    val viscosity = (1.46f - motion.speed * 0.050f).coerceIn(0.84f, 1.46f)

    val hold = motion.pressPhase
    val delayedTap = motion.tapPhase
    val settle = motion.settlePhase
    val tapPop = ordinaryVisualSmoothStep((delayedTap * tuning.tapPop).coerceIn(0f, 1f))
    val tapCarry = (delayedTap * (1f - hold * 0.16f)).coerceIn(0f, 1.26f)
    val sticky = (motion.lightPhase * 0.021f + motion.sweepPhase * 0.015f + tapCarry * tuning.sticky).coerceIn(0f, 0.12f)
    val body = (hold * 0.72f + tapPop * 1.06f + tapCarry * tuning.tapCarry).coerceIn(0f, 1.76f)
    val settleBody = settle * (1f - tapCarry * 0.60f).coerceIn(0.18f, 1f)
    val reboundSoft = (0.13f + motion.rebound * 0.012f).coerceIn(0.09f, 0.30f)

    val basePx = minSide * sizeBoost * (tuning.basePx + 0.0058f * motion.grow + sticky * 0.38f)
    val tapPx = minSide * sizeBoost * tapPop * (tuning.tapPx + 0.0074f * motion.grow)
    val settlePx = minSide * sizeBoost * settleBody * reboundSoft * (0.013f + 0.0024f * motion.rebound)
    val growPx = basePx * body + tapPx
    val xPx = growPx * (1f - elongated * tuning.elongatedX)
    val yPx = growPx * (1f + elongated * tuning.elongatedY)
    val xBackPx = settlePx * (1f - elongated * 0.44f)
    val yBackPx = settlePx * (1f + elongated * 0.16f)

    out.scaleX = 1f + (xPx - xBackPx) / w
    out.scaleY = 1f + (yPx - yBackPx) / h
    out.translationY = (basePx * body * 0.36f + tapPx * tuning.sink + settlePx * tuning.settle) * viscosity
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
