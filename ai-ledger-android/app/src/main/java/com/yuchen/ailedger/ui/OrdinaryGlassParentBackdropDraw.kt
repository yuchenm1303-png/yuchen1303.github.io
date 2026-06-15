package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.BackgroundTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun DrawScope.withOrdinaryParentTransform(
    node: OrdinaryGlassRenderNode,
    rect: Rect,
    block: DrawScope.() -> Unit
) {
    val transform = ordinaryGlassVisualTransform(node)
    withTransform({
        translate(rect.left, rect.top + transform.translationY)
    }) {
        withTransform({
            scale(
                scaleX = transform.scaleX,
                scaleY = transform.scaleY,
                pivot = Offset(rect.width * transform.origin.x, rect.height * transform.origin.y)
            )
        }) {
            block()
        }
    }
}

internal fun DrawScope.drawOrdinaryParentShadow(
    node: OrdinaryGlassRenderNode,
    rect: Rect
) {
    if (node.role == GlassRole.Shell || rect.width <= 1f || rect.height <= 1f) return
    withOrdinaryParentTransform(node, rect) {
        val w = rect.width.coerceAtLeast(1f)
        val h = rect.height.coerceAtLeast(1f)
        val shadowScale = ComposeGlassRuntimeDefaults.shadow * node.glassIntensity.coerceIn(0.25f, 1.45f)
        val ambientAlpha = (0.028f * shadowScale).coerceIn(0.004f, 0.080f)
        val spotAlpha = (0.0035f * shadowScale).coerceIn(0.001f, 0.014f)
        val elevationDp = when (node.role) {
            GlassRole.Card, GlassRole.Floating, GlassRole.Nav -> 4f
            GlassRole.Chip, GlassRole.Flex -> 3f
            GlassRole.Shell -> 0f
        }
        if (elevationDp <= 0f) return@withOrdinaryParentTransform

        val elevationPx = elevationDp.dp.toPx()
        val radiusPx = node.radius.dp.toPx()
        var index = 4
        while (index >= 1) {
            val fraction = index / 4f
            val spread = elevationPx * (0.45f + fraction * 0.85f)
            val yOffset = elevationPx * (0.22f + fraction * 0.42f)
            drawRoundRect(
                color = Color.Black.copy(alpha = ambientAlpha * (0.14f + 0.18f * fraction)),
                topLeft = Offset(-spread, yOffset - spread * 0.34f),
                size = Size(w + spread * 2f, h + spread * 2f),
                cornerRadius = CornerRadius(radiusPx + spread, radiusPx + spread),
                blendMode = BlendMode.SrcOver
            )
            index -= 1
        }
        drawRoundRect(
            color = Color.White.copy(alpha = spotAlpha),
            topLeft = Offset(0f, -0.35f.dp.toPx()),
            size = Size(w, h),
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            blendMode = BlendMode.Screen
        )
    }
}

internal fun DrawScope.drawOrdinaryParentBackdrop(
    node: OrdinaryGlassRenderNode,
    rect: Rect,
    backdrop: BlurredBackdropBitmap?,
    sampleOffset: Offset,
    spec: GlassBackdropSpec?
) {
    if (node.role == GlassRole.Shell || rect.width <= 1f || rect.height <= 1f) return
    val resolvedSpec = spec ?: return
    withOrdinaryParentTransform(node, rect) {
        val cache = ensureOrdinaryParentGeometry(node, rect)
        ensureOrdinaryParentBackdropBrushes(node, rect, resolvedSpec, cache)
        val localSize = cache.localSize
        clipPath(cache.shapePath) {
            if (backdrop != null) {
                drawOrdinaryParentBackdropImage(
                    backdrop = backdrop,
                    sampleOffset = sampleOffset,
                    itemSize = localSize,
                    alpha = cache.sampledBackdropAlpha
                )
            } else {
                drawRect(requireNotNull(cache.fallbackBase), size = localSize, blendMode = BlendMode.SrcOver)
                drawRect(requireNotNull(cache.fallbackGlowA), size = localSize, blendMode = BlendMode.Screen)
                drawRect(requireNotNull(cache.fallbackGlowB), size = localSize, blendMode = BlendMode.Screen)
            }
            if (cache.dimAlpha > 0.001f) {
                drawRect(
                    Color(0xFF020817).copy(alpha = cache.dimAlpha),
                    size = localSize,
                    blendMode = BlendMode.Multiply
                )
            }
            drawRect(requireNotNull(cache.primaryVeil), size = localSize, blendMode = BlendMode.SrcOver)
            drawRect(
                Color(0xFF72859A).copy(alpha = cache.backdropBaseAlpha * 0.16f * cache.backdropMilk),
                size = localSize,
                blendMode = BlendMode.SrcOver
            )
            drawRect(requireNotNull(cache.secondaryVeil), size = localSize, blendMode = BlendMode.SrcOver)
            drawRect(requireNotNull(cache.highlightVeil), size = localSize, blendMode = BlendMode.Screen)
        }
    }
}

private fun DrawScope.drawOrdinaryParentBackdropImage(
    backdrop: BlurredBackdropBitmap,
    sampleOffset: Offset,
    itemSize: Size,
    alpha: Float
) {
    val rootW = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
    val rootH = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)
    val localLeft = max(0f, -sampleOffset.x)
    val localTop = max(0f, -sampleOffset.y)
    val localRight = min(itemSize.width, rootW - sampleOffset.x)
    val localBottom = min(itemSize.height, rootH - sampleOffset.y)
    val visibleW = localRight - localLeft
    val visibleH = localBottom - localTop
    if (visibleW <= 0f || visibleH <= 0f) return

    val srcX = ((sampleOffset.x + localLeft) * backdrop.scale)
        .roundToInt().coerceIn(0, backdrop.image.width - 1)
    val srcY = ((sampleOffset.y + localTop) * backdrop.scale)
        .roundToInt().coerceIn(0, backdrop.image.height - 1)
    val srcW = (visibleW * backdrop.scale)
        .roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.width - srcX)
    val srcH = (visibleH * backdrop.scale)
        .roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.height - srcY)

    drawImage(
        image = backdrop.image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(localLeft.roundToInt(), localTop.roundToInt()),
        dstSize = IntSize(
            visibleW.roundToInt().coerceAtLeast(1),
            visibleH.roundToInt().coerceAtLeast(1)
        ),
        alpha = alpha,
        blendMode = BlendMode.SrcOver
    )
}

internal data class OrdinaryParentFallbackPalette(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val glowA: Color,
    val glowB: Color
)

internal fun ordinaryParentFallbackPalette(theme: BackgroundTheme): OrdinaryParentFallbackPalette = when (theme) {
    BackgroundTheme.Aurora -> OrdinaryParentFallbackPalette(
        Color(0xFF071426), Color(0xFF31446D), Color(0xFF8A6B65), Color(0xFFB79AFF), Color(0xFFFFA06E)
    )
    BackgroundTheme.Jade -> OrdinaryParentFallbackPalette(
        Color(0xFF071A22), Color(0xFF315B6D), Color(0xFF8A8266), Color(0xFF8EC2DD), Color(0xFF58C0BC)
    )
    BackgroundTheme.Sunset -> OrdinaryParentFallbackPalette(
        Color(0xFF20182D), Color(0xFF5D4774), Color(0xFFA87570), Color(0xFFC098FF), Color(0xFFFF9A64)
    )
    BackgroundTheme.Dawn -> OrdinaryParentFallbackPalette(
        Color(0xFF16253C), Color(0xFF708BAC), Color(0xFFC1A6A4), Color(0xFFE2CCFF), Color(0xFFFFC28A)
    )
}
