package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.model.ChatModel
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

    suspend fun run(
        goal: String,
        modelPreference: ChatModel,
        maxSteps: Int = DEFAULT_MAX_STEPS,
    ): AgentTaskRunResult {
        val logs = mutableListOf<AgentTaskStepLog>()
        if (!AgentRuntimeController.isEnabled()) {
            val message = "智能体已关闭，请先打开 Agent 开关。"
            AgentRuntimeController.finishTask(message, completed = false)
            return AgentTaskRunResult(false, false, message, logs)
        }

        AgentRuntimeController.startTask(goal)
        val memory = AgentRunMemory()

        repeat(maxSteps.coerceAtMost(DEFAULT_MAX_STEPS)) {
            val observation = captureOnce(forceVisual = true)
            if (!observation.enabled || !observation.serviceConnected) {
                val message = "无障碍服务未开启"
                AgentRuntimeController.failTask(message)
                return AgentTaskRunResult(false, false, message, logs)
            }

            val snapshot = observation.toAgentScreenSnapshot()
            memory.observe(snapshot)
            val deviceContext = buildDeviceContext(snapshot)

            val plan = withContext(Dispatchers.IO) {
                aiWorkerClient.requestAgentPlan(
                    goal = goal,
                    snapshot = snapshot,
                    modelPreference = modelPreference,
                    recentActions = memory.recentActionSummaries(),
                    deviceContext = deviceContext,
                    agentMemory = memory.toJson(),
                )
            }
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

            if (state != null && state.isWrong && state.confidence >= WRONG_CONFIDENCE_THRESHOLD) {
                if (memory.backAttempts >= MAX_BACK_ATTEMPTS) {
                    val message = memory.withDebug(state.reason.ifBlank { "云端判断当前页面偏离目标，但已达到最大返回次数。" })
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                val backStep = CloudAgentStep(
                    type = "back",
                    reason = state.reason.ifBlank { "云端状态判断当前页面偏离目标，返回上一层重新观察。" },
                    riskLevel = "low",
                    requiresConfirmation = false,
                )
                val result = executeAndRecord(backStep, snapshot.currentApp, logs)
                memory.remember(backStep, result)
                if (!result.ok || !result.shouldContinue) {
                    val message = memory.withDebug(result.message)
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                delayForStep(backStep)
                return@repeat
            }

            if (plan.step.type == "finish") {
                memory.rejectedFinishAttempts += 1
                if (memory.rejectedFinishAttempts >= MAX_REJECTED_FINISH_ATTEMPTS) {
                    val reason = state?.reason?.takeIf { it.isNotBlank() }
                        ?: "云端返回完成动作，但状态置信度不足，已暂停避免过早结束。"
                    val message = memory.withDebug(reason)
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                memory.recordPlannerRejection(plan.step, "finish 状态置信度不足，要求云端继续观察或规划下一步。")
                delay(DEFAULT_STEP_DELAY_MS)
                return@repeat
            }

            val chosenStep = chooseAction(snapshot, plan.step, memory)
            if (chosenStep == null) {
                memory.recordPlannerRejection(plan.step, "云端动作不可执行、缺少参数或与近期失败动作重复。")
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, plan.step, null)
                if (memory.repeatedCloudRejects <= MAX_REPLAN_REJECTS) {
                    delay(DEFAULT_STEP_DELAY_MS)
                    return@repeat
                }
                val message = memory.withDebug(plan.step.reason ?: state?.nextHint ?: "云端连续返回不可执行或重复动作")
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, false, message, logs)
            }

            if (AgentSafetyPolicy.requiresConfirmation(goal, chosenStep)) {
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, chosenStep, null)
                val message = memory.withDebug("动作需要确认：${chosenStep.typeLabel}")
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, true, message, logs)
            }
            if (!AgentSafetyPolicy.canAutoExecuteInCurrentStage(goal, chosenStep)) {
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, chosenStep, null)
                val message = memory.withDebug(chosenStep.reason ?: "当前动作暂不能自动执行：${chosenStep.typeLabel}")
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, false, message, logs)
            }

            val result = executeAndRecord(chosenStep, snapshot.currentApp, logs)
            memory.remember(chosenStep, result)
            if (!result.ok) {
                if (memory.shouldReplanAfterFailure(chosenStep)) {
                    delay(DEFAULT_STEP_DELAY_MS)
                    return@repeat
                }
                val message = memory.withDebug(result.message)
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, false, message, logs)
            }
            if (!result.shouldContinue) {
                val message = memory.withDebug(result.message)
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, false, message, logs)
            }

            delayForStep(chosenStep)
        }

        val message = memory.withDebug("已达到最大执行步数，请检查当前页面后继续。")
        AgentRuntimeController.finishTask(message, completed = false)
        return AgentTaskRunResult(false, false, message, logs)
    }

    private fun buildDeviceContext(snapshot: AgentScreenSnapshot): AgentDeviceContextSnapshot? {
        val context = applicationContext ?: return null
        val index = installedAppIndex ?: InstalledAppIndex(context)
        return runCatching { AgentDeviceContextProvider.build(context, snapshot, index) }.getOrNull()
    }

    private suspend fun captureOnce(forceVisual: Boolean = false): ScreenObservation {
        return withContext(Dispatchers.Default) { AiAgentAccessibilityService.captureFreshSnapshot(forceVisual = forceVisual) }
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

    private fun chooseAction(snapshot: AgentScreenSnapshot, cloudStep: CloudAgentStep, memory: AgentRunMemory): CloudAgentStep? {
        val candidate = sanitizeCloudStep(cloudStep, snapshot) ?: return null
        if (memory.isBlocked(candidate)) return null
        return candidate
    }

    private fun sanitizeCloudStep(step: CloudAgentStep, snapshot: AgentScreenSnapshot): CloudAgentStep? {
        if (step.type == "need_user_help") return null
        if (step.type !in CloudAgentStep.supportedTypes) return null
        if (step.type == "tap_xy") {
            val x = step.x ?: return null
            val y = step.y ?: return null
            if (x !in 0f..1f || y !in 0f..1f) return null
        }
        if (step.type == "tap_node" && step.targetNodeId.isNullOrBlank() && step.targetText.isNullOrBlank()) return null
        if (step.type == "input_text" && step.text.isNullOrBlank()) return null
        if (step.type == "open_app" && step.packageName != null && step.packageName == snapshot.currentApp) return null
        if (step.type == "wait" && snapshot.hasVisualImage && !isLoadingWaitReason(step.reason.orEmpty())) return null
        return step
    }

    private fun isLoadingWaitReason(reason: String): Boolean {
        val clean = normalize(reason)
        return loadingWaitWords.any { clean.contains(normalize(it)) }
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[\\s\u3000，。,.、:：/\\-]+"), "")

    private data class AgentRunMemory(
        var phase: AgentTaskPhase = AgentTaskPhase.Verifying,
        var backAttempts: Int = 0,
        var repeatedCloudRejects: Int = 0,
        var rejectedFinishAttempts: Int = 0,
        var recoverableFailures: Int = 0,
        private val recentStepKeys: MutableList<String> = mutableListOf(),
        private val recentActionLines: MutableList<String> = mutableListOf(),
        private val failedActionLines: MutableList<String> = mutableListOf(),
        private val blockedActionLines: MutableList<String> = mutableListOf(),
        var lastDebugLine: String = "",
    ) {
        fun observe(snapshot: AgentScreenSnapshot) {
            lastDebugLine = "调试：阶段=${phase.label} · app=${snapshot.currentApp.ifBlank { "未知" }} · 节点=${snapshot.nodeCount}/${snapshot.capturedNodeCount} · 全量=${snapshot.allNodes.size} · 点击=${snapshot.clickableNodes.size} · 输入=${snapshot.inputNodes.size} · 滚动=${snapshot.scrollableNodes.size} · 截图=${if (snapshot.hasVisualImage) "有" else "无"}"
        }

        fun rememberState(state: CloudAgentState) {
            if (state.reason.isBlank() && state.nextHint.isBlank()) return
            recentActionLines += "状态：complete=${state.isComplete}, progress=${state.expectedProgress}, wrong=${state.isWrong}, confidence=${"%.2f".format(state.confidence)} · ${state.reason.ifBlank { state.nextHint }}"
            trimHistory()
        }

        fun recordPlannerRejection(step: CloudAgentStep, reason: String) {
            repeatedCloudRejects += 1
            val line = "拒绝执行：${describeStep(step)} · $reason"
            blockedActionLines += line
            recentActionLines += line
            trimHistory()
        }

        fun withDebug(message: String): String = if (lastDebugLine.isBlank()) message else "$message\n$lastDebugLine"

        fun remember(step: CloudAgentStep, result: AgentExecutionResult) {
            val key = stepKey(step)
            recentStepKeys += key
            val line = "动作：${describeStep(step)} → ${if (result.ok) "成功" else "失败"}：${result.message.take(100)}"
            recentActionLines += line
            if (!result.ok) failedActionLines += line
            trimHistory()
            if (recentStepKeys.size > 8) recentStepKeys.removeAt(0)
            if (step.type == "back") {
                backAttempts += 1
                if (result.ok) {
                    recentStepKeys.clear()
                    repeatedCloudRejects = 0
                    rejectedFinishAttempts = 0
                    phase = AgentTaskPhase.Verifying
                }
            }
            if (result.ok && step.type != "finish") {
                repeatedCloudRejects = 0
                rejectedFinishAttempts = 0
                recoverableFailures = 0
            }
            if (!result.ok) repeatedCloudRejects += 1
        }

        fun shouldReplanAfterFailure(step: CloudAgentStep): Boolean {
            if (step.type !in RECOVERABLE_FAILURE_ACTIONS) return false
            recoverableFailures += 1
            return recoverableFailures <= MAX_RECOVERABLE_FAILURES
        }

        fun recentActionSummaries(): List<String> = recentActionLines.takeLast(8)
        fun isLikelyRepeated(step: CloudAgentStep): Boolean = recentStepKeys.count { it == stepKey(step) } >= 1
        fun isBlocked(step: CloudAgentStep): Boolean = isLikelyRepeated(step)

        fun toJson(): JSONObject = JSONObject().apply {
            put("schema", "agent_loop_memory_v1")
            put("recentActions", JSONArray().apply { recentActionLines.takeLast(8).forEach { put(it) } })
            put("failedActions", JSONArray().apply { failedActionLines.takeLast(6).forEach { put(it) } })
            put("blockedActions", JSONArray().apply { blockedActionLines.takeLast(6).forEach { put(it) } })
            put("loopSignals", JSONObject().apply {
                put("repeatedCloudRejects", repeatedCloudRejects)
                put("recoverableFailures", recoverableFailures)
                put("backAttempts", backAttempts)
                put("rejectedFinishAttempts", rejectedFinishAttempts)
            })
            put("policyHints", JSONArray().apply {
                put("不要重复 blockedActions 或 failedActions 中的同一路径。")
                put("如果 open_app 失败，必须改用 deviceContext.installedApps 中真实存在的 appName/packageName，或返回 need_user_help。")
                put("如果点击桌面文件夹后没有目标应用，下一轮不要再点同一个文件夹。")
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

        private fun stepKey(step: CloudAgentStep): String = listOf(
            step.type,
            step.targetNodeId,
            step.targetText,
            step.text,
            step.direction,
            step.x?.let { "%.4f".format(it) },
            step.y?.let { "%.4f".format(it) },
            step.appName,
            step.packageName,
        ).joinToString("|")
    }

    private enum class AgentTaskPhase(val label: String) {
        Verifying("云端观察"),
    }

    companion object {
        private const val DEFAULT_MAX_STEPS = 8
        private const val DEFAULT_STEP_DELAY_MS = 520L
        private const val OPEN_APP_DELAY_MS = 1_050L
        private const val TAP_DELAY_MS = 360L
        private const val INPUT_DELAY_MS = 320L
        private const val SCROLL_DELAY_MS = 560L
        private const val DEFAULT_WAIT_DELAY_MS = 720L
        private const val GLOBAL_ACTION_DELAY_MS = 420L
        private const val MIN_CUSTOM_STEP_DELAY_MS = 120L
        private const val MAX_CUSTOM_STEP_DELAY_MS = 2_000L
        private const val MAX_BACK_ATTEMPTS = 2
        private const val MAX_REJECTED_FINISH_ATTEMPTS = 2
        private const val MAX_REPLAN_REJECTS = 2
        private const val MAX_RECOVERABLE_FAILURES = 2
        private const val COMPLETE_CONFIDENCE_THRESHOLD = 0.72f
        private const val WRONG_CONFIDENCE_THRESHOLD = 0.78f
        private val RECOVERABLE_FAILURE_ACTIONS = setOf("open_app", "tap_node", "tap_xy", "scroll", "swipe", "wait")
        private val loadingWaitWords = listOf("加载", "正在", "等待", "过渡", "动画", "空白", "刷新", "刚变化", "loading", "blank", "transition", "wait")
    }
}
