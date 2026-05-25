package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.OpenGLGlassCardLayer
import kotlinx.coroutines.launch

enum class GlassRole(
    val fillScale: Float,
    val rimScale: Float,
    val glowScale: Float,
    val shadowDp: Int
) {
    Shell(0f, 1.00f, 1.00f, 10),
    Card(0f, 1.00f, 1.00f, 10),
    Chip(0f, 1.00f, 1.00f, 8),
    Nav(0f, 1.00f, 1.00f, 10),
    Floating(0f, 1.00f, 1.00f, 10),
    Flex(0f, 1.00f, 1.00f, 8)
}

/**
 * When a page-level OpenGL viewport is active, Shell glass is painted by that single
 * viewport layer instead of creating one TextureView per Shell card. Ordinary roles
 * stay fully Compose based and remain isolated from OpenGL.
 */
val LocalOpenGLGlassViewportActive = compositionLocalOf { false }

private const val STRONG_GLASS_BLUR_DP = 118
private const val MEDIUM_GLASS_BLUR_DP = 82
private const val UNIFIED_GLASS_BACKDROP_ALPHA = 0.96f
private const val UNIFIED_EDGE_STRENGTH = 0.22f
private val ShellPressPreloadEasing = CubicBezierEasing(0.18f, 0.00f, 0.10f, 1.00f)
private val ShellPressSinkEasing = CubicBezierEasing(0.18f, 0.00f, 0.00f, 1.00f)
private val ShellPressReleaseEasing = CubicBezierEasing(0.20f, 0.00f, 0.18f, 1.00f)

/**
 * Root OpenGL gate.
 *
 * Keep this enabled so deliberately promoted Shell glass can use the card-bound
 * TextureView/OpenGL path. Ordinary glass is still protected by roleUsesCardBoundOpenGl(),
 * so Card, Chip, Floating, Nav and Flex cannot enter OpenGL even when this gate is on.
 */
private const val USE_CARD_BOUND_OPENGL_GLASS = true

private fun blurForRole(role: GlassRole): Int = when (role) {
    GlassRole.Shell, GlassRole.Card, GlassRole.Floating -> STRONG_GLASS_BLUR_DP
    GlassRole.Nav -> 104
    GlassRole.Chip, GlassRole.Flex -> MEDIUM_GLASS_BLUR_DP
}

private fun roleUsesUnifiedBackdrop(role: GlassRole): Boolean = when (role) {
    GlassRole.Shell -> true
    GlassRole.Card, GlassRole.Nav, GlassRole.Chip, GlassRole.Floating, GlassRole.Flex -> false
}

/**
 * OpenGL is intentionally fenced at the root role policy.
 *
 * The rolled-back app uses GlassRole.Card for many ordinary surfaces: message bubbles,
 * text inputs, list rows, hint cards and compact tool entries. Letting every Card enter
 * OpenGL makes the boundary depend on naming accidents rather than architecture.
 *
 * For this branch, only Shell is allowed to create a card-bound OpenGL layer. Ordinary
 * cards keep the Compose backdrop/skin path. If a future screen needs a truly large
 * OpenGL card, promote that container deliberately instead of reusing Card for small UI.
 */
private fun roleUsesCardBoundOpenGl(role: GlassRole): Boolean = when (role) {
    GlassRole.Shell -> true
    GlassRole.Card, GlassRole.Nav, GlassRole.Chip, GlassRole.Floating, GlassRole.Flex -> false
}

