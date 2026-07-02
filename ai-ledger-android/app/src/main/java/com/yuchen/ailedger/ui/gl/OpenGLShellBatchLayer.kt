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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
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
    private val versionState = mutableLongStateOf(0L)

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
        versionState.longValue
        return cachedSnapshot
    }

    private fun rebuildSnapshot() {
        cachedSnapshot = entries.values.toList()
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
                    widthPx,
                    heightPx,
                    backdrop.fullWidthPx.toFloat().coerceAtLeast(1f),
                    backdrop.fullHeightPx.toFloat().coerceAtLeast(1f),
                    densityScale,
                    border,
                )
                val texturesChanged = view.setBackdropTextures(
                    clearBitmap,
                    blurLowBitmap,
                    blurMediumBitmap,
                    blurHighBitmap,
                )
                val blurChanged = view.setBackdropBlurAmount(backdrop.blurAmount)
                if (texturesChanged || blurChanged) view.requestRenderOnNextAnimationFrame()
            },
        )
    }
}

@Immutable
private data class BatchFrame(
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
    val press: Float,
    val pressX: Float,
    val pressY: Float,
    val style: GlassBorderStyle,
    val densityScale: Float,
)

private class WebOpenGLGlassBatchHostView(context: Context) : FrameLayout(context) {
    private val textureView = BatchTextureView(context)
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
    private var removeParent: (() -> Unit)? = null
    private var removeBackdrop: (() -> Unit)? = null
    private var removeTicker: (() -> Unit)? = null
    private val removeItems = ArrayList<() -> Unit>()
    private var renderPosted = false

    private val renderRunnable = Runnable {
        renderPosted = false
        if (isAttachedToWindow && syncFrames()) textureView.requestRender()
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
        this.items = items
        this.parentCoordinates = parentCoordinates
        this.backdropOrigin = backdropOrigin
        this.frameTicker = frameTicker
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
        val changed =
            abs(parentWidth - width) > BATCH_FRAME_EPSILON_PX ||
                abs(parentHeight - height) > BATCH_FRAME_EPSILON_PX ||
                abs(this.rootWidth - rootWidth) > BATCH_FRAME_EPSILON_PX ||
                abs(this.rootHeight - rootHeight) > BATCH_FRAME_EPSILON_PX ||
                abs(this.densityScale - densityScale) > 0.0001f ||
                this.borderStyle != borderStyle
        parentWidth = width.coerceAtLeast(1f)
        parentHeight = height.coerceAtLeast(1f)
        this.rootWidth = rootWidth.coerceAtLeast(1f)
        this.rootHeight = rootHeight.coerceAtLeast(1f)
        this.densityScale = densityScale.coerceAtLeast(0.001f)
        this.borderStyle = borderStyle
        if (changed) requestRenderOnNextAnimationFrame()
    }

    fun setBackdropTextures(clear: Bitmap, low: Bitmap, medium: Bitmap, high: Bitmap): Boolean =
        textureView.setBackdropTextures(clear, low, medium, high)

    fun setBackdropBlurAmount(amount: Float): Boolean = textureView.setBackdropBlurAmount(amount)

    fun requestRenderOnNextAnimationFrame() {
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
        requestRenderOnNextAnimationFrame()
    }

    private fun installSubscriptions() {
        if (removeParent != null || removeBackdrop != null || removeTicker != null || removeItems.isNotEmpty()) return
        removeParent = parentCoordinates?.addPlacementListener(::requestRenderOnNextAnimationFrame)
        removeBackdrop = backdropOrigin?.addPlacementListener(::requestRenderOnNextAnimationFrame)
        removeTicker = frameTicker?.addFrameListener(::refreshAtVsync)
        for (item in items) {
            removeItems += item.coordinates.addPlacementListener(::requestRenderOnNextAnimationFrame)
            removeItems += item.dynamicState.addFrameListener(::refreshAtVsync)
        }
    }

    private fun uninstallSubscriptions() {
        removeParent?.invoke()
        removeBackdrop?.invoke()
        removeTicker?.invoke()
        removeParent = null
        removeBackdrop = null
        removeTicker = null
        removeItems.forEach { it.invoke() }
        removeItems.clear()
    }

    private fun refreshAtVsync() {
        if (isAttachedToWindow && syncFrames()) textureView.requestRender()
    }

