package com.yuchen.ailedger.ui.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.ui.geometry.Offset
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.BackdropCoordinateSource
import com.yuchen.ailedger.ui.BackdropFrameTicker
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.PerformanceRuntimeMetrics
import com.yuchen.ailedger.ui.StartupPerformanceGate
import kotlin.math.abs
import kotlin.math.max

private const val WEB_GLASS_SPEC_EPSILON_PX = 0.5f
private const val WEB_GLASS_ORIGIN_EPSILON_PX = 0.35f
private const val WEB_GLASS_INTENSITY_EPSILON = 0.006f
private const val WEB_GLASS_PRESS_EPSILON = 0.003f
private const val WEB_GLASS_PRESS_CENTER_EPSILON = 0.002f
private const val WEB_GLASS_BLUR_EPSILON = 0.002f

private const val EGL_SWAP_BEHAVIOR_VALUE = 0x3093
private const val EGL_BUFFER_PRESERVED_VALUE = 0x3094
private const val EGL_SWAP_BEHAVIOR_PRESERVED_BIT_VALUE = 0x0400

/**
 * 保留 Compose/OpenGL 固定宿主尺寸链；清晰纹理与三级模糊纹理只在内容变化时上传。
 * 几何、采样原点、按压和强度在同一 VSync 中合并为一个快照、一次 Renderer 加锁和
 * 一次 EGL 唤醒。
 */
internal class WebOpenGLGlassCardHostView(context: Context) : FrameLayout(context) {
    private val textureView = WebOpenGLGlassTextureView(
        context = context,
        onFramePresented = ::onTextureFramePresented,
    )

    private var stableSurfaceWidth = 1
    private var stableSurfaceHeight = 1
    private var lastRootWidth = 1
    private var lastRootHeight = 1
    private var geometryAwaitingLayout = false
    private var renderPosted = false
    private var renderAfterLayout = false

    private var latestGlassWidth = 1f
    private var latestFullHeight = 1f
    private var latestViewportHeight = 1f
    private var latestRectOffsetY = 0f
    private var latestRadius = 24f
    private var latestBaseIntensity = 1f
    private var latestRootWidth = 1f
    private var latestRootHeight = 1f
    private var latestStaticPressProgress = 0f
    private var latestStaticPressCenterX = 0.5f
    private var latestStaticPressCenterY = 0.5f

    private var coordinateSource: GlassCoordinateSource? = null
    private var backdropOrigin: BackdropCoordinateSource? = null
    private var frameTicker: BackdropFrameTicker? = null
    private var dynamicState: OpenGLGlassDynamicState? = null
    private var removeCoordinateListener: (() -> Unit)? = null
    private var removeBackdropListener: (() -> Unit)? = null
    private var removeTickerListener: (() -> Unit)? = null
    private var removeDynamicListener: (() -> Unit)? = null

    private val renderRunnable = Runnable {
        renderPosted = false
        if (!geometryAwaitingLayout && isAttachedToWindow) {
            syncDynamicFrameToTexture()
            textureView.requestRender()
        }
    }

