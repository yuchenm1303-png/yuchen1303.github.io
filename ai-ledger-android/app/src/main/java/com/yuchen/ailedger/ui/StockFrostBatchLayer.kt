package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 股票页专用雾面玻璃批绘制系统。
 *
 * 这里只处理 FrostInfoGlassPanel 的背景裁切和纯色雾面罩，不调用 OpenGL，
 * 不注册到普通玻璃或 OpenGL registry，也不会触发任何 OpenGL geometry sync。
 * 所有可见小卡共享父级背景采样，子项只上报几何与材质参数。
 */
@Stable
private class StockFrostBatchNode(
    val key: Any = Any()
) {
    var rootX: Float = 0f
        private set
    var rootY: Float = 0f
        private set
    var width: Float = 0f
        private set
    var height: Float = 0f
        private set

    var radiusDp: Float = 16f
        private set
    var backdropAlpha: Float = 1f
        private set
    var frostAlpha: Float = 0f
        private set
    var dimAlpha: Float = 0f
        private set

    fun updateGeometry(x: Float, y: Float, width: Float, height: Float): Boolean {
        if (rootX == x && rootY == y && this.width == width && this.height == height) return false
        rootX = x
        rootY = y
        this.width = width
        this.height = height
        return true
    }

    fun updateStyle(
        radiusDp: Float,
        backdropAlpha: Float,
        frostAlpha: Float,
        dimAlpha: Float
    ): Boolean {
        val safeRadius = radiusDp.coerceAtLeast(0f)
        val safeBackdrop = backdropAlpha.coerceIn(0f, 1f)
        val safeFrost = frostAlpha.coerceIn(0f, 0.85f)
        val safeDim = dimAlpha.coerceIn(0f, 0.65f)
        if (
            this.radiusDp == safeRadius &&
            this.backdropAlpha == safeBackdrop &&
            this.frostAlpha == safeFrost &&
            this.dimAlpha == safeDim
        ) {
            return false
        }
        this.radiusDp = safeRadius
        this.backdropAlpha = safeBackdrop
        this.frostAlpha = safeFrost
        this.dimAlpha = safeDim
        return true
    }
}

@Stable
private class StockFrostBatchRegistry {
    private val nodes = LinkedHashMap<Any, StockFrostBatchNode>()
    private var cachedSnapshot: List<StockFrostBatchNode> = emptyList()

    var version by mutableLongStateOf(0L)
        private set

    fun register(node: StockFrostBatchNode) {
        if (nodes[node.key] === node) return
        nodes[node.key] = node
        cachedSnapshot = nodes.values.toList()
        invalidate()
    }

    fun unregister(key: Any) {
        if (nodes.remove(key) == null) return
        cachedSnapshot = nodes.values.toList()
        invalidate()
    }

    fun snapshot(): List<StockFrostBatchNode> {
        version
        return cachedSnapshot
    }

    fun invalidate() {
        version += 1L
    }

    fun clear() {
        if (nodes.isEmpty()) return
        nodes.clear()
        cachedSnapshot = emptyList()
        invalidate()
    }
}

private class ResolvedStockFrostItem {
    var rect: Rect = Rect.Zero
    var radiusPx: Float = 0f
}

private class StockFrostBatchDrawCache {
    private val resolvedPool = ArrayList<ResolvedStockFrostItem>()
    private val backdropPaths = LinkedHashMap<Int, Path>()
    private val frostPaths = LinkedHashMap<Int, Path>()
    private val dimPaths = LinkedHashMap<Int, Path>()
    private var resolvedCount = 0

    fun begin() {
        resolvedCount = 0
        backdropPaths.values.forEach(Path::reset)
        frostPaths.values.forEach(Path::reset)
        dimPaths.values.forEach(Path::reset)
    }

