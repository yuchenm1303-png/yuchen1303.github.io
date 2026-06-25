package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.model.ChatModel
import kotlinx.coroutines.delay

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
        return when (routeFor(executionMode)) {
            AgentOrchestratorRoute.LegacyRunner -> AgentTaskRunner(aiWorkerClient, applicationContext).run(
                goal = goal,
                modelPreference = modelPreference,
                maxSteps = maxSteps,
                executionMode = executionMode,
            )
            AgentOrchestratorRoute.VisualLoop -> runVisualLoopWithSafeEntryRetry(
                goal = goal,
                maxSteps = maxSteps,
                executionMode = executionMode,
            )
        }
    }

    private suspend fun runVisualLoopWithSafeEntryRetry(
        goal: String,
        maxSteps: Int,
        executionMode: AgentExecutionMode,
    ): AgentTaskRunResult {
        val first = VisualLoopRunner(aiWorkerClient, applicationContext).run(
            goal = goal,
            maxSteps = maxSteps,
            executionMode = executionMode,
        )
        if (!AgentEntryRetryPolicy.shouldRetry(first)) return first

        AgentRuntimeController.noteDiagnostic("DeepSeek 入口路由暂时失败，正在保留当前目标页面并安全重试一次")
        delay(VISUAL_ENTRY_RETRY_DELAY_MS)
        val second = VisualLoopRunner(aiWorkerClient, applicationContext).run(
            goal = goal,
            maxSteps = maxSteps,
            executionMode = executionMode,
        )
        val mergedLogs = (first.logs + second.logs).mapIndexed { index, log ->
            log.copy(index = index + 1)
        }
        return second.copy(logs = mergedLogs)
    }

    companion object {
        fun routeFor(executionMode: AgentExecutionMode): AgentOrchestratorRoute {
            return when (executionMode) {
                AgentExecutionMode.NormalChatDeviceTool -> AgentOrchestratorRoute.LegacyRunner
                AgentExecutionMode.VisualForce,
                AgentExecutionMode.ExplicitAgent -> AgentOrchestratorRoute.VisualLoop
            }
        }

        private const val VISUAL_ENTRY_RETRY_DELAY_MS = 420L
    }
}

enum class AgentOrchestratorRoute {
    LegacyRunner,
    VisualLoop,
}
