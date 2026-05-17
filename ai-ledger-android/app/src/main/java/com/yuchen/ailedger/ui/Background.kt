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
    val transition = rememberInfiniteTransition(label = "web-liquid-bg")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (quality.enableMotion && motionScale > 0f) {
                    (56000 / motionScale.coerceAtLeast(0.35f)).toInt()
                } else 90000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "weatherSkyBreathe"
    )
    val mist by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (quality.enableMotion && motionScale > 0f) {
                    (118000 / motionScale.coerceAtLeast(0.35f)).toInt()
                } else 160000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "weatherMistDrift"
    )
    val twinkle by transition.animateFloat(
        initialValue = 0.36f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (quality.enableMotion && motionScale > 0f) {
                    (19000 / motionScale.coerceAtLeast(0.35f)).toInt()
                } else 42000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "weatherStarsTwinkle"
    )

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val scaleDrift = (breathe - 0.5f) * motionScale
        val mistDrift = (mist - 0.5f) * motionScale

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF071326),
                    Color(0xFF14213F),
                    Color(0xFF473E60)
                ),
                start = Offset(w * 0.06f, 0f),
                end = Offset(w * 0.92f, h)
            )
        )

        fun sceneEllipse(
            cx: Float,
            cy: Float,
            rw: Float,
            rh: Float,
            color: Color,
            mode: BlendMode = BlendMode.Screen
        ) {
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = color.alpha * 0.42f), Color.Transparent),
                    center = Offset(w * cx, h * cy),
                    radius = w * rw
                ),
                topLeft = Offset(w * (cx - rw), h * (cy - rh)),
                size = Size(w * rw * 2f, h * rh * 2f),
                blendMode = mode
            )
        }

        sceneEllipse(0.72f - scaleDrift * 0.012f, 0.10f - scaleDrift * 0.010f, 0.28f, 0.18f, Color(0x387AA8FF), BlendMode.Plus)
        sceneEllipse(0.35f + scaleDrift * 0.018f, 0.44f + scaleDrift * 0.012f, 0.40f, 0.28f, Color(0x475274B8), BlendMode.Screen)
        sceneEllipse(0.58f - scaleDrift * 0.010f, 0.78f, 0.38f, 0.24f, Color(0x38675091), BlendMode.Lighten)
        sceneEllipse(0.20f + scaleDrift * 0.014f, 0.92f, 0.36f, 0.24f, Color(0x2E4A659B), BlendMode.Screen)

        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x2BC4D5FF), Color.Transparent),
                center = Offset(w * (0.18f + mistDrift * 0.04f), h * 0.20f),
                radius = w * 0.52f
            ),
            topLeft = Offset(w * (-0.25f + mistDrift * 0.05f), h * 0.02f),
            size = Size(w * 0.88f, h * 0.36f),
            blendMode = BlendMode.Screen
        )
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x2188A7E8), Color.Transparent),
                center = Offset(w * (0.75f - mistDrift * 0.05f), h * 0.18f),
                radius = w * 0.46f
            ),
            topLeft = Offset(w * (0.34f - mistDrift * 0.04f), h * 0.02f),
            size = Size(w * 0.88f, h * 0.34f),
            blendMode = BlendMode.Screen
        )
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x1FA486BC), Color.Transparent),
                center = Offset(w * (0.30f + mistDrift * 0.05f), h * 0.72f),
                radius = w * 0.62f
            ),
            topLeft = Offset(w * (-0.16f + mistDrift * 0.04f), h * 0.51f),
            size = Size(w * 1.0f, h * 0.42f),
            blendMode = BlendMode.Lighten
        )
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x1CC479AE), Color.Transparent),
                center = Offset(w * (0.84f - mistDrift * 0.04f), h * 0.76f),
                radius = w * 0.56f
            ),
            topLeft = Offset(w * (0.36f - mistDrift * 0.04f), h * 0.55f),
            size = Size(w * 0.96f, h * 0.40f),
            blendMode = BlendMode.Lighten
        )

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0x09E8EFFF),
                    Color.Transparent,
                    Color(0x08D3C2EE),
                    Color.Transparent
                ),
                start = Offset(w * (-0.08f + mistDrift * 0.03f), h * 0.05f),
                end = Offset(w * 1.08f, h * 0.82f)
            ),
            blendMode = BlendMode.Screen
        )

        repeat(quality.starCount.coerceAtLeast(30)) { index ->
            val xSeed = ((index * 37 + 6) % 100) / 100f
            val ySeed = ((index * 61 + 8) % 100) / 100f
            val alphaWave = if (quality.enableMotion && motionScale > 0f) {
                twinkle + 0.10f * sin((breathe * 6.28f) + index * 0.83f).toFloat()
            } else 0.50f
            val alpha = alphaWave.coerceIn(0.24f, 0.70f)
            val radius = when {
                index % 11 == 0 -> 1.9f
                index % 5 == 0 -> 1.45f
                else -> 0.9f
            }
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = radius,
                center = Offset(
                    w * (xSeed - mistDrift * 0.010f),
                    h * (0.035f + ySeed * 0.88f + mistDrift * 0.012f)
                )
            )
        }

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x1A070F1E),
                    Color(0x080D1427),
                    Color(0x524A3B5C)
                ),
                startY = 0f,
                endY = h
            ),
            blendMode = BlendMode.Multiply
        )
    }
}