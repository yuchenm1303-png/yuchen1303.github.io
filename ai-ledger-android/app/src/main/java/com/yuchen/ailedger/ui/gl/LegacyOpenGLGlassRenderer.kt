package com.yuchen.ailedger.ui.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.PerformanceRuntimeMetrics
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
private const val DIRTY_CACHE_GEOMETRY_STYLE = 1 shl 5
private const val DIRTY_ALL =
    DIRTY_SURFACE or DIRTY_GEOMETRY or DIRTY_SAMPLING or DIRTY_PRESS or
        DIRTY_STYLE or DIRTY_CACHE_GEOMETRY_STYLE
private const val DIRTY_CACHE_GEOMETRY =
    DIRTY_SURFACE or DIRTY_GEOMETRY or DIRTY_CACHE_GEOMETRY_STYLE

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

private class MutableLegacyGlassScissorRect {
    var left: Int = 0
    var top: Int = 0
    var right: Int = 0
    var bottom: Int = 0

    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    fun set(left: Int, top: Int, right: Int, bottom: Int) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    fun copyFrom(other: MutableLegacyGlassScissorRect) {
        set(other.left, other.top, other.right, other.bottom)
    }

    fun unionFrom(first: MutableLegacyGlassScissorRect, second: MutableLegacyGlassScissorRect) {
        set(
            left = min(first.left, second.left),
            top = min(first.top, second.top),
            right = max(first.right, second.right),
            bottom = max(first.bottom, second.bottom),
        )
    }
}

/**
 * 旧版 OpenGL 玻璃 Renderer。
 *
 * Host 的完整帧状态在一次锁内写入；圆角 SDF/厚度梯度缓存只受尺寸、圆角和环宽
 * 影响，材质亮度、折射、按压与采样原点不会误触发几何重建。原始 Shader 始终保留
 * 为精确回退路径。
 */
internal class LegacyOpenGLGlassRenderer {
    private val textureLock = Any()
    private val specLock = Any()
    private val geometryCache = LegacyOpenGLGlassGeometryCache()
    private val cacheFrame = LegacyOpenGLGlassCacheFrame()

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

    private val currentGlassScissor = MutableLegacyGlassScissorRect()
    private val previousGlassScissor = MutableLegacyGlassScissorRect()
    private val dirtyGlassScissor = MutableLegacyGlassScissorRect()
    private var hasPreviousGlassScissor = false

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

    fun setFrameState(
        width: Float,
        height: Float,
        rectOffsetY: Float,
        radius: Float,
        originX: Float,
        originY: Float,
        rootWidth: Float,
        rootHeight: Float,
        pressProgress: Float,
        pressCenterX: Float,
        pressCenterY: Float,
        geometryDirty: Boolean,
        samplingDirty: Boolean,
        pressDirty: Boolean,
    ) {
        if (!geometryDirty && !samplingDirty && !pressDirty) return
        synchronized(specLock) {
            var dirtyMask = 0
            if (geometryDirty) {
                geometryState[GEOMETRY_WIDTH] = width.coerceAtLeast(1f)
                geometryState[GEOMETRY_HEIGHT] = height.coerceAtLeast(1f)
                geometryState[GEOMETRY_OFFSET_Y] = rectOffsetY
                geometryState[GEOMETRY_RADIUS] = radius
                dirtyMask = dirtyMask or DIRTY_GEOMETRY
            }
            if (samplingDirty) {
                samplingState[SAMPLING_ORIGIN_X] = originX
                samplingState[SAMPLING_ORIGIN_Y] = originY
                samplingState[SAMPLING_ROOT_WIDTH] = rootWidth.coerceAtLeast(1f)
                samplingState[SAMPLING_ROOT_HEIGHT] = rootHeight.coerceAtLeast(1f)
                dirtyMask = dirtyMask or DIRTY_SAMPLING
            }
            if (pressDirty) {
                pressState[PRESS_PROGRESS] = pressProgress.coerceIn(0f, 1f)
                pressState[PRESS_CENTER_X] = pressCenterX.coerceIn(0f, 1f)
                pressState[PRESS_CENTER_Y] = pressCenterY.coerceIn(0f, 1f)
                dirtyMask = dirtyMask or DIRTY_PRESS
            }
            pendingDirtyMask = pendingDirtyMask or dirtyMask
        }
    }

