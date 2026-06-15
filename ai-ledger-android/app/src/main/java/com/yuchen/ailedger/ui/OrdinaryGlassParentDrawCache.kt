package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.max
import kotlin.math.min

/** 节点级 Path / Brush 缓存，滚动刷新时不重复构造静态材质对象。 */
internal class OrdinaryGlassParentDrawCache {
    var geometrySignature: Long = Long.MIN_VALUE
    var shapePath: Path = Path()
    var edgeBandPath: Path = Path()
    var bottomMassBandPath: Path = Path()
    var cornerRadius: CornerRadius = CornerRadius(0f, 0f)
    var localSize: Size = Size.Zero
    var rimSize: Size = Size.Zero

    var materialSignature: Long = Long.MIN_VALUE
    var baseField: Brush? = null
    var quietField: Brush? = null
    var edgeBandBrush: Brush? = null
    var flowBrush: Brush? = null
    var bottomMassBrush: Brush? = null
    var rimField: Brush? = null
    var rimStrokePx: Float = 0f

    var backdropSignature: Long = Long.MIN_VALUE
    var primaryVeil: Brush? = null
    var secondaryVeil: Brush? = null
    var highlightVeil: Brush? = null
    var fallbackBase: Brush? = null
    var fallbackGlowA: Brush? = null
    var fallbackGlowB: Brush? = null
    var sampledBackdropAlpha: Float = 1f
    var dimAlpha: Float = 0f
    var backdropBaseAlpha: Float = 0f
    var backdropMilk: Float = 0f
}

internal fun ordinaryParentSignatureOf(vararg values: Int): Long {
    var result = 1125899906842597L
    values.forEach { value -> result = result * 31L + value.toLong() }
    return result
}

internal fun Float.ordinaryParentSignatureBits(): Int = toBits()

internal fun DrawScope.ensureOrdinaryParentGeometry(
    node: OrdinaryGlassRenderNode,
    rect: Rect
): OrdinaryGlassParentDrawCache {
    val cache = node.parentDrawCache
    val glass = ComposeGlassLabState.style
    val w = rect.width.coerceAtLeast(1f)
    val h = rect.height.coerceAtLeast(1f)
    val radiusPx = node.radius.dp.toPx()
    val edgeWidth = glass.topWidthDp.dp.toPx().coerceAtLeast(0.05.dp.toPx())
    val bottomWidth = glass.bottomWidthDp.dp.toPx().coerceAtLeast(0.05.dp.toPx())
    val signature = ordinaryParentSignatureOf(
        w.ordinaryParentSignatureBits(), h.ordinaryParentSignatureBits(),
        radiusPx.ordinaryParentSignatureBits(), edgeWidth.ordinaryParentSignatureBits(),
        bottomWidth.ordinaryParentSignatureBits()
    )
    if (cache.geometrySignature == signature) return cache

    val opticalBandWidth = (
        1.0.dp.toPx() + edgeWidth * 2.20f + bottomWidth * 0.72f
    ).coerceIn(1.0.dp.toPx(), minOf(w, h) * 0.22f)
    val innerRight = (w - opticalBandWidth).coerceAtLeast(opticalBandWidth + 1f)
    val innerBottom = (h - opticalBandWidth).coerceAtLeast(opticalBandWidth + 1f)
    val innerRadius = (radiusPx - opticalBandWidth).coerceAtLeast(0f)

    val bottomMassWidth = (
        opticalBandWidth + bottomWidth * 2.10f
    ).coerceIn(opticalBandWidth, minOf(w, h) * 0.28f)
    val massInnerRight = (w - bottomMassWidth).coerceAtLeast(bottomMassWidth + 1f)
    val massInnerBottom = (h - bottomMassWidth).coerceAtLeast(bottomMassWidth + 1f)
    val massInnerRadius = (radiusPx - bottomMassWidth).coerceAtLeast(0f)

    cache.shapePath = Path().apply {
        addRoundRect(RoundRect(0f, 0f, w, h, radiusPx, radiusPx))
    }
    cache.edgeBandPath = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(RoundRect(0f, 0f, w, h, radiusPx, radiusPx))
        addRoundRect(
            RoundRect(
                opticalBandWidth, opticalBandWidth, innerRight, innerBottom,
                innerRadius, innerRadius
            )
        )
    }
    cache.bottomMassBandPath = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(RoundRect(0f, 0f, w, h, radiusPx, radiusPx))
        addRoundRect(
            RoundRect(
                bottomMassWidth, bottomMassWidth, massInnerRight, massInnerBottom,
                massInnerRadius, massInnerRadius
            )
        )
    }
    cache.cornerRadius = CornerRadius(radiusPx, radiusPx)
    cache.localSize = Size(w, h)
    val rimInset = 0.62.dp.toPx()
    cache.rimSize = Size(
        (w - rimInset * 2f).coerceAtLeast(1f),
        (h - rimInset * 2f).coerceAtLeast(1f)
    )
    cache.geometrySignature = signature
    cache.materialSignature = Long.MIN_VALUE
    cache.backdropSignature = Long.MIN_VALUE
    return cache
}