private fun effectiveGlassRadius(radius: Int, role: GlassRole): Int {
    if (radius >= 999) return radius
    return when (role) {
        GlassRole.Shell -> radius.coerceAtLeast(30)
        GlassRole.Card -> radius.coerceAtLeast(28)
        GlassRole.Floating -> radius.coerceAtLeast(26)
        GlassRole.Nav -> radius.coerceAtLeast(999)
        GlassRole.Chip, GlassRole.Flex -> radius
    }
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
    val effectiveRadius = effectiveGlassRadius(radius, role)
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    val coordinates = remember { GlassCoordinateSource() }
    val registry = LocalGlassItemRegistry.current
    val backdrop = LocalGlassBackdrop.current
    val cardBackdrop = LocalBlurredBackdrop.current
    val viewportOwnsShell = LocalOpenGLGlassViewportActive.current && role == GlassRole.Shell
    val useCardOpenGlBackdrop = USE_CARD_BOUND_OPENGL_GLASS && !viewportOwnsShell && roleUsesCardBoundOpenGl(role) && cardBackdrop != null
    val useUnifiedBackdrop = registry != null && roleUsesUnifiedBackdrop(role) && !useCardOpenGlBackdrop && !viewportOwnsShell
    val key = remember { Any() }

    val shellPressEnabled = role == GlassRole.Shell && motionIntensity > 0.02f
    val shellPress = remember { Animatable(0f) }
    val shellPressScope = rememberCoroutineScope()
    var shellPressSize by remember { mutableStateOf(Size(1f, 1f)) }
    val shellPressValue = if (shellPressEnabled) shellPress.value.coerceIn(-0.22f, 1.18f) else 0f
    val shellPressCompression = shellPressValue.coerceAtLeast(0f)
    val shellPressRebound = (-shellPressValue).coerceAtLeast(0f)
    val pressedGlassIntensity = glassIntensity * (1f + shellPressCompression * 0.30f - shellPressRebound * 0.06f)
    val shellPressModifier = if (shellPressEnabled) {
        Modifier
            .onSizeChanged { size ->
                shellPressSize = Size(
                    width = size.width.coerceAtLeast(1).toFloat(),
                    height = size.height.coerceAtLeast(1).toFloat()
                )
            }
            .pointerInput(motionIntensity) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val localX = (down.position.x / shellPressSize.width).coerceIn(0f, 1f)
                    val localY = (down.position.y / shellPressSize.height).coerceIn(0f, 1f)
                    // Local coordinates are intentionally sampled here even before the
                    // shader takes a center uniform: keeping this branch in place makes
                    // the interaction extensible without consuming scroll gestures.
                    if (localX >= 0f && localY >= 0f) {
                        shellPressScope.launch {
                            shellPress.stop()
                            shellPress.animateTo(
                                targetValue = 0.22f,
                                animationSpec = tween(durationMillis = 86, easing = ShellPressPreloadEasing)
                            )
                            shellPress.animateTo(
                                targetValue = 0.92f,
                                animationSpec = tween(durationMillis = 240, easing = ShellPressSinkEasing)
                            )
                            shellPress.animateTo(
                                targetValue = 0.98f,
                                animationSpec = spring(dampingRatio = 0.90f, stiffness = Spring.StiffnessLow)
                            )
                        }
                    }
                    val up = waitForUpOrCancellation()
                    shellPressScope.launch {
                        shellPress.stop()
                        if (up != null) {
                            val releaseShelf = (shellPress.value * 0.34f).coerceIn(0.14f, 0.24f)
                            shellPress.animateTo(
                                targetValue = releaseShelf,
                                animationSpec = tween(durationMillis = 128, easing = ShellPressReleaseEasing)
                            )
                            shellPress.animateTo(
                                targetValue = -0.16f,
                                animationSpec = tween(durationMillis = 145, easing = FastOutSlowInEasing)
                            )
                            shellPress.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessLow)
                            )
                        } else {
                            shellPress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }
            }
    } else {
        Modifier
    }

    if (useUnifiedBackdrop) {
        SideEffect {
            registry?.upsert(
                GlassRenderItem(
                    key = key,
                    coordinates = coordinates,
                    radius = effectiveRadius,
                    role = role,
                    quality = quality,
                    glassIntensity = pressedGlassIntensity,
                    edgeStrength = UNIFIED_EDGE_STRENGTH,
                    backdropAlpha = UNIFIED_GLASS_BACKDROP_ALPHA * pressedGlassIntensity.coerceIn(0.70f, 1.25f)
                )
            )
        }
    }
    DisposableEffect(registry, key, useUnifiedBackdrop) {
        onDispose { registry?.remove(key) }
    }

    Box(
        modifier = modifier
            .then(shellPressModifier)
            .onPlaced { coordinates.coordinates = it }
            .graphicsLayer {
                if (shellPressEnabled) {
                    // Do not scale the Shell container: scaling would visually stretch the
                    // TextureView/OpenGL output instead of resampling the backdrop. The
                    // visible capsule squash is painted as an optical rim/content response.
                    translationY = shellPressCompression * 1.30f - shellPressRebound * 0.95f
                    shadowElevation = shellPressCompression * 0.26f
                }
            }
            .glassOuterFrame(radius = effectiveRadius, glassIntensity = pressedGlassIntensity)
    ) {
        if (useCardOpenGlBackdrop) {
            OpenGLGlassCardLayer(
                radius = effectiveRadius,
                glassIntensity = pressedGlassIntensity,
                coordinateSource = coordinates,
                pressProgress = shellPressCompression,
                modifier = Modifier.matchParentSize()
            )
        } else if (!useUnifiedBackdrop && !viewportOwnsShell && backdrop != null) {
            SampledWeatherGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = effectiveRadius,
                coordinateSource = coordinates,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                blurRadiusDp = blurForRole(role),
                liftAlpha = UNIFIED_GLASS_BACKDROP_ALPHA * pressedGlassIntensity.coerceIn(0.70f, 1.25f)
            )
            SampledWeatherEdgeRefraction(
                modifier = Modifier.matchParentSize(),
                radius = effectiveRadius,
                coordinateSource = coordinates,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                strength = UNIFIED_EDGE_STRENGTH
            )
        }
        if (!useCardOpenGlBackdrop) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .glassSkin(
                        quality = quality,
                        radius = effectiveRadius,
                        shimmer = shimmer + shellPressCompression * 0.052f + shellPressRebound * 0.024f,
                        breathe = breathe,
                        glassIntensity = pressedGlassIntensity,
                        includeShadow = false
                    )
            )
        }
        if (shellPressEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1f + shellPressCompression * 0.010f - shellPressRebound * 0.006f
                        scaleY = 1f - shellPressCompression * 0.014f + shellPressRebound * 0.008f
                        translationY = shellPressCompression * 0.72f - shellPressRebound * 0.42f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            ) { content() }
        } else {
            content()
        }
        if (shellPressEnabled && (shellPressValue > 0.001f || shellPressValue < -0.001f)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shellPressOptics(press = shellPressValue, radius = effectiveRadius)
            )
        }
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
    val effectiveRadius = effectiveGlassRadius(radius, role)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "glass-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 0.28f else 0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "glass-press-lift"
    )
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    val coordinates = remember { GlassCoordinateSource() }
    val registry = LocalGlassItemRegistry.current
    val backdrop = LocalGlassBackdrop.current
    val cardBackdrop = LocalBlurredBackdrop.current
    val key = remember { Any() }
    val pressedIntensity = if (pressed) glassIntensity * 1.06f else glassIntensity
    val viewportOwnsShell = LocalOpenGLGlassViewportActive.current && role == GlassRole.Shell
    val useCardOpenGlBackdrop = USE_CARD_BOUND_OPENGL_GLASS && !viewportOwnsShell && roleUsesCardBoundOpenGl(role) && cardBackdrop != null
    val useUnifiedBackdrop = registry != null && roleUsesUnifiedBackdrop(role) && !useCardOpenGlBackdrop && !viewportOwnsShell

    if (useUnifiedBackdrop) {
        SideEffect {
            registry?.upsert(
                GlassRenderItem(
                    key = key,
                    coordinates = coordinates,
                    radius = effectiveRadius,
                    role = role,
                    quality = quality,
                    glassIntensity = pressedIntensity,
                    edgeStrength = UNIFIED_EDGE_STRENGTH,
                    backdropAlpha = UNIFIED_GLASS_BACKDROP_ALPHA * pressedIntensity.coerceIn(0.70f, 1.25f)
                )
            )
        }
    }
    DisposableEffect(registry, key, useUnifiedBackdrop) {
        onDispose { registry?.remove(key) }
    }

    Box(
        modifier = modifier
            .onPlaced { coordinates.coordinates = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift
                shadowElevation = if (pressed) 0.18f else 0f
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassOuterFrame(radius = effectiveRadius, glassIntensity = pressedIntensity)
    ) {
        if (useCardOpenGlBackdrop) {
            OpenGLGlassCardLayer(
                radius = effectiveRadius,
                glassIntensity = pressedIntensity,
                coordinateSource = coordinates,
                modifier = Modifier.matchParentSize()
            )
        } else if (!useUnifiedBackdrop && !viewportOwnsShell && backdrop != null) {
            SampledWeatherGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = effectiveRadius,
                coordinateSource = coordinates,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                blurRadiusDp = blurForRole(role),
                liftAlpha = UNIFIED_GLASS_BACKDROP_ALPHA * pressedIntensity.coerceIn(0.70f, 1.25f)
            )
            SampledWeatherEdgeRefraction(
                modifier = Modifier.matchParentSize(),
                radius = effectiveRadius,
                coordinateSource = coordinates,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                strength = UNIFIED_EDGE_STRENGTH
            )
        }
        if (!useCardOpenGlBackdrop) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .glassSkin(
                        quality = quality,
                        radius = effectiveRadius,
                        shimmer = shimmer + if (pressed) 0.024f else 0f,
                        breathe = breathe,
                        glassIntensity = pressedIntensity,
                        includeShadow = false
                    )
            )
        }
        content()
    }
}

