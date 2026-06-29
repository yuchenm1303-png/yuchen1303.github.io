package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

/** Cloud-provided semantic contract. Android stores and reports it but never invents task meaning. */
data class VisualTaskMilestone(
    val id: String,
    val title: String = "",
    val purpose: String = "",
    val successEvidence: List<String> = emptyList(),
    val failureEvidence: List<String> = emptyList(),
    val completed: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("purpose", purpose)
        put("successEvidence", JSONArray(successEvidence))
        put("failureEvidence", JSONArray(failureEvidence))
        put("completed", completed)
    }

    companion object {
        fun fromJson(value: JSONObject?): VisualTaskMilestone? {
            val item = value ?: return null
            val id = item.firstNonBlank("id", "milestoneId", "milestone") ?: return null
            return VisualTaskMilestone(
                id = id.take(100),
                title = item.firstNonBlank("title", "name").orEmpty().take(160),
                purpose = item.firstNonBlank("purpose", "goal", "description").orEmpty().take(240),
                successEvidence = item.stringList("successEvidence", "expectedEvidence", "evidence"),
                failureEvidence = item.stringList("failureEvidence", "wrongEvidence", "negativeEvidence"),
                completed = item.flexibleBoolean("completed") ?: item.flexibleBoolean("isComplete") ?: false,
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
    fun currentMilestone(): VisualTaskMilestone? = milestones.firstOrNull { it.id == currentMilestoneId }

    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", schema)
        put("originalGoal", originalGoal)
        put("currentMilestoneId", currentMilestoneId)
        put("milestones", JSONArray().apply { milestones.forEach { put(it.toJson()) } })
        put("completedMilestoneIds", JSONArray(completedMilestoneIds))
        put("explorationBudgetPerMilestone", explorationBudgetPerMilestone)
        put("legacyMode", legacyMode)
        put("taskRevision", taskRevision)
    }

    companion object {
        const val DEFAULT_EXPLORATION_BUDGET = 2

        fun fromJson(root: JSONObject?): VisualTaskContract? {
            root?.let { response ->
                VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
                    type = "model_response",
                    details = response,
                )
            }
            if (root == null) return null
            if (VisualUserTaskUpdateRuntime.hasUndispatchedRevision()) return null
            val containers = listOfNotNull(
                root.optJSONObject("taskContract"),
                root.optJSONObject("visualTaskContract"),
                root.optJSONObject("agentTaskContract"),
                root.optJSONObject("plan")?.optJSONObject("taskContract"),
                root.optJSONObject("data")?.optJSONObject("taskContract"),
                root.optJSONObject("result")?.optJSONObject("taskContract"),
                root.optJSONObject("agentMemory")?.optJSONObject("taskContract"),
            )
            val item = containers.firstOrNull() ?: return null
            val milestones = item.objectList("milestones", "steps")
                .mapNotNull(VisualTaskMilestone::fromJson)
                .distinctBy { it.id }
                .take(24)
            val currentId = item.firstNonBlank("currentMilestoneId", "milestoneId", "currentMilestone")
                ?: milestones.firstOrNull { !it.completed }?.id.orEmpty()
            val completed = (item.stringList("completedMilestoneIds", "completedMilestones") +
                milestones.filter { it.completed }.map { it.id })
                .distinct().take(24)
            val budget = item.optFlexibleInt("explorationBudgetPerMilestone")
                ?: item.optFlexibleInt("explorationBudget")
                ?: DEFAULT_EXPLORATION_BUDGET
            val revision = item.optFlexibleInt("taskRevision")
                ?: item.optFlexibleInt("userTaskRevision")
                ?: item.optFlexibleInt("revision")
                ?: 0
            return VisualTaskContract(
                originalGoal = item.firstNonBlank("originalGoal", "goal", "sourceGoal").orEmpty().take(240),
                currentMilestoneId = currentId.take(100),
                milestones = milestones,
                completedMilestoneIds = completed,
                explorationBudgetPerMilestone = budget.coerceIn(1, 4),
                schema = item.firstNonBlank("schema").orEmpty().ifBlank { "visual_task_contract_v1" }.take(80),
                legacyMode = item.flexibleBoolean("legacyMode") ?: false,
                taskRevision = revision.coerceAtLeast(0),
            )
        }
    }
}

