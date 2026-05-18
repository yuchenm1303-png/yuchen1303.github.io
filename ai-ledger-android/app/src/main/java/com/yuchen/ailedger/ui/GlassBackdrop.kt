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
    val alphaScale = liftAlpha.coerceIn(0.45f, 1.65f)
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
                motionScale = motionScale,
                alphaScale = alphaScale
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.070f * alphaScale),
                    Color.White.copy(alpha = 0.030f * alphaScale),
                    Color.White.copy(alpha = 0.010f * alphaScale),
                    Color.Transparent
                )
            ),
            blendMode = BlendMode.Screen
        )
        drawRect(
            brush = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.040f * alphaScale),
                    Color.White.copy(alpha = 0.012f * alphaScale),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.18f, size.height * 0.02f),
                radius = size.width * 0.72f
            ),
            blendMode = BlendMode.Screen
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.026f * alphaScale)
                ),
                startY = size.height * 0.56f,
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
        val topBand = (size.height * 0.20f).coerceIn(14f, 46.dp.toPx())
        val sideBand = (size.width * 0.080f).coerceIn(10f, 32.dp.toPx())
        val bottomBand = (size.height * 0.17f).coerceIn(10f, 38.dp.toPx())
        val shift = (5.dp.toPx() + 3.dp.toPx() * alphaScale).coerceAtMost(10.dp.toPx())

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
            sampledShiftedBackground(dx = 0f, dy = shift, localAlpha = alphaScale * 1.10f)
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.160f * alphaScale),
                        Color.White.copy(alpha = 0.055f * alphaScale),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = topBand
                ),
                blendMode = BlendMode.Screen
            )
        }
        clipRect(left = 0f, top = 0f, right = sideBand, bottom = size.height) {
            sampledShiftedBackground(dx = shift, dy = 0f)
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.White.copy(alpha = 0.092f * alphaScale), Color.Transparent),
                    startX = 0f,
                    endX = sideBand
                ),
                blendMode = BlendMode.Screen
            )
        }
        clipRect(left = size.width - sideBand, top = 0f, right = size.width, bottom = size.height) {
            sampledShiftedBackground(dx = -shift, dy = 0f)
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.050f * alphaScale)),
                    startX = size.width - sideBand,
                    endX = size.width
                ),
                blendMode = BlendMode.Screen
            )
        }
        clipRect(left = 0f, top = size.height - bottomBand, right = size.width, bottom = size.height) {
            sampledShiftedBackground(dx = 0f, dy = -shift, localAlpha = alphaScale * 0.95f)
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.018f * alphaScale),
                        Color.Black.copy(alpha = 0.070f * alphaScale)
                    ),
                    startY = size.height - bottomBand,
                    endY = size.height
                ),
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
    val a = alphaScale.coerceIn(0f, 1.75f)

    drawRect(
        brush = Brush.linearGradient(
            colors = palette.base,
            start = Offset(width * 0.06f, 0f),
            end = Offset(width * 0.92f, height)
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
                listOf(lifted, lifted.copy(alpha = lifted.alpha * 0.42f), Color.Transparent),
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
            colors = palette.ribbon.map { it.copy(alpha = (it.alpha * a).coerceIn(0f, 1f)) },
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
            val alpha = (wave * palette.starAlpha * a * (0.72f - (index % 5) * 0.08f)).coerceIn(0.06f, 0.78f)
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
        overlay = listOf(Color(0x1807101F), Color(0x060D1427), Color(0x47744F5C)),
        sceneA = Color(0x4C7AA8FF), sceneB = Color(0x5A5274B8), sceneC = Color(0x46675091), sceneD = Color(0x384A659B),
        mistA = Color(0x42C4D5FF), mistB = Color(0x3288A7E8), mistC = Color(0x30A486BC), mistD = Color(0x2CC479AE),
        ribbon = listOf(Color.Transparent, Color(0x14E8EFFF), Color.Transparent, Color(0x12D3C2EE), Color.Transparent),
        starAlpha = 1f
    )
    BackgroundTheme.Jade -> SampledPalette(
        base = listOf(Color(0xFF04141A), Color(0xFF062D36), Color(0xFF063C43)),
        overlay = listOf(Color(0x10020F14), Color(0x06020F14), Color(0x20020F14)),
        sceneA = Color(0x962EF2D0), sceneB = Color(0x7470F6FF), sceneC = Color(0x8022D8B8), sceneD = Color(0x4440FFF0),
        mistA = Color(0x48A8FFF0), mistB = Color(0x346CF6FF), mistC = Color(0x3A77F8E2), mistD = Color(0x32B1FCFF),
        ribbon = listOf(Color.Transparent, Color(0x32A8FFF0), Color.Transparent, Color(0x286CF6FF), Color.Transparent),
        starAlpha = 0.82f
    )
    BackgroundTheme.Sunset -> SampledPalette(
        base = listOf(Color(0xFF170A18), Color(0xFF341127), Color(0xFF351827)),
        overlay = listOf(Color(0x12100612), Color(0x08100612), Color(0x28100612)),
        sceneA = Color(0xB0FF8C6B), sceneB = Color(0x8EFF5E9E), sceneC = Color(0x84FFC56A), sceneD = Color(0x42FFB080),
        mistA = Color(0x48FFD6CB), mistB = Color(0x34FF9BBE), mistC = Color(0x34FFB39F), mistD = Color(0x30FFD58A),
        ribbon = listOf(Color.Transparent, Color(0x32FFD6CB), Color.Transparent, Color(0x28FFD58A), Color.Transparent),
        starAlpha = 0.72f
    )
    BackgroundTheme.Dawn -> SampledPalette(
        base = listOf(Color(0xFFF7EAF7), Color(0xFFE9F7FF), Color(0xFFD8F5F1)),
        overlay = listOf(Color(0x12FFFFFF), Color(0x08FFFFFF), Color(0x20FFFFFF)),
        sceneA = Color(0xD8FFB7D7), sceneB = Color(0xCBA7D8FF), sceneC = Color(0xC4A7F7E6), sceneD = Color(0x66FFFFFF),
        mistA = Color(0x66FFFFFF), mistB = Color(0x4ACFF4FF), mistC = Color(0x4AFFD8EC), mistD = Color(0x46D7FFF5),
        ribbon = listOf(Color.Transparent, Color(0x50FFFFFF), Color.Transparent, Color(0x38D7FFF5), Color.Transparent),
        starAlpha = 0f
    )
}
