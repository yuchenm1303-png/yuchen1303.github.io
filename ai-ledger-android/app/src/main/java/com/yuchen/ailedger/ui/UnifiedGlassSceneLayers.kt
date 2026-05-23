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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.roundToInt

@Composable
fun UnifiedGlassSceneBackgroundLayer(
    modifier: Modifier = Modifier,
    hostKey: Any? = null,
    layerCoordinateSource: GlassCoordinateSource? = null
) {
    if (!GlassFeatureFlags.USE_UNIFIED_GLASS_BACKGROUND_LAYER) return

    val registry = LocalGlassSceneRegistry.current
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    val spec = LocalGlassBackdrop.current
    val registryVersion = registry?.version ?: 0L

    Canvas(modifier = modifier) {
        registryVersion
        frameTicker?.frameNanos
        val layerRootOffset = layerCoordinateSource?.rootOffset() ?: Offset.Zero
        registry?.snapshot().orEmpty()
            .asSequence()
            .filter { it.hostKey == hostKey }
            .forEach { node ->
                when {
                    node.kind == GlassKind.Recessed && GlassFeatureFlags.USE_UNIFIED_RECESSED_GLASS -> {
                        drawUnifiedRecessedLegacy(node, layerRootOffset, cachedBackdrop, backdropOrigin)
                    }
                    node.kind == GlassKind.Chip && GlassFeatureFlags.USE_UNIFIED_CHIP_GLASS -> {
                        drawUnifiedChipLegacy(node, layerRootOffset, cachedBackdrop, backdropOrigin, spec)
                    }
                }
            }
    }
}

@Composable
fun UnifiedGlassSceneForegroundLayer(
    modifier: Modifier = Modifier,
    hostKey: Any? = null,
    layerCoordinateSource: GlassCoordinateSource? = null
) {
    if (!GlassFeatureFlags.USE_UNIFIED_GLASS_FOREGROUND_LAYER) return
    val registry = LocalGlassSceneRegistry.current
    val frameTicker = LocalBackdropFrameTicker.current
    val registryVersion = registry?.version ?: 0L
    Canvas(modifier = modifier) {
        registryVersion
        frameTicker?.frameNanos
        layerCoordinateSource?.rootOffset()
        registry?.version
    }
}

private fun DrawScope.drawUnifiedChipLegacy(
    node: GlassSceneNode,
    layerRootOffset: Offset,
    cachedBackdrop: BlurredBackdropBitmap?,
    backdropOrigin: BackdropCoordinateSource?,
    spec: GlassBackdropSpec?
) {
    if (!node.coordinates.isAttached()) return
    val itemSize = node.coordinates.itemSize()
    if (itemSize.width <= 0 || itemSize.height <= 0) return

    val topLeft = node.coordinates.rootOffset() - layerRootOffset
    val rect = Rect(topLeft, Size(itemSize.width.toFloat(), itemSize.height.toFloat()))
    if (rect.left >= size.width || rect.top >= size.height || rect.right <= 0f || rect.bottom <= 0f) return

    val radiusPx = node.radiusDp.dp.toPx()
    val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radiusPx, radiusPx))) }
    val quality = node.quality ?: spec?.quality ?: RenderQuality.Balanced
    val motionIntensity = spec?.motionIntensity ?: 1f

    clipPath(path) {
        drawBackdropCrop(rect, node.coordinates, cachedBackdrop, backdropOrigin, chipBackdropAlpha(quality))
        drawLegacyChipVeil(rect, quality, node.intensity)
        drawLegacyChipRim(rect, radiusPx, quality, motionIntensity, node.intensity, node.pressed)
    }
}

