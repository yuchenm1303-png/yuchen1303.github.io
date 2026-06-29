package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val BatchInsetRadius = 18f
private const val BatchInsetDepth = 0.52f
private const val BatchInsetBackdropAlpha = 0.82f
private const val BatchInsetRimHighlight = 0.34f
private const val BatchInsetInnerShadow = 0.67f
private const val BatchInsetFloorDim = 0.23f
private const val BatchPreloadMarginDp = 64f

internal class CachedInsetGlassBatchSlot {
    var geometrySignature: Long = Long.MIN_VALUE
    var mask: Path = Path()
    var corner: CornerRadius = CornerRadius(0f, 0f)
    var localSize: Size = Size.Zero
    var innerTopLeft: Offset = Offset.Zero
    var innerSize: Size = Size.Zero
    var innerBrush: Brush? = null
    var outerTopLeft: Offset = Offset.Zero
    var outerSize: Size = Size.Zero
    var fallbackBrush: Brush? = null
}

internal class InsetGlassBatchSlot(
    var rect: Rect,
    var coordinates: LayoutCoordinates
) {
    val cache = CachedInsetGlassBatchSlot()
}

internal class InsetGlassSliderBatchState {
    private val childCoordinates = LinkedHashMap<Any, LayoutCoordinates>()
    private val slots = LinkedHashMap<Any, InsetGlassBatchSlot>()
    private var cachedSnapshot: List<InsetGlassBatchSlot> = emptyList()
    private var hostCoordinates: LayoutCoordinates? = null
    private var hostSize: IntSize = IntSize.Zero
    internal val hostCoordinateSource = GlassCoordinateSource()

    internal var drawVersion by mutableLongStateOf(0L)
        private set

    internal fun snapshotForDraw(): List<InsetGlassBatchSlot> = cachedSnapshot

    internal fun updateHost(coordinates: LayoutCoordinates) {
        val firstPlacement = hostCoordinates == null
        val sizeChanged = hostSize != coordinates.size
        hostCoordinates = coordinates
        hostSize = coordinates.size
        hostCoordinateSource.coordinates = coordinates
        if (firstPlacement || sizeChanged) syncAll()
    }

    internal fun updateSlot(key: Any, coordinates: LayoutCoordinates) {
        childCoordinates[key] = coordinates
        syncSlot(key)
    }

    internal fun removeSlot(key: Any) {
        childCoordinates.remove(key)
        if (slots.remove(key) != null) rebuildSnapshot()
    }

    internal fun clear() {
        childCoordinates.clear()
        slots.clear()
        cachedSnapshot = emptyList()
        hostCoordinates = null
        hostSize = IntSize.Zero
        hostCoordinateSource.coordinates = null
        bumpDrawVersion()
    }

    private fun syncAll() {
        val keys = childCoordinates.keys.toTypedArray()
        keys.forEach(::syncSlot)
    }

    private fun syncSlot(key: Any) {
        val host = hostCoordinates ?: return
        val child = childCoordinates[key] ?: return
        if (!host.isAttached || !child.isAttached) {
            if (slots.remove(key) != null) rebuildSnapshot()
            return
        }

        val rect = host.localBoundingBoxOf(child, clipBounds = false)
        if (rect.width <= 0f || rect.height <= 0f) {
            if (slots.remove(key) != null) rebuildSnapshot()
            return
        }

        val current = slots[key]
        if (current == null) {
            slots[key] = InsetGlassBatchSlot(rect, child)
            rebuildSnapshot()
        } else {
            var changed = false
            if (current.rect != rect) {
                current.rect = rect
                changed = true
            }
            if (current.coordinates !== child) {
                current.coordinates = child
                changed = true
            }
            if (changed) bumpDrawVersion()
        }
    }

    private fun rebuildSnapshot() {
        cachedSnapshot = slots.values.toList()
        bumpDrawVersion()
    }

    private fun bumpDrawVersion() {
        drawVersion = if (drawVersion == Long.MAX_VALUE) 1L else drawVersion + 1L
    }
}

internal val LocalInsetGlassSliderBatchState =
    staticCompositionLocalOf<InsetGlassSliderBatchState?> { null }

