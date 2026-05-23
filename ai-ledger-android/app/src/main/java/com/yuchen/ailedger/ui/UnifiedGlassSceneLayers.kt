package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun UnifiedGlassSceneBackgroundLayer(modifier: Modifier = Modifier) {
    if (!GlassFeatureFlags.USE_UNIFIED_GLASS_BACKGROUND_LAYER) return

    val registry = LocalGlassSceneRegistry.current
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    val spec = LocalGlassBackdrop.current
    val view = LocalView.current
    val registryVersion = registry?.version ?: 0L

    Canvas(modifier = modifier) {
        registryVersion
        frameTicker?.frameNanos
        registry?.snapshot().orEmpty().forEach { node ->
            when {
                node.kind == GlassKind.Recessed && GlassFeatureFlags.USE_UNIFIED_RECESSED_GLASS -> {
                    drawUnifiedRecessedLegacy(node, cachedBackdrop, backdropOrigin)
                }
                node.kind == GlassKind.Chip && GlassFeatureFlags.USE_UNIFIED_CHIP_GLASS -> {
                    drawUnifiedChipLegacy(node, cachedBackdrop, backdropOrigin, spec, view.width, view.height)
                }
            }
        }
    }
}

@Composable
fun UnifiedGlassSceneForegroundLayer(modifier: Modifier = Modifier) {
    if (!GlassFeatureFlags.USE_UNIFIED_GLASS_FOREGROUND_LAYER) return

    val registry = LocalGlassSceneRegistry.current
    val frameTicker = LocalBackdropFrameTicker.current
    val registryVersion = registry?.version ?: 0L

    Canvas(modifier = modifier) {
        registryVersion
        frameTicker?.frameNanos
        registry?.snapshot()
    }
}

private fun DrawScope.drawUnifiedChipLegacy(
    node: GlassSceneNode,
    cachedBackdrop: BlurredBackdropBitmap?,
    backdropOrigin: BackdropCoordinateSource?,
    spec: GlassBackdropSpec?,
    viewWidth: Int,
    viewHeight: Int
) {
    if (!node.coordinates.isAttached()) return
    val itemSize = node.coordinates.itemSize()
    if (itemSize.width <= 0 || itemSize.height <= 0) return

    val topLeft = node.coordinates.rootOffset()
    val rect = Rect(topLeft, Size(itemSize.width.toFloat(), itemSize.height.toFloat()))
    if (rect.left >= size.width || rect.top >= size.height || rect.right <= 0f || rect.bottom <= 0f) return

    val radiusPx = node.radiusDp.dp.toPx()
    val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radiusPx, radiusPx))) }
    val quality = node.quality ?: spec?.quality ?: RenderQuality.Balanced
    val motionIntensity = spec?.motionIntensity ?: 1f
    val theme = spec?.theme ?: BackgroundTheme.Aurora
    val border = spec?.borderStyle ?: GlassBorderStyle()

    clipPath(path) {
        drawLegacySampledWeatherGlassBackdrop(
            node = node,
            rect = rect,
            cachedBackdrop = cachedBackdrop,
            backdropOrigin = backdropOrigin,
            quality = quality,
            theme = theme,
            viewWidth = viewWidth,
            viewHeight = viewHeight
        )
        drawLegacySampledWeatherEdgeRefraction(
            node = node,
            rect = rect,
            quality = quality,
            theme = theme,
            border = border,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            backdropOrigin = backdropOrigin
        )
        drawLegacyGlassSkin(
            node = node,
            rect = rect,
            radiusPx = radiusPx,
            quality = quality,
            motionIntensity = motionIntensity
        )
    }
}

