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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.LocalBackdropOrigin
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassBackdrop
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val GLASS_SPEC_EPSILON_PX = 0.5f
private const val GLASS_ORIGIN_EPSILON_PX = 0.35f
private const val GLASS_INTENSITY_EPSILON = 0.006f
private const val GLASS_PRESS_EPSILON = 0.003f
private const val GLASS_PRESS_CENTER_EPSILON = 0.002f
private const val GLASS_STABLE_SURFACE_FALLBACK_ANCHOR_Y = 0.44f

enum class OpenGLGlassSurfaceAnchor(val fraction: Float) {
    Top(0f),
    Center(0.44f),
    Bottom(1f)
}

val LocalOpenGLGlassSurfaceAnchor = compositionLocalOf {
    OpenGLGlassSurfaceAnchor.Center
}

@Composable
fun OpenGLGlassCardLayer(
    radius: Int,
    glassIntensity: Float,
    coordinateSource: GlassCoordinateSource? = null,
    modifier: Modifier = Modifier,
    pressProgress: Float = 0f,
    pressCenter: Offset = Offset(0.5f, 0.5f)
) {
    val backdrop = LocalBlurredBackdrop.current ?: return
    val border = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
    val backdropOrigin = LocalBackdropOrigin.current
    val density = LocalDensity.current
    val surfaceAnchor = LocalOpenGLGlassSurfaceAnchor.current.fraction

    val blurBitmap = backdrop.image.asAndroidBitmap()
    val lensBitmap = backdrop.lensImage.asAndroidBitmap()
    val radiusPx = with(density) { radius.dp.toPx() }.roundToInt().toFloat()
    val intensity = glassIntensity.coerceIn(0.35f, 1.35f)
    val cardOrigin = coordinateSource?.offsetRelativeTo(backdropOrigin) ?: Offset.Zero
    val press = pressProgress.coerceIn(0f, 1f)
    val pressX = pressCenter.x.coerceIn(0f, 1f)
    val pressY = pressCenter.y.coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
        val heightPx = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
        val rootWidthPx = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
        val rootHeightPx = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context -> OpenGLGlassCardHostView(context) },
            update = { view ->
                val anchorDirty = view.setStableSurfaceAnchor(surfaceAnchor)
                val surfaceDirty = view.setStableSurfaceSize(
                    width = widthPx.roundToInt(),
                    height = heightPx.roundToInt(),
                    rootWidth = rootWidthPx.roundToInt()
                )
                val specDirty = view.setGlassSpec(widthPx, heightPx, radiusPx, intensity)
                val samplingDirty = view.setSamplingSpec(cardOrigin.x, cardOrigin.y, rootWidthPx, rootHeightPx)
                val pressDirty = view.setPressSpec(press, pressX, pressY)
                val textureDirty = view.setBackdropTextures(blurBitmap, lensBitmap)
                val styleDirty = view.setGlassStyle(border)
                if (anchorDirty || surfaceDirty || specDirty || samplingDirty || pressDirty || textureDirty || styleDirty) {
                    view.requestRender()
                }
            }
        )
    }
}

private class OpenGLGlassCardHostView(context: Context) : FrameLayout(context) {
    private val textureView = OpenGLGlassCardTextureView(context)

    private var stableSurfaceWidth = 1
    private var stableSurfaceHeight = 1
    private var stableSurfaceOffsetY = 0
    private var stableSurfaceAnchorY = GLASS_STABLE_SURFACE_FALLBACK_ANCHOR_Y
    private var lastRootWidth = 1
    private var geometryAwaitingLayout = false

    private var latestGlassWidth = 1f
    private var latestGlassHeight = 1f
    private var latestRadius = 24f
    private var latestIntensity = 1f

    init {
        clipChildren = true
        clipToPadding = true
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(textureView, LayoutParams(1, 1))
    }

    fun setStableSurfaceAnchor(anchorY: Float): Boolean {
        val nextAnchor = anchorY.coerceIn(0f, 1f)
        val dirty = abs(nextAnchor - stableSurfaceAnchorY) > 0.001f
        stableSurfaceAnchorY = nextAnchor
        return dirty
    }

