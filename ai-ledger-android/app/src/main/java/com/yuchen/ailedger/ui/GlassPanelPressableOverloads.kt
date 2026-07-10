package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality

/**
 * Exact-arity overload for ordinary Compose glass panels.
 *
 * Keep the bottom composer input pill animated, restore ordinary Card panels to the shared
 * matte-frost material, and keep non-Card popovers on their quiet material. The original
 * GlassPanel remains the source of truth for Shell/OpenGL and advanced callers using
 * viewportTopInset or intensity.
 */
@Composable
fun GlassPanel(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    role: GlassRole = GlassRole.Card,
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

    val isComposerInputPill = radius >= 900
    if (isComposerInputPill) {
        PressableGlass(
            quality = quality,
            glassIntensity = glassIntensity,
            motionIntensity = motionIntensity,
            radius = radius,
            modifier = modifier,
            role = role,
            onClick = {},
            intensity = null,
            content = content
        )
        return
    }

    if (role == GlassRole.Card) {
        FrostInfoGlassPanel(
            modifier = modifier,
            radius = radius.coerceAtLeast(18).toFloat(),
            backdropAlpha = 1f,
            frostAlpha = (0.082f * glassIntensity).coerceIn(0.048f, 0.120f),
            dimAlpha = 0f,
            content = content
        )
        return
    }

    QuietPopoverGlassPanel(
        glassIntensity = glassIntensity,
        radius = radius,
        modifier = modifier,
        content = content
    )
}

@Composable
private fun QuietPopoverGlassPanel(
    glassIntensity: Float,
    radius: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(radius.coerceAtLeast(18).dp)
    val alpha = glassIntensity.coerceIn(0.70f, 1.20f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF102543).copy(alpha = 0.62f * alpha),
                        Color(0xFF0D203C).copy(alpha = 0.50f * alpha),
                        Color(0xFF081832).copy(alpha = 0.56f * alpha)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f * alpha), shape)
    ) {
        Box(Modifier.fillMaxSize(), content = { content() })
    }
}
