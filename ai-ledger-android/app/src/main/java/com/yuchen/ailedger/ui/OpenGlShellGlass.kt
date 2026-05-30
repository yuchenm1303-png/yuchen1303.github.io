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
 * Only Hero, or an explicit forceOpenGl call, is promoted to Shell. List/Summary/
 * Settings surfaces stay on the Compose glass path by default so scrolling cards
 * do not wake the OpenGL viewport unless a caller opts in deliberately.
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
    val useOpenGlShell = mood == OpenGlShellMood.Hero || forceOpenGl
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
            motionIntensity = motionIntensity,
            radius = radius,
            modifier = surfaceModifier.then(clickableModifier),
            role = GlassRole.Shell,
            content = content
        )
    } else if (onClick != null) {
        PressableGlass(
            quality = quality,
            glassIntensity = glassIntensity,
            motionIntensity = motionIntensity,
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
            motionIntensity = motionIntensity,
            radius = radius,
            modifier = surfaceModifier,
            role = GlassRole.Card,
            content = content
        )
    }
}