    fun setGlassSpec(width: Float, height: Float, rectOffsetY: Float, radius: Float) {
        setFrameState(
            width = width,
            height = height,
            rectOffsetY = rectOffsetY,
            radius = radius,
            originX = 0f,
            originY = 0f,
            rootWidth = 1f,
            rootHeight = 1f,
            pressProgress = 0f,
            pressCenterX = 0.5f,
            pressCenterY = 0.5f,
            geometryDirty = true,
            samplingDirty = false,
            pressDirty = false,
        )
    }

    fun setSamplingSpec(originX: Float, originY: Float, rootWidth: Float, rootHeight: Float) {
        setFrameState(
            width = 1f,
            height = 1f,
            rectOffsetY = 0f,
            radius = 0f,
            originX = originX,
            originY = originY,
            rootWidth = rootWidth,
            rootHeight = rootHeight,
            pressProgress = 0f,
            pressCenterX = 0.5f,
            pressCenterY = 0.5f,
            geometryDirty = false,
            samplingDirty = true,
            pressDirty = false,
        )
    }

    fun setPressSpec(progress: Float, centerX: Float, centerY: Float) {
        setFrameState(
            width = 1f,
            height = 1f,
            rectOffsetY = 0f,
            radius = 0f,
            originX = 0f,
            originY = 0f,
            rootWidth = 1f,
            rootHeight = 1f,
            pressProgress = progress,
            pressCenterX = centerX,
            pressCenterY = centerY,
            geometryDirty = false,
            samplingDirty = false,
            pressDirty = true,
        )
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
            val previousRingWidth = styleState[STYLE_RING_WIDTH]
            applyStyleValues(style)
            var dirty = DIRTY_STYLE
            if (previousRingWidth != styleState[STYLE_RING_WIDTH]) {
                dirty = dirty or DIRTY_CACHE_GEOMETRY_STYLE
            }
            pendingDirtyMask = pendingDirtyMask or dirty
        }
    }

    fun onSurfaceCreated() {
        program = buildLegacyGlassProgram(
            LEGACY_GLASS_VERTEX_SHADER,
            LegacyOpenGLGlassShader.FRAGMENT_SHADER,
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
            GLES20.GL_STATIC_DRAW,
        )
        bindDirectQuad()

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        geometryCache.onSurfaceCreated()
        forceFullClear = true
        hasPreviousGlassScissor = false
        synchronized(specLock) {
            pendingDirtyMask = pendingDirtyMask or DIRTY_ALL
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        geometryCache.onSurfaceChanged(viewportWidth, viewportHeight)
        forceFullClear = true
        hasPreviousGlassScissor = false
        synchronized(specLock) {
            pendingDirtyMask = pendingDirtyMask or DIRTY_SURFACE
        }
    }

    fun onDrawFrame() {
        if (program == 0) return
        GLES20.glUseProgram(program)
        bindDirectQuad()
        uploadPendingTexturesIfNeeded()

        val dirtyMask: Int
        synchronized(specLock) {
            dirtyMask = pendingDirtyMask
            pendingDirtyMask = 0
            if (dirtyMask and DIRTY_GEOMETRY != 0) geometryState.copyInto(geometrySnapshot)
            if (dirtyMask and DIRTY_SAMPLING != 0) samplingState.copyInto(samplingSnapshot)
            if (dirtyMask and DIRTY_PRESS != 0) pressState.copyInto(pressSnapshot)
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
            GLES20.glUniform1f(radiusHandle, clampedRadius())
        }
        if (dirtyMask and DIRTY_SAMPLING != 0) {
            GLES20.glUniform2f(
                cardOriginHandle,
                samplingSnapshot[SAMPLING_ORIGIN_X],
                samplingSnapshot[SAMPLING_ORIGIN_Y],
            )
            GLES20.glUniform2f(
                rootResolutionHandle,
                samplingSnapshot[SAMPLING_ROOT_WIDTH],
                samplingSnapshot[SAMPLING_ROOT_HEIGHT],
            )
        }
        if (dirtyMask and DIRTY_PRESS != 0) {
            GLES20.glUniform4f(
                pressHandle,
                pressSnapshot[PRESS_PROGRESS],
                pressSnapshot[PRESS_CENTER_X],
                pressSnapshot[PRESS_CENTER_Y],
                0f,
            )
        }
        if (dirtyMask and DIRTY_STYLE != 0) uploadStyleUniforms()

        val hasCurrentScissor = calculateGlassScissor()
        clearDirtyRegion(hasCurrentScissor)
        if (!hasCurrentScissor) {
            hasPreviousGlassScissor = false
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
            return
        }

        val geometryInvalidated = dirtyMask and DIRTY_CACHE_GEOMETRY != 0
        if (geometryInvalidated) geometryCache.invalidate()
        updateCacheFrame(currentGlassScissor)
        val cached = geometryCache.drawFrame(
            frame = cacheFrame,
            quadBufferId = quadBufferId,
            geometryInvalidatedThisFrame = geometryInvalidated,
        )
        if (!cached) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
            GLES20.glUseProgram(program)
            bindDirectQuad()
            bindTexture(GLES20.GL_TEXTURE0, blurTextureId)
            bindTexture(GLES20.GL_TEXTURE1, effectiveLensTextureId())
            applyScissor(currentGlassScissor)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        }
        previousGlassScissor.copyFrom(currentGlassScissor)
        hasPreviousGlassScissor = true
    }

    fun onRelease() {
        geometryCache.onRelease()
        deleteTexture(blurTextureId)
        deleteTexture(lensTextureId)
        if (quadBufferId != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadBufferId), 0)
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
        hasPreviousGlassScissor = false
    }

    private fun updateCacheFrame(scissor: MutableLegacyGlassScissorRect) {
        cacheFrame.viewportWidth = viewportWidth
        cacheFrame.viewportHeight = viewportHeight
        cacheFrame.rectWidth = drawCardWidth
        cacheFrame.rectHeight = drawCardHeight
        cacheFrame.rectOffsetY = drawRectOffsetY
        cacheFrame.radius = clampedRadius()
        cacheFrame.originX = samplingSnapshot[SAMPLING_ORIGIN_X]
        cacheFrame.originY = samplingSnapshot[SAMPLING_ORIGIN_Y]
        cacheFrame.rootWidth = samplingSnapshot[SAMPLING_ROOT_WIDTH]
        cacheFrame.rootHeight = samplingSnapshot[SAMPLING_ROOT_HEIGHT]
        cacheFrame.pressProgress = pressSnapshot[PRESS_PROGRESS]
        cacheFrame.pressCenterX = pressSnapshot[PRESS_CENTER_X]
        cacheFrame.pressCenterY = pressSnapshot[PRESS_CENTER_Y]
        cacheFrame.materialVisibility = styleSnapshot[STYLE_VISIBILITY]
        cacheFrame.materialMaxAlpha = styleSnapshot[STYLE_MAX_ALPHA]
        cacheFrame.materialEdgeBrightness = styleSnapshot[STYLE_EDGE_BRIGHTNESS]
        cacheFrame.refractionPullScale = styleSnapshot[STYLE_PULL_SCALE]
        cacheFrame.refractionEdgePullDp = styleSnapshot[STYLE_EDGE_PULL_DP]
        cacheFrame.refractionCompressionScale = styleSnapshot[STYLE_COMPRESSION_SCALE]
        cacheFrame.refractionCornerScale = styleSnapshot[STYLE_CORNER_SCALE]
        cacheFrame.opticsSampleRadius = styleSnapshot[STYLE_SAMPLE_RADIUS]
        cacheFrame.opticsRingWidth = styleSnapshot[STYLE_RING_WIDTH]
        cacheFrame.opticsDebugAlpha = styleSnapshot[STYLE_DEBUG_ALPHA]
        cacheFrame.opticsDarkScale = styleSnapshot[STYLE_DARK_SCALE]
        cacheFrame.texturesReady = texturesReady
        cacheFrame.blurTextureId = blurTextureId
        cacheFrame.lensTextureId = effectiveLensTextureId()
        cacheFrame.scissorLeft = scissor.left
        cacheFrame.scissorTop = scissor.top
        cacheFrame.scissorRight = scissor.right
        cacheFrame.scissorBottom = scissor.bottom
    }

    private fun clampedRadius(): Float =
        geometrySnapshot[GEOMETRY_RADIUS].coerceIn(2f, max(drawCardWidth, drawCardHeight))

    private fun uploadStyleUniforms() {
        GLES20.glUniform4f(
            materialHandle,
            styleSnapshot[STYLE_VISIBILITY],
            styleSnapshot[STYLE_MAX_ALPHA],
            styleSnapshot[STYLE_EDGE_BRIGHTNESS],
            0f,
        )
        GLES20.glUniform4f(
            refractionHandle,
            styleSnapshot[STYLE_PULL_SCALE],
            styleSnapshot[STYLE_EDGE_PULL_DP],
            styleSnapshot[STYLE_COMPRESSION_SCALE],
            styleSnapshot[STYLE_CORNER_SCALE],
        )
        GLES20.glUniform4f(
            opticsHandle,
            styleSnapshot[STYLE_SAMPLE_RADIUS],
            styleSnapshot[STYLE_RING_WIDTH],
            styleSnapshot[STYLE_DEBUG_ALPHA],
            styleSnapshot[STYLE_DARK_SCALE],
        )
    }

    private fun applyStyleValues(style: GlassBorderStyle) {
        styleState[STYLE_VISIBILITY] = style.openGlVisibility.coerceIn(0f, 20f)
        styleState[STYLE_MAX_ALPHA] = style.openGlMaxAlpha.coerceIn(0f, 1f)
        styleState[STYLE_EDGE_BRIGHTNESS] = style.edgeBrightness.coerceIn(-5f, 5f)
        styleState[STYLE_PULL_SCALE] = style.openGlPullScale.coerceIn(-300f, 300f)
        styleState[STYLE_EDGE_PULL_DP] = style.edgePullDp.coerceIn(-600f, 600f)
        styleState[STYLE_COMPRESSION_SCALE] = style.openGlCompressionScale.coerceIn(-10f, 10f)
        styleState[STYLE_CORNER_SCALE] = style.openGlCornerScale.coerceIn(0f, 200f)
        styleState[STYLE_SAMPLE_RADIUS] = style.openGlSampleRadiusScale.coerceIn(0f, 200f)
        styleState[STYLE_RING_WIDTH] = style.ringWidthDp.coerceIn(0f, 300f)
        styleState[STYLE_DEBUG_ALPHA] = style.openGlDebugLineAlpha.coerceIn(0f, 1f)
        styleState[STYLE_DARK_SCALE] = style.openGlDarkScale.coerceIn(-10f, 10f)
    }

    private fun calculateGlassScissor(): Boolean {
        val left = 0
        val top = (
            floor(drawRectOffsetY.toDouble()).toInt() - LEGACY_GLASS_SCISSOR_PADDING_PX
            ).coerceIn(0, viewportHeight)
        val right = ceil(
            (drawCardWidth + LEGACY_GLASS_SCISSOR_PADDING_PX).toDouble(),
        ).toInt().coerceIn(0, viewportWidth)
        val bottom = ceil(
            (drawRectOffsetY + drawCardHeight + LEGACY_GLASS_SCISSOR_PADDING_PX).toDouble(),
        ).toInt().coerceIn(0, viewportHeight)
        if (right <= left || bottom <= top) return false
        currentGlassScissor.set(left, top, right, bottom)
        return true
    }

    private fun clearDirtyRegion(hasCurrent: Boolean) {
        if (!partialClearSupported || forceFullClear) {
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            PerformanceRuntimeMetrics.recordOpenGlFullClear(viewportWidth, viewportHeight)
            forceFullClear = false
            return
        }
        when {
            !hasPreviousGlassScissor && hasCurrent -> dirtyGlassScissor.copyFrom(currentGlassScissor)
            hasPreviousGlassScissor && !hasCurrent -> dirtyGlassScissor.copyFrom(previousGlassScissor)
            hasPreviousGlassScissor && hasCurrent ->
                dirtyGlassScissor.unionFrom(previousGlassScissor, currentGlassScissor)
            else -> return
        }
        applyScissor(dirtyGlassScissor)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    private fun applyScissor(rect: MutableLegacyGlassScissorRect) {
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(
            rect.left,
            viewportHeight - rect.bottom,
            rect.width,
            rect.height,
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
            uploadTexture(0, GLES20.GL_TEXTURE0, blurTextureId, blurBitmap)
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
                uploadTexture(1, GLES20.GL_TEXTURE1, lensTextureId, lensBitmap)
                activeLensBitmap = lensBitmap
            }
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (!texturesReady) {
            texturesReady = true
            GLES20.glUniform1f(textureReadyHandle, 1f)
        }
    }

    private fun effectiveLensTextureId(): Int =
        if (lensTextureAliasesBlur || lensTextureId == 0) blurTextureId else lensTextureId

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
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        textureWidths[index] = 0
        textureHeights[index] = 0
        return textureId
    }

    private fun bindDirectQuad() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBufferId)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, 0)
    }

    private fun bindTexture(textureUnit: Int, textureId: Int) {
        GLES20.glActiveTexture(textureUnit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    }

    private fun deleteTexture(textureId: Int) {
        if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
    }
}

private fun buildLegacyGlassProgram(vertex: String, fragment: String): Int {
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

private val LEGACY_FULLSCREEN_QUAD = floatArrayOf(
    -1f, -1f,
    1f, -1f,
    -1f, 1f,
    1f, 1f,
)

private const val LEGACY_GLASS_VERTEX_SHADER = """
    attribute vec2 aPosition;
    void main(){ gl_Position=vec4(aPosition,0.0,1.0); }
"""
