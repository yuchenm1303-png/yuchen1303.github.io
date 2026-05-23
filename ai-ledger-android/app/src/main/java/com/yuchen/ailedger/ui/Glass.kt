package com.yuchen.ailedger.ui

import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.max
import kotlin.math.min

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
    GlassRole.Shell, GlassRole.Card, GlassRole.Nav -> true
    GlassRole.Chip, GlassRole.Floating, GlassRole.Flex -> false
}

private fun roleUsesCardBoundOpenGl(role: GlassRole): Boolean = when (role) {
    GlassRole.Shell, GlassRole.Card, GlassRole.Flex -> true
    GlassRole.Nav, GlassRole.Chip, GlassRole.Floating -> false
}

private fun roleUsesSampledBackdrop(role: GlassRole): Boolean = when (role) {
    GlassRole.Flex -> false
    GlassRole.Shell, GlassRole.Card, GlassRole.Nav, GlassRole.Chip, GlassRole.Floating -> true
}

private fun roleUsesBatchedChrome(role: GlassRole): Boolean = when (role) {
    GlassRole.Chip, GlassRole.Floating -> true
    GlassRole.Shell, GlassRole.Card, GlassRole.Nav, GlassRole.Flex -> false
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

data class BatchedGlassChromeRenderItem(
    val key: Any,
    val coordinates: GlassCoordinateSource,
    val radius: Int,
    val role: GlassRole,
    val glassIntensity: Float,
    val pressed: Boolean
)

class BatchedGlassChromeRegistry {
    private val items = linkedMapOf<Any, BatchedGlassChromeRenderItem>()
    private var cachedSnapshot: List<BatchedGlassChromeRenderItem> = emptyList()
    private var dirty = true

    var version by mutableLongStateOf(0L)
        private set

    fun upsert(item: BatchedGlassChromeRenderItem) {
        if (items[item.key] == item) return
        items[item.key] = item
        dirty = true
        version += 1L
    }

    fun remove(key: Any) {
        if (items.remove(key) != null) {
            dirty = true
            version += 1L
        }
    }

    fun snapshot(): List<BatchedGlassChromeRenderItem> {
        if (dirty) {
            cachedSnapshot = items.values.toList()
            dirty = false
        }
        return cachedSnapshot
    }
}

val LocalBatchedGlassChromeRegistry = compositionLocalOf<BatchedGlassChromeRegistry?> { null }

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
    val rootView = LocalView.current
    val heavyGlassReady = LocalHeavyGlassStartupReady.current
    val visibilityMarginPx = with(LocalDensity.current) { OPENGL_CARD_VISIBILITY_MARGIN_DP.dp.toPx() }
    var nearViewport by remember { mutableStateOf(true) }
    var measuredOnce by remember { mutableStateOf(false) }
    var measuredWidth by remember { mutableStateOf(0) }
    var measuredHeight by remember { mutableStateOf(0) }
    val tracksViewport = heavyGlassReady &&
        (roleUsesCardBoundOpenGl(role) || (registry != null && roleUsesUnifiedBackdrop(role)))

    val hasValidOpenGlSize = measuredOnce &&
        measuredWidth >= MIN_OPENGL_CARD_SIZE_PX &&
        measuredHeight >= MIN_OPENGL_CARD_SIZE_PX
    val useCardOpenGlBackdrop = heavyGlassReady &&
        hasValidOpenGlSize &&
        nearViewport &&
        USE_CARD_BOUND_OPENGL_GLASS &&
        roleUsesCardBoundOpenGl(role) &&
        cardBackdrop != null
    val useUnifiedBackdrop = heavyGlassReady &&
        nearViewport &&
        registry != null &&
        roleUsesUnifiedBackdrop(role) &&
        !useCardOpenGlBackdrop
    val useStableSampledBackdrop = heavyGlassReady &&
        roleUsesSampledBackdrop(role) &&
        !useUnifiedBackdrop &&
        backdrop != null
    val key = remember { Any() }

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
                if (tracksViewport) {
                    val nextNearViewport = it.isNearViewport(rootView, visibilityMarginPx)
                    if (nearViewport != nextNearViewport) nearViewport = nextNearViewport
                } else if (!nearViewport) {
                    nearViewport = true
                }
                if (!measuredOnce) measuredOnce = true
                if (measuredWidth != it.size.width) measuredWidth = it.size.width
                if (measuredHeight != it.size.height) measuredHeight = it.size.height
            }
            .glassOuterFrame(radius = effectiveRadius, glassIntensity = glassIntensity)
    ) {
        if (useStableSampledBackdrop && backdrop != null) {
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
        if (useCardOpenGlBackdrop) {
            OpenGLGlassCardLayer(
                radius = effectiveRadius,
                glassIntensity = glassIntensity,
                coordinateSource = coordinates,
                modifier = Modifier.matchParentSize()
            )
        }
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
    val chromeRegistry = LocalBatchedGlassChromeRegistry.current
    val backdrop = LocalGlassBackdrop.current
    val cardBackdrop = LocalBlurredBackdrop.current
    val rootView = LocalView.current
    val heavyGlassReady = LocalHeavyGlassStartupReady.current
    val visibilityMarginPx = with(LocalDensity.current) { OPENGL_CARD_VISIBILITY_MARGIN_DP.dp.toPx() }
    var nearViewport by remember { mutableStateOf(true) }
    var measuredOnce by remember { mutableStateOf(false) }
    var measuredWidth by remember { mutableStateOf(0) }
    var measuredHeight by remember { mutableStateOf(0) }
    val key = remember { Any() }
    val pressedIntensity = if (pressed) glassIntensity * 1.06f else glassIntensity
    val tracksViewport = heavyGlassReady &&
        (roleUsesCardBoundOpenGl(role) || roleUsesBatchedChrome(role) || (registry != null && roleUsesUnifiedBackdrop(role)))

    val hasValidOpenGlSize = measuredOnce &&
        measuredWidth >= MIN_OPENGL_CARD_SIZE_PX &&
        measuredHeight >= MIN_OPENGL_CARD_SIZE_PX
    val useBatchedChrome = heavyGlassReady &&
        hasValidOpenGlSize &&
        nearViewport &&
        chromeRegistry != null &&
        roleUsesBatchedChrome(role)
    val useCardOpenGlBackdrop = heavyGlassReady &&
        hasValidOpenGlSize &&
        nearViewport &&
        USE_CARD_BOUND_OPENGL_GLASS &&
        roleUsesCardBoundOpenGl(role) &&
        cardBackdrop != null
    val useUnifiedBackdrop = heavyGlassReady &&
        nearViewport &&
        registry != null &&
        roleUsesUnifiedBackdrop(role) &&
        !useCardOpenGlBackdrop &&
        !useBatchedChrome
    val useStableSampledBackdrop = heavyGlassReady &&
        roleUsesSampledBackdrop(role) &&
        !useUnifiedBackdrop &&
        !useBatchedChrome &&
        backdrop != null

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

    RegisterBatchedGlassChromeItem(
        key = key,
        coordinates = coordinates,
        radius = effectiveRadius,
        role = role,
        glassIntensity = pressedIntensity,
        pressed = pressed,
        enabled = useBatchedChrome
    )

    Box(
        modifier = modifier
            .onGloballyPositioned {
                coordinates.coordinates = it
                if (tracksViewport) {
                    val nextNearViewport = it.isNearViewport(rootView, visibilityMarginPx)
                    if (nearViewport != nextNearViewport) nearViewport = nextNearViewport
                } else if (!nearViewport) {
                    nearViewport = true
                }
                if (!measuredOnce) measuredOnce = true
                if (measuredWidth != it.size.width) measuredWidth = it.size.width
                if (measuredHeight != it.size.height) measuredHeight = it.size.height
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift
                shadowElevation = if (pressed) 0.18f else 0f
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassOuterFrame(radius = effectiveRadius, glassIntensity = pressedIntensity)
    ) {
        if (useStableSampledBackdrop && backdrop != null) {
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
        if (useCardOpenGlBackdrop) {
            OpenGLGlassCardLayer(
                radius = effectiveRadius,
                glassIntensity = pressedIntensity,
                coordinateSource = coordinates,
                modifier = Modifier.matchParentSize()
            )
        }
        if (useBatchedChrome) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .cheapGlassChromeBase(role = role, glassIntensity = pressedIntensity, pressed = pressed)
            )
        } else {
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
private fun RegisterBatchedGlassChromeItem(
    key: Any,
    coordinates: GlassCoordinateSource,
    radius: Int,
    role: GlassRole,
    glassIntensity: Float,
    pressed: Boolean,
    enabled: Boolean
) {
    val registry = LocalBatchedGlassChromeRegistry.current
    if (enabled && registry != null) {
        SideEffect {
            registry.upsert(
                BatchedGlassChromeRenderItem(
                    key = key,
                    coordinates = coordinates,
                    radius = radius,
                    role = role,
                    glassIntensity = glassIntensity,
                    pressed = pressed
                )
            )
        }
    }
    DisposableEffect(registry, key, enabled) {
        onDispose { registry?.remove(key) }
    }
}

@Composable
fun BatchedGlassChromeOverlayLayer(modifier: Modifier = Modifier) {
    val registry = LocalBatchedGlassChromeRegistry.current
    val frameTicker = LocalBackdropFrameTicker.current
    val registryVersion = registry?.version ?: 0L

    androidx.compose.foundation.Canvas(modifier = modifier) {
        registryVersion
        frameTicker?.frameNanos
        registry?.snapshot().orEmpty().forEach { item ->
            drawBatchedGlassChromeItem(item)
        }
    }
}

private fun DrawScope.drawBatchedGlassChromeItem(item: BatchedGlassChromeRenderItem) {
    if (!item.coordinates.isAttached()) return
    val itemSize = item.coordinates.itemSize()
    if (itemSize.width <= 0 || itemSize.height <= 0) return

    val rawTopLeft = item.coordinates.rootOffset()
    val rawWidth = itemSize.width.toFloat()
    val rawHeight = itemSize.height.toFloat()
    if (rawTopLeft.x >= size.width || rawTopLeft.y >= size.height || rawTopLeft.x + rawWidth <= 0f || rawTopLeft.y + rawHeight <= 0f) return

    val visualScale = if (item.pressed) 0.975f else 1f
    val visualWidth = rawWidth * visualScale
    val visualHeight = rawHeight * visualScale
    val topLeft = rawTopLeft + Offset((rawWidth - visualWidth) * 0.5f, (rawHeight - visualHeight) * 0.5f + if (item.pressed) 0.28.dp.toPx() else 0f)
    val itemSizePx = Size(visualWidth, visualHeight)
    val radiusPx = if (item.radius >= 999) min(visualWidth, visualHeight) * 0.5f else item.radius.dp.toPx().coerceAtMost(min(visualWidth, visualHeight) * 0.5f)
    val corner = CornerRadius(radiusPx, radiusPx)
    val material = glassMaterial(item.glassIntensity * if (item.pressed) 1.06f else 1f)
    val floatingBoost = if (item.role == GlassRole.Floating) 1.18f else 1.0f
    val pressedBoost = if (item.pressed) 1.22f else 1.0f
    val rimInset = 0.62.dp.toPx()
    val innerInset = 1.72.dp.toPx()
    val rimTopLeft = topLeft + Offset(rimInset, rimInset)
    val rimSize = Size((itemSizePx.width - rimInset * 2f).coerceAtLeast(1f), (itemSizePx.height - rimInset * 2f).coerceAtLeast(1f))
    val innerTopLeft = topLeft + Offset(innerInset, innerInset)
    val innerSize = Size((itemSizePx.width - innerInset * 2f).coerceAtLeast(1f), (itemSizePx.height - innerInset * 2f).coerceAtLeast(1f))

    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = material.topHighlight * 0.50f * floatingBoost * pressedBoost),
                Color.White.copy(alpha = material.topHighlight * 0.06f),
                Color.Transparent
            ),
            startY = topLeft.y,
            endY = topLeft.y + itemSizePx.height * 0.38f
        ),
        topLeft = topLeft,
        size = itemSizePx,
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.42f * floatingBoost)
            ),
            startY = topLeft.y + itemSizePx.height * 0.48f,
            endY = topLeft.y + itemSizePx.height
        ),
        topLeft = topLeft,
        size = itemSizePx,
        cornerRadius = corner,
        blendMode = BlendMode.Multiply
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = material.rim * 0.72f * floatingBoost * pressedBoost),
                Color.White.copy(alpha = material.rim * 0.08f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.16f),
                Color.White.copy(alpha = material.rim * 0.03f)
            ),
            start = topLeft,
            end = topLeft + Offset(itemSizePx.width, itemSizePx.height)
        ),
        topLeft = rimTopLeft,
        size = rimSize,
        cornerRadius = corner,
        style = Stroke(width = 0.42.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = material.rim * 0.040f * floatingBoost),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.13f * floatingBoost)
            ),
            start = topLeft + Offset(itemSizePx.width * 0.10f, 0f),
            end = topLeft + Offset(itemSizePx.width * 0.94f, itemSizePx.height)
        ),
        topLeft = innerTopLeft,
        size = innerSize,
        cornerRadius = corner,
        style = Stroke(width = 0.16.dp.toPx()),
        blendMode = BlendMode.SrcOver
    )
    if (item.pressed) {
        drawRoundRect(
            color = Color.White.copy(alpha = 0.030f),
            topLeft = topLeft,
            size = itemSizePx,
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )
    }
}

private fun Modifier.cheapGlassChromeBase(role: GlassRole, glassIntensity: Float, pressed: Boolean): Modifier {
    val safeIntensity = glassIntensity.coerceIn(0.25f, 1.45f)
    val baseAlpha = when (role) {
        GlassRole.Floating -> 0.060f
        GlassRole.Chip -> 0.044f
        else -> 0.040f
    }
    val pressedBoost = if (pressed) 0.020f else 0f
    return this.background(Color.White.copy(alpha = (baseAlpha * safeIntensity + pressedBoost).coerceIn(0.018f, 0.105f)))
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
