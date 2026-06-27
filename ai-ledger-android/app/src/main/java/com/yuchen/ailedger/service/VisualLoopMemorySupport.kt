package com.yuchen.ailedger.service

internal object VisualLoopMemorySupport {
    private const val RUNTIME_PREFIX = "visual_runtime_context:v2|"
    private const val LEGACY_RUNTIME_PREFIX = "visual_runtime_context:v1|"
    private const val LEDGER_PREFIX = "visual_execution_ledger:v2|"
    private const val LEGACY_MEMORY_PREFIX = "visual_task_memory:v1|"

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
    }

    /**
     * Exposes only objective Android execution state. GUI Plus remains the sole owner of page
     * meaning, milestones, route quality, hypotheses and completion semantics.
     */
    fun replaceMemoryLine(actions: MutableList<String>, memory: VisualTaskMemory) {
        actions.removeAll { it.startsWith(LEDGER_PREFIX) || it.startsWith(LEGACY_MEMORY_PREFIX) }
        VisualLoopSupport.appendRecent(
            actions,
            buildString {
                append(LEDGER_PREFIX)
                append("progressStatus=").append(memory.progressStatus.take(80))
                append("|currentSurfaceId=").append(memory.currentPage?.id.orEmpty().take(100))
                append("|lastConfirmedSurfaceId=").append(memory.lastConfirmedPage?.id.orEmpty().take(100))
                append("|replanRequested=").append(memory.replanRequested)
                append("|recoveryMode=").append(memory.recoveryMode)
                append("|semanticDecisionOwner=gui_plus")
                append("|localSemanticDecision=false")
            },
        )
    }

    fun updateLastHistory(history: MutableList<VisualAgentHistoryItem>, result: String) {
        if (history.isEmpty()) return
        history[history.lastIndex] = history.last().copy(executionResult = result.take(240))
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
        history += VisualAgentHistoryItem(visual.copy(base64Jpeg = ""), output, result)
        while (history.size > VisualLoopSupport.RECOVERY_HISTORY_ITEMS) history.removeAt(0)
    }
}
