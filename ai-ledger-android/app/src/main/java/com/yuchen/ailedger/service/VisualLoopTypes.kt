package com.yuchen.ailedger.service

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONObject

internal data class VisualLoopState(
    val goal: String,
    var modelTurns: Int = 0,
    var executedActions: Int = 0,
    var reobservations: Int = 0,
    var currentPackage: String = "",
    var lastAction: String = "",
    var pendingFinishPackage: String = "",
    var pendingFinishFingerprint: String = "",
    var pendingFinishCount: Int = 0,
    var rejectedPlans: Int = 0,
    var executionFailures: Int = 0,
    var paused: Boolean = false,
    var completed: Boolean = false,
) {
    fun clearFinishCandidate() {
        pendingFinishPackage = ""
        pendingFinishFingerprint = ""
        pendingFinishCount = 0
    }
}

internal enum class VisualFailureClass(val wireValue: String) {
    VisualLocal("visual_local"),
    StructuralRoute("structural_route"),
}

internal data class VisualActionValidation(
    val ok: Boolean,
    val message: String = "",
    val failureClass: VisualFailureClass = VisualFailureClass.VisualLocal,
)

internal data class VisualExecutionPermitValidation(
    val valid: Boolean,
    val reason: String,
)

/**
 * Single Android execution gate.
 *
 * The backend owns GUI Plus schema, purpose, milestones, evidence and task-contract validation, then
 * issues one observation-bound execution permit. Android validates only device-local facts that the
 * cloud cannot know at click time: current task revision, current package, observation binding,
 * action integrity and basic executable bounds. It never reinterprets page or task semantics.
 */
internal object VisualActionValidator {
    private const val PERMIT_VERSION = "visual_execution_permit_v2"
    private const val PERMIT_HASH_CHARS = 24

    private val acceptedPermitKinds = setOf(
        "controller_handoff_open",
        "gui_transaction_validated",
        "android_structural_clickable_anchor",
        "independent_gui_visual_grounding",
    )

    private val permitRequiredActions = setOf(
        "open_app", "tap_node", "tap_xy", "input_text", "scroll", "swipe", "back", "home",
        "recents", "notifications", "quick_settings", "wait",
    )

    fun validate(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        runtime: VisualAgentRuntimeContext? = null,
    ): VisualActionValidation {
        if (step.type !in CloudAgentStep.supportedTypes) return structural("Unsupported visual action: ${step.type}")
        if (VisualUserTaskUpdateRuntime.hasUndispatchedRevision()) {
            return local(
                "userTaskRevisionStaleResponse=true; a newer user instruction arrived after this model request started. " +
                    "Do not execute the stale action; re-observe with the latest authoritative user turn.",
            )
        }
        if (step.type == "open_app" && step.packageName.isNullOrBlank()) {
            return structural("open_app requires a packageName from the current device app catalog.")
        }
        if (runtime?.guiPlusEligible == true && step.type in CloudAgentStep.deviceToolTypes) {
            return structural("GUI Plus cannot execute internal device tools after visual handoff.")
        }
        if (runtime != null && !runtime.guiPlusEligible && step.type !in PRE_WORK_SURFACE_ACTIONS) {
            return structural("A verified target work surface is required before visual actions.")
        }
        if (
            runtime == null &&
            snapshot.packageName == VisualExecutionSessionState.ASSISTANT_HOST_PACKAGE &&
            step.type !in PRE_WORK_SURFACE_ACTIONS
        ) return structural("The assistant controller is not a verified target work surface.")
        if (step.type == "tap_xy" && (step.x == null || step.y == null || step.x !in 0f..1f || step.y !in 0f..1f)) {
            return local("Invalid tap coordinates.")
        }
        if (step.type == "input_text" && step.text.isNullOrBlank()) {
            return local("Input text is empty.")
        }
        validateExecutionPermit(step, snapshot, runtime)?.let { return it }
        VisualUserTaskUpdateRuntime.markDispatchedPlanValidated()
        return VisualActionValidation(true)
    }

