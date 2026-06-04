package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatModel
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
) {
    suspend fun run(
        goal: String,
        modelPreference: ChatModel,
        maxSteps: Int = DEFAULT_MAX_STEPS,
    ): AgentTaskRunResult {
        val logs = mutableListOf<AgentTaskStepLog>()
        val targetApp = AgentTargetAppResolver.resolve(goal)
        var localOpenAppAttempts = 0
        var recoveryAttempts = 0

        repeat(maxSteps.coerceAtMost(DEFAULT_MAX_STEPS)) { index ->
            val observation = withContext(Dispatchers.Default) {
                AiAgentAccessibilityService.captureFreshSnapshot()
            }
            if (!observation.enabled || !observation.serviceConnected) {
                return AgentTaskRunResult(false, false, "无障碍服务未开启", logs)
            }

            if (targetApp != null && observation.packageName != targetApp.packageName && localOpenAppAttempts < MAX_OPEN_APP_ATTEMPTS) {
                localOpenAppAttempts += 1
                val step = CloudAgentStep(
                    type = "open_app",
                    appName = targetApp.label,
                    packageName = targetApp.packageName,
                    reason = "目标属于${targetApp.label}，当前前台不是目标应用，先打开目标应用。",
                    riskLevel = "low",
                    requiresConfirmation = false,
                )
                val execution = withContext(Dispatchers.Main) { AiAgentAccessibilityService.executeStep(step) }
                logs += AgentTaskStepLog(index + 1, observation.packageName, step, execution)
                if (!execution.ok || !execution.shouldContinue) return AgentTaskRunResult(false, false, execution.message, logs)
                waitForPackage(targetApp.packageName)
                return@repeat
            }

            val snapshot = observation.toAgentScreenSnapshot()
            val step = withContext(Dispatchers.IO) {
                aiWorkerClient.requestAgentStep(goal = goal, snapshot = snapshot, modelPreference = modelPreference)
            }

            if (step.type == "finish") {
                logs += AgentTaskStepLog(index + 1, snapshot.currentApp, step, AgentExecutionResult(true, "任务完成", false))
                return AgentTaskRunResult(true, false, step.reason ?: "任务完成", logs)
            }

            if (step.type == "need_user_help") {
                val recovery = buildRecoveryStep(goal, snapshot, targetApp, recoveryAttempts)
                if (recovery != null) {
                    recoveryAttempts += 1
                    val execution = withContext(Dispatchers.Main) { AiAgentAccessibilityService.executeStep(recovery) }
                    logs += AgentTaskStepLog(index + 1, snapshot.currentApp, recovery, execution)
                    if (!execution.ok || !execution.shouldContinue) return AgentTaskRunResult(false, false, execution.message, logs)
                    delay(recovery.durationMs?.coerceIn(MIN_STEP_DELAY_MS, MAX_STEP_DELAY_MS) ?: DEFAULT_STEP_DELAY_MS)
                    return@repeat
                }
                logs += AgentTaskStepLog(index + 1, snapshot.currentApp, step, null)
                return AgentTaskRunResult(false, false, step.reason ?: "需要用户协助", logs)
            }

            if (AgentSafetyPolicy.requiresConfirmation(goal, step)) {
                logs += AgentTaskStepLog(index + 1, snapshot.currentApp, step, null)
                return AgentTaskRunResult(false, true, "动作需要确认：${step.typeLabel}", logs)
            }
            if (!AgentSafetyPolicy.canAutoExecuteInCurrentStage(goal, step)) {
                logs += AgentTaskStepLog(index + 1, snapshot.currentApp, step, null)
                return AgentTaskRunResult(false, false, step.reason ?: "当前动作暂不能自动执行：${step.typeLabel}", logs)
            }
            val execution = withContext(Dispatchers.Main) { AiAgentAccessibilityService.executeStep(step) }
            logs += AgentTaskStepLog(index + 1, snapshot.currentApp, step, execution)
            if (!execution.ok || !execution.shouldContinue) return AgentTaskRunResult(false, false, execution.message, logs)
            delay(step.durationMs?.coerceIn(MIN_STEP_DELAY_MS, MAX_STEP_DELAY_MS) ?: DEFAULT_STEP_DELAY_MS)
        }
        return AgentTaskRunResult(false, false, "已达到最大执行步数，请检查当前页面后继续。", logs)
    }

    private suspend fun waitForPackage(packageName: String) {
        repeat(2) {
            delay(650L)
            val now = withContext(Dispatchers.Default) { AiAgentAccessibilityService.captureFreshSnapshot() }
            if (now.packageName == packageName) return
        }
    }

    private fun buildRecoveryStep(
        goal: String,
        snapshot: AgentScreenSnapshot,
        targetApp: AgentTargetApp?,
        attempt: Int,
    ): CloudAgentStep? {
        if (targetApp != null && snapshot.currentApp != targetApp.packageName && attempt < MAX_OPEN_APP_ATTEMPTS) {
            return CloudAgentStep(
                type = "open_app",
                appName = targetApp.label,
                packageName = targetApp.packageName,
                reason = "自我纠错：目标应用未处于前台，重新打开目标应用。",
                riskLevel = "low",
                requiresConfirmation = false,
            )
        }
        findClickableByKeywords(snapshot, listOf("搜索", "查找", "search", "发现", "通讯录"))?.let { node ->
            return CloudAgentStep(
                type = "tap_node",
                targetNodeId = node.id,
                targetText = node.text,
                reason = "自我纠错：当前目标未直接出现，先进入可能的搜索或导航入口。",
                riskLevel = "low",
                requiresConfirmation = false,
            )
        }
        val targetPhrase = AgentTargetAppResolver.extractSearchKeyword(goal, targetApp)
        if (snapshot.inputNodes.isNotEmpty() && targetPhrase.isNotBlank()) {
            val input = snapshot.inputNodes.first()
            return CloudAgentStep(
                type = "input_text",
                targetNodeId = input.id,
                text = targetPhrase,
                reason = "自我纠错：发现输入框，输入任务目标关键词继续查找。",
                riskLevel = "low",
                requiresConfirmation = false,
            )
        }
        if (snapshot.scrollableNodes.isNotEmpty() && attempt < MAX_RECOVERY_ATTEMPTS) {
            val scroll = snapshot.scrollableNodes.first()
            return CloudAgentStep(
                type = "scroll",
                targetNodeId = scroll.id,
                direction = if (attempt % 2 == 0) "down" else "up",
                reason = "自我纠错：当前屏幕没有目标入口，滚动页面扩大搜索范围。",
                riskLevel = "low",
                requiresConfirmation = false,
            )
        }
        if (attempt < MAX_RECOVERY_ATTEMPTS) {
            return CloudAgentStep(
                type = "swipe",
                direction = if (attempt % 2 == 0) "up" else "down",
                reason = "自我纠错：没有可滚动节点时，尝试屏幕滑动寻找更多内容。",
                riskLevel = "low",
                requiresConfirmation = false,
            )
        }
        return null
    }

    private fun findClickableByKeywords(snapshot: AgentScreenSnapshot, keywords: List<String>): AgentScreenNode? {
        return snapshot.clickableNodes.firstOrNull { node ->
            keywords.any { keyword -> node.text.contains(keyword, ignoreCase = true) }
        }
    }

    companion object {
        private const val DEFAULT_MAX_STEPS = 6
        private const val DEFAULT_STEP_DELAY_MS = 1_100L
        private const val MIN_STEP_DELAY_MS = 650L
        private const val MAX_STEP_DELAY_MS = 2_500L
        private const val MAX_OPEN_APP_ATTEMPTS = 1
        private const val MAX_RECOVERY_ATTEMPTS = 2
    }
}
