package com.yuchen.ailedger.ui

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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onPlaced
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.max
import kotlin.math.min

/**
 * 普通 GlassPanel / PressableGlass 的页面级父绘制系统。
 *
 * ParentDraw 只保留一个 Host 绘制节点：
 * 1. 一次计算所有可见普通玻璃；
 * 2. 在业务内容下绘制阴影、背景采样和完整静态材质；
 * 3. 绘制页面业务内容；
 * 4. 仅为活跃按压节点绘制内容上方光学层。
 *
 * 按压 Overlay 会排除后序玻璃的可见区域，保持 Compose 子级原有 z-order。
 * Shadow 不再建立 registry 或空绘制层，直接回退子级绘制。
 */
enum class OrdinaryGlassRenderMode {
    Shadow,
    ParentDraw
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

    internal val parentDrawCache = OrdinaryGlassParentDrawCache()

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

    internal fun hasActivePressOptics(): Boolean {
        if (!pressable || role == GlassRole.Shell) return false
        return pressProgress > 0.001f || pressProgress < -0.001f ||
            lensProgress > 0.001f || sweepProgress > 0.001f
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

private class VisibleOrdinaryGlassItem(
    var node: OrdinaryGlassRenderNode,
    var rect: Rect,
    var transformedBounds: Rect
)

@Stable
class OrdinaryGlassSceneState(
    val rootGroup: GlassSceneGroup,
    val renderMode: OrdinaryGlassRenderMode
) {
    val coordinates = GlassCoordinateSource()
    val registry = OrdinaryGlassItemRegistry()

    private val visiblePool = ArrayList<VisibleOrdinaryGlassItem>()
    private var visibleCount = 0
    private var activePressCount = 0

    internal fun beginVisiblePass() {
        visibleCount = 0
        activePressCount = 0
    }

    internal fun appendVisible(
        node: OrdinaryGlassRenderNode,
        rect: Rect,
        transformedBounds: Rect
    ) {
        if (visibleCount < visiblePool.size) {
            val item = visiblePool[visibleCount]
            item.node = node
            item.rect = rect
            item.transformedBounds = transformedBounds
        } else {
            visiblePool.add(VisibleOrdinaryGlassItem(node, rect, transformedBounds))
        }
        visibleCount += 1
        if (node.hasActivePressOptics()) activePressCount += 1
    }

    internal fun forEachVisible(block: (OrdinaryGlassRenderNode, Rect) -> Unit) {
        var index = 0
        while (index < visibleCount) {
            val item = visiblePool[index]
            block(item.node, item.rect)
            index += 1
        }
    }

    internal fun forEachVisibleIndexed(
        block: (Int, OrdinaryGlassRenderNode, Rect, Rect) -> Unit
    ) {
        var index = 0
        while (index < visibleCount) {
            val item = visiblePool[index]
            block(index, item.node, item.rect, item.transformedBounds)
            index += 1
        }
    }

    internal fun hasVisibleActivePress(): Boolean = activePressCount > 0

    internal fun visibleItemCount(): Int = visibleCount

    internal fun transformedBoundsAt(index: Int): Rect =
        visiblePool[index].transformedBounds
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
 * Shell、Fallback 与 Shadow 都在这里硬排除。
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
        sceneState?.renderMode == OrdinaryGlassRenderMode.ParentDraw

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
    val context = GlassSceneContext(
        group = group,
        parentGroup = parentContext.group.takeUnless { it == GlassSceneGroup.Unassigned }
    )

    if (renderMode != OrdinaryGlassRenderMode.ParentDraw) {
        CompositionLocalProvider(
            LocalGlassSceneContext provides context,
            LocalOrdinaryGlassSceneState provides null,
            LocalOrdinaryGlassRenderMode provides OrdinaryGlassRenderMode.Shadow
        ) {
            Box(modifier = modifier) { content() }
        }
        return
    }

    val sceneState = remember(group) {
        OrdinaryGlassSceneState(
            rootGroup = group,
            renderMode = OrdinaryGlassRenderMode.ParentDraw
        )
    }
    val backdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val backdropTicker = LocalBackdropFrameTicker.current
    val backdropSpec = LocalGlassBackdrop.current

    DisposableEffect(sceneState) {
        onDispose { sceneState.registry.clear() }
    }

    CompositionLocalProvider(
        LocalGlassSceneContext provides context,
        LocalOrdinaryGlassSceneState provides sceneState,
        LocalOrdinaryGlassRenderMode provides OrdinaryGlassRenderMode.ParentDraw
    ) {
        Box(
            modifier = modifier
                .onPlaced { sceneState.coordinates.coordinates = it }
                .drawWithContent {
                    // 单一 Host 订阅背景刷新；不再存在第二个空转 Overlay Canvas。
                    backdropTicker?.frameNanos
                    collectVisibleOrdinaryGlassItems(
                        sceneState = sceneState,
                        viewportSize = size
                    )

                    sceneState.forEachVisible { node, rect ->
                        drawOrdinaryParentShadow(node = node, rect = rect)
                        drawOrdinaryParentBackdrop(
                            node = node,
                            rect = rect,
                            backdrop = backdrop,
                            sampleOffset = node.coordinates.offsetRelativeTo(backdropOrigin),
                            spec = backdropSpec
                        )
                        drawOrdinaryParentMaterial(node = node, rect = rect)
                    }

                    drawContent()

                    if (sceneState.hasVisibleActivePress()) {
                        sceneState.forEachVisibleIndexed { index, node, rect, bounds ->
                            if (node.hasActivePressOptics()) {
                                withLaterVisibleBoundsExcluded(
                                    sceneState = sceneState,
                                    itemIndex = index,
                                    itemBounds = bounds
                                ) {
                                    drawOrdinaryParentPressOptics(node = node, rect = rect)
                                }
                            }
                        }
                    }
                }
        ) {
            content()
        }
    }
}

private fun DrawScope.collectVisibleOrdinaryGlassItems(
    sceneState: OrdinaryGlassSceneState,
    viewportSize: Size
) {
    sceneState.registry.version
    sceneState.coordinates.placementVersion
    sceneState.beginVisiblePass()

    if (viewportSize.width <= 1f || viewportSize.height <= 1f) return
    if (!sceneState.coordinates.isAttached()) return

    val hostRoot = sceneState.coordinates.rootOffset()
    val viewport = Rect(0f, 0f, viewportSize.width, viewportSize.height)
    val nodes = sceneState.registry.snapshot()
    var index = 0

    while (index < nodes.size) {
        val node = nodes[index]
        index += 1
        if (node.role == GlassRole.Shell || !node.coordinates.isAttached()) continue

        val itemSize = node.coordinates.itemSize()
        if (itemSize.width <= 0 || itemSize.height <= 0) continue

        val localTopLeft = node.coordinates.rootOffset() - hostRoot
        val rect = Rect(
            offset = localTopLeft,
            size = Size(itemSize.width.toFloat(), itemSize.height.toFloat())
        )
        val transformedBounds = ordinaryGlassTransformedBounds(node = node, rect = rect)
        if (transformedBounds.intersectionOrNull(viewport) == null) continue
        sceneState.appendVisible(
            node = node,
            rect = rect,
            transformedBounds = transformedBounds
        )
    }
}

/**
 * 父级按压 Overlay 在页面内容之后绘制，但不能越过后序玻璃节点。
 * 只对真实相交的后序区域追加 Difference clip，未重叠列表不增加裁剪层。
 */
private fun DrawScope.withLaterVisibleBoundsExcluded(
    sceneState: OrdinaryGlassSceneState,
    itemIndex: Int,
    itemBounds: Rect,
    block: DrawScope.() -> Unit
) {
    fun DrawScope.drawFrom(nextIndex: Int) {
        var index = nextIndex
        while (index < sceneState.visibleItemCount()) {
            val laterBounds = sceneState.transformedBoundsAt(index)
            if (itemBounds.intersectionOrNull(laterBounds) != null) {
                clipRect(
                    left = laterBounds.left,
                    top = laterBounds.top,
                    right = laterBounds.right,
                    bottom = laterBounds.bottom,
                    clipOp = ClipOp.Difference
                ) {
                    drawFrom(index + 1)
                }
                return
            }
            index += 1
        }
        block()
    }

    drawFrom(itemIndex + 1)
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
