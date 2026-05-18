package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.sin

enum class GlassRole(
    val fillScale: Float,
    val rimScale: Float,
    val glowScale: Float,
    val shadowDp: Int
) {
    Shell(0f, 1.12f, 0.80f, 14),
    Card(0f, 1.00f, 0.72f, 11),
    Chip(0f, 0.62f, 0.44f, 4),
    Nav(0f, 1.20f, 0.86f, 15),
    Floating(0f, 1.18f, 0.88f, 16)
}

@Composable
fun GlassPanel(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    role: GlassRole = GlassRole.Card,
    content: @Composable () -> Unit
) {
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    var globalOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { globalOffset = it.localToWindow(Offset.Zero) }
            .glassOuterFrame(radius = radius, role = role, glassIntensity = glassIntensity)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassSkin(
                    quality = quality,
                    radius = radius,
                    shimmer = shimmer,
                    breathe = breathe,
                    glassIntensity = glassIntensity,
                    role = role,
                    includeShadow = false
                )
        )
        content()
    }
}

@Composable
fun PressableGlass(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    role: GlassRole = GlassRole.Chip,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.982f else 1f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
        label = "glass-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 0.40f else 0f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
        label = "glass-press-lift"
    )
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    var globalOffset by remember { mutableStateOf(Offset.Zero) }
    val pressedIntensity = if (pressed) glassIntensity * 1.08f else glassIntensity

    Box(
        modifier = modifier
            .onGloballyPositioned { globalOffset = it.localToWindow(Offset.Zero) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift
                shadowElevation = if (pressed) 0.25f else 0f
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassOuterFrame(radius = radius, role = role, glassIntensity = pressedIntensity)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassSkin(
                    quality = quality,
                    radius = radius,
                    shimmer = shimmer + if (pressed) 0.030f else 0f,
                    breathe = breathe,
                    glassIntensity = pressedIntensity,
                    role = role,
                    includeShadow = false
                )
        )
        content()
    }
}

@Composable
private fun rememberGlassShimmer(quality: RenderQuality, motionIntensity: Float): Float {
    if (!quality.enableMotion || motionIntensity <= 0.02f) return 0.18f
    val transition = rememberInfiniteTransition(label = "glass-shimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((15000 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glass-shimmer-value"
    )
    return shimmer
}

@Composable
private fun rememberGlassBreath(quality: RenderQuality, motionIntensity: Float): Float {
    if (!quality.enableMotion || motionIntensity <= 0.02f) return 0.42f
    val transition = rememberInfiniteTransition(label = "glass-breath")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((9200 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glass-breath-value"
    )
    return breathe
}

private fun Modifier.glassOuterFrame(
    radius: Int,
    role: GlassRole,
    glassIntensity: Float
): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val material = glassMaterial(role, glassIntensity)
    return this
        .shadow(
            elevation = role.shadowDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = material.shadowAmbient),
            spotColor = Color.White.copy(alpha = material.shadowSpot)
        )
        .clip(shape)
}

