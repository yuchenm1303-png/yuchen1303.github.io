package com.yuchen.ailedger.service

import android.content.Context
import android.os.SystemClock
import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class AgentTaskStepLog(
    val index: Int,
    val app: String,
    val step: CloudAgentStep,
    val execution: AgentExecutionResult?,
)

data class AgentTaskRunResult(
    val completed: Boolean,
    val stoppedForConfirmation: Boolean,
    val message: String,
    val logs: List<AgentTaskStepLog>,
)

class AgentTaskRunner(
    private val aiWorkerClient: AiWorkerClient,
    appContext: Context? = null,
) {
    private val applicationContext: Context? = appContext?.applicationContext
    private val installedAppIndex: InstalledAppIndex? = applicationContext?.let { InstalledAppIndex(it) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun run(
        goal: String,
        modelPreference: ChatModel,
        maxSteps: Int = Int.MAX_VALUE,
    ): AgentTaskRunResult {
        val logs = mutableListOf<AgentTaskStepLog>()
        if (!AgentRuntimeController.isEnabled()) {
            val message = "智能体已关闭，请先打开 Agent 开关。"
            AgentRuntimeController.finishTask(message, completed = false)
            return AgentTaskRunResult(false, false, message, logs)
        }

        val stopGeneration = AgentRuntimeController.currentManualStopGeneration()
        val recentActions = mutableListOf<String>()

        AgentRuntimeController.startTask(goal)

        return try {
            while (!isStopped(stopGeneration)) {
                val observation = captureOnce(forceVisual = true)
                if (!observation.enabled || !observation.serviceConnected) {
                    val message = if (AiAgentAccessibilityService.isConnected()) {
                        "智能体任务已停止，已跳过后台屏幕采集。"
                    } else {
                        "无障碍服务未开启。"
                    }
                    AgentRuntimeController.failTask(message)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                val snapshot = observation.toAgentScreenSnapshot()
                val deviceContext = buildDeviceContext(snapshot, goal)

                val plan = try {
                    withContext(Dispatchers.IO) {
                        aiWorkerClient.requestAgentPlan(
                            goal = goal,
                            snapshot = snapshot,
                            modelPreference = modelPreference,
                            recentActions = recentActions.takeLast(MAX_RECENT_ACTIONS),
                            deviceContext = deviceContext,
                            agentMemory = null,
                        )
                    }
                } catch (error: IOException) {
                    val message = "云端规划超时或失败：${error.message ?: "未知错误"}"
                    AgentRuntimeController.failTask(message)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                val state = plan.state
                if (state != null && state.isComplete && state.confidence >= COMPLETE_CONFIDENCE_THRESHOLD) {
                    val message = state.reason.ifBlank { plan.step.reason ?: "任务完成。" }
                    val finishStep = CloudAgentStep(type = "finish", reason = message, riskLevel = "low", requiresConfirmation = false)
                    val done = AgentExecutionResult(true, message, false)
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, finishStep, done)
                    AgentRuntimeController.finishTask(message, completed = true)
                    return AgentTaskRunResult(true, false, message, logs)
                }

                val step = plan.executableSteps
                    .asSequence()
                    .filterNot { it.type == "finish" }
                    .mapNotNull { sanitizeCloudStep(it, snapshot) }
                    .firstOrNull()

                if (step == null) {
                    val message = "云端没有给出可执行动作，已停止以避免后台持续截图和扫节点。"
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                if (step.type == "need_user_help") {
                    val message = step.reason ?: "需要用户协助。"
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                val wasConfirmedByUser = if (AgentSafetyPolicy.requiresConfirmation(goal, step)) {
                    val confirmed = AgentRuntimeController.requestRiskConfirmation(goal, step)
                    if (!confirmed) return stoppedByUserResult(logs)
                    true
                } else {
                    false
                }

                if (!wasConfirmedByUser && !AgentSafetyPolicy.canAutoExecuteInCurrentStage(goal, step)) {
                    val message = step.reason ?: "当前动作暂不能自动执行：${step.typeLabel}"
                    AgentRuntimeController.finishTask(message, completed = false)
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, step, null)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                val result = executeAndRecord(step, snapshot.currentApp, logs)
                recentActions += "${step.typeLabel}：${result.message.take(80)}"
                if (!result.ok || !result.shouldContinue) {
                    val message = result.message.ifBlank { "智能体动作结束。" }
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                delayForStep(step)
            }

            val message = "用户已手动停止本次智能体任务。"
            AgentRuntimeController.finishTask(message, completed = false)
            AgentTaskRunResult(false, false, message, logs)
        } catch (error: CancellationException) {
            AgentRuntimeController.stopTaskByUser("本次智能体任务已取消。")
            throw error
        } finally {
            AiAgentAccessibilityService.endTaskSession()
            AgentRuntimeController.resetCleanVisualCapture()
        }
    }

    private fun isStopped(startGeneration: Long): Boolean {
        return AgentRuntimeController.currentManualStopGeneration() != startGeneration ||
            !AgentRuntimeController.progress.value.running
    }

    private fun stoppedByUserResult(logs: List<AgentTaskStepLog>): AgentTaskRunResult {
        val message = "用户已手动停止本次智能体任务。"
        return AgentTaskRunResult(false, false, message, logs)
    }

    private fun buildDeviceContext(snapshot: AgentScreenSnapshot, goal: String): AgentDeviceContextSnapshot? {
        val context = applicationContext ?: return null
        val index = installedAppIndex ?: InstalledAppIndex(context)
        return runCatching {
            AgentDeviceContextProvider.build(
                context = context,
                screen = snapshot,
                goal = goal,
                installedAppIndex = index,
            )
        }.getOrNull()
    }

    private suspend fun captureOnce(forceVisual: Boolean = false): ScreenObservation {
        if (!AgentRuntimeController.progress.value.running) {
            return ScreenObservation(
                enabled = true,
                serviceConnected = AiAgentAccessibilityService.isConnected(),
                updatedAt = System.currentTimeMillis(),
            )
        }
        return withContext(Dispatchers.Default) {
            AiAgentAccessibilityService.captureFreshSnapshot(forceVisual = forceVisual)
        }
    }

    private suspend fun executeAndRecord(
        step: CloudAgentStep,
        currentApp: String,
        logs: MutableList<AgentTaskStepLog>,
    ): AgentExecutionResult {
        AgentRuntimeController.noteAction(step)
        delay(ACTION_OVERLAY_HIDE_STABILIZE_MS)
        val result = withContext(Dispatchers.Main) {
            AiAgentAccessibilityService.executeStep(step)
        }
        AgentRuntimeController.noteResult(step, result)
        logs += AgentTaskStepLog(logs.size + 1, currentApp, step, result)
        return result
    }

    private suspend fun delayForStep(step: CloudAgentStep) {
        val delayMs = step.durationMs?.coerceIn(MIN_CUSTOM_STEP_DELAY_MS, MAX_CUSTOM_STEP_DELAY_MS)
            ?: when (step.type) {
                "open_app" -> OPEN_APP_DELAY_MS
                "tap_node", "tap_xy" -> TAP_DELAY_MS
                "input_text" -> INPUT_DELAY_MS
                "scroll", "swipe" -> SCROLL_DELAY_MS
                "wait" -> DEFAULT_WAIT_DELAY_MS
                "back", "home", "recents", "notifications", "quick_settings" -> GLOBAL_ACTION_DELAY_MS
                "finish", "need_user_help" -> 0L
                else -> DEFAULT_STEP_DELAY_MS
            }
        if (delayMs > 0L) delay(delayMs)
    }

    private fun sanitizeCloudStep(step: CloudAgentStep, snapshot: AgentScreenSnapshot): CloudAgentStep? {
        if (step.type !in CloudAgentStep.supportedTypes) return null
        if (step.type == "tap_xy") {
            val x = step.x ?: return null
            val y = step.y ?: return null
            if (x !in 0f..1f || y !in 0f..1f) return null
        }
        if (step.type == "tap_node" && step.targetNodeId.isNullOrBlank() && step.targetText.isNullOrBlank()) return null
        if (step.type == "input_text" && step.text.isNullOrBlank()) return null
        if (step.type == "open_app" && step.packageName != null && step.packageName == snapshot.currentApp) return null
        return step
    }

    companion object {
        private const val DEFAULT_STEP_DELAY_MS = 280L
        private const val ACTION_OVERLAY_HIDE_STABILIZE_MS = 260L
        private const val OPEN_APP_DELAY_MS = 640L
        private const val TAP_DELAY_MS = 220L
        private const val INPUT_DELAY_MS = 180L
        private const val SCROLL_DELAY_MS = 260L
        private const val DEFAULT_WAIT_DELAY_MS = 360L
        private const val GLOBAL_ACTION_DELAY_MS = 240L
        private const val MIN_CUSTOM_STEP_DELAY_MS = 60L
        private const val MAX_CUSTOM_STEP_DELAY_MS = 1_000L
        private const val MAX_RECENT_ACTIONS = 6
        private const val COMPLETE_CONFIDENCE_THRESHOLD = 0.72f
    }
}
