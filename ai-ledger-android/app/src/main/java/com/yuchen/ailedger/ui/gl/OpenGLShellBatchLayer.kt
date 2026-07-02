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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.localToRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.BackdropCoordinateSource
import com.yuchen.ailedger.ui.BackdropFrameTicker
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.LocalBackdropFrameTicker
import com.yuchen.ailedger.ui.LocalBackdropOrigin
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassBackdrop
import com.yuchen.ailedger.ui.PerformanceRuntimeMetrics
import com.yuchen.ailedger.ui.StartupPerformanceGate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val BATCH_REFERENCE_SHORT_EDGE_DP = 160f
private const val BATCH_MINIMUM_OPTICAL_SCALE = 0.28f
private const val BATCH_SCISSOR_PADDING_PX = 2
private const val BATCH_FRAME_EPSILON_PX = 0.35f
private const val BATCH_INTENSITY_EPSILON = 0.004f
private const val BATCH_PRESS_EPSILON = 0.002f

private const val BATCH_EGL_SWAP_BEHAVIOR_VALUE = 0x3093
private const val BATCH_EGL_BUFFER_PRESERVED_VALUE = 0x3094
private const val BATCH_EGL_SWAP_BEHAVIOR_PRESERVED_BIT_VALUE = 0x0400

@Immutable
internal data class OpenGLShellBatchItem(
    val id: Any,
    val coordinates: GlassCoordinateSource,
    val radiusDp: Int,
    val baseIntensity: Float,
    val dynamicState: OpenGLGlassDynamicState,
)

@Stable
internal class OpenGLShellBatchState {
    private val entries = LinkedHashMap<Any, OpenGLShellBatchItem>()
    private var cachedSnapshot: List<OpenGLShellBatchItem> = emptyList()

    var version by mutableLongStateOf(0L)
        private set

    fun upsert(item: OpenGLShellBatchItem) {
        if (entries[item.id] == item) return
        entries[item.id] = item
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
        version
        return cachedSnapshot
    }

    private fun rebuildSnapshot() {
        cachedSnapshot = entries.values.toList()
        bumpVersion()
    }

    private fun bumpVersion() {
        version = if (version == Long.MAX_VALUE) 1L else version + 1L
    }
}

internal val LocalOpenGLShellBatchState =
    staticCompositionLocalOf<OpenGLShellBatchState?> { null }

@Composable
internal fun rememberOpenGLShellBatchState(): OpenGLShellBatchState =
    remember { OpenGLShellBatchState() }

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
    val border = remember(baseBorder, styleOverride) {
        styleOverride?.invoke(baseBorder) ?: baseBorder
    }
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
            factory = { context -> WebOpenGLGlassBatchHostView(context) },
            update = { view ->
                view.bindSources(
                    items = items,
                    parentCoordinates = parentCoordinates,
                    backdropOrigin = backdropOrigin,
                    frameTicker = frameTicker,
                )
                view.setParentSpec(
                    width = widthPx,
                    height = heightPx,
                    rootWidth = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f),
                    rootHeight = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f),
                    densityScale = densityScale,
                    borderStyle = border,
                )
                val textureDirty = view.setBackdropTextures(
                    clearBitmap = clearBitmap,
                    blurLowBitmap = blurLowBitmap,
                    blurMediumBitmap = blurMediumBitmap,
                    blurHighBitmap = blurHighBitmap,
                )
                val blurDirty = view.setBackdropBlurAmount(backdrop.blurAmount)
                if (textureDirty || blurDirty) view.requestRenderOnNextAnimationFrame()
            },
        )
    }
}

@Immutable
private data class WebOpenGLGlassBatchFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val radius: Float,
    val intensity: Float,
    val originX: Float,
    val originY: Float,
    val rootWidth: Float,
    val rootHeight: Float,
    val pressProgress: Float,
    val pressCenterX: Float,
    val pressCenterY: Float,
    val style: GlassBorderStyle,
    val densityScale: Float,
)

