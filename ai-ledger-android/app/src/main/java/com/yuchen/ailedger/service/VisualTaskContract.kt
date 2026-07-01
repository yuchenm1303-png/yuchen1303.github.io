package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

data class VisualTaskMilestone(
    val id: String,
    val title: String = "",
    val purpose: String = "",
    val successEvidence: List<String> = emptyList(),
    val failureEvidence: List<String> = emptyList(),
    val completed: Boolean = false,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("purpose", purpose)
        put("successEvidence", JSONArray(successEvidence)); put("failureEvidence", JSONArray(failureEvidence))
        put("completed", completed)
    }
    companion object {
        fun fromJson(value: JSONObject?): VisualTaskMilestone? {
            val item = value ?: return null
            val id = item.firstNonBlank("id", "milestoneId", "milestone") ?: return null
            return VisualTaskMilestone(
                id.take(100),
                item.firstNonBlank("title", "name").orEmpty().take(160),
                item.firstNonBlank("purpose", "goal", "description").orEmpty().take(240),
                item.stringList("successEvidence", "expectedEvidence", "evidence"),
                item.stringList("failureEvidence", "wrongEvidence", "negativeEvidence"),
                item.flexibleBoolean("completed") ?: item.flexibleBoolean("isComplete") ?: false,
            )
        }
    }
}

