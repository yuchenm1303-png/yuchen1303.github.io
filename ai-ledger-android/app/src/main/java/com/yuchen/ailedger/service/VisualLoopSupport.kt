package com.yuchen.ailedger.service

internal object VisualLoopSupport {
    const val MAX_RECENT_ACTIONS = 14
    const val MAX_RECENT_ACTION_CHARS = 1_200
    const val MAX_INTERACTION_TEXT_CHARS = 1_000
    const val MAX_INTERACTION_ACTIONS = 12
    const val MAX_INTERACTION_IN_REQUEST = 8
    const val CLIENT_ACTION_LIMIT = 14
    const val MIN_RUNTIME_ACTIONS = 6
    const val NORMAL_HISTORY_ITEMS = 2
    const val RECOVERY_HISTORY_ITEMS = 4
    const val MAX_APP_CONTEXT_ITEMS = 160
    const val MAX_REJECTIONS = 3
    const val PRIVATE_COMPLETION_TOKEN = "__user_completed_private_step__"

    fun materializeTap(step: CloudAgentStep, snapshot: AgentScreenSnapshot): CloudAgentStep {
        if (step.type != "tap_xy") return step
        val x = step.x ?: return step
        val y = step.y ?: return step
        val visual = snapshot.visual ?: return step
        val width = visual.displayWidth.takeIf { it > 0 } ?: visual.width.takeIf { it > 0 } ?: return step
        val height = visual.displayHeight.takeIf { it > 0 } ?: visual.height.takeIf { it > 0 } ?: return step
        return step.copy(
            x = (x * width).coerceIn(0f, width.toFloat()),
            y = (y * height).coerceIn(0f, height.toFloat()),
        )
    }

    fun requiresFreshObservation(step: CloudAgentStep): Boolean =
        step.type !in CloudAgentStep.deviceToolTypes &&
            step.type !in setOf("open_app", "wait", "need_user_help", "finish")

    fun requiresAccessibility(step: CloudAgentStep): Boolean =
        step.type == "open_app" ||
            (step.type !in CloudAgentStep.deviceToolTypes && step.type !in setOf("need_user_help", "finish"))

    fun validationFeedback(
        step: CloudAgentStep,
        validation: VisualActionValidation,
        runtime: VisualAgentRuntimeContext,
    ): String {
        val prefix = if (validation.failureClass == VisualFailureClass.StructuralRoute) {
            "visual_action_rejected"
        } else {
            "visual_action_retry"
        }
        return buildString {
            append(prefix).append(":type=").append(step.type)
            append("|failureClass=").append(validation.failureClass.wireValue)
            append("|surfaceState=").append(runtime.surfaceState.wireValue)
            append("|observationId=").append(runtime.observationId)
            append("|reason=").append(validation.message.take(260))
            append("|replanRequired=").append(validation.failureClass == VisualFailureClass.StructuralRoute)
        }.take(MAX_RECENT_ACTION_CHARS)
    }

    fun resultSummary(
        step: CloudAgentStep,
        signature: String,
        result: AgentExecutionResult,
    ): String {
        val status = when {
            result.ok -> "ok"
            step.type == "open_app" -> "failed"
            else -> "retry"
        }
        val target = step.targetText?.takeIf(String::isNotBlank)
            ?: step.appName?.takeIf(String::isNotBlank)
            ?: step.packageName?.takeIf(String::isNotBlank)
            ?: step.text?.take(32)?.takeIf(String::isNotBlank)
        return buildList {
            add(signature)
            add(status)
            target?.let { add("target=${it.take(56)}") }
            step.purpose?.takeIf(String::isNotBlank)?.let { add("purpose=${it.take(72)}") }
            step.hypothesisId?.takeIf(String::isNotBlank)?.let { add("hypothesis=${it.take(72)}") }
            add("result=${result.message.take(80)}")
        }.joinToString(":").take(MAX_RECENT_ACTION_CHARS)
    }

    fun appendRecent(actions: MutableList<String>, value: String) {
        value.trim().take(MAX_RECENT_ACTION_CHARS).takeIf(String::isNotBlank)?.let(actions::add)
        while (actions.size > MAX_RECENT_ACTIONS) actions.removeAt(0)
    }

    fun appendInteraction(actions: MutableList<String>, value: String) {
        value.trim().take(MAX_INTERACTION_TEXT_CHARS + 80).takeIf(String::isNotBlank)?.let(actions::add)
        while (actions.size > MAX_INTERACTION_ACTIONS) actions.removeAt(0)
    }

    fun requestActions(recent: List<String>, interactions: List<String>): List<String> {
        val interactionBudget = interactions.takeLast(MAX_INTERACTION_IN_REQUEST)
        val runtimeBudget = (CLIENT_ACTION_LIMIT - interactionBudget.size).coerceAtLeast(MIN_RUNTIME_ACTIONS)
        return recent.takeLast(runtimeBudget) + interactionBudget
    }

