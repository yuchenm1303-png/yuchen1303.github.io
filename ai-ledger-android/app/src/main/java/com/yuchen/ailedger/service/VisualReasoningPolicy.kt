package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

enum class VisualReasoningDepth(val wireValue: String) {
    Fast("fast"),
    Normal("normal"),
    Deep("deep"),
}

enum class VisualReasoningTrigger(val wireValue: String) {
    RepeatedNoProgress("repeated_no_progress"),
    FirstNoProgress("first_no_progress"),
    SemanticAmbiguity("semantic_ambiguity"),
    SemanticRegression("semantic_regression"),
    EntityConflict("entity_conflict"),
    RouteConstraintConflict("route_constraint_conflict"),
    UserCorrection("user_correction"),
    UserSupplement("user_supplement"),
    CompletionCandidate("completion_candidate"),
    ConflictingEvidence("conflicting_evidence"),
    RecoveryMode("recovery_mode"),
    ExplorationBudgetPressure("exploration_budget_pressure"),
    ExecutionFailure("execution_failure"),
    FailedHypothesis("failed_hypothesis"),
    BlockedAction("blocked_action"),
}

data class VisualReasoningContext(
    val depth: VisualReasoningDepth,
    val triggers: List<VisualReasoningTrigger> = emptyList(),
    val noProgressCount: Int = 0,
    val sameActionCount: Int = 0,
    val executionFailureCount: Int = 0,
    val failedHypothesisCount: Int = 0,
    val blockedActionCount: Int = 0,
    val explorationPressureLevel: String = "low",
    val historyItems: Int = 2,
    val selfCheckPasses: Int = 0,
    val candidateHypothesisLimit: Int = 1,
    val freshObservationRequired: Boolean = false,
    val completionEvidenceStrict: Boolean = false,
    val directExecutionAllowed: Boolean = true,
) {
    val deepThinkingRequested: Boolean
        get() = depth == VisualReasoningDepth.Deep

    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "visual_reasoning_context_v1")
        put("depth", depth.wireValue)
        put("triggers", JSONArray(triggers.map { it.wireValue }))
        put("noProgressCount", noProgressCount)
        put("sameActionCount", sameActionCount)
        put("executionFailureCount", executionFailureCount)
        put("failedHypothesisCount", failedHypothesisCount)
        put("blockedActionCount", blockedActionCount)
        put("explorationPressureLevel", explorationPressureLevel)
        put("historyItems", historyItems)
        put("selfCheckPasses", selfCheckPasses)
        put("candidateHypothesisLimit", candidateHypothesisLimit)
        put("freshObservationRequired", freshObservationRequired)
        put("completionEvidenceStrict", completionEvidenceStrict)
        put("directExecutionAllowed", directExecutionAllowed)
        put("deepThinkingRequested", deepThinkingRequested)
    }

    fun toPromptLine(): String = buildString {
        append(PROMPT_PREFIX)
        append("depth=").append(depth.wireValue)
        // Hyphenated metadata cannot be mistaken for legacy no_progress execution evidence.
        append("|triggers=").append(triggers.joinToString(",") { it.wireValue.replace('_', '-') })
        append("|noProgressCount=").append(noProgressCount)
        append("|sameActionCount=").append(sameActionCount)
        append("|executionFailureCount=").append(executionFailureCount)
        append("|failedHypothesisCount=").append(failedHypothesisCount)
        append("|blockedActionCount=").append(blockedActionCount)
        append("|explorationPressureLevel=").append(explorationPressureLevel)
        append("|historyItems=").append(historyItems)
        append("|selfCheckPasses=").append(selfCheckPasses)
        append("|candidateHypothesisLimit=").append(candidateHypothesisLimit)
        append("|freshObservationRequired=").append(freshObservationRequired)
        append("|completionEvidenceStrict=").append(completionEvidenceStrict)
        append("|directExecutionAllowed=").append(directExecutionAllowed)
        append("|semanticDecisionOwner=gui_plus")
        append("|localSemanticDecision=false")
    }.take(VisualLoopSupport.MAX_RECENT_ACTION_CHARS)

    companion object {
        const val PROMPT_PREFIX = "visual_reasoning_context:v1|"
    }
}