data class VisualTaskContract(
    val originalGoal: String = "",
    val currentMilestoneId: String = "",
    val milestones: List<VisualTaskMilestone> = emptyList(),
    val completedMilestoneIds: List<String> = emptyList(),
    val explorationBudgetPerMilestone: Int = DEFAULT_EXPLORATION_BUDGET,
    val schema: String = "visual_task_contract_v1",
    val legacyMode: Boolean = false,
    val taskRevision: Int = 0,
) {
    fun currentMilestone() = milestones.firstOrNull { it.id == currentMilestoneId }
    fun toJson() = JSONObject().apply {
        put("schema", schema); put("originalGoal", originalGoal); put("currentMilestoneId", currentMilestoneId)
        put("milestones", JSONArray().apply { milestones.forEach { put(it.toJson()) } })
        put("completedMilestoneIds", JSONArray(completedMilestoneIds))
        put("explorationBudgetPerMilestone", explorationBudgetPerMilestone)
        put("legacyMode", legacyMode); put("taskRevision", taskRevision)
    }

    companion object {
        const val DEFAULT_EXPLORATION_BUDGET = 2
        private const val UNIFIED_EXECUTION_PERMIT_VERSION = "visual_execution_permit_v2"

        fun fromJson(
            root: JSONObject?,
            committedContract: VisualTaskContract? = VisualCommittedTaskContractRuntime.currentOrNull(),
        ): VisualTaskContract? {
            root?.let { VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent("model_response", it) }
            root ?: return null
            if (VisualUserTaskUpdateRuntime.hasUndispatchedRevision()) return null

            val step = root.visualStep()
            val type = step?.firstNonBlank("type", "action", "tool", "name").normalizeWire()
            val args = step?.optJSONObject("args") ?: step?.optJSONObject("arguments") ?: JSONObject()
            val intent = step?.optJSONObject("actionIntent") ?: args.optJSONObject("actionIntent") ?: JSONObject()
            val rejection = root.transactionRejectionReason()
            val workSurface = root.isWorkSurfaceResponse()
            val unifiedExecutionPermit = args.firstNonBlank("executionPermitVersion") == UNIFIED_EXECUTION_PERMIT_VERSION

            if (rejection != null) {
                root.recordContractRejection(rejection)
                if (rejection == "provisional_completion_candidate" || rejection == "backend_action_rewrite_not_executed") return null
                if (workSurface && type != "open_app") root.failProtocol(rejection, "Cloud response cannot mutate committed task state.")
                return null
            }

            val item = listOfNotNull(
                root.optJSONObject("taskContract"), root.optJSONObject("visualTaskContract"),
                root.optJSONObject("agentTaskContract"), root.optJSONObject("plan")?.optJSONObject("taskContract"),
                root.optJSONObject("data")?.optJSONObject("taskContract"),
                root.optJSONObject("result")?.optJSONObject("taskContract"),
                root.optJSONObject("agentMemory")?.optJSONObject("taskContract"),
            ).firstOrNull()
            if (item == null) {
                if (workSurface && type != "open_app") {
                    if (unifiedExecutionPermit) {
                        root.recordPermitOwnedContractValidation(args)
                        return null
                    }
                    val committed = committedContract ?: root.failProtocol(
                        "task_contract_required",
                        "GUI Plus must establish the full ordered task contract before the first legacy work-surface action.",
                    )
                    val contractDecision = VisualTaskContractProtocol.validateContract(committed)
                    if (!contractDecision.accepted) {
                        root.recordContractRejection(contractDecision.code)
                        root.failProtocol(contractDecision.code, contractDecision.message)
                    }
                    root.validateActionIntent(step, args, intent, committed)
                    root.recordContractReuse(committed)
                }
                return null
            }

            val milestones = item.objectList("milestones", "steps")
                .mapNotNull(VisualTaskMilestone::fromJson).distinctBy { it.id }.take(24)
            val currentId = item.firstNonBlank("currentMilestoneId", "milestoneId", "currentMilestone")
                ?: milestones.firstOrNull { !it.completed }?.id.orEmpty()
            val contract = VisualTaskContract(
                originalGoal = item.firstNonBlank("originalGoal", "goal", "sourceGoal").orEmpty(),
                currentMilestoneId = currentId.take(100),
                milestones = milestones,
                completedMilestoneIds = (item.stringList("completedMilestoneIds", "completedMilestones") +
                    milestones.filter { it.completed }.map { it.id }).distinct().take(24),
                explorationBudgetPerMilestone = (item.optFlexibleInt("explorationBudgetPerMilestone")
                    ?: item.optFlexibleInt("explorationBudget") ?: DEFAULT_EXPLORATION_BUDGET).coerceIn(1, 4),
                schema = item.firstNonBlank("schema").orEmpty().ifBlank { "visual_task_contract_v1" }.take(80),
                legacyMode = item.flexibleBoolean("legacyMode") ?: false,
                taskRevision = (item.optFlexibleInt("taskRevision") ?: item.optFlexibleInt("userTaskRevision")
                    ?: item.optFlexibleInt("revision") ?: 0).coerceAtLeast(0),
            )
            val contractDecision = VisualTaskContractProtocol.validateContract(contract)
            if (!contractDecision.accepted) {
                root.recordContractRejection(contractDecision.code)
                if (workSurface && type != "open_app") root.failProtocol(contractDecision.code, contractDecision.message)
                return null
            }
            if (workSurface && type != "open_app" && !unifiedExecutionPermit) {
                root.validateActionIntent(step, args, intent, contract)
            }
            if (workSurface && unifiedExecutionPermit) {
                root.recordPermitOwnedContractValidation(args)
            }
            return contract
        }

        private fun JSONObject.validateActionIntent(
            step: JSONObject?,
            args: JSONObject,
            intent: JSONObject,
            contract: VisualTaskContract,
        ) {
            VisualTaskContractProtocol.validateActionIntent(
                actionType = step?.firstNonBlank("type", "action", "tool", "name").normalizeWire(),
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

        private fun VisualTaskContractProtocol.Decision.requireAccepted(root: JSONObject) {
            if (!accepted) root.failProtocol(code, message)
        }

        private fun JSONObject.failProtocol(code: String, message: String): Nothing {
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

        private fun JSONObject.recordContractRejection(reason: String) {
            VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
                "task_contract_transaction_rejected",
                JSONObject().apply {
                    put("reason", reason); put("committedStateChanged", false)
                    put("completionCandidateProvisional", reason == "provisional_completion_candidate")
                    put("visualDecisionOwner", "gui_plus"); put("responseObservationId", responseObservationId())
                },
            )
        }

        private fun JSONObject.recordContractReuse(contract: VisualTaskContract) {
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

        private fun JSONObject.recordPermitOwnedContractValidation(args: JSONObject) {
            VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
                "task_contract_validation_delegated_to_permit",
                JSONObject().apply {
                    put("permitVersion", args.optString("executionPermitVersion"))
                    put("permitId", args.optString("executionPermitId"))
                    put("permitKind", args.optString("executionPermitKind"))
                    put("responseObservationId", responseObservationId())
                    put("backendSemanticValidationRequired", true)
                    put("androidSemanticValidation", false)
                    put("androidExecutionGateRequired", true)
                },
            )
        }

        private fun JSONObject.isWorkSurfaceResponse(): Boolean {
            val state = firstNonBlank("verifiedSurfaceState", "surfaceState")
                ?: optJSONObject("verifiedSurfaceProtocol")?.firstNonBlank("surfaceState", "state")
                ?: optJSONObject("runtimeExecutionContext")?.firstNonBlank("surfaceState", "state")
                ?: optJSONObject("data")?.firstNonBlank("verifiedSurfaceState", "surfaceState")
                ?: optJSONObject("result")?.firstNonBlank("verifiedSurfaceState", "surfaceState")
            return state.normalizeWire() == "work_surface"
        }

        private fun JSONObject.responseObservationId(): String =
            firstNonBlank("observationId", "responseObservationId")
                ?: visualStep()?.optJSONObject("args")?.firstNonBlank("responseObservationId") ?: ""

        private fun JSONObject.transactionRejectionReason(): String? {
            val step = visualStep() ?: return null
            val type = step.firstNonBlank("type", "action", "tool", "name").normalizeWire()
            val args = step.optJSONObject("args") ?: step.optJSONObject("arguments") ?: JSONObject()
            if (type == "finish" || args.flexibleBoolean("completionCandidate") == true) {
                return "provisional_completion_candidate"
            }
            val rejected = args.firstNonBlank("rejectedActionType", "restoredRejectedActionType").normalizeWire()
            val compact = optJSONObject("debug")?.optJSONObject("guiCompactAction")
                ?: optJSONObject("guiCompactAction")
                ?: optJSONObject("data")?.optJSONObject("debug")?.optJSONObject("guiCompactAction")
                ?: optJSONObject("result")?.optJSONObject("debug")?.optJSONObject("guiCompactAction")
            val compactType = compact?.firstNonBlank("a", "action", "type").normalizeWire()
            return if (type == "wait" && rejected == "tap_xy" && compactType in
                setOf("tap_xy", "tap", "click", "press", "point")) "backend_action_rewrite_not_executed" else null
        }

        private fun JSONObject.visualStep(): JSONObject? = optJSONObject("agentStep") ?: optJSONObject("step")
            ?: optJSONObject("agentAction")?.optJSONObject("step")
            ?: optJSONObject("data")?.optJSONObject("agentStep") ?: optJSONObject("data")?.optJSONObject("step")
            ?: optJSONObject("result")?.optJSONObject("agentStep")

        private fun String?.normalizeWire() = orEmpty().trim().lowercase().replace('-', '_')
    }
}

data class VisualActionIntent(
    val purpose: String = "", val milestoneId: String = "",
    val expectedEvidence: List<String> = emptyList(), val failureEvidence: List<String> = emptyList(),
    val exploratory: Boolean = false, val reversible: Boolean = true, val confidence: Float? = null,
    val hypothesisId: String = "", val legacyMode: Boolean = true,
) {
    val hasSemanticContract get() = !legacyMode
    fun toJson() = JSONObject().apply {
        put("purpose", purpose); put("milestoneId", milestoneId)
        put("expectedEvidence", JSONArray(expectedEvidence)); put("failureEvidence", JSONArray(failureEvidence))
        put("exploratory", exploratory); put("reversible", reversible); confidence?.let { put("confidence", it) }
        put("hypothesisId", hypothesisId); put("legacyMode", legacyMode)
    }
}

data class VisualFailedHypothesis(
    val hypothesisId: String, val milestoneId: String, val pageStateId: String,
    val actionSignature: String, val actionCluster: String, val purpose: String,
    val failureReason: String, val count: Int = 1,
) {
    fun toJson() = JSONObject().apply {
        put("hypothesisId", hypothesisId); put("milestoneId", milestoneId); put("pageStateId", pageStateId)
        put("actionSignature", actionSignature); put("actionCluster", actionCluster); put("purpose", purpose)
        put("failureReason", failureReason); put("count", count)
    }
}

data class VisualBlockedAction(
    val milestoneId: String, val pageStateId: String, val actionCluster: String,
    val hypothesisId: String, val reason: String,
) {
    fun toJson() = JSONObject().apply {
        put("milestoneId", milestoneId); put("pageStateId", pageStateId); put("actionCluster", actionCluster)
        put("hypothesisId", hypothesisId); put("reason", reason)
    }
}

data class VisualPageState(val id: String, val packageName: String, val summary: String) {
    fun toJson() = JSONObject().apply { put("id", id); put("packageName", packageName); put("summary", summary) }
}

data class VisualTaskMemory(
    val originalGoal: String = "", val currentMilestoneId: String = "",
    val completedMilestoneIds: List<String> = emptyList(), val currentPage: VisualPageState? = null,
    val confirmedFacts: List<String> = emptyList(), val failedHypotheses: List<VisualFailedHypothesis> = emptyList(),
    val blockedActions: List<VisualBlockedAction> = emptyList(), val remainingExplorationBudget: Int = 0,
    val lastConfirmedPage: VisualPageState? = null, val progressStatus: String = "unknown",
    val replanRequested: Boolean = false, val recoveryMode: Boolean = false, val legacyMode: Boolean = true,
    val taskContract: VisualTaskContract? = null, val taskRevision: Int = 0,
    val taskRevisionPending: Boolean = false, val currentMilestoneInvalidated: Boolean = false,
    val latestUserUpdate: VisualUserTaskUpdate? = null,
    val userUpdateHistory: List<VisualUserTaskUpdate> = emptyList(),
    val reasoningContext: VisualReasoningContext? = null,
) {
    fun toJson(): JSONObject {
        val appliedRevision = maxOf(taskRevision, taskContract?.taskRevision ?: 0)
        val runtimeUpdates = VisualUserTaskUpdateRuntime.updatesAfter(appliedRevision)
        val history = (userUpdateHistory + runtimeUpdates).distinctBy { it.revision }.sortedBy { it.revision }.takeLast(8)
        val latest = history.lastOrNull() ?: latestUserUpdate
        val revision = maxOf(appliedRevision, latest?.revision ?: 0)
        val invalidated = currentMilestoneInvalidated || runtimeUpdates.any { it.invalidatesCurrentMilestone }
        val pending = taskRevisionPending || VisualUserTaskUpdateRuntime.isRevisionPending(revision)
        val replan = replanRequested || pending
        val progress = if (pending && progressStatus == "unknown") "user_update_pending_replan" else progressStatus
        val contract = taskContract?.copy(taskRevision = maxOf(taskContract.taskRevision, revision))
        val reasoning = reasoningContext ?: VisualReasoningRuntime.currentOrNull() ?: VisualReasoningPolicy.evaluate(
            copy(
                failedHypotheses = emptyList(), blockedActions = emptyList(), progressStatus = progress,
                replanRequested = replan, recoveryMode = recoveryMode || pending, taskContract = contract,
                taskRevision = revision, taskRevisionPending = pending,
                currentMilestoneInvalidated = invalidated, latestUserUpdate = latest,
                userUpdateHistory = history, reasoningContext = null,
            ),
        )
        return JSONObject().apply {
            put("schema", "visual_task_memory_v5_transactional_visual_authority")
            put("originalGoal", originalGoal); put("currentMilestoneId", currentMilestoneId)
            put("completedMilestoneIds", JSONArray(completedMilestoneIds)); put("currentPage", currentPage?.toJson() ?: JSONObject.NULL)
            put("confirmedFacts", JSONArray(confirmedFacts)); put("failedHypotheses", JSONArray()); put("blockedActions", JSONArray())
            put("remainingExplorationBudget", remainingExplorationBudget); put("lastConfirmedPage", lastConfirmedPage?.toJson() ?: JSONObject.NULL)
            put("progressStatus", progress); put("replanRequested", replan); put("recoveryMode", recoveryMode || pending)
            put("legacyMode", legacyMode); put("taskContract", contract?.toJson() ?: JSONObject.NULL); put("taskRevision", revision)
            put("taskRevisionPending", pending); put("currentMilestoneInvalidated", invalidated)
            put("latestUserUpdate", latest?.toJson() ?: JSONObject.NULL)
            put("userUpdateHistory", JSONArray().apply { history.forEach { put(it.toJson()) } })
            put("reasoningContext", reasoning.toJson()); put("reasoningDepth", reasoning.depth.wireValue)
            put("reasoningTriggers", JSONArray(reasoning.triggers.map { it.wireValue }))
            put("taskContractProtocol", VisualTaskContractProtocol.PROMPT_LINE)
            put("semanticDecisionOwner", "gui_plus"); put("localSemanticDecision", false)
            put("localProgressClassification", false); put("executionLedgerOnly", true)
            put("transactionalCompletion", true); put("provisionalStateCommitted", false)
        }
    }
}

internal fun JSONObject.firstNonBlank(vararg names: String): String? {
    for (name in names) if (has(name) && !isNull(name)) optString(name).trim().takeIf { it.isNotBlank() }?.let { return it }
    return null
}

internal fun JSONObject.stringList(vararg names: String): List<String> {
    for (name in names) {
        if (!has(name) || isNull(name)) continue
        return when (val value = opt(name)) {
            is JSONArray -> buildList {
                for (index in 0 until value.length()) value.optString(index).trim()
                    .takeIf { it.isNotBlank() }?.take(160)?.let(::add)
            }
            is String -> value.split('|', ';', '；', '\n').map { it.trim().take(160) }.filter { it.isNotBlank() }
            else -> emptyList()
        }.distinct().take(16)
    }
    return emptyList()
}

internal fun JSONObject.objectList(vararg names: String): List<JSONObject> {
    for (name in names) optJSONArray(name)?.let { array ->
        return buildList { for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add) }
    }
    return emptyList()
}

internal fun JSONObject.flexibleBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    return when (val raw = opt(name)) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> when (raw.trim().lowercase()) {
            "true", "1", "yes", "on", "enabled" -> true
            "false", "0", "no", "off", "disabled" -> false
            else -> null
        }
        else -> null
    }
}

private fun JSONObject.optFlexibleInt(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return when (val raw = opt(name)) {
        is Number -> raw.toInt()
        is String -> raw.trim().toIntOrNull()
        else -> null
    }
}
