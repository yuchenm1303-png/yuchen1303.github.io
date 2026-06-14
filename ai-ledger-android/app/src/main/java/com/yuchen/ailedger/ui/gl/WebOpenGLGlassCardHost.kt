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
import com.yuchen.ailedger.model.GlassBorderStyle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

private const val WEB_GLASS_SPEC_EPSILON_PX = 0.5f
private const val WEB_GLASS_ORIGIN_EPSILON_PX = 0.35f
private const val WEB_GLASS_INTENSITY_EPSILON = 0.006f
private const val WEB_GLASS_PRESS_EPSILON = 0.003f
private const val WEB_GLASS_PRESS_CENTER_EPSILON = 0.002f
private const val WEB_GLASS_BLUR_EPSILON = 0.002f
private const val WEB_GLASS_STABLE_SURFACE_FALLBACK_ANCHOR_Y = 0.44f
private const val WEB_GLASS_SCISSOR_PADDING_PX = 2

private const val DIRTY_SURFACE = 1
private const val DIRTY_GEOMETRY = 1 shl 1
private const val DIRTY_SAMPLING = 1 shl 2
private const val DIRTY_PRESS = 1 shl 3
private const val DIRTY_BLUR = 1 shl 4
private const val DIRTY_STYLE = 1 shl 5
private const val DIRTY_ALL =
    DIRTY_SURFACE or DIRTY_GEOMETRY or DIRTY_SAMPLING or DIRTY_PRESS or DIRTY_BLUR or DIRTY_STYLE

private const val GEOMETRY_WIDTH = 0
private const val GEOMETRY_HEIGHT = 1
private const val GEOMETRY_OFFSET_Y = 2
private const val GEOMETRY_RADIUS = 3
private const val GEOMETRY_INTENSITY = 4
private const val GEOMETRY_SIZE = 5

private const val SAMPLING_ORIGIN_X = 0
private const val SAMPLING_ORIGIN_Y = 1
private const val SAMPLING_ROOT_WIDTH = 2
private const val SAMPLING_ROOT_HEIGHT = 3
private const val SAMPLING_SIZE = 4

private const val PRESS_PROGRESS = 0
private const val PRESS_CENTER_X = 1
private const val PRESS_CENTER_Y = 2
private const val PRESS_SIZE = 3

private const val STYLE_MATERIAL_X = 0
private const val STYLE_MATERIAL_Y = 1
private const val STYLE_MATERIAL_Z = 2
private const val STYLE_BODY_LENS_A_X = 3
private const val STYLE_BODY_LENS_A_Y = 4
private const val STYLE_BODY_LENS_A_Z = 5
private const val STYLE_BODY_LENS_A_W = 6
private const val STYLE_BODY_LENS_B_X = 7
private const val STYLE_BODY_LENS_B_Y = 8
private const val STYLE_BODY_LENS_B_Z = 9
private const val STYLE_BODY_LENS_B_W = 10
private const val STYLE_BODY_X = 11
private const val STYLE_BODY_Y = 12
private const val STYLE_BODY_Z = 13
private const val STYLE_BODY_W = 14
private const val STYLE_SHOULDER_WIDTH = 15
private const val STYLE_SHOULDER_CAPTURE = 16
private const val STYLE_SHOULDER_ANGLE = 17
private const val STYLE_SHOULDER_FALLOFF = 18
private const val STYLE_SHOULDER_MATERIAL = 19
private const val STYLE_SHOULDER_FLOW = 20
private const val STYLE_SHOULDER_CORRECTION = 21
private const val STYLE_DISPERSION_STRENGTH = 22
private const val STYLE_DISPERSION_DISTANCE = 23
private const val STYLE_DISPERSION_EDGE_WIDTH = 24
private const val STYLE_DISPERSION_CONCENTRATION = 25
private const val STYLE_SIZE = 26

/**
 * 保留 Compose/OpenGL 固定宿主尺寸链；清晰纹理与三级模糊纹理只在内容变化时上传。
 */
