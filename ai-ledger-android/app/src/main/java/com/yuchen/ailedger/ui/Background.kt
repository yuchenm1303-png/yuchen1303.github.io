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
        val w = size.width
        val h = size.height
        val icon = min(w * 0.145f, h * 0.068f)
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFF061426), Color(0xFF0B2947), Color(0xFF164166), Color(0xFF07111F)),
                start = Offset(w * 0.08f, 0f),
                end = Offset(w * 0.92f, h)
            )
        )
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x552F72AD), Color.Transparent), center = Offset(w * 0.74f, h * 0.34f), radius = w * 0.58f),
            topLeft = Offset(w * 0.18f, h * 0.02f),
            size = Size(w * 1.12f, h * 0.75f),
            blendMode = BlendMode.Screen
        )
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x33236AA8), Color.Transparent), center = Offset(w * 0.20f, h * 0.62f), radius = w * 0.44f),
            topLeft = Offset(-w * 0.18f, h * 0.30f),
            size = Size(w * 0.80f, h * 0.58f),
            blendMode = BlendMode.Screen
        )
        val xs = listOf(0.15f, 0.38f, 0.62f, 0.85f)
        val ys = listOf(0.11f, 0.24f, 0.37f, 0.50f, 0.63f, 0.76f)
        val colors = listOf(Color(0xFF18AFFF), Color(0xFFFFB51B), Color(0xFF181A28), Color.White, Color(0xFFFF5058), Color(0xFFFF941D), Color(0xFFB9C3CD), Color(0xFF1078F8))
        var k = 0
        ys.forEach { y ->
            xs.forEach { x ->
                if (!(y == 0.24f && x > 0.50f) && !(y == 0.76f && x == 0.62f)) {
                    drawRoundRect(
                        color = colors[k % colors.size],
                        topLeft = Offset(w * x - icon / 2f, h * y - icon / 2f),
                        size = Size(icon, icon),
                        cornerRadius = CornerRadius(icon * 0.22f, icon * 0.22f)
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)),
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
            color = Color(0xFFB9C3CD).copy(alpha = 0.88f),
            topLeft = Offset(w * 0.53f, h * 0.21f),
            size = Size(w * 0.40f, h * 0.17f),
            cornerRadius = CornerRadius(w * 0.045f, w * 0.045f)
        )
        drawRoundRect(
            color = Color(0xFF071A2B).copy(alpha = 0.52f),
            topLeft = Offset(w * 0.04f, h * 0.885f),
            size = Size(w * 0.92f, h * 0.095f),
            cornerRadius = CornerRadius(h * 0.030f, h * 0.030f)
        )
        repeat(5) { i ->
            drawRoundRect(
                color = colors[(i + 2) % colors.size],
                topLeft = Offset(w * (0.14f + i * 0.18f) - icon * 0.40f, h * 0.932f - icon * 0.40f),
                size = Size(icon * 0.80f, icon * 0.80f),
                cornerRadius = CornerRadius(icon * 0.18f, icon * 0.18f)
            )
        }
    }
}
