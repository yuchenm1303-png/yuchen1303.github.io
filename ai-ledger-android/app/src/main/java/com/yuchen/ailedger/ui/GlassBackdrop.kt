package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.sin

data class GlassBackdropSpec(
    val quality: RenderQuality,
    val motionIntensity: Float,
    val theme: BackgroundTheme
)

val LocalGlassBackdrop = compositionLocalOf<GlassBackdropSpec?> { null }

@Composable
fun SampledWeatherGlassBackdrop(
    modifier: Modifier = Modifier,
    radius: Int,
    globalOffset: Offset,
    quality: RenderQuality,
    motionIntensity: Float,
    theme: BackgroundTheme,
    blurRadiusDp: Int = 24
) {
    val view = LocalView.current
    val motionScale = motionIntensity.coerceIn(0f, 1.4f)
    val transition = rememberInfiniteTransition(label = "sampled-glass-backdrop")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (quality.enableMotion && motionScale > 0f) (56000 / motionScale.coerceAtLeast(0.35f)).toInt() else 90000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sampled-bg-breathe"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (quality.enableMotion && motionScale > 0f) (118000 / motionScale.coerceAtLeast(0.35f)).toInt() else 160000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sampled-bg-drift"
    )
    val twinkle by transition.animateFloat(
        initialValue = 0.36f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (quality.enableMotion && motionScale > 0f) (19000 / motionScale.coerceAtLeast(0.35f)).toInt() else 42000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sampled-bg-twinkle"
    )

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .blur(blurRadiusDp.dp)
    ) {
        val rootW = if (view.width > 0) view.width.toFloat() else size.width
        val rootH = if (view.height > 0) view.height.toFloat() else size.height
        withTransform({ translate(left = -globalOffset.x, top = -globalOffset.y) }) {
            drawSampledWeatherSky(
                width = rootW,
                height = rootH,
                theme = theme,
                quality = quality,
                breathe = breathe,
                drift = drift,
                twinkle = twinkle,
                motionScale = motionScale
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.040f),
                    Color.White.copy(alpha = 0.018f),
                    Color.White.copy(alpha = 0.006f),
                    Color.Transparent
                )
            ),
            blendMode = BlendMode.Screen
        )
    }
}

private fun DrawScope.drawSampledWeatherSky(
    width: Float,
    height: Float,
    theme: BackgroundTheme,
    quality: RenderQuality,
    breathe: Float,
    drift: Float,
    twinkle: Float,
    motionScale: Float
) {
    val palette = sampledPalette(theme)
    val breatheDrift = (breathe - 0.5f) * motionScale
    val mistDrift = (drift - 0.5f) * motionScale

    drawRect(
        brush = Brush.linearGradient(
            colors = palette.base,
            start = Offset(width * 0.06f, 0f),
            end = Offset(width * 0.92f, height)
        ),
        size = Size(width, height)
    )
    drawRect(
        brush = Brush.verticalGradient(palette.overlay),
        size = Size(width, height),
        blendMode = if (theme == BackgroundTheme.Dawn) BlendMode.Screen else BlendMode.Multiply
    )

    fun oval(cx: Float, cy: Float, rw: Float, rh: Float, color: Color, mode: BlendMode = BlendMode.Screen) {
        drawOval(
            brush = Brush.radialGradient(
                listOf(color, color.copy(alpha = color.alpha * 0.42f), Color.Transparent),
                center = Offset(width * cx, height * cy),
                radius = width * rw
            ),
            topLeft = Offset(width * (cx - rw), height * (cy - rh)),
            size = Size(width * rw * 2f, height * rh * 2f),
            blendMode = mode
        )
    }

    oval(0.72f - breatheDrift * 0.020f, 0.10f + breatheDrift * 0.012f, 0.28f, 0.18f, palette.sceneA)
    oval(0.35f + breatheDrift * 0.018f, 0.44f + breatheDrift * 0.010f, 0.40f, 0.28f, palette.sceneB)
    oval(0.58f - breatheDrift * 0.010f, 0.78f - breatheDrift * 0.006f, 0.38f, 0.25f, palette.sceneC, BlendMode.Lighten)
    oval(0.20f + mistDrift * 0.026f, 0.92f, 0.36f, 0.22f, palette.sceneD, BlendMode.Lighten)

    oval(0.18f + mistDrift * 0.050f, 0.20f - mistDrift * 0.018f, 0.48f, 0.24f, palette.mistA)
    oval(0.75f - mistDrift * 0.044f, 0.18f + mistDrift * 0.012f, 0.42f, 0.23f, palette.mistB)
    oval(0.30f + mistDrift * 0.040f, 0.72f, 0.58f, 0.30f, palette.mistC, BlendMode.Lighten)
    oval(0.84f - mistDrift * 0.040f, 0.76f, 0.52f, 0.27f, palette.mistD, BlendMode.Lighten)

    drawRect(
        brush = Brush.linearGradient(
            colors = palette.ribbon,
            start = Offset(width * (-0.08f + mistDrift * 0.035f), height * 0.06f),
            end = Offset(width * (1.08f + mistDrift * 0.020f), height * 0.82f)
        ),
        size = Size(width, height),
        blendMode = BlendMode.Screen
    )

    if (palette.starAlpha > 0f) {
        val count = quality.starCount.coerceAtLeast(30).coerceAtMost(46)
        repeat(count) { index ->
            val x = ((index * 37 + 6) % 100) / 100f
            val y = ((index * 61 + 8) % 100) / 100f
            val wave = if (quality.enableMotion && motionScale > 0f) twinkle + 0.10f * sin(breathe * 6.28318f + index * 0.83f).toFloat() else 0.50f
            val alpha = (wave * palette.starAlpha * (0.72f - (index % 5) * 0.08f)).coerceIn(0.08f, 0.70f)
            val r = when {
                index % 11 == 0 -> 1.8f
                index % 5 == 0 -> 1.35f
                else -> 0.82f
            }
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = r,
                center = Offset(width * (x - mistDrift * 0.010f), height * (0.035f + y * 0.88f + mistDrift * 0.012f))
            )
        }
    }
}

