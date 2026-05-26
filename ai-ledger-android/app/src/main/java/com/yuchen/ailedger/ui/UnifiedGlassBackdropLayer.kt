package com.yuchen.ailedger.ui

import android.annotation.SuppressLint
import android.graphics.BitmapShader
import android.graphics.Paint as AndroidPaint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val GLASS_LENS_TAG = "GlassLensShader"
private const val SOFT_LENS_DETAIL_MIX = 0.34f
private const val LENS_BAND_STRENGTH = 1.25f
private const val SHADER_DIAGNOSTIC_TINT = 0.0f

@Volatile
private var hasLoggedShaderLensFailure = false

@Composable
fun UnifiedGlassBackdropLayer(modifier: Modifier = Modifier) {
    val registry = LocalGlassItemRegistry.current
    val backdrop = LocalBlurredBackdrop.current
    val origin = LocalBackdropOrigin.current
    val ticker = LocalBackdropFrameTicker.current
    val spec = LocalGlassBackdrop.current

    Canvas(modifier = modifier) {
        ticker?.frameNanos
        val cached = backdrop ?: return@Canvas
        val border = spec?.borderStyle ?: GlassBorderStyle()
        val screen = Rect(0f, 0f, size.width, size.height)
        registry?.snapshot().orEmpty().forEach { item ->
            if (!item.coordinates.isAttached()) return@forEach
            val itemSize = item.coordinates.itemSize()
            if (itemSize.width <= 0 || itemSize.height <= 0) return@forEach
            val topLeft = item.coordinates.rootOffset()
            val rect = Rect(topLeft, Size(itemSize.width.toFloat(), itemSize.height.toFloat()))
            val visible = rect.intersectionOrNull(screen) ?: return@forEach
            val sampleOffset = item.coordinates.offsetRelativeTo(origin)
            drawGlassBody(cached, rect, visible, sampleOffset, item.radius, item.quality, item.glassIntensity, item.backdropAlpha, border)
            drawContinuousLens(cached, rect, visible, sampleOffset, item.radius, border, item.edgeStrength)
            drawGlassHighlights(rect, item.radius, border)
        }
    }
}

private fun Rect.intersectionOrNull(other: Rect): Rect? {
    val left = max(left, other.left)
    val top = max(top, other.top)
    val right = min(right, other.right)
    val bottom = min(bottom, other.bottom)
    return if (right > left && bottom > top) Rect(left, top, right, bottom) else null
}

private fun DrawScope.drawGlassBody(
    backdrop: BlurredBackdropBitmap,
    itemRect: Rect,
    visibleRect: Rect,
    sampleOffset: Offset,
    radius: Int,
    quality: RenderQuality,
    glassIntensity: Float,
    backdropAlpha: Float,
    border: GlassBorderStyle
) {
    val corner = radius.dp.toPx()
    val bodyScale = (border.bodyAlpha / 0.20f).coerceIn(0.35f, 2.20f)
    val alpha = (glassIntensity * bodyScale).coerceIn(0.12f, 1.25f)
    val base = when (quality) {
        RenderQuality.Smooth -> 0.15f
        RenderQuality.Balanced -> 0.18f
        RenderQuality.Experimental -> 0.21f
    } * alpha
    val milk = when (quality) {
        RenderQuality.Smooth -> 0.040f
        RenderQuality.Balanced -> 0.052f
        RenderQuality.Experimental -> 0.064f
    } * alpha
    val path = Path().apply { addRoundRect(RoundRect(itemRect, CornerRadius(corner, corner))) }
    val srcX = ((sampleOffset.x + visibleRect.left - itemRect.left) * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.width - 1)
    val srcY = ((sampleOffset.y + visibleRect.top - itemRect.top) * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.height - 1)
    val dstW = visibleRect.width.roundToInt().coerceAtLeast(1)
    val dstH = visibleRect.height.roundToInt().coerceAtLeast(1)
    val srcW = (dstW * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.width - srcX)
    val srcH = (dstH * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.height - srcY)
    clipPath(path) {
        drawImage(
            image = backdrop.image,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(visibleRect.left.roundToInt(), visibleRect.top.roundToInt()),
            dstSize = IntSize(dstW, dstH),
            alpha = (backdropAlpha * (0.76f + border.bodyAlpha.coerceIn(0f, 0.50f))).coerceIn(0.25f, 1f),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            color = Color(0xFF72859A).copy(alpha = base * 0.22f),
            topLeft = Offset(visibleRect.left, visibleRect.top),
            size = Size(visibleRect.width, visibleRect.height)
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = milk * 0.34f),
                    Color(0xFFDCE5EF).copy(alpha = milk * 0.15f),
                    Color(0xFF172333).copy(alpha = base * 0.10f)
                ),
                startY = itemRect.top,
                endY = itemRect.bottom
            ),
            topLeft = Offset(visibleRect.left, visibleRect.top),
            size = Size(visibleRect.width, visibleRect.height)
        )
    }
}

