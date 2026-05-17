package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.sin

@Composable
fun WeatherNightBackground(quality: RenderQuality) {
    val transition = rememberInfiniteTransition(label = "night-sky")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (quality.enableMotion) 24000 else 60000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mist-drift"
    )

    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF07142E),
                    Color(0xFF10244A),
                    Color(0xFF2C315F),
                    Color(0xFF4B335D)
                )
            )
        )

        val glowCenter = Offset(size.width * 0.74f, size.height * 0.16f)
        val glowRadius = size.minDimension * 0.62f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x664E9BFF), Color.Transparent),
                center = glowCenter,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = glowCenter
        )

        repeat(quality.starCount) { index ->
            val xSeed = ((index * 37) % 100) / 100f
            val ySeed = ((index * 61) % 100) / 100f
            val twinkle = if (quality.enableMotion) {
                0.45f + 0.35f * sin((drift * 6.28f) + index).toFloat()
            } else {
                0.58f
            }
            drawCircle(
                color = Color.White.copy(alpha = twinkle.coerceIn(0.25f, 0.82f)),
                radius = if (index % 13 == 0) 2.1f else 1.15f,
                center = Offset(size.width * xSeed, size.height * (0.04f + ySeed * 0.74f))
            )
        }

        repeat(quality.mistCount) { index ->
            val y = size.height * (0.28f + index * 0.18f)
            val x = size.width * (-0.2f + drift * 0.36f + index * 0.11f)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x2E9AB7FF), Color.Transparent),
                    center = Offset(x + size.width * 0.42f, y),
                    radius = size.width * 0.62f
                ),
                topLeft = Offset(x, y - size.height * 0.08f),
                size = Size(size.width * 1.08f, size.height * 0.23f)
            )
        }
    }
}
