package com.yuchen.ailedger.ui.gl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.legacyOpenGlReferenceStyle
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.GlassSceneGroup
import com.yuchen.ailedger.ui.LegacyOpenGLShellHost
import com.yuchen.ailedger.ui.LocalBackdropFrameTicker
import com.yuchen.ailedger.ui.LocalBackdropOrigin
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassBackdrop
import com.yuchen.ailedger.ui.LocalGlassFoldoutClipRegistry
import com.yuchen.ailedger.ui.LocalGlassSceneGroup
import com.yuchen.ailedger.ui.OpenGlStartupBackdropBridge
import com.yuchen.ailedger.ui.applyGlassFoldoutClip
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val NewOpenGlReferenceShortEdgeDp = 160f
private const val NewOpenGlMinimumOpticalScale = 0.28f

val LocalNewOpenGlGlassStyleOverride =
    staticCompositionLocalOf<((GlassBorderStyle) -> GlassBorderStyle)?> { null }

/**
 * Shell 级 OpenGL Renderer 路由。
 *
 * 所有 OpenGL 路线只接收一套中度模糊颜色背景。旧版 Renderer 会自动把原镜片槽别名到
 * 同一 Bitmap，新版 Renderer 的 clear 参数也直接复用 medium Bitmap，因此不会再把一张
 * 独立清晰背景叠入玻璃。普通 Card、Chip、Floating、Nav 和 Flex 永远不会进入本入口。
 */
