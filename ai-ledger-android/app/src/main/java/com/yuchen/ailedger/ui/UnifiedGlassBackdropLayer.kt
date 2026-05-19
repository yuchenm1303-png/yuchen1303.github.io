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
    val bodyScale = (border.bodyAlpha / 0.20f).coerceIn(0.22f, 1.45f)
    val alpha = (glassIntensity * bodyScale).coerceIn(0.08f, 0.92f)
    val base = when (quality) {
        RenderQuality.Smooth -> 0.11f
        RenderQuality.Balanced -> 0.13f
        RenderQuality.Experimental -> 0.15f
    } * alpha
    val milk = when (quality) {
        RenderQuality.Smooth -> 0.030f
        RenderQuality.Balanced -> 0.038f
        RenderQuality.Experimental -> 0.048f
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
            alpha = (backdropAlpha * (0.58f + border.bodyAlpha.coerceIn(0f, 0.36f))).coerceIn(0.18f, 0.86f),
            blendMode = BlendMode.SrcOver
        )
        drawRect(Color(0xFF72859A).copy(alpha = base * 0.16f), Offset(visibleRect.left, visibleRect.top), Size(visibleRect.width, visibleRect.height))
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = milk * 0.24f),
                    Color(0xFFDCE5EF).copy(alpha = milk * 0.10f),
                    Color(0xFF172333).copy(alpha = base * 0.08f)
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
    val edgeWidth = (border.ringWidthDp.dp.toPx() * 1.10f + border.edgeBlurDp.dp.toPx() * 0.42f)
        .coerceIn(10.dp.toPx(), min(w, h) * 0.32f)
    val edgePull = (border.edgePullDp.dp.toPx() * 0.92f).coerceIn(0f, min(w, h) * 0.86f)
    val edgeAlpha = (border.edgeAlpha * (1.02f + strength * 0.38f) * border.edgeBrightness.coerceIn(0.72f, 1.30f)).coerceIn(0f, 0.74f)
    if (edgeAlpha <= 0.01f || edgePull <= 0.5f) return
    val path = Path().apply { addRoundRect(RoundRect(itemRect, CornerRadius(corner, corner))) }
    clipPath(path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (drawShaderLens(backdrop, itemRect, visibleRect, sampleOffset, corner, edgeWidth, edgePull, edgeAlpha, border)) return@clipPath
        }
        drawFallbackLens(backdrop, itemRect, visibleRect, sampleOffset, edgePull * 0.34f, edgeAlpha * 0.54f)
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
            setInputShader("backdrop", BitmapShader(backdrop.image.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            setFloatUniform("itemPos", itemRect.left, itemRect.top)
            setFloatUniform("itemSize", itemRect.width, itemRect.height)
            setFloatUniform("sampleOffset", sampleOffset.x, sampleOffset.y)
            setFloatUniform("backdropScale", backdrop.scale)
            setFloatUniform("radius", radius)
            setFloatUniform("edgeWidth", edgeWidth)
            setFloatUniform("edgePull", edgePull)
            setFloatUniform("edgeAlpha", edgeAlpha)
            setFloatUniform("edgeContrast", border.edgeContrast.coerceIn(1.00f, 1.90f))
            setFloatUniform("edgeSaturation", border.edgeSaturation.coerceIn(1.00f, 2.00f))
            setFloatUniform("edgeBrightness", border.edgeBrightness.coerceIn(0.82f, 1.30f))
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
    val insetX = sourceInset.coerceIn(0f, w * 0.24f)
    val insetY = sourceInset.coerceIn(0f, h * 0.24f)
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
    drawImage(backdrop.image, IntOffset(srcX, srcY), IntSize(srcW, srcH), IntOffset(visibleRect.left.roundToInt(), visibleRect.top.roundToInt()), IntSize(dstW, dstH), alpha = alpha.coerceIn(0f, 0.28f), blendMode = BlendMode.SrcOver)
}

private fun DrawScope.drawGlassHighlights(itemRect: Rect, radius: Int, border: GlassBorderStyle) {
    val w = itemRect.width
    val h = itemRect.height
    val corner = radius.dp.toPx()
    fun p(x: Float, y: Float) = Offset(itemRect.left + x, itemRect.top + y)
    fun s(width: Float, height: Float) = Size(width, height)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = border.topHighlightAlpha * 0.14f), Color(0xFFEAF3FF).copy(alpha = border.topHighlightAlpha * 0.024f), Color.Transparent), itemRect.top, itemRect.top + h * 0.20f),
        topLeft = p(1.dp.toPx(), 1.dp.toPx()),
        size = s(w - 2.dp.toPx(), h - 2.dp.toPx()),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 1.6.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(listOf(Color.White.copy(alpha = (border.outerStrokeAlpha * 0.28f).coerceIn(0f, 0.20f)), Color(0xFFE8F4FF).copy(alpha = border.outerStrokeAlpha * 0.09f), Color.White.copy(alpha = border.outerStrokeAlpha * 0.022f), Color(0xFFFFD9E5).copy(alpha = border.outerStrokeAlpha * 0.034f)), p(0f, 0f), p(w, h)),
        topLeft = p(0.65.dp.toPx(), 0.65.dp.toPx()),
        size = s(w - 1.3.dp.toPx(), h - 1.3.dp.toPx()),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 0.66.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = border.cornerGlintAlpha * 0.48f), Color(0xFFEAF5FF).copy(alpha = border.cornerGlintAlpha * 0.13f), Color.Transparent), p(w * 0.10f, h * 0.08f), w * 0.28f), p(0f, 0f), s(w, h), blendMode = BlendMode.Screen)
    if (border.bottomShadowAlpha > 0.01f) {
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xFF071225).copy(alpha = border.bottomShadowAlpha * 0.20f)), itemRect.top + h * 0.58f, itemRect.bottom),
            topLeft = p(2.dp.toPx(), 2.dp.toPx()),
            size = s(w - 4.dp.toPx(), h - 4.dp.toPx()),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 1.dp.toPx()),
            blendMode = BlendMode.Multiply
        )
    }
}

