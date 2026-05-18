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
    val transition = rememberInfiniteTransition(label = "web-weather-sky")
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
    val starDrift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (quality.enableMotion && motionScale > 0f) {
                    (150000 / motionScale.coerceAtLeast(0.35f)).toInt()
                } else 200000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "weatherStarsDrift"
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
        val breatheDrift = (breathe - 0.5f) * motionScale
        val mistDrift = (mist - 0.5f) * motionScale
        val starDx = -0.012f * starDrift * motionScale
        val starDy = 0.014f * starDrift * motionScale
        val sceneLimit = if (quality.enableMotion) palette.scene.size else palette.scene.size.coerceAtMost(2)
        val mistLimit = quality.mistCount.coerceIn(1, palette.webMist.size)
        val ribbonAlpha = if (quality.enableMotion) 1f else 0.48f

        drawRect(
            brush = Brush.linearGradient(
                colors = palette.base,
                start = Offset(w * 0.06f, 0f),
                end = Offset(w * 0.92f, h)
            )
        )

        drawRect(
            brush = Brush.verticalGradient(palette.baseOverlay, startY = 0f, endY = h),
            blendMode = if (theme == BackgroundTheme.Dawn) BlendMode.Screen else BlendMode.Multiply
        )

        palette.scene.take(sceneLimit).forEachIndexed { index, spec ->
            val dx = if (index % 2 == 0) -breatheDrift * 0.020f else breatheDrift * 0.018f
            val dy = if (index < 2) breatheDrift * 0.012f else -breatheDrift * 0.006f
            drawSceneEllipse(
                cx = spec.cx + dx,
                cy = spec.cy + dy,
                rw = spec.rw,
                rh = spec.rh,
                color = spec.color,
                mode = spec.mode,
                width = w,
                height = h
            )
        }

        palette.webMist.take(mistLimit).forEachIndexed { index, spec ->
            val dx = if (index % 2 == 0) mistDrift * 0.050f else -mistDrift * 0.044f
            val dy = if (index % 3 == 0) -mistDrift * 0.018f else mistDrift * 0.012f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        spec.color,
                        spec.color.copy(alpha = spec.color.alpha * 0.38f),
                        Color.Transparent
                    ),
                    center = Offset(w * (spec.cx + dx), h * (spec.cy + dy)),
                    radius = w * spec.radius
                ),
                topLeft = Offset(w * (spec.left + dx), h * (spec.top + dy)),
                size = Size(w * spec.width, h * spec.height),
                blendMode = spec.mode
            )
        }

        drawRect(
            brush = Brush.linearGradient(
                colors = palette.ribbon.map { it.copy(alpha = it.alpha * ribbonAlpha) },
                start = Offset(w * (-0.08f + mistDrift * 0.035f), h * 0.06f),
                end = Offset(w * (1.08f + mistDrift * 0.020f), h * 0.82f)
            ),
            blendMode = BlendMode.Screen
        )

        drawRect(
            brush = Brush.linearGradient(
                colors = palette.secondRibbon.map { it.copy(alpha = it.alpha * ribbonAlpha) },
                start = Offset(w * (0.06f - mistDrift * 0.020f), h * 0.02f),
                end = Offset(w * (0.92f + mistDrift * 0.018f), h * 0.95f)
            ),
            blendMode = BlendMode.Screen
        )

        if (palette.starAlpha > 0f) {
            val stars = cssLikeStars
            val visibleCount = quality.starCount.coerceAtLeast(palette.minStars).coerceAtMost(stars.size)
            repeat(visibleCount) { index ->
                val star = stars[index]
                val alphaWave = if (quality.enableMotion && motionScale > 0f) {
                    twinkle + 0.12f * sin((breathe * 6.28318f) + index * 0.83f).toFloat()
                } else 0.50f
                val alpha = (alphaWave * palette.starAlpha * star.alpha).coerceIn(0.08f, 0.72f)
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = star.radius,
                    center = Offset(
                        w * (star.x + starDx + mistDrift * star.dx),
                        h * (star.y + starDy + mistDrift * star.dy)
                    )
                )
            }
        }

        drawRect(
            brush = Brush.verticalGradient(palette.overlay, startY = 0f, endY = h),
            blendMode = if (theme == BackgroundTheme.Dawn) BlendMode.Screen else BlendMode.Multiply
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSceneEllipse(
    cx: Float,
    cy: Float,
    rw: Float,
    rh: Float,
    color: Color,
    mode: BlendMode,
    width: Float,
    height: Float
) {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = color.alpha * 0.42f), Color.Transparent),
            center = Offset(width * cx, height * cy),
            radius = width * rw
        ),
        topLeft = Offset(width * (cx - rw), height * (cy - rh)),
        size = Size(width * rw * 2f, height * rh * 2f),
        blendMode = mode
    )
}