@Composable
private fun rememberGlassShimmer(quality: RenderQuality, motionIntensity: Float): Float {
    return if (quality.enableMotion && motionIntensity > 0.02f) 0.20f else 0.16f
}

@Composable
private fun rememberGlassBreath(quality: RenderQuality, motionIntensity: Float): Float {
    return if (quality.enableMotion && motionIntensity > 0.02f) 0.38f else 0.34f
}

private fun Modifier.glassOuterFrame(radius: Int, glassIntensity: Float): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val material = glassMaterial(glassIntensity)
    return this
        .shadow(
            elevation = 5.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = material.shadowAmbient),
            spotColor = Color.White.copy(alpha = material.shadowSpot)
        )
        .clip(shape)
}

private fun Modifier.shellPressOptics(press: Float, radius: Int): Modifier {
    val safePress = press.coerceIn(-0.22f, 1.18f)
    if (safePress > -0.001f && safePress < 0.001f) return this

    return this.drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val p = (safePress.coerceAtLeast(0f) / 1.05f).coerceIn(0f, 1f)
        val rebound = ((-safePress).coerceAtLeast(0f) / 0.18f).coerceIn(0f, 1f)
        val compression = p * p
        val minSide = minOf(w, h).coerceAtLeast(1f)
        val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
        val rimInset = (0.72.dp + (3.80f * p).dp - (1.65f * rebound).dp).toPx()
        val rimSize = Size(
            width = (w - rimInset * 2f).coerceAtLeast(1f),
            height = (h - rimInset * 2f).coerceAtLeast(1f)
        )

        val pressureBloom = Brush.radialGradient(
            colors = listOf(
                Color(0xFFE6FFFF).copy(alpha = 0.060f * p + 0.018f * rebound),
                Color(0xFFA7F7FF).copy(alpha = 0.022f * p + 0.012f * rebound),
                Color.Transparent
            ),
            center = Offset(w * 0.50f, h * (0.34f - 0.025f * rebound)),
            radius = maxOf(w, h) * (0.52f + 0.07f * rebound)
        )
        val surfaceDent = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.038f * p + 0.026f * rebound),
                Color.Transparent,
                Color(0xFF031020).copy(alpha = 0.038f * compression)
            ),
            startY = h * 0.10f,
            endY = h * 0.78f
        )
        val topCompressedRim = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF2FFFF).copy(alpha = 0.170f * p + 0.065f * rebound),
                Color(0xFF91F4FF).copy(alpha = 0.060f * p + 0.030f * rebound),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * (0.22f + 0.04f * p)
        )
        val rimCompression = Brush.linearGradient(
            colors = listOf(
                Color(0xFFDFFFFF).copy(alpha = 0.060f * p + 0.036f * rebound),
                Color.Transparent,
                Color(0xFF02101D).copy(alpha = 0.048f * compression),
                Color(0xFFCFFFFF).copy(alpha = 0.042f * p + 0.026f * rebound)
            ),
            start = Offset(w * 0.08f, 0f),
            end = Offset(w * 0.94f, h)
        )
        val lowerWeightShade = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color(0xFF020B16).copy(alpha = 0.085f * compression)
            ),
            startY = h * 0.48f,
            endY = h
        )
        val leftCapsuleGlint = Brush.radialGradient(
            colors = listOf(
                Color(0xFFD8FFFF).copy(alpha = 0.090f * p + 0.034f * rebound),
                Color(0xFF88F5FF).copy(alpha = 0.022f * p + 0.012f * rebound),
                Color.Transparent
            ),
            center = Offset(w * 0.045f, h * 0.40f),
            radius = minSide * (0.66f + 0.12f * p)
        )
        val rightCapsuleGlint = Brush.radialGradient(
            colors = listOf(
                Color(0xFFE8FFFF).copy(alpha = 0.076f * p + 0.030f * rebound),
                Color(0xFF91F4FF).copy(alpha = 0.018f * p + 0.010f * rebound),
                Color.Transparent
            ),
            center = Offset(w * 0.955f, h * 0.38f),
            radius = minSide * (0.62f + 0.12f * p)
        )

        onDrawWithContent {
            drawContent()
            drawOval(
                brush = pressureBloom,
                topLeft = Offset(w * 0.08f, h * 0.04f),
                size = Size(w * 0.84f, h * 0.68f),
                blendMode = BlendMode.Screen
            )
            drawOval(
                brush = surfaceDent,
                topLeft = Offset(w * 0.13f, h * 0.10f),
                size = Size(w * 0.74f, h * 0.66f),
                blendMode = BlendMode.SrcOver
            )
            drawOval(
                brush = leftCapsuleGlint,
                topLeft = Offset(-w * 0.10f, h * 0.12f),
                size = Size(w * 0.28f, h * 0.74f),
                blendMode = BlendMode.Screen
            )
            drawOval(
                brush = rightCapsuleGlint,
                topLeft = Offset(w * 0.82f, h * 0.12f),
                size = Size(w * 0.28f, h * 0.74f),
                blendMode = BlendMode.Screen
            )
            drawRect(brush = lowerWeightShade, blendMode = BlendMode.Multiply)
            drawRoundRect(
                brush = topCompressedRim,
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = cornerRadius,
                style = Stroke(width = (0.92.dp + (0.32f * p).dp).toPx()),
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = rimCompression,
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = cornerRadius,
                style = Stroke(width = (0.68.dp + (0.22f * p).dp).toPx()),
                blendMode = BlendMode.SrcOver
            )
        }
    }
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
    val material = glassMaterial(glassIntensity)
    val pulse = 0.92f + breathe * 0.045f
    val safeShimmer = shimmer - shimmer.toInt()
    val base = if (includeShadow) this.glassOuterFrame(radius = radius, glassIntensity = glassIntensity) else this.clip(shape)

    return base.drawWithCache {
        val w = size.width
        val h = size.height
        val drift = safeShimmer - 0.5f
        val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
        val rimInset = 0.62.dp.toPx()
        val innerInset = 1.85.dp.toPx()
        val bottomInset = 2.10.dp.toPx()
        val rimSize = Size(w - rimInset * 2f, h - rimInset * 2f)
        val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
        val bottomSize = Size(w - bottomInset * 2f, h - bottomInset * 2f)

        val frostedNeutralVeil = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.frost * 0.28f * pulse),
                Color.White.copy(alpha = material.frost * 0.10f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.11f)
            ),
            startY = 0f,
            endY = h
        )
        val topLens = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.topHighlight * 0.48f * pulse),
                Color.White.copy(alpha = material.topHighlight * 0.055f),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.22f
        )
        val lowerShade = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.52f)
            ),
            startY = h * 0.64f,
            endY = h
        )
        val mainRim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.58f),
                Color.White.copy(alpha = material.rim * 0.048f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.14f),
                Color.White.copy(alpha = material.rim * 0.016f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        val topHairline = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.42f),
                Color.White.copy(alpha = material.rim * 0.030f),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.18f
        )
        val innerSoftRim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.022f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.10f),
                Color.White.copy(alpha = material.rim * 0.008f)
            ),
            start = Offset(w * 0.10f, 0f),
            end = Offset(w * 0.94f, h)
        )
        val bottomShadow = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.34f)
            ),
            startY = h * 0.58f,
            endY = h
        )
        val movingEdgeGlint = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = material.motionGlint),
                Color.Transparent
            ),
            start = Offset(w * (safeShimmer - 0.28f), 0f),
            end = Offset(w * (safeShimmer + 0.16f), h * 0.20f)
        )
        val cornerCatchlight = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = material.cornerHighlight),
                Color.White.copy(alpha = material.cornerHighlight * 0.08f),
                Color.Transparent
            ),
            center = Offset(w * (0.035f + drift * 0.010f), h * 0.020f),
            radius = w * 0.26f
        )

        onDrawWithContent {
            drawRect(frostedNeutralVeil, blendMode = BlendMode.Screen)
            drawRect(topLens, blendMode = BlendMode.Screen)
            drawRect(lowerShade, blendMode = BlendMode.Multiply)
            drawContent()
            drawRoundRect(brush = mainRim, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(width = 0.32.dp.toPx()), blendMode = BlendMode.Screen)
            drawRoundRect(brush = topHairline, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.10.dp.toPx()), blendMode = BlendMode.Screen)
            drawRoundRect(brush = innerSoftRim, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.10.dp.toPx()), blendMode = BlendMode.SrcOver)
            drawRoundRect(brush = bottomShadow, topLeft = Offset(bottomInset, bottomInset), size = bottomSize, cornerRadius = cornerRadius, style = Stroke(width = 0.10.dp.toPx()), blendMode = BlendMode.Multiply)
            if (quality.enableMotion) {
                drawRoundRect(brush = movingEdgeGlint, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(width = 0.07.dp.toPx()), blendMode = BlendMode.Plus)
            }
            drawRoundRect(brush = cornerCatchlight, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(width = 0.13.dp.toPx()), blendMode = BlendMode.Screen)
        }
    }
}