private fun DrawScope.drawContinuousLens(
    backdrop: BlurredBackdropBitmap,
    itemRect: Rect,
    visibleRect: Rect,
    sampleOffset: Offset,
    radius: Int,
    border: GlassBorderStyle,
    strength: Float
) {
    val w = itemRect.width
    val h = itemRect.height
    if (w <= 4f || h <= 4f) return
    val corner = radius.dp.toPx()
    val edgeWidth = (border.ringWidthDp.dp.toPx() + border.edgeBlurDp.dp.toPx() * 0.38f).coerceIn(6.dp.toPx(), min(w, h) * 0.40f)
    val edgePull = border.edgePullDp.dp.toPx().coerceIn(0f, min(w, h) * 1.08f)
    val edgeAlpha = (border.edgeAlpha * (0.58f + strength * 0.42f) * border.edgeBrightness.coerceIn(0.72f, 1.25f)).coerceIn(0f, 0.86f)
    if (edgeAlpha <= 0.01f || edgePull <= 0.5f) return
    val path = Path().apply { addRoundRect(RoundRect(itemRect, CornerRadius(corner, corner))) }
    clipPath(path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (drawShaderLens(backdrop, itemRect, visibleRect, sampleOffset, corner, edgeWidth, edgePull, edgeAlpha, border)) return@clipPath
        }
        drawFallbackLens(backdrop, itemRect, visibleRect, sampleOffset, edgePull * 0.28f, edgeAlpha * 0.40f)
    }
}

@SuppressLint("NewApi")
private fun DrawScope.drawShaderLens(
    backdrop: BlurredBackdropBitmap,
    itemRect: Rect,
    visibleRect: Rect,
    sampleOffset: Offset,
    radius: Float,
    edgeWidth: Float,
    edgePull: Float,
    edgeAlpha: Float,
    border: GlassBorderStyle
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    return runCatching {
        val lensShader = RuntimeShader(GLASS_LENS_SHADER).apply {
            setInputShader("backdropBlur", BitmapShader(backdrop.image.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            setInputShader("backdropLens", BitmapShader(backdrop.lensImage.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            setFloatUniform("itemPos", itemRect.left, itemRect.top)
            setFloatUniform("itemSize", itemRect.width, itemRect.height)
            setFloatUniform("sampleOffset", sampleOffset.x, sampleOffset.y)
            setFloatUniform("backdropScale", backdrop.scale)
            setFloatUniform("radius", radius)
            setFloatUniform("edgeWidth", edgeWidth)
            setFloatUniform("edgePull", edgePull)
            setFloatUniform("edgeAlpha", edgeAlpha)
            setFloatUniform("edgeContrast", border.edgeContrast.coerceIn(0.70f, 1.95f))
            setFloatUniform("edgeSaturation", border.edgeSaturation.coerceIn(0.60f, 2.25f))
            setFloatUniform("edgeBrightness", border.edgeBrightness.coerceIn(0.60f, 1.60f))
            setFloatUniform("lensMix", SOFT_LENS_DETAIL_MIX)
            setFloatUniform("bandStrength", LENS_BAND_STRENGTH)
            setFloatUniform("diagnosticTint", SHADER_DIAGNOSTIC_TINT)
        }
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            shader = lensShader
            isDither = true
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(visibleRect.left, visibleRect.top, visibleRect.right, visibleRect.bottom, paint)
        }
        true
    }.onFailure { error ->
        if (!hasLoggedShaderLensFailure) {
            hasLoggedShaderLensFailure = true
            Log.w(GLASS_LENS_TAG, "RuntimeShader lens failed; falling back to bitmap stretch lens.", error)
        }
    }.getOrDefault(false)
}

private fun DrawScope.drawFallbackLens(
    backdrop: BlurredBackdropBitmap,
    itemRect: Rect,
    visibleRect: Rect,
    sampleOffset: Offset,
    sourceInset: Float,
    alpha: Float
) {
    val w = itemRect.width.coerceAtLeast(1f)
    val h = itemRect.height.coerceAtLeast(1f)
    val insetX = sourceInset.coerceIn(0f, w * 0.20f)
    val insetY = sourceInset.coerceIn(0f, h * 0.20f)
    val srcWLocal = (w - insetX * 2f).coerceAtLeast(1f)
    val srcHLocal = (h - insetY * 2f).coerceAtLeast(1f)
    val relX = visibleRect.left - itemRect.left
    val relY = visibleRect.top - itemRect.top
    val srcLocalX = insetX + relX * srcWLocal / w
    val srcLocalY = insetY + relY * srcHLocal / h
    val dstW = visibleRect.width.roundToInt().coerceAtLeast(1)
    val dstH = visibleRect.height.roundToInt().coerceAtLeast(1)
    val srcX = ((sampleOffset.x + srcLocalX) * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.width - 1)
    val srcY = ((sampleOffset.y + srcLocalY) * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.height - 1)
    val srcW = (visibleRect.width * srcWLocal / w * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.width - srcX)
    val srcH = (visibleRect.height * srcHLocal / h * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.height - srcY)
    drawImage(
        image = backdrop.image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(visibleRect.left.roundToInt(), visibleRect.top.roundToInt()),
        dstSize = IntSize(dstW, dstH),
        alpha = alpha.coerceIn(0f, 0.34f),
        blendMode = BlendMode.SrcOver
    )
}

private fun DrawScope.drawGlassHighlights(itemRect: Rect, radius: Int, border: GlassBorderStyle) {
    val w = itemRect.width
    val h = itemRect.height
    val corner = radius.dp.toPx()
    fun p(x: Float, y: Float) = Offset(itemRect.left + x, itemRect.top + y)
    fun s(width: Float, height: Float) = Size(width, height)
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = border.topHighlightAlpha * 0.16f),
                Color(0xFFEAF3FF).copy(alpha = border.topHighlightAlpha * 0.028f),
                Color.Transparent
            ),
            startY = itemRect.top,
            endY = itemRect.top + h * 0.20f
        ),
        topLeft = p(1.dp.toPx(), 1.dp.toPx()),
        size = s(w - 2.dp.toPx(), h - 2.dp.toPx()),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 1.9.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = (border.outerStrokeAlpha * 0.32f).coerceIn(0f, 0.22f)),
                Color(0xFFE8F4FF).copy(alpha = border.outerStrokeAlpha * 0.11f),
                Color.White.copy(alpha = border.outerStrokeAlpha * 0.026f),
                Color(0xFFFFD9E5).copy(alpha = border.outerStrokeAlpha * 0.040f)
            ),
            start = p(0f, 0f),
            end = p(w, h)
        ),
        topLeft = p(0.65.dp.toPx(), 0.65.dp.toPx()),
        size = s(w - 1.3.dp.toPx(), h - 1.3.dp.toPx()),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 0.70.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = border.innerStrokeAlpha * 0.22f),
                Color(0xFFB9D7FF).copy(alpha = border.innerStrokeAlpha * 0.052f),
                Color(0xFFFFE5F0).copy(alpha = border.innerStrokeAlpha * 0.050f),
                Color.Transparent
            ),
            start = p(0f, h * 0.05f),
            end = p(w, h)
        ),
        topLeft = p(2.dp.toPx(), 2.dp.toPx()),
        size = s(w - 4.dp.toPx(), h - 4.dp.toPx()),
        cornerRadius = CornerRadius((corner - 2.dp.toPx()).coerceAtLeast(0f), (corner - 2.dp.toPx()).coerceAtLeast(0f)),
        style = Stroke(width = 0.45.dp.toPx()),
        blendMode = BlendMode.Screen
    )
}