@Composable
internal fun InsetGlassSliderBatchGroup(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    if (!LocalSettingsStaticBatchReady.current) {
        CompositionLocalProvider(LocalInsetGlassSliderBatchState provides null) {
            Box(modifier = modifier, content = content)
        }
        return
    }

    val state = remember { InsetGlassSliderBatchState() }
    DisposableEffect(state) {
        onDispose { state.clear() }
    }

    CompositionLocalProvider(LocalInsetGlassSliderBatchState provides state) {
        Box(modifier = modifier.onPlaced(state::updateHost)) {
            InsetGlassSliderBatchChrome(state = state)
            content()
        }
    }
}

@Composable
private fun BoxScope.InsetGlassSliderBatchChrome(
    state: InsetGlassSliderBatchState
) {
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    val foldoutClipRegistry = LocalGlassFoldoutClipRegistry.current

    Canvas(modifier = Modifier.matchParentSize()) {
        state.drawVersion
        val slots = state.snapshotForDraw()
        if (slots.isEmpty()) return@Canvas
        if (!state.hostCoordinateSource.isAttached()) return@Canvas

        val backdrop = cachedBackdrop
        val radiusPx = BatchInsetRadius.dp.toPx()
        val insetPx = (1.5f + BatchInsetDepth * 6f).dp.toPx()
        val innerStrokeWidthPx = (1.2f + BatchInsetDepth * 3f).dp.toPx()
        val outerInsetPx = 1.dp.toPx()
        val outerStrokeWidthPx = 0.9.dp.toPx()
        val preloadMarginPx = BatchPreloadMarginDp.dp.toPx()
        val hostRootOffset = state.hostCoordinateSource.rootOffset()
        val viewport = Rect(0f, 0f, size.width, size.height)
        val hostSampleOffset = if (backdrop != null) {
            state.hostCoordinateSource.offsetRelativeTo(backdropOrigin)
        } else {
            Offset.Unspecified
        }

        val hasHostVisibleSlot = slots.any { slot ->
            slot.coordinates.isAttached && slot.rect.isNearViewport(
                viewportWidth = size.width,
                viewportHeight = size.height,
                margin = preloadMarginPx,
            )
        }
        if (!hasHostVisibleSlot) return@Canvas
        if (backdrop != null) frameTicker?.frameNanos

        slots.forEach { slot ->
            if (!slot.coordinates.isAttached) return@forEach
            val rect = slot.rect
            if (!rect.isNearViewport(size.width, size.height, preloadMarginPx)) return@forEach

            val sampleOffset = if (hostSampleOffset.hasFiniteCoordinates()) {
                hostSampleOffset + rect.topLeft
            } else {
                Offset.Unspecified
            }
            val visibleInBackdrop = if (backdrop != null) {
                isSlotNearViewport(
                    sampleOffset = sampleOffset,
                    slotSize = rect.size,
                    viewportWidth = backdrop.fullWidthPx.toFloat(),
                    viewportHeight = backdrop.fullHeightPx.toFloat(),
                    margin = preloadMarginPx
                )
            } else {
                true
            }
            if (!visibleInBackdrop) return@forEach

            val foldoutClip = foldoutClipRegistry.resolveLocalClip(
                descendant = slot.coordinates,
                hostRootOffset = hostRootOffset,
                viewport = viewport
            ) ?: return@forEach
            if (rect.intersectionOrNull(foldoutClip) == null) return@forEach

            val cache = ensureBatchSlotCache(
                cache = slot.cache,
                slotSize = rect.size,
                radiusPx = radiusPx,
                insetPx = insetPx,
                innerStrokeWidthPx = innerStrokeWidthPx,
                outerInsetPx = outerInsetPx,
                outerStrokeWidthPx = outerStrokeWidthPx
            )

            clipRect(
                left = foldoutClip.left,
                top = foldoutClip.top,
                right = foldoutClip.right,
                bottom = foldoutClip.bottom
            ) {
                withTransform({ translate(rect.left, rect.top) }) {
                    clipPath(cache.mask) {
                        if (backdrop != null && sampleOffset.hasFiniteCoordinates()) {
                            drawSlotBackdrop(
                                backdrop = backdrop,
                                sampleOffset = sampleOffset,
                                slotSize = cache.localSize,
                                alpha = BatchInsetBackdropAlpha
                            )
                        } else {
                            drawRect(
                                brush = requireNotNull(cache.fallbackBrush),
                                size = cache.localSize
                            )
                        }
                        drawRect(
                            color = Color.Black.copy(alpha = BatchInsetFloorDim),
                            size = cache.localSize
                        )
                    }

                    drawRoundRect(
                        color = Color.Black.copy(alpha = BatchInsetInnerShadow * 0.45f),
                        size = cache.localSize,
                        cornerRadius = cache.corner,
                        blendMode = BlendMode.Multiply
                    )
                    drawRoundRect(
                        brush = requireNotNull(cache.innerBrush),
                        topLeft = cache.innerTopLeft,
                        size = cache.innerSize,
                        cornerRadius = cache.corner,
                        style = Stroke(width = innerStrokeWidthPx),
                        blendMode = BlendMode.Screen
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = BatchInsetRimHighlight * 0.18f),
                        topLeft = cache.outerTopLeft,
                        size = cache.outerSize,
                        cornerRadius = cache.corner,
                        style = Stroke(width = outerStrokeWidthPx),
                        blendMode = BlendMode.Screen
                    )
                }
            }
        }
    }
}

