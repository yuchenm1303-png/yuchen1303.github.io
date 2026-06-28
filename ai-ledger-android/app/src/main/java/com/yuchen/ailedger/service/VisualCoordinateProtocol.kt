package com.yuchen.ailedger.service

internal data class VisualDisplayFrame(
    val width: Int,
    val height: Int,
) {
    val valid: Boolean
        get() = width > 0 && height > 0

    val maxX: Float
        get() = (width - 1).coerceAtLeast(0).toFloat()

    val maxY: Float
        get() = (height - 1).coerceAtLeast(0).toFloat()

    val label: String
        get() = "${width}x${height}"
}

internal data class VisualCoordinatePoint(
    val x: Float,
    val y: Float,
)

internal data class VisualTapCoordinateResolution(
    val valid: Boolean,
    val point: VisualCoordinatePoint? = null,
    val reason: String,
    val scaledFromNormalized: Boolean = false,
    val frameMatched: Boolean = true,
)

/**
 * Single source of truth for GUI Plus coordinates.
 *
 * Normalized coordinates describe the complete screenshot/display, with (0, 0) at the top-left and
 * (1, 1) mapped to the last addressable physical pixel. A materialized point is never silently
 * rescaled or moved to a safety margin. If the physical display frame changed after observation,
 * execution is rejected so the visual loop can obtain a fresh screenshot.
 */
internal object VisualCoordinateProtocol {
    const val normalizedProtocol = "normalized_screen_0_1"
    const val pixelMappingProtocol = "full_display_last_pixel_v2"
    const val coordinateSpace = "full_display_physical_pixels"

    fun materializeNormalized(
        normalizedX: Float,
        normalizedY: Float,
        frame: VisualDisplayFrame,
    ): VisualTapCoordinateResolution {
        if (!frame.valid) return invalid("invalid_display_frame")
        if (!normalizedX.isFinite()) return invalid("invalid_normalized_x")
        if (!normalizedY.isFinite()) return invalid("invalid_normalized_y")
        if (normalizedX !in 0f..1f || normalizedY !in 0f..1f) {
            return invalid("coordinate_not_normalized")
        }
        return VisualTapCoordinateResolution(
            valid = true,
            point = VisualCoordinatePoint(
                x = normalizedX * frame.maxX,
                y = normalizedY * frame.maxY,
            ),
            reason = "normalized_materialized",
            scaledFromNormalized = true,
        )
    }

    fun resolveForExecution(
        rawX: Float,
        rawY: Float,
        currentFrame: VisualDisplayFrame,
        expectedFrame: VisualDisplayFrame?,
        alreadyMaterialized: Boolean,
    ): VisualTapCoordinateResolution {
        if (!currentFrame.valid) return invalid("invalid_current_display_frame")
        if (!rawX.isFinite()) return invalid("invalid_execution_x")
        if (!rawY.isFinite()) return invalid("invalid_execution_y")

        if (alreadyMaterialized) {
            val expected = expectedFrame?.takeIf(VisualDisplayFrame::valid)
                ?: return invalid("missing_source_display_frame")
            if (expected != currentFrame) {
                return VisualTapCoordinateResolution(
                    valid = false,
                    reason = "display_frame_changed:${expected.label}->${currentFrame.label}",
                    frameMatched = false,
                )
            }
            if (!contains(rawX, rawY, currentFrame)) {
                return invalid("materialized_coordinate_out_of_bounds")
            }
            return VisualTapCoordinateResolution(
                valid = true,
                point = VisualCoordinatePoint(rawX, rawY),
                reason = "materialized_exact",
            )
        }

        if (rawX in 0f..1f && rawY in 0f..1f) {
            return materializeNormalized(rawX, rawY, currentFrame)
        }
        if (!contains(rawX, rawY, currentFrame)) {
            return invalid("legacy_coordinate_out_of_bounds")
        }
        return VisualTapCoordinateResolution(
            valid = true,
            point = VisualCoordinatePoint(rawX, rawY),
            reason = "legacy_physical_exact",
        )
    }

    fun clipPhysicalPoint(
        x: Float,
        y: Float,
        frame: VisualDisplayFrame,
    ): VisualCoordinatePoint? {
        if (!frame.valid || !x.isFinite() || !y.isFinite()) return null
        return VisualCoordinatePoint(
            x = x.coerceIn(0f, frame.maxX),
            y = y.coerceIn(0f, frame.maxY),
        )
    }

    fun contains(x: Float, y: Float, frame: VisualDisplayFrame): Boolean =
        frame.valid && x.isFinite() && y.isFinite() && x in 0f..frame.maxX && y in 0f..frame.maxY

    private fun invalid(reason: String) = VisualTapCoordinateResolution(
        valid = false,
        reason = reason,
    )
}