private fun DrawScope.drawLegacySampledWeatherGlassBackdrop(
    node: GlassSceneNode,
    rect: Rect,
    cachedBackdrop: BlurredBackdropBitmap?,
    backdropOrigin: BackdropCoordinateSource?,
    quality: RenderQuality,
    theme: BackgroundTheme,
    viewWidth: Int,
    viewHeight: Int
) {
    val blurRadiusDp = 82
    val liftAlpha = 0.96f * node.intensity.coerceIn(0.70f, 1.25f)
    val alpha = liftAlpha.coerceIn(0.34f, 1.00f)
    val baseScrimAlpha = when (quality) {
        RenderQuality.Smooth -> 0.15f
        RenderQuality.Balanced -> 0.18f
        RenderQuality.Experimental -> 0.21f
    } * alpha
    val milkAlpha = when (quality) {
        RenderQuality.Smooth -> 0.040f
        RenderQuality.Balanced -> 0.052f
        RenderQuality.Experimental -> 0.064f
    } * alpha
    val highlightAlpha = when (quality) {
        RenderQuality.Smooth -> 0.036f
        RenderQuality.Balanced -> 0.046f
        RenderQuality.Experimental -> 0.056f
    } * alpha
    val backdropAlpha = when (quality) {
        RenderQuality.Smooth -> 0.90f
        RenderQuality.Balanced -> 0.94f
        RenderQuality.Experimental -> 0.98f
    }

    val sampleOffset = node.coordinates.offsetRelativeTo(backdropOrigin)
    if (cachedBackdrop != null) {
        val srcX = (sampleOffset.x * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.width - 1)
        val srcY = (sampleOffset.y * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.height - 1)
        val srcW = (rect.width * cachedBackdrop.scale).roundToInt().coerceAtLeast(1)
            .coerceAtMost(cachedBackdrop.image.width - srcX)
        val srcH = (rect.height * cachedBackdrop.scale).roundToInt().coerceAtLeast(1)
            .coerceAtMost(cachedBackdrop.image.height - srcY)
        drawImage(
            image = cachedBackdrop.image,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
            dstSize = IntSize(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)),
            alpha = backdropAlpha,
            blendMode = BlendMode.SrcOver
        )
    } else {
        val spreadPx = when (quality) {
            RenderQuality.Smooth -> blurRadiusDp * 0.46f
            RenderQuality.Balanced -> blurRadiusDp * 0.62f
            RenderQuality.Experimental -> blurRadiusDp * 0.76f
        }.coerceIn(34f, 96f)
        val rootW = if (viewWidth > 0) viewWidth.toFloat() else rect.width + sampleOffset.x
        val rootH = if (viewHeight > 0) viewHeight.toFloat() else rect.height + sampleOffset.y
        drawLegacyFallbackSpread(rect, rootW, rootH, theme, sampleOffset, spreadPx)
    }

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFE0EAF3).copy(alpha = milkAlpha * 0.48f),
                Color(0xFF9AADBF).copy(alpha = baseScrimAlpha * 0.28f),
                Color(0xFF40576D).copy(alpha = baseScrimAlpha * 0.30f)
            ),
            startY = rect.top,
            endY = rect.bottom
        ),
        topLeft = rect.topLeft,
        size = rect.size,
        blendMode = BlendMode.SrcOver
    )
    drawRect(
        color = Color(0xFF72859A).copy(alpha = baseScrimAlpha * 0.26f),
        topLeft = rect.topLeft,
        size = rect.size,
        blendMode = BlendMode.SrcOver
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = milkAlpha * 0.46f),
                Color(0xFFDCE5EF).copy(alpha = milkAlpha * 0.22f),
                Color(0xFF9BAEC1).copy(alpha = milkAlpha * 0.10f),
                Color(0xFF172333).copy(alpha = baseScrimAlpha * 0.14f)
            ),
            startY = rect.top,
            endY = rect.bottom
        ),
        topLeft = rect.topLeft,
        size = rect.size,
        blendMode = BlendMode.SrcOver
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = highlightAlpha * 0.42f),
                Color.White.copy(alpha = highlightAlpha * 0.08f),
                Color.Transparent
            ),
            center = rect.topLeft + Offset(rect.width * 0.42f, rect.height * 0.08f),
            radius = rect.width * 0.98f
        ),
        topLeft = rect.topLeft,
        size = rect.size,
        blendMode = BlendMode.Screen
    )
}

