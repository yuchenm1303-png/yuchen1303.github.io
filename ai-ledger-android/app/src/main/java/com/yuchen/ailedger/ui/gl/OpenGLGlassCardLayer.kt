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
import com.yuchen.ailedger.ui.BackdropCoordinateSource
import com.yuchen.ailedger.ui.BackdropFrameTicker
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.LocalBackdropFrameTicker
import com.yuchen.ailedger.ui.LocalBackdropOrigin
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassBackdrop
import com.yuchen.ailedger.ui.LocalGlassFoldoutClipRegistry
import com.yuchen.ailedger.ui.PerformanceRuntimeMetrics
import com.yuchen.ailedger.ui.applyGlassFoldoutClip
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val GLASS_SPEC_EPSILON_PX = 0.5f
private const val GLASS_ORIGIN_EPSILON_PX = 0.35f
private const val GLASS_PRESS_EPSILON = 0.003f
private const val GLASS_PRESS_CENTER_EPSILON = 0.002f
private const val GLASS_STABLE_SURFACE_FALLBACK_ANCHOR_Y = 0.44f

/**
 * legacy Shell 会在页面切换期间短暂并存多个 EGL Context。所有 Context 共用默认 Display，
 * 因此由这里统一配对 initialize/terminate，避免旧页面释放时终止新页面仍在使用的 Display。
 */
private object LegacyEglDisplayRuntime {
    private val lock = Any()
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var leaseCount = 0

    fun acquire(): EGLDisplay = synchronized(lock) {
        if (display == EGL14.EGL_NO_DISPLAY) {
            val candidate = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(candidate != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(candidate, version, 0, version, 1)) {
                "Unable to initialize EGL"
            }
            display = candidate
        }
        leaseCount += 1
        display
    }

    fun release(leasedDisplay: EGLDisplay) {
        if (leasedDisplay == EGL14.EGL_NO_DISPLAY) return
        synchronized(lock) {
            if (leasedDisplay != display || leaseCount <= 0) return
            leaseCount -= 1
            if (leaseCount == 0) {
                EGL14.eglTerminate(display)
                display = EGL14.EGL_NO_DISPLAY
            }
        }
    }
}

