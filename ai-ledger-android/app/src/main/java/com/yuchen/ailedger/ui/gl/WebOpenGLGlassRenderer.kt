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

private const val WEB_GLASS_SCISSOR_PADDING_PX = 2

private const val DIRTY_SURFACE = 1
private const val DIRTY_GEOMETRY = 1 shl 1
private const val DIRTY_SAMPLING = 1 shl 2
private const val DIRTY_PRESS = 1 shl 3
private const val DIRTY_BLUR = 1 shl 4
private const val DIRTY_STYLE = 1 shl 5
private const val DIRTY_ALL =
    DIRTY_SURFACE or DIRTY_GEOMETRY or DIRTY_SAMPLING or DIRTY_PRESS or DIRTY_BLUR or DIRTY_STYLE

private const val GEOMETRY_WIDTH = 0
private const val GEOMETRY_HEIGHT = 1
private const val GEOMETRY_OFFSET_Y = 2
private const val GEOMETRY_RADIUS = 3
private const val GEOMETRY_INTENSITY = 4
private const val GEOMETRY_SIZE = 5

private const val SAMPLING_ORIGIN_X = 0
private const val SAMPLING_ORIGIN_Y = 1
private const val SAMPLING_ROOT_WIDTH = 2
private const val SAMPLING_ROOT_HEIGHT = 3
private const val SAMPLING_SIZE = 4

private const val PRESS_PROGRESS = 0
private const val PRESS_CENTER_X = 1
private const val PRESS_CENTER_Y = 2
private const val PRESS_SIZE = 3

private const val STYLE_MATERIAL_X = 0
private const val STYLE_MATERIAL_Y = 1
private const val STYLE_MATERIAL_Z = 2
private const val STYLE_BODY_LENS_A_X = 3
private const val STYLE_BODY_LENS_A_Y = 4
private const val STYLE_BODY_LENS_A_Z = 5
private const val STYLE_BODY_LENS_B_X = 6
private const val STYLE_BODY_LENS_B_Y = 7
private const val STYLE_BODY_LENS_B_Z = 8
private const val STYLE_BODY_LENS_B_W = 9
private const val STYLE_BODY_X = 10
private const val STYLE_BODY_Y = 11
private const val STYLE_BODY_Z = 12
private const val STYLE_BODY_W = 13
private const val STYLE_SHOULDER_WIDTH = 14
private const val STYLE_SHOULDER_CAPTURE = 15
private const val STYLE_SHOULDER_ANGLE = 16
private const val STYLE_SHOULDER_FALLOFF = 17
private const val STYLE_SHOULDER_MATERIAL = 18
private const val STYLE_SHOULDER_FLOW = 19
private const val STYLE_DISPERSION_STRENGTH = 20
private const val STYLE_DISPERSION_DISTANCE = 21
private const val STYLE_DISPERSION_EDGE_WIDTH = 22
private const val STYLE_DISPERSION_CONCENTRATION = 23
private const val STYLE_SIZE = 24

private data class GlassScissorRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    fun union(other: GlassScissorRect): GlassScissorRect = GlassScissorRect(
        left = min(left, other.left),
        top = min(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom)
    )
}

/**
 * 单卡 Shell Renderer：只在状态脏时更新 uniform，并在纹理引用真正变化时上传。
 * high 级纹理按需创建；两级模糊模式直接绑定 medium texture，并释放曾经分配的
 * high 像素存储，保证切换模糊层级后不残留无效显存。
 */
internal class WebOpenGLGlassRenderer {
    private val textureLock = Any()
    private val specLock = Any()

    private var pendingClearBitmap: Bitmap? = null
    private var pendingBlurLowBitmap: Bitmap? = null
    private var pendingBlurMediumBitmap: Bitmap? = null
    private var pendingBlurHighBitmap: Bitmap? = null
    private var textureSetPending = false

