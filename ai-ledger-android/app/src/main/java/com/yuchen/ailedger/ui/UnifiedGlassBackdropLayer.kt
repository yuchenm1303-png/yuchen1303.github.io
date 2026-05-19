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
        drawRect(Color(0xFF72859A).copy(alpha = base * 0.22f), Offset(visibleRect.left, visibleRect.top), Size(visibleRect.width, visibleRect.height))
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
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
    val edgeWidth = (border.ringWidthDp.dp.toPx() * 1.76f + border.edgeBlurDp.dp.toPx() * 1.18f).coerceIn(22.dp.toPx(), min(w, h) * 0.64f)
    val edgePull = (border.edgePullDp.dp.toPx() * 1.88f).coerceIn(0f, min(w, h) * 1.62f)
    val edgeAlpha = (border.edgeAlpha * (1.76f + strength * 0.78f) * border.edgeBrightness.coerceIn(0.72f, 1.40f)).coerceIn(0f, 1.56f)
    if (edgeAlpha <= 0.01f || edgePull <= 0.5f) return
    val path = Path().apply { addRoundRect(RoundRect(itemRect, CornerRadius(corner, corner))) }
    clipPath(path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (drawShaderLens(backdrop, itemRect, visibleRect, sampleOffset, corner, edgeWidth, edgePull, edgeAlpha, border)) return@clipPath
        }
        drawFallbackLens(backdrop, itemRect, visibleRect, sampleOffset, edgePull * 0.36f, edgeAlpha * 0.58f)
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
            setFloatUniform("edgeContrast", border.edgeContrast.coerceIn(0.96f, 2.80f))
            setFloatUniform("edgeSaturation", border.edgeSaturation.coerceIn(0.92f, 3.00f))
            setFloatUniform("edgeBrightness", border.edgeBrightness.coerceIn(0.72f, 1.72f))
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
    drawImage(backdrop.image, IntOffset(srcX, srcY), IntSize(srcW, srcH), IntOffset(visibleRect.left.roundToInt(), visibleRect.top.roundToInt()), IntSize(dstW, dstH), alpha = alpha.coerceIn(0f, 0.50f), blendMode = BlendMode.SrcOver)
}

