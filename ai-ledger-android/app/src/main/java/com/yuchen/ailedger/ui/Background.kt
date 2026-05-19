package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.RenderQuality

@Composable
fun WeatherNightBackground(
    quality: RenderQuality,
    motionIntensity: Float = 1f,
    theme: BackgroundTheme = BackgroundTheme.Aurora,
    params: BackdropDebugParams = BackdropDebugParams()
) {
    Canvas(Modifier.fillMaxSize()) {
        drawWeatherNightBackground(
            w = size.width,
            h = size.height,
            theme = theme,
            alphaScale = 1f,
            params = params
        )
    }
}

fun DrawScope.drawWeatherNightBackground(
    w: Float,
    h: Float,
    theme: BackgroundTheme = BackgroundTheme.Aurora,
    alphaScale: Float = 1f,
    params: BackdropDebugParams = BackdropDebugParams()
) {
    drawTwilightWeatherSky(w, h, theme, alphaScale, glowOnly = false, params = params)
}

fun DrawScope.drawWeatherNightBackgroundGlow(
    w: Float,
    h: Float,
    theme: BackgroundTheme = BackgroundTheme.Aurora,
    alphaScale: Float = 1f,
    params: BackdropDebugParams = BackdropDebugParams()
) {
    drawTwilightWeatherSky(w, h, theme, alphaScale, glowOnly = true, params = params)
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

@Suppress("unused")
private fun legacyBackgroundPalette(theme: BackgroundTheme): BackgroundPalette {
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
