package com.yuchen.ailedger.service

import android.content.Context

internal class VisualTaskExecutor(
    internal val aiWorkerClient: AiWorkerClient,
    appContext: Context,
) {
    internal val applicationContext = appContext.applicationContext
    internal val installedAppIndex = InstalledAppIndex(applicationContext)
    internal val deviceToolExecutor = DeviceToolExecutor(applicationContext, installedAppIndex)
    internal val observationCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        VisualObservationCoordinator(
            captureSource = AccessibilityVisualObservationCaptureSource,
            foregroundPackageReader = ForegroundPackageProbe(DeviceShellBridge(applicationContext)),
        )
    }
    internal val clientDeviceId by lazy { AgentClientIdentity.getOrCreateDeviceId(applicationContext) }

    suspend fun run(
        goal: String,
        maxSteps: Int,
        executionMode: AgentExecutionMode,
    ): AgentTaskRunResult = runVisualTask(goal, maxSteps, executionMode)
}

internal data class VisualTaskSession(
    val state: VisualLoopState,
    val routeRetry: VisualRouteRetryState,
    val execution: VisualExecutionSessionState,
    val semantic: VisualSemanticProgressTracker,
    val agentSessionId: String,
    val deviceProfile: AgentDeviceProfile,
    val installedAppsByPackage: Map<String, InstalledAppEntry>,
    val appContext: List<VisualAgentAppContextItem>,
    val modelTurnBudget: Int,
    val maxSteps: Int,
    val executionMode: AgentExecutionMode,
    val logs: MutableList<AgentTaskStepLog> = mutableListOf(),
    val recentActions: MutableList<String> = mutableListOf(),
    val interactionActions: MutableList<String> = mutableListOf(),
    val visualHistory: MutableList<VisualAgentHistoryItem> = mutableListOf(),
    var prefetchedObservation: ScreenObservation? = null,
    var fullAppCatalogUploaded: Boolean = false,
    var runtimeTaskId: Long = 0L,
    var stopGeneration: Long = 0L,
) {
    fun stopped(): Boolean = AgentRuntimeController.currentManualStopGeneration() != stopGeneration
    fun canRun(): Boolean = !stopped() &&
        state.executedActions < maxSteps && state.modelTurns < modelTurnBudget
}
