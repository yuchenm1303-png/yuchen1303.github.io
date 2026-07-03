package com.yuchen.ailedger.service

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
        syncAnalyticsSnapshot()
    }

    fun syncAnalyticsSnapshot() {
        AgentAnalyticsRuntime.updateVisualLoopMetrics(
            taskId = AgentRuntimeController.currentTaskId(),
            modelTurns = modelTurns,
            executedActions = executedActions,
            reobservations = reobservations,
            rejectedPlans = rejectedPlans,
            executionFailures = executionFailures,
        )
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
