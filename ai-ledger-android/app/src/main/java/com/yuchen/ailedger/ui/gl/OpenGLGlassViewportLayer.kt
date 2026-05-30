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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.LocalBackdropFrameTicker
import com.yuchen.ailedger.ui.LocalBackdropOrigin
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassBackdrop
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val FRAME_NANOS_UNSET = Long.MIN_VALUE

data class OpenGLGlassViewportItem(
    val key: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val radiusPx: Float,
    val intensity: Float
)

@Composable
fun OpenGLGlassViewportLayer(
    items: List<OpenGLGlassViewportItem>,
    modifier: Modifier = Modifier
) {
    val backdrop = LocalBlurredBackdrop.current ?: return
    val border = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
    val backdropOrigin = LocalBackdropOrigin.current
    val ticker = LocalBackdropFrameTicker.current
    val density = LocalDensity.current
    val frameNanos = ticker?.frameNanos ?: FRAME_NANOS_UNSET
    val coordinates = remember { GlassCoordinateSource() }

    val blurBitmap = backdrop.image.asAndroidBitmap()
    val lensBitmap = backdrop.lensImage.asAndroidBitmap()

    BoxWithConstraints(
        modifier = modifier.onPlaced { coordinates.coordinates = it }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
        val heightPx = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
        val rootWidthPx = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
        val rootHeightPx = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)
        val viewportOrigin = coordinates.offsetRelativeTo(backdropOrigin)
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context -> OpenGLGlassViewportTextureView(context) },
            update = { view ->
                val viewportDirty = view.setViewportSpec(
                    width = widthPx,
                    height = heightPx,
                    originX = viewportOrigin.x,
                    originY = viewportOrigin.y,
                    rootWidth = rootWidthPx,
                    rootHeight = rootHeightPx
                )
                val itemDirty = view.setGlassItems(items)
                val textureDirty = view.setBackdropTextures(blurBitmap, lensBitmap)
                val styleDirty = view.setGlassStyle(border)
                val frameDirty = view.setFrameSignal(frameNanos)
                if (viewportDirty || itemDirty || textureDirty || styleDirty || frameDirty) {
                    view.requestRender()
                }
            }
        )
    }
}