    private var activeClearBitmap: Bitmap? = null
    private var activeBlurLowBitmap: Bitmap? = null
    private var activeBlurMediumBitmap: Bitmap? = null
    private var activeBlurHighBitmap: Bitmap? = null
    private var highTextureAliasesMedium = false

    private var clearTextureId = 0
    private var blurLowTextureId = 0
    private var blurMediumTextureId = 0
    private var blurHighTextureId = 0
    private val textureWidths = IntArray(4)
    private val textureHeights = IntArray(4)
    private var textureReady = false

    private val geometryState = FloatArray(GEOMETRY_SIZE).apply {
        this[GEOMETRY_WIDTH] = 1f
        this[GEOMETRY_HEIGHT] = 1f
        this[GEOMETRY_RADIUS] = 24f
        this[GEOMETRY_INTENSITY] = 1f
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
    private var blurAmount = 0f
    private var blurSnapshot = 0f

    private var drawCardWidth = 1f
    private var drawCardHeight = 1f
    private var drawRectOffsetY = 0f
    private var partialClearSupported = false
    private var forceFullClear = true
    private var previousGlassScissor: GlassScissorRect? = null

    private var program = 0
    private var quadBufferId = 0
    private var positionHandle = 0
    private var resolutionHandle = 0
    private var cardOriginHandle = 0
    private var rootResolutionHandle = 0
    private var rectHandle = 0
    private var radiusHandle = 0
    private var intensityHandle = 0
    private var pressHandle = 0
    private var textureReadyHandle = 0
    private var blurAmountHandle = 0
    private var clearTextureHandle = 0
    private var blurLowTextureHandle = 0
    private var blurMediumTextureHandle = 0
    private var blurHighTextureHandle = 0
    private var materialHandle = 0
    private var bodyLensAHandle = 0
    private var bodyLensBHandle = 0
    private var bodyHandle = 0
    private var shoulderHandle = 0
    private var shoulderFlowHandle = 0
    private var dispersionHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1

    init {
        applyStyleValues(GlassBorderStyle(), 1f)
    }

    fun setPartialClearSupported(supported: Boolean) {
        partialClearSupported = supported
    }

    fun setGlassSpec(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float,
        intensity: Float
    ) {
        synchronized(specLock) {
            geometryState[GEOMETRY_WIDTH] = width.coerceAtLeast(1f)
            geometryState[GEOMETRY_HEIGHT] = height.coerceAtLeast(1f)
            geometryState[GEOMETRY_OFFSET_Y] = rectOffsetY
            geometryState[GEOMETRY_RADIUS] = radius
            geometryState[GEOMETRY_INTENSITY] = intensity
            pendingDirtyMask = pendingDirtyMask or DIRTY_GEOMETRY
        }
    }

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float) {
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

    fun setBackdropTextures(clear: Bitmap, low: Bitmap, medium: Bitmap, high: Bitmap) {
        synchronized(textureLock) {
            pendingClearBitmap = clear
            pendingBlurLowBitmap = low
            pendingBlurMediumBitmap = medium
            pendingBlurHighBitmap = high
            textureSetPending = true
        }
    }

    fun setBackdropBlurAmount(amount: Float) {
        synchronized(specLock) {
            blurAmount = amount.coerceIn(0f, 4f)
            pendingDirtyMask = pendingDirtyMask or DIRTY_BLUR
        }
    }

    fun setGlassStyle(style: GlassBorderStyle, densityScale: Float) {
        synchronized(specLock) {
            applyStyleValues(style, densityScale.coerceAtLeast(0.1f))
            pendingDirtyMask = pendingDirtyMask or DIRTY_STYLE
        }
    }

    fun onSurfaceCreated() {
        program = buildProgram(WEB_VERTEX_SHADER, WebOpenGLGlassShaders.FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        cardOriginHandle = GLES20.glGetUniformLocation(program, "uCardOrigin")
        rootResolutionHandle = GLES20.glGetUniformLocation(program, "uRootResolution")
        rectHandle = GLES20.glGetUniformLocation(program, "uRect")
        radiusHandle = GLES20.glGetUniformLocation(program, "uRadius")
        intensityHandle = GLES20.glGetUniformLocation(program, "uIntensity")
        pressHandle = GLES20.glGetUniformLocation(program, "uPress")
        textureReadyHandle = GLES20.glGetUniformLocation(program, "uTextureReady")
        blurAmountHandle = GLES20.glGetUniformLocation(program, "uBlurAmount")
        clearTextureHandle = GLES20.glGetUniformLocation(program, "uClearTexture")
        blurLowTextureHandle = GLES20.glGetUniformLocation(program, "uBlurLowTexture")
        blurMediumTextureHandle = GLES20.glGetUniformLocation(program, "uBlurMediumTexture")
        blurHighTextureHandle = GLES20.glGetUniformLocation(program, "uBlurHighTexture")
        materialHandle = GLES20.glGetUniformLocation(program, "uMaterial")
        bodyLensAHandle = GLES20.glGetUniformLocation(program, "uBodyLensA")
        bodyLensBHandle = GLES20.glGetUniformLocation(program, "uBodyLensB")
        bodyHandle = GLES20.glGetUniformLocation(program, "uBody")
        shoulderHandle = GLES20.glGetUniformLocation(program, "uShoulder")
        shoulderFlowHandle = GLES20.glGetUniformLocation(program, "uShoulderFlow")
        dispersionHandle = GLES20.glGetUniformLocation(program, "uDispersion")

        GLES20.glUseProgram(program)
        GLES20.glUniform1i(clearTextureHandle, 0)
        GLES20.glUniform1i(blurLowTextureHandle, 1)
        GLES20.glUniform1i(blurMediumTextureHandle, 2)
        GLES20.glUniform1i(blurHighTextureHandle, 3)
        GLES20.glUniform1f(textureReadyHandle, 0f)

        clearTextureId = createConfiguredTexture(0, GLES20.GL_TEXTURE0)
        blurLowTextureId = createConfiguredTexture(1, GLES20.GL_TEXTURE1)
        blurMediumTextureId = createConfiguredTexture(2, GLES20.GL_TEXTURE2)
        blurHighTextureId = 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        val buffers = IntArray(1)
        GLES20.glGenBuffers(1, buffers, 0)
        quadBufferId = buffers[0]
        val quadVertices = ByteBuffer
            .allocateDirect(FULLSCREEN_QUAD.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(FULLSCREEN_QUAD)
                position(0)
            }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBufferId)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            FULLSCREEN_QUAD.size * 4,
            quadVertices,
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, 0)

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
            if (dirtyMask and DIRTY_GEOMETRY != 0) geometryState.copyInto(geometrySnapshot)
            if (dirtyMask and DIRTY_SAMPLING != 0) samplingState.copyInto(samplingSnapshot)
            if (dirtyMask and DIRTY_PRESS != 0) pressState.copyInto(pressSnapshot)
            if (dirtyMask and DIRTY_BLUR != 0) blurSnapshot = blurAmount
            if (dirtyMask and DIRTY_STYLE != 0) styleState.copyInto(styleSnapshot)
        }

