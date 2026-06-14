package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onPlaced
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.max
import kotlin.math.min

/**
 * 普通 GlassPanel / PressableGlass 的页面级父绘制系统。
 *
 * Shadow 只完成注册、坐标换算、可见性裁剪和状态读取，不输出像素；
 * ParentDraw 才允许父级 Underlay / Overlay 接管实际材质绘制。
 */
enum class OrdinaryGlassRenderMode {
    Shadow,
    ParentDraw
}

enum class OrdinaryGlassParentPhase {
    Underlay,
    Overlay
}

@Stable
class OrdinaryGlassRenderNode(
    val key: Any = Any(),
    val coordinates: GlassCoordinateSource = GlassCoordinateSource()
) {
    var sceneGroup by mutableStateOf(GlassSceneGroup.Unassigned)
    var role by mutableStateOf(GlassRole.Card)
    var quality by mutableStateOf(RenderQuality.Balanced)
    var radius by mutableIntStateOf(0)
    var glassIntensity by mutableFloatStateOf(1f)
    var backdropAlpha by mutableFloatStateOf(1f)
    var edgeStrength by mutableFloatStateOf(0f)
    var shimmer by mutableFloatStateOf(0f)
    var breathe by mutableFloatStateOf(0f)
    var pressProgress by mutableFloatStateOf(0f)
    var lensProgress by mutableFloatStateOf(0f)
    var sweepProgress by mutableFloatStateOf(0f)
    var elasticity by mutableFloatStateOf(0f)
    var pressCenter by mutableStateOf(Offset(0.5f, 0.5f))
    var pressable by mutableStateOf(false)
    var drawOrder by mutableLongStateOf(0L)
        internal set

    fun updateStatic(
        sceneGroup: GlassSceneGroup,
        role: GlassRole,
        quality: RenderQuality,
        radius: Int,
        glassIntensity: Float,
        backdropAlpha: Float,
        edgeStrength: Float,
        pressable: Boolean
    ) {
        this.sceneGroup = sceneGroup
        this.role = role
        this.quality = quality
        this.radius = radius
        this.glassIntensity = glassIntensity
        this.backdropAlpha = backdropAlpha
        this.edgeStrength = edgeStrength
        this.pressable = pressable
    }

    fun updateMotion(
        shimmer: Float,
        breathe: Float,
        pressProgress: Float,
        lensProgress: Float,
        sweepProgress: Float,
        elasticity: Float,
        pressCenter: Offset
    ) {
        this.shimmer = shimmer
        this.breathe = breathe
        this.pressProgress = pressProgress
        this.lensProgress = lensProgress
        this.sweepProgress = sweepProgress
        this.elasticity = elasticity
        this.pressCenter = pressCenter
    }
}

@Stable
class OrdinaryGlassItemRegistry {
    private val items = linkedMapOf<Any, OrdinaryGlassRenderNode>()
    private var cachedSnapshot: List<OrdinaryGlassRenderNode> = emptyList()
    private var nextDrawOrder = 1L

    var version by mutableLongStateOf(0L)
        private set

    fun register(item: OrdinaryGlassRenderNode) {
        if (items[item.key] === item) return
        if (item.drawOrder == 0L) item.drawOrder = nextDrawOrder++
        items[item.key] = item
        invalidate()
    }

    fun unregister(key: Any) {
        if (items.remove(key) != null) invalidate()
    }

    fun snapshot(): List<OrdinaryGlassRenderNode> {
        version
        return cachedSnapshot
    }

    fun clear() {
        if (items.isEmpty()) return
        items.clear()
        invalidate()
    }

    private fun invalidate() {
        cachedSnapshot = items.values.sortedBy { it.drawOrder }
        version += 1L
    }
}

@Stable
class OrdinaryGlassSceneState(
    val rootGroup: GlassSceneGroup,
    val renderMode: OrdinaryGlassRenderMode
) {
    val coordinates = GlassCoordinateSource()
    val registry = OrdinaryGlassItemRegistry()
}

val LocalOrdinaryGlassSceneState = staticCompositionLocalOf<OrdinaryGlassSceneState?> { null }
val LocalOrdinaryGlassRenderMode = staticCompositionLocalOf { OrdinaryGlassRenderMode.Shadow }

@Composable
internal fun BindOrdinaryGlassRenderNode(
    node: OrdinaryGlassRenderNode,
    enabled: Boolean
) {
    val sceneState = LocalOrdinaryGlassSceneState.current

    DisposableEffect(sceneState, node, enabled) {
        if (enabled && sceneState != null) sceneState.registry.register(node)
        onDispose {
            if (enabled && sceneState != null) sceneState.registry.unregister(node.key)
        }
    }
}

/**
 * GlassPanel / PressableGlass 的唯一普通玻璃上报入口。
 * Shell 在这里被硬排除，不会进入普通 Compose registry。
 */
