package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private const val ProgressTrackPreloadMarginDp = 48f
private const val ProgressChangeEpsilon = 0.0001f

internal class CachedInsetProgressTrack {
    var geometrySignature: Long = Long.MIN_VALUE
    var localSize: Size = Size.Zero
    var corner: CornerRadius = CornerRadius(0f, 0f)
}

internal class InsetProgressTrackSlot(
    var rect: Rect,
    var coordinates: LayoutCoordinates,
    var progress: Float,
) {
    val cache = CachedInsetProgressTrack()
}

/**
 * 设置页滑条动态轨道的单一父级状态。
 *
 * 子项只上报真实轨道坐标和 0..1 进度，不再各自持有 Canvas。几何快照仅在布局变化时
 * 重建；拖动只更新对应浮点值和父级绘制版本，不触碰静态凹槽玻璃缓存。
 */
internal class InsetGlassSliderProgressBatchState {
    private val trackCoordinates = LinkedHashMap<Any, LayoutCoordinates>()
    private val progressValues = LinkedHashMap<Any, Float>()
    private val tracks = LinkedHashMap<Any, InsetProgressTrackSlot>()
    private var cachedSnapshot: List<InsetProgressTrackSlot> = emptyList()
    private var hostCoordinates: LayoutCoordinates? = null
    private var hostSize: IntSize = IntSize.Zero
    private val hostCoordinateSource = GlassCoordinateSource()

    internal var geometryVersion by mutableLongStateOf(0L)
        private set

    internal var progressVersion by mutableLongStateOf(0L)
        private set

    internal fun snapshotForDraw(): List<InsetProgressTrackSlot> = cachedSnapshot

    internal fun updateHost(coordinates: LayoutCoordinates) {
        val firstPlacement = hostCoordinates == null
        val sizeChanged = hostSize != coordinates.size
        hostCoordinates = coordinates
        hostSize = coordinates.size
        hostCoordinateSource.coordinates = coordinates
        if (firstPlacement || sizeChanged) syncAllTracks()
    }

    internal fun updateTrack(key: Any, coordinates: LayoutCoordinates) {
        trackCoordinates[key] = coordinates
        syncTrack(key)
    }

    internal fun updateProgress(key: Any, progress: Float) {
        val normalized = progress.coerceIn(0f, 1f)
        val previous = progressValues.put(key, normalized)
        val slot = tracks[key]
        if (slot != null && abs(slot.progress - normalized) > ProgressChangeEpsilon) {
            slot.progress = normalized
            bumpProgressVersion()
        } else if (slot == null && previous != null && abs(previous - normalized) > ProgressChangeEpsilon) {
            bumpProgressVersion()
        }
    }

    internal fun removeTrack(key: Any) {
        trackCoordinates.remove(key)
        progressValues.remove(key)
        if (tracks.remove(key) != null) rebuildSnapshot()
    }

    internal fun clear() {
        trackCoordinates.clear()
        progressValues.clear()
        tracks.clear()
        cachedSnapshot = emptyList()
        hostCoordinates = null
        hostSize = IntSize.Zero
        hostCoordinateSource.coordinates = null
        bumpGeometryVersion()
        bumpProgressVersion()
    }

    internal fun isHostAttached(): Boolean = hostCoordinateSource.isAttached()

    internal fun hostRootOffset(): Offset = hostCoordinateSource.rootOffset()

    private fun syncAllTracks() {
        val keys = trackCoordinates.keys.toTypedArray()
        keys.forEach(::syncTrack)
    }

    private fun syncTrack(key: Any) {
        val host = hostCoordinates ?: return
        val track = trackCoordinates[key] ?: return
        if (!host.isAttached || !track.isAttached) {
            if (tracks.remove(key) != null) rebuildSnapshot()
            return
        }

        val rect = host.localBoundingBoxOf(track, clipBounds = false)
        if (rect.width <= 0f || rect.height <= 0f) {
            if (tracks.remove(key) != null) rebuildSnapshot()
            return
        }

        val progress = progressValues[key]?.coerceIn(0f, 1f) ?: 0f
        val current = tracks[key]
        if (current == null) {
            tracks[key] = InsetProgressTrackSlot(rect, track, progress)
            rebuildSnapshot()
        } else {
            var geometryChanged = false
            if (current.rect != rect) {
                current.rect = rect
                geometryChanged = true
            }
            if (current.coordinates !== track) {
                current.coordinates = track
                geometryChanged = true
            }
            if (geometryChanged) bumpGeometryVersion()
            if (abs(current.progress - progress) > ProgressChangeEpsilon) {
                current.progress = progress
                bumpProgressVersion()
            }
        }
    }

    private fun rebuildSnapshot() {
        cachedSnapshot = tracks.values.toList()
        bumpGeometryVersion()
    }