private fun DrawScope.drawLegacySampledWeatherEdgeRefraction(
    node: GlassSceneNode,
    rect: Rect,
    quality: RenderQuality,
    theme: BackgroundTheme,
    border: GlassBorderStyle,
    viewWidth: Int,
    viewHeight: Int,
    backdropOrigin: BackdropCoordinateSource?
) {
    quality
    val alpha = 0.22f.coerceIn(0f, 0.34f)
    val sampleOffset = node.coordinates.offsetRelativeTo(backdropOrigin)
    val w = rect.width
    val h = rect.height
    val corner = node.radiusDp.dp.toPx()
    val outerInset = 0.55.dp.toPx()
    val midInset = 2.70.dp.toPx()
    val innerInset = 7.0.dp.toPx()
    val cornerRadius = CornerRadius(corner, corner)

    val rootW = if (viewWidth > 0) viewWidth.toFloat() else max(w + sampleOffset.x, w)
    val rootH = if (viewHeight > 0) viewHeight.toFloat() else max(h + sampleOffset.y, h)
    val dominantLight = dominantBackdropLightLegacy(theme, rootW, rootH)
    val localLight = Offset(dominantLight.x - sampleOffset.x, dominantLight.y - sampleOffset.y)
    val nearestX = localLight.x.coerceIn(0f, w)
    val nearestY = localLight.y.coerceIn(0f, h)
    val dx = localLight.x - nearestX
    val dy = localLight.y - nearestY
    val lightDistance = sqrt(dx * dx + dy * dy)
    val edgeInfluence = 1f - smoothStepLegacy(0f, max(w, h) * 0.62f, lightDistance)
    val verticalAffinity = 1f - smoothStepLegacy(0f, h * 1.15f, abs(localLight.y - h * 0.5f))
    val horizontalAffinity = 1f - smoothStepLegacy(0f, w * 1.05f, abs(localLight.x - w * 0.5f))
    val lightBoost = (edgeInfluence * (0.48f + 0.32f * verticalAffinity + 0.20f * horizontalAffinity)).coerceIn(0f, 1f)
    val lightPhase = (localLight.x / max(w, 1f)).coerceIn(-0.35f, 1.35f)
    val topLeft = rect.topLeft

    val broadLens = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = (0.032f + 0.035f * lightBoost) * alpha),
            Color.White.copy(alpha = (0.012f + 0.018f * lightBoost) * alpha),
            Color.Transparent,
            Color.Black.copy(alpha = 0.010f * alpha),
            Color.White.copy(alpha = (0.006f + 0.014f * lightBoost) * alpha)
        ),
        start = topLeft + Offset(w * (lightPhase - 0.42f), 0f),
        end = topLeft + Offset(w * (lightPhase + 0.64f), h)
    )
    val topPrism = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = (0.040f + 0.040f * lightBoost) * alpha),
            Color.White.copy(alpha = 0.018f * alpha),
            Color.Transparent
        ),
        startY = rect.top,
        endY = rect.top + h * 0.30f
    )
    val sideCompression = Brush.horizontalGradient(
        colors = listOf(
            Color.White.copy(alpha = (0.016f + 0.020f * lightBoost) * alpha),
            Color.Transparent,
            Color.Transparent,
            Color.Black.copy(alpha = 0.010f * alpha),
            Color.White.copy(alpha = (0.010f + 0.022f * lightBoost) * alpha)
        ),
        startX = rect.left,
        endX = rect.right
    )
    val edgeLight = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = border.topHighlightAlpha * 0.22f * lightBoost),
            Color.White.copy(alpha = border.topHighlightAlpha * 0.050f * lightBoost),
            Color.Transparent
        ),
        center = topLeft + localLight,
        radius = max(w, h) * 0.58f
    )
    val innerDarkBend = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.004f * alpha),
            Color.Black.copy(alpha = 0.018f * alpha)
        ),
        startY = rect.top + h * 0.48f,
        endY = rect.bottom
    )

    drawRoundRect(broadLens, topLeft + Offset(outerInset, outerInset), Size(w - outerInset * 2f, h - outerInset * 2f), cornerRadius, style = Stroke(width = 8.5.dp.toPx()), blendMode = BlendMode.Screen)
    drawRoundRect(topPrism, topLeft + Offset(midInset, midInset), Size(w - midInset * 2f, h - midInset * 2f), cornerRadius, style = Stroke(width = 5.6.dp.toPx()), blendMode = BlendMode.Screen)
    drawRoundRect(sideCompression, topLeft + Offset(midInset, midInset), Size(w - midInset * 2f, h - midInset * 2f), cornerRadius, style = Stroke(width = 4.8.dp.toPx()), blendMode = BlendMode.Screen)
    drawRoundRect(innerDarkBend, topLeft + Offset(innerInset, innerInset), Size(w - innerInset * 2f, h - innerInset * 2f), cornerRadius, style = Stroke(width = 2.4.dp.toPx()), blendMode = BlendMode.Multiply)
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = border.outerStrokeAlpha),
                Color.White.copy(alpha = border.outerStrokeAlpha * 0.34f),
                Color.White.copy(alpha = border.outerStrokeAlpha * 0.12f)
            ),
            startY = rect.top,
            endY = rect.bottom
        ),
        topLeft = topLeft + Offset(outerInset, outerInset),
        size = Size(w - outerInset * 2f, h - outerInset * 2f),
        cornerRadius = cornerRadius,
        style = Stroke(width = 1.15.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = border.innerStrokeAlpha), Color.Transparent, Color.White.copy(alpha = border.innerStrokeAlpha * 0.28f)),
            startY = rect.top,
            endY = rect.bottom
        ),
        topLeft = topLeft + Offset(midInset, midInset),
        size = Size(w - midInset * 2f, h - midInset * 2f),
        cornerRadius = cornerRadius,
        style = Stroke(width = 0.82.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(edgeLight, topLeft + Offset(outerInset, outerInset), Size(w - outerInset * 2f, h - outerInset * 2f), cornerRadius, style = Stroke(width = 1.20.dp.toPx()), blendMode = BlendMode.Screen)
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = border.bottomShadowAlpha)),
            startY = rect.top + h * 0.52f,
            endY = rect.bottom
        ),
        topLeft = topLeft + Offset(midInset, midInset),
        size = Size(w - midInset * 2f, h - midInset * 2f),
        cornerRadius = cornerRadius,
        style = Stroke(width = 1.1.dp.toPx()),
        blendMode = BlendMode.Multiply
    )
}

