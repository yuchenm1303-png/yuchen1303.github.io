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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.LocalBackdropFrameTicker
import com.yuchen.ailedger.ui.LocalBackdropOrigin
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class DropletGlassStyle(
    val bodyBulgePx: Float = 44f,
    val edgePullPx: Float = 46f,
    val edgeWidthPx: Float = 9f,
    val lensMix: Float = 0.92f,
    val dragStrength: Float = 0.36f,
    val bottomGlow: Float = 0.32f,
    val topGloss: Float = 0.22f,
    val cornerGloss: Float = 0.30f,
    val innerDark: Float = 0.18f,
    val alpha: Float = 0.72f,
    val debugMaskAlpha: Float = 0f,
    val activeGlow: Float = 0.62f,
    val accentRed: Float = 0.52f,
    val accentGreen: Float = 0.78f,
    val accentBlue: Float = 1.00f,
    val warmRed: Float = 1.00f,
    val warmGreen: Float = 0.45f,
    val warmBlue: Float = 0.78f
)

data class DropletDebugMetrics(
    val composeW: Int = 0,
    val composeH: Int = 0,
    val viewW: Int = 0,
    val viewH: Int = 0,
    val renderW: Int = 0,
    val renderH: Int = 0,
    val surfaceW: Int = 0,
    val surfaceH: Int = 0,
    val eglW: Int = 0,
    val eglH: Int = 0,
    val radiusPx: Int = 0
) {
    fun label(): String = "C ${composeW}×${composeH}\nV ${viewW}×${viewH}\nR ${renderW}×${renderH}\nS ${surfaceW}×${surfaceH}\nE ${eglW}×${eglH}\nr $radiusPx"
}

