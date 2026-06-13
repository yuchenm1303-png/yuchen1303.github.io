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
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val WEB_GLASS_SPEC_EPSILON_PX = 0.5f
private const val WEB_GLASS_ORIGIN_EPSILON_PX = 0.35f
private const val WEB_GLASS_INTENSITY_EPSILON = 0.006f
private const val WEB_GLASS_PRESS_EPSILON = 0.003f
private const val WEB_GLASS_PRESS_CENTER_EPSILON = 0.002f
private const val WEB_GLASS_STABLE_SURFACE_FALLBACK_ANCHOR_Y = 0.44f

/**
 * Stable Android host for the exact web-preview renderer.
 *
 * Layout, sampling origin, viewport inset and press forwarding deliberately keep the existing
 * Compose/OpenGL stability chain untouched. Only the renderer behind this host is the new web glass.
 */
internal class WebOpenGLGlassCardHostView(context: Context) : FrameLayout(context) {
    private val textureView = WebOpenGLGlassTextureView(context)
    private var stableSurfaceWidth = 1
    private var stableSurfaceHeight = 1
    private var visibleSurfaceWidth = 1
    private var visibleSurfaceHeight = 1
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
        visibleSurfaceWidth = safeWidth
        visibleSurfaceHeight = safeHeight

        val targetWidth = if (rootSizeChanged) safeWidth else max(stableSurfaceWidth, safeWidth)
        val targetHeight = if (rootSizeChanged) safeHeight else max(stableSurfaceHeight, safeHeight)
        val sizeChanged = targetWidth != stableSurfaceWidth || targetHeight != stableSurfaceHeight
        stableSurfaceWidth = targetWidth
        stableSurfaceHeight = targetHeight

        val layoutParams = textureView.layoutParams as? LayoutParams
        val layoutParamDirty = layoutParams == null ||
            layoutParams.width != stableSurfaceWidth ||
            layoutParams.height != stableSurfaceHeight
        if (layoutParamDirty) {
            textureView.layoutParams = LayoutParams(stableSurfaceWidth, stableSurfaceHeight)
        }
        val dirty = sizeChanged || layoutParamDirty
        if (dirty) {
            geometryAwaitingLayout = true
            requestLayout()
        }
        return dirty
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        visibleSurfaceWidth = (right - left).coerceAtLeast(1)
        visibleSurfaceHeight = (bottom - top).coerceAtLeast(1)
        // The existing stable host deliberately keeps a fixed child surface. The visible glass rect
        // moves inside that surface through rectOffsetY; changing this would reintroduce bottom jitter.
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

    fun setBackdropTexture(bitmap: Bitmap): Boolean = textureView.setBackdropTexture(bitmap)

    fun setGlassStyle(style: GlassBorderStyle): Boolean = textureView.setGlassStyle(style)

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

private class WebOpenGLGlassTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: WebOpenGLGlassEglThread? = null
    private var latestBackdropBitmap: Bitmap? = null
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
    private var latestStyleSignature = latestStyle.webOpenGlSignature()