@Composable
fun NewOpenGLGlassCardLayer(
    radius: Int,
    glassIntensity: Float,
    coordinateSource: GlassCoordinateSource? = null,
    modifier: Modifier = Modifier,
    pressProgress: Float = 0f,
    pressCenter: Offset = Offset(0.5f, 0.5f),
    viewportTopInsetPx: Float = 0f,
    dynamicState: OpenGLGlassDynamicState? = null,
) {
    val sceneGroup = LocalGlassSceneGroup
    val usesLegacyShellRenderer =
        sceneGroup == GlassSceneGroup.SettingsPage ||
            sceneGroup == GlassSceneGroup.AssistantPage
    val localBackdrop = LocalBlurredBackdrop.current
    val backdrop = if (usesLegacyShellRenderer) {
        OpenGlStartupBackdropBridge.backdrop ?: localBackdrop
    } else {
        localBackdrop
    } ?: return

    if (!backdrop.isReady) {
        Box(
            modifier = modifier.startupStaticGlassLayer(
                radius = radius,
                glassIntensity = glassIntensity
            )
        ) {}
        return
    }

    val singleBackdrop = remember(backdrop) {
        backdrop.copy(lensImage = backdrop.blurMediumImage)
    }

    if (usesLegacyShellRenderer) {
        val currentSpec = LocalGlassBackdrop.current
        val legacySpec = remember(currentSpec) {
            currentSpec?.copy(borderStyle = legacyOpenGlReferenceStyle())
        }

        if (legacySpec != null) {
            CompositionLocalProvider(
                LocalGlassBackdrop provides legacySpec,
                LocalBlurredBackdrop provides singleBackdrop,
            ) {
                LegacyOpenGLShellHost(
                    quality = legacySpec.quality,
                    glassIntensity = glassIntensity,
                    motionIntensity = legacySpec.motionIntensity,
                    radius = radius,
                    modifier = modifier,
                    coordinateSource = coordinateSource,
                    manageCoordinatePlacement = false,
                    pressProgress = pressProgress,
                    pressCenter = pressCenter,
                    viewportTopInsetPx = viewportTopInsetPx,
                    dynamicState = dynamicState,
                ) {}
            }
        } else {
            CompositionLocalProvider(LocalBlurredBackdrop provides singleBackdrop) {
                OpenGLGlassCardLayer(
                    radius = radius,
                    glassIntensity = glassIntensity,
                    coordinateSource = coordinateSource,
                    modifier = modifier,
                    pressProgress = pressProgress,
                    pressCenter = pressCenter,
                    viewportTopInsetPx = viewportTopInsetPx,
                    dynamicState = dynamicState,
                )
            }
        }
        return
    }

    val baseBorder = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
    val styleOverride = LocalNewOpenGlGlassStyleOverride.current
    val border = remember(baseBorder, styleOverride) {
        styleOverride?.invoke(baseBorder) ?: baseBorder
    }
    val rendererBorder = remember(border) { border.onlyWebOpenGLRendererFields() }
    val backdropOrigin = LocalBackdropOrigin.current
    val backdropTicker = LocalBackdropFrameTicker.current
    val density = LocalDensity.current
    val densityScale = density.density.coerceAtLeast(0.001f)
    val surfaceAnchor = LocalOpenGLGlassSurfaceAnchor.current.fraction
    val localViewportTopInsetPx = with(density) { LocalOpenGLGlassViewportTopInset.current.toPx() }
    val effectiveViewportTopInsetPx = max(viewportTopInsetPx, localViewportTopInsetPx)
    val foldoutClipRegistry = LocalGlassFoldoutClipRegistry.current
    foldoutClipRegistry?.version

    val blurLowBitmap = remember(singleBackdrop.blurLowImage) {
        singleBackdrop.blurLowImage.asAndroidBitmap()
    }
    val blurMediumBitmap = remember(singleBackdrop.blurMediumImage) {
        singleBackdrop.blurMediumImage.asAndroidBitmap()
    }
    val blurHighBitmap = remember(singleBackdrop.blurHighImage) {
        singleBackdrop.blurHighImage.asAndroidBitmap()
    }

    val intensity = border.newOpenGlGlassIntensity.takeIf { it > 0f }?.coerceIn(0.35f, 1.35f)
        ?: glassIntensity.coerceIn(0.35f, 1.35f)
    val staticPress = pressProgress.coerceIn(0f, 1f)
    val staticPressX = pressCenter.x.coerceIn(0f, 1f)
    val staticPressY = pressCenter.y.coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val shortEdgePx = min(widthPx, heightPx)
        val shortEdgeDp = shortEdgePx / densityScale
        val opticalScale = (shortEdgeDp / NewOpenGlReferenceShortEdgeDp)
            .coerceIn(NewOpenGlMinimumOpticalScale, 1f)
        val scaledRendererBorder = remember(rendererBorder, opticalScale) {
            rendererBorder.scaleNewOpenGlOpticalDistances(opticalScale)
        }
        val radiusPx = if (radius >= 999) {
            shortEdgePx * 0.5f
        } else {
            with(density) { radius.dp.toPx() }.coerceIn(0f, shortEdgePx * 0.5f)
        }
        val safeViewportTopInsetPx = effectiveViewportTopInsetPx
            .coerceIn(0f, (heightPx - 1f).coerceAtLeast(0f))
        val viewportHeightPx = (heightPx - safeViewportTopInsetPx).coerceAtLeast(1f)
        val rootWidthPx = singleBackdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
        val rootHeightPx = singleBackdrop.fullHeightPx.toFloat().coerceAtLeast(1f)

        val safeSurfaceAnchor = surfaceAnchor.coerceIn(0f, 1f)
        val stableSurfaceWidthPx = max(widthPx, rootWidthPx)
        val stableSurfaceHeightPx = max(
            heightPx,
            rootHeightPx * (1f - safeSurfaceAnchor)
        )

        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context -> WebOpenGLGlassCardHostView(context) },
            update = { view ->
                view.applyGlassFoldoutClip(
                    registry = foldoutClipRegistry,
                    coordinates = coordinateSource?.coordinates
                )
                view.bindDynamicSources(
                    coordinateSource = coordinateSource,
                    backdropOrigin = backdropOrigin,
                    frameTicker = backdropTicker,
                    dynamicState = dynamicState,
                )
                val frameDirty = view.setFrameSpec(
                    width = widthPx,
                    fullHeight = heightPx,
                    viewportHeight = viewportHeightPx,
                    rectOffsetY = safeViewportTopInsetPx,
                    radius = radiusPx,
                    baseIntensity = intensity,
                    rootWidth = rootWidthPx,
                    rootHeight = rootHeightPx,
                    staticPressProgress = staticPress,
                    staticPressCenterX = staticPressX,
                    staticPressCenterY = staticPressY,
                )
                val textureDirty = view.setBackdropTextures(
                    clearBitmap = blurMediumBitmap,
                    blurLowBitmap = blurLowBitmap,
                    blurMediumBitmap = blurMediumBitmap,
                    blurHighBitmap = blurHighBitmap
                )
                val blurDirty = view.setBackdropBlurAmount(singleBackdrop.blurAmount)
                val styleDirty = view.setGlassStyle(scaledRendererBorder, densityScale)
                val surfaceDirty = view.setStableSurfaceSize(
                    stableSurfaceWidthPx.roundToInt(),
                    stableSurfaceHeightPx.roundToInt(),
                    rootWidthPx.roundToInt(),
                    rootHeightPx.roundToInt()
                )

                if (surfaceDirty || frameDirty || textureDirty || blurDirty || styleDirty) {
                    view.requestRenderOnNextAnimationFrame()
                }
            }
        )
    }
}

