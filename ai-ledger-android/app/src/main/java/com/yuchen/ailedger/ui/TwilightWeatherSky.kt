package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import kotlin.math.min

fun DrawScope.drawTwilightWeatherSky(
    w: Float,
    h: Float,
    theme: BackgroundTheme,
    alphaScale: Float = 1f,
    glowOnly: Boolean = false,
    params: BackdropDebugParams = BackdropDebugParams()
) {
    val a = alphaScale.coerceIn(0f, 1f)
    val p = twilightPalette(theme)
    drawRect(
        Brush.verticalGradient(
            listOf(
                p.top.copy(alpha = a),
                p.upper.copy(alpha = a),
                p.mid.copy(alpha = a),
                p.horizon.copy(alpha = a),
                p.bottom.copy(alpha = a)
            ),
            startY = 0f,
            endY = h
        )
    )

    drawWeatherOval(w * 0.82f, h * 0.12f, w * 0.52f, h * 0.26f, p.violet, 0.38f * a)
    drawWeatherOval(w * 0.34f, h * 0.82f, w * 0.70f, h * 0.34f, p.warm, 0.34f * a)
    drawWeatherOval(w * 0.24f, h * 0.44f, w * 0.46f, h * 0.28f, p.blue, 0.22f * a)

    val cloudAlpha = params.cloudAlpha * if (glowOnly) 0.72f else 1f
    drawCloudBand(w, h, 0.10f, 0.18f, p.cloudLight, 0.40f * a * cloudAlpha, -0.12f, params)
    drawCloudBand(w, h, 0.22f, 0.21f, p.cloudBlue, 0.38f * a * cloudAlpha, 0.06f, params)
    drawCloudBand(w, h, 0.44f, 0.27f, p.cloudWarm, 0.34f * a * cloudAlpha, -0.04f, params)
    drawCloudBand(w, h, 0.66f, 0.26f, p.cloudRose, 0.28f * a * cloudAlpha, 0.14f, params)

    drawStars(w, h, a)
    drawCrescent(w, h, a, p, params)
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, Color.Transparent, Color(0xFF070B18).copy(alpha = 0.16f * a)),
            startY = h * 0.60f,
            endY = h
        ),
        blendMode = BlendMode.Multiply
    )
}

private fun DrawScope.drawWeatherOval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, alpha: Float) {
    drawOval(
        brush = Brush.radialGradient(
            listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.28f), Color.Transparent),
            center = Offset(cx, cy),
            radius = maxOf(rx, ry)
        ),
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2f, ry * 2f),
        blendMode = BlendMode.Screen
    )
}

private fun DrawScope.drawCloudBand(
    w: Float,
    h: Float,
    y: Float,
    bandHeight: Float,
    color: Color,
    alpha: Float,
    drift: Float,
    params: BackdropDebugParams
) {
    val cy = h * y
    val bh = h * bandHeight
    val positions = listOf(-0.10f, 0.08f, 0.24f, 0.41f, 0.60f, 0.78f, 0.96f, 1.12f)
    positions.forEachIndexed { index, x ->
        val center = Offset(
            x = w * (x + drift),
            y = cy + bh * if (index % 2 == 0) 0.09f else -0.05f
        )
        val width = w * (0.18f + (index % 3) * 0.035f) * params.cloudStretchX
        val height = bh * (0.38f + (index % 2) * 0.10f) * params.cloudStretchY
        drawCloudBlob(
            center = center,
            width = width,
            height = height,
            color = color,
            alpha = alpha * (0.78f + (index % 4) * 0.055f),
            params = params
        )
    }
}

private fun DrawScope.drawCloudBlob(
    center: Offset,
    width: Float,
    height: Float,
    color: Color,
    alpha: Float,
    params: BackdropDebugParams
) {
    val softness = params.cloudSoftness
    drawOval(
        brush = Brush.radialGradient(
            listOf(
                color.copy(alpha = alpha * 0.42f),
                color.copy(alpha = alpha * 0.16f),
                Color.Transparent
            ),
            center = center,
            radius = width * 0.55f * softness
        ),
        topLeft = Offset(center.x - width * 0.62f, center.y - height * 0.56f),
        size = Size(width * 1.24f, height * 1.12f),
        blendMode = BlendMode.Screen
    )
    drawOval(
        brush = Brush.radialGradient(
            listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.32f),
                Color.Transparent
            ),
            center = center + Offset(0f, -height * 0.04f),
            radius = width * 0.43f * softness
        ),
        topLeft = Offset(center.x - width * 0.48f, center.y - height * 0.42f),
        size = Size(width * 0.96f, height * 0.84f),
        blendMode = BlendMode.Screen
    )
    drawOval(
        brush = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = params.cloudHighlightAlpha * alpha),
                Color.Transparent
            ),
            center = center + Offset(0f, -height * 0.24f),
            radius = width * 0.28f
        ),
        topLeft = Offset(center.x - width * 0.30f, center.y - height * 0.38f),
        size = Size(width * 0.60f, height * 0.26f),
        blendMode = BlendMode.Screen
    )
}