    init {
        isOpaque = false
        alpha = 1f
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setGlassSpec(width: Float, height: Float, rectOffsetY: Float, radius: Float, intensity: Float): Boolean {
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
            renderThread?.setGlassSpec(
                latestWidth,
                latestHeight,
                latestRectOffsetY,
                latestRadius,
                latestIntensity
            )
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
            renderThread?.setSamplingSpec(
                latestOriginX,
                latestOriginY,
                latestRootWidth,
                latestRootHeight
            )
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

    fun setBackdropTexture(bitmap: Bitmap): Boolean {
        val dirty = bitmap !== latestBackdropBitmap
        latestBackdropBitmap = bitmap
        if (dirty) renderThread?.setBackdropTexture(bitmap)
        return dirty
    }

    fun setGlassStyle(style: GlassBorderStyle): Boolean {
        val signature = style.webOpenGlSignature()
        val dirty = signature != latestStyleSignature
        latestStyle = style
        latestStyleSignature = signature
        if (dirty) renderThread?.setGlassStyle(style)
        return dirty
    }

    fun requestRender() {
        renderThread?.requestRender()
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        renderThread?.shutdown()
        renderThread = WebOpenGLGlassEglThread(Surface(surfaceTexture), width, height).also { thread ->
            thread.setGlassSpec(latestWidth, latestHeight, latestRectOffsetY, latestRadius, latestIntensity)
            thread.setSamplingSpec(latestOriginX, latestOriginY, latestRootWidth, latestRootHeight)
            thread.setPressSpec(latestPressProgress, latestPressCenterX, latestPressCenterY)
            thread.setGlassStyle(latestStyle)
            latestBackdropBitmap?.let(thread::setBackdropTexture)
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

    fun setGlassSpec(width: Float, height: Float, rectOffsetY: Float, radius: Float, intensity: Float) =
        renderer.setGlassSpec(width, height, rectOffsetY, radius, intensity)

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float) =
        renderer.setSamplingSpec(originX, originY, rootWidth, rootHeight)

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float) =
        renderer.setPressSpec(progress, centerX, centerY)

    fun setBackdropTexture(bitmap: Bitmap) = renderer.setBackdropTexture(bitmap)

    fun setGlassStyle(style: GlassBorderStyle) = renderer.setGlassStyle(style)

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
        check(EGL14.eglChooseConfig(eglDisplay, attributes, 0, configs, 0, configs.size, count, 0))
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
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglSurface = EGL14.EGL_NO_SURFACE
        eglContext = EGL14.EGL_NO_CONTEXT
    }
}

private data class WebOpenGLGlassDrawSpec(
    val cardWidth: Float,
    val cardHeight: Float,
    val rectOffsetY: Float,
    val cardRadius: Float,
    val cardIntensity: Float,
    val cardOriginX: Float,
    val cardOriginY: Float,
    val rootWidth: Float,
    val rootHeight: Float,
    val style: GlassBorderStyle
)

private data class WebOpenGLGlassStyleSignature(
    val glassIntensity: Float,
    val visibility: Float,
    val maxAlpha: Float,
    val edgeBrightness: Float,
    val openGlPullScale: Float,
    val edgePullDp: Float,
    val compression: Float,
    val corner: Float,
    val sampleRadius: Float,
    val ringWidth: Float,
    val dark: Float,
    val debugLine: Float,
    val bodyWidth: Float,
    val bodyCurve: Float,
    val bodyGain: Float,
    val bodyBandPos: Float,
    val bodyBandWidth: Float,
    val bodyBandGain: Float,
    val brightness: Float
)

private fun GlassBorderStyle.webOpenGlSignature(): WebOpenGLGlassStyleSignature =
    WebOpenGLGlassStyleSignature(
        glassIntensity = newOpenGlGlassIntensity.coerceIn(0.35f, 1.35f),
        visibility = openGlVisibility.coerceIn(0f, 20f),
        maxAlpha = openGlMaxAlpha.coerceIn(0f, 1f),
        edgeBrightness = edgeBrightness.coerceIn(-5f, 5f),
        openGlPullScale = openGlPullScale.coerceIn(-300f, 300f),
        edgePullDp = edgePullDp.coerceIn(-600f, 600f),
        compression = openGlCompressionScale.coerceIn(-10f, 10f),
        corner = openGlCornerScale.coerceIn(0f, 200f),
        sampleRadius = openGlSampleRadiusScale.coerceIn(0f, 200f),
        ringWidth = ringWidthDp.coerceIn(0f, 300f),
        dark = openGlDarkScale.coerceIn(-10f, 10f),
        debugLine = openGlDebugLineAlpha.coerceIn(0f, 1f),
        bodyWidth = newOpenGlBodyWidth.coerceIn(0.18f, 1.5f),
        bodyCurve = newOpenGlBodyCurve.coerceIn(0.20f, 3.2f),
        bodyGain = newOpenGlBodyGain.coerceIn(0f, 900f),
        bodyBandPos = newOpenGlBodyBandPos.coerceIn(0.55f, 0.98f),
        bodyBandWidth = newOpenGlBodyBandWidth.coerceIn(0.015f, 0.8f),
        bodyBandGain = newOpenGlBodyBandGain.coerceIn(0f, 1500f),
        brightness = newOpenGlBrightness.coerceIn(0.4f, 2.2f)
    )

private data class FlowTextureKey(
    val width: Int,
    val height: Int,
    val radiusQuarterPx: Int
)

private class WebOpenGLGlassRenderer {
    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(FULLSCREEN_QUAD.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(FULLSCREEN_QUAD)
            position(0)
        }
    private val textureLock = Any()
    private val specLock = Any()
    private var pendingBitmap: Bitmap? = null
    private var backdropTextureId = 0
    private var flowTextureId = 0
    private var textureReady = false
    private var flowTextureReady = false
    private var flowTextureKey: FlowTextureKey? = null
    private var flowDepthPx = 1f
    private var cardWidth = 1f
    private var cardHeight = 1f
    private var rectOffsetY = 0f
    private var cardRadius = 24f
    private var cardIntensity = 1f
    private var cardOriginX = 0f
    private var cardOriginY = 0f
    private var rootWidth = 1f
    private var rootHeight = 1f
    // Kept to preserve the established press forwarding chain. The web shader itself remains exact;
    // Compose still applies the shell scale/sink/rebound and all surface optics above this layer.
    private var pressProgress = 0f
    private var pressCenterX = 0.5f
    private var pressCenterY = 0.5f
    private var style = GlassBorderStyle()
    private var program = 0
    private var positionHandle = 0
    private var resolutionHandle = 0
    private var cardOriginHandle = 0
    private var rootResolutionHandle = 0
    private var rectHandle = 0
    private var radiusHandle = 0
    private var intensityHandle = 0
    private var textureReadyHandle = 0
    private var textureHandle = 0
    private var flowTextureHandle = 0
    private var flowDepthHandle = 0
    private var materialHandle = 0
    private var oldAHandle = 0
    private var oldBHandle = 0
    private var bodyHandle = 0
    private var bandHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1

