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
 * Hero and List are OpenGL Shell surfaces. List is used by the six independent
 * feature-page tool cards. Nested glyphs remain their own Card/Chip/Floating
 * glass components and stay isolated from OpenGL.
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
    val useOpenGlShell = mood == OpenGlShellMood.Hero || mood == OpenGlShellMood.List || forceOpenGl
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