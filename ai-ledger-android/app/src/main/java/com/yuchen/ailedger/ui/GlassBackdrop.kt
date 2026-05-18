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
import androidx.compose.ui.graphics.drawscope.clipRect
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
    blurRadiusDp: Int = 24,
    liftAlpha: Float = 1f
) {
    val view = LocalView.current
    val motionScale = motionIntensity.coerceIn(0f, 1.4f)
    val alphaScale = liftAlpha.coerceIn(0.35f, 1.45f)
    val transition = rememberInfiniteTransition(label = "sampled-glass-backdrop")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (quality.enableMotion && motionScale > 0f) (52000 / motionScale.coerceAtLeast(0.35f)).toInt() else 86000,
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
                durationMillis = if (quality.enableMotion && motionScale > 0f) (104000 / motionScale.coerceAtLeast(0.35f)).toInt() else 150000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sampled-bg-drift"
    )
    val twinkle by transition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.74f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (quality.enableMotion && motionScale > 0f) (17000 / motionScale.coerceAtLeast(0.35f)).toInt() else 38000,
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
                motionScale = motionScale,
                alphaScale = alphaScale
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.055f * alphaScale),
                    Color.White.copy(alpha = 0.020f * alphaScale),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.016f * alphaScale)
                )
            ),
            blendMode = BlendMode.Screen
        )
        drawRect(
            brush = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.035f * alphaScale),
                    Color.White.copy(alpha = 0.010f * alphaScale),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.18f, size.height * 0.02f),
                radius = size.width * 0.68f
            ),
            blendMode = BlendMode.Screen
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.020f * alphaScale)
                ),
                startY = size.height * 0.58f,
                endY = size.height
            ),
            blendMode = BlendMode.Multiply
        )
    }
}