private const val GLASS_LENS_SHADER = """
uniform shader backdropBlur;
uniform shader backdropLens;
uniform float2 itemPos;
uniform float2 itemSize;
uniform float2 sampleOffset;
uniform float backdropScale;
uniform float radius;
uniform float edgeWidth;
uniform float edgePull;
uniform float edgeAlpha;
uniform float edgeContrast;
uniform float edgeSaturation;
uniform float edgeBrightness;
uniform float lensMix;
uniform float bandStrength;
uniform float diagnosticTint;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float edgeMaskFromDistance(float distInside, float edgeWidthValue) {
    return smoothstep(edgeWidthValue, 0.0, distInside);
}

half4 main(float2 fragCoord) {
    float2 local = fragCoord - itemPos;
    float2 halfSize = itemSize * 0.5;
    float2 centered = local - halfSize;
    float dist = sdRoundRect(centered, halfSize, radius);
    float distInside = max(0.0, -dist);
    float rimMask = edgeMaskFromDistance(distInside, max(edgeWidth, 1.0));
    float2 fromCenter = normalize(centered + float2(0.0001, 0.0001));
    float pull = edgePull * rimMask * 0.22;
    float2 sampleCoord = (sampleOffset + local - fromCenter * pull) * backdropScale;
    half4 blurColor = backdropBlur.eval(sampleCoord);
    half4 lensColor = backdropLens.eval(sampleCoord);
    half3 mixedColor = mix(blurColor.rgb, lensColor.rgb, half(lensMix * rimMask));
    float lum = dot(float3(mixedColor), float3(0.299, 0.587, 0.114));
    float3 saturated = mix(float3(lum), float3(mixedColor), edgeSaturation);
    saturated = (saturated - 0.5) * edgeContrast + 0.5;
    saturated *= edgeBrightness;
    half3 finalColor = half3(saturated) + half3(0.025, 0.035, 0.055) * half(rimMask * bandStrength);
    finalColor += half3(diagnosticTint, 0.0, 0.0) * half(rimMask);
    return half4(finalColor, half(edgeAlpha * rimMask));
}
"""
