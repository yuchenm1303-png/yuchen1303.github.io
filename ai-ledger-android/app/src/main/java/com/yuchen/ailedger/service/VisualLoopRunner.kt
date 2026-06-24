package com.yuchen.ailedger.service

import android.content.Context
import android.os.SystemClock
import java.io.IOException
import kotlin.math.roundToInt
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
    private val clientDeviceId by lazy { AgentClientIdentity.getOrCreateDeviceId(applicationContext) }

    suspend fun run(
        goal: String,
        maxSteps: Int = Int.MAX_VALUE,
        executionMode: AgentExecutionMode = AgentExecutionMode.VisualForce,
    ): AgentTaskRunResult {
        val logs = mutableListOf<AgentTaskStepLog>()
        if (requiresAgentSwitch(executionMode) && !AgentRuntimeController.isEnabled()) {
            val message = "Visual agent is off; forced visual loop was not started."
            AgentRuntimeController.finishTask(message, completed = false)
            return AgentTaskRunResult(false, false, message, logs)
        }

        val state = VisualLoopState(goal = goal.trim().take(240), running = true)
        val executionSession = VisualExecutionSessionState()
        val recentActions = mutableListOf<String>()
        val interactionActions = mutableListOf<String>()
        val visualHistory = mutableListOf<VisualAgentHistoryItem>()
        val agentSessionId = AgentClientIdentity.newVisualSessionId()
        val deviceProfile = AgentDeviceProfile.current()
        val installedApps = withContext(Dispatchers.IO) {
            installedAppIndex.getLaunchableApps(forceReload = false)
        }
        val installedAppsByPackage = installedApps.associateBy { it.packageName }
        val visualAppContext = buildVisualAppContext(installedApps)
        val modelTurnBudget = modelTurnBudget(maxSteps)
        var prefetchedObservation: ScreenObservation? = null
        var fullAppCatalogUploaded = false

        appendRecentAction(recentActions, deviceProfile.toPromptLine())
        appendRecentAction(
            recentActions,
            "cloud_routing:v3|mainBrain=deepseek|appSelectionOwner=deepseek|visualOwner=gui_plus|androidSemanticRouting=false|localKeywordMatching=false|targetBindingRequired=true",
        )
        appendRecentAction(
            recentActions,
            "app_identity:v2|machineIdentity=packageName|appNameRole=display_only|androidCanonicalizesLabel=true",
        )

        AiAgentAccessibilityService.beginTaskSession()
        AgentRuntimeController.startTask(state.goal)
        val stopGeneration = AgentRuntimeController.currentManualStopGeneration()

        return try {
            while (
                !isStopped(stopGeneration) &&
                state.executedActionCount < maxSteps &&
                state.modelTurnCount < modelTurnBudget
            ) {
                if (!waitWhileUserTakeoverPaused(stopGeneration)) break

                val observation = prefetchedObservation?.also { prefetchedObservation = null }
                    ?: captureOnce(forceVisual = executionSession.requiresVisualObservation())
                val snapshot = observation.toAgentScreenSnapshot()
                state.currentPackage = snapshot.currentApp
                val runtimeContext = executionSession.runtimeContext(snapshot)
                val requestObservation = if (runtimeContext.guiPlusEligible && snapshot.visual?.hasImage != true) {
                    captureOnce(forceVisual = true)
                } else {
                    observation
                }
                val requestSnapshot = requestObservation.toAgentScreenSnapshot()
                val requestRuntimeContext = if (requestObservation === observation) {
                    runtimeContext
                } else {
                    executionSession.runtimeContext(requestSnapshot)
                }
                state.currentPackage = requestSnapshot.currentApp
                replaceRuntimeContextAction(recentActions, requestRuntimeContext)
                val requestActions = buildRequestActions(recentActions, interactionActions)
                val requestAppContext = appContextForTurn(
                    fullContext = visualAppContext,
                    runtimeContext = requestRuntimeContext,
                    fullCatalogUploaded = fullAppCatalogUploaded,
                )

                state.modelTurnCount += 1
                val plan = try {
                    withContext(Dispatchers.IO) {
                        aiWorkerClient.requestVisualAgentStep(
                            goal = state.goal,
                            snapshot = requestSnapshot,
                            recentActions = requestActions,
                            visualHistory = visualHistory,
                            appContext = requestAppContext,
                            deviceId = clientDeviceId,
                            agentSessionId = agentSessionId,
                            executionMode = executionMode,
                            deviceProfile = deviceProfile,
                            runtimeContext = requestRuntimeContext,
                        )
                    }
                } catch (error: IOException) {
                    val message = "visual_agent_step failed: ${error.message ?: "unknown error"}"
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                if (requestAppContext.size == visualAppContext.size) fullAppCatalogUploaded = true
                AgentRuntimeController.noteModelOutput(plan.rawModelOutput)

                val step = plan.step
                if (requiresAccessibilityRuntime(step) && (!requestObservation.enabled || !requestObservation.serviceConnected)) {
                    val message = "The cloud selected a visual action, but the Android accessibility service is not connected."
                    AgentRuntimeController.failTask(message)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                val validation = VisualActionValidator.validate(step, requestSnapshot, requestRuntimeContext)
                if (!validation.ok) {
                    val rejection = buildValidationFeedback(step, validation, requestRuntimeContext)
                    logs += AgentTaskStepLog(logs.size + 1, requestSnapshot.currentApp, step.copy(reason = validation.message), null)
                    appendRecentAction(recentActions, rejection)
                    rememberVisualTurn(visualHistory, requestSnapshot, plan, rejection)
                    if (validation.failureClass == VisualFailureClass.StructuralRoute) {
                        executionSession.markStructuralReplan()
                    }
                    state.replanRejectCount += 1
                    state.reobserveCount += 1
                    if (state.replanRejectCount >= MAX_STRUCTURED_REPLAN_REJECTIONS) {
                        if (!pauseForUserAndContinue(
                                validation.message,
                                stopGeneration,
                                state,
                                recentActions,
                                interactionActions,
                                "validation_rejected",
                                step,
                            )
                        ) break
                        state.replanRejectCount = 0
                    }
                    continue
                }

                if (step.type == "finish") {
                    val message = step.reason ?: "Visual task completed."
                    val finishFingerprint = VisualActionValidator.completionFingerprint(requestSnapshot)
                    val sameFreshScreenCandidate = state.pendingFinishCount > 0 &&
                        state.pendingFinishPackage == requestSnapshot.currentApp &&
                        state.pendingFinishFingerprint == finishFingerprint &&
                        executionSession.isVerifiedWorkSurface(requestSnapshot)
                    if (sameFreshScreenCandidate) {
                        val verifiedMessage = "$message Fresh-screen completion verification passed."
                        logs += AgentTaskStepLog(
                            logs.size + 1,
                            requestSnapshot.currentApp,
                            step,
                            AgentExecutionResult(true, verifiedMessage, false),
                        )
                        state.completed = true
                        state.clearPendingFinishVerification()
                        AgentRuntimeController.finishTask(verifiedMessage, completed = true)
                        return AgentTaskRunResult(true, false, verifiedMessage, logs)
                    }

                    state.pendingFinishPackage = requestSnapshot.currentApp
                    state.pendingFinishFingerprint = finishFingerprint
                    state.pendingFinishCount = 1
                    val finishVerificationSummary = buildString {
                        append("finish_verification_pending")
                        append(":package=").append(requestSnapshot.currentApp.take(100))
                        append(":fingerprint=").append(Integer.toHexString(finishFingerprint.hashCode()))
                        append(":observationId=").append(requestRuntimeContext.observationId)
                        append(":reason=").append(message.take(80))
                    }.take(MAX_RECENT_ACTION_CHARS)
                    logs += AgentTaskStepLog(
                        logs.size + 1,
                        requestSnapshot.currentApp,
                        step,
                        AgentExecutionResult(true, "Completion candidate captured; waiting for a fresh-screen verification.", true),
                    )
                    appendRecentAction(recentActions, finishVerificationSummary)
                    rememberVisualTurn(visualHistory, requestSnapshot, plan, finishVerificationSummary)
                    state.reobserveCount += 1
                    delay(FINISH_VERIFICATION_DELAY_MS)
                    continue
                }
                state.clearPendingFinishVerification()

                if (step.type == "need_user_help") {
                    val message = step.reason ?: "Visual agent requested user assistance."
                    logs += AgentTaskStepLog(logs.size + 1, requestSnapshot.currentApp, step, null)
                    if (!pauseForUserAndContinue(
                            message = message,
                            stopGeneration = stopGeneration,
                            state = state,
                            recentActions = recentActions,
                            interactionActions = interactionActions,
                            reason = "model_help",
                            requestStep = step,
                        )
                    ) break
                    continue
                }

                val preparedStep = prepareStepForExecution(
                    step = step,
                    snapshot = requestSnapshot,
                    installedAppsByPackage = installedAppsByPackage,
                )
                if (!preparedStep.ok || preparedStep.step == null) {
                    val message = preparedStep.message
                    val rejection = "visual_action_rejected:type=${step.type}|failureClass=structural_route|reason=${message.take(260)}|replanRequired=${preparedStep.replanRequired}"
                    logs += AgentTaskStepLog(logs.size + 1, requestSnapshot.currentApp, step.copy(reason = message), null)
                    appendRecentAction(recentActions, rejection)
                    rememberVisualTurn(visualHistory, requestSnapshot, plan, rejection)
                    executionSession.markStructuralReplan()
                    state.replanRejectCount += 1
                    state.reobserveCount += 1
                    if (!preparedStep.replanRequired || state.replanRejectCount >= MAX_STRUCTURED_REPLAN_REJECTIONS) {
                        if (!pauseForUserAndContinue(
                                message,
                                stopGeneration,
                                state,
                                recentActions,
                                interactionActions,
                                "prepare_blocked",
                                step,
                            )
                        ) break
                        state.replanRejectCount = 0
                    }
                    continue
                }

                val executableStep = preparedStep.step
                val actionCluster = VisualActionValidator.actionClusterSignature(executableStep)
                val nextRepeatCount = if (actionCluster == state.lastActionCluster) {
                    state.sameActionClusterCount + 1
                } else {
                    1
                }
                if (nextRepeatCount >= REPEATED_ACTION_CLUSTER_LIMIT) {
                    val message = "Repeated visual action in the same screen area requires a new cloud route."
                    val rejection = "visual_action_rejected:type=${executableStep.type}|failureClass=structural_route|reason=repeated_action_cluster|count=$nextRepeatCount|replanRequired=true"
                    logs += AgentTaskStepLog(logs.size + 1, requestSnapshot.currentApp, executableStep.copy(reason = message), null)
                    appendRecentAction(recentActions, rejection)
                    rememberVisualTurn(visualHistory, requestSnapshot, plan, rejection)
                    executionSession.markStructuralReplan()
                    state.sameActionClusterCount = 0
                    state.lastActionCluster = ""
                    state.reobserveCount += 1
                    continue
                }

                if (executableStep.type == "open_app") {
                    val expectedPackage = executableStep.packageName?.trim().orEmpty()
                    executionSession.beginLaunch(expectedPackage)
                    if (preparedStep.alreadyForeground) {
                        val result = AgentExecutionResult(
                            ok = true,
                            message = "Target package is already foreground and was verified: $expectedPackage",
                            shouldContinue = true,
                        )
                        AgentRuntimeController.noteAction(executableStep)
                        AgentRuntimeController.noteResult(executableStep, result)
                        logs += AgentTaskStepLog(logs.size + 1, requestSnapshot.currentApp, executableStep, result)
                        val resultSummary = buildExecutionResultSummary(executableStep, VisualActionValidator.actionSignature(executableStep), result)
                        val verification = "open_app_package_verified:package=$expectedPackage|mode=already_foreground"
                        appendRecentAction(recentActions, resultSummary)
                        appendRecentAction(recentActions, verification)
                        rememberVisualTurn(visualHistory, requestSnapshot, plan, "$resultSummary;$verification")
                        executionSession.markTargetVerified(expectedPackage)
                        state.noProgressCount = 0
                        state.consecutiveScreenChangeCount = 0
                        state.sameActionClusterCount = 0
                        state.lastActionCluster = ""
                        state.executedActionCount += 1
                        continue
                    }
                }

                val confirmed = if (AgentSafetyPolicy.requiresConfirmation(state.goal, executableStep)) {
                    if (!AgentRuntimeController.requestRiskConfirmation(state.goal, executableStep)) {
                        return AgentTaskRunResult(false, false, "User stopped the visual task.", logs)
                    }
                    true
                } else {
                    false
                }
                if (!confirmed && !AgentSafetyPolicy.canAutoExecuteInCurrentStage(state.goal, executableStep)) {
                    val message = executableStep.reason ?: "Action is blocked by Android safety policy."
                    if (!pauseForUserAndContinue(
                            message,
                            stopGeneration,
                            state,
                            recentActions,
                            interactionActions,
                            "safety_blocked",
                            executableStep,
                        )
                    ) break
                    continue
                }

                if (requiresFreshObservation(executableStep)) {
                    val currentBeforeExecution = captureOnce(
                        forceVisual = false,
                        settleMs = PACKAGE_CHECK_OVERLAY_SETTLE_MS,
                    ).toAgentScreenSnapshot()
                    executionSession.synchronizeWith(currentBeforeExecution)
                    val stillOnVerifiedSurface = executionSession.isVerifiedWorkSurface(currentBeforeExecution)
                    val contextFresh = VisualObservationProtocol.isActionContextFresh(requestSnapshot, currentBeforeExecution)
                    if (!stillOnVerifiedSurface || !contextFresh) {
                        val staleFeedback = buildString {
                            append("visual_action_stale")
                            append(":type=").append(executableStep.type)
                            append("|failureClass=")
                            append(if (stillOnVerifiedSurface) "visual_local" else "structural_route")
                            append("|observationId=").append(requestRuntimeContext.observationId)
                            append("|observedPackage=").append(requestSnapshot.packageName.take(100))
                            append("|currentPackage=").append(currentBeforeExecution.packageName.take(100))
                            append("|reason=").append(if (stillOnVerifiedSurface) "screen_changed_before_execution" else "target_surface_lost")
                        }.take(MAX_RECENT_ACTION_CHARS)
                        logs += AgentTaskStepLog(
                            logs.size + 1,
                            currentBeforeExecution.currentApp,
                            executableStep.copy(reason = "The verified target surface changed before execution; a fresh decision is required."),
                            null,
                        )
                        appendRecentAction(recentActions, staleFeedback)
                        rememberVisualTurn(visualHistory, requestSnapshot, plan, staleFeedback)
                        if (!stillOnVerifiedSurface) executionSession.markStructuralReplan()
                        state.reobserveCount += 1
                        continue
                    }
                }

                val beforeFingerprint = VisualActionValidator.snapshotFingerprint(requestSnapshot)
                val result = executeStep(executableStep, requestSnapshot.currentApp, logs, confirmed)
                state.executedActionCount += 1
                state.lastAction = VisualActionValidator.actionSignature(executableStep)
                state.lastActionCluster = actionCluster
                state.sameActionClusterCount = nextRepeatCount
                state.replanRejectCount = 0
                val resultSummary = buildExecutionResultSummary(executableStep, state.lastAction, result)
                appendRecentAction(recentActions, resultSummary)
                rememberVisualTurn(visualHistory, requestSnapshot, plan, resultSummary)

                if (executableStep.type == "open_app") {
                    val expectedPackage = executableStep.packageName?.trim().orEmpty()
                    if (!result.ok) {
                        val failure = "open_app_package_verification_failed:expected=$expectedPackage|actual=${requestSnapshot.packageName.take(100)}|failureClass=structural_route|reason=launch_execution_failed"
                        appendRecentAction(recentActions, failure)
                        updateLatestVisualTurnResult(visualHistory, "$resultSummary;$failure")
                        executionSession.markStructuralReplan()
                        continue
                    }
                    val verification = awaitStableTargetPackage(
                        expectedPackage = expectedPackage,
                        stopGeneration = stopGeneration,
                    )
                    if (verification.verified) {
                        val verifiedEvent = "open_app_package_verified:package=$expectedPackage|stableSamples=${verification.stableSamples}"
                        appendRecentAction(recentActions, verifiedEvent)
                        updateLatestVisualTurnResult(visualHistory, "$resultSummary;$verifiedEvent")
                        executionSession.markTargetVerified(expectedPackage)
                        state.noProgressCount = 0
                        state.consecutiveScreenChangeCount = 0
                        state.sameActionClusterCount = 0
                        state.lastActionCluster = ""
                        verification.lastObservation?.let { prefetchedObservation = it }
                    } else {
                        val actualPackage = verification.lastSnapshot?.packageName.orEmpty()
                        val pending = actualPackage.isBlank() || actualPackage == VisualExecutionSessionState.ASSISTANT_HOST_PACKAGE ||
                            actualPackage in TRANSIENT_HANDOFF_PACKAGES
                        val failure = if (pending) {
                            "open_app_package_verification_pending:expected=$expectedPackage|actual=${actualPackage.take(100)}|reason=transient_surface"
                        } else {
                            "open_app_package_verification_failed:expected=$expectedPackage|actual=${actualPackage.take(100)}|failureClass=structural_route|reason=target_not_stable"
                        }
                        appendRecentAction(recentActions, failure)
                        updateLatestVisualTurnResult(visualHistory, "$resultSummary;$failure")
                        if (!pending) executionSession.markStructuralReplan()
                    }
                    continue
                }

                if (executableStep.type in CloudAgentStep.deviceToolTypes) {
                    val message = result.message.ifBlank { "Internal device tool finished." }
                    AgentRuntimeController.finishTask(message, completed = result.ok)
                    return AgentTaskRunResult(result.ok, false, message, logs)
                }

                if (!result.ok || !result.shouldContinue) {
                    val message = result.message.ifBlank { "Visual action stopped." }
                    if (!pauseForUserAndContinue(
                            message,
                            stopGeneration,
                            state,
                            recentActions,
                            interactionActions,
                            "action_stopped",
                            executableStep,
                        )
                    ) break
                    continue
                }

                delayForStep(executableStep)
                val afterObservation = captureOnce(forceVisual = true)
                val after = afterObservation.toAgentScreenSnapshot()
                executionSession.synchronizeWith(after)
                prefetchedObservation = afterObservation
                val pageChanged = VisualActionValidator.snapshotFingerprint(after) != beforeFingerprint
                val visualChangeSummary = if (!pageChanged) {
                    state.consecutiveScreenChangeCount = 0
                    state.noProgressCount += 1
                    if (state.noProgressCount >= STRUCTURAL_NO_PROGRESS_LIMIT) {
                        executionSession.markStructuralReplan()
                        prefetchedObservation = null
                        "visual_no_progress:${state.lastAction}:count=${state.noProgressCount}:screen=unchanged|failureClass=structural_route"
                    } else {
                        "visual_local_retry:action=${state.lastAction}:count=${state.noProgressCount}|reason=screen_unchanged"
                    }
                } else {
                    state.noProgressCount = 0
                    state.sameActionClusterCount = 0
                    state.lastActionCluster = ""
                    state.consecutiveScreenChangeCount += 1
                    if (state.consecutiveScreenChangeCount >= EXPLORATION_SPRAWL_LIMIT) {
                        "visual_exploration_sprawl:${state.lastAction}:count=${state.consecutiveScreenChangeCount}:screen=changed|failureClass=visual_local|reason=multi_hop_without_convergence"
                    } else {
                        "visual_screen_changed:${state.lastAction}:screen=changed"
                    }
                }
                appendRecentAction(recentActions, visualChangeSummary)

                updateLatestVisualTurnResult(
                    visualHistory,
                    listOf(resultSummary, visualChangeSummary)
                        .filter { it.isNotBlank() }
                        .joinToString(";"),
                )
            }

            val message = when {
                state.executedActionCount >= maxSteps -> "Visual loop reached action budget."
                state.modelTurnCount >= modelTurnBudget -> "Visual loop reached planning budget."
                else -> "Visual loop stopped."
            }
            AgentRuntimeController.finishTask(message, completed = false)
            AgentTaskRunResult(false, false, message, logs)
        } catch (error: CancellationException) {
            AgentRuntimeController.stopTaskByUser("Visual task was cancelled.")
            throw error
        } finally {
            AiAgentAccessibilityService.endTaskSession()
            AgentRuntimeController.resetCleanVisualCapture()
        }
    }

    private suspend fun captureOnce(
        forceVisual: Boolean,
        settleMs: Long = if (forceVisual) 0L else NON_VISUAL_CAPTURE_SETTLE_MS,
    ): ScreenObservation {
        AgentRuntimeController.beginCleanVisualCapture()
        return try {
            if (settleMs > 0L) delay(settleMs)
            withContext(Dispatchers.Default) {
                AiAgentAccessibilityService.captureFreshSnapshot(forceVisual = forceVisual)
            }
        } finally {
            AgentRuntimeController.endCleanVisualCapture()
        }
    }

    private suspend fun awaitStableTargetPackage(
        expectedPackage: String,
        stopGeneration: Long,
    ): LaunchPackageVerification {
        if (expectedPackage.isBlank()) return LaunchPackageVerification(false, 0, null, null)
        val deadline = SystemClock.elapsedRealtime() + OPEN_APP_VERIFY_TIMEOUT_MS
        var stableSamples = 0
        var lastSnapshot: AgentScreenSnapshot? = null
        var lastObservation: ScreenObservation? = null
        delay(OPEN_APP_INITIAL_SETTLE_MS)
        while (!isStopped(stopGeneration) && SystemClock.elapsedRealtime() < deadline) {
            val observation = captureOnce(
                forceVisual = true,
                settleMs = PACKAGE_CHECK_OVERLAY_SETTLE_MS,
            )
            val snapshot = observation.toAgentScreenSnapshot()
            lastObservation = observation
            lastSnapshot = snapshot
            if (snapshot.packageName == expectedPackage) {
                stableSamples += 1
                if (stableSamples >= OPEN_APP_REQUIRED_STABLE_SAMPLES) {
                    return LaunchPackageVerification(true, stableSamples, snapshot, observation)
                }
            } else if (snapshot.packageName.isNotBlank() &&
                snapshot.packageName != VisualExecutionSessionState.ASSISTANT_HOST_PACKAGE &&
                snapshot.packageName !in TRANSIENT_HANDOFF_PACKAGES
            ) {
                stableSamples = 0
            }
            delay(OPEN_APP_VERIFY_POLL_MS)
        }
        return LaunchPackageVerification(false, stableSamples, lastSnapshot, lastObservation)
    }

    private suspend fun executeStep(
        step: CloudAgentStep,
        currentApp: String,
        logs: MutableList<AgentTaskStepLog>,
        confirmedHighRisk: Boolean,
    ): AgentExecutionResult {
        AgentRuntimeController.noteAction(step)
        val result = if (step.type in CloudAgentStep.deviceToolTypes) {
            withContext(Dispatchers.IO) { deviceToolExecutor.execute(step, confirmedHighRisk) }
        } else {
            withContext(Dispatchers.Main) { AiAgentAccessibilityService.executeStep(step) }
        }
        AgentRuntimeController.noteResult(step, result)
        logs += AgentTaskStepLog(logs.size + 1, currentApp, step, result)
        return result
    }

    private fun prepareStepForExecution(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        installedAppsByPackage: Map<String, InstalledAppEntry>,
    ): PreparedVisualStep {
        if (step.type != "open_app") {
            return PreparedVisualStep(ok = true, step = materializeTapCoordinateFrame(step, snapshot))
        }

        val requestedPackage = step.packageName?.trim().orEmpty()
        if (requestedPackage.isBlank()) {
            return PreparedVisualStep(
                ok = false,
                message = "open_app requires a packageName selected by DeepSeek from the current device app catalog; appName is display-only.",
                replanRequired = true,
            )
        }

        val installed = installedAppsByPackage[requestedPackage]
            ?: return PreparedVisualStep(
                ok = false,
                message = "App package is not installed or not launchable: $requestedPackage",
                replanRequired = true,
            )
        return PreparedVisualStep(
            ok = true,
            step = step.copy(appName = installed.label, packageName = installed.packageName),
            alreadyForeground = snapshot.currentApp == requestedPackage,
        )
    }

    private fun buildVisualAppContext(apps: List<InstalledAppEntry>): List<VisualAgentAppContextItem> {
        return apps
            .asSequence()
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
            .take(MAX_APP_CONTEXT_ITEMS)
            .toList()
    }

    private fun appContextForTurn(
        fullContext: List<VisualAgentAppContextItem>,
        runtimeContext: VisualAgentRuntimeContext,
        fullCatalogUploaded: Boolean,
    ): List<VisualAgentAppContextItem> {
        if (!fullCatalogUploaded || runtimeContext.surfaceState != VisualSurfaceState.WorkSurface) {
            return fullContext
        }
        val verifiedPackage = runtimeContext.verifiedTargetPackage
        return fullContext.filter { it.packageName == verifiedPackage }.take(1)
    }

    private fun materializeTapCoordinateFrame(step: CloudAgentStep, snapshot: AgentScreenSnapshot): CloudAgentStep {
        if (step.type != "tap_xy") return step
        val x = step.x ?: return step
        val y = step.y ?: return step
        val visual = snapshot.visual ?: return step
        val visualWidth = visual.displayWidth.takeIf { it > 0 } ?: visual.width.takeIf { it > 0 } ?: return step
        val visualHeight = visual.displayHeight.takeIf { it > 0 } ?: visual.height.takeIf { it > 0 } ?: return step
        return step.copy(
            x = (x * visualWidth).coerceIn(0f, visualWidth.toFloat()),
            y = (y * visualHeight).coerceIn(0f, visualHeight.toFloat()),
        )
    }

    private fun requiresFreshObservation(step: CloudAgentStep): Boolean {
        return step.type !in CloudAgentStep.deviceToolTypes &&
            step.type !in setOf("open_app", "wait", "need_user_help", "finish")
    }

    private fun requiresAccessibilityRuntime(step: CloudAgentStep): Boolean {
        return step.type == "open_app" ||
            (step.type !in CloudAgentStep.deviceToolTypes && step.type !in setOf("need_user_help", "finish"))
    }

    private fun buildValidationFeedback(
        step: CloudAgentStep,
        validation: VisualActionValidation,
        runtimeContext: VisualAgentRuntimeContext,
    ): String {
        val prefix = if (validation.failureClass == VisualFailureClass.StructuralRoute) {
            "visual_action_rejected"
        } else {
            "visual_action_retry"
        }
        return buildString {
            append(prefix)
            append(":type=").append(step.type)
            append("|failureClass=").append(validation.failureClass.wireValue)
            append("|surfaceState=").append(runtimeContext.surfaceState.wireValue)
            append("|observationId=").append(runtimeContext.observationId)
            append("|reason=").append(validation.message.take(260))
            append("|replanRequired=").append(validation.failureClass == VisualFailureClass.StructuralRoute)
        }.take(MAX_RECENT_ACTION_CHARS)
    }

    private fun buildExecutionResultSummary(
        step: CloudAgentStep,
        actionSignature: String,
        result: AgentExecutionResult,
    ): String {
        val status = when {
            result.ok -> "ok"
            step.type == "open_app" -> "failed"
            else -> "retry"
        }
        val target = step.targetText?.takeIf { it.isNotBlank() }
            ?: step.appName?.takeIf { it.isNotBlank() }
            ?: step.packageName?.takeIf { it.isNotBlank() }
            ?: step.text?.take(32)?.takeIf { it.isNotBlank() }
        return buildList {
            add(actionSignature)
            add(status)
            target?.let { add("target=${it.take(56)}") }
            step.reason?.takeIf { it.isNotBlank() }?.let { add("reason=${it.take(72)}") }
            add("result=${result.message.take(80)}")
        }.joinToString(":").take(MAX_RECENT_ACTION_CHARS)
    }

    private fun replaceRuntimeContextAction(
        recentActions: MutableList<String>,
        runtimeContext: VisualAgentRuntimeContext,
    ) {
        recentActions.removeAll { it.startsWith(RUNTIME_CONTEXT_PREFIX) }
        appendRecentAction(
            recentActions,
            buildString {
                append(RUNTIME_CONTEXT_PREFIX)
                append("state=").append(runtimeContext.surfaceState.wireValue)
                append("|selectedTargetPackage=").append(runtimeContext.selectedTargetPackage.take(100))
                append("|verifiedTargetPackage=").append(runtimeContext.verifiedTargetPackage.take(100))
                append("|currentPackage=").append(runtimeContext.currentPackage.take(100))
                append("|guiPlusEligible=").append(runtimeContext.guiPlusEligible)
                append("|observationId=").append(runtimeContext.observationId)
                append("|routeEpoch=").append(runtimeContext.routeEpoch)
                append("|surfaceEpoch=").append(runtimeContext.surfaceEpoch)
            },
        )
    }

    private fun appendRecentAction(recentActions: MutableList<String>, value: String) {
        value.trim().take(MAX_RECENT_ACTION_CHARS).takeIf { it.isNotBlank() }?.let(recentActions::add)
        while (recentActions.size > MAX_RECENT_ACTIONS) recentActions.removeAt(0)
    }

    private fun appendInteractionAction(interactionActions: MutableList<String>, value: String) {
        value.trim().take(MAX_INTERACTION_TEXT_CHARS + 80).takeIf { it.isNotBlank() }?.let(interactionActions::add)
        while (interactionActions.size > MAX_INTERACTION_ACTIONS) interactionActions.removeAt(0)
    }

    private fun buildRequestActions(
        recentActions: List<String>,
        interactionActions: List<String>,
    ): List<String> {
        val interactionBudget = interactionActions.takeLast(MAX_INTERACTION_ACTIONS_IN_REQUEST)
        val runtimeBudget = (VISUAL_CLIENT_ACTION_LIMIT - interactionBudget.size).coerceAtLeast(MIN_RUNTIME_ACTIONS_IN_REQUEST)
        return recentActions.takeLast(runtimeBudget) + interactionBudget
    }

    private fun updateLatestVisualTurnResult(
        history: MutableList<VisualAgentHistoryItem>,
        executionResult: String,
    ) {
        if (history.isEmpty()) return
        val lastIndex = history.lastIndex
        history[lastIndex] = history[lastIndex].copy(executionResult = executionResult.take(240))
    }

    private fun isStopped(startGeneration: Long): Boolean {
        return AgentRuntimeController.currentManualStopGeneration() != startGeneration
    }

    private suspend fun waitWhileUserTakeoverPaused(startGeneration: Long): Boolean {
        while (!isStopped(startGeneration) && AgentRuntimeController.isUserTakeoverPaused()) {
            AgentRuntimeController.ensureOverlayCaptureVisibleIfIdle()
            delay(USER_TAKEOVER_POLL_MS)
        }
        return !isStopped(startGeneration)
    }

    private suspend fun pauseForUserAndContinue(
        message: String,
        stopGeneration: Long,
        state: VisualLoopState,
        recentActions: MutableList<String>,
        interactionActions: MutableList<String>,
        reason: String,
        requestStep: CloudAgentStep = CloudAgentStep(type = "need_user_help", reason = message),
    ): Boolean {
        AgentRuntimeController.pauseForUserTakeover(message)
        state.paused = true
        val sensitive = AgentSafetyPolicy.requiresUserProvidedInput(state.goal, requestStep)
        appendInteractionAction(interactionActions, "guiPlusQuestion:${message.take(MAX_INTERACTION_TEXT_CHARS)}")
        val userInstruction = AgentRuntimeController.requestUserInput(
            goal = state.goal,
            step = requestStep,
            title = if (sensitive) "需要你完成隐私操作" else "GUI Plus 需要你补充信息",
            messageOverride = if (sensitive) {
                "$message\n\n请在目标应用中手动完成密码、验证码或身份校验。App 不会读取或回传具体内容；完成后点击“已完成，继续”。"
            } else {
                message
            },
            hintOverride = if (sensitive) "请在目标应用中完成" else "可以分行说明你的选择、条件或补充信息",
            positiveText = if (sensitive) "已完成，继续" else "发送给 GUI Plus",
            negativeText = "停止任务",
        )?.trim().orEmpty()
        if (userInstruction.isNotBlank()) {
            val replyForModel = if (sensitive || userInstruction == PRIVATE_COMPLETION_TOKEN) {
                "[用户已在目标应用中完成敏感输入]"
            } else {
                userInstruction.take(MAX_INTERACTION_TEXT_CHARS)
            }
            appendInteractionAction(interactionActions, "userReply:$replyForModel")
            AgentRuntimeController.resumeFromUserTakeover("已把你的回复交给 GUI Plus，继续执行。")
        }
        val canContinue = userInstruction.isNotBlank() || waitWhileUserTakeoverPaused(stopGeneration)
        state.paused = false
        state.noProgressCount = 0
        state.consecutiveScreenChangeCount = 0
        state.sameActionClusterCount = 0
        state.lastActionCluster = ""
        state.clearPendingFinishVerification()
        if (canContinue) {
            appendRecentAction(recentActions, "userTakeover=resumed:$reason")
        }
        return canContinue
    }

    private suspend fun delayForStep(step: CloudAgentStep) {
        val delayMs = if (step.type == "wait") {
            step.durationMs?.coerceIn(MIN_CUSTOM_STEP_DELAY_MS, MAX_WAIT_DELAY_MS) ?: DEFAULT_WAIT_DELAY_MS
        } else {
            when (step.type) {
                "tap_xy", "tap_node" -> TAP_DELAY_MS
                "input_text" -> INPUT_DELAY_MS
                "swipe", "scroll" -> SCROLL_DELAY_MS
                "back", "home", "recents" -> GLOBAL_ACTION_DELAY_MS
                else -> DEFAULT_STEP_DELAY_MS
            }
        }
        if (delayMs > 0L) delay(delayMs)
    }

    private fun modelTurnBudget(maxSteps: Int): Int {
        if (maxSteps == Int.MAX_VALUE) return Int.MAX_VALUE
        return (maxSteps * MODEL_TURN_MULTIPLIER)
            .coerceAtLeast(maxSteps + MIN_EXTRA_MODEL_TURNS)
            .coerceAtMost(MAX_MODEL_TURN_BUDGET)
    }

    private data class PreparedVisualStep(
        val ok: Boolean,
        val message: String = "",
        val step: CloudAgentStep? = null,
        val replanRequired: Boolean = false,
        val alreadyForeground: Boolean = false,
    )

    private data class LaunchPackageVerification(
        val verified: Boolean,
        val stableSamples: Int,
        val lastSnapshot: AgentScreenSnapshot?,
        val lastObservation: ScreenObservation?,
    )

    private fun rememberVisualTurn(
        history: MutableList<VisualAgentHistoryItem>,
        snapshot: AgentScreenSnapshot,
        plan: CloudAgentPlan,
        executionResult: String,
    ) {
        val visual = snapshot.visual ?: return
        val assistantOutput = plan.rawModelOutput.ifBlank { plan.step.reason.orEmpty() }
        if (assistantOutput.isBlank()) return
        history += VisualAgentHistoryItem(
            screenshot = visual.copy(base64Jpeg = ""),
            assistantOutput = assistantOutput,
            executionResult = executionResult,
        )
        while (history.size > MAX_VISUAL_HISTORY_ITEMS) history.removeAt(0)
    }

    companion object {
        private const val MAX_RECENT_ACTIONS = 14
        private const val MAX_RECENT_ACTION_CHARS = 1_200
        private const val MAX_INTERACTION_TEXT_CHARS = 1_000
        private const val MAX_INTERACTION_ACTIONS = 12
        private const val MAX_INTERACTION_ACTIONS_IN_REQUEST = 8
        private const val VISUAL_CLIENT_ACTION_LIMIT = 14
        private const val MIN_RUNTIME_ACTIONS_IN_REQUEST = 6
        private const val MAX_VISUAL_HISTORY_ITEMS = 2
        private const val MAX_APP_CONTEXT_ITEMS = 160
        private const val MAX_STRUCTURED_REPLAN_REJECTIONS = 3
        private const val REPEATED_ACTION_CLUSTER_LIMIT = 3
        private const val STRUCTURAL_NO_PROGRESS_LIMIT = 3
        private const val EXPLORATION_SPRAWL_LIMIT = 4
        private const val USER_TAKEOVER_POLL_MS = 120L
        private const val NON_VISUAL_CAPTURE_SETTLE_MS = 35L
        private const val PACKAGE_CHECK_OVERLAY_SETTLE_MS = 20L
        private const val DEFAULT_STEP_DELAY_MS = 130L
        private const val TAP_DELAY_MS = 110L
        private const val INPUT_DELAY_MS = 130L
        private const val SCROLL_DELAY_MS = 180L
        private const val DEFAULT_WAIT_DELAY_MS = 300L
        private const val GLOBAL_ACTION_DELAY_MS = 120L
        private const val FINISH_VERIFICATION_DELAY_MS = 280L
        private const val MIN_CUSTOM_STEP_DELAY_MS = 60L
        private const val MAX_WAIT_DELAY_MS = 60_000L
        private const val OPEN_APP_INITIAL_SETTLE_MS = 260L
        private const val OPEN_APP_VERIFY_POLL_MS = 140L
        private const val OPEN_APP_VERIFY_TIMEOUT_MS = 4_200L
        private const val OPEN_APP_REQUIRED_STABLE_SAMPLES = 2
        private const val MODEL_TURN_MULTIPLIER = 3
        private const val MIN_EXTRA_MODEL_TURNS = 8
        private const val MAX_MODEL_TURN_BUDGET = 120
        private const val PRIVATE_COMPLETION_TOKEN = "__user_completed_private_step__"
        private const val RUNTIME_CONTEXT_PREFIX = "visual_runtime_context:v1|"
        private val TRANSIENT_HANDOFF_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
        )

        internal fun requiresAgentSwitch(executionMode: AgentExecutionMode): Boolean {
            return executionMode == AgentExecutionMode.VisualForce
        }
    }
}

