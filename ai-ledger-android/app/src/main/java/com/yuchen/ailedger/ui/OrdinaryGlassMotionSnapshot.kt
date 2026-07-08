package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.Offset

internal class OrdinaryGlassMotionSnapshot {
    var pressPhase: Float = 0f
    var tapPhase: Float = 0f
    var releasePhase: Float = 0f
    var lightPhase: Float = 0f
    var sweepPhase: Float = 0f
    var settlePhase: Float = 0f
    var pressCenter: Offset = Offset(0.5f, 0.5f)
    var speed: Float = 1f
    var master: Float = 1f
    var grow: Float = 1f
    var rebound: Float = 1f
    var touchLight: Float = 1f
    var prism: Float = 1f
    var sweepGain: Float = 1f
    var afterglow: Float = 1f

    fun reset() {
        pressPhase = 0f
        tapPhase = 0f
        releasePhase = 0f
        lightPhase = 0f
        sweepPhase = 0f
        settlePhase = 0f
        pressCenter = Offset(0.5f, 0.5f)
        speed = 1f
        master = 1f
        grow = 1f
        rebound = 1f
        touchLight = 1f
        prism = 1f
        sweepGain = 1f
        afterglow = 1f
    }

    fun isIdle(): Boolean = pressPhase == 0f && tapPhase == 0f && releasePhase == 0f &&
        lightPhase == 0f && sweepPhase == 0f && settlePhase == 0f
}

internal fun updateOrdinaryGlassMotionSnapshot(
    node: OrdinaryGlassRenderNode,
    out: OrdinaryGlassMotionSnapshot
) {
    if (!node.pressable || node.role == GlassRole.Shell) {
        out.reset()
        return
    }

    val motion = ComposeGlassLabState.motionStyle.normalized()
    val positive = node.pressProgress.coerceAtLeast(0f)
    val negative = (-node.pressProgress).coerceAtLeast(0f)
    val lens = node.lensProgress.coerceAtLeast(0f)
    val sweep = node.sweepProgress.coerceAtLeast(0f)

    val master = ordinarySnapshotMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 7f)
    out.pressPhase = ordinarySnapshotSmoothStep(positive.coerceIn(0f, 1.72f) / 1.72f)
    out.tapPhase = ordinarySnapshotSmoothStep((positive + lens * 0.62f).coerceIn(0f, 2.65f) / 2.65f)
    out.releasePhase = ordinarySnapshotSmoothStep(negative.coerceIn(0f, 1.40f) / 1.40f)
    out.lightPhase = maxOf(positive * 0.36f, lens).coerceIn(0f, 2.15f)
    out.sweepPhase = sweep.coerceIn(0f, 2.20f)
    out.settlePhase = out.releasePhase
    out.pressCenter = node.pressCenter
    out.speed = motion.speed.coerceIn(0.08f, 8f)
    out.master = master
    out.grow = (ordinarySnapshotMotionPower(value = motion.deformation, uiMax = 1.5f, effectiveMax = 7f) * master).coerceIn(0f, 10f)
    out.rebound = (ordinarySnapshotMotionPower(value = motion.rebound, uiMax = 1.5f, effectiveMax = 7f) * master).coerceIn(0f, 8f)
    out.touchLight = (ordinarySnapshotMotionPower(value = motion.touchLight, uiMax = 1.8f, effectiveMax = 13f) * master).coerceIn(0f, 42f)
    out.prism = (ordinarySnapshotMotionPower(value = motion.prism, uiMax = 1.5f, effectiveMax = 10f) * master).coerceIn(0f, 28f)
    out.sweepGain = (ordinarySnapshotMotionPower(value = motion.sweep, uiMax = 1.5f, effectiveMax = 10f) * master).coerceIn(0f, 30f)
    out.afterglow = (ordinarySnapshotMotionPower(value = motion.afterglow, uiMax = 1.5f, effectiveMax = 9f) * master).coerceIn(0f, 18f)
}

private fun ordinarySnapshotMotionPower(value: Float, uiMax: Float, effectiveMax: Float): Float {
    val clean = value.coerceAtLeast(0f)
    if (clean <= 1f) return clean
    val span = (uiMax - 1f).coerceAtLeast(0.001f)
    val t = ((clean - 1f) / span).coerceIn(0f, 1f)
    return 1f + t * (effectiveMax - 1f)
}

private fun ordinarySnapshotSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