@Composable
fun OpenGLDropletGlassLayer(
    radius: Int,
    coordinateSource: GlassCoordinateSource? = null,
    style: DropletGlassStyle = DropletGlassStyle(),
    modifier: Modifier = Modifier
) {
    val backdrop = LocalBlurredBackdrop.current ?: return
    val origin = LocalBackdropOrigin.current
    val ticker = LocalBackdropFrameTicker.current
    val density = LocalDensity.current
    val frameNanos = ticker?.frameNanos ?: 0L
    val blurBitmap = backdrop.image.asAndroidBitmap()
    val lensBitmap = backdrop.lensImage.asAndroidBitmap()
    val cardOrigin = coordinateSource?.offsetRelativeTo(origin) ?: Offset.Zero
    val radiusPx = with(density) { radius.dp.toPx() }.roundToInt().toFloat()
    var renderSize by remember { mutableStateOf(IntSize.Zero) }
    var debugMetrics by remember { mutableStateOf(DropletDebugMetrics()) }
    val showDebugMetrics = style.debugMaskAlpha > 0.001f

    Box(modifier = modifier.onSizeChanged { size -> if (size.width > 0 && size.height > 0 && size != renderSize) renderSize = size }) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { OpenGLDropletTextureView(it) },
            update = { view ->
                view.noteComposeFrame(frameNanos)
                val w = renderSize.width.takeIf { it > 0 } ?: view.width.takeIf { it > 0 } ?: 1
                val h = renderSize.height.takeIf { it > 0 } ?: view.height.takeIf { it > 0 } ?: 1
                view.setDebugSink(w, h) { debugMetrics = it }
                val dirtyA = view.bindBounds(w, h, radiusPx)
                val dirtyB = view.setSampling(cardOrigin.x, cardOrigin.y, backdrop.fullWidthPx.toFloat(), backdrop.fullHeightPx.toFloat())
                val dirtyC = view.setTextures(blurBitmap, lensBitmap)
                val dirtyD = view.setStyle(style)
                if (dirtyA || dirtyB || dirtyC || dirtyD) view.requestRender()
            }
        )
        if (showDebugMetrics) {
            Text(
                text = debugMetrics.label(),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(7.dp)).padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

private class OpenGLDropletTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var thread: DropletEglThread? = null
    private var blur: Bitmap? = null
    private var lens: Bitmap? = null
    private var renderW = 1
    private var renderH = 1
    private var radiusPx = 24f
    private var originX = 0f
    private var originY = 0f
    private var rootW = 1f
    private var rootH = 1f
    private var style = DropletGlassStyle()
    private var composeW = 0
    private var composeH = 0
    private var surfaceW = 0
    private var surfaceH = 0
    private var eglW = 0
    private var eglH = 0
    private var lastDebugMetrics = DropletDebugMetrics()
    private var onDebugMetrics: ((DropletDebugMetrics) -> Unit)? = null

    init {
        isOpaque = false
        alpha = 1f
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun noteComposeFrame(frameNanos: Long) = Unit

    fun setDebugSink(width: Int, height: Int, callback: ((DropletDebugMetrics) -> Unit)?) {
        composeW = width.coerceAtLeast(0)
        composeH = height.coerceAtLeast(0)
        onDebugMetrics = callback
        reportDebugMetrics()
    }

    fun bindBounds(width: Int, height: Int, radius: Float): Boolean {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val dirty = w != renderW || h != renderH || abs(radius - radiusPx) > 0.5f
        renderW = w
        renderH = h
        radiusPx = radius
        if (dirty) {
            surfaceTexture?.setDefaultBufferSize(renderW, renderH)
            thread?.setBounds(renderW, renderH, radiusPx)
            reportDebugMetrics()
        }
        return dirty
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) bindBounds(w, h, radiusPx)
        reportDebugMetrics()
    }

    fun setSampling(x: Float, y: Float, rw: Float, rh: Float): Boolean {
        val dirty = abs(x - originX) > 0.05f || abs(y - originY) > 0.05f || abs(rw - rootW) > 0.5f || abs(rh - rootH) > 0.5f
        originX = x
        originY = y
        rootW = rw.coerceAtLeast(1f)
        rootH = rh.coerceAtLeast(1f)
        if (dirty) thread?.setSampling(originX, originY, rootW, rootH)
        return dirty
    }

    fun setTextures(blurBitmap: Bitmap, lensBitmap: Bitmap): Boolean {
        val dirty = blurBitmap !== blur || lensBitmap !== lens
        blur = blurBitmap
        lens = lensBitmap
        if (dirty) thread?.setTextures(blurBitmap, lensBitmap)
        return dirty
    }

    fun setStyle(next: DropletGlassStyle): Boolean {
        val dirty = next != style
        style = next
        if (dirty) thread?.setStyle(next)
        return dirty
    }

    fun requestRender() = thread?.requestRender() ?: Unit

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        thread?.shutdown()
        surfaceW = width.coerceAtLeast(1)
        surfaceH = height.coerceAtLeast(1)
        val w = renderW.takeIf { it > 1 } ?: surfaceW
        val h = renderH.takeIf { it > 1 } ?: surfaceH
        surfaceTexture.setDefaultBufferSize(w, h)
        reportDebugMetrics()
        thread = DropletEglThread(Surface(surfaceTexture), w, h) { ew, eh -> post { eglW = ew; eglH = eh; reportDebugMetrics() } }.also {
            it.setBounds(w, h, radiusPx)
            it.setSampling(originX, originY, rootW, rootH)
            it.setStyle(style)
            val b = blur
            val l = lens
            if (b != null && l != null) it.setTextures(b, l)
            it.start()
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        surfaceW = width.coerceAtLeast(1)
        surfaceH = height.coerceAtLeast(1)
        val w = renderW.takeIf { it > 1 } ?: surfaceW
        val h = renderH.takeIf { it > 1 } ?: surfaceH
        surfaceTexture.setDefaultBufferSize(w, h)
        thread?.setBounds(w, h, radiusPx)
        reportDebugMetrics()
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        thread?.shutdown()
        thread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

    private fun reportDebugMetrics() {
        val metrics = DropletDebugMetrics(composeW, composeH, width, height, renderW, renderH, surfaceW, surfaceH, eglW, eglH, radiusPx.roundToInt())
        if (metrics != lastDebugMetrics) {
            lastDebugMetrics = metrics
            post { onDebugMetrics?.invoke(metrics) }
        }
    }
}

private class DropletEglThread(
    private val surface: Surface,
    width: Int,
    height: Int,
    private val onEglSurfaceSize: ((Int, Int) -> Unit)? = null
) : Thread("OpenGLDropletGlassThread") {
    private val renderer = DropletRenderer()
    private val lock = Object()
    @Volatile private var running = true
    @Volatile private var pending = true
    @Volatile private var viewportW = max(width, 1)
    @Volatile private var viewportH = max(height, 1)
    @Volatile private var sizeDirty = true
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun setBounds(w: Int, h: Int, radius: Float) {
        viewportW = w.coerceAtLeast(1)
        viewportH = h.coerceAtLeast(1)
        renderer.setBounds(viewportW, viewportH, radius)
        sizeDirty = true
        requestRender()
    }

    fun setSampling(x: Float, y: Float, rw: Float, rh: Float) = renderer.setSampling(x, y, rw, rh)
    fun setTextures(blur: Bitmap, lens: Bitmap) = renderer.setTextures(blur, lens)
    fun setStyle(style: DropletGlassStyle) = renderer.setStyle(style)

    fun requestRender() { synchronized(lock) { pending = true; lock.notifyAll() } }
    fun shutdown() { running = false; requestRender() }

    override fun run() {
        try {
            initEgl()
            renderer.onSurfaceCreated()
            renderer.onSurfaceChanged(viewportW, viewportH)
            notifyEglSurfaceSize()
            sizeDirty = false
            while (running) {
                synchronized(lock) { while (!pending && running) lock.wait(); pending = false }
                if (!running) break
                if (sizeDirty) { renderer.onSurfaceChanged(viewportW, viewportH); notifyEglSurfaceSize(); sizeDirty = false }
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
        val attrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_STENCIL_SIZE, 0, EGL14.EGL_NONE)
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, configs.size, count, 0)) { "Unable to choose EGL config" }
        val config = configs[0] ?: error("No EGL config found")
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }
        eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL window surface" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "Unable to make EGL current" }
        notifyEglSurfaceSize()
    }

    private fun notifyEglSurfaceSize() {
        if (display == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return
        val w = IntArray(1)
        val h = IntArray(1)
        val okW = EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_WIDTH, w, 0)
        val okH = EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_HEIGHT, h, 0)
        if (okW && okH) onEglSurfaceSize?.invoke(w[0], h[0])
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

