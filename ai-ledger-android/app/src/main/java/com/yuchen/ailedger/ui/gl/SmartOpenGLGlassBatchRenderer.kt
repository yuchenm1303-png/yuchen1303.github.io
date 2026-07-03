package com.yuchen.ailedger.ui.gl

import android.opengl.GLES20
import android.opengl.GLUtils
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.PerformanceRuntimeMetrics
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val SMART_VERTICES_PER_ITEM = 6
private const val SMART_VERTEX_FLOATS = 14
private const val SMART_VERTEX_STRIDE_BYTES = SMART_VERTEX_FLOATS * 4
private const val SMART_ITEM_VERTEX_FLOATS = SMART_VERTICES_PER_ITEM * SMART_VERTEX_FLOATS
private const val SMART_ITEM_VERTEX_BYTES = SMART_ITEM_VERTEX_FLOATS * 4
private const val SMART_FULL_VERTEX_FLOATS = BATCH_RENDER_LIMIT * SMART_ITEM_VERTEX_FLOATS
private const val SMART_CLEAR_PADDING_PX = 4

internal class SmartOpenGLGlassBatchRenderer {
    private val packetLock = Any()
    private val textureLock = Any()
    private val pendingPacket = UnifiedGlassBatchPacket()
    private val drawPacket = UnifiedGlassBatchPacket()
    private val presentedPacket = UnifiedGlassBatchPacket()

    private var packetPending = false
    private var pendingBlur = 0f
    private var drawBlur = 0f
    private var pendingClear: BatchPlatformBitmap? = null
    private var pendingLow: BatchPlatformBitmap? = null
    private var pendingMedium: BatchPlatformBitmap? = null
    private var pendingHigh: BatchPlatformBitmap? = null
    private var texturesPending = false
    private var activeClear: BatchPlatformBitmap? = null
    private var activeLow: BatchPlatformBitmap? = null
    private var activeMedium: BatchPlatformBitmap? = null
    private var activeHigh: BatchPlatformBitmap? = null
    private var highAliasesMedium = false

    private var clearTexture = 0
    private var lowTexture = 0
    private var mediumTexture = 0
    private var highTexture = 0
    private val textureWidths = IntArray(4)
    private val textureHeights = IntArray(4)
    private var textureReady = false
    private var bufferPreserved = false
    private var firstFrame = true
    private var surfaceDirty = true

    private var program = 0
    private var vertexBufferObject = 0
    private var positionHandle = 0
    private var rectHandle = 0
    private var cardHandle = 0
    private var pressHandle = 0
    private var resolutionHandle = 0
    private var rootResolutionHandle = 0
    private var textureReadyHandle = 0
    private var blurHandle = 0
    private var clearHandle = 0
    private var lowHandle = 0
    private var mediumHandle = 0
    private var highHandle = 0
    private var materialHandle = 0
    private var bodyLensAHandle = 0
    private var bodyLensBHandle = 0
    private var bodyHandle = 0
    private var shoulderHandle = 0
    private var shoulderFlowHandle = 0
    private var dispersionHandle = 0
    private var opticalScaleHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var uploadedStyle: GlassBorderStyle? = null
    private var uploadedDensity = -1f

