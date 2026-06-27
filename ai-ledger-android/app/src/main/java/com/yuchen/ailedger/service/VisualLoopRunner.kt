package com.yuchen.ailedger.service

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class VisualLoopRunner(
    private val aiWorkerClient: AiWorkerClient,
    appContext: Context,
) {
    private val applicationContext = appContext.applicationContext
    private val installedAppIndex = InstalledAppIndex(applicationContext)
    private val deviceToolExecutor = DeviceToolExecutor(applicationContext, installedAppIndex)
    private val observationCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        VisualObservationCoordinator(
            captureSource = AccessibilityVisualObservationCaptureSource,
            foregroundPackageReader = ForegroundPackageProbe(DeviceShellBridge(applicationContext)),
        )
    }
    private val clientDeviceId by lazy { AgentClientIdentity.getOrCreateDeviceId(applicationContext) }

    suspend fun run(
        goal: String,
        maxSteps: Int = Int.MAX_VALUE,
        executionMode: AgentExecutionMode = AgentExecutionMode.VisualForce,
    ): AgentTaskRunResult {
        if (requiresAgentSwitch(executionMode) && !AgentRuntimeController.isEnabled()) {
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
            appContext = buildAppContext(apps),
            modelTurnBudget = VisualLoopSupport.modelTurnBudget(maxSteps),
            maxSteps = maxSteps,
            executionMode = executionMode,
        )
        VisualLoopSupport.appendRecent(session.recentActions, session.deviceProfile.toPromptLine())
        VisualLoopSupport.appendRecent(
            session.recentActions,
            "cloud_routing:v6|mainBrain=deepseek|visualOwner=gui_plus|localSemanticDecision=false|completionPermitRequired=true|completionAck=true",
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

    private fun buildAppContext(
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

    private suspend fun captureTurn(session: VisualTaskSession): VisualTurn? {
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

    private fun appContextForTurn(
        session: VisualTaskSession,
        runtime: VisualAgentRuntimeContext,
    ): List<VisualAgentAppContextItem> {
        if (!session.fullAppCatalogUploaded || runtime.surfaceState != VisualSurfaceState.WorkSurface) {
            return session.appContext
        }
        return session.appContext.filter { it.packageName == runtime.verifiedTargetPackage }.take(1)
    }

    private suspend fun requestPlan(
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
            aiWorkerClient.requestVisualAgentStepCancellable(
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
                isStopped = session::stopped,
            )
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
                    delay(decision.backoffMs)
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

    private suspend fun handlePlan(
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
        session.clearCompletionCandidate()
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
                session.state.executedActions += 1
                session.state.lastAction = VisualActionValidator.actionSignature(executable)
                session.state.rejectedPlans = 0
                val summary = VisualLoopSupport.resultSummary(executable, session.state.lastAction, result)
                VisualLoopSupport.appendRecent(session.recentActions, summary)
                VisualLoopMemorySupport.rememberTurn(session.visualHistory, turn.snapshot, plan, summary)
                handleOpenAppResult(session, executable, turn.snapshot, result, summary)
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

        var executionLease: CleanVisualCaptureLease? = null
        fun releaseExecutionLease() {
            executionLease?.close()
            executionLease = null
        }
        try {
            if (VisualLoopSupport.requiresFreshObservation(executable)) {
                executionLease = AgentRuntimeController.acquireCleanVisualCaptureLease()
                val fresh = observationCoordinator.captureTrustedObservation(
                    forceVisual = false,
                    expectedPackage = session.execution.selectedTargetPackage,
                    settleMs = 160L,
                ).toAgentScreenSnapshot()
                session.execution.synchronizeWith(fresh)
                val verified = session.execution.isVerifiedWorkSurface(fresh)
                val freshness = VisualObservationProtocol.evaluateActionContextFreshness(
                    step = executable,
                    observedSnapshot = turn.snapshot,
                    currentSnapshot = fresh,
                )
                if (!verified || !freshness.fresh) {
                    val failureClass = if (verified) "visual_local" else "structural_route"
                    val reason = if (verified) freshness.reason else "target_surface_lost"
                    val feedback = buildString {
                        append("visual_action_stale:type=").append(executable.type)
                        append("|failureClass=").append(failureClass)
                        append("|reason=").append(reason)
                        append("|surfaceSimilarity=").append(freshness.surfaceSimilarity)
                        append("|replanRequired=true")
                    }
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
                releaseExecutionLease()
                handleOpenAppResult(session, executable, turn.snapshot, result, summary)
                return VisualLoopDecision.Continue
            }
            if (executable.type in CloudAgentStep.deviceToolTypes) {
                releaseExecutionLease()
                val message = result.message.ifBlank { "Internal device tool finished." }
                if (result.ok) {
                    AgentRuntimeController.finishTask(session.runtimeTaskId, AgentTaskOutcome.Completed(message))
                } else {
                    AgentRuntimeController.failTask(session.runtimeTaskId, message)
                }
                return VisualLoopDecision.Return(AgentTaskRunResult(result.ok, false, message, session.logs))
            }
            if (!result.ok || !result.shouldContinue) {
                session.state.executionFailures += 1
                val feedback = buildString {
                    append("visual_local_retry:action=").append(session.state.lastAction)
                    append(":count=").append(session.state.executionFailures)
                    append("|semanticStatus=execution_failed|requiresStrategyChange=true")
                    append("|replanRequired=").append(session.state.executionFailures >= 2)
                    append("|reason=").append(result.message.take(260))
                }
                VisualLoopSupport.appendRecent(session.recentActions, feedback)
                VisualLoopMemorySupport.updateLastHistory(session.visualHistory, "$summary;$feedback")
                session.prefetchedObservation = observationCoordinator.captureTrustedObservation(
                    forceVisual = true,
                    expectedPackage = session.execution.selectedTargetPackage,
                )
                releaseExecutionLease()
                return VisualLoopDecision.Continue
            }
            session.state.executionFailures = 0

            delayForStep(executable)
            val afterObservation = observationCoordinator.captureTrustedObservation(
                forceVisual = true,
                expectedPackage = session.execution.selectedTargetPackage,
            )
            releaseExecutionLease()
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
        } finally {
            releaseExecutionLease()
        }
    }

    private fun fatal(session: VisualTaskSession, message: String): VisualLoopDecision {
        AgentRuntimeController.failTask(session.runtimeTaskId, message)
        return VisualLoopDecision.Return(AgentTaskRunResult(false, false, message, session.logs))
    }

    private suspend fun rejectPlan(
        session: VisualTaskSession,
        turn: VisualTurn,
        plan: CloudAgentPlan,
        validation: VisualActionValidation,
    ): VisualLoopDecision {
        val step = plan.step
        val feedback = VisualLoopSupport.validationFeedback(step, validation, turn.runtime)
        session.logs += AgentTaskStepLog(
            session.logs.size + 1,
            turn.snapshot.currentApp,
            step.copy(reason = validation.message),
            null,
        )
        VisualLoopSupport.appendRecent(session.recentActions, feedback)
        VisualLoopMemorySupport.rememberTurn(session.visualHistory, turn.snapshot, plan, feedback)
        if (validation.failureClass == VisualFailureClass.StructuralRoute) {
            session.execution.markStructuralReplan()
        }
        session.state.rejectedPlans += 1
        session.state.reobservations += 1
        if (session.state.rejectedPlans < VisualLoopSupport.MAX_REJECTIONS) {
            return VisualLoopDecision.Continue
        }
        val continued = pauseForUserAndContinue(
            session,
            validation.message,
            "validation_rejected",
            step,
        )
        session.state.rejectedPlans = 0
        return if (continued) VisualLoopDecision.Continue else VisualLoopDecision.Stop
    }

    private suspend fun rejectPrepared(
        session: VisualTaskSession,
        turn: VisualTurn,
        plan: CloudAgentPlan,
        prepared: PreparedVisualStep,
    ): VisualLoopDecision {
        val step = plan.step
        val feedback = "visual_action_rejected:type=${step.type}|failureClass=structural_route|reason=${prepared.message.take(260)}|replanRequired=${prepared.replanRequired}"
        session.logs += AgentTaskStepLog(
            session.logs.size + 1,
            turn.snapshot.currentApp,
            step.copy(reason = prepared.message),
            null,
        )
        VisualLoopSupport.appendRecent(session.recentActions, feedback)
        VisualLoopMemorySupport.rememberTurn(session.visualHistory, turn.snapshot, plan, feedback)
        session.execution.markStructuralReplan()
        session.state.rejectedPlans += 1
        session.state.reobservations += 1
        if (prepared.replanRequired && session.state.rejectedPlans < VisualLoopSupport.MAX_REJECTIONS) {
            return VisualLoopDecision.Continue
        }
        val continued = pauseForUserAndContinue(session, prepared.message, "prepare_blocked", step)
        session.state.rejectedPlans = 0
        return if (continued) VisualLoopDecision.Continue else VisualLoopDecision.Stop
    }

    private suspend fun handleFinish(
        session: VisualTaskSession,
        turn: VisualTurn,
        plan: CloudAgentPlan,
    ): VisualLoopDecision {
        val step = plan.step
        val state = session.state
        val message = step.reason ?: "Visual task completed."
        val expectedCandidate = session.pendingCompletionCandidate
        if (expectedCandidate == null) {
            val candidate = VisualCompletionPermitPolicy.candidate(
                step = step,
                expectedSessionId = session.agentSessionId,
                expectedObservationId = turn.runtime.observationId,
            )
            if (!candidate.valid || candidate.value == null) {
                val feedback = "finish_candidate_rejected:reason=${candidate.reason}|observationId=${turn.runtime.observationId}"
                session.logs += AgentTaskStepLog(
                    session.logs.size + 1,
                    turn.snapshot.currentApp,
                    step.copy(reason = feedback),
                    null,
                )
                VisualLoopSupport.appendRecent(session.recentActions, feedback)
                VisualLoopMemorySupport.rememberTurn(session.visualHistory, turn.snapshot, plan, feedback)
                session.clearCompletionCandidate()
                state.reobservations += 1
                return VisualLoopDecision.Continue
            }
            session.pendingCompletionCandidate = candidate.value
            session.pendingCompletionPackage = turn.snapshot.currentApp
            state.pendingFinishPackage = turn.snapshot.currentApp
            state.pendingFinishFingerprint = VisualActionValidator.completionFingerprint(turn.snapshot)
            state.pendingFinishCount = 1
            val feedback = buildString {
                append("finish_verification_pending:package=").append(turn.snapshot.currentApp.take(100))
                append(":candidateId=").append(candidate.value.id.take(120))
                append(":observationId=").append(candidate.value.observationId.take(120))
                append(":reason=").append(message.take(80))
            }
            session.logs += AgentTaskStepLog(
                session.logs.size + 1,
                turn.snapshot.currentApp,
                step,
                AgentExecutionResult(true, "Completion candidate captured; waiting for independent fresh verification.", true),
            )
            VisualLoopSupport.appendRecent(session.recentActions, feedback)
            VisualLoopMemorySupport.rememberTurn(session.visualHistory, turn.snapshot, plan, feedback)
            state.reobservations += 1
            return VisualLoopDecision.Continue
        }

        val permit = VisualCompletionPermitPolicy.permit(
            step = step,
            expectedSessionId = session.agentSessionId,
            expectedObservationId = turn.runtime.observationId,
            expectedCandidate = expectedCandidate,
        )
        val workSurfaceValid = session.pendingCompletionPackage == turn.snapshot.currentApp &&
            session.execution.isVerifiedWorkSurface(turn.snapshot)
        if (!permit.valid || permit.value == null || !workSurfaceValid) {
            val reason = if (!workSurfaceValid) "completion_work_surface_changed" else permit.reason
            val feedback = "finish_permit_rejected:reason=$reason|observationId=${turn.runtime.observationId}|replanRequired=false"
            session.logs += AgentTaskStepLog(
                session.logs.size + 1,
                turn.snapshot.currentApp,
                step.copy(reason = feedback),
                null,
            )
            VisualLoopSupport.appendRecent(session.recentActions, feedback)
            VisualLoopMemorySupport.rememberTurn(session.visualHistory, turn.snapshot, plan, feedback)
            session.clearCompletionCandidate()
            state.reobservations += 1
            return VisualLoopDecision.Continue
        }

        val completionPermit = permit.value
        val ack = withContext(Dispatchers.IO) {
            aiWorkerClient.acknowledgeVisualCompletion(
                goal = state.goal,
                deviceId = clientDeviceId,
                permit = completionPermit,
            )
        }
        AgentRuntimeController.noteDiagnostic(
            if (ack.acknowledged) "GUI Plus 完成许可已确认" else "完成许可有效；后端清理确认将在会话过期时完成",
        )
        val finalMessage = "$message Independent GUI completion permit verified."
        session.logs += AgentTaskStepLog(
            session.logs.size + 1,
            turn.snapshot.currentApp,
            step,
            AgentExecutionResult(true, finalMessage, false),
        )
        state.completed = true
        session.clearCompletionCandidate()
        AgentRuntimeController.finishTask(session.runtimeTaskId, AgentTaskOutcome.Completed(finalMessage))
        return VisualLoopDecision.Return(AgentTaskRunResult(true, false, finalMessage, session.logs))
    }

    private fun prepareStep(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        appsByPackage: Map<String, InstalledAppEntry>,
    ): PreparedVisualStep {
        if (step.type != "open_app") {
            return PreparedVisualStep(true, step = VisualLoopSupport.materializeTap(step, snapshot))
        }
        val requestedPackage = step.packageName?.trim().orEmpty()
        if (requestedPackage.isBlank()) {
            return PreparedVisualStep(
                false,
                "open_app requires a packageName selected by DeepSeek.",
                replanRequired = true,
            )
        }
        val installed = appsByPackage[requestedPackage]
            ?: return PreparedVisualStep(
                false,
                "App package is not installed or launchable: $requestedPackage",
                replanRequired = true,
            )
        return PreparedVisualStep(
            ok = true,
            step = step.copy(appName = installed.label, packageName = installed.packageName),
            alreadyForeground = snapshot.currentApp == requestedPackage,
        )
    }

    private fun recordResult(
        session: VisualTaskSession,
        currentApp: String,
        step: CloudAgentStep,
        result: AgentExecutionResult,
    ) {
        AgentRuntimeController.noteAction(step)
        AgentRuntimeController.noteResult(step, result)
        session.logs += AgentTaskStepLog(session.logs.size + 1, currentApp, step, result)
    }

    private suspend fun executeStep(
        session: VisualTaskSession,
        step: CloudAgentStep,
        currentApp: String,
        confirmedHighRisk: Boolean,
    ): AgentExecutionResult {
        AgentRuntimeController.noteAction(step)
        return try {
            val result = if (step.type in CloudAgentStep.deviceToolTypes) {
                withContext(Dispatchers.IO) { deviceToolExecutor.execute(step, confirmedHighRisk) }
            } else {
                withContext(Dispatchers.Main) { AiAgentAccessibilityService.executeStep(step) }
            }
            AgentRuntimeController.noteResult(step, result)
            session.logs += AgentTaskStepLog(session.logs.size + 1, currentApp, step, result)
            result
        } catch (error: Throwable) {
            if (step.type !in CloudAgentStep.deviceToolTypes) {
                AgentRuntimeController.endCleanVisualCapture()
            }
            throw error
        }
    }

    private suspend fun handleOpenAppResult(
        session: VisualTaskSession,
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        result: AgentExecutionResult,
        summary: String,
    ) {
        val expectedPackage = step.packageName.orEmpty()
        if (!result.ok) {
            val feedback = "open_app_package_verification_failed:expected=$expectedPackage|actual=${snapshot.packageName.take(100)}|failureClass=structural_route|reason=launch_execution_failed"
            VisualLoopSupport.appendRecent(session.recentActions, feedback)
            VisualLoopMemorySupport.updateLastHistory(session.visualHistory, "$summary;$feedback")
            session.execution.markStructuralReplan()
            return
        }
        val verification = observationCoordinator.awaitStableTargetPackage(
            expectedPackage = expectedPackage,
            isStopped = session::stopped,
        )
        if (verification.verified) {
            val accepted = session.execution.markTargetVerified(expectedPackage, verification)
            if (accepted) {
                val feedback = "open_app_package_verified:package=$expectedPackage|stableSamples=${verification.stableSamples}|visualFrame=true"
                VisualLoopSupport.appendRecent(session.recentActions, feedback)
                VisualLoopMemorySupport.updateLastHistory(session.visualHistory, "$summary;$feedback")
                session.semantic.onVerifiedSurface(verification.lastSnapshot ?: snapshot)
                session.prefetchedObservation = verification.lastObservation
            } else {
                val actual = verification.lastSnapshot?.packageName.orEmpty()
                val feedback = "open_app_package_verification_failed:expected=$expectedPackage|actual=${actual.take(100)}|failureClass=structural_route|reason=verification_proof_invalid"
                VisualLoopSupport.appendRecent(session.recentActions, feedback)
                VisualLoopMemorySupport.updateLastHistory(session.visualHistory, "$summary;$feedback")
                session.execution.markStructuralReplan()
            }
        } else {
            val actual = verification.lastSnapshot?.packageName.orEmpty()
            val pending = VisualSurfacePackagePolicy.requiresForegroundFallback(actual)
            val feedback = if (pending) {
                "open_app_package_verification_pending:expected=$expectedPackage|actual=${actual.take(100)}|reason=transient_surface"
            } else {
                "open_app_package_verification_failed:expected=$expectedPackage|actual=${actual.take(100)}|failureClass=structural_route|reason=target_not_stable"
            }
            VisualLoopSupport.appendRecent(session.recentActions, feedback)
            VisualLoopMemorySupport.updateLastHistory(session.visualHistory, "$summary;$feedback")
            if (!pending) session.execution.markStructuralReplan()
        }
    }

    private suspend fun delayForStep(step: CloudAgentStep) {
        val delayMs = if (step.type == "wait") {
            step.durationMs?.coerceIn(60L, 60_000L) ?: 300L
        } else when (step.type) {
            "tap_xy", "tap_node" -> 110L
            "input_text" -> 130L
            "swipe", "scroll" -> 180L
            "back", "home", "recents", "notifications", "quick_settings" -> 120L
            else -> 130L
        }
        if (delayMs > 0L) delay(delayMs)
    }

    private suspend fun waitWhileUserTakeoverPaused(session: VisualTaskSession): Boolean {
        while (!session.stopped() && AgentRuntimeController.isUserTakeoverPaused()) {
            AgentRuntimeController.ensureOverlayCaptureVisibleIfIdle()
            delay(120L)
        }
        return !session.stopped()
    }

    private suspend fun pauseForUserAndContinue(
        session: VisualTaskSession,
        message: String,
        reason: String,
        step: CloudAgentStep,
    ): Boolean {
        AgentRuntimeController.pauseForUserTakeover(message)
        session.state.paused = true
        VisualLoopSupport.appendInteraction(
            session.interactionActions,
            "guiPlusQuestion:${message.take(VisualLoopSupport.MAX_INTERACTION_TEXT_CHARS)}",
        )
        val reply = AgentRuntimeController.requestUserInput(
            goal = session.state.goal,
            step = step,
            title = "需要用户确认",
            messageOverride = message,
            hintOverride = "补充信息或完成手动步骤后继续",
            positiveText = "继续",
            negativeText = "停止任务",
        )?.trim().orEmpty()
        if (reply.isNotBlank()) {
            val safeReply = if (reply == VisualLoopSupport.PRIVATE_COMPLETION_TOKEN) {
                "[用户已完成手动步骤]"
            } else {
                reply.take(VisualLoopSupport.MAX_INTERACTION_TEXT_CHARS)
            }
            VisualLoopSupport.appendInteraction(session.interactionActions, "userReply:$safeReply")
            AgentRuntimeController.resumeFromUserTakeover("已收到用户回复，继续执行。")
        }
        val canContinue = reply.isNotBlank() || waitWhileUserTakeoverPaused(session)
        session.state.paused = false
        session.clearCompletionCandidate()
        session.semantic.resetAfterUserTakeover()
        if (canContinue) {
            VisualLoopSupport.appendRecent(session.recentActions, "userTakeover=resumed:$reason")
        }
        return canContinue
    }

    private fun finishForLoopExit(session: VisualTaskSession): AgentTaskRunResult {
        val message = when {
            session.state.executedActions >= session.maxSteps -> "Visual loop reached action budget."
            session.state.modelTurns >= session.modelTurnBudget -> "Visual loop reached planning budget."
            else -> "Visual loop stopped."
        }
        if (!session.stopped()) {
            val outcome = if (
                session.state.executedActions >= session.maxSteps ||
                session.state.modelTurns >= session.modelTurnBudget
            ) {
                AgentTaskOutcome.BudgetExceeded(message)
            } else {
                AgentTaskOutcome.Paused(message)
            }
            AgentRuntimeController.finishTask(session.runtimeTaskId, outcome)
        }
        return AgentTaskRunResult(false, false, message, session.logs)
    }

    companion object {
        internal fun requiresAgentSwitch(executionMode: AgentExecutionMode): Boolean =
            executionMode == AgentExecutionMode.VisualForce
    }
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
    var pendingCompletionCandidate: VisualCompletionCandidate? = null,
    var pendingCompletionPackage: String = "",
) {
    fun stopped(): Boolean =
        AgentRuntimeController.currentManualStopGeneration() != stopGeneration

    fun canRun(): Boolean =
        !stopped() && state.executedActions < maxSteps && state.modelTurns < modelTurnBudget

    fun clearCompletionCandidate() {
        pendingCompletionCandidate = null
        pendingCompletionPackage = ""
        state.clearFinishCandidate()
    }
}