    private fun onTextureFramePresented() {
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    init {
        clipChildren = true
        clipToPadding = true
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(textureView, LayoutParams(1, 1))
    }

    fun bindDynamicSources(
        coordinateSource: GlassCoordinateSource?,
        backdropOrigin: BackdropCoordinateSource?,
        frameTicker: BackdropFrameTicker?,
        dynamicState: OpenGLGlassDynamicState?,
    ) {
        if (
            this.coordinateSource === coordinateSource &&
            this.backdropOrigin === backdropOrigin &&
            this.frameTicker === frameTicker &&
            this.dynamicState === dynamicState
        ) return

        uninstallDynamicSubscriptions()
        this.coordinateSource = coordinateSource
        this.backdropOrigin = backdropOrigin
        this.frameTicker = frameTicker
        this.dynamicState = dynamicState
        if (isAttachedToWindow) installDynamicSubscriptions()
    }

    fun setFrameSpec(
        width: Float,
        fullHeight: Float,
        viewportHeight: Float,
        rectOffsetY: Float,
        radius: Float,
        baseIntensity: Float,
        rootWidth: Float,
        rootHeight: Float,
        staticPressProgress: Float,
        staticPressCenterX: Float,
        staticPressCenterY: Float,
    ): Boolean {
        latestGlassWidth = width.coerceAtLeast(1f)
        latestFullHeight = fullHeight.coerceAtLeast(1f)
        latestViewportHeight = viewportHeight.coerceAtLeast(1f)
        latestRectOffsetY = rectOffsetY
        latestRadius = radius
        latestBaseIntensity = baseIntensity.coerceIn(0.35f, 1.35f)
        latestRootWidth = rootWidth.coerceAtLeast(1f)
        latestRootHeight = rootHeight.coerceAtLeast(1f)
        latestStaticPressProgress = staticPressProgress.coerceIn(0f, 1f)
        latestStaticPressCenterX = staticPressCenterX.coerceIn(0f, 1f)
        latestStaticPressCenterY = staticPressCenterY.coerceIn(0f, 1f)
        return if (geometryAwaitingLayout) {
            renderAfterLayout = true
            false
        } else {
            syncDynamicFrameToTexture()
        }
    }

    fun setStableSurfaceSize(width: Int, height: Int, rootWidth: Int, rootHeight: Int): Boolean {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val safeRootWidth = rootWidth.coerceAtLeast(1)
        val safeRootHeight = rootHeight.coerceAtLeast(1)
        val rootSizeChanged = abs(safeRootWidth - lastRootWidth) > 2 ||
            abs(safeRootHeight - lastRootHeight) > 2
        lastRootWidth = safeRootWidth
        lastRootHeight = safeRootHeight

        val targetWidth = if (rootSizeChanged) safeWidth else max(stableSurfaceWidth, safeWidth)
        val targetHeight = if (rootSizeChanged) safeHeight else max(stableSurfaceHeight, safeHeight)
        val sizeChanged = targetWidth != stableSurfaceWidth || targetHeight != stableSurfaceHeight
        stableSurfaceWidth = targetWidth
        stableSurfaceHeight = targetHeight
        PerformanceRuntimeMetrics.recordOpenGlSurface(stableSurfaceWidth, stableSurfaceHeight)

        val current = textureView.layoutParams as? LayoutParams
        val layoutDirty = current == null ||
            current.width != stableSurfaceWidth ||
            current.height != stableSurfaceHeight
        if (layoutDirty) textureView.layoutParams = LayoutParams(stableSurfaceWidth, stableSurfaceHeight)

        val dirty = sizeChanged || layoutDirty
        if (dirty) {
            geometryAwaitingLayout = true
            renderAfterLayout = true
            requestLayout()
        }
        return dirty
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        installDynamicSubscriptions()
        requestRenderOnNextAnimationFrame()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        textureView.translationY = 0f
        textureView.layout(0, 0, stableSurfaceWidth, stableSurfaceHeight)
        geometryAwaitingLayout = false
        if (renderPosted) {
            removeCallbacks(renderRunnable)
            renderPosted = false
        }
        val frameDirty = syncDynamicFrameToTexture()
        val shouldRender = renderAfterLayout || frameDirty
        renderAfterLayout = false
        if (shouldRender) textureView.requestRender()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(renderRunnable)
        renderPosted = false
        uninstallDynamicSubscriptions()
        super.onDetachedFromWindow()
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

    fun setBackdropBlurAmount(amount: Float): Boolean = textureView.setBackdropBlurAmount(amount)

    fun setGlassStyle(style: GlassBorderStyle, densityScale: Float): Boolean =
        textureView.setGlassStyle(style, densityScale)

    fun requestRenderOnNextAnimationFrame() {
        if (geometryAwaitingLayout) {
            renderAfterLayout = true
            return
        }
        if (renderPosted) return
        renderPosted = true
        postOnAnimation(renderRunnable)
    }

    private fun installDynamicSubscriptions() {
        if (removeCoordinateListener != null || removeBackdropListener != null || removeTickerListener != null || removeDynamicListener != null) return
        removeCoordinateListener = coordinateSource?.addPlacementListener(::requestRenderOnNextAnimationFrame)
        removeBackdropListener = backdropOrigin?.addPlacementListener(::requestRenderOnNextAnimationFrame)
        removeTickerListener = frameTicker?.addFrameListener(::refreshDynamicFrameAtVsync)
        removeDynamicListener = dynamicState?.addFrameListener(::refreshDynamicFrameAtVsync)
    }

    private fun uninstallDynamicSubscriptions() {
        removeCoordinateListener?.invoke()
        removeBackdropListener?.invoke()
        removeTickerListener?.invoke()
        removeDynamicListener?.invoke()
        removeCoordinateListener = null
        removeBackdropListener = null
        removeTickerListener = null
        removeDynamicListener = null
    }

    private fun refreshDynamicFrameAtVsync() {
        if (geometryAwaitingLayout) {
            renderAfterLayout = true
            return
        }
        if (!isAttachedToWindow) return
        if (syncDynamicFrameToTexture()) textureView.requestRender()
    }

    private fun syncDynamicFrameToTexture(): Boolean {
        val origin = coordinateSource?.offsetRelativeToNow(backdropOrigin) ?: Offset.Zero
        val dynamic = dynamicState?.latestSnapshot()
        val rawCenterY = dynamic?.pressCenter?.y ?: latestStaticPressCenterY
        val mappedCenterY = (
            (rawCenterY * latestFullHeight - latestRectOffsetY) / latestViewportHeight
            ).coerceIn(0f, 1f)
        return textureView.setFrameState(
            width = latestGlassWidth,
            height = latestViewportHeight,
            rectOffsetY = latestRectOffsetY,
            radius = latestRadius,
            intensity = (
                latestBaseIntensity * (dynamic?.glassIntensityScale ?: 1f)
                ).coerceIn(0.35f, 1.35f),
            originX = origin.x,
            originY = origin.y + latestRectOffsetY,
            rootWidth = latestRootWidth,
            rootHeight = latestRootHeight,
            pressProgress = dynamic?.openGlPress ?: latestStaticPressProgress,
            pressCenterX = dynamic?.pressCenter?.x ?: latestStaticPressCenterX,
            pressCenterY = mappedCenterY,
        )
    }
}

private class WebOpenGLGlassTextureView(
    context: Context,
    private val onFramePresented: () -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: WebOpenGLGlassEglThread? = null
    private var latestClearBitmap: Bitmap? = null
    private var latestBlurLowBitmap: Bitmap? = null
    private var latestBlurMediumBitmap: Bitmap? = null
    private var latestBlurHighBitmap: Bitmap? = null
    private var latestBlurAmount = 0f
    private var latestWidth = 1f
    private var latestHeight = 1f
    private var latestRectOffsetY = 0f
    private var latestRadius = 24f
    private var latestIntensity = 1f
    private var latestOriginX = 0f
    private var latestOriginY = 0f
    private var latestRootWidth = 1f
    private var latestRootHeight = 1f
    private var latestPressProgress = 0f
    private var latestPressCenterX = 0.5f
    private var latestPressCenterY = 0.5f
    private var latestStyle = GlassBorderStyle()
    private var latestDensityScale = 1f

    init {
        isOpaque = false
        alpha = 1f
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setFrameState(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float,
        intensity: Float,
        originX: Float,
        originY: Float,
        rootWidth: Float,
        rootHeight: Float,
        pressProgress: Float,
        pressCenterX: Float,
        pressCenterY: Float,
    ): Boolean {
        val nextWidth = width.coerceAtLeast(1f)
        val nextHeight = height.coerceAtLeast(1f)
        val nextIntensity = intensity.coerceIn(0.35f, 1.35f)
        val nextRootWidth = rootWidth.coerceAtLeast(1f)
        val nextRootHeight = rootHeight.coerceAtLeast(1f)
        val nextPress = pressProgress.coerceIn(0f, 1f)
        val nextPressX = pressCenterX.coerceIn(0f, 1f)
        val nextPressY = pressCenterY.coerceIn(0f, 1f)

        val geometryDirty = abs(nextWidth - latestWidth) > WEB_GLASS_SPEC_EPSILON_PX ||
            abs(nextHeight - latestHeight) > WEB_GLASS_SPEC_EPSILON_PX ||
            abs(rectOffsetY - latestRectOffsetY) > WEB_GLASS_SPEC_EPSILON_PX ||
            abs(radius - latestRadius) > WEB_GLASS_SPEC_EPSILON_PX ||
            abs(nextIntensity - latestIntensity) > WEB_GLASS_INTENSITY_EPSILON
        val samplingDirty = abs(originX - latestOriginX) > WEB_GLASS_ORIGIN_EPSILON_PX ||
            abs(originY - latestOriginY) > WEB_GLASS_ORIGIN_EPSILON_PX ||
            abs(nextRootWidth - latestRootWidth) > WEB_GLASS_SPEC_EPSILON_PX ||
            abs(nextRootHeight - latestRootHeight) > WEB_GLASS_SPEC_EPSILON_PX
        val pressDirty = abs(nextPress - latestPressProgress) > WEB_GLASS_PRESS_EPSILON ||
            abs(nextPressX - latestPressCenterX) > WEB_GLASS_PRESS_CENTER_EPSILON ||
            abs(nextPressY - latestPressCenterY) > WEB_GLASS_PRESS_CENTER_EPSILON
        val dirty = geometryDirty || samplingDirty || pressDirty

        latestWidth = nextWidth
        latestHeight = nextHeight
        latestRectOffsetY = rectOffsetY
        latestRadius = radius
        latestIntensity = nextIntensity
        latestOriginX = originX
        latestOriginY = originY
        latestRootWidth = nextRootWidth
        latestRootHeight = nextRootHeight
        latestPressProgress = nextPress
        latestPressCenterX = nextPressX
        latestPressCenterY = nextPressY

        if (dirty) {
            renderThread?.setFrameState(
                width = nextWidth,
                height = nextHeight,
                rectOffsetY = rectOffsetY,
                radius = radius,
                intensity = nextIntensity,
                originX = originX,
                originY = originY,
                rootWidth = nextRootWidth,
                rootHeight = nextRootHeight,
                pressProgress = nextPress,
                pressCenterX = nextPressX,
                pressCenterY = nextPressY,
                geometryDirty = geometryDirty,
                samplingDirty = samplingDirty,
                pressDirty = pressDirty,
            )
        }
        return dirty
    }

    fun setBackdropTextures(
        clearBitmap: Bitmap,
        blurLowBitmap: Bitmap,
        blurMediumBitmap: Bitmap,
        blurHighBitmap: Bitmap,
    ): Boolean {
        val clearChanged = clearBitmap !== latestClearBitmap
        val lowChanged = blurLowBitmap !== latestBlurLowBitmap
        val mediumChanged = blurMediumBitmap !== latestBlurMediumBitmap
        val highChanged = blurHighBitmap !== latestBlurHighBitmap
        val dirty = clearChanged || lowChanged || mediumChanged || highChanged
        latestClearBitmap = clearBitmap
        latestBlurLowBitmap = blurLowBitmap
        latestBlurMediumBitmap = blurMediumBitmap
        latestBlurHighBitmap = blurHighBitmap
        if (dirty) {
            if (clearChanged) PerformanceRuntimeMetrics.recordOpenGlTextureUpload(clearBitmap.width, clearBitmap.height)
            if (lowChanged) PerformanceRuntimeMetrics.recordOpenGlTextureUpload(blurLowBitmap.width, blurLowBitmap.height)
            if (mediumChanged) PerformanceRuntimeMetrics.recordOpenGlTextureUpload(blurMediumBitmap.width, blurMediumBitmap.height)
            if (highChanged && blurHighBitmap !== blurMediumBitmap) {
                PerformanceRuntimeMetrics.recordOpenGlTextureUpload(blurHighBitmap.width, blurHighBitmap.height)
            }
            renderThread?.setBackdropTextures(clearBitmap, blurLowBitmap, blurMediumBitmap, blurHighBitmap)
        }
        return dirty
    }

    fun setBackdropBlurAmount(amount: Float): Boolean {
        val safeAmount = amount.coerceIn(0f, 4f)
        val dirty = abs(safeAmount - latestBlurAmount) > WEB_GLASS_BLUR_EPSILON
        latestBlurAmount = safeAmount
        if (dirty) renderThread?.setBackdropBlurAmount(safeAmount)
        return dirty
    }

    fun setGlassStyle(style: GlassBorderStyle, densityScale: Float): Boolean {
        val safeDensity = densityScale.coerceAtLeast(0.1f)
        val densityUnchanged = abs(safeDensity - latestDensityScale) <= 0.0001f
        if (style === latestStyle && densityUnchanged) return false
        val dirty = !densityUnchanged || style != latestStyle
        latestStyle = style
        latestDensityScale = safeDensity
        if (dirty) renderThread?.setGlassStyle(style, safeDensity)
        return dirty
    }

    fun requestRender() {
        renderThread?.requestRender()
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        renderThread?.shutdown()
        renderThread = WebOpenGLGlassEglThread(
            surface = Surface(surfaceTexture),
            width = width,
            height = height,
            onFirstFramePresented = StartupPerformanceGate::markOpenGlFirstFrameReady,
        ).also { thread ->
            thread.setFrameState(
                width = latestWidth,
                height = latestHeight,
                rectOffsetY = latestRectOffsetY,
                radius = latestRadius,
                intensity = latestIntensity,
                originX = latestOriginX,
                originY = latestOriginY,
                rootWidth = latestRootWidth,
                rootHeight = latestRootHeight,
                pressProgress = latestPressProgress,
                pressCenterX = latestPressCenterX,
                pressCenterY = latestPressCenterY,
                geometryDirty = true,
                samplingDirty = true,
                pressDirty = true,
            )
            thread.setBackdropBlurAmount(latestBlurAmount)
            thread.setGlassStyle(latestStyle, latestDensityScale)
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

    override fun onSurfaceTextureSizeChanged(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        renderThread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        renderThread?.shutdown()
        renderThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        onFramePresented()
    }
}

private class WebOpenGLGlassEglThread(
    private val surface: Surface,
    width: Int,
    height: Int,
    private val onFirstFramePresented: () -> Unit,
) : Thread("WebOpenGLGlassTextureThread") {
    private val renderer = WebOpenGLGlassRenderer()
    private val renderLock = Object()

    @Volatile private var running = true
    @Volatile private var pendingRender = true
    @Volatile private var viewportWidth = max(width, 1)
    @Volatile private var viewportHeight = max(height, 1)
    @Volatile private var sizeDirty = true

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var preservedSwap = false
    private var firstFramePresented = false
    private var metricsContextActive = false

    fun setFrameState(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float,
        intensity: Float,
        originX: Float,
        originY: Float,
        rootWidth: Float,
        rootHeight: Float,
        pressProgress: Float,
        pressCenterX: Float,
        pressCenterY: Float,
        geometryDirty: Boolean,
        samplingDirty: Boolean,
        pressDirty: Boolean,
    ) = renderer.setFrameState(
        width = width,
        height = height,
        rectOffsetY = rectOffsetY,
        radius = radius,
        intensity = intensity,
        originX = originX,
        originY = originY,
        rootWidth = rootWidth,
        rootHeight = rootHeight,
        pressProgress = pressProgress,
        pressCenterX = pressCenterX,
        pressCenterY = pressCenterY,
        geometryDirty = geometryDirty,
        samplingDirty = samplingDirty,
        pressDirty = pressDirty,
    )

    fun setBackdropTextures(clear: Bitmap, low: Bitmap, medium: Bitmap, high: Bitmap) =
        renderer.setBackdropTextures(clear, low, medium, high)

    fun setBackdropBlurAmount(amount: Float) = renderer.setBackdropBlurAmount(amount)

    fun setGlassStyle(style: GlassBorderStyle, densityScale: Float) =
        renderer.setGlassStyle(style, densityScale)

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
            renderer.setPartialClearSupported(preservedSwap)
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
            EGL14.EGL_WINDOW_BIT or EGL_SWAP_BEHAVIOR_PRESERVED_BIT_VALUE,
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

        preservedSwap = preservedConfig != null && EGL14.eglSurfaceAttrib(
            eglDisplay,
            eglSurface,
            EGL_SWAP_BEHAVIOR_VALUE,
            EGL_BUFFER_PRESERVED_VALUE,
        )
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
