package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

private const val PageFrostPreloadMarginDp = 64f

@Composable
internal fun PageFrostParentLayer(
    layerState: PageFrostParentLayerState,
    modifier: Modifier = Modifier,
) {
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    val foldoutClipRegistry = LocalGlassFoldoutClipRegistry.current

    Canvas(modifier = modifier) {
        layerState.drawVersion
        val items = layerState.items()
        if (items.isEmpty()) return@Canvas

        val root = layerState.rootBounds()
        if (root.width <= 1f || root.height <= 1f) return@Canvas
        val viewport = Rect(0f, 0f, size.width, size.height)
        val preloadMargin = PageFrostPreloadMarginDp.dp.toPx()
        val backdrop = cachedBackdrop
        val backdropRoot = backdropOrigin?.rootOffset() ?: Offset.Zero
        if (backdrop != null) frameTicker?.frameNanos

        items.forEach { item ->
            val coordinates = item.coordinates
            if (!coordinates.isAttached) return@forEach
            val localRect = layerState.localBoundsOf(coordinates) ?: return@forEach
            if (localRect.width <= 1f || localRect.height <= 1f) return@forEach
            if (!localRect.isNearPageFrostViewport(viewport, preloadMargin)) return@forEach

            val foldoutClip = foldoutClipRegistry.resolveLocalClip(
                descendant = coordinates,
                hostRootOffset = root.topLeft,
                viewport = viewport,
            ) ?: return@forEach
            if (!localRect.overlapsPageFrostRect(foldoutClip)) return@forEach

            val sourceSize = Size(
                coordinates.size.width.toFloat().coerceAtLeast(1f),
                coordinates.size.height.toFloat().coerceAtLeast(1f),
            )
            val cache = ensurePageFrostCache(item, localRect.size, sourceSize)
            val sampleOffset = coordinates.positionInRoot() - backdropRoot
            clipRect(
                left = foldoutClip.left,
                top = foldoutClip.top,
                right = foldoutClip.right,
                bottom = foldoutClip.bottom,
            ) {
                withTransform({ translate(localRect.left, localRect.top) }) {
                    clipPath(cache.mask) {
                        if (backdrop != null) {
                            drawPageFrostBackdrop(
                                backdrop = backdrop,
                                sampleOffset = sampleOffset,
                                sampleSize = sourceSize,
                                destinationSize = cache.localSize,
                                alpha = item.backdropAlpha,
                            )
                        } else {
                            drawRect(
                                brush = requireNotNull(cache.fallbackBrush),
                                size = cache.localSize,
                                blendMode = BlendMode.SrcOver,
                            )
                        }
                        if (item.frostAlpha > 0f) {
                            drawRect(Color.White.copy(alpha = item.frostAlpha), size = cache.localSize)
                        }
                        if (item.dimAlpha > 0f) {
                            drawRect(Color.Black.copy(alpha = item.dimAlpha), size = cache.localSize)
                        }
                    }
                }
            }
        }
    }
}

private fun Rect.overlapsPageFrostRect(other: Rect): Boolean =
    right > other.left && bottom > other.top && left < other.right && top < other.bottom

internal fun Rect.isNearPageFrostViewport(viewport: Rect, margin: Float): Boolean =
    right >= viewport.left - margin &&
        bottom >= viewport.top - margin &&
        left <= viewport.right + margin &&
        top <= viewport.bottom + margin