@Composable
fun SampledWeatherEdgeRefraction(
    modifier: Modifier = Modifier,
    radius: Int,
    globalOffset: Offset,
    quality: RenderQuality,
    motionIntensity: Float,
    theme: BackgroundTheme,
    strength: Float = 1f
) {
    val view = LocalView.current
    val motionScale = motionIntensity.coerceIn(0f, 1.4f)
    val alphaScale = strength.coerceIn(0f, 1.75f)
    val transition = rememberInfiniteTransition(label = "sampled-edge-refraction")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (quality.enableMotion) (56000 / motionScale.coerceAtLeast(0.35f)).toInt() else 90000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edge-bg-breathe"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (quality.enableMotion) (118000 / motionScale.coerceAtLeast(0.35f)).toInt() else 160000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edge-bg-drift"
    )
    val twinkle by transition.animateFloat(
        initialValue = 0.36f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (quality.enableMotion) (19000 / motionScale.coerceAtLeast(0.35f)).toInt() else 42000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edge-bg-twinkle"
    )

    Canvas(
        modifier = modifier.clip(RoundedCornerShape(radius.dp))
    ) {
        val rootW = if (view.width > 0) view.width.toFloat() else size.width
        val rootH = if (view.height > 0) view.height.toFloat() else size.height
        val topBand = (size.height * 0.14f).coerceIn(8f, 28.dp.toPx())
        val sideBand = (size.width * 0.045f).coerceIn(6f, 18.dp.toPx())
        val bottomBand = (size.height * 0.11f).coerceIn(8f, 24.dp.toPx())
        val shift = (3.dp.toPx() + 1.5.dp.toPx() * alphaScale).coerceAtMost(6.dp.toPx())

        fun sampledShiftedBackground(dx: Float, dy: Float, localAlpha: Float = alphaScale) {
            withTransform({ translate(left = -globalOffset.x + dx, top = -globalOffset.y + dy) }) {
                drawSampledWeatherSky(
                    width = rootW,
                    height = rootH,
                    theme = theme,
                    quality = quality,
                    breathe = breathe,
                    drift = drift,
                    twinkle = twinkle,
                    motionScale = motionScale,
                    alphaScale = localAlpha
                )
            }
        }

        clipRect(left = 0f, top = 0f, right = size.width, bottom = topBand) {
            sampledShiftedBackground(dx = 0f, dy = shift, localAlpha = alphaScale * 0.60f)
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.060f * alphaScale), Color.Transparent),
                    startY = 0f,
                    endY = topBand
                ),
                blendMode = BlendMode.Screen
            )
        }
        clipRect(left = 0f, top = 0f, right = sideBand, bottom = size.height) {
            sampledShiftedBackground(dx = shift, dy = 0f, localAlpha = alphaScale * 0.52f)
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.038f * alphaScale), Color.Transparent)),
                blendMode = BlendMode.Screen
            )
        }
        clipRect(left = size.width - sideBand, top = 0f, right = size.width, bottom = size.height) {
            sampledShiftedBackground(dx = -shift, dy = 0f, localAlpha = alphaScale * 0.46f)
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.022f * alphaScale))),
                blendMode = BlendMode.Screen
            )
        }
        clipRect(left = 0f, top = size.height - bottomBand, right = size.width, bottom = size.height) {
            sampledShiftedBackground(dx = 0f, dy = -shift, localAlpha = alphaScale * 0.44f)
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.030f * alphaScale))),
                blendMode = BlendMode.Multiply
            )
        }
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
    motionScale: Float,
    alphaScale: Float = 1f
) {
    val palette = sampledPalette(theme)
    val breatheDrift = (breathe - 0.5f) * motionScale
    val mistDrift = (drift - 0.5f) * motionScale
    val a = alphaScale.coerceIn(0f, 1.55f)

    drawRect(
        brush = Brush.linearGradient(
            colors = palette.base,
            start = Offset(width * 0.02f, 0f),
            end = Offset(width * 0.98f, height)
        ),
        size = Size(width, height)
    )
    drawRect(
        brush = Brush.verticalGradient(palette.overlay.map { it.copy(alpha = (it.alpha * a).coerceIn(0f, 1f)) }),
        size = Size(width, height),
        blendMode = if (theme == BackgroundTheme.Dawn) BlendMode.Screen else BlendMode.Multiply
    )

    fun oval(cx: Float, cy: Float, rw: Float, rh: Float, color: Color, mode: BlendMode = BlendMode.Screen) {
        val lifted = color.copy(alpha = (color.alpha * a).coerceIn(0f, 1f))
        drawOval(
            brush = Brush.radialGradient(
                listOf(lifted, lifted.copy(alpha = lifted.alpha * 0.44f), Color.Transparent),
                center = Offset(width * cx, height * cy),
                radius = width * rw
            ),
            topLeft = Offset(width * (cx - rw), height * (cy - rh)),
            size = Size(width * rw * 2f, height * rh * 2f),
            blendMode = mode
        )
    }

    oval(0.76f - breatheDrift * 0.020f, 0.10f + breatheDrift * 0.012f, 0.30f, 0.18f, palette.sceneA)
    oval(0.30f + breatheDrift * 0.020f, 0.34f + breatheDrift * 0.014f, 0.44f, 0.26f, palette.sceneB)
    oval(0.64f - breatheDrift * 0.012f, 0.72f - breatheDrift * 0.006f, 0.48f, 0.28f, palette.sceneC, BlendMode.Lighten)
    oval(0.18f + mistDrift * 0.028f, 0.92f, 0.44f, 0.22f, palette.sceneD, BlendMode.Lighten)

    oval(0.10f + mistDrift * 0.050f, 0.18f - mistDrift * 0.018f, 0.52f, 0.24f, palette.mistA)
    oval(0.76f - mistDrift * 0.044f, 0.22f + mistDrift * 0.012f, 0.46f, 0.23f, palette.mistB)
    oval(0.34f + mistDrift * 0.040f, 0.68f, 0.62f, 0.30f, palette.mistC, BlendMode.Lighten)
    oval(0.86f - mistDrift * 0.040f, 0.78f, 0.56f, 0.27f, palette.mistD, BlendMode.Lighten)

    drawRect(
        brush = Brush.linearGradient(
            colors = palette.ribbon.map { it.copy(alpha = (it.alpha * a).coerceIn(0f, 1f)) },
            start = Offset(width * (-0.18f + mistDrift * 0.040f), height * 0.08f),
            end = Offset(width * (1.12f + mistDrift * 0.026f), height * 0.70f)
        ),
        size = Size(width, height),
        blendMode = BlendMode.Screen
    )
    drawRect(
        brush = Brush.linearGradient(
            colors = palette.secondRibbon.map { it.copy(alpha = (it.alpha * a).coerceIn(0f, 1f)) },
            start = Offset(width * (1.10f - mistDrift * 0.030f), height * 0.02f),
            end = Offset(width * (-0.12f - mistDrift * 0.020f), height * 0.92f)
        ),
        size = Size(width, height),
        blendMode = BlendMode.Screen
    )

    if (palette.starAlpha > 0f) {
        val count = quality.starCount.coerceAtLeast(42).coerceAtMost(72)
        repeat(count) { index ->
            val x = ((index * 37 + 6) % 100) / 100f
            val y = ((index * 61 + 8) % 100) / 100f
            val wave = if (quality.enableMotion && motionScale > 0f) twinkle + 0.12f * sin(breathe * 6.28318f + index * 0.83f).toFloat() else 0.52f
            val alpha = (wave * palette.starAlpha * a * (0.74f - (index % 5) * 0.075f)).coerceIn(0.045f, 0.72f)
            val r = when {
                index % 17 == 0 -> 2.0f
                index % 7 == 0 -> 1.45f
                else -> 0.78f
            }
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = r,
                center = Offset(width * (x - mistDrift * 0.010f), height * (0.035f + y * 0.90f + mistDrift * 0.012f))
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
    val secondRibbon: List<Color>,
    val starAlpha: Float
)

private fun sampledPalette(theme: BackgroundTheme): SampledPalette = when (theme) {
    BackgroundTheme.Aurora -> SampledPalette(
        base = listOf(Color(0xFF071225), Color(0xFF172847), Color(0xFF3C365C), Color(0xFF5A4266)),
        overlay = listOf(Color(0x10040B18), Color(0x040A1428), Color(0x407A5160)),
        sceneA = Color(0x5A7DB4FF), sceneB = Color(0x665775BF), sceneC = Color(0x566B579B), sceneD = Color(0x444B6FA9),
        mistA = Color(0x52D8E4FF), mistB = Color(0x3C88B8F0), mistC = Color(0x38B78AC8), mistD = Color(0x34D17DB8),
        ribbon = listOf(Color.Transparent, Color(0x20E8F4FF), Color.Transparent, Color(0x18D8C6F2), Color.Transparent),
        secondRibbon = listOf(Color.Transparent, Color(0x16B5E6FF), Color.Transparent, Color(0x14FF9FD6), Color.Transparent),
        starAlpha = 1f
    )
    BackgroundTheme.Jade -> SampledPalette(
        base = listOf(Color(0xFF031018), Color(0xFF06303A), Color(0xFF064852), Color(0xFF073B46)),
        overlay = listOf(Color(0x0E020F14), Color(0x05020F14), Color(0x1E020F14)),
        sceneA = Color(0x9E2EF2D0), sceneB = Color(0x8070F6FF), sceneC = Color(0x8822D8B8), sceneD = Color(0x5240FFF0),
        mistA = Color(0x56A8FFF0), mistB = Color(0x406CF6FF), mistC = Color(0x4277F8E2), mistD = Color(0x38B1FCFF),
        ribbon = listOf(Color.Transparent, Color(0x3AA8FFF0), Color.Transparent, Color(0x306CF6FF), Color.Transparent),
        secondRibbon = listOf(Color.Transparent, Color(0x20FFFFFF), Color.Transparent, Color(0x1C2EF2D0), Color.Transparent),
        starAlpha = 0.82f
    )
    BackgroundTheme.Sunset -> SampledPalette(
        base = listOf(Color(0xFF120818), Color(0xFF2B1030), Color(0xFF40204A), Color(0xFF1D1D3C)),
        overlay = listOf(Color(0x0E100612), Color(0x06100612), Color(0x28100612)),
        sceneA = Color(0xBAFF8C6B), sceneB = Color(0x9AFF5E9E), sceneC = Color(0x8EFFC56A), sceneD = Color(0x55FFB080),
        mistA = Color(0x54FFD6CB), mistB = Color(0x40FF9BBE), mistC = Color(0x40FFB39F), mistD = Color(0x36FFD58A),
        ribbon = listOf(Color.Transparent, Color(0x3AFFD6CB), Color.Transparent, Color(0x30FFD58A), Color.Transparent),
        secondRibbon = listOf(Color.Transparent, Color(0x24A7D8FF), Color.Transparent, Color(0x22FF7AB6), Color.Transparent),
        starAlpha = 0.78f
    )
    BackgroundTheme.Dawn -> SampledPalette(
        base = listOf(Color(0xFFF7EAF7), Color(0xFFE9F7FF), Color(0xFFD8F5F1), Color(0xFFEAF0FF)),
        overlay = listOf(Color(0x12FFFFFF), Color(0x08FFFFFF), Color(0x20FFFFFF)),
        sceneA = Color(0xD8FFB7D7), sceneB = Color(0xCBA7D8FF), sceneC = Color(0xC4A7F7E6), sceneD = Color(0x66FFFFFF),
        mistA = Color(0x66FFFFFF), mistB = Color(0x4ACFF4FF), mistC = Color(0x4AFFD8EC), mistD = Color(0x46D7FFF5),
        ribbon = listOf(Color.Transparent, Color(0x50FFFFFF), Color.Transparent, Color(0x38D7FFF5), Color.Transparent),
        secondRibbon = listOf(Color.Transparent, Color(0x30A7D8FF), Color.Transparent, Color(0x26FFD8EC), Color.Transparent),
        starAlpha = 0f
    )
}
