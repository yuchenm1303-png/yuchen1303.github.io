package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.model.ChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Selects only the runtime family. All agent semantics stay in the cloud models:
 * DeepSeek routes and GUI Plus operates a verified visual work surface.
 */
class AgentOrchestrator(
    private val aiWorkerClient: AiWorkerClient,
    appContext: Context,
) {
    private val applicationContext = appContext.applicationContext

    suspend fun run(
        goal: String,
        modelPreference: ChatModel,
        maxSteps: Int = Int.MAX_VALUE,
        executionMode: AgentExecutionMode,
    ): AgentTaskRunResult {
        // 普通聊天的设备工具判断已合并到同一个 /chat 请求中，由后端与正文生成并行完成。
        // 这里仅终止旧的串行 LegacyRunner 网络探测；动作执行、风险确认与结果验证仍由 Android 负责。
        if (executionMode == AgentExecutionMode.NormalChatDeviceTool) {
            return AgentTaskRunResult(
                completed = false,
                stoppedForConfirmation = false,
                message = "普通聊天内部工具由单请求并行探测处理。",
                logs = emptyList(),
                handled = false,
            )
        }

        return when (routeFor(executionMode)) {
            AgentOrchestratorRoute.LegacyRunner -> AgentTaskRunner(aiWorkerClient, applicationContext).run(
                goal = goal,
                modelPreference = modelPreference,
                maxSteps = maxSteps,
                executionMode = executionMode,
            )
            AgentOrchestratorRoute.VisualLoop -> withContext(Dispatchers.IO) {
                VisualLoopRunner(aiWorkerClient, applicationContext).run(
                    goal = goal,
                    maxSteps = maxSteps,
                    executionMode = executionMode,
                )
            }
        }
    }

    companion object {
        fun routeFor(executionMode: AgentExecutionMode): AgentOrchestratorRoute {
            return when (executionMode) {
                AgentExecutionMode.NormalChatDeviceTool -> AgentOrchestratorRoute.LegacyRunner
                AgentExecutionMode.VisualForce,
                AgentExecutionMode.ExplicitAgent -> AgentOrchestratorRoute.VisualLoop
            }
        }
    }
}

enum class AgentOrchestratorRoute {
    LegacyRunner,
    VisualLoop,
}
