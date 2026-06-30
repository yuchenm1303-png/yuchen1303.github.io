package com.yuchen.ailedger.service

import org.json.JSONObject

internal fun JSONObject.validateVisualActionIntent(
    step: JSONObject?,
    args: JSONObject,
    intent: JSONObject,
    contract: VisualTaskContract,
) {
    VisualTaskContractProtocol.validateActionIntent(
        actionType = step?.firstNonBlank("type", "action", "tool", "name").normalizeVisualWire(),
        purpose = step?.firstNonBlank("purpose") ?: intent.firstNonBlank("purpose")
            ?: args.firstNonBlank("purpose") ?: "",
        milestoneId = step?.firstNonBlank("milestoneId") ?: intent.firstNonBlank("milestoneId")
            ?: args.firstNonBlank("milestoneId") ?: "",
        expectedEvidence = (step?.stringList("expectedEvidence") ?: emptyList())
            .ifEmpty { intent.stringList("expectedEvidence") }
            .ifEmpty { args.stringList("expectedEvidence") },
        contract = contract,
    ).requireAccepted(this)
}

internal fun VisualTaskContractProtocol.Decision.requireAccepted(root: JSONObject) {
    if (!accepted) root.failVisualProtocol(code, message)
}

internal fun JSONObject.failVisualProtocol(code: String, message: String): Nothing {
    VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
        "visual_protocol_failure",
        JSONObject().apply {
            put("code", code); put("message", message); put("retryable", true)
            put("userHelpRequired", false); put("responseObservationId", responseObservationId())
            put("protocol", VisualTaskContractProtocol.PROMPT_LINE); put("semanticDecisionOwner", "gui_plus")
            put("localSemanticDecision", false)
        },
    )
    throw VisualAgentRequestException(
        httpStatus = 200,
        code = "visual_protocol_$code",
        retryable = true,
        backendMessage = "$message ${VisualTaskContractProtocol.PROMPT_LINE}",
    )
}

internal fun JSONObject.recordContractRejection(reason: String) {
    VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
        "task_contract_transaction_rejected",
        JSONObject().apply {
            put("reason", reason); put("committedStateChanged", false)
            put("completionCandidateProvisional", reason == "provisional_completion_candidate")
            put("visualDecisionOwner", "gui_plus"); put("responseObservationId", responseObservationId())
        },
    )
}

internal fun JSONObject.recordContractReuse(contract: VisualTaskContract) {
    VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
        "task_contract_reused",
        JSONObject().apply {
            put("taskRevision", contract.taskRevision)
            put("currentMilestoneId", contract.currentMilestoneId)
            put("milestoneCount", contract.milestones.size)
            put("responseObservationId", responseObservationId())
            put("incomingContractPresent", false)
            put("semanticDecisionOwner", "gui_plus")
            put("localSemanticDecision", false)
        },
    )
}

internal fun JSONObject.isWorkSurfaceResponse(): Boolean {
    val state = firstNonBlank("verifiedSurfaceState", "surfaceState")
        ?: optJSONObject("verifiedSurfaceProtocol")?.firstNonBlank("surfaceState", "state")
        ?: optJSONObject("runtimeExecutionContext")?.firstNonBlank("surfaceState", "state")
        ?: optJSONObject("data")?.firstNonBlank("verifiedSurfaceState", "surfaceState")
        ?: optJSONObject("result")?.firstNonBlank("verifiedSurfaceState", "surfaceState")
    return state.normalizeVisualWire() == "work_surface"
}

internal fun JSONObject.responseObservationId(): String =
    firstNonBlank("observationId", "responseObservationId")
        ?: visualStep()?.optJSONObject("args")?.firstNonBlank("responseObservationId") ?: ""

internal fun JSONObject.transactionRejectionReason(): String? {
    val step = visualStep() ?: return null
    val type = step.firstNonBlank("type", "action", "tool", "name").normalizeVisualWire()
    val args = step.optJSONObject("args") ?: step.optJSONObject("arguments") ?: JSONObject()
    if (type == "finish" || args.flexibleBoolean("completionCandidate") == true) {
        return "provisional_completion_candidate"
    }

    val rejected = args.firstNonBlank("rejectedActionType", "restoredRejectedActionType").normalizeVisualWire()
    val compact = optJSONObject("debug")?.optJSONObject("guiCompactAction")
        ?: optJSONObject("guiCompactAction")
        ?: optJSONObject("data")?.optJSONObject("debug")?.optJSONObject("guiCompactAction")
        ?: optJSONObject("result")?.optJSONObject("debug")?.optJSONObject("guiCompactAction")
    val compactType = compact?.firstNonBlank("a", "action", "type").normalizeVisualWire()
    return if (
        type == "wait" &&
        rejected == "tap_xy" &&
        compactType in setOf("tap_xy", "tap", "click", "press", "point")
    ) {
        "backend_action_rewrite_not_executed"
    } else {
        null
    }
}

internal fun JSONObject.visualStep(): JSONObject? = optJSONObject("agentStep") ?: optJSONObject("step")
    ?: optJSONObject("agentAction")?.optJSONObject("step")
    ?: optJSONObject("data")?.optJSONObject("agentStep") ?: optJSONObject("data")?.optJSONObject("step")
    ?: optJSONObject("result")?.optJSONObject("agentStep")

internal fun String?.normalizeVisualWire() = orEmpty().trim().lowercase().replace('-', '_')