internal class WebOpenGLGlassCardHostView(context: Context) : FrameLayout(context) {
    private val textureView = WebOpenGLGlassTextureView(context)
    private var stableSurfaceWidth = 1
    private var stableSurfaceHeight = 1
    private var stableSurfaceAnchorY = WEB_GLASS_STABLE_SURFACE_FALLBACK_ANCHOR_Y
    private var lastRootWidth = 1
    private var lastRootHeight = 1
    private var geometryAwaitingLayout = false
    private var latestGlassWidth = 1f
    private var latestGlassHeight = 1f
    private var latestRectOffsetY = 0f
    private var latestRadius = 24f
    private var latestIntensity = 1f
    private var latestOriginX = 0f
    private var latestOriginY = 0f
    private var latestRootWidth = 1f
    private var latestRootHeight = 1f

    init {
        clipChildren = true
        clipToPadding = true
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(textureView, LayoutParams(1, 1))
    }

    fun setStableSurfaceAnchor(anchorY: Float): Boolean {
        stableSurfaceAnchorY = anchorY.coerceIn(0f, 1f)
        return false
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

        val current = textureView.layoutParams as? LayoutParams
        val layoutDirty = current == null ||
            current.width != stableSurfaceWidth ||
            current.height != stableSurfaceHeight
        if (layoutDirty) {
            textureView.layoutParams = LayoutParams(stableSurfaceWidth, stableSurfaceHeight)
        }
        val dirty = sizeChanged || layoutDirty
        if (dirty) {
            geometryAwaitingLayout = true
            requestLayout()
        }
        return dirty
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        textureView.translationY = 0f
        textureView.layout(0, 0, stableSurfaceWidth, stableSurfaceHeight)
        geometryAwaitingLayout = false
        val glassDirty = syncGlassSpecToTexture()
        val samplingDirty = syncSamplingSpecToTexture()
        if (glassDirty || samplingDirty) textureView.requestRender()
    }

    fun setGlassSpec(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float,
        intensity: Float
    ): Boolean {
        latestGlassWidth = width.coerceAtLeast(1f)
        latestGlassHeight = height.coerceAtLeast(1f)
        latestRectOffsetY = rectOffsetY
        latestRadius = radius
        latestIntensity = intensity
        return if (geometryAwaitingLayout) false else syncGlassSpecToTexture()
    }

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float): Boolean {
        latestOriginX = originX
        latestOriginY = originY
        latestRootWidth = rootWidth.coerceAtLeast(1f)
        latestRootHeight = rootHeight.coerceAtLeast(1f)
        return if (geometryAwaitingLayout) false else syncSamplingSpecToTexture()
    }

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float): Boolean =
        textureView.setPressSpec(progress, centerX, centerY)

    fun setBackdropTextures(
        clearBitmap: Bitmap,
        blurLowBitmap: Bitmap,
        blurMediumBitmap: Bitmap,
        blurHighBitmap: Bitmap
    ): Boolean = textureView.setBackdropTextures(
        clearBitmap,
        blurLowBitmap,
        blurMediumBitmap,
        blurHighBitmap
    )

    fun setBackdropBlurAmount(amount: Float): Boolean = textureView.setBackdropBlurAmount(amount)

    fun setGlassStyle(style: GlassBorderStyle, densityScale: Float): Boolean =
        textureView.setGlassStyle(style, densityScale)

    fun requestRender() {
        if (!geometryAwaitingLayout) textureView.requestRender()
    }

    private fun syncGlassSpecToTexture(): Boolean = textureView.setGlassSpec(
        width = latestGlassWidth,
        height = latestGlassHeight,
        rectOffsetY = latestRectOffsetY,
        radius = latestRadius,
        intensity = latestIntensity
    )

    private fun syncSamplingSpecToTexture(): Boolean = textureView.setSamplingSpec(
        originX = latestOriginX,
        originY = latestOriginY,
        rootWidth = latestRootWidth,
        rootHeight = latestRootHeight
    )
}

