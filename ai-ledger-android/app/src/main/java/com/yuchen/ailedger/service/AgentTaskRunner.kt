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
        val targetApp = detectTargetApp(goal)
        var localOpenAppTried = false

        repeat(maxSteps) { index ->
            val observation = withContext(Dispatchers.Default) {
                AiAgentAccessibilityService.captureFreshSnapshot()
            }
            if (!observation.enabled || !observation.serviceConnected) {
                return AgentTaskRunResult(
                    completed = false,
                    stoppedForConfirmation = false,
                    message = "无障碍服务未开启",
                    logs = logs,
                )
            }

            if (targetApp != null && !localOpenAppTried && observation.packageName != targetApp.packageName) {
                localOpenAppTried = true
                val step = CloudAgentStep(
                    type = "open_app",
                    appName = targetApp.label,
                    packageName = targetApp.packageName,
                    reason = "目标属于${targetApp.label}，当前不在目标应用，先打开目标应用。",
                    riskLevel = "low",
                    requiresConfirmation = false,
                )
                val execution = withContext(Dispatchers.Main) {
                    AiAgentAccessibilityService.executeStep(step)
                }
                logs += AgentTaskStepLog(index + 1, observation.packageName, step, execution)
                if (!execution.ok || !execution.shouldContinue) {
                    return AgentTaskRunResult(false, false, execution.message, logs)
                }
                delay(DEFAULT_APP_OPEN_DELAY_MS)
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
            val execution = withContext(Dispatchers.Main) {
                AiAgentAccessibilityService.executeStep(step)
            }
            logs += AgentTaskStepLog(index + 1, snapshot.currentApp, step, execution)
            if (!execution.ok || !execution.shouldContinue) {
                return AgentTaskRunResult(false, false, execution.message, logs)
            }
            delay(step.durationMs?.coerceIn(350L, 2_500L) ?: DEFAULT_STEP_DELAY_MS)
        }
        return AgentTaskRunResult(
            completed = false,
            stoppedForConfirmation = false,
            message = "已达到最大执行步数，请检查当前页面后继续。",
            logs = logs,
        )
    }

    private fun detectTargetApp(goal: String): TargetApp? {
        val clean = goal.lowercase().replace(" ", "")
        return TARGET_APPS.firstOrNull { item ->
            item.aliases.any { alias -> clean.contains(alias.lowercase()) }
        }
    }

    private data class TargetApp(
        val label: String,
        val packageName: String,
        val aliases: List<String>,
    )

    companion object {
        private const val DEFAULT_MAX_STEPS = 8
        private const val DEFAULT_STEP_DELAY_MS = 900L
        private const val DEFAULT_APP_OPEN_DELAY_MS = 1_400L

        private val TARGET_APPS = listOf(
            TargetApp("微信", "com.tencent.mm", listOf("微信", "wechat", "wx")),
            TargetApp("QQ", "com.tencent.mobileqq", listOf("qq", "腾讯qq")),
            TargetApp("哔哩哔哩", "tv.danmaku.bili", listOf("哔哩", "哔哩哔哩", "b站", "bilibili")),
            TargetApp("小红书", "com.xingin.xhs", listOf("小红书")),
            TargetApp("抖音", "com.ss.android.ugc.aweme", listOf("抖音", "douyin")),
        )
    }
}
