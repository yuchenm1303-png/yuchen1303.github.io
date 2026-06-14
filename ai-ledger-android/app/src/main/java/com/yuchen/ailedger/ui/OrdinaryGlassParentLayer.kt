package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.onPlaced
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.max
import kotlin.math.min

/**
 * 普通 Compose 玻璃父级绘制分为前后两层。
 * 当前只运行影子准备流程，不输出任何像素，保证现有界面视觉完全不变。
 */
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
        val existing = items[item.key]
        if (existing === item) return
        if (item.drawOrder == 0L) {
            item.drawOrder = nextDrawOrder++
        }
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
    val rootGroup: GlassSceneGroup
) {
    val coordinates = GlassCoordinateSource()
    val registry = OrdinaryGlassItemRegistry()
}

val LocalOrdinaryGlassSceneState = staticCompositionLocalOf<OrdinaryGlassSceneState?> { null }

@Composable
fun OrdinaryGlassSceneHost(
    group: GlassSceneGroup,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val parentContext = LocalGlassSceneContext.current
    val sceneState = remember(group) { OrdinaryGlassSceneState(group) }

    DisposableEffect(sceneState) {
        onDispose { sceneState.registry.clear() }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalGlassSceneContext provides GlassSceneContext(
            group = group,
            parentGroup = parentContext.group.takeUnless { it == GlassSceneGroup.Unassigned }
        ),
        LocalOrdinaryGlassSceneState provides sceneState
    ) {
        Box(
            modifier = modifier.onPlaced { sceneState.coordinates.coordinates = it }
        ) {
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
        // 读取 registry、宿主和子项的状态，提前验证坐标、裁剪和生命周期。
        // 这一阶段故意不绘制，原 GlassPanel / PressableGlass 仍保持完整视觉输出。
        prepareVisibleOrdinaryGlassItems(
            sceneState = sceneState,
            phase = phase,
            viewportSize = size
        )
    }
}

private data class PreparedOrdinaryGlassItem(
    val node: OrdinaryGlassRenderNode,
    val rect: Rect,
    val visibleRect: Rect,
    val phase: OrdinaryGlassParentPhase
)

private fun prepareVisibleOrdinaryGlassItems(
    sceneState: OrdinaryGlassSceneState,
    phase: OrdinaryGlassParentPhase,
    viewportSize: Size
): List<PreparedOrdinaryGlassItem> {
    sceneState.registry.version
    sceneState.coordinates.placementVersion

    if (viewportSize.width <= 1f || viewportSize.height <= 1f) return emptyList()
    if (!sceneState.coordinates.isAttached()) return emptyList()

    val hostRoot = sceneState.coordinates.rootOffset()
    val viewport = Rect(0f, 0f, viewportSize.width, viewportSize.height)
    val prepared = ArrayList<PreparedOrdinaryGlassItem>()

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
        val visibleRect = rect.intersectionOrNull(viewport) ?: return@forEach

        // 主动读取阶段相关参数，让后续切换父级输出时无需改变注册协议。
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

        prepared += PreparedOrdinaryGlassItem(
            node = node,
            rect = rect,
            visibleRect = visibleRect,
            phase = phase
        )
    }

    return prepared
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
