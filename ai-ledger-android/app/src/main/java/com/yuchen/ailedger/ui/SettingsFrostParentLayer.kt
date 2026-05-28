package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

@Stable
data class SettingsFrostParentItem(
    val id: String,
    val rectInRoot: Rect,
    val radiusDp: Float,
    val backdropAlpha: Float,
    val frostAlpha: Float,
    val dimAlpha: Float
)

@Stable
class SettingsFrostParentLayerState {
    private val entries = mutableStateMapOf<String, SettingsFrostParentItem>()
    var rootBounds by mutableStateOf(Rect.Zero)
        private set

    val items: List<SettingsFrostParentItem>
        get() = entries.values.sortedWith(compareBy<SettingsFrostParentItem> { it.rectInRoot.top }.thenBy { it.rectInRoot.left })

    fun updateRoot(bounds: Rect) {
        if (rootBounds != bounds) rootBounds = bounds
    }

    fun upsert(
        id: String,
        rectInRoot: Rect,
        radiusDp: Float,
        backdropAlpha: Float,
        frostAlpha: Float,
        dimAlpha: Float
    ) {
        if (rectInRoot.width <= 1f || rectInRoot.height <= 1f) return
        val next = SettingsFrostParentItem(
            id = id,
            rectInRoot = rectInRoot,
            radiusDp = radiusDp,
            backdropAlpha = backdropAlpha.coerceIn(0f, 1f),
            frostAlpha = frostAlpha.coerceIn(0f, 0.85f),
            dimAlpha = dimAlpha.coerceIn(0f, 0.65f)
        )
        if (entries[id] != next) entries[id] = next
    }

    fun remove(id: String) {
        entries.remove(id)
    }
}

val LocalSettingsFrostParentLayer = compositionLocalOf<SettingsFrostParentLayerState?> { null }

@Composable
fun rememberSettingsFrostParentLayerState(): SettingsFrostParentLayerState = remember { SettingsFrostParentLayerState() }

@Composable
fun SettingsFrostParentScope(
    layerState: SettingsFrostParentLayerState,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalSettingsFrostParentLayer provides layerState) {
        content()
    }
}

fun Modifier.registerSettingsFrostParentItem(
    id: String,
    layerState: SettingsFrostParentLayerState?,
    radiusDp: Float,
    backdropAlpha: Float,
    frostAlpha: Float,
    dimAlpha: Float
): Modifier {
    if (layerState == null) return this
    return onGloballyPositioned { coordinates ->
        layerState.upsert(
            id = id,
            rectInRoot = coordinates.boundsInRoot(),
            radiusDp = radiusDp,
            backdropAlpha = backdropAlpha,
            frostAlpha = frostAlpha,
            dimAlpha = dimAlpha
        )
    }
}

@Composable
fun SettingsFrostParentLayer(
    layerState: SettingsFrostParentLayerState,
    modifier: Modifier = Modifier
) {
    val cachedBackdrop = LocalBlurredBackdrop.current
    val frameTicker = LocalBackdropFrameTicker.current
    Canvas(
        modifier = modifier.onGloballyPositioned { coordinates ->
            layerState.updateRoot(coordinates.boundsInRoot())
        }
    ) {
        frameTicker?.frameNanos
        val root = layerState.rootBounds
        layerState.items.forEach { item ->
            val localRect = Rect(
                left = item.rectInRoot.left - root.left,
                top = item.rectInRoot.top - root.top,
                right = item.rectInRoot.right - root.left,
                bottom = item.rectInRoot.bottom - root.top
            )
            if (localRect.right > 0f && localRect.left < size.width && localRect.bottom > 0f && localRect.top < size.height) {
                drawSettingsFrostParentItem(item, localRect, cachedBackdrop)
            }
        }
    }
}

private fun DrawScope.drawSettingsFrostParentItem(
    item: SettingsFrostParentItem,
    rect: Rect,
    backdrop: BlurredBackdropBitmap?
) {
    val radiusPx = item.radiusDp.dp.toPx().coerceAtMost(max(1f, minOf(rect.width, rect.height) * 0.5f))
    val corner = CornerRadius(radiusPx, radiusPx)
    val clipPath = Path().apply {
        addRoundRect(RoundRect(rect, corner))
    }
    clipPath(clipPath) {
        if (backdrop != null) {
            drawParentBackdropImage(item, rect, backdrop)
        } else {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF1A2B58), Color(0xFF5B4A8E), Color(0xFFB85D78))),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                blendMode = BlendMode.SrcOver
            )
        }
        if (item.frostAlpha > 0.001f) {
            drawRect(
                color = Color.White.copy(alpha = item.frostAlpha),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                blendMode = BlendMode.SrcOver
            )
        }
        if (item.dimAlpha > 0.001f) {
            drawRect(
                color = Color.Black.copy(alpha = item.dimAlpha),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                blendMode = BlendMode.SrcOver
            )
        }
    }
}

private fun DrawScope.drawParentBackdropImage(
    item: SettingsFrostParentItem,
    rect: Rect,
    backdrop: BlurredBackdropBitmap
) {
    val visibleLeft = max(0f, -rect.left)
    val visibleTop = max(0f, -rect.top)
    val visibleRight = minOf(rect.width, size.width - rect.left)
    val visibleBottom = minOf(rect.height, size.height - rect.top)
    val visibleW = visibleRight - visibleLeft
    val visibleH = visibleBottom - visibleTop
    if (visibleW <= 0f || visibleH <= 0f) return

    val srcX = ((item.rectInRoot.left + visibleLeft) * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.width - 1)
    val srcY = ((item.rectInRoot.top + visibleTop) * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.height - 1)
    val srcW = (visibleW * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.width - srcX)
    val srcH = (visibleH * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.height - srcY)

    drawImage(
        image = backdrop.image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset((rect.left + visibleLeft).roundToInt(), (rect.top + visibleTop).roundToInt()),
        dstSize = IntSize(visibleW.roundToInt().coerceAtLeast(1), visibleH.roundToInt().coerceAtLeast(1)),
        alpha = item.backdropAlpha,
        blendMode = BlendMode.SrcOver
    )
}

@Composable
fun SettingsFrostParentRegistrationCleanup(layerState: SettingsFrostParentLayerState?, id: String) {
    DisposableEffect(layerState, id) {
        onDispose { layerState?.remove(id) }
    }
}