private fun DrawScope.drawGlassHighlights(itemRect: Rect, radius: Int, border: GlassBorderStyle) {
    val w = itemRect.width
    val h = itemRect.height
    val corner = radius.dp.toPx()
    fun p(x: Float, y: Float) = Offset(itemRect.left + x, itemRect.top + y)
    fun s(width: Float, height: Float) = Size(width, height)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = border.topHighlightAlpha * 0.16f), Color(0xFFEAF3FF).copy(alpha = border.topHighlightAlpha * 0.028f), Color.Transparent), itemRect.top, itemRect.top + h * 0.20f),
        topLeft = p(1.dp.toPx(), 1.dp.toPx()),
        size = s(w - 2.dp.toPx(), h - 2.dp.toPx()),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 1.9.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(listOf(Color.White.copy(alpha = (border.outerStrokeAlpha * 0.32f).coerceIn(0f, 0.22f)), Color(0xFFE8F4FF).copy(alpha = border.outerStrokeAlpha * 0.11f), Color.White.copy(alpha = border.outerStrokeAlpha * 0.026f), Color(0xFFFFD9E5).copy(alpha = border.outerStrokeAlpha * 0.040f)), p(0f, 0f), p(w, h)),
        topLeft = p(0.65.dp.toPx(), 0.65.dp.toPx()),
        size = s(w - 1.3.dp.toPx(), h - 1.3.dp.toPx()),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 0.70.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = border.cornerGlintAlpha * 0.55f), Color(0xFFEAF5FF).copy(alpha = border.cornerGlintAlpha * 0.16f), Color.Transparent), p(w * 0.10f, h * 0.08f), w * 0.28f), p(0f, 0f), s(w, h), blendMode = BlendMode.Screen)
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
    color = mix(float3(luma, luma, luma), color, edgeSaturation * 1.24);
    color = (color - float3(0.5, 0.5, 0.5)) * edgeContrast * 1.30 + float3(0.5, 0.5, 0.5);
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

    float2 baseCoord = (sampleOffset + local) * backdropScale;
    half4 base = backdrop.eval(baseCoord);
    float3 baseColor = float3(base.r, base.g, base.b);

    float crestRim = 1.0 - smoothstep(0.0, max(edgeWidth * 0.22, 1.0), inside);
    float outerRim = 1.0 - smoothstep(0.0, max(edgeWidth * 0.72, 1.0), inside);
    float innerRim = 1.0 - smoothstep(max(edgeWidth * 0.10, 1.0), max(edgeWidth * 1.92, 2.0), inside);
    float softRim = 1.0 - smoothstep(max(edgeWidth * 0.58, 1.0), max(edgeWidth * 2.80, 2.0), inside);
    float rimMask = clamp(outerRim * 1.04 + innerRim * 0.58, 0.0, 1.0);
    float tailMask = clamp(softRim * 0.26, 0.0, 0.26);
    float maxSize = max(max(itemSize.x, itemSize.y), 1.0);
    float cornerCurve = clamp(abs(edgeNormal.x * edgeNormal.y) * 2.65, 0.0, 1.0);
    float tangentPhase = clamp(dot(p / maxSize, edgeTangent) * 3.45, -1.0, 1.0);

    float2 surfaceLocal = local + edgeNormal * inside;
    float crestReach = min(edgeWidth * 0.66, edgePull * 0.26) * (1.0 + cornerCurve * 0.45);
    float nearReach = min(edgeWidth * 1.10, edgePull * 0.38) * (1.0 + cornerCurve * 0.44);
    float pressReach = min(edgeWidth * 2.05, edgePull * 0.62) * (1.0 + cornerCurve * 0.66);
    float tangentBend = edgeWidth * tangentPhase * (crestRim * 0.38 + outerRim * 0.54 + innerRim * 0.36 + softRim * 0.14) * (1.0 + cornerCurve * 1.15);

    float2 crestLocal = surfaceLocal + edgeNormal * crestReach + edgeTangent * tangentBend * 0.42;
    float2 nearLocal = surfaceLocal + edgeNormal * nearReach + edgeTangent * tangentBend * 0.88;
    float2 pressLocal = surfaceLocal + edgeNormal * pressReach + edgeTangent * tangentBend * 1.36;
    float2 insideLocal = local - edgeNormal * min(edgeWidth * 0.58, edgePull * 0.18) * innerRim;

    half4 crestA = backdrop.eval((sampleOffset + crestLocal + edgeNormal * 1.10 + edgeTangent * 0.58) * backdropScale);
    half4 crestB = backdrop.eval((sampleOffset + crestLocal - edgeNormal * 0.86 - edgeTangent * 0.44) * backdropScale);
    half4 nearA = backdrop.eval((sampleOffset + nearLocal + edgeNormal * 2.25 + edgeTangent * 1.32) * backdropScale);
    half4 nearB = backdrop.eval((sampleOffset + nearLocal - edgeNormal * 1.86 - edgeTangent * 1.06) * backdropScale);
    half4 press = backdrop.eval((sampleOffset + pressLocal) * backdropScale);
    half4 inward = backdrop.eval((sampleOffset + insideLocal) * backdropScale);

    float3 crestColor = float3(crestA.r, press.g, crestB.b);
    float3 edgeSplit = float3(nearA.r, press.g, nearB.b);
    float3 pressedColor = float3(press.r, press.g, press.b);
    float3 inwardColor = float3(inward.r, inward.g, inward.b);

    float crestMix = clamp(crestRim * (1.18 + cornerCurve * 0.30), 0.0, 1.0);
    float edgeMix = clamp(rimMask * (1.04 + cornerCurve * 0.24), 0.0, 0.96);
    float pressMix = clamp(innerRim * 0.58 + cornerCurve * outerRim * 0.18, 0.0, 0.72);
    float inwardMix = clamp(tailMask, 0.0, 0.26);

    float3 refracted = mix(baseColor, edgeSplit, edgeMix);
    refracted = mix(refracted, pressedColor, pressMix);
    refracted = mix(refracted, inwardColor, inwardMix);
    refracted = adjustColor(refracted);
    float3 crestAdjusted = adjustColor(crestColor);
    refracted = mix(refracted, crestAdjusted, crestMix * 0.92);

    float rimGlow = crestRim * 0.210 + outerRim * 0.130 + innerRim * 0.040 + cornerCurve * crestRim * 0.082;
    refracted = clamp(refracted + float3(rimGlow, rimGlow, rimGlow) * (0.55 + crestRim * 0.45), float3(0.0, 0.0, 0.0), float3(1.0, 1.0, 1.0));

    float alphaField = clamp(crestRim * 1.25 + outerRim * 0.98 + innerRim * 0.46 + cornerCurve * crestRim * 0.24, 0.0, 1.0);
    float a = clamp(edgeAlpha * alphaField, 0.0, 1.0);
    return half4(refracted, a);
}
"""