    private fun syncFrames(): Boolean {
        val parentRoot = parentCoordinates?.rootOffsetNow() ?: Offset.Zero
        val backdropRoot = backdropOrigin?.rootOffsetNow() ?: Offset.Zero
        val density = densityScale.coerceAtLeast(0.001f)
        val frames = ArrayList<BatchFrame>(items.size)

        for (item in items) {
            val coordinates = item.coordinates.coordinates ?: continue
            if (!coordinates.isAttached) continue
            val itemSize = coordinates.size
            if (itemSize.width <= 1 || itemSize.height <= 1) continue

            val itemRoot = coordinates.localToRoot(Offset.Zero)
            val width = itemSize.width.toFloat()
            val height = itemSize.height.toFloat()
            val dynamic = item.dynamicState.latestSnapshot()
            val centerX = dynamic.pressCenter.x.coerceIn(0f, 1f)
            val centerY = dynamic.pressCenter.y.coerceIn(0f, 1f)
            val scaleX = 1f + dynamic.pressCompression * 0.014f - dynamic.pressRebound * 0.004f
            val scaleY = 1f - dynamic.pressCompression * 0.022f + dynamic.pressRebound * 0.008f
            val translationY = dynamic.pressCompression * 2.10f - dynamic.pressRebound * 0.80f
            val left = itemRoot.x - parentRoot.x + (1f - scaleX) * centerX * width
            val top = itemRoot.y - parentRoot.y + (1f - scaleY) * centerY * height + translationY
            val globalLeft = itemRoot.x + (1f - scaleX) * centerX * width
            val globalTop = itemRoot.y + (1f - scaleY) * centerY * height + translationY
            val shortEdgeDp = min(width, height) / density
            val opticalScale = (shortEdgeDp / BATCH_REFERENCE_SHORT_EDGE_DP)
                .coerceIn(BATCH_MINIMUM_OPTICAL_SCALE, 1f)

            frames += BatchFrame(
                left = left,
                top = top,
                width = (width * scaleX).coerceAtLeast(1f),
                height = (height * scaleY).coerceAtLeast(1f),
                radius = item.radiusDp * density * min(scaleX, scaleY),
                intensity = (item.baseIntensity * dynamic.glassIntensityScale).coerceIn(0.35f, 1.35f),
                originX = globalLeft - backdropRoot.x,
                originY = globalTop - backdropRoot.y,
                rootWidth = rootWidth,
                rootHeight = rootHeight,
                press = dynamic.openGlPress.coerceIn(0f, 1f),
                pressX = centerX,
                pressY = centerY,
                style = borderStyle.scaleBatchOpticalDistances(opticalScale),
                densityScale = density,
            )
        }
        return textureView.setFrames(frames)
    }
}