    private fun validateExecutionPermit(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        runtime: VisualAgentRuntimeContext?,
    ): VisualActionValidation? {
        if (runtime == null || step.type !in permitRequiredActions) return null
        val result = validatePermitOnly(step, snapshot, runtime)
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "execution_permit_validation",
            details = JSONObject().apply {
                put("valid", result.valid)
                put("reason", result.reason)
                put("stepType", step.type)
                put("currentPackage", snapshot.packageName)
                put("observationId", runtime.observationId)
                put("permitVersion", step.argString("executionPermitVersion").orEmpty())
                put("permitKind", step.argString("executionPermitKind").orEmpty())
            },
        )
        return if (result.valid) null else local("executionPermitRejected=true; reason=${result.reason}")
    }

    internal fun validatePermitOnly(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        runtime: VisualAgentRuntimeContext,
    ): VisualExecutionPermitValidation {
        val args = step.toolArgs ?: return VisualExecutionPermitValidation(false, "missing_permit_args")
        val version = args.cleanString("executionPermitVersion")
        val permitId = args.cleanString("executionPermitId")
        val permitKind = args.cleanString("executionPermitKind")
        val permitObservationId = args.cleanString("executionPermitObservationId")
        val permitSessionId = args.cleanString("executionPermitSessionId")
        val permitPackageName = args.cleanString("executionPermitPackageName")
        val permitActionType = args.cleanString("executionPermitActionType")
        val permitX = args.finiteDouble("executionPermitX")
        val permitY = args.finiteDouble("executionPermitY")
        val permitHash = args.cleanString("executionPermitActionHash")
        val responseObservationId = args.cleanString("responseObservationId")
        val responseSessionId = args.cleanString("responseSessionId")

        if (version != PERMIT_VERSION) return VisualExecutionPermitValidation(false, "unsupported_permit_version")
        if (permitKind !in acceptedPermitKinds) return VisualExecutionPermitValidation(false, "unsupported_permit_kind")
        if (permitActionType != step.type) return VisualExecutionPermitValidation(false, "permit_action_mismatch")
        if (
            permitObservationId.isBlank() || permitObservationId != runtime.observationId.trim() ||
            (responseObservationId.isNotBlank() && responseObservationId != permitObservationId)
        ) return VisualExecutionPermitValidation(false, "permit_observation_mismatch")
        if (
            permitSessionId.isBlank() ||
            (responseSessionId.isNotBlank() && responseSessionId != permitSessionId)
        ) return VisualExecutionPermitValidation(false, "permit_session_mismatch")
        if (
            permitPackageName.isBlank() || permitPackageName != snapshot.packageName ||
            (runtime.currentPackage.isNotBlank() && permitPackageName != runtime.currentPackage)
        ) return VisualExecutionPermitValidation(false, "permit_package_mismatch")
        if (step.x != null && (permitX == null || kotlin.math.abs(step.x.toDouble() - permitX) > 0.0000015)) {
            return VisualExecutionPermitValidation(false, "permit_coordinate_mismatch")
        }
        if (step.y != null && (permitY == null || kotlin.math.abs(step.y.toDouble() - permitY) > 0.0000015)) {
            return VisualExecutionPermitValidation(false, "permit_coordinate_mismatch")
        }

        val expectedHash = executionPermitHash(
            sessionId = permitSessionId,
            observationId = permitObservationId,
            packageName = permitPackageName,
            kind = permitKind,
            step = step,
            canonicalX = permitX,
            canonicalY = permitY,
        )
        if (permitHash != expectedHash || permitId != "permit_$expectedHash") {
            return VisualExecutionPermitValidation(false, "permit_hash_mismatch")
        }
        return VisualExecutionPermitValidation(true, "verified")
    }

    internal fun executionPermitHash(
        sessionId: String,
        observationId: String,
        packageName: String,
        kind: String,
        step: CloudAgentStep,
        canonicalX: Double? = null,
        canonicalY: Double? = null,
    ): String {
        val canonical = listOf(
            backendSafeText(sessionId, 160),
            backendSafeText(observationId, 160),
            backendSafeText(packageName, 180),
            backendSafeText(kind, 100),
            permitActionCanonical(step, canonicalX, canonicalY),
        ).joinToString("|") { permitCanonicalPart(it) }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(PERMIT_HASH_CHARS)
    }

    private fun permitActionCanonical(
        step: CloudAgentStep,
        canonicalX: Double? = null,
        canonicalY: Double? = null,
    ): String = listOf(
        backendSafeText(step.type, 48),
        backendSafeText(step.appName.orEmpty(), 120),
        backendSafeText(step.packageName.orEmpty(), 160),
        backendSafeText(step.targetText.orEmpty(), 180),
        step.text.orEmpty(),
        backendSafeText(step.direction.orEmpty(), 32),
        canonicalNumber(canonicalX ?: step.x?.toDouble()),
        canonicalNumber(canonicalY ?: step.y?.toDouble()),
        step.durationMs?.toString().orEmpty(),
    ).joinToString("|") { permitCanonicalPart(it) }

    private fun permitCanonicalPart(value: String): String =
        "${value.toByteArray(Charsets.UTF_8).size}:$value"

    private fun backendSafeText(value: String, max: Int): String =
        value.replace(Regex("\\s+"), " ").trim().take(max)

    private fun canonicalNumber(value: Double?): String = value
        ?.takeIf(Double::isFinite)
        ?.let { String.format(Locale.US, "%.6f", it) }
        .orEmpty()

    fun actionSignature(step: CloudAgentStep): String = listOfNotNull(
        step.type,
        step.packageName,
        step.appName,
        step.targetText,
        step.text?.takeIf { step.type != "input_text" }?.take(32),
        step.direction,
        step.x?.toString(),
        step.y?.toString(),
        step.milestoneId,
        step.hypothesisId,
    ).joinToString("|")

    /** Legacy diagnostic helper retained without participating in execution decisions. */
    fun actionClusterSignature(step: CloudAgentStep): String {
        if (step.type != "tap_xy") return actionSignature(step)
        val x = step.x ?: return actionSignature(step)
        val y = step.y ?: return actionSignature(step)
        return "tap_xy|${(x / LEGACY_TAP_CLUSTER_PX).roundToInt()}|${(y / LEGACY_TAP_CLUSTER_PX).roundToInt()}"
    }

    /** Objective frame fingerprint for diagnostics/anti-replay only, never task-progress semantics. */
    fun snapshotFingerprint(snapshot: AgentScreenSnapshot): String {
        val text = snapshot.texts.take(16).joinToString("|") { it.take(40) }
        val nodes = snapshot.clickableNodes.take(16).joinToString("|") { "${it.text.take(24)}#${it.bounds}" }
        val visual = if (snapshot.capturedNodeCount <= 3 || snapshot.clickableNodes.isEmpty()) {
            sampledVisualFingerprint(snapshot.visual)
        } else ""
        return listOf(snapshot.currentApp, snapshot.capturedNodeCount.toString(), text, nodes, visual).joinToString("::")
    }

    fun completionFingerprint(snapshot: AgentScreenSnapshot): String {
        val text = snapshot.texts.asSequence().map { it.trim().take(40) }
            .filter(String::isNotBlank).take(20).joinToString("|")
        val nodes = snapshot.clickableNodes.asSequence()
            .map { "${it.text.trim().take(24)}#${it.bounds}" }.take(16).joinToString("|")
        return listOf(snapshot.currentApp, text, nodes).joinToString("::")
    }

    private fun structural(message: String) =
        VisualActionValidation(false, message, VisualFailureClass.StructuralRoute)

    private fun local(message: String) =
        VisualActionValidation(false, message, VisualFailureClass.VisualLocal)

    private fun sampledVisualFingerprint(visual: AgentScreenVisual?): String {
        val image = visual?.takeIf { it.hasImage } ?: return ""
        val data = image.base64Jpeg
        if (data.isBlank()) return ""
        val stride = (data.length / 256).coerceAtLeast(1)
        var hash = 1_125_899_906_842_597L
        var index = 0
        while (index < data.length) {
            hash = hash * 31L + data[index].code
            index += stride
        }
        return "${image.width}x${image.height}:${data.length}:${hash.toString(16)}"
    }

    private fun JSONObject.cleanString(name: String): String =
        optString(name).trim().take(180)

    private fun JSONObject.finiteDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getDouble(name) }.getOrNull()
            ?.takeIf(Double::isFinite)
            ?: optString(name).trim().toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    private val PRE_WORK_SURFACE_ACTIONS = CloudAgentStep.deviceToolTypes + "need_user_help"
    private const val LEGACY_TAP_CLUSTER_PX = 160f
}