        if (dirtyMask and DIRTY_SURFACE != 0) {
            GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        }
        if (dirtyMask and DIRTY_GEOMETRY != 0) {
            drawCardWidth = geometrySnapshot[GEOMETRY_WIDTH]
            drawCardHeight = geometrySnapshot[GEOMETRY_HEIGHT]
            drawRectOffsetY = geometrySnapshot[GEOMETRY_OFFSET_Y]
            GLES20.glUniform4f(rectHandle, 0f, drawRectOffsetY, drawCardWidth, drawCardHeight)
            GLES20.glUniform1f(
                radiusHandle,
                geometrySnapshot[GEOMETRY_RADIUS].coerceIn(2f, max(drawCardWidth, drawCardHeight))
            )
            GLES20.glUniform1f(
                intensityHandle,
                geometrySnapshot[GEOMETRY_INTENSITY].coerceIn(0.35f, 1.35f)
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
        if (dirtyMask and DIRTY_BLUR != 0) {
            GLES20.glUniform1f(blurAmountHandle, blurSnapshot)
        }
        if (dirtyMask and DIRTY_STYLE != 0) uploadStyleUniforms()

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
        deleteTexture(clearTextureId)
        deleteTexture(blurLowTextureId)
        deleteTexture(blurMediumTextureId)
        deleteTexture(blurHighTextureId)
        if (quadBufferId != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadBufferId), 0)
        if (program != 0) GLES20.glDeleteProgram(program)
        clearTextureId = 0
        blurLowTextureId = 0
        blurMediumTextureId = 0
        blurHighTextureId = 0
        quadBufferId = 0
        program = 0
        textureReady = false
        highTextureAliasesMedium = false
        activeClearBitmap = null
        activeBlurLowBitmap = null
        activeBlurMediumBitmap = null
        activeBlurHighBitmap = null
        synchronized(textureLock) {
            pendingClearBitmap = null
            pendingBlurLowBitmap = null
            pendingBlurMediumBitmap = null
            pendingBlurHighBitmap = null
            textureSetPending = false
        }
        previousGlassScissor = null
    }