data class VisualLoopState(
    val goal: String,
    var modelTurnCount: Int = 0,
    var executedActionCount: Int = 0,
    var reobserveCount: Int = 0,
    var currentPackage: String = "",
    var lastAction: String = "",
    var lastActionCluster: String = "",
    var sameActionClusterCount: Int = 0,
    var consecutiveScreenChangeCount: Int = 0,
    var noProgressCount: Int = 0,
    var pendingFinishPackage: String = "",
    var pendingFinishFingerprint: String = "",
    var pendingFinishCount: Int = 0,
    var replanRejectCount: Int = 0,
    var running: Boolean = false,
    var paused: Boolean = false,
    var completed: Boolean = false,
)

private fun VisualLoopState.clearPendingFinishVerification() {
    pendingFinishPackage = ""
    pendingFinishFingerprint = ""
    pendingFinishCount = 0
}

enum class VisualFailureClass(val wireValue: String) {
    VisualLocal("visual_local"),
    StructuralRoute("structural_route"),
}

data class VisualActionValidation(
    val ok: Boolean,
    val message: String = "",
    val failureClass: VisualFailureClass = VisualFailureClass.VisualLocal,
)

object VisualActionValidator {
    fun validate(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        runtimeContext: VisualAgentRuntimeContext? = null,
    ): VisualActionValidation {
        if (step.type !in VisualAgentProtocol.supportedStepTypes) {
            return VisualActionValidation(
                false,
                "Unsupported visual action: ${step.type}",
                VisualFailureClass.StructuralRoute,
            )
        }
        if (step.type == "open_app" && step.packageName.isNullOrBlank()) {
            return VisualActionValidation(
                false,
                "open_app requires a packageName selected by DeepSeek from the current device app catalog.",
                VisualFailureClass.StructuralRoute,
            )
        }
        if (runtimeContext?.guiPlusEligible == true && step.type in CloudAgentStep.deviceToolTypes) {
            return VisualActionValidation(
                false,
                "GUI Plus owns only visual actions after handoff; internal tools and app selection remain owned by DeepSeek.",
                VisualFailureClass.StructuralRoute,
            )
        }
        if (runtimeContext != null && !runtimeContext.guiPlusEligible && step.type !in PRE_WORK_SURFACE_ACTIONS) {
            return VisualActionValidation(
                false,
                "No verified target work surface is active. DeepSeek must select an exact installed package with open_app before GUI Plus can act or finish.",
                VisualFailureClass.StructuralRoute,
            )
        }
        if (runtimeContext == null &&
            snapshot.packageName == VisualExecutionSessionState.ASSISTANT_HOST_PACKAGE &&
            step.type !in PRE_WORK_SURFACE_ACTIONS
        ) {
            return VisualActionValidation(
                false,
                "The current screen is the AI controller, not a verified target app. DeepSeek must return open_app before GUI Plus can act or finish.",
                VisualFailureClass.StructuralRoute,
            )
        }
        if (step.type == "tap_xy" && (step.x == null || step.y == null || step.x !in 0f..1f || step.y !in 0f..1f)) {
            return VisualActionValidation(false, "Invalid tap coordinates.")
        }
        if (step.type == "input_text" && step.text.isNullOrBlank()) {
            return VisualActionValidation(false, "Input text is empty.")
        }
        return VisualActionValidation(true)
    }

