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
import androidx.compose.runtime.remember
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

private const val NEW_GLASS_SPEC_EPSILON_PX = 0.5f
private const val NEW_GLASS_ORIGIN_EPSILON_PX = 0.35f
private const val NEW_GLASS_INTENSITY_EPSILON = 0.006f
private const val NEW_GLASS_PRESS_EPSILON = 0.003f
private const val NEW_GLASS_PRESS_CENTER_EPSILON = 0.002f
private const val NEW_GLASS_STABLE_SURFACE_FALLBACK_ANCHOR_Y = 0.44f

@Composable
fun NewOpenGLGlassCardLayer(
    radius: Int,
    glassIntensity: Float,
    coordinateSource: GlassCoordinateSource? = null,
    modifier: Modifier = Modifier,
    pressProgress: Float = 0f,
    pressCenter: Offset = Offset(0.5f, 0.5f),
    viewportTopInsetPx: Float = 0f
) {
    val backdrop = LocalBlurredBackdrop.current ?: return
    val border = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
    val backdropOrigin = LocalBackdropOrigin.current
    val density = LocalDensity.current
    val surfaceAnchor = LocalOpenGLGlassSurfaceAnchor.current.fraction
    val localViewportTopInsetPx = with(density) { LocalOpenGLGlassViewportTopInset.current.toPx() }
    val effectiveViewportTopInsetPx = max(viewportTopInsetPx, localViewportTopInsetPx)

    val blurBitmap = remember(backdrop.image) { backdrop.image.asAndroidBitmap() }
    val lensBitmap = remember(backdrop.lensImage) { backdrop.lensImage.asAndroidBitmap() }
    val radiusPx = with(density) { radius.dp.toPx() }.roundToInt().toFloat()
    val intensity = glassIntensity.coerceIn(0.35f, 1.35f)
    val cardOrigin = coordinateSource?.offsetRelativeTo(backdropOrigin) ?: Offset.Zero
    val press = pressProgress.coerceIn(0f, 1f)
    val pressX = pressCenter.x.coerceIn(0f, 1f)
    val pressY = pressCenter.y.coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
        val heightPx = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
        val safeViewportTopInsetPx = effectiveViewportTopInsetPx.coerceIn(0f, (heightPx - 1f).coerceAtLeast(0f))
        val viewportHeightPx = (heightPx - safeViewportTopInsetPx).coerceAtLeast(1f)
        val rootWidthPx = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
        val rootHeightPx = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context -> NewOpenGLGlassCardHostView(context) },
            update = { view ->
                view.setStableSurfaceAnchor(surfaceAnchor)
                val surfaceDirty = view.setStableSurfaceSize(
                    width = widthPx.roundToInt(),
                    height = heightPx.roundToInt(),
                    rootWidth = rootWidthPx.roundToInt(),
                    rootHeight = rootHeightPx.roundToInt()
                )
                val specDirty = view.setGlassSpec(
                    width = widthPx,
                    height = viewportHeightPx,
                    rectOffsetY = safeViewportTopInsetPx,
                    radius = radiusPx,
                    intensity = intensity
                )
                val samplingDirty = view.setSamplingSpec(
                    originX = cardOrigin.x,
                    originY = cardOrigin.y + safeViewportTopInsetPx,
                    rootWidth = rootWidthPx,
                    rootHeight = rootHeightPx
                )
                val pressDirty = view.setPressSpec(press, pressX, pressY)
                val textureDirty = view.setBackdropTextures(blurBitmap, lensBitmap)
                val styleDirty = view.setGlassStyle(border)
                if (surfaceDirty || specDirty || samplingDirty || pressDirty || textureDirty || styleDirty) {
                    view.requestRender()
                }
            }
        )
    }
}

private class NewOpenGLGlassCardHostView(context: Context) : FrameLayout(context) {
    private val textureView = NewOpenGLGlassCardTextureView(context)

