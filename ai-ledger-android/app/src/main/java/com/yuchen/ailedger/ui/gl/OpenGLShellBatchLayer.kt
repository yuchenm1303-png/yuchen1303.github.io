package com.yuchen.ailedger.ui.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.BackdropCoordinateSource
import com.yuchen.ailedger.ui.BackdropFrameTicker
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.LocalBackdropFrameTicker
import com.yuchen.ailedger.ui.LocalBackdropOrigin
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassBackdrop
import com.yuchen.ailedger.ui.OpenGLFrameFinalizer
import com.yuchen.ailedger.ui.PerformanceRuntimeMetrics
import com.yuchen.ailedger.ui.StartupPerformanceGate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val MAX_BATCH_ITEMS = 8
private const val FRAME_FLOATS = 11
private const val FRAME_LEFT = 0
private const val FRAME_TOP = 1
private const val FRAME_WIDTH = 2
private const val FRAME_HEIGHT = 3
private const val FRAME_RADIUS = 4
private const val FRAME_INTENSITY = 5
private const val FRAME_ORIGIN_X = 6
private const val FRAME_ORIGIN_Y = 7
private const val FRAME_PRESS = 8
private const val FRAME_PRESS_X = 9
private const val FRAME_PRESS_Y = 10

private const val VERTICES_PER_ITEM = 6
private const val VERTEX_FLOATS = 14
private const val VERTEX_STRIDE_BYTES = VERTEX_FLOATS * 4
private const val ITEM_VERTEX_FLOATS = VERTICES_PER_ITEM * VERTEX_FLOATS
private const val ITEM_VERTEX_BYTES = ITEM_VERTEX_FLOATS * 4
private const val FULL_VERTEX_FLOATS = MAX_BATCH_ITEMS * ITEM_VERTEX_FLOATS

private const val BATCH_REFERENCE_SHORT_EDGE_DP = 160f
private const val BATCH_MINIMUM_OPTICAL_SCALE = 0.28f
private const val BATCH_FRAME_EPSILON_PX = 0.01f
private const val BATCH_INTENSITY_EPSILON = 0.004f
private const val BATCH_PRESS_EPSILON = 0.002f
private const val BATCH_CLEAR_PADDING_PX = 4
private const val BATCH_EGL_SWAP_BEHAVIOR_VALUE = 0x3093
private const val BATCH_EGL_BUFFER_PRESERVED_VALUE = 0x3094
private const val BATCH_EGL_SWAP_BEHAVIOR_PRESERVED_BIT_VALUE = 0x0400

/**
 * 单张设置 Shell 的稳定描述。
 *
 * 几何保存在父级局部坐标中。页面滚动时父级和八张子卡一起移动，因此不再逐张执行
 * localToRoot；只更新一次父级根坐标即可得到全部卡片的真实背景采样原点。
 */
@Stable
internal class OpenGLShellBatchItem(
    val id: Any,
    val radiusDp: Int,
    val dynamicState: OpenGLGlassDynamicState,
    baseIntensity: Float,
) {
    private var latestCoordinates: LayoutCoordinates? = null
    private var parentCoordinates: GlassCoordinateSource? = null
    private val geometryListeners = CopyOnWriteArraySet<() -> Unit>()
    private val propertyListeners = CopyOnWriteArraySet<() -> Unit>()

    internal var localLeft = 0f
        private set
    internal var localTop = 0f
        private set
    internal var width = 0f
        private set
    internal var height = 0f
        private set
    internal var attached = false
        private set
    internal var baseIntensity = baseIntensity
        private set

    internal fun bindParent(source: GlassCoordinateSource?) {
        if (parentCoordinates === source) return
        parentCoordinates = source
        refreshPlacement()
    }

    internal fun updatePlacement(coordinates: LayoutCoordinates) {
        latestCoordinates = coordinates
        refreshPlacement()
    }

    internal fun updateBaseIntensity(value: Float) {
        val safe = value.coerceIn(0.35f, 1.35f)
        if (abs(baseIntensity - safe) <= BATCH_INTENSITY_EPSILON) return
        baseIntensity = safe
        propertyListeners.forEach { it() }
    }

    internal fun addGeometryListener(listener: () -> Unit): () -> Unit {
        geometryListeners += listener
        return { geometryListeners -= listener }
    }

    internal fun addPropertyListener(listener: () -> Unit): () -> Unit {
        propertyListeners += listener
        return { propertyListeners -= listener }
    }

    private fun refreshPlacement() {
        val item = latestCoordinates
        val parent = parentCoordinates?.coordinates
        val nextAttached = item?.isAttached == true && parent?.isAttached == true
        if (!nextAttached) {
            if (attached) {
                attached = false
                width = 0f
                height = 0f
                if (!OpenGLFrameFinalizer.requestActiveTickerFrame()) {
                    geometryListeners.forEach { it() }
                }
            }
            return
        }

        val local = parent!!.localPositionOf(item!!, Offset.Zero)
        val nextWidth = item.size.width.toFloat()
        val nextHeight = item.size.height.toFloat()
        val changed =
            !attached ||
                abs(localLeft - local.x) > BATCH_FRAME_EPSILON_PX ||
                abs(localTop - local.y) > BATCH_FRAME_EPSILON_PX ||
                abs(width - nextWidth) > BATCH_FRAME_EPSILON_PX ||
                abs(height - nextHeight) > BATCH_FRAME_EPSILON_PX

        attached = true
        localLeft = local.x
        localTop = local.y
        width = nextWidth
        height = nextHeight
        if (changed && !OpenGLFrameFinalizer.requestActiveTickerFrame()) {
            geometryListeners.forEach { it() }
        }
    }
}

@Stable
internal class OpenGLShellBatchState {
    private val entries = LinkedHashMap<Any, OpenGLShellBatchItem>()
    private var cachedSnapshot: List<OpenGLShellBatchItem> = emptyList()
    private val versionState = mutableLongStateOf(0L)
    private var parentCoordinates: GlassCoordinateSource? = null

