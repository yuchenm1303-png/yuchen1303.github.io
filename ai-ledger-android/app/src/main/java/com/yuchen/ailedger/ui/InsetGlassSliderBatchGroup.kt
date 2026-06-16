package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
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

internal data class InsetGlassBatchSlot(
    val rect: Rect,
    val coordinateSource: GlassCoordinateSource
)

private data class CachedInsetGlassBatchSlot(
    val slot: InsetGlassBatchSlot,
    val mask: Path,
    val corner: CornerRadius,
    val innerTopLeft: Offset,
    val innerSize: Size,
    val innerBrush: Brush,
    val outerTopLeft: Offset,
    val outerSize: Size,
    val fallbackBrush: Brush
)

/**
 * 仅服务于凹槽参数滑块的父级批绘制状态。
 * 不接入普通玻璃 registry，也不触发 OpenGL geometry sync。
 */
internal class InsetGlassSliderBatchState {
    private val childCoordinates = LinkedHashMap<Any, LayoutCoordinates>()
    private val childCoordinateSources = LinkedHashMap<Any, GlassCoordinateSource>()
    private var hostCoordinates: LayoutCoordinates? = null
    private var hostSize: IntSize = IntSize.Zero

    internal val slots = mutableStateMapOf<Any, InsetGlassBatchSlot>()

    internal fun updateHost(coordinates: LayoutCoordinates) {
        val firstPlacement = hostCoordinates == null
        val sizeChanged = hostSize != coordinates.size
        hostCoordinates = coordinates
        hostSize = coordinates.size
        if (firstPlacement || sizeChanged) syncAll()
    }

    internal fun updateSlot(key: Any, coordinates: LayoutCoordinates) {
        childCoordinates[key] = coordinates
        childCoordinateSources.getOrPut(key) { GlassCoordinateSource() }.coordinates = coordinates
        syncSlot(key)
    }

    internal fun removeSlot(key: Any) {
        childCoordinates.remove(key)
        childCoordinateSources.remove(key)
        slots.remove(key)
    }

    internal fun clear() {
        childCoordinates.clear()
        childCoordinateSources.clear()
        slots.clear()
        hostCoordinates = null
        hostSize = IntSize.Zero
    }

    private fun syncAll() {
        childCoordinates.keys.forEach(::syncSlot)
    }

    private fun syncSlot(key: Any) {
        val host = hostCoordinates ?: return
        val child = childCoordinates[key] ?: return
        val coordinateSource = childCoordinateSources[key] ?: return
        if (!host.isAttached || !child.isAttached) return
        val rect = host.localBoundingBoxOf(child, clipBounds = false)
        if (rect.width <= 0f || rect.height <= 0f) return
        val current = slots[key]
        if (current == null || current.rect != rect || current.coordinateSource !== coordinateSource) {
            slots[key] = InsetGlassBatchSlot(rect, coordinateSource)
        }
    }
}

internal val LocalInsetGlassSliderBatchState =
    staticCompositionLocalOf<InsetGlassSliderBatchState?> { null }

/**
 * 每个展开参数组使用一个 Host。静态玻璃底面在父级一次绘制，
 * 子滑块只绘制进度轨和文字，避免大量重复背景裁切与 Canvas 节点。
 */
@Composable
internal fun InsetGlassSliderBatchGroup(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = remember { InsetGlassSliderBatchState() }
    DisposableEffect(state) {
        onDispose { state.clear() }
    }

    CompositionLocalProvider(LocalInsetGlassSliderBatchState provides state) {
        Box(modifier = modifier.onPlaced(state::updateHost)) {
            InsetGlassSliderBatchChrome(
                state = state,
                modifier = Modifier.matchParentSize()
            )
            content()
        }
    }
}