internal fun DrawScope.ensureOrdinaryParentMaterialBrushes(
    node: OrdinaryGlassRenderNode,
    rect: Rect,
    cache: OrdinaryGlassParentDrawCache
) {
    val glass = ComposeGlassLabState.style
    val intensityScale = node.glassIntensity.coerceIn(0.25f, 1.45f)
    val pulse = 0.94f + node.breathe * 0.030f
    val frost = ComposeGlassRuntimeDefaults.frost * intensityScale
    val quiet = glass.quiet
    val topLight = glass.topLight * intensityScale
    val pathFlow = glass.topVariation
    val bottomLight = glass.bottomLight * intensityScale
    val outerRim = glass.outerRim * intensityScale
    val bottomMass = glass.bottomMass * intensityScale
    val sideCarry = glass.sideLight * intensityScale
    val w = rect.width.coerceAtLeast(1f)
    val h = rect.height.coerceAtLeast(1f)

    val signature = ordinaryParentSignatureOf(
        w.ordinaryParentSignatureBits(), h.ordinaryParentSignatureBits(),
        intensityScale.ordinaryParentSignatureBits(), pulse.ordinaryParentSignatureBits(),
        frost.ordinaryParentSignatureBits(), quiet.ordinaryParentSignatureBits(),
        topLight.ordinaryParentSignatureBits(), pathFlow.ordinaryParentSignatureBits(),
        bottomLight.ordinaryParentSignatureBits(), outerRim.ordinaryParentSignatureBits(),
        bottomMass.ordinaryParentSignatureBits(), sideCarry.ordinaryParentSignatureBits()
    )
    if (cache.materialSignature == signature) return

    val topAlpha = (0.120f + 0.055f * pathFlow) * topLight
    val sideAlpha = 0.028f * sideCarry
    val bottomAlpha = 0.090f * bottomLight
    cache.baseField = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.026f * frost * pulse),
            Color.White.copy(alpha = 0f),
            Color.Black.copy(alpha = 0.080f * quiet)
        ), Offset.Zero, Offset(w, h)
    )
    cache.quietField = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = 0.010f / quiet.coerceAtLeast(0.25f)),
            Color.Transparent,
            Color.Black.copy(alpha = 0.045f * quiet)
        ), Offset(w * 0.50f, h * 0.46f), maxOf(w, h) * 0.72f
    )
    cache.edgeBandBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = topAlpha),
            Color(0xFFEAF9FF).copy(alpha = topAlpha * 0.36f),
            Color.White.copy(alpha = sideAlpha),
            Color.Transparent,
            Color.White.copy(alpha = bottomAlpha * 0.26f),
            Color.White.copy(alpha = bottomAlpha)
        ), 0f, h
    )
    cache.flowBrush = Brush.horizontalGradient(
        listOf(
            Color.White.copy(alpha = topAlpha * 0.18f * pathFlow),
            Color.White.copy(alpha = topAlpha * 0.04f),
            Color.Transparent,
            Color.White.copy(alpha = topAlpha * 0.08f * pathFlow),
            Color.White.copy(alpha = topAlpha * 0.025f)
        ), 0f, w
    )
    cache.bottomMassBrush = Brush.verticalGradient(
        listOf(
            Color.Transparent,
            Color.Transparent,
            Color(0xFF07132F).copy(alpha = 0.024f * bottomMass),
            Color(0xFF030714).copy(alpha = 0.082f * bottomMass),
            Color(0xFF00020A).copy(alpha = 0.180f * bottomMass)
        ), 0f, h
    )
    cache.rimField = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.120f * outerRim),
            Color.White.copy(alpha = 0.018f * outerRim),
            Color.Transparent,
            Color.Black.copy(alpha = 0.072f * bottomMass),
            Color.White.copy(alpha = 0.014f * outerRim)
        ), Offset.Zero, Offset(w, h)
    )
    cache.rimStrokePx = maxOf(0.34.dp.toPx(), 0.48.dp.toPx() * outerRim)
    cache.materialSignature = signature
}

