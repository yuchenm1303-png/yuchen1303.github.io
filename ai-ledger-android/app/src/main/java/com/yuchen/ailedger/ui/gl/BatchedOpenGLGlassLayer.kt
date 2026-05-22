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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.BackdropCoordinateSource
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.GlassRole
import com.yuchen.ailedger.ui.LocalBackdropFrameTicker
import com.yuchen.ailedger.ui.LocalBackdropOrigin
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassBackdrop
import com.yuchen.ailedger.ui.LocalOpenGlGlassFrameCoordinator
import com.yuchen.ailedger.ui.OpenGlGlassFrameCoordinator
import com.yuchen.ailedger.ui.OpenGlGlassFrameRect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val SIGNATURE_QUANTIZE = 4f
private const val DEFAULT_SCROLL_PREDICTION_FACTOR = 0.72f
private const val MIN_SCROLL_PREDICTION_PX = 0.25f
private const val MAX_SCROLL_PREDICTION_PX = 36f
private const val FOLLOW_UP_RENDER_WINDOW_NANOS = 96_000_000L

class BatchedOpenGlGlassRegistry {
    private val items = linkedMapOf<Any, BatchedOpenGlGlassItem>()
    private var cachedSnapshot: List<BatchedOpenGlGlassItem> = emptyList()
    private var snapshotDirty = true

    fun upsert(item: BatchedOpenGlGlassItem) {
        if (items[item.key] == item) return
        items[item.key] = item
        snapshotDirty = true
    }

    fun remove(key: Any) {
        if (items.remove(key) != null) snapshotDirty = true
    }

    fun snapshot(): List<BatchedOpenGlGlassItem> {
        if (snapshotDirty) {
            cachedSnapshot = items.values.toList()
            snapshotDirty = false
        }
        return cachedSnapshot
    }
}

data class BatchedOpenGlGlassItem(
    val key: Any,
    val coordinates: GlassCoordinateSource,
    val radius: Int,
    val role: GlassRole,
    val glassIntensity: Float,
    val zIndex: Float = role.defaultOpenGlZIndex()
)

val LocalBatchedOpenGlGlassRegistry = compositionLocalOf<BatchedOpenGlGlassRegistry?> { null }

private fun GlassRole.defaultOpenGlZIndex(): Float = when (this) {
    GlassRole.Shell -> 30f
    GlassRole.Flex -> 20f
    GlassRole.Card -> 18f
    GlassRole.Floating -> 40f
    GlassRole.Nav -> 50f
    GlassRole.Chip -> 10f
}

private data class DrawItem(
    val key: Any,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val originX: Float,
    val originY: Float,
    val radiusPx: Float,
    val intensity: Float,
    val zIndex: Float
)

private class BatchedOpenGlViewHolder {
    @Volatile var view: BatchedOpenGlGlassTextureView? = null
}

@Composable
fun RegisterBatchedOpenGlGlassItem(
    key: Any,
    coordinates: GlassCoordinateSource,
    radius: Int,
    role: GlassRole,
    glassIntensity: Float,
    enabled: Boolean
) {
    val registry = LocalBatchedOpenGlGlassRegistry.current
    val frameCoordinator = LocalOpenGlGlassFrameCoordinator.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameNanos = LocalBackdropFrameTicker.current?.frameNanos ?: 0L

    if (enabled && registry != null) {
        SideEffect {
            registry.upsert(
                BatchedOpenGlGlassItem(
                    key = key,
                    coordinates = coordinates,
                    radius = radius,
                    role = role,
                    glassIntensity = glassIntensity
                )
            )
            frameNanos.hashCode()
            val current = coordinates.coordinates
            if (current != null && current.isAttached) {
                val topLeft = current.localToRoot(Offset.Zero)
                val size = current.size
                val origin = topLeft - (backdropOrigin?.rootOffset() ?: Offset.Zero)
                frameCoordinator?.upsert(
                    OpenGlGlassFrameRect(
                        key = key,
                        left = topLeft.x,
                        top = topLeft.y,
                        width = size.width.toFloat(),
                        height = size.height.toFloat(),
                        originX = origin.x,
                        originY = origin.y
                    )
                )
            }
        }
    }
    DisposableEffect(registry, frameCoordinator, key, enabled) {
        onDispose {
            registry?.remove(key)
            frameCoordinator?.remove(key)
        }
    }
}

