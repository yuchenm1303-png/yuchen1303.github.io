package com.yuchen.ailedger.ui

import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.OpenGLGlassCardLayer
import com.yuchen.ailedger.ui.gl.RegisterBatchedOpenGlGlassItem

val LocalHeavyGlassStartupReady = compositionLocalOf { true }

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

private const val STRONG_GLASS_BLUR_DP = 118
private const val MEDIUM_GLASS_BLUR_DP = 82
private const val UNIFIED_GLASS_BACKDROP_ALPHA = 0.96f
private const val UNIFIED_EDGE_STRENGTH = 0.22f
private const val USE_CARD_BOUND_OPENGL_GLASS = true
private const val OPENGL_CARD_VISIBILITY_MARGIN_DP = 0
private const val MIN_OPENGL_CARD_SIZE_PX = 48

private fun blurForRole(role: GlassRole): Int = when (role) {
    GlassRole.Shell, GlassRole.Card, GlassRole.Floating -> STRONG_GLASS_BLUR_DP
    GlassRole.Nav -> 104
    GlassRole.Chip, GlassRole.Flex -> MEDIUM_GLASS_BLUR_DP
}

private fun roleUsesUnifiedBackdrop(role: GlassRole): Boolean = when (role) {
    GlassRole.Nav -> true
    GlassRole.Shell, GlassRole.Card, GlassRole.Chip, GlassRole.Floating, GlassRole.Flex -> false
}

private fun roleUsesCardBoundOpenGl(role: GlassRole): Boolean = when (role) {
    GlassRole.Shell, GlassRole.Card, GlassRole.Flex -> true
    GlassRole.Nav, GlassRole.Chip, GlassRole.Floating -> false
}

private fun roleUsesBatchedOpenGl(role: GlassRole): Boolean = false

