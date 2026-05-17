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
                    (30000 / motionScale.coerceAtLeast(0.35f)).toInt()
                } else {
                    68000
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
                    (21000 / motionScale.coerceAtLeast(0.35f)).toInt()
                } else {
                    56000
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
                    Color(0xFF030A1B),
                    Color(0xFF071D3D),
                    Color(0xFF162D55),
                    Color(0xFF2B2752),
                    Color(0xFF432C59)
                )
            )
        )

        val ambientA = Offset(
            x = size.width * (0.20f + drift * (0.12f * motionScale)),
            y = size.height * (0.12f + pulse * (0.08f * motionScale))
        )
        val ambientB = Offset(
            x = size.width * (0.82f - drift * (0.10f * motionScale)),
            y = size.height * (0.20f + drift * (0.05f * motionScale))
        )
        val ambientC = Offset(
            x = size.width * (0.48f + pulse * (0.07f * motionScale)),
            y = size.height * (0.80f - drift * (0.04f * motionScale))
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x4A52A8FF), Color.Transparent),
                center = ambientA,
                radius = size.minDimension * 0.70f
            ),
            center = ambientA,
            radius = size.minDimension * 0.70f,
            blendMode = BlendMode.Plus
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x3C5EE0FF), Color.Transparent),
                center = ambientB,
                radius = size.minDimension * 0.64f
            ),
            center = ambientB,
            radius = size.minDimension * 0.64f,
            blendMode = BlendMode.Screen
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x34B788FF), Color.Transparent),
                center = ambientC,
                radius = size.minDimension * 0.78f
            ),
            center = ambientC,
            radius = size.minDimension * 0.78f,
            blendMode = BlendMode.Lighten
        )

        repeat(quality.starCount) { index ->
            val xSeed = ((index * 37) % 100) / 100f
            val ySeed = ((index * 61) % 100) / 100f
            val twinkle = if (quality.enableMotion && motionScale > 0f) {
                0.38f + 0.30f * sin((drift * 6.28f) + index).toFloat()
            } else {
                0.50f
            }
            drawCircle(
                color = Color.White.copy(alpha = twinkle.coerceIn(0.20f, 0.68f)),
                radius = if (index % 13 == 0) 1.9f else 1.0f,
                center = Offset(size.width * xSeed, size.height * (0.04f + ySeed * 0.74f))
            )
        }

        repeat(quality.mistCount + 1) { index ->
            val y = size.height * (0.26f + index * 0.18f)
            val x = size.width * (-0.30f + drift * (0.35f * motionScale.coerceAtLeast(0.1f)) + index * 0.10f)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x169AB7FF), Color.Transparent),
                    center = Offset(x + size.width * 0.46f, y),
                    radius = size.width * 0.70f
                ),
                topLeft = Offset(x, y - size.height * 0.075f),
                size = Size(size.width * 1.10f, size.height * 0.20f),
                blendMode = BlendMode.Screen
            )
        }

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0x22030614),
                    Color(0x55030614)
                ),
                startY = size.height * 0.58f,
                endY = size.height
            ),
            blendMode = BlendMode.Multiply
        )
    }
}