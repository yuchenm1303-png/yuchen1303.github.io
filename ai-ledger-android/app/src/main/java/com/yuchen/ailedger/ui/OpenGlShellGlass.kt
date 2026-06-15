package com.yuchen.ailedger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.model.RenderQuality

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