private fun DrawScope.drawLegacyGlassSkin(
    node: GlassSceneNode,
    rect: Rect,
    radiusPx: Float,
    quality: RenderQuality,
    motionIntensity: Float
) {
    val material = glassMaterialLegacy(node.intensity)
    val breathe = if (quality.enableMotion && motionIntensity > 0.02f) 0.38f else 0.34f
    val shimmer = (if (quality.enableMotion && motionIntensity > 0.02f) 0.20f else 0.16f) + if (node.pressed) 0.024f else 0f
    val pulse = 0.92f + breathe * 0.045f
    val safeShimmer = shimmer - shimmer.toInt()
    val w = rect.width
    val h = rect.height
    val rimInset = 0.62.dp.toPx()
    val innerInset = 1.85.dp.toPx()
    val cornerRadius = CornerRadius(radiusPx, radiusPx)
    val rimSize = Size(w - rimInset * 2f, h - rimInset * 2f)
    val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)

    val veil = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = material.frost * 0.28f * pulse),
            Color.White.copy(alpha = material.frost * 0.10f),
            Color.Transparent,
            Color.Black.copy(alpha = material.depthShadow * 0.12f)
        ),
        startY = rect.top,
        endY = rect.bottom
    )
    val rim = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = material.rim * 0.58f),
            Color.White.copy(alpha = material.rim * 0.05f),
            Color.Transparent,
            Color.Black.copy(alpha = material.depthShadow * 0.14f)
        ),
        start = rect.topLeft,
        end = rect.bottomRight
    )
    val glint = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = material.motionGlint),
            Color.Transparent
        ),
        start = rect.topLeft + Offset(w * (safeShimmer - 0.28f), 0f),
        end = rect.topLeft + Offset(w * (safeShimmer + 0.16f), h * 0.20f)
    )

    drawRect(veil, topLeft = rect.topLeft, size = rect.size, blendMode = BlendMode.Screen)
    drawRoundRect(rim, topLeft = rect.topLeft + Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(width = 0.32.dp.toPx()), blendMode = BlendMode.Screen)
    drawRoundRect(
        color = Color.White.copy(alpha = material.rim * 0.10f),
        topLeft = rect.topLeft + Offset(innerInset, innerInset),
        size = innerSize,
        cornerRadius = cornerRadius,
        style = Stroke(width = 0.10.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    if (quality.enableMotion) {
        drawRoundRect(glint, topLeft = rect.topLeft + Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(width = 0.07.dp.toPx()), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.drawUnifiedRecessedLegacy(
    node: GlassSceneNode,
    cachedBackdrop: BlurredBackdropBitmap?,
    backdropOrigin: BackdropCoordinateSource?
) {
    if (!node.coordinates.isAttached()) return
    val outerSize = node.coordinates.itemSize()
    if (outerSize.width <= 0 || outerSize.height <= 0) return

    val outerTopLeft = node.coordinates.rootOffset()
    val outerWidth = outerSize.width.toFloat()
    val outerHeight = outerSize.height.toFloat()
    if (outerTopLeft.x >= size.width || outerTopLeft.y >= size.height || outerTopLeft.x + outerWidth <= 0f || outerTopLeft.y + outerHeight <= 0f) return

    val depth = node.depth.coerceIn(0f, 1f)
    val radiusPx = node.radiusDp.dp.toPx()
    val floorInsetPx = node.floorInsetDp.dp.toPx()
    val floorRadiusPx = (node.radiusDp - 1.2f).coerceAtLeast(5f).dp.toPx()
    val floorTopLeft = outerTopLeft + Offset(floorInsetPx, floorInsetPx)
    val floorSize = Size((outerWidth - floorInsetPx * 2f).coerceAtLeast(1f), (outerHeight - floorInsetPx * 2f).coerceAtLeast(1f))
    val floorRect = Rect(floorTopLeft, floorSize)
    val floorPath = Path().apply { addRoundRect(RoundRect(floorRect, CornerRadius(floorRadiusPx, floorRadiusPx))) }

    drawOuterInsetDepthLegacy(outerTopLeft, Size(outerWidth, outerHeight), radiusPx, depth, node.innerShadowAlpha)
    clipPath(floorPath) {
        drawUnifiedBackdropCrop(
            floorRect = floorRect,
            floorCoordinates = node.secondaryCoordinates,
            fallbackCoordinates = node.coordinates,
            backdropAlpha = node.floorAlpha.coerceIn(0f, 1f),
            cachedBackdrop = cachedBackdrop,
            backdropOrigin = backdropOrigin
        )
        drawRect(color = Color.Black.copy(alpha = (node.floorDimAlpha + depth * 0.06f).coerceIn(0f, 0.75f)), topLeft = floorTopLeft, size = floorSize)
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = node.innerShadowAlpha * (0.12f + depth * 0.14f)),
                    Color.Transparent,
                    Color.White.copy(alpha = node.rimAlpha * 0.035f)
                ),
                startY = floorTopLeft.y,
                endY = floorTopLeft.y + floorSize.height
            ),
            topLeft = floorTopLeft,
            size = floorSize
        )
    }
    drawDynamicInsetRimHighlightLegacy(node, outerTopLeft, Size(outerWidth, outerHeight), radiusPx, cachedBackdrop, backdropOrigin)
    drawInsetStrokesLegacy(node, outerTopLeft, Size(outerWidth, outerHeight), radiusPx, floorTopLeft, floorSize, floorRadiusPx, depth)
}

