package com.yuchen.ailedger.ui.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import com.yuchen.ailedger.model.GlassBorderStyle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val LEGACY_GLASS_SCISSOR_PADDING_PX = 2

private const val DIRTY_SURFACE = 1
private const val DIRTY_GEOMETRY = 1 shl 1
private const val DIRTY_SAMPLING = 1 shl 2
private const val DIRTY_PRESS = 1 shl 3
private const val DIRTY_STYLE = 1 shl 4
private const val DIRTY_ALL =
    DIRTY_SURFACE or DIRTY_GEOMETRY or DIRTY_SAMPLING or DIRTY_PRESS or DIRTY_STYLE

private const val GEOMETRY_WIDTH = 0
private const val GEOMETRY_HEIGHT = 1
private const val GEOMETRY_OFFSET_Y = 2
private const val GEOMETRY_RADIUS = 3
private const val GEOMETRY_SIZE = 4

private const val SAMPLING_ORIGIN_X = 0
private const val SAMPLING_ORIGIN_Y = 1
private const val SAMPLING_ROOT_WIDTH = 2
private const val SAMPLING_ROOT_HEIGHT = 3
private const val SAMPLING_SIZE = 4

private const val PRESS_PROGRESS = 0
private const val PRESS_CENTER_X = 1
private const val PRESS_CENTER_Y = 2
private const val PRESS_SIZE = 3

private const val STYLE_VISIBILITY = 0
private const val STYLE_MAX_ALPHA = 1
private const val STYLE_EDGE_BRIGHTNESS = 2
private const val STYLE_PULL_SCALE = 3
private const val STYLE_EDGE_PULL_DP = 4
private const val STYLE_COMPRESSION_SCALE = 5
private const val STYLE_CORNER_SCALE = 6
private const val STYLE_SAMPLE_RADIUS = 7
private const val STYLE_RING_WIDTH = 8
private const val STYLE_DEBUG_ALPHA = 9
private const val STYLE_DARK_SCALE = 10
private const val STYLE_SIZE = 11

private data class LegacyGlassScissorRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    fun union(other: LegacyGlassScissorRect): LegacyGlassScissorRect =
        LegacyGlassScissorRect(
            left = min(left, other.left),
            top = min(top, other.top),
            right = max(right, other.right),
            bottom = max(bottom, other.bottom)
        )
}

/**
 * 旧版 OpenGL 玻璃 Renderer。
 *
 * 保持原 Shader、uniform、采样和绘制顺序不变，只优化纹理资源生命周期：
 * - blur / lens 分别按 Bitmap 引用判脏；
 * - 两者引用同一 Bitmap 时只保留一份 GPU texture；
 * - 从双纹理回到单纹理复用时立即释放闲置显存。
 */
internal class LegacyOpenGLGlassRenderer {
    private val textureLock = Any()
    private val specLock = Any()

    private var pendingBlurBitmap: Bitmap? = null
    private var pendingLensBitmap: Bitmap? = null
    private var textureSetPending = false

    private var activeBlurBitmap: Bitmap? = null
    private var activeLensBitmap: Bitmap? = null
    private var lensTextureAliasesBlur = false

    private var blurTextureId = 0
    private var lensTextureId = 0
    private val textureWidths = IntArray(2)
    private val textureHeights = IntArray(2)
    private var texturesReady = false

    private val geometryState = FloatArray(GEOMETRY_SIZE).apply {
        this[GEOMETRY_WIDTH] = 1f
        this[GEOMETRY_HEIGHT] = 1f
        this[GEOMETRY_RADIUS] = 24f
    }
    private val samplingState = FloatArray(SAMPLING_SIZE).apply {
        this[SAMPLING_ROOT_WIDTH] = 1f
        this[SAMPLING_ROOT_HEIGHT] = 1f
    }
    private val pressState = FloatArray(PRESS_SIZE).apply {
        this[PRESS_CENTER_X] = 0.5f
        this[PRESS_CENTER_Y] = 0.5f
    }
    private val styleState = FloatArray(STYLE_SIZE)

