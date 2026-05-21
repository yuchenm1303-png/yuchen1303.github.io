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
import androidx.compose.ui.unit.dp
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

@Composable
fun OpenGLGlassCardLayer(
    radius: Int,
    glassIntensity: Float,
    coordinateSource: GlassCoordinateSource? = null,
    modifier: Modifier = Modifier
) {
    val backdrop = LocalBlurredBackdrop.current ?: return
    val border = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
    val origin = LocalBackdropOrigin.current
    val ticker = LocalBackdropFrameTicker.current
    val density = LocalDensity.current
    // 读取 ticker 只用于让 Compose 在滑动/动画帧重新执行 update，从而刷新坐标；
    // 真正的 GL 渲染只在参数或坐标变脏时触发，避免静止页面每张卡片每帧重绘。
    val frameNanos = ticker?.frameNanos ?: 0L
    val blurBitmap = backdrop.image.asAndroidBitmap()
    val lensBitmap = backdrop.lensImage.asAndroidBitmap()
    val cardOrigin = coordinateSource?.offsetRelativeTo(origin) ?: Offset.Zero
    val radiusPx = with(density) { radius.dp.toPx() }.roundToInt().toFloat()
    val intensity = glassIntensity.coerceIn(0.35f, 1.35f)

    BoxWithConstraints(modifier = modifier) {
        val w = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
        val h = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { OpenGLGlassCardTextureView(it) },
            update = { view ->
                view.noteComposeFrame(frameNanos)
                val dirtyA = view.setGlassSpec(w, h, radiusPx, intensity)
                val dirtyB = view.setSamplingSpec(cardOrigin.x, cardOrigin.y, backdrop.fullWidthPx.toFloat(), backdrop.fullHeightPx.toFloat())
                val dirtyC = view.setBackdropTextures(blurBitmap, lensBitmap)
                val dirtyD = view.setGlassStyle(border)
                if (dirtyA || dirtyB || dirtyC || dirtyD) view.requestRender()
            }
        )
    }
}

