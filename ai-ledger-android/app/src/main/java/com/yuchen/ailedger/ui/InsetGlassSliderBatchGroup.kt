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
import androidx.compose.ui.layout.onGloballyPositioned
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

private data class InsetGlassBatchSlot(
    val rect: Rect,
    val coordinateSource: GlassCoordinateSource
)

/**
 * 仅服务于凹槽参数滑块的父级批绘制状态。
 * 不接入普通玻璃 registry，也不触发 OpenGL geometry sync。
 */
internal class InsetGlassSliderBatchState {
    private val childCoordinates = LinkedHashMap<Any, LayoutCoordinates>()
    private val childCoordinateSources = LinkedHashMap<Any, GlassCoordinateSource>()
    private var hostCoordinates: LayoutCoordinates? = null

    internal val slots = mutableStateMapOf<Any, InsetGlassBatchSlot>()

    internal fun updateHost(coordinates: LayoutCoordinates) {
        hostCoordinates = coordinates
        syncAll()
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
    }

    private fun syncAll() {
        childCoordinates.keys.toList().forEach(::syncSlot)
    }

    private fun syncSlot(key: Any) {
        val host = hostCoordinates ?: return
        val child = childCoordinates[key] ?: return
        val coordinateSource = childCoordinateSources[key] ?: return
        if (!host.isAttached || !child.isAttached) return
        val rect = host.localBoundingBoxOf(child, clipBounds = false)
        if (rect.width <= 0f || rect.height <= 0f) return
        val current = slots[key]
        if (current?.rect != rect || current.coordinateSource !== coordinateSource) {
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
        Box(
            modifier = modifier.onGloballyPositioned(state::updateHost)
        ) {
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
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    val density = LocalDensity.current
    val slotEntries = state.slots.values
        .filter { it.rect.width > 0f && it.rect.height > 0f }
        .toList()

    val radiusPx = with(density) { BatchInsetRadius.dp.toPx() }
    val insetPx = with(density) { (1.5f + BatchInsetDepth * 6f).dp.toPx() }
    val innerStrokeWidthPx = with(density) { (1.2f + BatchInsetDepth * 3f).dp.toPx() }
    val outerInsetPx = with(density) { 1.dp.toPx() }
    val outerStrokeWidthPx = with(density) { 0.9.dp.toPx() }

    val slotMasks = remember(slotEntries, radiusPx) {
        slotEntries.map { slot ->
            slot to Path().apply {
                addRoundRect(
                    RoundRect(
                        left = slot.rect.left,
                        top = slot.rect.top,
                        right = slot.rect.right,
                        bottom = slot.rect.bottom,
                        radiusX = radiusPx,
                        radiusY = radiusPx
                    )
                )
            }
        }
    }

    Canvas(modifier = modifier) {
        frameTicker?.frameNanos
        if (slotMasks.isEmpty()) return@Canvas

        slotMasks.forEach { (slot, mask) ->
            val rect = slot.rect
            clipPath(mask) {
                val backdrop = cachedBackdrop
                if (backdrop != null) {
                    drawSlotBackdrop(
                        backdrop = backdrop,
                        sampleOffset = slot.coordinateSource.offsetRelativeTo(backdropOrigin),
                        destination = rect,
                        alpha = BatchInsetBackdropAlpha
                    )
                } else {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A2B58),
                                Color(0xFF5B4A8E),
                                Color(0xFFB85D78)
                            ),
                            startY = rect.top,
                            endY = rect.bottom
                        ),
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

            val corner = CornerRadius(radiusPx, radiusPx)
            drawRoundRect(
                color = Color.Black.copy(alpha = BatchInsetInnerShadow * 0.45f),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = corner,
                blendMode = BlendMode.Multiply
            )

            val innerWidth = (rect.width - insetPx * 2f).coerceAtLeast(1f)
            val innerHeight = (rect.height - insetPx * 2f).coerceAtLeast(1f)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = BatchInsetInnerShadow * 0.42f),
                        Color.Transparent,
                        Color.White.copy(alpha = BatchInsetRimHighlight * 0.26f)
                    ),
                    startY = rect.top,
                    endY = rect.bottom
                ),
                topLeft = Offset(rect.left + insetPx, rect.top + insetPx),
                size = Size(innerWidth, innerHeight),
                cornerRadius = corner,
                style = Stroke(width = innerStrokeWidthPx),
                blendMode = BlendMode.Screen
            )

            drawRoundRect(
                color = Color.White.copy(alpha = BatchInsetRimHighlight * 0.18f),
                topLeft = Offset(rect.left + outerInsetPx, rect.top + outerInsetPx),
                size = Size(
                    (rect.width - outerInsetPx * 2f).coerceAtLeast(1f),
                    (rect.height - outerInsetPx * 2f).coerceAtLeast(1f)
                ),
                cornerRadius = corner,
                style = Stroke(width = outerStrokeWidthPx),
                blendMode = BlendMode.Screen
            )
        }
    }
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
