package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.model.ChatModel

class AgentOrchestrator(
    private val aiWorkerClient: AiWorkerClient,
    appContext: Context,
) {
    private val legacyRunner = LegacyAgentRunner(aiWorkerClient, appContext)
    private val visualRunner = VisualLoopRunner(aiWorkerClient, appContext)

    suspend fun run(
        goal: String,
        modelPreference: ChatModel,
        maxSteps: Int = 18,
        executionMode: AgentExecutionMode = AgentExecutionMode.ExplicitAgent,
    ): AgentTaskRunResult {
        return when (executionMode) {
            AgentExecutionMode.NormalChatDeviceTool -> legacyRunner.run(
                goal = goal,
                modelPreference = modelPreference,
                maxSteps = maxSteps,
                executionMode = executionMode,
            )
            AgentExecutionMode.VisualForce,
            AgentExecutionMode.ExplicitAgent -> visualRunner.run(
                goal = goal,
                maxSteps = maxSteps,
                executionMode = executionMode,
            )
        }
    }
}