internal fun DrawScope.ensureOrdinaryParentBackdropBrushes(
    node: OrdinaryGlassRenderNode,
    rect: Rect,
    spec: GlassBackdropSpec,
    cache: OrdinaryGlassParentDrawCache
) {
    val params = spec.params
    val theme = spec.theme
    val glass = ComposeGlassLabState.style
    val alpha = node.backdropAlpha.coerceIn(0.12f, 1.55f)
    val dim = glass.backdropDim.coerceIn(0f, 1.80f)
    val milk = glass.backdropMilk.coerceIn(0f, 1.80f)
    val highlight = glass.backdropHighlight.coerceIn(0f, 1.80f)
    val w = rect.width.coerceAtLeast(1f)
    val h = rect.height.coerceAtLeast(1f)
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
    val sampledAlpha = (when (node.quality) {
        RenderQuality.Smooth -> 0.90f
        RenderQuality.Balanced -> 0.94f
        RenderQuality.Experimental -> 0.98f
    } * alpha).coerceIn(0f, 1f)
    val dimAlpha = (0.060f * dim * alpha).coerceIn(0f, 0.22f)
    val cloudAlpha = params.cloudAlpha.coerceIn(0.25f, 2.2f)
    val signature = ordinaryParentSignatureOf(
        w.ordinaryParentSignatureBits(), h.ordinaryParentSignatureBits(),
        node.quality.ordinal, theme.ordinal, alpha.ordinaryParentSignatureBits(),
        dim.ordinaryParentSignatureBits(), milk.ordinaryParentSignatureBits(),
        highlight.ordinaryParentSignatureBits(), cloudAlpha.ordinaryParentSignatureBits()
    )
    if (cache.backdropSignature == signature) return

    cache.primaryVeil = Brush.verticalGradient(
        listOf(
            Color(0xFFE0EAF3).copy(alpha = milkAlpha * 0.48f),
            Color(0xFF9AADBF).copy(alpha = baseAlpha * 0.18f * dim),
            Color(0xFF40576D).copy(alpha = baseAlpha * 0.22f * dim)
        )
    )
    cache.secondaryVeil = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = milkAlpha * 0.34f),
            Color(0xFFDCE5EF).copy(alpha = milkAlpha * 0.16f),
            Color(0xFF9BAEC1).copy(alpha = milkAlpha * 0.07f),
            Color(0xFF172333).copy(alpha = baseAlpha * 0.10f * dim)
        )
    )
    cache.highlightVeil = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = highlightAlpha * 0.42f),
            Color.White.copy(alpha = highlightAlpha * 0.08f),
            Color.Transparent
        ), Offset(w * 0.42f, h * 0.08f), w * 0.98f
    )
    val palette = ordinaryParentFallbackPalette(theme)
    cache.fallbackBase = Brush.verticalGradient(
        listOf(
            palette.top.copy(alpha = 0.46f + baseAlpha * 0.34f),
            palette.mid.copy(alpha = 0.34f + baseAlpha * 0.22f),
            palette.bottom.copy(alpha = 0.38f + baseAlpha * 0.24f)
        )
    )
    cache.fallbackGlowA = Brush.radialGradient(
        listOf(
            palette.glowA.copy(alpha = (0.15f + highlightAlpha * 1.4f) * cloudAlpha.coerceIn(0.65f, 1.35f)),
            palette.glowA.copy(alpha = 0.045f * cloudAlpha.coerceIn(0.65f, 1.35f)),
            Color.Transparent
        ), Offset(w * 0.76f, h * 0.12f), max(w, h) * 0.72f
    )
    cache.fallbackGlowB = Brush.radialGradient(
        listOf(
            palette.glowB.copy(alpha = (0.12f + milkAlpha * 1.6f) * cloudAlpha.coerceIn(0.65f, 1.35f)),
            palette.glowB.copy(alpha = 0.035f * cloudAlpha.coerceIn(0.65f, 1.35f)),
            Color.Transparent
        ), Offset(w * 0.22f, h * 0.76f), max(w, h) * 0.66f
    )
    cache.sampledBackdropAlpha = sampledAlpha
    cache.dimAlpha = dimAlpha
    cache.backdropBaseAlpha = baseAlpha
    cache.backdropMilk = milk
    cache.backdropSignature = signature
}