private fun DrawScope.drawOuterInsetDepthLegacy(topLeft: Offset, slotSize: Size, radiusPx: Float, depth: Float, innerShadowAlpha: Float) {
    val shadow = (0.30f + depth * 0.70f) * innerShadowAlpha
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = shadow * 0.72f),
                Color(0xFF070C29).copy(alpha = 0.28f + depth * 0.12f),
                Color.Black.copy(alpha = shadow * 0.18f)
            ),
            startY = topLeft.y,
            endY = topLeft.y + slotSize.height
        ),
        topLeft = topLeft,
        size = slotSize,
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        blendMode = BlendMode.Multiply
    )
}

private fun DrawScope.drawUnifiedBackdropCrop(
    floorRect: Rect,
    floorCoordinates: GlassCoordinateSource?,
    fallbackCoordinates: GlassCoordinateSource,
    backdropAlpha: Float,
    cachedBackdrop: BlurredBackdropBitmap?,
    backdropOrigin: BackdropCoordinateSource?
) {
    val floorSize = Size(floorRect.width, floorRect.height)
    if (cachedBackdrop != null) {
        val sampleOffset = when {
            floorCoordinates?.isAttached() == true -> floorCoordinates.offsetRelativeTo(backdropOrigin)
            fallbackCoordinates.isAttached() -> fallbackCoordinates.offsetRelativeTo(backdropOrigin) + Offset(floorRect.left - fallbackCoordinates.rootOffset().x, floorRect.top - fallbackCoordinates.rootOffset().y)
            else -> Offset(floorRect.left, floorRect.top) - (backdropOrigin?.rootOffset() ?: Offset.Zero)
        }
        val srcX = (sampleOffset.x * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.width - 1)
        val srcY = (sampleOffset.y * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.height - 1)
        val srcW = (floorSize.width * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(cachedBackdrop.image.width - srcX)
        val srcH = (floorSize.height * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(cachedBackdrop.image.height - srcY)
        drawImage(
            image = cachedBackdrop.image,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(floorRect.left.roundToInt(), floorRect.top.roundToInt()),
            dstSize = IntSize(floorSize.width.roundToInt().coerceAtLeast(1), floorSize.height.roundToInt().coerceAtLeast(1)),
            alpha = backdropAlpha,
            blendMode = BlendMode.SrcOver
        )
    } else {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF1A2B58), Color(0xFF5B4A8E), Color(0xFFB85D78)),
                startY = floorRect.top,
                endY = floorRect.bottom
            ),
            topLeft = Offset(floorRect.left, floorRect.top),
            size = floorSize
        )
    }
}