private fun Modifier.startupStaticGlassLayer(
    radius: Int,
    glassIntensity: Float
): Modifier = drawWithCache {
    val cornerRadiusPx = if (radius >= 999) {
        min(size.width, size.height) * 0.5f
    } else {
        radius.dp.toPx().coerceAtLeast(0f)
    }
    val intensity = glassIntensity.coerceIn(0.35f, 1.35f)
    val fill = Color(0xFF17345B).copy(alpha = (0.34f * intensity).coerceIn(0.18f, 0.48f))
    val innerLift = Color.White.copy(alpha = (0.040f * intensity).coerceIn(0.018f, 0.065f))
    val edge = Color.White.copy(alpha = (0.16f * intensity).coerceIn(0.08f, 0.22f))
    val strokeWidth = 1.dp.toPx().coerceAtLeast(1f)
    val corner = CornerRadius(cornerRadiusPx, cornerRadiusPx)

    onDrawBehind {
        drawRoundRect(color = fill, cornerRadius = corner)
        drawRoundRect(
            color = innerLift,
            topLeft = Offset(strokeWidth, strokeWidth),
            size = Size(
                width = (size.width - strokeWidth * 2f).coerceAtLeast(0f),
                height = (size.height - strokeWidth * 2f).coerceAtLeast(0f)
            ),
            cornerRadius = CornerRadius(
                (cornerRadiusPx - strokeWidth).coerceAtLeast(0f),
                (cornerRadiusPx - strokeWidth).coerceAtLeast(0f)
            )
        )
        drawRoundRect(
            color = edge,
            cornerRadius = corner,
            style = Stroke(width = strokeWidth)
        )
    }
}

private fun GlassBorderStyle.scaleNewOpenGlOpticalDistances(scale: Float): GlassBorderStyle {
    val safeScale = scale.coerceIn(NewOpenGlMinimumOpticalScale, 1f)
    if (safeScale >= 0.999f) return this

    return copy(
        newOpenGlBodyLensBasePull = newOpenGlBodyLensBasePull * safeScale,
        newOpenGlBodyLensPullDp = newOpenGlBodyLensPullDp * safeScale,
        newOpenGlBodyLensExtraDistance = newOpenGlBodyLensExtraDistance * safeScale,
        newOpenGlBodyLensReachDp = newOpenGlBodyLensReachDp * safeScale,
        newOpenGlShoulderWidthDp = newOpenGlShoulderWidthDp * safeScale,
        newOpenGlShoulderCaptureWidthDp = newOpenGlShoulderCaptureWidthDp * safeScale,
        newOpenGlDispersionDistanceDp = newOpenGlDispersionDistanceDp * safeScale,
        newOpenGlDispersionEdgeWidthDp = newOpenGlDispersionEdgeWidthDp * safeScale
    )
}

private fun GlassBorderStyle.onlyWebOpenGLRendererFields(): GlassBorderStyle = GlassBorderStyle().copy(
    newOpenGlBodyVisibility = newOpenGlBodyVisibility,
    newOpenGlBodyMaxAlpha = newOpenGlBodyMaxAlpha,
    newOpenGlBodyOutputBrightness = newOpenGlBodyOutputBrightness,
    newOpenGlBodyLensBasePull = newOpenGlBodyLensBasePull,
    newOpenGlBodyLensPullDp = newOpenGlBodyLensPullDp,
    newOpenGlBodyLensConcentration = newOpenGlBodyLensConcentration,
    newOpenGlBodyLensExtraDistance = newOpenGlBodyLensExtraDistance,
    newOpenGlBodyLensReachDp = newOpenGlBodyLensReachDp,
    newOpenGlBodyLensDark = newOpenGlBodyLensDark,
    newOpenGlBodyLensDebug = newOpenGlBodyLensDebug,
    newOpenGlBodyWidth = newOpenGlBodyWidth,
    newOpenGlBodyCurve = newOpenGlBodyCurve,
    newOpenGlBodyGain = newOpenGlBodyGain,
    newOpenGlBrightness = newOpenGlBrightness,
    newOpenGlShoulderWidthDp = newOpenGlShoulderWidthDp,
    newOpenGlShoulderCaptureWidthDp = newOpenGlShoulderCaptureWidthDp,
    newOpenGlShoulderMaxAngleDeg = newOpenGlShoulderMaxAngleDeg,
    newOpenGlShoulderFalloffRoundness = newOpenGlShoulderFalloffRoundness,
    newOpenGlShoulderMaterialStrength = newOpenGlShoulderMaterialStrength,
    newOpenGlShoulderTangentialFlowStrength = newOpenGlShoulderTangentialFlowStrength,
    newOpenGlDispersionStrength = newOpenGlDispersionStrength,
    newOpenGlDispersionDistanceDp = newOpenGlDispersionDistanceDp,
    newOpenGlDispersionEdgeWidthDp = newOpenGlDispersionEdgeWidthDp,
    newOpenGlDispersionConcentration = newOpenGlDispersionConcentration
)