private class OpenGLGlassViewportTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: ViewportGlassEglThread? = null
    private var latestBlurBitmap: Bitmap? = null
    private var latestLensBitmap: Bitmap? = null
    private var latestItems: List<OpenGLGlassViewportItem> = emptyList()
    private var latestWidth = 1f
    private var latestHeight = 1f
    private var latestOriginX = 0f
    private var latestOriginY = 0f
    private var latestRootWidth = 1f
    private var latestRootHeight = 1f
    private var latestStyle = GlassBorderStyle()
    private var latestFrameSignal = FRAME_NANOS_UNSET
    private var pendingRenderRequested = false

    init {
        isOpaque = false
        alpha = 1f
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setViewportSpec(width: Float, height: Float, originX: Float, originY: Float, rootWidth: Float, rootHeight: Float): Boolean {
        val nextWidth = width.coerceAtLeast(1f)
        val nextHeight = height.coerceAtLeast(1f)
        val nextRootWidth = rootWidth.coerceAtLeast(1f)
        val nextRootHeight = rootHeight.coerceAtLeast(1f)
        val dirty = abs(nextWidth - latestWidth) > 0.5f ||
            abs(nextHeight - latestHeight) > 0.5f ||
            abs(originX - latestOriginX) > 0.05f ||
            abs(originY - latestOriginY) > 0.05f ||
            abs(nextRootWidth - latestRootWidth) > 0.5f ||
            abs(nextRootHeight - latestRootHeight) > 0.5f
        if (!dirty) return false
        latestWidth = nextWidth
        latestHeight = nextHeight
        latestOriginX = originX
        latestOriginY = originY
        latestRootWidth = nextRootWidth
        latestRootHeight = nextRootHeight
        renderThread?.setViewportSpec(latestWidth, latestHeight, latestOriginX, latestOriginY, latestRootWidth, latestRootHeight)
        return true
    }

    fun setGlassItems(items: List<OpenGLGlassViewportItem>): Boolean {
        val next = items.filter { it.width > 0.5f && it.height > 0.5f }
        if (next == latestItems) return false
        latestItems = next
        renderThread?.setGlassItems(next)
        return true
    }

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap): Boolean {
        val dirty = blurBitmap !== latestBlurBitmap || lensBitmap !== latestLensBitmap
        if (!dirty) return false
        latestBlurBitmap = blurBitmap
        latestLensBitmap = lensBitmap
        renderThread?.setBackdropTextures(blurBitmap, lensBitmap)
        return true
    }

    fun setGlassStyle(style: GlassBorderStyle): Boolean {
        if (style == latestStyle) return false
        latestStyle = style
        renderThread?.setGlassStyle(style)
        return true
    }

    fun setFrameSignal(frameNanos: Long): Boolean {
        if (frameNanos == FRAME_NANOS_UNSET || frameNanos == latestFrameSignal) return false
        latestFrameSignal = frameNanos
        return true
    }

    fun requestRender() {
        pendingRenderRequested = true
        renderThread?.requestRender()
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        renderThread?.shutdown()
        renderThread = ViewportGlassEglThread(Surface(surfaceTexture), width, height).also { thread ->
            thread.setViewportSpec(latestWidth, latestHeight, latestOriginX, latestOriginY, latestRootWidth, latestRootHeight)
            thread.setGlassItems(latestItems)
            thread.setGlassStyle(latestStyle)
            val blur = latestBlurBitmap
            val lens = latestLensBitmap
            if (blur != null && lens != null) thread.setBackdropTextures(blur, lens)
            thread.start()
            if (pendingRenderRequested || latestItems.isNotEmpty()) thread.requestRender()
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        renderThread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        renderThread?.shutdown()
        renderThread = null
        pendingRenderRequested = false
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
}

private class ViewportGlassEglThread(
    private val surface: Surface,
    width: Int,
    height: Int
) : Thread("OpenGLGlassViewportThread") {
    private val renderer = OpenGLGlassViewportRenderer()
    private val renderLock = Object()

    @Volatile private var running = true
    @Volatile private var pendingRender = true
    @Volatile private var viewportWidth = max(width, 1)
    @Volatile private var viewportHeight = max(height, 1)
    @Volatile private var sizeDirty = true

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun setViewportSpec(width: Float, height: Float, originX: Float, originY: Float, rootWidth: Float, rootHeight: Float) = renderer.setViewportSpec(width, height, originX, originY, rootWidth, rootHeight)
    fun setGlassItems(items: List<OpenGLGlassViewportItem>) = renderer.setGlassItems(items)
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

private class OpenGLGlassViewportRenderer {
    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(FULLSCREEN_QUAD.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(FULLSCREEN_QUAD)
            position(0)
        }

    private val textureLock = Any()
    private var pendingBlurBitmap: Bitmap? = null
    private var pendingLensBitmap: Bitmap? = null
    private var activeBlurBitmap: Bitmap? = null
    private var activeLensBitmap: Bitmap? = null
    private var blurTextureId = 0
    private var lensTextureId = 0
    private var texturesReady = false

    @Volatile private var items: List<OpenGLGlassViewportItem> = emptyList()
    private var viewportOriginX = 0f
    private var viewportOriginY = 0f
    private var rootWidth = 1f
    private var rootHeight = 1f
    private var style = GlassBorderStyle()
    private var program = 0
    private var positionHandle = 0
    private var resolutionHandle = 0
    private var viewportOriginHandle = 0
    private var rootResolutionHandle = 0
    private var rectHandle = 0
    private var radiusHandle = 0
    private var intensityHandle = 0
    private var textureReadyHandle = 0
    private var blurTextureHandle = 0
    private var lensTextureHandle = 0
    private var materialHandle = 0
    private var refractionHandle = 0
    private var opticsHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1

    fun setViewportSpec(width: Float, height: Float, originX: Float, originY: Float, rootWidth: Float, rootHeight: Float) {
        viewportWidth = width.roundToInt().coerceAtLeast(1)
        viewportHeight = height.roundToInt().coerceAtLeast(1)
        viewportOriginX = originX
        viewportOriginY = originY
        this.rootWidth = rootWidth.coerceAtLeast(1f)
        this.rootHeight = rootHeight.coerceAtLeast(1f)
    }

    fun setGlassItems(items: List<OpenGLGlassViewportItem>) {
        this.items = items
    }

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap) {
        synchronized(textureLock) {
            pendingBlurBitmap = blurBitmap
            pendingLensBitmap = lensBitmap
        }
    }

    fun setGlassStyle(style: GlassBorderStyle) {
        this.style = style
    }

    fun onSurfaceCreated() {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        viewportOriginHandle = GLES20.glGetUniformLocation(program, "uViewportOrigin")
        rootResolutionHandle = GLES20.glGetUniformLocation(program, "uRootResolution")
        rectHandle = GLES20.glGetUniformLocation(program, "uRect")
        radiusHandle = GLES20.glGetUniformLocation(program, "uRadius")
        intensityHandle = GLES20.glGetUniformLocation(program, "uIntensity")
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
        val drawItems = items
        if (drawItems.isEmpty()) return

        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES20.glUniform2f(viewportOriginHandle, viewportOriginX, viewportOriginY)
        GLES20.glUniform2f(rootResolutionHandle, rootWidth, rootHeight)
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
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)

        drawItems.forEach { item ->
            if (item.width <= 0.5f || item.height <= 0.5f) return@forEach
            val left = item.left.roundToInt().coerceIn(-viewportWidth, viewportWidth)
            val top = item.top.roundToInt().coerceIn(-viewportHeight, viewportHeight)
            val right = (item.left + item.width).roundToInt().coerceIn(0, viewportWidth)
            val bottom = (item.top + item.height).roundToInt().coerceIn(0, viewportHeight)
            val scissorWidth = right - left.coerceAtLeast(0)
            val scissorHeight = bottom - top.coerceAtLeast(0)
            if (scissorWidth <= 0 || scissorHeight <= 0) return@forEach
            val scissorX = left.coerceAtLeast(0)
            val scissorY = (viewportHeight - bottom).coerceAtLeast(0)
            GLES20.glScissor(scissorX, scissorY, scissorWidth, scissorHeight)
            GLES20.glUniform4f(rectHandle, item.left, item.top, item.width, item.height)
            GLES20.glUniform1f(radiusHandle, item.radiusPx.coerceIn(2f, max(item.width, item.height)))
            GLES20.glUniform1f(intensityHandle, item.intensity.coerceIn(0.35f, 1.35f))
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
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
            error("OpenGL program link failed: $log")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return glProgram
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("OpenGL shader compile failed: $log")
        }
        return shader
    }
}

