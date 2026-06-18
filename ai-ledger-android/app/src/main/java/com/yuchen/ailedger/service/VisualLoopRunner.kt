package com.yuchen.ailedger.service

import android.content.Context
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

    suspend fun run(
        goal: String,
        maxSteps: Int = Int.MAX_VALUE,
    ): AgentTaskRunResult {
        val logs = mutableListOf<AgentTaskStepLog>()
        if (!AgentRuntimeController.isEnabled()) {
            val message = "Visual agent is off; direct visual loop was not started."
            AgentRuntimeController.finishTask(message, completed = false)
            return AgentTaskRunResult(false, false, message, logs)
        }

        val state = VisualLoopState(goal = goal.trim().take(240), running = true)
        val recentActions = mutableListOf<String>()
        val visualHistory = mutableListOf<VisualAgentHistoryItem>()
        AiAgentAccessibilityService.beginTaskSession()
        AgentRuntimeController.startTask(state.goal)
        val stopGeneration = AgentRuntimeController.currentManualStopGeneration()

        return try {
            while (!isStopped(stopGeneration) && logs.size < maxSteps) {
                if (!waitWhileUserTakeoverPaused(stopGeneration)) break

                val observation = captureOnce()
                if (!observation.enabled || !observation.serviceConnected) {
                    val message = "Accessibility service is not connected; visual loop stopped."
                    AgentRuntimeController.failTask(message)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                val snapshot = observation.toAgentScreenSnapshot()
                state.currentPackage = snapshot.currentApp

                val plan = try {
                    withContext(Dispatchers.IO) {
                        aiWorkerClient.requestVisualAgentStep(
                            goal = state.goal,
                            snapshot = snapshot,
                            recentActions = recentActions,
                            visualHistory = visualHistory,
                        )
                    }
                } catch (error: IOException) {
                    val message = "visual_agent_step failed: ${error.message ?: "unknown error"}"
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                val step = plan.step
                val validation = VisualActionValidator.validate(step, snapshot)
                if (!validation.ok) {
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, step.copy(reason = validation.message), null)
                    AgentRuntimeController.finishTask(validation.message, completed = false)
                    return AgentTaskRunResult(false, false, validation.message, logs)
                }

                if (step.type == "finish") {
                    val message = step.reason ?: "Visual task completed."
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, step, AgentExecutionResult(true, message, false))
                    state.completed = true
                    AgentRuntimeController.finishTask(message, completed = true)
                    return AgentTaskRunResult(true, false, message, logs)
                }
                if (step.type == "need_user_help") {
                    val message = step.reason ?: "Visual agent requested user assistance."
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, step, null)
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                val preparedStep = prepareStepForExecution(step, snapshot)
                if (!preparedStep.ok || preparedStep.step == null) {
                    val message = preparedStep.message
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, step.copy(reason = message), null)
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                val executableStep = preparedStep.step
                val actionCluster = VisualActionValidator.actionClusterSignature(executableStep)
                val nextRepeatCount = if (actionCluster == state.lastActionCluster) state.sameActionClusterCount + 1 else 0
                if (nextRepeatCount >= REPEATED_ACTION_CLUSTER_LIMIT) {
                    val message = "Repeated visual action in the same screen area was blocked; user takeover is required."
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, executableStep.copy(reason = message), null)
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
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
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                val beforeFingerprint = VisualActionValidator.snapshotFingerprint(snapshot)
                val result = executeStep(executableStep, snapshot.currentApp, logs, confirmed)
                state.lastAction = VisualActionValidator.actionSignature(executableStep)
                state.lastActionCluster = actionCluster
                state.sameActionClusterCount = nextRepeatCount
                val resultSummary = "${state.lastAction}:${if (result.ok) "ok" else "failed"}:${result.message.take(80)}"
                recentActions += resultSummary
                while (recentActions.size > MAX_RECENT_ACTIONS) recentActions.removeAt(0)
                rememberVisualTurn(visualHistory, snapshot, plan, resultSummary)

                if (!result.ok || !result.shouldContinue) {
                    val message = result.message.ifBlank { "Visual action stopped." }
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                delayForStep(executableStep)
                val after = captureOnce().toAgentScreenSnapshot()
                if (VisualActionValidator.snapshotFingerprint(after) == beforeFingerprint) {
                    state.noProgressCount += 1
                    if (state.noProgressCount >= NO_PROGRESS_LIMIT) {
                        val message = "No progress after repeated visual actions; user takeover is required."
                        AgentRuntimeController.pauseForUserTakeover(message)
                        state.paused = true
                        if (!waitWhileUserTakeoverPaused(stopGeneration)) break
                        state.paused = false
                        state.noProgressCount = 0
                    }
                } else {
                    state.noProgressCount = 0
                }
                state.stepCount += 1
            }

            val message = if (logs.size >= maxSteps) "Visual loop reached max step budget." else "Visual loop stopped."
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

    private suspend fun captureOnce(): ScreenObservation {
        AgentRuntimeController.beginCleanVisualCapture()
        return try {
            delay(OVERLAY_HIDE_STABILIZE_MS)
            withContext(Dispatchers.Default) {
                AiAgentAccessibilityService.captureFreshSnapshot(forceVisual = true)
            }
        } finally {
            AgentRuntimeController.endCleanVisualCapture()
        }
    }

    private suspend fun executeStep(
        step: CloudAgentStep,
        currentApp: String,
        logs: MutableList<AgentTaskStepLog>,
        confirmedHighRisk: Boolean,
    ): AgentExecutionResult {
        AgentRuntimeController.noteAction(step)
        AgentRuntimeController.beginCleanVisualCapture()
        val result = try {
            delay(OVERLAY_HIDE_STABILIZE_MS)
            if (step.type == "open_app") {
                withContext(Dispatchers.IO) { deviceToolExecutor.execute(step, confirmedHighRisk) }
            } else {
                withContext(Dispatchers.Main) { AiAgentAccessibilityService.executeStep(step) }
            }
        } finally {
            AgentRuntimeController.endCleanVisualCapture()
        }
        AgentRuntimeController.noteResult(step, result)
        logs += AgentTaskStepLog(logs.size + 1, currentApp, step, result)
        return result
    }

    private fun prepareStepForExecution(step: CloudAgentStep, snapshot: AgentScreenSnapshot): PreparedVisualStep {
        if (step.type != "open_app") return PreparedVisualStep(ok = true, step = materializeTapCoordinateFrame(step, snapshot))

        val requestedName = step.appName ?: step.targetText ?: step.text.orEmpty()
        val resolution = installedAppIndex.resolveExplicitAppName(appName = requestedName)
        return when (resolution.status) {
            ExplicitAppResolutionStatus.Exact -> {
                val app = requireNotNull(resolution.app)
                if (snapshot.currentApp == app.packageName) {
                    PreparedVisualStep(ok = false, message = "Already in ${app.label}; duplicate open_app was blocked.")
                } else {
                    PreparedVisualStep(ok = true, step = step.copy(appName = app.label, packageName = app.packageName))
                }
            }
            ExplicitAppResolutionStatus.Ambiguous -> PreparedVisualStep(ok = false, message = "App name is ambiguous: $requestedName")
            ExplicitAppResolutionStatus.NotFound -> PreparedVisualStep(ok = false, message = "App is not installed: $requestedName")
        }
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

    private suspend fun delayForStep(step: CloudAgentStep) {
        val delayMs = step.durationMs?.coerceIn(MIN_CUSTOM_STEP_DELAY_MS, MAX_CUSTOM_STEP_DELAY_MS)
            ?: when (step.type) {
                "open_app" -> OPEN_APP_DELAY_MS
                "tap_xy" -> TAP_DELAY_MS
                "input_text" -> INPUT_DELAY_MS
                "swipe" -> SCROLL_DELAY_MS
                "wait" -> DEFAULT_WAIT_DELAY_MS
                "back", "home" -> GLOBAL_ACTION_DELAY_MS
                else -> DEFAULT_STEP_DELAY_MS
            }
        if (delayMs > 0L) delay(delayMs)
    }

    private data class PreparedVisualStep(
        val ok: Boolean,
        val message: String = "",
        val step: CloudAgentStep? = null,
    )

    private fun rememberVisualTurn(
        history: MutableList<VisualAgentHistoryItem>,
        snapshot: AgentScreenSnapshot,
        plan: CloudAgentPlan,
        executionResult: String,
    ) {
        val visual = snapshot.visual?.takeIf { it.hasImage } ?: return
        val assistantOutput = plan.rawModelOutput.ifBlank { plan.step.reason.orEmpty() }
        if (assistantOutput.isBlank()) return
        history += VisualAgentHistoryItem(
            screenshot = visual,
            assistantOutput = assistantOutput,
            executionResult = executionResult,
        )
        while (history.size > MAX_VISUAL_HISTORY_ITEMS) history.removeAt(0)
    }

    companion object {
        private const val MAX_RECENT_ACTIONS = 6
        private const val MAX_VISUAL_HISTORY_ITEMS = 4
        private const val NO_PROGRESS_LIMIT = 2
        private const val REPEATED_ACTION_CLUSTER_LIMIT = 2
        private const val USER_TAKEOVER_POLL_MS = 120L
        private const val OVERLAY_HIDE_STABILIZE_MS = 260L
        private const val DEFAULT_STEP_DELAY_MS = 280L
        private const val OPEN_APP_DELAY_MS = 640L
        private const val TAP_DELAY_MS = 220L
        private const val INPUT_DELAY_MS = 180L
        private const val SCROLL_DELAY_MS = 260L
        private const val DEFAULT_WAIT_DELAY_MS = 360L
        private const val GLOBAL_ACTION_DELAY_MS = 240L
        private const val MIN_CUSTOM_STEP_DELAY_MS = 60L
        private const val MAX_CUSTOM_STEP_DELAY_MS = 1_000L
    }
}

data class VisualLoopState(
    val goal: String,
    var stepCount: Int = 0,
    var currentPackage: String = "",
    var lastAction: String = "",
    var lastActionCluster: String = "",
    var sameActionClusterCount: Int = 0,
    var noProgressCount: Int = 0,
    var running: Boolean = false,
    var paused: Boolean = false,
    var completed: Boolean = false,
)

data class VisualActionValidation(
    val ok: Boolean,
    val message: String = "",
)

object VisualActionValidator {
    fun validate(step: CloudAgentStep, snapshot: AgentScreenSnapshot): VisualActionValidation {
        if (step.type !in allowedTypes) return VisualActionValidation(false, "Unsupported visual action: ${step.type}")
        if (step.type == "tap_xy" && (step.x == null || step.y == null || step.x !in 0f..1f || step.y !in 0f..1f)) {
            return VisualActionValidation(false, "Invalid tap coordinates.")
        }
        if (step.type == "input_text" && step.text.isNullOrBlank()) {
            return VisualActionValidation(false, "Input text is empty.")
        }
        if (step.type == "open_app" && step.appName.isNullOrBlank() && step.targetText.isNullOrBlank() && step.text.isNullOrBlank()) {
            return VisualActionValidation(false, "open_app requires an explicit app name.")
        }
        if (step.type == "open_app" && !step.packageName.isNullOrBlank() && step.packageName == snapshot.currentApp) {
            return VisualActionValidation(false, "Duplicate open_app was blocked.")
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
        return listOf(snapshot.currentApp, snapshot.capturedNodeCount.toString(), textKey, nodeKey).joinToString("::")
    }

    private val allowedTypes = setOf(
        "open_app",
        "tap_xy",
        "input_text",
        "swipe",
        "back",
        "home",
        "wait",
        "finish",
        "need_user_help",
    )

    private const val TAP_CLUSTER_BUCKET_PX = 96f
}