@Composable
fun BatchedOpenGlGlassLayer(
    modifier: Modifier = Modifier,
    scrollPrediction: Float = DEFAULT_SCROLL_PREDICTION_FACTOR
) {
    val registry = LocalBatchedOpenGlGlassRegistry.current
    val frameCoordinator = LocalOpenGlGlassFrameCoordinator.current
    val backdrop = LocalBlurredBackdrop.current ?: return
    val border = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
    val origin = LocalBackdropOrigin.current
    val ticker = LocalBackdropFrameTicker.current
    val density = LocalDensity.current
    val frameNanos = ticker?.frameNanos ?: 0L
    val frameRectVersion = frameCoordinator?.version ?: 0L
    val blurBitmap = backdrop.image.asAndroidBitmap()
    val lensBitmap = backdrop.lensImage.asAndroidBitmap()
    val safePrediction = scrollPrediction.coerceIn(0f, 1.4f)
    val viewHolder = remember { BatchedOpenGlViewHolder() }

    BoxWithConstraints(modifier = modifier) {
        val viewportW = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
        val viewportH = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)
        val items = buildDrawItems(
            registry = registry,
            frameCoordinator = frameCoordinator,
            density = density,
            origin = origin,
            viewportW = viewportW,
            viewportH = viewportH
        )

        DisposableEffect(viewHolder) {
            onDispose { viewHolder.view = null }
        }

        DisposableEffect(
            registry,
            frameCoordinator,
            density,
            origin,
            viewportW,
            viewportH,
            backdrop.fullWidthPx,
            backdrop.fullHeightPx,
            safePrediction
        ) {
            if (frameCoordinator == null) {
                onDispose { }
            } else {
                val listener = {
                    val latestItems = buildDrawItems(
                        registry = registry,
                        frameCoordinator = frameCoordinator,
                        density = density,
                        origin = origin,
                        viewportW = viewportW,
                        viewportH = viewportH
                    )
                    viewHolder.view?.let { view ->
                        view.setItems(
                            latestItems,
                            backdrop.fullWidthPx.toFloat(),
                            backdrop.fullHeightPx.toFloat(),
                            safePrediction
                        )
                        view.requestRender()
                    }
                }
                frameCoordinator.addListener(listener)
                onDispose { frameCoordinator.removeListener(listener) }
            }
        }

        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context ->
                BatchedOpenGlGlassTextureView(context).also { viewHolder.view = it }
            },
            update = { view ->
                viewHolder.view = view
                view.noteComposeFrame(frameNanos + frameRectVersion)
                val dirtyA = view.setViewportHint(viewportW, viewportH)
                val dirtyB = view.setBackdropTextures(blurBitmap, lensBitmap)
                val dirtyC = view.setGlassStyle(border)
                val dirtyD = view.setItems(items, backdrop.fullWidthPx.toFloat(), backdrop.fullHeightPx.toFloat(), safePrediction)
                if (dirtyA || dirtyB || dirtyC || dirtyD) view.requestRender()
            }
        )
    }
}

