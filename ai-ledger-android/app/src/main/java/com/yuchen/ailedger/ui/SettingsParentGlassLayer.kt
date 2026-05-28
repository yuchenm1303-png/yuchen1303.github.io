package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.AssistantUiState
import kotlin.math.min

@Stable
enum class SettingsParentGlassKind { Shell, Detail, Tile, Chip, Floating, Metric }

@Stable
data class SettingsParentGlassVisual(
    val id: String,
    val rectInRoot: Rect,
    val radiusDp: Float,
    val kind: SettingsParentGlassKind,
    val selected: Boolean,
    val emphasis: Float
)

@Stable
class SettingsParentGlassLayerState {
    private val visuals = mutableStateMapOf<String, SettingsParentGlassVisual>()
    var rootBounds by mutableStateOf(Rect.Zero)
        private set

    val items: List<SettingsParentGlassVisual>
        get() = visuals.values.sortedWith(compareBy<SettingsParentGlassVisual> { it.rectInRoot.top }.thenBy { it.rectInRoot.left })

    fun updateRoot(bounds: Rect) {
        if (bounds != rootBounds) rootBounds = bounds
    }

    fun upsert(id: String, rectInRoot: Rect, radiusDp: Float, kind: SettingsParentGlassKind, selected: Boolean, emphasis: Float) {
        if (rectInRoot.width <= 1f || rectInRoot.height <= 1f) return
        val next = SettingsParentGlassVisual(
            id = id,
            rectInRoot = rectInRoot,
            radiusDp = radiusDp,
            kind = kind,
            selected = selected,
            emphasis = emphasis.coerceIn(0f, 2f)
        )
        if (visuals[id] != next) visuals[id] = next
    }

    fun remove(id: String) {
        visuals.remove(id)
    }
}

val LocalSettingsParentGlassLayer = compositionLocalOf<SettingsParentGlassLayerState?> { null }

@Composable
fun rememberSettingsParentGlassLayerState(): SettingsParentGlassLayerState = remember { SettingsParentGlassLayerState() }

fun Modifier.registerSettingsParentGlass(
    id: String,
    layerState: SettingsParentGlassLayerState?,
    radiusDp: Float,
    kind: SettingsParentGlassKind,
    selected: Boolean = false,
    emphasis: Float = 1f
): Modifier {
    if (layerState == null) return this
    return onGloballyPositioned { coordinates ->
        layerState.upsert(id, coordinates.boundsInRoot(), radiusDp, kind, selected, emphasis)
    }
}

@Composable
fun SettingsParentGlassBox(
    id: String,
    radiusDp: Float,
    kind: SettingsParentGlassKind,
    selected: Boolean,
    modifier: Modifier = Modifier,
    emphasis: Float = 1f,
    content: @Composable () -> Unit
) {
    val layerState = LocalSettingsParentGlassLayer.current
    DisposableEffect(layerState, id) { onDispose { layerState?.remove(id) } }
    Box(
        modifier = modifier.registerSettingsParentGlass(
            id = id,
            layerState = layerState,
            radiusDp = radiusDp,
            kind = kind,
            selected = selected,
            emphasis = emphasis
        )
    ) {
        content()
    }
}

@Composable
fun SettingsParentGlassLayer(
    layerState: SettingsParentGlassLayerState,
    state: AssistantUiState,
    modifier: Modifier = Modifier
) {
    val motion = if (state.quality.enableMotion) state.motionIntensity.coerceIn(0f, 1.35f) else 0f
    Canvas(
        modifier = modifier.onGloballyPositioned { coordinates ->
            layerState.updateRoot(coordinates.boundsInRoot())
        }
    ) {
        val root = layerState.rootBounds
        layerState.items.forEach { item ->
            val localRect = Rect(
                left = item.rectInRoot.left - root.left,
                top = item.rectInRoot.top - root.top,
                right = item.rectInRoot.right - root.left,
                bottom = item.rectInRoot.bottom - root.top
            )
            if (localRect.right > 0f && localRect.left < size.width && localRect.bottom > 0f && localRect.top < size.height) {
                drawSettingsParentGlassMaterial(item, localRect, motion)
            }
        }
    }
}