    private val fullVertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(SMART_FULL_VERTEX_FLOATS * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val itemVertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(SMART_ITEM_VERTEX_FLOATS * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    fun setBufferPreserved(value: Boolean) {
        bufferPreserved = value
    }

    fun setPacket(packet: UnifiedGlassBatchPacket) {
        synchronized(packetLock) {
            pendingPacket.copyFrom(packet)
            packetPending = true
        }
    }

    fun setBackdropBlurAmount(amount: Float) {
        synchronized(packetLock) {
            pendingBlur = amount.coerceIn(0f, 4f)
        }
    }

    fun setBackdropTextures(
        clear: BatchPlatformBitmap,
        low: BatchPlatformBitmap,
        medium: BatchPlatformBitmap,
        high: BatchPlatformBitmap,
    ) {
        synchronized(textureLock) {
            pendingClear = clear
            pendingLow = low
            pendingMedium = medium
            pendingHigh = high
            texturesPending = true
        }
    }

    fun onSurfaceCreated() {
        program = buildSmartBatchProgram(
            WebOpenGLGlassBatchShaders.VERTEX_SHADER,
            WebOpenGLGlassBatchShaders.FRAGMENT_SHADER,
        )
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        rectHandle = GLES20.glGetAttribLocation(program, "aRect")
        cardHandle = GLES20.glGetAttribLocation(program, "aCard")
        pressHandle = GLES20.glGetAttribLocation(program, "aPress")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        rootResolutionHandle = GLES20.glGetUniformLocation(program, "uRootResolution")
        textureReadyHandle = GLES20.glGetUniformLocation(program, "uTextureReady")
        blurHandle = GLES20.glGetUniformLocation(program, "uBlurAmount")
        clearHandle = GLES20.glGetUniformLocation(program, "uClearTexture")
        lowHandle = GLES20.glGetUniformLocation(program, "uBlurLowTexture")
        mediumHandle = GLES20.glGetUniformLocation(program, "uBlurMediumTexture")
        highHandle = GLES20.glGetUniformLocation(program, "uBlurHighTexture")
        materialHandle = GLES20.glGetUniformLocation(program, "uMaterial")
        bodyLensAHandle = GLES20.glGetUniformLocation(program, "uBodyLensA")
        bodyLensBHandle = GLES20.glGetUniformLocation(program, "uBodyLensB")
        bodyHandle = GLES20.glGetUniformLocation(program, "uBody")
        shoulderHandle = GLES20.glGetUniformLocation(program, "uShoulder")
        shoulderFlowHandle = GLES20.glGetUniformLocation(program, "uShoulderFlow")
        dispersionHandle = GLES20.glGetUniformLocation(program, "uDispersion")
        opticalScaleHandle = GLES20.glGetUniformLocation(program, "uOpticalScale")

        GLES20.glUseProgram(program)
        GLES20.glUniform1i(clearHandle, 0)
        GLES20.glUniform1i(lowHandle, 1)
        GLES20.glUniform1i(mediumHandle, 2)
        GLES20.glUniform1i(highHandle, 3)
        GLES20.glUniform1f(textureReadyHandle, 0f)
        clearTexture = createTexture(0, GLES20.GL_TEXTURE0)
        lowTexture = createTexture(1, GLES20.GL_TEXTURE1)
        mediumTexture = createTexture(2, GLES20.GL_TEXTURE2)

        val buffers = IntArray(1)
        GLES20.glGenBuffers(1, buffers, 0)
        vertexBufferObject = buffers[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferObject)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            SMART_FULL_VERTEX_FLOATS * 4,
            null,
            GLES20.GL_DYNAMIC_DRAW,
        )
        bindVertexAttributes()
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        surfaceDirty = true
    }

    fun onDrawFrame() {
        val texturesChanged = uploadTexturesIfNeeded()
        consumePendingPacket()
        if (program == 0) return

        GLES20.glUseProgram(program)
        bindVertexAttributes()
        GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES20.glUniform2f(rootResolutionHandle, drawPacket.rootWidth, drawPacket.rootHeight)
        GLES20.glUniform1f(blurHandle, drawBlur)
        uploadStyleIfNeeded(drawPacket.style, drawPacket.densityScale)

        val fullDraw =
            !bufferPreserved ||
                firstFrame ||
                surfaceDirty ||
                texturesChanged ||
                drawPacket.fullDraw
        val clearAll =
            !bufferPreserved ||
                firstFrame ||
                surfaceDirty ||
                drawPacket.clearAll

        if (clearAll) {
            clearEntireSurface()
        } else if (fullDraw && drawPacket.clearMask != 0) {
            clearDamageUnion(drawPacket.clearMask)
        }

        if (fullDraw) {
            uploadFullVertexBuffer(drawPacket)
            drawActiveItems(drawPacket)
        } else {
            drawDirtyItems(drawPacket)
        }

        presentedPacket.copyFrom(drawPacket)
        firstFrame = false
        surfaceDirty = false
    }

    fun onRelease() {
        deleteTexture(clearTexture)
        deleteTexture(lowTexture)
        deleteTexture(mediumTexture)
        deleteTexture(highTexture)
        if (vertexBufferObject != 0) {
            GLES20.glDeleteBuffers(1, intArrayOf(vertexBufferObject), 0)
        }
        if (program != 0) GLES20.glDeleteProgram(program)
        vertexBufferObject = 0
        program = 0
    }

    private fun consumePendingPacket() {
        synchronized(packetLock) {
            if (packetPending) {
                drawPacket.copyFrom(pendingPacket)
                packetPending = false
            }
            drawBlur = pendingBlur
        }
    }

    private fun drawActiveItems(packet: UnifiedGlassBatchPacket) {
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        for (index in 0 until BATCH_RENDER_LIMIT) {
            val bit = 1 shl index
            if ((packet.activeMask and bit) == 0) continue
            setItemOpticalScale(packet, index)
            GLES20.glDrawArrays(
                GLES20.GL_TRIANGLES,
                index * SMART_VERTICES_PER_ITEM,
                SMART_VERTICES_PER_ITEM,
            )
        }
    }

    private fun drawDirtyItems(packet: UnifiedGlassBatchPacket) {
        var dirty = packet.dirtyMask
        var index = 0
        while (dirty != 0 && index < BATCH_RENDER_LIMIT) {
            val bit = 1 shl index
            if ((dirty and bit) != 0) {
                if ((packet.clearMask and bit) != 0) clearItemUnion(index)
                uploadItemVertexBuffer(packet, index)
                if ((packet.activeMask and bit) != 0) {
                    GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
                    setItemOpticalScale(packet, index)
                    GLES20.glDrawArrays(
                        GLES20.GL_TRIANGLES,
                        index * SMART_VERTICES_PER_ITEM,
                        SMART_VERTICES_PER_ITEM,
                    )
                }
                dirty = dirty and bit.inv()
            }
            index += 1
        }
    }

    private fun setItemOpticalScale(packet: UnifiedGlassBatchPacket, index: Int) {
        val base = index * BATCH_RENDER_FRAME_FLOATS
        GLES20.glUniform1f(
            opticalScaleHandle,
            packet.values[base + BATCH_FRAME_OPTICAL_SCALE]
                .coerceIn(BATCH_MINIMUM_OPTICAL_SCALE, 1f),
        )
    }

    private fun clearEntireSurface() {
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        PerformanceRuntimeMetrics.recordOpenGlFullClear(viewportWidth, viewportHeight)
    }

    private fun clearDamageUnion(mask: Int) {
        val bounds = resolveDamageBounds(mask) ?: return
        clearScissor(bounds[0], bounds[1], bounds[2], bounds[3])
    }

    private fun clearItemUnion(index: Int) {
        val bounds = resolveDamageBounds(1 shl index) ?: return
        clearScissor(bounds[0], bounds[1], bounds[2], bounds[3])
    }

    private fun resolveDamageBounds(mask: Int): FloatArray? {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY

        for (index in 0 until BATCH_RENDER_LIMIT) {
            val bit = 1 shl index
            if ((mask and bit) == 0) continue
            if ((presentedPacket.activeMask and bit) != 0) {
                val base = index * BATCH_RENDER_FRAME_FLOATS
                left = min(left, presentedPacket.values[base + BATCH_FRAME_LEFT])
                top = min(top, presentedPacket.values[base + BATCH_FRAME_TOP])
                right = max(
                    right,
                    presentedPacket.values[base + BATCH_FRAME_LEFT] +
                        presentedPacket.values[base + BATCH_FRAME_WIDTH],
                )
                bottom = max(
                    bottom,
                    presentedPacket.values[base + BATCH_FRAME_TOP] +
                        presentedPacket.values[base + BATCH_FRAME_HEIGHT],
                )
            }
            if ((drawPacket.activeMask and bit) != 0) {
                val base = index * BATCH_RENDER_FRAME_FLOATS
                left = min(left, drawPacket.values[base + BATCH_FRAME_LEFT])
                top = min(top, drawPacket.values[base + BATCH_FRAME_TOP])
                right = max(
                    right,
                    drawPacket.values[base + BATCH_FRAME_LEFT] +
                        drawPacket.values[base + BATCH_FRAME_WIDTH],
                )
                bottom = max(
                    bottom,
                    drawPacket.values[base + BATCH_FRAME_TOP] +
                        drawPacket.values[base + BATCH_FRAME_HEIGHT],
                )
            }
        }
        if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) {
            return null
        }
        return floatArrayOf(left, top, right, bottom)
    }

