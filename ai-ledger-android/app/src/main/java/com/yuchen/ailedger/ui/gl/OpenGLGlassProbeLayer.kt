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
import kotlin.math.max

/**
 * Stage 3 OpenGL glass probe.
 *
 * The layer reads real glass item bounds from GlassItemRegistry and samples the app's generated
 * blurred/lens backdrop bitmaps as OpenGL textures. It still runs as a probe layer until the old
 * Compose fallback is intentionally reduced or disabled.
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
                left = topLeft.x,
                top = topLeft.y,
                width = size.width.toFloat(),
                height = size.height.toFloat(),
                radiusPx = with(density) { item.radius.dp.toPx() },
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

        val rects = synchronized(rectLock) { glassRects }
        if (rects.isEmpty()) return

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
        texturesReady = false
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
                vec2 q = abs(p) - (halfSize - vec2(radius));
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
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
                return mix(fallback, realColor, clamp(uTextureReady, 0.0, 1.0));
            }

            vec3 lensBackdrop(vec2 uv) {
                vec3 fallback = syntheticBackdrop(uv);
                vec3 realColor = texture2D(uLensTexture, texUv(uv)).rgb;
                return mix(fallback, realColor, clamp(uTextureReady, 0.0, 1.0));
            }

            vec3 softLensSample(vec2 uv, vec2 n, vec2 t, float radiusPx) {
                vec2 r = vec2(radiusPx) / uResolution;
                vec3 c = lensBackdrop(uv) * 0.28;
                c += lensBackdrop(uv + n * r.x * 0.75) * 0.15;
                c += lensBackdrop(uv - n * r.x * 0.75) * 0.15;
                c += lensBackdrop(uv + t * r.x * 0.42) * 0.10;
                c += lensBackdrop(uv - t * r.x * 0.42) * 0.10;
                c += lensBackdrop(uv + (n + t * 0.45) * r.x) * 0.08;
                c += lensBackdrop(uv + (n - t * 0.45) * r.x) * 0.08;
                c += lensBackdrop(uv - (n + t * 0.45) * r.x) * 0.03;
                c += lensBackdrop(uv - (n - t * 0.45) * r.x) * 0.03;
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
                float mask = 1.0 - smoothstep(0.0, 2.0, sd);
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
                float corner = clamp(abs(normal.x * normal.y) * 2.2, 0.0, 1.0);

                float edgeWidth = min(rectSize.x, rectSize.y) * 0.32;
                float surfaceGate = clamp(1.0 - inside / max(edgeWidth * 1.42, 1.0), 0.0, 1.0);
                float edgeCore = exp(-inside / max(edgeWidth * 0.12, 1.0));
                float edgeShoulder = exp(-inside / max(edgeWidth * 0.45, 1.0)) * 0.38;
                float outerHighlight = exp(-inside / max(edgeWidth * 0.075, 1.0));
                float compressionBand = exp(-pow((inside - edgeWidth * 0.22) / max(edgeWidth * 0.15, 1.0), 2.0));
                float innerShadow = exp(-pow((inside - edgeWidth * 0.58) / max(edgeWidth * 0.23, 1.0), 2.0));
                float caustic = corner * exp(-inside / max(edgeWidth * 0.28, 1.0));

                vec2 uv = coord / uResolution;
                float pull = (edgeCore * 26.0 + edgeShoulder * 40.0 + caustic * 18.0) * surfaceGate;
                float tangentBend = sin((p.x + p.y) * 0.018) * edgeShoulder * 14.0;
                vec2 refractUv = uv + (normal * pull + tangent * tangentBend) / uResolution;

                vec3 base = blurBackdrop(uv);
                vec3 refracted = softLensSample(refractUv, normal, tangent, mix(3.0, 9.0, corner));
                vec3 color = mix(base, refracted, surfaceGate * 0.66);
                vec3 compressed = clamp((color - 0.5) * 1.18 + 0.5, 0.0, 1.0);
                color = mix(color, compressed, compressionBand * 0.18);
                color += vec3(outerHighlight * 0.10);
                color -= vec3(innerShadow * 0.055);
                color += vec3(caustic * 0.10, caustic * 0.08, caustic * 0.055);
                color = mix(color, vec3(1.0), 0.075);
                color = clamp(color, 0.0, 1.0);

                float alpha = mask * (0.18 + edgeCore * 0.17 + outerHighlight * 0.12 + caustic * 0.06) * clamp(uIntensity, 0.35, 1.35);
                gl_FragColor = vec4(color, alpha);
            }
        """
    }
}

private const val MAX_GLASS_ITEMS = 36
