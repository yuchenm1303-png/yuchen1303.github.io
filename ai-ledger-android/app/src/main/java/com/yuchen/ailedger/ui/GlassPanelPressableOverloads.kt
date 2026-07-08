package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality

/**
 * Exact-arity overload for ordinary Compose glass panels that are explicitly used as compact
 * input controls.
 *
 * This intentionally does not make every ordinary GlassPanel pressable: popover panels, memory
 * cards and large information panels should keep a quiet static glass surface. Only capsule-like
 * control glass with a very large radius, such as the bottom composer input pill, is routed through
 * PressableGlass to receive the 8830 press/bloom/sweep chain.
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
    val shouldUsePressableInputGlass = role != GlassRole.Shell && radius >= 900
    if (!shouldUsePressableInputGlass) {
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
