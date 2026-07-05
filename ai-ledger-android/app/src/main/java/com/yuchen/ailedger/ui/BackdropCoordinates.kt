package com.yuchen.ailedger.ui

import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
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
import java.util.ArrayDeque
import java.util.IdentityHashMap

/**
 * OpenGL Shell 的最终帧提交器。
 *
 * 所有 placement、滚动、页面 graphicsLayer 动画和按压状态只负责标记本帧需要刷新；
 * 真正的坐标读取统一延迟到 Android 根视图的 PreDraw，此时 Compose 本帧的布局与图层
 * 变换已经全部落定。这样旧版单卡、新版单卡和共享 Shell 都可以消费同一时刻的最终状态，
 * 不再各自跨一帧追踪坐标。
 *
 * 热路径只复用一个 OnPreDrawListener 和每个 Ticker 的固定提交动作，不再为每次滚动帧复制
 * ticker 集合或创建新的监听器/lambda。
 */
internal object OpenGLFrameFinalizer {
    private val boundRoots = IdentityHashMap<View, Int>()
    private val activeTickers = linkedSetOf<BackdropFrameTicker>()
    private val pendingActions = ArrayDeque<() -> Unit>()

    private var scheduledView: View? = null
    private var scheduledObserver: ViewTreeObserver? = null

    internal var isDispatchingFrame: Boolean = false
        private set

    private val preDrawListener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            detachScheduledListener()
            drainPendingActions()
            return true
        }
    }

    fun bindHostView(view: View): () -> Unit {
        val root = view.rootView
        boundRoots[root] = (boundRoots[root] ?: 0) + 1
        if (pendingActions.isNotEmpty()) schedulePreDraw()

        var released = false
        return {
            if (!released) {
                released = true
                val remaining = (boundRoots[root] ?: 1) - 1
                if (remaining <= 0) {
                    boundRoots.remove(root)
                    if (scheduledView === root) {
                        detachScheduledListener()
                        if (pendingActions.isNotEmpty()) schedulePreDraw()
                    }
                } else {
                    boundRoots[root] = remaining
                }
            }
        }
    }

    internal fun registerTicker(ticker: BackdropFrameTicker) {
        activeTickers += ticker
    }

    internal fun unregisterTicker(ticker: BackdropFrameTicker) {
        activeTickers -= ticker
    }

    /**
     * 请求所有当前可见 OpenGL Shell 在本次 traversal 的 PreDraw 阶段提交最终快照。
     * 返回 false 仅表示当前没有任何 OpenGL Host 订阅 ticker，调用方应走兼容回调。
     */
    fun requestActiveTickerFrame(frameTimeNanos: Long = System.nanoTime()): Boolean {
        if (activeTickers.isEmpty()) return false
        for (ticker in activeTickers) {
            ticker.requestFinalizedFrame(frameTimeNanos)
        }
        return true
    }

    internal fun dispatch(action: () -> Unit) {
        pendingActions.addLast(action)
        if (isDispatchingFrame) return
        schedulePreDraw()
    }

    private fun schedulePreDraw() {
        if (scheduledObserver != null || isDispatchingFrame) return

        var target: View? = null
        for (root in boundRoots.keys) {
            if (root.isAttachedToWindow && root.viewTreeObserver.isAlive) {
                target = root
                break
            }
        }
        if (target == null) {
            drainPendingActions()
            return
        }

        val observer = target.viewTreeObserver
        scheduledView = target
        scheduledObserver = observer
        observer.addOnPreDrawListener(preDrawListener)
        target.postInvalidateOnAnimation()
    }

    private fun detachScheduledListener() {
        val observer = scheduledObserver
        if (observer != null && observer.isAlive) {
            observer.removeOnPreDrawListener(preDrawListener)
        }
        scheduledView = null
        scheduledObserver = null
    }

    private fun drainPendingActions() {
        if (isDispatchingFrame) return
        isDispatchingFrame = true
        try {
            while (pendingActions.isNotEmpty()) {
                pendingActions.removeFirst().invoke()
            }
        } finally {
            isDispatchingFrame = false
        }
    }
}

class BackdropCoordinateSource {
    private var lastRootOffset: Offset? = null
    private var lastSize: IntSize = IntSize.Zero
    private val placementListeners = linkedSetOf<() -> Unit>()

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
        if (OpenGLFrameFinalizer.requestActiveTickerFrame()) return
        if (placementListeners.isEmpty()) return
        for (listener in placementListeners) listener()
    }
}

class GlassCoordinateSource {
    private var wasAttached = false
    private var lastRootOffset: Offset? = null
    private var lastSize: IntSize = IntSize.Zero
    private val placementListeners = linkedSetOf<() -> Unit>()

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
        if (OpenGLFrameFinalizer.requestActiveTickerFrame()) return
        if (placementListeners.isEmpty()) return
        for (listener in placementListeners) listener()
    }
}

/**
 * 背景/玻璃位置更新统一按真实显示 VSync 合并，并在同帧 PreDraw 读取最终坐标。
 *
 * 一帧内的 nested-scroll、程序化滚动、布局回调、页面动画和动态按压只保留最后一次请求；
 * Compose frameNanos 与 OpenGL Host 监听器也在同一份最终帧提交中更新。
 */
class BackdropFrameTicker {
    var frameNanos by mutableLongStateOf(0L)
        private set

    private var framePosted = false
    private var finalDispatchQueued = false
    private var pendingFrameNanos = 0L
    private val frameListeners = linkedSetOf<() -> Unit>()

    private val finalDispatchAction: () -> Unit = {
        finalDispatchQueued = false
        val committedFrameNanos = pendingFrameNanos.coerceAtLeast(System.nanoTime())
        pendingFrameNanos = 0L
        frameNanos = committedFrameNanos
        if (frameListeners.isNotEmpty()) {
            for (listener in frameListeners) listener()
        }
    }

    fun addFrameListener(listener: () -> Unit): () -> Unit {
        val wasEmpty = frameListeners.isEmpty()
        frameListeners += listener
        if (wasEmpty) {
            OpenGLFrameFinalizer.registerTicker(this)
        }
        var removed = false
        return {
            if (!removed) {
                removed = true
                frameListeners -= listener
                if (frameListeners.isEmpty()) {
                    OpenGLFrameFinalizer.unregisterTicker(this)
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun requestFrame(nowNanos: Long = System.nanoTime(), force: Boolean = false) {
        if (framePosted) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    internal fun requestFinalizedFrame(frameTimeNanos: Long = System.nanoTime()) {
        pendingFrameNanos = maxOf(pendingFrameNanos, frameTimeNanos.coerceAtLeast(1L))
        if (finalDispatchQueued) return
        finalDispatchQueued = true
        OpenGLFrameFinalizer.dispatch(finalDispatchAction)
    }

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        framePosted = false
        requestFinalizedFrame(frameTimeNanos)
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
