package com.yuchen.ailedger.service

internal object VisualLoopMemorySupport {
    private const val RUNTIME_PREFIX = "visual_runtime_context:v1|"
    private const val MEMORY_PREFIX = "visual_task_memory:v1|"

    fun replaceRuntimeLine(actions: MutableList<String>, runtime: VisualAgentRuntimeContext) {
        actions.removeAll { it.startsWith(RUNTIME_PREFIX) }
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

    fun replaceMemoryLine(actions: MutableList<String>, memory: VisualTaskMemory) {
        actions.removeAll { it.startsWith(MEMORY_PREFIX) }
        VisualLoopSupport.appendRecent(
            actions,
            buildString {
                append(MEMORY_PREFIX)
                append("milestone=").append(memory.currentMilestoneId.take(80))
                append("|completed=").append(memory.completedMilestoneIds.joinToString(",").take(160))
                append("|failedHypotheses=")
                append(memory.failedHypotheses.takeLast(4).joinToString(",") { it.hypothesisId }.take(220))
                append("|blockedActions=")
                append(memory.blockedActions.takeLast(4).joinToString(",") { it.actionCluster }.take(220))
                append("|explorationBudgetRemaining=").append(memory.remainingExplorationBudget)
                append("|lastConfirmedPage=").append(memory.lastConfirmedPage?.id.orEmpty())
                append("|progressStatus=").append(memory.progressStatus)
                append("|replanRequested=").append(memory.replanRequested)
                append("|legacyMode=").append(memory.legacyMode)
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
