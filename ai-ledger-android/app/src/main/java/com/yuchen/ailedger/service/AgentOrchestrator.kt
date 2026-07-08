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

        val cloudCall = if (executionMode == AgentExecutionMode.ExplicitAgent) {
            ClientToolCallRegistry.consumeVisual()
        } else {
            null
        }
        val effectiveExecutionMode = if (executionMode == AgentExecutionMode.ExplicitAgent && cloudCall == null) {
            AgentRuntimeController.noteDiagnostic("云端未携带可消费的视觉工具调用，已回落到稳定 VisualForce 执行入口。")
            AgentExecutionMode.VisualForce
        } else {
            executionMode
        }
        return runVisualLoop(
            goal = goal,
            modelPreference = modelPreference,
            maxSteps = maxSteps,
            executionMode = effectiveExecutionMode,
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
            val result = withContext(Dispatchers.IO) {
                VisualLoopRunner(aiWorkerClient, applicationContext).run(
                    goal = goal,
                    maxSteps = maxSteps,
                    executionMode = executionMode,
                )
            }
            terminalReason = "visual_task_${result.visualReceiptStatus()}"
            AgentRuntimeController.finishTask(result.resolvedOutcome())
            if (
                executionMode == AgentExecutionMode.ExplicitAgent &&
                cloudCall != null &&
                !result.isAccessibilityUnavailable()
            ) {
                reportVisualResult(invocation, goal, modelPreference, result)
            } else {
                result
            }
        } catch (error: CancellationException) {
            terminalReason = "visual_task_cancelled"
            throw error
        } finally {
            VisualTaskInvocationRuntime.clear(invocation)
            if (executionMode == AgentExecutionMode.ExplicitAgent) {
                VisualSessionCleanupDispatcher.enqueue(invocation, terminalReason)
            }
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
                            if (execution?.ok == true) "verified"
                            else if (execution == null) "state_mismatch"
                            else "failed",
                        )
                        put("ok", execution?.ok == true)
                        put("verified", execution?.ok == true)
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
                    result.copy(message = reply)
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