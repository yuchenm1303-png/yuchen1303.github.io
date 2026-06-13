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
                val surfaceDirty = view.setStableSurfaceSize(widthPx.roundToInt(), heightPx.roundToInt(), rootWidthPx.roundToInt(), rootHeightPx.roundToInt())
                val specDirty = view.setGlassSpec(widthPx, viewportHeightPx, safeViewportTopInsetPx, radiusPx, intensity)
                val samplingDirty = view.setSamplingSpec(cardOrigin.x, cardOrigin.y + safeViewportTopInsetPx, rootWidthPx, rootHeightPx)
                val pressDirty = view.setPressSpec(press, pressX, pressY)
                val textureDirty = view.setBackdropTextures(blurBitmap, lensBitmap)
                val styleDirty = view.setGlassStyle(border)
                if (surfaceDirty || specDirty || samplingDirty || pressDirty || textureDirty || styleDirty) view.requestRender()
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
        val rootSizeChanged = abs(safeRootWidth - lastRootWidth) > 2 || abs(safeRootHeight - lastRootHeight) > 2
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
        val layoutParamDirty = lp == null || lp.width != stableSurfaceWidth || lp.height != stableSurfaceHeight
        if (layoutParamDirty) textureView.layoutParams = LayoutParams(stableSurfaceWidth, stableSurfaceHeight)
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
        textureView.translationY = 0f
        textureView.layout(0, 0, stableSurfaceWidth, stableSurfaceHeight)
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

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float): Boolean = textureView.setPressSpec(progress, centerX, centerY)
    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap): Boolean = textureView.setBackdropTextures(blurBitmap, lensBitmap)
    fun setGlassStyle(style: GlassBorderStyle): Boolean = textureView.setGlassStyle(style)
    fun requestRender() { if (!geometryAwaitingLayout) textureView.requestRender() }
    private fun syncGlassSpecToTexture(): Boolean = textureView.setGlassSpec(latestGlassWidth, latestGlassHeight, latestRectOffsetY, latestRadius, latestIntensity)
    private fun syncSamplingSpecToTexture(): Boolean = textureView.setSamplingSpec(latestOriginX, latestOriginY, latestRootWidth, latestRootHeight)
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
        val dirty = abs(width - latestWidth) > NEW_GLASS_SPEC_EPSILON_PX || abs(height - latestHeight) > NEW_GLASS_SPEC_EPSILON_PX || abs(rectOffsetY - latestRectOffsetY) > NEW_GLASS_SPEC_EPSILON_PX || abs(radius - latestRadius) > NEW_GLASS_SPEC_EPSILON_PX || abs(intensity - latestIntensity) > NEW_GLASS_INTENSITY_EPSILON
        latestWidth = width.coerceAtLeast(1f)
        latestHeight = height.coerceAtLeast(1f)
        latestRectOffsetY = rectOffsetY
        latestRadius = radius
        latestIntensity = intensity
        if (dirty) renderThread?.setGlassSpec(latestWidth, latestHeight, latestRectOffsetY, latestRadius, latestIntensity)
        return dirty
    }

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float): Boolean {
        val nextRootWidth = rootWidth.coerceAtLeast(1f)
        val nextRootHeight = rootHeight.coerceAtLeast(1f)
        val dirty = abs(originX - latestOriginX) > NEW_GLASS_ORIGIN_EPSILON_PX || abs(originY - latestOriginY) > NEW_GLASS_ORIGIN_EPSILON_PX || abs(nextRootWidth - latestRootWidth) > NEW_GLASS_SPEC_EPSILON_PX || abs(nextRootHeight - latestRootHeight) > NEW_GLASS_SPEC_EPSILON_PX
        latestOriginX = originX
        latestOriginY = originY
        latestRootWidth = nextRootWidth
        latestRootHeight = nextRootHeight
        if (dirty) renderThread?.setSamplingSpec(latestOriginX, latestOriginY, latestRootWidth, latestRootHeight)
        return dirty
    }

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float): Boolean {
        val p = progress.coerceIn(0f, 1f)
        val x = centerX.coerceIn(0f, 1f)
        val y = centerY.coerceIn(0f, 1f)
        val dirty = abs(p - latestPressProgress) > NEW_GLASS_PRESS_EPSILON || abs(x - latestPressCenterX) > NEW_GLASS_PRESS_CENTER_EPSILON || abs(y - latestPressCenterY) > NEW_GLASS_PRESS_CENTER_EPSILON
        latestPressProgress = p
        latestPressCenterX = x
        latestPressCenterY = y
        if (dirty) renderThread?.setPressSpec(p, x, y)
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
        val sig = style.newOpenGlSignature()
        val dirty = sig != latestStyleSignature
        latestStyle = style
        latestStyleSignature = sig
        if (dirty) renderThread?.setGlassStyle(style)
        return dirty
    }

    fun requestRender() { renderThread?.requestRender() }

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
    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) { renderThread?.resize(width, height) }
    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean { renderThread?.shutdown(); renderThread = null; return true }
    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
}

