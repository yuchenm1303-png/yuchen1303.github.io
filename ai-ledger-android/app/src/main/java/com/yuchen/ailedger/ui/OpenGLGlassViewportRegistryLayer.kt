package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.ui.gl.OpenGLGlassViewportItem
import com.yuchen.ailedger.ui.gl.OpenGLGlassViewportLayer
import kotlin.math.roundToInt

@Composable
fun OpenGLGlassViewportRegistryLayer(
    registry: GlassItemRegistry,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val origin = LocalBackdropOrigin.current
    val items = registry.snapshot().mapNotNull { item ->
        if (item.role != GlassRole.Shell) return@mapNotNull null
        if (!item.coordinates.isAttached()) return@mapNotNull null
        val size = item.coordinates.itemSize()
        if (size.width <= 0 || size.height <= 0) return@mapNotNull null
        val offset = item.coordinates.offsetRelativeTo(origin)
        OpenGLGlassViewportItem(
            key = item.key.toString(),
            left = offset.x.roundToInt().toFloat(),
            top = offset.y.roundToInt().toFloat(),
            width = size.width.coerceAtLeast(1).toFloat(),
            height = size.height.coerceAtLeast(1).toFloat(),
            radiusPx = with(density) { item.radius.dp.toPx() },
            intensity = item.glassIntensity
        )
    }
    OpenGLGlassViewportLayer(items = items, modifier = modifier)
}
