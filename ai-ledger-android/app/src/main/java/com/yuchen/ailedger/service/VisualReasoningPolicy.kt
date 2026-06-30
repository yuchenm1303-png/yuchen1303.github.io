package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

/**
 * Compatibility-only wire types retained for old diagnostics and persisted payloads.
 * Android no longer chooses reasoning depth or changes cloud execution strategy.
 */
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
    val depth: VisualReasoningDepth = VisualReasoningDepth.Fast,
    val triggers: List<VisualReasoningTrigger> = emptyList(),
    val noProgressCount: Int = 0,
    val sameActionCount: Int = 0,
    val routeCycleLength: Int = 0,
    val provisionalRollbackCount: Int = 0,
    val executionFailureCount: Int = 0,
    val failedHypothesisCount: Int = 0,
    val blockedActionCount: Int = 0,
    val explorationPressureLevel: String = "off",
    val historyItems: Int = 0,
    val selfCheckPasses: Int = 0,
    val candidateHypothesisLimit: Int = 0,
    val freshObservationRequired: Boolean = false,
    val completionEvidenceStrict: Boolean = false,
    val directExecutionAllowed: Boolean = true,
) {
    val deepThinkingRequested: Boolean get() = false

    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "visual_reasoning_off_v1")
        put("depth", VisualReasoningDepth.Fast.wireValue)
        put("triggers", JSONArray())
        put("enabled", false)
        put("decisionOwner", "cloud")
        put("localSemanticDecision", false)
    }

    fun toPromptLine(): String = PROMPT_PREFIX + "enabled=false|decisionOwner=cloud|localSemanticDecision=false"

    companion object {
        const val PROMPT_PREFIX = "visual_reasoning_context:off|"
    }
}

internal object VisualReasoningRuntime {
    fun update(value: VisualReasoningContext) = Unit
    fun currentOrNull(): VisualReasoningContext? = null
    internal fun resetForTests() = Unit
}

internal object VisualReasoningPolicy {
    const val DEEP_REPLAN_PREFIX = "visual_replan_requested:reason=adaptive_reasoning_depth|"

    fun evaluate(
        memory: VisualTaskMemory,
        recentActions: List<String> = emptyList(),
    ): VisualReasoningContext = VisualReasoningContext()

    fun deepReplanLine(context: VisualReasoningContext): String? = null
}
