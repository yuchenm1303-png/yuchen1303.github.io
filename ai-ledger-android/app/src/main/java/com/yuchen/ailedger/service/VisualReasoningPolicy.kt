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
    RepeatedAction("repeated_action"),
    RouteCycle("route_cycle"),
    ProvisionalStateRollback("provisional_state_rollback"),
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
    val routeCycleLength: Int = 0,
    val provisionalRollbackCount: Int = 0,
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
        put("schema", "visual_reasoning_context_v3_transaction_watchdog")
        put("depth", depth.wireValue)
        put("triggers", JSONArray(triggers.map { it.wireValue }))
        put("noProgressCount", noProgressCount)
        put("sameActionCount", sameActionCount)
        put("routeCycleLength", routeCycleLength)
        put("provisionalRollbackCount", provisionalRollbackCount)
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
        put("semanticDecisionOwner", "gui_plus")
        put("localProgressClassification", false)
        put("transactionalState", true)
    }

    fun toPromptLine(): String = buildString {
        append(PROMPT_PREFIX)
        append("depth=").append(depth.wireValue)
        append("|triggers=").append(triggers.joinToString(",") { trigger ->
            when (trigger) {
                VisualReasoningTrigger.BlockedAction -> "action-suppressed"
                else -> trigger.wireValue.replace('_', '-')
            }
        })
        append("|noProgressCount=").append(noProgressCount)
        append("|sameActionCount=").append(sameActionCount)
        append("|routeCycleLength=").append(routeCycleLength)
        append("|provisionalRollbackCount=").append(provisionalRollbackCount)
        append("|executionFailureCount=").append(executionFailureCount)
        append("|failedHypothesisCount=0")
        append("|suppressedActionCount=0")
        append("|explorationPressureLevel=").append(explorationPressureLevel)
        append("|historyItems=").append(historyItems)
        append("|selfCheckPasses=").append(selfCheckPasses)
        append("|candidateHypothesisLimit=").append(candidateHypothesisLimit)
        append("|freshObservationRequired=").append(freshObservationRequired)
        append("|completionEvidenceStrict=").append(completionEvidenceStrict)
        append("|directExecutionAllowed=").append(directExecutionAllowed)
        append("|semanticDecisionOwner=gui_plus")
        append("|localSemanticDecision=false")
        append("|localProgressClassification=false")
        append("|transactionalState=true")
    }.take(VisualLoopSupport.MAX_RECENT_ACTION_CHARS)

    companion object {
        const val PROMPT_PREFIX = "visual_reasoning_context:v3|"
    }
}

/** Task-scoped projection only; visual meaning remains owned by GUI Plus. */
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

/**
 * Adaptive execution watchdog.
 *
 * It reacts only to mechanical loop and transaction evidence: repeated actions, repeated route
 * sequences, explicit re-observation, execution failures, route/package conflicts, completion
 * candidate rollback and authoritative user updates. Frame differences, accessibility-tree
 * differences and local page semantics never classify progress.
 */
internal object VisualReasoningPolicy {
    const val DEEP_REPLAN_PREFIX = "visual_replan_requested:reason=adaptive_reasoning_depth|"
    private const val RUNTIME_PREFIX = "visual_runtime_context:v2|"

