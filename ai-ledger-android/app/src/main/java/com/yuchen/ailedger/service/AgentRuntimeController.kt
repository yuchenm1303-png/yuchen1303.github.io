package com.yuchen.ailedger.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AgentOverlayProgress(
    val enabled: Boolean = true,
    val running: Boolean = false,
    val title: String = "AI 智能体",
    val status: String = "待命",
    val currentAction: String = "等待任务",
    val lastResult: String = "",
    val logs: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

object AgentRuntimeController {
    private val mutableEnabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    private val mutableProgress = MutableStateFlow(AgentOverlayProgress())
    val progress: StateFlow<AgentOverlayProgress> = mutableProgress.asStateFlow()

    fun isEnabled(): Boolean = mutableEnabled.value

    fun setEnabled(value: Boolean) {
        mutableEnabled.value = value
        mutableProgress.value = mutableProgress.value.copy(
            enabled = value,
            running = if (value) mutableProgress.value.running else false,
            status = if (value) "待命" else "已关闭",
            currentAction = if (value) "等待任务" else "智能体自动执行已暂停",
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun noteAction(step: CloudAgentStep) {
        val actionText = buildActionText(step)
        mutableProgress.value = mutableProgress.value.copy(
            enabled = true,
            running = true,
            status = "执行中",
            currentAction = actionText,
            logs = (mutableProgress.value.logs + actionText).takeLast(MAX_LOGS),
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun noteResult(step: CloudAgentStep, result: AgentExecutionResult) {
        val resultText = result.message.take(64)
        mutableProgress.value = mutableProgress.value.copy(
            running = result.shouldContinue && result.ok,
            status = when {
                result.ok && result.shouldContinue -> "执行中"
                result.ok -> "已完成"
                else -> "已暂停"
            },
            currentAction = buildActionText(step),
            lastResult = resultText,
            logs = (mutableProgress.value.logs + "结果：$resultText").takeLast(MAX_LOGS),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun buildActionText(step: CloudAgentStep): String {
        return buildString {
            append(step.typeLabel)
            step.appName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it.take(16)) }
            step.targetText?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it.take(18)) }
            step.text?.takeIf { it.isNotBlank() }?.let { append(" · 输入 ").append(it.take(14)) }
            step.direction?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }.ifBlank { step.type }
    }

    private const val MAX_LOGS = 5
}