/** Task-scoped projection only; semantic facts remain owned by VisualTaskMemory. */
internal object VisualReasoningRuntime {
    private val lock = Any()
    private var taskId: Long = 0L
    private var context: VisualReasoningContext? = null

    fun update(value: VisualReasoningContext) {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return
        synchronized(lock) {
            if (taskId != currentTaskId) {
                taskId = currentTaskId
                context = null
            }
            context = value
        }
    }

    fun currentOrNull(): VisualReasoningContext? {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return null
        return synchronized(lock) {
            context?.takeIf { taskId == currentTaskId }
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            taskId = 0L
            context = null
        }
    }

    private fun currentTaskIdOrZero(): Long =
        runCatching { AgentRuntimeController.currentTaskId() }.getOrDefault(0L)
}

internal object VisualReasoningPolicy {
    const val DEEP_REPLAN_PREFIX = "visual_replan_requested:reason=adaptive_reasoning_depth|"
    private const val RUNTIME_PREFIX = "visual_runtime_context:v2|"

    fun evaluate(
        memory: VisualTaskMemory,
        recentActions: List<String> = emptyList(),
    ): VisualReasoningContext {
        val activeActions = activeObjectiveWindow(recentActions)
        val runtimeState = activeActions.latestRuntimeState()
        val noProgressCount = activeActions.count { it.isNoProgressEvidence() }
        val executionFailureCount = activeActions.count { it.isExecutionFailureEvidence() }
        val sameActionCount = consecutiveSameExecutedActionCount(activeActions)
        val failedHypothesisCount = memory.failedHypotheses.size
        val blockedActionCount = memory.blockedActions.size
        val repeatedHypothesisFailure = memory.failedHypotheses.any { it.count >= 2 }
        val completionCandidate = activeActions.any { it.isCompletionCandidateEvidence() }
        val semanticAmbiguity = memory.progressStatus.contains("ambiguous", ignoreCase = true) ||
            activeActions.any { it.contains("semanticStatus=ambiguous", ignoreCase = true) }
        val semanticRegression = memory.progressStatus.contains("regression", ignoreCase = true) ||
            memory.progressStatus.contains("regressed", ignoreCase = true) ||
            activeActions.any { it.isSemanticRegressionEvidence() }
        val routeConflict = activeActions.any { it.isRouteConstraintConflictEvidence() } ||
            runtimeState?.surfaceState == "replanning"
        val entityConflict = activeActions.any { it.isEntityConflictEvidence() } ||
            runtimeState?.packageConflict == true
        val conflictingEvidence = activeActions.hasConflictingEvidence()
        val latestUpdateKind = memory.latestUserUpdate?.kind
        val userCorrection = memory.taskRevisionPending && (
            memory.currentMilestoneInvalidated || latestUpdateKind in setOf(
                VisualUserTaskUpdateKind.Correction,
                VisualUserTaskUpdateKind.GoalRevision,
                VisualUserTaskUpdateKind.CancelSubgoal,
            )
        )
        val userSupplement = memory.taskRevisionPending && !userCorrection && latestUpdateKind in setOf(
            VisualUserTaskUpdateKind.Supplement,
            VisualUserTaskUpdateKind.ManualStepCompleted,
        )
        val budgetKnown = !memory.legacyMode || memory.taskContract != null
        val budgetExhausted = budgetKnown && memory.remainingExplorationBudget <= 0
        val budgetTight = budgetKnown && memory.remainingExplorationBudget == 1
        val recovery = !memory.taskRevisionPending && (memory.replanRequested || memory.recoveryMode)

        val triggerSet = linkedSetOf<VisualReasoningTrigger>()
        when {
            noProgressCount >= 2 -> triggerSet += VisualReasoningTrigger.RepeatedNoProgress
            noProgressCount == 1 -> triggerSet += VisualReasoningTrigger.FirstNoProgress
        }
        if (semanticAmbiguity) triggerSet += VisualReasoningTrigger.SemanticAmbiguity
        if (semanticRegression) triggerSet += VisualReasoningTrigger.SemanticRegression
        if (entityConflict) triggerSet += VisualReasoningTrigger.EntityConflict
        if (routeConflict) triggerSet += VisualReasoningTrigger.RouteConstraintConflict
        if (userCorrection) triggerSet += VisualReasoningTrigger.UserCorrection
        if (userSupplement) triggerSet += VisualReasoningTrigger.UserSupplement
        if (completionCandidate) triggerSet += VisualReasoningTrigger.CompletionCandidate
        if (conflictingEvidence) triggerSet += VisualReasoningTrigger.ConflictingEvidence
        if (recovery) triggerSet += VisualReasoningTrigger.RecoveryMode
        if (budgetExhausted || budgetTight) triggerSet += VisualReasoningTrigger.ExplorationBudgetPressure
        if (executionFailureCount > 0) triggerSet += VisualReasoningTrigger.ExecutionFailure
        if (failedHypothesisCount > 0) triggerSet += VisualReasoningTrigger.FailedHypothesis
        if (blockedActionCount > 0) triggerSet += VisualReasoningTrigger.BlockedAction

        val hardDeep = noProgressCount >= 2 ||
            sameActionCount >= 2 ||
            semanticRegression ||
            entityConflict ||
            routeConflict ||
            userCorrection ||
            completionCandidate ||
            conflictingEvidence ||
            recovery ||
            budgetExhausted ||
            repeatedHypothesisFailure ||
            blockedActionCount > 0 ||
            executionFailureCount >= 2
        val normal = noProgressCount == 1 ||
            semanticAmbiguity ||
            userSupplement ||
            budgetTight ||
            executionFailureCount == 1 ||
            failedHypothesisCount == 1
        val depth = when {
            hardDeep -> VisualReasoningDepth.Deep
            normal -> VisualReasoningDepth.Normal
            else -> VisualReasoningDepth.Fast
        }
        val pressure = when {
            budgetExhausted || blockedActionCount > 0 || noProgressCount >= 2 || sameActionCount >= 2 -> "high"
            budgetTight || noProgressCount == 1 || executionFailureCount > 0 || semanticAmbiguity -> "medium"
            else -> "low"
        }
        return VisualReasoningContext(
            depth = depth,
            triggers = triggerSet.toList(),
            noProgressCount = noProgressCount,
            sameActionCount = sameActionCount,
            executionFailureCount = executionFailureCount,
            failedHypothesisCount = failedHypothesisCount,
            blockedActionCount = blockedActionCount,
            explorationPressureLevel = pressure,
            historyItems = if (depth == VisualReasoningDepth.Deep) 4 else 2,
            selfCheckPasses = when (depth) {
                VisualReasoningDepth.Fast -> 0
                VisualReasoningDepth.Normal -> 1
                VisualReasoningDepth.Deep -> 2
            },
            candidateHypothesisLimit = when (depth) {
                VisualReasoningDepth.Fast -> 1
                VisualReasoningDepth.Normal -> 2
                VisualReasoningDepth.Deep -> 3
            },
            freshObservationRequired = depth == VisualReasoningDepth.Deep || semanticAmbiguity,
            completionEvidenceStrict = depth == VisualReasoningDepth.Deep || completionCandidate,
            directExecutionAllowed = depth != VisualReasoningDepth.Deep,
        )
    }

