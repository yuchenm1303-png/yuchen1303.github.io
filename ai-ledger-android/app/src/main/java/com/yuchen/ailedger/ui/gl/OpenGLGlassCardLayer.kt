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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.LocalBackdropFrameTicker
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Card-bound OpenGL lens overlay.
 *
 * Compose / SampledWeatherGlassBackdrop owns the frosted body. This layer must only draw the
 * optical edge lens: compressed rim, inner dark band, thin highlight and corner caustics.
 */
@Composable
fun OpenGLGlassCardLayer(
    radius: Int,
    glassIntensity: Float,
    coordinateSource: GlassCoordinateSource? = null,
    modifier: Modifier = Modifier
) {
    val backdrop = LocalBlurredBackdrop.current ?: return
    val ticker = LocalBackdropFrameTicker.current
    val density = LocalDensity.current
    val rootView = LocalView.current
    ticker?.frameNanos

    val blurBitmap = backdrop.image.asAndroidBitmap()
    val lensBitmap = backdrop.lensImage.asAndroidBitmap()
    val radiusPx = with(density) { radius.dp.toPx() }.roundToInt().toFloat()
    val intensity = glassIntensity.coerceIn(0.35f, 1.35f)
    val cardOrigin = coordinateSource?.rootOffset() ?: Offset.Zero

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
        val heightPx = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
        val rootWidthPx = rootView.width.coerceAtLeast(widthPx.roundToInt()).toFloat().coerceAtLeast(1f)
        val rootHeightPx = rootView.height.coerceAtLeast(heightPx.roundToInt()).toFloat().coerceAtLeast(1f)
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context -> OpenGLGlassCardTextureView(context) },
            update = { view ->
                val specDirty = view.setGlassSpec(widthPx, heightPx, radiusPx, intensity)
                val samplingDirty = view.setSamplingSpec(cardOrigin.x, cardOrigin.y, rootWidthPx, rootHeightPx)
                val textureDirty = view.setBackdropTextures(blurBitmap, lensBitmap)
                if (specDirty || samplingDirty || textureDirty) view.requestRender()
            }
        )
    }
}

