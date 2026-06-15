package com.yuchen.ailedger.ui

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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 普通 Compose 玻璃父级背景采样与外部阴影。
 *
 * 数值与 SampledWeatherGlassBackdrop / glassOuterFrame 保持同一来源，
 * 仅在 OrdinaryGlassRenderMode.ParentDraw 下由页面级 Underlay 调用。
 */
internal fun DrawScope.drawOrdinaryComposeGlassBackdrop(
    node: OrdinaryGlassRenderNode,
    rect: Rect,
    backdrop: BlurredBackdropBitmap?,
    sampleOffset: Offset,
    spec: GlassBackdropSpec?
) {
    if (node.role == GlassRole.Shell || rect.width <= 1f || rect.height <= 1f) return

    withOrdinaryBackdropTransform(node, rect) {
        val w = rect.width.coerceAtLeast(1f)
        val h = rect.height.coerceAtLeast(1f)
        val radiusPx = node.radius.dp.toPx()
        val shapePath = Path().apply {
            addRoundRect(RoundRect(0f, 0f, w, h, radiusPx, radiusPx))
        }
        val params = spec?.params ?: BackdropDebugParams()
        val theme = spec?.theme ?: BackgroundTheme.Aurora
        val glass = ComposeGlassLabState.style
        val alpha = node.backdropAlpha.coerceIn(0.12f, 1.55f)
        val dim = glass.backdropDim.coerceIn(0f, 1.80f)
        val milk = glass.backdropMilk.coerceIn(0f, 1.80f)
        val highlight = glass.backdropHighlight.coerceIn(0f, 1.80f)
        val baseAlpha = when (node.quality) {
            RenderQuality.Smooth -> 0.15f
            RenderQuality.Balanced -> 0.18f
            RenderQuality.Experimental -> 0.21f
        } * alpha
        val milkAlpha = when (node.quality) {
            RenderQuality.Smooth -> 0.040f
            RenderQuality.Balanced -> 0.052f
            RenderQuality.Experimental -> 0.064f
        } * alpha * milk
        val highlightAlpha = when (node.quality) {
            RenderQuality.Smooth -> 0.036f
            RenderQuality.Balanced -> 0.046f
            RenderQuality.Experimental -> 0.056f
        } * alpha * highlight
        val sampledBackdropAlpha = (when (node.quality) {
            RenderQuality.Smooth -> 0.90f
            RenderQuality.Balanced -> 0.94f
            RenderQuality.Experimental -> 0.98f
        } * alpha).coerceIn(0f, 1f)
        val dimAlpha = (0.060f * dim * alpha).coerceIn(0f, 0.22f)

        val primaryVeil = Brush.verticalGradient(
            listOf(
                Color(0xFFE0EAF3).copy(alpha = milkAlpha * 0.48f),
                Color(0xFF9AADBF).copy(alpha = baseAlpha * 0.18f * dim),
                Color(0xFF40576D).copy(alpha = baseAlpha * 0.22f * dim)
            )
        )
        val secondaryVeil = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = milkAlpha * 0.34f),
                Color(0xFFDCE5EF).copy(alpha = milkAlpha * 0.16f),
                Color(0xFF9BAEC1).copy(alpha = milkAlpha * 0.07f),
                Color(0xFF172333).copy(alpha = baseAlpha * 0.10f * dim)
            )
        )
        val highlightVeil = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = highlightAlpha * 0.42f),
                Color.White.copy(alpha = highlightAlpha * 0.08f),
                Color.Transparent
            ),
            center = Offset(w * 0.42f, h * 0.08f),
            radius = w * 0.98f
        )
        val palette = ordinaryFallbackPalette(theme)
        val cloudAlpha = params.cloudAlpha.coerceIn(0.25f, 2.2f)
        val fallbackBase = Brush.verticalGradient(
            colors = listOf(
                palette.top.copy(alpha = 0.46f + baseAlpha * 0.34f),
                palette.mid.copy(alpha = 0.34f + baseAlpha * 0.22f),
                palette.bottom.copy(alpha = 0.38f + baseAlpha * 0.24f)
            )
        )
        val fallbackGlowA = Brush.radialGradient(
            colors = listOf(
                palette.glowA.copy(alpha = (0.15f + highlightAlpha * 1.4f) * cloudAlpha.coerceIn(0.65f, 1.35f)),
                palette.glowA.copy(alpha = 0.045f * cloudAlpha.coerceIn(0.65f, 1.35f)),
                Color.Transparent
            ),
            center = Offset(w * 0.76f, h * 0.12f),
            radius = max(w, h) * 0.72f
        )
        val fallbackGlowB = Brush.radialGradient(
            colors = listOf(
                palette.glowB.copy(alpha = (0.12f + milkAlpha * 1.6f) * cloudAlpha.coerceIn(0.65f, 1.35f)),
                palette.glowB.copy(alpha = 0.035f * cloudAlpha.coerceIn(0.65f, 1.35f)),
                Color.Transparent
            ),
            center = Offset(w * 0.22f, h * 0.76f),
            radius = max(w, h) * 0.66f
        )
        val localSize = Size(w, h)

        clipPath(shapePath) {
            if (backdrop != null) {
                drawOrdinaryVisibleBackdropImage(
                    backdrop = backdrop,
                    sampleOffset = sampleOffset,
                    itemSize = localSize,
                    alpha = sampledBackdropAlpha
                )
            } else {
                drawRect(brush = fallbackBase, size = localSize, blendMode = BlendMode.SrcOver)
                drawRect(brush = fallbackGlowA, size = localSize, blendMode = BlendMode.Screen)
                drawRect(brush = fallbackGlowB, size = localSize, blendMode = BlendMode.Screen)
            }
            if (dimAlpha > 0.001f) {
                drawRect(
                    color = Color(0xFF020817).copy(alpha = dimAlpha),
                    size = localSize,
                    blendMode = BlendMode.Multiply
                )
            }
            drawRect(brush = primaryVeil, size = localSize, blendMode = BlendMode.SrcOver)
            drawRect(
                color = Color(0xFF72859A).copy(alpha = baseAlpha * 0.16f * milk),
                size = localSize,
                blendMode = BlendMode.SrcOver
            )
            drawRect(brush = secondaryVeil, size = localSize, blendMode = BlendMode.SrcOver)
            drawRect(brush = highlightVeil, size = localSize, blendMode = BlendMode.Screen)
        }
    }
}

