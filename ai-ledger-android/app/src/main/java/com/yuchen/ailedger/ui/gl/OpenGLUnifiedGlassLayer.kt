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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.LocalBackdropFrameTicker
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassBackdrop
import com.yuchen.ailedger.ui.LocalGlassItemRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MAX_GLASS_ITEMS = 32
private const val GEOMETRY_PREDICT_FACTOR = 0.58f
private const val GEOMETRY_PREDICT_MAX_PX = 30f
private const val GEOMETRY_PREDICT_RESET_PX = 260f

@Composable
fun OpenGLUnifiedGlassLayer(modifier: Modifier = Modifier) {
    val backdrop = LocalBlurredBackdrop.current ?: return
    val registry = LocalGlassItemRegistry.current ?: return
    val border = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
    val ticker = LocalBackdropFrameTicker.current
    val density = LocalDensity.current
    val frameNanos = ticker?.frameNanos ?: 0L
    val blurBitmap = backdrop.image.asAndroidBitmap()
    val lensBitmap = backdrop.lensImage.asAndroidBitmap()
    val rects = FloatArray(MAX_GLASS_ITEMS * 4)
    val itemParams = FloatArray(MAX_GLASS_ITEMS * 4)
    var itemCount = 0
    registry.snapshot().forEach { item ->
        if (itemCount >= MAX_GLASS_ITEMS || !item.coordinates.isAttached()) return@forEach
        val itemSize = item.coordinates.itemSize()
        if (itemSize.width <= 0 || itemSize.height <= 0) return@forEach
        val topLeft = item.coordinates.rootOffset()
        val i4 = itemCount * 4
        rects[i4] = topLeft.x
        rects[i4 + 1] = topLeft.y
        rects[i4 + 2] = itemSize.width.toFloat()
        rects[i4 + 3] = itemSize.height.toFloat()
        itemParams[i4] = with(density) { item.radius.dp.toPx() }
        itemParams[i4 + 1] = item.backdropAlpha
        itemParams[i4 + 2] = item.edgeStrength
        itemParams[i4 + 3] = item.glassIntensity
        itemCount++
    }

    AndroidView(
        modifier = modifier.onSizeChanged { },
        factory = { OpenGLUnifiedGlassTextureView(it) },
        update = { view ->
            val dirtyTextures = view.setBackdropTextures(blurBitmap, lensBitmap)
            val dirtyStyle = view.setGlassStyle(border)
            val dirtyItems = view.setGlassItems(itemCount, rects, itemParams, backdrop.fullWidthPx.toFloat(), backdrop.fullHeightPx.toFloat())
            if (dirtyTextures || dirtyStyle || dirtyItems || frameNanos >= 0L) view.requestRender()
        }
    )
}

private class OpenGLUnifiedGlassTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var thread: UnifiedGlassEglThread? = null
    private var blur: Bitmap? = null
    private var lens: Bitmap? = null
    private var style = GlassBorderStyle()
    private var itemCount = 0
    private var rects = FloatArray(MAX_GLASS_ITEMS * 4)
    private var rawRects = FloatArray(MAX_GLASS_ITEMS * 4)
    private var itemParams = FloatArray(MAX_GLASS_ITEMS * 4)
    private var hasRawRects = false
    private var rootW = 1f
    private var rootH = 1f

    init {
        isOpaque = false
        alpha = 1f
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
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

    fun setGlassItems(count: Int, nextRects: FloatArray, nextParams: FloatArray, rootWidth: Float, rootHeight: Float): Boolean {
        val safeCount = count.coerceIn(0, MAX_GLASS_ITEMS)
        val predicted = FloatArray(MAX_GLASS_ITEMS * 4)
        val used = safeCount * 4
        for (itemIndex in 0 until safeCount) {
            val i4 = itemIndex * 4
            val x = nextRects[i4]
            val y = nextRects[i4 + 1]
            val w = nextRects[i4 + 2]
            val h = nextRects[i4 + 3]
            var px = x
            var py = y
            if (hasRawRects && itemIndex < itemCount) {
                val dx = x - rawRects[i4]
                val dy = y - rawRects[i4 + 1]
                if (abs(dx) < GEOMETRY_PREDICT_RESET_PX && abs(dy) < GEOMETRY_PREDICT_RESET_PX) {
                    px = x + dx.coerceIn(-GEOMETRY_PREDICT_MAX_PX, GEOMETRY_PREDICT_MAX_PX) * GEOMETRY_PREDICT_FACTOR
                    py = y + dy.coerceIn(-GEOMETRY_PREDICT_MAX_PX, GEOMETRY_PREDICT_MAX_PX) * GEOMETRY_PREDICT_FACTOR
                }
            }
            predicted[i4] = px
            predicted[i4 + 1] = py
            predicted[i4 + 2] = w
            predicted[i4 + 3] = h
        }

        var dirty = safeCount != itemCount || abs(rootWidth - rootW) > 0.5f || abs(rootHeight - rootH) > 0.5f
        for (i in 0 until used) {
            if (abs(predicted[i] - rects[i]) > 0.05f || abs(nextParams[i] - itemParams[i]) > 0.05f) {
                dirty = true
                break
            }
        }
        itemCount = safeCount
        rootW = rootWidth.coerceAtLeast(1f)
        rootH = rootHeight.coerceAtLeast(1f)
        System.arraycopy(predicted, 0, rects, 0, rects.size)
        System.arraycopy(nextRects, 0, rawRects, 0, rawRects.size)
        System.arraycopy(nextParams, 0, itemParams, 0, itemParams.size)
        hasRawRects = safeCount > 0
        thread?.setGlassItems(itemCount, rects, itemParams, rootW, rootH)
        return dirty
    }

    fun requestRender() = thread?.requestRender() ?: Unit

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        thread?.shutdown()
        thread = UnifiedGlassEglThread(Surface(surfaceTexture), width, height).also {
            it.setGlassStyle(style)
            it.setGlassItems(itemCount, rects, itemParams, rootW, rootH)
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

private class UnifiedGlassEglThread(private val surface: Surface, width: Int, height: Int) : Thread("OpenGLUnifiedGlassThread") {
    private val renderer = OpenGLUnifiedGlassRenderer()
    private val lock = Object()
    @Volatile private var running = true
    @Volatile private var pending = true
    @Volatile private var viewportW = max(width, 1)
    @Volatile private var viewportH = max(height, 1)
    @Volatile private var sizeDirty = true
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun setBackdropTextures(blur: Bitmap, lens: Bitmap) = renderer.setBackdropTextures(blur, lens)
    fun setGlassStyle(style: GlassBorderStyle) = renderer.setGlassStyle(style)
    fun setGlassItems(count: Int, rects: FloatArray, params: FloatArray, rootW: Float, rootH: Float) = renderer.setGlassItems(count, rects, params, rootW, rootH)

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
        check(display != EGL14.EGL_NO_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1))
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
        check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, configs.size, count, 0))
        val config = configs[0] ?: error("No EGL config")
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT)
        eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context))
    }

    private fun releaseEgl() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
}

private class OpenGLUnifiedGlassRenderer {
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
    private var itemCount = 0
    private val rects = FloatArray(MAX_GLASS_ITEMS * 4)
    private val itemParams = FloatArray(MAX_GLASS_ITEMS * 4)
    private var rootW = 1f
    private var rootH = 1f
    private var style = GlassBorderStyle()
    private var program = 0
    private var viewportW = 1
    private var viewportH = 1
    private var aPosition = 0
    private var uResolution = 0
    private var uRootResolution = 0
    private var uItemCount = 0
    private var uRects = 0
    private var uItemParams = 0
    private var uMaterial = 0
    private var uRefraction = 0
    private var uOptics = 0
    private var uLegacy = 0
    private var uExtra = 0
    private var uReady = 0
    private var uBlur = 0
    private var uLens = 0