private class NewCardGlassEglThread(private val surface: Surface, width: Int, height: Int) : Thread("NewOpenGLGlassCardTextureThread") {
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
    fun setGlassSpec(width: Float, height: Float, rectOffsetY: Float, radius: Float, intensity: Float) = renderer.setGlassSpec(width, height, rectOffsetY, radius, intensity)
    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float) = renderer.setSamplingSpec(originX, originY, rootWidth, rootHeight)
    fun setPressSpec(progress: Float, centerX: Float, centerY: Float) = renderer.setPressSpec(progress, centerX, centerY)
    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap) = renderer.setBackdropTextures(blurBitmap, lensBitmap)
    fun setGlassStyle(style: GlassBorderStyle) = renderer.setGlassStyle(style)
    fun requestRender() { synchronized(renderLock) { pendingRender = true; renderLock.notifyAll() } }
    fun resize(width: Int, height: Int) { viewportWidth = max(width, 1); viewportHeight = max(height, 1); sizeDirty = true; requestRender() }
    fun shutdown() { running = false; requestRender() }
    override fun run() {
        try {
            initEgl()
            renderer.onSurfaceCreated()
            renderer.onSurfaceChanged(viewportWidth, viewportHeight)
            sizeDirty = false
            while (running) {
                synchronized(renderLock) { while (!pendingRender && running) renderLock.wait(); pendingRender = false }
                if (!running) break
                if (sizeDirty) { renderer.onSurfaceChanged(viewportWidth, viewportHeight); sizeDirty = false }
                renderer.onDrawFrame()
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            }
        } finally { runCatching { renderer.onRelease() }; releaseEgl(); surface.release() }
    }
    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1))
        val attrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_STENCIL_SIZE, 0, EGL14.EGL_NONE)
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, attrs, 0, configs, 0, configs.size, count, 0))
        val config = configs[0] ?: error("No EGL config")
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
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

private data class NewCardGlassDrawSpec(val cardWidth: Float, val cardHeight: Float, val rectOffsetY: Float, val cardRadius: Float, val cardIntensity: Float, val cardOriginX: Float, val cardOriginY: Float, val rootWidth: Float, val rootHeight: Float, val pressProgress: Float, val pressCenterX: Float, val pressCenterY: Float, val style: GlassBorderStyle)

private data class NewOpenGLGlassStyleSignature(
    val visibility: Float, val maxAlpha: Float, val brightness: Float, val clarity: Float, val tangentSmear: Float, val blurRadius: Float,
    val bodyWidth: Float, val bodyCurve: Float, val bodyGain: Float, val bodyBandPos: Float, val bodyBandWidth: Float, val bodyBandGain: Float,
    val outerRimWidthPx: Float, val outerRimCompression: Float, val outerRimReachPx: Float, val outerRimGain: Float,
    val innerWallOffsetPx: Float, val innerWallWidthPx: Float, val innerWallGain: Float, val innerWallFalloff: Float, val innerWallReachPx: Float, val darkExtract: Float, val edgeShoulderWidthPx: Float, val edgeTangentSmear: Float,
    val legacyRingWidth: Float, val legacyEdgePull: Float, val legacyBrightness: Float, val legacyDark: Float, val legacyBodyPull: Float
)