private class BatchTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var thread: BatchEglThread? = null
    private var frames: List<BatchFrame> = emptyList()
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

    fun setFrames(next: List<BatchFrame>): Boolean {
        if (next.batchApproximatelyEquals(frames)) return false
        frames = next
        thread?.setFrames(next)
        return true
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
            Surface(surfaceTexture),
            width,
            height,
            StartupPerformanceGate::markOpenGlFirstFrameReady,
        ).also { next ->
            next.setFrames(frames)
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

    fun setFrames(frames: List<BatchFrame>) = renderer.setFrames(frames)
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
        val preserved = chooseConfig(EGL14.EGL_WINDOW_BIT or BATCH_EGL_SWAP_BEHAVIOR_PRESERVED_BIT_VALUE)
        val config = preserved ?: chooseConfig(EGL14.EGL_WINDOW_BIT) ?: error("No EGL config")
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
        if (preserved != null) {
            EGL14.eglSurfaceAttrib(
                display,
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
    private val textureLock = Any()
    private val frameLock = Any()
    private var pendingFrames: List<BatchFrame> = emptyList()
    private var drawFrames: List<BatchFrame> = emptyList()
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
    private var program = 0
    private var quadBuffer = 0
    private var positionHandle = 0
    private var resolutionHandle = 0
    private var cardOriginHandle = 0
    private var rootResolutionHandle = 0
    private var rectHandle = 0
    private var radiusHandle = 0
    private var intensityHandle = 0
    private var pressHandle = 0
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

    fun setFrames(frames: List<BatchFrame>) {
        synchronized(frameLock) { pendingFrames = frames }
    }

    fun setBackdropBlurAmount(amount: Float) {
        synchronized(frameLock) { pendingBlur = amount.coerceIn(0f, 4f) }
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
        program = buildProgram(BATCH_VERTEX_SHADER, WebOpenGLGlassShaders.FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        cardOriginHandle = GLES20.glGetUniformLocation(program, "uCardOrigin")
        rootResolutionHandle = GLES20.glGetUniformLocation(program, "uRootResolution")
        rectHandle = GLES20.glGetUniformLocation(program, "uRect")
        radiusHandle = GLES20.glGetUniformLocation(program, "uRadius")
        intensityHandle = GLES20.glGetUniformLocation(program, "uIntensity")
        pressHandle = GLES20.glGetUniformLocation(program, "uPress")
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
        quadBuffer = buffers[0]
        val vertices = ByteBuffer.allocateDirect(BATCH_QUAD.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(BATCH_QUAD)
                position(0)
            }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBuffer)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, BATCH_QUAD.size * 4, vertices, GLES20.GL_STATIC_DRAW)
        bindQuad()
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
        uploadTexturesIfNeeded()
        if (program == 0) return
        synchronized(frameLock) {
            drawFrames = pendingFrames
            drawBlur = pendingBlur
        }
        GLES20.glUseProgram(program)
        bindQuad()
        GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES20.glUniform1f(blurHandle, drawBlur)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        PerformanceRuntimeMetrics.recordOpenGlFullClear(viewportWidth, viewportHeight)

        var lastStyle: GlassBorderStyle? = null
        var lastDensity = -1f
        for (frame in drawFrames) {
            val left = (floor(frame.left.toDouble()).toInt() - BATCH_SCISSOR_PADDING_PX).coerceIn(0, viewportWidth)
            val top = (floor(frame.top.toDouble()).toInt() - BATCH_SCISSOR_PADDING_PX).coerceIn(0, viewportHeight)
            val right = (ceil((frame.left + frame.width).toDouble()).toInt() + BATCH_SCISSOR_PADDING_PX).coerceIn(0, viewportWidth)
            val bottom = (ceil((frame.top + frame.height).toDouble()).toInt() + BATCH_SCISSOR_PADDING_PX).coerceIn(0, viewportHeight)
            if (right <= left || bottom <= top) continue
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            GLES20.glScissor(left, viewportHeight - bottom, right - left, bottom - top)
            GLES20.glUniform4f(rectHandle, frame.left, frame.top, frame.width, frame.height)
            GLES20.glUniform1f(radiusHandle, frame.radius.coerceIn(2f, max(frame.width, frame.height)))
            GLES20.glUniform1f(intensityHandle, frame.intensity)
            GLES20.glUniform2f(cardOriginHandle, frame.originX, frame.originY)
            GLES20.glUniform2f(rootResolutionHandle, frame.rootWidth, frame.rootHeight)
            GLES20.glUniform4f(pressHandle, frame.press, frame.pressX, frame.pressY, 0f)
            if (lastStyle != frame.style || abs(lastDensity - frame.densityScale) > 0.0001f) {
                uploadStyle(frame.style, frame.densityScale)
                lastStyle = frame.style
                lastDensity = frame.densityScale
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    fun onRelease() {
        deleteTexture(clearTexture)
        deleteTexture(lowTexture)
        deleteTexture(mediumTexture)
        deleteTexture(highTexture)
        if (quadBuffer != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadBuffer), 0)
        if (program != 0) GLES20.glDeleteProgram(program)
    }

    private fun uploadStyle(style: GlassBorderStyle, densityScale: Float) {
        val density = densityScale.coerceAtLeast(0.1f)
        GLES20.glUniform4f(materialHandle, style.newOpenGlBodyVisibility.coerceIn(0f, 20f), style.newOpenGlBodyMaxAlpha.coerceIn(0f, 1f), style.newOpenGlBodyOutputBrightness.coerceIn(0.2f, 2.8f), 0f)
        GLES20.glUniform4f(bodyLensAHandle, style.newOpenGlBodyLensBasePull.coerceIn(-300f, 300f) * density, style.newOpenGlBodyLensPullDp.coerceIn(-600f, 600f) * density, style.newOpenGlBodyLensConcentration.coerceIn(-10f, 10f), 0f)
        GLES20.glUniform4f(bodyLensBHandle, style.newOpenGlBodyLensExtraDistance.coerceIn(0f, 200f) * density, style.newOpenGlBodyLensReachDp.coerceIn(8f, 180f) * density, style.newOpenGlBodyLensDark.coerceIn(-10f, 10f), style.newOpenGlBodyLensDebug.coerceIn(0f, 1f))
        GLES20.glUniform4f(bodyHandle, style.newOpenGlBodyWidth.coerceIn(0.18f, 1.5f), style.newOpenGlBodyCurve.coerceIn(0.2f, 3.2f), style.newOpenGlBodyGain.coerceIn(0f, 900f), style.newOpenGlBrightness.coerceIn(0.4f, 2.2f))
        GLES20.glUniform4f(shoulderHandle, style.newOpenGlShoulderWidthDp.coerceIn(4f, 96f) * density, style.newOpenGlShoulderMaxAngleDeg.coerceIn(0f, 89.5f), style.newOpenGlShoulderFalloffRoundness.coerceIn(0f, 1f), style.newOpenGlShoulderMaterialStrength.coerceIn(0f, 4f))
        GLES20.glUniform2f(shoulderFlowHandle, style.newOpenGlShoulderCaptureWidthDp.coerceIn(4f, 192f) * density, style.newOpenGlShoulderTangentialFlowStrength.coerceIn(0f, 2.4f))
        GLES20.glUniform4f(dispersionHandle, style.newOpenGlDispersionStrength.coerceIn(0f, 1.5f), style.newOpenGlDispersionDistanceDp.coerceIn(0f, 8f) * density, style.newOpenGlDispersionEdgeWidthDp.coerceIn(2f, 64f) * density, style.newOpenGlDispersionConcentration.coerceIn(0.25f, 4f))
    }

    private fun uploadTexturesIfNeeded() {
        val clear: Bitmap
        val low: Bitmap
        val medium: Bitmap
        val high: Bitmap
        synchronized(textureLock) {
            if (!texturesPending) return
            clear = pendingClear ?: return
            low = pendingLow ?: return
            medium = pendingMedium ?: return
            high = pendingHigh ?: return
            pendingClear = null
            pendingLow = null
            pendingMedium = null
            pendingHigh = null
            texturesPending = false
        }
        if (clear !== activeClear) {
            uploadTexture(0, GLES20.GL_TEXTURE0, clearTexture, clear)
            activeClear = clear
        }
        if (low !== activeLow) {
            uploadTexture(1, GLES20.GL_TEXTURE1, lowTexture, low)
            activeLow = low
        }
        if (medium !== activeMedium) {
            uploadTexture(2, GLES20.GL_TEXTURE2, mediumTexture, medium)
            activeMedium = medium
        }
        if (high === medium) {
            if (!highAliasesMedium || highTexture != 0) {
                deleteTexture(highTexture)
                highTexture = 0
                bindTexture(GLES20.GL_TEXTURE3, mediumTexture)
                highAliasesMedium = true
            }
            activeHigh = high
        } else {
            if (highTexture == 0) highTexture = createTexture(3, GLES20.GL_TEXTURE3)
            else if (highAliasesMedium) bindTexture(GLES20.GL_TEXTURE3, highTexture)
            highAliasesMedium = false
            if (high !== activeHigh) {
                uploadTexture(3, GLES20.GL_TEXTURE3, highTexture, high)
                activeHigh = high
            }
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (!textureReady) {
            textureReady = true
            GLES20.glUniform1f(textureReadyHandle, 1f)
        }
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

    private fun bindQuad() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, 0)
    }

    private fun bindTexture(unit: Int, texture: Int) {
        GLES20.glActiveTexture(unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
    }

    private fun deleteTexture(texture: Int) {
        if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
    }
}

private fun List<BatchFrame>.batchApproximatelyEquals(other: List<BatchFrame>): Boolean {
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
            abs(a.press - b.press) > BATCH_PRESS_EPSILON ||
            abs(a.pressX - b.pressX) > BATCH_PRESS_EPSILON ||
            abs(a.pressY - b.pressY) > BATCH_PRESS_EPSILON ||
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

private val BATCH_QUAD = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
private const val BATCH_VERTEX_SHADER = """
    attribute vec2 aPosition;
    void main(){ gl_Position=vec4(aPosition,0.0,1.0); }
"""
