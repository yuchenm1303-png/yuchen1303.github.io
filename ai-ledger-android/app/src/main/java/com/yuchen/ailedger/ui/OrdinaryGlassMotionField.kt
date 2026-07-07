package com.yuchen.ailedger.ui

/**
 * 普通 Compose 玻璃的统一连续动效场。
 *
 * PressableGlass 仍然对外输出 press / lens / sweep 三个兼容通道，
 * 但可见形变和白光不再各自重新拼接这些通道，而是先汇入同一个连续场。
 * 这样按压、短点击、释放、余辉和扫光共享同一个能量包络，减少段落交接卡顿。
 */
internal data class OrdinaryGlassMotionField(
    val positivePress: Float,
    val releasePress: Float,
    val lens: Float,
    val sweep: Float,
    val compression: Float,
    val tapImpulse: Float,
    val contactEnergy: Float,
    val releaseEnergy: Float,
    val tailEnergy: Float,
    val glowEnergy: Float,
    val sweepEnergy: Float,
    val visibility: Float,
)

internal fun ordinaryGlassMotionField(
    pressProgress: Float,
    lensProgress: Float,
    sweepProgress: Float,
    motion: ComposeGlassMotionStyle,
): OrdinaryGlassMotionField {
    val normalized = motion.normalized()
    val speed = normalized.speed.coerceIn(0.08f, 8f)
    val timeScale = ordinaryMotionFieldSpeedToScale(speed)
    val positive = pressProgress.coerceAtLeast(0f).coerceIn(0f, 2.40f)
    val release = (-pressProgress).coerceAtLeast(0f).coerceIn(0f, 2.00f)
    val lens = lensProgress.coerceAtLeast(0f).coerceIn(0f, 3.80f)
    val sweep = sweepProgress.coerceAtLeast(0f).coerceIn(0f, 4.00f)

    val master = ordinaryMotionFieldPower(normalized.master, uiMax = 1.5f, effectiveMax = 8f)
    val tapGain = ordinaryMotionFieldPower(normalized.tapImpulse, uiMax = 1.6f, effectiveMax = 4.0f) * master
    val cohesion = ordinaryMotionFieldPower(normalized.releaseCohesion, uiMax = 1.6f, effectiveMax = 4.0f) * master
    val continuity = ordinaryMotionFieldPower(normalized.fieldContinuity, uiMax = 1.6f, effectiveMax = 4.0f) * master
    val sweepMomentum = ordinaryMotionFieldPower(normalized.sweepMomentum, uiMax = 1.6f, effectiveMax = 4.0f) * master

    val positiveDamp = 1f - ordinaryMotionFieldSmootherStep((positive / 1.20f).coerceIn(0f, 1f)) * 0.62f
    val pressEnergy = positive * 0.44f + lens * 0.24f + sweep * 0.10f
    val tapEnergy = lens * 0.68f + sweep * (0.34f + sweepMomentum * 0.014f) + positive * 0.18f
    val releaseEnergyRaw = release * (0.46f + cohesion * 0.018f) +
        lens * (0.30f + cohesion * 0.012f) * positiveDamp +
        sweep * (0.34f + sweepMomentum * 0.014f) * positiveDamp
    val tailEnergyRaw = release * 0.24f + lens * (0.22f + continuity * 0.012f) + sweep * (0.44f + sweepMomentum * 0.020f)
    val contactRaw = maxOf(positive * 0.52f, lens * 0.70f, sweep * 0.18f)

    val compression = ordinaryMotionFieldSmootherStep((pressEnergy * timeScale).coerceIn(0f, 2.72f) / 2.72f)
    val tapImpulse = ordinaryMotionFieldSmootherStep((tapEnergy * timeScale).coerceIn(0f, 2.08f) / 2.08f) * tapGain.coerceIn(0f, 4f)
    val contactEnergy = ordinaryMotionFieldSmootherStep((contactRaw * timeScale).coerceIn(0f, 2.52f) / 2.52f)
    val releaseEnergy = ordinaryMotionFieldSmootherStep((releaseEnergyRaw * timeScale).coerceIn(0f, 2.56f) / 2.56f)
    val tailEnergy = ordinaryMotionFieldSmootherStep((tailEnergyRaw * timeScale).coerceIn(0f, 2.72f) / 2.72f)
    val glowEnergy = maxOf(releaseEnergy, tailEnergy * (0.58f + continuity.coerceIn(0f, 4f) * 0.055f))
    val sweepEnergy = ordinaryMotionFieldSmootherStep(((sweep * (0.86f + sweepMomentum * 0.024f) + glowEnergy * 0.44f) * timeScale).coerceIn(0f, 3.60f) / 3.60f)
    val visibility = ordinaryMotionFieldSmootherStep((maxOf(positive, lens, sweep, glowEnergy) / 0.30f).coerceIn(0f, 1f))

    return OrdinaryGlassMotionField(
        positivePress = positive,
        releasePress = release,
        lens = lens,
        sweep = sweep,
        compression = compression,
        tapImpulse = tapImpulse,
        contactEnergy = contactEnergy,
        releaseEnergy = releaseEnergy,
        tailEnergy = tailEnergy,
        glowEnergy = glowEnergy,
        sweepEnergy = sweepEnergy,
        visibility = visibility,
    )
}

private fun ordinaryMotionFieldSpeedToScale(speed: Float): Float =
    when {
        speed <= 1f -> (0.10f + speed * 0.66f).coerceIn(0.16f, 0.76f)
        else -> (0.76f + (speed - 1f) * 0.58f).coerceIn(0.76f, 4.82f)
    }

private fun ordinaryMotionFieldPower(value: Float, uiMax: Float, effectiveMax: Float): Float {
    val clean = value.coerceAtLeast(0f)
    if (clean <= 1f) return clean
    val span = (uiMax - 1f).coerceAtLeast(0.001f)
    val t = ((clean - 1f) / span).coerceIn(0f, 1f)
    return 1f + t * (effectiveMax - 1f)
}

private fun ordinaryMotionFieldSmootherStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}