private fun GlassBorderStyle.newOpenGlSignature(): NewOpenGLGlassStyleSignature = NewOpenGLGlassStyleSignature(
    visibility = openGlVisibility.coerceIn(0f, 20f), maxAlpha = openGlMaxAlpha.coerceIn(0f, 1f), brightness = newOpenGlBrightness.coerceIn(0.4f, 2.2f), clarity = newOpenGlClarity.coerceIn(0f, 1.6f), tangentSmear = newOpenGlTangentSmear.coerceIn(0f, 1f), blurRadius = edgeBlurDp.coerceIn(0f, 128f),
    bodyWidth = newOpenGlBodyWidth.coerceIn(0.18f, 1.5f), bodyCurve = newOpenGlBodyCurve.coerceIn(0.20f, 3.2f), bodyGain = newOpenGlBodyGain.coerceIn(0f, 900f), bodyBandPos = newOpenGlBodyBandPos.coerceIn(0.55f, 0.98f), bodyBandWidth = newOpenGlBodyBandWidth.coerceIn(0.015f, 0.24f), bodyBandGain = newOpenGlBodyBandGain.coerceIn(0f, 1500f),
    outerRimWidthPx = newOpenGlOuterRimWidthPx.coerceIn(0.6f, 14f), outerRimCompression = newOpenGlOuterRimCompression.coerceIn(0.25f, 3f), outerRimReachPx = newOpenGlOuterRimReachPx.coerceIn(0f, 32f), outerRimGain = newOpenGlOuterRimGain.coerceIn(0f, 2.5f),
    innerWallOffsetPx = newOpenGlInnerWallOffsetPx.coerceIn(1f, 18f), innerWallWidthPx = newOpenGlInnerWallWidthPx.coerceIn(2f, 34f), innerWallGain = newOpenGlInnerWallGain.coerceIn(0f, 420f), innerWallFalloff = newOpenGlInnerWallFalloff.coerceIn(0.25f, 4f), innerWallReachPx = newOpenGlInnerWallReachPx.coerceIn(0f, 42f), darkExtract = newOpenGlDarkExtract.coerceIn(0f, 1.6f), edgeShoulderWidthPx = newOpenGlEdgeShoulderWidthPx.coerceIn(4f, 38f), edgeTangentSmear = newOpenGlEdgeTangentSmear.coerceIn(0f, 160f),
    legacyRingWidth = ringWidthDp.coerceIn(0f, 96f), legacyEdgePull = edgePullDp.coerceIn(-600f, 600f), legacyBrightness = edgeBrightness.coerceIn(-5f, 5f), legacyDark = openGlDarkScale.coerceIn(-10f, 10f), legacyBodyPull = openGlPullScale.coerceIn(-300f, 300f)
)