    fun modelTurnBudget(maxSteps: Int): Int {
        if (maxSteps == Int.MAX_VALUE) return Int.MAX_VALUE
        return (maxSteps * 3).coerceAtLeast(maxSteps + 8).coerceAtMost(120)
    }
}

internal fun fatal(session: VisualTaskSession, message: String): VisualLoopDecision {
    AgentRuntimeController.failTask(session.runtimeTaskId, message)
    return VisualLoopDecision.Return(AgentTaskRunResult(false, false, message, session.logs))
}

internal suspend fun VisualTaskExecutor.rejectPlan(
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
    if (validation.failureClass == VisualFailureClass.StructuralRoute) session.execution.markStructuralReplan()
    session.state.rejectedPlans += 1
    session.state.reobservations += 1
    if (session.state.rejectedPlans < VisualLoopSupport.MAX_REJECTIONS) return VisualLoopDecision.Continue
    val continued = pauseForUserAndContinue(session, validation.message, "validation_rejected", step)
    session.state.rejectedPlans = 0
    return if (continued) VisualLoopDecision.Continue else VisualLoopDecision.Stop
}

internal suspend fun VisualTaskExecutor.rejectPrepared(
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

internal fun VisualTaskExecutor.handleFinish(
    session: VisualTaskSession,
    turn: VisualTurn,
    plan: CloudAgentPlan,
): VisualLoopDecision {
    val step = plan.step
    val state = session.state
    val message = step.reason ?: "Visual task completed."
    val fingerprint = VisualActionValidator.completionFingerprint(turn.snapshot)
    val verified = state.pendingFinishCount > 0 &&
        state.pendingFinishPackage == turn.snapshot.currentApp &&
        state.pendingFinishFingerprint == fingerprint &&
        session.execution.isVerifiedWorkSurface(turn.snapshot)
    if (verified) {
        val finalMessage = "$message Fresh-screen completion verification passed."
        session.logs += AgentTaskStepLog(
            session.logs.size + 1,
            turn.snapshot.currentApp,
            step,
            AgentExecutionResult(true, finalMessage, false),
        )
        state.completed = true
        state.clearFinishCandidate()
        AgentRuntimeController.finishTask(session.runtimeTaskId, AgentTaskOutcome.Completed(finalMessage))
        return VisualLoopDecision.Return(AgentTaskRunResult(true, false, finalMessage, session.logs))
    }
    state.pendingFinishPackage = turn.snapshot.currentApp
    state.pendingFinishFingerprint = fingerprint
    state.pendingFinishCount = 1
    val feedback = "finish_verification_pending:package=${turn.snapshot.currentApp.take(100)}:fingerprint=${Integer.toHexString(fingerprint.hashCode())}:observationId=${turn.runtime.observationId}:reason=${message.take(80)}"
    session.logs += AgentTaskStepLog(
        session.logs.size + 1,
        turn.snapshot.currentApp,
        step,
        AgentExecutionResult(true, "Completion candidate captured; waiting for fresh verification.", true),
    )
    VisualLoopSupport.appendRecent(session.recentActions, feedback)
    VisualLoopMemorySupport.rememberTurn(session.visualHistory, turn.snapshot, plan, feedback)
    state.reobservations += 1
    return VisualLoopDecision.Continue
}

internal fun VisualTaskExecutor.prepareStep(
    step: CloudAgentStep,
    snapshot: AgentScreenSnapshot,
    appsByPackage: Map<String, InstalledAppEntry>,
): PreparedVisualStep {
    if (step.type != "open_app") {
        return PreparedVisualStep(true, step = VisualLoopSupport.materializeTap(step, snapshot))
    }
    val requestedPackage = step.packageName?.trim().orEmpty()
    if (requestedPackage.isBlank()) {
        return PreparedVisualStep(false, "open_app requires a packageName selected by DeepSeek.", replanRequired = true)
    }
    val installed = appsByPackage[requestedPackage]
        ?: return PreparedVisualStep(false, "App package is not installed or launchable: $requestedPackage", replanRequired = true)
    return PreparedVisualStep(
        ok = true,
        step = step.copy(appName = installed.label, packageName = installed.packageName),
        alreadyForeground = snapshot.currentApp == requestedPackage,
    )
}

internal fun recordResult(
    session: VisualTaskSession,
    currentApp: String,
    step: CloudAgentStep,
    result: AgentExecutionResult,
) {
    AgentRuntimeController.noteAction(step)
    AgentRuntimeController.noteResult(step, result)
    session.logs += AgentTaskStepLog(session.logs.size + 1, currentApp, step, result)
}