fun Modifier.glassSkin(
    quality: RenderQuality,
    radius: Int,
    shimmer: Float = 0f,
    breathe: Float = 0.42f,
    glassIntensity: Float = 1f,
    role: GlassRole = GlassRole.Card,
    includeShadow: Boolean = true
): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val material = glassMaterial(role, glassIntensity)
    val pulse = 0.92f + breathe * 0.055f
    val safeShimmer = shimmer - shimmer.toInt()
    val base = if (includeShadow) {
        this.glassOuterFrame(radius = radius, role = role, glassIntensity = glassIntensity)
    } else {
        this.clip(shape)
    }

    return base.drawWithCache {
        val w = size.width
        val h = size.height
        val drift = safeShimmer - 0.5f
        val phase = safeShimmer * 6.28318f
        val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())

        val outerGlowStroke = when (role) {
            GlassRole.Nav -> 2.20.dp.toPx()
            GlassRole.Chip -> 0.72.dp.toPx()
            else -> 1.70.dp.toPx()
        }
        val thicknessStroke = when (role) {
            GlassRole.Nav -> 0.82.dp.toPx()
            GlassRole.Chip -> 0.32.dp.toPx()
            else -> 0.66.dp.toPx()
        }
        val outerStroke = when (role) {
            GlassRole.Nav -> 0.50.dp.toPx()
            GlassRole.Chip -> 0.26.dp.toPx()
            else -> 0.44.dp.toPx()
        }
        val innerStroke = when (role) {
            GlassRole.Chip -> 0.14.dp.toPx()
            else -> 0.26.dp.toPx()
        }
        val hairStroke = when (role) {
            GlassRole.Chip -> 0.10.dp.toPx()
            else -> 0.16.dp.toPx()
        }

        val outerGlowInset = outerGlowStroke / 2f
        val thicknessInset = 0.94.dp.toPx()
        val outerInset = outerStroke / 2f
        val innerInset = if (role == GlassRole.Nav) 2.05.dp.toPx() else 1.70.dp.toPx()
        val hairInset = if (role == GlassRole.Nav) 3.55.dp.toPx() else 2.95.dp.toPx()
        val outerGlowSize = Size(w - outerGlowStroke, h - outerGlowStroke)
        val thicknessSize = Size(w - thicknessInset * 2f, h - thicknessInset * 2f)
        val outerSize = Size(w - outerStroke, h - outerStroke)
        val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
        val hairSize = Size(w - hairInset * 2f, h - hairInset * 2f)

        val topLens = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.topHighlight * pulse),
                Color.White.copy(alpha = material.topHighlight * 0.20f),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.28f
        )
        val lowerShade = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow)
            ),
            startY = h * 0.58f,
            endY = h
        )
        val sideCatch = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.sideHighlight),
                Color.Transparent,
                Color.Transparent,
                Color.White.copy(alpha = material.sideHighlight * 0.40f)
            )
        )
        val movingEdgeGlint = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = material.motionGlint),
                Color.White.copy(alpha = material.motionGlint * 0.20f),
                Color.Transparent
            ),
            start = Offset(w * (safeShimmer - 0.30f), 0f),
            end = Offset(w * (safeShimmer + 0.18f), h * 0.24f)
        )
        val cornerCatchlight = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = material.cornerHighlight),
                Color.White.copy(alpha = material.cornerHighlight * 0.22f),
                Color.Transparent
            ),
            center = Offset(w * (0.035f + drift * 0.010f), h * 0.020f),
            radius = w * 0.30f
        )

        val featherRim = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.34f),
                Color.White.copy(alpha = material.rim * 0.060f),
                Color.Transparent,
                Color.Transparent,
                Color.White.copy(alpha = material.rim * 0.020f)
            ),
            startY = 0f,
            endY = h
        )
        val thicknessRim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.42f),
                Color.White.copy(alpha = material.rim * 0.090f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.55f),
                Color.White.copy(alpha = material.rim * 0.045f)
            ),
            start = Offset(w * 0.03f, 0f),
            end = Offset(w * 0.98f, h)
        )
        val outerRim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.62f),
                Color.White.copy(alpha = material.rim * 0.14f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.40f),
                Color.White.copy(alpha = material.rim * 0.055f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        val innerRefraction = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.080f),
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.42f),
                Color.White.copy(alpha = material.rim * 0.022f)
            ),
            start = Offset(w * 0.08f, 0f),
            end = Offset(w * 0.94f, h)
        )
        val topHairline = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.74f),
                Color.White.copy(alpha = material.rim * 0.12f),
                Color.Transparent,
                Color.Transparent
            ),
            startY = 0f,
            endY = h
        )

        onDrawWithContent {
            drawRect(topLens, blendMode = BlendMode.Screen)
            drawRect(sideCatch, blendMode = BlendMode.Screen)
            drawRect(lowerShade, blendMode = BlendMode.Multiply)
            drawContent()

            if (role != GlassRole.Chip) {
                drawRoundRect(brush = featherRim, topLeft = Offset(outerGlowInset, outerGlowInset), size = outerGlowSize, cornerRadius = cornerRadius, style = Stroke(width = outerGlowStroke), blendMode = BlendMode.Screen)
                drawRoundRect(brush = thicknessRim, topLeft = Offset(thicknessInset, thicknessInset), size = thicknessSize, cornerRadius = cornerRadius, style = Stroke(width = thicknessStroke), blendMode = BlendMode.Screen)
            }
            drawRoundRect(brush = outerRim, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = outerStroke), blendMode = BlendMode.Screen)
            if (role != GlassRole.Chip) {
                drawRoundRect(brush = innerRefraction, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = cornerRadius, style = Stroke(width = innerStroke), blendMode = BlendMode.SrcOver)
                drawRoundRect(brush = topHairline, topLeft = Offset(hairInset, hairInset), size = hairSize, cornerRadius = cornerRadius, style = Stroke(width = hairStroke), blendMode = BlendMode.Screen)
                if (quality.enableMotion) {
                    drawRoundRect(brush = movingEdgeGlint, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.18.dp.toPx()), blendMode = BlendMode.Plus)
                }
                drawRoundRect(brush = cornerCatchlight, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.26.dp.toPx()), blendMode = BlendMode.Screen)
            }
        }
    }
}