enum class OpenGLGlassSurfaceAnchor(val fraction: Float) {
    Top(0f),
    Center(0.44f),
    Bottom(1f),
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
 * 稳定 Surface、anchor 与 viewportTopInset 链保持原样。几何、采样原点和按压状态
 * 通过 VSync 快照直达 Host，不再依赖 Compose placementVersion 逐帧重组。
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
    viewportTopInsetPx: Float = 0f,
    dynamicState: OpenGLGlassDynamicState? = null,
) {
    val backdrop = LocalBlurredBackdrop.current ?: return
    val border = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
    val backdropOrigin = LocalBackdropOrigin.current
    val backdropTicker = LocalBackdropFrameTicker.current
    val density = LocalDensity.current
    val surfaceAnchor = LocalOpenGLGlassSurfaceAnchor.current.fraction
    val localViewportTopInsetPx = with(density) {
        LocalOpenGLGlassViewportTopInset.current.toPx()
    }
    val effectiveViewportTopInsetPx = max(viewportTopInsetPx, localViewportTopInsetPx)
    val foldoutClipRegistry = LocalGlassFoldoutClipRegistry.current
    foldoutClipRegistry?.version

    val blurBitmap = remember(backdrop.image) { backdrop.image.asAndroidBitmap() }
    val lensBitmap = remember(backdrop.lensImage) { backdrop.lensImage.asAndroidBitmap() }
    val radiusPx = with(density) { radius.dp.toPx() }.roundToInt().toFloat()
    val staticPress = pressProgress.coerceIn(0f, 1f)
    val staticPressX = pressCenter.x.coerceIn(0f, 1f)
    val staticPressY = pressCenter.y.coerceIn(0f, 1f)

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
                view.applyGlassFoldoutClip(
                    registry = foldoutClipRegistry,
                    coordinates = coordinateSource?.coordinates,
                )
                view.bindDynamicSources(
                    coordinateSource = coordinateSource,
                    backdropOrigin = backdropOrigin,
                    frameTicker = backdropTicker,
                    dynamicState = dynamicState,
                )
                view.setStableSurfaceAnchor(surfaceAnchor)
                val surfaceDirty = view.setStableSurfaceSize(
                    width = widthPx.roundToInt(),
                    height = heightPx.roundToInt(),
                    rootWidth = rootWidthPx.roundToInt(),
                    rootHeight = rootHeightPx.roundToInt(),
                )
                val frameDirty = view.setFrameSpec(
                    width = widthPx,
                    height = viewportHeightPx,
                    rectOffsetY = safeViewportTopInsetPx,
                    radius = radiusPx,
                    rootWidth = rootWidthPx,
                    rootHeight = rootHeightPx,
                    staticPressProgress = staticPress,
                    staticPressCenterX = staticPressX,
                    staticPressCenterY = staticPressY,
                )
                val textureDirty = view.setBackdropTextures(blurBitmap, lensBitmap)
                val styleDirty = view.setGlassStyle(border)
                if (surfaceDirty || frameDirty || textureDirty || styleDirty) {
                    view.requestRenderOnNextAnimationFrame()
                }
            },
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
    private var renderAfterLayout = false

    private var latestGlassWidth = 1f
    private var latestGlassHeight = 1f
    private var latestRectOffsetY = 0f
    private var latestRadius = 24f
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

    fun setStableSurfaceAnchor(anchorY: Float): Boolean {
        stableSurfaceAnchorY = anchorY.coerceIn(0f, 1f)
        return false
    }

    fun setStableSurfaceSize(
        width: Int,
        height: Int,
        rootWidth: Int,
        rootHeight: Int,
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

    fun setFrameSpec(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float,
        rootWidth: Float,
        rootHeight: Float,
        staticPressProgress: Float,
        staticPressCenterX: Float,
        staticPressCenterY: Float,
    ): Boolean {
        latestGlassWidth = width.coerceAtLeast(1f)
        latestGlassHeight = height.coerceAtLeast(1f)
        latestRectOffsetY = rectOffsetY
        latestRadius = radius
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        installDynamicSubscriptions()
        requestRenderOnNextAnimationFrame()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        @Suppress("UNUSED_VARIABLE")
        val preservedAnchor = stableSurfaceAnchorY
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

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap): Boolean =
        textureView.setBackdropTextures(blurBitmap, lensBitmap)

    fun setGlassStyle(style: GlassBorderStyle): Boolean = textureView.setGlassStyle(style)

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
        return textureView.setFrameState(
            width = latestGlassWidth,
            height = latestGlassHeight,
            rectOffsetY = latestRectOffsetY,
            radius = latestRadius,
            originX = origin.x,
            originY = origin.y + latestRectOffsetY,
            rootWidth = latestRootWidth,
            rootHeight = latestRootHeight,
            pressProgress = dynamic?.openGlPress ?: latestStaticPressProgress,
            pressCenterX = dynamic?.pressCenter?.x ?: latestStaticPressCenterX,
            pressCenterY = dynamic?.pressCenter?.y ?: latestStaticPressCenterY,
        )
    }
}