    private fun clearScissor(left: Float, top: Float, right: Float, bottom: Float) {
        val scissorLeft =
            (floor(left.toDouble()).toInt() - SMART_CLEAR_PADDING_PX).coerceIn(0, viewportWidth)
        val scissorTop =
            (floor(top.toDouble()).toInt() - SMART_CLEAR_PADDING_PX).coerceIn(0, viewportHeight)
        val scissorRight =
            (ceil(right.toDouble()).toInt() + SMART_CLEAR_PADDING_PX).coerceIn(0, viewportWidth)
        val scissorBottom =
            (ceil(bottom.toDouble()).toInt() + SMART_CLEAR_PADDING_PX).coerceIn(0, viewportHeight)
        if (scissorRight <= scissorLeft || scissorBottom <= scissorTop) return

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(
            scissorLeft,
            viewportHeight - scissorBottom,
            scissorRight - scissorLeft,
            scissorBottom - scissorTop,
        )
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    private fun uploadFullVertexBuffer(packet: UnifiedGlassBatchPacket) {
        fullVertexBuffer.clear()
        for (index in 0 until BATCH_RENDER_LIMIT) {
            writeItemVertices(fullVertexBuffer, packet, index)
        }
        fullVertexBuffer.flip()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferObject)
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER,
            0,
            fullVertexBuffer.remaining() * 4,
            fullVertexBuffer,
        )
    }

    private fun uploadItemVertexBuffer(packet: UnifiedGlassBatchPacket, index: Int) {
        itemVertexBuffer.clear()
        writeItemVertices(itemVertexBuffer, packet, index)
        itemVertexBuffer.flip()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferObject)
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER,
            index * SMART_ITEM_VERTEX_BYTES,
            itemVertexBuffer.remaining() * 4,
            itemVertexBuffer,
        )
    }

    private fun writeItemVertices(
        buffer: FloatBuffer,
        packet: UnifiedGlassBatchPacket,
        index: Int,
    ) {
        val bit = 1 shl index
        if ((packet.activeMask and bit) == 0) {
            repeat(SMART_ITEM_VERTEX_FLOATS) { buffer.put(0f) }
            return
        }

        val base = index * BATCH_RENDER_FRAME_FLOATS
        val left = packet.values[base + BATCH_FRAME_LEFT]
        val top = packet.values[base + BATCH_FRAME_TOP]
        val right = left + packet.values[base + BATCH_FRAME_WIDTH]
        val bottom = top + packet.values[base + BATCH_FRAME_HEIGHT]
        putVertex(buffer, left, top, packet.values, base)
        putVertex(buffer, right, top, packet.values, base)
        putVertex(buffer, left, bottom, packet.values, base)
        putVertex(buffer, left, bottom, packet.values, base)
        putVertex(buffer, right, top, packet.values, base)
        putVertex(buffer, right, bottom, packet.values, base)
    }

    private fun putVertex(
        buffer: FloatBuffer,
        x: Float,
        y: Float,
        values: FloatArray,
        base: Int,
    ) {
        buffer.put(x)
        buffer.put(y)
        buffer.put(values[base + BATCH_FRAME_LEFT])
        buffer.put(values[base + BATCH_FRAME_TOP])
        buffer.put(values[base + BATCH_FRAME_WIDTH])
        buffer.put(values[base + BATCH_FRAME_HEIGHT])
        buffer.put(values[base + BATCH_FRAME_ORIGIN_X])
        buffer.put(values[base + BATCH_FRAME_ORIGIN_Y])
        buffer.put(values[base + BATCH_FRAME_RADIUS])
        buffer.put(values[base + BATCH_FRAME_INTENSITY])
        buffer.put(values[base + BATCH_FRAME_PRESS])
        buffer.put(values[base + BATCH_FRAME_PRESS_X])
        buffer.put(values[base + BATCH_FRAME_PRESS_Y])
        buffer.put(0f)
    }

    private fun bindVertexAttributes() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferObject)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(rectHandle)
        GLES20.glEnableVertexAttribArray(cardHandle)
        GLES20.glEnableVertexAttribArray(pressHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            SMART_VERTEX_STRIDE_BYTES,
            0,
        )
        GLES20.glVertexAttribPointer(
            rectHandle,
            4,
            GLES20.GL_FLOAT,
            false,
            SMART_VERTEX_STRIDE_BYTES,
            2 * 4,
        )
        GLES20.glVertexAttribPointer(
            cardHandle,
            4,
            GLES20.GL_FLOAT,
            false,
            SMART_VERTEX_STRIDE_BYTES,
            6 * 4,
        )
        GLES20.glVertexAttribPointer(
            pressHandle,
            4,
            GLES20.GL_FLOAT,
            false,
            SMART_VERTEX_STRIDE_BYTES,
            10 * 4,
        )
    }

    private fun uploadStyleIfNeeded(style: GlassBorderStyle, densityScale: Float) {
        if (uploadedStyle == style && abs(uploadedDensity - densityScale) <= 0.0001f) return
        uploadedStyle = style
        uploadedDensity = densityScale
        val density = densityScale.coerceAtLeast(0.1f)

        GLES20.glUniform4f(
            materialHandle,
            style.newOpenGlBodyVisibility.coerceIn(0f, 20f),
            style.newOpenGlBodyMaxAlpha.coerceIn(0f, 1f),
            style.newOpenGlBodyOutputBrightness.coerceIn(0.2f, 2.8f),
            0f,
        )
        GLES20.glUniform4f(
            bodyLensAHandle,
            style.newOpenGlBodyLensBasePull.coerceIn(-300f, 300f) * density,
            style.newOpenGlBodyLensPullDp.coerceIn(-600f, 600f) * density,
            style.newOpenGlBodyLensConcentration.coerceIn(-10f, 10f),
            0f,
        )
        GLES20.glUniform4f(
            bodyLensBHandle,
            style.newOpenGlBodyLensExtraDistance.coerceIn(0f, 200f) * density,
            style.newOpenGlBodyLensReachDp.coerceIn(8f, 180f) * density,
            style.newOpenGlBodyLensDark.coerceIn(-10f, 10f),
            style.newOpenGlBodyLensDebug.coerceIn(0f, 1f),
        )
        GLES20.glUniform4f(
            bodyHandle,
            style.newOpenGlBodyWidth.coerceIn(0.18f, 1.5f),
            style.newOpenGlBodyCurve.coerceIn(0.2f, 3.2f),
            style.newOpenGlBodyGain.coerceIn(0f, 900f),
            style.newOpenGlBrightness.coerceIn(0.4f, 2.2f),
        )
        GLES20.glUniform4f(
            shoulderHandle,
            style.newOpenGlShoulderWidthDp.coerceIn(4f, 96f) * density,
            style.newOpenGlShoulderMaxAngleDeg.coerceIn(0f, 89.5f),
            style.newOpenGlShoulderFalloffRoundness.coerceIn(0f, 1f),
            style.newOpenGlShoulderMaterialStrength.coerceIn(0f, 4f),
        )
        GLES20.glUniform2f(
            shoulderFlowHandle,
            style.newOpenGlShoulderCaptureWidthDp.coerceIn(4f, 192f) * density,
            style.newOpenGlShoulderTangentialFlowStrength.coerceIn(0f, 2.4f),
        )
        GLES20.glUniform4f(
            dispersionHandle,
            style.newOpenGlDispersionStrength.coerceIn(0f, 1.5f),
            style.newOpenGlDispersionDistanceDp.coerceIn(0f, 8f) * density,
            style.newOpenGlDispersionEdgeWidthDp.coerceIn(2f, 64f) * density,
            style.newOpenGlDispersionConcentration.coerceIn(0.25f, 4f),
        )
    }

    private fun uploadTexturesIfNeeded(): Boolean {
        val clear: BatchPlatformBitmap
        val low: BatchPlatformBitmap
        val medium: BatchPlatformBitmap
        val high: BatchPlatformBitmap
        synchronized(textureLock) {
            if (!texturesPending) return false
            clear = pendingClear ?: return false
            low = pendingLow ?: return false
            medium = pendingMedium ?: return false
            high = pendingHigh ?: return false
            pendingClear = null
            pendingLow = null
            pendingMedium = null
            pendingHigh = null
            texturesPending = false
        }

        var changed = false
        if (clear !== activeClear) {
            uploadTexture(0, GLES20.GL_TEXTURE0, clearTexture, clear)
            activeClear = clear
            changed = true
        }
        if (low !== activeLow) {
            uploadTexture(1, GLES20.GL_TEXTURE1, lowTexture, low)
            activeLow = low
            changed = true
        }
        if (medium !== activeMedium) {
            uploadTexture(2, GLES20.GL_TEXTURE2, mediumTexture, medium)
            activeMedium = medium
            changed = true
        }
        if (high === medium) {
            if (!highAliasesMedium || highTexture != 0) {
                deleteTexture(highTexture)
                highTexture = 0
                bindTexture(GLES20.GL_TEXTURE3, mediumTexture)
                highAliasesMedium = true
                changed = true
            }
            activeHigh = high
        } else {
            if (highTexture == 0) {
                highTexture = createTexture(3, GLES20.GL_TEXTURE3)
                changed = true
            } else if (highAliasesMedium) {
                bindTexture(GLES20.GL_TEXTURE3, highTexture)
            }
            highAliasesMedium = false
            if (high !== activeHigh) {
                uploadTexture(3, GLES20.GL_TEXTURE3, highTexture, high)
                activeHigh = high
                changed = true
            }
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (!textureReady) {
            textureReady = true
            GLES20.glUniform1f(textureReadyHandle, 1f)
            changed = true
        }
        return changed
    }

    private fun uploadTexture(
        index: Int,
        unit: Int,
        texture: Int,
        bitmap: BatchPlatformBitmap,
    ) {
        bindTexture(unit, texture)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        if (textureWidths[index] == bitmap.width && textureHeights[index] == bitmap.height) {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        } else {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            textureWidths[index] = bitmap.width
            textureHeights[index] = bitmap.height
        }
    }

    private fun createTexture(index: Int, unit: Int): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texture = textures[0]
        bindTexture(unit, texture)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR,
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
        textureWidths[index] = 0
        textureHeights[index] = 0
        return texture
    }

    private fun bindTexture(unit: Int, texture: Int) {
        GLES20.glActiveTexture(unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
    }

    private fun deleteTexture(texture: Int) {
        if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
    }
}

private fun buildSmartBatchProgram(vertex: String, fragment: String): Int {
    fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    val vertexShader = compile(GLES20.GL_VERTEX_SHADER, vertex)
    val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
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
