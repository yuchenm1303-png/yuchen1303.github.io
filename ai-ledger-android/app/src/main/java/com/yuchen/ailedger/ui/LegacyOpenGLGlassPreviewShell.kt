package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.OpenGLGlassCardLayer

@Composable
fun LegacyOpenGLGlassPreviewShell(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val coordinates = remember { GlassCoordinateSource() }
    Box(modifier = modifier.onPlaced { coordinates.coordinates = it }) {
        OpenGLGlassCardLayer(
            radius = radius,
            glassIntensity = glassIntensity,
            coordinateSource = coordinates,
            modifier = Modifier.matchParentSize()
        )
        content()
    }
}