    fun actionSignature(step: CloudAgentStep): String {
        return listOfNotNull(
            step.type,
            step.packageName,
            step.appName,
            step.targetText,
            step.text?.take(32),
            step.direction,
            step.x?.toString(),
            step.y?.toString(),
        ).joinToString("|")
    }

    fun actionClusterSignature(step: CloudAgentStep): String {
        if (step.type != "tap_xy") return actionSignature(step)
        val x = step.x ?: return actionSignature(step)
        val y = step.y ?: return actionSignature(step)
        val bucketX = (x / TAP_CLUSTER_BUCKET_PX).roundToInt()
        val bucketY = (y / TAP_CLUSTER_BUCKET_PX).roundToInt()
        return listOf("tap_xy", bucketX, bucketY).joinToString("|")
    }

    fun snapshotFingerprint(snapshot: AgentScreenSnapshot): String {
        val textKey = snapshot.texts.take(16).joinToString("|") { it.take(40) }
        val nodeKey = snapshot.clickableNodes.take(16).joinToString("|") { "${it.text.take(24)}#${it.bounds}" }
        val visualKey = if (snapshot.capturedNodeCount <= VISUAL_FINGERPRINT_NODE_THRESHOLD || snapshot.clickableNodes.isEmpty()) {
            sampledVisualFingerprint(snapshot.visual)
        } else {
            ""
        }
        return listOf(snapshot.currentApp, snapshot.capturedNodeCount.toString(), textKey, nodeKey, visualKey).joinToString("::")
    }