private val FULLSCREEN_QUAD = floatArrayOf(
    -1f, -1f,
    1f, -1f,
    -1f, 1f,
    1f, 1f
)

private const val VERTEX_SHADER = """
attribute vec2 aPosition;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

private const val FRAGMENT_SHADER = """
precision mediump float;
uniform vec2 uResolution;
uniform vec2 uViewportOrigin;
uniform vec2 uRootResolution;
uniform vec4 uRect;
uniform float uRadius;
uniform float uIntensity;
uniform float uTextureReady;
uniform sampler2D uBlurTexture;
uniform sampler2D uLensTexture;
uniform vec4 uMaterial;
uniform vec4 uRefraction;
uniform vec4 uOptics;

float sdRoundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

vec3 saturateColor(vec3 color, float amount) {
    float gray = dot(color, vec3(0.2126, 0.7152, 0.0722));
    return mix(vec3(gray), color, amount);
}

void main() {
    vec2 frag = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);
    vec2 local = frag - uRect.xy;
    vec2 halfSize = uRect.zw * 0.5;
    vec2 centered = local - halfSize;
    float dist = sdRoundedBox(centered, halfSize, uRadius);
    if (dist > 1.0) discard;

    vec2 uvLocal = local / max(uRect.zw, vec2(1.0));
    float edgeDistance = min(min(uvLocal.x, 1.0 - uvLocal.x), min(uvLocal.y, 1.0 - uvLocal.y));
    float edge = 1.0 - smoothstep(0.0, max(uOptics.y, 1.0) / max(min(uRect.z, uRect.w), 1.0), edgeDistance);
    float corner = smoothstep(0.18, 0.96, length((uvLocal - vec2(0.5)) * vec2(uRect.z / max(uRect.w, 1.0), 1.0)));
    float pull = edge * uRefraction.x + corner * uRefraction.z;
    vec2 pullDir = normalize(centered + vec2(0.001));
    vec2 sampleRoot = uViewportOrigin + frag - pullDir * pull;
    vec2 rootUv = clamp(sampleRoot / max(uRootResolution, vec2(1.0)), vec2(0.0), vec2(1.0));
    vec3 blur = texture2D(uBlurTexture, rootUv).rgb;
    vec3 lens = texture2D(uLensTexture, rootUv).rgb;
    vec3 backdrop = mix(blur, lens, clamp(uOptics.x * 0.34, 0.0, 1.0));
    if (uTextureReady < 0.5) {
        backdrop = vec3(0.08, 0.14, 0.26);
    }

    float visibility = clamp(uMaterial.x / 10.0, 0.0, 1.0);
    float maxAlpha = clamp(uMaterial.y, 0.0, 1.0);
    float bodyAlpha = clamp(0.20 + uMaterial.w * 0.34, 0.05, 0.92) * uIntensity;
    float edgeAlpha = clamp(edge * (0.20 + uMaterial.z * 0.10), 0.0, 1.0);
    vec3 body = saturateColor(backdrop, clamp(1.0 + uMaterial.z * 0.12, 0.5, 2.3));
    vec3 milk = vec3(0.86, 0.92, 1.0) * (0.06 + bodyAlpha * 0.18);
    vec3 dark = vec3(0.01, 0.03, 0.08) * (edge * clamp(uOptics.w * 0.08, 0.0, 0.8));
    vec3 rim = vec3(0.78, 0.94, 1.0) * edgeAlpha;
    vec3 color = body * (0.78 + bodyAlpha * 0.28) + milk + rim - dark;

    float top = smoothstep(0.55, 0.0, uvLocal.y) * 0.10;
    float bottom = smoothstep(0.40, 1.0, uvLocal.y) * 0.12;
    color += vec3(top);
    color -= vec3(bottom * 0.38);

    float alpha = clamp((0.42 + bodyAlpha * 0.34 + edge * 0.20) * visibility, 0.0, maxAlpha);
    alpha *= 1.0 - smoothstep(-1.0, 1.0, dist) * 0.12;
    gl_FragColor = vec4(color, alpha);
}
"""