private fun ensureBatchSlotCache(
    cache: CachedInsetGlassBatchSlot,
    slotSize: Size,
    radiusPx: Float,
    insetPx: Float,
    innerStrokeWidthPx: Float,
    outerInsetPx: Float,
    outerStrokeWidthPx: Float
): CachedInsetGlassBatchSlot {
    val width = slotSize.width.coerceAtLeast(1f)
    val height = slotSize.height.coerceAtLeast(1f)
    val signature = batchGeometrySignature(
        width,
        height,
        radiusPx,
        insetPx,
        innerStrokeWidthPx,
        outerInsetPx,
        outerStrokeWidthPx
    )
    if (cache.geometrySignature == signature) return cache

    cache.localSize = Size(width, height)
    cache.corner = CornerRadius(radiusPx, radiusPx)
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
    cache.innerTopLeft = Offset(insetPx, insetPx)
    cache.innerSize = Size(
        (width - insetPx * 2f).coerceAtLeast(1f),
        (height - insetPx * 2f).coerceAtLeast(1f)
    )
    cache.innerBrush = Brush.verticalGradient(
        colors = listOf(
            Color.Black.copy(alpha = BatchInsetInnerShadow * 0.42f),
            Color.Transparent,
            Color.White.copy(alpha = BatchInsetRimHighlight * 0.26f)
        ),
        startY = 0f,
        endY = height
    )
    cache.outerTopLeft = Offset(outerInsetPx, outerInsetPx)
    cache.outerSize = Size(
        (width - outerInsetPx * 2f).coerceAtLeast(1f),
        (height - outerInsetPx * 2f).coerceAtLeast(1f)
    )
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

private fun batchGeometrySignature(
    width: Float,
    height: Float,
    radius: Float,
    inset: Float,
    innerStroke: Float,
    outerInset: Float,
    outerStroke: Float
): Long {
    var result = 1125899906842597L
    result = result * 31L + width.toBits()
    result = result * 31L + height.toBits()
    result = result * 31L + radius.toBits()
    result = result * 31L + inset.toBits()
    result = result * 31L + innerStroke.toBits()
    result = result * 31L + outerInset.toBits()
    result = result * 31L + outerStroke.toBits()
    return result
}

private fun Rect.isNearViewport(
    viewportWidth: Float,
    viewportHeight: Float,
    margin: Float,
): Boolean = right >= -margin &&
    bottom >= -margin &&
    left <= viewportWidth + margin &&
    top <= viewportHeight + margin

private fun Offset.hasFiniteCoordinates(): Boolean = x.isFinite() && y.isFinite()

private fun isSlotNearViewport(
    sampleOffset: Offset,
    slotSize: Size,
    viewportWidth: Float,
    viewportHeight: Float,
    margin: Float
): Boolean {
    if (!sampleOffset.hasFiniteCoordinates()) return false
    return sampleOffset.x + slotSize.width >= -margin &&
        sampleOffset.y + slotSize.height >= -margin &&
        sampleOffset.x <= viewportWidth + margin &&
        sampleOffset.y <= viewportHeight + margin
}

private fun DrawScope.drawSlotBackdrop(
    backdrop: BlurredBackdropBitmap,
    sampleOffset: Offset,
    slotSize: Size,
    alpha: Float
) {
    val rootWidth = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
    val rootHeight = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)
    val localLeft = max(0f, -sampleOffset.x)
    val localTop = max(0f, -sampleOffset.y)
    val localRight = min(slotSize.width, rootWidth - sampleOffset.x)
    val localBottom = min(slotSize.height, rootHeight - sampleOffset.y)
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
