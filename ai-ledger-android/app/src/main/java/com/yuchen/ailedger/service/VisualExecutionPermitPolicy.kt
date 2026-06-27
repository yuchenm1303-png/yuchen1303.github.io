package com.yuchen.ailedger.service

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import org.json.JSONObject

internal data class VisualExecutionPermitValidation(
    val valid: Boolean,
    val reason: String,
)

/**
 * Pure protocol-integrity validation for cloud-issued GUI execution permits.
 *
 * It never reads page text, target labels or app-specific semantics. The policy only proves that
 * the action returned by the backend is bound to one session, one observation and one coordinate.
 */
internal object VisualExecutionPermitPolicy {
    private const val TAP_ACTION_TYPE = "tap_xy"
    private const val TAP_HASH_CHARS = 24
    private const val COORDINATE_EPSILON = 0.00001f

    private val acceptedTapPermitKinds = setOf(
        "android_structural_clickable_anchor",
        "independent_gui_visual_grounding",
    )

    fun validateTap(step: CloudAgentStep): VisualExecutionPermitValidation {
        if (step.type != TAP_ACTION_TYPE) {
            return VisualExecutionPermitValidation(false, "wrong_action_type")
        }
        val x = step.x?.takeIf(Float::isFinite)
            ?: return VisualExecutionPermitValidation(false, "missing_action_x")
        val y = step.y?.takeIf(Float::isFinite)
            ?: return VisualExecutionPermitValidation(false, "missing_action_y")
        if (x !in 0f..1f || y !in 0f..1f) {
            return VisualExecutionPermitValidation(false, "coordinate_not_normalized")
        }

        val args = step.toolArgs ?: return VisualExecutionPermitValidation(false, "missing_permit_args")
        val permitId = args.cleanString("executionPermitId")
        val permitKind = args.cleanString("executionPermitKind")
        val permitObservationId = args.cleanString("executionPermitObservationId")
        val permitSessionId = args.cleanString("executionPermitSessionId")
        val permitActionType = args.cleanString("executionPermitActionType")
        val permitHash = args.cleanString("executionPermitActionHash")
        val responseObservationId = args.cleanString("responseObservationId")
        val responseSessionId = args.cleanString("responseSessionId")
        val permitX = args.finiteFloat("executionPermitX")
        val permitY = args.finiteFloat("executionPermitY")

        if (permitKind !in acceptedTapPermitKinds) {
            return VisualExecutionPermitValidation(false, "unsupported_permit_kind")
        }
        if (permitActionType != TAP_ACTION_TYPE) {
            return VisualExecutionPermitValidation(false, "permit_action_mismatch")
        }
        if (
            permitObservationId.isBlank() || responseObservationId.isBlank() ||
            permitObservationId != responseObservationId
        ) {
            return VisualExecutionPermitValidation(false, "permit_observation_mismatch")
        }
        if (
            permitSessionId.isBlank() || responseSessionId.isBlank() ||
            permitSessionId != responseSessionId
        ) {
            return VisualExecutionPermitValidation(false, "permit_session_mismatch")
        }
        if (permitX == null || permitY == null || abs(permitX - x) > COORDINATE_EPSILON || abs(permitY - y) > COORDINATE_EPSILON) {
            return VisualExecutionPermitValidation(false, "permit_coordinate_mismatch")
        }

        val expectedHash = tapPermitHash(
            sessionId = permitSessionId,
            observationId = permitObservationId,
            x = x,
            y = y,
            kind = permitKind,
        )
        if (permitHash != expectedHash || permitId != "permit_$expectedHash") {
            return VisualExecutionPermitValidation(false, "permit_hash_mismatch")
        }
        return VisualExecutionPermitValidation(true, "verified")
    }

    internal fun tapPermitHash(
        sessionId: String,
        observationId: String,
        x: Float,
        y: Float,
        kind: String,
    ): String {
        val canonical = listOf(
            sessionId.trim(),
            observationId.trim(),
            TAP_ACTION_TYPE,
            String.format(Locale.US, "%.6f", x),
            String.format(Locale.US, "%.6f", y),
            kind.trim(),
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(TAP_HASH_CHARS)
    }

    private fun JSONObject.cleanString(name: String): String =
        optString(name).trim().take(180)

    private fun JSONObject.finiteFloat(name: String): Float? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getDouble(name).toFloat() }.getOrNull()
            ?.takeIf(Float::isFinite)
            ?: optString(name).trim().toFloatOrNull()?.takeIf(Float::isFinite)
    }
}
