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
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.sin

@Composable
fun WeatherNightBackground(
    quality: RenderQuality,
    motionIntensity: Float = 1f,
    theme: BackgroundTheme = BackgroundTheme.Aurora
) {
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
    val palette = theme.palette()

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val scaleDrift = (breathe - 0.5f) * motionScale
        val mistDrift = (mist - 0.5f) * motionScale

        drawRect(
            brush = Brush.linearGradient(
                colors = palette.base,
                start = Offset(w * 0.06f, 0f),
                end = Offset(w * 0.92f, h)
            )
        )

        fun sceneEllipse(cx: Float, cy: Float, rw: Float, rh: Float, color: Color, mode: BlendMode = BlendMode.Screen) {
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

        palette.scene.forEachIndexed { index, spec ->
            val dx = if (index % 2 == 0) -scaleDrift * 0.014f else scaleDrift * 0.018f
            val dy = if (index < 2) scaleDrift * 0.012f else 0f
            sceneEllipse(spec.cx + dx, spec.cy + dy, spec.rw, spec.rh, spec.color, spec.mode)
        }

        palette.mist.forEachIndexed { index, spec ->
            val drift = if (index % 2 == 0) mistDrift * 0.05f else -mistDrift * 0.05f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(spec.color, Color.Transparent),
                    center = Offset(w * (spec.cx + drift), h * spec.cy),
                    radius = w * spec.radius
                ),
                topLeft = Offset(w * (spec.left + drift), h * spec.top),
                size = Size(w * spec.width, h * spec.height),
                blendMode = spec.mode
            )
        }

        drawRect(
            brush = Brush.linearGradient(
                colors = palette.ribbon,
                start = Offset(w * (-0.08f + mistDrift * 0.03f), h * 0.05f),
                end = Offset(w * 1.08f, h * 0.82f)
            ),
            blendMode = BlendMode.Screen
        )

        repeat(quality.starCount.coerceAtLeast(palette.minStars)) { index ->
            val xSeed = ((index * 37 + 6) % 100) / 100f
            val ySeed = ((index * 61 + 8) % 100) / 100f
            val alphaWave = if (quality.enableMotion && motionScale > 0f) {
                twinkle + 0.10f * sin((breathe * 6.28f) + index * 0.83f).toFloat()
            } else 0.50f
            val alpha = (alphaWave * palette.starAlpha).coerceIn(0.12f, 0.72f)
            val radius = when {
                index % 11 == 0 -> 1.9f
                index % 5 == 0 -> 1.45f
                else -> 0.9f
            }
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = radius,
                center = Offset(w * (xSeed - mistDrift * 0.010f), h * (0.035f + ySeed * 0.88f + mistDrift * 0.012f))
            )
        }

        drawRect(
            brush = Brush.verticalGradient(palette.overlay, startY = 0f, endY = h),
            blendMode = if (theme == BackgroundTheme.Dawn) BlendMode.Screen else BlendMode.Multiply
        )
    }
}

private data class BgPalette(
    val base: List<Color>,
    val scene: List<EllipseSpec>,
    val mist: List<MistSpec>,
    val ribbon: List<Color>,
    val overlay: List<Color>,
    val starAlpha: Float,
    val minStars: Int
)

private data class EllipseSpec(val cx: Float, val cy: Float, val rw: Float, val rh: Float, val color: Color, val mode: BlendMode)
private data class MistSpec(val cx: Float, val cy: Float, val left: Float, val top: Float, val width: Float, val height: Float, val radius: Float, val color: Color, val mode: BlendMode)

