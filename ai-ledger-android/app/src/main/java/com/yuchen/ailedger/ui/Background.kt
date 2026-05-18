package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.min

@Composable
fun WeatherNightBackground(
    quality: RenderQuality,
    motionIntensity: Float = 1f,
    theme: BackgroundTheme = BackgroundTheme.Aurora
) {
    Canvas(Modifier.fillMaxSize()) {
        drawWeatherNightBackground(
            w = size.width,
            h = size.height,
            theme = theme,
            alphaScale = 1f
        )
    }
}

fun DrawScope.drawWeatherNightBackground(
    w: Float,
    h: Float,
    theme: BackgroundTheme = BackgroundTheme.Aurora,
    alphaScale: Float = 1f
) {
    val a = alphaScale.coerceIn(0f, 1f)
    val palette = backgroundPalette(theme)
    val icon = min(w * 0.145f, h * 0.068f)

    drawRect(
        brush = Brush.linearGradient(
            listOf(
                palette.deep.copy(alpha = a),
                palette.mid.copy(alpha = a),
                palette.glow.copy(alpha = a),
                palette.bottom.copy(alpha = a)
            ),
            start = Offset(w * 0.08f, 0f),
            end = Offset(w * 0.92f, h)
        )
    )
    drawOval(
        brush = Brush.radialGradient(
            listOf(palette.primaryAura.copy(alpha = 0.34f * a), Color.Transparent),
            center = Offset(w * 0.74f, h * 0.34f),
            radius = w * 0.58f
        ),
        topLeft = Offset(w * 0.18f, h * 0.02f),
        size = Size(w * 1.12f, h * 0.75f),
        blendMode = BlendMode.Screen
    )
    drawOval(
        brush = Brush.radialGradient(
            listOf(palette.secondaryAura.copy(alpha = 0.20f * a), Color.Transparent),
            center = Offset(w * 0.20f, h * 0.62f),
            radius = w * 0.44f
        ),
        topLeft = Offset(-w * 0.18f, h * 0.30f),
        size = Size(w * 0.80f, h * 0.58f),
        blendMode = BlendMode.Screen
    )

    val xs = listOf(0.15f, 0.38f, 0.62f, 0.85f)
    val ys = listOf(0.11f, 0.24f, 0.37f, 0.50f, 0.63f, 0.76f)
    var k = 0
    ys.forEach { y ->
        xs.forEach { x ->
            if (!(y == 0.24f && x > 0.50f) && !(y == 0.76f && x == 0.62f)) {
                drawRoundRect(
                    color = palette.icons[k % palette.icons.size].copy(alpha = 0.92f * a),
                    topLeft = Offset(w * x - icon / 2f, h * y - icon / 2f),
                    size = Size(icon, icon),
                    cornerRadius = CornerRadius(icon * 0.22f, icon * 0.22f)
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.25f * a), Color.Transparent)
                    ),
                    topLeft = Offset(w * x - icon / 2f, h * y - icon / 2f),
                    size = Size(icon, icon),
                    cornerRadius = CornerRadius(icon * 0.22f, icon * 0.22f),
                    blendMode = BlendMode.Screen
                )
                k++
            }
        }
    }

    drawRoundRect(
        color = palette.widget.copy(alpha = 0.88f * a),
        topLeft = Offset(w * 0.53f, h * 0.21f),
        size = Size(w * 0.40f, h * 0.17f),
        cornerRadius = CornerRadius(w * 0.045f, w * 0.045f)
    )
    drawRoundRect(
        color = Color(0xFF071A2B).copy(alpha = 0.52f * a),
        topLeft = Offset(w * 0.04f, h * 0.885f),
        size = Size(w * 0.92f, h * 0.095f),
        cornerRadius = CornerRadius(h * 0.030f, h * 0.030f)
    )
    repeat(5) { i ->
        drawRoundRect(
            color = palette.icons[(i + 2) % palette.icons.size].copy(alpha = 0.92f * a),
            topLeft = Offset(w * (0.14f + i * 0.18f) - icon * 0.40f, h * 0.932f - icon * 0.40f),
            size = Size(icon * 0.80f, icon * 0.80f),
            cornerRadius = CornerRadius(icon * 0.18f, icon * 0.18f)
        )
    }
}

private data class BackgroundPalette(
    val deep: Color,
    val mid: Color,
    val glow: Color,
    val bottom: Color,
    val primaryAura: Color,
    val secondaryAura: Color,
    val widget: Color,
    val icons: List<Color>
)

private fun backgroundPalette(theme: BackgroundTheme): BackgroundPalette {
    return when (theme) {
        BackgroundTheme.Aurora -> BackgroundPalette(
            deep = Color(0xFF061426),
            mid = Color(0xFF0B2947),
            glow = Color(0xFF164166),
            bottom = Color(0xFF07111F),
            primaryAura = Color(0xFF2F72AD),
            secondaryAura = Color(0xFF236AA8),
            widget = Color(0xFFB9C3CD),
            icons = listOf(
                Color(0xFF18AFFF), Color(0xFFFFB51B), Color(0xFF181A28), Color.White,
                Color(0xFFFF5058), Color(0xFFFF941D), Color(0xFFB9C3CD), Color(0xFF1078F8)
            )
        )
        BackgroundTheme.Jade -> BackgroundPalette(
            deep = Color(0xFF071B21),
            mid = Color(0xFF0B3A43),
            glow = Color(0xFF0C5B66),
            bottom = Color(0xFF061419),
            primaryAura = Color(0xFF22C7A7),
            secondaryAura = Color(0xFF40DCA8),
            widget = Color(0xFFC8D8D2),
            icons = listOf(
                Color(0xFF20D3B2), Color(0xFFFFC95C), Color(0xFF1D2630), Color.White,
                Color(0xFFFF6B7C), Color(0xFF50B7FF), Color(0xFFBFD5CE), Color(0xFF0D8E7B)
            )
        )
        BackgroundTheme.Sunset -> BackgroundPalette(
            deep = Color(0xFF221327),
            mid = Color(0xFF4B2138),
            glow = Color(0xFF7E3D4F),
            bottom = Color(0xFF140E1E),
            primaryAura = Color(0xFFFF7A6E),
            secondaryAura = Color(0xFFFFB35B),
            widget = Color(0xFFD8C6C8),
            icons = listOf(
                Color(0xFFFF6E82), Color(0xFFFFB84A), Color(0xFF242233), Color.White,
                Color(0xFFFF4F6D), Color(0xFFFF8B2C), Color(0xFFCDC1D2), Color(0xFF6A79FF)
            )
        )
        BackgroundTheme.Dawn -> BackgroundPalette(
            deep = Color(0xFF1A2634),
            mid = Color(0xFF52657A),
            glow = Color(0xFF93A8B7),
            bottom = Color(0xFF101822),
            primaryAura = Color(0xFFEAF2FF),
            secondaryAura = Color(0xFF9ED4FF),
            widget = Color(0xFFE7E9EE),
            icons = listOf(
                Color(0xFF45B8FF), Color(0xFFFFC861), Color(0xFF28303A), Color.White,
                Color(0xFFFF6F83), Color(0xFFFFA15C), Color(0xFFD9E0E9), Color(0xFF358CFF)
            )
        )
    }
}