internal fun DrawScope.drawOrdinaryComposeGlassShadow(
    node: OrdinaryGlassRenderNode,
    rect: Rect
) {
    if (node.role == GlassRole.Shell || rect.width <= 1f || rect.height <= 1f) return

    withOrdinaryBackdropTransform(node, rect) {
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
        if (elevationDp <= 0f) return@withOrdinaryBackdropTransform

        val elevationPx = elevationDp.dp.toPx()
        val radiusPx = node.radius.dp.toPx()
        val layers = 4
        for (index in layers downTo 1) {
            val fraction = index / layers.toFloat()
            val spread = elevationPx * (0.45f + fraction * 0.85f)
            val yOffset = elevationPx * (0.22f + fraction * 0.42f)
            drawRoundRect(
                color = Color.Black.copy(alpha = ambientAlpha * (0.14f + 0.18f * fraction)),
                topLeft = Offset(-spread, yOffset - spread * 0.34f),
                size = Size(w + spread * 2f, h + spread * 2f),
                cornerRadius = CornerRadius(radiusPx + spread, radiusPx + spread),
                blendMode = BlendMode.SrcOver
            )
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

private fun DrawScope.withOrdinaryBackdropTransform(
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

private fun DrawScope.drawOrdinaryVisibleBackdropImage(
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
        .roundToInt()
        .coerceIn(0, backdrop.image.width - 1)
    val srcY = ((sampleOffset.y + localTop) * backdrop.scale)
        .roundToInt()
        .coerceIn(0, backdrop.image.height - 1)
    val srcW = (visibleW * backdrop.scale)
        .roundToInt()
        .coerceAtLeast(1)
        .coerceAtMost(backdrop.image.width - srcX)
    val srcH = (visibleH * backdrop.scale)
        .roundToInt()
        .coerceAtLeast(1)
        .coerceAtMost(backdrop.image.height - srcY)

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

private data class OrdinaryFallbackPalette(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val glowA: Color,
    val glowB: Color
)

private fun ordinaryFallbackPalette(theme: BackgroundTheme): OrdinaryFallbackPalette = when (theme) {
    BackgroundTheme.Aurora -> OrdinaryFallbackPalette(
        Color(0xFF071426), Color(0xFF31446D), Color(0xFF8A6B65), Color(0xFFB79AFF), Color(0xFFFFA06E)
    )
    BackgroundTheme.Jade -> OrdinaryFallbackPalette(
        Color(0xFF071A22), Color(0xFF315B6D), Color(0xFF8A8266), Color(0xFF8EC2DD), Color(0xFF58C0BC)
    )
    BackgroundTheme.Sunset -> OrdinaryFallbackPalette(
        Color(0xFF20182D), Color(0xFF5D4774), Color(0xFFA87570), Color(0xFFC098FF), Color(0xFFFF9A64)
    )
    BackgroundTheme.Dawn -> OrdinaryFallbackPalette(
        Color(0xFF16253C), Color(0xFF708BAC), Color(0xFFC1A6A4), Color(0xFFE2CCFF), Color(0xFFFFC28A)
    )
}
