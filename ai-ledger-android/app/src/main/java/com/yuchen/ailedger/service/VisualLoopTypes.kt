package com.yuchen.ailedger.service

import kotlin.math.roundToInt

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

internal object VisualActionValidator {
    fun validate(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        runtime: VisualAgentRuntimeContext? = null,
    ): VisualActionValidation {
        if (step.type !in CloudAgentStep.supportedTypes) return structural("Unsupported visual action: ${step.type}")
        if (VisualUserTaskUpdateRuntime.hasUndispatchedRevision()) {
            return VisualActionValidation(
                ok = false,
                message = "userTaskRevisionStaleResponse=true; a newer user instruction arrived after this model request started. Do not execute the stale action; re-observe and plan again with the latest authoritative user turn.",
                failureClass = VisualFailureClass.VisualLocal,
            )
        }
        if (isRepairableGuiProtocolFailure(step)) {
            return VisualActionValidation(
                ok = false,
                message = "protocolRepairRequired=true; GUI Plus returned a malformed or non-executable mobile_use protocol result. Keep the original task and current work surface, inspect the fresh screenshot, and return exactly one supported official mobile_use action instead of asking the user for help.",
                failureClass = VisualFailureClass.VisualLocal,
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
            return VisualActionValidation(false, "Invalid tap coordinates.")
        }
        if (step.type == "input_text" && step.text.isNullOrBlank()) {
            return VisualActionValidation(false, "Input text is empty.")
        }
        VisualUserTaskUpdateRuntime.markDispatchedPlanValidated()
        return VisualActionValidation(true)
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

    /**
     * Coarse physical-coordinate cluster used only when semantic purpose/hypothesis is unavailable.
     * Nearby taps share one cluster so a failed action cannot evade blocking by moving a few pixels.
     */
    fun actionClusterSignature(step: CloudAgentStep): String {
        if (step.type != "tap_xy") return actionSignature(step)
        val x = step.x ?: return actionSignature(step)
        val y = step.y ?: return actionSignature(step)
        return "tap_xy|${(x / LEGACY_TAP_CLUSTER_PX).roundToInt()}|${(y / LEGACY_TAP_CLUSTER_PX).roundToInt()}"
    }

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
        return REPAIRABLE_GUI_PROTOCOL_MARKERS.any(reason::contains)
    }

    private fun structural(message: String) =
        VisualActionValidation(false, message, VisualFailureClass.StructuralRoute)

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

    private val REPAIRABLE_GUI_PROTOCOL_MARKERS = listOf(
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
    private val PRE_WORK_SURFACE_ACTIONS = CloudAgentStep.deviceToolTypes + "need_user_help"
    private const val LEGACY_TAP_CLUSTER_PX = 160f
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
