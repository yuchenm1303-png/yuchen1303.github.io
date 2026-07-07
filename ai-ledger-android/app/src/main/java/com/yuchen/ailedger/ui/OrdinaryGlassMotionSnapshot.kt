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
    val positive = node.pressProgress.coerceAtLeast(0f)
    val negative = (-node.pressProgress).coerceAtLeast(0f)
    val lens = node.lensProgress.coerceAtLeast(0f)
    val sweep = node.sweepProgress.coerceAtLeast(0f)

    val press = ordinarySnapshotSmoothStep((positive * 9.60f + lens * 2.20f).coerceIn(0f, 1f))
    val tap = ordinarySnapshotSmoothStep((positive * 7.20f + lens * 1.65f + sweep * 0.26f).coerceIn(0f, 1f))
    val release = ordinarySnapshotSmoothStep((negative * 4.80f).coerceIn(0f, 1f))
    val releaseCut = (1f - release).coerceIn(0f, 1f)
    val settle = release * (1f - press * 0.64f).coerceIn(0.36f, 1f)
    val light = maxOf(
        press * 0.76f,
        tap * 0.72f,
        lens.coerceIn(0f, 1f) * 0.42f,
    ).coerceIn(0f, 1f)
    val cleanSweep = maxOf(
        tap * 0.58f,
        ordinarySnapshotSmoothStep((sweep * 0.62f).coerceIn(0f, 1f)),
    ).coerceIn(0f, 1f)

    val master = ordinarySnapshotMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f)
    out.pressPhase = press * (1f - settle * 0.32f).coerceIn(0.68f, 1f)
    out.tapPhase = tap * (1f - settle * 0.42f).coerceIn(0.58f, 1f)
    out.releasePhase = release
    out.lightPhase = if (negative > 0.001f) 0f else light * releaseCut
    out.sweepPhase = if (negative > 0.001f) 0f else cleanSweep * releaseCut
    out.settlePhase = settle
    out.pressCenter = node.pressCenter
    out.speed = speed
    out.master = master
    out.grow = (ordinarySnapshotMotionPower(value = motion.deformation, uiMax = 1.5f, effectiveMax = 8f) * master).coerceIn(0f, 12f)
    out.rebound = (ordinarySnapshotMotionPower(value = motion.rebound, uiMax = 1.5f, effectiveMax = 8f) * master).coerceIn(0f, 10f)
    out.touchLight = ordinarySnapshotMotionPower(value = motion.touchLight, uiMax = 1.8f, effectiveMax = 16f) * master
    out.sweepGain = ordinarySnapshotMotionPower(value = motion.sweep, uiMax = 1.5f, effectiveMax = 16f) * master
    out.afterglow = ordinarySnapshotMotionPower(value = motion.afterglow, uiMax = 1.5f, effectiveMax = 12f) * master
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
