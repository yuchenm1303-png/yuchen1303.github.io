package com.yuchen.ailedger.service

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Android's single device-local execution gate.
 *
 * The backend owns GUI Plus action schema, purpose, milestones, evidence and task-contract checks.
 * Android validates only facts that can change after the cloud response: current work surface,
 * observation binding, cloud permit integrity, executable bounds and authoritative user revisions.
 */
internal object VisualActionValidator {
    private const val PERMIT_VERSION_V2 = "visual_execution_permit_v2"
    private const val PERMIT_HASH_CHARS = 24
    private const val COORDINATE_EPSILON = 0.0000015
    private const val LEGACY_TAP_CLUSTER_PX = 160f

    private val acceptedPermitKinds = setOf(
        "gui_transaction_validated",
        "backend_safe_reobserve",
        "android_structural_clickable_anchor",
        "independent_gui_visual_grounding",
    )
    private val observationBoundActions = setOf(
        "tap_xy", "tap_node", "input_text", "scroll", "swipe", "back", "home", "recents",
        "notifications", "quick_settings", "wait",
    )
    private val preWorkSurfaceActions = CloudAgentStep.deviceToolTypes + "need_user_help"

    fun validate(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        runtime: VisualAgentRuntimeContext? = null,
    ): VisualActionValidation {
        if (step.type !in CloudAgentStep.supportedTypes) {
            return structural("Unsupported visual action: ${step.type}")
        }
        if (VisualUserTaskUpdateRuntime.hasUndispatchedRevision()) {
            return local(
                "userTaskRevisionStaleResponse=true; a newer user instruction arrived after this model request started. " +
                    "Do not execute the stale action; re-observe with the latest authoritative user turn.",
            )
        }
        if (isRepairableGuiProtocolFailure(step)) {
            return local(
                "protocolRepairRequired=true; the cloud did not return one executable official mobile_use action. " +
                    "Keep the task and fresh work surface, then request one corrected action.",
            )
        }
        if (step.type == "open_app" && step.packageName.isNullOrBlank()) {
            return structural("open_app requires a packageName from the current device app catalog.")
        }
        if (runtime?.guiPlusEligible == true && step.type in CloudAgentStep.deviceToolTypes) {
            return structural("GUI Plus cannot execute internal device tools after visual handoff.")
        }
        if (runtime != null && !runtime.guiPlusEligible && step.type !in preWorkSurfaceActions) {
            return structural("A verified target work surface is required before visual actions.")
        }
        if (
            runtime == null &&
            snapshot.packageName == VisualExecutionSessionState.ASSISTANT_HOST_PACKAGE &&
            step.type !in preWorkSurfaceActions
        ) {
            return structural("The assistant controller is not a verified target work surface.")
        }
        if (step.type == "tap_xy" && (step.x == null || step.y == null || step.x !in 0f..1f || step.y !in 0f..1f)) {
            return local("Invalid tap coordinates.")
        }
        if (step.type == "input_text" && step.text.isNullOrBlank()) {
            return local("Input text is empty.")
        }

        val context = runtime?.takeIf { it.guiPlusEligible }
        if (context != null && step.type in observationBoundActions) {
            val permit = validatePermit(step, snapshot, context)
            recordPermitValidation(step, snapshot, context, permit)
            if (!permit.valid) {
                return local("executionPermitRejected=true; reason=${permit.reason}")
            }
        }

        VisualUserTaskUpdateRuntime.markDispatchedPlanValidated()
        return VisualActionValidation(true)
    }

    internal fun validatePermit(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        runtime: VisualAgentRuntimeContext,
    ): VisualExecutionPermitValidation {
        val args = step.toolArgs ?: return VisualExecutionPermitValidation(false, "missing_permit_args")
        val version = args.cleanString("executionPermitVersion")
        return when {
            version == PERMIT_VERSION_V2 -> validateV2Permit(step, snapshot, runtime, args)
            version.isBlank() && step.type == "tap_xy" -> validateLegacyTapPermit(step, runtime, args)
            else -> VisualExecutionPermitValidation(false, "unsupported_permit_version")
        }
    }

