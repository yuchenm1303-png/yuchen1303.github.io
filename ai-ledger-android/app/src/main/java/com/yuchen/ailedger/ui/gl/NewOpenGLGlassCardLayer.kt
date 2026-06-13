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
import com.yuchen.ailedger.ui.LocalBackdropOrigin
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassBackdrop
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
    val backdrop = LocalBlurredBackdrop.current ?: return
    val border = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
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

    val radiusPx = with(density) { radius.dp.toPx() }.roundToInt().toFloat()
    val intensity = border.newOpenGlGlassIntensity.takeIf { it > 0f }?.coerceIn(0.35f, 1.35f)
        ?: glassIntensity.coerceIn(0.35f, 1.35f)
    val cardOrigin = coordinateSource?.offsetRelativeTo(backdropOrigin) ?: Offset.Zero
    val press = pressProgress.coerceIn(0f, 1f)
    val pressX = pressCenter.x.coerceIn(0f, 1f)
    val rawPressY = pressCenter.y.coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
        val heightPx = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
        val safeViewportTopInsetPx = effectiveViewportTopInsetPx.coerceIn(0f, (heightPx - 1f).coerceAtLeast(0f))
        val viewportHeightPx = (heightPx - safeViewportTopInsetPx).coerceAtLeast(1f)
        val mappedPressY = ((rawPressY * heightPx - safeViewportTopInsetPx) / viewportHeightPx)
            .coerceIn(0f, 1f)
        val rootWidthPx = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
        val rootHeightPx = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context -> WebOpenGLGlassCardHostView(context) },
            update = { view ->
                view.setStableSurfaceAnchor(surfaceAnchor)
                val surfaceDirty = view.setStableSurfaceSize(
                    widthPx.roundToInt(),
                    heightPx.roundToInt(),
                    rootWidthPx.roundToInt(),
                    rootHeightPx.roundToInt()
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
                val styleDirty = view.setGlassStyle(border, densityScale)
                if (surfaceDirty || specDirty || samplingDirty || pressDirty || textureDirty || blurDirty || styleDirty) {
                    view.requestRender()
                }
            }
        )
    }
}
