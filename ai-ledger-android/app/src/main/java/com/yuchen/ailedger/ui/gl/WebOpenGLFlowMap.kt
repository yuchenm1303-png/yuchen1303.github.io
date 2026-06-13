package com.yuchen.ailedger.ui.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class WebOpenGLFlowMap(
    val width: Int,
    val height: Int,
    val depthPx: Float,
    val pixels: ByteBuffer
)

private data class WebOpenGLFlowMapKey(
    val fullWidth: Int,
    val fullHeight: Int,
    val radiusQuarterPx: Int
)

private data class CachedWebOpenGLFlowMap(
    val width: Int,
    val height: Int,
    val depthPx: Float,
    val pixels: ByteArray
) {
    fun newUploadBuffer(): WebOpenGLFlowMap {
        val buffer = ByteBuffer
            .allocateDirect(pixels.size)
            .order(ByteOrder.nativeOrder())
        buffer.put(pixels)
        buffer.position(0)
        return WebOpenGLFlowMap(
            width = width,
            height = height,
            depthPx = depthPx,
            pixels = buffer
        )
    }
}

/**
 * Exact CPU port of the web preview's precomputed harmonic ring Flow Map.
 *
 * Generated RGBA data is cached across renderer/TextureView recreation. Cache hits only skip the
 * deterministic CPU solve; every renderer still receives its own direct upload buffer, so GL thread
 * ownership and the generated pixels remain unchanged.
 */