    fun deepReplanLine(context: VisualReasoningContext): String? {
        if (!context.deepThinkingRequested) return null
        return buildString {
            append(DEEP_REPLAN_PREFIX)
            append("reasoningDepth=deep")
            append("|triggers=").append(context.triggers.joinToString(",") { it.wireValue })
            append("|semanticStatus=adaptive_deep")
            append("|requiresStrategyChange=true")
            append("|replanRequired=true")
            append("|routeRefreshRequested=false")
            append("|completionEvidenceStrict=").append(context.completionEvidenceStrict)
        }.take(VisualLoopSupport.MAX_RECENT_ACTION_CHARS)
    }

    private fun activeObjectiveWindow(recentActions: List<String>): List<String> {
        val filtered = recentActions.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { it.startsWith(VisualReasoningContext.PROMPT_PREFIX) }
            .filterNot { it.startsWith(DEEP_REPLAN_PREFIX) }
            .toList()
        val lastReset = filtered.indexOfLast { it.isConfirmedProgressReset() }
        return (if (lastReset >= 0) filtered.drop(lastReset + 1) else filtered)
            .takeLast(MAX_ACTIVE_ACTIONS)
    }

    private fun List<String>.latestRuntimeState(): RuntimeState? {
        val line = asReversed().firstOrNull { it.startsWith(RUNTIME_PREFIX) } ?: return null
        val fields = linkedMapOf<String, String>()
        line.split('|').drop(1).forEach { part ->
            val separator = part.indexOf('=')
            if (separator > 0) fields[part.substring(0, separator)] = part.substring(separator + 1)
        }
        val verified = fields["verifiedTargetPackage"].orEmpty().trim()
        val current = fields["currentPackage"].orEmpty().trim()
        return RuntimeState(
            surfaceState = fields["state"].orEmpty().trim().lowercase(),
            packageConflict = verified.isNotBlank() && current.isNotBlank() && verified != current,
        )
    }

