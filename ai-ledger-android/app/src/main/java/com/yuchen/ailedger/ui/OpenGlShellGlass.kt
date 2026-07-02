package com.yuchen.ailedger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.LocalOpenGLShellBatchState

enum class OpenGlShellMood {
    Hero,
    Summary,
    List,
    Settings
}

/**
 * Shared glass entry for surfaces that are deliberately promoted to Shell/OpenGL.
 *
 * Shell identity follows page visibility instead of the heavy-effects throttle:
 * - active and fading pages keep the same Shell/OpenGL host until alpha reaches zero;
 * - hidden cached pages release OpenGL and fall back to a lightweight Card;
 * - diagnostics.openGlGlassOff still hard-disables Shell OpenGL;
 * - heavy-effects readiness only controls motion intensity, never Shell/Card identity.
 *
 * 当页面提供 [LocalOpenGLShellBatchState] 时，仍复用同一个入口、动态参数和点击行为，
 * 但底层 OpenGL 输出登记到父级共享宿主，由父级在同一帧内逐卡执行同一着色器。
 */
@Composable
fun OpenGlShellGlass(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    mood: OpenGlShellMood = OpenGlShellMood.Hero,
    forceOpenGl: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val pageVisible = LocalPageVisible.current
    val heavyEffectsEnabled = LocalPageHeavyEffectsEnabled.current
    val diagnostics = LocalPerformanceDiagnostics.current
    val wantsOpenGlShell = mood == OpenGlShellMood.Hero || forceOpenGl
    val useOpenGlShell = wantsOpenGlShell && pageVisible && !diagnostics.openGlGlassOff
    val resolvedMotionIntensity = if (heavyEffectsEnabled) motionIntensity else 0f
    val surfaceModifier = modifier
    val batchState = LocalOpenGLShellBatchState.current

    if (useOpenGlShell && batchState != null) {
        OpenGlShellBatchItemSurface(
            quality = quality,
            glassIntensity = glassIntensity,
            motionIntensity = resolvedMotionIntensity,
            radius = radius,
            modifier = surfaceModifier,
            onClick = onClick,
            content = content,
        )
        return
    }

    if (useOpenGlShell) {
        val interaction = remember { MutableInteractionSource() }
        val clickableModifier = if (onClick != null) {
            Modifier.clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
        } else {
            Modifier
        }

        GlassPanel(
            quality = quality,
            glassIntensity = glassIntensity,
            motionIntensity = resolvedMotionIntensity,
            radius = radius,
            modifier = surfaceModifier.then(clickableModifier),
            role = GlassRole.Shell,
            content = content
        )
    } else if (onClick != null) {
        PressableGlass(
            quality = quality,
            glassIntensity = glassIntensity,
            motionIntensity = resolvedMotionIntensity,
            radius = radius,
            modifier = surfaceModifier,
            role = GlassRole.Card,
            onClick = onClick,
            content = content
        )
    } else {
        GlassPanel(
            quality = quality,
            glassIntensity = glassIntensity,
            motionIntensity = resolvedMotionIntensity,
            radius = radius,
            modifier = surfaceModifier,
            role = GlassRole.Card,
            content = content
        )
    }
}
