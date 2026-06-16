package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class SettingsFrostDrawCache {
    var geometrySignature = Long.MIN_VALUE
    var mask = Path()
    var localSize = Size.Zero
    var fallbackBrush: Brush? = null
}

@Stable
class SettingsFrostParentItem internal constructor(
    val id: String,
    rectInRoot: Rect,
    radiusDp: Float,
    backdropAlpha: Float,
    frostAlpha: Float,
    dimAlpha: Float
) {
    var rectInRoot: Rect = rectInRoot
        internal set
    var radiusDp: Float = radiusDp
        internal set
    var backdropAlpha: Float = backdropAlpha
        internal set
    var frostAlpha: Float = frostAlpha
        internal set
    var dimAlpha: Float = dimAlpha
        internal set

    internal val drawCache = SettingsFrostDrawCache()
}

@Stable
class SettingsFrostParentLayerState {
    private val entries = LinkedHashMap<String, SettingsFrostParentItem>()
    private var cachedSnapshot: List<SettingsFrostParentItem> = emptyList()
    private var rootCoordinates: LayoutCoordinates? = null

    internal var drawVersion by mutableLongStateOf(0L)
        private set

    internal val items: List<SettingsFrostParentItem>
        get() = cachedSnapshot

    internal fun updateRoot(coordinates: LayoutCoordinates) {
        rootCoordinates = coordinates
    }

    internal fun rootBounds(): Rect = rootCoordinates
        ?.takeIf { it.isAttached }
        ?.boundsInRoot()
        ?: Rect.Zero

    fun upsert(
        id: String,
        rectInRoot: Rect,
        radiusDp: Float,
        backdropAlpha: Float,
        frostAlpha: Float,
        dimAlpha: Float
    ) {
        if (rectInRoot.width <= 1f || rectInRoot.height <= 1f) return
        val safeBackdrop = backdropAlpha.coerceIn(0f, 1f)
        val safeFrost = frostAlpha.coerceIn(0f, 0.85f)
        val safeDim = dimAlpha.coerceIn(0f, 0.65f)
        val current = entries[id]
        if (current == null) {
            entries[id] = SettingsFrostParentItem(
                id = id,
                rectInRoot = rectInRoot,
                radiusDp = radiusDp,
                backdropAlpha = safeBackdrop,
                frostAlpha = safeFrost,
                dimAlpha = safeDim
            )
            rebuildSnapshot()
            return
        }

        var changed = false
        if (current.rectInRoot != rectInRoot) {
            current.rectInRoot = rectInRoot
            changed = true
        }
        if (current.radiusDp != radiusDp) {
            current.radiusDp = radiusDp
            current.drawCache.geometrySignature = Long.MIN_VALUE
            changed = true
        }
        if (current.backdropAlpha != safeBackdrop) {
            current.backdropAlpha = safeBackdrop
            changed = true
        }
        if (current.frostAlpha != safeFrost) {
            current.frostAlpha = safeFrost
            changed = true
        }
        if (current.dimAlpha != safeDim) {
            current.dimAlpha = safeDim
            changed = true
        }
        if (changed) bumpDrawVersion()
    }

    fun remove(id: String) {
        if (entries.remove(id) != null) rebuildSnapshot()
    }

    private fun rebuildSnapshot() {
        cachedSnapshot = entries.values.toList()
        bumpDrawVersion()
    }

    private fun bumpDrawVersion() {
        drawVersion = if (drawVersion == Long.MAX_VALUE) 1L else drawVersion + 1L
    }
}

val LocalSettingsFrostParentLayer =
    staticCompositionLocalOf<SettingsFrostParentLayerState?> { null }