private const val GLASS_LENS_SHADER = """
uniform shader backdrop;
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
float roundedBoxSdf(float2 p, float2 halfSize, float r) {
    float2 b = max(halfSize - float2(r, r), float2(0.0, 0.0));
    float2 q = abs(p) - b;
    return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - r;
}
float3 adjustColor(float3 color) {
    float luma = dot(color, float3(0.2126, 0.7152, 0.0722));
    color = mix(float3(luma, luma, luma), color, edgeSaturation * 1.10);
    color = (color - float3(0.5, 0.5, 0.5)) * edgeContrast * 1.06 + float3(0.5, 0.5, 0.5);
    return clamp(color * edgeBrightness, float3(0.0, 0.0, 0.0), float3(1.0, 1.0, 1.0));
}
half4 main(float2 coord) {
    float2 local = coord - itemPos;
    float2 center = itemSize * 0.5;
    float2 p = local - center;
    float2 halfSize = itemSize * 0.5;
    float sd = roundedBoxSdf(p, halfSize, radius);
    float inside = max(-sd, 0.0);

    float2 dx = float2(1.0, 0.0);
    float2 dy = float2(0.0, 1.0);
    float gx = roundedBoxSdf(p + dx, halfSize, radius) - roundedBoxSdf(p - dx, halfSize, radius);
    float gy = roundedBoxSdf(p + dy, halfSize, radius) - roundedBoxSdf(p - dy, halfSize, radius);
    float2 edgeNormal = normalize(float2(gx, gy) + float2(0.0001, 0.0001));
    float2 edgeTangent = float2(-edgeNormal.y, edgeNormal.x);

    float ringU = clamp(inside / max(edgeWidth, 1.0), 0.0, 1.0);
    float rimGate = 1.0 - smoothstep(0.0, 1.0, ringU);
    float crest = 1.0 - smoothstep(0.0, max(edgeWidth * 0.14, 1.0), inside);
    float colorLine = smoothstep(max(edgeWidth * 0.04, 0.5), max(edgeWidth * 0.12, 1.0), inside) * (1.0 - smoothstep(max(edgeWidth * 0.24, 2.0), max(edgeWidth * 0.46, 3.0), inside));
    float softRim = 1.0 - smoothstep(max(edgeWidth * 0.28, 1.0), max(edgeWidth * 1.16, 2.0), inside);
    float maxSize = max(max(itemSize.x, itemSize.y), 1.0);
    float cornerCurve = clamp(abs(edgeNormal.x * edgeNormal.y) * 2.20, 0.0, 1.0);
    float tangentPhase = clamp(dot(p / maxSize, edgeTangent) * 2.80, -1.0, 1.0);

    float2 baseCoord = (sampleOffset + local) * backdropScale;
    half4 base = backdrop.eval(baseCoord);
    float3 baseColor = float3(base.r, base.g, base.b);

    float2 surfaceLocal = local + edgeNormal * inside;
    float squeezeDepth = mix(
        min(max(itemSize.x, itemSize.y) * 0.30, edgePull * 0.70),
        edgeWidth * 0.36,
        pow(ringU, 0.52)
    ) * (1.0 + cornerCurve * 0.16);
    float tangentSqueeze = edgeWidth * tangentPhase * rimGate * (0.44 + cornerCurve * 0.46);
    float2 squeezedLocal = surfaceLocal - edgeNormal * squeezeDepth + edgeTangent * tangentSqueeze;
    float2 crestLocal = surfaceLocal - edgeNormal * min(edgePull * 0.26, edgeWidth * 0.78) + edgeTangent * tangentSqueeze * 0.28;
    float2 inwardLocal = local - edgeNormal * min(edgePull * 0.07, edgeWidth * 0.24) * softRim;

    half4 sqR = backdrop.eval((sampleOffset + squeezedLocal + edgeNormal * 1.55 + edgeTangent * 0.62) * backdropScale);
    half4 sqG = backdrop.eval((sampleOffset + squeezedLocal) * backdropScale);
    half4 sqB = backdrop.eval((sampleOffset + squeezedLocal - edgeNormal * 1.30 - edgeTangent * 0.54) * backdropScale);
    half4 crestA = backdrop.eval((sampleOffset + crestLocal + edgeNormal * 0.78 + edgeTangent * 0.28) * backdropScale);
    half4 crestB = backdrop.eval((sampleOffset + crestLocal - edgeNormal * 0.62 - edgeTangent * 0.24) * backdropScale);
    half4 inward = backdrop.eval((sampleOffset + inwardLocal) * backdropScale);

    float3 squeezedColor = adjustColor(float3(sqR.r, sqG.g, sqB.b));
    float3 crestColor = adjustColor(float3(crestA.r, sqG.g, crestB.b));
    float3 inwardColor = float3(inward.r, inward.g, inward.b);

    float squeezeMix = clamp(rimGate * (0.74 - ringU * 0.22) * edgeAlpha, 0.0, 0.48);
    float crestMix = clamp(crest * (0.36 + cornerCurve * 0.08) * edgeAlpha, 0.0, 0.30);
    float lineMix = clamp(colorLine * (0.72 + cornerCurve * 0.22) * edgeAlpha, 0.0, 0.42);
    float inwardMix = clamp(softRim * 0.07, 0.0, 0.08);

    float3 refracted = mix(baseColor, inwardColor, inwardMix);
    refracted = mix(refracted, squeezedColor, squeezeMix);
    refracted = mix(refracted, crestColor, crestMix + lineMix * 0.20);
    refracted = clamp(refracted + float3(crest * 0.035 + colorLine * 0.038), float3(0.0), float3(1.0));

    float alphaField = clamp(rimGate * 0.56 + crest * 0.22 + colorLine * 0.28 + cornerCurve * colorLine * 0.12, 0.0, 1.0);
    float a = clamp(edgeAlpha * alphaField, 0.0, 0.46);
    return half4(refracted, a);
}
"""