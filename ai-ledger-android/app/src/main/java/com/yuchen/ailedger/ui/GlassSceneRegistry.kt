package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.yuchen.ailedger.model.RenderQuality

enum class GlassKind {
    Surface,
    Chip,
    Recessed,
    Nav,
    OpenGlHero
}

enum class GlassRendererHint {
    KeepExisting,
    ComposeCanvas,
    OpenGlCard
}

data class GlassSceneNode(
    val key: Any,
    val kind: GlassKind,
    val coordinates: GlassCoordinateSource,
    val radiusDp: Float,
    val alpha: Float = 1f,
    val depth: Float = 0f,
    val intensity: Float = 1f,
    val blur: Float = 1f,
    val rim: Float = 1f,
    val highlight: Float = 1f,
    val zIndex: Float = 0f,
    val quality: RenderQuality? = null,
    val role: GlassRole? = null,
    val pressed: Boolean = false,
    val selected: Boolean = false,
    val rendererHint: GlassRendererHint = GlassRendererHint.KeepExisting,
    val secondaryCoordinates: GlassCoordinateSource? = null,
    val floorInsetDp: Float = 0f,
    val floorAlpha: Float = 1f,
    val rimAlpha: Float = 1f,
    val innerShadowAlpha: Float = 1f,
    val floorDimAlpha: Float = 0f
)

class GlassSceneRegistry {
    private val nodes = linkedMapOf<Any, GlassSceneNode>()
    private var cachedSnapshot: List<GlassSceneNode> = emptyList()
    private var dirty = true

    var version by mutableLongStateOf(0L)
        private set

    fun upsert(node: GlassSceneNode) {
        if (nodes[node.key] == node) return
        nodes[node.key] = node
        dirty = true
        version += 1L
    }

    fun remove(key: Any) {
        if (nodes.remove(key) != null) {
            dirty = true
            version += 1L
        }
    }

    fun snapshot(): List<GlassSceneNode> {
        if (dirty) {
            cachedSnapshot = nodes.values.sortedBy { it.zIndex }
            dirty = false
        }
        return cachedSnapshot
    }
}

val LocalGlassSceneRegistry = compositionLocalOf<GlassSceneRegistry?> { null }

fun glassKindForRole(role: GlassRole): GlassKind = when (role) {
    GlassRole.Nav -> GlassKind.Nav
    GlassRole.Chip -> GlassKind.Chip
    GlassRole.Shell,
    GlassRole.Card,
    GlassRole.Floating,
    GlassRole.Flex -> GlassKind.Surface
}

fun glassDepthForRole(role: GlassRole): Float = when (role) {
    GlassRole.Shell -> 0.72f
    GlassRole.Card -> 0.72f
    GlassRole.Floating -> 0.80f
    GlassRole.Flex -> 0.58f
    GlassRole.Nav -> 0.66f
    GlassRole.Chip -> 0.38f
}

fun glassZIndexForRole(role: GlassRole): Float = when (role) {
    GlassRole.Nav -> 50f
    GlassRole.Floating -> 40f
    GlassRole.Shell -> 30f
    GlassRole.Flex -> 20f
    GlassRole.Card -> 18f
    GlassRole.Chip -> 10f
}

@Composable
fun RegisterGlassSceneNode(
    key: Any,
    coordinates: GlassCoordinateSource,
    kind: GlassKind,
    radiusDp: Float,
    alpha: Float = 1f,
    depth: Float = 0f,
    intensity: Float = 1f,
    blur: Float = 1f,
    rim: Float = 1f,
    highlight: Float = 1f,
    zIndex: Float = 0f,
    quality: RenderQuality? = null,
    role: GlassRole? = null,
    pressed: Boolean = false,
    selected: Boolean = false,
    rendererHint: GlassRendererHint = GlassRendererHint.KeepExisting,
    secondaryCoordinates: GlassCoordinateSource? = null,
    floorInsetDp: Float = 0f,
    floorAlpha: Float = 1f,
    rimAlpha: Float = 1f,
    innerShadowAlpha: Float = 1f,
    floorDimAlpha: Float = 0f
) {
    val registry = LocalGlassSceneRegistry.current
    if (GlassFeatureFlags.USE_GLASS_SCENE_REGISTRY && registry != null) {
        SideEffect {
            registry.upsert(
                GlassSceneNode(
                    key = key,
                    kind = kind,
                    coordinates = coordinates,
                    radiusDp = radiusDp,
                    alpha = alpha,
                    depth = depth,
                    intensity = intensity,
                    blur = blur,
                    rim = rim,
                    highlight = highlight,
                    zIndex = zIndex,
                    quality = quality,
                    role = role,
                    pressed = pressed,
                    selected = selected,
                    rendererHint = rendererHint,
                    secondaryCoordinates = secondaryCoordinates,
                    floorInsetDp = floorInsetDp,
                    floorAlpha = floorAlpha,
                    rimAlpha = rimAlpha,
                    innerShadowAlpha = innerShadowAlpha,
                    floorDimAlpha = floorDimAlpha
                )
            )
        }
    }
    DisposableEffect(registry, key) {
        onDispose { registry?.remove(key) }
    }
}