private fun DrawScope.drawDynamicInsetRimHighlightLegacy(
    node: GlassSceneNode,
    topLeft: Offset,
    slotSize: Size,
    radiusPx: Float,
    cachedBackdrop: BlurredBackdropBitmap?,
    backdropOrigin: BackdropCoordinateSource?
) {
    val image = cachedBackdrop?.image ?: return
    val depth = node.depth.coerceIn(0f, 1f)
    val alpha = node.rimAlpha * (0.42f + depth * 0.20f)
    if (alpha <= 0.001f) return
    val sampleOffset = node.coordinates.offsetRelativeTo(backdropOrigin)
    val srcX = (sampleOffset.x * cachedBackdrop.scale).roundToInt().coerceIn(0, image.width - 1)
    val srcY = (sampleOffset.y * cachedBackdrop.scale).roundToInt().coerceIn(0, image.height - 1)
    val srcW = (slotSize.width * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(image.width - srcX)
    val srcH = (slotSize.height * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(image.height - srcY)
    val strokePx = 1.20f.dp.toPx()
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(topLeft, slotSize), Paint())
        drawImage(
            image = image,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize(slotSize.width.roundToInt().coerceAtLeast(1), slotSize.height.roundToInt().coerceAtLeast(1)),
            alpha = alpha.coerceIn(0f, 1f),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            color = Color.White,
            topLeft = topLeft + Offset(strokePx * 0.50f, strokePx * 0.50f),
            size = Size(slotSize.width - strokePx, slotSize.height - strokePx),
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = strokePx),
            blendMode = BlendMode.DstIn
        )
        canvas.restore()
    }
}