    fun setBackdropTextures(blur: Bitmap, lens: Bitmap) { synchronized(textureLock) { pendingBlur = blur; pendingLens = lens } }
    fun setGlassStyle(next: GlassBorderStyle) { style = next }
    fun setGlassItems(count: Int, nextRects: FloatArray, nextParams: FloatArray, rootWidth: Float, rootHeight: Float) {
        itemCount = count.coerceIn(0, MAX_GLASS_ITEMS)
        System.arraycopy(nextRects, 0, rects, 0, rects.size)
        System.arraycopy(nextParams, 0, itemParams, 0, itemParams.size)
        rootW = rootWidth.coerceAtLeast(1f)
        rootH = rootHeight.coerceAtLeast(1f)
    }

    fun onSurfaceCreated() {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        uResolution = GLES20.glGetUniformLocation(program, "uResolution")
        uRootResolution = GLES20.glGetUniformLocation(program, "uRootResolution")
        uItemCount = GLES20.glGetUniformLocation(program, "uItemCount")
        uRects = GLES20.glGetUniformLocation(program, "uRects")
        uItemParams = GLES20.glGetUniformLocation(program, "uItemParams")
        uMaterial = GLES20.glGetUniformLocation(program, "uMaterial")
        uRefraction = GLES20.glGetUniformLocation(program, "uRefraction")
        uOptics = GLES20.glGetUniformLocation(program, "uOptics")
        uLegacy = GLES20.glGetUniformLocation(program, "uLegacy")
        uExtra = GLES20.glGetUniformLocation(program, "uExtra")
        uReady = GLES20.glGetUniformLocation(program, "uTextureReady")
        uBlur = GLES20.glGetUniformLocation(program, "uBlurTexture")
        uLens = GLES20.glGetUniformLocation(program, "uLensTexture")
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
        GLES20.glUniform2f(uResolution, viewportW.toFloat(), viewportH.toFloat())
        GLES20.glUniform2f(uRootResolution, rootW, rootH)
        GLES20.glUniform1i(uItemCount, itemCount)
        GLES20.glUniform4fv(uRects, MAX_GLASS_ITEMS, rects, 0)
        GLES20.glUniform4fv(uItemParams, MAX_GLASS_ITEMS, itemParams, 0)
        GLES20.glUniform4f(uMaterial, style.openGlVisibility.coerceIn(0f, 20f), style.openGlMaxAlpha.coerceIn(0f, 1f), style.edgeBrightness.coerceIn(-5f, 5f), style.bodyAlpha.coerceIn(-5f, 5f))
        GLES20.glUniform4f(uRefraction, style.openGlPullScale.coerceIn(-1200f, 1200f), style.edgePullDp.coerceIn(-2400f, 2400f), style.openGlCompressionScale.coerceIn(-10f, 10f), style.openGlCornerScale.coerceIn(0f, 800f))
        GLES20.glUniform4f(uOptics, style.openGlSampleRadiusScale.coerceIn(0f, 600f), style.ringWidthDp.coerceIn(0f, 900f), style.openGlDebugLineAlpha.coerceIn(0f, 1f), style.openGlDarkScale.coerceIn(-12f, 12f))
        GLES20.glUniform4f(uLegacy, style.edgeAlpha.coerceIn(0f, 3f), style.edgeBlurDp.coerceIn(0f, 600f), style.edgeContrast.coerceIn(0f, 8f), style.edgeSaturation.coerceIn(0f, 8f))
        GLES20.glUniform4f(uExtra, style.openGlEdgeWidthScale.coerceIn(-10f, 10f), style.openGlSpecularScale.coerceIn(-10f, 10f), style.openGlChromaticScale.coerceIn(-10f, 10f), style.bottomShadowAlpha.coerceIn(0f, 2f))
        GLES20.glUniform1f(uReady, if (ready) 1f else 0f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTex)
        GLES20.glUniform1i(uBlur, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lensTex)
        GLES20.glUniform1i(uLens, 1)
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
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
            error("OpenGL unified glass link failed: $log")
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
            error("OpenGL unified glass shader failed: $log")
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
            const int MAX_ITEMS = 32;
            uniform vec2 uResolution;
            uniform vec2 uRootResolution;
            uniform int uItemCount;
            uniform vec4 uRects[MAX_ITEMS];
            uniform vec4 uItemParams[MAX_ITEMS];
            uniform vec4 uMaterial;
            uniform vec4 uRefraction;
            uniform vec4 uOptics;
            uniform vec4 uLegacy;
            uniform vec4 uExtra;
            uniform float uTextureReady;
            uniform sampler2D uBlurTexture;
            uniform sampler2D uLensTexture;

            float sat(float x) { return clamp(x, 0.0, 1.0); }
            vec2 safeUv(vec2 uv) { return clamp(uv, 0.0, 1.0); }
            float roundedBoxSdf(vec2 coord, vec2 size, float radius) {
                vec2 p = coord - size * 0.5;
                vec2 q = abs(p) - max(size * 0.5 - vec2(radius), vec2(0.0));
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }
            vec3 fallbackBackdrop(vec2 uv) { return mix(vec3(0.04, 0.08, 0.22), vec3(0.52, 0.23, 0.45), smoothstep(0.0, 1.0, uv.y)); }
            vec3 sampleBlur(vec2 uv) { return mix(fallbackBackdrop(uv), texture2D(uBlurTexture, safeUv(uv)).rgb, sat(uTextureReady)); }
            vec3 sampleLens(vec2 uv) { return mix(fallbackBackdrop(uv), texture2D(uLensTexture, safeUv(uv)).rgb, sat(uTextureReady)); }
            vec2 sdfNormal(vec2 coord, vec2 size, float radius) {
                float d = 1.25;
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
            vec3 adjustColor(vec3 c) {
                float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
                c = mix(vec3(luma), c, max(uLegacy.w, 0.0));
                c = (c - vec3(0.5)) * max(uLegacy.z, 0.0) + vec3(0.5);
                return clamp(c * uMaterial.z, 0.0, 1.0);
            }
            vec3 blur9(vec2 uv, float px) {
                vec2 p = vec2(px) / max(uRootResolution, vec2(1.0));
                vec3 c = sampleBlur(uv) * 0.22;
                c += sampleBlur(uv + vec2(p.x, 0.0)) * 0.11;
                c += sampleBlur(uv - vec2(p.x, 0.0)) * 0.11;
                c += sampleBlur(uv + vec2(0.0, p.y)) * 0.11;
                c += sampleBlur(uv - vec2(0.0, p.y)) * 0.11;
                c += sampleBlur(uv + p) * 0.085;
                c += sampleBlur(uv - p) * 0.085;
                c += sampleBlur(uv + vec2(p.x, -p.y)) * 0.085;
                c += sampleBlur(uv + vec2(-p.x, p.y)) * 0.085;
                return c;
            }
            vec4 evalItem(vec2 coord, vec4 rect, vec4 itemParam) {
                vec2 local = coord - rect.xy;
                vec2 size = max(rect.zw, vec2(1.0));
                float radius = min(itemParam.x, min(size.x, size.y) * 0.5);
                float sd = roundedBoxSdf(local, size, radius);
                float mask = 1.0 - smoothstep(0.0, 1.35, sd);
                if (mask <= 0.001) return vec4(0.0);
                float inside = max(-sd, 0.0);
                float baseEdgeWidth = max(uOptics.y + uLegacy.y * 0.20, 0.0);
                float edgeWidth = clamp(baseEdgeWidth * max(0.1, 1.0 + uExtra.x), 2.0, min(size.x, size.y) * 0.42);
                float edgeWide = 1.0 - smoothstep(0.0, edgeWidth, inside);
                float edgeCore = 1.0 - smoothstep(0.0, max(edgeWidth * 0.30, 2.0), inside);
                float edgeDragBand = pow(1.0 - smoothstep(0.0, max(edgeWidth * 1.45, 8.0), inside), 1.35);
                vec2 normal = sdfNormal(local, size, radius);
                vec2 tangent = vec2(-normal.y, normal.x);
                vec2 centerDir = normalize(local - size * 0.5 + vec2(0.001));
                vec2 dir = mix(centerDir, normal, edgeWide);
                float bodyPull = uRefraction.x * 0.08 * (1.0 - edgeWide);
                float edgePull = uRefraction.y * edgeWide;
                vec2 offsetPx = dir * (bodyPull + edgePull);
                float limitPx = mix(12.0, 54.0, edgeWide) + sat(abs(uRefraction.y) / 600.0) * 14.0;
                float lenPx = length(offsetPx);
                offsetPx *= (lenPx / (1.0 + lenPx / max(limitPx, 1.0))) / max(lenPx, 0.0001);
                vec2 uv = safeUv((coord + offsetPx) / max(uRootResolution, vec2(1.0)));
                vec3 color = blur9(uv, max(uOptics.x, 0.0) * (1.0 + edgeWide * 0.35));
                float lensMix = edgeCore * sat(max(uRefraction.z, 0.0)) * 0.40;
                color = mix(color, sampleLens(uv), lensMix);
                float dragPull = clamp(8.0 + abs(uRefraction.y) * 0.030, 8.0, 42.0);
                float smear = clamp(4.0 + edgeWidth * 0.55, 4.0, 22.0);
                vec2 dragBase = coord - normal * dragPull;
                vec3 drag = sampleLens(safeUv(dragBase / max(uRootResolution, vec2(1.0)))) * 0.32;
                drag += sampleLens(safeUv((dragBase + tangent * smear) / max(uRootResolution, vec2(1.0)))) * 0.18;
                drag += sampleLens(safeUv((dragBase - tangent * smear) / max(uRootResolution, vec2(1.0)))) * 0.18;
                drag += sampleLens(safeUv((dragBase - normal * dragPull * 0.9) / max(uRootResolution, vec2(1.0)))) * 0.20;
                drag += sampleLens(safeUv((dragBase + normal * dragPull * 0.45) / max(uRootResolution, vec2(1.0)))) * 0.12;
                float dragAlpha = edgeDragBand * (0.035 + sat(max(uRefraction.z, 0.0)) * 0.105 + edgeCore * 0.030 + uLegacy.x * 0.035) * colorSignal(drag);
                color = mix(color, drag, sat(dragAlpha));
                float top = smoothstep(size.y, 0.0, local.y) * edgeWide;
                float bottom = smoothstep(0.0, size.y, local.y) * edgeWide;
                color += vec3(top * uExtra.y * 0.04);
                color -= vec3(bottom * uExtra.w * 0.05);
                color -= vec3(0.06, 0.07, 0.09) * uOptics.w * edgeWide;
                color = adjustColor(color);
                float debug = smoothstep(-1.65, 0.0, sd) * mask;
                color = mix(color, vec3(1.0, 0.45, 0.0), debug * uOptics.z);
                float alpha = clamp(uMaterial.x * uMaterial.y * itemParam.y * itemParam.w, 0.0, 1.0) * mask;
                return vec4(color, alpha);
            }
            void main() {
                vec2 coord = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);
                vec4 acc = vec4(0.0);
                for (int i = 0; i < MAX_ITEMS; i++) {
                    if (i >= uItemCount) break;
                    vec4 layer = evalItem(coord, uRects[i], uItemParams[i]);
                    acc.rgb = layer.rgb * layer.a + acc.rgb * (1.0 - layer.a);
                    acc.a = layer.a + acc.a * (1.0 - layer.a);
                }
                gl_FragColor = acc;
            }
        """
    }
}