    fun setStableSurfaceSize(width: Int, height: Int, rootWidth: Int): Boolean {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val safeRootWidth = rootWidth.coerceAtLeast(1)

        val rootWidthChanged = abs(safeRootWidth - lastRootWidth) > 2
        lastRootWidth = safeRootWidth

        val targetWidth = if (rootWidthChanged) safeWidth else max(stableSurfaceWidth, safeWidth)
        val targetHeight = if (rootWidthChanged) safeHeight else max(stableSurfaceHeight, safeHeight)
        val targetOffsetY = if (targetHeight > safeHeight) {
    ((targetHeight - safeHeight) * stableSurfaceAnchorY).roundToInt()
} else {
    0
}

        val sizeChanged = targetWidth != stableSurfaceWidth || targetHeight != stableSurfaceHeight
        val offsetChanged = targetOffsetY != stableSurfaceOffsetY

        stableSurfaceWidth = targetWidth
        stableSurfaceHeight = targetHeight
        stableSurfaceOffsetY = targetOffsetY

        val lp = textureView.layoutParams as? LayoutParams
        val layoutParamDirty = lp == null || lp.width != stableSurfaceWidth || lp.height != stableSurfaceHeight
        if (layoutParamDirty) {
            textureView.layoutParams = LayoutParams(stableSurfaceWidth, stableSurfaceHeight)
        }

        if (sizeChanged || offsetChanged || layoutParamDirty) {
            geometryAwaitingLayout = true
            requestLayout()
        }

        return sizeChanged || offsetChanged || layoutParamDirty
    }
override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    textureView.layout(
        0,
        0,
        stableSurfaceWidth,
        stableSurfaceHeight
    )
    geometryAwaitingLayout = false

    val glassDirty = syncGlassSpecToTexture()
    val samplingDirty = syncSamplingSpecToTexture()
    if (glassDirty || samplingDirty) {
        textureView.requestRender()
    }
}

    fun setGlassSpec(width: Float, height: Float, radius: Float, intensity: Float): Boolean {
        latestGlassWidth = width.coerceAtLeast(1f)
        latestGlassHeight = height.coerceAtLeast(1f)
        latestRadius = radius
        latestIntensity = intensity
        return if (geometryAwaitingLayout) {
            false
        } else {
            syncGlassSpecToTexture()
        }
    }

    private fun syncGlassSpecToTexture(): Boolean = textureView.setGlassSpec(
    latestGlassWidth,
    max(latestGlassHeight, stableSurfaceHeight.toFloat()),
    -stableSurfaceOffsetY.toFloat(),
    latestRadius,
    latestIntensity
)

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float): Boolean {
    latestOriginX = originX
    latestOriginY = originY
    latestRootWidth = rootWidth
    latestRootHeight = rootHeight
    return if (geometryAwaitingLayout) {
        false
    } else {
        syncSamplingSpecToTexture()
    }
}

private fun syncSamplingSpecToTexture(): Boolean =
    textureView.setSamplingSpec(
        latestOriginX,
        latestOriginY - stableSurfaceOffsetY.toFloat(),
        latestRootWidth,
        latestRootHeight
    )

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float): Boolean =
        textureView.setPressSpec(progress, centerX, centerY)

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap): Boolean =
        textureView.setBackdropTextures(blurBitmap, lensBitmap)

    fun setGlassStyle(style: GlassBorderStyle): Boolean = textureView.setGlassStyle(style)

    fun requestRender() {
        if (!geometryAwaitingLayout) {
            textureView.requestRender()
        }
    }
}