private class WebOpenGLGlassBatchHostView(context: Context) : FrameLayout(context) {
    private val textureView = WebOpenGLGlassBatchTextureView(context)

    private var items: List<OpenGLShellBatchItem> = emptyList()
    private var parentCoordinates: GlassCoordinateSource? = null
    private var backdropOrigin: BackdropCoordinateSource? = null
    private var frameTicker: BackdropFrameTicker? = null
    private var removeParentListener: (() -> Unit)? = null
    private var removeBackdropListener: (() -> Unit)? = null
    private var removeTickerListener: (() -> Unit)? = null
    private val removeItemListeners = ArrayList<() -> Unit>()

    private var latestParentWidth = 1f
    private var latestParentHeight = 1f
    private var latestRootWidth = 1f
    private var latestRootHeight = 1f
    private var latestDensityScale = 1f
    private var latestBorderStyle = GlassBorderStyle()

    private var renderPosted = false
    private val renderRunnable = Runnable {
        renderPosted = false
        if (isAttachedToWindow && syncFramesToTexture()) textureView.requestRender()
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

        uninstallDynamicSubscriptions()
        this.items = items
        this.parentCoordinates = parentCoordinates
        this.backdropOrigin = backdropOrigin
        this.frameTicker = frameTicker
        if (isAttachedToWindow) installDynamicSubscriptions()
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
        val changed =
            abs(latestParentWidth - width) > BATCH_FRAME_EPSILON_PX ||
                abs(latestParentHeight - height) > BATCH_FRAME_EPSILON_PX ||
                abs(latestRootWidth - rootWidth) > BATCH_FRAME_EPSILON_PX ||
                abs(latestRootHeight - rootHeight) > BATCH_FRAME_EPSILON_PX ||
                abs(latestDensityScale - densityScale) > 0.0001f ||
                latestBorderStyle != borderStyle
        latestParentWidth = width.coerceAtLeast(1f)
        latestParentHeight = height.coerceAtLeast(1f)
        latestRootWidth = rootWidth.coerceAtLeast(1f)
        latestRootHeight = rootHeight.coerceAtLeast(1f)
        latestDensityScale = densityScale.coerceAtLeast(0.001f)
        latestBorderStyle = borderStyle
        if (changed) requestRenderOnNextAnimationFrame()
    }

    fun setBackdropTextures(
        clearBitmap: Bitmap,
        blurLowBitmap: Bitmap,
        blurMediumBitmap: Bitmap,
        blurHighBitmap: Bitmap,
    ): Boolean = textureView.setBackdropTextures(
        clearBitmap,
        blurLowBitmap,
        blurMediumBitmap,
        blurHighBitmap,
    )

    fun setBackdropBlurAmount(amount: Float): Boolean =
        textureView.setBackdropBlurAmount(amount)

