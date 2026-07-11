package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val FINAL_TOOL_RESULT_SOURCE = "final_chat_model_client_tool_result"

/**
 * Android never chooses an agent tool. It either executes an explicit local VisualForce request or
 * mechanically consumes the exact structured tool call selected by the cloud Final Chat Model.
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
        if (executionMode == AgentExecutionMode.NormalChatDeviceTool) {
            return AgentTaskRunResult(
                completed = false,
                stoppedForConfirmation = false,
                message = "普通聊天只由云端 Final Chat Model 决策工具。",
                logs = emptyList(),
                handled = false,
            )
        }

        // 普通聊天同一时刻只允许一个发送任务，因此这里消费唯一待执行的视觉调用，
        // 不再用自然语言 goal 重新匹配已经具有稳定 callId 的结构化事务。
        val cloudCall = if (executionMode == AgentExecutionMode.ExplicitAgent) {
            aiWorkerClient.consumeVisualClientToolCall()
        } else {
            null
        }
        if (executionMode == AgentExecutionMode.ExplicitAgent && cloudCall == null) {
            val message = "云端视觉工具调用关联失败，已安全停止，未执行任何屏幕操作。"
            AgentRuntimeController.noteDiagnostic("visual_client_tool_call_missing：当前没有唯一待执行的 computer_run_task。")
            return AgentTaskRunResult(
                completed = false,
                stoppedForConfirmation = false,
                message = message,
                logs = emptyList(),
            )
        }
        if (
            executionMode == AgentExecutionMode.ExplicitAgent &&
            (cloudCall?.name != "computer_run_task" || cloudCall.id.isBlank())
        ) {
            val message = "云端视觉工具调用格式无效，已安全停止，未执行任何屏幕操作。"
            AgentRuntimeController.noteDiagnostic("visual_client_tool_call_invalid：工具名或调用编号无效。")
            return AgentTaskRunResult(
                completed = false,
                stoppedForConfirmation = false,
                message = message,
                logs = emptyList(),
            )
        }
        return runVisualLoop(
            goal = goal,
            modelPreference = modelPreference,
            maxSteps = maxSteps,
            executionMode = executionMode,
            cloudCall = cloudCall,
        )
    }

    private suspend fun runVisualLoop(
        goal: String,
        modelPreference: ChatModel,
        maxSteps: Int,
        executionMode: AgentExecutionMode,
        cloudCall: CloudClientToolCall?,
    ): AgentTaskRunResult {
        val invocation = VisualTaskInvocationRuntime.begin(goal, cloudCall)
        var terminalReason = "visual_task_terminal"
        return try {
            val bootstrapStopGeneration = AgentRuntimeController.currentManualStopGeneration()
            val bootstrapResult = if (executionMode == AgentExecutionMode.ExplicitAgent && cloudCall != null) {
                withContext(Dispatchers.IO) {
                    VisualBootstrapRunner(applicationContext).prepareFirstFrame(
                        plan = VisualBootstrapPlan.fromClientToolCall(cloudCall),
                        isStopped = {
                            AgentRuntimeController.currentManualStopGeneration() != bootstrapStopGeneration
                        },
                    )
                }
            } else {
                VisualBootstrapFirstFrameState.clear()
                null
            }
            if (bootstrapResult != null && !bootstrapResult.ok) {
                val result = AgentTaskRunResult(
                    completed = false,
                    stoppedForConfirmation = false,
                    message = bootstrapResult.message,
                    logs = bootstrapResult.logs,
                )
                terminalReason = "visual_task_bootstrap_failed"
                AgentRuntimeController.failTask(bootstrapResult.message)
                return reportVisualResult(invocation, goal, modelPreference, result)
            }
            bootstrapResult?.let { AgentRuntimeController.noteDiagnostic(it.message.take(120)) }
            val result = withContext(Dispatchers.IO) {
                VisualLoopRunner(aiWorkerClient, applicationContext).run(
                    goal = goal,
                    maxSteps = maxSteps,
                    executionMode = executionMode,
                    clientToolCall = cloudCall,
                )
            }
            terminalReason = "visual_task_${result.visualReceiptStatus()}"
            AgentRuntimeController.finishTask(result.resolvedOutcome())
            if (executionMode == AgentExecutionMode.ExplicitAgent && cloudCall != null) {
                reportVisualResult(invocation, goal, modelPreference, result)
            } else {
                result
            }
        } catch (error: CancellationException) {
            terminalReason = "visual_task_cancelled"
            throw error
        } finally {
            VisualTaskInvocationRuntime.clear(invocation)
            VisualBootstrapFirstFrameState.clear()
            VisualSessionCleanupDispatcher.enqueue(invocation, terminalReason)
        }
    }

    private suspend fun reportVisualResult(
        invocation: VisualTaskInvocation,
        goal: String,
        modelPreference: ChatModel,
        result: AgentTaskRunResult,
    ): AgentTaskRunResult {
        val call = invocation.clientToolCall ?: return result
        val receipt = JSONObject().apply {
            put("protocol", call.resultProtocol)
            put("toolCallId", call.id)
            put("toolName", call.name)
            put("toolArguments", JSONObject(call.arguments.toString()))
            put("finalModel", call.finalModel ?: modelPreference.id)
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
                        put("riskLevel", call.riskLevel)
                        put("requiresConfirmation", AgentSafetyPolicy.requiresConfirmation(goal, log.step))
                        put("appName", log.step.appName.orEmpty())
                        put("packageName", log.step.packageName.orEmpty())
                        put(
                            "status",
                            if (execution?.ok == true) "executed"
                            else if (execution == null) "state_mismatch"
                            else "failed",
                        )
                        put("ok", execution?.ok == true)
                        put("verified", false)
                        put("shouldContinue", execution?.shouldContinue == true)
                        put("technicalDetail", (execution?.message ?: log.step.reason).orEmpty().take(1_800))
                        put("undoAvailable", false)
                    })
                }
            })
        }
        val resolvedModel = call.finalModel
            ?.takeIf(String::isNotBlank)
            ?.let(ChatModel::fromId)
            ?: modelPreference
        val marker = "[[AI_LEDGER_CLIENT_TOOL_RESULT_V1]]${receipt}"
        return runCatching {
            withContext(Dispatchers.IO) {
                aiWorkerClient.sendChat(
                    messages = listOf(
                        ChatMessage(
                            id = "client-tool-result-${call.id}",
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
                val reply = response.reply.trim()
                if (response.source == FINAL_TOOL_RESULT_SOURCE && reply.isNotBlank()) {
                    if (result.isAccessibilityUnavailable()) result else result.copy(message = reply)
                } else {
                    AgentRuntimeController.noteDiagnostic("云端没有确认客户端工具结果续写来源。")
                    result
                }
            },
            onFailure = { error ->
                AgentRuntimeController.noteDiagnostic("客户端工具结果回传失败：${error.message.orEmpty().take(80)}")
                result
            },
        )
    }
}

private fun AgentTaskRunResult.visualReceiptStatus(): String = when {
    completed -> "verified"
    stoppedForConfirmation -> "confirmation_required"
    isAccessibilityUnavailable() -> "permission_required"
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
