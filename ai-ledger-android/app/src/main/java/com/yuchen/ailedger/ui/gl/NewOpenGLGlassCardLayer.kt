package com.yuchen.ailedger.ui.gl

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.GlassSceneGroup
import com.yuchen.ailedger.ui.LocalBackdropOrigin
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassBackdrop
import com.yuchen.ailedger.ui.LocalGlassSceneGroup
import kotlin.math.max
import kotlin.math.roundToInt

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
    // 设置页当前状态卡片保留旧版 OpenGL 材质；其他 Shell 继续使用新版 Renderer。
    if (LocalGlassSceneGroup == GlassSceneGroup.SettingsPage) {
        OpenGLGlassCardLayer(
            radius = radius,
            glassIntensity = glassIntensity,
            coordinateSource = coordinateSource,
            modifier = modifier,
            pressProgress = pressProgress,
            pressCenter = pressCenter,
            viewportTopInsetPx = viewportTopInsetPx
        )
        return
    }

    val backdrop = LocalBlurredBackdrop.current ?: return
    val border = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
    val rendererBorder = remember(border) { border.onlyWebOpenGLRendererFields() }
    val backdropOrigin = LocalBackdropOrigin.current
    val density = LocalDensity.current
    val densityScale = density.density
    val surfaceAnchor = LocalOpenGLGlassSurfaceAnchor.current.fraction
    val localViewportTopInsetPx = with(density) { LocalOpenGLGlassViewportTopInset.current.toPx() }
    val effectiveViewportTopInsetPx = max(viewportTopInsetPx, localViewportTopInsetPx)

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
        // 玻璃真实几何保持浮点连续值，避免形状动画被逐像素取整后出现跳步。
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val safeViewportTopInsetPx = effectiveViewportTopInsetPx.coerceIn(0f, (heightPx - 1f).coerceAtLeast(0f))
        val viewportHeightPx = (heightPx - safeViewportTopInsetPx).coerceAtLeast(1f)
        val mappedPressY = ((rawPressY * heightPx - safeViewportTopInsetPx) / viewportHeightPx)
            .coerceIn(0f, 1f)
        val rootWidthPx = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
        val rootHeightPx = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)

        // TextureView/EGL Surface 始终使用稳定包络尺寸；玻璃形状只通过 uniform 连续变化。
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
                // 先把本帧最新几何和采样坐标写入 Renderer，避免 Surface 布局期间吞掉中间状态。
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

                // Surface 只在稳定包络真正增大或根视口改变时调整，不参与玻璃形状逐帧动画。
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

/**
 * Host 的样式判脏只看新版 Renderer 真正读取的字段。
 * 旧 OpenGL、Compose 普通玻璃和兼容字段变化不会再无意义唤醒 EGL。
 */
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