private class DropletRenderer {
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(8 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }
    private val textureLock = Any()
    private var pendingBlur: Bitmap? = null
    private var pendingLens: Bitmap? = null
    private var activeBlur: Bitmap? = null
    private var activeLens: Bitmap? = null
    private var blurTex = 0
    private var lensTex = 0
    private var ready = false
    private var renderW = 1
    private var renderH = 1
    private var radius = 24f
    private var originX = 0f
    private var originY = 0f
    private var rootW = 1f
    private var rootH = 1f
    private var style = DropletGlassStyle()
    private var program = 0
    private var positionHandle = 0
    private var sizeHandle = 0
    private var originHandle = 0
    private var rootHandle = 0
    private var radiusHandle = 0
    private var readyHandle = 0
    private var shapeHandle = 0
    private var lightHandle = 0
    private var alphaHandle = 0
    private var selectedHandle = 0
    private var accentHandle = 0
    private var warmHandle = 0
    private var blurHandle = 0
    private var lensHandle = 0

    fun setBounds(w: Int, h: Int, r: Float) { renderW = w.coerceAtLeast(1); renderH = h.coerceAtLeast(1); radius = r }
    fun setSampling(x: Float, y: Float, rw: Float, rh: Float) { originX = x; originY = y; rootW = rw.coerceAtLeast(1f); rootH = rh.coerceAtLeast(1f) }
    fun setTextures(blur: Bitmap, lens: Bitmap) { synchronized(textureLock) { pendingBlur = blur; pendingLens = lens } }
    fun setStyle(s: DropletGlassStyle) { style = s }

