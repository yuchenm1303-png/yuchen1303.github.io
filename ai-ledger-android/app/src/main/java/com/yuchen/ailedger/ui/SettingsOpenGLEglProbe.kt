package com.yuchen.ailedger.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

internal object SettingsOpenGLProbeState {
    @Volatile var status: String = "等待 Surface"
    @Volatile var swapCount: Long = 0L

    fun reset() {
        status = "等待 Surface"
        swapCount = 0L
    }
}

/**
 * 左侧是普通 Compose 纯色，右侧是真正由 EGL/GLES 清屏并 swap 的 TextureView。
 * 仅用于设置页诊断，不接入玻璃 registry、geometry sync 或生产 Renderer。
 */
@Composable
internal fun SettingsOpenGLCompositionProbe(
    modifier: Modifier = Modifier,
) {
    val probeKey = remember { Any() }
    DisposableEffect(probeKey) {
        SettingsOpenGLProbeState.reset()
        onDispose { }
    }

    Column(modifier = modifier) {
        Text(
            text = "纯色探针：左 Compose 洋红 · 右 EGL 青绿",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.padding(top = 5.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(Color(0xFFFF00A8), RoundedCornerShape(7.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(7.dp))
                )
                Text("Compose", color = Color.White, fontSize = 8.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(52.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(7.dp))
                ) {
                    AndroidView(
                        modifier = Modifier.matchParentSize(),
                        factory = { context -> SettingsOpenGLEglProbeTextureView(context) },
                    )
                }
                Text("EGL TextureView", color = Color.White, fontSize = 8.sp)
            }
        }
    }
}

private class SettingsOpenGLEglProbeTextureView(
    context: Context,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: SettingsOpenGLProbeThread? = null

    init {
        isOpaque = false
        alpha = 1f
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        renderThread?.shutdown()
        renderThread = SettingsOpenGLProbeThread(
            surface = Surface(surfaceTexture),
            width = width,
            height = height,
        ).also { it.start() }
    }

    override fun onSurfaceTextureSizeChanged(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        renderThread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        renderThread?.shutdown()
        renderThread = null
        SettingsOpenGLProbeState.status = "Surface 已销毁"
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
}

private class SettingsOpenGLProbeThread(
    private val surface: Surface,
    width: Int,
    height: Int,
) : Thread("SettingsOpenGLEglProbe") {
    @Volatile private var running = true
    @Volatile private var viewportWidth = width.coerceAtLeast(1)
    @Volatile private var viewportHeight = height.coerceAtLeast(1)

    fun resize(width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
    }

    fun shutdown() {
        running = false
        interrupt()
    }

    override fun run() {
        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        try {
            SettingsOpenGLProbeState.status = "初始化 EGL"
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "无 EGL display" }

            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize 失败" }

            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val configAttributes = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE,
            )
            check(
                EGL14.eglChooseConfig(
                    display,
                    configAttributes,
                    0,
                    configs,
                    0,
                    configs.size,
                    count,
                    0,
                ) && count[0] > 0
            ) { "无 EGL config" }
            val config = configs[0] ?: error("EGL config 为空")

            context = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0,
            )
            check(context != EGL14.EGL_NO_CONTEXT) { "创建 Context 失败" }

            eglSurface = EGL14.eglCreateWindowSurface(
                display,
                config,
                surface,
                intArrayOf(EGL14.EGL_NONE),
                0,
            )
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "创建 WindowSurface 失败" }
            check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
                "eglMakeCurrent 失败"
            }

            SettingsOpenGLProbeState.status = "EGL 已就绪"
            while (running) {
                GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
                GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
                GLES20.glClearColor(0f, 1f, 0.55f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                if (!EGL14.eglSwapBuffers(display, eglSurface)) {
                    SettingsOpenGLProbeState.status =
                        "swap 失败 0x${EGL14.eglGetError().toString(16)}"
                    break
                }
                SettingsOpenGLProbeState.swapCount += 1L
                SettingsOpenGLProbeState.status = "纯色帧已提交"
                try {
                    sleep(250L)
                } catch (_: InterruptedException) {
                    if (!running) break
                }
            }
        } catch (error: Throwable) {
            if (running) {
                val detail = error.message.orEmpty().take(42)
                SettingsOpenGLProbeState.status =
                    "异常 ${error.javaClass.simpleName}${if (detail.isBlank()) "" else ": $detail"}"
            }
        } finally {
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
            surface.release()
        }
    }
}