    private fun uploadStyleUniforms() {
        GLES20.glUniform4f(
            materialHandle,
            styleSnapshot[STYLE_MATERIAL_X],
            styleSnapshot[STYLE_MATERIAL_Y],
            styleSnapshot[STYLE_MATERIAL_Z],
            0f
        )
        GLES20.glUniform4f(
            bodyLensAHandle,
            styleSnapshot[STYLE_BODY_LENS_A_X],
            styleSnapshot[STYLE_BODY_LENS_A_Y],
            styleSnapshot[STYLE_BODY_LENS_A_Z],
            0f
        )
        GLES20.glUniform4f(
            bodyLensBHandle,
            styleSnapshot[STYLE_BODY_LENS_B_X],
            styleSnapshot[STYLE_BODY_LENS_B_Y],
            styleSnapshot[STYLE_BODY_LENS_B_Z],
            styleSnapshot[STYLE_BODY_LENS_B_W]
        )
        GLES20.glUniform4f(
            bodyHandle,
            styleSnapshot[STYLE_BODY_X],
            styleSnapshot[STYLE_BODY_Y],
            styleSnapshot[STYLE_BODY_Z],
            styleSnapshot[STYLE_BODY_W]
        )
        GLES20.glUniform4f(
            shoulderHandle,
            styleSnapshot[STYLE_SHOULDER_WIDTH],
            styleSnapshot[STYLE_SHOULDER_ANGLE],
            styleSnapshot[STYLE_SHOULDER_FALLOFF],
            styleSnapshot[STYLE_SHOULDER_MATERIAL]
        )
        GLES20.glUniform2f(
            shoulderFlowHandle,
            styleSnapshot[STYLE_SHOULDER_CAPTURE],
            styleSnapshot[STYLE_SHOULDER_FLOW]
        )
        GLES20.glUniform4f(
            dispersionHandle,
            styleSnapshot[STYLE_DISPERSION_STRENGTH],
            styleSnapshot[STYLE_DISPERSION_DISTANCE],
            styleSnapshot[STYLE_DISPERSION_EDGE_WIDTH],
            styleSnapshot[STYLE_DISPERSION_CONCENTRATION]
        )
    }

