package com.yuchen.ailedger.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint as AndroidPaint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
private const val SHADER_DIAGNOSTIC_TINT = 1.0f

@Volatile
private var hasLoggedShaderLensFailure = false

private class GlassLensShaderCache {
    private var blurBitmap: Bitmap? = null
    private var lensBitmap: Bitmap? = null
    private var shader: Any? = null
    private var cachedPaint: AndroidPaint? = null

    @SuppressLint("NewApi")
    fun configuredPaint(
        backdrop: BlurredBackdropBitmap,
        itemRect: Rect,
        sampleOffset: Offset,
        radius: Float,
        edgeWidth: Float,
        edgePull: Float,
        edgeAlpha: Float,
        border: GlassBorderStyle
    ): AndroidPaint {
        val nextBlurBitmap = backdrop.image.asAndroidBitmap()
        val nextLensBitmap = backdrop.lensImage.asAndroidBitmap()
        val currentShader = shader as? RuntimeShader
        val lensShader = if (currentShader != null && blurBitmap === nextBlurBitmap && lensBitmap === nextLensBitmap) {
            currentShader
        } else {
            RuntimeShader(GLASS_LENS_SHADER).apply {
                setInputShader("backdropBlur", BitmapShader(nextBlurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
                setInputShader("backdropLens", BitmapShader(nextLensBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
                setFloatUniform("lensMix", SOFT_LENS_DETAIL_MIX)
                setFloatUniform("bandStrength", LENS_BAND_STRENGTH)
                setFloatUniform("diagnosticTint", SHADER_DIAGNOSTIC_TINT)
            }.also { nextShader ->
                blurBitmap = nextBlurBitmap
                lensBitmap = nextLensBitmap
                shader = nextShader
                cachedPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    this.shader = nextShader
                    isDither = true
                }
            }
        }

        lensShader.setFloatUniform("itemPos", itemRect.left, itemRect.top)
        lensShader.setFloatUniform("itemSize", itemRect.width, itemRect.height)
        lensShader.setFloatUniform("sampleOffset", sampleOffset.x, sampleOffset.y)
        lensShader.setFloatUniform("backdropScale", backdrop.scale)
        lensShader.setFloatUniform("radius", radius)
        lensShader.setFloatUniform("edgeWidth", edgeWidth)
        lensShader.setFloatUniform("edgePull", edgePull)
        lensShader.setFloatUniform("edgeAlpha", edgeAlpha)
        lensShader.setFloatUniform("edgeContrast", border.edgeContrast.coerceIn(0.70f, 1.95f))
        lensShader.setFloatUniform("edgeSaturation", border.edgeSaturation.coerceIn(0.60f, 2.25f))
        lensShader.setFloatUniform("edgeBrightness", border.edgeBrightness.coerceIn(0.60f, 1.60f))
        return cachedPaint ?: error("Glass lens shader paint requested before shader initialization.")
    }

    fun clear() {
        blurBitmap = null
        lensBitmap = null
        shader = null
        cachedPaint = null
    }
}

private class UnifiedGlassDrawCache {
    val itemPath = Path()
    val bitmapMatrix = AndroidMatrix()
    val bitmapPaint = AndroidPaint(
        AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG or AndroidPaint.DITHER_FLAG
    ).apply {
        isDither = true
        isFilterBitmap = true
    }
}

@Composable
fun UnifiedGlassBackdropLayer(modifier: Modifier = Modifier) {
    val registry = LocalGlassItemRegistry.current
    val backdrop = LocalBlurredBackdrop.current
    val origin = LocalBackdropOrigin.current
    val ticker = LocalBackdropFrameTicker.current
    val spec = LocalGlassBackdrop.current
    val registryVersion = registry?.version ?: 0L
    val shaderCache = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GlassLensShaderCache() else null
    }
    val drawCache = remember { UnifiedGlassDrawCache() }

    Canvas(modifier = modifier) {
        registryVersion
        ticker?.frameNanos
        val cached = backdrop ?: return@Canvas
        val border = spec?.borderStyle ?: GlassBorderStyle()
        val screen = Rect(0f, 0f, size.width, size.height)
        val items = registry?.snapshot() ?: return@Canvas
        items.forEach { item ->
            if (!item.coordinates.isAttached()) return@forEach
            val itemSize = item.coordinates.itemSize()
            if (itemSize.width <= 0 || itemSize.height <= 0) return@forEach
            val topLeft = item.coordinates.rootOffset()
            val rect = Rect(topLeft, Size(itemSize.width.toFloat(), itemSize.height.toFloat()))
            val visible = rect.intersectionOrNull(screen) ?: return@forEach
            val sampleOffset = item.coordinates.offsetRelativeTo(origin)
            val corner = item.radius.dp.toPx()
            val itemPath = drawCache.itemPath.apply {
                reset()
                addRoundRect(RoundRect(rect, CornerRadius(corner, corner)))
            }
            drawGlassBody(cached, rect, visible, sampleOffset, itemPath, item.quality, item.glassIntensity, item.backdropAlpha, border, drawCache)
            drawContinuousLens(cached, rect, visible, sampleOffset, corner, itemPath, border, item.edgeStrength, shaderCache)
            drawGlassHighlights(rect, corner, border)
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
    itemPath: Path,
    quality: RenderQuality,
    glassIntensity: Float,
    backdropAlpha: Float,
    border: GlassBorderStyle,
    drawCache: UnifiedGlassDrawCache
) {
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
    clipPath(itemPath) {
        drawBackdropBodyImage(
            backdrop = backdrop,
            itemRect = itemRect,
            visibleRect = visibleRect,
            sampleOffset = sampleOffset,
            alpha = (backdropAlpha * (0.76f + border.bodyAlpha.coerceIn(0f, 0.50f))).coerceIn(0.25f, 1f),
            drawCache = drawCache
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

private fun DrawScope.drawBackdropBodyImage(
    backdrop: BlurredBackdropBitmap,
    itemRect: Rect,
    visibleRect: Rect,
    sampleOffset: Offset,
    alpha: Float,
    drawCache: UnifiedGlassDrawCache
) {
    val bitmap = backdrop.image.asAndroidBitmap()
    val backdropRootX = itemRect.left - sampleOffset.x
    val backdropRootY = itemRect.top - sampleOffset.y
    val scale = backdrop.scale.coerceAtLeast(0.0001f)
    val matrix = drawCache.bitmapMatrix.apply {
        reset()
        setScale(1f / scale, 1f / scale)
        postTranslate(backdropRootX, backdropRootY)
    }
    val paint = drawCache.bitmapPaint.apply {
        this.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    }
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        val checkpoint = nativeCanvas.save()
        nativeCanvas.clipRect(visibleRect.left, visibleRect.top, visibleRect.right, visibleRect.bottom)
        nativeCanvas.drawBitmap(bitmap, matrix, paint)
        nativeCanvas.restoreToCount(checkpoint)
    }
}

private fun DrawScope.drawContinuousLens(
    backdrop: BlurredBackdropBitmap,
    itemRect: Rect,
    visibleRect: Rect,
    sampleOffset: Offset,
    corner: Float,
    itemPath: Path,
    border: GlassBorderStyle,
    strength: Float,
    shaderCache: GlassLensShaderCache?
) {
    val w = itemRect.width
    val h = itemRect.height
    if (w <= 4f || h <= 4f) return
    val edgeWidth = (border.ringWidthDp.dp.toPx() + border.edgeBlurDp.dp.toPx() * 0.38f).coerceIn(6.dp.toPx(), min(w, h) * 0.40f)
    val edgePull = border.edgePullDp.dp.toPx().coerceIn(0f, min(w, h) * 1.08f)
    val edgeAlpha = (border.edgeAlpha * (0.58f + strength * 0.42f) * border.edgeBrightness.coerceIn(0.72f, 1.25f)).coerceIn(0f, 0.86f)
    if (edgeAlpha <= 0.01f || edgePull <= 0.5f) return
    clipPath(itemPath) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shaderCache != null) {
            if (drawShaderLens(backdrop, itemRect, visibleRect, sampleOffset, corner, edgeWidth, edgePull, edgeAlpha, border, shaderCache)) return@clipPath
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
    border: GlassBorderStyle,
    shaderCache: GlassLensShaderCache
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    return runCatching {
        val paint = shaderCache.configuredPaint(backdrop, itemRect, sampleOffset, radius, edgeWidth, edgePull, edgeAlpha, border)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(visibleRect.left, visibleRect.top, visibleRect.right, visibleRect.bottom, paint)
        }
        true
    }.onFailure { error ->
        shaderCache.clear()
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
    drawImage(backdrop.image, IntOffset(srcX, srcY), IntSize(srcW, srcH), IntOffset(visibleRect.left.roundToInt(), visibleRect.top.roundToInt()), IntSize(dstW, dstH), alpha = alpha.coerceIn(0f, 0.34f), blendMode = BlendMode.SrcOver)
}

private fun DrawScope.drawGlassHighlights(itemRect: Rect, corner: Float, border: GlassBorderStyle) {
    val w = itemRect.width
    val h = itemRect.height
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
float roundedBoxSdf(float2 p, float2 halfSize, float r) {
    float2 b = max(halfSize - float2(r, r), float2(0.0, 0.0));
    float2 q = abs(p) - b;
    return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - r;
}
float3 adjustColor(float3 color) {
    float luma = dot(color, float3(0.2126, 0.7152, 0.0722));
    color = mix(float3(luma, luma, luma), color, edgeSaturation);
    color = (color - float3(0.5, 0.5, 0.5)) * edgeContrast + float3(0.5, 0.5, 0.5);
    return clamp(color * edgeBrightness, float3(0.0, 0.0, 0.0), float3(1.0, 1.0, 1.0));
}
half4 sampleSoftLens(float2 uv, float2 n, float2 t, float radiusPx) {
    half4 c = backdropLens.eval(uv) * 0.30;
    c += backdropLens.eval(uv + n * radiusPx) * 0.13;
    c += backdropLens.eval(uv - n * radiusPx) * 0.13;
    c += backdropLens.eval(uv + t * radiusPx) * 0.11;
    c += backdropLens.eval(uv - t * radiusPx) * 0.11;
    c += backdropLens.eval(uv + (n + t) * radiusPx * 0.70) * 0.08;
    c += backdropLens.eval(uv + (n - t) * radiusPx * 0.70) * 0.07;
    c += backdropLens.eval(uv - (n + t) * radiusPx * 0.70) * 0.04;
    c += backdropLens.eval(uv - (n - t) * radiusPx * 0.70) * 0.03;
    return c;
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

    float maxSize = max(max(itemSize.x, itemSize.y), 1.0);
    float cornerCurve = clamp(abs(edgeNormal.x * edgeNormal.y) * 2.15, 0.0, 1.0);
    float tangentPhase = clamp(dot(p / maxSize, edgeTangent) * 2.0, -1.0, 1.0);
    float surfaceGate = clamp(1.0 - inside / max(edgeWidth * 1.55, 1.0), 0.0, 1.0);

    float edgeCore = exp(-inside / max(edgeWidth * 0.14, 1.0));
    float edgeShoulder = exp(-inside / max(edgeWidth * 0.46, 1.0)) * 0.34;
    float softTail = exp(-inside / max(edgeWidth * 0.92, 1.0)) * 0.030;

    float2 surfaceLocal = local + edgeNormal * inside;
    float nearReach = min(edgePull * 0.20, edgeWidth * 0.62) * (1.0 + cornerCurve * 0.20);
    float midReach = min(edgePull * 0.42, edgeWidth * 1.32) * (1.0 + cornerCurve * 0.32);
    float farReach = min(edgePull * 0.68, edgeWidth * 2.18);
    float compression = inside * (0.16 + edgeCore * 0.14 + edgeShoulder * 0.22 + cornerCurve * 0.08);
    float tangentBend = edgeWidth * tangentPhase * (edgeCore * 0.12 + edgeShoulder * 0.22) * (1.0 + cornerCurve * 0.46);

    float2 baseCoord = (sampleOffset + local) * backdropScale;
    float2 nearLocal = surfaceLocal + edgeNormal * (nearReach - compression * 0.32) + edgeTangent * tangentBend * 0.44;
    float2 midLocal = surfaceLocal + edgeNormal * (midReach - compression) + edgeTangent * tangentBend;
    float2 farLocal = surfaceLocal + edgeNormal * (farReach - compression * 1.24) + edgeTangent * tangentBend * 1.16;
    float2 innerLocal = local - edgeNormal * edgePull * clamp(edgeShoulder * 0.12 + softTail * 0.12, 0.0, 0.18);

    float2 nearUv = (sampleOffset + nearLocal) * backdropScale;
    float2 midUv = (sampleOffset + midLocal) * backdropScale;
    float2 farUv = (sampleOffset + farLocal) * backdropScale;
    float2 innerUv = (sampleOffset + innerLocal) * backdropScale;

    half4 base = backdropBlur.eval(baseCoord);
    half4 nearBlur = backdropBlur.eval(nearUv);
    half4 midBlur = backdropBlur.eval(midUv);
    half4 far = backdropBlur.eval(farUv);
    half4 inner = backdropBlur.eval(innerUv);

    float softRadius = mix(2.8, 6.8, cornerCurve) * (1.0 + edgeShoulder * 1.25);
    half4 nearSoft = sampleSoftLens(nearUv, edgeNormal, edgeTangent, softRadius);
    half4 midSoft = sampleSoftLens(midUv, edgeNormal, edgeTangent, softRadius * 1.35);

    float lensGate = lensMix * clamp(edgeCore * 0.70 + edgeShoulder * 0.48 + cornerCurve * edgeCore * 0.16, 0.0, 1.0) * surfaceGate;
    float midLensGate = lensGate * clamp(0.52 + cornerCurve * 0.18, 0.0, 0.72);

    float3 baseColor = float3(base.r, base.g, base.b);
    float3 nearBlurColor = float3(nearBlur.r, nearBlur.g, nearBlur.b);
    float3 midBlurColor = float3(midBlur.r, midBlur.g, midBlur.b);
    float3 nearSoftColor = float3(nearSoft.r, nearSoft.g, nearSoft.b);
    float3 midSoftColor = float3(midSoft.r, midSoft.g, midSoft.b);
    float3 nearColor = mix(nearBlurColor, nearSoftColor, lensGate);
    float3 midColor = mix(midBlurColor, midSoftColor, midLensGate);
    float3 farColor = float3(far.r, far.g, far.b);
    float3 innerColor = float3(inner.r, inner.g, inner.b);

    float edgeMix = clamp(surfaceGate * (edgeCore * 0.82 + edgeShoulder * 0.44 + cornerCurve * edgeCore * 0.12), 0.0, 0.86);
    float midMix = clamp(surfaceGate * (edgeShoulder * 0.60 + cornerCurve * edgeCore * 0.09), 0.0, 0.46);
    float farMix = clamp(surfaceGate * softTail * 0.28, 0.0, 0.10);
    float innerMix = clamp(surfaceGate * (edgeShoulder * 0.09 + softTail * 0.07), 0.0, 0.12);

    float3 refracted = mix(baseColor, nearColor, edgeMix);
    refracted = mix(refracted, midColor, midMix);
    refracted = mix(refracted, farColor, farMix);
    refracted = mix(refracted, innerColor, innerMix);
    refracted = adjustColor(refracted);

    float brightBand = exp(-inside / max(edgeWidth * 0.10, 1.0)) * 0.072;
    float compressionBand = exp(-pow((inside - edgeWidth * 0.24) / max(edgeWidth * 0.18, 1.0), 2.0)) * 0.095;
    float innerDarkBand = exp(-pow((inside - edgeWidth * 0.58) / max(edgeWidth * 0.26, 1.0), 2.0)) * 0.060;
    float cornerCaustic = cornerCurve * exp(-inside / max(edgeWidth * 0.32, 1.0)) * 0.092;
    float3 compressed = clamp((refracted - float3(0.5, 0.5, 0.5)) * 1.12 + float3(0.5, 0.5, 0.5), float3(0.0, 0.0, 0.0), float3(1.0, 1.0, 1.0));

    refracted = mix(refracted, compressed, compressionBand * bandStrength);
    refracted += float3(brightBand, brightBand, brightBand) * bandStrength;
    refracted -= float3(innerDarkBand, innerDarkBand, innerDarkBand) * bandStrength;
    refracted += float3(cornerCaustic * 1.05, cornerCaustic * 0.92, cornerCaustic * 0.78) * bandStrength;
    refracted = mix(refracted, float3(1.0, 0.22, 0.02), diagnosticTint * edgeCore * surfaceGate * 0.72);

    float rimGlow = edgeCore * 0.032 + edgeShoulder * 0.016 + cornerCurve * edgeCore * 0.018;
    refracted = clamp(refracted + float3(rimGlow, rimGlow, rimGlow), float3(0.0, 0.0, 0.0), float3(1.0, 1.0, 1.0));

    float alphaField = clamp(edgeCore * 0.84 + edgeShoulder * 0.23 + softTail * 0.035 + cornerCurve * edgeCore * 0.07, 0.0, 0.88);
    float a = edgeAlpha * alphaField * surfaceGate;
    return half4(refracted, a);
}
"""