private fun DrawScope.drawInsetStrokesLegacy(
    node: GlassSceneNode,
    outerTopLeft: Offset,
    outerSize: Size,
    radiusPx: Float,
    floorTopLeft: Offset,
    floorSize: Size,
    floorRadiusPx: Float,
    depth: Float
) {
    val shadowWidth = (1.2f + depth * 3.8f).dp.toPx()
    val innerShadowAlpha = node.innerShadowAlpha
    val rimHighlightAlpha = node.rimAlpha
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = innerShadowAlpha * (0.58f + depth * 0.36f)),
                Color.Black.copy(alpha = innerShadowAlpha * (0.16f + depth * 0.16f)),
                Color.Transparent
            ),
            startY = floorTopLeft.y,
            endY = floorTopLeft.y + floorSize.height
        ),
        topLeft = floorTopLeft,
        size = floorSize,
        cornerRadius = CornerRadius(floorRadiusPx, floorRadiusPx),
        style = Stroke(width = shadowWidth),
        blendMode = BlendMode.Multiply
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = rimHighlightAlpha * 0.28f),
                Color.White.copy(alpha = rimHighlightAlpha * 0.08f),
                Color.Transparent,
                Color.Black.copy(alpha = innerShadowAlpha * 0.14f)
            ),
            start = outerTopLeft,
            end = outerTopLeft + Offset(outerSize.width, outerSize.height)
        ),
        topLeft = outerTopLeft + Offset(0.65.dp.toPx(), 0.65.dp.toPx()),
        size = Size(outerSize.width - 1.3.dp.toPx(), outerSize.height - 1.3.dp.toPx()),
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        style = Stroke(width = 0.72.dp.toPx()),
        blendMode = BlendMode.Screen
    )
}

private fun DrawScope.drawLegacyFallbackSpread(rect: Rect, rootW: Float, rootH: Float, theme: BackgroundTheme, sampleOffset: Offset, spreadPx: Float) {
    rootW
    rootH
    theme
    sampleOffset
    spreadPx
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF1A2B58), Color(0xFF5B4A8E), Color(0xFFB85D78)),
            startY = rect.top,
            endY = rect.bottom
        ),
        topLeft = rect.topLeft,
        size = rect.size
    )
}

private data class GlassMaterialLegacy(
    val frost: Float,
    val rim: Float,
    val topHighlight: Float,
    val cornerHighlight: Float,
    val motionGlint: Float,
    val depthShadow: Float,
    val shadowAmbient: Float,
    val shadowSpot: Float
)

private fun glassMaterialLegacy(intensity: Float): GlassMaterialLegacy {
    val safeIntensity = intensity.coerceIn(0.25f, 1.45f)
    val base = GlassMaterialLegacy(
        frost = 0.044f,
        rim = 0.104f,
        topHighlight = 0.036f,
        cornerHighlight = 0.021f,
        motionGlint = 0.0035f,
        depthShadow = 0.015f,
        shadowAmbient = 0.028f,
        shadowSpot = 0.0035f
    )
    return GlassMaterialLegacy(
        frost = (base.frost * safeIntensity).coerceIn(0.006f, 0.052f),
        rim = (base.rim * safeIntensity).coerceIn(0.018f, 0.154f),
        topHighlight = (base.topHighlight * safeIntensity).coerceIn(0.002f, 0.048f),
        cornerHighlight = (base.cornerHighlight * safeIntensity).coerceIn(0.002f, 0.038f),
        motionGlint = (base.motionGlint * safeIntensity).coerceIn(0.001f, 0.008f),
        depthShadow = (base.depthShadow * safeIntensity).coerceIn(0.002f, 0.027f),
        shadowAmbient = (base.shadowAmbient * safeIntensity).coerceIn(0.004f, 0.046f),
        shadowSpot = (base.shadowSpot * safeIntensity).coerceIn(0.001f, 0.008f)
    )
}

private fun smoothStepLegacy(edge0: Float, edge1: Float, value: Float): Float {
    val span = (edge1 - edge0).takeIf { abs(it) > 0.0001f } ?: return 0f
    val t = ((value - edge0) / span).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun dominantBackdropLightLegacy(theme: BackgroundTheme, rootW: Float, rootH: Float): Offset {
    val x = when (theme) {
        BackgroundTheme.Aurora -> 0.80f
        BackgroundTheme.Jade -> 0.76f
        BackgroundTheme.Sunset -> 0.82f
        BackgroundTheme.Dawn -> 0.78f
    }
    val y = when (theme) {
        BackgroundTheme.Aurora -> 0.30f
        BackgroundTheme.Jade -> 0.34f
        BackgroundTheme.Sunset -> 0.31f
        BackgroundTheme.Dawn -> 0.35f
    }
    return Offset(rootW * x, rootH * y)
}
