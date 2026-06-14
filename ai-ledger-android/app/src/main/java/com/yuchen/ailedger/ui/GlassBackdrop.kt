package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class GlassBackdropSpec(
    val quality: RenderQuality,
    val motionIntensity: Float,
    val theme: BackgroundTheme,
    val params: BackdropDebugParams = BackdropDebugParams(),
    val borderStyle: GlassBorderStyle = GlassBorderStyle()
)

val LocalGlassBackdrop = compositionLocalOf<GlassBackdropSpec?> { null }

private data class ComposeBackdropSample(
    val image: ImageBitmap,
    val fullWidthPx: Int,
    val fullHeightPx: Int,
    val scale: Float
)

@Composable
fun SampledWeatherGlassBackdrop(
    modifier: Modifier = Modifier,
    radius: Int,
    coordinateSource: GlassCoordinateSource,
    quality: RenderQuality,
    motionIntensity: Float,
    theme: BackgroundTheme,
    blurRadiusDp: Int = 112,
    liftAlpha: Float = 1f,
    ordinaryRoleHint: GlassRole? = null,
    ordinaryGlassIntensityHint: Float = liftAlpha,
    ordinaryEdgeStrengthHint: Float = 0f,
    ordinaryPressableHint: Boolean = false,
    ordinaryShimmerHint: Float = 0f,
    ordinaryBreatheHint: Float = 0f,
    ordinaryPressProgressHint: Float = 0f,
    ordinaryLensProgressHint: Float = 0f,
    ordinarySweepProgressHint: Float = 0f,
    ordinaryElasticityHint: Float = 0f,
    ordinaryPressCenterHint: Offset = Offset(0.5f, 0.5f)
) {
    val rawBackdrop = LocalBlurredBackdrop.current
    val cachedBackdrop = remember(
        rawBackdrop?.image,
        rawBackdrop?.fullWidthPx,
        rawBackdrop?.fullHeightPx,
        rawBackdrop?.scale
    ) {
        rawBackdrop?.let {
            ComposeBackdropSample(
                image = it.image,
                fullWidthPx = it.fullWidthPx,
                fullHeightPx = it.fullHeightPx,
                scale = it.scale
            )
        }
    }
    val spec = LocalGlassBackdrop.current
    val origin = LocalBackdropOrigin.current
    val ticker = LocalBackdropFrameTicker.current
    val sampleOffsetState = remember(coordinateSource, origin, ticker) {
        derivedStateOf(structuralEqualityPolicy()) {
            ticker?.frameNanos
            coordinateSource.offsetRelativeTo(origin)
        }
    }
    val params = spec?.params ?: BackdropDebugParams()
    val glass = ComposeGlassLabState.style
    val alpha = liftAlpha.coerceIn(0.12f, 1.55f)
    val dim = glass.backdropDim.coerceIn(0f, 1.80f)
    val milk = glass.backdropMilk.coerceIn(0f, 1.80f)
    val highlight = glass.backdropHighlight.coerceIn(0f, 1.80f)
    val baseAlpha = when (quality) {
        RenderQuality.Smooth -> 0.15f
        RenderQuality.Balanced -> 0.18f
        RenderQuality.Experimental -> 0.21f
    } * alpha
    val milkAlpha = when (quality) {
        RenderQuality.Smooth -> 0.040f
        RenderQuality.Balanced -> 0.052f
        RenderQuality.Experimental -> 0.064f
    } * alpha * milk
    val highlightAlpha = when (quality) {
        RenderQuality.Smooth -> 0.036f
        RenderQuality.Balanced -> 0.046f
        RenderQuality.Experimental -> 0.056f
    } * alpha * highlight
    val backdropAlpha = (when (quality) {
        RenderQuality.Smooth -> 0.90f
        RenderQuality.Balanced -> 0.94f
        RenderQuality.Experimental -> 0.98f
    } * alpha).coerceIn(0f, 1f)
    val dimAlpha = (0.060f * dim * alpha).coerceIn(0f, 0.22f)

    // 第一阶段只登记普通 Compose 玻璃的几何和状态，不关闭当前子级绘制。
    // Shell 通过角色提示或现有 blur 特征被硬排除，不进入普通玻璃 registry。
    val sceneGroup = LocalGlassSceneGroup
    val inferredRole = ordinaryRoleHint ?: when {
        blurRadiusDp >= 100 -> GlassRole.Shell
        radius >= 999 -> GlassRole.Nav
        blurRadiusDp <= 62 -> GlassRole.Chip
        else -> GlassRole.Card
    }
    val ordinaryNode = remember(coordinateSource) {
        OrdinaryGlassRenderNode(coordinates = coordinateSource)
    }
    val ordinaryNodeEnabled = inferredRole != GlassRole.Shell && sceneGroup != GlassSceneGroup.Unassigned

    SideEffect {
        if (ordinaryNodeEnabled) {
            ordinaryNode.updateStatic(
                sceneGroup = sceneGroup,
                role = inferredRole,
                quality = quality,
                radius = radius,
                glassIntensity = ordinaryGlassIntensityHint,
                backdropAlpha = backdropAlpha,
                edgeStrength = ordinaryEdgeStrengthHint,
                pressable = ordinaryPressableHint
            )
            ordinaryNode.updateMotion(
                shimmer = ordinaryShimmerHint,
                breathe = ordinaryBreatheHint,
                pressProgress = ordinaryPressProgressHint,
                lensProgress = ordinaryLensProgressHint,
                sweepProgress = ordinarySweepProgressHint,
                elasticity = ordinaryElasticityHint,
                pressCenter = ordinaryPressCenterHint
            )
        }
    }
    BindOrdinaryGlassRenderNode(node = ordinaryNode, enabled = ordinaryNodeEnabled)

    val cachedDrawModifier = remember(
        radius,
        cachedBackdrop,
        sampleOffsetState,
        theme,
        params,
        baseAlpha,
        milkAlpha,
        highlightAlpha,
        backdropAlpha,
        dimAlpha,
        dim,
        milk
    ) {
        Modifier
            .clip(RoundedCornerShape(radius.dp))
            .drawWithCache {
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
                    center = Offset(size.width * 0.42f, size.height * 0.08f),
                    radius = size.width * 0.98f
                )
                val palette = fallbackPalette(theme)
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
                    center = Offset(size.width * 0.76f, size.height * 0.12f),
                    radius = max(size.width, size.height) * 0.72f
                )
                val fallbackGlowB = Brush.radialGradient(
                    colors = listOf(
                        palette.glowB.copy(alpha = (0.12f + milkAlpha * 1.6f) * cloudAlpha.coerceIn(0.65f, 1.35f)),
                        palette.glowB.copy(alpha = 0.035f * cloudAlpha.coerceIn(0.65f, 1.35f)),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.22f, size.height * 0.76f),
                    radius = max(size.width, size.height) * 0.66f
                )

                onDrawBehind {
                    val sampleOffset = sampleOffsetState.value
                    if (cachedBackdrop != null) {
                        drawVisibleBackdropImage(cachedBackdrop, sampleOffset, backdropAlpha)
                    } else {
                        drawRect(fallbackBase, blendMode = BlendMode.SrcOver)
                        drawRect(fallbackGlowA, blendMode = BlendMode.Screen)
                        drawRect(fallbackGlowB, blendMode = BlendMode.Screen)
                    }
                    if (dimAlpha > 0.001f) {
                        drawRect(Color(0xFF020817).copy(alpha = dimAlpha), blendMode = BlendMode.Multiply)
                    }
                    drawRect(primaryVeil, blendMode = BlendMode.SrcOver)
                    drawRect(
                        Color(0xFF72859A).copy(alpha = baseAlpha * 0.16f * milk),
                        blendMode = BlendMode.SrcOver
                    )
                    drawRect(secondaryVeil, blendMode = BlendMode.SrcOver)
                    drawRect(highlightVeil, blendMode = BlendMode.Screen)
                }
            }
    }

    Box(modifier = modifier.then(cachedDrawModifier)) {}
}