    fun evaluate(
        memory: VisualTaskMemory,
        recentActions: List<String> = emptyList(),
    ): VisualReasoningContext {
        val activeActions = activeObjectiveWindow(recentActions)
        val events = activeActions.mapNotNull { it.toExecutionEventOrNull() }
        val runtimeState = activeActions.latestRuntimeState()
        val noProgressCount = consecutiveReobserveOrFailureCount(events, activeActions)
        val executionFailureCount = consecutiveFailureCount(events, activeActions)
        val sameActionCount = consecutiveSameExecutedActionCount(events)
        val routeCycleLength = repeatedRouteCycleLength(events)
        val completionCandidate = activeActions.hasLiveCompletionCandidate()
        val provisionalRollbackCount = activeActions.provisionalRollbackCount()
        val routeConflict = activeActions.any { it.isRouteConstraintConflictEvidence() } ||
            runtimeState?.surfaceState == "replanning"
        val entityConflict = activeActions.any { it.isEntityConflictEvidence() } ||
            runtimeState?.packageConflict == true
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
        val recovery = !memory.taskRevisionPending && (memory.replanRequested || memory.recoveryMode)

        val triggerSet = linkedSetOf<VisualReasoningTrigger>()
        when {
            noProgressCount >= 2 -> triggerSet += VisualReasoningTrigger.RepeatedNoProgress
            noProgressCount == 1 -> triggerSet += VisualReasoningTrigger.FirstNoProgress
        }
        if (sameActionCount >= 2) triggerSet += VisualReasoningTrigger.RepeatedAction
        if (routeCycleLength > 0) triggerSet += VisualReasoningTrigger.RouteCycle
        if (provisionalRollbackCount > 0) triggerSet += VisualReasoningTrigger.ProvisionalStateRollback
        if (entityConflict) triggerSet += VisualReasoningTrigger.EntityConflict
        if (routeConflict) triggerSet += VisualReasoningTrigger.RouteConstraintConflict
        if (userCorrection) triggerSet += VisualReasoningTrigger.UserCorrection
        if (userSupplement) triggerSet += VisualReasoningTrigger.UserSupplement
        if (completionCandidate) triggerSet += VisualReasoningTrigger.CompletionCandidate
        if (recovery) triggerSet += VisualReasoningTrigger.RecoveryMode
        if (executionFailureCount > 0) triggerSet += VisualReasoningTrigger.ExecutionFailure

        val hardDeep = noProgressCount >= 2 ||
            sameActionCount >= 2 ||
            routeCycleLength > 0 ||
            provisionalRollbackCount > 0 ||
            entityConflict ||
            routeConflict ||
            userCorrection ||
            completionCandidate ||
            recovery ||
            executionFailureCount >= 2
        val normal = noProgressCount == 1 ||
            userSupplement ||
            executionFailureCount == 1
        val depth = when {
            hardDeep -> VisualReasoningDepth.Deep
            normal -> VisualReasoningDepth.Normal
            else -> VisualReasoningDepth.Fast
        }
        val pressure = when {
            routeCycleLength > 0 || provisionalRollbackCount > 0 ||
                noProgressCount >= 2 || sameActionCount >= 2 || executionFailureCount >= 2 -> "high"
            noProgressCount == 1 || executionFailureCount == 1 -> "medium"
            else -> "low"
        }
        return VisualReasoningContext(
            depth = depth,
            triggers = triggerSet.toList(),
            noProgressCount = noProgressCount,
            sameActionCount = sameActionCount,
            routeCycleLength = routeCycleLength,
            provisionalRollbackCount = provisionalRollbackCount,
            executionFailureCount = executionFailureCount,
            failedHypothesisCount = 0,
            blockedActionCount = 0,
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
            freshObservationRequired = depth == VisualReasoningDepth.Deep,
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
            append("|discardProvisionalState=").append(context.provisionalRollbackCount > 0)
            append("|avoidRepeatedRoute=").append(context.routeCycleLength > 0)
            append("|routeCycleLength=").append(context.routeCycleLength)
            append("|decisionOwner=gui_plus")
            append("|localProgressClassification=false")
        }.take(VisualLoopSupport.MAX_RECENT_ACTION_CHARS)
    }

    private fun activeObjectiveWindow(recentActions: List<String>): List<String> = recentActions.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .filterNot { it.startsWith(VisualReasoningContext.PROMPT_PREFIX) }
        .filterNot { it.startsWith(LEGACY_PROMPT_PREFIX_V2) }
        .filterNot { it.startsWith(LEGACY_PROMPT_PREFIX_V1) }
        .filterNot { it.startsWith(DEEP_REPLAN_PREFIX) }
        .toList()
        .takeLast(MAX_ACTIVE_ACTIONS)

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

    private fun List<String>.hasLiveCompletionCandidate(): Boolean {
        val pending = indexOfLast { it.isCompletionCandidateEvidence() }
        if (pending < 0) return false
        val rollback = indexOfLast { it.isCompletionRollbackEvidence() }
        return pending > rollback
    }

    private fun List<String>.provisionalRollbackCount(): Int {
        val latestPending = indexOfLast { it.isCompletionCandidateEvidence() }
        return countIndexed { index, line ->
            line.isCompletionRollbackEvidence() && index > latestPending
        }
    }

    private fun String.isCompletionCandidateEvidence(): Boolean =
        contains("finish_verification_pending", ignoreCase = true) ||
            contains("completionCandidate=true", ignoreCase = true)

    private fun String.isCompletionRollbackEvidence(): Boolean {
        val value = lowercase()
        return value.startsWith("finish_candidate_rejected:") ||
            value.startsWith("finish_permit_rejected:") ||
            value.startsWith("completion_candidate_rolled_back:")
    }

    private fun String.isRouteConstraintConflictEvidence(): Boolean {
        val value = lowercase()
        return value.contains("failureclass=structural_route") ||
            value.contains("target_surface_lost") ||
            value.contains("routeconstraintconflict=true") ||
            value.contains("routerefreshrequested=true") ||
            value.contains("structuralregression=true")
    }