data class VisualActionIntent(
    val purpose: String = "",
    val milestoneId: String = "",
    val expectedEvidence: List<String> = emptyList(),
    val failureEvidence: List<String> = emptyList(),
    val exploratory: Boolean = false,
    val reversible: Boolean = true,
    val confidence: Float? = null,
    val hypothesisId: String = "",
    val legacyMode: Boolean = true,
) {
    val hasSemanticContract: Boolean
        get() = !legacyMode

    fun toJson(): JSONObject = JSONObject().apply {
        put("purpose", purpose)
        put("milestoneId", milestoneId)
        put("expectedEvidence", JSONArray(expectedEvidence))
        put("failureEvidence", JSONArray(failureEvidence))
        put("exploratory", exploratory)
        put("reversible", reversible)
        confidence?.let { put("confidence", it) }
        put("hypothesisId", hypothesisId)
        put("legacyMode", legacyMode)
    }
}

data class VisualFailedHypothesis(
    val hypothesisId: String,
    val milestoneId: String,
    val pageStateId: String,
    val actionSignature: String,
    val actionCluster: String,
    val purpose: String,
    val failureReason: String,
    val count: Int = 1,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("hypothesisId", hypothesisId)
        put("milestoneId", milestoneId)
        put("pageStateId", pageStateId)
        put("actionSignature", actionSignature)
        put("actionCluster", actionCluster)
        put("purpose", purpose)
        put("failureReason", failureReason)
        put("count", count)
    }
}

data class VisualBlockedAction(
    val milestoneId: String,
    val pageStateId: String,
    val actionCluster: String,
    val hypothesisId: String,
    val reason: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("milestoneId", milestoneId)
        put("pageStateId", pageStateId)
        put("actionCluster", actionCluster)
        put("hypothesisId", hypothesisId)
        put("reason", reason)
    }
}

data class VisualPageState(
    val id: String,
    val packageName: String,
    val summary: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("packageName", packageName)
        put("summary", summary)
    }
}

