package com.yuchen.ailedger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.yuchen.ailedger.model.RenderQuality

enum class OpenGlShellMood {
    Hero,
    Summary,
    List,
    Settings
}

/**
 * Shared large-glass entry for deliberately promoted OpenGL Shell surfaces.
 *
 * It keeps the existing role policy intact: only Shell may enter OpenGL. The actual
 * three-phase press choreography still lives in GlassPanel: Compose shell compression,
 * delayed OpenGL lens pull, and surface highlight afterglow.
 */
@Composable
fun OpenGlShellGlass(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    mood: OpenGlShellMood = OpenGlShellMood.Hero,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
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
        modifier = modifier
            .openGlShellMoodAura(mood = mood, motionIntensity = motionIntensity)
            .then(clickableModifier),
        role = GlassRole.Shell,
        content = content
    )
}

private fun Modifier.openGlShellMoodAura(
    mood: OpenGlShellMood,
    motionIntensity: Float
): Modifier = drawWithCache {
    val motion = motionIntensity.coerceIn(0f, 1.4f)
    val longSide = maxOf(size.width, size.height).coerceAtLeast(1f)
    val shortSide = minOf(size.width, size.height).coerceAtLeast(1f)
    val accent = when (mood) {
        OpenGlShellMood.Hero -> Color(0xFF8DF9EA)
        OpenGlShellMood.Summary -> Color(0xFFFFD166)
        OpenGlShellMood.List -> Color(0xFF9EB7FF)
        OpenGlShellMood.Settings -> Color(0xFFC7A8FF)
    }
    val center = when (mood) {
        OpenGlShellMood.Hero -> Offset(size.width * 0.20f, size.height * 0.10f)
        OpenGlShellMood.Summary -> Offset(size.width * 0.78f, size.height * 0.18f)
        OpenGlShellMood.List -> Offset(size.width * 0.12f, size.height * 0.50f)
        OpenGlShellMood.Settings -> Offset(size.width * 0.50f, size.height * 0.02f)
    }
    val radius = when (mood) {
        OpenGlShellMood.Hero -> longSide * 0.78f
        OpenGlShellMood.Summary -> longSide * 0.58f
        OpenGlShellMood.List -> shortSide * 1.85f
        OpenGlShellMood.Settings -> longSide * 0.52f
    }
    val strength = when (mood) {
        OpenGlShellMood.Hero -> 0.22f
        OpenGlShellMood.Summary -> 0.18f
        OpenGlShellMood.List -> 0.13f
        OpenGlShellMood.Settings -> 0.15f
    } * (0.45f + motion * 0.55f)
    val aura = Brush.radialGradient(
        colors = listOf(
            accent.copy(alpha = strength),
            Color.White.copy(alpha = strength * 0.28f),
            Color.Transparent
        ),
        center = center,
        radius = radius
    )

    onDrawWithContent {
        drawRect(aura, blendMode = BlendMode.Screen)
        drawContent()
    }
}
