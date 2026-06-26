package com.yuchen.ailedger.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VisualAgentHudTarget(
    val taskId: Long = 0L,
    val revision: Long = 0L,
    val x: Float = 0.52f,
    val y: Float = 0.46f,
    val normalized: Boolean = true,
    val positioned: Boolean = false,
    val actionType: String = "",
    val targetText: String = "",
    val detail: String = "",
    val plannedAt: Long = System.currentTimeMillis(),
)

/**
 * Lightweight bridge from the verified visual plan to the presentation-only HUD.
 * It never changes, delays, validates, or executes an agent action.
 */
object VisualAgentHudRuntime {
    private val mutableTarget = MutableStateFlow<VisualAgentHudTarget?>(null)
    val target: StateFlow<VisualAgentHudTarget?> = mutableTarget.asStateFlow()

    private var revision: Long = 0L

    fun notePlannedStep(step: CloudAgentStep) {
        val x = step.x
        val y = step.y
        if (x != null && y != null) {
            notePlannedTarget(
                step = step,
                x = x,
                y = y,
                normalized = x in 0f..1.05f && y in 0f..1.05f,
            )
            return
        }
        val taskId = AgentRuntimeController.currentTaskId()
        val previous = mutableTarget.value?.takeIf { it.taskId == taskId }
        revision += 1L
        mutableTarget.value = VisualAgentHudTarget(
            taskId = taskId,
            revision = revision,
            x = previous?.x ?: 0.52f,
            y = previous?.y ?: 0.46f,
            normalized = previous?.normalized ?: true,
            positioned = false,
            actionType = step.type,
            targetText = step.targetText.orEmpty().trim().take(72),
            detail = step.reason.orEmpty().trim().take(180),
            plannedAt = System.currentTimeMillis(),
        )
    }

    fun notePlannedTarget(
        step: CloudAgentStep,
        x: Float,
        y: Float,
        normalized: Boolean = false,
    ) {
        revision += 1L
        mutableTarget.value = VisualAgentHudTarget(
            taskId = AgentRuntimeController.currentTaskId(),
            revision = revision,
            x = x,
            y = y,
            normalized = normalized,
            positioned = true,
            actionType = step.type,
            targetText = step.targetText.orEmpty().trim().take(72),
            detail = step.reason.orEmpty().trim().take(180),
            plannedAt = System.currentTimeMillis(),
        )
    }

    fun clearForTask(taskId: Long) {
        val current = mutableTarget.value ?: return
        if (taskId <= 0L || current.taskId == taskId) mutableTarget.value = null
    }
}
