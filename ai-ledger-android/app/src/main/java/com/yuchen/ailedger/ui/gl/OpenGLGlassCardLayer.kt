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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val GLASS_SPEC_EPSILON_PX = 0.5f
private const val GLASS_ORIGIN_EPSILON_PX = 0.35f
private const val GLASS_PRESS_EPSILON = 0.003f
private const val GLASS_PRESS_CENTER_EPSILON = 0.002f
private const val GLASS_STABLE_SURFACE_FALLBACK_ANCHOR_Y = 0.44f

private const val EGL_SWAP_BEHAVIOR_VALUE = 0x3093
private const val EGL_BUFFER_PRESERVED_VALUE = 0x3094
private const val EGL_SWAP_BEHAVIOR_PRESERVED_BIT_VALUE = 0x0400

enum class OpenGLGlassSurfaceAnchor(val fraction: Float) {
    Top(0f),
    Center(0.44f),
    Bottom(1f)
}

val LocalOpenGLGlassSurfaceAnchor = compositionLocalOf {
    OpenGLGlassSurfaceAnchor.Center
}

val LocalOpenGLGlassViewportTopInset = compositionLocalOf {
    0.dp
}

/**
 * 旧版 OpenGL Shell 宿主。
 *
 * 稳定 Surface、anchor 与 viewportTopInset 链保持原样；底层 Renderer 已改为
 * dirty-mask、VBO、纹理复用和局部清屏实现。旧 Shader 历史上未消费
 * glassIntensity，因此保留公开参数但不为它触发无意义重绘。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun OpenGLGlassCardLayer(
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
    val localViewportTopInsetPx = with(density) {
        LocalOpenGLGlassViewportTopInset.current.toPx()
    }
    val effectiveViewportTopInsetPx = max(viewportTopInsetPx, localViewportTopInsetPx)

    val blurBitmap = remember(backdrop.image) { backdrop.image.asAndroidBitmap() }
    val lensBitmap = remember(backdrop.lensImage) { backdrop.lensImage.asAndroidBitmap() }
    val radiusPx = with(density) { radius.dp.toPx() }.roundToInt().toFloat()
    val cardOrigin = coordinateSource?.offsetRelativeTo(backdropOrigin) ?: Offset.Zero
    val press = pressProgress.coerceIn(0f, 1f)
    val pressX = pressCenter.x.coerceIn(0f, 1f)
    val pressY = pressCenter.y.coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
            .roundToInt()
            .coerceAtLeast(1)
            .toFloat()
        val heightPx = with(density) { maxHeight.toPx() }
            .roundToInt()
            .coerceAtLeast(1)
            .toFloat()
        val safeViewportTopInsetPx = effectiveViewportTopInsetPx
            .coerceIn(0f, (heightPx - 1f).coerceAtLeast(0f))
        val viewportHeightPx = (heightPx - safeViewportTopInsetPx).coerceAtLeast(1f)
        val rootWidthPx = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
        val rootHeightPx = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)

        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context -> OpenGLGlassCardHostView(context) },
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
                    radius = radiusPx
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
                if (
                    surfaceDirty || specDirty || samplingDirty ||
                    pressDirty || textureDirty || styleDirty
                ) {
                    view.requestRenderOnNextAnimationFrame()
                }
            }
        )
    }
}

private class OpenGLGlassCardHostView(context: Context) : FrameLayout(context) {
    private val textureView = OpenGLGlassCardTextureView(context)

    private var stableSurfaceWidth = 1
    private var stableSurfaceHeight = 1
    private var stableSurfaceAnchorY = GLASS_STABLE_SURFACE_FALLBACK_ANCHOR_Y
    private var lastRootWidth = 1
    private var lastRootHeight = 1
    private var geometryAwaitingLayout = false
    private var renderPosted = false

    private var latestGlassWidth = 1f
    private var latestGlassHeight = 1f
    private var latestRectOffsetY = 0f
    private var latestRadius = 24f
    private var latestOriginX = 0f
    private var latestOriginY = 0f
    private var latestRootWidth = 1f
    private var latestRootHeight = 1f

    private val renderRunnable = Runnable {
        renderPosted = false
        if (!geometryAwaitingLayout && isAttachedToWindow) {
            textureView.requestRender()
        }
    }

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

    fun setStableSurfaceSize(
        width: Int,
        height: Int,
        rootWidth: Int,
        rootHeight: Int
    ): Boolean {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val safeRootWidth = rootWidth.coerceAtLeast(1)
        val safeRootHeight = rootHeight.coerceAtLeast(1)
        val rootSizeChanged =
            abs(safeRootWidth - lastRootWidth) > 2 ||
                abs(safeRootHeight - lastRootHeight) > 2

        lastRootWidth = safeRootWidth
        lastRootHeight = safeRootHeight

        val targetWidth = if (rootSizeChanged) {
            safeWidth
        } else {
            max(stableSurfaceWidth, safeWidth)
        }
        val targetHeight = if (rootSizeChanged) {
            safeHeight
        } else {
            max(stableSurfaceHeight, safeHeight)
        }
        val sizeChanged =
            targetWidth != stableSurfaceWidth ||
                targetHeight != stableSurfaceHeight

        stableSurfaceWidth = targetWidth
        stableSurfaceHeight = targetHeight

        val current = textureView.layoutParams as? LayoutParams
        val layoutDirty =
            current == null ||
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

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        // anchor 链保持存在；旧实现同样不通过 translation 改变稳定 Surface。
        @Suppress("UNUSED_VARIABLE")
        val preservedAnchor = stableSurfaceAnchorY
        textureView.translationY = 0f
        textureView.layout(0, 0, stableSurfaceWidth, stableSurfaceHeight)
        geometryAwaitingLayout = false

        if (renderPosted) {
            removeCallbacks(renderRunnable)
            renderPosted = false
        }
        val glassDirty = syncGlassSpecToTexture()
        val samplingDirty = syncSamplingSpecToTexture()
        if (changed || glassDirty || samplingDirty) {
            textureView.requestRender()
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(renderRunnable)
        renderPosted = false
        super.onDetachedFromWindow()
    }

    fun setGlassSpec(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float
    ): Boolean {
        latestGlassWidth = width.coerceAtLeast(1f)
        latestGlassHeight = height.coerceAtLeast(1f)
        latestRectOffsetY = rectOffsetY
        latestRadius = radius
        return if (geometryAwaitingLayout) false else syncGlassSpecToTexture()
    }

    fun setSamplingSpec(
        originX: Float,
        originY: Float,
        rootWidth: Float,
        rootHeight: Float
    ): Boolean {
        latestOriginX = originX
        latestOriginY = originY
        latestRootWidth = rootWidth.coerceAtLeast(1f)
        latestRootHeight = rootHeight.coerceAtLeast(1f)
        return if (geometryAwaitingLayout) false else syncSamplingSpecToTexture()
    }

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float): Boolean =
        textureView.setPressSpec(progress, centerX, centerY)

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap): Boolean =
        textureView.setBackdropTextures(blurBitmap, lensBitmap)

    fun setGlassStyle(style: GlassBorderStyle): Boolean =
        textureView.setGlassStyle(style)

    fun requestRenderOnNextAnimationFrame() {
        if (geometryAwaitingLayout || renderPosted) return
        renderPosted = true
        postOnAnimation(renderRunnable)
    }

    private fun syncGlassSpecToTexture(): Boolean =
        textureView.setGlassSpec(
            latestGlassWidth,
            latestGlassHeight,
            latestRectOffsetY,
            latestRadius
        )

    private fun syncSamplingSpecToTexture(): Boolean =
        textureView.setSamplingSpec(
            latestOriginX,
            latestOriginY,
            latestRootWidth,
            latestRootHeight
        )
}

private class OpenGLGlassCardTextureView(
    context: Context
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: CardGlassEglThread? = null
    private var latestBlurBitmap: Bitmap? = null
    private var latestLensBitmap: Bitmap? = null
    private var latestWidth = 1f
    private var latestHeight = 1f
    private var latestRectOffsetY = 0f
    private var latestRadius = 24f
    private var latestOriginX = 0f
    private var latestOriginY = 0f
    private var latestRootWidth = 1f
    private var latestRootHeight = 1f
    private var latestPressProgress = 0f
    private var latestPressCenterX = 0.5f
    private var latestPressCenterY = 0.5f
    private var latestStyle = GlassBorderStyle()
    private var latestStyleSignature = latestStyle.openGlSignature()

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
        radius: Float
    ): Boolean {
        val nextWidth = width.coerceAtLeast(1f)
        val nextHeight = height.coerceAtLeast(1f)
        val dirty =
            abs(nextWidth - latestWidth) > GLASS_SPEC_EPSILON_PX ||
                abs(nextHeight - latestHeight) > GLASS_SPEC_EPSILON_PX ||
                abs(rectOffsetY - latestRectOffsetY) > GLASS_SPEC_EPSILON_PX ||
                abs(radius - latestRadius) > GLASS_SPEC_EPSILON_PX
        latestWidth = nextWidth
        latestHeight = nextHeight
        latestRectOffsetY = rectOffsetY
        latestRadius = radius
        if (dirty) {
            renderThread?.setGlassSpec(
                nextWidth,
                nextHeight,
                rectOffsetY,
                radius
            )
        }
        return dirty
    }

    fun setSamplingSpec(
        originX: Float,
        originY: Float,
        rootWidth: Float,
        rootHeight: Float
    ): Boolean {
        val nextRootWidth = rootWidth.coerceAtLeast(1f)
        val nextRootHeight = rootHeight.coerceAtLeast(1f)
        val dirty =
            abs(originX - latestOriginX) > GLASS_ORIGIN_EPSILON_PX ||
                abs(originY - latestOriginY) > GLASS_ORIGIN_EPSILON_PX ||
                abs(nextRootWidth - latestRootWidth) > GLASS_SPEC_EPSILON_PX ||
                abs(nextRootHeight - latestRootHeight) > GLASS_SPEC_EPSILON_PX
        latestOriginX = originX
        latestOriginY = originY
        latestRootWidth = nextRootWidth
        latestRootHeight = nextRootHeight
        if (dirty) {
            renderThread?.setSamplingSpec(
                originX,
                originY,
                nextRootWidth,
                nextRootHeight
            )
        }
        return dirty
    }

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float): Boolean {
        val safeProgress = progress.coerceIn(0f, 1f)
        val safeCenterX = centerX.coerceIn(0f, 1f)
        val safeCenterY = centerY.coerceIn(0f, 1f)
        val dirty =
            abs(safeProgress - latestPressProgress) > GLASS_PRESS_EPSILON ||
                abs(safeCenterX - latestPressCenterX) > GLASS_PRESS_CENTER_EPSILON ||
                abs(safeCenterY - latestPressCenterY) > GLASS_PRESS_CENTER_EPSILON
        latestPressProgress = safeProgress
        latestPressCenterX = safeCenterX
        latestPressCenterY = safeCenterY
        if (dirty) {
            renderThread?.setPressSpec(
                safeProgress,
                safeCenterX,
                safeCenterY
            )
        }
        return dirty
    }

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap): Boolean {
        val dirty =
            blurBitmap !== latestBlurBitmap ||
                lensBitmap !== latestLensBitmap
        latestBlurBitmap = blurBitmap
        latestLensBitmap = lensBitmap
        if (dirty) {
            renderThread?.setBackdropTextures(blurBitmap, lensBitmap)
        }
        return dirty
    }

    fun setGlassStyle(style: GlassBorderStyle): Boolean {
        val nextSignature = style.openGlSignature()
        val dirty = nextSignature != latestStyleSignature
        latestStyle = style
        latestStyleSignature = nextSignature
        if (dirty) renderThread?.setGlassStyle(style)
        return dirty
    }

    fun requestRender() {
        renderThread?.requestRender()
    }

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        renderThread?.shutdown()
        renderThread = CardGlassEglThread(
            surface = Surface(surfaceTexture),
            width = width,
            height = height
        ).also { thread ->
            thread.setGlassSpec(
                latestWidth,
                latestHeight,
                latestRectOffsetY,
                latestRadius
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
            thread.setGlassStyle(latestStyle)
            val blur = latestBlurBitmap
            val lens = latestLensBitmap
            if (blur != null && lens != null) {
                thread.setBackdropTextures(blur, lens)
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

private class CardGlassEglThread(
    private val surface: Surface,
    width: Int,
    height: Int
) : Thread("OpenGLGlassCardTextureThread") {
    private val renderer = LegacyOpenGLGlassRenderer()
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

    fun setGlassSpec(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float
    ) = renderer.setGlassSpec(width, height, rectOffsetY, radius)

    fun setSamplingSpec(
        originX: Float,
        originY: Float,
        rootWidth: Float,
        rootHeight: Float
    ) = renderer.setSamplingSpec(originX, originY, rootWidth, rootHeight)

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
            renderer.setPartialClearSupported(preservedSwap)
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
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            "Unable to initialize EGL"
        }

        val preservedConfig = chooseConfig(
            EGL14.EGL_WINDOW_BIT or EGL_SWAP_BEHAVIOR_PRESERVED_BIT_VALUE
        )
        val config = preservedConfig ?: chooseConfig(EGL14.EGL_WINDOW_BIT)
            ?: error("No EGL config found")

        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION,
                2,
                EGL14.EGL_NONE
            ),
            0
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) {
            "Unable to create EGL context"
        }

        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            config,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE) {
            "Unable to create EGL window surface"
        }
        check(
            EGL14.eglMakeCurrent(
                eglDisplay,
                eglSurface,
                eglSurface,
                eglContext
            )
        ) { "Unable to make EGL context current" }

        preservedSwap = preservedConfig != null && EGL14.eglSurfaceAttrib(
            eglDisplay,
            eglSurface,
            EGL_SWAP_BEHAVIOR_VALUE,
            EGL_BUFFER_PRESERVED_VALUE
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
            EGL14.EGL_NONE
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
            0
        )
        return if (success && count[0] > 0) configs[0] else null
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

private data class OpenGLGlassStyleSignature(
    val visibility: Float,
    val maxAlpha: Float,
    val edgeBrightness: Float,
    val pullScale: Float,
    val edgePullDp: Float,
    val compressionScale: Float,
    val cornerScale: Float,
    val sampleRadiusScale: Float,
    val ringWidthDp: Float,
    val debugLineAlpha: Float,
    val darkScale: Float
)

private fun GlassBorderStyle.openGlSignature(): OpenGLGlassStyleSignature =
    OpenGLGlassStyleSignature(
        visibility = openGlVisibility.coerceIn(0f, 20f),
        maxAlpha = openGlMaxAlpha.coerceIn(0f, 1f),
        edgeBrightness = edgeBrightness.coerceIn(-5f, 5f),
        pullScale = openGlPullScale.coerceIn(-300f, 300f),
        edgePullDp = edgePullDp.coerceIn(-600f, 600f),
        compressionScale = openGlCompressionScale.coerceIn(-10f, 10f),
        cornerScale = openGlCornerScale.coerceIn(0f, 200f),
        sampleRadiusScale = openGlSampleRadiusScale.coerceIn(0f, 200f),
        ringWidthDp = ringWidthDp.coerceIn(0f, 300f),
        debugLineAlpha = openGlDebugLineAlpha.coerceIn(0f, 1f),
        darkScale = openGlDarkScale.coerceIn(-10f, 10f)
    )