private data class BgPalette(
    val base: List<Color>,
    val baseOverlay: List<Color>,
    val scene: List<EllipseSpec>,
    val webMist: List<MistSpec>,
    val ribbon: List<Color>,
    val secondRibbon: List<Color>,
    val overlay: List<Color>,
    val starAlpha: Float,
    val minStars: Int
)

private data class EllipseSpec(
    val cx: Float,
    val cy: Float,
    val rw: Float,
    val rh: Float,
    val color: Color,
    val mode: BlendMode
)

private data class MistSpec(
    val cx: Float,
    val cy: Float,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val radius: Float,
    val color: Color,
    val mode: BlendMode
)

private data class StarSpec(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val dx: Float,
    val dy: Float
)

private fun BackgroundTheme.palette(): BgPalette {
    return when (this) {
        BackgroundTheme.Aurora -> BgPalette(
            base = listOf(Color(0xFF071326), Color(0xFF14213F), Color(0xFF473E60)),
            baseOverlay = listOf(Color(0x1A07101F), Color(0x080D1427), Color(0x52744F5C)),
            scene = listOf(
                EllipseSpec(0.72f, 0.10f, 0.28f, 0.18f, Color(0x387AA8FF), BlendMode.Screen),
                EllipseSpec(0.35f, 0.44f, 0.40f, 0.28f, Color(0x475274B8), BlendMode.Screen),
                EllipseSpec(0.58f, 0.78f, 0.38f, 0.25f, Color(0x38675091), BlendMode.Lighten),
                EllipseSpec(0.20f, 0.92f, 0.36f, 0.22f, Color(0x2E4A659B), BlendMode.Lighten)
            ),
            webMist = defaultWebMist(
                Color(0x2BC4D5FF),
                Color(0x2188A7E8),
                Color(0x1FA486BC),
                Color(0x1CC479AE)
            ),
            ribbon = listOf(Color.Transparent, Color(0x09E8EFFF), Color.Transparent, Color(0x08D3C2EE), Color.Transparent),
            secondRibbon = listOf(Color.Transparent, Color(0x07FFFFFF), Color.Transparent, Color(0x06B4C6FF), Color.Transparent),
            overlay = listOf(Color(0x1A07101E), Color(0x080D1427), Color(0x52744F5C)),
            starAlpha = 1f,
            minStars = 30
        )
        BackgroundTheme.Jade -> BgPalette(
            base = listOf(Color(0xFF04141A), Color(0xFF062D36), Color(0xFF063C43), Color(0xFF04151C)),
            baseOverlay = listOf(Color(0x12020F14), Color(0x08020F14), Color(0x24020F14)),
            scene = listOf(
                EllipseSpec(0.21f, 0.17f, 0.46f, 0.25f, Color(0x8A2EF2D0), BlendMode.Plus),
                EllipseSpec(0.82f, 0.32f, 0.50f, 0.28f, Color(0x6670F6FF), BlendMode.Screen),
                EllipseSpec(0.64f, 0.76f, 0.54f, 0.32f, Color(0x7522D8B8), BlendMode.Lighten)
            ),
            webMist = defaultWebMist(Color(0x33A8FFF0), Color(0x236CF6FF), Color(0x2877F8E2), Color(0x1FB1FCFF)),
            ribbon = listOf(Color.Transparent, Color(0x24A8FFF0), Color.Transparent, Color(0x1C6CF6FF), Color.Transparent),
            secondRibbon = listOf(Color.Transparent, Color(0x18FFFFFF), Color.Transparent, Color(0x126CF6FF), Color.Transparent),
            overlay = listOf(Color(0x1A020F14), Color(0x12020F14), Color(0x28020F14)),
            starAlpha = 0.82f,
            minStars = 24
        )
        BackgroundTheme.Sunset -> BgPalette(
            base = listOf(Color(0xFF170A18), Color(0xFF341127), Color(0xFF351827), Color(0xFF120A16)),
            baseOverlay = listOf(Color(0x14100612), Color(0x0A100612), Color(0x2E100612)),
            scene = listOf(
                EllipseSpec(0.22f, 0.17f, 0.50f, 0.25f, Color(0xA3FF8C6B), BlendMode.Plus),
                EllipseSpec(0.80f, 0.28f, 0.52f, 0.28f, Color(0x7AFF5E9E), BlendMode.Screen),
                EllipseSpec(0.66f, 0.75f, 0.55f, 0.32f, Color(0x70FFC56A), BlendMode.Lighten)
            ),
            webMist = defaultWebMist(Color(0x32FFD6CB), Color(0x24FF9BBE), Color(0x24FFB39F), Color(0x20FFD58A)),
            ribbon = listOf(Color.Transparent, Color(0x22FFD6CB), Color.Transparent, Color(0x1CFFD58A), Color.Transparent),
            secondRibbon = listOf(Color.Transparent, Color(0x18FFFFFF), Color.Transparent, Color(0x12FFD58A), Color.Transparent),
            overlay = listOf(Color(0x1A100612), Color(0x14100612), Color(0x2E100612)),
            starAlpha = 0.72f,
            minStars = 18
        )
        BackgroundTheme.Dawn -> BgPalette(
            base = listOf(Color(0xFFF7EAF7), Color(0xFFE9F7FF), Color(0xFFD8F5F1), Color(0xFFF8F1FF)),
            baseOverlay = listOf(Color(0x10FFFFFF), Color(0x08FFFFFF), Color(0x18FFFFFF)),
            scene = listOf(
                EllipseSpec(0.22f, 0.17f, 0.50f, 0.25f, Color(0xD1FFB7D7), BlendMode.Screen),
                EllipseSpec(0.80f, 0.28f, 0.50f, 0.27f, Color(0xC2A7D8FF), BlendMode.Screen),
                EllipseSpec(0.68f, 0.74f, 0.54f, 0.31f, Color(0xB8A7F7E6), BlendMode.Screen)
            ),
            webMist = defaultWebMist(Color(0x55FFFFFF), Color(0x35CFF4FF), Color(0x35FFD8EC), Color(0x32D7FFF5)),
            ribbon = listOf(Color.Transparent, Color(0x40FFFFFF), Color.Transparent, Color(0x2AD7FFF5), Color.Transparent),
            secondRibbon = listOf(Color.Transparent, Color(0x2AFFFFFF), Color.Transparent, Color(0x1AD7FFF5), Color.Transparent),
            overlay = listOf(Color(0x14FFFFFF), Color(0x0FFFFFFF), Color(0x22FFFFFF)),
            starAlpha = 0.0f,
            minStars = 0
        )
    }
}

