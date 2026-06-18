package com.yuchen.ailedger.service

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val MAX_LOGS = 24
private const val OVERLAY_CAPTURE_WATCHDOG_MS = 2_500L
private const val MODEL_OUTPUT_LOG_CHARS = 240
private const val MAX_MODEL_OUTPUT_LOG_LINES = 14

data class AgentPendingConfirmation(
    val id: Long = System.currentTimeMillis(),
    val title: String = "需要确认",
    val actionText: String,
    val message: String,
    val positiveText: String = "继续执行",
    val negativeText: String = "取消任务",
)

data class AgentPendingUserInput(
    val id: Long = System.currentTimeMillis(),
    val title: String = "需要你输入",
    val actionText: String,
    val message: String,
    val hint: String = "请输入内容",
    val positiveText: String = "确认输入",
    val negativeText: String = "取消任务",
    val sensitive: Boolean = false,
)

data class AgentOverlayProgress(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val title: String = "AI 智能体",
    val status: String = "已关闭",
    val currentAction: String = "强制视觉智能体已关闭",
    val lastResult: String = "",
    val logs: List<String> = emptyList(),
    val pendingConfirmation: AgentPendingConfirmation? = null,
    val pendingUserInput: AgentPendingUserInput? = null,
    val userTakeoverPaused: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

object AgentRuntimeController {
    // 首页 Agent 开关只代表强制 GUI/视觉智能体；普通聊天内部设备工具不受它控制。
    private val mutableEnabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    private val mutableProgress = MutableStateFlow(AgentOverlayProgress())
    val progress: StateFlow<AgentOverlayProgress> = mutableProgress.asStateFlow()

    private val mutableOverlayHiddenForCapture = MutableStateFlow(false)
    val overlayHiddenForCapture: StateFlow<Boolean> = mutableOverlayHiddenForCapture.asStateFlow()

    private val confirmationLock = Any()
    private var pendingConfirmationId: Long = 0L
    private var pendingConfirmationDeferred: CompletableDeferred<Boolean>? = null

    private val userInputLock = Any()
    private var pendingUserInputId: Long = 0L
    private var pendingUserInputDeferred: CompletableDeferred<String?>? = null

    private val overlayCaptureLock = Any()
    private val overlayCaptureRestoreHandler = Handler(Looper.getMainLooper())
    private var overlayCaptureDepth: Int = 0
    private var overlayCaptureGeneration: Long = 0L

    @Volatile private var manualStopGeneration: Long = 0L
    @Volatile private var userTakeoverPaused: Boolean = false

    fun isEnabled(): Boolean = mutableEnabled.value

    fun currentManualStopGeneration(): Long = manualStopGeneration

    fun isManualStopRequested(startGeneration: Long): Boolean {
        return manualStopGeneration != startGeneration
    }

    fun isUserTakeoverPaused(): Boolean = userTakeoverPaused

    fun setEnabled(value: Boolean) {
        val current = mutableProgress.value
        if (mutableEnabled.value == value && current.enabled == value) return
        if (!value) {
            manualStopGeneration += 1L
            userTakeoverPaused = false
            completePendingConfirmation(false)
            completePendingUserInput(null)
            AiAgentAccessibilityService.endTaskSession()
            resetCleanVisualCapture()
        } else {
            ensureOverlayCaptureVisibleIfIdle()
        }
        mutableEnabled.value = value
        publishProgress(
            current.copy(
                enabled = value,
                running = if (value) current.running else false,
                status = if (value) "待命" else "已关闭",
                currentAction = if (value) "等待视觉任务" else "强制视觉智能体已关闭",
                lastResult = if (value) current.lastResult else "",
                pendingConfirmation = if (value) current.pendingConfirmation else null,
                pendingUserInput = if (value) current.pendingUserInput else null,
                userTakeoverPaused = if (value) current.userTakeoverPaused else false,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun beginCleanVisualCapture() {
        val generation: Long
        synchronized(overlayCaptureLock) {
            overlayCaptureDepth += 1
            overlayCaptureGeneration += 1L
            generation = overlayCaptureGeneration
            if (!mutableOverlayHiddenForCapture.value) mutableOverlayHiddenForCapture.value = true
        }
        overlayCaptureRestoreHandler.postDelayed({
            synchronized(overlayCaptureLock) {
                if (overlayCaptureDepth > 0 && overlayCaptureGeneration == generation) {
                    overlayCaptureDepth = 0
                    overlayCaptureGeneration += 1L
                    if (mutableOverlayHiddenForCapture.value) mutableOverlayHiddenForCapture.value = false
                }
            }
        }, OVERLAY_CAPTURE_WATCHDOG_MS)
    }

    fun endCleanVisualCapture() {
        synchronized(overlayCaptureLock) {
            overlayCaptureDepth = (overlayCaptureDepth - 1).coerceAtLeast(0)
            if (overlayCaptureDepth == 0) {
                overlayCaptureGeneration += 1L
                if (mutableOverlayHiddenForCapture.value) mutableOverlayHiddenForCapture.value = false
            }
        }
    }

    fun resetCleanVisualCapture() {
        synchronized(overlayCaptureLock) {
            overlayCaptureDepth = 0
            overlayCaptureGeneration += 1L
            if (mutableOverlayHiddenForCapture.value) mutableOverlayHiddenForCapture.value = false
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
        completePendingUserInput(null)
        userTakeoverPaused = false
        resetCleanVisualCapture()
        val cleanGoal = goal.trim().take(48).ifBlank { "手机智能体任务" }
        publishProgress(
            AgentOverlayProgress(
                enabled = mutableEnabled.value,
                running = true,
                title = "AI 智能体",
                status = "准备执行",
                currentAction = cleanGoal,
                logs = listOf("目标：$cleanGoal"),
            )
        )
    }

    fun stopTaskByUser(message: String = "用户手动停止了本次智能体任务。") {
        manualStopGeneration += 1L
        userTakeoverPaused = false
        completePendingConfirmation(false)
        completePendingUserInput(null)
        AiAgentAccessibilityService.endTaskSession()
        resetCleanVisualCapture()
        val current = mutableProgress.value
        val resultText = message.trim().take(72).ifBlank { "用户手动停止了本次智能体任务。" }
        publishProgress(
            current.copy(
                running = false,
                status = "已手动停止",
                currentAction = "用户手动停止",
                lastResult = resultText,
                pendingConfirmation = null,
                pendingUserInput = null,
                userTakeoverPaused = false,
                logs = (current.logs + "停止：$resultText").takeLast(MAX_LOGS),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun pauseForUserTakeover(message: String = "用户接管中，智能体暂停自动点击。") {
        ensureOverlayCaptureVisibleIfIdle()
        userTakeoverPaused = true
        val current = mutableProgress.value
        publishProgress(
            current.copy(
                enabled = true,
                running = current.running,
                status = "用户接管",
                currentAction = "等待用户接管",
                lastResult = message.take(72),
                userTakeoverPaused = true,
                logs = (current.logs + "接管：$message").takeLast(MAX_LOGS),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun resumeFromUserTakeover(message: String = "用户已恢复智能体执行。") {
        userTakeoverPaused = false
        val current = mutableProgress.value
        publishProgress(
            current.copy(
                enabled = true,
                running = current.running,
                status = if (current.running) "执行中" else "待命",
                currentAction = if (current.running) "继续执行" else "等待任务",
                lastResult = message.take(72),
                userTakeoverPaused = false,
                logs = (current.logs + "恢复：$message").takeLast(MAX_LOGS),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun finishTask(message: String, completed: Boolean) {
        completePendingConfirmation(false)
        completePendingUserInput(null)
        userTakeoverPaused = false
        AiAgentAccessibilityService.endTaskSession()
        resetCleanVisualCapture()
        val current = mutableProgress.value
        val resultText = message.trim().take(72).ifBlank { if (completed) "任务完成" else "任务暂停" }
        publishProgress(
            current.copy(
                running = false,
                status = if (completed) "已完成" else "已暂停",
                currentAction = if (completed) "任务完成" else "任务已暂停",
                lastResult = resultText,
                pendingConfirmation = null,
                pendingUserInput = null,
                userTakeoverPaused = false,
                logs = (current.logs + "最终：$resultText").takeLast(MAX_LOGS),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun failTask(message: String) {
        completePendingConfirmation(false)
        completePendingUserInput(null)
        userTakeoverPaused = false
        AiAgentAccessibilityService.endTaskSession()
        resetCleanVisualCapture()
        val current = mutableProgress.value
        val resultText = message.trim().take(72).ifBlank { "智能体执行失败" }
        publishProgress(
            current.copy(
                running = false,
                status = "执行失败",
                currentAction = "任务异常",
                lastResult = resultText,
                pendingConfirmation = null,
                pendingUserInput = null,
                userTakeoverPaused = false,
                logs = (current.logs + "失败：$resultText").takeLast(MAX_LOGS),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun noteAction(step: CloudAgentStep) {
        val current = mutableProgress.value
        if (!current.running) return
        if (step.type !in CloudAgentStep.deviceToolTypes) beginCleanVisualCapture()
        val actionText = buildActionText(step)
        publishProgress(
            current.copy(
                enabled = true,
                running = true,
                status = "执行中",
                currentAction = actionText,
                pendingConfirmation = null,
                pendingUserInput = null,
                logs = (current.logs + actionText).takeLast(MAX_LOGS),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun noteResult(step: CloudAgentStep, result: AgentExecutionResult) {
        if (step.type !in CloudAgentStep.deviceToolTypes) endCleanVisualCapture()
        val current = mutableProgress.value
        if (!current.running) return
        val resultText = result.message.take(64)
        publishProgress(
            current.copy(
                // noteResult 只记录中间结果，不提前关闭 running。
                // 真正结束必须由 AgentTaskRunner.finishTask/failTask 统一收口，避免一次性内部工具被误判成“任务已暂停”。
                running = current.running,
                status = when {
                    result.ok && result.shouldContinue -> "执行中"
                    result.ok -> "已完成"
                    else -> "重新规划"
                },
                currentAction = buildActionText(step),
                lastResult = resultText,
                pendingConfirmation = null,
                pendingUserInput = null,
                logs = (current.logs + "结果：$resultText").takeLast(MAX_LOGS),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun noteDiagnostic(message: String) {
        val current = mutableProgress.value
        if (!current.running && current.pendingConfirmation == null && current.pendingUserInput == null) return
        val text = message.trim().take(90)
        if (text.isBlank()) return
        publishProgress(
            current.copy(
                lastResult = text,
                logs = (current.logs + "诊断：$text").takeLast(MAX_LOGS),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun noteModelOutput(output: String) {
        val current = mutableProgress.value
        if (!current.running) return
        val chunks = output
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .flatMap { line -> line.chunked(MODEL_OUTPUT_LOG_CHARS) }
            .take(MAX_MODEL_OUTPUT_LOG_LINES)
        if (chunks.isEmpty()) return
        val entries = chunks.mapIndexed { index, line ->
            if (index == 0) "模型：$line" else "模型续：$line"
        }
        publishProgress(
            current.copy(
                logs = (current.logs + entries).takeLast(MAX_LOGS),
                updatedAt = System.currentTimeMillis(),
            )
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
            val current = mutableProgress.value
            publishProgress(
                current.copy(
                    enabled = true,
                    running = true,
                    status = "等待确认",
                    currentAction = "高风险动作确认",
                    lastResult = confirmation.message,
                    pendingConfirmation = confirmation,
                    pendingUserInput = null,
                    logs = (current.logs + "等待确认：$actionText").takeLast(MAX_LOGS),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
        return try {
            deferred.await()
        } finally {
            clearPendingConfirmationIfSame(confirmation.id, deferred)
        }
    }

    suspend fun requestUserInput(
        goal: String,
        step: CloudAgentStep,
        title: String? = null,
        messageOverride: String? = null,
        hintOverride: String? = null,
        positiveText: String? = null,
        negativeText: String? = null,
    ): String? {
        ensureOverlayCaptureVisibleIfIdle()
        val actionText = buildActionText(step)
        val sensitive = AgentSafetyPolicy.requiresUserProvidedInput(goal, step)
        val request = AgentPendingUserInput(
            id = System.currentTimeMillis(),
            title = title ?: "需要你输入",
            actionText = actionText,
            message = messageOverride ?: buildUserInputMessage(goal, step, sensitive),
            hint = hintOverride ?: if (sensitive) "请输入验证码/密码" else "请输入需要填入的内容",
            positiveText = positiveText ?: "确认输入",
            negativeText = negativeText ?: "取消任务",
            sensitive = sensitive,
        )
        val deferred = CompletableDeferred<String?>()
        synchronized(userInputLock) {
            pendingUserInputDeferred?.complete(null)
            pendingUserInputId = request.id
            pendingUserInputDeferred = deferred
            val current = mutableProgress.value
            publishProgress(
                current.copy(
                    enabled = true,
                    running = true,
                    status = "等待输入",
                    currentAction = request.title,
                    lastResult = request.message,
                    pendingConfirmation = null,
                    pendingUserInput = request,
                    logs = (current.logs + "等待输入：$actionText").takeLast(MAX_LOGS),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
        return try {
            deferred.await()
        } finally {
            clearPendingUserInputIfSame(request.id, deferred)
        }
    }

    fun choosePendingAction(accepted: Boolean) {
        if (accepted) confirmPendingRiskAction() else cancelPendingRiskAction()
    }

    fun confirmPendingRiskAction() {
        val deferred = synchronized(confirmationLock) {
            val currentDeferred = pendingConfirmationDeferred ?: return
            pendingConfirmationDeferred = null
            pendingConfirmationId = 0L
            val current = mutableProgress.value
            publishProgress(
                current.copy(
                    running = true,
                    status = "已确认",
                    currentAction = current.pendingConfirmation?.actionText ?: "继续执行",
                    lastResult = "用户已确认，继续执行。",
                    pendingConfirmation = null,
                    logs = (current.logs + "确认：继续执行").takeLast(MAX_LOGS),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            currentDeferred
        }
        deferred.complete(true)
    }

    fun cancelPendingRiskAction() {
        val deferred = synchronized(confirmationLock) {
            val current = pendingConfirmationDeferred ?: return
            pendingConfirmationDeferred = null
            pendingConfirmationId = 0L
            current
        }
        deferred.complete(false)
        stopTaskByUser("已取消本次智能体任务。")
    }

    fun submitPendingUserInput(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val deferred = synchronized(userInputLock) {
            val currentDeferred = pendingUserInputDeferred ?: return
            pendingUserInputDeferred = null
            pendingUserInputId = 0L
            val current = mutableProgress.value
            publishProgress(
                current.copy(
                    running = true,
                    status = "已输入",
                    currentAction = current.pendingUserInput?.actionText ?: "继续执行",
                    lastResult = "用户已提供输入，继续执行。",
                    pendingUserInput = null,
                    logs = (current.logs + "输入：用户已提供内容").takeLast(MAX_LOGS),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            currentDeferred
        }
        deferred.complete(clean)
    }

    fun cancelPendingUserInput() {
        val deferred = synchronized(userInputLock) {
            val current = pendingUserInputDeferred ?: return
            pendingUserInputDeferred = null
            pendingUserInputId = 0L
            current
        }
        deferred.complete(null)
        stopTaskByUser("用户取消了输入，本次任务已停止。")
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

    private fun completePendingUserInput(value: String?) {
        val deferred = synchronized(userInputLock) {
            val current = pendingUserInputDeferred ?: return
            pendingUserInputDeferred = null
            pendingUserInputId = 0L
            current
        }
        deferred.complete(value)
    }

    private fun clearPendingConfirmationIfSame(id: Long, deferred: CompletableDeferred<Boolean>) {
        synchronized(confirmationLock) {
            if (pendingConfirmationId == id && pendingConfirmationDeferred === deferred) {
                pendingConfirmationDeferred = null
                pendingConfirmationId = 0L
                publishProgress(mutableProgress.value.copy(pendingConfirmation = null, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    private fun clearPendingUserInputIfSame(id: Long, deferred: CompletableDeferred<String?>) {
        synchronized(userInputLock) {
            if (pendingUserInputId == id && pendingUserInputDeferred === deferred) {
                pendingUserInputDeferred = null
                pendingUserInputId = 0L
                publishProgress(mutableProgress.value.copy(pendingUserInput = null, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    private fun publishProgress(next: AgentOverlayProgress) {
        val current = mutableProgress.value
        if (current.hasSameOverlayContent(next)) return
        mutableProgress.value = next
    }

    private fun AgentOverlayProgress.hasSameOverlayContent(other: AgentOverlayProgress): Boolean {
        return enabled == other.enabled &&
            running == other.running &&
            title == other.title &&
            status == other.status &&
            currentAction == other.currentAction &&
            lastResult == other.lastResult &&
            logs == other.logs &&
            pendingConfirmation == other.pendingConfirmation &&
            pendingUserInput == other.pendingUserInput &&
            userTakeoverPaused == other.userTakeoverPaused
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
            append("即将执行可能影响账号、资金、消息或数据的操作")
            target?.let { append("：").append(it.take(24)) }
            reason?.let { append("。原因：").append(it) }
            if (goal.isNotBlank()) append("。目标：").append(goal.take(36))
        }.take(120)
    }

    private fun buildUserInputMessage(goal: String, step: CloudAgentStep, sensitive: Boolean): String {
        val target = step.targetText?.takeIf { it.isNotBlank() } ?: step.reason?.takeIf { it.isNotBlank() }
        return buildString {
            append(if (sensitive) "当前步骤需要由你亲自输入验证码、密码或一次性校验码，智能体不会猜测或代填。" else "当前步骤需要你提供输入内容。")
            target?.let { append(" 位置：").append(it.take(28)) }
            if (goal.isNotBlank()) append(" 目标：").append(goal.take(36))
        }.take(130)
    }
}
