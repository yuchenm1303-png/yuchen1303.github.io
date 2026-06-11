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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Immutable
data class OpenGLLiquidPotentialLabOptics(
    val surfaceWidth: Float = 0.18f,
    val surfaceSteepness: Float = 1.20f,
    val refractionGainPx: Float = 96f,
    val slopeResponse: Float = 0.78f,
    val lensClarity: Float = 0.64f,
    val tangentSmear: Float = 0.32f,
    val centerLensPx: Float = 14f,
    val edgeDarkness: Float = 0.36f,
    val highlightStrength: Float = 0.34f,
    val brightness: Float = 1.00f
)

@Immutable
private data class OpenGLLabBackdropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f
)

@Composable
fun OpenGLLiquidPotentialLabLayer(
    optics: OpenGLLiquidPotentialLabOptics,
    radiusDp: Int,
    modifier: Modifier = Modifier
) {
    val backdrop = LocalBlurredBackdrop.current
    val density = LocalDensity.current
    val rootView = LocalView.current
    var backdropRect by remember { mutableStateOf(OpenGLLabBackdropRect()) }
    val radiusPx = with(density) { radiusDp.dp.toPx() }.roundToInt().toFloat()
    val blurBitmap = remember(backdrop?.image) { backdrop?.image?.asAndroidBitmap() }
    val lensBitmap = remember(backdrop?.lensImage) { backdrop?.lensImage?.asAndroidBitmap() }
    AndroidView(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val position = coordinates.positionInWindow()
            val rootWidth = rootView.width.toFloat().coerceAtLeast(1f)
            val rootHeight = rootView.height.toFloat().coerceAtLeast(1f)
            val next = OpenGLLabBackdropRect(
                left = (position.x / rootWidth).coerceIn(0f, 1f),
                top = (position.y / rootHeight).coerceIn(0f, 1f),
                width = (coordinates.size.width.toFloat() / rootWidth).coerceIn(0.001f, 1f),
                height = (coordinates.size.height.toFloat() / rootHeight).coerceIn(0.001f, 1f)
            )
            if (next != backdropRect) backdropRect = next
        },
        factory = { context -> OpenGLLiquidPotentialLabView(context) },
        update = { view ->
            val radiusDirty = view.setRadius(radiusPx)
            val opticsDirty = view.setOptics(optics)
            val rectDirty = view.setBackdropRect(backdropRect)
            val textureDirty = view.setBackdropTextures(blurBitmap, lensBitmap)
            if (radiusDirty || opticsDirty || rectDirty || textureDirty) view.requestRender()
        }
    )
}