private fun buildDrawItems(
    registry: BatchedOpenGlGlassRegistry?,
    frameCoordinator: OpenGlGlassFrameCoordinator?,
    density: Density,
    origin: BackdropCoordinateSource?,
    viewportW: Int,
    viewportH: Int
): List<DrawItem> {
    val frameRectMap = frameCoordinator?.snapshot().orEmpty().associateBy { it.key }
    return registry?.snapshot().orEmpty().mapNotNull { item ->
        val frameRect = frameRectMap[item.key]
        val width: Float
        val height: Float
        val left: Float
        val top: Float
        val originX: Float
        val originY: Float

        if (frameRect != null) {
            width = frameRect.width
            height = frameRect.height
            left = frameRect.left
            top = frameRect.top
            originX = frameRect.originX
            originY = frameRect.originY
        } else {
            if (!item.coordinates.isAttached()) return@mapNotNull null
            val size = item.coordinates.itemSize()
            if (size.width <= 0 || size.height <= 0) return@mapNotNull null
            val topLeft = item.coordinates.rootOffset()
            val sample = item.coordinates.offsetRelativeTo(origin)
            width = size.width.toFloat()
            height = size.height.toFloat()
            left = topLeft.x
            top = topLeft.y
            originX = sample.x
            originY = sample.y
        }

        if (width <= 0f || height <= 0f) return@mapNotNull null
        DrawItem(
            key = item.key,
            left = left,
            top = top,
            width = width,
            height = height,
            originX = originX,
            originY = originY,
            radiusPx = with(density) { item.radius.dp.toPx() },
            intensity = item.glassIntensity.coerceIn(0.35f, 1.30f),
            zIndex = item.zIndex
        )
    }.filter { item ->
        item.left < viewportW && item.top < viewportH && item.left + item.width > 0f && item.top + item.height > 0f
    }.sortedBy { it.zIndex }
}

private class BatchedOpenGlGlassTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var thread: BatchedGlassEglThread? = null
    private var blur: Bitmap? = null
    private var lens: Bitmap? = null
    private var style = GlassBorderStyle()
    private var rawItems: List<DrawItem> = emptyList()
    private var items: List<DrawItem> = emptyList()
    private var rootW = 1f
    private var rootH = 1f
    private var viewportHintW = 1
    private var viewportHintH = 1
    private var lastItemSignature = 0
    private var lastComposeFrameNanos = 0L
    private var lastItemChangeAtNanos = 0L

    init {
        isOpaque = false
        alpha = 1f
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun noteComposeFrame(frameNanos: Long) {
        if (frameNanos == 0L || frameNanos == lastComposeFrameNanos) return
        lastComposeFrameNanos = frameNanos
        if (System.nanoTime() - lastItemChangeAtNanos <= FOLLOW_UP_RENDER_WINDOW_NANOS) {
            thread?.requestRender()
        }
    }

    fun setViewportHint(w: Int, h: Int): Boolean {
        val dirty = w != viewportHintW || h != viewportHintH
        viewportHintW = max(w, 1)
        viewportHintH = max(h, 1)
        return dirty
    }

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap): Boolean {
        val dirty = blurBitmap !== blur || lensBitmap !== lens
        blur = blurBitmap
        lens = lensBitmap
        if (dirty) thread?.setBackdropTextures(blurBitmap, lensBitmap)
        return dirty
    }

    fun setGlassStyle(next: GlassBorderStyle): Boolean {
        val dirty = next != style
        style = next
        if (dirty) thread?.setGlassStyle(next)
        return dirty
    }

    fun setItems(next: List<DrawItem>, rootW: Float, rootH: Float, predictionFactor: Float): Boolean {
        val predicted = next.withPredictedMotionFrom(rawItems, predictionFactor.coerceIn(0f, 1.4f))
        val signature = predicted.fastSignature(rootW, rootH)
        val dirty = signature != lastItemSignature
        rawItems = next
        if (dirty) {
            items = predicted
            this.rootW = rootW.coerceAtLeast(1f)
            this.rootH = rootH.coerceAtLeast(1f)
            lastItemSignature = signature
            lastItemChangeAtNanos = System.nanoTime()
            thread?.setItems(predicted, this.rootW, this.rootH)
        }
        return dirty
    }

    fun requestRender() = thread?.requestRender() ?: Unit

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        thread?.shutdown()
        thread = BatchedGlassEglThread(Surface(surfaceTexture), width, height).also {
            it.setGlassStyle(style)
            it.setItems(items, rootW, rootH)
            val b = blur
            val l = lens
            if (b != null && l != null) it.setBackdropTextures(b, l)
            it.start()
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

private fun List<DrawItem>.fastSignature(rootW: Float, rootH: Float): Int {
    fun q(value: Float): Int = (value * SIGNATURE_QUANTIZE).roundToInt()
    var result = size * 31 + q(rootW) * 17 + q(rootH)
    forEach { item ->
        result = result * 31 + item.key.hashCode()
        result = result * 31 + q(item.left)
        result = result * 31 + q(item.top)
        result = result * 31 + q(item.width)
        result = result * 31 + q(item.height)
        result = result * 31 + q(item.originX)
        result = result * 31 + q(item.originY)
        result = result * 31 + q(item.radiusPx)
        result = result * 31 + q(item.intensity * 100f)
    }
    return result
}

private fun List<DrawItem>.withPredictedMotionFrom(previous: List<DrawItem>, predictionFactor: Float): List<DrawItem> {
    if (predictionFactor <= 0.001f || isEmpty() || previous.isEmpty()) return this
    val previousByKey = previous.associateBy { it.key }
    var changed = false
    val predicted = map { item ->
        val last = previousByKey[item.key] ?: return@map item
        val dx = (item.left - last.left).coerceIn(-MAX_SCROLL_PREDICTION_PX, MAX_SCROLL_PREDICTION_PX)
        val dy = (item.top - last.top).coerceIn(-MAX_SCROLL_PREDICTION_PX, MAX_SCROLL_PREDICTION_PX)
        val predictX = if (abs(dx) > MIN_SCROLL_PREDICTION_PX) dx * predictionFactor else 0f
        val predictY = if (abs(dy) > MIN_SCROLL_PREDICTION_PX) dy * predictionFactor else 0f
        if (predictX == 0f && predictY == 0f) {
            item
        } else {
            changed = true
            item.copy(
                left = item.left + predictX,
                top = item.top + predictY,
                originX = item.originX + predictX,
                originY = item.originY + predictY
            )
        }
    }
    return if (changed) predicted else this
}

private class BatchedGlassEglThread(private val surface: Surface, width: Int, height: Int) : Thread("BatchedOpenGLGlassThread") {
    private val renderer = BatchedOpenGlGlassRenderer()
    private val lock = Object()
    @Volatile private var running = true
    @Volatile private var pending = true
    @Volatile private var viewportW = max(width, 1)
    @Volatile private var viewportH = max(height, 1)
    @Volatile private var sizeDirty = true
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun setItems(items: List<DrawItem>, rootW: Float, rootH: Float) = renderer.setItems(items, rootW, rootH)
    fun setBackdropTextures(blur: Bitmap, lens: Bitmap) = renderer.setBackdropTextures(blur, lens)
    fun setGlassStyle(style: GlassBorderStyle) = renderer.setGlassStyle(style)

    fun requestRender() {
        synchronized(lock) {
            pending = true
            lock.notifyAll()
        }
    }

    fun resize(width: Int, height: Int) {
        viewportW = max(width, 1)
        viewportH = max(height, 1)
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
            renderer.onSurfaceChanged(viewportW, viewportH)
            sizeDirty = false
            while (running) {
                synchronized(lock) {
                    while (!pending && running) lock.wait()
                    pending = false
                }
                if (!running) break
                if (sizeDirty) {
                    renderer.onSurfaceChanged(viewportW, viewportH)
                    sizeDirty = false
                }
                renderer.onDrawFrame()
                EGL14.eglSwapBuffers(display, eglSurface)
            }
        } finally {
            runCatching { renderer.onRelease() }
            releaseEgl()
            surface.release()
        }
    }

    private fun initEgl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "Unable to initialize EGL" }
        val attrs = intArrayOf(
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
        check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, configs.size, count, 0)) { "Unable to choose EGL config" }
        val config = configs[0] ?: error("No EGL config found")
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }
        eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL window surface" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "Unable to make EGL current" }
    }

    private fun releaseEgl() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}