private class OpenGLGlassCardTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: CardGlassEglThread? = null
    private var latestBlurBitmap: Bitmap? = null
    private var latestLensBitmap: Bitmap? = null
    private var latestWidth = 1f
    private var latestHeight = 1f
    private var latestRadius = 24f
    private var latestIntensity = 1f
    private var latestOriginX = 0f
    private var latestOriginY = 0f
    private var latestRootWidth = 1f
    private var latestRootHeight = 1f

    init {
        isOpaque = false
        alpha = 1f
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setGlassSpec(width: Float, height: Float, radius: Float, intensity: Float): Boolean {
        val nextWidth = width.coerceAtLeast(1f)
        val nextHeight = height.coerceAtLeast(1f)
        val dirty = abs(nextWidth - latestWidth) > 0.5f ||
            abs(nextHeight - latestHeight) > 0.5f ||
            abs(radius - latestRadius) > 0.5f ||
            abs(intensity - latestIntensity) > 0.006f
        latestWidth = nextWidth
        latestHeight = nextHeight
        latestRadius = radius
        latestIntensity = intensity
        if (dirty) renderThread?.setGlassSpec(latestWidth, latestHeight, latestRadius, latestIntensity)
        return dirty
    }

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float): Boolean {
        val nextRootWidth = rootWidth.coerceAtLeast(1f)
        val nextRootHeight = rootHeight.coerceAtLeast(1f)
        val dirty = abs(originX - latestOriginX) > 0.75f ||
            abs(originY - latestOriginY) > 0.75f ||
            abs(nextRootWidth - latestRootWidth) > 0.5f ||
            abs(nextRootHeight - latestRootHeight) > 0.5f
        latestOriginX = originX
        latestOriginY = originY
        latestRootWidth = nextRootWidth
        latestRootHeight = nextRootHeight
        if (dirty) renderThread?.setSamplingSpec(latestOriginX, latestOriginY, latestRootWidth, latestRootHeight)
        return dirty
    }

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap): Boolean {
        val dirty = blurBitmap !== latestBlurBitmap || lensBitmap !== latestLensBitmap
        latestBlurBitmap = blurBitmap
        latestLensBitmap = lensBitmap
        if (dirty) renderThread?.setBackdropTextures(blurBitmap, lensBitmap)
        return dirty
    }

    fun requestRender() {
        renderThread?.requestRender()
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        renderThread?.shutdown()
        renderThread = CardGlassEglThread(Surface(surfaceTexture), width, height).also { thread ->
            thread.setGlassSpec(latestWidth, latestHeight, latestRadius, latestIntensity)
            thread.setSamplingSpec(latestOriginX, latestOriginY, latestRootWidth, latestRootHeight)
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

    fun setGlassSpec(width: Float, height: Float, radius: Float, intensity: Float) {
        renderer.setGlassSpec(width, height, radius, intensity)
    }

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float) {
        renderer.setSamplingSpec(originX, originY, rootWidth, rootHeight)
    }

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap) {
        renderer.setBackdropTextures(blurBitmap, lensBitmap)
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
    private var pendingBlurBitmap: Bitmap? = null
    private var pendingLensBitmap: Bitmap? = null
    private var activeBlurBitmap: Bitmap? = null
    private var activeLensBitmap: Bitmap? = null
    private var blurTextureId = 0
    private var lensTextureId = 0
    private var texturesReady = false

    private var cardWidth = 1f
    private var cardHeight = 1f
    private var cardRadius = 24f
    private var cardIntensity = 1f
    private var cardOriginX = 0f
    private var cardOriginY = 0f
    private var rootWidth = 1f
    private var rootHeight = 1f

    private var program = 0
    private var positionHandle = 0
    private var resolutionHandle = 0
    private var cardOriginHandle = 0
    private var rootResolutionHandle = 0
    private var rectHandle = 0
    private var radiusHandle = 0
    private var intensityHandle = 0
    private var textureReadyHandle = 0
    private var blurTextureHandle = 0
    private var lensTextureHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1

    fun setGlassSpec(width: Float, height: Float, radius: Float, intensity: Float) {
        cardWidth = width.coerceAtLeast(1f)
        cardHeight = height.coerceAtLeast(1f)
        cardRadius = radius
        cardIntensity = intensity
    }

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float) {
        cardOriginX = originX
        cardOriginY = originY
        this.rootWidth = rootWidth.coerceAtLeast(1f)
        this.rootHeight = rootHeight.coerceAtLeast(1f)
    }

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap) {
        synchronized(textureLock) {
            pendingBlurBitmap = blurBitmap
            pendingLensBitmap = lensBitmap
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
        textureReadyHandle = GLES20.glGetUniformLocation(program, "uTextureReady")
        blurTextureHandle = GLES20.glGetUniformLocation(program, "uBlurTexture")
        lensTextureHandle = GLES20.glGetUniformLocation(program, "uLensTexture")

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

        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES20.glUniform2f(cardOriginHandle, cardOriginX, cardOriginY)
        GLES20.glUniform2f(rootResolutionHandle, rootWidth, rootHeight)
        GLES20.glUniform4f(rectHandle, 0f, 0f, cardWidth, cardHeight)
        GLES20.glUniform1f(radiusHandle, cardRadius.coerceIn(2f, max(cardWidth, cardHeight)))
        GLES20.glUniform1f(intensityHandle, cardIntensity)
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
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
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
        val blur: Bitmap?
        val lens: Bitmap?
        synchronized(textureLock) {
            blur = pendingBlurBitmap
            lens = pendingLensBitmap
        }
        if (blur == null || lens == null) {
            texturesReady = false
            return
        }
        if (blur === activeBlurBitmap && lens === activeLensBitmap && texturesReady) return
        uploadBitmapToTexture(blurTextureId, blur)
        uploadBitmapToTexture(lensTextureId, lens)
        activeBlurBitmap = blur
        activeLensBitmap = lens
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
            uniform float uTextureReady;
            uniform sampler2D uBlurTexture;
            uniform sampler2D uLensTexture;

            float roundedBoxSdf(vec2 p, vec2 halfSize, float radius) {
                vec2 q = abs(p) - max(halfSize - vec2(radius), vec2(0.0));
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }

            float sat(float x) { return clamp(x, 0.0, 1.0); }

            vec2 texUv(vec2 uv) { return clamp(vec2(uv.x, 1.0 - uv.y), 0.0, 1.0); }

            vec2 globalUv(vec2 localCoord) {
                return clamp((uCardOrigin + localCoord) / max(uRootResolution, vec2(1.0)), 0.0, 1.0);
            }

            vec2 pxToRootUv(vec2 px) {
                return px / max(uRootResolution, vec2(1.0));
            }

            vec3 fallbackBackdrop(vec2 uv) {
                float h = smoothstep(0.0, 1.0, uv.y);
                return mix(vec3(0.22, 0.34, 0.70), vec3(0.90, 0.62, 0.64), h);
            }

            vec3 blurBackdrop(vec2 uv) {
                vec3 fallback = fallbackBackdrop(uv);
                vec3 realColor = texture2D(uBlurTexture, texUv(uv)).rgb;
                return mix(fallback, realColor, sat(uTextureReady));
            }

            vec3 lensBackdrop(vec2 uv) {
                vec3 fallback = fallbackBackdrop(uv);
                vec3 realColor = texture2D(uLensTexture, texUv(uv)).rgb;
                return mix(fallback, realColor, sat(uTextureReady));
            }

            vec3 lensDispersion(vec2 uv, vec2 n, float amountPx) {
                vec2 d = pxToRootUv(n * amountPx);
                float r = lensBackdrop(uv + d * 0.70).r;
                float g = lensBackdrop(uv).g;
                float b = lensBackdrop(uv - d * 0.70).b;
                return vec3(r, g, b);
            }

            vec3 softLensSample(vec2 uv, vec2 n, vec2 t, float radiusPx, float dispersionPx) {
                vec2 r = pxToRootUv(vec2(radiusPx));
                vec3 c = lensDispersion(uv, n, dispersionPx) * 0.36;
                c += lensDispersion(uv + n * r.x * 0.90, n, dispersionPx) * 0.18;
                c += lensDispersion(uv - n * r.x * 0.90, n, dispersionPx) * 0.15;
                c += lensBackdrop(uv + t * r.x * 0.52) * 0.10;
                c += lensBackdrop(uv - t * r.x * 0.52) * 0.10;
                c += lensBackdrop(uv + (n + t * 0.45) * r.x * 1.00) * 0.06;
                c += lensBackdrop(uv - (n + t * 0.45) * r.x * 1.00) * 0.05;
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
                if (mask <= 0.001) discard;

                float inside = max(-sd, 0.0);
                float gx = roundedBoxSdf(p + vec2(1.0, 0.0), rectSize * 0.5, radius) - roundedBoxSdf(p - vec2(1.0, 0.0), rectSize * 0.5, radius);
                float gy = roundedBoxSdf(p + vec2(0.0, 1.0), rectSize * 0.5, radius) - roundedBoxSdf(p - vec2(0.0, 1.0), rectSize * 0.5, radius);
                vec2 normal = normalize(vec2(gx, gy) + vec2(0.0001));
                vec2 tangent = vec2(-normal.y, normal.x);
                float corner = sat(abs(normal.x * normal.y) * 2.35);

                vec2 bgUv = globalUv(coord);
                vec2 local01 = clamp((coord - rectPos) / rectSize, 0.0, 1.0);

                // Temporary diagnostic: if card-bound OpenGL is visible, every GL card shows
                // a strong orange horizontal line through its center.
                if (abs(local01.y - 0.50) < 0.018) {
                    gl_FragColor = vec4(1.0, 0.36, 0.0, 0.95 * mask);
                    return;
                }

                float minSide = min(rectSize.x, rectSize.y);
                float edgeWidth = minSide * 0.32;

                float outerHairline = exp(-inside / max(edgeWidth * 0.018, 1.0));
                float rimLens = exp(-inside / max(edgeWidth * 0.075, 1.0));
                float rimShoulder = exp(-inside / max(edgeWidth * 0.24, 1.0));
                float compressionBand = exp(-pow((inside - edgeWidth * 0.23) / max(edgeWidth * 0.115, 1.0), 2.0));
                float innerDarkBand = exp(-pow((inside - edgeWidth * 0.52) / max(edgeWidth * 0.20, 1.0), 2.0));
                float cornerCaustic = corner * exp(-inside / max(edgeWidth * 0.22, 1.0));

                float edgeMask = sat(outerHairline * 1.00 + rimLens * 0.72 + compressionBand * 0.55 + cornerCaustic * 0.70 + innerDarkBand * 0.18);
                if (edgeMask <= 0.006) discard;

                float topLight = smoothstep(1.0, 0.0, local01.y) * (rimShoulder + outerHairline * 0.75);
                float bottomShade = smoothstep(0.55, 1.0, local01.y) * exp(-inside / max(edgeWidth * 0.76, 1.0));
                float leftCool = smoothstep(0.20, 0.0, local01.x) * edgeMask;
                float rightWarm = smoothstep(0.56, 1.0, local01.x) * smoothstep(0.18, 1.0, local01.y) * edgeMask;

                float outerPull = rimLens * min(48.0, edgeWidth * 1.05);
                float innerPull = compressionBand * min(76.0, edgeWidth * 1.35);
                float shoulderPull = rimShoulder * min(32.0, edgeWidth * 0.66);
                float cornerPull = cornerCaustic * min(46.0, edgeWidth * 0.86);

                vec2 outerUv = bgUv + pxToRootUv(normal * (outerPull + shoulderPull + cornerPull));
                vec2 innerUv = bgUv - pxToRootUv(normal * innerPull);
                vec2 tangentUv = bgUv + pxToRootUv(tangent * (compressionBand - 0.35) * min(18.0, edgeWidth * 0.42));

                float dispersion = (0.72 + corner * 2.10 + outerHairline * 0.80) * edgeMask;
                float lensRadius = mix(3.0, 10.0, sat(corner + rimShoulder * 0.50));
                vec3 outerLens = softLensSample(outerUv, normal, tangent, lensRadius, dispersion);
                vec3 innerLens = softLensSample(innerUv, normal, tangent, lensRadius * 0.78, dispersion * 0.70);
                vec3 tangentLens = blurBackdrop(tangentUv);
                vec3 softBase = blurBackdrop(bgUv);

                vec3 compressed = mix(innerLens, outerLens, 0.60 + corner * 0.12);
                compressed = mix(compressed, tangentLens, 0.16 * rimShoulder);
                compressed = mix(softBase, compressed, sat(rimLens * 0.86 + compressionBand * 0.74 + cornerCaustic * 0.78));
                compressed = clamp((compressed - 0.5) * (1.18 + compressionBand * 0.23) + 0.5, 0.0, 1.0);

                vec3 color = compressed;
                color += vec3(0.70, 0.86, 1.0) * (outerHairline * 0.11 + leftCool * 0.030 + topLight * 0.020);
                color += vec3(1.0, 0.66, 0.72) * (cornerCaustic * 0.11 + rightWarm * 0.035);
                color += vec3(1.0) * (compressionBand * 0.032 + outerHairline * 0.032);
                color -= vec3(0.060, 0.080, 0.125) * (innerDarkBand * 0.48 + bottomShade * 0.030);
                color = clamp(color, 0.0, 1.0);

                float thinSpec = outerHairline * (0.58 + topLight * 0.42 + corner * 0.45);
                float edgeAlpha = rimLens * 0.18 + compressionBand * 0.15 + cornerCaustic * 0.16 + innerDarkBand * 0.035 + thinSpec * 0.055;
                float alpha = mask * edgeAlpha * clamp(uIntensity, 0.35, 1.24);
                gl_FragColor = vec4(color, clamp(alpha, 0.0, 0.38));
            }
        """
    }
}