    private fun applyStyleValues(style: GlassBorderStyle, densityScale: Float) {
        styleState[STYLE_MATERIAL_X] = style.newOpenGlBodyVisibility.coerceIn(0f, 20f)
        styleState[STYLE_MATERIAL_Y] = style.newOpenGlBodyMaxAlpha.coerceIn(0f, 1f)
        styleState[STYLE_MATERIAL_Z] = style.newOpenGlBodyOutputBrightness.coerceIn(0.2f, 2.8f)
        styleState[STYLE_BODY_LENS_A_X] =
            style.newOpenGlBodyLensBasePull.coerceIn(-300f, 300f) * densityScale
        styleState[STYLE_BODY_LENS_A_Y] =
            style.newOpenGlBodyLensPullDp.coerceIn(-600f, 600f) * densityScale
        styleState[STYLE_BODY_LENS_A_Z] =
            style.newOpenGlBodyLensConcentration.coerceIn(-10f, 10f)
        styleState[STYLE_BODY_LENS_B_X] =
            style.newOpenGlBodyLensExtraDistance.coerceIn(0f, 200f) * densityScale
        styleState[STYLE_BODY_LENS_B_Y] =
            style.newOpenGlBodyLensReachDp.coerceIn(8f, 180f) * densityScale
        styleState[STYLE_BODY_LENS_B_Z] = style.newOpenGlBodyLensDark.coerceIn(-10f, 10f)
        styleState[STYLE_BODY_LENS_B_W] = style.newOpenGlBodyLensDebug.coerceIn(0f, 1f)
        styleState[STYLE_BODY_X] = style.newOpenGlBodyWidth.coerceIn(0.18f, 1.5f)
        styleState[STYLE_BODY_Y] = style.newOpenGlBodyCurve.coerceIn(0.2f, 3.2f)
        styleState[STYLE_BODY_Z] = style.newOpenGlBodyGain.coerceIn(0f, 900f)
        styleState[STYLE_BODY_W] = style.newOpenGlBrightness.coerceIn(0.4f, 2.2f)
        styleState[STYLE_SHOULDER_WIDTH] =
            style.newOpenGlShoulderWidthDp.coerceIn(4f, 96f) * densityScale
        styleState[STYLE_SHOULDER_CAPTURE] =
            style.newOpenGlShoulderCaptureWidthDp.coerceIn(4f, 192f) * densityScale
        styleState[STYLE_SHOULDER_ANGLE] =
            style.newOpenGlShoulderMaxAngleDeg.coerceIn(0f, 89.5f)
        styleState[STYLE_SHOULDER_FALLOFF] =
            style.newOpenGlShoulderFalloffRoundness.coerceIn(0f, 1f)
        styleState[STYLE_SHOULDER_MATERIAL] =
            style.newOpenGlShoulderMaterialStrength.coerceIn(0f, 4f)
        styleState[STYLE_SHOULDER_FLOW] =
            style.newOpenGlShoulderTangentialFlowStrength.coerceIn(0f, 2.4f)
        styleState[STYLE_DISPERSION_STRENGTH] =
            style.newOpenGlDispersionStrength.coerceIn(0f, 1.5f)
        styleState[STYLE_DISPERSION_DISTANCE] =
            style.newOpenGlDispersionDistanceDp.coerceIn(0f, 8f) * densityScale
        styleState[STYLE_DISPERSION_EDGE_WIDTH] =
            style.newOpenGlDispersionEdgeWidthDp.coerceIn(2f, 64f) * densityScale
        styleState[STYLE_DISPERSION_CONCENTRATION] =
            style.newOpenGlDispersionConcentration.coerceIn(0.25f, 4f)
    }

    private fun calculateGlassScissor(): GlassScissorRect? {
        val left = 0
        val top = (floor(drawRectOffsetY.toDouble()).toInt() - WEB_GLASS_SCISSOR_PADDING_PX)
            .coerceIn(0, viewportHeight)
        val right = ceil((drawCardWidth + WEB_GLASS_SCISSOR_PADDING_PX).toDouble())
            .toInt()
            .coerceIn(0, viewportWidth)
        val bottom = ceil(
            (drawRectOffsetY + drawCardHeight + WEB_GLASS_SCISSOR_PADDING_PX).toDouble()
        )
            .toInt()
            .coerceIn(0, viewportHeight)
        return if (right > left && bottom > top) {
            GlassScissorRect(left, top, right, bottom)
        } else {
            null
        }
    }

