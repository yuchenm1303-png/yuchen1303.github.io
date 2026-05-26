package com.yuchen.ailedger.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import com.yuchen.ailedger.model.RenderQuality

class BackdropCoordinateSource {
    private var lastRootOffset: Offset? = null
    private var lastSize: IntSize = IntSize.Zero
    var placementVersion by mutableLongStateOf(0L)
        private set

    var coordinates: LayoutCoordinates? = null
        set(value) {
            field = value
            syncPlacementVersion(value)
        }

    private fun syncPlacementVersion(current: LayoutCoordinates?) {
        if (current == null || !current.isAttached) {
            if (lastRootOffset != null || lastSize != IntSize.Zero) {
                lastRootOffset = null
                lastSize = IntSize.Zero
                placementVersion = System.nanoTime()
            }
            return
        }
        val rootOffset = current.localToRoot(Offset.Zero)
        val size = current.size
        if (lastRootOffset != rootOffset || lastSize != size) {
            lastRootOffset = rootOffset
            lastSize = size
            placementVersion = System.nanoTime()
        }
    }

    fun rootOffset(): Offset {
        placementVersion
        val current = coordinates
        return if (current != null && current.isAttached) {
            current.localToRoot(Offset.Zero)
        } else {
            Offset.Zero
        }
    }
}

class GlassCoordinateSource {
    private var lastRootOffset: Offset? = null
    private var lastSize: IntSize = IntSize.Zero
    var placementVersion by mutableLongStateOf(0L)
        private set

    var coordinates: LayoutCoordinates? = null
        set(value) {
            field = value
            syncPlacementVersion(value)
        }

    private fun syncPlacementVersion(current: LayoutCoordinates?) {
        if (current == null || !current.isAttached) {
            if (lastRootOffset != null || lastSize != IntSize.Zero) {
                lastRootOffset = null
                lastSize = IntSize.Zero
                placementVersion = System.nanoTime()
            }
            return
        }
        val rootOffset = current.localToRoot(Offset.Zero)
        val size = current.size
        if (lastRootOffset != rootOffset || lastSize != size) {
            lastRootOffset = rootOffset
            lastSize = size
            placementVersion = System.nanoTime()
        }
    }

    fun rootOffset(): Offset {
        placementVersion
        val current = coordinates
        return if (current != null && current.isAttached) {
            current.localToRoot(Offset.Zero)
        } else {
            Offset.Zero
        }
    }

    fun itemSize(): IntSize {
        placementVersion
        val current = coordinates
        return if (current != null && current.isAttached) current.size else IntSize.Zero
    }

    fun offsetRelativeTo(backdrop: BackdropCoordinateSource?): Offset {
        placementVersion
        val current = coordinates
        return if (current != null && current.isAttached) {
            current.localToRoot(Offset.Zero) - (backdrop?.rootOffset() ?: Offset.Zero)
        } else {
            Offset.Zero
        }
    }

    fun isAttached(): Boolean {
        placementVersion
        return coordinates?.isAttached == true
    }
}

class BackdropFrameTicker {
    var frameNanos by mutableLongStateOf(0L)
}

@Composable
fun SyncGlassBackdropToScroll(listState: LazyListState) {
    val ticker = LocalBackdropFrameTicker.current ?: return
    LaunchedEffect(listState, ticker) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect {
                ticker.frameNanos = System.nanoTime()
            }
    }
}

data class GlassRenderItem(
    val key: Any,
    val coordinates: GlassCoordinateSource,
    val radius: Int,
    val role: GlassRole,
    val quality: RenderQuality,
    val glassIntensity: Float,
    val edgeStrength: Float,
    val backdropAlpha: Float
)

class GlassItemRegistry {
    private val items = linkedMapOf<Any, GlassRenderItem>()
    var version by mutableLongStateOf(0L)
        private set

    fun upsert(item: GlassRenderItem) {
        val previous = items[item.key]
        items[item.key] = item
        if (previous != item) version = System.nanoTime()
    }

    fun remove(key: Any) {
        if (items.remove(key) != null) version = System.nanoTime()
    }

    fun snapshot(): List<GlassRenderItem> {
        version
        return items.values.toList()
    }
}

val LocalBackdropOrigin = compositionLocalOf<BackdropCoordinateSource?> { null }
val LocalBackdropFrameTicker = compositionLocalOf<BackdropFrameTicker?> { null }
val LocalGlassItemRegistry = compositionLocalOf<GlassItemRegistry?> { null }