    private fun String.isConfirmedProgressReset(): Boolean {
        val value = lowercase()
        return value.contains("open_app_package_verified") ||
            value.contains("visual_screen_changed") ||
            (value.contains("visual_execution_observed") &&
                value.contains("screenchanged=true") &&
                !value.contains("replanrequired=true"))
    }

    private fun String.isNoProgressEvidence(): Boolean {
        val value = lowercase()
        return value.contains("visual_no_progress") ||
            value.contains("semanticstatus=stalled") ||
            value.contains("screen_unchanged_unjudged") ||
            value.contains("no_progress") ||
            value.contains("no progress") ||
            (value.contains("visual_execution_observed") && value.contains("screenchanged=false"))
    }

    private fun String.isExecutionFailureEvidence(): Boolean {
        val value = lowercase()
        return value.contains(":failed:") ||
            value.contains("visual_action_retry") ||
            value.contains("visual_action_stale") ||
            value.contains("visual_local_retry") ||
            value.contains("execution_failed")
    }

    private fun String.isCompletionCandidateEvidence(): Boolean =
        contains("finish_verification_pending", ignoreCase = true) ||
            contains("completionCandidate=true", ignoreCase = true)

    private fun String.isSemanticRegressionEvidence(): Boolean {
        val value = lowercase()
        return value.contains("semanticstatus=regressed") ||
            value.contains("structuralregression=true") ||
            value.contains("verified_work_surface_lost")
    }

    private fun String.isRouteConstraintConflictEvidence(): Boolean {
        val value = lowercase()
        return value.contains("failureclass=structural_route") ||
            value.contains("target_surface_lost") ||
            value.contains("routeconstraintconflict=true") ||
            value.contains("routerefreshrequested=true")
    }

    private fun String.isEntityConflictEvidence(): Boolean {
        val value = lowercase()
        return value.contains("currentpackagematchesverifiedtarget=false") ||
            value.contains("entityconflict=true") ||
            value.contains("wrong_entity") ||
            value.contains("package_mismatch")
    }

    private fun List<String>.hasConflictingEvidence(): Boolean {
        val hasPositiveExecution = any {
            val value = it.lowercase()
            value.contains(":ok:") || value.contains("screenchanged=true")
        }
        val hasNegativeSemanticEvidence = any {
            val value = it.lowercase()
            value.contains("semanticstatus=ambiguous") ||
                value.contains("semanticstatus=regressed") ||
                value.contains("replanrequired=true") ||
                value.contains("failureclass=")
        }
        return hasPositiveExecution && hasNegativeSemanticEvidence
    }

    private fun consecutiveSameExecutedActionCount(actions: List<String>): Int {
        val signatures = actions.mapNotNull { it.executedActionSignatureOrNull() }
        val latest = signatures.lastOrNull() ?: return 0
        return signatures.asReversed().takeWhile { it == latest }.count()
    }

    private fun String.executedActionSignatureOrNull(): String? {
        val clean = trim()
        val signature = when {
            ":failed:" in clean -> clean.substringBefore(":failed:")
            ":retry:" in clean -> clean.substringBefore(":retry:")
            ":ok:" in clean -> clean.substringBefore(":ok:")
            else -> null
        }
        return signature?.take(180)?.takeIf(String::isNotBlank)
    }

    private data class RuntimeState(
        val surfaceState: String,
        val packageConflict: Boolean,
    )

    private const val MAX_ACTIVE_ACTIONS = 14
}