private fun DrawScope.drawLegacyChipVeil(rect: Rect, quality: RenderQuality, intensity: Float) {
    val alpha = (0.96f * intensity.coerceIn(0.70f, 1.25f)).coerceIn(0.34f, 1f)
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

private fun DrawScope.drawLegacyChipRim(rect: Rect, radiusPx: Float, quality: RenderQuality, motionIntensity: Float, intensity: Float, pressed: Boolean) {
    val material = glassMaterialLegacy(intensity)
    val breathe = if (quality.enableMotion && motionIntensity > 0.02f) 0.38f else 0.34f
    val shimmer = (if (quality.enableMotion && motionIntensity > 0.02f) 0.20f else 0.16f) + if (pressed) 0.024f else 0f
    val pulse = 0.92f + breathe * 0.045f
    val safeShimmer = shimmer - shimmer.toInt()
    val rimInset = 0.62.dp.toPx()
    val innerInset = 1.85.dp.toPx()
    val rimSize = Size(rect.width - rimInset * 2f, rect.height - rimInset * 2f)
    val innerSize = Size(rect.width - innerInset * 2f, rect.height - innerInset * 2f)
    val cornerRadius = CornerRadius(radiusPx, radiusPx)

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = material.frost * 0.28f * pulse), Color.White.copy(alpha = material.frost * 0.10f), Color.Transparent, Color.Black.copy(alpha = material.depthShadow * 0.12f)),
            startY = rect.top,
            endY = rect.bottom
        ),
        topLeft = rect.topLeft,
        size = rect.size,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = material.rim * 0.58f), Color.White.copy(alpha = material.rim * 0.05f), Color.Transparent, Color.Black.copy(alpha = material.depthShadow * 0.14f)),
            start = rect.topLeft,
            end = rect.bottomRight
        ),
        topLeft = rect.topLeft + Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(width = 0.32.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(color = Color.White.copy(alpha = material.rim * 0.10f), topLeft = rect.topLeft + Offset(innerInset, innerInset), size = innerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.10.dp.toPx()), blendMode = BlendMode.Screen)
    if (quality.enableMotion) {
        drawRoundRect(
            brush = Brush.linearGradient(colors = listOf(Color.Transparent, Color.White.copy(alpha = material.motionGlint), Color.Transparent), start = rect.topLeft + Offset(rect.width * (safeShimmer - 0.28f), 0f), end = rect.topLeft + Offset(rect.width * (safeShimmer + 0.16f), rect.height * 0.20f)),
            topLeft = rect.topLeft + Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = 0.07.dp.toPx()),
            blendMode = BlendMode.Plus
        )
    }
}

private fun DrawScope.drawUnifiedRecessedLegacy(node: GlassSceneNode, layerRootOffset: Offset, cachedBackdrop: BlurredBackdropBitmap?, backdropOrigin: BackdropCoordinateSource?) {
    if (!node.coordinates.isAttached()) return
    val outerSize = node.coordinates.itemSize()
    if (outerSize.width <= 0 || outerSize.height <= 0) return

    val outerTopLeft = node.coordinates.rootOffset() - layerRootOffset
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
        drawBackdropCrop(rect = floorRect, coordinates = node.secondaryCoordinates ?: node.coordinates, cachedBackdrop = cachedBackdrop, backdropOrigin = backdropOrigin, alpha = node.floorAlpha.coerceIn(0f, 1f))
        drawRect(color = Color.Black.copy(alpha = (node.floorDimAlpha + depth * 0.06f).coerceIn(0f, 0.75f)), topLeft = floorTopLeft, size = floorSize)
        drawRect(brush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = node.innerShadowAlpha * (0.12f + depth * 0.14f)), Color.Transparent, Color.White.copy(alpha = node.rimAlpha * 0.035f)), startY = floorTopLeft.y, endY = floorTopLeft.y + floorSize.height), topLeft = floorTopLeft, size = floorSize)
    }
    drawDynamicInsetRimHighlightLegacy(node, outerTopLeft, Size(outerWidth, outerHeight), radiusPx, cachedBackdrop, backdropOrigin)
    drawInsetStrokesLegacy(node, outerTopLeft, Size(outerWidth, outerHeight), radiusPx, floorTopLeft, floorSize, floorRadiusPx, depth)
}

private fun DrawScope.drawBackdropCrop(rect: Rect, coordinates: GlassCoordinateSource, cachedBackdrop: BlurredBackdropBitmap?, backdropOrigin: BackdropCoordinateSource?, alpha: Float) {
    if (cachedBackdrop != null) {
        val sampleOffset = coordinates.offsetRelativeTo(backdropOrigin)
        val srcX = (sampleOffset.x * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.width - 1)
        val srcY = (sampleOffset.y * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.height - 1)
        val srcW = (rect.width * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(cachedBackdrop.image.width - srcX)
        val srcH = (rect.height * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(cachedBackdrop.image.height - srcY)
        drawImage(image = cachedBackdrop.image, srcOffset = IntOffset(srcX, srcY), srcSize = IntSize(srcW, srcH), dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()), dstSize = IntSize(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)), alpha = alpha, blendMode = BlendMode.SrcOver)
    } else {
        drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF1A2B58), Color(0xFF5B4A8E), Color(0xFFB85D78)), startY = rect.top, endY = rect.bottom), topLeft = rect.topLeft, size = rect.size)
    }
}