private class OpenGLGlassCardTextureView(
    context: Context,
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

    fun setFrameState(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float,
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
        val nextRootWidth = rootWidth.coerceAtLeast(1f)
        val nextRootHeight = rootHeight.coerceAtLeast(1f)
        val nextPress = pressProgress.coerceIn(0f, 1f)
        val nextPressX = pressCenterX.coerceIn(0f, 1f)
        val nextPressY = pressCenterY.coerceIn(0f, 1f)

        val geometryDirty =
            abs(nextWidth - latestWidth) > GLASS_SPEC_EPSILON_PX ||
                abs(nextHeight - latestHeight) > GLASS_SPEC_EPSILON_PX ||
                abs(rectOffsetY - latestRectOffsetY) > GLASS_SPEC_EPSILON_PX ||
                abs(radius - latestRadius) > GLASS_SPEC_EPSILON_PX
        val samplingDirty =
            abs(originX - latestOriginX) > GLASS_ORIGIN_EPSILON_PX ||
                abs(originY - latestOriginY) > GLASS_ORIGIN_EPSILON_PX ||
                abs(nextRootWidth - latestRootWidth) > GLASS_SPEC_EPSILON_PX ||
                abs(nextRootHeight - latestRootHeight) > GLASS_SPEC_EPSILON_PX
        val pressDirty =
            abs(nextPress - latestPressProgress) > GLASS_PRESS_EPSILON ||
                abs(nextPressX - latestPressCenterX) > GLASS_PRESS_CENTER_EPSILON ||
                abs(nextPressY - latestPressCenterY) > GLASS_PRESS_CENTER_EPSILON
        val dirty = geometryDirty || samplingDirty || pressDirty

        latestWidth = nextWidth
        latestHeight = nextHeight
        latestRectOffsetY = rectOffsetY
        latestRadius = radius
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

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap): Boolean {
        val blurChanged = blurBitmap !== latestBlurBitmap
        val lensChanged = lensBitmap !== latestLensBitmap
        val dirty = blurChanged || lensChanged
        latestBlurBitmap = blurBitmap
        latestLensBitmap = lensBitmap
        if (dirty) {
            if (blurChanged) PerformanceRuntimeMetrics.recordOpenGlTextureUpload(blurBitmap.width, blurBitmap.height)
            if (lensChanged && lensBitmap !== blurBitmap) {
                PerformanceRuntimeMetrics.recordOpenGlTextureUpload(lensBitmap.width, lensBitmap.height)
            }
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
        height: Int,
    ) {
        renderThread?.shutdown()
        renderThread = CardGlassEglThread(
            surface = Surface(surfaceTexture),
            width = width,
            height = height,
        ).also { thread ->
            thread.setFrameState(
                width = latestWidth,
                height = latestHeight,
                rectOffsetY = latestRectOffsetY,
                radius = latestRadius,
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
            thread.setGlassStyle(latestStyle)
            val blur = latestBlurBitmap
            val lens = latestLensBitmap
            if (blur != null && lens != null) thread.setBackdropTextures(blur, lens)
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

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
}

private class CardGlassEglThread(
    private val surface: Surface,
    width: Int,
    height: Int,
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
    private var displayLeaseActive = false
    private var metricsContextActive = false

    fun setFrameState(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float,
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

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap) =
        renderer.setBackdropTextures(blurBitmap, lensBitmap)

    fun setGlassStyle(style: GlassBorderStyle) = renderer.setGlassStyle(style)

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
            // 透明 TextureView 每帧都完整清屏重绘，因此不申请 preserved back buffer。
            // 这与纯色探针使用同一类标准 EGL window 配置，也避开厂商驱动的 preserved 合成分支。
            renderer.setPartialClearSupported(false)
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
                if (EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                    PerformanceRuntimeMetrics.recordOpenGlFrame()
                }
            }
        } finally {
            runCatching { renderer.onRelease() }
            releaseEgl()
            surface.release()
        }
    }

    private fun initEgl() {
        eglDisplay = LegacyEglDisplayRuntime.acquire()
        displayLeaseActive = true

        val config = chooseConfig(EGL14.EGL_WINDOW_BIT)
            ?: error("No EGL config found")

        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION,
                2,
                EGL14.EGL_NONE,
            ),
            0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }

        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            config,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0,
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL window surface" }
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            "Unable to make EGL context current"
        }
        metricsContextActive = true
        PerformanceRuntimeMetrics.recordOpenGlContextCreated()
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
        val leasedDisplay = eglDisplay
        if (leasedDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                leasedDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(leasedDisplay, eglSurface)
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(leasedDisplay, eglContext)
            }
        }
        if (displayLeaseActive) {
            displayLeaseActive = false
            LegacyEglDisplayRuntime.release(leasedDisplay)
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
    val darkScale: Float,
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
        darkScale = openGlDarkScale.coerceIn(-10f, 10f),
    )