    private var stableSurfaceWidth = 1
    private var stableSurfaceHeight = 1
    private var visibleSurfaceWidth = 1
    private var visibleSurfaceHeight = 1
    private var stableSurfaceAnchorY = NEW_GLASS_STABLE_SURFACE_FALLBACK_ANCHOR_Y
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

        val rootSizeChanged =
            abs(safeRootWidth - lastRootWidth) > 2 ||
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

        val lp = textureView.layoutParams as? LayoutParams
        val layoutParamDirty =
            lp == null ||
                lp.width != stableSurfaceWidth ||
                lp.height != stableSurfaceHeight

        if (layoutParamDirty) {
            textureView.layoutParams = LayoutParams(stableSurfaceWidth, stableSurfaceHeight)
        }

        val layoutChanged = sizeChanged || layoutParamDirty
        if (layoutChanged) {
            geometryAwaitingLayout = true
            requestLayout()
        }
        return layoutChanged
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        visibleSurfaceWidth = (right - left).coerceAtLeast(1)
        visibleSurfaceHeight = (bottom - top).coerceAtLeast(1)
        layoutStableSurfaceChild()
        geometryAwaitingLayout = false
        val glassDirty = syncGlassSpecToTexture()
        val samplingDirty = syncSamplingSpecToTexture()
        if (glassDirty || samplingDirty) textureView.requestRender()
    }

    fun setGlassSpec(width: Float, height: Float, rectOffsetY: Float, radius: Float, intensity: Float): Boolean {
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
        latestRootWidth = rootWidth
        latestRootHeight = rootHeight
        return if (geometryAwaitingLayout) false else syncSamplingSpecToTexture()
    }

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float): Boolean =
        textureView.setPressSpec(progress, centerX, centerY)

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap): Boolean =
        textureView.setBackdropTextures(blurBitmap, lensBitmap)

    fun setGlassStyle(style: GlassBorderStyle): Boolean =
        textureView.setGlassStyle(style)

    fun requestRender() {
        if (!geometryAwaitingLayout) textureView.requestRender()
    }

    private fun layoutStableSurfaceChild() {
        textureView.translationY = 0f
        textureView.layout(0, 0, stableSurfaceWidth, stableSurfaceHeight)
    }

    private fun syncGlassSpecToTexture(): Boolean =
        textureView.setGlassSpec(
            latestGlassWidth.coerceAtLeast(1f),
            latestGlassHeight.coerceAtLeast(1f),
            latestRectOffsetY,
            latestRadius,
            latestIntensity
        )

    private fun syncSamplingSpecToTexture(): Boolean =
        textureView.setSamplingSpec(latestOriginX, latestOriginY, latestRootWidth, latestRootHeight)
}

private class NewOpenGLGlassCardTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: NewCardGlassEglThread? = null
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
    private var latestPressProgress = 0f
    private var latestPressCenterX = 0.5f
    private var latestPressCenterY = 0.5f
    private var latestStyle = GlassBorderStyle()
    private var latestStyleSignature = latestStyle.newOpenGlSignature()

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
        val dirty = abs(nextWidth - latestWidth) > NEW_GLASS_SPEC_EPSILON_PX ||
            abs(nextHeight - latestHeight) > NEW_GLASS_SPEC_EPSILON_PX ||
            abs(nextRectOffsetY - latestRectOffsetY) > NEW_GLASS_SPEC_EPSILON_PX ||
            abs(radius - latestRadius) > NEW_GLASS_SPEC_EPSILON_PX ||
            abs(intensity - latestIntensity) > NEW_GLASS_INTENSITY_EPSILON
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
        val dirty = abs(originX - latestOriginX) > NEW_GLASS_ORIGIN_EPSILON_PX ||
            abs(originY - latestOriginY) > NEW_GLASS_ORIGIN_EPSILON_PX ||
            abs(nextRootWidth - latestRootWidth) > NEW_GLASS_SPEC_EPSILON_PX ||
            abs(nextRootHeight - latestRootHeight) > NEW_GLASS_SPEC_EPSILON_PX
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
        val dirty = abs(nextProgress - latestPressProgress) > NEW_GLASS_PRESS_EPSILON ||
            abs(nextCenterX - latestPressCenterX) > NEW_GLASS_PRESS_CENTER_EPSILON ||
            abs(nextCenterY - latestPressCenterY) > NEW_GLASS_PRESS_CENTER_EPSILON
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
        val nextSignature = style.newOpenGlSignature()
        val dirty = nextSignature != latestStyleSignature
        latestStyle = style
        latestStyleSignature = nextSignature
        if (dirty) renderThread?.setGlassStyle(style)
        return dirty
    }

    fun requestRender() {
        renderThread?.requestRender()
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        renderThread?.shutdown()
        renderThread = NewCardGlassEglThread(Surface(surfaceTexture), width, height).also { thread ->
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

private class NewCardGlassEglThread(
    private val surface: Surface,
    width: Int,
    height: Int
) : Thread("NewOpenGLGlassCardTextureThread") {
    private val renderer = NewOpenGLGlassCardRenderer()
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

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap) =
        renderer.setBackdropTextures(blurBitmap, lensBitmap)

    fun setGlassStyle(style: GlassBorderStyle) =
        renderer.setGlassStyle(style)

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

private data class NewCardGlassDrawSpec(
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

private data class NewOpenGLGlassStyleSignature(
    val visibility: Float,
    val maxAlpha: Float,
    val bodyWidth: Float,
    val bodyCurve: Float,
    val bodyGain: Float,
    val bodyBandPos: Float,
    val bodyBandWidth: Float,
    val bodyBandGain: Float,
    val outerRimWidthPx: Float,
    val outerRimCompression: Float,
    val outerRimReachPx: Float,
    val outerRimGain: Float,
    val innerWallOffsetPx: Float,
    val innerWallWidthPx: Float,
    val innerWallGain: Float,
    val innerWallFalloff: Float,
    val innerWallReachPx: Float,
    val darkExtract: Float,
    val edgeShoulderWidthPx: Float,
    val edgeTangentSmear: Float
)

private fun GlassBorderStyle.newOpenGlSignature(): NewOpenGLGlassStyleSignature =
    NewOpenGLGlassStyleSignature(
        visibility = openGlVisibility.coerceIn(0f, 20f),
        maxAlpha = openGlMaxAlpha.coerceIn(0f, 1f),
        bodyWidth = newOpenGlBodyWidth.coerceIn(0.18f, 1.5f),
        bodyCurve = newOpenGlBodyCurve.coerceIn(0.20f, 3.2f),
        bodyGain = newOpenGlBodyGain.coerceIn(0f, 900f),
        bodyBandPos = newOpenGlBodyBandPos.coerceIn(0.55f, 0.98f),
        bodyBandWidth = newOpenGlBodyBandWidth.coerceIn(0.015f, 0.24f),
        bodyBandGain = newOpenGlBodyBandGain.coerceIn(0f, 1500f),
        outerRimWidthPx = newOpenGlOuterRimWidthPx.coerceIn(0.6f, 14f),
        outerRimCompression = newOpenGlOuterRimCompression.coerceIn(0.25f, 3f),
        outerRimReachPx = newOpenGlOuterRimReachPx.coerceIn(0f, 32f),
        outerRimGain = newOpenGlOuterRimGain.coerceIn(0f, 2.5f),
        innerWallOffsetPx = newOpenGlInnerWallOffsetPx.coerceIn(1f, 18f),
        innerWallWidthPx = newOpenGlInnerWallWidthPx.coerceIn(2f, 34f),
        innerWallGain = newOpenGlInnerWallGain.coerceIn(0f, 420f),
        innerWallFalloff = newOpenGlInnerWallFalloff.coerceIn(0.25f, 4f),
        innerWallReachPx = newOpenGlInnerWallReachPx.coerceIn(0f, 42f),
        darkExtract = newOpenGlDarkExtract.coerceIn(0f, 1.6f),
        edgeShoulderWidthPx = newOpenGlEdgeShoulderWidthPx.coerceIn(4f, 38f),
        edgeTangentSmear = newOpenGlEdgeTangentSmear.coerceIn(0f, 160f)
    )

private class NewOpenGLGlassCardRenderer {
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
    private var bodyHandle = 0
    private var bodyBandHandle = 0
    private var outerRimHandle = 0
    private var innerWallHandle = 0
    private var edgeParamsHandle = 0
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
        bodyHandle = GLES20.glGetUniformLocation(program, "uBody")
        bodyBandHandle = GLES20.glGetUniformLocation(program, "uBodyBand")
        outerRimHandle = GLES20.glGetUniformLocation(program, "uOuterRim")
        innerWallHandle = GLES20.glGetUniformLocation(program, "uInnerWall")
        edgeParamsHandle = GLES20.glGetUniformLocation(program, "uEdgeParams")
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
            NewCardGlassDrawSpec(
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
            style.newOpenGlBrightness.coerceIn(0.4f, 2.2f),
            0f
        )
        GLES20.glUniform4f(
            bodyHandle,
            style.newOpenGlBodyWidth.coerceIn(0.18f, 1.5f),
            style.newOpenGlBodyCurve.coerceIn(0.20f, 3.2f),
            style.newOpenGlBodyGain.coerceIn(0f, 900f),
            0f
        )
        GLES20.glUniform4f(
            bodyBandHandle,
            style.newOpenGlBodyBandPos.coerceIn(0.55f, 0.98f),
            style.newOpenGlBodyBandWidth.coerceIn(0.015f, 0.24f),
            style.newOpenGlBodyBandGain.coerceIn(0f, 1500f),
            0f
        )
        GLES20.glUniform4f(
            outerRimHandle,
            style.newOpenGlOuterRimWidthPx.coerceIn(0.6f, 14f),
            style.newOpenGlOuterRimCompression.coerceIn(0.25f, 3f),
            style.newOpenGlOuterRimReachPx.coerceIn(0f, 32f),
            style.newOpenGlOuterRimGain.coerceIn(0f, 2.5f)
        )
        GLES20.glUniform4f(
            innerWallHandle,
            style.newOpenGlInnerWallOffsetPx.coerceIn(1f, 18f),
            style.newOpenGlInnerWallWidthPx.coerceIn(2f, 34f),
            style.newOpenGlInnerWallGain.coerceIn(0f, 420f),
            style.newOpenGlInnerWallFalloff.coerceIn(0.25f, 4f)
        )
        GLES20.glUniform4f(
            edgeParamsHandle,
            style.newOpenGlInnerWallReachPx.coerceIn(0f, 42f),
            style.newOpenGlDarkExtract.coerceIn(0f, 1.6f),
            style.newOpenGlEdgeShoulderWidthPx.coerceIn(4f, 38f),
            style.newOpenGlEdgeTangentSmear.coerceIn(0f, 160f)
        )
        GLES20.glUniform4f(
            opticsHandle,
            style.newOpenGlClarity.coerceIn(0f, 1.6f),
            style.newOpenGlTangentSmear.coerceIn(0f, 1f),
            0f,
            0f
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
        if (program != 0) GLES20.glDeleteProgram(program)
        blurTextureId = 0
        lensTextureId = 0
        program = 0
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
            error("New OpenGL glass program link failed: $log")
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
            error("New OpenGL glass shader compile failed: $log")
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
            uniform vec4 uBody;
            uniform vec4 uBodyBand;
            uniform vec4 uOuterRim;
            uniform vec4 uInnerWall;
            uniform vec4 uEdgeParams;
            uniform vec4 uOptics;
            uniform sampler2D uBlurTexture;
            uniform sampler2D uLensTexture;

            float sat(float x){return clamp(x,0.0,1.0);}
            float me(vec2 u){return min(min(u.x,1.0-u.x),min(u.y,1.0-u.y));}
            float borderFade(vec2 u){return smoothstep(0.010,0.085,me(u));}
            float luma(vec3 c){return dot(c,vec3(0.299,0.587,0.114));}

            vec2 texUv(vec2 uv){return clamp(uv,0.0,1.0);}
            vec2 globalUv(vec2 visualCoord){return clamp((uCardOrigin+visualCoord)/max(uRootResolution,vec2(1.0)),0.0,1.0);}

            vec3 fallbackBackdrop(vec2 uv){
                float h=smoothstep(0.0,1.0,uv.y);
                return mix(vec3(0.05,0.12,0.30),vec3(0.30,0.55,0.75),h);
            }
            vec3 blurSrc(vec2 uv){
                vec3 fallback=fallbackBackdrop(uv);
                vec3 realColor=texture2D(uBlurTexture,texUv(uv)).rgb;
                return mix(fallback,realColor,sat(uTextureReady));
            }
            vec3 lensSrc(vec2 uv){
                vec3 fallback=fallbackBackdrop(uv);
                vec3 realColor=texture2D(uLensTexture,texUv(uv)).rgb;
                return mix(fallback,realColor,sat(uTextureReady));
            }

            vec3 blurSample(vec2 uv){
                vec2 px=vec2(1.0)/max(uRootResolution,vec2(1.0));
                vec3 c=blurSrc(uv)*0.22;
                c+=blurSrc(uv+vec2(px.x*4.0,0.0))*0.10;
                c+=blurSrc(uv-vec2(px.x*4.0,0.0))*0.10;
                c+=blurSrc(uv+vec2(0.0,px.y*4.0))*0.10;
                c+=blurSrc(uv-vec2(0.0,px.y*4.0))*0.10;
                c+=blurSrc(uv+vec2(px.x*10.0,0.0))*0.08;
                c+=blurSrc(uv-vec2(px.x*10.0,0.0))*0.08;
                c+=blurSrc(uv+vec2(0.0,px.y*10.0))*0.08;
                c+=blurSrc(uv-vec2(0.0,px.y*10.0))*0.08;
                c+=blurSrc(uv+vec2(px.x*8.0,px.y*6.0))*0.07;
                c+=blurSrc(uv-vec2(px.x*8.0,px.y*6.0))*0.07;
                return c;
            }

            float boxSdf(vec2 c,vec2 z,float ra){
                vec2 p=c-z*0.5;
                vec2 q=abs(p)-max(z*0.5-vec2(ra),vec2(0.0));
                return length(max(q,0.0))+min(max(q.x,q.y),0.0)-ra;
            }

            float rhoAt(vec2 c,vec2 z){
                vec2 uv=clamp(c/z,vec2(0.0),vec2(1.0));
                vec2 q=abs(uv*2.0-1.0);
                float n=5.8;
                return pow(pow(q.x,n)+pow(q.y,n),1.0/n);
            }

            float gauss(float x,float m,float w){
                float q=(x-m)/max(w,0.0001);
                return exp(-q*q);
            }

            float heightAt(vec2 c,vec2 z,float ra){
                float sd=boxSdf(c,z,ra);
                float shape=1.0-smoothstep(0.0,4.0,sd);
                float rho=rhoAt(c,z);
                float body=pow(sat(1.0-rho),max(uBody.y,0.20));
                body=pow(body,max(0.28,2.0-uBody.y*0.55));
                body*=mix(1.0,1.9,sat(uBody.x*0.75));
                float broad=pow(sat(body),0.72);
                float shell=gauss(rho,uBodyBand.x,uBodyBand.y);
                float shoulder=gauss(rho,uBodyBand.x-0.025,uBodyBand.y*1.8);
                float asym=0.015*sin((c.x/z.x)*4.5+(c.y/z.y)*2.1)+0.012*sin((c.x/z.x+c.y/z.y)*2.7+1.4);
                return (broad+shell*1.55+shoulder*0.85+asym*sat(broad))*shape;
            }

            vec2 softLimit(vec2 v,float l){
                float n=length(v);
                float m=n/(1.0+n/max(l,1.0));
                return v*(m/max(n,0.0001));
            }

            void main(){
                vec2 coord=vec2(gl_FragCoord.x,uResolution.y-gl_FragCoord.y);
                vec2 rectOffset=uRect.xy;
                vec2 z=max(uRect.zw,vec2(1.0));
                vec2 c=coord-rectOffset;
                float ra=min(uRadius,min(z.x,z.y)*0.5);
                float sd=boxSdf(c,z,ra);
                float mask=1.0-smoothstep(0.0,1.4,sd);
                if(mask<=0.001) discard;

                vec2 u=c/z;
                float rho=rhoAt(c,z);
                float stepPx=2.0;
                float hL=heightAt(c-vec2(stepPx,0.0),z,ra);
                float hR=heightAt(c+vec2(stepPx,0.0),z,ra);
                float hU=heightAt(c-vec2(0.0,stepPx),z,ra);
                float hD=heightAt(c+vec2(0.0,stepPx),z,ra);
                float hC=heightAt(c,z,ra);

                vec2 grad=vec2(hR-hL,hD-hU)*0.5;
                float slope=length(grad);
                vec2 n=grad/max(slope,0.0001);
                vec2 t=vec2(-n.y,n.x);
                float fade=borderFade(u);

                float bodyPresence=sat(pow(sat(1.0-rho),0.42)*(0.48+uBody.x*0.62));
                float shellPresence=gauss(rho,uBodyBand.x,uBodyBand.y)+gauss(rho,uBodyBand.x-0.028,uBodyBand.y*1.9)*0.55;

                float sideRho=abs(u.x*2.0-1.0);
                float sideVertical=smoothstep(0.055,0.30,u.y)*smoothstep(0.055,0.30,1.0-u.y);
                float sideCore=gauss(sideRho,uBodyBand.x,max(uBodyBand.y*0.72,0.014));
                float sideShoulder=gauss(sideRho,uBodyBand.x-0.060,max(uBodyBand.y*1.65,0.026))*0.52;
                float sideBodyBand=(sideCore+sideShoulder)*sideVertical*sat(0.42+bodyPresence*0.38+shellPresence*0.40);
                float sidePresence=max(shellPresence,sideBodyBand*0.96);

                float energy=pow(sat((slope*11.0+hC*0.22)*max(uBodyBand.z,1.0)),max(0.32,max(uBodyBand.z,1.0)*0.55));
                float bodyPx=uBody.z*bodyPresence*(0.35+hC*0.55+energy*0.35);
                float shellPx=uBodyBand.z*shellPresence*(0.36+energy*0.70+slope*2.4);

                vec2 flow=grad*(bodyPx+shellPx);
                flow.x*=mix(1.0,1.72,sideBodyBand);

                vec2 inwardDir=(vec2(0.5)-u);
                inwardDir.y*=0.72;
                flow+=inwardDir*(bodyPresence*uBody.z*0.08);

                float sidePull=(uBodyBand.z*0.185+uBody.z*0.105)*sideBodyBand*(0.72+hC*0.42+bodyPresence*0.25);
                flow+=vec2(sign(0.5-u.x),0.0)*sidePull;
                flow.y+=(0.5-u.y)*sideBodyBand*(uBodyBand.z*0.030+uBody.z*0.022)*(0.45+hC*0.35);

                float edgeInset=max(-sd,0.0);
                float outerW=max(uOuterRim.x,0.35);
                float outerComp=max(uOuterRim.y,0.25);
                float outerReach=max(uOuterRim.z,0.0);
                float outerGain=uOuterRim.w;
                float wallOffset=max(uInnerWall.x,0.5);
                float wallW=max(uInnerWall.y,1.0);
                float wallGain=uInnerWall.z;
                float wallFalloff=max(uInnerWall.w,0.25);
                float wallReach=uEdgeParams.x;
                float darkExtract=uEdgeParams.y;
                float shoulderW=max(uEdgeParams.z,wallOffset+wallW+1.0);
                float tangentSmear=uEdgeParams.w;

                float outerBase=sat(1.0-edgeInset/outerW);
                float outerWindow=smoothstep(outerW+1.2,0.0,edgeInset);
                float outerCompressed=pow(outerBase,max(0.32,1.0/outerComp))*outerWindow;
                float outerCrest=gauss(edgeInset,outerW*0.42,max(outerW*0.32,0.55))*outerWindow;
                float outerShoulder=pow(sat(1.0-edgeInset/(outerW+outerReach*0.34+2.0)),1.55)*0.28;
                float outerRim=sat(outerCompressed*0.82+outerCrest*0.88+outerShoulder);

                float wallStart=smoothstep(wallOffset-0.9,wallOffset+0.45,edgeInset);
                float wallEnd=1.0-smoothstep(wallOffset+wallW,wallOffset+wallW+2.4,edgeInset);
                float wallT=sat((edgeInset-wallOffset)/max(wallW,0.001));
                float innerTransitionWall=wallStart*wallEnd*pow(1.0-wallT,wallFalloff);
                float softShoulder=pow(sat(1.0-edgeInset/shoulderW),2.0)*(1.0-sat(innerTransitionWall*0.55));
                float darkMask=innerTransitionWall*sat(0.35+softShoulder*0.45);

                float sdL=boxSdf(c-vec2(stepPx,0.0),z,ra);
                float sdR=boxSdf(c+vec2(stepPx,0.0),z,ra);
                float sdU=boxSdf(c-vec2(0.0,stepPx),z,ra);
                float sdD=boxSdf(c+vec2(0.0,stepPx),z,ra);
                vec2 sdGrad=vec2(sdR-sdL,sdD-sdU)*0.5;
                float sdSlope=length(sdGrad);
                vec2 edgeN=sdGrad/max(sdSlope,0.0001);
                vec2 edgeT=vec2(-edgeN.y,edgeN.x);
                float edgeProfile=sat(outerRim+innerTransitionWall+softShoulder*0.16);

                float press=sat(uPress.x);
                vec2 pressCenter=clamp(uPress.yz,vec2(0.0),vec2(1.0));
                vec2 pressDelta=u-pressCenter;
                pressDelta.x*=min(z.x/max(z.y,1.0),2.2);
                float pressField=pow(sat(1.0-length(pressDelta)*0.92),1.45)*press;
                vec2 pressInward=(pressCenter-u)*z*(0.012*press+0.028*pressField);

                float outerFlow=outerRim*outerGain*(34.0+outerReach*2.6+outerW*4.5)*(0.65+outerCrest*1.20+outerComp*0.28);
                flow+=edgeN*outerFlow;
                flow+=edgeN*wallGain*(innerTransitionWall*(1.0+slope*0.22)+softShoulder*0.12);
                flow+=pressInward;

                float lim=max(me(u)*max(min(z.x,z.y),1.0)*0.92,12.0);
                flow=softLimit(flow,min(1120.0,lim+bodyPresence*150.0+shellPresence*175.0+sideBodyBand*420.0+edgeProfile*620.0))*fade;

                vec2 ru=globalUv(c)+flow/max(uRootResolution,vec2(1.0));
                vec3 clear=lensSrc(ru);
                vec3 medium=blurSample(ru);
                float clarity=sat(uOptics.x*(0.18+max(sidePresence,edgeProfile)*0.82+slope*0.30)+innerTransitionWall*0.26+outerCompressed*0.34+outerCrest*0.22);
                vec3 col=mix(medium,clear,clarity);

                vec2 edgePx=edgeN/max(uRootResolution,vec2(1.0));
                vec3 outerShallow=lensSrc(ru+edgePx*(0.20+outerW*0.12));
                vec3 outerDeep=lensSrc(ru-edgePx*(outerReach*1.25+outerW*2.4+outerGain*8.0));
                vec3 outerFar=lensSrc(ru-edgePx*(outerReach*2.2+outerW*4.2+outerGain*14.0));
                vec3 outerSample=mix(mix(outerShallow,outerDeep,sat(0.48+outerComp*0.12+outerCrest*0.25)),outerFar,sat(outerGain*0.24+outerCrest*0.36));
                float outerMix=sat((outerCompressed*0.85+outerCrest*1.25+outerShoulder*0.35)*outerGain*(1.05+outerComp*0.30));
                col=mix(col,outerSample,outerMix);

                vec3 wallOuter=lensSrc(ru+edgePx*(wallReach*0.30+outerW*0.40));
                vec3 wallInner=lensSrc(ru-edgePx*(wallReach+wallW*0.40));
                vec3 wallSample=mix(wallInner,wallOuter,sat(0.52+innerTransitionWall*0.30));
                float wallMix=sat(innerTransitionWall*(0.72+wallGain/900.0));
                col=mix(col,wallSample,wallMix);

                vec3 darkSample=mix(wallSample,min(wallOuter,wallInner),0.78);
                float darkMix=darkMask*darkExtract*(0.42+(1.0-min(luma(wallOuter),luma(wallInner)))*0.58);
                col=mix(col,darkSample,darkMix*0.58);
                col*=1.0-darkMix*0.13;

                float smear=sat(uOptics.y*max(sidePresence,edgeProfile)*(0.18+slope*3.2));
                if(smear>0.001){
                    vec2 tp=t*(5.0+28.0*smear)/max(uRootResolution,vec2(1.0));
                    vec3 drag=lensSrc(ru+tp)*0.38+lensSrc(ru-tp)*0.38+medium*0.24;
                    col=mix(col,drag,smear*0.34);
                }

                float edgeSmear=sat((tangentSmear/160.0)*(innerTransitionWall*0.88+outerRim*0.34+softShoulder*0.14));
                if(edgeSmear>0.001){
                    float bias=sign(dot(flow,edgeT)+(u.x-0.5)*0.03+(u.y-0.5)*0.01+0.0001);
                    vec2 ep=edgeT*bias*(4.0+tangentSmear*0.16+innerTransitionWall*9.0)/max(uRootResolution,vec2(1.0));
                    vec3 eDrag=lensSrc(ru+ep)*0.44+lensSrc(ru-ep)*0.44+wallOuter*0.12;
                    col=mix(col,eDrag,edgeSmear*0.36);
                }

                float rim=sidePresence*sat(0.35+slope*3.5+sideBodyBand*0.45);
                float edgeLineLift=(outerCompressed+outerCrest*0.80)*(0.18+abs(luma(outerShallow)-luma(outerFar))*1.34);
                float edgeWallLift=innerTransitionWall*(0.06+abs(luma(wallOuter)-luma(wallInner))*0.34);
                float fres=pow(1.0-sat(me(u)*3.0),1.35);

                col+=vec3(0.03,0.07,0.10)*rim*0.18;
                col+=vec3(0.010,0.015,0.018)*(edgeLineLift+edgeWallLift);
                col*=(1.0+rim*0.05+fres*0.028+edgeLineLift*0.16+edgeWallLift*0.09);
                col*=uMaterial.z;
                col=clamp(col,0.0,1.0);

                float alpha=0.90*mask*sat(uMaterial.x/20.0)*uMaterial.y*uIntensity;
                gl_FragColor=vec4(col,alpha);
            }
        """
    }
}