    private val geometrySnapshot = FloatArray(GEOMETRY_SIZE)
    private val samplingSnapshot = FloatArray(SAMPLING_SIZE)
    private val pressSnapshot = FloatArray(PRESS_SIZE)
    private val styleSnapshot = FloatArray(STYLE_SIZE)

    private var pendingDirtyMask = DIRTY_ALL

    private var drawCardWidth = 1f
    private var drawCardHeight = 1f
    private var drawRectOffsetY = 0f
    private var partialClearSupported = false
    private var forceFullClear = true
    private var previousGlassScissor: LegacyGlassScissorRect? = null

    private var program = 0
    private var quadBufferId = 0
    private var positionHandle = 0
    private var resolutionHandle = 0
    private var cardOriginHandle = 0
    private var rootResolutionHandle = 0
    private var rectHandle = 0
    private var radiusHandle = 0
    private var pressHandle = 0
    private var textureReadyHandle = 0
    private var blurTextureHandle = 0
    private var lensTextureHandle = 0
    private var materialHandle = 0
    private var refractionHandle = 0
    private var opticsHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1

    init {
        applyStyleValues(GlassBorderStyle())
    }

    fun setPartialClearSupported(supported: Boolean) {
        partialClearSupported = supported
    }

    fun setGlassSpec(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float
    ) {
        synchronized(specLock) {
            geometryState[GEOMETRY_WIDTH] = width.coerceAtLeast(1f)
            geometryState[GEOMETRY_HEIGHT] = height.coerceAtLeast(1f)
            geometryState[GEOMETRY_OFFSET_Y] = rectOffsetY
            geometryState[GEOMETRY_RADIUS] = radius
            pendingDirtyMask = pendingDirtyMask or DIRTY_GEOMETRY
        }
    }