    fun append(
        rect: Rect,
        radiusPx: Float,
        backdropAlpha: Float,
        frostAlpha: Float,
        dimAlpha: Float
    ) {
        val item = if (resolvedCount < resolvedPool.size) {
            resolvedPool[resolvedCount]
        } else {
            ResolvedStockFrostItem().also(resolvedPool::add)
        }
        item.rect = rect
        item.radiusPx = radiusPx
        resolvedCount += 1

        val roundRect = RoundRect(
            rect = rect,
            cornerRadius = CornerRadius(radiusPx, radiusPx)
        )
        appendPath(backdropPaths, backdropAlpha, roundRect)
        if (frostAlpha > 0f) appendPath(frostPaths, frostAlpha, roundRect)
        if (dimAlpha > 0f) appendPath(dimPaths, dimAlpha, roundRect)
    }

    private fun appendPath(paths: MutableMap<Int, Path>, alpha: Float, roundRect: RoundRect) {
        paths.getOrPut(alpha.toBits()) { Path() }.addRoundRect(roundRect)
    }

    fun forEachResolved(block: (ResolvedStockFrostItem) -> Unit) {
        var index = 0
        while (index < resolvedCount) {
            block(resolvedPool[index])
            index += 1
        }
    }

    fun forEachBackdropPath(block: (Float, Path) -> Unit) =
        forEachPath(backdropPaths, block)

    fun forEachFrostPath(block: (Float, Path) -> Unit) =
        forEachPath(frostPaths, block)

    fun forEachDimPath(block: (Float, Path) -> Unit) =
        forEachPath(dimPaths, block)

    private fun forEachPath(paths: Map<Int, Path>, block: (Float, Path) -> Unit) {
        paths.forEach { (alphaBits, path) ->
            if (!path.isEmpty) block(Float.fromBits(alphaBits), path)
        }
    }
}

private class StockFrostHostGeometry {
    var root: Offset = Offset.Zero
}

private val LocalStockFrostBatchRegistry =
    staticCompositionLocalOf<StockFrostBatchRegistry?> { null }