private class OpenGLGlassCardTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: CardGlassEglThread? = null
    private var latestBlurBitmap: Bitmap? = null
    private var latestLensBitmap: Bitmap? = null
    private var latestWidth = 1f
    private var latestHeight = 1f
    private var latestRectOffsetY = 0f
    private var latestRadius = 24f
    private var latestIntensity = 1f
    private var latestOriginX = 0f
    private var latestOriginY = 0f
    private var latestRootWidth = 1f
    private var latestRootHeight = 1f
    private var latestOriginX = 0f
    private var latestOriginY = 0f
    private var latestRootWidth = 1f
    private var latestRootHeight = 1f
    private var latestPressProgress = 0f
    private var latestPressCenterX = 0.5f
    private var latestPressCenterY = 0.5f
    private var latestStyle = GlassBorderStyle()

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
        val nextRectOffsetY = rectOffsetY
        val dirty = abs(nextWidth - latestWidth) > GLASS_SPEC_EPSILON_PX ||
            abs(nextHeight - latestHeight) > GLASS_SPEC_EPSILON_PX ||
            abs(nextRectOffsetY - latestRectOffsetY) > GLASS_SPEC_EPSILON_PX ||
            abs(radius - latestRadius) > GLASS_SPEC_EPSILON_PX ||
            abs(intensity - latestIntensity) > GLASS_INTENSITY_EPSILON
        latestWidth = nextWidth
        latestHeight = nextHeight
        latestRectOffsetY = nextRectOffsetY
        latestRadius = radius
        latestIntensity = intensity
        if (dirty) renderThread?.setGlassSpec(latestWidth, latestHeight, latestRectOffsetY, latestRadius, latestIntensity)
        return dirty
    }

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float): Boolean {
        val nextRootWidth = rootWidth.coerceAtLeast(1f)
        val nextRootHeight = rootHeight.coerceAtLeast(1f)
        val dirty = abs(originX - latestOriginX) > GLASS_ORIGIN_EPSILON_PX ||
            abs(originY - latestOriginY) > GLASS_ORIGIN_EPSILON_PX ||
            abs(nextRootWidth - latestRootWidth) > GLASS_SPEC_EPSILON_PX ||
            abs(nextRootHeight - latestRootHeight) > GLASS_SPEC_EPSILON_PX
        latestOriginX = originX
        latestOriginY = originY
        latestRootWidth = nextRootWidth
        latestRootHeight = nextRootHeight
        if (dirty) renderThread?.setSamplingSpec(latestOriginX, latestOriginY, latestRootWidth, latestRootHeight)
        return dirty
    }

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float): Boolean {
        val nextProgress = progress.coerceIn(0f, 1f)
        val nextCenterX = centerX.coerceIn(0f, 1f)
        val nextCenterY = centerY.coerceIn(0f, 1f)
        val dirty = abs(nextProgress - latestPressProgress) > GLASS_PRESS_EPSILON ||
            abs(nextCenterX - latestPressCenterX) > GLASS_PRESS_CENTER_EPSILON ||
            abs(nextCenterY - latestPressCenterY) > GLASS_PRESS_CENTER_EPSILON
        latestPressProgress = nextProgress
        latestPressCenterX = nextCenterX
        latestPressCenterY = nextCenterY
        if (dirty) renderThread?.setPressSpec(latestPressProgress, latestPressCenterX, latestPressCenterY)
        return dirty
    }

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap): Boolean {
        val dirty = blurBitmap !== latestBlurBitmap || lensBitmap !== latestLensBitmap
        latestBlurBitmap = blurBitmap
        latestLensBitmap = lensBitmap
        if (dirty) renderThread?.setBackdropTextures(blurBitmap, lensBitmap)
        return dirty
    }

    fun setGlassStyle(style: GlassBorderStyle): Boolean {
        val dirty = style != latestStyle
        latestStyle = style
        if (dirty) renderThread?.setGlassStyle(style)
        return dirty
    }

    fun requestRender() {
        renderThread?.requestRender()
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        renderThread?.shutdown()
        renderThread = CardGlassEglThread(Surface(surfaceTexture), width, height).also { thread ->
            thread.setGlassSpec(latestWidth, latestHeight, latestRectOffsetY, latestRadius, latestIntensity)
            thread.setSamplingSpec(latestOriginX, latestOriginY, latestRootWidth, latestRootHeight)
            thread.setPressSpec(latestPressProgress, latestPressCenterX, latestPressCenterY)
            thread.setGlassStyle(latestStyle)
            val blur = latestBlurBitmap
            val lens = latestLensBitmap
            if (blur != null && lens != null) thread.setBackdropTextures(blur, lens)
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

private class CardGlassEglThread(
    private val surface: Surface,
    width: Int,
    height: Int
) : Thread("OpenGLGlassCardTextureThread") {
    private val renderer = OpenGLGlassCardRenderer()
    private val renderLock = Object()

    @Volatile private var running = true
    @Volatile private var pendingRender = true
    @Volatile private var viewportWidth = max(width, 1)
    @Volatile private var viewportHeight = max(height, 1)
    @Volatile private var sizeDirty = true

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun setGlassSpec(width: Float, height: Float, rectOffsetY: Float, radius: Float, intensity: Float) = renderer.setGlassSpec(width, height, rectOffsetY, radius, intensity)
    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float) = renderer.setSamplingSpec(originX, originY, rootWidth, rootHeight)
    fun setPressSpec(progress: Float, centerX: Float, centerY: Float) = renderer.setPressSpec(progress, centerX, centerY)
    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap) = renderer.setBackdropTextures(blurBitmap, lensBitmap)
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
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "Unable to initialize EGL" }
        val configAttributes = intArrayOf(
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
        val configCount = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, configs, 0, configs.size, configCount, 0)) { "Unable to choose EGL config" }
        val eglConfig = configs[0] ?: error("No EGL config found")
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL window surface" }
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "Unable to make EGL context current" }
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglSurface = EGL14.EGL_NO_SURFACE
        eglContext = EGL14.EGL_NO_CONTEXT
    }
}