private fun roleUsesSampledBackdrop(role: GlassRole): Boolean = when (role) {
    GlassRole.Shell, GlassRole.Card, GlassRole.Flex -> false
    GlassRole.Nav, GlassRole.Chip, GlassRole.Floating -> true
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

private fun LayoutCoordinates.isNearViewport(rootView: View, marginPx: Float): Boolean {
    val viewportW = rootView.width
    val viewportH = rootView.height
    if (viewportW <= 0 || viewportH <= 0) return true
    val bounds = boundsInWindow()
    return bounds.right >= -marginPx &&
        bounds.left <= viewportW + marginPx &&
        bounds.bottom >= -marginPx &&
        bounds.top <= viewportH + marginPx
}

private fun LayoutCoordinates.pushOpenGlFrameRect(
    key: Any,
    frameCoordinator: OpenGlGlassFrameCoordinator?,
    backdropOrigin: BackdropCoordinateSource?
) {
    if (!isAttached || frameCoordinator == null) return
    val topLeft = localToRoot(Offset.Zero)
    val origin = topLeft - (backdropOrigin?.rootOffset() ?: Offset.Zero)
    frameCoordinator.upsert(
        OpenGlGlassFrameRect(
            key = key,
            left = topLeft.x,
            top = topLeft.y,
            width = size.width.toFloat(),
            height = size.height.toFloat(),
            originX = origin.x,
            originY = origin.y
        )
    )
}

private fun useUnifiedChipGlass(sceneRegistry: GlassSceneRegistry?, role: GlassRole, heavyGlassReady: Boolean): Boolean {
    return heavyGlassReady &&
        role == GlassRole.Chip &&
        GlassFeatureFlags.USE_GLASS_SCENE_REGISTRY &&
        GlassFeatureFlags.USE_UNIFIED_CHIP_GLASS &&
        GlassFeatureFlags.USE_UNIFIED_GLASS_BACKGROUND_LAYER &&
        GlassFeatureFlags.USE_UNIFIED_GLASS_FOREGROUND_LAYER &&
        sceneRegistry != null
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
    val sceneRegistry = LocalGlassSceneRegistry.current
    val backdrop = LocalGlassBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameCoordinator = LocalOpenGlGlassFrameCoordinator.current
    val cardBackdrop = LocalBlurredBackdrop.current
    val rootView = LocalView.current
    val heavyGlassReady = LocalHeavyGlassStartupReady.current
    val visibilityMarginPx = with(LocalDensity.current) { OPENGL_CARD_VISIBILITY_MARGIN_DP.dp.toPx() }
    var nearViewport by remember { mutableStateOf(true) }
    var measuredOnce by remember { mutableStateOf(false) }
    var measuredWidth by remember { mutableStateOf(0) }
    var measuredHeight by remember { mutableStateOf(0) }
    val tracksViewport = heavyGlassReady &&
        (roleUsesCardBoundOpenGl(role) || roleUsesBatchedOpenGl(role) || (registry != null && roleUsesUnifiedBackdrop(role)))

    val hasValidOpenGlSize = measuredOnce &&
        measuredWidth >= MIN_OPENGL_CARD_SIZE_PX &&
        measuredHeight >= MIN_OPENGL_CARD_SIZE_PX
    val useCardOpenGlBackdrop = heavyGlassReady &&
        hasValidOpenGlSize &&
        nearViewport &&
        USE_CARD_BOUND_OPENGL_GLASS &&
        roleUsesCardBoundOpenGl(role) &&
        cardBackdrop != null
    val useBatchedOpenGlBackdrop = heavyGlassReady &&
        hasValidOpenGlSize &&
        nearViewport &&
        USE_CARD_BOUND_OPENGL_GLASS &&
        roleUsesBatchedOpenGl(role) &&
        cardBackdrop != null &&
        !useCardOpenGlBackdrop
    val useUnifiedBackdrop = heavyGlassReady &&
        nearViewport &&
        registry != null &&
        roleUsesUnifiedBackdrop(role) &&
        !useCardOpenGlBackdrop &&
        !useBatchedOpenGlBackdrop
    val useUnifiedChipGlass = useUnifiedChipGlass(sceneRegistry, role, heavyGlassReady)
    val lazyItemKey = LocalOpenGlLazyItemKey.current
    val key = remember(lazyItemKey) { lazyItemKey ?: Any() }

    RegisterGlassSceneNode(
        key = key,
        coordinates = coordinates,
        kind = if (useCardOpenGlBackdrop) GlassKind.OpenGlHero else glassKindForRole(role),
        radiusDp = effectiveRadius.toFloat(),
        depth = glassDepthForRole(role),
        intensity = glassIntensity,
        zIndex = glassZIndexForRole(role),
        quality = quality,
        role = role,
        rendererHint = when {
            useCardOpenGlBackdrop -> GlassRendererHint.OpenGlCard
            useUnifiedChipGlass -> GlassRendererHint.ComposeCanvas
            else -> GlassRendererHint.KeepExisting
        }
    )

    if (useBatchedOpenGlBackdrop) {
        RegisterBatchedOpenGlGlassItem(
            key = key,
            coordinates = coordinates,
            radius = effectiveRadius,
            role = role,
            glassIntensity = glassIntensity,
            enabled = true
        )
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
                    glassIntensity = glassIntensity,
                    edgeStrength = UNIFIED_EDGE_STRENGTH,
                    backdropAlpha = UNIFIED_GLASS_BACKDROP_ALPHA * glassIntensity.coerceIn(0.70f, 1.25f)
                )
            )
        }
        DisposableEffect(registry, key) {
            onDispose {
                registry?.remove(key)
                coordinates.coordinates = null
            }
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned {
                coordinates.coordinates = it
                val nextNearViewport = if (tracksViewport) it.isNearViewport(rootView, visibilityMarginPx) else true
                if (nearViewport != nextNearViewport) nearViewport = nextNearViewport
                if (!measuredOnce) measuredOnce = true
                if (measuredWidth != it.size.width) measuredWidth = it.size.width
                if (measuredHeight != it.size.height) measuredHeight = it.size.height

                val nextUseBatchedOpenGlBackdrop = heavyGlassReady &&
                    it.size.width >= MIN_OPENGL_CARD_SIZE_PX &&
                    it.size.height >= MIN_OPENGL_CARD_SIZE_PX &&
                    nextNearViewport &&
                    USE_CARD_BOUND_OPENGL_GLASS &&
                    roleUsesBatchedOpenGl(role) &&
                    cardBackdrop != null
                if (nextUseBatchedOpenGlBackdrop) {
                    it.pushOpenGlFrameRect(key, frameCoordinator, backdropOrigin)
                }
            }
            .glassContainerFrame(radius = effectiveRadius, glassIntensity = glassIntensity, useUnifiedSceneCanvas = useUnifiedChipGlass)
    ) {
        if (useCardOpenGlBackdrop) {
            OpenGLGlassCardLayer(
                radius = effectiveRadius,
                glassIntensity = glassIntensity,
                coordinateSource = coordinates,
                modifier = Modifier.matchParentSize()
            )
        } else if (heavyGlassReady && roleUsesSampledBackdrop(role) && !useUnifiedChipGlass && !useBatchedOpenGlBackdrop && !useUnifiedBackdrop && backdrop != null) {
            SampledWeatherGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = effectiveRadius,
                coordinateSource = coordinates,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                blurRadiusDp = blurForRole(role),
                liftAlpha = UNIFIED_GLASS_BACKDROP_ALPHA * glassIntensity.coerceIn(0.70f, 1.25f)
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
        if (!useUnifiedChipGlass && !useCardOpenGlBackdrop && !useBatchedOpenGlBackdrop) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .glassSkin(
                        quality = quality,
                        radius = effectiveRadius,
                        shimmer = shimmer,
                        breathe = breathe,
                        glassIntensity = glassIntensity,
                        includeShadow = false
                    )
            )
        }
        CompositionLocalProvider(LocalOpenGlLazyItemKey provides null) { content() }
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
    val sceneRegistry = LocalGlassSceneRegistry.current
    val backdrop = LocalGlassBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameCoordinator = LocalOpenGlGlassFrameCoordinator.current
    val cardBackdrop = LocalBlurredBackdrop.current
    val rootView = LocalView.current
    val heavyGlassReady = LocalHeavyGlassStartupReady.current
    val visibilityMarginPx = with(LocalDensity.current) { OPENGL_CARD_VISIBILITY_MARGIN_DP.dp.toPx() }
    var nearViewport by remember { mutableStateOf(true) }
    var measuredOnce by remember { mutableStateOf(false) }
    var measuredWidth by remember { mutableStateOf(0) }
    var measuredHeight by remember { mutableStateOf(0) }
    val lazyItemKey = LocalOpenGlLazyItemKey.current
    val key = remember(lazyItemKey) { lazyItemKey ?: Any() }
    val pressedIntensity = if (pressed) glassIntensity * 1.06f else glassIntensity
    val tracksViewport = heavyGlassReady &&
        (roleUsesCardBoundOpenGl(role) || roleUsesBatchedOpenGl(role) || (registry != null && roleUsesUnifiedBackdrop(role)))

    val hasValidOpenGlSize = measuredOnce &&
        measuredWidth >= MIN_OPENGL_CARD_SIZE_PX &&
        measuredHeight >= MIN_OPENGL_CARD_SIZE_PX
    val useCardOpenGlBackdrop = heavyGlassReady &&
        hasValidOpenGlSize &&
        nearViewport &&
        USE_CARD_BOUND_OPENGL_GLASS &&
        roleUsesCardBoundOpenGl(role) &&
        cardBackdrop != null
    val useBatchedOpenGlBackdrop = heavyGlassReady &&
        hasValidOpenGlSize &&
        nearViewport &&
        USE_CARD_BOUND_OPENGL_GLASS &&
        roleUsesBatchedOpenGl(role) &&
        cardBackdrop != null &&
        !useCardOpenGlBackdrop
    val useUnifiedBackdrop = heavyGlassReady &&
        nearViewport &&
        registry != null &&
        roleUsesUnifiedBackdrop(role) &&
        !useCardOpenGlBackdrop &&
        !useBatchedOpenGlBackdrop
    val useUnifiedChipGlass = useUnifiedChipGlass(sceneRegistry, role, heavyGlassReady)

    RegisterGlassSceneNode(
        key = key,
        coordinates = coordinates,
        kind = if (useCardOpenGlBackdrop) GlassKind.OpenGlHero else glassKindForRole(role),
        radiusDp = effectiveRadius.toFloat(),
        depth = glassDepthForRole(role),
        intensity = pressedIntensity,
        zIndex = glassZIndexForRole(role),
        quality = quality,
        role = role,
        pressed = pressed,
        rendererHint = when {
            useCardOpenGlBackdrop -> GlassRendererHint.OpenGlCard
            useUnifiedChipGlass -> GlassRendererHint.ComposeCanvas
            else -> GlassRendererHint.KeepExisting
        }
    )

    if (useBatchedOpenGlBackdrop) {
        RegisterBatchedOpenGlGlassItem(
            key = key,
            coordinates = coordinates,
            radius = effectiveRadius,
            role = role,
            glassIntensity = pressedIntensity,
            enabled = true
        )
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
                    glassIntensity = pressedIntensity,
                    edgeStrength = UNIFIED_EDGE_STRENGTH,
                    backdropAlpha = UNIFIED_GLASS_BACKDROP_ALPHA * pressedIntensity.coerceIn(0.70f, 1.25f)
                )
            )
        }
        DisposableEffect(registry, key) {
            onDispose {
                registry?.remove(key)
                coordinates.coordinates = null
            }
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned {
                coordinates.coordinates = it
                val nextNearViewport = if (tracksViewport) it.isNearViewport(rootView, visibilityMarginPx) else true
                if (nearViewport != nextNearViewport) nearViewport = nextNearViewport
                if (!measuredOnce) measuredOnce = true
                if (measuredWidth != it.size.width) measuredWidth = it.size.width
                if (measuredHeight != it.size.height) measuredHeight = it.size.height

                val nextUseBatchedOpenGlBackdrop = heavyGlassReady &&
                    it.size.width >= MIN_OPENGL_CARD_SIZE_PX &&
                    it.size.height >= MIN_OPENGL_CARD_SIZE_PX &&
                    nextNearViewport &&
                    USE_CARD_BOUND_OPENGL_GLASS &&
                    roleUsesBatchedOpenGl(role) &&
                    cardBackdrop != null
                if (nextUseBatchedOpenGlBackdrop) {
                    it.pushOpenGlFrameRect(key, frameCoordinator, backdropOrigin)
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift
                shadowElevation = if (!useUnifiedChipGlass && pressed) 0.18f else 0f
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassContainerFrame(radius = effectiveRadius, glassIntensity = pressedIntensity, useUnifiedSceneCanvas = useUnifiedChipGlass)
    ) {
        if (useCardOpenGlBackdrop) {
            OpenGLGlassCardLayer(
                radius = effectiveRadius,
                glassIntensity = pressedIntensity,
                coordinateSource = coordinates,
                modifier = Modifier.matchParentSize()
            )
        } else if (heavyGlassReady && roleUsesSampledBackdrop(role) && !useUnifiedChipGlass && !useBatchedOpenGlBackdrop && !useUnifiedBackdrop && backdrop != null) {
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
        if (!useUnifiedChipGlass && !useCardOpenGlBackdrop && !useBatchedOpenGlBackdrop) {
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
        CompositionLocalProvider(LocalOpenGlLazyItemKey provides null) { content() }
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

private fun Modifier.glassContainerFrame(radius: Int, glassIntensity: Float, useUnifiedSceneCanvas: Boolean): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    return if (useUnifiedSceneCanvas) {
        this.clip(shape)
    } else {
        this.glassOuterFrame(radius = radius, glassIntensity = glassIntensity)
    }
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
        val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
        val rimInset = 0.62.dp.toPx()
        val innerInset = 1.85.dp.toPx()
        val rimSize = androidx.compose.ui.geometry.Size(w - rimInset * 2f, h - rimInset * 2f)
        val innerSize = androidx.compose.ui.geometry.Size(w - innerInset * 2f, h - innerInset * 2f)
        val veil = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.frost * 0.28f * pulse),
                Color.White.copy(alpha = material.frost * 0.10f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.12f)
            ),
            startY = 0f,
            endY = h
        )
        val rim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.58f),
                Color.White.copy(alpha = material.rim * 0.05f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.14f)
            ),
            start = Offset.Zero,
            end = Offset(w, h)
        )
        val glint = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = material.motionGlint),
                Color.Transparent
            ),
            start = Offset(w * (safeShimmer - 0.28f), 0f),
            end = Offset(w * (safeShimmer + 0.16f), h * 0.20f)
        )
        onDrawWithContent {
            drawRect(veil, blendMode = BlendMode.Screen)
            drawContent()
            drawRoundRect(
                brush = rim,
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = cornerRadius,
                style = Stroke(width = 0.32.dp.toPx()),
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                color = Color.White.copy(alpha = material.rim * 0.10f),
                topLeft = Offset(innerInset, innerInset),
                size = innerSize,
                cornerRadius = cornerRadius,
                style = Stroke(width = 0.10.dp.toPx()),
                blendMode = BlendMode.Screen
            )
            if (quality.enableMotion) {
                drawRoundRect(
                    brush = glint,
                    topLeft = Offset(rimInset, rimInset),
                    size = rimSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 0.07.dp.toPx()),
                    blendMode = BlendMode.Plus
                )
            }
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