internal object WebOpenGLFlowMapFactory {
    private const val CACHE_CAPACITY = 6
    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<WebOpenGLFlowMapKey, CachedWebOpenGLFlowMap>(
        CACHE_CAPACITY,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<WebOpenGLFlowMapKey, CachedWebOpenGLFlowMap>?
        ): Boolean = size > CACHE_CAPACITY
    }

    fun build(fullWidth: Int, fullHeight: Int, radiusPx: Float): WebOpenGLFlowMap {
        val safeFullWidth = fullWidth.coerceAtLeast(1)
        val safeFullHeight = fullHeight.coerceAtLeast(1)
        val safeRadius = min(radiusPx, safeFullHeight * 0.5f)
        val key = WebOpenGLFlowMapKey(
            fullWidth = safeFullWidth,
            fullHeight = safeFullHeight,
            radiusQuarterPx = (safeRadius * 4f).roundToInt()
        )

        synchronized(cacheLock) {
            cache[key]?.let { return it.newUploadBuffer() }
        }

        val generated = buildUncached(safeFullWidth, safeFullHeight, safeRadius)
        val canonical = synchronized(cacheLock) {
            cache[key] ?: generated.also { cache[key] = it }
        }
        return canonical.newUploadBuffer()
    }

    private fun buildUncached(
        safeFullWidth: Int,
        safeFullHeight: Int,
        outerRadius: Float
    ): CachedWebOpenGLFlowMap {
        val aspect = safeFullWidth.toFloat() / safeFullHeight.toFloat()
        val flowWidth = (safeFullWidth / 3.5f).roundToInt().coerceIn(256, 448)
        val flowHeight = (flowWidth / max(aspect, 0.25f)).roundToInt().coerceIn(64, 160)
        val scaleX = safeFullWidth.toFloat() / flowWidth.toFloat()
        val scaleY = safeFullHeight.toFloat() / flowHeight.toFloat()
        val cell = max(scaleX, scaleY)
        val flowDepth = min(
            safeFullHeight * 0.44f,
            max(outerRadius * 1.7f, safeFullHeight * 0.32f)
        )
        val innerWidth = max(safeFullWidth - 2f * flowDepth, safeFullWidth * 0.12f)
        val innerHeight = max(safeFullHeight - 2f * flowDepth, safeFullHeight * 0.16f)
        val innerRadius = min(
            max(outerRadius - flowDepth * 0.45f, 4f),
            min(innerWidth, innerHeight) * 0.45f
        )

        val count = flowWidth * flowHeight
        val fieldA = FloatArray(count)
        val fieldB = FloatArray(count)
        val fixed = ByteArray(count)
        val ring = ByteArray(count)
        val boundary = cell * 1.35f

        for (y in 0 until flowHeight) {
            for (x in 0 until flowWidth) {
                val index = y * flowWidth + x
                val px = (x + 0.5f) * scaleX
                val py = (y + 0.5f) * scaleY
                val outer = roundedRectSdf(
                    x = px,
                    y = py,
                    width = safeFullWidth.toFloat(),
                    height = safeFullHeight.toFloat(),
                    radius = outerRadius
                )
                val inner = roundedRectSdf(
                    x = px - flowDepth,
                    y = py - flowDepth,
                    width = innerWidth,
                    height = innerHeight,
                    radius = innerRadius
                )
                when {
                    outer > 0f -> {
                        fixed[index] = 1
                        fieldA[index] = 0f
                        fieldB[index] = 0f
                    }
                    inner <= 0f -> {
                        fixed[index] = 1
                        fieldA[index] = 1f
                        fieldB[index] = 1f
                    }
                    else -> {
                        ring[index] = 1
                        val outerDepth = max(-outer, 0f)
                        val innerDistance = max(inner, 0f)
                        val estimate = clamp01(outerDepth / max(outerDepth + innerDistance, 1e-5f))
                        fieldA[index] = estimate
                        fieldB[index] = estimate
                        if (outerDepth <= boundary) {
                            fixed[index] = 1
                            fieldA[index] = 0f
                            fieldB[index] = 0f
                        } else if (innerDistance <= boundary) {
                            fixed[index] = 1
                            fieldA[index] = 1f
                            fieldB[index] = 1f
                        }
                    }
                }
            }
        }

        var current = fieldA
        var next = fieldB
        val diagonal = 0.70710678f
        val denominator = 4f + 4f * diagonal
        repeat(180) {
            for (y in 1 until flowHeight - 1) {
                for (x in 1 until flowWidth - 1) {
                    val index = y * flowWidth + x
                    if (fixed[index].toInt() != 0) continue
                    val cardinal = current[index - 1] + current[index + 1] +
                        current[index - flowWidth] + current[index + flowWidth]
                    val diagonals = current[index - flowWidth - 1] + current[index - flowWidth + 1] +
                        current[index + flowWidth - 1] + current[index + flowWidth + 1]
                    next[index] = (cardinal + diagonal * diagonals) / denominator
                }
            }
            val swap = current
            current = next
            next = swap
        }

        val pixels = ByteArray(count * 4)
        var outputIndex = 0
        for (y in 0 until flowHeight) {
            for (x in 0 until flowWidth) {
                val index = y * flowWidth + x
                val t = clamp01(current[index])
                var nx = 0f
                var ny = 0f
                var safe = 0f

                if (ring[index].toInt() != 0 && x > 0 && x < flowWidth - 1 && y > 0 && y < flowHeight - 1) {
                    val gx = (current[index + 1] - current[index - 1]) / (2f * scaleX)
                    val gy = (current[index + flowWidth] - current[index - flowWidth]) / (2f * scaleY)
                    val gradient = hypot(gx.toDouble(), gy.toDouble()).toFloat()
                    if (gradient > 1e-6f) {
                        nx = -gx / gradient
                        ny = -gy / gradient
                    } else {
                        val epsilon = max(1f, cell * 0.5f)
                        val dx = roundedRectSdf(
                            (x + 0.5f) * scaleX + epsilon,
                            (y + 0.5f) * scaleY,
                            safeFullWidth.toFloat(),
                            safeFullHeight.toFloat(),
                            outerRadius
                        ) - roundedRectSdf(
                            (x + 0.5f) * scaleX - epsilon,
                            (y + 0.5f) * scaleY,
                            safeFullWidth.toFloat(),
                            safeFullHeight.toFloat(),
                            outerRadius
                        )
                        val dy = roundedRectSdf(
                            (x + 0.5f) * scaleX,
                            (y + 0.5f) * scaleY + epsilon,
                            safeFullWidth.toFloat(),
                            safeFullHeight.toFloat(),
                            outerRadius
                        ) - roundedRectSdf(
                            (x + 0.5f) * scaleX,
                            (y + 0.5f) * scaleY - epsilon,
                            safeFullWidth.toFloat(),
                            safeFullHeight.toFloat(),
                            outerRadius
                        )
                        val magnitude = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                            .takeIf { it > 1e-6f } ?: 1f
                        nx = dx / magnitude
                        ny = dy / magnitude
                    }
                    val innerFade = 1f - smoothStep(0.78f, 0.995f, t)
                    val quality = clamp01(gradient * flowDepth * 0.9f)
                    safe = innerFade * (0.55f + 0.45f * quality)
                }

                pixels[outputIndex++] = toByte(t)
                pixels[outputIndex++] = toByte(nx * 0.5f + 0.5f)
                pixels[outputIndex++] = toByte(ny * 0.5f + 0.5f)
                pixels[outputIndex++] = toByte(clamp01(safe))
            }
        }

        return CachedWebOpenGLFlowMap(
            width = flowWidth,
            height = flowHeight,
            depthPx = flowDepth,
            pixels = pixels
        )
    }

    private fun roundedRectSdf(x: Float, y: Float, width: Float, height: Float, radius: Float): Float {
        val safeRadius = radius.coerceIn(0f, min(width, height) * 0.5f)
        val qx = abs(x - width * 0.5f) - max(width * 0.5f - safeRadius, 0f)
        val qy = abs(y - height * 0.5f) - max(height * 0.5f - safeRadius, 0f)
        return hypot(max(qx, 0f).toDouble(), max(qy, 0f).toDouble()).toFloat() +
            min(max(qx, qy), 0f) - safeRadius
    }

    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        val t = clamp01((value - edge0) / max(edge1 - edge0, 1e-6f))
        return t * t * (3f - 2f * t)
    }

    private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

    private fun toByte(value: Float): Byte =
        (clamp01(value) * 255f).roundToInt().coerceIn(0, 255).toByte()
}