    fun completionFingerprint(snapshot: AgentScreenSnapshot): String {
        val textKey = snapshot.texts
            .asSequence()
            .map { it.trim().take(40) }
            .filter { it.isNotBlank() }
            .take(COMPLETION_FINGERPRINT_TEXT_LIMIT)
            .joinToString("|")
        val nodeKey = snapshot.clickableNodes
            .asSequence()
            .map { "${it.text.trim().take(24)}#${it.bounds}" }
            .take(COMPLETION_FINGERPRINT_NODE_LIMIT)
            .joinToString("|")
        return listOf(snapshot.currentApp, textKey, nodeKey).joinToString("::")
    }

    private fun sampledVisualFingerprint(visual: AgentScreenVisual?): String {
        val image = visual?.takeIf { it.hasImage } ?: return ""
        val data = image.base64Jpeg
        if (data.isBlank()) return ""
        val stride = (data.length / VISUAL_FINGERPRINT_SAMPLE_COUNT).coerceAtLeast(1)
        var hash = 1_125_899_906_842_597L
        var index = 0
        while (index < data.length) {
            hash = hash * 31L + data[index].code
            index += stride
        }
        return "${image.width}x${image.height}:${data.length}:${hash.toString(16)}"
    }

    private val PRE_WORK_SURFACE_ACTIONS = CloudAgentStep.deviceToolTypes + "need_user_help"
    private const val TAP_CLUSTER_BUCKET_PX = 96f
    private const val VISUAL_FINGERPRINT_NODE_THRESHOLD = 3
    private const val VISUAL_FINGERPRINT_SAMPLE_COUNT = 256
    private const val COMPLETION_FINGERPRINT_TEXT_LIMIT = 20
    private const val COMPLETION_FINGERPRINT_NODE_LIMIT = 16
}