private data class GlassMaterial(
    val rim: Float,
    val topHighlight: Float,
    val sideHighlight: Float,
    val cornerHighlight: Float,
    val motionGlint: Float,
    val depthShadow: Float,
    val shadowAmbient: Float,
    val shadowSpot: Float
)

private fun glassMaterial(role: GlassRole, intensity: Float): GlassMaterial {
    val safeIntensity = intensity.coerceIn(0.25f, 1.45f)
    val base = when (role) {
        GlassRole.Shell -> GlassMaterial(0.180f, 0.050f, 0.020f, 0.052f, 0.018f, 0.026f, 0.090f, 0.022f)
        GlassRole.Card -> GlassMaterial(0.150f, 0.038f, 0.016f, 0.042f, 0.014f, 0.022f, 0.072f, 0.018f)
        GlassRole.Chip -> GlassMaterial(0.082f, 0.018f, 0.006f, 0.018f, 0.006f, 0.010f, 0.030f, 0.006f)
        GlassRole.Nav -> GlassMaterial(0.198f, 0.048f, 0.020f, 0.050f, 0.016f, 0.024f, 0.080f, 0.020f)
        GlassRole.Floating -> GlassMaterial(0.190f, 0.046f, 0.018f, 0.050f, 0.016f, 0.024f, 0.076f, 0.018f)
    }
    return GlassMaterial(
        rim = (base.rim * safeIntensity * role.rimScale).coerceIn(0.030f, 0.300f),
        topHighlight = (base.topHighlight * safeIntensity * role.glowScale).coerceIn(0.004f, 0.085f),
        sideHighlight = (base.sideHighlight * safeIntensity * role.glowScale).coerceIn(0.002f, 0.040f),
        cornerHighlight = (base.cornerHighlight * safeIntensity * role.glowScale).coerceIn(0.004f, 0.085f),
        motionGlint = (base.motionGlint * safeIntensity * role.glowScale).coerceIn(0.002f, 0.038f),
        depthShadow = (base.depthShadow * safeIntensity * role.rimScale).coerceIn(0.003f, 0.050f),
        shadowAmbient = (base.shadowAmbient * safeIntensity).coerceIn(0.012f, 0.120f),
        shadowSpot = (base.shadowSpot * safeIntensity).coerceIn(0.002f, 0.032f)
    )
}
