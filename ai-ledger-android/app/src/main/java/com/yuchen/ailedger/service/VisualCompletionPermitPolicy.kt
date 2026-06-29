package com.yuchen.ailedger.service

import java.security.MessageDigest
import org.json.JSONObject

internal data class VisualCompletionCandidate(
    val id: String,
    val sessionId: String,
    val observationId: String,
    val taskRevision: Int = 0,
)

internal data class VisualCompletionPermit(
    val id: String,
    val kind: String,
    val sessionId: String,
    val observationId: String,
    val actionHash: String,
    val candidate: VisualCompletionCandidate,
    val confidence: Double,
)

internal data class VisualCompletionValidation<T>(
    val valid: Boolean,
    val reason: String,
    val value: T? = null,
)

/**
 * Protocol-only verification for GUI Plus completion candidates and independent completion permits.
 * It never reads page text, target labels, app names or user-goal keywords.
 *
 * A user task revision is a hard completion barrier: a candidate captured before the newest user
 * reply can never be upgraded into a permit, even if the backend verifier still returns a stale
 * confirmation for the former goal.
 */
internal object VisualCompletionPermitPolicy {
    private const val ACTION_TYPE = "finish"
    private const val PERMIT_KIND = "independent_gui_completion_verification"
    private const val HASH_CHARS = 24
    private const val MIN_CONFIDENCE = 0.80

    fun candidate(
        step: CloudAgentStep,
        expectedSessionId: String,
        expectedObservationId: String,
        currentTaskRevision: Int = VisualUserTaskUpdateRuntime.currentRevision(),
    ): VisualCompletionValidation<VisualCompletionCandidate> {
        fun finish(result: VisualCompletionValidation<VisualCompletionCandidate>) = report(
            stage = "candidate",
            validation = result,
            step = step,
            expectedSessionId = expectedSessionId,
            expectedObservationId = expectedObservationId,
            currentTaskRevision = currentTaskRevision,
        )
        if (step.type != ACTION_TYPE) return finish(invalid("wrong_action_type"))
        val args = step.toolArgs ?: return finish(invalid("missing_completion_candidate_args"))
        if (!args.optBoolean("completionCandidate", false)) {
            return finish(invalid("completion_candidate_marker_missing"))
        }

        val candidateId = args.cleanString("completionCandidateId")
        val sessionId = args.cleanString("completionCandidateSessionId")
        val observationId = args.cleanString("completionCandidateObservationId")
        val responseSessionId = args.cleanString("responseSessionId")
        val responseObservationId = args.cleanString("responseObservationId")
        if (candidateId.isBlank()) return finish(invalid("completion_candidate_id_missing"))
        if (
            sessionId != expectedSessionId.trim() ||
            responseSessionId != expectedSessionId.trim()
        ) return finish(invalid("completion_candidate_session_mismatch"))
        if (
            observationId != expectedObservationId.trim() ||
            responseObservationId != expectedObservationId.trim()
        ) return finish(invalid("completion_candidate_observation_mismatch"))
        return finish(
            valid(
                VisualCompletionCandidate(
                    id = candidateId,
                    sessionId = sessionId,
                    observationId = observationId,
                    taskRevision = currentTaskRevision.coerceAtLeast(0),
                ),
            ),
        )
    }

