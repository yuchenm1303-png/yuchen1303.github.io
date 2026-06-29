package com.yuchen.ailedger.ui

import android.view.Choreographer
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
import java.util.concurrent.CopyOnWriteArraySet

class BackdropCoordinateSource {
    private var lastRootOffset: Offset? = null
    private var lastSize: IntSize = IntSize.Zero
    private val placementListeners = CopyOnWriteArraySet<() -> Unit>()

    var placementVersion by mutableLongStateOf(0L)
        private set

    var coordinates: LayoutCoordinates? = null
        set(value) {
            field = value
            syncPlacementVersion(value)
        }

    fun addPlacementListener(listener: () -> Unit): () -> Unit {
        placementListeners += listener
        return { placementListeners -= listener }
    }

    private fun syncPlacementVersion(current: LayoutCoordinates?) {
        if (current == null || !current.isAttached) {
            if (lastRootOffset != null || lastSize != IntSize.Zero) {
                lastRootOffset = null
                lastSize = IntSize.Zero
                placementVersion += 1L
                notifyPlacementListeners()
            }
            return
        }
        val rootOffset = current.localToRoot(Offset.Zero)
        val size = current.size
        if (lastRootOffset != rootOffset || lastSize != size) {
            lastRootOffset = rootOffset
            lastSize = size
            placementVersion += 1L
            notifyPlacementListeners()
        }
    }

    fun rootOffset(): Offset {
        placementVersion
        return rootOffsetNow()
    }

    fun rootOffsetNow(): Offset {
        val current = coordinates
        return if (current != null && current.isAttached) {
            current.localToRoot(Offset.Zero)
        } else {
            Offset.Zero
        }
    }

    private fun notifyPlacementListeners() {
        for (listener in placementListeners) listener()
    }
}

class GlassCoordinateSource {
    private var wasAttached = false
    private var lastRootOffset: Offset? = null
    private var lastSize: IntSize = IntSize.Zero
    private val placementListeners = CopyOnWriteArraySet<() -> Unit>()

    var placementVersion by mutableLongStateOf(0L)
        private set

    var coordinates: LayoutCoordinates? = null
        set(value) {
            field = value
            syncPlacementVersion(value)
        }

    fun addPlacementListener(listener: () -> Unit): () -> Unit {
        placementListeners += listener
        return { placementListeners -= listener }
    }

    private fun syncPlacementVersion(current: LayoutCoordinates?) {
        val attached = current?.isAttached == true
        val size = if (attached) current?.size ?: IntSize.Zero else IntSize.Zero
        val rootOffset = if (attached) current?.localToRoot(Offset.Zero) else null
        if (wasAttached != attached || lastRootOffset != rootOffset || lastSize != size) {
            wasAttached = attached
            lastRootOffset = rootOffset
            lastSize = size
            placementVersion += 1L
            notifyPlacementListeners()
        }
    }

    fun rootOffset(): Offset {
        placementVersion
        return rootOffsetNow()
    }

    fun rootOffsetNow(): Offset {
        val current = coordinates
        return if (current != null && current.isAttached) {
            current.localToRoot(Offset.Zero)
        } else {
            Offset.Zero
        }
    }

    fun itemSize(): IntSize {
        placementVersion
        return itemSizeNow()
    }

    fun itemSizeNow(): IntSize {
        val current = coordinates
        return if (current != null && current.isAttached) current.size else IntSize.Zero
    }

    fun offsetRelativeTo(backdrop: BackdropCoordinateSource?): Offset {
        placementVersion
        return offsetRelativeToNow(backdrop)
    }

    fun offsetRelativeToNow(backdrop: BackdropCoordinateSource?): Offset {
        val current = coordinates
        return if (current != null && current.isAttached) {
            current.localToRoot(Offset.Zero) - (backdrop?.rootOffsetNow() ?: Offset.Zero)
        } else {
            Offset.Zero
        }
    }

    fun isAttached(): Boolean {
        placementVersion
        return isAttachedNow()
    }

    fun isAttachedNow(): Boolean = coordinates?.isAttached == true

    private fun notifyPlacementListeners() {
        for (listener in placementListeners) listener()
    }
}

/**
 * 背景/玻璃位置更新统一按真实显示 VSync 合并。
 *
 * 一帧内的 nested-scroll、程序化滚动和布局回调只保留最后一次请求；兼容的
 * Compose frameNanos 状态仍只写一次，OpenGL Host 可通过监听器直接消费而无需重组。
 */
class BackdropFrameTicker {
    var frameNanos by mutableLongStateOf(0L)
        private set

    private var framePosted = false
    private val frameListeners = CopyOnWriteArraySet<() -> Unit>()

    fun addFrameListener(listener: () -> Unit): () -> Unit {
        frameListeners += listener
        return { frameListeners -= listener }
    }

    @Suppress("UNUSED_PARAMETER")
    fun requestFrame(nowNanos: Long = System.nanoTime(), force: Boolean = false) {
        if (framePosted) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        framePosted = false
        frameNanos = frameTimeNanos
        for (listener in frameListeners) listener()
    }
}

/**
 * 非助手页面的滚动帧统一由 App 根级 NestedScrollConnection 驱动。
 * 这里仅补齐程序化跳转和滚动停止后的最终采样位置，避免再启动第二条逐帧循环。
 */
@Composable
fun SyncGlassBackdropToScroll(listState: LazyListState) {
    val ticker = LocalBackdropFrameTicker.current ?: return
    LaunchedEffect(listState, ticker) {
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress
            )
        }.collect { (_, _, isScrolling) ->
            if (!isScrolling) ticker.requestFrame(force = true)
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
    val backdropAlpha: Float,
    val sceneGroup: GlassSceneGroup = GlassSceneGroup.Unassigned
)

/**
 * 旧全屏统一玻璃层的兼容 registry。
 *
 * 当前生产普通玻璃使用 OrdinaryGlassItemRegistry，Shell 使用独立 OpenGL Host。这个
 * registry 只为旧入口保留，并且在真正收到节点前不分配 Map，避免 App 根节点为一条
 * 已停用的渲染链常驻集合对象。
 */
class GlassItemRegistry {
    private var items: LinkedHashMap<Any, GlassRenderItem>? = null
    private var cachedSnapshot: List<GlassRenderItem> = emptyList()

    var version by mutableLongStateOf(0L)
        private set

    fun upsert(item: GlassRenderItem) {
        val liveItems = items ?: linkedMapOf<Any, GlassRenderItem>().also { items = it }
        val previous = liveItems[item.key]
        liveItems[item.key] = item
        if (previous != item) invalidate()
    }

    fun remove(key: Any) {
        val liveItems = items ?: return
        if (liveItems.remove(key) != null) {
            if (liveItems.isEmpty()) items = null
            invalidate()
        }
    }

    fun snapshot(): List<GlassRenderItem> {
        version
        return cachedSnapshot
    }

    private fun invalidate() {
        cachedSnapshot = items?.values?.toList().orEmpty()
        version += 1L
    }
}

val LocalBackdropOrigin = compositionLocalOf<BackdropCoordinateSource?> { null }
val LocalBackdropFrameTicker = compositionLocalOf<BackdropFrameTicker?> { null }
val LocalGlassItemRegistry = compositionLocalOf<GlassItemRegistry?> { null }