    private fun String.isEntityConflictEvidence(): Boolean {
        val value = lowercase()
        return value.contains("currentpackagematchesverifiedtarget=false") ||
            value.contains("entityconflict=true") ||
            value.contains("wrong_entity") ||
            value.contains("package_mismatch")
    }

    private fun String.toExecutionEventOrNull(): ExecutionEvent? {
        val clean = trim()
        val markerAndOutcome = when {
            ":failed:" in clean -> ":failed:" to ExecutionOutcome.Failure
            ":retry:" in clean -> ":retry:" to ExecutionOutcome.Failure
            ":ok:" in clean -> ":ok:" to ExecutionOutcome.Success
            else -> return null
        }
        val signature = clean.substringBefore(markerAndOutcome.first).trim().take(180)
        if (signature.isBlank()) return null
        return ExecutionEvent(
            signature = signature,
            outcome = markerAndOutcome.second,
            reobserve = signature.equals(REOBSERVE_SIGNATURE, ignoreCase = true) ||
                signature.startsWith("wait|重新观察|", ignoreCase = true),
        )
    }

    private fun consecutiveReobserveOrFailureCount(
        events: List<ExecutionEvent>,
        actions: List<String>,
    ): Int {
        var count = 0
        for (event in events.asReversed()) {
            if (event.reobserve || event.outcome == ExecutionOutcome.Failure) {
                count += 1
            } else {
                break
            }
        }
        if (count > 0) return count
        return if (actions.asReversed().take(4).any { it.isUnpairedFailureFeedback() }) 1 else 0
    }

    private fun consecutiveFailureCount(
        events: List<ExecutionEvent>,
        actions: List<String>,
    ): Int {
        val eventCount = events.asReversed().takeWhile { it.outcome == ExecutionOutcome.Failure }.count()
        if (eventCount > 0) return eventCount
        return if (actions.asReversed().take(4).any { it.isUnpairedFailureFeedback() }) 1 else 0
    }

    private fun consecutiveSameExecutedActionCount(events: List<ExecutionEvent>): Int {
        val latest = events.lastOrNull()?.signature ?: return 0
        return events.asReversed().takeWhile { it.signature == latest }.count()
    }

    /**
     * Detects a repeated mechanical route without interpreting screen content. It catches either an
     * exact repeated suffix (A-B-C / A-B-C) or a return to the same action after at least two distinct
     * intervening successful actions (A-B-C-D-A). This is a loop signal only; GUI Plus decides why.
     */
    private fun repeatedRouteCycleLength(events: List<ExecutionEvent>): Int {
        val successful = events.filter { it.outcome == ExecutionOutcome.Success && !it.reobserve }
        if (successful.size < MIN_ROUTE_CYCLE_EVENTS) return 0
        val signatures = successful.map { it.signature }
        val maxExact = minOf(MAX_ROUTE_CYCLE_LENGTH, signatures.size / 2)
        for (length in 2..maxExact) {
            val previous = signatures.subList(signatures.size - length * 2, signatures.size - length)
            val latest = signatures.subList(signatures.size - length, signatures.size)
            if (previous == latest && latest.distinct().size >= 2) return length
        }
        val latest = signatures.last()
        val previousIndex = signatures.dropLast(1).indexOfLast { it == latest }
        if (previousIndex < 0) return 0
        val distance = signatures.lastIndex - previousIndex
        if (distance !in 3..MAX_ROUTE_CYCLE_DISTANCE) return 0
        val intervening = signatures.subList(previousIndex + 1, signatures.lastIndex)
        return if (intervening.distinct().size >= 2) distance else 0
    }

    private fun String.isUnpairedFailureFeedback(): Boolean {
        val value = lowercase()
        return value.startsWith("visual_action_retry:") ||
            value.startsWith("visual_action_stale:") ||
            value.startsWith("visual_local_retry:") ||
            value.contains("semanticstatus=execution_failed")
    }

    private data class ExecutionEvent(
        val signature: String,
        val outcome: ExecutionOutcome,
        val reobserve: Boolean,
    )

    private enum class ExecutionOutcome { Success, Failure }

    private data class RuntimeState(
        val surfaceState: String,
        val packageConflict: Boolean,
    )

    private const val LEGACY_PROMPT_PREFIX_V2 = "visual_reasoning_context:v2|"
    private const val LEGACY_PROMPT_PREFIX_V1 = "visual_reasoning_context:v1|"
    private const val REOBSERVE_SIGNATURE = "wait|重新观察"
    private const val MIN_ROUTE_CYCLE_EVENTS = 4
    private const val MAX_ROUTE_CYCLE_LENGTH = 6
    private const val MAX_ROUTE_CYCLE_DISTANCE = 8
    private const val MAX_ACTIVE_ACTIONS = 30
}
