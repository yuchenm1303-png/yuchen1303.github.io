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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.ui.LocalBackdropFrameTicker
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassItemRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * OpenGL liquid glass layer.
 *
 * This layer reads real glass item bounds from GlassItemRegistry and samples the app's generated
 * blurred/lens backdrop bitmaps as OpenGL textures. The shader uses a thick-edge lens model:
 * transparent body, compressed rim, inner shadow, outer highlight, corner caustics and subtle
 * chromatic dispersion.
 */
@Composable
fun OpenGLGlassProbeLayer(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!enabled) return
    val registry = LocalGlassItemRegistry.current
    val backdrop = LocalBlurredBackdrop.current
    val ticker = LocalBackdropFrameTicker.current
    val density = LocalDensity.current
    ticker?.frameNanos

    val textureSource = backdrop?.let {
        GlBackdropTextureSource(
            blurBitmap = it.image.asAndroidBitmap(),
            lensBitmap = it.lensImage.asAndroidBitmap()
        )
    }
    val items = registry
        ?.snapshot()
        .orEmpty()
        .mapNotNull { item ->
            if (!item.coordinates.isAttached()) return@mapNotNull null
            val size = item.coordinates.itemSize()
            if (size.width <= 1 || size.height <= 1) return@mapNotNull null
            val topLeft = item.coordinates.rootOffset()
            GlGlassRect(
                left = topLeft.x.roundToInt().toFloat(),
                top = topLeft.y.roundToInt().toFloat(),
                width = size.width.toFloat(),
                height = size.height.toFloat(),
                radiusPx = with(density) { item.radius.dp.toPx() }.roundToInt().toFloat(),
                intensity = item.glassIntensity.coerceIn(0.35f, 1.35f)
            )
        }
        .take(MAX_GLASS_ITEMS)

    AndroidView(
        modifier = modifier,
        factory = { context -> OpenGLGlassProbeTextureView(context) },
        update = { view ->
            view.setBackdropTextures(textureSource)
            view.setGlassRects(items)
            view.requestRender()
        }
    )
}

private data class GlGlassRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val radiusPx: Float,
    val intensity: Float
)

private data class GlBackdropTextureSource(
    val blurBitmap: Bitmap,
    val lensBitmap: Bitmap
)