    private fun validateV2Permit(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        runtime: VisualAgentRuntimeContext,
        args: JSONObject,
    ): VisualExecutionPermitValidation {
        val permitId = args.cleanString("executionPermitId")
        val permitKind = args.cleanString("executionPermitKind")
        val permitObservationId = args.cleanString("executionPermitObservationId")
        val permitSessionId = args.cleanString("executionPermitSessionId")
        val permitPackageName = args.cleanString("executionPermitPackageName")
        val permitActionType = args.cleanString("executionPermitActionType")
        val permitHash = args.cleanString("executionPermitActionHash")
        val responseObservationId = args.cleanString("responseObservationId")
        val responseSessionId = args.cleanString("responseSessionId")
        val permitX = args.finiteDouble("executionPermitX")
        val permitY = args.finiteDouble("executionPermitY")

        if (permitKind !in acceptedPermitKinds) {
            return VisualExecutionPermitValidation(false, "unsupported_permit_kind")
        }
        if (permitActionType != step.type) {
            return VisualExecutionPermitValidation(false, "permit_action_mismatch")
        }
        if (
            permitObservationId.isBlank() || permitObservationId != runtime.observationId.trim() ||
            responseObservationId.isBlank() || responseObservationId != permitObservationId
        ) {
            return VisualExecutionPermitValidation(false, "permit_observation_mismatch")
        }
        if (
            permitSessionId.isBlank() || responseSessionId.isBlank() ||
            responseSessionId != permitSessionId
        ) {
            return VisualExecutionPermitValidation(false, "permit_session_mismatch")
        }
        if (
            permitPackageName.isBlank() || permitPackageName != snapshot.packageName ||
            runtime.currentPackage.isNotBlank() && permitPackageName != runtime.currentPackage
        ) {
            return VisualExecutionPermitValidation(false, "permit_package_mismatch")
        }
        if (step.x != null && (permitX == null || abs(step.x.toDouble() - permitX) > COORDINATE_EPSILON)) {
            return VisualExecutionPermitValidation(false, "permit_coordinate_mismatch")
        }
        if (step.y != null && (permitY == null || abs(step.y.toDouble() - permitY) > COORDINATE_EPSILON)) {
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

    private fun validateLegacyTapPermit(
        step: CloudAgentStep,
        runtime: VisualAgentRuntimeContext,
        args: JSONObject,
    ): VisualExecutionPermitValidation {
        val actionX = step.x?.takeIf(Float::isFinite)?.toDouble()
            ?: return VisualExecutionPermitValidation(false, "missing_action_x")
        val actionY = step.y?.takeIf(Float::isFinite)?.toDouble()
            ?: return VisualExecutionPermitValidation(false, "missing_action_y")
        val kind = args.cleanString("executionPermitKind")
        val observationId = args.cleanString("executionPermitObservationId")
        val sessionId = args.cleanString("executionPermitSessionId")
        val responseObservationId = args.cleanString("responseObservationId")
        val responseSessionId = args.cleanString("responseSessionId")
        val permitX = args.finiteDouble("executionPermitX")
        val permitY = args.finiteDouble("executionPermitY")
        val hash = args.cleanString("executionPermitActionHash")
        val permitId = args.cleanString("executionPermitId")

        if (kind !in acceptedPermitKinds) return VisualExecutionPermitValidation(false, "unsupported_permit_kind")
        if (observationId != runtime.observationId || observationId != responseObservationId) {
            return VisualExecutionPermitValidation(false, "permit_observation_mismatch")
        }
        if (sessionId.isBlank() || sessionId != responseSessionId) {
            return VisualExecutionPermitValidation(false, "permit_session_mismatch")
        }
        if (
            permitX == null || permitY == null ||
            abs(actionX - permitX) > COORDINATE_EPSILON ||
            abs(actionY - permitY) > COORDINATE_EPSILON
        ) {
            return VisualExecutionPermitValidation(false, "permit_coordinate_mismatch")
        }
        val expected = legacyTapPermitHash(sessionId, observationId, permitX, permitY, kind)
        if (hash != expected || permitId != "permit_$expected") {
            return VisualExecutionPermitValidation(false, "permit_hash_mismatch")
        }
        return VisualExecutionPermitValidation(true, "verified_legacy")
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
            permitSafeText(sessionId, 160),
            permitSafeText(observationId, 160),
            permitSafeText(packageName, 180),
            permitSafeText(kind, 100),
            permitActionCanonical(step, canonicalX, canonicalY),
        ).joinToString("|") { permitCanonicalPart(it) }
        return sha256(canonical).take(PERMIT_HASH_CHARS)
    }

    private fun permitActionCanonical(
        step: CloudAgentStep,
        canonicalX: Double?,
        canonicalY: Double?,
    ): String = listOf(
        permitSafeText(step.type, 48),
        permitSafeText(step.appName.orEmpty(), 120),
        permitSafeText(step.packageName.orEmpty(), 160),
        permitSafeText(step.targetText.orEmpty(), 180),
        step.text.orEmpty(),
        permitSafeText(step.direction.orEmpty(), 32),
        permitCanonicalNumber(canonicalX ?: step.x?.toDouble()),
        permitCanonicalNumber(canonicalY ?: step.y?.toDouble()),
        step.durationMs?.toString().orEmpty(),
    ).joinToString("|") { permitCanonicalPart(it) }

    private fun legacyTapPermitHash(
        sessionId: String,
        observationId: String,
        x: Double,
        y: Double,
        kind: String,
    ): String {
        val canonical = listOf(
            sessionId.trim(),
            observationId.trim(),
            "tap_xy",
            String.format(Locale.US, "%.6f", x),
            String.format(Locale.US, "%.6f", y),
            kind.trim(),
        ).joinToString("|")
        return sha256(canonical).take(PERMIT_HASH_CHARS)
    }

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

    private fun isRepairableGuiProtocolFailure(step: CloudAgentStep): Boolean {
        if (step.type != "need_user_help") return false
        val reason = step.reason.orEmpty().lowercase()
        return repairableProtocolMarkers.any(reason::contains)
    }

    private fun recordPermitValidation(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        runtime: VisualAgentRuntimeContext,
        result: VisualExecutionPermitValidation,
    ) {
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
    }

    private fun permitSafeText(value: String, max: Int): String =
        value.trim().take(max).replace(Regex("\\s+"), " ").trim()

    private fun permitCanonicalPart(value: String): String =
        "${value.toByteArray(Charsets.UTF_8).size}:$value"

    private fun permitCanonicalNumber(value: Double?): String = value
        ?.takeIf(Double::isFinite)
        ?.let { String.format(Locale.US, "%.6f", it) }
        .orEmpty()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

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

    private fun JSONObject.cleanString(name: String): String = optString(name).trim().take(180)

    private fun JSONObject.finiteDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getDouble(name) }.getOrNull()
            ?.takeIf(Double::isFinite)
            ?: optString(name).trim().toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    private fun structural(message: String) =
        VisualActionValidation(false, message, VisualFailureClass.StructuralRoute)

    private fun local(message: String) =
        VisualActionValidation(false, message, VisualFailureClass.VisualLocal)

    private val repairableProtocolMarkers = listOf(
        "未知 mobile_use action",
        "unknown mobile_use action",
        "没有返回官方 mobile_use tool_call",
        "did not return official mobile_use tool_call",
        "model_contract_error",
        "android client does not support gui plus action",
        "mobile_use click 缺少可靠坐标",
        "视觉定位未给出可靠中心坐标",
        "视觉未可靠定位目标控件",
        "android 当前动作协议不支持 long_press",
        "android 当前不支持 mobile_use key",
        "android 当前不支持 system_button",
    )
}
