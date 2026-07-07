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
    val speed = motion.speed.coerceIn(0.08f, 8f)
    val timeScale = ordinarySnapshotSpeedToScale(speed)
    val positive = node.pressProgress.coerceAtLeast(0f)
    val negative = (-node.pressProgress).coerceAtLeast(0f)
    val lens = node.lensProgress.coerceAtLeast(0f)
    val sweep = node.sweepProgress.coerceAtLeast(0f)

    val holdPhase = (positive * 0.52f + lens * 0.30f + sweep * 0.12f) * timeScale
    val tapPhase = (lens * 0.82f + sweep * 0.48f + positive * 0.18f) * timeScale
    val releasePhase = (negative * 0.34f + lens * 0.36f + sweep * 0.26f) * timeScale
    val hold = ordinarySnapshotSmoothStep(holdPhase.coerceIn(0f, 1.84f) / 1.84f)
    val tap = ordinarySnapshotSmoothStep(tapPhase.coerceIn(0f, 1.74f) / 1.74f)
    val release = ordinarySnapshotSmoothStep(releasePhase.coerceIn(0f, 1.96f) / 1.96f)
    val releaseFade = (1f - release * 0.82f).coerceIn(0f, 1f)
    val light = maxOf(
        hold * 0.66f,
        tap * 0.82f,
        lens.coerceIn(0f, 1f) * 0.62f
    ).coerceIn(0f, 1f) * releaseFade
    val cleanSweep = maxOf(
        tap * 0.68f,
        ordinarySnapshotSmoothStep((sweep * timeScale).coerceIn(0f, 1.18f) / 1.18f)
    ).coerceIn(0f, 1f) * releaseFade

    val master = ordinarySnapshotMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f)
    out.pressPhase = hold
    out.tapPhase = tap
    out.releasePhase = release
    out.lightPhase = light
    out.sweepPhase = cleanSweep
    out.settlePhase = release
    out.pressCenter = node.pressCenter
    out.speed = speed
    out.master = master
    out.grow = (ordinarySnapshotMotionPower(value = motion.deformation, uiMax = 1.5f, effectiveMax = 8f) * master).coerceIn(0f, 12f)
    out.rebound = (ordinarySnapshotMotionPower(value = motion.rebound, uiMax = 1.5f, effectiveMax = 8f) * master).coerceIn(0f, 10f)
    out.touchLight = ordinarySnapshotMotionPower(value = motion.touchLight, uiMax = 1.8f, effectiveMax = 16f) * master
    out.sweepGain = ordinarySnapshotMotionPower(value = motion.sweep, uiMax = 1.5f, effectiveMax = 16f) * master
    out.afterglow = ordinarySnapshotMotionPower(value = motion.afterglow, uiMax = 1.5f, effectiveMax = 12f) * master
}

private fun ordinarySnapshotSpeedToScale(speed: Float): Float =
    when {
        speed <= 1f -> (0.92f / speed.coerceAtLeast(0.08f)).coerceIn(0.92f, 3.80f)
        else -> (0.92f / speed).coerceIn(0.48f, 0.92f)
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
