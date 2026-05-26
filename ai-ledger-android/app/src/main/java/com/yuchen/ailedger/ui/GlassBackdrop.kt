package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalView
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

@Composable
fun SampledWeatherGlassBackdrop(
    modifier: Modifier = Modifier,
    radius: Int,
    coordinateSource: GlassCoordinateSource,
    quality: RenderQuality,
    motionIntensity: Float,
    theme: BackgroundTheme,
    blurRadiusDp: Int = 112,
    liftAlpha: Float = 1f
) {
    val view = LocalView.current
    val cachedBackdrop = LocalBlurredBackdrop.current
    val spec = LocalGlassBackdrop.current
    val origin = LocalBackdropOrigin.current
    val ticker = LocalBackdropFrameTicker.current
    val params = spec?.params ?: BackdropDebugParams()
    val alpha = liftAlpha.coerceIn(0.34f, 1f)
    val baseAlpha = when (quality) { RenderQuality.Smooth -> 0.15f; RenderQuality.Balanced -> 0.18f; RenderQuality.Experimental -> 0.21f } * alpha
    val milkAlpha = when (quality) { RenderQuality.Smooth -> 0.040f; RenderQuality.Balanced -> 0.052f; RenderQuality.Experimental -> 0.064f } * alpha
    val highlightAlpha = when (quality) { RenderQuality.Smooth -> 0.036f; RenderQuality.Balanced -> 0.046f; RenderQuality.Experimental -> 0.056f } * alpha
    val backdropAlpha = when (quality) { RenderQuality.Smooth -> 0.90f; RenderQuality.Balanced -> 0.94f; RenderQuality.Experimental -> 0.98f }
    val fallbackBlur = when (quality) { RenderQuality.Smooth -> blurRadiusDp * 0.24f; RenderQuality.Balanced -> blurRadiusDp * 0.32f; RenderQuality.Experimental -> blurRadiusDp * 0.40f }.coerceIn(14f, 46f)
    val spreadPx = when (quality) { RenderQuality.Smooth -> blurRadiusDp * 0.46f; RenderQuality.Balanced -> blurRadiusDp * 0.62f; RenderQuality.Experimental -> blurRadiusDp * 0.76f }.coerceIn(34f, 96f)

    Canvas(modifier.clip(RoundedCornerShape(radius.dp)).then(if (cachedBackdrop == null) Modifier.blur(fallbackBlur.dp) else Modifier)) {
        ticker?.frameNanos
        val sampleOffset = coordinateSource.offsetRelativeTo(origin)
        if (cachedBackdrop != null) {
            drawVisibleBackdropImage(cachedBackdrop, sampleOffset, backdropAlpha)
        } else {
            val rootW = if (view.width > 0) view.width.toFloat() else size.width + sampleOffset.x
            val rootH = if (view.height > 0) view.height.toFloat() else size.height + sampleOffset.y
            drawSpreadBackdropSamples(rootW, rootH, theme, params, sampleOffset, spreadPx)
        }
        drawRect(Brush.verticalGradient(listOf(Color(0xFFE0EAF3).copy(alpha = milkAlpha * 0.48f), Color(0xFF9AADBF).copy(alpha = baseAlpha * 0.28f), Color(0xFF40576D).copy(alpha = baseAlpha * 0.30f))), blendMode = BlendMode.SrcOver)
        drawRect(Color(0xFF72859A).copy(alpha = baseAlpha * 0.26f), blendMode = BlendMode.SrcOver)
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = milkAlpha * 0.46f), Color(0xFFDCE5EF).copy(alpha = milkAlpha * 0.22f), Color(0xFF9BAEC1).copy(alpha = milkAlpha * 0.10f), Color(0xFF172333).copy(alpha = baseAlpha * 0.14f))), blendMode = BlendMode.SrcOver)
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = highlightAlpha * 0.42f), Color.White.copy(alpha = highlightAlpha * 0.08f), Color.Transparent), center = Offset(size.width * 0.42f, size.height * 0.08f), radius = size.width * 0.98f), blendMode = BlendMode.Screen)
    }
}