    internal fun bindParent(source: GlassCoordinateSource) {
        if (parentCoordinates === source) return
        parentCoordinates = source
        entries.values.forEach { it.bindParent(source) }
    }

    fun register(item: OpenGLShellBatchItem) {
        if (entries[item.id] === item) return
        entries[item.id] = item
        item.bindParent(parentCoordinates)
        rebuildSnapshot()
    }

    fun remove(id: Any) {
        if (entries.remove(id) != null) rebuildSnapshot()
    }

    fun clear() {
        if (entries.isEmpty()) return
        entries.clear()
        cachedSnapshot = emptyList()
        bumpVersion()
    }

    fun snapshot(): List<OpenGLShellBatchItem> {
        versionState.longValue
        return cachedSnapshot
    }

    private fun rebuildSnapshot() {
        cachedSnapshot = entries.values.take(MAX_BATCH_ITEMS)
        bumpVersion()
    }

    private fun bumpVersion() {
        versionState.longValue = if (versionState.longValue == Long.MAX_VALUE) 1L else versionState.longValue + 1L
    }
}

internal val LocalOpenGLShellBatchState = staticCompositionLocalOf<OpenGLShellBatchState?> { null }

@Composable
internal fun rememberOpenGLShellBatchState(): OpenGLShellBatchState = remember { OpenGLShellBatchState() }

@Composable
internal fun NewOpenGLGlassBatchLayer(
    state: OpenGLShellBatchState,
    parentCoordinates: GlassCoordinateSource,
    modifier: Modifier = Modifier,
) {
    val items = state.snapshot()
    val backdrop = LocalBlurredBackdrop.current ?: return
    if (!backdrop.isReady || items.isEmpty()) return

    val baseBorder = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
    val styleOverride = LocalNewOpenGlGlassStyleOverride.current
    val border = remember(baseBorder, styleOverride) { styleOverride?.invoke(baseBorder) ?: baseBorder }
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    val density = LocalDensity.current
    val densityScale = density.density.coerceAtLeast(0.001f)
    val clearBitmap = remember(backdrop.lensImage) { backdrop.lensImage.asAndroidBitmap() }
    val blurLowBitmap = remember(backdrop.blurLowImage) { backdrop.blurLowImage.asAndroidBitmap() }
    val blurMediumBitmap = remember(backdrop.blurMediumImage) { backdrop.blurMediumImage.asAndroidBitmap() }
    val blurHighBitmap = remember(backdrop.blurHighImage) { backdrop.blurHighImage.asAndroidBitmap() }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { WebOpenGLGlassBatchHostView(it) },
            update = { view ->
                view.bindSources(items, parentCoordinates, backdropOrigin, frameTicker)
                view.setParentSpec(
                    width = widthPx,
                    height = heightPx,
                    rootWidth = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f),
                    rootHeight = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f),
                    densityScale = densityScale,
                    borderStyle = border,
                )
                view.setBackdropTextures(clearBitmap, blurLowBitmap, blurMediumBitmap, blurHighBitmap)
                view.setBackdropBlurAmount(backdrop.blurAmount)
            },
        )
    }
}

private class BatchPacket {
    val values = FloatArray(MAX_BATCH_ITEMS * FRAME_FLOATS)
    var activeMask = 0
    var dirtyMask = 0
    var fullDraw = true
    var clearAll = true
    var rootWidth = 1f
    var rootHeight = 1f
    var densityScale = 1f
    var style = GlassBorderStyle()
    var generation = 0L

    fun copyFrom(other: BatchPacket) {
        System.arraycopy(other.values, 0, values, 0, values.size)
        activeMask = other.activeMask
        dirtyMask = other.dirtyMask
        fullDraw = other.fullDraw
        clearAll = other.clearAll
        rootWidth = other.rootWidth
        rootHeight = other.rootHeight
        densityScale = other.densityScale
        style = other.style
        generation = other.generation
    }
}

private class WebOpenGLGlassBatchHostView(context: Context) : FrameLayout(context) {
    private val textureView = BatchTextureView(context)
    private val packet = BatchPacket()
    private var items: List<OpenGLShellBatchItem> = emptyList()
    private var parentCoordinates: GlassCoordinateSource? = null
    private var backdropOrigin: BackdropCoordinateSource? = null
    private var frameTicker: BackdropFrameTicker? = null
    private var parentWidth = 1f
    private var parentHeight = 1f
    private var rootWidth = 1f
    private var rootHeight = 1f
    private var densityScale = 1f
    private var borderStyle = GlassBorderStyle()
    private var scaledStyle = GlassBorderStyle()
    private var scaledStyleShortEdgeDp = -1f
    private var previousActiveMask = 0
    private var pendingDirtyMask = (1 shl MAX_BATCH_ITEMS) - 1
    private var geometryDirtyMask = (1 shl MAX_BATCH_ITEMS) - 1
    private var rootDirty = true
    private var fullDrawRequested = true
    private var clearAllRequested = true
    private var removeParent: (() -> Unit)? = null
    private var removeBackdrop: (() -> Unit)? = null
    private var removeTicker: (() -> Unit)? = null
    private val removeItemListeners = ArrayList<() -> Unit>()
    private var renderPosted = false

    private val renderRunnable = Runnable {
        renderPosted = false
        if (!isAttachedToWindow) return@Runnable
        if (syncPacket()) textureView.requestRender()
    }