    private fun clearDirtyRegion(current: GlassScissorRect?) {
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

    private fun applyScissor(rect: GlassScissorRect) {
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(
            rect.left,
            viewportHeight - rect.bottom,
            rect.width,
            rect.height
        )
    }

    private fun uploadPendingTexturesIfNeeded() {
        val clear: Bitmap
        val low: Bitmap
        val medium: Bitmap
        val high: Bitmap
        synchronized(textureLock) {
            if (!textureSetPending) return
            clear = pendingClearBitmap ?: return
            low = pendingBlurLowBitmap ?: return
            medium = pendingBlurMediumBitmap ?: return
            high = pendingBlurHighBitmap ?: return
            pendingClearBitmap = null
            pendingBlurLowBitmap = null
            pendingBlurMediumBitmap = null
            pendingBlurHighBitmap = null
            textureSetPending = false
        }

        if (clear !== activeClearBitmap) {
            uploadTexture(0, GLES20.GL_TEXTURE0, clearTextureId, clear)
            activeClearBitmap = clear
        }
        if (low !== activeBlurLowBitmap) {
            uploadTexture(1, GLES20.GL_TEXTURE1, blurLowTextureId, low)
            activeBlurLowBitmap = low
        }
        if (medium !== activeBlurMediumBitmap) {
            uploadTexture(2, GLES20.GL_TEXTURE2, blurMediumTextureId, medium)
            activeBlurMediumBitmap = medium
        }

        val aliasHighToMedium = high === medium
        if (aliasHighToMedium) {
            if (!highTextureAliasesMedium || blurHighTextureId != 0) {
                deleteTexture(blurHighTextureId)
                blurHighTextureId = 0
                textureWidths[3] = 0
                textureHeights[3] = 0
                bindTexture(GLES20.GL_TEXTURE3, blurMediumTextureId)
                highTextureAliasesMedium = true
            }
            activeBlurHighBitmap = high
        } else {
            if (blurHighTextureId == 0) {
                blurHighTextureId = createConfiguredTexture(3, GLES20.GL_TEXTURE3)
            } else if (highTextureAliasesMedium) {
                bindTexture(GLES20.GL_TEXTURE3, blurHighTextureId)
            }
            highTextureAliasesMedium = false
            if (high !== activeBlurHighBitmap) {
                uploadTexture(3, GLES20.GL_TEXTURE3, blurHighTextureId, high)
                activeBlurHighBitmap = high
            }
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (!textureReady) {
            textureReady = true
            GLES20.glUniform1f(textureReadyHandle, 1f)
        }
    }

    private fun uploadTexture(index: Int, textureUnit: Int, textureId: Int, bitmap: Bitmap) {
        bindTexture(textureUnit, textureId)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        if (textureWidths[index] == bitmap.width && textureHeights[index] == bitmap.height) {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
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

private fun buildProgram(vertex: String, fragment: String): Int {
    fun compileShader(type: Int, source: String): Int {
        val shaderId = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shaderId, source)
        GLES20.glCompileShader(shaderId)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { GLES20.glGetShaderInfoLog(shaderId) }
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
    check(status[0] != 0) { GLES20.glGetProgramInfoLog(program) }
    return program
}

private val FULLSCREEN_QUAD = floatArrayOf(
    -1f, -1f,
    1f, -1f,
    -1f, 1f,
    1f, 1f
)

private const val WEB_VERTEX_SHADER = """
    attribute vec2 aPosition;
    void main(){ gl_Position=vec4(aPosition,0.0,1.0); }
"""
