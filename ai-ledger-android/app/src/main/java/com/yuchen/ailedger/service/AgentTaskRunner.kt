package com.yuchen.ailedger.service

import android.content.Context
import android.os.SystemClock
import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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

        AgentRuntimeController.startTask(goal)
        val stopGeneration = AgentRuntimeController.currentManualStopGeneration()
        val memory = AgentRunMemory()
        installedAppIndex?.let { index ->
            val preloadStart = SystemClock.elapsedRealtime()
            withContext(Dispatchers.IO) { index.getLaunchableApps(forceReload = false) }
            memory.recordTrace("应用索引 ${SystemClock.elapsedRealtime() - preloadStart}ms")
        }

        tryRunInternalDeviceControl(goal, memory)?.let { return it }

        while (true) {
            if (isStopped(stopGeneration)) return stoppedByUserResult(memory, logs)

            val loopStart = SystemClock.elapsedRealtime()
            val captureStart = SystemClock.elapsedRealtime()
            val observation = captureOnce(forceVisual = true)
            val captureMs = SystemClock.elapsedRealtime() - captureStart
            if (!observation.enabled || !observation.serviceConnected) {
                val message = "无障碍服务未开启"
                AgentRuntimeController.failTask(message)
                return AgentTaskRunResult(false, false, message, logs)
            }

            val snapshotStart = SystemClock.elapsedRealtime()
            val snapshot = observation.toAgentScreenSnapshot()
            val snapshotMs = SystemClock.elapsedRealtime() - snapshotStart
            memory.observe(snapshot)

            if (isStopped(stopGeneration)) return stoppedByUserResult(memory, logs)

            val deviceStart = SystemClock.elapsedRealtime()
            val deviceContext = buildDeviceContext(snapshot, goal)
            val deviceMs = SystemClock.elapsedRealtime() - deviceStart

            val plan = try {
                val cloudStart = SystemClock.elapsedRealtime()
                val result = withContext(Dispatchers.IO) {
                    aiWorkerClient.requestAgentPlan(
                        goal = goal,
                        snapshot = snapshot,
                        modelPreference = modelPreference,
                        recentActions = memory.recentActionSummaries(),
                        deviceContext = deviceContext,
                        agentMemory = memory.toJson(),
                    )
                }
                val cloudMs = SystemClock.elapsedRealtime() - cloudStart
                memory.recordTrace("第${memory.loopIndex + 1}轮：采集${captureMs}ms · 快照${snapshotMs}ms · 设备${deviceMs}ms · 云端${cloudMs}ms · 截图=${if (snapshot.hasVisualImage) "有" else "无"}")
                result
            } catch (error: IOException) {
                val totalMs = SystemClock.elapsedRealtime() - loopStart
                val message = "云端规划超时或失败：${error.message ?: "未知错误"}"
                memory.recordTrace("第${memory.loopIndex + 1}轮失败：采集${captureMs}ms · 快照${snapshotMs}ms · 设备${deviceMs}ms · 总${totalMs}ms")
                AgentRuntimeController.failTask(memory.withDebug(message))
                return AgentTaskRunResult(false, false, memory.withDebug(message), logs)
            }
            memory.loopIndex += 1

            val state = plan.state
            state?.let { memory.rememberState(it) }
            if (state != null && state.isComplete && state.confidence >= COMPLETE_CONFIDENCE_THRESHOLD) {
                val reason = state.reason.ifBlank { plan.step.reason ?: "云端状态判断任务已完成。" }
                val finishStep = CloudAgentStep(type = "finish", reason = reason, riskLevel = "low", requiresConfirmation = false)
                val done = AgentExecutionResult(true, reason, false)
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, finishStep, done)
                val message = memory.withDebug(reason)
                AgentRuntimeController.finishTask(message, completed = true)
                return AgentTaskRunResult(true, false, message, logs)
            }

            if (state != null && state.isWrong && state.confidence >= WRONG_CONFIDENCE_THRESHOLD && memory.backAttempts < MAX_BACK_ATTEMPTS) {
                val backStep = CloudAgentStep(
                    type = "back",
                    reason = state.reason.ifBlank { "云端状态判断当前页面偏离目标，返回上一层重新观察。" },
                    riskLevel = "low",
                    requiresConfirmation = false,
                )
                val result = executeTimed(backStep, snapshot.currentApp, logs, memory)
                memory.remember(backStep, result)
                delayForStep(backStep)
                continue
            }

            val steps = plan.executableSteps
                .filterNot { it.type == "finish" }
                .take(CloudAgentPlan.MAX_BATCH_STEPS)
                .ifEmpty { listOf(plan.step) }

            var executedAny = false
            for ((index, rawStep) in steps.withIndex()) {
                if (isStopped(stopGeneration)) return stoppedByUserResult(memory, logs)
                val chosenStep = chooseAction(snapshot, rawStep)
                if (chosenStep == null) {
                    memory.recordPlannerRejection(rawStep, "云端动作不可执行或缺少必要参数。")
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, rawStep, null)
                    continue
                }
                if (chosenStep.type == "need_user_help") {
                    val message = memory.withDebug(chosenStep.reason ?: "需要用户协助。")
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                val wasConfirmedByUser = if (AgentSafetyPolicy.requiresConfirmation(goal, chosenStep)) {
                    val confirmed = AgentRuntimeController.requestRiskConfirmation(goal, chosenStep)
                    if (!confirmed) return stoppedByUserResult(memory, logs)
                    true
                } else {
                    false
                }
                if (!wasConfirmedByUser && !AgentSafetyPolicy.canAutoExecuteInCurrentStage(goal, chosenStep)) {
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, chosenStep, null)
                    val message = memory.withDebug(chosenStep.reason ?: "当前动作暂不能自动执行：${chosenStep.typeLabel}")
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                val result = executeTimed(chosenStep, snapshot.currentApp, logs, memory)
                val isLastStep = index >= steps.lastIndex
                memory.remember(
                    step = chosenStep,
                    result = result,
                    forceVisualAfterSuccess = shouldForceVisualAfterBatchStep(chosenStep, result, isLastStep),
                )
                executedAny = true
                if (!result.ok) {
                    delay(REPLAN_DELAY_MS)
                    break
                }
                if (!result.shouldContinue) {
                    val message = memory.withDebug(result.message)
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                delayForStep(chosenStep)
                if (shouldStopBatchAfterStep(chosenStep, result, isLastStep)) break
            }

            if (!executedAny) delay(REPLAN_DELAY_MS)
        }
    }

    private fun isStopped(startGeneration: Long): Boolean {
        return AgentRuntimeController.currentManualStopGeneration() != startGeneration
    }

    private fun stoppedByUserResult(memory: AgentRunMemory, logs: List<AgentTaskStepLog>): AgentTaskRunResult {
        val message = memory.withDebug("用户已手动停止本次智能体任务。")
        return AgentTaskRunResult(false, false, message, logs)
    }

    private suspend fun tryRunInternalDeviceControl(goal: String, memory: AgentRunMemory): AgentTaskRunResult? {
        val context = applicationContext ?: return null
        val runtime = DeviceControlRuntime(context, installedAppIndex ?: InstalledAppIndex(context))
        val initialResult = withContext(Dispatchers.Main) { runtime.tryExecute(goal) } ?: return null
        memory.recordTrace("内部控制：${initialResult.title}")

        val pendingAction = initialResult.pendingAction
        val finalResult = if (pendingAction != null) {
            val confirmStep = CloudAgentStep(
                type = "wait",
                targetText = pendingAction.target,
                reason = pendingAction.reason,
                riskLevel = pendingAction.riskLevel.name.lowercase(),
                requiresConfirmation = true,
            )
            val confirmed = AgentRuntimeController.requestRiskConfirmation(goal, confirmStep)
            if (!confirmed) return stoppedByUserResult(memory, emptyList())
            withContext(Dispatchers.IO) { runtime.executePendingAction(pendingAction) }
        } else {
            initialResult
        }

        memory.recordTrace("内部控制结果：${if (finalResult.ok) "成功" else "未完成"} · ${finalResult.title}")
        val message = memory.withDebug(finalResult.message)
        AgentRuntimeController.finishTask(message, completed = finalResult.ok)
        return AgentTaskRunResult(
            completed = finalResult.ok,
            stoppedForConfirmation = false,
            message = message,
            logs = emptyList(),
        )
    }

    private fun shouldForceVisualAfterBatchStep(step: CloudAgentStep, result: AgentExecutionResult, isLastStep: Boolean): Boolean {
        if (!result.ok || isLastStep) return true
        return step.type in BATCH_BREAK_AFTER_ACTION_TYPES
    }

    private fun shouldStopBatchAfterStep(step: CloudAgentStep, result: AgentExecutionResult, isLastStep: Boolean): Boolean {
        if (isLastStep || !result.ok || !result.shouldContinue) return true
        return step.type in BATCH_BREAK_AFTER_ACTION_TYPES
    }

    private fun buildDeviceContext(snapshot: AgentScreenSnapshot, goal: String): AgentDeviceContextSnapshot? {
        val context = applicationContext ?: return null
        val index = installedAppIndex ?: InstalledAppIndex(context)
        return runCatching { AgentDeviceContextProvider.build(context = context, screen = snapshot, goal = goal, installedAppIndex = index) }.getOrNull()
    }

    private suspend fun captureOnce(forceVisual: Boolean = false): ScreenObservation {
        return withContext(Dispatchers.Default) { AiAgentAccessibilityService.captureFreshSnapshot(forceVisual = forceVisual) }
    }

    private suspend fun executeTimed(step: CloudAgentStep, currentApp: String, logs: MutableList<AgentTaskStepLog>, memory: AgentRunMemory): AgentExecutionResult {
        val start = SystemClock.elapsedRealtime()
        val result = executeAndRecord(step, currentApp, logs)
        memory.recordTrace("执行 ${step.type} ${SystemClock.elapsedRealtime() - start}ms")
        return result
    }

    private suspend fun executeAndRecord(step: CloudAgentStep, currentApp: String, logs: MutableList<AgentTaskStepLog>): AgentExecutionResult {
        AgentRuntimeController.noteAction(step)
        val result = withContext(Dispatchers.Main) { AiAgentAccessibilityService.executeStep(step) }
        AgentRuntimeController.noteResult(step, result)
        logs += AgentTaskStepLog(logs.size + 1, currentApp, step, result)
        return result
    }

    private suspend fun delayForStep(step: CloudAgentStep) {
        val delayMs = step.durationMs?.coerceIn(MIN_CUSTOM_STEP_DELAY_MS, MAX_CUSTOM_STEP_DELAY_MS) ?: when (step.type) {
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

    private fun chooseAction(snapshot: AgentScreenSnapshot, cloudStep: CloudAgentStep): CloudAgentStep? {
        return sanitizeCloudStep(cloudStep, snapshot)
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

    private data class AgentRunMemory(
        var backAttempts: Int = 0,
        var executedStepCount: Int = 0,
        var loopIndex: Int = 0,
        private val recentActionLines: MutableList<String> = mutableListOf(),
        private val failedActionLines: MutableList<String> = mutableListOf(),
        private val blockedActionLines: MutableList<String> = mutableListOf(),
        var lastDebugLine: String = "",
    ) {
        fun observe(snapshot: AgentScreenSnapshot) {
            lastDebugLine = "调试：阶段=云端观察 · app=${snapshot.currentApp.ifBlank { "未知" }} · 节点=${snapshot.nodeCount}/${snapshot.capturedNodeCount} · 全量=${snapshot.allNodes.size} · 点击=${snapshot.clickableNodes.size} · 输入=${snapshot.inputNodes.size} · 滚动=${snapshot.scrollableNodes.size} · 截图=${if (snapshot.hasVisualImage) "有" else "无"} · 步数=$executedStepCount"
        }

        fun rememberState(state: CloudAgentState) {
            if (state.reason.isBlank() && state.nextHint.isBlank()) return
            recentActionLines += "状态：complete=${state.isComplete}, progress=${state.expectedProgress}, wrong=${state.isWrong}, confidence=${"%.2f".format(state.confidence)} · ${state.reason.ifBlank { state.nextHint }}"
            trimHistory()
        }

        fun recordTrace(text: String) {
            val line = text.take(100)
            recentActionLines += "诊断：$line"
            AgentRuntimeController.noteDiagnostic(line)
            trimHistory()
        }

        fun recordPlannerRejection(step: CloudAgentStep, reason: String) {
            val line = "拒绝执行：${describeStep(step)} · $reason"
            blockedActionLines += line
            recentActionLines += line
            trimHistory()
        }

        fun withDebug(message: String): String = if (lastDebugLine.isBlank()) message else "$message\n$lastDebugLine"

        fun remember(step: CloudAgentStep, result: AgentExecutionResult, forceVisualAfterSuccess: Boolean = true) {
            executedStepCount += 1
            if (step.type == "back" && result.ok) backAttempts += 1
            val line = "动作：${describeStep(step)} → ${if (result.ok) "成功" else "失败"}：${result.message.take(100)}"
            recentActionLines += line
            if (!result.ok) failedActionLines += line
            trimHistory()
        }

        fun recentActionSummaries(): List<String> = recentActionLines.takeLast(8)

        fun toJson(): JSONObject = JSONObject().apply {
            put("schema", "agent_loop_memory_v9_unlimited_manual_stop")
            put("recentActions", JSONArray().apply { recentActionLines.takeLast(8).forEach { put(it) } })
            put("failedActions", JSONArray().apply { failedActionLines.takeLast(6).forEach { put(it) } })
            put("blockedActions", JSONArray().apply { blockedActionLines.takeLast(6).forEach { put(it) } })
            put("loopSignals", JSONObject().apply {
                put("executedStepCount", executedStepCount)
                put("loopIndex", loopIndex)
            })
            put("policyHints", JSONArray().apply {
                put("这是无固定步数上限的视觉主导 Computer Use 循环。")
                put("只有用户手动停止、任务完成、无障碍/云端失败或安全策略拦截才会结束。")
                put("每轮都根据最新截图重新判断，不要因为步数变多就提前结束。")
            })
        }

        private fun trimHistory() {
            while (recentActionLines.size > 10) recentActionLines.removeAt(0)
            while (failedActionLines.size > 8) failedActionLines.removeAt(0)
            while (blockedActionLines.size > 8) blockedActionLines.removeAt(0)
        }

        private fun describeStep(step: CloudAgentStep): String = buildString {
            append(step.type)
            step.appName?.let { append(" · app=").append(it) }
            step.packageName?.let { append(" · pkg=").append(it) }
            step.targetText?.let { append(" · target=").append(it) }
            step.direction?.let { append(" · ").append(it) }
            step.x?.let { append(" · x=").append("%.3f".format(it)) }
            step.y?.let { append(" y=").append("%.3f".format(it)) }
        }
    }

    companion object {
        private const val DEFAULT_STEP_DELAY_MS = 280L
        private const val REPLAN_DELAY_MS = 140L
        private const val OPEN_APP_DELAY_MS = 640L
        private const val TAP_DELAY_MS = 160L
        private const val INPUT_DELAY_MS = 180L
        private const val SCROLL_DELAY_MS = 260L
        private const val DEFAULT_WAIT_DELAY_MS = 360L
        private const val GLOBAL_ACTION_DELAY_MS = 240L
        private const val MIN_CUSTOM_STEP_DELAY_MS = 60L
        private const val MAX_CUSTOM_STEP_DELAY_MS = 1_000L
        private const val MAX_BACK_ATTEMPTS = 2
        private const val COMPLETE_CONFIDENCE_THRESHOLD = 0.72f
        private const val WRONG_CONFIDENCE_THRESHOLD = 0.78f
        private val BATCH_BREAK_AFTER_ACTION_TYPES = setOf("open_app", "input_text", "back", "home", "recents", "notifications", "quick_settings")
    }
}
