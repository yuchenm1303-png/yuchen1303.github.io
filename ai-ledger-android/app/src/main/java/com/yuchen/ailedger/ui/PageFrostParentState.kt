package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot

internal class PageFrostDrawCache {
    var geometrySignature: Long = Long.MIN_VALUE
    var mask: Path = Path()
    var localSize: Size = Size.Zero
    var fallbackBrush: Brush? = null
}

@Stable
internal class PageFrostParentItem(
    val id: Any,
    coordinates: LayoutCoordinates,
    rectInRoot: Rect,
    radiusDp: Float,
    backdropAlpha: Float,
    frostAlpha: Float,
    dimAlpha: Float,
) {
    var coordinates: LayoutCoordinates = coordinates
        internal set
    var rectInRoot: Rect = rectInRoot
        internal set
    var radiusDp: Float = radiusDp
        internal set
    var backdropAlpha: Float = backdropAlpha
        internal set
    var frostAlpha: Float = frostAlpha
        internal set
    var dimAlpha: Float = dimAlpha
        internal set

    internal val drawCache = PageFrostDrawCache()
}

/**
 * 每个页面独立持有的普通雾面玻璃注册表。
 * 这里只保存 Compose 几何与材质参数，不接入任何 OpenGL 结构。
 */
@Stable
internal class PageFrostParentLayerState {
    private val entries = LinkedHashMap<Any, PageFrostParentItem>()
    private var cachedSnapshot: List<PageFrostParentItem> = emptyList()
    private var rootCoordinates: LayoutCoordinates? = null

    internal var drawVersion by mutableLongStateOf(0L)
        private set

    internal fun items(): List<PageFrostParentItem> = cachedSnapshot

    internal fun updateRoot(coordinates: LayoutCoordinates) {
        val changed = rootCoordinates !== coordinates
        rootCoordinates = coordinates
        if (changed) bumpDrawVersion()
    }

    internal fun rootBounds(): Rect = rootCoordinates
        ?.takeIf { it.isAttached }
        ?.boundsInRoot()
        ?: Rect.Zero

    internal fun localBoundsOf(coordinates: LayoutCoordinates): Rect? {
        val root = rootCoordinates ?: return null
        if (!root.isAttached || !coordinates.isAttached) return null
        return runCatching {
            root.localBoundingBoxOf(coordinates, clipBounds = false)
        }.getOrNull()
    }

    internal fun upsert(
        id: Any,
        coordinates: LayoutCoordinates,
        radiusDp: Float,
        backdropAlpha: Float,
        frostAlpha: Float,
        dimAlpha: Float,
    ) {
        if (!coordinates.isAttached) return
        val rectInRoot = coordinates.boundsInRoot()
        if (rectInRoot.width <= 1f || rectInRoot.height <= 1f) return

        val safeRadius = radiusDp.coerceAtLeast(0f)
        val safeBackdrop = backdropAlpha.coerceIn(0f, 1f)
        val safeFrost = frostAlpha.coerceIn(0f, 0.85f)
        val safeDim = dimAlpha.coerceIn(0f, 0.65f)
        val current = entries[id]
        if (current == null) {
            entries[id] = PageFrostParentItem(
                id = id,
                coordinates = coordinates,
                rectInRoot = rectInRoot,
                radiusDp = safeRadius,
                backdropAlpha = safeBackdrop,
                frostAlpha = safeFrost,
                dimAlpha = safeDim,
            )
            rebuildSnapshot()
            return
        }

        var changed = false
        if (current.coordinates !== coordinates) {
            current.coordinates = coordinates
            changed = true
        }
        if (current.rectInRoot != rectInRoot) {
            current.rectInRoot = rectInRoot
            current.drawCache.geometrySignature = Long.MIN_VALUE
            changed = true
        }
        if (current.radiusDp != safeRadius) {
            current.radiusDp = safeRadius
            current.drawCache.geometrySignature = Long.MIN_VALUE
            changed = true
        }
        if (current.backdropAlpha != safeBackdrop) {
            current.backdropAlpha = safeBackdrop
            changed = true
        }
        if (current.frostAlpha != safeFrost) {
            current.frostAlpha = safeFrost
            changed = true
        }
        if (current.dimAlpha != safeDim) {
            current.dimAlpha = safeDim
            changed = true
        }
        if (changed) bumpDrawVersion()
    }

    internal fun remove(id: Any) {
        if (entries.remove(id) != null) rebuildSnapshot()
    }

    internal fun clear() {
        if (entries.isEmpty() && rootCoordinates == null) return
        entries.clear()
        cachedSnapshot = emptyList()
        rootCoordinates = null
        bumpDrawVersion()
    }

    private fun rebuildSnapshot() {
        cachedSnapshot = entries.values.toList()
        bumpDrawVersion()
    }

    private fun bumpDrawVersion() {
        drawVersion = if (drawVersion == Long.MAX_VALUE) 1L else drawVersion + 1L
    }
}

internal val LocalPageFrostParentLayer =
    staticCompositionLocalOf<PageFrostParentLayerState?> { null }

@Composable
internal fun rememberPageFrostParentLayerState(): PageFrostParentLayerState =
    remember { PageFrostParentLayerState() }