private class BatchedOpenGlGlassRenderer {
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(8 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val textureLock = Any()
    private val itemLock = Any()
    private var pendingBlur: Bitmap? = null
    private var pendingLens: Bitmap? = null
    private var activeBlur: Bitmap? = null
    private var activeLens: Bitmap? = null
    private var blurTex = 0
    private var lensTex = 0
    private var ready = false
    private var items: List<DrawItem> = emptyList()
    private var rootW = 1f
    private var rootH = 1f
    @Volatile private var style = GlassBorderStyle()
    private var program = 0
    private var positionHandle = 0
    private var resolutionHandle = 0
    private var originHandle = 0
    private var rootHandle = 0
    private var rectHandle = 0
    private var radiusHandle = 0
    private var textureReadyHandle = 0
    private var materialHandle = 0
    private var refractionHandle = 0
    private var opticsHandle = 0
    private var blurHandle = 0
    private var lensHandle = 0
    private var viewportW = 1
    private var viewportH = 1

    fun setItems(next: List<DrawItem>, rw: Float, rh: Float) {
        synchronized(itemLock) {
            items = next
            rootW = rw.coerceAtLeast(1f)
            rootH = rh.coerceAtLeast(1f)
        }
    }

    fun setBackdropTextures(blur: Bitmap, lens: Bitmap) {
        synchronized(textureLock) {
            pendingBlur = blur
            pendingLens = lens
        }
    }

    fun setGlassStyle(s: GlassBorderStyle) {
        style = s
    }

    fun onSurfaceCreated() {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        originHandle = GLES20.glGetUniformLocation(program, "uCardOrigin")
        rootHandle = GLES20.glGetUniformLocation(program, "uRootResolution")
        rectHandle = GLES20.glGetUniformLocation(program, "uRect")
        radiusHandle = GLES20.glGetUniformLocation(program, "uRadius")
        textureReadyHandle = GLES20.glGetUniformLocation(program, "uTextureReady")
        materialHandle = GLES20.glGetUniformLocation(program, "uMaterial")
        refractionHandle = GLES20.glGetUniformLocation(program, "uRefraction")
        opticsHandle = GLES20.glGetUniformLocation(program, "uOptics")
        blurHandle = GLES20.glGetUniformLocation(program, "uBlurTexture")
        lensHandle = GLES20.glGetUniformLocation(program, "uLensTexture")
        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        blurTex = textures[0]
        lensTex = textures[1]
        configureTexture(blurTex)
        configureTexture(lensTex)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
    }

    fun onSurfaceChanged(w: Int, h: Int) {
        viewportW = max(w, 1)
        viewportH = max(h, 1)
        GLES20.glViewport(0, 0, viewportW, viewportH)
    }

    fun onDrawFrame() {
        uploadPendingTextures()
        val drawItems: List<DrawItem>
        val drawRootW: Float
        val drawRootH: Float
        synchronized(itemLock) {
            drawItems = items
            drawRootW = rootW
            drawRootH = rootH
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolutionHandle, viewportW.toFloat(), viewportH.toFloat())
        GLES20.glUniform2f(rootHandle, drawRootW, drawRootH)
        GLES20.glUniform1f(textureReadyHandle, if (ready) 1f else 0f)
        val currentStyle = style
        GLES20.glUniform4f(refractionHandle, currentStyle.openGlPullScale.coerceIn(-240f, 240f), currentStyle.edgePullDp.coerceIn(-420f, 420f), currentStyle.openGlCompressionScale.coerceIn(-8f, 8f), currentStyle.openGlCornerScale.coerceIn(0f, 160f))
        GLES20.glUniform4f(opticsHandle, currentStyle.openGlSampleRadiusScale.coerceIn(0f, 80f), currentStyle.ringWidthDp.coerceIn(0f, 220f), currentStyle.openGlDebugLineAlpha.coerceIn(0f, 1f), currentStyle.openGlDarkScale.coerceIn(-8f, 8f))
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTex)
        GLES20.glUniform1i(blurHandle, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lensTex)
        GLES20.glUniform1i(lensHandle, 1)
        GLES20.glEnableVertexAttribArray(positionHandle)
        drawItems.forEach { item -> drawItem(item, currentStyle) }
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun drawItem(item: DrawItem, currentStyle: GlassBorderStyle) {
        if (item.width <= 1f || item.height <= 1f) return
        if (item.left >= viewportW || item.top >= viewportH || item.left + item.width <= 0f || item.top + item.height <= 0f) return
        val l = item.left / viewportW.toFloat() * 2f - 1f
        val r = (item.left + item.width) / viewportW.toFloat() * 2f - 1f
        val t = 1f - item.top / viewportH.toFloat() * 2f
        val b = 1f - (item.top + item.height) / viewportH.toFloat() * 2f
        vertices.clear()
        vertices.put(floatArrayOf(l, b, r, b, l, t, r, t))
        vertices.position(0)
        GLES20.glUniform2f(originHandle, item.originX, item.originY)
        GLES20.glUniform4f(rectHandle, item.left, item.top, item.width, item.height)
        GLES20.glUniform1f(radiusHandle, item.radiusPx.coerceIn(2f, max(item.width, item.height)))
        GLES20.glUniform4f(materialHandle, currentStyle.openGlVisibility.coerceIn(0f, 20f), currentStyle.openGlMaxAlpha.coerceIn(0f, 1f) * item.intensity, currentStyle.edgeBrightness.coerceIn(-4f, 4f), currentStyle.bodyAlpha.coerceIn(-4f, 4f))
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun onRelease() {
        val textures = intArrayOf(blurTex, lensTex)
        if (blurTex != 0 || lensTex != 0) GLES20.glDeleteTextures(2, textures, 0)
    }

    private fun uploadPendingTextures() {
        val pair = synchronized(textureLock) { pendingBlur to pendingLens }
        val b = pair.first
        val l = pair.second
        if (b == null || l == null) {
            ready = false
            return
        }
        if (b !== activeBlur) {
            uploadBitmap(blurTex, b)
            activeBlur = b
        }
        if (l !== activeLens) {
            uploadBitmap(lensTex, l)
            activeLens = l
        }
        ready = true
    }

    private fun configureTexture(id: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun uploadBitmap(id: Int, bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun buildProgram(vertex: String, fragment: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            error("Batched OpenGL glass program link failed: $log")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return p
    }

    private fun compileShader(type: Int, source: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, source)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            GLES20.glDeleteShader(s)
            error("Batched OpenGL glass shader compile failed: $log")
        }
        return s
    }

    private companion object {
        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() { gl_Position = vec4(aPosition, 0.0, 1.0); }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec2 uResolution;
            uniform vec2 uCardOrigin;
            uniform vec2 uRootResolution;
            uniform vec4 uRect;
            uniform float uRadius;
            uniform float uTextureReady;
            uniform vec4 uMaterial;
            uniform vec4 uRefraction;
            uniform vec4 uOptics;
            uniform sampler2D uBlurTexture;
            uniform sampler2D uLensTexture;

            float sat(float x) { return clamp(x, 0.0, 1.0); }

            float roundedBoxSdf(vec2 local, vec2 size, float radius) {
                vec2 p = local - size * 0.5;
                vec2 q = abs(p) - max(size * 0.5 - vec2(radius), vec2(0.0));
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }

            vec2 globalUv(vec2 localCoord) {
                return clamp((uCardOrigin + localCoord) / max(uRootResolution, vec2(1.0)), 0.0, 1.0);
            }

            vec3 sampleBlur(vec2 uv) {
                vec3 fallback = mix(vec3(0.05, 0.10, 0.23), vec3(0.32, 0.24, 0.45), smoothstep(0.0, 1.0, uv.y));
                return mix(fallback, texture2D(uBlurTexture, uv).rgb, sat(uTextureReady));
            }

            vec3 sampleLens(vec2 uv) {
                vec3 fallback = mix(vec3(0.05, 0.10, 0.23), vec3(0.32, 0.24, 0.45), smoothstep(0.0, 1.0, uv.y));
                return mix(fallback, texture2D(uLensTexture, uv).rgb, sat(uTextureReady));
            }

            vec2 sdfNormal(vec2 local, vec2 size, float radius) {
                float d = 2.0;
                float l = roundedBoxSdf(local - vec2(d, 0.0), size, radius);
                float r = roundedBoxSdf(local + vec2(d, 0.0), size, radius);
                float t = roundedBoxSdf(local - vec2(0.0, d), size, radius);
                float b = roundedBoxSdf(local + vec2(0.0, d), size, radius);
                vec2 n = vec2(r - l, b - t);
                return n / max(length(n), 0.001);
            }

            void main() {
                vec2 screenCoord = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);
                vec2 local = screenCoord - uRect.xy;
                vec2 size = max(uRect.zw, vec2(1.0));
                float radius = min(uRadius, min(size.x, size.y) * 0.5);
                float sd = roundedBoxSdf(local, size, radius);
                float mask = 1.0 - smoothstep(0.0, 1.35, sd);
                if (mask <= 0.001) discard;

                float inside = max(-sd, 0.0);
                float edgeWidth = clamp(uOptics.y, 4.0, min(size.x, size.y) * 0.32);
                float edgeWide = 1.0 - smoothstep(0.0, edgeWidth, inside);
                float edgeCore = 1.0 - smoothstep(0.0, max(edgeWidth * 0.26, 2.0), inside);
                vec2 normal = sdfNormal(local, size, radius);
                vec2 centerDir = normalize(local - size * 0.5 + vec2(0.001));
                vec2 dir = mix(centerDir, normal, edgeWide);

                float bodyPull = uRefraction.x * 0.035 * (1.0 - edgeWide);
                float edgePull = uRefraction.y * edgeWide;
                vec2 offsetPx = dir * (bodyPull + edgePull);
                float limitPx = mix(10.0, 42.0, edgeWide);
                float lenPx = length(offsetPx);
                offsetPx *= (lenPx / (1.0 + lenPx / max(limitPx, 1.0))) / max(lenPx, 0.0001);

                vec2 uv = globalUv(local + offsetPx);
                vec3 base = sampleBlur(uv);
                vec2 stepUv = vec2(max(uOptics.x, 0.0)) / max(uRootResolution, vec2(1.0));
                vec3 soft = base * 0.52;
                soft += sampleBlur(uv + vec2(stepUv.x, 0.0)) * 0.12;
                soft += sampleBlur(uv - vec2(stepUv.x, 0.0)) * 0.12;
                soft += sampleBlur(uv + vec2(0.0, stepUv.y)) * 0.12;
                soft += sampleBlur(uv - vec2(0.0, stepUv.y)) * 0.12;

                vec3 lens = sampleLens(uv);
                float lensMix = edgeCore * sat(max(uRefraction.z, 0.0)) * 0.28;
                vec3 color = mix(soft, lens, lensMix);

                float brightBand = edgeCore * 0.045;
                float darkBand = smoothstep(edgeWidth * 0.30, edgeWidth, inside) * edgeWide * 0.040;
                color += vec3(brightBand);
                color -= vec3(darkBand) * sat(uOptics.w);
                color *= uMaterial.z;
                float debug = smoothstep(-1.65, 0.0, sd) * mask;
                color = mix(color, vec3(1.0, 0.45, 0.0), debug * uOptics.z);
                color = clamp(color, 0.0, 1.0);
                gl_FragColor = vec4(color, clamp(uMaterial.x * uMaterial.y, 0.0, 1.0) * mask);
            }
        """
    }
}