data class VisualTaskMemory(
    val originalGoal: String = "",
    val currentMilestoneId: String = "",
    val completedMilestoneIds: List<String> = emptyList(),
    val currentPage: VisualPageState? = null,
    val confirmedFacts: List<String> = emptyList(),
    val failedHypotheses: List<VisualFailedHypothesis> = emptyList(),
    val blockedActions: List<VisualBlockedAction> = emptyList(),
    val remainingExplorationBudget: Int = 0,
    val lastConfirmedPage: VisualPageState? = null,
    val progressStatus: String = "unknown",
    val replanRequested: Boolean = false,
    val recoveryMode: Boolean = false,
    val legacyMode: Boolean = true,
    val taskContract: VisualTaskContract? = null,
    val taskRevision: Int = 0,
    val taskRevisionPending: Boolean = false,
    val currentMilestoneInvalidated: Boolean = false,
    val latestUserUpdate: VisualUserTaskUpdate? = null,
    val userUpdateHistory: List<VisualUserTaskUpdate> = emptyList(),
    val reasoningContext: VisualReasoningContext? = null,
) {
    fun toJson(): JSONObject {
        val appliedRevision = maxOf(taskRevision, taskContract?.taskRevision ?: 0)
        val runtimeUpdates = VisualUserTaskUpdateRuntime.updatesAfter(appliedRevision)
        val effectiveHistory = (userUpdateHistory + runtimeUpdates)
            .distinctBy { it.revision }
            .sortedBy { it.revision }
            .takeLast(8)
        val effectiveLatest = effectiveHistory.lastOrNull() ?: latestUserUpdate
        val effectiveRevision = maxOf(appliedRevision, effectiveLatest?.revision ?: 0)
        val runtimeInvalidation = runtimeUpdates.any { it.invalidatesCurrentMilestone }
        val effectiveInvalidation = currentMilestoneInvalidated || runtimeInvalidation
        val effectivePending = taskRevisionPending || VisualUserTaskUpdateRuntime.isRevisionPending(effectiveRevision)
        val effectiveReplan = replanRequested || effectivePending
        val effectiveProgress = if (effectivePending && progressStatus == "unknown") {
            "user_update_pending_replan"
        } else {
            progressStatus
        }
        val effectiveFailedHypotheses = if (effectiveInvalidation) emptyList() else failedHypotheses
        val effectiveBlockedActions = if (runtimeUpdates.isNotEmpty() || effectivePending) emptyList() else blockedActions
        val effectiveContract = taskContract?.copy(taskRevision = maxOf(taskContract.taskRevision, effectiveRevision))
        val effectiveReasoning = reasoningContext
            ?: VisualReasoningRuntime.currentOrNull()
            ?: VisualReasoningPolicy.evaluate(
                copy(
                    failedHypotheses = effectiveFailedHypotheses,
                    blockedActions = effectiveBlockedActions,
                    progressStatus = effectiveProgress,
                    replanRequested = effectiveReplan,
                    recoveryMode = recoveryMode || effectivePending,
                    taskContract = effectiveContract,
                    taskRevision = effectiveRevision,
                    taskRevisionPending = effectivePending,
                    currentMilestoneInvalidated = effectiveInvalidation,
                    latestUserUpdate = effectiveLatest,
                    userUpdateHistory = effectiveHistory,
                    reasoningContext = null,
                ),
            )

        return JSONObject().apply {
            put("schema", "visual_task_memory_v3_adaptive_reasoning")
            put("originalGoal", originalGoal)
            put("currentMilestoneId", currentMilestoneId)
            put("completedMilestoneIds", JSONArray(completedMilestoneIds))
            put("currentPage", currentPage?.toJson() ?: JSONObject.NULL)
            put("confirmedFacts", JSONArray(confirmedFacts))
            put("failedHypotheses", JSONArray().apply { effectiveFailedHypotheses.forEach { put(it.toJson()) } })
            put("blockedActions", JSONArray().apply { effectiveBlockedActions.forEach { put(it.toJson()) } })
            put("remainingExplorationBudget", remainingExplorationBudget)
            put("lastConfirmedPage", lastConfirmedPage?.toJson() ?: JSONObject.NULL)
            put("progressStatus", effectiveProgress)
            put("replanRequested", effectiveReplan)
            put("recoveryMode", recoveryMode || effectivePending)
            put("legacyMode", legacyMode)
            put("taskContract", effectiveContract?.toJson() ?: JSONObject.NULL)
            put("taskRevision", effectiveRevision)
            put("taskRevisionPending", effectivePending)
            put("currentMilestoneInvalidated", effectiveInvalidation)
            put("latestUserUpdate", effectiveLatest?.toJson() ?: JSONObject.NULL)
            put("userUpdateHistory", JSONArray().apply { effectiveHistory.forEach { put(it.toJson()) } })
            put("reasoningContext", effectiveReasoning.toJson())
            put("reasoningDepth", effectiveReasoning.depth.wireValue)
            put("reasoningTriggers", JSONArray(effectiveReasoning.triggers.map { it.wireValue }))
        }
    }
}

internal fun JSONObject.firstNonBlank(vararg names: String): String? {
    for (name in names) {
        if (!has(name) || isNull(name)) continue
        optString(name).trim().takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

internal fun JSONObject.stringList(vararg names: String): List<String> {
    for (name in names) {
        if (!has(name) || isNull(name)) continue
        return when (val value = opt(name)) {
            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    value.optString(index).trim().takeIf { it.isNotBlank() }?.take(160)?.let(::add)
                }
            }
            is String -> value.split('|', ';', '；', '\n')
                .map { it.trim().take(160) }
                .filter { it.isNotBlank() }
            else -> emptyList()
        }.distinct().take(16)
    }
    return emptyList()
}

internal fun JSONObject.objectList(vararg names: String): List<JSONObject> {
    for (name in names) {
        val array = optJSONArray(name) ?: continue
        return buildList {
            for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
        }
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
