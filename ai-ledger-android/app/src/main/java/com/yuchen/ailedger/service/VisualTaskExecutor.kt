package com.yuchen.ailedger.service

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

internal suspend fun VisualTaskExecutor.requestPlan(
    session: VisualTaskSession,
    turn: VisualTurn,
): VisualPlanRequest {
    val memory = session.semantic.memorySnapshot(turn.snapshot)
    val historyLimit = if (memory.recoveryMode) {
        VisualLoopSupport.RECOVERY_HISTORY_ITEMS
    } else {
        VisualLoopSupport.NORMAL_HISTORY_ITEMS
    }
    val requestApps = appContextForTurn(session, turn.runtime)
    session.state.modelTurns += 1
    val plan = try {
        withContext(Dispatchers.IO) {
            aiWorkerClient.requestVisualAgentStep(
                goal = session.state.goal,
                snapshot = turn.snapshot,
                recentActions = VisualLoopSupport.requestActions(
                    session.recentActions,
                    session.interactionActions,
                ),
                visualHistory = session.visualHistory.takeLast(historyLimit),
                appContext = requestApps,
                deviceId = clientDeviceId,
                agentSessionId = session.agentSessionId,
                executionMode = session.executionMode,
                deviceProfile = session.deviceProfile,
                runtimeContext = turn.runtime,
                taskMemory = memory,
            )
        }
    } catch (error: IOException) {
        if (session.stopped()) return VisualPlanRequest.Retry
        return when (val decision = session.routeRetry.onFailure(error)) {
            is VisualRouteRetryDecision.Retry -> {
                session.state.reobservations += 1
                val recovered = observationCoordinator.captureTrustedObservation(
                    forceVisual = true,
                    expectedPackage = session.execution.selectedTargetPackage,
                )
                val recoveredSnapshot = recovered.toAgentScreenSnapshot()
                val recoveredRuntime = session.execution.runtimeContext(recoveredSnapshot)
                session.prefetchedObservation = recovered
                VisualLoopMemorySupport.replaceRuntimeLine(session.recentActions, recoveredRuntime)
                val structured = error as? VisualAgentRequestException
                VisualLoopSupport.appendRecent(
                    session.recentActions,
                    buildString {
                        append("visual_route_retry:attempt=").append(decision.attempt)
                        append("|code=").append(structured?.code ?: "io_error")
                        append("|httpStatus=").append(structured?.httpStatus ?: 0)
                        append("|retryable=true|currentPackage=")
                        append(recoveredSnapshot.packageName.take(100))
                        append("|workSurfaceRecovered=").append(recoveredRuntime.guiPlusEligible)
                        append("|observationId=").append(recoveredRuntime.observationId)
                    },
                )
                if (!recoveredRuntime.guiPlusEligible) delay(decision.backoffMs)
                VisualPlanRequest.Retry
            }
            is VisualRouteRetryDecision.Stop -> {
                val message = "visual_agent_step failed: ${error.message ?: "unknown error"}; retryStopReason=${decision.reason}"
                AgentRuntimeController.failTask(session.runtimeTaskId, message)
                VisualPlanRequest.Fatal(
                    AgentTaskRunResult(false, false, message, session.logs),
                )
            }
        }
    }
    session.routeRetry.onSuccess()
    if (requestApps.size == session.appContext.size) session.fullAppCatalogUploaded = true
    AgentRuntimeController.noteModelOutput(plan.rawModelOutput)
    session.semantic.updateTaskContract(plan.taskContract, session.state.goal)
    return VisualPlanRequest.Ready(plan)
}

