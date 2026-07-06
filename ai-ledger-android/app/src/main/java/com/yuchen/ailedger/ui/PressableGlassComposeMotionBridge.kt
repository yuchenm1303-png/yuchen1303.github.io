package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality

/**
 * 普通玻璃真实光动效桥接。
 *
 * 这里不额外绘制任何光圈、边框、扫光或独立形变，避免外层坐标和玻璃本体不同步。
 * 光效、按压形变、释放回弹交回 Glass.kt 原生 PressableGlass 链处理，使用同一组
 * ordinaryPress / ordinaryLens / ordinarySweep 进度，因此边界、节奏和释放时序天然同步。
 * Shell 角色直接转发，避免触碰 OpenGL 大玻璃稳定链。
 */
@Composable
fun PressableGlass(
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    radius: Int,
    modifier: Modifier,
    role: GlassRole,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    if (role == GlassRole.Shell) {
        PressableGlass(
            quality = quality,
            glassIntensity = glassIntensity,
            motionIntensity = motionIntensity,
            radius = radius,
            modifier = modifier,
            role = role,
            onClick = onClick,
            intensity = null,
            content = content
        )
        return
    }

    val motion = ComposeGlassLabState.motionStyle.normalized()
    PressableGlass(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = resolvedNativeOrdinaryMotion(motionIntensity, motion),
        radius = radius,
        modifier = modifier,
        role = role,
        onClick = onClick,
        intensity = null,
        content = content
    )
}

@Composable
fun GlassPanel(
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    radius: Int,
    modifier: Modifier,
    role: GlassRole,
    content: @Composable () -> Unit
) {
    if (role == GlassRole.Shell) {
        GlassPanel(
            quality = quality,
            glassIntensity = glassIntensity,
            motionIntensity = motionIntensity,
            radius = radius,
            modifier = modifier,
            role = role,
            viewportTopInset = 0.dp,
            intensity = null,
            content = content
        )
        return
    }

    val motion = ComposeGlassLabState.motionStyle.normalized()
    GlassPanel(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = resolvedNativeOrdinaryMotion(motionIntensity, motion),
        radius = radius,
        modifier = modifier,
        role = role,
        viewportTopInset = 0.dp,
        intensity = null,
        content = content
    )
}

private fun resolvedNativeOrdinaryMotion(baseMotion: Float, motion: ComposeGlassMotionStyle): Float {
    val master = ordinaryMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 1.35f)
    val touch = ordinaryMotionPower(value = motion.touchLight, uiMax = 1.8f, effectiveMax = 1.20f)
    val sweep = ordinaryMotionPower(value = motion.sweep, uiMax = 1.5f, effectiveMax = 1.18f)
    val afterglow = ordinaryMotionPower(value = motion.afterglow, uiMax = 1.5f, effectiveMax = 1.12f)
    val speed = motion.speed.coerceIn(0.35f, 2.5f)
    val speedEnergy = (0.90f + (speed - 1f) * 0.10f).coerceIn(0.76f, 1.10f)
    val gain = (0.76f + touch * 0.12f + sweep * 0.08f + afterglow * 0.04f).coerceIn(0.20f, 1.24f)
    return (baseMotion * master * gain * speedEnergy).coerceIn(0f, 1f)
}

private fun ordinaryMotionPower(value: Float, uiMax: Float, effectiveMax: Float): Float {
    val clean = value.coerceAtLeast(0f)
    if (clean <= 1f) return clean
    val span = (uiMax - 1f).coerceAtLeast(0.001f)
    val t = ((clean - 1f) / span).coerceIn(0f, 1f)
    return 1f + t * (effectiveMax - 1f)
}