    fun setSamplingSpec(
        originX: Float,
        originY: Float,
        rootWidth: Float,
        rootHeight: Float
    ) {
        synchronized(specLock) {
            samplingState[SAMPLING_ORIGIN_X] = originX
            samplingState[SAMPLING_ORIGIN_Y] = originY
            samplingState[SAMPLING_ROOT_WIDTH] = rootWidth.coerceAtLeast(1f)
            samplingState[SAMPLING_ROOT_HEIGHT] = rootHeight.coerceAtLeast(1f)
            pendingDirtyMask = pendingDirtyMask or DIRTY_SAMPLING
        }
    }

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float) {
        synchronized(specLock) {
            pressState[PRESS_PROGRESS] = progress.coerceIn(0f, 1f)
            pressState[PRESS_CENTER_X] = centerX.coerceIn(0f, 1f)
            pressState[PRESS_CENTER_Y] = centerY.coerceIn(0f, 1f)
            pendingDirtyMask = pendingDirtyMask or DIRTY_PRESS
        }
    }

    fun setBackdropTextures(blurBitmap: Bitmap, lensBitmap: Bitmap) {
        synchronized(textureLock) {
            pendingBlurBitmap = blurBitmap
            pendingLensBitmap = lensBitmap
            textureSetPending = true
        }
    }

    fun setGlassStyle(style: GlassBorderStyle) {
        synchronized(specLock) {
            applyStyleValues(style)
            pendingDirtyMask = pendingDirtyMask or DIRTY_STYLE
        }
    }

    fun onSurfaceCreated() {
        program = buildLegacyGlassProgram(
            LEGACY_GLASS_VERTEX_SHADER,
            LegacyOpenGLGlassShader.FRAGMENT_SHADER
        )
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        cardOriginHandle = GLES20.glGetUniformLocation(program, "uCardOrigin")
        rootResolutionHandle = GLES20.glGetUniformLocation(program, "uRootResolution")
        rectHandle = GLES20.glGetUniformLocation(program, "uRect")
        radiusHandle = GLES20.glGetUniformLocation(program, "uRadius")
        pressHandle = GLES20.glGetUniformLocation(program, "uPress")
        textureReadyHandle = GLES20.glGetUniformLocation(program, "uTextureReady")
        blurTextureHandle = GLES20.glGetUniformLocation(program, "uBlurTexture")
        lensTextureHandle = GLES20.glGetUniformLocation(program, "uLensTexture")
        materialHandle = GLES20.glGetUniformLocation(program, "uMaterial")
        refractionHandle = GLES20.glGetUniformLocation(program, "uRefraction")
        opticsHandle = GLES20.glGetUniformLocation(program, "uOptics")

        GLES20.glUseProgram(program)
        GLES20.glUniform1i(blurTextureHandle, 0)
        GLES20.glUniform1i(lensTextureHandle, 1)
        GLES20.glUniform1f(textureReadyHandle, 0f)

        blurTextureId = createConfiguredTexture(0, GLES20.GL_TEXTURE0)
        lensTextureId = 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        val buffers = IntArray(1)
        GLES20.glGenBuffers(1, buffers, 0)
        quadBufferId = buffers[0]
        val quadVertices = ByteBuffer
            .allocateDirect(LEGACY_FULLSCREEN_QUAD.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(LEGACY_FULLSCREEN_QUAD)
                position(0)
            }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBufferId)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            LEGACY_FULLSCREEN_QUAD.size * 4,
            quadVertices,
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            0
        )

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        forceFullClear = true
        previousGlassScissor = null
        synchronized(specLock) {
            pendingDirtyMask = pendingDirtyMask or DIRTY_ALL
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        forceFullClear = true
        previousGlassScissor = null
        synchronized(specLock) {
            pendingDirtyMask = pendingDirtyMask or DIRTY_SURFACE
        }
    }

    fun onDrawFrame() {
        uploadPendingTexturesIfNeeded()
        if (program == 0) return

        val dirtyMask: Int
        synchronized(specLock) {
            dirtyMask = pendingDirtyMask
            pendingDirtyMask = 0
            if (dirtyMask and DIRTY_GEOMETRY != 0) {
                geometryState.copyInto(geometrySnapshot)
            }
            if (dirtyMask and DIRTY_SAMPLING != 0) {
                samplingState.copyInto(samplingSnapshot)
            }
            if (dirtyMask and DIRTY_PRESS != 0) {
                pressState.copyInto(pressSnapshot)
            }
            if (dirtyMask and DIRTY_STYLE != 0) {
                styleState.copyInto(styleSnapshot)
            }
        }

        if (dirtyMask and DIRTY_SURFACE != 0) {
            GLES20.glUniform2f(
                resolutionHandle,
                viewportWidth.toFloat(),
                viewportHeight.toFloat()
            )
        }
        if (dirtyMask and DIRTY_GEOMETRY != 0) {
            drawCardWidth = geometrySnapshot[GEOMETRY_WIDTH]
            drawCardHeight = geometrySnapshot[GEOMETRY_HEIGHT]
            drawRectOffsetY = geometrySnapshot[GEOMETRY_OFFSET_Y]
            GLES20.glUniform4f(
                rectHandle,
                0f,
                drawRectOffsetY,
                drawCardWidth,
                drawCardHeight
            )
            GLES20.glUniform1f(
                radiusHandle,
                geometrySnapshot[GEOMETRY_RADIUS]
                    .coerceIn(2f, max(drawCardWidth, drawCardHeight))
            )
        }
        if (dirtyMask and DIRTY_SAMPLING != 0) {
            GLES20.glUniform2f(
                cardOriginHandle,
                samplingSnapshot[SAMPLING_ORIGIN_X],
                samplingSnapshot[SAMPLING_ORIGIN_Y]
            )
            GLES20.glUniform2f(
                rootResolutionHandle,
                samplingSnapshot[SAMPLING_ROOT_WIDTH],
                samplingSnapshot[SAMPLING_ROOT_HEIGHT]
            )
        }
        if (dirtyMask and DIRTY_PRESS != 0) {
            GLES20.glUniform4f(
                pressHandle,
                pressSnapshot[PRESS_PROGRESS],
                pressSnapshot[PRESS_CENTER_X],
                pressSnapshot[PRESS_CENTER_Y],
                0f
            )
        }
        if (dirtyMask and DIRTY_STYLE != 0) {
            uploadStyleUniforms()
        }

        val currentScissor = calculateGlassScissor()
        clearDirtyRegion(currentScissor)
        if (currentScissor == null) {
            previousGlassScissor = null
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
            return
        }

        applyScissor(currentScissor)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        previousGlassScissor = currentScissor
    }

    fun onRelease() {
        deleteTexture(blurTextureId)
        deleteTexture(lensTextureId)
        if (quadBufferId != 0) {
            GLES20.glDeleteBuffers(1, intArrayOf(quadBufferId), 0)
        }
        if (program != 0) GLES20.glDeleteProgram(program)
        blurTextureId = 0
        lensTextureId = 0
        quadBufferId = 0
        program = 0
        texturesReady = false
        lensTextureAliasesBlur = false
        activeBlurBitmap = null
        activeLensBitmap = null
        synchronized(textureLock) {
            pendingBlurBitmap = null
            pendingLensBitmap = null
            textureSetPending = false
        }
        previousGlassScissor = null
    }

    private fun uploadStyleUniforms() {
        GLES20.glUniform4f(
            materialHandle,
            styleSnapshot[STYLE_VISIBILITY],
            styleSnapshot[STYLE_MAX_ALPHA],
            styleSnapshot[STYLE_EDGE_BRIGHTNESS],
            0f
        )
        GLES20.glUniform4f(
            refractionHandle,
            styleSnapshot[STYLE_PULL_SCALE],
            styleSnapshot[STYLE_EDGE_PULL_DP],
            styleSnapshot[STYLE_COMPRESSION_SCALE],
            styleSnapshot[STYLE_CORNER_SCALE]
        )
        GLES20.glUniform4f(
            opticsHandle,
            styleSnapshot[STYLE_SAMPLE_RADIUS],
            styleSnapshot[STYLE_RING_WIDTH],
            styleSnapshot[STYLE_DEBUG_ALPHA],
            styleSnapshot[STYLE_DARK_SCALE]
        )
    }

    private fun applyStyleValues(style: GlassBorderStyle) {
        styleState[STYLE_VISIBILITY] = style.openGlVisibility.coerceIn(0f, 20f)
        styleState[STYLE_MAX_ALPHA] = style.openGlMaxAlpha.coerceIn(0f, 1f)
        styleState[STYLE_EDGE_BRIGHTNESS] = style.edgeBrightness.coerceIn(-5f, 5f)
        styleState[STYLE_PULL_SCALE] = style.openGlPullScale.coerceIn(-300f, 300f)
        styleState[STYLE_EDGE_PULL_DP] = style.edgePullDp.coerceIn(-600f, 600f)
        styleState[STYLE_COMPRESSION_SCALE] =
            style.openGlCompressionScale.coerceIn(-10f, 10f)
        styleState[STYLE_CORNER_SCALE] = style.openGlCornerScale.coerceIn(0f, 200f)
        styleState[STYLE_SAMPLE_RADIUS] =
            style.openGlSampleRadiusScale.coerceIn(0f, 200f)
        styleState[STYLE_RING_WIDTH] = style.ringWidthDp.coerceIn(0f, 300f)
        styleState[STYLE_DEBUG_ALPHA] =
            style.openGlDebugLineAlpha.coerceIn(0f, 1f)
        styleState[STYLE_DARK_SCALE] = style.openGlDarkScale.coerceIn(-10f, 10f)
    }

    private fun calculateGlassScissor(): LegacyGlassScissorRect? {
        val left = 0
        val top = (
            floor(drawRectOffsetY.toDouble()).toInt() -
                LEGACY_GLASS_SCISSOR_PADDING_PX
            ).coerceIn(0, viewportHeight)
        val right = ceil(
            (drawCardWidth + LEGACY_GLASS_SCISSOR_PADDING_PX).toDouble()
        ).toInt().coerceIn(0, viewportWidth)
        val bottom = ceil(
            (
                drawRectOffsetY + drawCardHeight +
                    LEGACY_GLASS_SCISSOR_PADDING_PX
                ).toDouble()
        ).toInt().coerceIn(0, viewportHeight)
        return if (right > left && bottom > top) {
            LegacyGlassScissorRect(left, top, right, bottom)
        } else {
            null
        }
    }

    private fun clearDirtyRegion(current: LegacyGlassScissorRect?) {
        if (!partialClearSupported || forceFullClear) {
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            forceFullClear = false
            return
        }

        val dirty = when {
            previousGlassScissor == null -> current
            current == null -> previousGlassScissor
            else -> previousGlassScissor!!.union(current)
        } ?: return
        applyScissor(dirty)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    private fun applyScissor(rect: LegacyGlassScissorRect) {
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(
            rect.left,
            viewportHeight - rect.bottom,
            rect.width,
            rect.height
        )
    }

    private fun uploadPendingTexturesIfNeeded() {
        val blurBitmap: Bitmap
        val lensBitmap: Bitmap
        synchronized(textureLock) {
            if (!textureSetPending) return
            blurBitmap = pendingBlurBitmap ?: return
            lensBitmap = pendingLensBitmap ?: return
            pendingBlurBitmap = null
            pendingLensBitmap = null
            textureSetPending = false
        }

        if (blurBitmap !== activeBlurBitmap) {
            uploadTexture(
                index = 0,
                textureUnit = GLES20.GL_TEXTURE0,
                textureId = blurTextureId,
                bitmap = blurBitmap
            )
            activeBlurBitmap = blurBitmap
        }

        val aliasLensToBlur = lensBitmap === blurBitmap
        if (aliasLensToBlur) {
            if (!lensTextureAliasesBlur || lensTextureId != 0) {
                deleteTexture(lensTextureId)
                lensTextureId = 0
                textureWidths[1] = 0
                textureHeights[1] = 0
                bindTexture(GLES20.GL_TEXTURE1, blurTextureId)
                lensTextureAliasesBlur = true
            }
            activeLensBitmap = lensBitmap
        } else {
            if (lensTextureId == 0) {
                lensTextureId = createConfiguredTexture(1, GLES20.GL_TEXTURE1)
            } else if (lensTextureAliasesBlur) {
                bindTexture(GLES20.GL_TEXTURE1, lensTextureId)
            }
            lensTextureAliasesBlur = false
            if (lensBitmap !== activeLensBitmap) {
                uploadTexture(
                    index = 1,
                    textureUnit = GLES20.GL_TEXTURE1,
                    textureId = lensTextureId,
                    bitmap = lensBitmap
                )
                activeLensBitmap = lensBitmap
            }
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (!texturesReady) {
            texturesReady = true
            GLES20.glUniform1f(textureReadyHandle, 1f)
        }
    }

    private fun uploadTexture(
        index: Int,
        textureUnit: Int,
        textureId: Int,
        bitmap: Bitmap
    ) {
        bindTexture(textureUnit, textureId)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        if (
            textureWidths[index] == bitmap.width &&
            textureHeights[index] == bitmap.height
        ) {
            GLUtils.texSubImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                0,
                0,
                bitmap
            )
        } else {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            textureWidths[index] = bitmap.width
            textureHeights[index] = bitmap.height
        }
    }

    private fun createConfiguredTexture(index: Int, textureUnit: Int): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureId = textures[0]
        bindTexture(textureUnit, textureId)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        textureWidths[index] = 0
        textureHeights[index] = 0
        return textureId
    }

    private fun bindTexture(textureUnit: Int, textureId: Int) {
        GLES20.glActiveTexture(textureUnit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    }

    private fun deleteTexture(textureId: Int) {
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
    }
}

private fun buildLegacyGlassProgram(vertex: String, fragment: String): Int {
    fun compileShader(type: Int, source: String): Int {
        val shaderId = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shaderId, source)
        GLES20.glCompileShader(shaderId)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) {
            GLES20.glGetShaderInfoLog(shaderId)
        }
        return shaderId
    }

    val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertex)
    val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment)
    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vertexShader)
    GLES20.glAttachShader(program, fragmentShader)
    GLES20.glLinkProgram(program)
    val status = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
    GLES20.glDeleteShader(vertexShader)
    GLES20.glDeleteShader(fragmentShader)
    check(status[0] != 0) {
        GLES20.glGetProgramInfoLog(program)
    }
    return program
}

private val LEGACY_FULLSCREEN_QUAD = floatArrayOf(
    -1f, -1f,
    1f, -1f,
    -1f, 1f,
    1f, 1f
)

private const val LEGACY_GLASS_VERTEX_SHADER = """
    attribute vec2 aPosition;
    void main(){ gl_Position=vec4(aPosition,0.0,1.0); }
"""