private fun BackgroundTheme.palette(): BgPalette {
    return when (this) {
        BackgroundTheme.Aurora -> BgPalette(
            base = listOf(Color(0xFF08162A), Color(0xFF152442), Color(0xFF4B405E)),
            scene = listOf(
                EllipseSpec(0.76f, 0.12f, 0.27f, 0.17f, Color(0x3380B0FF), BlendMode.Plus),
                EllipseSpec(0.40f, 0.58f, 0.39f, 0.27f, Color(0x4D536AA9), BlendMode.Screen),
                EllipseSpec(0.58f, 0.88f, 0.34f, 0.22f, Color(0x33694D88), BlendMode.Lighten)
            ),
            mist = defaultMist(Color(0x2BC4D5FF), Color(0x2188A7E8), Color(0x1FA486BC), Color(0x1CC479AE)),
            ribbon = listOf(Color.Transparent, Color(0x09E8EFFF), Color.Transparent, Color(0x08D3C2EE), Color.Transparent),
            overlay = listOf(Color(0x29081224), Color(0x0A0F1629), Color(0x4D483A58)),
            starAlpha = 1f,
            minStars = 34
        )
        BackgroundTheme.Jade -> BgPalette(
            base = listOf(Color(0xFF04141A), Color(0xFF062D36), Color(0xFF063C43), Color(0xFF04151C)),
            scene = listOf(
                EllipseSpec(0.21f, 0.17f, 0.46f, 0.25f, Color(0x8A2EF2D0), BlendMode.Plus),
                EllipseSpec(0.82f, 0.32f, 0.50f, 0.28f, Color(0x6670F6FF), BlendMode.Screen),
                EllipseSpec(0.64f, 0.76f, 0.54f, 0.32f, Color(0x7522D8B8), BlendMode.Lighten)
            ),
            mist = defaultMist(Color(0x33A8FFF0), Color(0x236CF6FF), Color(0x2877F8E2), Color(0x1FB1FCFF)),
            ribbon = listOf(Color.Transparent, Color(0x24A8FFF0), Color.Transparent, Color(0x1C6CF6FF), Color.Transparent),
            overlay = listOf(Color(0x1A020F14), Color(0x12020F14), Color(0x28020F14)),
            starAlpha = 0.82f,
            minStars = 24
        )
        BackgroundTheme.Sunset -> BgPalette(
            base = listOf(Color(0xFF170A18), Color(0xFF341127), Color(0xFF351827), Color(0xFF120A16)),
            scene = listOf(
                EllipseSpec(0.22f, 0.17f, 0.50f, 0.25f, Color(0xA3FF8C6B), BlendMode.Plus),
                EllipseSpec(0.80f, 0.28f, 0.52f, 0.28f, Color(0x7AFF5E9E), BlendMode.Screen),
                EllipseSpec(0.66f, 0.75f, 0.55f, 0.32f, Color(0x70FFC56A), BlendMode.Lighten)
            ),
            mist = defaultMist(Color(0x32FFD6CB), Color(0x24FF9BBE), Color(0x24FFB39F), Color(0x20FFD58A)),
            ribbon = listOf(Color.Transparent, Color(0x22FFD6CB), Color.Transparent, Color(0x1CFFD58A), Color.Transparent),
            overlay = listOf(Color(0x1A100612), Color(0x14100612), Color(0x2E100612)),
            starAlpha = 0.72f,
            minStars = 18
        )
        BackgroundTheme.Dawn -> BgPalette(
            base = listOf(Color(0xFFF7EAF7), Color(0xFFE9F7FF), Color(0xFFD8F5F1), Color(0xFFF8F1FF)),
            scene = listOf(
                EllipseSpec(0.22f, 0.17f, 0.50f, 0.25f, Color(0xD1FFB7D7), BlendMode.Screen),
                EllipseSpec(0.80f, 0.28f, 0.50f, 0.27f, Color(0xC2A7D8FF), BlendMode.Screen),
                EllipseSpec(0.68f, 0.74f, 0.54f, 0.31f, Color(0xB8A7F7E6), BlendMode.Screen)
            ),
            mist = defaultMist(Color(0x55FFFFFF), Color(0x35CFF4FF), Color(0x35FFD8EC), Color(0x32D7FFF5)),
            ribbon = listOf(Color.Transparent, Color(0x40FFFFFF), Color.Transparent, Color(0x2AD7FFF5), Color.Transparent),
            overlay = listOf(Color(0x14FFFFFF), Color(0x0FFFFFFF), Color(0x22FFFFFF)),
            starAlpha = 0.0f,
            minStars = 0
        )
    }
}

private fun defaultMist(a: Color, b: Color, c: Color, d: Color): List<MistSpec> {
    return listOf(
        MistSpec(0.18f, 0.20f, -0.25f, 0.02f, 0.88f, 0.36f, 0.52f, a, BlendMode.Screen),
        MistSpec(0.75f, 0.18f, 0.34f, 0.02f, 0.88f, 0.34f, 0.46f, b, BlendMode.Screen),
        MistSpec(0.30f, 0.72f, -0.16f, 0.51f, 1.00f, 0.42f, 0.62f, c, BlendMode.Lighten),
        MistSpec(0.84f, 0.76f, 0.36f, 0.55f, 0.96f, 0.40f, 0.56f, d, BlendMode.Lighten)
    )
}