private data class FallbackGlassPalette(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val glowA: Color,
    val glowB: Color
)

private fun fallbackPalette(theme: BackgroundTheme): FallbackGlassPalette = when (theme) {
    BackgroundTheme.Aurora -> FallbackGlassPalette(Color(0xFF071426), Color(0xFF31446D), Color(0xFF8A6B65), Color(0xFFB79AFF), Color(0xFFFFA06E))
    BackgroundTheme.Jade -> FallbackGlassPalette(Color(0xFF071A22), Color(0xFF315B6D), Color(0xFF8A8266), Color(0xFF8EC2DD), Color(0xFF58C0BC))
    BackgroundTheme.Sunset -> FallbackGlassPalette(Color(0xFF20182D), Color(0xFF5D4774), Color(0xFFA87570), Color(0xFFC098FF), Color(0xFFFF9A64))
    BackgroundTheme.Dawn -> FallbackGlassPalette(Color(0xFF16253C), Color(0xFF708BAC), Color(0xFFC1A6A4), Color(0xFFE2CCFF), Color(0xFFFFC28A))
}

private fun DrawScope.drawVisibleBackdropImage(backdrop: ComposeBackdropSample, sampleOffset: Offset, alpha: Float) {
    val rootW = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
    val rootH = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)
    val localLeft = max(0f, -sampleOffset.x)
    val localTop = max(0f, -sampleOffset.y)
    val localRight = min(size.width, rootW - sampleOffset.x)
    val localBottom = min(size.height, rootH - sampleOffset.y)
    val visibleW = localRight - localLeft
    val visibleH = localBottom - localTop
    if (visibleW <= 0f || visibleH <= 0f) return
    val srcX = ((sampleOffset.x + localLeft) * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.width - 1)
    val srcY = ((sampleOffset.y + localTop) * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.height - 1)
    val srcW = (visibleW * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.width - srcX)
    val srcH = (visibleH * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.height - srcY)
    drawImage(
        image = backdrop.image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(localLeft.roundToInt(), localTop.roundToInt()),
        dstSize = IntSize(visibleW.roundToInt().coerceAtLeast(1), visibleH.roundToInt().coerceAtLeast(1)),
        alpha = alpha,
        blendMode = BlendMode.SrcOver
    )
}