    fun onSurfaceCreated() {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        sizeHandle = GLES20.glGetUniformLocation(program, "uSize")
        originHandle = GLES20.glGetUniformLocation(program, "uCardOrigin")
        rootHandle = GLES20.glGetUniformLocation(program, "uRootResolution")
        radiusHandle = GLES20.glGetUniformLocation(program, "uRadius")
        readyHandle = GLES20.glGetUniformLocation(program, "uTextureReady")
        shapeHandle = GLES20.glGetUniformLocation(program, "uShape")
        lightHandle = GLES20.glGetUniformLocation(program, "uLight")
        alphaHandle = GLES20.glGetUniformLocation(program, "uAlpha")
        selectedHandle = GLES20.glGetUniformLocation(program, "uSelected")
        accentHandle = GLES20.glGetUniformLocation(program, "uAccentColor")
        warmHandle = GLES20.glGetUniformLocation(program, "uWarmColor")
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

    fun onSurfaceChanged(w: Int, h: Int) { GLES20.glViewport(0, 0, w.coerceAtLeast(1), h.coerceAtLeast(1)) }

    fun onDrawFrame() {
        uploadPendingTextures()
        GLES20.glViewport(0, 0, renderW, renderH)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        val w = renderW.toFloat()
        val h = renderH.toFloat()
        val r = if (w > h * 1.35f && radius > h * 0.5f) h * 0.5f else radius.coerceIn(2f, max(w, h))
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(sizeHandle, w, h)
        GLES20.glUniform2f(originHandle, originX, originY)
        GLES20.glUniform2f(rootHandle, rootW, rootH)
        GLES20.glUniform1f(radiusHandle, r)
        GLES20.glUniform1f(readyHandle, if (ready) 1f else 0f)
        GLES20.glUniform4f(shapeHandle, style.bodyBulgePx.coerceIn(-80f, 120f), style.edgePullPx.coerceIn(-160f, 180f), style.edgeWidthPx.coerceIn(2f, 72f), style.lensMix.coerceIn(0f, 1f))
        GLES20.glUniform4f(lightHandle, style.dragStrength.coerceIn(0f, 2.5f), style.bottomGlow.coerceIn(0f, 2.5f), style.topGloss.coerceIn(0f, 2.5f), style.cornerGloss.coerceIn(0f, 2.5f))
        GLES20.glUniform4f(alphaHandle, style.innerDark.coerceIn(0f, 1.5f), style.alpha.coerceIn(0f, 1f), style.debugMaskAlpha.coerceIn(0f, 1f), 0f)
        GLES20.glUniform1f(selectedHandle, style.activeGlow.coerceIn(0f, 2f))
        GLES20.glUniform3f(accentHandle, style.accentRed.coerceIn(0f, 1f), style.accentGreen.coerceIn(0f, 1f), style.accentBlue.coerceIn(0f, 1f))
        GLES20.glUniform3f(warmHandle, style.warmRed.coerceIn(0f, 1f), style.warmGreen.coerceIn(0f, 1f), style.warmBlue.coerceIn(0f, 1f))
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

    fun onRelease() { if (blurTex != 0 || lensTex != 0) GLES20.glDeleteTextures(2, intArrayOf(blurTex, lensTex), 0) }

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
        if (ok[0] == 0) { val log = GLES20.glGetProgramInfoLog(p); GLES20.glDeleteProgram(p); error("OpenGL droplet program link failed: $log") }
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
        if (ok[0] == 0) { val log = GLES20.glGetShaderInfoLog(s); GLES20.glDeleteShader(s); error("OpenGL droplet shader compile failed: $log") }
        return s
    }

    private companion object {
        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() { gl_Position = vec4(aPosition, 0.0, 1.0); }
        """

        const val FRAGMENT_SHADER = """
            precision highp float;
            uniform vec2 uSize;
            uniform vec2 uCardOrigin;
            uniform vec2 uRootResolution;
            uniform float uRadius;
            uniform float uTextureReady;
            uniform vec4 uShape;
            uniform vec4 uLight;
            uniform vec4 uAlpha;
            uniform float uSelected;
            uniform vec3 uAccentColor;
            uniform vec3 uWarmColor;
            uniform sampler2D uBlurTexture;
            uniform sampler2D uLensTexture;
            float sat(float x) { return clamp(x, 0.0, 1.0); }
            float capsuleSdf(vec2 coord, vec2 size, float radius) {
                vec2 c1 = vec2(radius, size.y * 0.5);
                vec2 c2 = vec2(size.x - radius, size.y * 0.5);
                vec2 pa = coord - c1;
                vec2 ba = c2 - c1;
                float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.001), 0.0, 1.0);
                return length(pa - ba * h) - radius;
            }
            vec2 globalUv(vec2 coord) { return clamp((uCardOrigin + coord) / max(uRootResolution, vec2(1.0)), 0.0, 1.0); }
            vec3 fallback(vec2 uv) { return mix(vec3(0.04, 0.12, 0.24), vec3(0.12, 0.36, 0.42), smoothstep(0.0, 1.0, uv.y)); }
            vec3 sampleBlur(vec2 uv) { return mix(fallback(uv), texture2D(uBlurTexture, uv).rgb, sat(uTextureReady)); }
            vec3 sampleLens(vec2 uv) { return mix(fallback(uv), texture2D(uLensTexture, uv).rgb, sat(uTextureReady)); }
            float signal(vec3 c) {
                float luma = dot(c, vec3(0.299, 0.587, 0.114));
                float chroma = length(c - vec3(luma));
                return sat((luma - 0.16) * 1.55 + chroma * 1.65);
            }
            void main() {
                vec2 coord = vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y);
                vec2 size = max(uSize, vec2(1.0));
                float radius = min(uRadius, size.y * 0.5);
                float sd = capsuleSdf(coord, size, radius);
                float mask = 1.0 - smoothstep(0.0, 1.35, sd);
                if (mask <= 0.001) discard;
                if (uAlpha.z > 0.001) {
                    vec3 debugColor = mix(vec3(0.0, 0.95, 1.0), vec3(1.0, 0.25, 0.95), coord.x / size.x);
                    gl_FragColor = vec4(debugColor, uAlpha.z * mask);
                    return;
                }
                vec2 center = size * 0.5;
                float halfLine = max(size.x * 0.5 - radius, 0.0);
                vec2 spine = vec2(clamp(coord.x, center.x - halfLine, center.x + halfLine), center.y);
                vec2 local = coord - spine;
                vec2 wholeLocal = coord - center;
                float distToSpine = length(local);
                float rNorm = sat(distToSpine / max(radius, 1.0));
                float inside = max(radius - distToSpine, 0.0);
                vec2 normal = local / max(distToSpine, 0.001);
                vec2 tangent = vec2(-normal.y, normal.x);
                float thickness = sqrt(max(0.0, 1.0 - rNorm * rNorm));
                float rim = pow(rNorm, 1.85);
                float edgeWidth = clamp(uShape.z, 2.0, size.y * 0.48);
                float edge = 1.0 - smoothstep(0.0, edgeWidth, inside);
                float wideRim = 1.0 - smoothstep(0.0, max(edgeWidth * 2.4, 9.0), inside);
                float lensStrength = sat(abs(uShape.x) / 72.0);
                vec2 centerField = -vec2(wholeLocal.x * 0.12, wholeLocal.y * 0.58) * lensStrength * thickness * (1.0 - edge * 0.15);
                vec2 magnifyOffset = -local * lensStrength * (0.38 + 0.22 * thickness) * (1.0 - edge * 0.20);
                vec2 softHorizontal = vec2(-(coord.x - center.x) * lensStrength * 0.06, 0.0) * thickness;
                vec2 rimOffset = normal * uShape.y * edge * (0.18 + 0.82 * rim);
                vec2 edgeCompression = -normal * abs(uShape.y) * 0.16 * wideRim * (1.0 - thickness);
                vec2 offsetPx = centerField + magnifyOffset + softHorizontal + rimOffset + edgeCompression;
                float lenPx = length(offsetPx);
                offsetPx *= (lenPx / (1.0 + lenPx / 74.0)) / max(lenPx, 0.0001);
                vec2 uv = globalUv(coord + offsetPx);
                vec3 sharp = sampleLens(uv);
                vec3 soft = sampleBlur(uv);
                vec3 color = mix(soft, sharp, sat(0.72 + uShape.w * 0.28));
                float smear = clamp(5.0 + edgeWidth * 0.78, 4.0, 22.0);
                vec2 dragBase = coord - normal * clamp(6.0 + abs(uShape.y) * 0.12, 5.0, 30.0);
                vec3 drag = sampleLens(globalUv(dragBase)) * 0.36;
                drag += sampleLens(globalUv(dragBase + tangent * smear)) * 0.22;
                drag += sampleLens(globalUv(dragBase - tangent * smear)) * 0.22;
                drag += sampleLens(globalUv(dragBase - normal * 10.0)) * 0.20;
                color = mix(color, drag, sat(wideRim * uLight.x * signal(drag) * 0.28));
                float y = coord.y / max(size.y, 1.0);
                float x = coord.x / max(size.x, 1.0);
                float topFacing = sat(-normal.y);
                float bottomFacing = sat(normal.y);
                vec3 specColor = mix(vec3(1.0), vec3(0.84, 0.78, 1.0), 0.28);
                float topLine = topFacing * edge * smoothstep(0.02, 0.18, y) * (1.0 - smoothstep(0.24, 0.56, y));
                color += specColor * topLine * uLight.z * 0.45;
                color += specColor * pow(sat(dot(normal, normalize(vec2(0.62, -0.78)))), 5.0) * edge * uLight.w * 0.48;
                float bottomBand = bottomFacing * (0.20 + 0.80 * wideRim) * smoothstep(0.46, 1.0, y);
                vec3 warm = mix(drag, vec3(1.0, 0.34, 0.70), 0.20);
                color = mix(color, warm, sat(bottomBand * uLight.y * 0.24));
                color += vec3(1.0, 0.42, 0.76) * bottomBand * signal(drag) * uLight.y * 0.06;
                float active = sat(uSelected);
                vec3 accentColor = clamp(uAccentColor, 0.0, 1.0);
                vec3 rimColor = mix(vec3(1.0), accentColor, 0.36);
                vec3 warmColor = clamp(uWarmColor, 0.0, 1.0);
                vec2 warmDelta = (coord - vec2(size.x * 0.50, size.y * 1.05)) / vec2(size.x * 0.42, size.y * 0.56);
                float warmBlob = pow(sat(1.0 - dot(warmDelta, warmDelta)), 1.65) * smoothstep(0.30, 1.0, y);
                float causticLine = pow(sat(1.0 - abs(y - 0.78) * 5.0), 2.0) * smoothstep(0.10, 0.48, x) * (1.0 - smoothstep(0.94, 1.0, x));
                float edgeGlow = wideRim * (0.20 + 0.80 * signal(drag)) * (0.28 + 0.72 * rim);
                float cornerHotspot = pow(sat(dot(normal, normalize(vec2(0.70, -0.72)))), 7.0) * edge;
                color += warmColor * active * (warmBlob * (0.18 + 0.82 * thickness) + causticLine * (0.22 + 0.78 * bottomFacing)) * 0.58;
                color += accentColor * active * edgeGlow * 0.22;
                color += rimColor * active * topLine * (0.32 + 0.68 * thickness) * 0.30;
                color += mix(warmColor, vec3(1.0), 0.36) * active * cornerHotspot * 0.28;
                color = mix(color, mix(color, sampleLens(globalUv(coord - normal * 14.0)), 0.26), active * warmBlob * 0.16);
                float rimShadow = (wideRim * 0.38 + rim * 0.14) * (0.42 + 0.58 * sat(dot(normal, normalize(vec2(0.36, 0.94)))));
                float bottomShadow = bottomFacing * smoothstep(0.36, 1.0, y) * (0.28 + 0.72 * wideRim);
                color -= vec3(0.055, 0.065, 0.10) * (rimShadow + bottomShadow * 0.45) * uAlpha.x;
                gl_FragColor = vec4(clamp(color, 0.0, 1.0), mask);
            }
        """
    }
}
