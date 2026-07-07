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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality

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
    internal var foldoutClipRegistry: GlassFoldoutClipRegistry? = null
    var drawOrder by mutableLongStateOf(0L)
        internal set

    internal val parentDrawCache = OrdinaryGlassParentDrawCache()

    internal fun updateStatic(
        sceneGroup: GlassSceneGroup,
        role: GlassRole,
        quality: RenderQuality,
        radius: Int,
        glassIntensity: Float,
        backdropAlpha: Float,
        edgeStrength: Float,
        pressable: Boolean,
        foldoutClipRegistry: GlassFoldoutClipRegistry?
    ) {
        if (this.sceneGroup != sceneGroup) this.sceneGroup = sceneGroup
        if (this.role != role) this.role = role
        if (this.quality != quality) this.quality = quality
        if (this.radius != radius) this.radius = radius
        if (this.glassIntensity != glassIntensity) this.glassIntensity = glassIntensity
        if (this.backdropAlpha != backdropAlpha) this.backdropAlpha = backdropAlpha
        if (this.edgeStrength != edgeStrength) this.edgeStrength = edgeStrength
        if (this.pressable != pressable) this.pressable = pressable
        if (this.foldoutClipRegistry !== foldoutClipRegistry) {
            this.foldoutClipRegistry = foldoutClipRegistry
        }
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
    var sampleOffset: Offset,
    var foldoutClipRect: Rect?
) {
    val motion = OrdinaryGlassMotionSnapshot()
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
        viewport: Rect,
        foldoutClip: Rect?,
        backdropOrigin: BackdropCoordinateSource?,
        resolveSampleOffset: Boolean
    ) {
        val item = if (visibleCount < visiblePool.size) {
            visiblePool[visibleCount]
        } else {
            val created = VisibleOrdinaryGlassItem(
                node = node,
                rect = rect,
                transformedBounds = rect,
                sampleOffset = Offset.Zero,
                foldoutClipRect = null
            )
            visiblePool.add(created)
            created
        }

        item.node = node
        item.rect = rect
        item.foldoutClipRect = foldoutClip
        updateOrdinaryGlassMotionSnapshot(node = node, out = item.motion)
        updateOrdinaryGlassVisualTransform(item = item, out = item.transform)
        val transformedBounds = ordinaryGlassTransformedBounds(
            transform = item.transform,
            rect = rect
        )
        val visibilityClip = foldoutClip ?: viewport
        item.transformedBounds = transformedBounds.intersectionOrNull(visibilityClip) ?: return

        item.sampleOffset = if (resolveSampleOffset) {
            node.coordinates.offsetRelativeTo(backdropOrigin)
        } else {
            Offset.Zero
        }
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
    val foldoutClipRegistry = LocalGlassFoldoutClipRegistry.current
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
            pressable = pressable,
            foldoutClipRegistry = foldoutClipRegistry
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
                    backdropTicker?.frameNanos
                    collectVisibleOrdinaryGlassItems(
                        sceneState = sceneState,
                        viewportSize = size,
                        backdropOrigin = backdropOrigin,
                        resolveSampleOffset = backdrop != null && backdropSpec != null
                    )

                    sceneState.forEachVisible { item ->
                        withOrdinaryGlassFoldoutClip(item) {
                            drawOrdinaryParentShadow(item = item)
                            drawOrdinaryParentBackdrop(
                                item = item,
                                backdrop = backdrop,
                                spec = backdropSpec
                            )
                            drawOrdinaryParentMaterial(item = item)
                        }
                    }

                    drawContent()

                    if (sceneState.hasVisibleActivePress()) {
                        sceneState.forEachVisibleIndexed { index, item ->
                            if (item.node.hasActivePressOptics()) {
                                withOrdinaryGlassFoldoutClip(item) {
                                    withLaterVisibleBoundsExcluded(
                                        sceneState = sceneState,
                                        itemIndex = index,
                                        itemBounds = item.transformedBounds
                                    ) {
                                        drawOrdinaryParentPressureFieldOptics(item = item)
                                    }
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
    backdropOrigin: BackdropCoordinateSource?,
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

        val foldoutClip = when (
            val result = node.foldoutClipRegistry?.resolveFor(node.coordinates.coordinates)
                ?: GlassFoldoutClipResult.Unbounded
        ) {
            GlassFoldoutClipResult.Unbounded -> null
            GlassFoldoutClipResult.Hidden -> continue
            is GlassFoldoutClipResult.Visible -> {
                val local = result.rectInRoot.translate(-hostRoot)
                local.intersectionOrNull(viewport) ?: continue
            }
        }

        val localTopLeft = node.coordinates.rootOffset() - hostRoot
        val rect = Rect(
            offset = localTopLeft,
            size = Size(itemSize.width.toFloat(), itemSize.height.toFloat())
        )
        sceneState.appendVisibleIfIntersecting(
            node = node,
            rect = rect,
            viewport = viewport,
            foldoutClip = foldoutClip,
            backdropOrigin = backdropOrigin,
            resolveSampleOffset = resolveSampleOffset
        )
    }
}

private inline fun DrawScope.withOrdinaryGlassFoldoutClip(
    item: VisibleOrdinaryGlassItem,
    block: DrawScope.() -> Unit
) {
    val clip = item.foldoutClipRect
    if (clip == null) {
        block()
    } else {
        clipRect(
            left = clip.left,
            top = clip.top,
            right = clip.right,
            bottom = clip.bottom
        ) {
            block()
        }
    }
}

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

private fun composeMotionPower(value: Float, uiMax: Float, effectiveMax: Float): Float {
    val clean = value.coerceAtLeast(0f)
    if (clean <= 1f) return clean
    val span = (uiMax - 1f).coerceAtLeast(0.001f)
    val t = ((clean - 1f) / span).coerceIn(0f, 1f)
    return 1f + t * (effectiveMax - 1f)
}

private fun ordinaryParentPressureSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun DrawScope.drawOrdinaryParentPressureFieldOptics(item: VisibleOrdinaryGlassItem) {
    val node = item.node
    if (!node.pressable || node.role == GlassRole.Shell) return

    val rect = item.transformedBounds
    val w = rect.width.coerceAtLeast(1f)
    val h = rect.height.coerceAtLeast(1f)
    if (w <= 1f || h <= 1f) return

    val positivePress = node.pressProgress.coerceAtLeast(0f)
    val lens = node.lensProgress.coerceAtLeast(0f)
    val sweep = node.sweepProgress.coerceAtLeast(0f)
    val rawActive = maxOf(positivePress * 0.50f, lens * 0.56f, sweep * 0.42f)
    val active = ordinaryParentPressureSmoothStep((rawActive / 1.18f).coerceIn(0f, 1f))
    if (active <= 0.001f) return

    val maxSide = maxOf(w, h)
    val minSide = minOf(w, h).coerceAtLeast(1f)
    val centerNorm = Offset(
        node.pressCenter.x.coerceIn(0f, 1f),
        node.pressCenter.y.coerceIn(0f, 1f)
    )
    val tapCenter = Offset(
        centerNorm.x * w,
        centerNorm.y * h
    )
    val visualCenter = Offset(w * 0.50f, h * 0.48f)

    val phaseFromSweep = ordinaryParentPressureSmoothStep((sweep / 3.10f).coerceIn(0f, 1f))
    val phaseFromLens = ordinaryParentPressureSmoothStep((lens / 3.35f).coerceIn(0f, 1f))
    val phase = maxOf(phaseFromSweep, phaseFromLens * 0.62f, active * 0.38f).coerceIn(0f, 1f)
    val drift = (0.12f + phase * 0.24f + node.elasticity.coerceIn(0f, 1f) * 0.030f)
        .coerceIn(0.08f, 0.40f)
    val fieldCenter = Offset(
        tapCenter.x + (visualCenter.x - tapCenter.x) * drift,
        tapCenter.y + (visualCenter.y - tapCenter.y) * drift * 0.82f
    )

    val motion = ComposeGlassLabState.motionStyle.normalized()
    val master = composeMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f)
    val touchLight = composeMotionPower(value = motion.touchLight, uiMax = 1.8f, effectiveMax = 16f) * master
    val sweepGain = composeMotionPower(value = motion.sweep, uiMax = 1.5f, effectiveMax = 16f) * master
    val afterglow = composeMotionPower(value = motion.afterglow, uiMax = 1.5f, effectiveMax = 12f) * master
    val capsule = ComposeGlassLabState.capsuleTuning.normalized()
    val capsuleLight = (1f + capsule.tapPx * 4.8f + capsule.sticky * 7.2f + capsule.basePx * 3.6f)
        .coerceIn(0.92f, 1.74f)

    val lightUnit = (touchLight / 128f).coerceIn(0f, 1f)
    val sweepUnit = (sweepGain / 128f).coerceIn(0f, 1f)
    val afterUnit = (afterglow / 96f).coerceIn(0f, 1f)
    val elasticityBoost = node.elasticity.coerceIn(0.08f, 1f)
    val phaseTail = ordinaryParentPressureSmoothStep(((phase - 0.18f) / 0.82f).coerceIn(0f, 1f))

    val fieldEnergy = (active * (0.96f + lightUnit * 0.30f + afterUnit * 0.18f) * capsuleLight * elasticityBoost)
        .coerceIn(0f, 1.38f)
    val waveEnergy = (active * (0.58f + sweepUnit * 0.40f + afterUnit * 0.20f) * capsuleLight)
        .coerceIn(0f, 1.28f)
    val fieldRadius = (maxSide * (0.34f + phase * 0.70f + fieldEnergy * 0.12f))
        .coerceAtLeast(minSide * 0.74f)

    val bodyAlpha = (0.145f * fieldEnergy).coerceIn(0f, 0.28f)
    val coreAlpha = (0.072f * fieldEnergy).coerceIn(0f, 0.14f)
    val waveAlpha = (0.125f * waveEnergy * (0.60f + phaseTail * 0.40f)).coerceIn(0f, 0.24f)
    val tailAlpha = (0.058f * fieldEnergy * (0.70f + afterUnit * 0.30f)).coerceIn(0f, 0.13f)
    val radiusPx = node.radius.dp.toPx()
    val cornerRadius = CornerRadius(radiusPx, radiusPx)

    translate(left = rect.left, top = rect.top) {
        drawRoundRect(
            brush = Brush.radialGradient(
                0.00f to Color.White.copy(alpha = coreAlpha),
                0.22f to Color(0xFFF2FAFF).copy(alpha = bodyAlpha * 0.74f),
                0.48f to Color.White.copy(alpha = waveAlpha),
                0.68f to Color(0xFFDFFFFF).copy(alpha = tailAlpha),
                1.00f to Color.Transparent,
                center = fieldCenter,
                radius = fieldRadius
            ),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = cornerRadius,
            blendMode = BlendMode.Screen
        )

        val rimInset = (minSide * 0.006f).coerceIn(0.40f, 1.20f)
        val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))
        val rimRadius = (radiusPx - rimInset).coerceAtLeast(0f)
        val rimCorner = CornerRadius(rimRadius, rimRadius)
        val edgeStroke = (0.60f + minSide * 0.010f * active + 0.22f * sweepUnit).coerceIn(0.55f, 3.20f)
        val edgeEnergy = (fieldEnergy * 0.88f + waveEnergy * 0.58f).coerceIn(0f, 1.34f)

        drawRoundRect(
            brush = Brush.radialGradient(
                0.00f to Color.White.copy(alpha = (0.075f * edgeEnergy).coerceIn(0f, 0.16f)),
                0.34f to Color(0xFFF8FFFF).copy(alpha = (0.060f * edgeEnergy).coerceIn(0f, 0.13f)),
                0.58f to Color.White.copy(alpha = (0.155f * edgeEnergy).coerceIn(0f, 0.28f)),
                0.80f to Color(0xFFCFFFFA).copy(alpha = (0.062f * edgeEnergy).coerceIn(0f, 0.14f)),
                1.00f to Color.Transparent,
                center = fieldCenter,
                radius = fieldRadius * 1.08f
            ),
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = rimCorner,
            style = Stroke(edgeStroke),
            blendMode = BlendMode.Plus
        )
    }
}
