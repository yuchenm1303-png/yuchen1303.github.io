package com.yuchen.ailedger.ui.gl

import com.yuchen.ailedger.model.GlassBorderStyle

internal const val BATCH_RENDER_LIMIT = 8
internal const val BATCH_RENDER_FRAME_FLOATS = 12
internal const val BATCH_FRAME_LEFT = 0
internal const val BATCH_FRAME_TOP = 1
internal const val BATCH_FRAME_WIDTH = 2
internal const val BATCH_FRAME_HEIGHT = 3
internal const val BATCH_FRAME_RADIUS = 4
internal const val BATCH_FRAME_INTENSITY = 5
internal const val BATCH_FRAME_ORIGIN_X = 6
internal const val BATCH_FRAME_ORIGIN_Y = 7
internal const val BATCH_FRAME_PRESS = 8
internal const val BATCH_FRAME_PRESS_X = 9
internal const val BATCH_FRAME_PRESS_Y = 10
internal const val BATCH_FRAME_OPTICAL_SCALE = 11
internal const val BATCH_REFERENCE_SHORT_EDGE_DP = 160f
internal const val BATCH_MINIMUM_OPTICAL_SCALE = 0.28f
internal const val BATCH_FRAME_EPSILON_PX = 0.01f
internal const val BATCH_VALUE_EPSILON = 0.002f

internal class UnifiedGlassBatchPacket {
    val values = FloatArray(BATCH_RENDER_LIMIT * BATCH_RENDER_FRAME_FLOATS)
    var activeMask = 0
    var dirtyMask = 0
    var clearMask = 0
    var fullDraw = true
    var clearAll = true
    var rootWidth = 1f
    var rootHeight = 1f
    var densityScale = 1f
    var style = GlassBorderStyle()
    var generation = 0L

    fun copyFrom(other: UnifiedGlassBatchPacket) {
        System.arraycopy(other.values, 0, values, 0, values.size)
        activeMask = other.activeMask
        dirtyMask = other.dirtyMask
        clearMask = other.clearMask
        fullDraw = other.fullDraw
        clearAll = other.clearAll
        rootWidth = other.rootWidth
        rootHeight = other.rootHeight
        densityScale = other.densityScale
        style = other.style
        generation = other.generation
    }
}
