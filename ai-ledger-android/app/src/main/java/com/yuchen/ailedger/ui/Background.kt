package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.sin

@Composable
fun WeatherNightBackground(quality: RenderQuality, motionIntensity: Float = 1f) {
    val motionScale = motionIntensity.coerceIn(0f, 1.4f)
    val transition = rememberInfiniteTransition(label = "liquid-bg")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (quality.enableMotion && motionScale > 0f) {
                    (26000 / motionScale.coerceAtLeast(0.35f)).toInt()
                } else {
                    65000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liquid-bg-drift"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (quality.enableMotion && motionScale > 0f) {
                    (18000 / motionScale.coerceAtLeast(0.35f)).toInt()
                } else {
                    52000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liquid-bg-pulse"
    )

    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF07142E),
                    Color(0xFF10244A),
                    Color(0xFF312F5D),
                    Color(0xFF4C345D)
                )
            )
        )

        val ambientA = Offset(
            x = size.width * (0.18f + drift * (0.14f * motionScale)),
            y = size.height * (0.14f + pulse * (0.10f * motionScale))
        )
        val ambientB = Offset(
            x = size.width * (0.84f - drift * (0.12f * motionScale)),
            y = size.height * (0.18f + drift * (0.06f * motionScale))
        )
        val ambientC = Offset(
            x = size.width * (0.52f + pulse * (0.08f * motionScale)),
            y = size.height * (0.78f - drift * (0.05f * motionScale))
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x6652A8FF), Color.Transparent),
                center = ambientA,
                radius = size.minDimension * 0.66f
            ),
            center = ambientA,
            radius = size.minDimension * 0.66f,
            blendMode = BlendMode.Plus
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x555EE0FF), Color.Transparent),
                center = ambientB,
                radius = size.minDimension * 0.58f
            ),
            center = ambientB,
            radius = size.minDimension * 0.58f,
            blendMode = BlendMode.Screen
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x44B788FF), Color.Transparent),
                center = ambientC,
                radius = size.minDimension * 0.72f
            ),
            center = ambientC,
            radius = size.minDimension * 0.72f,
            blendMode = BlendMode.Lighten
        )

        repeat(quality.starCount) { index ->
            val xSeed = ((index * 37) % 100) / 100f
            val ySeed = ((index * 61) % 100) / 100f
            val twinkle = if (quality.enableMotion && motionScale > 0f) {
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

        repeat(quality.mistCount + 1) { index ->
            val y = size.height * (0.24f + index * 0.18f)
            val x = size.width * (-0.26f + drift * (0.42f * motionScale.coerceAtLeast(0.1f)) + index * 0.10f)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x309AB7FF), Color.Transparent),
                    center = Offset(x + size.width * 0.46f, y),
                    radius = size.width * 0.68f
                ),
                topLeft = Offset(x, y - size.height * 0.09f),
                size = Size(size.width * 1.18f, size.height * 0.26f),
                blendMode = BlendMode.Screen
            )
        }
    }
}