    init {
        clipChildren = true
        clipToPadding = true
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bindSources(
        items: List<OpenGLShellBatchItem>,
        parentCoordinates: GlassCoordinateSource,
        backdropOrigin: BackdropCoordinateSource?,
        frameTicker: BackdropFrameTicker?,
    ) {
        if (
            this.items == items &&
            this.parentCoordinates === parentCoordinates &&
            this.backdropOrigin === backdropOrigin &&
            this.frameTicker === frameTicker
        ) return

        uninstallSubscriptions()
        this.items = items.take(MAX_BATCH_ITEMS)
        this.parentCoordinates = parentCoordinates
        this.backdropOrigin = backdropOrigin
        this.frameTicker = frameTicker
        pendingDirtyMask = (1 shl this.items.size) - 1
        geometryDirtyMask = pendingDirtyMask
        rootDirty = true
        fullDrawRequested = true
        clearAllRequested = true
        if (isAttachedToWindow) installSubscriptions()
        requestRenderOnNextAnimationFrame()
    }

    fun setParentSpec(
        width: Float,
        height: Float,
        rootWidth: Float,
        rootHeight: Float,
        densityScale: Float,
        borderStyle: GlassBorderStyle,
    ) {
        val surfaceChanged =
            abs(parentWidth - width) > BATCH_FRAME_EPSILON_PX ||
                abs(parentHeight - height) > BATCH_FRAME_EPSILON_PX
        val opticalChanged =
            abs(this.densityScale - densityScale) > 0.0001f ||
                this.borderStyle != borderStyle
        val rootSizeChanged =
            abs(this.rootWidth - rootWidth) > BATCH_FRAME_EPSILON_PX ||
                abs(this.rootHeight - rootHeight) > BATCH_FRAME_EPSILON_PX

        parentWidth = width.coerceAtLeast(1f)
        parentHeight = height.coerceAtLeast(1f)
        this.rootWidth = rootWidth.coerceAtLeast(1f)
        this.rootHeight = rootHeight.coerceAtLeast(1f)
        this.densityScale = densityScale.coerceAtLeast(0.001f)
        this.borderStyle = borderStyle

        if (surfaceChanged || rootSizeChanged) {
            rootDirty = true
            fullDrawRequested = true
            clearAllRequested = true
        }
        if (opticalChanged) {
            scaledStyleShortEdgeDp = -1f
            fullDrawRequested = true
        }
        if (surfaceChanged || rootSizeChanged || opticalChanged) requestRenderOnNextAnimationFrame()
    }

    fun setBackdropTextures(clear: Bitmap, low: Bitmap, medium: Bitmap, high: Bitmap) {
        if (textureView.setBackdropTextures(clear, low, medium, high)) {
            fullDrawRequested = true
            requestRenderOnNextAnimationFrame()
        }
    }

    fun setBackdropBlurAmount(amount: Float) {
        if (textureView.setBackdropBlurAmount(amount)) {
            fullDrawRequested = true
            requestRenderOnNextAnimationFrame()
        }
    }

    fun requestRenderOnNextAnimationFrame() {
        if (OpenGLFrameFinalizer.isDispatchingFrame) {
            if (renderPosted) {
                removeCallbacks(renderRunnable)
                renderPosted = false
            }
            if (isAttachedToWindow && syncPacket()) textureView.requestRender()
            return
        }
        if (renderPosted) return
        renderPosted = true
        postOnAnimation(renderRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        installSubscriptions()
        requestRenderOnNextAnimationFrame()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(renderRunnable)
        renderPosted = false
        uninstallSubscriptions()
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        textureView.layout(0, 0, right - left, bottom - top)
        if (changed) {
            clearAllRequested = true
            fullDrawRequested = true
        }
        requestRenderOnNextAnimationFrame()
    }

    private fun installSubscriptions() {
        if (removeParent != null || removeBackdrop != null || removeTicker != null || removeItemListeners.isNotEmpty()) return
        removeParent = parentCoordinates?.addPlacementListener {
            rootDirty = true
            fullDrawRequested = true
            requestRenderOnNextAnimationFrame()
        }
        removeBackdrop = backdropOrigin?.addPlacementListener {
            rootDirty = true
            fullDrawRequested = true
            requestRenderOnNextAnimationFrame()
        }
        removeTicker = frameTicker?.addFrameListener {
            rootDirty = true
            fullDrawRequested = true
            requestRenderOnNextAnimationFrame()
        }
        items.forEachIndexed { index, item ->
            val bit = 1 shl index
            removeItemListeners += item.addGeometryListener {
                geometryDirtyMask = geometryDirtyMask or bit
                pendingDirtyMask = pendingDirtyMask or bit
                requestRenderOnNextAnimationFrame()
            }
            removeItemListeners += item.addPropertyListener {
                pendingDirtyMask = pendingDirtyMask or bit
                requestRenderOnNextAnimationFrame()
            }
            removeItemListeners += item.dynamicState.addFrameListener {
                pendingDirtyMask = pendingDirtyMask or bit
                requestRenderOnNextAnimationFrame()
            }
        }
    }

    private fun uninstallSubscriptions() {
        removeParent?.invoke()
        removeBackdrop?.invoke()
        removeTicker?.invoke()
        removeParent = null
        removeBackdrop = null
        removeTicker = null
        removeItemListeners.forEach { it.invoke() }
        removeItemListeners.clear()
    }

    private fun syncPacket(): Boolean {
        if (
            !rootDirty &&
            !fullDrawRequested &&
            !clearAllRequested &&
            pendingDirtyMask == 0 &&
            geometryDirtyMask == 0
        ) return false

        val parentRoot = parentCoordinates?.rootOffsetNow() ?: Offset.Zero
        val backdropRoot = backdropOrigin?.rootOffsetNow() ?: Offset.Zero
        val density = densityScale.coerceAtLeast(0.001f)
        var activeMask = 0
        var shortEdgeDp = 0f

        for (index in 0 until MAX_BATCH_ITEMS) {
            val base = index * FRAME_FLOATS
            if (index >= items.size) {
                clearFrame(packet.values, base)
                continue
            }
            val item = items[index]
            if (!item.attached || item.width <= 1f || item.height <= 1f) {
                clearFrame(packet.values, base)
                continue
            }

            val dynamic = item.dynamicState.latestSnapshot()
            val centerX = dynamic.pressCenter.x.coerceIn(0f, 1f)
            val centerY = dynamic.pressCenter.y.coerceIn(0f, 1f)
            val scaleX = 1f + dynamic.pressCompression * 0.014f - dynamic.pressRebound * 0.004f
            val scaleY = 1f - dynamic.pressCompression * 0.022f + dynamic.pressRebound * 0.008f
            val translationY = dynamic.pressCompression * 2.10f - dynamic.pressRebound * 0.80f
            val transformedWidth = (item.width * scaleX).coerceAtLeast(1f)
            val transformedHeight = (item.height * scaleY).coerceAtLeast(1f)
            val transformedLeft = item.localLeft + (1f - scaleX) * centerX * item.width
            val transformedTop = item.localTop + (1f - scaleY) * centerY * item.height + translationY
            val globalLeft = parentRoot.x + transformedLeft
            val globalTop = parentRoot.y + transformedTop
            val globallyVisible =
                globalLeft + transformedWidth > 0f &&
                    globalLeft < rootWidth &&
                    globalTop + transformedHeight > 0f &&
                    globalTop < rootHeight

            if (globallyVisible) activeMask = activeMask or (1 shl index)
            if (shortEdgeDp <= 0f) shortEdgeDp = min(item.width, item.height) / density

            packet.values[base + FRAME_LEFT] = transformedLeft
            packet.values[base + FRAME_TOP] = transformedTop
            packet.values[base + FRAME_WIDTH] = transformedWidth
            packet.values[base + FRAME_HEIGHT] = transformedHeight
            packet.values[base + FRAME_RADIUS] = item.radiusDp * density * min(scaleX, scaleY)
            packet.values[base + FRAME_INTENSITY] =
                (item.baseIntensity * dynamic.glassIntensityScale).coerceIn(0.35f, 1.35f)
            packet.values[base + FRAME_ORIGIN_X] = globalLeft - backdropRoot.x
            packet.values[base + FRAME_ORIGIN_Y] = globalTop - backdropRoot.y
            packet.values[base + FRAME_PRESS] = dynamic.openGlPress.coerceIn(0f, 1f)
            packet.values[base + FRAME_PRESS_X] = centerX
            packet.values[base + FRAME_PRESS_Y] = centerY
        }

        val safeOpticalScale = (shortEdgeDp / BATCH_REFERENCE_SHORT_EDGE_DP)
            .coerceIn(BATCH_MINIMUM_OPTICAL_SCALE, 1f)
        val styleChanged =
            scaledStyleShortEdgeDp < 0f ||
                abs(scaledStyleShortEdgeDp - shortEdgeDp) > 0.001f
        if (styleChanged) {
            scaledStyleShortEdgeDp = shortEdgeDp
            scaledStyle = borderStyle.scaleBatchOpticalDistances(safeOpticalScale)
            fullDrawRequested = true
        }

        val activeMaskChanged = activeMask != previousActiveMask
        if (activeMaskChanged) clearAllRequested = true
        packet.activeMask = activeMask
        packet.fullDraw = fullDrawRequested || rootDirty
        packet.dirtyMask = if (packet.fullDraw) {
            activeMask or previousActiveMask
        } else {
            (pendingDirtyMask or geometryDirtyMask) and (activeMask or previousActiveMask)
        }
        packet.clearAll = clearAllRequested
        packet.rootWidth = rootWidth
        packet.rootHeight = rootHeight
        packet.densityScale = density
        packet.style = scaledStyle
        packet.generation = if (packet.generation == Long.MAX_VALUE) 1L else packet.generation + 1L

        textureView.setPacket(packet)
        previousActiveMask = activeMask
        pendingDirtyMask = 0
        geometryDirtyMask = 0
        rootDirty = false
        fullDrawRequested = false
        clearAllRequested = false
        return packet.fullDraw || packet.dirtyMask != 0 || packet.clearAll
    }

    private fun clearFrame(values: FloatArray, base: Int) {
        for (offset in 0 until FRAME_FLOATS) values[base + offset] = 0f
    }
}

private class BatchTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var thread: BatchEglThread? = null
    private val latestPacket = BatchPacket()
    private var clear: Bitmap? = null
    private var low: Bitmap? = null
    private var medium: Bitmap? = null
    private var high: Bitmap? = null
    private var blurAmount = 0f

    init {
        isOpaque = false
        alpha = 1f
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setPacket(packet: BatchPacket) {
        latestPacket.copyFrom(packet)
        thread?.setPacket(packet)
    }

    fun setBackdropTextures(clear: Bitmap, low: Bitmap, medium: Bitmap, high: Bitmap): Boolean {
        val changed = clear !== this.clear || low !== this.low || medium !== this.medium || high !== this.high
        this.clear = clear
        this.low = low
        this.medium = medium
        this.high = high
        if (changed) {
            PerformanceRuntimeMetrics.recordOpenGlTextureUpload(clear.width, clear.height)
            PerformanceRuntimeMetrics.recordOpenGlTextureUpload(low.width, low.height)
            PerformanceRuntimeMetrics.recordOpenGlTextureUpload(medium.width, medium.height)
            if (high !== medium) PerformanceRuntimeMetrics.recordOpenGlTextureUpload(high.width, high.height)
            thread?.setBackdropTextures(clear, low, medium, high)
        }
        return changed
    }

    fun setBackdropBlurAmount(amount: Float): Boolean {
        val safe = amount.coerceIn(0f, 4f)
        if (abs(safe - blurAmount) <= 0.002f) return false
        blurAmount = safe
        thread?.setBackdropBlurAmount(safe)
        return true
    }

    fun requestRender() = thread?.requestRender() ?: Unit

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        thread?.shutdown()
        thread = BatchEglThread(
            surface = Surface(surfaceTexture),
            width = width,
            height = height,
            onFirstFrame = StartupPerformanceGate::markOpenGlFirstFrameReady,
        ).also { next ->
            next.setPacket(latestPacket)
            next.setBackdropBlurAmount(blurAmount)
            val clear = clear
            val low = low
            val medium = medium
            val high = high
            if (clear != null && low != null && medium != null && high != null) {
                next.setBackdropTextures(clear, low, medium, high)
            }
            next.start()
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        thread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        thread?.shutdown()
        thread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
}

private class BatchEglThread(
    private val surface: Surface,
    width: Int,
    height: Int,
    private val onFirstFrame: () -> Unit,
) : Thread("WebOpenGLGlassBatchTextureThread") {
    private val renderer = BatchRenderer()
    private val renderLock = Object()
    @Volatile private var running = true
    @Volatile private var pendingRender = true
    @Volatile private var viewportWidth = max(width, 1)
    @Volatile private var viewportHeight = max(height, 1)
    @Volatile private var sizeDirty = true
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var firstFramePresented = false
    private var metricsContextActive = false

    fun setPacket(packet: BatchPacket) = renderer.setPacket(packet)
    fun setBackdropTextures(clear: Bitmap, low: Bitmap, medium: Bitmap, high: Bitmap) =
        renderer.setBackdropTextures(clear, low, medium, high)
    fun setBackdropBlurAmount(amount: Float) = renderer.setBackdropBlurAmount(amount)

    fun requestRender() {
        synchronized(renderLock) {
            if (running && !pendingRender) {
                pendingRender = true
                PerformanceRuntimeMetrics.recordOpenGlRenderRequest()
            }
            renderLock.notifyAll()
        }
    }

    fun resize(width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
        sizeDirty = true
        requestRender()
    }

    fun shutdown() {
        running = false
        synchronized(renderLock) { renderLock.notifyAll() }
    }

    override fun run() {
        try {
            initEgl()
            renderer.onSurfaceCreated()
            renderer.onSurfaceChanged(viewportWidth, viewportHeight)
            PerformanceRuntimeMetrics.recordOpenGlSurface(viewportWidth, viewportHeight)
            sizeDirty = false
            while (running) {
                synchronized(renderLock) {
                    while (!pendingRender && running) renderLock.wait()
                    pendingRender = false
                }
                if (!running) break
                if (sizeDirty) {
                    renderer.onSurfaceChanged(viewportWidth, viewportHeight)
                    PerformanceRuntimeMetrics.recordOpenGlSurface(viewportWidth, viewportHeight)
                    sizeDirty = false
                }
                renderer.onDrawFrame()
                if (EGL14.eglSwapBuffers(display, eglSurface)) {
                    PerformanceRuntimeMetrics.recordOpenGlFrame()
                    if (!firstFramePresented) {
                        firstFramePresented = true
                        onFirstFrame()
                    }
                }
            }
        } finally {
            runCatching { renderer.onRelease() }
            releaseEgl()
            surface.release()
        }
    }

    private fun initEgl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1))
        val preservedConfig = chooseConfig(EGL14.EGL_WINDOW_BIT or BATCH_EGL_SWAP_BEHAVIOR_PRESERVED_BIT_VALUE)
        val config = preservedConfig ?: chooseConfig(EGL14.EGL_WINDOW_BIT) ?: error("No EGL config")
        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0,
        )
        check(context != EGL14.EGL_NO_CONTEXT)
        eglSurface = EGL14.eglCreateWindowSurface(
            display,
            config,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0,
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context))
        metricsContextActive = true
        PerformanceRuntimeMetrics.recordOpenGlContextCreated()

        val preserved = preservedConfig != null && EGL14.eglSurfaceAttrib(
            display,
            eglSurface,
            BATCH_EGL_SWAP_BEHAVIOR_VALUE,
            BATCH_EGL_BUFFER_PRESERVED_VALUE,
        )
        renderer.setBufferPreserved(preserved)
    }

    private fun chooseConfig(surfaceType: Int): EGLConfig? {
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, surfaceType,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val success = EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)
        return if (success && count[0] > 0) configs[0] else null
    }

    private fun releaseEgl() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        if (metricsContextActive) {
            metricsContextActive = false
            PerformanceRuntimeMetrics.recordOpenGlContextReleased()
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}