    fun requestRenderOnNextAnimationFrame() {
        if (renderPosted) return
        renderPosted = true
        postOnAnimation(renderRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        installDynamicSubscriptions()
        requestRenderOnNextAnimationFrame()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(renderRunnable)
        renderPosted = false
        uninstallDynamicSubscriptions()
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        textureView.layout(0, 0, right - left, bottom - top)
        requestRenderOnNextAnimationFrame()
    }

    private fun installDynamicSubscriptions() {
        if (removeParentListener != null || removeBackdropListener != null || removeTickerListener != null || removeItemListeners.isNotEmpty()) return
        removeParentListener = parentCoordinates?.addPlacementListener(::requestRenderOnNextAnimationFrame)
        removeBackdropListener = backdropOrigin?.addPlacementListener(::requestRenderOnNextAnimationFrame)
        removeTickerListener = frameTicker?.addFrameListener(::refreshDynamicFrameAtVsync)
        for (item in items) {
            removeItemListeners += item.coordinates.addPlacementListener(::requestRenderOnNextAnimationFrame)
            removeItemListeners += item.dynamicState.addFrameListener(::refreshDynamicFrameAtVsync)
        }
    }

    private fun uninstallDynamicSubscriptions() {
        removeParentListener?.invoke()
        removeBackdropListener?.invoke()
        removeTickerListener?.invoke()
        removeParentListener = null
        removeBackdropListener = null
        removeTickerListener = null
        removeItemListeners.forEach { it.invoke() }
        removeItemListeners.clear()
    }

    private fun refreshDynamicFrameAtVsync() {
        if (!isAttachedToWindow) return
        if (syncFramesToTexture()) textureView.requestRender()
    }

    private fun syncFramesToTexture(): Boolean {
        val parentRoot = parentCoordinates?.rootOffsetNow() ?: Offset.Zero
        val backdropRoot = backdropOrigin?.rootOffsetNow() ?: Offset.Zero
        val densityScale = latestDensityScale.coerceAtLeast(0.001f)
        val frames = ArrayList<WebOpenGLGlassBatchFrame>(items.size)

        for (item in items) {
            val coordinates = item.coordinates.coordinates ?: continue
            if (!coordinates.isAttached) continue
            val size = coordinates.size
            if (size.width <= 1 || size.height <= 1) continue

            val itemRoot = coordinates.localToRoot(Offset.Zero)
            val localLeft = itemRoot.x - parentRoot.x
            val localTop = itemRoot.y - parentRoot.y
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            val dynamic = item.dynamicState.latestSnapshot()
            val centerX = dynamic.pressCenter.x.coerceIn(0f, 1f)
            val centerY = dynamic.pressCenter.y.coerceIn(0f, 1f)
            val scaleX = 1f + dynamic.pressCompression * 0.014f - dynamic.pressRebound * 0.004f
            val scaleY = 1f - dynamic.pressCompression * 0.022f + dynamic.pressRebound * 0.008f
            val translationY = dynamic.pressCompression * 2.10f - dynamic.pressRebound * 0.80f
            val transformedWidth = (width * scaleX).coerceAtLeast(1f)
            val transformedHeight = (height * scaleY).coerceAtLeast(1f)
            val transformedLeft = localLeft + (1f - scaleX) * centerX * width
            val transformedTop = localTop + (1f - scaleY) * centerY * height + translationY
            val transformedRootLeft = itemRoot.x + (1f - scaleX) * centerX * width
            val transformedRootTop = itemRoot.y + (1f - scaleY) * centerY * height + translationY
            val shortEdgeDp = min(width, height) / densityScale
            val opticalScale = (shortEdgeDp / BATCH_REFERENCE_SHORT_EDGE_DP)
                .coerceIn(BATCH_MINIMUM_OPTICAL_SCALE, 1f)
            val scaledStyle = latestBorderStyle.scaleBatchOpticalDistances(opticalScale)

            frames += WebOpenGLGlassBatchFrame(
                left = transformedLeft,
                top = transformedTop,
                width = transformedWidth,
                height = transformedHeight,
                radius = item.radiusDp.dp.value * densityScale * min(scaleX, scaleY),
                intensity = (item.baseIntensity * dynamic.glassIntensityScale).coerceIn(0.35f, 1.35f),
                originX = transformedRootLeft - backdropRoot.x,
                originY = transformedRootTop - backdropRoot.y,
                rootWidth = latestRootWidth,
                rootHeight = latestRootHeight,
                pressProgress = dynamic.openGlPress.coerceIn(0f, 1f),
                pressCenterX = centerX,
                pressCenterY = centerY,
                style = scaledStyle,
                densityScale = densityScale,
            )
        }

        return textureView.setFrames(frames)
    }
}

private class WebOpenGLGlassBatchTextureView(context: Context) :
    TextureView(context), TextureView.SurfaceTextureListener {

    private var renderThread: WebOpenGLGlassBatchEglThread? = null
    private var latestFrames: List<WebOpenGLGlassBatchFrame> = emptyList()
    private var latestClearBitmap: Bitmap? = null
    private var latestBlurLowBitmap: Bitmap? = null
    private var latestBlurMediumBitmap: Bitmap? = null
    private var latestBlurHighBitmap: Bitmap? = null
    private var latestBlurAmount = 0f

    init {
        isOpaque = false
        alpha = 1f
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setFrames(frames: List<WebOpenGLGlassBatchFrame>): Boolean {
        if (frames.batchApproximatelyEquals(latestFrames)) return false
        latestFrames = frames
        renderThread?.setFrames(frames)
        return true
    }

    fun setBackdropTextures(
        clearBitmap: Bitmap,
        blurLowBitmap: Bitmap,
        blurMediumBitmap: Bitmap,
        blurHighBitmap: Bitmap,
    ): Boolean {
        val dirty =
            clearBitmap !== latestClearBitmap ||
                blurLowBitmap !== latestBlurLowBitmap ||
                blurMediumBitmap !== latestBlurMediumBitmap ||
                blurHighBitmap !== latestBlurHighBitmap
        latestClearBitmap = clearBitmap
        latestBlurLowBitmap = blurLowBitmap
        latestBlurMediumBitmap = blurMediumBitmap
        latestBlurHighBitmap = blurHighBitmap
        if (dirty) {
            PerformanceRuntimeMetrics.recordOpenGlTextureUpload(clearBitmap.width, clearBitmap.height)
            PerformanceRuntimeMetrics.recordOpenGlTextureUpload(blurLowBitmap.width, blurLowBitmap.height)
            PerformanceRuntimeMetrics.recordOpenGlTextureUpload(blurMediumBitmap.width, blurMediumBitmap.height)
            if (blurHighBitmap !== blurMediumBitmap) {
                PerformanceRuntimeMetrics.recordOpenGlTextureUpload(blurHighBitmap.width, blurHighBitmap.height)
            }
            renderThread?.setBackdropTextures(clearBitmap, blurLowBitmap, blurMediumBitmap, blurHighBitmap)
        }
        return dirty
    }

    fun setBackdropBlurAmount(amount: Float): Boolean {
        val safeAmount = amount.coerceIn(0f, 4f)
        if (abs(safeAmount - latestBlurAmount) <= 0.002f) return false
        latestBlurAmount = safeAmount
        renderThread?.setBackdropBlurAmount(safeAmount)
        return true
    }

    fun requestRender() {
        renderThread?.requestRender()
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        renderThread?.shutdown()
        renderThread = WebOpenGLGlassBatchEglThread(
            surface = Surface(surfaceTexture),
            width = width,
            height = height,
            onFirstFramePresented = StartupPerformanceGate::markOpenGlFirstFrameReady,
        ).also { thread ->
            thread.setFrames(latestFrames)
            thread.setBackdropBlurAmount(latestBlurAmount)
            val clear = latestClearBitmap
            val low = latestBlurLowBitmap
            val medium = latestBlurMediumBitmap
            val high = latestBlurHighBitmap
            if (clear != null && low != null && medium != null && high != null) {
                thread.setBackdropTextures(clear, low, medium, high)
            }
            thread.start()
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        renderThread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        renderThread?.shutdown()
        renderThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
}

private class WebOpenGLGlassBatchEglThread(
    private val surface: Surface,
    width: Int,
    height: Int,
    private val onFirstFramePresented: () -> Unit,
) : Thread("WebOpenGLGlassBatchTextureThread") {
    private val renderer = WebOpenGLGlassBatchRenderer()
    private val renderLock = Object()

    @Volatile private var running = true
    @Volatile private var pendingRender = true
    @Volatile private var viewportWidth = max(width, 1)
    @Volatile private var viewportHeight = max(height, 1)
    @Volatile private var sizeDirty = true

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var firstFramePresented = false
    private var metricsContextActive = false

    fun setFrames(frames: List<WebOpenGLGlassBatchFrame>) = renderer.setFrames(frames)

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
        PerformanceRuntimeMetrics.recordOpenGlSurface(viewportWidth, viewportHeight)
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
                    sizeDirty = false
                }
                renderer.onDrawFrame()
                val swapped = EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                if (swapped) {
                    PerformanceRuntimeMetrics.recordOpenGlFrame()
                    if (!firstFramePresented) {
                        firstFramePresented = true
                        onFirstFramePresented()
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
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1))

        val preservedConfig = chooseConfig(
            EGL14.EGL_WINDOW_BIT or BATCH_EGL_SWAP_BEHAVIOR_PRESERVED_BIT_VALUE,
        )
        val config = preservedConfig ?: chooseConfig(EGL14.EGL_WINDOW_BIT)
            ?: error("No EGL config")

        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            config,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0,
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
        metricsContextActive = true
        PerformanceRuntimeMetrics.recordOpenGlContextCreated()

        if (preservedConfig != null) {
            EGL14.eglSurfaceAttrib(
                eglDisplay,
                eglSurface,
                BATCH_EGL_SWAP_BEHAVIOR_VALUE,
                BATCH_EGL_BUFFER_PRESERVED_VALUE,
            )
        }
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
        val success = EGL14.eglChooseConfig(
            eglDisplay,
            attributes,
            0,
            configs,
            0,
            configs.size,
            count,
            0,
        )
        return if (success && count[0] > 0) configs[0] else null
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        if (metricsContextActive) {
            metricsContextActive = false
            PerformanceRuntimeMetrics.recordOpenGlContextReleased()
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglSurface = EGL14.EGL_NO_SURFACE
        eglContext = EGL14.EGL_NO_CONTEXT
    }
}

private class WebOpenGLGlassBatchRenderer {
    private val textureLock = Any()
    private val frameLock = Any()

    private var pendingFrames: List<WebOpenGLGlassBatchFrame> = emptyList()
    private var drawFrames: List<WebOpenGLGlassBatchFrame> = emptyList()
    private var pendingBlurAmount = 0f
    private var drawBlurAmount = 0f

    private var pendingClearBitmap: Bitmap? = null
    private var pendingBlurLowBitmap: Bitmap? = null
    private var pendingBlurMediumBitmap: Bitmap? = null
    private var pendingBlurHighBitmap: Bitmap? = null
    private var textureSetPending = false
    private var activeClearBitmap: Bitmap? = null
    private var activeBlurLowBitmap: Bitmap? = null
    private var activeBlurMediumBitmap: Bitmap? = null
    private var activeBlurHighBitmap: Bitmap? = null
    private var highTextureAliasesMedium = false

    private var clearTextureId = 0
    private var blurLowTextureId = 0
    private var blurMediumTextureId = 0
    private var blurHighTextureId = 0
    private val textureWidths = IntArray(4)
    private val textureHeights = IntArray(4)
    private var textureReady = false

    private var program = 0
    private var quadBufferId = 0
    private var positionHandle = 0
    private var resolutionHandle = 0
    private var cardOriginHandle = 0
    private var rootResolutionHandle = 0
    private var rectHandle = 0
    private var radiusHandle = 0
    private var intensityHandle = 0
    private var pressHandle = 0
    private var textureReadyHandle = 0
    private var blurAmountHandle = 0
    private var clearTextureHandle = 0
    private var blurLowTextureHandle = 0
    private var blurMediumTextureHandle = 0
    private var blurHighTextureHandle = 0
    private var materialHandle = 0
    private var bodyLensAHandle = 0
    private var bodyLensBHandle = 0
    private var bodyHandle = 0
    private var shoulderHandle = 0
    private var shoulderFlowHandle = 0
    private var dispersionHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1

    fun setFrames(frames: List<WebOpenGLGlassBatchFrame>) {
        synchronized(frameLock) { pendingFrames = frames }
    }

    fun setBackdropBlurAmount(amount: Float) {
        synchronized(frameLock) { pendingBlurAmount = amount.coerceIn(0f, 4f) }
    }

    fun setBackdropTextures(clear: Bitmap, low: Bitmap, medium: Bitmap, high: Bitmap) {
        synchronized(textureLock) {
            pendingClearBitmap = clear
            pendingBlurLowBitmap = low
            pendingBlurMediumBitmap = medium
            pendingBlurHighBitmap = high
            textureSetPending = true
        }
    }

    fun onSurfaceCreated() {
        program = buildBatchProgram(BATCH_VERTEX_SHADER, WebOpenGLGlassShaders.FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        cardOriginHandle = GLES20.glGetUniformLocation(program, "uCardOrigin")
        rootResolutionHandle = GLES20.glGetUniformLocation(program, "uRootResolution")
        rectHandle = GLES20.glGetUniformLocation(program, "uRect")
        radiusHandle = GLES20.glGetUniformLocation(program, "uRadius")
        intensityHandle = GLES20.glGetUniformLocation(program, "uIntensity")
        pressHandle = GLES20.glGetUniformLocation(program, "uPress")
        textureReadyHandle = GLES20.glGetUniformLocation(program, "uTextureReady")
        blurAmountHandle = GLES20.glGetUniformLocation(program, "uBlurAmount")
        clearTextureHandle = GLES20.glGetUniformLocation(program, "uClearTexture")
        blurLowTextureHandle = GLES20.glGetUniformLocation(program, "uBlurLowTexture")
        blurMediumTextureHandle = GLES20.glGetUniformLocation(program, "uBlurMediumTexture")
        blurHighTextureHandle = GLES20.glGetUniformLocation(program, "uBlurHighTexture")
        materialHandle = GLES20.glGetUniformLocation(program, "uMaterial")
        bodyLensAHandle = GLES20.glGetUniformLocation(program, "uBodyLensA")
        bodyLensBHandle = GLES20.glGetUniformLocation(program, "uBodyLensB")
        bodyHandle = GLES20.glGetUniformLocation(program, "uBody")
        shoulderHandle = GLES20.glGetUniformLocation(program, "uShoulder")
        shoulderFlowHandle = GLES20.glGetUniformLocation(program, "uShoulderFlow")
        dispersionHandle = GLES20.glGetUniformLocation(program, "uDispersion")

        GLES20.glUseProgram(program)
        GLES20.glUniform1i(clearTextureHandle, 0)
        GLES20.glUniform1i(blurLowTextureHandle, 1)
        GLES20.glUniform1i(blurMediumTextureHandle, 2)
        GLES20.glUniform1i(blurHighTextureHandle, 3)
        GLES20.glUniform1f(textureReadyHandle, 0f)

        clearTextureId = createConfiguredBatchTexture(0, GLES20.GL_TEXTURE0)
        blurLowTextureId = createConfiguredBatchTexture(1, GLES20.GL_TEXTURE1)
        blurMediumTextureId = createConfiguredBatchTexture(2, GLES20.GL_TEXTURE2)

        val buffers = IntArray(1)
        GLES20.glGenBuffers(1, buffers, 0)
        quadBufferId = buffers[0]
        val quadVertices = ByteBuffer
            .allocateDirect(BATCH_FULLSCREEN_QUAD.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(BATCH_FULLSCREEN_QUAD)
                position(0)
            }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBufferId)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            BATCH_FULLSCREEN_QUAD.size * 4,
            quadVertices,
            GLES20.GL_STATIC_DRAW,
        )
        bindBatchQuad()
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    fun onDrawFrame() {
        uploadPendingBatchTexturesIfNeeded()
        if (program == 0) return
        synchronized(frameLock) {
            drawFrames = pendingFrames
            drawBlurAmount = pendingBlurAmount
        }

        GLES20.glUseProgram(program)
        bindBatchQuad()
        GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES20.glUniform1f(blurAmountHandle, drawBlurAmount)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        PerformanceRuntimeMetrics.recordOpenGlFullClear(viewportWidth, viewportHeight)

        var lastStyle: GlassBorderStyle? = null
        var lastDensity = -1f
        for (frame in drawFrames) {
            val left = (floor(frame.left.toDouble()).toInt() - BATCH_SCISSOR_PADDING_PX)
                .coerceIn(0, viewportWidth)
            val top = (floor(frame.top.toDouble()).toInt() - BATCH_SCISSOR_PADDING_PX)
                .coerceIn(0, viewportHeight)
            val right = (ceil((frame.left + frame.width).toDouble()).toInt() + BATCH_SCISSOR_PADDING_PX)
                .coerceIn(0, viewportWidth)
            val bottom = (ceil((frame.top + frame.height).toDouble()).toInt() + BATCH_SCISSOR_PADDING_PX)
                .coerceIn(0, viewportHeight)
            if (right <= left || bottom <= top) continue

            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            GLES20.glScissor(left, viewportHeight - bottom, right - left, bottom - top)
            GLES20.glUniform4f(rectHandle, frame.left, frame.top, frame.width, frame.height)
            GLES20.glUniform1f(radiusHandle, frame.radius.coerceIn(2f, max(frame.width, frame.height)))
            GLES20.glUniform1f(intensityHandle, frame.intensity)
            GLES20.glUniform2f(cardOriginHandle, frame.originX, frame.originY)
            GLES20.glUniform2f(rootResolutionHandle, frame.rootWidth, frame.rootHeight)
            GLES20.glUniform4f(
                pressHandle,
                frame.pressProgress,
                frame.pressCenterX,
                frame.pressCenterY,
                0f,
            )
            if (lastStyle != frame.style || abs(lastDensity - frame.densityScale) > 0.0001f) {
                uploadBatchStyleUniforms(frame.style, frame.densityScale)
                lastStyle = frame.style
                lastDensity = frame.densityScale
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    fun onRelease() {
        deleteBatchTexture(clearTextureId)
        deleteBatchTexture(blurLowTextureId)
        deleteBatchTexture(blurMediumTextureId)
        deleteBatchTexture(blurHighTextureId)
        if (quadBufferId != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadBufferId), 0)
        if (program != 0) GLES20.glDeleteProgram(program)
        clearTextureId = 0
        blurLowTextureId = 0
        blurMediumTextureId = 0
        blurHighTextureId = 0
        quadBufferId = 0
        program = 0
        textureReady = false
    }

    private fun uploadBatchStyleUniforms(style: GlassBorderStyle, densityScale: Float) {
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

    private fun uploadPendingBatchTexturesIfNeeded() {
        val clear: Bitmap
        val low: Bitmap
        val medium: Bitmap
        val high: Bitmap
        synchronized(textureLock) {
            if (!textureSetPending) return
            clear = pendingClearBitmap ?: return
            low = pendingBlurLowBitmap ?: return
            medium = pendingBlurMediumBitmap ?: return
            high = pendingBlurHighBitmap ?: return
            pendingClearBitmap = null
            pendingBlurLowBitmap = null
            pendingBlurMediumBitmap = null
            pendingBlurHighBitmap = null
            textureSetPending = false
        }

        if (clear !== activeClearBitmap) {
            uploadBatchTexture(0, GLES20.GL_TEXTURE0, clearTextureId, clear)
            activeClearBitmap = clear
        }
        if (low !== activeBlurLowBitmap) {
            uploadBatchTexture(1, GLES20.GL_TEXTURE1, blurLowTextureId, low)
            activeBlurLowBitmap = low
        }
        if (medium !== activeBlurMediumBitmap) {
            uploadBatchTexture(2, GLES20.GL_TEXTURE2, blurMediumTextureId, medium)
            activeBlurMediumBitmap = medium
        }

        val aliasHighToMedium = high === medium
        if (aliasHighToMedium) {
            if (!highTextureAliasesMedium || blurHighTextureId != 0) {
                deleteBatchTexture(blurHighTextureId)
                blurHighTextureId = 0
                textureWidths[3] = 0
                textureHeights[3] = 0
                bindBatchTexture(GLES20.GL_TEXTURE3, blurMediumTextureId)
                highTextureAliasesMedium = true
            }
            activeBlurHighBitmap = high
        } else {
            if (blurHighTextureId == 0) {
                blurHighTextureId = createConfiguredBatchTexture(3, GLES20.GL_TEXTURE3)
            } else if (highTextureAliasesMedium) {
                bindBatchTexture(GLES20.GL_TEXTURE3, blurHighTextureId)
            }
            highTextureAliasesMedium = false
            if (high !== activeBlurHighBitmap) {
                uploadBatchTexture(3, GLES20.GL_TEXTURE3, blurHighTextureId, high)
                activeBlurHighBitmap = high
            }
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (!textureReady) {
            textureReady = true
            GLES20.glUniform1f(textureReadyHandle, 1f)
        }
    }

    private fun uploadBatchTexture(index: Int, textureUnit: Int, textureId: Int, bitmap: Bitmap) {
        bindBatchTexture(textureUnit, textureId)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        if (textureWidths[index] == bitmap.width && textureHeights[index] == bitmap.height) {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        } else {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            textureWidths[index] = bitmap.width
            textureHeights[index] = bitmap.height
        }
    }

    private fun createConfiguredBatchTexture(index: Int, textureUnit: Int): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureId = textures[0]
        bindBatchTexture(textureUnit, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        textureWidths[index] = 0
        textureHeights[index] = 0
        return textureId
    }

    private fun bindBatchQuad() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBufferId)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, 0)
    }

    private fun bindBatchTexture(textureUnit: Int, textureId: Int) {
        GLES20.glActiveTexture(textureUnit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    }

    private fun deleteBatchTexture(textureId: Int) {
        if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
    }
}

private fun List<WebOpenGLGlassBatchFrame>.batchApproximatelyEquals(
    other: List<WebOpenGLGlassBatchFrame>,
): Boolean {
    if (size != other.size) return false
    for (index in indices) {
        val a = this[index]
        val b = other[index]
        if (
            abs(a.left - b.left) > BATCH_FRAME_EPSILON_PX ||
            abs(a.top - b.top) > BATCH_FRAME_EPSILON_PX ||
            abs(a.width - b.width) > BATCH_FRAME_EPSILON_PX ||
            abs(a.height - b.height) > BATCH_FRAME_EPSILON_PX ||
            abs(a.radius - b.radius) > BATCH_FRAME_EPSILON_PX ||
            abs(a.intensity - b.intensity) > BATCH_INTENSITY_EPSILON ||
            abs(a.originX - b.originX) > BATCH_FRAME_EPSILON_PX ||
            abs(a.originY - b.originY) > BATCH_FRAME_EPSILON_PX ||
            abs(a.pressProgress - b.pressProgress) > BATCH_PRESS_EPSILON ||
            abs(a.pressCenterX - b.pressCenterX) > BATCH_PRESS_EPSILON ||
            abs(a.pressCenterY - b.pressCenterY) > BATCH_PRESS_EPSILON ||
            a.style != b.style
        ) return false
    }
    return true
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

private fun buildBatchProgram(vertex: String, fragment: String): Int {
    fun compileShader(type: Int, source: String): Int {
        val shaderId = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shaderId, source)
        GLES20.glCompileShader(shaderId)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { GLES20.glGetShaderInfoLog(shaderId) }
        return shaderId
    }

    val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertex)
    val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment)
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

private val BATCH_FULLSCREEN_QUAD = floatArrayOf(
    -1f, -1f,
    1f, -1f,
    -1f, 1f,
    1f, 1f,
)

private const val BATCH_VERTEX_SHADER = """
    attribute vec2 aPosition;
    void main(){ gl_Position=vec4(aPosition,0.0,1.0); }
"""