@Composable
internal fun StockFrostBatchHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val registry = remember { StockFrostBatchRegistry() }
    val drawCache = remember { StockFrostBatchDrawCache() }
    val hostGeometry = remember { StockFrostHostGeometry() }
    val backdropCoordinates = remember { GlassCoordinateSource() }
    val backdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current

    DisposableEffect(registry) {
        onDispose { registry.clear() }
    }

    CompositionLocalProvider(LocalStockFrostBatchRegistry provides registry) {
        Box(
            modifier = modifier
                .onGloballyPositioned { coordinates ->
                    backdropCoordinates.coordinates = coordinates
                    val root = coordinates.positionInRoot()
                    if (hostGeometry.root != root) {
                        hostGeometry.root = root
                        registry.invalidate()
                    }
                }
                .drawWithContent {
                    frameTicker?.frameNanos
                    registry.version
                    drawCache.begin()

                    val root = hostGeometry.root
                    val viewportWidth = size.width
                    val viewportHeight = size.height
                    registry.snapshot().forEach { node ->
                        if (node.width <= 0f || node.height <= 0f) return@forEach
                        val left = node.rootX - root.x
                        val top = node.rootY - root.y
                        val right = left + node.width
                        val bottom = top + node.height
                        if (
                            right <= 0f || bottom <= 0f ||
                            left >= viewportWidth || top >= viewportHeight
                        ) {
                            return@forEach
                        }
                        drawCache.append(
                            rect = Rect(left, top, right, bottom),
                            radiusPx = node.radiusDp.dp.toPx(),
                            backdropAlpha = node.backdropAlpha,
                            frostAlpha = node.frostAlpha,
                            dimAlpha = node.dimAlpha
                        )
                    }

                    if (backdrop != null && size.width > 0f && size.height > 0f) {
                        val sampleOffset = backdropCoordinates.offsetRelativeTo(backdropOrigin)
                        val srcX = (sampleOffset.x * backdrop.scale)
                            .roundToInt()
                            .coerceIn(0, backdrop.image.width - 1)
                        val srcY = (sampleOffset.y * backdrop.scale)
                            .roundToInt()
                            .coerceIn(0, backdrop.image.height - 1)
                        val srcW = (size.width * backdrop.scale)
                            .roundToInt()
                            .coerceAtLeast(1)
                            .coerceAtMost(backdrop.image.width - srcX)
                        val srcH = (size.height * backdrop.scale)
                            .roundToInt()
                            .coerceAtLeast(1)
                            .coerceAtMost(backdrop.image.height - srcY)
                        val dstW = size.width.roundToInt().coerceAtLeast(1)
                        val dstH = size.height.roundToInt().coerceAtLeast(1)

                        drawCache.forEachBackdropPath { alpha, path ->
                            clipPath(path) {
                                drawImage(
                                    image = backdrop.image,
                                    srcOffset = IntOffset(srcX, srcY),
                                    srcSize = IntSize(srcW, srcH),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(dstW, dstH),
                                    alpha = alpha,
                                    blendMode = BlendMode.SrcOver
                                )
                            }
                        }
                    } else {
                        drawCache.forEachResolved { item ->
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF1A2B58),
                                        Color(0xFF5B4A8E),
                                        Color(0xFFB85D78)
                                    ),
                                    startY = item.rect.top,
                                    endY = item.rect.bottom
                                ),
                                topLeft = item.rect.topLeft,
                                size = item.rect.size,
                                cornerRadius = CornerRadius(item.radiusPx, item.radiusPx)
                            )
                        }
                    }

                    drawCache.forEachResolved { item ->
                        val radius = CornerRadius(item.radiusPx, item.radiusPx)
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF2A357A).copy(alpha = 0.34f),
                                    Color(0xFF101A45).copy(alpha = 0.24f),
                                    Color(0xFF4A2F69).copy(alpha = 0.24f)
                                ),
                                startY = item.rect.top,
                                endY = item.rect.bottom
                            ),
                            topLeft = item.rect.topLeft,
                            size = item.rect.size,
                            cornerRadius = radius
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.085f),
                            topLeft = item.rect.topLeft,
                            size = item.rect.size,
                            cornerRadius = radius,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.035f),
                            topLeft = item.rect.topLeft,
                            size = item.rect.size.copy(height = item.rect.height * 0.42f),
                            cornerRadius = radius
                        )
                    }

                    drawCache.forEachFrostPath { alpha, path ->
                        clipPath(path) {
                            drawRect(Color.White.copy(alpha = alpha))
                        }
                    }
                    drawCache.forEachDimPath { alpha, path ->
                        clipPath(path) {
                            drawRect(Color.Black.copy(alpha = alpha))
                        }
                    }

                    drawContent()
                }
        ) {
            content()
        }
    }
}

@Composable
internal fun StockFrostBatchSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    backdropAlpha: Float = 1f,
    frostAlpha: Float = 0f,
    dimAlpha: Float = 0f
) {
    val registry = LocalStockFrostBatchRegistry.current
    if (registry == null) {
        FrostInfoGlassPanel(
            radius = radius.value,
            backdropAlpha = backdropAlpha,
            frostAlpha = frostAlpha,
            dimAlpha = dimAlpha,
            modifier = modifier
        ) {}
        return
    }

    val node = remember { StockFrostBatchNode() }
    SideEffect {
        if (node.updateStyle(radius.value, backdropAlpha, frostAlpha, dimAlpha)) {
            registry.invalidate()
        }
    }
    DisposableEffect(registry, node) {
        registry.register(node)
        onDispose { registry.unregister(node.key) }
    }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val root = coordinates.positionInRoot()
            if (
                node.updateGeometry(
                    x = root.x,
                    y = root.y,
                    width = coordinates.size.width.toFloat(),
                    height = coordinates.size.height.toFloat()
                )
            ) {
                registry.invalidate()
            }
        }
    )
}