private data class GlassMaterial(
    val frost: Float,
    val rim: Float,
    val topHighlight: Float,
    val cornerHighlight: Float,
    val motionGlint: Float,
    val depthShadow: Float,
    val shadowAmbient: Float,
    val shadowSpot: Float
)

private fun glassMaterial(intensity: Float): GlassMaterial {
    val safeIntensity = intensity.coerceIn(0.25f, 1.45f)
    val base = GlassMaterial(
        frost = 0.044f,
        rim = 0.104f,
        topHighlight = 0.036f,
        cornerHighlight = 0.021f,
        motionGlint = 0.0035f,
        depthShadow = 0.015f,
        shadowAmbient = 0.028f,
        shadowSpot = 0.0035f
    )
    return GlassMaterial(
        frost = (base.frost * safeIntensity).coerceIn(0.006f, 0.052f),
        rim = (base.rim * safeIntensity).coerceIn(0.018f, 0.154f),
        topHighlight = (base.topHighlight * safeIntensity).coerceIn(0.002f, 0.048f),
        cornerHighlight = (base.cornerHighlight * safeIntensity).coerceIn(0.002f, 0.038f),
        motionGlint = (base.motionGlint * safeIntensity).coerceIn(0.001f, 0.008f),
        depthShadow = (base.depthShadow * safeIntensity).coerceIn(0.002f, 0.027f),
        shadowAmbient = (base.shadowAmbient * safeIntensity).coerceIn(0.004f, 0.046f),
        shadowSpot = (base.shadowSpot * safeIntensity).coerceIn(0.001f, 0.008f)
    )
}