@Composable
internal fun ReportOrdinaryGlassNode(
    coordinates: GlassCoordinateSource,
    role: GlassRole,
    quality: RenderQuality,
    radius: Int,
    glassIntensity: Float,
    backdropAlpha: Float,
    edgeStrength: Float,
    pressable: Boolean,
    shimmer: Float,
    breathe: Float,
    pressProgress: Float,
    lensProgress: Float,
    sweepProgress: Float,
    elasticity: Float,
    pressCenter: Offset
) {
    val sceneGroup = LocalGlassSceneGroup
    val sceneState = LocalOrdinaryGlassSceneState.current
    val node = remember(coordinates) {
        OrdinaryGlassRenderNode(coordinates = coordinates)
    }
    val enabled = role != GlassRole.Shell &&
        sceneGroup != GlassSceneGroup.Unassigned &&
        sceneState != null

    SideEffect {
        if (enabled) {
            node.updateStatic(
                sceneGroup = sceneGroup,
                role = role,
                quality = quality,
                radius = radius,
                glassIntensity = glassIntensity,
                backdropAlpha = backdropAlpha,
                edgeStrength = edgeStrength,
                pressable = pressable
            )
            node.updateMotion(
                shimmer = shimmer,
                breathe = breathe,
                pressProgress = pressProgress,
                lensProgress = lensProgress,
                sweepProgress = sweepProgress,
                elasticity = elasticity,
                pressCenter = pressCenter
            )
        }
    }

    BindOrdinaryGlassRenderNode(node = node, enabled = enabled)
}

@Composable
fun OrdinaryGlassSceneHost(
    group: GlassSceneGroup,
    modifier: Modifier = Modifier,
    renderMode: OrdinaryGlassRenderMode = OrdinaryGlassRenderMode.Shadow,
    content: @Composable () -> Unit
) {
    val parentContext = LocalGlassSceneContext.current
    val sceneState = remember(group, renderMode) {
        OrdinaryGlassSceneState(rootGroup = group, renderMode = renderMode)
    }

    DisposableEffect(sceneState) {
        onDispose { sceneState.registry.clear() }
    }

    CompositionLocalProvider(
        LocalGlassSceneContext provides GlassSceneContext(
            group = group,
            parentGroup = parentContext.group.takeUnless { it == GlassSceneGroup.Unassigned }
        ),
        LocalOrdinaryGlassSceneState provides sceneState,
        LocalOrdinaryGlassRenderMode provides renderMode
    ) {
        Box(modifier = modifier.onPlaced { sceneState.coordinates.coordinates = it }) {
            OrdinaryGlassParentLayer(
                sceneState = sceneState,
                phase = OrdinaryGlassParentPhase.Underlay,
                modifier = Modifier.matchParentSize()
            )
            content()
            OrdinaryGlassParentLayer(
                sceneState = sceneState,
                phase = OrdinaryGlassParentPhase.Overlay,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun OrdinaryGlassParentLayer(
    sceneState: OrdinaryGlassSceneState,
    phase: OrdinaryGlassParentPhase,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {
        observeVisibleOrdinaryGlassItems(
            sceneState = sceneState,
            phase = phase,
            viewportSize = size
        )
    }
}

/**
 * Shadow 阶段只遍历，不构造每帧临时列表；ParentDraw 才调用父级材质函数。
 */
private fun DrawScope.observeVisibleOrdinaryGlassItems(
    sceneState: OrdinaryGlassSceneState,
    phase: OrdinaryGlassParentPhase,
    viewportSize: Size
) {
    sceneState.registry.version
    sceneState.coordinates.placementVersion

    if (viewportSize.width <= 1f || viewportSize.height <= 1f) return
    if (!sceneState.coordinates.isAttached()) return

    val hostRoot = sceneState.coordinates.rootOffset()
    val viewport = Rect(0f, 0f, viewportSize.width, viewportSize.height)

    sceneState.registry.snapshot().forEach { node ->
        if (node.role == GlassRole.Shell) return@forEach
        if (!node.coordinates.isAttached()) return@forEach

        val itemSize = node.coordinates.itemSize()
        if (itemSize.width <= 0 || itemSize.height <= 0) return@forEach

        val localTopLeft = node.coordinates.rootOffset() - hostRoot
        val rect = Rect(
            offset = localTopLeft,
            size = Size(itemSize.width.toFloat(), itemSize.height.toFloat())
        )
        rect.intersectionOrNull(viewport) ?: return@forEach

        if (sceneState.renderMode == OrdinaryGlassRenderMode.ParentDraw) {
            when (phase) {
                OrdinaryGlassParentPhase.Underlay -> {
                    drawOrdinaryComposeGlassMaterial(node = node, rect = rect)
                }
                OrdinaryGlassParentPhase.Overlay -> {
                    drawOrdinaryComposeGlassPressOptics(node = node, rect = rect)
                }
            }
        } else {
            when (phase) {
                OrdinaryGlassParentPhase.Underlay -> {
                    node.quality
                    node.radius
                    node.glassIntensity
                    node.backdropAlpha
                    node.edgeStrength
                }
                OrdinaryGlassParentPhase.Overlay -> {
                    node.shimmer
                    node.breathe
                    node.pressProgress
                    node.lensProgress
                    node.sweepProgress
                    node.elasticity
                    node.pressCenter
                    node.pressable
                }
            }
        }
    }
}

private fun Rect.intersectionOrNull(other: Rect): Rect? {
    val clippedLeft = max(left, other.left)
    val clippedTop = max(top, other.top)
    val clippedRight = min(right, other.right)
    val clippedBottom = min(bottom, other.bottom)
    return if (clippedRight > clippedLeft && clippedBottom > clippedTop) {
        Rect(clippedLeft, clippedTop, clippedRight, clippedBottom)
    } else {
        null
    }
}