    fun setGlassSpec(width: Float, height: Float, rectOffsetY: Float, radius: Float, intensity: Float) {
        synchronized(specLock) {
            cardWidth = width.coerceAtLeast(1f)
            cardHeight = height.coerceAtLeast(1f)
            this.rectOffsetY = rectOffsetY
            cardRadius = radius
            cardIntensity = intensity
        }
    }

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float) {
        synchronized(specLock) {
            cardOriginX = originX
            cardOriginY = originY
            this.rootWidth = rootWidth.coerceAtLeast(1f)
            this.rootHeight = rootHeight.coerceAtLeast(1f)
        }
    }

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float) {
        synchronized(specLock) {
            pressProgress = progress.coerceIn(0f, 1f)
            pressCenterX = centerX.coerceIn(0f, 1f)
            pressCenterY = centerY.coerceIn(0f, 1f)
        }
    }

    fun setBackdropTexture(bitmap: Bitmap) {
        synchronized(textureLock) { pendingBitmap = bitmap }
    }

    fun setGlassStyle(style: GlassBorderStyle) {
        synchronized(specLock) { this.style = style }
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
        textureReadyHandle = GLES20.glGetUniformLocation(program, "uTextureReady")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        flowTextureHandle = GLES20.glGetUniformLocation(program, "uFlow")
        flowDepthHandle = GLES20.glGetUniformLocation(program, "uFlowDepth")
        materialHandle = GLES20.glGetUniformLocation(program, "uMaterial")
        oldAHandle = GLES20.glGetUniformLocation(program, "uOldA")
        oldBHandle = GLES20.glGetUniformLocation(program, "uOldB")
        bodyHandle = GLES20.glGetUniformLocation(program, "uBody")
        bandHandle = GLES20.glGetUniformLocation(program, "uBodyBand")

        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        backdropTextureId = textures[0]
        flowTextureId = textures[1]
        configureTexture(backdropTextureId)
        configureTexture(flowTextureId)
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
        uploadPendingTextureIfNeeded()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return

        val drawSpec = synchronized(specLock) {
            WebOpenGLGlassDrawSpec(
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                rectOffsetY = rectOffsetY,
                cardRadius = cardRadius,
                cardIntensity = cardIntensity,
                cardOriginX = cardOriginX,
                cardOriginY = cardOriginY,
                rootWidth = rootWidth,
                rootHeight = rootHeight,
                style = style
            )
        }
        ensureFlowTexture(drawSpec)
        val s = drawSpec.style

        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES20.glUniform2f(cardOriginHandle, drawSpec.cardOriginX, drawSpec.cardOriginY)
        GLES20.glUniform2f(rootResolutionHandle, drawSpec.rootWidth, drawSpec.rootHeight)
        GLES20.glUniform4f(
            rectHandle,
            0f,
            drawSpec.rectOffsetY,
            drawSpec.cardWidth,
            drawSpec.cardHeight
        )
        GLES20.glUniform1f(
            radiusHandle,
            drawSpec.cardRadius.coerceIn(2f, max(drawSpec.cardWidth, drawSpec.cardHeight))
        )
        GLES20.glUniform1f(intensityHandle, drawSpec.cardIntensity.coerceIn(0.35f, 1.35f))
        GLES20.glUniform1f(textureReadyHandle, if (textureReady) 1f else 0f)
        GLES20.glUniform1f(flowDepthHandle, flowDepthPx)
        GLES20.glUniform4f(
            materialHandle,
            s.openGlVisibility.coerceIn(0f, 20f),
            s.openGlMaxAlpha.coerceIn(0f, 1f),
            s.edgeBrightness.coerceIn(-5f, 5f),
            s.openGlDebugLineAlpha.coerceIn(0f, 1f)
        )
        GLES20.glUniform4f(
            oldAHandle,
            s.openGlPullScale.coerceIn(-300f, 300f),
            s.edgePullDp.coerceIn(-600f, 600f),
            s.openGlCompressionScale.coerceIn(-10f, 10f),
            s.openGlCornerScale.coerceIn(0f, 200f)
        )
        GLES20.glUniform4f(
            oldBHandle,
            s.openGlSampleRadiusScale.coerceIn(0f, 200f),
            s.ringWidthDp.coerceIn(0f, 300f),
            s.openGlDarkScale.coerceIn(-10f, 10f),
            s.openGlDebugLineAlpha.coerceIn(0f, 1f)
        )
        GLES20.glUniform4f(
            bodyHandle,
            s.newOpenGlBodyWidth.coerceIn(0.18f, 1.5f),
            s.newOpenGlBodyCurve.coerceIn(0.20f, 3.2f),
            s.newOpenGlBodyGain.coerceIn(0f, 900f),
            s.newOpenGlBrightness.coerceIn(0.4f, 2.2f)
        )
        GLES20.glUniform4f(
            bandHandle,
            s.newOpenGlBodyBandPos.coerceIn(0.55f, 0.98f),
            s.newOpenGlBodyBandWidth.coerceIn(0.015f, 0.8f),
            s.newOpenGlBodyBandGain.coerceIn(0f, 1500f),
            0f
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backdropTextureId)
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, flowTextureId)
        GLES20.glUniform1i(flowTextureHandle, 1)

        quadVertices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, quadVertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    fun onRelease() {
        val textures = intArrayOf(backdropTextureId, flowTextureId)
        if (backdropTextureId != 0 || flowTextureId != 0) {
            GLES20.glDeleteTextures(2, textures, 0)
        }
        if (program != 0) GLES20.glDeleteProgram(program)
        backdropTextureId = 0
        flowTextureId = 0
        program = 0
        flowTextureReady = false
        flowTextureKey = null
    }

    private fun uploadPendingTextureIfNeeded() {
        val bitmap = synchronized(textureLock) {
            pendingBitmap?.also { pendingBitmap = null }
        } ?: return
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backdropTextureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        textureReady = true
    }

    private fun ensureFlowTexture(drawSpec: WebOpenGLGlassDrawSpec) {
        val width = drawSpec.cardWidth.roundToInt().coerceAtLeast(1)
        val height = drawSpec.cardHeight.roundToInt().coerceAtLeast(1)
        val radius = drawSpec.cardRadius.coerceIn(0f, minOf(width, height) * 0.5f)
        val key = FlowTextureKey(
            width = width,
            height = height,
            radiusQuarterPx = (radius * 4f).roundToInt()
        )
        if (flowTextureReady && key == flowTextureKey) return

        val map = WebOpenGLFlowMapFactory.build(width, height, radius)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, flowTextureId)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        map.pixels.position(0)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            map.width,
            map.height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            map.pixels
        )
        flowDepthPx = map.depthPx
        flowTextureKey = key
        flowTextureReady = true
    }

    private fun configureTexture(textureId: Int) {
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
