package com.yuchen.ailedger.service

import android.content.Context
import java.io.IOException
import java.text.Normalizer
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
        val recentActions = mutableListOf<String>()
        val visualHistory = mutableListOf<VisualAgentHistoryItem>()
        val agentSessionId = AgentClientIdentity.newVisualSessionId()
        val visualAppContext = withContext(Dispatchers.IO) { buildVisualAppContext() }
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
                            appContext = visualAppContext,
                            deviceId = clientDeviceId,
                            agentSessionId = agentSessionId,
                            executionMode = executionMode,
                        )
                    }
                } catch (error: IOException) {
                    val message = "visual_agent_step failed: ${error.message ?: "unknown error"}"
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                AgentRuntimeController.noteModelOutput(plan.rawModelOutput)

                val step = plan.step
                val validation = VisualActionValidator.validate(step, snapshot)
                if (!validation.ok) {
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, step.copy(reason = validation.message), null)
                    AgentRuntimeController.finishTask(validation.message, completed = false)
                    return AgentTaskRunResult(false, false, validation.message, logs)
                }

                if (step.type == "finish") {
                    val message = step.reason ?: "Visual task completed."
                    val finishFingerprint = VisualActionValidator.completionFingerprint(snapshot)
                    val sameFreshScreenCandidate = state.pendingFinishCount > 0 &&
                        state.pendingFinishPackage == snapshot.currentApp &&
                        state.pendingFinishFingerprint == finishFingerprint
                    if (sameFreshScreenCandidate) {
                        val verifiedMessage = "$message Fresh-screen completion verification passed."
                        logs += AgentTaskStepLog(
                            logs.size + 1,
                            snapshot.currentApp,
                            step,
                            AgentExecutionResult(true, verifiedMessage, false),
                        )
                        state.completed = true
                        state.clearPendingFinishVerification()
                        AgentRuntimeController.finishTask(verifiedMessage, completed = true)
                        return AgentTaskRunResult(true, false, verifiedMessage, logs)
                    }

                    state.pendingFinishPackage = snapshot.currentApp
                    state.pendingFinishFingerprint = finishFingerprint
                    state.pendingFinishCount = 1
                    val finishVerificationSummary = buildString {
                        append("finish_verification_pending")
                        append(":package=").append(snapshot.currentApp.take(100))
                        append(":fingerprint=").append(Integer.toHexString(finishFingerprint.hashCode()))
                        append(":reason=").append(message.take(80))
                    }.take(MAX_RECENT_ACTION_CHARS)
                    logs += AgentTaskStepLog(
                        logs.size + 1,
                        snapshot.currentApp,
                        step,
                        AgentExecutionResult(true, "Completion candidate captured; waiting for a fresh-screen verification.", true),
                    )
                    appendRecentAction(recentActions, finishVerificationSummary)
                    rememberVisualTurn(visualHistory, snapshot, plan, finishVerificationSummary)
                    state.stepCount += 1
                    delay(FINISH_VERIFICATION_DELAY_MS)
                    continue
                }
                state.clearPendingFinishVerification()

                if (step.type == "need_user_help") {
                    val message = step.reason ?: "Visual agent requested user assistance."
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, step, null)
                    if (!pauseForUserAndContinue(
                            message = message,
                            stopGeneration = stopGeneration,
                            state = state,
                            recentActions = recentActions,
                            reason = "model_help",
                            requestStep = step,
                        )
                    ) break
                    continue
                }

                val preparedStep = prepareStepForExecution(step, snapshot)
                if (!preparedStep.ok || preparedStep.step == null) {
                    val message = preparedStep.message
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, step.copy(reason = message), null)
                    if (!pauseForUserAndContinue(message, stopGeneration, state, recentActions, "prepare_blocked")) break
                    continue
                }

                val executableStep = preparedStep.step
                val actionCluster = VisualActionValidator.actionClusterSignature(executableStep)
                val nextRepeatCount = if (actionCluster == state.lastActionCluster) state.sameActionClusterCount + 1 else 0
                if (nextRepeatCount >= REPEATED_ACTION_CLUSTER_LIMIT) {
                    val message = "Repeated visual action in the same screen area was blocked; user takeover is required."
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, executableStep.copy(reason = message), null)
                    if (!pauseForUserAndContinue(message, stopGeneration, state, recentActions, "repeated_action")) break
                    continue
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
                    if (!pauseForUserAndContinue(message, stopGeneration, state, recentActions, "safety_blocked", executableStep)) break
                    continue
                }

                val beforeFingerprint = VisualActionValidator.snapshotFingerprint(snapshot)
                val result = executeStep(executableStep, snapshot.currentApp, logs, confirmed)
                state.lastAction = VisualActionValidator.actionSignature(executableStep)
                state.lastActionCluster = actionCluster
                state.sameActionClusterCount = nextRepeatCount
                val resultSummary = buildExecutionResultSummary(executableStep, state.lastAction, result)
                appendRecentAction(recentActions, resultSummary)
                rememberVisualTurn(visualHistory, snapshot, plan, resultSummary)

                if (!result.ok || !result.shouldContinue) {
                    val message = result.message.ifBlank { "Visual action stopped." }
                    if (!pauseForUserAndContinue(message, stopGeneration, state, recentActions, "action_stopped", executableStep)) break
                    continue
                }

                delayForStep(executableStep)
                val after = captureOnce().toAgentScreenSnapshot()
                val pageChanged = VisualActionValidator.snapshotFingerprint(after) != beforeFingerprint
                val verificationSummary = if (!pageChanged) {
                    state.noProgressCount += 1
                    "visual_no_progress:${state.lastAction}:count=${state.noProgressCount}:screen=unchanged"
                } else {
                    state.noProgressCount = 0
                    "visual_screen_changed:${state.lastAction}:screen=changed"
                }
                appendRecentAction(recentActions, verificationSummary)
                updateLatestVisualTurnResult(visualHistory, "$resultSummary;$verificationSummary")

                if (!pageChanged && state.noProgressCount >= NO_PROGRESS_LIMIT) {
                    val message = "No progress after repeated visual actions; user takeover is required."
                    if (!pauseForUserAndContinue(message, stopGeneration, state, recentActions, "no_progress")) break
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

        val requestedName = step.appName?.trim().orEmpty()
        val requestedPackage = step.packageName?.trim().orEmpty()
        if (requestedName.isBlank() || requestedPackage.isBlank()) {
            return PreparedVisualStep(ok = false, message = "open_app requires the canonical appName and packageName pair.")
        }

        val installed = installedAppIndex.getLaunchableApps().firstOrNull { it.packageName == requestedPackage }
            ?: return PreparedVisualStep(ok = false, message = "App package is not installed: $requestedPackage")
        if (normalizeCanonicalAppLabel(installed.label) != normalizeCanonicalAppLabel(requestedName)) {
            return PreparedVisualStep(
                ok = false,
                message = "App identity mismatch: $requestedName / $requestedPackage",
            )
        }
        if (snapshot.currentApp == requestedPackage) {
            return PreparedVisualStep(ok = false, message = "Already in ${installed.label}; duplicate open_app was blocked.")
        }
        return PreparedVisualStep(ok = true, step = step)
    }

    private fun normalizeCanonicalAppLabel(value: String): String {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
            .lowercase()
            .replace(Regex("\\s+"), "")
    }

    private fun buildVisualAppContext(): List<VisualAgentAppContextItem> {
        return installedAppIndex.getLaunchableApps()
            .asSequence()
            .map { app ->
                VisualAgentAppContextItem(
                    label = app.label,
                    packageName = app.packageName,
                )
            }
            .sortedWith(compareBy<VisualAgentAppContextItem> { it.label.lowercase() }.thenBy { it.packageName })
            .take(MAX_APP_CONTEXT_ITEMS)
            .toList()
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

    private fun buildExecutionResultSummary(
        step: CloudAgentStep,
        actionSignature: String,
        result: AgentExecutionResult,
    ): String {
        val status = if (result.ok) "ok" else "failed"
        val target = step.targetText?.takeIf { it.isNotBlank() }
            ?: step.appName?.takeIf { it.isNotBlank() }
            ?: step.text?.take(32)?.takeIf { it.isNotBlank() }
        return buildList {
            add(actionSignature)
            add(status)
            target?.let { add("target=${it.take(56)}") }
            step.reason?.takeIf { it.isNotBlank() }?.let { add("reason=${it.take(72)}") }
            add("result=${result.message.take(80)}")
        }.joinToString(":").take(MAX_RECENT_ACTION_CHARS)
    }

    private fun appendRecentAction(recentActions: MutableList<String>, value: String) {
        value.trim().take(MAX_RECENT_ACTION_CHARS).takeIf { it.isNotBlank() }?.let(recentActions::add)
        while (recentActions.size > MAX_RECENT_ACTIONS) recentActions.removeAt(0)
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
        reason: String,
        requestStep: CloudAgentStep = CloudAgentStep(type = "need_user_help", reason = message),
    ): Boolean {
        AgentRuntimeController.pauseForUserTakeover(message)
        state.paused = true
        val sensitive = AgentSafetyPolicy.requiresUserProvidedInput(state.goal, requestStep)
        appendRecentAction(recentActions, "guiPlusQuestion:${message.take(MAX_INTERACTION_TEXT_CHARS)}")
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
            appendRecentAction(recentActions, "userReply:$replyForModel")
            AgentRuntimeController.resumeFromUserTakeover("已把你的回复交给 GUI Plus，继续执行。")
        }
        val canContinue = userInstruction.isNotBlank() || waitWhileUserTakeoverPaused(stopGeneration)
        state.paused = false
        state.noProgressCount = 0
        state.sameActionClusterCount = 0
        state.lastActionCluster = ""
        state.clearPendingFinishVerification()
        if (canContinue) {
            appendRecentAction(recentActions, "userTakeover=resumed:$reason")
        }
        return canContinue
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
        private const val MAX_RECENT_ACTIONS = 12
        private const val MAX_RECENT_ACTION_CHARS = 1_200
        private const val MAX_INTERACTION_TEXT_CHARS = 1_000
        private const val MAX_VISUAL_HISTORY_ITEMS = 4
        private const val MAX_APP_CONTEXT_ITEMS = 160
        private const val NO_PROGRESS_LIMIT = Int.MAX_VALUE
        private const val REPEATED_ACTION_CLUSTER_LIMIT = Int.MAX_VALUE
        private const val USER_TAKEOVER_POLL_MS = 120L
        private const val OVERLAY_HIDE_STABILIZE_MS = 260L
        private const val DEFAULT_STEP_DELAY_MS = 280L
        private const val OPEN_APP_DELAY_MS = 640L
        private const val TAP_DELAY_MS = 220L
        private const val INPUT_DELAY_MS = 180L
        private const val SCROLL_DELAY_MS = 260L
        private const val DEFAULT_WAIT_DELAY_MS = 360L
        private const val GLOBAL_ACTION_DELAY_MS = 240L
        private const val FINISH_VERIFICATION_DELAY_MS = 420L
        private const val MIN_CUSTOM_STEP_DELAY_MS = 60L
        private const val MAX_CUSTOM_STEP_DELAY_MS = 1_000L
        private const val PRIVATE_COMPLETION_TOKEN = "__user_completed_private_step__"

        internal fun requiresAgentSwitch(executionMode: AgentExecutionMode): Boolean {
            return executionMode == AgentExecutionMode.VisualForce
        }
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
    var pendingFinishPackage: String = "",
    var pendingFinishFingerprint: String = "",
    var pendingFinishCount: Int = 0,
    var running: Boolean = false,
    var paused: Boolean = false,
    var completed: Boolean = false,
)