    fun permit(
        step: CloudAgentStep,
        expectedSessionId: String,
        expectedObservationId: String,
        expectedCandidate: VisualCompletionCandidate,
        currentTaskRevision: Int = VisualUserTaskUpdateRuntime.currentRevision(),
    ): VisualCompletionValidation<VisualCompletionPermit> {
        fun finish(result: VisualCompletionValidation<VisualCompletionPermit>) = report(
            stage = "permit",
            validation = result,
            step = step,
            expectedSessionId = expectedSessionId,
            expectedObservationId = expectedObservationId,
            currentTaskRevision = currentTaskRevision,
        )
        if (step.type != ACTION_TYPE) return finish(invalid("wrong_action_type"))
        if (currentTaskRevision > expectedCandidate.taskRevision) {
            return finish(invalid("completion_candidate_invalidated_by_user_revision"))
        }
        val args = step.toolArgs ?: return finish(invalid("missing_completion_permit_args"))
        val permitId = args.cleanString("completionPermitId")
        val permitKind = args.cleanString("completionPermitKind")
        val permitObservationId = args.cleanString("completionPermitObservationId")
        val permitSessionId = args.cleanString("completionPermitSessionId")
        val permitActionType = args.cleanString("completionPermitActionType")
        val permitHash = args.cleanString("completionPermitActionHash")
        val responseObservationId = args.cleanString("responseObservationId")
        val responseSessionId = args.cleanString("responseSessionId")
        val candidateId = args.cleanString("completionCandidateId")
        val candidateObservationId = args.cleanString("completionCandidateObservationId")
        val verdict = args.cleanString("completionVerifierVerdict").lowercase()
        val confidence = args.finiteDouble("completionVerifierConfidence") ?: 0.0

        if (permitKind != PERMIT_KIND) return finish(invalid("completion_permit_kind_invalid"))
        if (permitActionType != ACTION_TYPE) return finish(invalid("completion_permit_action_mismatch"))
        if (
            permitSessionId != expectedSessionId.trim() ||
            responseSessionId != expectedSessionId.trim()
        ) return finish(invalid("completion_permit_session_mismatch"))
        if (
            permitObservationId != expectedObservationId.trim() ||
            responseObservationId != expectedObservationId.trim()
        ) return finish(invalid("completion_permit_observation_mismatch"))
        if (
            candidateId != expectedCandidate.id ||
            candidateObservationId != expectedCandidate.observationId ||
            expectedCandidate.sessionId != expectedSessionId.trim()
        ) return finish(invalid("completion_candidate_binding_mismatch"))
        if (candidateObservationId == permitObservationId) {
            return finish(invalid("completion_permit_requires_fresh_observation"))
        }
        if (verdict != "confirmed") return finish(invalid("completion_verifier_not_confirmed"))
        if (confidence < MIN_CONFIDENCE) return finish(invalid("completion_verifier_confidence_low"))

        val expectedHash = completionPermitHash(
            sessionId = permitSessionId,
            candidateId = candidateId,
            candidateObservationId = candidateObservationId,
            observationId = permitObservationId,
            kind = permitKind,
        )
        if (permitHash != expectedHash || permitId != "completion_permit_$expectedHash") {
            return finish(invalid("completion_permit_hash_mismatch"))
        }
        return finish(
            valid(
                VisualCompletionPermit(
                    id = permitId,
                    kind = permitKind,
                    sessionId = permitSessionId,
                    observationId = permitObservationId,
                    actionHash = permitHash,
                    candidate = expectedCandidate,
                    confidence = confidence,
                ),
            ),
        )
    }

    internal fun completionPermitHash(
        sessionId: String,
        candidateId: String,
        candidateObservationId: String,
        observationId: String,
        kind: String = PERMIT_KIND,
    ): String {
        val canonical = listOf(
            sessionId.trim(),
            candidateId.trim(),
            candidateObservationId.trim(),
            observationId.trim(),
            ACTION_TYPE,
            kind.trim(),
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(HASH_CHARS)
    }

    private fun <T> report(
        stage: String,
        validation: VisualCompletionValidation<T>,
        step: CloudAgentStep,
        expectedSessionId: String,
        expectedObservationId: String,
        currentTaskRevision: Int,
    ): VisualCompletionValidation<T> {
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "completion_protocol",
            details = JSONObject().apply {
                put("stage", stage)
                put("valid", validation.valid)
                put("reason", validation.reason)
                put("stepType", step.type)
                put("expectedSessionId", expectedSessionId.take(180))
                put("expectedObservationId", expectedObservationId.take(180))
                put("currentTaskRevision", currentTaskRevision)
                put("toolArgs", step.toolArgs ?: JSONObject.NULL)
            },
        )
        return validation
    }

    private fun JSONObject.cleanString(name: String): String = optString(name).trim().take(180)

    private fun JSONObject.finiteDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getDouble(name) }.getOrNull()?.takeIf(Double::isFinite)
            ?: optString(name).trim().toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    private fun <T> invalid(reason: String): VisualCompletionValidation<T> =
        VisualCompletionValidation(valid = false, reason = reason)

    private fun <T> valid(value: T): VisualCompletionValidation<T> =
        VisualCompletionValidation(valid = true, reason = "verified", value = value)
}