private data class CardGlassDrawSpec(
    val cardWidth: Float,
    val cardHeight: Float,
    val rectOffsetY: Float,
    val cardRadius: Float,
    val cardIntensity: Float,
    val cardOriginX: Float,
    val cardOriginY: Float,
    val rootWidth: Float,
    val rootHeight: Float,
    val pressProgress: Float,
    val pressCenterX: Float,
    val pressCenterY: Float,
    val style: GlassBorderStyle
)

private class OpenGLGlassCardRenderer {
    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(FULLSCREEN_QUAD.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(FULLSCREEN_QUAD)
            position(0)
        }

    private val textureLock = Any()
    private val specLock = Any()
    private var pendingBlurBitmap: Bitmap? = null
    private var pendingLensBitmap: Bitmap? = null
    private var activeBlurBitmap: Bitmap? = null
    private var activeLensBitmap: Bitmap? = null
    private var blurTextureId = 0
    private var lensTextureId = 0
    private var texturesReady = false

    private var cardWidth = 1f
    private var cardHeight = 1f
    private var rectOffsetY = 0f
    private var cardRadius = 24f
    private var cardIntensity = 1f
    private var cardOriginX = 0f
    private var cardOriginY = 0f
    private var rootWidth = 1f
    private var rootHeight = 1f
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
    private var pressHandle = 0
    private var textureReadyHandle = 0
    private var blurTextureHandle = 0
    private var lensTextureHandle = 0
    private var materialHandle = 0
    private var refractionHandle = 0
    private var opticsHandle = 0
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

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap) {
        synchronized(textureLock) {
            pendingBlurBitmap = blurBitmap
            pendingLensBitmap = lensBitmap
        }
    }

    fun setGlassStyle(style: GlassBorderStyle) {
        synchronized(specLock) {
            this.style = style
        }
    }

    fun onSurfaceCreated() {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        cardOriginHandle = GLES20.glGetUniformLocation(program, "uCardOrigin")
        rootResolutionHandle = GLES20.glGetUniformLocation(program, "uRootResolution")
        rectHandle = GLES20.glGetUniformLocation(program, "uRect")
        radiusHandle = GLES20.glGetUniformLocation(program, "uRadius")
        intensityHandle = GLES20.glGetUniformLocation(program, "uIntensity")
        pressHandle = GLES20.glGetUniformLocation(program, "uPress")
        textureReadyHandle = GLES20.glGetUniformLocation(program, "uTextureReady")
        blurTextureHandle = GLES20.glGetUniformLocation(program, "uBlurTexture")
        lensTextureHandle = GLES20.glGetUniformLocation(program, "uLensTexture")
        materialHandle = GLES20.glGetUniformLocation(program, "uMaterial")
        refractionHandle = GLES20.glGetUniformLocation(program, "uRefraction")
        opticsHandle = GLES20.glGetUniformLocation(program, "uOptics")

        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        blurTextureId = textures[0]
        lensTextureId = textures[1]
        configureTexture(blurTextureId)
        configureTexture(lensTextureId)

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
        uploadPendingTexturesIfNeeded()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return

        val drawSpec = synchronized(specLock) {
            CardGlassDrawSpec(
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                rectOffsetY = rectOffsetY,
                cardRadius = cardRadius,
                cardIntensity = cardIntensity,
                cardOriginX = cardOriginX,
                cardOriginY = cardOriginY,
                rootWidth = rootWidth,
                rootHeight = rootHeight,
                pressProgress = pressProgress,
                pressCenterX = pressCenterX,
                pressCenterY = pressCenterY,
                style = style
            )
        }
        val style = drawSpec.style

        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES20.glUniform2f(cardOriginHandle, drawSpec.cardOriginX, drawSpec.cardOriginY)
        GLES20.glUniform2f(rootResolutionHandle, drawSpec.rootWidth, drawSpec.rootHeight)
        GLES20.glUniform4f(rectHandle, 0f, drawSpec.rectOffsetY, drawSpec.cardWidth, drawSpec.cardHeight)
        GLES20.glUniform1f(radiusHandle, drawSpec.cardRadius.coerceIn(2f, max(drawSpec.cardWidth, drawSpec.cardHeight)))
        GLES20.glUniform1f(intensityHandle, drawSpec.cardIntensity)
        GLES20.glUniform4f(pressHandle, drawSpec.pressProgress, drawSpec.pressCenterX, drawSpec.pressCenterY, 0f)
        GLES20.glUniform1f(textureReadyHandle, if (texturesReady) 1f else 0f)
        GLES20.glUniform4f(
            materialHandle,
            style.openGlVisibility.coerceIn(0f, 20f),
            style.openGlMaxAlpha.coerceIn(0f, 1f),
            style.edgeBrightness.coerceIn(-5f, 5f),
            style.bodyAlpha.coerceIn(-5f, 5f)
        )
        GLES20.glUniform4f(
            refractionHandle,
            style.openGlPullScale.coerceIn(-300f, 300f),
            style.edgePullDp.coerceIn(-600f, 600f),
            style.openGlCompressionScale.coerceIn(-10f, 10f),
            style.openGlCornerScale.coerceIn(0f, 200f)
        )
        GLES20.glUniform4f(
            opticsHandle,
            style.openGlSampleRadiusScale.coerceIn(0f, 200f),
            style.ringWidthDp.coerceIn(0f, 300f),
            style.openGlDebugLineAlpha.coerceIn(0f, 1f),
            style.openGlDarkScale.coerceIn(-10f, 10f)
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextureId)
        GLES20.glUniform1i(blurTextureHandle, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lensTextureId)
        GLES20.glUniform1i(lensTextureHandle, 1)

        quadVertices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, quadVertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    fun onRelease() {
        val textures = intArrayOf(blurTextureId, lensTextureId)
        if (blurTextureId != 0 || lensTextureId != 0) GLES20.glDeleteTextures(2, textures, 0)
        blurTextureId = 0
        lensTextureId = 0
        activeBlurBitmap = null
        activeLensBitmap = null
        texturesReady = false
    }

    private fun uploadPendingTexturesIfNeeded() {
        val pair: Pair<Bitmap?, Bitmap?> = synchronized(textureLock) { pendingBlurBitmap to pendingLensBitmap }
        val blur = pair.first
        val lens = pair.second
        if (blur == null || lens == null) {
            texturesReady = false
            return
        }
        if (blur !== activeBlurBitmap) {
            uploadBitmapToTexture(blurTextureId, blur)
            activeBlurBitmap = blur
        }
        if (lens !== activeLensBitmap) {
            uploadBitmapToTexture(lensTextureId, lens)
            activeLensBitmap = lens
        }
        texturesReady = true
    }

    private fun configureTexture(textureId: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun uploadBitmapToTexture(textureId: Int, bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val glProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(glProgram, vertexShader)
        GLES20.glAttachShader(glProgram, fragmentShader)
        GLES20.glLinkProgram(glProgram)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(glProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(glProgram)
            GLES20.glDeleteProgram(glProgram)
            error("OpenGL glass card program link failed: $log")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return glProgram
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("OpenGL glass card shader compile failed: $log")
        }
        return shader
    }

    private companion object {
        val FULLSCREEN_QUAD = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec2 uResolution;
            uniform vec2 uCardOrigin;
            uniform vec2 uRootResolution;
            uniform vec4 uRect;
            uniform float uRadius;
            uniform float uIntensity;
            uniform vec4 uPress;
            uniform float uTextureReady;
            uniform vec4 uMaterial;
            uniform vec4 uRefraction;
            uniform vec4 uOptics;
            uniform sampler2D uBlurTexture;
            uniform sampler2D uLensTexture;

            float sat(float x) { return clamp(x, 0.0, 1.0); }

            float roundedBoxSdfAt(vec2 coord, vec2 rectSize, float radius) {
                vec2 p = coord - rectSize * 0.5;
                vec2 halfSize = rectSize * 0.5;
                vec2 q = abs(p) - max(halfSize - vec2(radius), vec2(0.0));
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }

            vec2 texUv(vec2 uv) {
                return clamp(uv, 0.0, 1.0);
            }

            vec2 globalUv(vec2 visualCoord) {
                return clamp((uCardOrigin + visualCoord) / max(uRootResolution, vec2(1.0)), 0.0, 1.0);
            }

            vec3 fallbackBackdrop(vec2 uv) {
                float h = smoothstep(0.0, 1.0, uv.y);
                return mix(vec3(0.12, 0.22, 0.38), vec3(0.36, 0.50, 0.72), h);
            }

            vec3 sourceBlurBackdrop(vec2 uv) {
                vec3 fallback = fallbackBackdrop(uv);
                vec3 realColor = texture2D(uBlurTexture, texUv(uv)).rgb;
                return mix(fallback, realColor, sat(uTextureReady));
            }

            vec3 sourceLensBackdrop(vec2 uv) {
                vec3 fallback = fallbackBackdrop(uv);
                vec3 realColor = texture2D(uLensTexture, texUv(uv)).rgb;
                return mix(fallback, realColor, sat(uTextureReady));
            }

            vec3 blurBackdrop(vec2 uv, float edgeWeight) {
                float blurBoost = 1.0 + edgeWeight * 0.38;
                vec2 px = vec2(max(uOptics.x, 0.0) * blurBoost) / max(uRootResolution, vec2(1.0));
                vec3 c = sourceBlurBackdrop(uv) * 0.200;
                c += sourceBlurBackdrop(uv + vec2(px.x, 0.0)) * 0.110;
                c += sourceBlurBackdrop(uv - vec2(px.x, 0.0)) * 0.110;
                c += sourceBlurBackdrop(uv + vec2(0.0, px.y)) * 0.110;
                c += sourceBlurBackdrop(uv - vec2(0.0, px.y)) * 0.110;
                c += sourceBlurBackdrop(uv + vec2(px.x, px.y)) * 0.090;
                c += sourceBlurBackdrop(uv + vec2(-px.x, px.y)) * 0.090;
                c += sourceBlurBackdrop(uv + vec2(px.x, -px.y)) * 0.090;
                c += sourceBlurBackdrop(uv + vec2(-px.x, -px.y)) * 0.090;
                return c;
            }

            float effectiveEdgeWidth(vec2 rectSize) {
                float maxSafe = min(rectSize.x, rectSize.y) * 0.34;
                return clamp(uOptics.y, 6.0, maxSafe);
            }

            float insideDistanceAt(vec2 coord, vec2 rectSize, float radius) {
                return max(-roundedBoxSdfAt(coord, rectSize, radius), 0.0);
            }

            float rimWideAt(vec2 coord, vec2 rectSize, float radius) {
                float inside = insideDistanceAt(coord, rectSize, radius);
                float w = effectiveEdgeWidth(rectSize);
                return 1.0 - smoothstep(0.0, w, inside);
            }

            float rimCoreAt(vec2 coord, vec2 rectSize, float radius) {
                float inside = insideDistanceAt(coord, rectSize, radius);
                float w = max(effectiveEdgeWidth(rectSize) * 0.28, 3.0);
                return 1.0 - smoothstep(0.0, w, inside);
            }

            float edgeDragBandAt(vec2 coord, vec2 rectSize, float radius) {
                float inside = insideDistanceAt(coord, rectSize, radius);
                float w = max(effectiveEdgeWidth(rectSize) * 1.45, 8.0);
                return pow(1.0 - smoothstep(0.0, w, inside), 1.35);
            }

            vec2 sdfNormalAt(vec2 coord, vec2 rectSize, float radius) {
                float d = 1.25;
                float l = roundedBoxSdfAt(coord - vec2(d, 0.0), rectSize, radius);
                float r = roundedBoxSdfAt(coord + vec2(d, 0.0), rectSize, radius);
                float u = roundedBoxSdfAt(coord - vec2(0.0, d), rectSize, radius);
                float b = roundedBoxSdfAt(coord + vec2(0.0, d), rectSize, radius);
                vec2 n = vec2(r - l, b - u);
                return n / max(length(n), 0.001);
            }

            float colorSignal(vec3 c) {
                float luma = dot(c, vec3(0.299, 0.587, 0.114));
                float chroma = length(c - vec3(luma));
                return sat((luma - 0.20) * 1.25 + chroma * 1.55);
            }

            vec3 edgeColorDrag(vec2 coord, vec2 rectSize, float radius, float band, float core) {
                vec2 n = sdfNormalAt(coord, rectSize, radius);
                vec2 t = vec2(-n.y, n.x);
                float pull = clamp(8.0 + abs(uRefraction.y) * 0.030, 8.0, 42.0);
                float smear = clamp(4.0 + effectiveEdgeWidth(rectSize) * 0.55, 4.0, 22.0);

                vec2 baseIn = coord - n * pull;
                vec2 baseFar = coord - n * (pull * 1.85);
                vec2 baseOut = coord + n * (pull * 0.45);

                vec3 c = sourceLensBackdrop(globalUv(baseIn)) * 0.28;
                c += sourceLensBackdrop(globalUv(baseFar)) * 0.18;
                c += sourceLensBackdrop(globalUv(baseOut)) * 0.12;
                c += sourceLensBackdrop(globalUv(baseIn + t * smear)) * 0.14;
                c += sourceLensBackdrop(globalUv(baseIn - t * smear)) * 0.14;
                c += sourceLensBackdrop(globalUv(baseIn + t * smear * 1.85)) * 0.07;
                c += sourceLensBackdrop(globalUv(baseIn - t * smear * 1.85)) * 0.07;

                vec3 soft = blurBackdrop(globalUv(baseIn), band) * 0.45 + c * 0.55;
                float signal = colorSignal(c);
                float dragAlpha = band * (0.035 + sat(max(uRefraction.z, 0.0)) * 0.105 + core * 0.030) * signal;
                return mix(vec3(0.0), soft, sat(dragAlpha));
            }

            float bodyDomeAt(vec2 coord, vec2 rectSize) {
                vec2 local = clamp(coord / rectSize, 0.0, 1.0);
                vec2 p = local * 2.0 - 1.0;
                p.x *= min(rectSize.x / max(rectSize.y, 1.0), 2.4) * 0.38;
                float d = length(p);
                return pow(sat(1.0 - d * 0.74), 1.65);
            }

            float thicknessAt(vec2 coord, vec2 rectSize, float radius) {
                float sd = roundedBoxSdfAt(coord, rectSize, radius);
                float maskGuard = 1.0 - smoothstep(1.5, 16.0, sd);
                float rimWide = rimWideAt(coord, rectSize, radius);
                float rimCore = rimCoreAt(coord, rectSize, radius);
                float dome = bodyDomeAt(coord, rectSize);
                float t = dome * 0.22 + rimWide * 0.46 + rimCore * 0.34;
                return t * maskGuard;
            }

            float pressFieldAt(vec2 coord, vec2 rectSize, vec2 center, float press) {
                vec2 local = clamp(coord / rectSize, 0.0, 1.0);
                vec2 delta = local - center;
                delta.x *= min(rectSize.x / max(rectSize.y, 1.0), 2.2);
                float d = length(delta);
                float field = pow(sat(1.0 - d * 0.92), 1.45);
                return field * press;
            }

            vec2 softLimitPx(vec2 v, float limitPx) {
                float len = length(v);
                float softLen = len / (1.0 + len / max(limitPx, 1.0));
                return v * (softLen / max(len, 0.0001));
            }

            void main() {
                vec2 coord = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);
                vec2 rectOffset = uRect.xy;
                vec2 rectSize = max(uRect.zw, vec2(1.0));
                vec2 visualCoord = coord - rectOffset;
                float radius = min(uRadius, min(rectSize.x, rectSize.y) * 0.5);
                float sd = roundedBoxSdfAt(visualCoord, rectSize, radius);
                float mask = 1.0 - smoothstep(0.0, 1.35, sd);
                if (mask <= 0.001) discard;

                float press = sat(uPress.x);
                vec2 pressCenter = clamp(uPress.yz, vec2(0.0), vec2(1.0));
                vec2 pressCenterPx = pressCenter * rectSize;
                float pressField = pressFieldAt(visualCoord, rectSize, pressCenter, press);
                float pressWide = press * pow(sat(1.0 - length((visualCoord / rectSize - pressCenter) * vec2(min(rectSize.x / max(rectSize.y, 1.0), 2.2), 1.0)) * 0.58), 1.25);
                vec2 inwardPx = softLimitPx((pressCenterPx - visualCoord) * (0.028 * press + 0.070 * pressField), 24.0 + press * 18.0);
                vec2 pressedCoord = visualCoord + inwardPx;

                vec2 bgUv = globalUv(pressedCoord);
                float stepPx = 2.0;
                float tL = thicknessAt(visualCoord - vec2(stepPx, 0.0), rectSize, radius);
                float tR = thicknessAt(visualCoord + vec2(stepPx, 0.0), rectSize, radius);
                float tU = thicknessAt(visualCoord - vec2(0.0, stepPx), rectSize, radius);
                float tD = thicknessAt(visualCoord + vec2(0.0, stepPx), rectSize, radius);
                vec2 grad = vec2(tR - tL, tD - tU);

                float rimWide = rimWideAt(visualCoord, rectSize, radius);
                float rimCore = rimCoreAt(visualCoord, rectSize, radius);
                float dragBand = edgeDragBandAt(visualCoord, rectSize, radius);
                float gLen = length(grad);
                float gradGate = smoothstep(0.0004, 0.012, gLen);
                grad *= gradGate * min(1.0, 0.22 / max(gLen, 0.0001));
                float gradEnergy = sat(length(grad) * max(uRefraction.w, 0.0));

                vec2 pressDelta = visualCoord - pressCenterPx;
                vec2 pressDir = pressDelta / max(length(pressDelta), 0.001);
                vec2 pressDimplePx = -pressDir * pressField * (8.0 + press * 10.0);
                vec2 rawRefractPx = grad * (uRefraction.x + uRefraction.y * rimWide + press * (26.0 + 52.0 * pressField)) * max(uMaterial.x, 0.0);
                rawRefractPx += pressDimplePx;
                rawRefractPx += inwardPx * (0.76 + 0.46 * rimWide);
                float limitPx = mix(18.0, 62.0, rimWide) + sat(abs(uRefraction.y) / 600.0) * 16.0 + press * 20.0;
                vec2 refractPx = softLimitPx(rawRefractPx, limitPx);
                vec2 refractedUv = bgUv + refractPx / max(uRootResolution, vec2(1.0));

                vec3 color = blurBackdrop(refractedUv, rimWide + pressField * 0.85 + pressWide * 0.22);
                vec3 lensColor = sourceLensBackdrop(refractedUv);
                float lensMix = sat(rimCore * max(uRefraction.z, 0.0) * 0.42 + pressField * 0.220 + pressWide * 0.075);
                color = mix(color, lensColor, lensMix);

                vec3 dragColor = edgeColorDrag(visualCoord + inwardPx * 0.72, rectSize, radius, dragBand + press * rimWide * 0.32 + pressField * 0.18, rimCore);
                float dragMix = sat(max(max(dragColor.r, dragColor.g), dragColor.b));
                color = mix(color, dragColor, dragMix);

                float rimOpticalBoost = rimCore * 0.16 + gradEnergy * 0.045 + press * rimCore * 0.080 + pressField * 0.040;
                color *= uMaterial.z * (1.0 + rimOpticalBoost);
                color *= 1.0 - pressField * 0.070 - pressWide * 0.025;
                color += vec3(0.018, 0.035, 0.046) * pressField * 0.38;

                float debugEdge = smoothstep(-1.65, 0.0, sd) * mask;
                color = mix(color, vec3(1.0, 0.45, 0.0), debugEdge * uOptics.z);
                color -= vec3(0.06, 0.07, 0.09) * uOptics.w * rimWide;
                color = clamp(color, 0.0, 1.0);

                gl_FragColor = vec4(color, clamp(uMaterial.y * uMaterial.x, 0.0, 1.0) * mask);
            }
        """
    }
}