@Composable
fun SampledWeatherEdgeRefraction(
    modifier: Modifier = Modifier,
    radius: Int,
    coordinateSource: GlassCoordinateSource,
    quality: RenderQuality,
    motionIntensity: Float,
    theme: BackgroundTheme,
    strength: Float = 1f
) {
    val spec = LocalGlassBackdrop.current
    val origin = LocalBackdropOrigin.current
    val ticker = LocalBackdropFrameTicker.current
    val glass = ComposeGlassLabState.style
    val border = spec?.borderStyle ?: GlassBorderStyle()
    val offsetState = remember(coordinateSource, origin, ticker) {
        derivedStateOf(structuralEqualityPolicy()) {
            ticker?.frameNanos
            coordinateSource.offsetRelativeTo(origin)
        }
    }
    val edgeAlpha = (0.055f * strength.coerceIn(0f, 2f) * glass.edge).coerceIn(0f, 0.18f)
    val edgeWidth = (1.0f + glass.topWidthDp.coerceIn(0.55f, 1.85f) * 1.4f).dp
    val lineAlpha = border.outerStrokeAlpha.coerceIn(0f, 1.6f)
    val cachedEdgeModifier = remember(radius, offsetState, edgeAlpha, edgeWidth, lineAlpha) {
        Modifier
            .clip(RoundedCornerShape(radius.dp))
            .drawWithCache {
                val corner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
                val topGlow = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = edgeAlpha * 0.95f),
                        Color(0xFFBFF7FF).copy(alpha = edgeAlpha * 0.40f),
                        Color.Transparent
                    ),
                    endY = size.height * 0.30f
                )
                onDrawBehind {
                    offsetState.value
                    drawRoundRect(
                        brush = topGlow,
                        cornerRadius = corner,
                        style = Stroke(edgeWidth.toPx()),
                        blendMode = BlendMode.Screen
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.045f * lineAlpha),
                        cornerRadius = corner,
                        style = Stroke(0.7.dp.toPx()),
                        blendMode = BlendMode.Screen
                    )
                }
            }
    }
    Box(modifier = modifier.then(cachedEdgeModifier)) {}
}
