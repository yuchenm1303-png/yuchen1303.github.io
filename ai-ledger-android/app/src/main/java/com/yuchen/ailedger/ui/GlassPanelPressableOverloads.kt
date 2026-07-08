package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality

/**
 * Exact-arity overload for ordinary Compose glass panels that are used as compact controls.
 *
 * The composer input pill calls GlassPanel with this six-argument form. The original GlassPanel
 * remains the source of truth for Shell/OpenGL and for advanced callers using viewportTopInset or
 * intensity. Non-Shell panels routed here reuse PressableGlass so they get the same 8830 press,
 * bloom, sweep and release afterglow chain as other ordinary Compose controls.
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
}