private class OpenGLGlassCardTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var thread: CardGlassEglThread? = null
    private var blur: Bitmap? = null
    private var lens: Bitmap? = null
    private var widthPx = 1f
    private var heightPx = 1f
    private var radiusPx = 24f
    private var intensity = 1f
    private var originX = 0f
    private var originY = 0f
    private var rootW = 1f
    private var rootH = 1f
    private var style = GlassBorderStyle()
    private var lastComposeFrame = 0L

    init {
        isOpaque = false
        alpha = 1f
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun noteComposeFrame(frameNanos: Long) {
        lastComposeFrame = frameNanos
    }

    fun setGlassSpec(w: Float, h: Float, radius: Float, intensity: Float): Boolean {
        val dirty = abs(w - widthPx) > 0.5f || abs(h - heightPx) > 0.5f || abs(radius - radiusPx) > 0.5f || abs(intensity - this.intensity) > 0.006f
        widthPx = w.coerceAtLeast(1f)
        heightPx = h.coerceAtLeast(1f)
        radiusPx = radius
        this.intensity = intensity
        if (dirty) thread?.setGlassSpec(widthPx, heightPx, radiusPx, this.intensity)
        return dirty
    }

    fun setSamplingSpec(x: Float, y: Float, rootW: Float, rootH: Float): Boolean {
        val dirty = abs(x - originX) > 0.05f || abs(y - originY) > 0.05f || abs(rootW - this.rootW) > 0.5f || abs(rootH - this.rootH) > 0.5f
        originX = x
        originY = y
        this.rootW = rootW.coerceAtLeast(1f)
        this.rootH = rootH.coerceAtLeast(1f)
        if (dirty) thread?.setSamplingSpec(originX, originY, this.rootW, this.rootH)
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

    fun requestRender() = thread?.requestRender() ?: Unit

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        thread?.shutdown()
        thread = CardGlassEglThread(Surface(surfaceTexture), width, height).also {
            it.setGlassSpec(widthPx, heightPx, radiusPx, intensity)
            it.setSamplingSpec(originX, originY, rootW, rootH)
            it.setGlassStyle(style)
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

private class CardGlassEglThread(private val surface: Surface, width: Int, height: Int) : Thread("OpenGLGlassCardTextureThread") {
    private val renderer = OpenGLGlassCardRenderer()
    private val lock = Object()
    @Volatile private var running = true
    @Volatile private var pending = true
    @Volatile private var viewportW = max(width, 1)
    @Volatile private var viewportH = max(height, 1)
    @Volatile private var sizeDirty = true
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun setGlassSpec(w: Float, h: Float, radius: Float, intensity: Float) = renderer.setGlassSpec(w, h, radius, intensity)
    fun setSamplingSpec(x: Float, y: Float, rootW: Float, rootH: Float) = renderer.setSamplingSpec(x, y, rootW, rootH)
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

private class OpenGLGlassCardRenderer {
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(8 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        position(0)
    }
    private val textureLock = Any()
    private var pendingBlur: Bitmap? = null
    private var pendingLens: Bitmap? = null
    private var activeBlur: Bitmap? = null
    private var activeLens: Bitmap? = null
    private var blurTex = 0
    private var lensTex = 0
    private var ready = false
    private var cardW = 1f
    private var cardH = 1f
    private var radius = 24f
    private var intensity = 1f
    private var originX = 0f
    private var originY = 0f
    private var rootW = 1f
    private var rootH = 1f
    private var style = GlassBorderStyle()
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

    fun setGlassSpec(w: Float, h: Float, r: Float, i: Float) { cardW = w.coerceAtLeast(1f); cardH = h.coerceAtLeast(1f); radius = r; intensity = i }
    fun setSamplingSpec(x: Float, y: Float, rw: Float, rh: Float) { originX = x; originY = y; rootW = rw.coerceAtLeast(1f); rootH = rh.coerceAtLeast(1f) }
    fun setBackdropTextures(blur: Bitmap, lens: Bitmap) { synchronized(textureLock) { pendingBlur = blur; pendingLens = lens } }
    fun setGlassStyle(s: GlassBorderStyle) { style = s }

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

    fun onSurfaceChanged(w: Int, h: Int) { viewportW = max(w, 1); viewportH = max(h, 1); GLES20.glViewport(0, 0, viewportW, viewportH) }

    fun onDrawFrame() {
        uploadPendingTextures()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolutionHandle, viewportW.toFloat(), viewportH.toFloat())
        GLES20.glUniform2f(originHandle, originX, originY)
        GLES20.glUniform2f(rootHandle, rootW, rootH)
        GLES20.glUniform4f(rectHandle, 0f, 0f, cardW, cardH)
        GLES20.glUniform1f(radiusHandle, radius.coerceIn(2f, max(cardW, cardH)))
        GLES20.glUniform1f(textureReadyHandle, if (ready) 1f else 0f)
        GLES20.glUniform4f(materialHandle, style.openGlVisibility.coerceIn(0f, 20f), style.openGlMaxAlpha.coerceIn(0f, 1f) * intensity, style.edgeBrightness.coerceIn(-5f, 5f), style.bodyAlpha.coerceIn(-5f, 5f))
        GLES20.glUniform4f(refractionHandle, style.openGlPullScale.coerceIn(-300f, 300f), style.edgePullDp.coerceIn(-600f, 600f), style.openGlCompressionScale.coerceIn(-10f, 10f), style.openGlCornerScale.coerceIn(0f, 200f))
        GLES20.glUniform4f(opticsHandle, style.openGlSampleRadiusScale.coerceIn(0f, 200f), style.ringWidthDp.coerceIn(0f, 300f), style.openGlDebugLineAlpha.coerceIn(0f, 1f), style.openGlDarkScale.coerceIn(-10f, 10f))
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTex)
        GLES20.glUniform1i(blurHandle, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lensTex)
        GLES20.glUniform1i(lensHandle, 1)
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    fun onRelease() {
        val textures = intArrayOf(blurTex, lensTex)
        if (blurTex != 0 || lensTex != 0) GLES20.glDeleteTextures(2, textures, 0)
    }

    private fun uploadPendingTextures() {
        val pair = synchronized(textureLock) { pendingBlur to pendingLens }
        val b = pair.first
        val l = pair.second
        if (b == null || l == null) { ready = false; return }
        if (b !== activeBlur) { uploadBitmap(blurTex, b); activeBlur = b }
        if (l !== activeLens) { uploadBitmap(lensTex, l); activeLens = l }
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
            error("OpenGL glass card program link failed: $log")
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
            error("OpenGL glass card shader compile failed: $log")
        }
        return s
    }

    private companion object {
        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() { gl_Position = vec4(aPosition, 0.0, 1.0); }
        """

        const val FRAGMENT_SHADER = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
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

            float roundedBoxSdf(vec2 coord, vec2 size, float radius) {
                vec2 p = coord - size * 0.5;
                vec2 q = abs(p) - max(size * 0.5 - vec2(radius), vec2(0.0));
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }

            vec2 globalUv(vec2 coord) {
                return clamp((uCardOrigin + coord) / max(uRootResolution, vec2(1.0)), 0.0, 1.0);
            }

            vec3 sampleBlur(vec2 uv) {
                vec3 fallback = mix(vec3(0.05, 0.10, 0.23), vec3(0.32, 0.24, 0.45), smoothstep(0.0, 1.0, uv.y));
                return mix(fallback, texture2D(uBlurTexture, uv).rgb, sat(uTextureReady));
            }

            vec3 sampleLens(vec2 uv) {
                vec3 fallback = mix(vec3(0.05, 0.10, 0.23), vec3(0.32, 0.24, 0.45), smoothstep(0.0, 1.0, uv.y));
                return mix(fallback, texture2D(uLensTexture, uv).rgb, sat(uTextureReady));
            }

            vec2 sdfNormal(vec2 coord, vec2 size, float radius) {
                float d = 1.5;
                float l = roundedBoxSdf(coord - vec2(d, 0.0), size, radius);
                float r = roundedBoxSdf(coord + vec2(d, 0.0), size, radius);
                float t = roundedBoxSdf(coord - vec2(0.0, d), size, radius);
                float b = roundedBoxSdf(coord + vec2(0.0, d), size, radius);
                vec2 n = vec2(r - l, b - t);
                return n / max(length(n), 0.001);
            }

            float colorSignal(vec3 c) {
                float luma = dot(c, vec3(0.299, 0.587, 0.114));
                float chroma = length(c - vec3(luma));
                return sat((luma - 0.18) * 1.35 + chroma * 1.6);
            }

            vec3 blur9(vec2 uv, float px) {
                vec2 stepUv = vec2(px) / max(uRootResolution, vec2(1.0));
                vec3 c = sampleBlur(uv) * 0.22;
                c += sampleBlur(uv + vec2(stepUv.x, 0.0)) * 0.11;
                c += sampleBlur(uv - vec2(stepUv.x, 0.0)) * 0.11;
                c += sampleBlur(uv + vec2(0.0, stepUv.y)) * 0.11;
                c += sampleBlur(uv - vec2(0.0, stepUv.y)) * 0.11;
                c += sampleBlur(uv + stepUv) * 0.085;
                c += sampleBlur(uv - stepUv) * 0.085;
                c += sampleBlur(uv + vec2(stepUv.x, -stepUv.y)) * 0.085;
                c += sampleBlur(uv + vec2(-stepUv.x, stepUv.y)) * 0.085;
                return c;
            }

            void main() {
                vec2 coord = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);
                vec2 size = max(uRect.zw, vec2(1.0));
                float radius = min(uRadius, min(size.x, size.y) * 0.5);
                float sd = roundedBoxSdf(coord, size, radius);
                float mask = 1.0 - smoothstep(0.0, 1.35, sd);
                if (mask <= 0.001) discard;

                float inside = max(-sd, 0.0);
                float edgeWidth = clamp(uOptics.y, 3.0, min(size.x, size.y) * 0.34);
                float edgeWide = 1.0 - smoothstep(0.0, edgeWidth, inside);
                float edgeCore = 1.0 - smoothstep(0.0, max(edgeWidth * 0.30, 2.0), inside);
                float edgeDragBand = pow(1.0 - smoothstep(0.0, max(edgeWidth * 1.45, 8.0), inside), 1.35);

                vec2 normal = sdfNormal(coord, size, radius);
                vec2 tangent = vec2(-normal.y, normal.x);
                vec2 centerDir = normalize(coord - size * 0.5 + vec2(0.001));
                vec2 dir = mix(centerDir, normal, edgeWide);

                float bodyPull = uRefraction.x * 0.08 * (1.0 - edgeWide);
                float edgePull = uRefraction.y * edgeWide;
                vec2 offsetPx = dir * (bodyPull + edgePull);
                float limitPx = mix(12.0, 54.0, edgeWide) + sat(abs(uRefraction.y) / 600.0) * 14.0;
                float lenPx = length(offsetPx);
                offsetPx *= (lenPx / (1.0 + lenPx / max(limitPx, 1.0))) / max(lenPx, 0.0001);

                vec2 uv = globalUv(coord + offsetPx);
                vec3 color = blur9(uv, max(uOptics.x, 0.0) * (1.0 + edgeWide * 0.35));

                float lensMix = edgeCore * sat(max(uRefraction.z, 0.0)) * 0.40;
                color = mix(color, sampleLens(uv), lensMix);

                float dragPull = clamp(8.0 + abs(uRefraction.y) * 0.030, 8.0, 42.0);
                float smear = clamp(4.0 + edgeWidth * 0.55, 4.0, 22.0);
                vec2 dragBase = coord - normal * dragPull;
                vec3 drag = sampleLens(globalUv(dragBase)) * 0.32;
                drag += sampleLens(globalUv(dragBase + tangent * smear)) * 0.18;
                drag += sampleLens(globalUv(dragBase - tangent * smear)) * 0.18;
                drag += sampleLens(globalUv(dragBase - normal * dragPull * 0.9)) * 0.20;
                drag += sampleLens(globalUv(dragBase + normal * dragPull * 0.45)) * 0.12;
                float dragAlpha = edgeDragBand * (0.035 + sat(max(uRefraction.z, 0.0)) * 0.105 + edgeCore * 0.030) * colorSignal(drag);
                color = mix(color, drag, sat(dragAlpha));

                color *= uMaterial.z * (1.0 + edgeCore * 0.12);
                color -= vec3(0.06, 0.07, 0.09) * uOptics.w * edgeWide;
                float debug = smoothstep(-1.65, 0.0, sd) * mask;
                color = mix(color, vec3(1.0, 0.45, 0.0), debug * uOptics.z);
                color = clamp(color, 0.0, 1.0);
                gl_FragColor = vec4(color, clamp(uMaterial.x * uMaterial.y, 0.0, 1.0) * mask);
            }
        """
    }
}