@Composable
private fun InsetGlassSliderBatchChrome(
    state: InsetGlassSliderBatchState,
    modifier: Modifier = Modifier
) {
    val slotEntries = state.slots.values
        .asSequence()
        .filter { it.rect.width > 0f && it.rect.height > 0f }
        .toList()

    // 空的嵌套 Host 不创建 Canvas，也不读取全局帧 ticker。
    if (slotEntries.isEmpty()) return

    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    val density = LocalDensity.current

    val radiusPx = with(density) { BatchInsetRadius.dp.toPx() }
    val insetPx = with(density) { (1.5f + BatchInsetDepth * 6f).dp.toPx() }
    val innerStrokeWidthPx = with(density) { (1.2f + BatchInsetDepth * 3f).dp.toPx() }
    val outerInsetPx = with(density) { 1.dp.toPx() }
    val outerStrokeWidthPx = with(density) { 0.9.dp.toPx() }
    val preloadMarginPx = with(density) { BatchPreloadMarginDp.dp.toPx() }

    // 槽位几何变化时才重建 Path、Brush、CornerRadius 和 Size。
    val cachedSlots = remember(
        slotEntries,
        radiusPx,
        insetPx,
        outerInsetPx
    ) {
        slotEntries.map { slot ->
            val rect = slot.rect
            val corner = CornerRadius(radiusPx, radiusPx)
            CachedInsetGlassBatchSlot(
                slot = slot,
                mask = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = rect.left,
                            top = rect.top,
                            right = rect.right,
                            bottom = rect.bottom,
                            radiusX = radiusPx,
                            radiusY = radiusPx
                        )
                    )
                },
                corner = corner,
                innerTopLeft = Offset(rect.left + insetPx, rect.top + insetPx),
                innerSize = Size(
                    (rect.width - insetPx * 2f).coerceAtLeast(1f),
                    (rect.height - insetPx * 2f).coerceAtLeast(1f)
                ),
                innerBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = BatchInsetInnerShadow * 0.42f),
                        Color.Transparent,
                        Color.White.copy(alpha = BatchInsetRimHighlight * 0.26f)
                    ),
                    startY = rect.top,
                    endY = rect.bottom
                ),
                outerTopLeft = Offset(rect.left + outerInsetPx, rect.top + outerInsetPx),
                outerSize = Size(
                    (rect.width - outerInsetPx * 2f).coerceAtLeast(1f),
                    (rect.height - outerInsetPx * 2f).coerceAtLeast(1f)
                ),
                fallbackBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A2B58),
                        Color(0xFF5B4A8E),
                        Color(0xFFB85D78)
                    ),
                    startY = rect.top,
                    endY = rect.bottom
                )
            )
        }
    }

    Canvas(modifier = modifier) {
        val backdrop = cachedBackdrop
        // 不降低 ticker 频率：滚动时背景裁剪仍逐帧跟随。
        if (backdrop != null) frameTicker?.frameNanos

        // 用一个锚点估算 Host 的屏幕原点。长分组先靠缓存 rect 做离屏判断，
        // 只有进入屏幕附近的槽位才读取自己的精确 LayoutCoordinates。
        val anchor = cachedSlots.first()
        val anchorSampleOffset = if (backdrop != null) {
            anchor.slot.coordinateSource.offsetRelativeTo(backdropOrigin)
        } else {
            Offset.Unspecified
        }
        val estimatedHostOffset = if (anchorSampleOffset.isSpecified) {
            anchorSampleOffset - anchor.slot.rect.topLeft
        } else {
            Offset.Unspecified
        }

        cachedSlots.forEach slotLoop@ { cache ->
            val slot = cache.slot
            val rect = slot.rect
            val estimatedSampleOffset = if (estimatedHostOffset.isSpecified) {
                estimatedHostOffset + rect.topLeft
            } else if (backdrop != null) {
                slot.coordinateSource.offsetRelativeTo(backdropOrigin)
            } else {
                Offset.Unspecified
            }

            val visible = if (backdrop != null) {
                isSlotNearViewport(
                    sampleOffset = estimatedSampleOffset,
                    slotSize = rect.size,
                    viewportWidth = backdrop.fullWidthPx.toFloat(),
                    viewportHeight = backdrop.fullHeightPx.toFloat(),
                    margin = preloadMarginPx
                )
            } else {
                rect.right >= -preloadMarginPx &&
                    rect.bottom >= -preloadMarginPx &&
                    rect.left <= size.width + preloadMarginPx &&
                    rect.top <= size.height + preloadMarginPx
            }
            if (!visible) return@slotLoop

            val exactSampleOffset = when {
                backdrop == null -> Offset.Unspecified
                cache === anchor -> anchorSampleOffset
                else -> slot.coordinateSource.offsetRelativeTo(backdropOrigin)
            }

            clipPath(cache.mask) {
                if (backdrop != null && exactSampleOffset.isSpecified) {
                    drawSlotBackdrop(
                        backdrop = backdrop,
                        sampleOffset = exactSampleOffset,
                        destination = rect,
                        alpha = BatchInsetBackdropAlpha
                    )
                } else {
                    drawRect(
                        brush = cache.fallbackBrush,
                        topLeft = rect.topLeft,
                        size = rect.size
                    )
                }
                drawRect(
                    color = Color.Black.copy(alpha = BatchInsetFloorDim),
                    topLeft = rect.topLeft,
                    size = rect.size
                )
            }

            drawRoundRect(
                color = Color.Black.copy(alpha = BatchInsetInnerShadow * 0.45f),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = cache.corner,
                blendMode = BlendMode.Multiply
            )
            drawRoundRect(
                brush = cache.innerBrush,
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

private fun isSlotNearViewport(
    sampleOffset: Offset,
    slotSize: Size,
    viewportWidth: Float,
    viewportHeight: Float,
    margin: Float
): Boolean {
    if (!sampleOffset.isSpecified) return false
    return sampleOffset.x + slotSize.width >= -margin &&
        sampleOffset.y + slotSize.height >= -margin &&
        sampleOffset.x <= viewportWidth + margin &&
        sampleOffset.y <= viewportHeight + margin
}

/**
 * 每个槽位使用自己的实际屏幕坐标采样背景。
 * 仍然只由父级 Canvas 执行，避免 Host 级可见宽度裁切造成固定竖向断层。
 */
private fun DrawScope.drawSlotBackdrop(
    backdrop: BlurredBackdropBitmap,
    sampleOffset: Offset,
    destination: Rect,
    alpha: Float
) {
    val rootWidth = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
    val rootHeight = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)
    val localLeft = max(0f, -sampleOffset.x)
    val localTop = max(0f, -sampleOffset.y)
    val localRight = min(destination.width, rootWidth - sampleOffset.x)
    val localBottom = min(destination.height, rootHeight - sampleOffset.y)
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
        dstOffset = IntOffset(
            (destination.left + localLeft).roundToInt(),
            (destination.top + localTop).roundToInt()
        ),
        dstSize = IntSize(
            visibleWidth.roundToInt().coerceAtLeast(1),
            visibleHeight.roundToInt().coerceAtLeast(1)
        ),
        alpha = alpha,
        blendMode = BlendMode.SrcOver
    )
}