internal suspend fun VisualTaskExecutor.handlePlan(
    session: VisualTaskSession,
    turn: VisualTurn,
    plan: CloudAgentPlan,
): VisualLoopDecision {
    val step = plan.step
    if (VisualLoopSupport.requiresAccessibility(step) &&
        (!turn.observation.enabled || !turn.observation.serviceConnected)
    ) return fatal(session, "The Android accessibility service is not connected.")

    val validation = VisualActionValidator.validate(step, turn.snapshot, turn.runtime)
    if (!validation.ok) return rejectPlan(session, turn, plan, validation)
    if (step.type == "finish") return handleFinish(session, turn, plan)
    session.state.clearFinishCandidate()
    if (step.type == "need_user_help") {
        session.logs += AgentTaskStepLog(session.logs.size + 1, turn.snapshot.currentApp, step, null)
        val continued = pauseForUserAndContinue(
            session,
            step.reason ?: "Visual agent requested user assistance.",
            "model_help",
            step,
        )
        return if (continued) VisualLoopDecision.Continue else VisualLoopDecision.Stop
    }

    val prepared = prepareStep(step, turn.snapshot, session.installedAppsByPackage)
    if (!prepared.ok || prepared.step == null) return rejectPrepared(session, turn, plan, prepared)
    val executable = prepared.step
    val blocked = session.semantic.blockedHypothesisReason(executable, turn.snapshot)
    if (blocked != null) {
        val memory = session.semantic.memorySnapshot(turn.snapshot)
        val feedback = buildString {
            append("visual_local_retry:action=").append(VisualActionValidator.actionSignature(executable))
            append(":count=").append(memory.failedHypotheses.size)
            append("|semanticStatus=blocked_hypothesis")
            append("|milestone=").append(memory.currentMilestoneId.take(80))
            append("|explorationBudgetRemaining=").append(memory.remainingExplorationBudget)
            append("|requiresStrategyChange=true|replanRequired=true")
            append("|reason=").append(blocked.take(260))
        }
        session.logs += AgentTaskStepLog(
            session.logs.size + 1,
            turn.snapshot.currentApp,
            executable.copy(reason = blocked),
            null,
        )
        VisualLoopSupport.appendRecent(session.recentActions, feedback)
        VisualLoopMemorySupport.rememberTurn(session.visualHistory, turn.snapshot, plan, feedback)
        session.state.reobservations += 1
        return VisualLoopDecision.Continue
    }

    if (executable.type == "open_app") {
        val expectedPackage = executable.packageName.orEmpty()
        session.execution.beginLaunch(expectedPackage)
        if (prepared.alreadyForeground) {
            val result = AgentExecutionResult(true, "Target package is already foreground: $expectedPackage", true)
            recordResult(session, turn.snapshot.currentApp, executable, result)
            val summary = VisualLoopSupport.resultSummary(executable, VisualActionValidator.actionSignature(executable), result)
            val verified = "open_app_package_verified:package=$expectedPackage|mode=already_foreground"
            VisualLoopSupport.appendRecent(session.recentActions, summary)
            VisualLoopSupport.appendRecent(session.recentActions, verified)
            VisualLoopMemorySupport.rememberTurn(session.visualHistory, turn.snapshot, plan, "$summary;$verified")
            session.execution.markTargetVerified(expectedPackage)
            session.semantic.onVerifiedSurface(turn.snapshot)
            session.state.executedActions += 1
            return VisualLoopDecision.Continue
        }
    }

    val confirmed = if (AgentSafetyPolicy.requiresConfirmation(session.state.goal, executable)) {
        if (!AgentRuntimeController.requestRiskConfirmation(session.state.goal, executable)) {
            return VisualLoopDecision.Return(
                AgentTaskRunResult(false, false, "User stopped the visual task.", session.logs),
            )
        }
        true
    } else false
    if (!confirmed && !AgentSafetyPolicy.canAutoExecuteInCurrentStage(session.state.goal, executable)) {
        val continued = pauseForUserAndContinue(
            session,
            executable.reason ?: "Action is blocked by Android safety policy.",
            "safety_blocked",
            executable,
        )
        return if (continued) VisualLoopDecision.Continue else VisualLoopDecision.Stop
    }

    if (VisualLoopSupport.requiresFreshObservation(executable)) {
        val fresh = observationCoordinator.captureTrustedObservation(
            forceVisual = false,
            expectedPackage = session.execution.selectedTargetPackage,
            settleMs = 160L,
        ).toAgentScreenSnapshot()
        session.execution.synchronizeWith(fresh)
        val verified = session.execution.isVerifiedWorkSurface(fresh)
        val contextFresh = VisualObservationProtocol.isActionContextFresh(turn.snapshot, fresh)
        if (!verified || !contextFresh) {
            val feedback = "visual_action_stale:type=${executable.type}|failureClass=${if (verified) "visual_local" else "structural_route"}|reason=${if (verified) "screen_changed_before_execution" else "target_surface_lost"}|replanRequired=true"
            session.logs += AgentTaskStepLog(
                session.logs.size + 1,
                fresh.currentApp,
                executable.copy(reason = "A fresh visual decision is required."),
                null,
            )
            VisualLoopSupport.appendRecent(session.recentActions, feedback)
            VisualLoopMemorySupport.rememberTurn(session.visualHistory, turn.snapshot, plan, feedback)
            if (!verified) session.execution.markStructuralReplan()
            session.state.reobservations += 1
            return VisualLoopDecision.Continue
        }
    }

    if (session.stopped()) return VisualLoopDecision.Stop
    val result = executeStep(session, executable, turn.snapshot.currentApp, confirmed)
    session.state.executedActions += 1
    session.state.lastAction = VisualActionValidator.actionSignature(executable)
    session.state.rejectedPlans = 0
    val summary = VisualLoopSupport.resultSummary(executable, session.state.lastAction, result)
    VisualLoopSupport.appendRecent(session.recentActions, summary)
    VisualLoopMemorySupport.rememberTurn(session.visualHistory, turn.snapshot, plan, summary)

    if (executable.type == "open_app") {
        handleOpenAppResult(session, executable, turn.snapshot, result, summary)
        return VisualLoopDecision.Continue
    }
    if (executable.type in CloudAgentStep.deviceToolTypes) {
        val message = result.message.ifBlank { "Internal device tool finished." }
        if (result.ok) AgentRuntimeController.finishTask(session.runtimeTaskId, AgentTaskOutcome.Completed(message))
        else AgentRuntimeController.failTask(session.runtimeTaskId, message)
        return VisualLoopDecision.Return(AgentTaskRunResult(result.ok, false, message, session.logs))
    }
    if (!result.ok || !result.shouldContinue) {
        session.state.executionFailures += 1
        val feedback = "visual_local_retry:action=${session.state.lastAction}:count=${session.state.executionFailures}|semanticStatus=execution_failed|requiresStrategyChange=true|replanRequired=${session.state.executionFailures >= 2}|reason=${result.message.take(260)}"
        VisualLoopSupport.appendRecent(session.recentActions, feedback)
        VisualLoopMemorySupport.updateLastHistory(session.visualHistory, "$summary;$feedback")
        session.prefetchedObservation = observationCoordinator.captureTrustedObservation(
            forceVisual = true,
            expectedPackage = session.execution.selectedTargetPackage,
        )
        return VisualLoopDecision.Continue
    }
    session.state.executionFailures = 0

    delayForStep(executable)
    val afterObservation = observationCoordinator.captureTrustedObservation(
        forceVisual = true,
        expectedPackage = session.execution.selectedTargetPackage,
    )
    val after = afterObservation.toAgentScreenSnapshot()
    session.execution.synchronizeWith(after)
    val progress = session.semantic.evaluate(
        step = executable,
        before = turn.snapshot,
        after = after,
        verifiedTargetPackage = turn.runtime.verifiedTargetPackage,
    )
    val feedback = progress.toFeedbackLine(executable)
    VisualLoopSupport.appendRecent(session.recentActions, feedback)
    VisualLoopMemorySupport.replaceMemoryLine(session.recentActions, progress.taskMemory)
    VisualLoopMemorySupport.updateLastHistory(session.visualHistory, "$summary;$feedback")
    session.prefetchedObservation = afterObservation
    if (progress.status == VisualSemanticProgressStatus.Ambiguous && progress.reobserveRecommended) {
        delay(120L)
        session.prefetchedObservation = observationCoordinator.captureTrustedObservation(
            forceVisual = true,
            expectedPackage = session.execution.selectedTargetPackage,
        )
    }
    if (progress.structuralRegression) {
        session.execution.markStructuralReplan()
        session.prefetchedObservation = null
    }
    if (progress.requiresReplan) {
        VisualLoopSupport.appendRecent(
            session.recentActions,
            "visual_replan_requested:milestone=${progress.milestoneId.take(80)}|semanticStatus=${progress.status.wireValue}|reason=${progress.reason.take(220)}",
        )
    }
    if (progress.shouldPauseForUser) {
        val continued = pauseForUserAndContinue(
            session,
            "The last action regressed on a non-reversible or repeatedly failing path.",
            "semantic_regression",
            executable,
        )
        return if (continued) VisualLoopDecision.Continue else VisualLoopDecision.Stop
    }
    return VisualLoopDecision.Continue
}