private class BatchRenderer {
    private val packetLock = Any()
    private val textureLock = Any()
    private val pendingPacket = BatchPacket()
    private val drawPacket = BatchPacket()
    private val presentedPacket = BatchPacket()
    private var packetPending = false
    private var pendingBlur = 0f
    private var drawBlur = 0f
    private var pendingClear: Bitmap? = null
    private var pendingLow: Bitmap? = null
    private var pendingMedium: Bitmap? = null
    private var pendingHigh: Bitmap? = null
    private var texturesPending = false
    private var activeClear: Bitmap? = null
    private var activeLow: Bitmap? = null
    private var activeMedium: Bitmap? = null
    private var activeHigh: Bitmap? = null
    private var highAliasesMedium = false
    private var clearTexture = 0
    private var lowTexture = 0
    private var mediumTexture = 0
    private var highTexture = 0
    private val textureWidths = IntArray(4)
    private val textureHeights = IntArray(4)
    private var textureReady = false
    private var bufferPreserved = false
    private var firstFrame = true
    private var surfaceDirty = true

    private var program = 0
    private var vertexBufferObject = 0
    private var positionHandle = 0
    private var rectHandle = 0
    private var cardHandle = 0
    private var pressHandle = 0
    private var resolutionHandle = 0
    private var rootResolutionHandle = 0
    private var textureReadyHandle = 0
    private var blurHandle = 0
    private var clearHandle = 0
    private var lowHandle = 0
    private var mediumHandle = 0
    private var highHandle = 0
    private var materialHandle = 0
    private var bodyLensAHandle = 0
    private var bodyLensBHandle = 0
    private var bodyHandle = 0
    private var shoulderHandle = 0
    private var shoulderFlowHandle = 0
    private var dispersionHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var uploadedStyle: GlassBorderStyle? = null
    private var uploadedDensity = -1f

