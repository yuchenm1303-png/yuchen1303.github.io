package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
            AgentOrchestratorRoute.VisualLoop -> runVisualLoop(
                goal = goal,
                modelPreference = modelPreference,
                maxSteps = maxSteps,
                executionMode = executionMode,
            )
        }
    }

    private suspend fun runVisualLoop(
        goal: String,
        modelPreference: ChatModel,
        maxSteps: Int,
        executionMode: AgentExecutionMode,
    ): AgentTaskRunResult {
        val invocation = VisualTaskInvocationRuntime.begin(goal)
        return try {
            val result = withContext(Dispatchers.IO) {
                VisualLoopRunner(aiWorkerClient, applicationContext).run(
                    goal = goal,
                    maxSteps = maxSteps,
                    executionMode = executionMode,
                )
            }
            // Runner 内部大多数终态已经收口；这里作为统一兜底，补齐风险拒绝等直接返回分支。
            AgentRuntimeController.finishTask(result.resolvedOutcome())
            if (executionMode == AgentExecutionMode.ExplicitAgent && !result.isAccessibilityUnavailable()) {
                reportVisualResult(invocation, goal, modelPreference, result)
            } else {
                result
            }
        } catch (error: CancellationException) {
            throw error
        } finally {
            VisualTaskInvocationRuntime.clear(invocation)
        }
    }

    private suspend fun reportVisualResult(
        invocation: VisualTaskInvocation,
        goal: String,
        modelPreference: ChatModel,
        result: AgentTaskRunResult,
    ): AgentTaskRunResult {
        val call = invocation.clientToolCall
        val callId = call?.id ?: invocation.taskInvocationId
        val toolArguments = call?.arguments?.let { JSONObject(it.toString()) }
            ?: JSONObject().put("goal", goal.trim().take(1_200))
        val receipt = JSONObject().apply {
            put("protocol", call?.resultProtocol ?: AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL)
            put("toolCallId", callId)
            put("toolName", call?.name ?: "computer_run_task")
            put("toolArguments", toolArguments)
            put("finalModel", call?.finalModel ?: modelPreference.id)
            put("goal", goal.trim().take(300))
            put("status", result.visualReceiptStatus())
            put("completed", result.completed)
            put("handled", result.handled)
            put("stoppedForConfirmation", result.stoppedForConfirmation)
            put("resultSummary", result.message.take(1_800))
            put("actions", JSONArray().apply {
                result.logs.takeLast(6).forEach { log ->
                    val execution = log.execution
                    put(JSONObject().apply {
                        put("tool", log.step.type)
                        put("toolLabel", log.step.typeLabel)
                        put("requestedArgs", log.step.toolArgs?.let { JSONObject(it.toString()) } ?: JSONObject())
                        put("riskLevel", call?.riskLevel ?: "low")
                        put("requiresConfirmation", AgentSafetyPolicy.requiresConfirmation(goal, log.step))
                        put("appName", log.step.appName.orEmpty())
                        put("packageName", log.step.packageName.orEmpty())
                        put("status", if (execution?.ok == true) "verified" else if (execution == null) "state_mismatch" else "failed")
                        put("ok", execution?.ok == true)
                        put("verified", execution?.ok == true)
                        put("shouldContinue", execution?.shouldContinue == true)
                        put("technicalDetail", (execution?.message ?: log.step.reason).orEmpty().take(1_800))
                        put("undoAvailable", false)
                    })
                }
            })
        }
        val marker = "[[AI_LEDGER_CLIENT_TOOL_RESULT_V1]]${receipt}"
        val resolvedModel = call?.finalModel
            ?.takeIf(String::isNotBlank)
            ?.let(ChatModel::fromId)
            ?: modelPreference
        return runCatching {
            withContext(Dispatchers.IO) {
                aiWorkerClient.sendChat(
                    messages = listOf(
                        ChatMessage(
                            id = "client-tool-result-$callId",
                            text = marker,
                            role = MessageRole.User,
                        ),
                    ),
                    modelPreference = resolvedModel,
                    onlineEnabled = false,
                )
            }
        }.fold(
            onSuccess = { response ->
                response.reply.trim().takeIf(String::isNotBlank)?.let { result.copy(message = it) } ?: result
            },
            onFailure = { error ->
                AgentRuntimeController.noteDiagnostic("客户端工具结果回传失败：${error.message.orEmpty().take(80)}")
                result
            },
        )
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

private fun AgentTaskRunResult.visualReceiptStatus(): String = when {
    completed -> "verified"
    stoppedForConfirmation -> "confirmation_required"
    message.contains("cancel", ignoreCase = true) ||
        message.contains("用户停止") ||
        message.contains("已暂停") -> "cancelled"
    logs.any { it.execution?.ok == false } -> "failed"
    else -> "state_mismatch"
}

private fun AgentTaskRunResult.isAccessibilityUnavailable(): Boolean {
    val text = message.lowercase()
    return text.contains("accessibility") ||
        text.contains("无障碍服务未开启") ||
        text.contains("无障碍服务未连接") ||
        text.contains("需要视觉/无障碍") ||
        text.contains("无障碍执行")
}

enum class AgentOrchestratorRoute {
    LegacyRunner,
    VisualLoop,
}
