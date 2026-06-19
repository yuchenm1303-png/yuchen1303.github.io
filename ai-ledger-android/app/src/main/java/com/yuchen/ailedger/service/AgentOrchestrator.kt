package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.model.ChatModel

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
        val route = routeFor(executionMode)
        val resolvedMaxSteps = when {
            maxSteps != Int.MAX_VALUE -> maxSteps
            route == AgentOrchestratorRoute.VisualLoop -> DEFAULT_VISUAL_MAX_STEPS
            else -> Int.MAX_VALUE
        }
        return when (route) {
            AgentOrchestratorRoute.LegacyRunner -> AgentTaskRunner(aiWorkerClient, applicationContext).run(
                goal = goal,
                modelPreference = modelPreference,
                maxSteps = resolvedMaxSteps,
                executionMode = executionMode,
            )
            AgentOrchestratorRoute.VisualLoop -> VisualLoopRunner(aiWorkerClient, applicationContext).run(
                goal = goal,
                maxSteps = resolvedMaxSteps,
                executionMode = executionMode,
            )
        }
    }

    companion object {
        private const val DEFAULT_VISUAL_MAX_STEPS = 36

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