private data class SampledPalette(
    val base: List<Color>,
    val overlay: List<Color>,
    val sceneA: Color,
    val sceneB: Color,
    val sceneC: Color,
    val sceneD: Color,
    val mistA: Color,
    val mistB: Color,
    val mistC: Color,
    val mistD: Color,
    val ribbon: List<Color>,
    val starAlpha: Float
)

private fun sampledPalette(theme: BackgroundTheme): SampledPalette = when (theme) {
    BackgroundTheme.Aurora -> SampledPalette(
        base = listOf(Color(0xFF071326), Color(0xFF14213F), Color(0xFF473E60)),
        overlay = listOf(Color(0x1A07101F), Color(0x080D1427), Color(0x52744F5C)),
        sceneA = Color(0x387AA8FF), sceneB = Color(0x475274B8), sceneC = Color(0x38675091), sceneD = Color(0x2E4A659B),
        mistA = Color(0x2BC4D5FF), mistB = Color(0x2188A7E8), mistC = Color(0x1FA486BC), mistD = Color(0x1CC479AE),
        ribbon = listOf(Color.Transparent, Color(0x09E8EFFF), Color.Transparent, Color(0x08D3C2EE), Color.Transparent),
        starAlpha = 1f
    )
    BackgroundTheme.Jade -> SampledPalette(
        base = listOf(Color(0xFF04141A), Color(0xFF062D36), Color(0xFF063C43)),
        overlay = listOf(Color(0x12020F14), Color(0x08020F14), Color(0x24020F14)),
        sceneA = Color(0x8A2EF2D0), sceneB = Color(0x6670F6FF), sceneC = Color(0x7522D8B8), sceneD = Color(0x3340FFF0),
        mistA = Color(0x33A8FFF0), mistB = Color(0x236CF6FF), mistC = Color(0x2877F8E2), mistD = Color(0x1FB1FCFF),
        ribbon = listOf(Color.Transparent, Color(0x24A8FFF0), Color.Transparent, Color(0x1C6CF6FF), Color.Transparent),
        starAlpha = 0.82f
    )
    BackgroundTheme.Sunset -> SampledPalette(
        base = listOf(Color(0xFF170A18), Color(0xFF341127), Color(0xFF351827)),
        overlay = listOf(Color(0x14100612), Color(0x0A100612), Color(0x2E100612)),
        sceneA = Color(0xA3FF8C6B), sceneB = Color(0x7AFF5E9E), sceneC = Color(0x70FFC56A), sceneD = Color(0x30FFB080),
        mistA = Color(0x32FFD6CB), mistB = Color(0x24FF9BBE), mistC = Color(0x24FFB39F), mistD = Color(0x20FFD58A),
        ribbon = listOf(Color.Transparent, Color(0x22FFD6CB), Color.Transparent, Color(0x1CFFD58A), Color.Transparent),
        starAlpha = 0.72f
    )
    BackgroundTheme.Dawn -> SampledPalette(
        base = listOf(Color(0xFFF7EAF7), Color(0xFFE9F7FF), Color(0xFFD8F5F1)),
        overlay = listOf(Color(0x10FFFFFF), Color(0x08FFFFFF), Color(0x18FFFFFF)),
        sceneA = Color(0xD1FFB7D7), sceneB = Color(0xC2A7D8FF), sceneC = Color(0xB8A7F7E6), sceneD = Color(0x50FFFFFF),
        mistA = Color(0x55FFFFFF), mistB = Color(0x35CFF4FF), mistC = Color(0x35FFD8EC), mistD = Color(0x32D7FFF5),
        ribbon = listOf(Color.Transparent, Color(0x40FFFFFF), Color.Transparent, Color(0x2AD7FFF5), Color.Transparent),
        starAlpha = 0f
    )
}
