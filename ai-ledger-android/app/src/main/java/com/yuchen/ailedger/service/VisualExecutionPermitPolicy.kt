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
    private const val COORDINATE_EPSILON = 0.0000015

    private val acceptedTapPermitKinds = setOf(
        "android_structural_clickable_anchor",
        "independent_gui_visual_grounding",
    )

    fun validateTap(step: CloudAgentStep): VisualExecutionPermitValidation {
        fun finish(result: VisualExecutionPermitValidation): VisualExecutionPermitValidation {
            VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
                type = "tap_permit_validation",
                details = JSONObject().apply {
                    put("valid", result.valid)
                    put("reason", result.reason)
                    put("stepType", step.type)
                    put("x", step.x ?: JSONObject.NULL)
                    put("y", step.y ?: JSONObject.NULL)
                    put("targetText", step.targetText ?: JSONObject.NULL)
                    put("toolArgs", step.toolArgs ?: JSONObject.NULL)
                },
            )
            return result
        }

        if (step.type != TAP_ACTION_TYPE) {
            return finish(VisualExecutionPermitValidation(false, "wrong_action_type"))
        }
        val actionX = step.x?.takeIf(Float::isFinite)?.toDouble()
            ?: return finish(VisualExecutionPermitValidation(false, "missing_action_x"))
        val actionY = step.y?.takeIf(Float::isFinite)?.toDouble()
            ?: return finish(VisualExecutionPermitValidation(false, "missing_action_y"))
        if (actionX !in 0.0..1.0 || actionY !in 0.0..1.0) {
            return finish(VisualExecutionPermitValidation(false, "coordinate_not_normalized"))
        }

        val args = step.toolArgs ?: return finish(VisualExecutionPermitValidation(false, "missing_permit_args"))
        val permitId = args.cleanString("executionPermitId")
        val permitKind = args.cleanString("executionPermitKind")
        val permitObservationId = args.cleanString("executionPermitObservationId")
        val permitSessionId = args.cleanString("executionPermitSessionId")
        val permitActionType = args.cleanString("executionPermitActionType")
        val permitHash = args.cleanString("executionPermitActionHash")
        val responseObservationId = args.cleanString("responseObservationId")
        val responseSessionId = args.cleanString("responseSessionId")
        val permitX = args.finiteDouble("executionPermitX")
        val permitY = args.finiteDouble("executionPermitY")

        if (permitKind !in acceptedTapPermitKinds) {
            return finish(VisualExecutionPermitValidation(false, "unsupported_permit_kind"))
        }
        if (permitActionType != TAP_ACTION_TYPE) {
            return finish(VisualExecutionPermitValidation(false, "permit_action_mismatch"))
        }
        if (
            permitObservationId.isBlank() || responseObservationId.isBlank() ||
            permitObservationId != responseObservationId
        ) {
            return finish(VisualExecutionPermitValidation(false, "permit_observation_mismatch"))
        }
        if (
            permitSessionId.isBlank() || responseSessionId.isBlank() ||
            permitSessionId != responseSessionId
        ) {
            return finish(VisualExecutionPermitValidation(false, "permit_session_mismatch"))
        }
        if (
            permitX == null || permitY == null ||
            abs(permitX - actionX) > COORDINATE_EPSILON ||
            abs(permitY - actionY) > COORDINATE_EPSILON
        ) {
            return finish(VisualExecutionPermitValidation(false, "permit_coordinate_mismatch"))
        }

        // Hash the backend's already quantized six-decimal values. The Float action coordinates are
        // checked above but never re-quantized for hashing, avoiding Double→Float boundary drift.
        val expectedHash = tapPermitHash(
            sessionId = permitSessionId,
            observationId = permitObservationId,
            x = permitX,
            y = permitY,
            kind = permitKind,
        )
        if (permitHash != expectedHash || permitId != "permit_$expectedHash") {
            return finish(VisualExecutionPermitValidation(false, "permit_hash_mismatch"))
        }
        return finish(VisualExecutionPermitValidation(true, "verified"))
    }

    internal fun tapPermitHash(
        sessionId: String,
        observationId: String,
        x: Double,
        y: Double,
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

    internal fun tapPermitHash(
        sessionId: String,
        observationId: String,
        x: Float,
        y: Float,
        kind: String,
    ): String = tapPermitHash(sessionId, observationId, x.toDouble(), y.toDouble(), kind)

    private fun JSONObject.cleanString(name: String): String =
        optString(name).trim().take(180)

    private fun JSONObject.finiteDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getDouble(name) }.getOrNull()
            ?.takeIf(Double::isFinite)
            ?: optString(name).trim().toDoubleOrNull()?.takeIf(Double::isFinite)
    }
}
