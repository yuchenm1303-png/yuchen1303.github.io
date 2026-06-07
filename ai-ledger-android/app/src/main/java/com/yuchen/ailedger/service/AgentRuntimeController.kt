package com.yuchen.ailedger.service

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AgentPendingConfirmation(
    val id: Long = System.currentTimeMillis(),
    val title: String = "需要确认",
    val actionText: String,
    val message: String,
    val positiveText: String = "继续执行",
    val negativeText: String = "取消任务",
)

data class AgentOverlayProgress(
    val enabled: Boolean = true,
    val running: Boolean = false,
    val title: String = "AI 智能体",
    val status: String = "待命",
    val currentAction: String = "等待任务",
    val lastResult: String = "",
    val logs: List<String> = emptyList(),
    val pendingConfirmation: AgentPendingConfirmation? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

object AgentRuntimeController {
    private val mutableEnabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    private val mutableProgress = MutableStateFlow(AgentOverlayProgress())
    val progress: StateFlow<AgentOverlayProgress> = mutableProgress.asStateFlow()

    private val mutableOverlayHiddenForCapture = MutableStateFlow(false)
    val overlayHiddenForCapture: StateFlow<Boolean> = mutableOverlayHiddenForCapture.asStateFlow()

    private val confirmationLock = Any()
    private var pendingConfirmationId: Long = 0L
    private var pendingConfirmationDeferred: CompletableDeferred<Boolean>? = null

    private val overlayCaptureLock = Any()
    private val overlayCaptureRestoreHandler = Handler(Looper.getMainLooper())
    private var overlayCaptureDepth: Int = 0
    private var overlayCaptureGeneration: Long = 0L

    fun isEnabled(): Boolean = mutableEnabled.value

    fun setEnabled(value: Boolean) {
        if (!value) {
            completePendingConfirmation(false)
            AiAgentAccessibilityService.endTaskSession()
            resetCleanVisualCapture()
        } else {
            ensureOverlayCaptureVisibleIfIdle()
        }
        mutableEnabled.value = value
        mutableProgress.value = mutableProgress.value.copy(
            enabled = value,
            running = if (value) mutableProgress.value.running else false,
            status = if (value) "待命" else "已关闭",
            currentAction = if (value) "等待任务" else "智能体自动执行已暂停",
            lastResult = if (value) mutableProgress.value.lastResult else "",
            pendingConfirmation = if (value) mutableProgress.value.pendingConfirmation else null,
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun beginCleanVisualCapture() {
        val generation: Long
        synchronized(overlayCaptureLock) {
            overlayCaptureDepth += 1
            overlayCaptureGeneration += 1L
            generation = overlayCaptureGeneration
            mutableOverlayHiddenForCapture.value = true
        }
        overlayCaptureRestoreHandler.postDelayed({
            synchronized(overlayCaptureLock) {
                if (overlayCaptureDepth > 0 && overlayCaptureGeneration == generation) {
                    overlayCaptureDepth = 0
                    overlayCaptureGeneration += 1L
                    mutableOverlayHiddenForCapture.value = false
                }
            }
        }, OVERLAY_CAPTURE_WATCHDOG_MS)
    }

    fun endCleanVisualCapture() {
        synchronized(overlayCaptureLock) {
            overlayCaptureDepth = (overlayCaptureDepth - 1).coerceAtLeast(0)
            if (overlayCaptureDepth == 0) {
                overlayCaptureGeneration += 1L
                mutableOverlayHiddenForCapture.value = false
            }
        }
    }

    fun resetCleanVisualCapture() {
        synchronized(overlayCaptureLock) {
            overlayCaptureDepth = 0
            overlayCaptureGeneration += 1L
            mutableOverlayHiddenForCapture.value = false
        }
    }

    fun ensureOverlayCaptureVisibleIfIdle() {
        synchronized(overlayCaptureLock) {
            if (overlayCaptureDepth == 0 && mutableOverlayHiddenForCapture.value) {
                overlayCaptureGeneration += 1L
                mutableOverlayHiddenForCapture.value = false
            }
        }
    }

    fun startTask(goal: String) {
        completePendingConfirmation(false)
        resetCleanVisualCapture()
        AiAgentAccessibilityService.beginTaskSession()
        val cleanGoal = goal.trim().take(48).ifBlank { "手机智能体任务" }
        mutableProgress.value = AgentOverlayProgress(
            enabled = mutableEnabled.value,
            running = true,
            title = "AI 智能体",
            status = "准备执行",
            currentAction = cleanGoal,
            logs = listOf("目标：$cleanGoal"),
        )
    }

    fun finishTask(message: String, completed: Boolean) {
        completePendingConfirmation(false)
        AiAgentAccessibilityService.endTaskSession()
        resetCleanVisualCapture()
        val resultText = message.trim().take(72).ifBlank { if (completed) "任务完成" else "任务暂停" }
        mutableProgress.value = mutableProgress.value.copy(
            running = false,
            status = if (completed) "已完成" else "已暂停",
            currentAction = if (completed) "任务完成" else "任务已暂停",
            lastResult = resultText,
            pendingConfirmation = null,
            logs = (mutableProgress.value.logs + "最终：$resultText").takeLast(MAX_LOGS),
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun failTask(message: String) {
        completePendingConfirmation(false)
        AiAgentAccessibilityService.endTaskSession()
        resetCleanVisualCapture()
        val resultText = message.trim().take(72).ifBlank { "智能体执行失败" }
        mutableProgress.value = mutableProgress.value.copy(
            running = false,
            status = "执行失败",
            currentAction = "任务异常",
            lastResult = resultText,
            pendingConfirmation = null,
            logs = (mutableProgress.value.logs + "失败：$resultText").takeLast(MAX_LOGS),
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun noteAction(step: CloudAgentStep) {
        ensureOverlayCaptureVisibleIfIdle()
        val actionText = buildActionText(step)
        mutableProgress.value = mutableProgress.value.copy(
            enabled = true,
            running = true,
            status = "执行中",
            currentAction = actionText,
            pendingConfirmation = null,
            logs = (mutableProgress.value.logs + actionText).takeLast(MAX_LOGS),
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun noteResult(step: CloudAgentStep, result: AgentExecutionResult) {
        ensureOverlayCaptureVisibleIfIdle()
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
            pendingConfirmation = null,
            logs = (mutableProgress.value.logs + "结果：$resultText").takeLast(MAX_LOGS),
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun noteDiagnostic(message: String) {
        val current = mutableProgress.value
        if (!current.running && current.pendingConfirmation == null) return
        val text = message.trim().take(90)
        if (text.isBlank()) return
        mutableProgress.value = current.copy(
            lastResult = text,
            logs = (current.logs + "诊断：$text").takeLast(MAX_LOGS),
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun requestRiskConfirmation(goal: String, step: CloudAgentStep): Boolean {
        ensureOverlayCaptureVisibleIfIdle()
        val actionText = buildActionText(step)
        val confirmation = AgentPendingConfirmation(
            id = System.currentTimeMillis(),
            actionText = actionText,
            message = buildConfirmationMessage(goal, step),
        )
        val deferred = CompletableDeferred<Boolean>()
        synchronized(confirmationLock) {
            pendingConfirmationDeferred?.complete(false)
            pendingConfirmationId = confirmation.id
            pendingConfirmationDeferred = deferred
            mutableProgress.value = mutableProgress.value.copy(
                enabled = true,
                running = true,
                status = "等待确认",
                currentAction = "高风险动作确认",
                lastResult = confirmation.message,
                pendingConfirmation = confirmation,
                logs = (mutableProgress.value.logs + "等待确认：$actionText").takeLast(MAX_LOGS),
                updatedAt = System.currentTimeMillis(),
            )
        }
        return try {
            deferred.await()
        } finally {
            clearPendingIfSame(confirmation.id, deferred)
        }
    }

    fun choosePendingAction(accepted: Boolean) {
        if (accepted) confirmPendingRiskAction() else cancelPendingRiskAction()
    }

    fun confirmPendingRiskAction() {
        val deferred = synchronized(confirmationLock) {
            val current = pendingConfirmationDeferred ?: return
            pendingConfirmationDeferred = null
            pendingConfirmationId = 0L
            mutableProgress.value = mutableProgress.value.copy(
                running = true,
                status = "已确认",
                currentAction = mutableProgress.value.pendingConfirmation?.actionText ?: "继续执行",
                lastResult = "用户已确认，继续执行。",
                pendingConfirmation = null,
                logs = (mutableProgress.value.logs + "确认：继续执行").takeLast(MAX_LOGS),
                updatedAt = System.currentTimeMillis(),
            )
            current
        }
        deferred.complete(true)
    }

    fun cancelPendingRiskAction() {
        AiAgentAccessibilityService.endTaskSession()
        resetCleanVisualCapture()
        val deferred = synchronized(confirmationLock) {
            val current = pendingConfirmationDeferred ?: return
            pendingConfirmationDeferred = null
            pendingConfirmationId = 0L
            mutableProgress.value = mutableProgress.value.copy(
                running = false,
                status = "已取消",
                currentAction = "用户取消高风险动作",
                lastResult = "已取消本次智能体任务。",
                pendingConfirmation = null,
                logs = (mutableProgress.value.logs + "确认：取消任务").takeLast(MAX_LOGS),
                updatedAt = System.currentTimeMillis(),
            )
            current
        }
        deferred.complete(false)
    }

    private fun completePendingConfirmation(value: Boolean) {
        val deferred = synchronized(confirmationLock) {
            val current = pendingConfirmationDeferred ?: return
            pendingConfirmationDeferred = null
            pendingConfirmationId = 0L
            current
        }
        deferred.complete(value)
    }

    private fun clearPendingIfSame(id: Long, deferred: CompletableDeferred<Boolean>) {
        synchronized(confirmationLock) {
            if (pendingConfirmationId == id && pendingConfirmationDeferred === deferred) {
                AiAgentAccessibilityService.endTaskSession()
                resetCleanVisualCapture()
                pendingConfirmationDeferred = null
                pendingConfirmationId = 0L
                mutableProgress.value = mutableProgress.value.copy(
                    running = false,
                    status = "已暂停",
                    currentAction = "确认已失效",
                    lastResult = "高风险确认已取消。",
                    pendingConfirmation = null,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
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

    private fun buildConfirmationMessage(goal: String, step: CloudAgentStep): String {
        val reason = step.reason?.takeIf { it.isNotBlank() }?.take(48)
        val target = step.targetText?.takeIf { it.isNotBlank() } ?: step.text?.takeIf { it.isNotBlank() }
        return buildString {
            append("即将执行可能有风险的操作")
            target?.let { append("：").append(it.take(24)) }
            reason?.let { append("。原因：").append(it) }
            if (goal.isNotBlank()) append("。目标：").append(goal.take(36))
        }.take(96)
    }

    private const val MAX_LOGS = 7
    private const val OVERLAY_CAPTURE_WATCHDOG_MS = 2_500L
}