private fun DrawScope.drawStars(w: Float, h: Float, alpha: Float) {
    val points = listOf(
        0.12f to 0.17f, 0.21f to 0.10f, 0.31f to 0.19f, 0.47f to 0.12f,
        0.63f to 0.16f, 0.74f to 0.09f, 0.88f to 0.20f, 0.18f to 0.30f,
        0.52f to 0.27f, 0.82f to 0.34f, 0.70f to 0.43f
    )
    val r = min(w, h) * 0.0028f
    points.forEachIndexed { i, p ->
        drawCircle(
            Color.White.copy(alpha = (0.18f + (i % 3) * 0.08f) * alpha),
            radius = r * (0.72f + (i % 4) * 0.18f),
            center = Offset(w * p.first, h * p.second),
            blendMode = BlendMode.Screen
        )
    }
}

private fun DrawScope.drawCrescent(w: Float, h: Float, alpha: Float, p: TwilightPalette, params: BackdropDebugParams) {
    val r = min(w, h) * 0.024f * params.moonScale
    val c = Offset(w * 0.82f, h * 0.21f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFF3D6).copy(alpha = params.moonHaloAlpha * alpha), Color.Transparent),
            center = c,
            radius = r * 2.35f
        ),
        radius = r * 2.35f,
        center = c,
        blendMode = BlendMode.Screen
    )
    drawCircle(Color(0xFFFFF3D6).copy(alpha = 0.62f * alpha), r, c, blendMode = BlendMode.Screen)
    drawCircle(p.upper.copy(alpha = 0.97f * alpha), r * 1.05f, Offset(c.x + r * 0.46f, c.y - r * 0.12f))
    drawCircle(Color.White.copy(alpha = params.moonRimAlpha * alpha), r * 0.20f, Offset(c.x - r * 0.42f, c.y + r * 0.02f), blendMode = BlendMode.Screen)
}

private data class TwilightPalette(
    val top: Color,
    val upper: Color,
    val mid: Color,
    val horizon: Color,
    val bottom: Color,
    val violet: Color,
    val warm: Color,
    val blue: Color,
    val cloudLight: Color,
    val cloudBlue: Color,
    val cloudWarm: Color,
    val cloudRose: Color
)

private fun twilightPalette(theme: BackgroundTheme): TwilightPalette = when (theme) {
    BackgroundTheme.Aurora -> TwilightPalette(Color(0xFF061426), Color(0xFF263A68), Color(0xFF596B99), Color(0xFF8B7186), Color(0xFFB78369), Color(0xFFB79AFF), Color(0xFFFFA06E), Color(0xFF5CA9E6), Color(0xFFB7B6E8), Color(0xFF88A7CE), Color(0xFFD4A19A), Color(0xFFC08194))
    BackgroundTheme.Jade -> TwilightPalette(Color(0xFF071A22), Color(0xFF24465F), Color(0xFF5E7E95), Color(0xFF83A394), Color(0xFFB59B79), Color(0xFF8EC2DD), Color(0xFFE8B37F), Color(0xFF58C0BC), Color(0xFFAEC7D8), Color(0xFF80AFC1), Color(0xFFC7AE92), Color(0xFFA68F97))
    BackgroundTheme.Sunset -> TwilightPalette(Color(0xFF20182D), Color(0xFF49365E), Color(0xFF735C83), Color(0xFFA87586), Color(0xFFD1976B), Color(0xFFC098FF), Color(0xFFFF9A64), Color(0xFF7587D5), Color(0xFFC6B3E6), Color(0xFF9CA2C8), Color(0xFFE0A18D), Color(0xFFD0809A))
    BackgroundTheme.Dawn -> TwilightPalette(Color(0xFF16253C), Color(0xFF526A91), Color(0xFF89A5BE), Color(0xFFC1A6A4), Color(0xFFD8B287), Color(0xFFE2CCFF), Color(0xFFFFC28A), Color(0xFF9ED4FF), Color(0xFFD7D6F0), Color(0xFFAAC5DA), Color(0xFFE2C0A6), Color(0xFFD5A0AD))
}