    private val fullVertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(FULL_VERTEX_FLOATS * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val itemVertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(ITEM_VERTEX_FLOATS * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    fun setBufferPreserved(value: Boolean) {
        bufferPreserved = value
    }

    fun setPacket(packet: BatchPacket) {
        synchronized(packetLock) {
            pendingPacket.copyFrom(packet)
            packetPending = true
        }
    }

    fun setBackdropBlurAmount(amount: Float) {
        synchronized(packetLock) { pendingBlur = amount.coerceIn(0f, 4f) }
    }

    fun setBackdropTextures(clear: Bitmap, low: Bitmap, medium: Bitmap, high: Bitmap) {
        synchronized(textureLock) {
            pendingClear = clear
            pendingLow = low
            pendingMedium = medium
            pendingHigh = high
            texturesPending = true
        }
    }

    fun onSurfaceCreated() {
        program = buildProgram(
            WebOpenGLGlassBatchShaders.VERTEX_SHADER,
            WebOpenGLGlassBatchShaders.FRAGMENT_SHADER,
        )
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        rectHandle = GLES20.glGetAttribLocation(program, "aRect")
        cardHandle = GLES20.glGetAttribLocation(program, "aCard")
        pressHandle = GLES20.glGetAttribLocation(program, "aPress")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        rootResolutionHandle = GLES20.glGetUniformLocation(program, "uRootResolution")
        textureReadyHandle = GLES20.glGetUniformLocation(program, "uTextureReady")
        blurHandle = GLES20.glGetUniformLocation(program, "uBlurAmount")
        clearHandle = GLES20.glGetUniformLocation(program, "uClearTexture")
        lowHandle = GLES20.glGetUniformLocation(program, "uBlurLowTexture")
        mediumHandle = GLES20.glGetUniformLocation(program, "uBlurMediumTexture")
        highHandle = GLES20.glGetUniformLocation(program, "uBlurHighTexture")
        materialHandle = GLES20.glGetUniformLocation(program, "uMaterial")
        bodyLensAHandle = GLES20.glGetUniformLocation(program, "uBodyLensA")
        bodyLensBHandle = GLES20.glGetUniformLocation(program, "uBodyLensB")
        bodyHandle = GLES20.glGetUniformLocation(program, "uBody")
        shoulderHandle = GLES20.glGetUniformLocation(program, "uShoulder")
        shoulderFlowHandle = GLES20.glGetUniformLocation(program, "uShoulderFlow")
        dispersionHandle = GLES20.glGetUniformLocation(program, "uDispersion")

        GLES20.glUseProgram(program)
        GLES20.glUniform1i(clearHandle, 0)
        GLES20.glUniform1i(lowHandle, 1)
        GLES20.glUniform1i(mediumHandle, 2)
        GLES20.glUniform1i(highHandle, 3)
        GLES20.glUniform1f(textureReadyHandle, 0f)
        clearTexture = createTexture(0, GLES20.GL_TEXTURE0)
        lowTexture = createTexture(1, GLES20.GL_TEXTURE1)
        mediumTexture = createTexture(2, GLES20.GL_TEXTURE2)

        val buffers = IntArray(1)
        GLES20.glGenBuffers(1, buffers, 0)
        vertexBufferObject = buffers[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferObject)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            FULL_VERTEX_FLOATS * 4,
            null,
            GLES20.GL_DYNAMIC_DRAW,
        )
        bindVertexAttributes()
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        surfaceDirty = true
    }

    fun onDrawFrame() {
        val texturesChanged = uploadTexturesIfNeeded()
        consumePendingPacket()
        if (program == 0) return

        GLES20.glUseProgram(program)
        bindVertexAttributes()
        GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES20.glUniform2f(rootResolutionHandle, drawPacket.rootWidth, drawPacket.rootHeight)
        GLES20.glUniform1f(blurHandle, drawBlur)
        uploadStyleIfNeeded(drawPacket.style, drawPacket.densityScale)

        val fullDraw =
            !bufferPreserved ||
                firstFrame ||
                surfaceDirty ||
                texturesChanged ||
                drawPacket.fullDraw
        val clearAll =
            !bufferPreserved ||
                firstFrame ||
                surfaceDirty ||
                drawPacket.clearAll

        if (clearAll) clearEntireSurface()

        if (fullDraw) {
            uploadFullVertexBuffer(drawPacket)
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
            GLES20.glDrawArrays(
                GLES20.GL_TRIANGLES,
                0,
                MAX_BATCH_ITEMS * VERTICES_PER_ITEM,
            )
        } else {
            var dirty = drawPacket.dirtyMask
            var index = 0
            while (dirty != 0 && index < MAX_BATCH_ITEMS) {
                val bit = 1 shl index
                if ((dirty and bit) != 0) {
                    clearItemUnion(index)
                    uploadItemVertexBuffer(drawPacket, index)
                    if ((drawPacket.activeMask and bit) != 0) {
                        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
                        GLES20.glDrawArrays(
                            GLES20.GL_TRIANGLES,
                            index * VERTICES_PER_ITEM,
                            VERTICES_PER_ITEM,
                        )
                    }
                    dirty = dirty and bit.inv()
                }
                index += 1
            }
        }

        presentedPacket.copyFrom(drawPacket)
        firstFrame = false
        surfaceDirty = false
    }

    fun onRelease() {
        deleteTexture(clearTexture)
        deleteTexture(lowTexture)
        deleteTexture(mediumTexture)
        deleteTexture(highTexture)
        if (vertexBufferObject != 0) GLES20.glDeleteBuffers(1, intArrayOf(vertexBufferObject), 0)
        if (program != 0) GLES20.glDeleteProgram(program)
        vertexBufferObject = 0
        program = 0
    }

    private fun consumePendingPacket() {
        synchronized(packetLock) {
            if (packetPending) {
                drawPacket.copyFrom(pendingPacket)
                packetPending = false
            }
            drawBlur = pendingBlur
        }
    }

    private fun clearEntireSurface() {
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        PerformanceRuntimeMetrics.recordOpenGlFullClear(viewportWidth, viewportHeight)
    }

    private fun clearItemUnion(index: Int) {
        val bit = 1 shl index
        val oldActive = (presentedPacket.activeMask and bit) != 0
        val newActive = (drawPacket.activeMask and bit) != 0
        if (!oldActive && !newActive) return

        val oldBase = index * FRAME_FLOATS
        val newBase = index * FRAME_FLOATS
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY

        if (oldActive) {
            left = min(left, presentedPacket.values[oldBase + FRAME_LEFT])
            top = min(top, presentedPacket.values[oldBase + FRAME_TOP])
            right = max(
                right,
                presentedPacket.values[oldBase + FRAME_LEFT] + presentedPacket.values[oldBase + FRAME_WIDTH],
            )
            bottom = max(
                bottom,
                presentedPacket.values[oldBase + FRAME_TOP] + presentedPacket.values[oldBase + FRAME_HEIGHT],
            )
        }
        if (newActive) {
            left = min(left, drawPacket.values[newBase + FRAME_LEFT])
            top = min(top, drawPacket.values[newBase + FRAME_TOP])
            right = max(right, drawPacket.values[newBase + FRAME_LEFT] + drawPacket.values[newBase + FRAME_WIDTH])
            bottom = max(bottom, drawPacket.values[newBase + FRAME_TOP] + drawPacket.values[newBase + FRAME_HEIGHT])
        }

        val scissorLeft = (floor(left.toDouble()).toInt() - BATCH_CLEAR_PADDING_PX).coerceIn(0, viewportWidth)
        val scissorTop = (floor(top.toDouble()).toInt() - BATCH_CLEAR_PADDING_PX).coerceIn(0, viewportHeight)
        val scissorRight = (ceil(right.toDouble()).toInt() + BATCH_CLEAR_PADDING_PX).coerceIn(0, viewportWidth)
        val scissorBottom = (ceil(bottom.toDouble()).toInt() + BATCH_CLEAR_PADDING_PX).coerceIn(0, viewportHeight)
        if (scissorRight <= scissorLeft || scissorBottom <= scissorTop) return

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(
            scissorLeft,
            viewportHeight - scissorBottom,
            scissorRight - scissorLeft,
            scissorBottom - scissorTop,
        )
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    private fun uploadFullVertexBuffer(packet: BatchPacket) {
        fullVertexBuffer.clear()
        for (index in 0 until MAX_BATCH_ITEMS) {
            writeItemVertices(fullVertexBuffer, packet, index)
        }
        fullVertexBuffer.flip()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferObject)
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER,
            0,
            fullVertexBuffer.remaining() * 4,
            fullVertexBuffer,
        )
    }