private class NewOpenGLGlassCardRenderer {
    private val quadVertices: FloatBuffer = ByteBuffer.allocateDirect(FULLSCREEN_QUAD.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(FULLSCREEN_QUAD); position(0) }
    private val textureLock = Any()
    private val specLock = Any()
    private var pendingBlurBitmap: Bitmap? = null
    private var pendingLensBitmap: Bitmap? = null
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
    private var legacyRimHandle = 0
    private var legacyRim2Handle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    fun setGlassSpec(width: Float, height: Float, rectOffsetY: Float, radius: Float, intensity: Float) { synchronized(specLock) { cardWidth = width.coerceAtLeast(1f); cardHeight = height.coerceAtLeast(1f); this.rectOffsetY = rectOffsetY; cardRadius = radius; cardIntensity = intensity } }
    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float) { synchronized(specLock) { cardOriginX = originX; cardOriginY = originY; this.rootWidth = rootWidth.coerceAtLeast(1f); this.rootHeight = rootHeight.coerceAtLeast(1f) } }
    fun setPressSpec(progress: Float, centerX: Float, centerY: Float) { synchronized(specLock) { pressProgress = progress.coerceIn(0f, 1f); pressCenterX = centerX.coerceIn(0f, 1f); pressCenterY = centerY.coerceIn(0f, 1f) } }
    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap) { synchronized(textureLock) { pendingBlurBitmap = blurBitmap; pendingLensBitmap = lensBitmap } }
    fun setGlassStyle(style: GlassBorderStyle) { synchronized(specLock) { this.style = style } }
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
        legacyRimHandle = GLES20.glGetUniformLocation(program, "uLegacyRim")
        legacyRim2Handle = GLES20.glGetUniformLocation(program, "uLegacyRim2")
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
    fun onSurfaceChanged(width: Int, height: Int) { viewportWidth = max(width, 1); viewportHeight = max(height, 1); GLES20.glViewport(0, 0, viewportWidth, viewportHeight) }
    fun onDrawFrame() {
        uploadPendingTexturesIfNeeded()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        val drawSpec = synchronized(specLock) { NewCardGlassDrawSpec(cardWidth, cardHeight, rectOffsetY, cardRadius, cardIntensity, cardOriginX, cardOriginY, rootWidth, rootHeight, pressProgress, pressCenterX, pressCenterY, style) }
        val s = drawSpec.style
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES20.glUniform2f(cardOriginHandle, drawSpec.cardOriginX, drawSpec.cardOriginY)
        GLES20.glUniform2f(rootResolutionHandle, drawSpec.rootWidth, drawSpec.rootHeight)
        GLES20.glUniform4f(rectHandle, 0f, drawSpec.rectOffsetY, drawSpec.cardWidth, drawSpec.cardHeight)
        GLES20.glUniform1f(radiusHandle, drawSpec.cardRadius.coerceIn(2f, max(drawSpec.cardWidth, drawSpec.cardHeight)))
        GLES20.glUniform1f(intensityHandle, drawSpec.cardIntensity)
        GLES20.glUniform4f(pressHandle, drawSpec.pressProgress, drawSpec.pressCenterX, drawSpec.pressCenterY, 0f)
        GLES20.glUniform1f(textureReadyHandle, if (texturesReady) 1f else 0f)
        GLES20.glUniform4f(materialHandle, s.openGlVisibility.coerceIn(0f, 20f), s.openGlMaxAlpha.coerceIn(0f, 1f), s.newOpenGlBrightness.coerceIn(0.4f, 2.2f), s.edgeBlurDp.coerceIn(0f, 128f))
        GLES20.glUniform4f(bodyHandle, s.newOpenGlBodyWidth.coerceIn(0.18f, 1.5f), s.newOpenGlBodyCurve.coerceIn(0.20f, 3.2f), s.newOpenGlBodyGain.coerceIn(0f, 900f), 0f)
        GLES20.glUniform4f(bodyBandHandle, s.newOpenGlBodyBandPos.coerceIn(0.55f, 0.98f), s.newOpenGlBodyBandWidth.coerceIn(0.015f, 0.24f), s.newOpenGlBodyBandGain.coerceIn(0f, 1500f), 0f)
        GLES20.glUniform4f(outerRimHandle, s.newOpenGlOuterRimWidthPx.coerceIn(0.6f, 14f), s.newOpenGlOuterRimCompression.coerceIn(0.25f, 3f), s.newOpenGlOuterRimReachPx.coerceIn(0f, 32f), s.newOpenGlOuterRimGain.coerceIn(0f, 2.5f))
        GLES20.glUniform4f(innerWallHandle, s.newOpenGlInnerWallOffsetPx.coerceIn(1f, 18f), s.newOpenGlInnerWallWidthPx.coerceIn(2f, 34f), s.newOpenGlInnerWallGain.coerceIn(0f, 420f), s.newOpenGlInnerWallFalloff.coerceIn(0.25f, 4f))
        GLES20.glUniform4f(edgeParamsHandle, s.newOpenGlInnerWallReachPx.coerceIn(0f, 42f), s.newOpenGlDarkExtract.coerceIn(0f, 1.6f), s.newOpenGlEdgeShoulderWidthPx.coerceIn(4f, 38f), s.newOpenGlEdgeTangentSmear.coerceIn(0f, 160f))
        GLES20.glUniform4f(opticsHandle, s.newOpenGlClarity.coerceIn(0f, 1.6f), s.newOpenGlTangentSmear.coerceIn(0f, 1f), 0f, 0f)
        GLES20.glUniform4f(legacyRimHandle, s.ringWidthDp.coerceIn(0f, 96f), s.edgePullDp.coerceIn(-600f, 600f), s.edgeBrightness.coerceIn(-5f, 5f), s.openGlDarkScale.coerceIn(-10f, 10f))
        GLES20.glUniform4f(legacyRim2Handle, s.openGlPullScale.coerceIn(-300f, 300f), s.outerStrokeAlpha.coerceIn(0f, 1.5f), s.topHighlightAlpha.coerceIn(0f, 2f), s.bottomShadowAlpha.coerceIn(0f, 1.2f))
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextureId); GLES20.glUniform1i(blurTextureHandle, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lensTextureId); GLES20.glUniform1i(lensTextureHandle, 1)
        quadVertices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, quadVertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }
    fun onRelease() { val textures = intArrayOf(blurTextureId, lensTextureId); if (blurTextureId != 0 || lensTextureId != 0) GLES20.glDeleteTextures(2, textures, 0); if (program != 0) GLES20.glDeleteProgram(program); blurTextureId = 0; lensTextureId = 0; program = 0 }
    private fun uploadPendingTexturesIfNeeded() {
        val pair = synchronized(textureLock) { val b = pendingBlurBitmap; val l = pendingLensBitmap; if (b != null && l != null) { pendingBlurBitmap = null; pendingLensBitmap = null; b to l } else null }
        val (blur, lens) = pair ?: return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextureId); GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, blur, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lensTextureId); GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, lens, 0)
        texturesReady = true
    }
    private fun configureTexture(id: Int) { GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE) }
}

