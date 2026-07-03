package com.yuchen.ailedger.ui.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface
import android.view.TextureView
import com.yuchen.ailedger.ui.PerformanceRuntimeMetrics
import com.yuchen.ailedger.ui.StartupPerformanceGate
import kotlin.math.abs
import kotlin.math.max

private const val SMART_BATCH_EGL_SWAP_BEHAVIOR = 0x3093
private const val SMART_BATCH_EGL_BUFFER_PRESERVED = 0x3094
private const val SMART_BATCH_EGL_PRESERVED_BIT = 0x0400

internal class SmartOpenGLGlassBatchTextureView(
    context: Context,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var thread: SmartOpenGLGlassBatchEglThread? = null
    private val latestPacket = UnifiedGlassBatchPacket()
    private var clear: BatchPlatformBitmap? = null
    private var low: BatchPlatformBitmap? = null
    private var medium: BatchPlatformBitmap? = null
    private var high: BatchPlatformBitmap? = null
    private var blurAmount = 0f

    init {
        isOpaque = false
        alpha = 1f
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setPacket(packet: UnifiedGlassBatchPacket) {
        latestPacket.copyFrom(packet)
        thread?.setPacket(packet)
    }

    fun setBackdropTextures(
        clear: BatchPlatformBitmap,
        low: BatchPlatformBitmap,
        medium: BatchPlatformBitmap,
        high: BatchPlatformBitmap,
    ): Boolean {
        val changed = clear !== this.clear ||
            low !== this.low ||
            medium !== this.medium ||
            high !== this.high
        this.clear = clear
        this.low = low
        this.medium = medium
        this.high = high
        if (changed) {
            PerformanceRuntimeMetrics.recordOpenGlTextureUpload(clear.width, clear.height)
            PerformanceRuntimeMetrics.recordOpenGlTextureUpload(low.width, low.height)
            PerformanceRuntimeMetrics.recordOpenGlTextureUpload(medium.width, medium.height)
            if (high !== medium) {
                PerformanceRuntimeMetrics.recordOpenGlTextureUpload(high.width, high.height)
            }
            thread?.setBackdropTextures(clear, low, medium, high)
        }
        return changed
    }

    fun setBackdropBlurAmount(amount: Float): Boolean {
        val safe = amount.coerceIn(0f, 4f)
        if (abs(safe - blurAmount) <= 0.002f) return false
        blurAmount = safe
        thread?.setBackdropBlurAmount(safe)
        return true
    }

    fun requestRender() = thread?.requestRender() ?: Unit

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        thread?.shutdown()
        thread = SmartOpenGLGlassBatchEglThread(
            surface = Surface(surfaceTexture),
            width = width,
            height = height,
            onFirstFrame = StartupPerformanceGate::markOpenGlFirstFrameReady,
        ).also { next ->
            next.setPacket(latestPacket)
            next.setBackdropBlurAmount(blurAmount)
            val clear = clear
            val low = low
            val medium = medium
            val high = high
            if (clear != null && low != null && medium != null && high != null) {
                next.setBackdropTextures(clear, low, medium, high)
            }
            next.start()
        }
    }

    override fun onSurfaceTextureSizeChanged(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        thread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        thread?.shutdown()
        thread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
}

private class SmartOpenGLGlassBatchEglThread(
    private val surface: Surface,
    width: Int,
    height: Int,
    private val onFirstFrame: () -> Unit,
) : Thread("SmartOpenGLGlassBatchThread") {
    private val renderer = SmartOpenGLGlassBatchRenderer()
    private val renderLock = Object()

    @Volatile private var running = true
    @Volatile private var pendingRender = true
    @Volatile private var viewportWidth = max(width, 1)
    @Volatile private var viewportHeight = max(height, 1)
    @Volatile private var sizeDirty = true

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var firstFramePresented = false
    private var metricsContextActive = false

    fun setPacket(packet: UnifiedGlassBatchPacket) = renderer.setPacket(packet)

    fun setBackdropTextures(
        clear: BatchPlatformBitmap,
        low: BatchPlatformBitmap,
        medium: BatchPlatformBitmap,
        high: BatchPlatformBitmap,
    ) = renderer.setBackdropTextures(clear, low, medium, high)

    fun setBackdropBlurAmount(amount: Float) = renderer.setBackdropBlurAmount(amount)

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
                    PerformanceRuntimeMetrics.recordOpenGlSurface(viewportWidth, viewportHeight)
                    sizeDirty = false
                }
                renderer.onDrawFrame()
                if (EGL14.eglSwapBuffers(display, eglSurface)) {
                    PerformanceRuntimeMetrics.recordOpenGlFrame()
                    if (!firstFramePresented) {
                        firstFramePresented = true
                        onFirstFrame()
                    }
                }
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
        val preservedConfig = chooseConfig(EGL14.EGL_WINDOW_BIT or SMART_BATCH_EGL_PRESERVED_BIT)
        val config = preservedConfig ?: chooseConfig(EGL14.EGL_WINDOW_BIT) ?: error("No EGL config")
        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0,
        )
        check(context != EGL14.EGL_NO_CONTEXT)
        eglSurface = EGL14.eglCreateWindowSurface(
            display,
            config,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0,
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context))
        metricsContextActive = true
        PerformanceRuntimeMetrics.recordOpenGlContextCreated()

        val preserved = preservedConfig != null && EGL14.eglSurfaceAttrib(
            display,
            eglSurface,
            SMART_BATCH_EGL_SWAP_BEHAVIOR,
            SMART_BATCH_EGL_BUFFER_PRESERVED,
        )
        renderer.setBufferPreserved(preserved)
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
            display,
            attributes,
            0,
            configs,
            0,
            1,
            count,
            0,
        )
        return if (success && count[0] > 0) configs[0] else null
    }

    private fun releaseEgl() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, eglSurface)
            }
            if (context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context)
            }
            EGL14.eglTerminate(display)
        }
        if (metricsContextActive) {
            metricsContextActive = false
            PerformanceRuntimeMetrics.recordOpenGlContextReleased()
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}
