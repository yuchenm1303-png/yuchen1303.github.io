package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes only objective Android execution state for the cloud loop.
 * It never derives visual meaning, reasoning depth, hypotheses or route strategy.
 */
internal object VisualLoopMemorySupport {
    private const val RUNTIME_PREFIX = "visual_runtime_context:v2|"
    private const val LEGACY_RUNTIME_PREFIX = "visual_runtime_context:v1|"
    private const val LEDGER_PREFIX = "visual_execution_ledger:v5|"
    private val legacyLedgerPrefixes = listOf(
        "visual_execution_ledger:v4|",
        "visual_execution_ledger:v3|",
        "visual_execution_ledger:v2|",
        "visual_task_memory:v1|",
        "visual_reasoning_context:v1|",
        "visual_reasoning_context:v2|",
        "visual_reasoning_context:v3|",
        "visual_reasoning_context:v4|",
        "visual_replan_requested:reason=adaptive_reasoning_depth|",
    )

    fun replaceRuntimeLine(actions: MutableList<String>, runtime: VisualAgentRuntimeContext) {
        actions.removeAll { it.startsWith(RUNTIME_PREFIX) || it.startsWith(LEGACY_RUNTIME_PREFIX) }
        VisualLoopSupport.appendRecent(
            actions,
            buildString {
                append(RUNTIME_PREFIX)
                append("state=").append(runtime.surfaceState.wireValue)
                append("|selectedTargetPackage=").append(runtime.selectedTargetPackage.take(100))
                append("|verifiedTargetPackage=").append(runtime.verifiedTargetPackage.take(100))
                append("|currentPackage=").append(runtime.currentPackage.take(100))
                append("|guiPlusEligible=").append(runtime.guiPlusEligible)
                append("|observationId=").append(runtime.observationId)
                append("|routeEpoch=").append(runtime.routeEpoch)
                append("|surfaceEpoch=").append(runtime.surfaceEpoch)
            },
        )
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "runtime_context",
            details = JSONObject().apply {
                put("surfaceState", runtime.surfaceState.wireValue)
                put("selectedTargetPackage", runtime.selectedTargetPackage)
                put("verifiedTargetPackage", runtime.verifiedTargetPackage)
                put("currentPackage", runtime.currentPackage)
                put("guiPlusEligible", runtime.guiPlusEligible)
                put("observationId", runtime.observationId)
                put("routeEpoch", runtime.routeEpoch)
                put("surfaceEpoch", runtime.surfaceEpoch)
            },
        )
    }

    fun replaceMemoryLine(actions: MutableList<String>, memory: VisualTaskMemory) {
        actions.removeAll { line ->
            line.startsWith(LEDGER_PREFIX) || legacyLedgerPrefixes.any(line::startsWith)
        }
        VisualLoopSupport.appendRecent(
            actions,
            buildString {
                append(LEDGER_PREFIX)
                append("executionStatus=").append(memory.progressStatus.take(80))
                append("|currentMilestoneId=").append(memory.currentMilestoneId.take(80))
                append("|committedMilestoneCount=").append(memory.completedMilestoneIds.size)
                append("|currentFrameId=").append(memory.currentPage?.id.orEmpty().take(100))
                append("|lastVerifiedFrameId=").append(memory.lastConfirmedPage?.id.orEmpty().take(100))
                append("|taskRevision=").append(memory.taskRevision)
                append("|taskRevisionPending=").append(memory.taskRevisionPending)
                append("|currentMilestoneInvalidated=").append(memory.currentMilestoneInvalidated)
                append("|latestUserUpdateKind=").append(memory.latestUserUpdate?.kind?.wireValue.orEmpty())
                append("|replanRequested=").append(memory.replanRequested)
                append("|recoveryMode=").append(memory.recoveryMode)
                append("|executionLedgerOnly=true")
                append("|semanticDecisionOwner=gui_plus")
                append("|localSemanticDecision=false")
            },
        )
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "task_memory",
            details = JSONObject().apply {
                put("executionStatus", memory.progressStatus)
                put("currentMilestoneId", memory.currentMilestoneId)
                put("currentFrame", memory.currentPage?.toJson() ?: JSONObject.NULL)
                put("lastVerifiedFrame", memory.lastConfirmedPage?.toJson() ?: JSONObject.NULL)
                put("replanRequested", memory.replanRequested)
                put("recoveryMode", memory.recoveryMode)
                put("committedMilestoneIds", JSONArray(memory.completedMilestoneIds))
                put("confirmedProtocolFacts", JSONArray(memory.confirmedFacts))
                put("taskContract", memory.taskContract?.toJson() ?: JSONObject.NULL)
                put("legacyMode", memory.legacyMode)
                put("taskRevision", memory.taskRevision)
                put("taskRevisionPending", memory.taskRevisionPending)
                put("currentMilestoneInvalidated", memory.currentMilestoneInvalidated)
                put("latestUserUpdateRevision", memory.latestUserUpdate?.revision ?: 0)
                put("latestUserUpdateKind", memory.latestUserUpdate?.kind?.wireValue.orEmpty())
                put("executionLedgerOnly", true)
                put("semanticDecisionOwner", "gui_plus")
                put("localSemanticDecision", false)
            },
        )
    }

    fun updateLastHistory(history: MutableList<VisualAgentHistoryItem>, result: String) {
        if (history.isEmpty()) return
        history[history.lastIndex] = history.last().copy(executionResult = result.take(240))
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "visual_history_update",
            details = JSONObject().apply {
                put("historySize", history.size)
                put("executionResult", result.take(1_200))
            },
        )
    }

    fun rememberTurn(
        history: MutableList<VisualAgentHistoryItem>,
        snapshot: AgentScreenSnapshot,
        plan: CloudAgentPlan,
        result: String,
    ) {
        val visual = snapshot.visual ?: return
        val output = plan.rawModelOutput.ifBlank { plan.step.reason.orEmpty() }
        if (output.isBlank()) return
        history += VisualAgentHistoryItem(visual, output, result)
        while (history.size > VisualLoopSupport.RECOVERY_HISTORY_ITEMS) history.removeAt(0)

        val step = plan.step
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "model_response",
            details = JSONObject().apply {
                put("observationPackage", snapshot.packageName)
                put("rawModelOutput", output.take(12_000))
                put("parsedStep", JSONObject().apply {
                    put("type", step.type)
                    put("targetNodeId", step.targetNodeId ?: JSONObject.NULL)
                    put("targetText", step.targetText ?: JSONObject.NULL)
                    put("text", if (step.type == "input_text") "[输入内容已隐藏]" else step.text ?: JSONObject.NULL)
                    put("direction", step.direction ?: JSONObject.NULL)
                    put("reason", step.reason ?: JSONObject.NULL)
                    put("appName", step.appName ?: JSONObject.NULL)
                    put("packageName", step.packageName ?: JSONObject.NULL)
                    put("x", step.x ?: JSONObject.NULL)
                    put("y", step.y ?: JSONObject.NULL)
                    put("riskLevel", step.riskLevel)
                    put("requiresConfirmation", step.requiresConfirmation)
                    put("toolArgs", if (step.type == "input_text") "[输入参数已隐藏]" else step.toolArgs ?: JSONObject.NULL)
                })
                put("agentState", plan.state?.let { state ->
                    JSONObject().apply {
                        put("isComplete", state.isComplete)
                        put("expectedProgress", state.expectedProgress)
                        put("isWrong", state.isWrong)
                        put("confidence", state.confidence)
                        put("reason", state.reason)
                        put("nextHint", state.nextHint)
                    }
                } ?: JSONObject.NULL)
                put("committedTaskContract", plan.taskContract?.toJson() ?: JSONObject.NULL)
                put("stopConditions", JSONArray(plan.stopConditions.toList()))
                put("executionResult", result.take(1_200))
                put("historySizeAfterAppend", history.size)
            },
        )
    }
}