/** Compatibility bridge for existing tests/callers; all logic lives in the single execution gate. */
@Deprecated("Use VisualActionValidator")
internal object VisualExecutionPermitPolicy {
    fun validateTap(step: CloudAgentStep): VisualExecutionPermitValidation {
        val args = step.toolArgs ?: return VisualExecutionPermitValidation(false, "missing_permit_args")
        val packageName = args.optString("executionPermitPackageName").trim()
        val observationId = args.optString("executionPermitObservationId").trim()
        val runtime = VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.WorkSurface,
            verifiedTargetPackage = packageName,
            currentPackage = packageName,
            observationId = observationId,
            guiPlusEligible = true,
        )
        val snapshot = AgentScreenSnapshot(
            currentApp = packageName,
            packageName = packageName,
            nodeCount = 0,
            capturedNodeCount = 0,
            texts = emptyList(),
            allNodes = emptyList(),
            clickableNodes = emptyList(),
            inputNodes = emptyList(),
            scrollableNodes = emptyList(),
        )
        return VisualActionValidator.validatePermitOnly(step, snapshot, runtime)
    }

    internal fun tapPermitHash(
        sessionId: String,
        observationId: String,
        x: Double,
        y: Double,
        kind: String,
    ): String = VisualActionValidator.executionPermitHash(
        sessionId = sessionId,
        observationId = observationId,
        packageName = "",
        kind = kind,
        step = CloudAgentStep(type = "tap_xy", x = x.toFloat(), y = y.toFloat()),
    )

    internal fun tapPermitHash(
        sessionId: String,
        observationId: String,
        x: Float,
        y: Float,
        kind: String,
    ): String = tapPermitHash(sessionId, observationId, x.toDouble(), y.toDouble(), kind)
}

internal data class PreparedVisualStep(
    val ok: Boolean,
    val message: String = "",
    val step: CloudAgentStep? = null,
    val replanRequired: Boolean = false,
    val alreadyForeground: Boolean = false,
)

internal data class VisualTurn(
    val observation: ScreenObservation,
    val snapshot: AgentScreenSnapshot,
    val runtime: VisualAgentRuntimeContext,
)

internal sealed interface VisualPlanRequest {
    data class Ready(val plan: CloudAgentPlan) : VisualPlanRequest
    data object Retry : VisualPlanRequest
    data class Fatal(val result: AgentTaskRunResult) : VisualPlanRequest
}

internal sealed interface VisualLoopDecision {
    data object Continue : VisualLoopDecision
    data class Return(val result: AgentTaskRunResult) : VisualLoopDecision
    data object Stop : VisualLoopDecision
}
