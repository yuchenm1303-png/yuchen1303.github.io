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

/**
 * 仅服务于凹槽参数滑块的父级批绘制状态。
 * 不接入普通玻璃 registry，也不触发 OpenGL geometry sync。
 */
internal class InsetGlassSliderBatchState {
    private val childCoordinates = LinkedHashMap<Any, LayoutCoordinates>()
    private var hostCoordinates: LayoutCoordinates? = null

    internal val slots = mutableStateMapOf<Any, Rect>()
    internal val coordinateSource = GlassCoordinateSource()

    internal fun updateHost(coordinates: LayoutCoordinates) {
        hostCoordinates = coordinates
        coordinateSource.coordinates = coordinates
        syncAll()
    }

    internal fun updateSlot(key: Any, coordinates: LayoutCoordinates) {
        childCoordinates[key] = coordinates
        syncSlot(key)
    }

    internal fun removeSlot(key: Any) {
        childCoordinates.remove(key)
        slots.remove(key)
    }

    internal fun clear() {
        childCoordinates.clear()
        slots.clear()
        hostCoordinates = null
    }

    private fun syncAll() {
        childCoordinates.keys.toList().forEach(::syncSlot)
    }

    private fun syncSlot(key: Any) {
        val host = hostCoordinates ?: return
        val child = childCoordinates[key] ?: return
        if (!host.isAttached || !child.isAttached) return
        val rect = host.localBoundingBoxOf(child, clipBounds = false)
        if (rect.width <= 0f || rect.height <= 0f) return
        if (slots[key] != rect) slots[key] = rect
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
    val slotRects = state.slots.values
        .filter { it.width > 0f && it.height > 0f }
        .toList()

    val radiusPx = with(density) { BatchInsetRadius.dp.toPx() }
    val insetPx = with(density) { (1.5f + BatchInsetDepth * 6f).dp.toPx() }
    val innerStrokeWidthPx = with(density) { (1.2f + BatchInsetDepth * 3f).dp.toPx() }
    val outerInsetPx = with(density) { 1.dp.toPx() }
    val outerStrokeWidthPx = with(density) { 0.9.dp.toPx() }
    val slotMask = remember(slotRects, radiusPx) {
        Path().apply {
            slotRects.forEach { rect ->
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
            }
        }
    }

    Canvas(modifier = modifier) {
        frameTicker?.frameNanos
        if (slotRects.isEmpty()) return@Canvas

        clipPath(slotMask) {
            val backdrop = cachedBackdrop
            if (backdrop != null) {
                val sampleOffset = state.coordinateSource.offsetRelativeTo(backdropOrigin)
                drawVisibleBatchBackdrop(
                    backdrop = backdrop,
                    sampleOffset = sampleOffset,
                    alpha = BatchInsetBackdropAlpha
                )
            } else {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1A2B58),
                            Color(0xFF5B4A8E),
                            Color(0xFFB85D78)
                        )
                    )
                )
            }
            drawRect(Color.Black.copy(alpha = BatchInsetFloorDim))
        }

        slotRects.forEach { rect ->
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

private fun DrawScope.drawVisibleBatchBackdrop(
    backdrop: BlurredBackdropBitmap,
    sampleOffset: Offset,
    alpha: Float
) {
    val rootWidth = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
    val rootHeight = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)
    val localLeft = max(0f, -sampleOffset.x)
    val localTop = max(0f, -sampleOffset.y)
    val localRight = min(size.width, rootWidth - sampleOffset.x)
    val localBottom = min(size.height, rootHeight - sampleOffset.y)
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