private fun chipBackdropAlpha(quality: RenderQuality): Float = when (quality) {
    RenderQuality.Smooth -> 0.90f
    RenderQuality.Balanced -> 0.94f
    RenderQuality.Experimental -> 0.98f
}

private fun DrawScope.drawOuterInsetDepthLegacy(topLeft: Offset, slotSize: Size, radiusPx: Float, depth: Float, innerShadowAlpha: Float) {
    val shadow = (0.30f + depth * 0.70f) * innerShadowAlpha
    drawRoundRect(brush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = shadow * 0.72f), Color(0xFF070C29).copy(alpha = 0.28f + depth * 0.12f), Color.Black.copy(alpha = shadow * 0.18f)), startY = topLeft.y, endY = topLeft.y + slotSize.height), topLeft = topLeft, size = slotSize, cornerRadius = CornerRadius(radiusPx, radiusPx), blendMode = BlendMode.Multiply)
}

private fun DrawScope.drawDynamicInsetRimHighlightLegacy(node: GlassSceneNode, topLeft: Offset, slotSize: Size, radiusPx: Float, cachedBackdrop: BlurredBackdropBitmap?, backdropOrigin: BackdropCoordinateSource?) {
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
        drawImage(image = image, srcOffset = IntOffset(srcX, srcY), srcSize = IntSize(srcW, srcH), dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()), dstSize = IntSize(slotSize.width.roundToInt().coerceAtLeast(1), slotSize.height.roundToInt().coerceAtLeast(1)), alpha = alpha.coerceIn(0f, 1f), blendMode = BlendMode.Screen)
        drawRoundRect(color = Color.White, topLeft = topLeft + Offset(strokePx * 0.50f, strokePx * 0.50f), size = Size(slotSize.width - strokePx, slotSize.height - strokePx), cornerRadius = CornerRadius(radiusPx, radiusPx), style = Stroke(width = strokePx), blendMode = BlendMode.DstIn)
        canvas.restore()
    }
}

private fun DrawScope.drawInsetStrokesLegacy(node: GlassSceneNode, outerTopLeft: Offset, outerSize: Size, radiusPx: Float, floorTopLeft: Offset, floorSize: Size, floorRadiusPx: Float, depth: Float) {
    val shadowWidth = (1.2f + depth * 3.8f).dp.toPx()
    drawRoundRect(brush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = node.innerShadowAlpha * (0.58f + depth * 0.36f)), Color.Black.copy(alpha = node.innerShadowAlpha * (0.16f + depth * 0.16f)), Color.Transparent), startY = floorTopLeft.y, endY = floorTopLeft.y + floorSize.height), topLeft = floorTopLeft, size = floorSize, cornerRadius = CornerRadius(floorRadiusPx, floorRadiusPx), style = Stroke(width = shadowWidth), blendMode = BlendMode.Multiply)
    drawRoundRect(brush = Brush.linearGradient(listOf(Color.White.copy(alpha = node.rimAlpha * 0.28f), Color.White.copy(alpha = node.rimAlpha * 0.08f), Color.Transparent, Color.Black.copy(alpha = node.innerShadowAlpha * 0.14f)), start = outerTopLeft, end = outerTopLeft + Offset(outerSize.width, outerSize.height)), topLeft = outerTopLeft + Offset(0.65.dp.toPx(), 0.65.dp.toPx()), size = Size(outerSize.width - 1.3.dp.toPx(), outerSize.height - 1.3.dp.toPx()), cornerRadius = CornerRadius(radiusPx, radiusPx), style = Stroke(width = 0.72.dp.toPx()), blendMode = BlendMode.Screen)
}

private data class GlassMaterialLegacy(val frost: Float, val rim: Float, val motionGlint: Float, val depthShadow: Float)

private fun glassMaterialLegacy(intensity: Float): GlassMaterialLegacy {
    val safeIntensity = intensity.coerceIn(0.25f, 1.45f)
    return GlassMaterialLegacy(
        frost = (0.044f * safeIntensity).coerceIn(0.006f, 0.052f),
        rim = (0.104f * safeIntensity).coerceIn(0.018f, 0.154f),
        motionGlint = (0.0035f * safeIntensity).coerceIn(0.001f, 0.008f),
        depthShadow = (0.015f * safeIntensity).coerceIn(0.002f, 0.027f)
    )
}