private fun buildProgram(vertex: String, fragment: String): Int {
    fun shader(type: Int, source: String): Int { val id = GLES20.glCreateShader(type); GLES20.glShaderSource(id, source); GLES20.glCompileShader(id); val ok = IntArray(1); GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0); check(ok[0] != 0) { GLES20.glGetShaderInfoLog(id) }; return id }
    val vs = shader(GLES20.GL_VERTEX_SHADER, vertex)
    val fs = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
    val p = GLES20.glCreateProgram(); GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p); val ok = IntArray(1); GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0); GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs); check(ok[0] != 0) { GLES20.glGetProgramInfoLog(p) }; return p
}

private val FULLSCREEN_QUAD = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

private const val VERTEX_SHADER = """
    attribute vec2 aPosition;
    varying vec2 vUv;
    void main(){
        vUv=aPosition*0.5+0.5;
        gl_Position=vec4(aPosition,0.0,1.0);
    }
"""

private const val FRAGMENT_SHADER = """
    precision highp float;
    varying vec2 vUv;
    uniform vec2 uResolution;
    uniform vec2 uCardOrigin;
    uniform vec2 uRootResolution;
    uniform vec4 uRect;
    uniform float uRadius;
    uniform float uIntensity;
    uniform vec4 uPress;
    uniform float uTextureReady;
    uniform sampler2D uBlurTexture;
    uniform sampler2D uLensTexture;
    uniform vec4 uMaterial;
    uniform vec4 uBody;
    uniform vec4 uBodyBand;
    uniform vec4 uOuterRim;
    uniform vec4 uInnerWall;
    uniform vec4 uEdgeParams;
    uniform vec4 uOptics;
    uniform vec4 uLegacyRim;
    uniform vec4 uLegacyRim2;

    float sat(float x){return clamp(x,0.0,1.0);} 
    float luma(vec3 c){return dot(c,vec3(0.299,0.587,0.114));}
    float me(vec2 u){return min(min(u.x,1.0-u.x),min(u.y,1.0-u.y));}
    vec2 texUv(vec2 uv){return clamp(uv,0.0,1.0);} 
    vec2 globalUv(vec2 visualCoord){return clamp((uCardOrigin+visualCoord)/max(uRootResolution,vec2(1.0)),0.0,1.0);} 
    vec3 fallbackBackdrop(vec2 uv){float h=smoothstep(0.0,1.0,uv.y);return mix(vec3(0.04,0.10,0.27),vec3(0.26,0.45,0.72),h);} 
    vec3 blurSrc(vec2 uv){return mix(fallbackBackdrop(uv),texture2D(uBlurTexture,texUv(uv)).rgb,sat(uTextureReady));}
    vec3 lensSrc(vec2 uv){return mix(fallbackBackdrop(uv),texture2D(uLensTexture,texUv(uv)).rgb,sat(uTextureReady));}
    vec3 blurSample(vec2 uv){
        vec2 px=vec2(max(uMaterial.w,0.0))/max(uRootResolution,vec2(1.0));
        vec3 c=blurSrc(uv)*0.20;
        c+=blurSrc(uv+vec2(px.x*0.18,0.0))*0.10; c+=blurSrc(uv-vec2(px.x*0.18,0.0))*0.10;
        c+=blurSrc(uv+vec2(0.0,px.y*0.18))*0.10; c+=blurSrc(uv-vec2(0.0,px.y*0.18))*0.10;
        c+=blurSrc(uv+vec2(px.x*0.36,px.y*0.24))*0.10; c+=blurSrc(uv-vec2(px.x*0.36,px.y*0.24))*0.10;
        c+=blurSrc(uv+vec2(-px.x*0.30,px.y*0.32))*0.10; c+=blurSrc(uv+vec2(px.x*0.30,-px.y*0.32))*0.10;
        return c;
    }
    float boxSdf(vec2 c,vec2 z,float ra){vec2 p=c-z*0.5;vec2 q=abs(p)-max(z*0.5-vec2(ra),vec2(0.0));return length(max(q,0.0))+min(max(q.x,q.y),0.0)-ra;}
    float rhoAt(vec2 c,vec2 z){vec2 uv=clamp(c/z,vec2(0.0),vec2(1.0));vec2 q=abs(uv*2.0-1.0);float n=5.8;return pow(pow(q.x,n)+pow(q.y,n),1.0/n);} 
    float gauss(float x,float m,float w){float q=(x-m)/max(w,0.0001);return exp(-q*q);} 
    vec2 softLimit(vec2 v,float l){float n=length(v);float m=n/(1.0+n/max(l,1.0));return v*(m/max(n,0.0001));}
    float heightAt(vec2 c,vec2 z,float ra){
        float sd=boxSdf(c,z,ra);float shape=1.0-smoothstep(0.0,4.0,sd);float rho=rhoAt(c,z);
        float body=pow(sat(1.0-rho),max(uBody.y,0.20));body=pow(body,max(0.28,2.0-uBody.y*0.55));body*=mix(1.0,1.9,sat(uBody.x*0.75));
        float shell=gauss(rho,uBodyBand.x,uBodyBand.y);float shoulder=gauss(rho,uBodyBand.x-0.025,uBodyBand.y*1.8);
        return (pow(sat(body),0.72)+shell*1.55+shoulder*0.85)*shape;
    }
    void main(){
        vec2 coord=vec2(gl_FragCoord.x,uResolution.y-gl_FragCoord.y);vec2 rectOffset=uRect.xy;vec2 z=max(uRect.zw,vec2(1.0));vec2 c=coord-rectOffset;float ra=min(uRadius,min(z.x,z.y)*0.5);float sd=boxSdf(c,z,ra);float mask=1.0-smoothstep(0.0,1.4,sd);if(mask<=0.001)discard;
        vec2 u=c/z;float rho=rhoAt(c,z);float stepPx=2.0;float hL=heightAt(c-vec2(stepPx,0.0),z,ra);float hR=heightAt(c+vec2(stepPx,0.0),z,ra);float hU=heightAt(c-vec2(0.0,stepPx),z,ra);float hD=heightAt(c+vec2(0.0,stepPx),z,ra);float hC=heightAt(c,z,ra);
        vec2 grad=vec2(hR-hL,hD-hU)*0.5;float slope=length(grad);float fade=smoothstep(0.010,0.085,me(u));
        float bodyPresence=sat(pow(sat(1.0-rho),0.42)*(0.48+uBody.x*0.62));float shellPresence=gauss(rho,uBodyBand.x,uBodyBand.y)+gauss(rho,uBodyBand.x-0.028,uBodyBand.y*1.9)*0.55;
        float sideRho=abs(u.x*2.0-1.0);float sideVertical=smoothstep(0.055,0.30,u.y)*smoothstep(0.055,0.30,1.0-u.y);float sideCore=gauss(sideRho,uBodyBand.x,max(uBodyBand.y*0.72,0.014));float sideShoulder=gauss(sideRho,uBodyBand.x-0.060,max(uBodyBand.y*1.65,0.026))*0.52;float sideBodyBand=(sideCore+sideShoulder)*sideVertical*sat(0.42+bodyPresence*0.38+shellPresence*0.40);float sidePresence=max(shellPresence,sideBodyBand*0.96);
        float energy=pow(sat((slope*11.0+hC*0.22)*max(uBodyBand.z,1.0)),max(0.32,max(uBodyBand.z,1.0)*0.55));
        float bodyPx=uBody.z*bodyPresence*(0.35+hC*0.55+energy*0.35);float shellPx=uBodyBand.z*shellPresence*(0.36+energy*0.70+slope*2.4);vec2 flow=grad*(bodyPx+shellPx);flow.x*=mix(1.0,1.72,sideBodyBand);
        vec2 inwardDir=(vec2(0.5)-u);inwardDir.y*=0.72;flow+=inwardDir*(bodyPresence*uBody.z*0.08);float sidePull=(uBodyBand.z*0.185+uBody.z*0.105)*sideBodyBand*(0.72+hC*0.42+bodyPresence*0.25);flow+=vec2(sign(0.5-u.x),0.0)*sidePull;flow.y+=(0.5-u.y)*sideBodyBand*(uBodyBand.z*0.030+uBody.z*0.022)*(0.45+hC*0.35);
        float edgeInset=max(-sd,0.0);float sdL=boxSdf(c-vec2(stepPx,0.0),z,ra);float sdR=boxSdf(c+vec2(stepPx,0.0),z,ra);float sdU=boxSdf(c-vec2(0.0,stepPx),z,ra);float sdD=boxSdf(c+vec2(0.0,stepPx),z,ra);vec2 sdGrad=vec2(sdR-sdL,sdD-sdU)*0.5;float sdSlope=length(sdGrad);vec2 edgeN=sdGrad/max(sdSlope,0.0001);vec2 edgeT=vec2(-edgeN.y,edgeN.x);
        float outerW=max(uOuterRim.x,0.35);float outerComp=max(uOuterRim.y,0.25);float outerReach=max(uOuterRim.z,0.0);float outerGain=uOuterRim.w;float outerBase=sat(1.0-edgeInset/outerW);float outerWindow=smoothstep(outerW+1.2,0.0,edgeInset);float outerCompressed=pow(outerBase,max(0.32,1.0/outerComp))*outerWindow;float outerCrest=gauss(edgeInset,outerW*0.42,max(outerW*0.32,0.55))*outerWindow;float outerShoulder=pow(sat(1.0-edgeInset/(outerW+outerReach*0.34+2.0)),1.55)*0.28;float outerRim=sat(outerCompressed*0.82+outerCrest*0.88+outerShoulder);
        float wallOffset=max(uInnerWall.x,0.5);float wallW=max(uInnerWall.y,1.0);float wallGain=uInnerWall.z;float wallFalloff=max(uInnerWall.w,0.25);float wallReach=uEdgeParams.x;float darkExtract=uEdgeParams.y;float shoulderW=max(uEdgeParams.z,wallOffset+wallW+1.0);float tangentSmear=uEdgeParams.w;float wallStart=smoothstep(wallOffset-0.9,wallOffset+0.45,edgeInset);float wallEnd=1.0-smoothstep(wallOffset+wallW,wallOffset+wallW+2.4,edgeInset);float wallT=sat((edgeInset-wallOffset)/max(wallW,0.001));float innerTransitionWall=wallStart*wallEnd*pow(1.0-wallT,wallFalloff);float softShoulder=pow(sat(1.0-edgeInset/shoulderW),2.0)*(1.0-sat(innerTransitionWall*0.55));float edgeProfile=sat(outerRim+innerTransitionWall+softShoulder*0.16);
        float legacyW=max(uLegacyRim.x,0.0);float legacyPull=uLegacyRim.y;float legacyBright=uLegacyRim.z;float legacyDark=uLegacyRim.w;float legacyBodyPull=uLegacyRim2.x;float legacyRim=legacyW>0.1?smoothstep(legacyW+1.6,0.0,edgeInset):0.0;float legacyCore=legacyW>0.1?pow(sat(1.0-edgeInset/max(legacyW*0.32,1.0)),2.2):0.0;float legacyInner=legacyW>0.1?gauss(edgeInset,legacyW*0.76,max(legacyW*0.38,1.5)):0.0;float legacyBottom=smoothstep(0.50,1.0,u.y);
        float press=sat(uPress.x);vec2 pressCenter=clamp(uPress.yz,vec2(0.0),vec2(1.0));vec2 pressDelta=u-pressCenter;pressDelta.x*=min(z.x/max(z.y,1.0),2.2);float pressField=pow(sat(1.0-length(pressDelta)*0.92),1.45)*press;flow+=(pressCenter-u)*z*(0.012*press+0.028*pressField);
        flow+=edgeN*outerRim*outerGain*(34.0+outerReach*2.6+outerW*4.5)*(0.65+outerCrest*1.20+outerComp*0.28);flow+=edgeN*wallGain*(innerTransitionWall*(1.0+slope*0.22)+softShoulder*0.12);flow+=edgeN*(legacyRim*legacyPull*0.22+legacyCore*legacyPull*0.34);flow+=grad*(legacyBodyPull*0.018*legacyRim);
        float lim=max(me(u)*max(min(z.x,z.y),1.0)*0.92,12.0);flow=softLimit(flow,min(1120.0,lim+bodyPresence*150.0+shellPresence*175.0+sideBodyBand*420.0+edgeProfile*620.0+legacyRim*520.0))*fade;
        vec2 ru=globalUv(c)+flow/max(uRootResolution,vec2(1.0));vec3 clear=lensSrc(ru);vec3 medium=blurSample(ru);float clarity=sat(uOptics.x*(0.18+max(sidePresence,max(edgeProfile,legacyRim))*0.82+slope*0.30)+innerTransitionWall*0.26+outerCompressed*0.34+outerCrest*0.22);vec3 col=mix(medium,clear,clarity);
        vec2 edgePx=edgeN/max(uRootResolution,vec2(1.0));vec3 legacyNear=lensSrc(ru+edgePx*(0.8+legacyW*0.04));vec3 legacyDeep=lensSrc(ru-edgePx*(legacyW*1.85+abs(legacyPull)*0.030));vec3 legacyFar=lensSrc(ru-edgePx*(legacyW*3.20+abs(legacyPull)*0.045));vec3 legacySample=mix(mix(legacyNear,legacyDeep,0.58),legacyFar,sat(legacyCore+0.24));float legacyMix=sat((legacyRim*0.42+legacyCore*0.72+legacyInner*0.34)*(0.55+abs(legacyPull)/360.0));col=mix(col,legacySample,legacyMix);
        vec3 outerShallow=lensSrc(ru+edgePx*(0.20+outerW*0.12));vec3 outerDeep=lensSrc(ru-edgePx*(outerReach*1.25+outerW*2.4+outerGain*8.0));vec3 outerFar=lensSrc(ru-edgePx*(outerReach*2.2+outerW*4.2+outerGain*14.0));vec3 outerSample=mix(mix(outerShallow,outerDeep,sat(0.48+outerComp*0.12+outerCrest*0.25)),outerFar,sat(outerGain*0.24+outerCrest*0.36));float outerMix=sat((outerCompressed*0.85+outerCrest*1.25+outerShoulder*0.35)*outerGain*(1.05+outerComp*0.30));col=mix(col,outerSample,outerMix);
        vec3 wallOuter=lensSrc(ru+edgePx*(wallReach*0.30+outerW*0.40));vec3 wallInner=lensSrc(ru-edgePx*(wallReach+wallW*0.40));vec3 wallSample=mix(wallInner,wallOuter,sat(0.52+innerTransitionWall*0.30));col=mix(col,wallSample,sat(innerTransitionWall*(0.72+wallGain/900.0)));
        float darkMix=(innerTransitionWall*darkExtract+legacyInner*abs(legacyDark)*0.095+legacyBottom*legacyRim*max(legacyDark,0.0)*0.035)*(0.42+(1.0-min(luma(wallOuter),luma(wallInner)))*0.58);col=mix(col,mix(wallSample,min(wallOuter,wallInner),0.78),sat(darkMix)*0.58);col*=1.0-sat(darkMix)*0.13;
        float smear=sat(uOptics.y*max(sidePresence,max(edgeProfile,legacyRim))*(0.18+slope*3.2));float edgeSmear=sat((tangentSmear/160.0)*(innerTransitionWall*0.88+outerRim*0.34+softShoulder*0.14+legacyRim*0.52));if(max(smear,edgeSmear)>0.001){vec2 ep=edgeT*(4.0+tangentSmear*0.16+innerTransitionWall*9.0+legacyRim*8.0)/max(uRootResolution,vec2(1.0));vec3 drag=lensSrc(ru+ep)*0.42+lensSrc(ru-ep)*0.42+medium*0.16;col=mix(col,drag,sat(smear*0.34+edgeSmear*0.36));}
        float rim=sidePresence*sat(0.35+slope*3.5+sideBodyBand*0.45);float legacyLift=(legacyCore*0.46+legacyRim*0.12)*(0.22+abs(luma(legacyNear)-luma(legacyFar))*1.10)*max(legacyBright,0.0);float fres=pow(1.0-sat(me(u)*3.0),1.35);col+=vec3(0.03,0.07,0.10)*rim*0.18;col+=vec3(0.020,0.030,0.040)*(legacyLift+outerCrest*outerGain*0.08);col*=(1.0+rim*0.05+fres*0.028+legacyLift*0.18);col*=uMaterial.z;col=clamp(col,0.0,1.0);
        float alpha=0.90*mask*sat(uMaterial.x/20.0)*uMaterial.y*uIntensity;
        gl_FragColor=vec4(col,alpha);
    }
"""
