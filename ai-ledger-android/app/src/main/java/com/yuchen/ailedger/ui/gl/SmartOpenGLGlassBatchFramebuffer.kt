package com.yuchen.ailedger.ui.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * 批量 Shell 的持久化离屏画布。
 *
 * 单卡脏区更新始终写入同一张 FBO 纹理，因此完整批画面不再依赖 EGL/TextureView
 * 后备缓冲是否恰好保留上一帧。提交到窗口时只做一次全屏纹理复制，保证每次 swap
 * 都包含全部卡片，同时保留单卡玻璃 shader 的局部重绘收益。
 */
internal class SmartOpenGLGlassBatchFramebuffer {
    private var framebuffer = 0
    private var colorTexture = 0
    private var presentProgram = 0
    private var positionHandle = -1
    private var textureCoordinateHandle = -1
    private var textureHandle = -1
    private var width = 1
    private var height = 1
    private var ready = false

    private val quadBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(FULLSCREEN_QUAD.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(FULLSCREEN_QUAD)
            position(0)
        }

    fun onSurfaceCreated(width: Int, height: Int): Boolean {
        presentProgram = buildProgram(PRESENT_VERTEX_SHADER, PRESENT_FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(presentProgram, "aPosition")
        textureCoordinateHandle = GLES20.glGetAttribLocation(presentProgram, "aTextureCoordinate")
        textureHandle = GLES20.glGetUniformLocation(presentProgram, "uTexture")
        return resize(width, height)
    }

    fun resize(width: Int, height: Int): Boolean {
        val safeWidth = max(width, 1)
        val safeHeight = max(height, 1)
        if (ready && this.width == safeWidth && this.height == safeHeight) return true

        releaseTarget()
        this.width = safeWidth
        this.height = safeHeight

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        colorTexture = textures[0]
        GLES20.glActiveTexture(PRESENT_TEXTURE_UNIT)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colorTexture)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_NEAREST,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            safeWidth,
            safeHeight,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null,
        )

        val framebuffers = IntArray(1)
        GLES20.glGenFramebuffers(1, framebuffers, 0)
        framebuffer = framebuffers[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            colorTexture,
            0,
        )
        ready = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) ==
            GLES20.GL_FRAMEBUFFER_COMPLETE

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (!ready) releaseTarget()
        return ready
    }

    fun bindForRender() {
        GLES20.glBindFramebuffer(
            GLES20.GL_FRAMEBUFFER,
            if (ready) framebuffer else 0,
        )
        GLES20.glViewport(0, 0, width, height)
    }

    fun presentToWindow() {
        if (!ready || presentProgram == 0 || colorTexture == 0) return

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DITHER)
        GLES20.glUseProgram(presentProgram)

        GLES20.glActiveTexture(PRESENT_TEXTURE_UNIT)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colorTexture)
        GLES20.glUniform1i(textureHandle, PRESENT_TEXTURE_INDEX)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        quadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            FULLSCREEN_VERTEX_STRIDE_BYTES,
            quadBuffer,
        )
        quadBuffer.position(2)
        GLES20.glEnableVertexAttribArray(textureCoordinateHandle)
        GLES20.glVertexAttribPointer(
            textureCoordinateHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            FULLSCREEN_VERTEX_STRIDE_BYTES,
            quadBuffer,
        )
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureCoordinateHandle)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glEnable(GLES20.GL_DITHER)
    }

    fun onRelease() {
        releaseTarget()
        if (presentProgram != 0) GLES20.glDeleteProgram(presentProgram)
        presentProgram = 0
        positionHandle = -1
        textureCoordinateHandle = -1
        textureHandle = -1
    }

    private fun releaseTarget() {
        if (framebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        }
        if (colorTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(colorTexture), 0)
        }
        framebuffer = 0
        colorTexture = 0
        ready = false
    }

    private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] != 0) { GLES20.glGetShaderInfoLog(shader) }
            return shader
        }

        val vertexShader = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        check(status[0] != 0) { GLES20.glGetProgramInfoLog(program) }
        return program
    }

    private companion object {
        private const val PRESENT_TEXTURE_INDEX = 4
        private val PRESENT_TEXTURE_UNIT = GLES20.GL_TEXTURE0 + PRESENT_TEXTURE_INDEX
        private const val FULLSCREEN_VERTEX_STRIDE_BYTES = 4 * 4

        private val FULLSCREEN_QUAD = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )

        private const val PRESENT_VERTEX_SHADER = """
            precision highp float;
            attribute vec2 aPosition;
            attribute vec2 aTextureCoordinate;
            varying vec2 vTextureCoordinate;

            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTextureCoordinate = aTextureCoordinate;
            }
        """

        private const val PRESENT_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTextureCoordinate;

            void main() {
                gl_FragColor = texture2D(uTexture, vTextureCoordinate);
            }
        """
    }
}
