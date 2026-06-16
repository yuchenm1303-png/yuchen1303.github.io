package com.yuchen.ailedger.ui

import android.graphics.Rect as AndroidRect
import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal sealed interface GlassFoldoutClipResult {
    data object Unbounded : GlassFoldoutClipResult
    data object Hidden : GlassFoldoutClipResult
    data class Visible(val rectInRoot: Rect) : GlassFoldoutClipResult
}

private class GlassFoldoutClipEntry(
    val key: Any
) {
    var coordinates: LayoutCoordinates? = null
    var rectInRoot: Rect = Rect.Zero
    var valid: Boolean = false
}

/**
 * 设置页折叠动画的唯一裁剪来源。
 *
 * 每个折叠容器只在真实布局回调中更新自己的当前可见矩形。玻璃节点通过
 * LayoutCoordinates 祖先链定位所属折叠容器，因此不需要为普通、Frost、Inset、
 * OpenGL 分别维护动画进度，也不会在每帧扫描整棵 Compose 树。
 */
@Stable
internal class GlassFoldoutClipRegistry {
    private val entries = LinkedHashMap<Any, GlassFoldoutClipEntry>()
    private val entriesByCoordinates = IdentityHashMap<LayoutCoordinates, GlassFoldoutClipEntry>()

    var version by mutableLongStateOf(0L)
        private set

    fun register(key: Any) {
        if (entries.containsKey(key)) return
        entries[key] = GlassFoldoutClipEntry(key)
        bumpVersion()
    }

    fun update(key: Any, coordinates: LayoutCoordinates) {
        val entry = entries.getOrPut(key) { GlassFoldoutClipEntry(key) }
        val previousCoordinates = entry.coordinates
        if (previousCoordinates !== coordinates) {
            if (previousCoordinates != null) entriesByCoordinates.remove(previousCoordinates)
            entry.coordinates = coordinates
            entriesByCoordinates[coordinates] = entry
        }

        val nextRect = if (coordinates.isAttached) coordinates.boundsInRoot() else Rect.Zero
        val nextValid = nextRect.hasUsableFoldoutBounds()
        if (
            previousCoordinates !== coordinates ||
            entry.rectInRoot != nextRect ||
            entry.valid != nextValid
        ) {
            entry.rectInRoot = nextRect
            entry.valid = nextValid
            bumpVersion()
        }
    }

    fun invalidate(key: Any) {
        val entry = entries[key] ?: return
        if (!entry.valid && entry.rectInRoot == Rect.Zero) return
        entry.valid = false
        entry.rectInRoot = Rect.Zero
        bumpVersion()
    }

    fun unregister(key: Any) {
        val entry = entries.remove(key) ?: return
        entry.coordinates?.let { entriesByCoordinates.remove(it) }
        bumpVersion()
    }

    fun clear() {
        if (entries.isEmpty()) return
        entries.clear()
        entriesByCoordinates.clear()
        bumpVersion()
    }

    fun resolveFor(descendant: LayoutCoordinates?): GlassFoldoutClipResult {
        version
        if (descendant == null || !descendant.isAttached) return GlassFoldoutClipResult.Hidden

        var cursor: LayoutCoordinates? = descendant
        var matched = false
        var resolved: Rect? = null

        while (cursor != null) {
            val entry = entriesByCoordinates[cursor]
            if (entry != null) {
                matched = true
                if (!entry.valid) return GlassFoldoutClipResult.Hidden
                resolved = if (resolved == null) {
                    entry.rectInRoot
                } else {
                    resolved.intersectionOrNull(entry.rectInRoot)
                        ?: return GlassFoldoutClipResult.Hidden
                }
            }
            cursor = cursor.parentLayoutCoordinates
        }

        return if (matched && resolved != null) {
            GlassFoldoutClipResult.Visible(resolved)
        } else {
            GlassFoldoutClipResult.Unbounded
        }
    }

    private fun bumpVersion() {
        version = if (version == Long.MAX_VALUE) 1L else version + 1L
    }
}

internal val LocalGlassFoldoutClipRegistry =
    staticCompositionLocalOf<GlassFoldoutClipRegistry?> { null }

internal fun GlassFoldoutClipRegistry?.resolveLocalClip(
    descendant: LayoutCoordinates?,
    hostRootOffset: Offset,
    viewport: Rect
): Rect? {
    if (this == null) return viewport
    return when (val result = resolveFor(descendant)) {
        GlassFoldoutClipResult.Unbounded -> viewport
        GlassFoldoutClipResult.Hidden -> null
        is GlassFoldoutClipResult.Visible -> {
            val local = result.rectInRoot.translate(-hostRootOffset)
            local.intersectionOrNull(viewport)
        }
    }
}

internal fun View.applyGlassFoldoutClip(
    registry: GlassFoldoutClipRegistry?,
    coordinates: LayoutCoordinates?
) {
    val target = when {
        registry == null -> null
        coordinates == null || !coordinates.isAttached -> AndroidRect(0, 0, 0, 0)
        else -> when (val result = registry.resolveFor(coordinates)) {
            GlassFoldoutClipResult.Unbounded -> null
            GlassFoldoutClipResult.Hidden -> AndroidRect(0, 0, 0, 0)
            is GlassFoldoutClipResult.Visible -> {
                val viewRect = coordinates.boundsInRoot()
                val visible = viewRect.intersectionOrNull(result.rectInRoot)
                    ?: return setGlassFoldoutClipIfChanged(AndroidRect(0, 0, 0, 0))
                AndroidRect(
                    floor(visible.left - viewRect.left).toInt().coerceAtLeast(0),
                    floor(visible.top - viewRect.top).toInt().coerceAtLeast(0),
                    ceil(visible.right - viewRect.left).toInt().coerceAtMost(max(0, coordinates.size.width)),
                    ceil(visible.bottom - viewRect.top).toInt().coerceAtMost(max(0, coordinates.size.height))
                )
            }
        }
    }
    setGlassFoldoutClipIfChanged(target)
}

private fun View.setGlassFoldoutClipIfChanged(target: AndroidRect?) {
    val current = clipBounds
    if (current == target) return
    clipBounds = target
}

private fun Rect.hasUsableFoldoutBounds(): Boolean =
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
        width > 0.5f && height > 0.5f

internal fun Rect.intersectionOrNull(
    other: Rect,
    minimumExtent: Float = 0f
): Rect? {
    val clippedLeft = max(left, other.left)
    val clippedTop = max(top, other.top)
    val clippedRight = min(right, other.right)
    val clippedBottom = min(bottom, other.bottom)
    return if (
        clippedRight - clippedLeft > minimumExtent &&
        clippedBottom - clippedTop > minimumExtent
    ) {
        Rect(clippedLeft, clippedTop, clippedRight, clippedBottom)
    } else {
        null
    }
}
