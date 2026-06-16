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
        if (this.sceneGroup != sceneGroup) this.sceneGroup = sceneGroup
        if (this.role != role) this.role = role
        if (this.quality != quality) this.quality = quality
        if (this.radius != radius) this.radius = radius
        if (this.glassIntensity != glassIntensity) this.glassIntensity = glassIntensity
        if (this.backdropAlpha != backdropAlpha) this.backdropAlpha = backdropAlpha
        if (this.edgeStrength != edgeStrength) this.edgeStrength = edgeStrength
        if (this.pressable != pressable) this.pressable = pressable
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
        if (this.shimmer != shimmer) this.shimmer = shimmer
        if (this.breathe != breathe) this.breathe = breathe
        if (this.pressProgress != pressProgress) this.pressProgress = pressProgress
        if (this.lensProgress != lensProgress) this.lensProgress = lensProgress
        if (this.sweepProgress != sweepProgress) this.sweepProgress = sweepProgress
        if (this.elasticity != elasticity) this.elasticity = elasticity
        if (this.pressCenter != pressCenter) this.pressCenter = pressCenter
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

internal class VisibleOrdinaryGlassItem(
    var node: OrdinaryGlassRenderNode,
    var rect: Rect,
    var transformedBounds: Rect,
    var sampleOffset: Offset
) {
    val transform = OrdinaryGlassVisualTransform()
}

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

    internal fun appendVisibleIfIntersecting(
        node: OrdinaryGlassRenderNode,
        rect: Rect,
        sampleOffset: Offset,
        viewport: Rect
    ) {
        val item = if (visibleCount < visiblePool.size) {
            visiblePool[visibleCount]
        } else {
            VisibleOrdinaryGlassItem(
                node = node,
                rect = rect,
                transformedBounds = rect,
                sampleOffset = sampleOffset
            ).also(visiblePool::add)
        }

        item.node = node
        item.rect = rect
        item.sampleOffset = sampleOffset
        updateOrdinaryGlassVisualTransform(node = node, out = item.transform)
        item.transformedBounds = ordinaryGlassTransformedBounds(
            transform = item.transform,
            rect = rect
        )
        if (item.transformedBounds.intersectionOrNull(viewport) == null) return

        visibleCount += 1
        if (node.hasActivePressOptics()) activePressCount += 1
    }

    internal fun forEachVisible(block: (VisibleOrdinaryGlassItem) -> Unit) {
        var index = 0
        while (index < visibleCount) {
            block(visiblePool[index])
            index += 1
        }
    }

    internal fun forEachVisibleIndexed(
        block: (Int, VisibleOrdinaryGlassItem) -> Unit
    ) {
        var index = 0
        while (index < visibleCount) {
            block(index, visiblePool[index])
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
 * Shell、Fallback 与 Shadow 都在这里硬排除，并且不会创建任何节点或绘制缓存。
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
    val enabled = role != GlassRole.Shell &&
        sceneGroup != GlassSceneGroup.Unassigned &&
        sceneState?.renderMode == OrdinaryGlassRenderMode.ParentDraw
    if (!enabled) return

    val node = remember(coordinates) {
        OrdinaryGlassRenderNode(coordinates = coordinates)
    }

    SideEffect {
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

    BindOrdinaryGlassRenderNode(node = node, enabled = true)
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
                        viewportSize = size,
                        backdropOrigin = backdropOrigin,
                        resolveSampleOffset = backdrop != null && backdropSpec != null
                    )

                    sceneState.forEachVisible { item ->
                        drawOrdinaryParentShadow(item = item)
                        drawOrdinaryParentBackdrop(
                            item = item,
                            backdrop = backdrop,
                            spec = backdropSpec
                        )
                        drawOrdinaryParentMaterial(item = item)
                    }

                    drawContent()

                    if (sceneState.hasVisibleActivePress()) {
                        sceneState.forEachVisibleIndexed { index, item ->
                            if (item.node.hasActivePressOptics()) {
                                withLaterVisibleBoundsExcluded(
                                    sceneState = sceneState,
                                    itemIndex = index,
                                    itemBounds = item.transformedBounds
                                ) {
                                    drawOrdinaryParentPressOptics(item = item)
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
    viewportSize: Size,
    backdropOrigin: GlassCoordinateSource,
    resolveSampleOffset: Boolean
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
        val sampleOffset = if (resolveSampleOffset) {
            node.coordinates.offsetRelativeTo(backdropOrigin)
        } else {
            Offset.Zero
        }
        sceneState.appendVisibleIfIntersecting(
            node = node,
            rect = rect,
            sampleOffset = sampleOffset,
            viewport = viewport
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
