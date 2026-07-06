package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.model.RenderQuality

/**
 * 将“Compose 玻璃光动效”实验参数接入真实普通玻璃按钮链。
 *
 * 首页底部 + 按钮、发送按钮等普通组件走的是 PressableGlass，而不是调试页预览用的
 * composeGlassMotionClickable。这个重载只拦截未显式传入 intensity 的普通 PressableGlass
 * 调用，并把 ComposeGlassLabState.motionStyle 映射为原实现实际消费的 glassIntensity 与
 * motionIntensity。Shell 角色保持原样，避免触碰 OpenGL 大玻璃稳定链。
 */
@Composable
fun PressableGlass(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    role: GlassRole = GlassRole.Chip,
    onClick: () -> Unit = {},
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
    val master = motion.master.coerceIn(0f, 1.5f)
    val deformation = motion.deformation.coerceIn(0f, 1.5f)
    val touchLight = motion.touchLight.coerceIn(0f, 1.8f)
    val prism = motion.prism.coerceIn(0f, 1.5f)
    val sweep = motion.sweep.coerceIn(0f, 1.5f)
    val rebound = motion.rebound.coerceIn(0f, 1.5f)
    val afterglow = motion.afterglow.coerceIn(0f, 1.5f)

    val deformationGain = 0.32f + deformation * 0.68f
    val temporalGain = 0.50f + rebound * 0.22f + afterglow * 0.18f + sweep * 0.10f
    val opticalGain = 0.50f + touchLight * 0.24f + prism * 0.11f + sweep * 0.10f + afterglow * 0.05f

    val resolvedMotionIntensity = (motionIntensity * master * deformationGain * temporalGain)
        .coerceIn(0f, 1.4f)
    val resolvedGlassIntensity = (glassIntensity * (0.48f + master * 0.52f) * opticalGain)
        .coerceIn(0.10f, 2.40f)

    PressableGlass(
        quality = quality,
        glassIntensity = resolvedGlassIntensity,
        motionIntensity = resolvedMotionIntensity,
        radius = radius,
        modifier = modifier,
        role = role,
        onClick = onClick,
        intensity = null,
        content = content
    )
}
