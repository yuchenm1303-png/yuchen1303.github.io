package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

private const val PageFrostPreloadMarginDp = 64f

/** 通用雾面玻璃的页面级单 Canvas，不接入任何 OpenGL 结构。 */
@Composable
internal fun PageFrostParentLayer(
    layerState: PageFrostParentLayerState,
    modifier: Modifier = Modifier,
) {
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    val foldoutClipRegistry = LocalGlassFoldoutClipRegistry.current

    Canvas(modifier = modifier.onGloballyPositioned(layerState::updateRoot)) {
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
            if (!item.coordinates.isAttached) return@forEach
            val localRect = Rect(
                left = item.rectInRoot.left - root.left,
                top = item.rectInRoot.top - root.top,
                right = item.rectInRoot.right - root.left,
                bottom = item.rectInRoot.bottom - root.top,
            )
            if (!localRect.isNearPageFrostViewport(viewport, preloadMargin)) return@forEach

            val foldoutClip = foldoutClipRegistry.resolveLocalClip(
                descendant = item.coordinates,
                hostRootOffset = root.topLeft,
                viewport = viewport,
            ) ?: return@forEach
            if (!localRect.overlapsPageFrostRect(foldoutClip)) return@forEach

            val cache = ensurePageFrostCache(item, localRect.size)
            val sampleOffset = item.rectInRoot.topLeft - backdropRoot
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
                                localSize = cache.localSize,
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
                            drawRect(
                                color = Color.White.copy(alpha = item.frostAlpha),
                                size = cache.localSize,
                            )
                        }
                        if (item.dimAlpha > 0f) {
                            drawRect(
                                color = Color.Black.copy(alpha = item.dimAlpha),
                                size = cache.localSize,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Rect.overlapsPageFrostRect(other: Rect): Boolean =
    right > other.left &&
        bottom > other.top &&
        left < other.right &&
        top < other.bottom

internal fun Rect.isNearPageFrostViewport(viewport: Rect, margin: Float): Boolean =
    right >= viewport.left - margin &&
        bottom >= viewport.top - margin &&
        left <= viewport.right + margin &&
        top <= viewport.bottom + margin
