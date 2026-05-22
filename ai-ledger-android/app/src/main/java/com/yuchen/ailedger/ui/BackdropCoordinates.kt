package com.yuchen.ailedger.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import com.yuchen.ailedger.model.RenderQuality

class BackdropCoordinateSource {
    var coordinates: LayoutCoordinates? = null

    fun rootOffset(): Offset {
        val current = coordinates
        return if (current != null && current.isAttached) {
            current.localToRoot(Offset.Zero)
        } else {
            Offset.Zero
        }
    }
}

class GlassCoordinateSource {
    var coordinates: LayoutCoordinates? = null

    fun rootOffset(): Offset {
        val current = coordinates
        return if (current != null && current.isAttached) {
            current.localToRoot(Offset.Zero)
        } else {
            Offset.Zero
        }
    }

    fun itemSize(): IntSize {
        val current = coordinates
        return if (current != null && current.isAttached) current.size else IntSize.Zero
    }

    fun offsetRelativeTo(backdrop: BackdropCoordinateSource?): Offset {
        val current = coordinates
        return if (current != null && current.isAttached) {
            current.localToRoot(Offset.Zero) - (backdrop?.rootOffset() ?: Offset.Zero)
        } else {
            Offset.Zero
        }
    }

    fun isAttached(): Boolean = coordinates?.isAttached == true
}

class BackdropFrameTicker {
    var frameNanos by mutableLongStateOf(0L)
}

data class OpenGlGlassFrameRect(
    val key: Any,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val originX: Float,
    val originY: Float
)

class OpenGlGlassFrameCoordinator {
    private val rects = linkedMapOf<Any, OpenGlGlassFrameRect>()
    private var cachedSnapshot: List<OpenGlGlassFrameRect> = emptyList()
    private var dirty = true

    var version by mutableLongStateOf(0L)
        private set

    fun upsert(rect: OpenGlGlassFrameRect) {
        if (rect.width <= 0f || rect.height <= 0f) return
        if (rects[rect.key] == rect) return
        rects[rect.key] = rect
        dirty = true
        version += 1L
    }

    fun remove(key: Any) {
        if (rects.remove(key) != null) {
            dirty = true
            version += 1L
        }
    }

    fun snapshot(): List<OpenGlGlassFrameRect> {
        if (dirty) {
            cachedSnapshot = rects.values.toList()
            dirty = false
        }
        return cachedSnapshot
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
    private var cachedSnapshot: List<GlassRenderItem> = emptyList()
    private var dirty = false

    var version by mutableLongStateOf(0L)
        private set

    fun upsert(item: GlassRenderItem) {
        if (items[item.key] == item) return
        items[item.key] = item
        dirty = true
        version += 1L
    }

    fun remove(key: Any) {
        if (items.remove(key) != null) {
            dirty = true
            version += 1L
        }
    }

    fun snapshot(): List<GlassRenderItem> {
        if (dirty) {
            cachedSnapshot = items.values.toList()
            dirty = false
        }
        return cachedSnapshot
    }
}

val LocalBackdropOrigin = compositionLocalOf<BackdropCoordinateSource?> { null }
val LocalBackdropFrameTicker = compositionLocalOf<BackdropFrameTicker?> { null }
val LocalGlassItemRegistry = compositionLocalOf<GlassItemRegistry?> { null }
val LocalOpenGlGlassFrameCoordinator = compositionLocalOf<OpenGlGlassFrameCoordinator?> { null }