private class OpenGLGlassProbeTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: GlassEglRenderThread? = null
    private var latestRects: List<GlGlassRect> = emptyList()
    private var latestTextureSource: GlBackdropTextureSource? = null

    init {
        isOpaque = false
        alpha = 1f
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setGlassRects(rects: List<GlGlassRect>) {
        latestRects = rects
        renderThread?.setGlassRects(rects)
    }

    fun setBackdropTextures(source: GlBackdropTextureSource?) {
        latestTextureSource = source
        renderThread?.setBackdropTextures(source)
    }

    fun requestRender() {
        renderThread?.requestRender()
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        renderThread?.shutdown()
        renderThread = GlassEglRenderThread(Surface(surfaceTexture), width, height).also {
            it.setBackdropTextures(latestTextureSource)
            it.setGlassRects(latestRects)
            it.start()
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

private class GlassEglRenderThread(
    private val surface: Surface,
    width: Int,
    height: Int
) : Thread("OpenGLGlassProbeTextureThread") {
    private val renderer = OpenGLGlassProbeRenderer()
    private val renderLock = Object()

    @Volatile private var running = true
    @Volatile private var pendingRender = true
    @Volatile private var viewportWidth = max(width, 1)
    @Volatile private var viewportHeight = max(height, 1)
    @Volatile private var sizeDirty = true

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun setGlassRects(rects: List<GlGlassRect>) {
        renderer.setGlassRects(rects)
        requestRender()
    }

    fun setBackdropTextures(source: GlBackdropTextureSource?) {
        renderer.setBackdropTextures(source)
        requestRender()
    }

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
                    while (!pendingRender && running) {
                        renderLock.wait()
                    }
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
        check(EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, configs, 0, configs.size, configCount, 0)) {
            "Unable to choose EGL config"
        }
        val eglConfig = configs[0] ?: error("No EGL config found")

        val contextAttributes = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttributes, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }

        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL window surface" }

        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "Unable to make EGL context current" }
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
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

private class OpenGLGlassProbeRenderer {
    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(FULLSCREEN_QUAD.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(FULLSCREEN_QUAD)
            position(0)
        }

    private val rectLock = Any()
    private val textureLock = Any()
    private var glassRects: List<GlGlassRect> = emptyList()
    private var stableGlassRects: List<GlGlassRect> = emptyList()
    private var pendingTextureSource: GlBackdropTextureSource? = null
    private var activeBlurBitmap: Bitmap? = null
    private var activeLensBitmap: Bitmap? = null
    private var blurTextureId = 0
    private var lensTextureId = 0
    private var texturesReady = false

    private var program = 0
    private var positionHandle = 0
    private var resolutionHandle = 0
    private var timeHandle = 0
    private var rectHandle = 0
    private var radiusHandle = 0
    private var intensityHandle = 0
    private var textureReadyHandle = 0
    private var blurTextureHandle = 0
    private var lensTextureHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var startTimeNanos = System.nanoTime()

    fun setGlassRects(rects: List<GlGlassRect>) {
        synchronized(rectLock) {
            glassRects = rects
        }
    }

    fun setBackdropTextures(source: GlBackdropTextureSource?) {
        synchronized(textureLock) {
            pendingTextureSource = source
        }
    }

    fun onSurfaceCreated() {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        timeHandle = GLES20.glGetUniformLocation(program, "uTime")
        rectHandle = GLES20.glGetUniformLocation(program, "uRect")
        radiusHandle = GLES20.glGetUniformLocation(program, "uRadius")
        intensityHandle = GLES20.glGetUniformLocation(program, "uIntensity")
        textureReadyHandle = GLES20.glGetUniformLocation(program, "uTextureReady")
        blurTextureHandle = GLES20.glGetUniformLocation(program, "uBlurTexture")
        lensTextureHandle = GLES20.glGetUniformLocation(program, "uLensTexture")
        startTimeNanos = System.nanoTime()

        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        blurTextureId = textures[0]
        lensTextureId = textures[1]
        configureTexture(blurTextureId)
        configureTexture(lensTextureId)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
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

        val targetRects = synchronized(rectLock) { glassRects }
        if (targetRects.isEmpty()) {
            stableGlassRects = emptyList()
            return
        }
        val rects = stabilizeRects(targetRects)

        val seconds = (System.nanoTime() - startTimeNanos) / 1_000_000_000f
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES20.glUniform1f(timeHandle, seconds)
        GLES20.glUniform1f(textureReadyHandle, if (texturesReady) 1f else 0f)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextureId)
        GLES20.glUniform1i(blurTextureHandle, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lensTextureId)
        GLES20.glUniform1i(lensTextureHandle, 1)

        quadVertices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, quadVertices)
        rects.forEach { rect ->
            val safeWidth = rect.width.coerceAtLeast(1f)
            val safeHeight = rect.height.coerceAtLeast(1f)
            GLES20.glUniform4f(rectHandle, rect.left, rect.top, safeWidth, safeHeight)
            GLES20.glUniform1f(radiusHandle, rect.radiusPx.coerceIn(2f, max(safeWidth, safeHeight)))
            GLES20.glUniform1f(intensityHandle, rect.intensity)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    fun onRelease() {
        val textures = intArrayOf(blurTextureId, lensTextureId).filter { it != 0 }.toIntArray()
        if (textures.isNotEmpty()) GLES20.glDeleteTextures(textures.size, textures, 0)
        blurTextureId = 0
        lensTextureId = 0
        activeBlurBitmap = null
        activeLensBitmap = null
        stableGlassRects = emptyList()
        texturesReady = false
    }

    private fun stabilizeRects(targetRects: List<GlGlassRect>): List<GlGlassRect> {
        if (stableGlassRects.size != targetRects.size) {
            stableGlassRects = targetRects
            return targetRects
        }
        val next = targetRects.mapIndexed { index, target ->
            val previous = stableGlassRects[index]
            if (previous.isProbablyDifferentItem(target)) {
                target
            } else {
                target.stabilizedFrom(previous)
            }
        }
        stableGlassRects = next
        return next
    }

    private fun GlGlassRect.isProbablyDifferentItem(target: GlGlassRect): Boolean {
        return abs(width - target.width) > 24f ||
            abs(height - target.height) > 24f ||
            abs(radiusPx - target.radiusPx) > 18f ||
            abs(left - target.left) > 96f ||
            abs(top - target.top) > 96f
    }

    private fun GlGlassRect.stabilizedFrom(previous: GlGlassRect): GlGlassRect {
        return copy(
            left = stabilizeAxis(previous.left, left),
            top = stabilizeAxis(previous.top, top),
            width = stabilizeSize(previous.width, width),
            height = stabilizeSize(previous.height, height),
            radiusPx = stabilizeSize(previous.radiusPx, radiusPx),
            intensity = stabilizeFloat(previous.intensity, intensity, 0.02f, 0.55f)
        )
    }

    private fun stabilizeAxis(previous: Float, target: Float): Float {
        val delta = target - previous
        val distance = abs(delta)
        if (distance < 0.65f) return previous
        if (distance > 42f) return target
        return (previous + delta * 0.86f).roundToInt().toFloat()
    }

    private fun stabilizeSize(previous: Float, target: Float): Float {
        val delta = target - previous
        val distance = abs(delta)
        if (distance < 0.65f) return previous
        if (distance > 10f) return target
        return (previous + delta * 0.72f).roundToInt().toFloat()
    }

    private fun stabilizeFloat(previous: Float, target: Float, deadZone: Float, follow: Float): Float {
        val delta = target - previous
        if (abs(delta) < deadZone) return previous
        return previous + delta * follow
    }

    private fun uploadPendingTexturesIfNeeded() {
        val source = synchronized(textureLock) { pendingTextureSource } ?: run {
            texturesReady = false
            return
        }
        if (source.blurBitmap === activeBlurBitmap && source.lensBitmap === activeLensBitmap && texturesReady) return
        uploadBitmapToTexture(blurTextureId, source.blurBitmap)
        uploadBitmapToTexture(lensTextureId, source.lensBitmap)
        activeBlurBitmap = source.blurBitmap
        activeLensBitmap = source.lensBitmap
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
            error("OpenGL glass probe program link failed: $log")
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
            error("OpenGL glass probe shader compile failed: $log")
        }
        return shader
    }

    private companion object {
        val FULLSCREEN_QUAD = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
        )

        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec2 uResolution;
            uniform float uTime;
            uniform vec4 uRect;
            uniform float uRadius;
            uniform float uIntensity;
            uniform float uTextureReady;
            uniform sampler2D uBlurTexture;
            uniform sampler2D uLensTexture;

            float roundedBoxSdf(vec2 p, vec2 halfSize, float radius) {
                vec2 q = abs(p) - max(halfSize - vec2(radius), vec2(0.0));
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }

            float sat(float x) {
                return clamp(x, 0.0, 1.0);
            }

            vec3 saturateColor(vec3 color, float amount) {
                float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                return mix(vec3(luma), color, amount);
            }

            vec3 syntheticBackdrop(vec2 uv) {
                float blueBand = smoothstep(0.16, 0.46, uv.x) * (1.0 - smoothstep(0.58, 0.94, uv.x));
                float redBand = smoothstep(0.38, 0.66, uv.x) * (1.0 - smoothstep(0.82, 1.04, uv.x));
                float horizon = smoothstep(0.18, 0.94, uv.y);
                float wave = 0.5 + 0.5 * sin((uv.x * 7.0 + uv.y * 3.0 + uTime * 0.12) * 3.14159);
                vec3 sky = mix(vec3(0.18, 0.30, 0.66), vec3(0.88, 0.57, 0.62), horizon);
                vec3 color = sky;
                color = mix(color, vec3(0.20, 0.46, 1.0), blueBand * 0.62);
                color = mix(color, vec3(1.0, 0.24, 0.16), redBand * 0.70);
                color += vec3(0.05, 0.04, 0.08) * wave;
                return clamp(color, 0.0, 1.0);
            }

            vec2 texUv(vec2 uv) {
                return clamp(vec2(uv.x, 1.0 - uv.y), 0.0, 1.0);
            }

            vec3 blurBackdrop(vec2 uv) {
                vec3 fallback = syntheticBackdrop(uv);
                vec3 realColor = texture2D(uBlurTexture, texUv(uv)).rgb;
                return mix(fallback, realColor, sat(uTextureReady));
            }

            vec3 lensBackdrop(vec2 uv) {
                vec3 fallback = syntheticBackdrop(uv);
                vec3 realColor = texture2D(uLensTexture, texUv(uv)).rgb;
                return mix(fallback, realColor, sat(uTextureReady));
            }

            vec3 lensDispersion(vec2 uv, vec2 n, float amountPx) {
                vec2 d = n * amountPx / uResolution;
                float r = lensBackdrop(uv + d * 0.85).r;
                float g = lensBackdrop(uv).g;
                float b = lensBackdrop(uv - d * 0.85).b;
                return vec3(r, g, b);
            }

            vec3 anisotropicLensSample(vec2 uv, vec2 n, vec2 t, float radiusPx, float dispersionPx) {
                vec2 r = vec2(radiusPx) / uResolution;
                vec3 c = lensDispersion(uv, n, dispersionPx) * 0.24;
                c += lensDispersion(uv + n * r.x * 0.55, n, dispersionPx) * 0.15;
                c += lensDispersion(uv - n * r.x * 0.55, n, dispersionPx) * 0.13;
                c += lensDispersion(uv + n * r.x * 1.15, n, dispersionPx * 0.80) * 0.10;
                c += lensDispersion(uv - n * r.x * 1.15, n, dispersionPx * 0.80) * 0.09;
                c += lensBackdrop(uv + t * r.x * 0.42) * 0.07;
                c += lensBackdrop(uv - t * r.x * 0.42) * 0.07;
                c += lensBackdrop(uv + (n + t * 0.45) * r.x * 0.88) * 0.05;
                c += lensBackdrop(uv + (n - t * 0.45) * r.x * 0.88) * 0.05;
                c += lensBackdrop(uv - (n + t * 0.45) * r.x * 0.88) * 0.03;
                c += lensBackdrop(uv - (n - t * 0.45) * r.x * 0.88) * 0.02;
                return c;
            }

            void main() {
                vec2 coord = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);
                vec2 rectPos = uRect.xy;
                vec2 rectSize = max(uRect.zw, vec2(1.0));
                vec2 center = rectPos + rectSize * 0.5;
                float radius = min(uRadius, min(rectSize.x, rectSize.y) * 0.5);
                vec2 p = coord - center;
                float sd = roundedBoxSdf(p, rectSize * 0.5, radius);
                float mask = 1.0 - smoothstep(0.0, 1.65, sd);
                if (mask <= 0.001) {
                    discard;
                }

                float inside = max(-sd, 0.0);
                vec2 dx = vec2(1.0, 0.0);
                vec2 dy = vec2(0.0, 1.0);
                float gx = roundedBoxSdf(p + dx, rectSize * 0.5, radius) - roundedBoxSdf(p - dx, rectSize * 0.5, radius);
                float gy = roundedBoxSdf(p + dy, rectSize * 0.5, radius) - roundedBoxSdf(p - dy, rectSize * 0.5, radius);
                vec2 normal = normalize(vec2(gx, gy) + vec2(0.0001));
                vec2 tangent = vec2(-normal.y, normal.x);
                float corner = sat(abs(normal.x * normal.y) * 2.35);

                vec2 uv = coord / uResolution;
                vec2 local01 = clamp((coord - rectPos) / rectSize, 0.0, 1.0);
                float minSide = min(rectSize.x, rectSize.y);
                float edgeWidth = minSide * 0.36;

                float surfaceGate = sat(1.0 - inside / max(edgeWidth * 1.58, 1.0));
                float edgeCore = exp(-inside / max(edgeWidth * 0.090, 1.0));
                float outerRim = exp(-inside / max(edgeWidth * 0.050, 1.0));
                float edgeShoulder = exp(-inside / max(edgeWidth * 0.42, 1.0));
                float innerFade = exp(-inside / max(edgeWidth * 0.95, 1.0));
                float compressionBand = exp(-pow((inside - edgeWidth * 0.23) / max(edgeWidth * 0.145, 1.0), 2.0));
                float innerShadow = exp(-pow((inside - edgeWidth * 0.60) / max(edgeWidth * 0.24, 1.0), 2.0));
                float cornerCaustic = corner * exp(-inside / max(edgeWidth * 0.26, 1.0));
                float topLight = smoothstep(1.0, 0.0, local01.y) * surfaceGate;
                float bottomShade = smoothstep(0.55, 1.0, local01.y) * innerFade;
                float leftGlow = smoothstep(0.20, 0.0, local01.x) * surfaceGate;
                float rightWarm = smoothstep(0.55, 1.0, local01.x) * smoothstep(0.18, 1.0, local01.y) * surfaceGate;

                float tangentPhase = sin((p.x * 0.013 + p.y * 0.009) + uTime * 0.20);
                float cornerBoost = 1.0 + corner * 0.62;
                float outerPull = edgeCore * min(34.0, edgeWidth * 0.74);
                float innerPull = compressionBand * min(56.0, edgeWidth * 1.02);
                float shoulderPull = edgeShoulder * min(30.0, edgeWidth * 0.68);
                float pull = (outerPull + innerPull + shoulderPull + cornerCaustic * min(30.0, edgeWidth * 0.58)) * surfaceGate * cornerBoost;
                float tangentBend = tangentPhase * edgeShoulder * min(18.0, edgeWidth * 0.36) * (1.0 + corner * 0.55);
                vec2 refractUv = uv + (normal * pull + tangent * tangentBend) / uResolution;

                vec3 base = blurBackdrop(uv);
                vec3 body = saturateColor(base, 1.06);
                body = mix(body, vec3(1.0), 0.055 + topLight * 0.025);
                body *= 1.018;

                float sampleRadius = mix(3.0, 10.5, sat(corner + edgeShoulder * 0.38));
                float dispersion = (0.65 + corner * 1.65 + edgeCore * 0.90) * surfaceGate;
                vec3 lensColor = anisotropicLensSample(refractUv, normal, tangent, sampleRadius, dispersion);
                vec3 compressed = clamp((lensColor - 0.5) * 1.28 + 0.5, 0.0, 1.0);
                lensColor = mix(lensColor, compressed, compressionBand * 0.34);

                float lensMix = sat(surfaceGate * (edgeCore * 0.72 + edgeShoulder * 0.38 + compressionBand * 0.42 + cornerCaustic * 0.30));
                vec3 color = mix(body, lensColor, lensMix);

                vec3 coolRim = vec3(0.74, 0.87, 1.0);
                vec3 warmRim = vec3(1.0, 0.68, 0.74);
                color += coolRim * (outerRim * 0.135 + leftGlow * 0.035 + topLight * 0.030);
                color += warmRim * (cornerCaustic * 0.115 + rightWarm * 0.045);
                color += vec3(1.0) * (compressionBand * 0.038);
                color -= vec3(0.07, 0.09, 0.13) * (innerShadow * 0.74 + bottomShade * 0.050);

                float thinSpec = outerRim * (0.62 + topLight * 0.62 + corner * 0.48);
                color += vec3(1.0, 0.97, 0.92) * thinSpec * 0.070;
                color = clamp(color, 0.0, 1.0);

                float bodyAlpha = 0.095;
                float edgeAlpha = edgeCore * 0.32 + edgeShoulder * 0.12 + compressionBand * 0.13 + outerRim * 0.18 + cornerCaustic * 0.13;
                float highlightAlpha = topLight * 0.030 + thinSpec * 0.040;
                float alpha = mask * (bodyAlpha + edgeAlpha + highlightAlpha) * clamp(uIntensity, 0.35, 1.28);
                alpha = clamp(alpha, 0.0, 0.62);
                gl_FragColor = vec4(color, alpha);
            }
        """
    }
}

private const val MAX_GLASS_ITEMS = 36
