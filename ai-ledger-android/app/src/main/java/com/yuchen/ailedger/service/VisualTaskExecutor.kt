package com.yuchen.ailedger.service

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

internal suspend fun VisualTaskExecutor.runVisualTask(
    goal: String,
    maxSteps: Int,
    executionMode: AgentExecutionMode,
): AgentTaskRunResult {
    if (VisualLoopRunner.requiresAgentSwitch(executionMode) && !AgentRuntimeController.isEnabled()) {
        return AgentTaskRunResult(false, false, "Visual agent is off; forced visual loop was not started.", emptyList())
    }
    val apps = withContext(Dispatchers.IO) { installedAppIndex.getLaunchableApps(forceReload = false) }
    val state = VisualLoopState(goal.trim().take(240))
    val session = VisualTaskSession(
        state = state,
        routeRetry = VisualRouteRetryState(),
        execution = VisualExecutionSessionState(),
        semantic = VisualSemanticProgressTracker(originalGoal = state.goal),
        agentSessionId = AgentClientIdentity.newVisualSessionId(),
        deviceProfile = AgentDeviceProfile.current(),
        installedAppsByPackage = apps.associateBy { it.packageName },
        appContext = executorAppContext(apps),
        modelTurnBudget = VisualLoopSupport.modelTurnBudget(maxSteps),
        maxSteps = maxSteps,
        executionMode = executionMode,
    )
    VisualLoopSupport.appendRecent(session.recentActions, session.deviceProfile.toPromptLine())
    VisualLoopSupport.appendRecent(
        session.recentActions,
        "cloud_routing:v5|mainBrain=deepseek|visualOwner=gui_plus|taskContractHarness=true|semanticProgressVerification=true|failedHypothesisBlocking=true",
    )
    VisualLoopSupport.appendRecent(
        session.recentActions,
        "app_identity:v2|machineIdentity=packageName|appNameRole=display_only",
    )
    AiAgentAccessibilityService.beginTaskSession()
    session.runtimeTaskId = AgentRuntimeController.startTask(state.goal)
    session.stopGeneration = AgentRuntimeController.currentManualStopGeneration()
    return try {
        while (session.canRun() && waitWhileUserTakeoverPaused(session)) {
            val turn = captureTurn(session) ?: break
            when (val request = requestPlan(session, turn)) {
                is VisualPlanRequest.Retry -> continue
                is VisualPlanRequest.Fatal -> return request.result
                is VisualPlanRequest.Ready -> when (val decision = handlePlan(session, turn, request.plan)) {
                    is VisualLoopDecision.Continue -> continue
                    is VisualLoopDecision.Return -> return decision.result
                    is VisualLoopDecision.Stop -> break
                }
            }
        }
        finishForLoopExit(session)
    } catch (error: CancellationException) {
        AgentRuntimeController.finishTask(
            session.runtimeTaskId,
            AgentTaskOutcome.Cancelled("Visual task was cancelled."),
        )
        throw error
    } finally {
        AiAgentAccessibilityService.endTaskSession()
        AgentRuntimeController.resetCleanVisualCapture()
    }
}

private fun VisualTaskExecutor.executorAppContext(
    apps: List<InstalledAppEntry>,
): List<VisualAgentAppContextItem> = apps.asSequence()
    .distinctBy { it.packageName }
    .map { app ->
        VisualAgentAppContextItem(
            label = app.label.trim(),
            packageName = app.packageName.trim(),
            aliases = installedAppIndex.aliasesFor(app),
            capabilities = emptyList(),
        )
    }
    .filter { it.label.isNotBlank() && it.packageName.isNotBlank() }
    .take(VisualLoopSupport.MAX_APP_CONTEXT_ITEMS)
    .toList()

internal suspend fun VisualTaskExecutor.captureTurn(session: VisualTaskSession): VisualTurn? {
    var observation = session.prefetchedObservation?.also { session.prefetchedObservation = null }
        ?: observationCoordinator.captureTrustedObservation(
            forceVisual = session.execution.requiresVisualObservation(),
            expectedPackage = session.execution.selectedTargetPackage,
        )
    var snapshot = observation.toAgentScreenSnapshot()
    session.state.currentPackage = snapshot.currentApp
    var runtime = session.execution.runtimeContext(snapshot)
    if (runtime.guiPlusEligible && snapshot.visual?.hasImage != true) {
        observation = observationCoordinator.captureTrustedObservation(
            forceVisual = true,
            expectedPackage = session.execution.selectedTargetPackage,
        )
        snapshot = observation.toAgentScreenSnapshot()
        runtime = session.execution.runtimeContext(snapshot)
        session.state.currentPackage = snapshot.currentApp
    }
    if (session.stopped()) return null
    VisualLoopMemorySupport.replaceRuntimeLine(session.recentActions, runtime)
    VisualLoopMemorySupport.replaceMemoryLine(
        session.recentActions,
        session.semantic.memorySnapshot(snapshot),
    )
    when {
        runtime.guiPlusEligible -> AgentRuntimeController.noteDiagnostic("GUI Plus 正在分析当前子目标与页面证据")
        runtime.surfaceState == VisualSurfaceState.Launching ->
            AgentRuntimeController.noteDiagnostic("正在确认目标应用前台状态")
    }
    return VisualTurn(observation, snapshot, runtime)
}

internal fun VisualTaskExecutor.appContextForTurn(
    session: VisualTaskSession,
    runtime: VisualAgentRuntimeContext,
): List<VisualAgentAppContextItem> {
    if (!session.fullAppCatalogUploaded || runtime.surfaceState != VisualSurfaceState.WorkSurface) {
        return session.appContext
    }
    return session.appContext.filter { it.packageName == runtime.verifiedTargetPackage }.take(1)
}