private fun VisualLoopState.clearPendingFinishVerification() {
    pendingFinishPackage = ""
    pendingFinishFingerprint = ""
    pendingFinishCount = 0
}

data class VisualActionValidation(
    val ok: Boolean,
    val message: String = "",
)

object VisualActionValidator {
    fun validate(step: CloudAgentStep, snapshot: AgentScreenSnapshot): VisualActionValidation {
        if (step.type !in VisualAgentProtocol.supportedStepTypes) {
            return VisualActionValidation(false, "Unsupported visual action: ${step.type}")
        }
        if (step.type == "tap_xy" && (step.x == null || step.y == null || step.x !in 0f..1f || step.y !in 0f..1f)) {
            return VisualActionValidation(false, "Invalid tap coordinates.")
        }
        if (step.type == "input_text") {
            if (step.text.isNullOrBlank()) {
                return VisualActionValidation(false, "Input text is empty.")
            }
            val matchedTarget = snapshot.inputNodes.any { node ->
                (!step.targetNodeId.isNullOrBlank() && node.id == step.targetNodeId) ||
                    (!step.targetText.isNullOrBlank() && (
                        node.text == step.targetText || node.text.contains(step.targetText, ignoreCase = true)
                    ))
            }
            if (step.shouldUseFocusedDirectInput && snapshot.inputNodes.size > 1 && !matchedTarget) {
                return VisualActionValidation(
                    false,
                    "当前页面存在多个输入框，但 GUI Plus 没有提供可确认的输入目标；已阻止盲目输入。",
                )
            }
            if (!step.shouldUseFocusedDirectInput && snapshot.inputNodes.size != 1 && !matchedTarget) {
                return VisualActionValidation(
                    false,
                    "输入动作缺少唯一输入框或明确目标；已阻止将文字写入错误位置。",
                )
            }
        }
        if (step.type == "open_app" && (step.appName.isNullOrBlank() || step.packageName.isNullOrBlank())) {
            return VisualActionValidation(false, "open_app requires the canonical appName and packageName pair.")
        }
        if (step.type == "open_app" && step.packageName == snapshot.currentApp) {
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

    private const val TAP_CLUSTER_BUCKET_PX = 96f
    private const val VISUAL_FINGERPRINT_NODE_THRESHOLD = 3
    private const val VISUAL_FINGERPRINT_SAMPLE_COUNT = 256
    private const val COMPLETION_FINGERPRINT_TEXT_LIMIT = 20
    private const val COMPLETION_FINGERPRINT_NODE_LIMIT = 16
}
