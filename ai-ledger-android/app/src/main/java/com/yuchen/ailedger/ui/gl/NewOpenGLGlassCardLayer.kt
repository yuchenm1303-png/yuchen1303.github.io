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
import com.yuchen.ailedger.ui.LegacyOpenGLGlassPreviewShell
import com.yuchen.ailedger.ui.LocalBackdropOrigin
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassBackdrop
import com.yuchen.ailedger.ui.LocalGlassFoldoutClipRegistry
import com.yuchen.ailedger.ui.LocalGlassSceneGroup
import com.yuchen.ailedger.ui.applyGlassFoldoutClip
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

val LocalNewOpenGlGlassStyleOverride =
    staticCompositionLocalOf<((GlassBorderStyle) -> GlassBorderStyle)?> { null }

@Composable
fun NewOpenGLGlassCardLayer(
    radius: Int,
    glassIntensity: Float,
    coordinateSource: GlassCoordinateSource? = null,
    modifier: Modifier = Modifier,
    pressProgress: Float = 0f,
    pressCenter: Offset = Offset(0.5f, 0.5f),
    viewportTopInsetPx: Float = 0f
) {
    val backdrop = LocalBlurredBackdrop.current ?: return

    // Do not create an EGL context, compile the shader or upload placeholder textures during the
    // first layout burst. The Shell keeps exactly the same bounds and receives a cheap static skin;
    // once the real sampler set arrives this node is replaced in-place by the single OpenGL host.
    if (!backdrop.isReady) {
        Box(
            modifier = modifier.startupStaticGlassLayer(
                radius = radius,
                glassIntensity = glassIntensity
            )
        ) {}
        return
    }

    val sceneGroup = LocalGlassSceneGroup
    val useLegacyRenderer =
        sceneGroup == GlassSceneGroup.SettingsPage ||
            sceneGroup == GlassSceneGroup.AssistantPage

    // 设置页顶部状态卡片和首页聊天大玻璃共同复用实验室原版 OpenGL 完整宿主链：
    // 同一参数源、单样本优化、Compose 轮廓裁剪和旧 Renderer。
    if (useLegacyRenderer) {
        val currentSpec = LocalGlassBackdrop.current
        val legacySpec = remember(currentSpec) {
            currentSpec?.copy(borderStyle = legacyOpenGlReferenceStyle())
        }

        if (legacySpec != null) {
            CompositionLocalProvider(LocalGlassBackdrop provides legacySpec) {
                LegacyOpenGLGlassPreviewShell(
                    quality = legacySpec.quality,
                    glassIntensity = glassIntensity,
                    motionIntensity = legacySpec.motionIntensity,
                    radius = radius,
                    modifier = modifier,
                    coordinateSource = coordinateSource,
                    pressProgress = pressProgress,
                    pressCenter = pressCenter,
                    viewportTopInsetPx = viewportTopInsetPx
                ) {}
            }
        } else {
            OpenGLGlassCardLayer(
                radius = radius,
                glassIntensity = glassIntensity,
                coordinateSource = coordinateSource,
                modifier = modifier,
                pressProgress = pressProgress,
                pressCenter = pressCenter,
                viewportTopInsetPx = viewportTopInsetPx
            )
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
    val density = LocalDensity.current
    val densityScale = density.density
    val surfaceAnchor = LocalOpenGLGlassSurfaceAnchor.current.fraction
    val localViewportTopInsetPx = with(density) { LocalOpenGLGlassViewportTopInset.current.toPx() }
    val effectiveViewportTopInsetPx = max(viewportTopInsetPx, localViewportTopInsetPx)
    val foldoutClipRegistry = LocalGlassFoldoutClipRegistry.current
    foldoutClipRegistry?.version

    val clearBitmap = remember(backdrop.lensImage) { backdrop.lensImage.asAndroidBitmap() }
    val blurLowBitmap = remember(backdrop.blurLowImage) { backdrop.blurLowImage.asAndroidBitmap() }
    val blurMediumBitmap = remember(backdrop.blurMediumImage) { backdrop.blurMediumImage.asAndroidBitmap() }
    val blurHighBitmap = remember(backdrop.blurHighImage) { backdrop.blurHighImage.asAndroidBitmap() }

    val radiusPx = with(density) { radius.dp.toPx() }
    val intensity = border.newOpenGlGlassIntensity.takeIf { it > 0f }?.coerceIn(0.35f, 1.35f)
        ?: glassIntensity.coerceIn(0.35f, 1.35f)
    val cardOrigin = coordinateSource?.offsetRelativeTo(backdropOrigin) ?: Offset.Zero
    val press = pressProgress.coerceIn(0f, 1f)
    val pressX = pressCenter.x.coerceIn(0f, 1f)
    val rawPressY = pressCenter.y.coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val safeViewportTopInsetPx = effectiveViewportTopInsetPx.coerceIn(0f, (heightPx - 1f).coerceAtLeast(0f))
        val viewportHeightPx = (heightPx - safeViewportTopInsetPx).coerceAtLeast(1f)
        val mappedPressY = ((rawPressY * heightPx - safeViewportTopInsetPx) / viewportHeightPx)
            .coerceIn(0f, 1f)
        val rootWidthPx = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
        val rootHeightPx = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)

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
                val specDirty = view.setGlassSpec(
                    widthPx,
                    viewportHeightPx,
                    safeViewportTopInsetPx,
                    radiusPx,
                    intensity
                )
                val samplingDirty = view.setSamplingSpec(
                    cardOrigin.x,
                    cardOrigin.y + safeViewportTopInsetPx,
                    rootWidthPx,
                    rootHeightPx
                )
                val pressDirty = view.setPressSpec(press, pressX, mappedPressY)
                val textureDirty = view.setBackdropTextures(
                    clearBitmap = clearBitmap,
                    blurLowBitmap = blurLowBitmap,
                    blurMediumBitmap = blurMediumBitmap,
                    blurHighBitmap = blurHighBitmap
                )
                val blurDirty = view.setBackdropBlurAmount(backdrop.blurAmount)
                val styleDirty = view.setGlassStyle(rendererBorder, densityScale)
                val surfaceDirty = view.setStableSurfaceSize(
                    stableSurfaceWidthPx.roundToInt(),
                    stableSurfaceHeightPx.roundToInt(),
                    rootWidthPx.roundToInt(),
                    rootHeightPx.roundToInt()
                )

                if (surfaceDirty || specDirty || samplingDirty || pressDirty || textureDirty || blurDirty || styleDirty) {
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
