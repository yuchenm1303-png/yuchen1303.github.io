package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.model.RenderQuality

@Composable
fun PressableGlass(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    role: GlassRole = GlassRole.Chip,
    onPressedClick: () -> Unit,
    content: @Composable () -> Unit
) {
    PressableGlass(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = motionIntensity,
        radius = radius,
        modifier = modifier,
        role = role,
        intensity = null,
        onClick = onPressedClick,
        content = content
    )
}