    private fun bumpGeometryVersion() {
        geometryVersion = if (geometryVersion == Long.MAX_VALUE) 1L else geometryVersion + 1L
    }

    private fun bumpProgressVersion() {
        progressVersion = if (progressVersion == Long.MAX_VALUE) 1L else progressVersion + 1L
    }
}

internal val LocalInsetGlassSliderProgressBatchState =
    staticCompositionLocalOf<InsetGlassSliderProgressBatchState?> { null }

/**
 * 设置页所有 InsetGlassParameterSlider 共用的一张动态进度轨父级画布。
 *
 * 静态凹槽仍由 InsetGlassSliderBatchGroup 绘制；本层只负责灰色轨底和青色进度，拖动
 * 任意滑条时只失效这一张轻量 Canvas，子滑条不再创建独立绘制节点。
 */
@Composable
internal fun InsetGlassSliderProgressBatchGroup(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!LocalSettingsStaticBatchReady.current) {
        CompositionLocalProvider(LocalInsetGlassSliderProgressBatchState provides null) {
            Box(modifier = modifier, content = content)
        }
        return
    }

    val state = remember { InsetGlassSliderProgressBatchState() }
    DisposableEffect(state) {
        onDispose { state.clear() }
    }

    CompositionLocalProvider(LocalInsetGlassSliderProgressBatchState provides state) {
        Box(modifier = modifier.onPlaced(state::updateHost)) {
            InsetGlassSliderProgressBatchLayer(state)
            content()
        }
    }
}

@Composable
private fun BoxScope.InsetGlassSliderProgressBatchLayer(
    state: InsetGlassSliderProgressBatchState,
) {
    val foldoutClipRegistry = LocalGlassFoldoutClipRegistry.current
    val inactiveColor = remember { Color.White.copy(alpha = 0.09f) }
    val activeBrush = remember {
        Brush.horizontalGradient(
            listOf(
                Color(0xFFBFFAFF).copy(alpha = 0.95f),
                Color(0xFF8DF9EA).copy(alpha = 0.72f),
            )
        )
    }

    Canvas(modifier = Modifier.matchParentSize()) {
        state.geometryVersion
        state.progressVersion
        val tracks = state.snapshotForDraw()
        if (tracks.isEmpty() || !state.isHostAttached()) return@Canvas

        val viewport = Rect(0f, 0f, size.width, size.height)
        val hostRootOffset = state.hostRootOffset()
        val preloadMarginPx = ProgressTrackPreloadMarginDp.dp.toPx()

        tracks.forEach { track ->
            if (!track.coordinates.isAttached) return@forEach
            val rect = track.rect
            if (
                rect.right < -preloadMarginPx ||
                rect.bottom < -preloadMarginPx ||
                rect.left > size.width + preloadMarginPx ||
                rect.top > size.height + preloadMarginPx
            ) {
                return@forEach
            }

            val foldoutClip = foldoutClipRegistry.resolveLocalClip(
                descendant = track.coordinates,
                hostRootOffset = hostRootOffset,
                viewport = viewport,
            ) ?: return@forEach
            val clipLeft = maxOf(rect.left, foldoutClip.left)
            val clipTop = maxOf(rect.top, foldoutClip.top)
            val clipRight = minOf(rect.right, foldoutClip.right)
            val clipBottom = minOf(rect.bottom, foldoutClip.bottom)
            if (clipRight <= clipLeft || clipBottom <= clipTop) return@forEach

            val cache = ensureProgressTrackCache(track.cache, rect.size)
            clipRect(
                left = clipLeft,
                top = clipTop,
                right = clipRight,
                bottom = clipBottom,
            ) {
                withTransform({ translate(rect.left, rect.top) }) {
                    drawRoundRect(
                        color = inactiveColor,
                        size = cache.localSize,
                        cornerRadius = cache.corner,
                    )
                    val activeWidth = cache.localSize.width * track.progress.coerceIn(0f, 1f)
                    if (activeWidth > 0f) {
                        drawRoundRect(
                            brush = activeBrush,
                            size = Size(activeWidth, cache.localSize.height),
                            cornerRadius = cache.corner,
                            blendMode = BlendMode.Screen,
                        )
                    }
                }
            }
        }
    }
}

private fun ensureProgressTrackCache(
    cache: CachedInsetProgressTrack,
    size: Size,
): CachedInsetProgressTrack {
    val width = size.width.coerceAtLeast(1f)
    val height = size.height.coerceAtLeast(1f)
    val signature = progressGeometrySignature(width, height)
    if (cache.geometrySignature == signature) return cache

    cache.localSize = Size(width, height)
    val radius = height / 2f
    cache.corner = CornerRadius(radius, radius)
    cache.geometrySignature = signature
    return cache
}

private fun progressGeometrySignature(width: Float, height: Float): Long {
    var result = 1125899906842597L
    result = result * 31L + width.toBits()
    result = result * 31L + height.toBits()
    return result
}
