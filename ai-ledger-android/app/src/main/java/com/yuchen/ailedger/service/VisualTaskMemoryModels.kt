package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

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