@Composable
fun rememberSettingsFrostParentLayerState(): SettingsFrostParentLayerState =
    remember { SettingsFrostParentLayerState() }

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
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current

    Canvas(
        modifier = modifier.onGloballyPositioned(layerState::updateRoot)
    ) {
        layerState.drawVersion
        val items = layerState.items
        if (items.isEmpty()) return@Canvas

        val backdrop = cachedBackdrop
        if (backdrop != null) frameTicker?.frameNanos
        val root = layerState.rootBounds()
        val backdropRoot = backdropOrigin?.rootOffset() ?: Offset.Zero

        items.forEach { item ->
            val localRect = Rect(
                left = item.rectInRoot.left - root.left,
                top = item.rectInRoot.top - root.top,
                right = item.rectInRoot.right - root.left,
                bottom = item.rectInRoot.bottom - root.top
            )
            if (
                localRect.right <= 0f ||
                localRect.left >= size.width ||
                localRect.bottom <= 0f ||
                localRect.top >= size.height
            ) return@forEach

            val cache = ensureSettingsFrostCache(item, localRect.size)
            val sampleOffset = item.rectInRoot.topLeft - backdropRoot
            withTransform({ translate(localRect.left, localRect.top) }) {
                clipPath(cache.mask) {
                    if (backdrop != null) {
                        drawParentBackdropImage(
                            backdrop = backdrop,
                            sampleOffset = sampleOffset,
                            localSize = cache.localSize,
                            alpha = item.backdropAlpha
                        )
                    } else {
                        drawRect(
                            brush = requireNotNull(cache.fallbackBrush),
                            size = cache.localSize,
                            blendMode = BlendMode.SrcOver
                        )
                    }
                    if (item.frostAlpha > 0.001f) {
                        drawRect(
                            color = Color.White.copy(alpha = item.frostAlpha),
                            size = cache.localSize,
                            blendMode = BlendMode.SrcOver
                        )
                    }
                    if (item.dimAlpha > 0.001f) {
                        drawRect(
                            color = Color.Black.copy(alpha = item.dimAlpha),
                            size = cache.localSize,
                            blendMode = BlendMode.SrcOver
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.ensureSettingsFrostCache(
    item: SettingsFrostParentItem,
    itemSize: Size
): SettingsFrostDrawCache {
    val cache = item.drawCache
    val width = itemSize.width.coerceAtLeast(1f)
    val height = itemSize.height.coerceAtLeast(1f)
    val radiusPx = item.radiusDp.dp.toPx()
        .coerceAtMost(max(1f, min(width, height) * 0.5f))
    val signature = frostGeometrySignature(width, height, radiusPx)
    if (cache.geometrySignature == signature) return cache

    cache.localSize = Size(width, height)
    cache.mask = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = width,
                bottom = height,
                radiusX = radiusPx,
                radiusY = radiusPx
            )
        )
    }
    cache.fallbackBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1A2B58),
            Color(0xFF5B4A8E),
            Color(0xFFB85D78)
        ),
        startY = 0f,
        endY = height
    )
    cache.geometrySignature = signature
    return cache
}

private fun frostGeometrySignature(width: Float, height: Float, radius: Float): Long {
    var result = 1125899906842597L
    result = result * 31L + width.toBits()
    result = result * 31L + height.toBits()
    result = result * 31L + radius.toBits()
    return result
}

private fun DrawScope.drawParentBackdropImage(
    backdrop: BlurredBackdropBitmap,
    sampleOffset: Offset,
    localSize: Size,
    alpha: Float
) {
    val rootWidth = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
    val rootHeight = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)
    val localLeft = max(0f, -sampleOffset.x)
    val localTop = max(0f, -sampleOffset.y)
    val localRight = min(localSize.width, rootWidth - sampleOffset.x)
    val localBottom = min(localSize.height, rootHeight - sampleOffset.y)
    val visibleWidth = localRight - localLeft
    val visibleHeight = localBottom - localTop
    if (visibleWidth <= 0f || visibleHeight <= 0f) return

    val sourceX = ((sampleOffset.x + localLeft) * backdrop.scale)
        .roundToInt()
        .coerceIn(0, backdrop.image.width - 1)
    val sourceY = ((sampleOffset.y + localTop) * backdrop.scale)
        .roundToInt()
        .coerceIn(0, backdrop.image.height - 1)
    val sourceWidth = (visibleWidth * backdrop.scale)
        .roundToInt()
        .coerceAtLeast(1)
        .coerceAtMost(backdrop.image.width - sourceX)
    val sourceHeight = (visibleHeight * backdrop.scale)
        .roundToInt()
        .coerceAtLeast(1)
        .coerceAtMost(backdrop.image.height - sourceY)

    drawImage(
        image = backdrop.image,
        srcOffset = IntOffset(sourceX, sourceY),
        srcSize = IntSize(sourceWidth, sourceHeight),
        dstOffset = IntOffset(localLeft.roundToInt(), localTop.roundToInt()),
        dstSize = IntSize(
            visibleWidth.roundToInt().coerceAtLeast(1),
            visibleHeight.roundToInt().coerceAtLeast(1)
        ),
        alpha = alpha,
        blendMode = BlendMode.SrcOver
    )
}

@Composable
fun SettingsFrostParentRegistrationCleanup(
    layerState: SettingsFrostParentLayerState?,
    id: String
) {
    DisposableEffect(layerState, id) {
        onDispose { layerState?.remove(id) }
    }
}