private fun defaultWebMist(a: Color, b: Color, c: Color, d: Color): List<MistSpec> {
    return listOf(
        MistSpec(0.18f, 0.20f, -0.20f, 0.02f, 0.78f, 0.34f, 0.48f, a, BlendMode.Screen),
        MistSpec(0.75f, 0.18f, 0.40f, 0.02f, 0.76f, 0.32f, 0.42f, b, BlendMode.Screen),
        MistSpec(0.30f, 0.72f, -0.14f, 0.50f, 0.94f, 0.40f, 0.58f, c, BlendMode.Lighten),
        MistSpec(0.84f, 0.76f, 0.40f, 0.55f, 0.88f, 0.38f, 0.52f, d, BlendMode.Lighten)
    )
}

private val cssLikeStars = listOf(
    StarSpec(0.06f, 0.08f, 0.8f, 0.36f, -0.012f, 0.010f),
    StarSpec(0.09f, 0.16f, 1.1f, 0.70f, -0.010f, 0.012f),
    StarSpec(0.17f, 0.09f, 0.8f, 0.30f, -0.014f, 0.008f),
    StarSpec(0.22f, 0.08f, 1.0f, 0.42f, -0.010f, 0.014f),
    StarSpec(0.29f, 0.18f, 0.9f, 0.32f, -0.012f, 0.010f),
    StarSpec(0.35f, 0.27f, 1.1f, 0.52f, -0.012f, 0.012f),
    StarSpec(0.43f, 0.07f, 0.8f, 0.28f, -0.010f, 0.010f),
    StarSpec(0.52f, 0.12f, 1.2f, 0.62f, -0.014f, 0.012f),
    StarSpec(0.58f, 0.24f, 0.9f, 0.30f, -0.012f, 0.010f),
    StarSpec(0.66f, 0.30f, 1.0f, 0.38f, -0.014f, 0.014f),
    StarSpec(0.78f, 0.10f, 1.1f, 0.68f, -0.010f, 0.012f),
    StarSpec(0.83f, 0.18f, 0.8f, 0.28f, -0.012f, 0.010f),
    StarSpec(0.90f, 0.25f, 1.0f, 0.48f, -0.010f, 0.012f),
    StarSpec(0.16f, 0.45f, 1.0f, 0.42f, -0.014f, 0.010f),
    StarSpec(0.28f, 0.40f, 0.8f, 0.28f, -0.010f, 0.014f),
    StarSpec(0.34f, 0.58f, 0.8f, 0.26f, -0.014f, 0.012f),
    StarSpec(0.42f, 0.53f, 0.9f, 0.36f, -0.010f, 0.010f),
    StarSpec(0.53f, 0.42f, 0.8f, 0.28f, -0.012f, 0.014f),
    StarSpec(0.62f, 0.47f, 1.1f, 0.46f, -0.010f, 0.012f),
    StarSpec(0.72f, 0.60f, 0.8f, 0.28f, -0.014f, 0.010f),
    StarSpec(0.86f, 0.56f, 0.9f, 0.34f, -0.010f, 0.014f),
    StarSpec(0.96f, 0.46f, 0.8f, 0.28f, -0.012f, 0.012f),
    StarSpec(0.10f, 0.70f, 0.9f, 0.32f, -0.014f, 0.010f),
    StarSpec(0.20f, 0.86f, 0.8f, 0.26f, -0.010f, 0.014f),
    StarSpec(0.30f, 0.76f, 0.9f, 0.34f, -0.012f, 0.012f),
    StarSpec(0.46f, 0.84f, 0.8f, 0.28f, -0.010f, 0.010f),
    StarSpec(0.56f, 0.70f, 0.8f, 0.26f, -0.014f, 0.012f),
    StarSpec(0.70f, 0.78f, 1.0f, 0.40f, -0.010f, 0.014f),
    StarSpec(0.82f, 0.92f, 0.8f, 0.26f, -0.012f, 0.010f),
    StarSpec(0.92f, 0.82f, 0.9f, 0.34f, -0.014f, 0.012f),
    StarSpec(0.04f, 0.34f, 0.7f, 0.22f, -0.010f, 0.010f),
    StarSpec(0.74f, 0.34f, 0.7f, 0.26f, -0.012f, 0.012f),
    StarSpec(0.88f, 0.68f, 0.7f, 0.24f, -0.010f, 0.014f),
    StarSpec(0.38f, 0.68f, 0.7f, 0.22f, -0.014f, 0.010f)
)