    private fun uploadItemVertexBuffer(packet: BatchPacket, index: Int) {
        itemVertexBuffer.clear()
        writeItemVertices(itemVertexBuffer, packet, index)
        itemVertexBuffer.flip()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferObject)
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER,
            index * ITEM_VERTEX_BYTES,
            itemVertexBuffer.remaining() * 4,
            itemVertexBuffer,
        )
    }

    private fun writeItemVertices(buffer: FloatBuffer, packet: BatchPacket, index: Int) {
        val bit = 1 shl index
        if ((packet.activeMask and bit) == 0) {
            repeat(ITEM_VERTEX_FLOATS) { buffer.put(0f) }
            return
        }

        val base = index * FRAME_FLOATS
        val left = packet.values[base + FRAME_LEFT]
        val top = packet.values[base + FRAME_TOP]
        val right = left + packet.values[base + FRAME_WIDTH]
        val bottom = top + packet.values[base + FRAME_HEIGHT]
        putVertex(buffer, left, top, packet.values, base)
        putVertex(buffer, right, top, packet.values, base)
        putVertex(buffer, left, bottom, packet.values, base)
        putVertex(buffer, left, bottom, packet.values, base)
        putVertex(buffer, right, top, packet.values, base)
        putVertex(buffer, right, bottom, packet.values, base)
    }

    private fun putVertex(
        buffer: FloatBuffer,
        x: Float,
        y: Float,
        values: FloatArray,
        base: Int,
    ) {
        buffer.put(x)
        buffer.put(y)
        buffer.put(values[base + FRAME_LEFT])
        buffer.put(values[base + FRAME_TOP])
        buffer.put(values[base + FRAME_WIDTH])
        buffer.put(values[base + FRAME_HEIGHT])
        buffer.put(values[base + FRAME_ORIGIN_X])
        buffer.put(values[base + FRAME_ORIGIN_Y])
        buffer.put(values[base + FRAME_RADIUS])
        buffer.put(values[base + FRAME_INTENSITY])
        buffer.put(values[base + FRAME_PRESS])
        buffer.put(values[base + FRAME_PRESS_X])
        buffer.put(values[base + FRAME_PRESS_Y])
        buffer.put(0f)
    }

    private fun bindVertexAttributes() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferObject)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(rectHandle)
        GLES20.glEnableVertexAttribArray(cardHandle)
        GLES20.glEnableVertexAttribArray(pressHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, VERTEX_STRIDE_BYTES, 0)
        GLES20.glVertexAttribPointer(rectHandle, 4, GLES20.GL_FLOAT, false, VERTEX_STRIDE_BYTES, 2 * 4)
        GLES20.glVertexAttribPointer(cardHandle, 4, GLES20.GL_FLOAT, false, VERTEX_STRIDE_BYTES, 6 * 4)
        GLES20.glVertexAttribPointer(pressHandle, 4, GLES20.GL_FLOAT, false, VERTEX_STRIDE_BYTES, 10 * 4)
    }

    private fun uploadStyleIfNeeded(style: GlassBorderStyle, densityScale: Float) {
        if (uploadedStyle == style && abs(uploadedDensity - densityScale) <= 0.0001f) return
        uploadedStyle = style
        uploadedDensity = densityScale
        val density = densityScale.coerceAtLeast(0.1f)
        GLES20.glUniform4f(
            materialHandle,
            style.newOpenGlBodyVisibility.coerceIn(0f, 20f),
            style.newOpenGlBodyMaxAlpha.coerceIn(0f, 1f),
            style.newOpenGlBodyOutputBrightness.coerceIn(0.2f, 2.8f),
            0f,
        )
        GLES20.glUniform4f(
            bodyLensAHandle,
            style.newOpenGlBodyLensBasePull.coerceIn(-300f, 300f) * density,
            style.newOpenGlBodyLensPullDp.coerceIn(-600f, 600f) * density,
            style.newOpenGlBodyLensConcentration.coerceIn(-10f, 10f),
            0f,
        )
        GLES20.glUniform4f(
            bodyLensBHandle,
            style.newOpenGlBodyLensExtraDistance.coerceIn(0f, 200f) * density,
            style.newOpenGlBodyLensReachDp.coerceIn(8f, 180f) * density,
            style.newOpenGlBodyLensDark.coerceIn(-10f, 10f),
            style.newOpenGlBodyLensDebug.coerceIn(0f, 1f),
        )
        GLES20.glUniform4f(
            bodyHandle,
            style.newOpenGlBodyWidth.coerceIn(0.18f, 1.5f),
            style.newOpenGlBodyCurve.coerceIn(0.2f, 3.2f),
            style.newOpenGlBodyGain.coerceIn(0f, 900f),
            style.newOpenGlBrightness.coerceIn(0.4f, 2.2f),
        )
        GLES20.glUniform4f(
            shoulderHandle,
            style.newOpenGlShoulderWidthDp.coerceIn(4f, 96f) * density,
            style.newOpenGlShoulderMaxAngleDeg.coerceIn(0f, 89.5f),
            style.newOpenGlShoulderFalloffRoundness.coerceIn(0f, 1f),
            style.newOpenGlShoulderMaterialStrength.coerceIn(0f, 4f),
        )
        GLES20.glUniform2f(
            shoulderFlowHandle,
            style.newOpenGlShoulderCaptureWidthDp.coerceIn(4f, 192f) * density,
            style.newOpenGlShoulderTangentialFlowStrength.coerceIn(0f, 2.4f),
        )
        GLES20.glUniform4f(
            dispersionHandle,
            style.newOpenGlDispersionStrength.coerceIn(0f, 1.5f),
            style.newOpenGlDispersionDistanceDp.coerceIn(0f, 8f) * density,
            style.newOpenGlDispersionEdgeWidthDp.coerceIn(2f, 64f) * density,
            style.newOpenGlDispersionConcentration.coerceIn(0.25f, 4f),
        )
    }

    private fun uploadTexturesIfNeeded(): Boolean {
        val clear: Bitmap
        val low: Bitmap
        val medium: Bitmap
        val high: Bitmap
        synchronized(textureLock) {
            if (!texturesPending) return false
            clear = pendingClear ?: return false
            low = pendingLow ?: return false
            medium = pendingMedium ?: return false
            high = pendingHigh ?: return false
            pendingClear = null
            pendingLow = null
            pendingMedium = null
            pendingHigh = null
            texturesPending = false
        }

        var changed = false
        if (clear !== activeClear) {
            uploadTexture(0, GLES20.GL_TEXTURE0, clearTexture, clear)
            activeClear = clear
            changed = true
        }
        if (low !== activeLow) {
            uploadTexture(1, GLES20.GL_TEXTURE1, lowTexture, low)
            activeLow = low
            changed = true
        }
        if (medium !== activeMedium) {
            uploadTexture(2, GLES20.GL_TEXTURE2, mediumTexture, medium)
            activeMedium = medium
            changed = true
        }
        if (high === medium) {
            if (!highAliasesMedium || highTexture != 0) {
                deleteTexture(highTexture)
                highTexture = 0
                bindTexture(GLES20.GL_TEXTURE3, mediumTexture)
                highAliasesMedium = true
                changed = true
            }
            activeHigh = high
        } else {
            if (highTexture == 0) {
                highTexture = createTexture(3, GLES20.GL_TEXTURE3)
                changed = true
            } else if (highAliasesMedium) {
                bindTexture(GLES20.GL_TEXTURE3, highTexture)
            }
            highAliasesMedium = false
            if (high !== activeHigh) {
                uploadTexture(3, GLES20.GL_TEXTURE3, highTexture, high)
                activeHigh = high
                changed = true
            }
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (!textureReady) {
            textureReady = true
            GLES20.glUniform1f(textureReadyHandle, 1f)
            changed = true
        }
        return changed
    }

    private fun uploadTexture(index: Int, unit: Int, texture: Int, bitmap: Bitmap) {
        bindTexture(unit, texture)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        if (textureWidths[index] == bitmap.width && textureHeights[index] == bitmap.height) {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        } else {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            textureWidths[index] = bitmap.width
            textureHeights[index] = bitmap.height
        }
    }

    private fun createTexture(index: Int, unit: Int): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texture = textures[0]
        bindTexture(unit, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        textureWidths[index] = 0
        textureHeights[index] = 0
        return texture
    }

    private fun bindTexture(unit: Int, texture: Int) {
        GLES20.glActiveTexture(unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
    }

    private fun deleteTexture(texture: Int) {
        if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
    }
}

private fun GlassBorderStyle.scaleBatchOpticalDistances(scale: Float): GlassBorderStyle {
    val safeScale = scale.coerceIn(BATCH_MINIMUM_OPTICAL_SCALE, 1f)
    if (safeScale >= 0.999f) return this
    return copy(
        newOpenGlBodyLensBasePull = newOpenGlBodyLensBasePull * safeScale,
        newOpenGlBodyLensPullDp = newOpenGlBodyLensPullDp * safeScale,
        newOpenGlBodyLensExtraDistance = newOpenGlBodyLensExtraDistance * safeScale,
        newOpenGlBodyLensReachDp = newOpenGlBodyLensReachDp * safeScale,
        newOpenGlShoulderWidthDp = newOpenGlShoulderWidthDp * safeScale,
        newOpenGlShoulderCaptureWidthDp = newOpenGlShoulderCaptureWidthDp * safeScale,
        newOpenGlDispersionDistanceDp = newOpenGlDispersionDistanceDp * safeScale,
        newOpenGlDispersionEdgeWidthDp = newOpenGlDispersionEdgeWidthDp * safeScale,
    )
}

private fun buildProgram(vertex: String, fragment: String): Int {
    fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    val vertexShader = compile(GLES20.GL_VERTEX_SHADER, vertex)
    val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vertexShader)
    GLES20.glAttachShader(program, fragmentShader)
    GLES20.glLinkProgram(program)
    val status = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
    GLES20.glDeleteShader(vertexShader)
    GLES20.glDeleteShader(fragmentShader)
    check(status[0] != 0) { GLES20.glGetProgramInfoLog(program) }
    return program
}