private fun DrawScope.drawVisibleBackdropImage(backdrop: BlurredBackdropBitmap, sampleOffset: Offset, alpha: Float) {
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

private fun DrawScope.drawSpreadBackdropSamples(rootW: Float, rootH: Float, theme: BackgroundTheme, params: BackdropDebugParams, sampleOffset: Offset, spreadPx: Float) {
    val samples = listOf(Offset.Zero to 0.090f, Offset(-0.55f, 0f) to 0.070f, Offset(0.55f, 0f) to 0.070f, Offset(0f, -0.55f) to 0.070f, Offset(0f, 0.55f) to 0.070f, Offset(-1.05f, -0.72f) to 0.055f, Offset(1.05f, 0.72f) to 0.055f, Offset(-0.72f, 1.05f) to 0.048f, Offset(0.72f, -1.05f) to 0.048f)
    samples.forEach { (unitOffset, sampleAlpha) ->
        withTransform({ translate(left = -sampleOffset.x + unitOffset.x * spreadPx, top = -sampleOffset.y + unitOffset.y * spreadPx) }) {
            drawWeatherNightBackground(rootW, rootH, theme, sampleAlpha, params)
        }
    }
    withTransform({ translate(left = -sampleOffset.x, top = -sampleOffset.y) }) { drawWeatherNightBackgroundGlow(rootW, rootH, theme, 0.82f, params) }
}

@Composable
fun SampledWeatherEdgeRefraction(modifier: Modifier = Modifier, radius: Int, coordinateSource: GlassCoordinateSource, quality: RenderQuality, motionIntensity: Float, theme: BackgroundTheme, strength: Float = 1f) {
    val spec = LocalGlassBackdrop.current
    val origin = LocalBackdropOrigin.current
    val ticker = LocalBackdropFrameTicker.current
    val border = spec?.borderStyle ?: GlassBorderStyle()
    val alpha = strength.coerceIn(0f, 0.34f)
    Canvas(modifier.clip(RoundedCornerShape(radius.dp))) {
        ticker?.frameNanos
        val sampleOffset = coordinateSource.offsetRelativeTo(origin)
        val w = size.width
        val h = size.height
        val corner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
        val phase = ((sampleOffset.x + sampleOffset.y) / 900f) % 1f
        val outerInset = 0.55.dp.toPx()
        val midInset = 2.70.dp.toPx()
        val innerInset = 7.0.dp.toPx()
        drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.055f * alpha), Color.White.copy(alpha = 0.018f * alpha), Color.Transparent, Color.Black.copy(alpha = 0.010f * alpha), Color.White.copy(alpha = 0.010f * alpha)), Offset(w * (phase - 0.18f), 0f), Offset(w * (phase + 0.82f), h)), Offset(outerInset, outerInset), Size(w - outerInset * 2f, h - outerInset * 2f), corner, style = Stroke(8.5.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.070f * alpha), Color.White.copy(alpha = 0.018f * alpha), Color.Transparent), endY = h * 0.30f), Offset(midInset, midInset), Size(w - midInset * 2f, h - midInset * 2f), corner, style = Stroke(5.6.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.030f * alpha), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.010f * alpha), Color.White.copy(alpha = 0.016f * alpha))), Offset(midInset, midInset), Size(w - midInset * 2f, h - midInset * 2f), corner, style = Stroke(4.8.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.004f * alpha), Color.Black.copy(alpha = 0.018f * alpha)), startY = h * 0.48f, endY = h), Offset(innerInset, innerInset), Size(w - innerInset * 2f, h - innerInset * 2f), corner, style = Stroke(2.4.dp.toPx()), blendMode = BlendMode.Multiply)
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = border.outerStrokeAlpha), Color.White.copy(alpha = border.outerStrokeAlpha * 0.34f), Color.White.copy(alpha = border.outerStrokeAlpha * 0.12f)), endY = h), Offset(outerInset, outerInset), Size(w - outerInset * 2f, h - outerInset * 2f), corner, style = Stroke(1.15.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = border.innerStrokeAlpha), Color.Transparent, Color.White.copy(alpha = border.innerStrokeAlpha * 0.28f)), endY = h), Offset(midInset, midInset), Size(w - midInset * 2f, h - midInset * 2f), corner, style = Stroke(0.82.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = border.topHighlightAlpha * 0.38f), Color.Transparent), Offset(w * (phase - 0.32f), 0f), Offset(w * (phase + 0.18f), h * 0.18f)), Offset(outerInset, outerInset), Size(w - outerInset * 2f, h - outerInset * 2f), corner, style = Stroke(1.0.dp.toPx()), blendMode = BlendMode.Plus)
        drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = border.bottomShadowAlpha)), startY = h * 0.52f, endY = h), Offset(midInset, midInset), Size(w - midInset * 2f, h - midInset * 2f), corner, style = Stroke(1.1.dp.toPx()), blendMode = BlendMode.Multiply)
    }
}