private class OpenGLLiquidPotentialLabView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: LabEglThread? = null
    private var latestBlurBitmap: Bitmap? = null
    private var latestLensBitmap: Bitmap? = null
    private var latestRadius = 32f
    private var latestOptics = OpenGLLiquidPotentialLabOptics()
    private var latestBackdropRect = OpenGLLabBackdropRect()

    init {
        isOpaque = false
        alpha = 1f
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setRadius(radius: Float): Boolean {
        val safeRadius = radius.coerceAtLeast(1f)
        val dirty = abs(safeRadius - latestRadius) > 0.5f
        latestRadius = safeRadius
        if (dirty) renderThread?.setRadius(safeRadius)
        return dirty
    }

    fun setOptics(optics: OpenGLLiquidPotentialLabOptics): Boolean {
        val dirty = optics != latestOptics
        latestOptics = optics
        if (dirty) renderThread?.setOptics(optics)
        return dirty
    }

    fun setBackdropRect(rect: OpenGLLabBackdropRect): Boolean {
        val dirty = rect != latestBackdropRect
        latestBackdropRect = rect
        if (dirty) renderThread?.setBackdropRect(rect)
        return dirty
    }

    fun setBackdropTextures(blurBitmap: Bitmap?, lensBitmap: Bitmap?): Boolean {
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
        renderThread = LabEglThread(Surface(surfaceTexture), width, height).also { thread ->
            thread.setRadius(latestRadius)
            thread.setOptics(latestOptics)
            thread.setBackdropRect(latestBackdropRect)
            thread.setBackdropTextures(latestBlurBitmap, latestLensBitmap)
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

private class LabEglThread(
    private val surface: Surface,
    width: Int,
    height: Int
) : Thread("OpenGLLiquidPotentialLabThread") {
    private val renderer = LabRenderer()
    private val renderLock = Object()

    @Volatile private var running = true
    @Volatile private var pendingRender = true
    @Volatile private var viewportWidth = max(width, 1)
    @Volatile private var viewportHeight = max(height, 1)
    @Volatile private var sizeDirty = true

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun setRadius(radius: Float) {
        renderer.setRadius(radius)
    }

    fun setOptics(optics: OpenGLLiquidPotentialLabOptics) {
        renderer.setOptics(optics)
    }

    fun setBackdropRect(rect: OpenGLLabBackdropRect) {
        renderer.setBackdropRect(rect)
    }

    fun setBackdropTextures(blurBitmap: Bitmap?, lensBitmap: Bitmap?) {
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
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
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

private class LabRenderer {
    private val quadVertices = java.nio.ByteBuffer
        .allocateDirect(FULLSCREEN_QUAD.size * Float.SIZE_BYTES)
        .order(java.nio.ByteOrder.nativeOrder())
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
    private var radius = 32f
    private var optics = OpenGLLiquidPotentialLabOptics()
    private var backdropRect = OpenGLLabBackdropRect()

    private var program = 0
    private var positionHandle = 0
    private var resolutionHandle = 0
    private var radiusHandle = 0
    private var textureReadyHandle = 0
    private var blurTextureHandle = 0
    private var lensTextureHandle = 0
    private var opticsAHandle = 0
    private var opticsBHandle = 0
    private var opticsCHandle = 0
    private var backdropRectHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1

    fun setRadius(radius: Float) {
        synchronized(specLock) { this.radius = radius.coerceAtLeast(1f) }
    }

    fun setOptics(optics: OpenGLLiquidPotentialLabOptics) {
        synchronized(specLock) { this.optics = optics }
    }

    fun setBackdropRect(rect: OpenGLLabBackdropRect) {
        synchronized(specLock) { this.backdropRect = rect }
    }

    fun setBackdropTextures(blurBitmap: Bitmap?, lensBitmap: Bitmap?) {
        synchronized(textureLock) {
            pendingBlurBitmap = blurBitmap
            pendingLensBitmap = lensBitmap
        }
    }

    fun onSurfaceCreated() {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        radiusHandle = GLES20.glGetUniformLocation(program, "uRadius")
        textureReadyHandle = GLES20.glGetUniformLocation(program, "uTextureReady")
        blurTextureHandle = GLES20.glGetUniformLocation(program, "uBlurTexture")
        lensTextureHandle = GLES20.glGetUniformLocation(program, "uLensTexture")
        opticsAHandle = GLES20.glGetUniformLocation(program, "uOpticsA")
        opticsBHandle = GLES20.glGetUniformLocation(program, "uOpticsB")
        opticsCHandle = GLES20.glGetUniformLocation(program, "uOpticsC")
        backdropRectHandle = GLES20.glGetUniformLocation(program, "uBackdropRect")
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
        var localRadius = radius
        var localOptics = optics
        var localRect = backdropRect
        synchronized(specLock) {
            localRadius = radius
            localOptics = optics
            localRect = backdropRect
        }
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES20.glUniform1f(radiusHandle, localRadius)
        GLES20.glUniform1f(textureReadyHandle, if (texturesReady) 1f else 0f)
        GLES20.glUniform4f(
            backdropRectHandle,
            localRect.left.coerceIn(0f, 1f),
            localRect.top.coerceIn(0f, 1f),
            localRect.width.coerceIn(0.001f, 1f),
            localRect.height.coerceIn(0.001f, 1f)
        )
        GLES20.glUniform4f(
            opticsAHandle,
            localOptics.surfaceWidth.coerceIn(0.04f, 0.75f),
            localOptics.surfaceSteepness.coerceIn(0.35f, 3.2f),
            localOptics.refractionGainPx.coerceIn(0f, 300f),
            localOptics.slopeResponse.coerceIn(0.25f, 1.6f)
        )
        GLES20.glUniform4f(
            opticsBHandle,
            localOptics.lensClarity.coerceIn(0f, 2f),
            localOptics.tangentSmear.coerceIn(0f, 2.4f),
            localOptics.centerLensPx.coerceIn(0f, 120f),
            localOptics.edgeDarkness.coerceIn(0f, 2f)
        )
        GLES20.glUniform4f(
            opticsCHandle,
            localOptics.highlightStrength.coerceIn(0f, 2f),
            localOptics.brightness.coerceIn(0.45f, 2.2f),
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
        var blur: Bitmap? = null
        var lens: Bitmap? = null
        synchronized(textureLock) {
            blur = pendingBlurBitmap
            lens = pendingLensBitmap
        }
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
            error("OpenGL liquid lab program link failed: $log")
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
            error("OpenGL liquid lab shader compile failed: $log")
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
            precision highp float;
            uniform vec2 uResolution;
            uniform float uRadius;
            uniform float uTextureReady;
            uniform vec4 uOpticsA;
            uniform vec4 uOpticsB;
            uniform vec4 uOpticsC;
            uniform vec4 uBackdropRect;
            uniform sampler2D uBlurTexture;
            uniform sampler2D uLensTexture;

            float sat(float x) { return clamp(x, 0.0, 1.0); }
            vec2 clampUv(vec2 uv) { return clamp(uv, vec2(0.0), vec2(1.0)); }
            vec2 safeGlassUv(vec2 uv) { return clamp(uv, vec2(0.018), vec2(0.982)); }
            vec2 backdropUv(vec2 localUv) { return uBackdropRect.xy + safeGlassUv(localUv) * uBackdropRect.zw; }

            float roundedBoxSdf(vec2 coord, vec2 rectSize, float radius) {
                vec2 p = coord - rectSize * 0.5;
                vec2 halfSize = rectSize * 0.5;
                vec2 q = abs(p) - max(halfSize - vec2(radius), vec2(0.0));
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }

            vec3 fallbackBackdrop(vec2 uv) {
                float h = smoothstep(0.0, 1.0, uv.y);
                float x = smoothstep(0.0, 1.0, uv.x);
                return mix(vec3(0.10, 0.20, 0.34), vec3(0.36, 0.68, 0.82), h * 0.72 + x * 0.18);
            }

            vec3 sampleBlur(vec2 localUv) {
                if (uTextureReady < 0.5) return fallbackBackdrop(clampUv(localUv));
                return texture2D(uBlurTexture, clampUv(backdropUv(localUv))).rgb;
            }

            vec3 sampleLens(vec2 localUv) {
                if (uTextureReady < 0.5) return fallbackBackdrop(clampUv(localUv));
                return texture2D(uLensTexture, clampUv(backdropUv(localUv))).rgb;
            }

            float edgeProfileAt(float sd, float fieldWidth, float steepness) {
                float inside = max(-sd, 0.0);
                float shape = 1.0 - smoothstep(0.0, 3.0, sd);
                float t = inside / max(fieldWidth, 1.0);
                float curve = pow(max(t, 0.0), max(steepness, 0.35));
                return exp(-curve * 1.42) * shape;
            }

            float surfaceHeightAt(vec2 coord, vec2 rectSize, float radius) {
                float sd = roundedBoxSdf(coord, rectSize, radius);
                float minSide = max(min(rectSize.x, rectSize.y), 1.0);
                float fieldWidth = minSide * (0.06 + uOpticsA.x * 0.28);
                float edgeProfile = edgeProfileAt(sd, fieldWidth, uOpticsA.y);
                float shape = 1.0 - smoothstep(0.0, 3.0, sd);

                vec2 local = clampUv(coord / rectSize);
                vec2 p = local * 2.0 - 1.0;
                p.x *= min(rectSize.x / max(rectSize.y, 1.0), 2.35) * 0.42;
                float centerD = length(p);
                float dome = pow(sat(1.0 - centerD * 0.74), 1.82);
                float bridge = pow(sat(edgeProfile * dome), 0.62);
                float cornerFocus = sat(length((local - vec2(0.5)) * vec2(rectSize.x / max(rectSize.y, 1.0), 1.0)) * 1.22 - 0.20);
                float height = edgeProfile * 0.58 + bridge * 0.22 + dome * 0.14 + cornerFocus * edgeProfile * 0.11;
                return height * shape;
            }

            vec2 softLimitPx(vec2 v, float limitPx) {
                float len = length(v);
                float softLen = len / (1.0 + len / max(limitPx, 1.0));
                return v * (softLen / max(len, 0.0001));
            }

            void main() {
                vec2 coord = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);
                vec2 rectSize = max(uResolution, vec2(1.0));
                float radius = min(uRadius, min(rectSize.x, rectSize.y) * 0.5);
                float sd = roundedBoxSdf(coord, rectSize, radius);
                float mask = 1.0 - smoothstep(0.0, 1.40, sd);
                if (mask <= 0.001) discard;

                vec2 uv = coord / rectSize;
                vec2 centerDelta = uv - vec2(0.5);
                centerDelta.x *= min(rectSize.x / max(rectSize.y, 1.0), 2.35);
                float centerD = length(centerDelta);
                float body = pow(sat(1.0 - centerD * 0.78), 1.60);

                float minSide = max(min(rectSize.x, rectSize.y), 1.0);
                float fieldWidth = minSide * (0.06 + uOpticsA.x * 0.28);
                float inside = max(-sd, 0.0);
                float edgeProfile = edgeProfileAt(sd, fieldWidth, uOpticsA.y);
                float edgeSafe = smoothstep(1.5, 12.0, inside);

                float stepPx = 2.0;
                float pL = surfaceHeightAt(coord - vec2(stepPx, 0.0), rectSize, radius);
                float pR = surfaceHeightAt(coord + vec2(stepPx, 0.0), rectSize, radius);
                float pU = surfaceHeightAt(coord - vec2(0.0, stepPx), rectSize, radius);
                float pD = surfaceHeightAt(coord + vec2(0.0, stepPx), rectSize, radius);
                vec2 grad = vec2(pR - pL, pD - pU) * 0.5;
                float slope = length(grad);
                vec2 gradDir = grad / max(slope, 0.0001);
                vec2 bodyDir = centerDelta / max(centerD, 0.0001);

                float response = max(uOpticsA.w, 0.25);
                float slopeEnergy = pow(sat(slope * 8.2 + edgeProfile * 0.055), response);
                float shoulderEnergy = sat(edgeProfile * 0.22 + slopeEnergy * 0.70);
                vec2 slopeRefract = gradDir * slopeEnergy * uOpticsA.z * 0.52 * (0.24 + shoulderEnergy * 0.62);
                vec2 bodyLens = -bodyDir * body * uOpticsB.z * 0.18;
                vec2 flow = softLimitPx(slopeRefract + bodyLens, mix(26.0, 72.0, shoulderEnergy));
                vec2 refractedUv = safeGlassUv(uv + flow / max(rectSize, vec2(1.0)));

                vec3 blurColor = sampleBlur(refractedUv);
                vec3 lensColor = sampleLens(refractedUv);
                float lensMix = sat((0.12 + shoulderEnergy * 0.54 + body * 0.05) * uOpticsB.x * 0.78);
                vec3 color = mix(blurColor, lensColor, lensMix);

                float smear = sat((slopeEnergy * 0.55 + edgeProfile * 0.20) * uOpticsB.y * 0.36 * edgeSafe);
                if (smear > 0.002) {
                    vec2 tangent = vec2(-gradDir.y, gradDir.x);
                    vec2 tangentPx = tangent * (3.0 + 18.0 * smear) / max(rectSize, vec2(1.0));
                    vec2 normalPx = gradDir * (2.0 + 7.0 * smear) / max(rectSize, vec2(1.0));
                    vec3 drag = sampleLens(refractedUv + tangentPx) * 0.34;
                    drag += sampleLens(refractedUv - tangentPx) * 0.34;
                    drag += sampleBlur(refractedUv + normalPx) * 0.16;
                    drag += sampleBlur(refractedUv - normalPx) * 0.16;
                    color = mix(color, drag, sat(smear * 0.24));
                }

                float lightFacing = sat(0.54 - gradDir.y * 0.36 + gradDir.x * 0.05);
                float highlight = sat((slopeEnergy * 0.36 + edgeProfile * 0.10 + body * 0.020) * lightFacing * uOpticsC.x);
                color *= uOpticsC.y * (1.0 + highlight * 0.24);
                color += vec3(0.030, 0.050, 0.064) * highlight;
                color -= vec3(0.044, 0.056, 0.070) * uOpticsB.w * shoulderEnergy;
                color += vec3(0.014, 0.026, 0.034) * body * 0.055;
                color = clamp(color, 0.0, 1.0);
                gl_FragColor = vec4(color, 0.90 * mask);
            }
        """
    }
}