private class WebOpenGLGlassTextureView(
    context: Context
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

    fun setGlassSpec(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float,
        intensity: Float
    ): Boolean {
        val nextWidth = width.coerceAtLeast(1f)
        val nextHeight = height.coerceAtLeast(1f)
        val nextIntensity = intensity.coerceIn(0.35f, 1.35f)
        val dirty = abs(nextWidth - latestWidth) > WEB_GLASS_SPEC_EPSILON_PX ||
            abs(nextHeight - latestHeight) > WEB_GLASS_SPEC_EPSILON_PX ||
            abs(rectOffsetY - latestRectOffsetY) > WEB_GLASS_SPEC_EPSILON_PX ||
            abs(radius - latestRadius) > WEB_GLASS_SPEC_EPSILON_PX ||
            abs(nextIntensity - latestIntensity) > WEB_GLASS_INTENSITY_EPSILON
        latestWidth = nextWidth
        latestHeight = nextHeight
        latestRectOffsetY = rectOffsetY
        latestRadius = radius
        latestIntensity = nextIntensity
        if (dirty) {
            renderThread?.setGlassSpec(nextWidth, nextHeight, rectOffsetY, radius, nextIntensity)
        }
        return dirty
    }

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float): Boolean {
        val nextRootWidth = rootWidth.coerceAtLeast(1f)
        val nextRootHeight = rootHeight.coerceAtLeast(1f)
        val dirty = abs(originX - latestOriginX) > WEB_GLASS_ORIGIN_EPSILON_PX ||
            abs(originY - latestOriginY) > WEB_GLASS_ORIGIN_EPSILON_PX ||
            abs(nextRootWidth - latestRootWidth) > WEB_GLASS_SPEC_EPSILON_PX ||
            abs(nextRootHeight - latestRootHeight) > WEB_GLASS_SPEC_EPSILON_PX
        latestOriginX = originX
        latestOriginY = originY
        latestRootWidth = nextRootWidth
        latestRootHeight = nextRootHeight
        if (dirty) {
            renderThread?.setSamplingSpec(originX, originY, nextRootWidth, nextRootHeight)
        }
        return dirty
    }

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float): Boolean {
        val safeProgress = progress.coerceIn(0f, 1f)
        val safeCenterX = centerX.coerceIn(0f, 1f)
        val safeCenterY = centerY.coerceIn(0f, 1f)
        val dirty = abs(safeProgress - latestPressProgress) > WEB_GLASS_PRESS_EPSILON ||
            abs(safeCenterX - latestPressCenterX) > WEB_GLASS_PRESS_CENTER_EPSILON ||
            abs(safeCenterY - latestPressCenterY) > WEB_GLASS_PRESS_CENTER_EPSILON
        latestPressProgress = safeProgress
        latestPressCenterX = safeCenterX
        latestPressCenterY = safeCenterY
        if (dirty) renderThread?.setPressSpec(safeProgress, safeCenterX, safeCenterY)
        return dirty
    }

    fun setBackdropTextures(
        clearBitmap: Bitmap,
        blurLowBitmap: Bitmap,
        blurMediumBitmap: Bitmap,
        blurHighBitmap: Bitmap
    ): Boolean {
        val dirty = clearBitmap !== latestClearBitmap ||
            blurLowBitmap !== latestBlurLowBitmap ||
            blurMediumBitmap !== latestBlurMediumBitmap ||
            blurHighBitmap !== latestBlurHighBitmap
        latestClearBitmap = clearBitmap
        latestBlurLowBitmap = blurLowBitmap
        latestBlurMediumBitmap = blurMediumBitmap
        latestBlurHighBitmap = blurHighBitmap
        if (dirty) {
            renderThread?.setBackdropTextures(
                clearBitmap,
                blurLowBitmap,
                blurMediumBitmap,
                blurHighBitmap
            )
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
        val dirty = style != latestStyle || abs(safeDensity - latestDensityScale) > 0.0001f
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
            height = height
        ).also { thread ->
            thread.setGlassSpec(
                latestWidth,
                latestHeight,
                latestRectOffsetY,
                latestRadius,
                latestIntensity
            )
            thread.setSamplingSpec(
                latestOriginX,
                latestOriginY,
                latestRootWidth,
                latestRootHeight
            )
            thread.setPressSpec(
                latestPressProgress,
                latestPressCenterX,
                latestPressCenterY
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
        height: Int
    ) {
        renderThread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        renderThread?.shutdown()
        renderThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
}

private class WebOpenGLGlassEglThread(
    private val surface: Surface,
    width: Int,
    height: Int
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

    fun setGlassSpec(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float,
        intensity: Float
    ) = renderer.setGlassSpec(width, height, rectOffsetY, radius, intensity)

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float) =
        renderer.setSamplingSpec(originX, originY, rootWidth, rootHeight)

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float) =
        renderer.setPressSpec(progress, centerX, centerY)

    fun setBackdropTextures(clear: Bitmap, low: Bitmap, medium: Bitmap, high: Bitmap) =
        renderer.setBackdropTextures(clear, low, medium, high)

    fun setBackdropBlurAmount(amount: Float) = renderer.setBackdropBlurAmount(amount)

    fun setGlassStyle(style: GlassBorderStyle, densityScale: Float) =
        renderer.setGlassStyle(style, densityScale)

    fun requestRender() {
        synchronized(renderLock) {
            pendingRender = true
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
        requestRender()
    }

    override fun run() {
        try {
            initEgl()
            renderer.onSurfaceCreated()
            renderer.onSurfaceChanged(viewportWidth, viewportHeight)
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
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
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
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(
            EGL14.eglChooseConfig(
                eglDisplay,
                attributes,
                0,
                configs,
                0,
                configs.size,
                count,
                0
            )
        )
        val config = configs[0] ?: error("No EGL config")
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            config,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
            }
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglSurface = EGL14.EGL_NO_SURFACE
        eglContext = EGL14.EGL_NO_CONTEXT
    }
}

private class WebOpenGLGlassRenderer {
    private val textureLock = Any()
    private val specLock = Any()

    private var pendingClearBitmap: Bitmap? = null
    private var pendingBlurLowBitmap: Bitmap? = null
    private var pendingBlurMediumBitmap: Bitmap? = null
    private var pendingBlurHighBitmap: Bitmap? = null

    private var clearTextureId = 0
    private var blurLowTextureId = 0
    private var blurMediumTextureId = 0
    private var blurHighTextureId = 0
    private val textureWidths = IntArray(4)
    private val textureHeights = IntArray(4)
    private var textureReady = false

    private val geometryState = FloatArray(GEOMETRY_SIZE).apply {
        this[GEOMETRY_WIDTH] = 1f
        this[GEOMETRY_HEIGHT] = 1f
        this[GEOMETRY_RADIUS] = 24f
        this[GEOMETRY_INTENSITY] = 1f
    }
    private val samplingState = FloatArray(SAMPLING_SIZE).apply {
        this[SAMPLING_ROOT_WIDTH] = 1f
        this[SAMPLING_ROOT_HEIGHT] = 1f
    }
    private val pressState = FloatArray(PRESS_SIZE).apply {
        this[PRESS_CENTER_X] = 0.5f
        this[PRESS_CENTER_Y] = 0.5f
    }
    private val styleState = FloatArray(STYLE_SIZE)

    private val geometrySnapshot = FloatArray(GEOMETRY_SIZE)
    private val samplingSnapshot = FloatArray(SAMPLING_SIZE)
    private val pressSnapshot = FloatArray(PRESS_SIZE)
    private val styleSnapshot = FloatArray(STYLE_SIZE)

    private var pendingDirtyMask = DIRTY_ALL
    private var blurAmount = 0f
    private var blurSnapshot = 0f

    private var drawCardWidth = 1f
    private var drawCardHeight = 1f
    private var drawRectOffsetY = 0f

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

    init {
        applyStyleValues(GlassBorderStyle(), 1f)
    }

    fun setGlassSpec(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float,
        intensity: Float
    ) {
        synchronized(specLock) {
            geometryState[GEOMETRY_WIDTH] = width.coerceAtLeast(1f)
            geometryState[GEOMETRY_HEIGHT] = height.coerceAtLeast(1f)
            geometryState[GEOMETRY_OFFSET_Y] = rectOffsetY
            geometryState[GEOMETRY_RADIUS] = radius
            geometryState[GEOMETRY_INTENSITY] = intensity
            pendingDirtyMask = pendingDirtyMask or DIRTY_GEOMETRY
        }
    }

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float) {
        synchronized(specLock) {
            samplingState[SAMPLING_ORIGIN_X] = originX
            samplingState[SAMPLING_ORIGIN_Y] = originY
            samplingState[SAMPLING_ROOT_WIDTH] = rootWidth.coerceAtLeast(1f)
            samplingState[SAMPLING_ROOT_HEIGHT] = rootHeight.coerceAtLeast(1f)
            pendingDirtyMask = pendingDirtyMask or DIRTY_SAMPLING
        }
    }

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float) {
        synchronized(specLock) {
            pressState[PRESS_PROGRESS] = progress.coerceIn(0f, 1f)
            pressState[PRESS_CENTER_X] = centerX.coerceIn(0f, 1f)
            pressState[PRESS_CENTER_Y] = centerY.coerceIn(0f, 1f)
            pendingDirtyMask = pendingDirtyMask or DIRTY_PRESS
        }
    }

    fun setBackdropTextures(clear: Bitmap, low: Bitmap, medium: Bitmap, high: Bitmap) {
        synchronized(textureLock) {
            pendingClearBitmap = clear
            pendingBlurLowBitmap = low
            pendingBlurMediumBitmap = medium
            pendingBlurHighBitmap = high
        }
    }

    fun setBackdropBlurAmount(amount: Float) {
        synchronized(specLock) {
            blurAmount = amount.coerceIn(0f, 4f)
            pendingDirtyMask = pendingDirtyMask or DIRTY_BLUR
        }
    }

    fun setGlassStyle(style: GlassBorderStyle, densityScale: Float) {
        synchronized(specLock) {
            applyStyleValues(style, densityScale.coerceAtLeast(0.1f))
            pendingDirtyMask = pendingDirtyMask or DIRTY_STYLE
        }
    }

    fun onSurfaceCreated() {
        program = buildProgram(WEB_VERTEX_SHADER, WebOpenGLGlassShaders.FRAGMENT_SHADER)
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

        val textures = IntArray(4)
        GLES20.glGenTextures(4, textures, 0)
        clearTextureId = textures[0]
        blurLowTextureId = textures[1]
        blurMediumTextureId = textures[2]
        blurHighTextureId = textures[3]
        configureTexture(0, GLES20.GL_TEXTURE0, clearTextureId)
        configureTexture(1, GLES20.GL_TEXTURE1, blurLowTextureId)
        configureTexture(2, GLES20.GL_TEXTURE2, blurMediumTextureId)
        configureTexture(3, GLES20.GL_TEXTURE3, blurHighTextureId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        val buffers = IntArray(1)
        GLES20.glGenBuffers(1, buffers, 0)
        quadBufferId = buffers[0]
        val quadVertices = ByteBuffer
            .allocateDirect(FULLSCREEN_QUAD.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(FULLSCREEN_QUAD)
                position(0)
            }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBufferId)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            FULLSCREEN_QUAD.size * 4,
            quadVertices,
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        synchronized(specLock) {
            pendingDirtyMask = pendingDirtyMask or DIRTY_ALL
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        synchronized(specLock) {
            pendingDirtyMask = pendingDirtyMask or DIRTY_SURFACE
        }
    }

    fun onDrawFrame() {
        uploadPendingTexturesIfNeeded()
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return

        val dirtyMask: Int
        synchronized(specLock) {
            dirtyMask = pendingDirtyMask
            pendingDirtyMask = 0
            if (dirtyMask and DIRTY_GEOMETRY != 0) {
                geometryState.copyInto(geometrySnapshot)
            }
            if (dirtyMask and DIRTY_SAMPLING != 0) {
                samplingState.copyInto(samplingSnapshot)
            }
            if (dirtyMask and DIRTY_PRESS != 0) {
                pressState.copyInto(pressSnapshot)
            }
            if (dirtyMask and DIRTY_BLUR != 0) {
                blurSnapshot = blurAmount
            }
            if (dirtyMask and DIRTY_STYLE != 0) {
                styleState.copyInto(styleSnapshot)
            }
        }

        if (dirtyMask and DIRTY_SURFACE != 0) {
            GLES20.glUniform2f(
                resolutionHandle,
                viewportWidth.toFloat(),
                viewportHeight.toFloat()
            )
        }
        if (dirtyMask and DIRTY_GEOMETRY != 0) {
            drawCardWidth = geometrySnapshot[GEOMETRY_WIDTH]
            drawCardHeight = geometrySnapshot[GEOMETRY_HEIGHT]
            drawRectOffsetY = geometrySnapshot[GEOMETRY_OFFSET_Y]
            GLES20.glUniform4f(
                rectHandle,
                0f,
                drawRectOffsetY,
                drawCardWidth,
                drawCardHeight
            )
            GLES20.glUniform1f(
                radiusHandle,
                geometrySnapshot[GEOMETRY_RADIUS].coerceIn(
                    2f,
                    max(drawCardWidth, drawCardHeight)
                )
            )
            GLES20.glUniform1f(
                intensityHandle,
                geometrySnapshot[GEOMETRY_INTENSITY].coerceIn(0.35f, 1.35f)
            )
        }
        if (dirtyMask and DIRTY_SAMPLING != 0) {
            GLES20.glUniform2f(
                cardOriginHandle,
                samplingSnapshot[SAMPLING_ORIGIN_X],
                samplingSnapshot[SAMPLING_ORIGIN_Y]
            )
            GLES20.glUniform2f(
                rootResolutionHandle,
                samplingSnapshot[SAMPLING_ROOT_WIDTH],
                samplingSnapshot[SAMPLING_ROOT_HEIGHT]
            )
        }
        if (dirtyMask and DIRTY_PRESS != 0) {
            GLES20.glUniform4f(
                pressHandle,
                pressSnapshot[PRESS_PROGRESS],
                pressSnapshot[PRESS_CENTER_X],
                pressSnapshot[PRESS_CENTER_Y],
                0f
            )
        }
        if (dirtyMask and DIRTY_BLUR != 0) {
            GLES20.glUniform1f(blurAmountHandle, blurSnapshot)
        }
        if (dirtyMask and DIRTY_STYLE != 0) {
            GLES20.glUniform4f(
                materialHandle,
                styleSnapshot[STYLE_MATERIAL_X],
                styleSnapshot[STYLE_MATERIAL_Y],
                styleSnapshot[STYLE_MATERIAL_Z],
                0f
            )
            GLES20.glUniform4f(
                bodyLensAHandle,
                styleSnapshot[STYLE_BODY_LENS_A_X],
                styleSnapshot[STYLE_BODY_LENS_A_Y],
                styleSnapshot[STYLE_BODY_LENS_A_Z],
                styleSnapshot[STYLE_BODY_LENS_A_W]
            )
            GLES20.glUniform4f(
                bodyLensBHandle,
                styleSnapshot[STYLE_BODY_LENS_B_X],
                styleSnapshot[STYLE_BODY_LENS_B_Y],
                styleSnapshot[STYLE_BODY_LENS_B_Z],
                styleSnapshot[STYLE_BODY_LENS_B_W]
            )
            GLES20.glUniform4f(
                bodyHandle,
                styleSnapshot[STYLE_BODY_X],
                styleSnapshot[STYLE_BODY_Y],
                styleSnapshot[STYLE_BODY_Z],
                styleSnapshot[STYLE_BODY_W]
            )
            GLES20.glUniform4f(
                shoulderHandle,
                styleSnapshot[STYLE_SHOULDER_WIDTH],
                styleSnapshot[STYLE_SHOULDER_ANGLE],
                styleSnapshot[STYLE_SHOULDER_FALLOFF],
                styleSnapshot[STYLE_SHOULDER_MATERIAL]
            )
            GLES20.glUniform4f(
                shoulderFlowHandle,
                styleSnapshot[STYLE_SHOULDER_CAPTURE],
                styleSnapshot[STYLE_SHOULDER_FLOW],
                styleSnapshot[STYLE_SHOULDER_CORRECTION],
                0f
            )
            GLES20.glUniform4f(
                dispersionHandle,
                styleSnapshot[STYLE_DISPERSION_STRENGTH],
                styleSnapshot[STYLE_DISPERSION_DISTANCE],
                styleSnapshot[STYLE_DISPERSION_EDGE_WIDTH],
                styleSnapshot[STYLE_DISPERSION_CONCENTRATION]
            )
        }

        if (!applyGlassScissor()) return
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun onRelease() {
        val textures = intArrayOf(
            clearTextureId,
            blurLowTextureId,
            blurMediumTextureId,
            blurHighTextureId
        )
        if (textures.any { it != 0 }) GLES20.glDeleteTextures(4, textures, 0)
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

    private fun applyStyleValues(style: GlassBorderStyle, densityScale: Float) {
        styleState[STYLE_MATERIAL_X] = style.newOpenGlBodyVisibility.coerceIn(0f, 20f)
        styleState[STYLE_MATERIAL_Y] = style.newOpenGlBodyMaxAlpha.coerceIn(0f, 1f)
        styleState[STYLE_MATERIAL_Z] = style.newOpenGlBodyOutputBrightness.coerceIn(0.2f, 2.8f)
        styleState[STYLE_BODY_LENS_A_X] =
            style.newOpenGlBodyLensBasePull.coerceIn(-300f, 300f) * densityScale
        styleState[STYLE_BODY_LENS_A_Y] =
            style.newOpenGlBodyLensPullDp.coerceIn(-600f, 600f) * densityScale
        styleState[STYLE_BODY_LENS_A_Z] =
            style.newOpenGlBodyLensConcentration.coerceIn(-10f, 10f)
        styleState[STYLE_BODY_LENS_A_W] =
            style.newOpenGlBodyLensCornerBoost.coerceIn(0f, 200f)
        styleState[STYLE_BODY_LENS_B_X] =
            style.newOpenGlBodyLensExtraDistance.coerceIn(0f, 200f) * densityScale
        styleState[STYLE_BODY_LENS_B_Y] =
            style.newOpenGlBodyLensReachDp.coerceIn(8f, 180f) * densityScale
        styleState[STYLE_BODY_LENS_B_Z] = style.newOpenGlBodyLensDark.coerceIn(-10f, 10f)
        styleState[STYLE_BODY_LENS_B_W] = style.newOpenGlBodyLensDebug.coerceIn(0f, 1f)
        styleState[STYLE_BODY_X] = style.newOpenGlBodyWidth.coerceIn(0.18f, 1.5f)
        styleState[STYLE_BODY_Y] = style.newOpenGlBodyCurve.coerceIn(0.2f, 3.2f)
        styleState[STYLE_BODY_Z] = style.newOpenGlBodyGain.coerceIn(0f, 900f)
        styleState[STYLE_BODY_W] = style.newOpenGlBrightness.coerceIn(0.4f, 2.2f)
        styleState[STYLE_SHOULDER_WIDTH] =
            style.newOpenGlShoulderWidthDp.coerceIn(4f, 96f) * densityScale
        styleState[STYLE_SHOULDER_CAPTURE] =
            style.newOpenGlShoulderCaptureWidthDp.coerceIn(4f, 192f) * densityScale
        styleState[STYLE_SHOULDER_ANGLE] =
            style.newOpenGlShoulderMaxAngleDeg.coerceIn(0f, 89.5f)
        styleState[STYLE_SHOULDER_FALLOFF] =
            style.newOpenGlShoulderFalloffRoundness.coerceIn(0f, 1f)
        styleState[STYLE_SHOULDER_MATERIAL] =
            style.newOpenGlShoulderMaterialStrength.coerceIn(0f, 4f)
        styleState[STYLE_SHOULDER_FLOW] =
            style.newOpenGlShoulderTangentialFlowStrength.coerceIn(0f, 2.4f)
        styleState[STYLE_SHOULDER_CORRECTION] =
            style.newOpenGlShoulderTangentialCorrection.coerceIn(0f, 1f)
        styleState[STYLE_DISPERSION_STRENGTH] =
            style.newOpenGlDispersionStrength.coerceIn(0f, 1.5f)
        styleState[STYLE_DISPERSION_DISTANCE] =
            style.newOpenGlDispersionDistanceDp.coerceIn(0f, 8f) * densityScale
        styleState[STYLE_DISPERSION_EDGE_WIDTH] =
            style.newOpenGlDispersionEdgeWidthDp.coerceIn(2f, 64f) * densityScale
        styleState[STYLE_DISPERSION_CONCENTRATION] =
            style.newOpenGlDispersionConcentration.coerceIn(0.25f, 4f)
    }

    private fun applyGlassScissor(): Boolean {
        val top = (drawRectOffsetY.toInt() - WEB_GLASS_SCISSOR_PADDING_PX).coerceAtLeast(0)
        val right = ceil((drawCardWidth + WEB_GLASS_SCISSOR_PADDING_PX).toDouble())
            .toInt()
            .coerceIn(0, viewportWidth)
        val bottom = ceil(
            (drawRectOffsetY + drawCardHeight + WEB_GLASS_SCISSOR_PADDING_PX).toDouble()
        )
            .toInt()
            .coerceIn(0, viewportHeight)
        val height = (bottom - top).coerceAtLeast(0)
        if (right <= 0 || height <= 0) return false
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(0, viewportHeight - bottom, right, height)
        return true
    }

    private fun uploadPendingTexturesIfNeeded() {
        var clear: Bitmap? = null
        var low: Bitmap? = null
        var medium: Bitmap? = null
        var high: Bitmap? = null
        synchronized(textureLock) {
            clear = pendingClearBitmap
            low = pendingBlurLowBitmap
            medium = pendingBlurMediumBitmap
            high = pendingBlurHighBitmap
            pendingClearBitmap = null
            pendingBlurLowBitmap = null
            pendingBlurMediumBitmap = null
            pendingBlurHighBitmap = null
        }
        val clearBitmap = clear ?: return
        val lowBitmap = low ?: return
        val mediumBitmap = medium ?: return
        val highBitmap = high ?: return
        uploadTexture(0, GLES20.GL_TEXTURE0, clearTextureId, clearBitmap)
        uploadTexture(1, GLES20.GL_TEXTURE1, blurLowTextureId, lowBitmap)
        uploadTexture(2, GLES20.GL_TEXTURE2, blurMediumTextureId, mediumBitmap)
        uploadTexture(3, GLES20.GL_TEXTURE3, blurHighTextureId, highBitmap)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (!textureReady) {
            textureReady = true
            GLES20.glUniform1f(textureReadyHandle, 1f)
        }
    }

    private fun uploadTexture(index: Int, textureUnit: Int, textureId: Int, bitmap: Bitmap) {
        GLES20.glActiveTexture(textureUnit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        if (textureWidths[index] == bitmap.width && textureHeights[index] == bitmap.height) {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        } else {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            textureWidths[index] = bitmap.width
            textureHeights[index] = bitmap.height
        }
    }

    private fun configureTexture(index: Int, textureUnit: Int, textureId: Int) {
        GLES20.glActiveTexture(textureUnit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        textureWidths[index] = 0
        textureHeights[index] = 0
    }
}

private fun buildProgram(vertex: String, fragment: String): Int {
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

private val FULLSCREEN_QUAD = floatArrayOf(
    -1f, -1f,
    1f, -1f,
    -1f, 1f,
    1f, 1f
)

private const val WEB_VERTEX_SHADER = """
    attribute vec2 aPosition;
    void main(){ gl_Position=vec4(aPosition,0.0,1.0); }
"""