private fun DrawScope.drawSettingsParentGlassMaterial(item: SettingsParentGlassVisual, rect: Rect, motion: Float) {
    val w = rect.width.coerceAtLeast(1f)
    val h = rect.height.coerceAtLeast(1f)
    val radiusPx = min(item.radiusDp.dp.toPx(), min(w, h) * 0.5f)
    val corner = CornerRadius(radiusPx, radiusPx)
    val topLeft = Offset(rect.left, rect.top)
    val rectSize = Size(w, h)
    val selectedBoost = if (item.selected) 1f else 0f
    val kindBoost = when (item.kind) {
        SettingsParentGlassKind.Shell -> 1.16f
        SettingsParentGlassKind.Detail -> 1.06f
        SettingsParentGlassKind.Tile -> 0.98f
        SettingsParentGlassKind.Floating -> 1.08f
        SettingsParentGlassKind.Chip -> 0.88f
        SettingsParentGlassKind.Metric -> 0.82f
    }
    val energy = (item.emphasis * kindBoost + selectedBoost * 0.40f).coerceIn(0f, 2f)
    val selectedAlpha = if (item.selected) 1f else 0f

    drawRoundRect(
        color = Color(0xFF07163A).copy(alpha = (0.118f + 0.032f * energy).coerceIn(0f, 0.22f)),
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = corner,
        blendMode = BlendMode.SrcOver
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.070f + 0.018f * energy),
                Color(0xFF93FFF3).copy(alpha = 0.026f + 0.032f * selectedAlpha),
                Color(0xFF92A5FF).copy(alpha = 0.025f + 0.026f * selectedAlpha),
                Color(0xFFFF8EE8).copy(alpha = 0.014f + 0.026f * selectedAlpha),
                Color(0xFF010A25).copy(alpha = 0.070f + 0.024f * energy)
            ),
            start = Offset(rect.left, rect.top),
            end = Offset(rect.right, rect.bottom)
        ),
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = (0.040f + 0.045f * selectedAlpha + 0.015f * motion) * energy),
                Color(0xFF8DF9EA).copy(alpha = (0.030f + 0.055f * selectedAlpha) * energy),
                Color(0xFFFF80DD).copy(alpha = 0.020f * selectedAlpha * energy),
                Color.Transparent
            ),
            center = Offset(rect.left + w * if (item.kind == SettingsParentGlassKind.Detail) 0.38f else 0.70f, rect.top + h * 0.18f),
            radius = maxOf(w, h) * (0.58f + selectedAlpha * 0.16f)
        ),
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.072f + 0.018f * energy),
                Color.Transparent,
                Color(0xFF000718).copy(alpha = 0.050f + 0.020f * energy)
            ),
            startY = rect.top,
            endY = rect.bottom
        ),
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = corner,
        blendMode = BlendMode.SrcOver
    )

    val rimInset = 0.72.dp.toPx()
    val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))
    val rimCorner = CornerRadius((radiusPx - rimInset).coerceAtLeast(1f), (radiusPx - rimInset).coerceAtLeast(1f))
    val rimTopLeft = Offset(rect.left + rimInset, rect.top + rimInset)
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.150f + 0.070f * selectedAlpha),
                Color(0xFF8DF9EA).copy(alpha = 0.050f + 0.060f * selectedAlpha),
                Color.Transparent,
                Color(0xFF07122A).copy(alpha = 0.045f),
                Color.White.copy(alpha = 0.040f + 0.030f * selectedAlpha)
            ),
            start = Offset(rect.left, rect.top),
            end = Offset(rect.right, rect.bottom)
        ),
        topLeft = rimTopLeft,
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 0.78.dp.toPx() + selectedAlpha * 0.38.dp.toPx()),
        blendMode = BlendMode.Screen
    )

    if (item.selected || item.kind == SettingsParentGlassKind.Detail || item.kind == SettingsParentGlassKind.Shell) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFF75D9).copy(alpha = 0.040f * energy),
                    Color.White.copy(alpha = 0.094f * energy),
                    Color(0xFFFFE08A).copy(alpha = 0.040f * energy),
                    Color(0xFF76FFF1).copy(alpha = 0.066f * energy),
                    Color.Transparent
                ),
                start = Offset(rect.left + w * -0.14f, rect.top - h * 0.06f),
                end = Offset(rect.left + w * 0.74f, rect.bottom + h * 0.10f)
            ),
            topLeft = rimTopLeft,
            size = rimSize,
            cornerRadius = rimCorner,
            style = Stroke(width = 1.25.dp.toPx() + selectedAlpha * 0.70.dp.toPx()),
            blendMode = BlendMode.Plus
        )
    }
}
