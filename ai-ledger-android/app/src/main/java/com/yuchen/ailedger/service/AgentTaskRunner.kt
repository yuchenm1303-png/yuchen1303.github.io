package com.yuchen.ailedger.service

import android.content.Context
import android.os.SystemClock
import android.view.Choreographer
import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject

data class AgentTaskStepLog(
    val index: Int,
    val app: String,
    val step: CloudAgentStep,
    val execution: AgentExecutionResult?,
)

data class AgentTaskRunResult(
    val completed: Boolean,
    val stoppedForConfirmation: Boolean,
    val message: String,
    val logs: List<AgentTaskStepLog>,
)

class AgentTaskRunner(
    private val aiWorkerClient: AiWorkerClient,
    appContext: Context? = null,
) {
    private val applicationContext: Context? = appContext?.applicationContext
    private val installedAppIndex: InstalledAppIndex? = applicationContext?.let { InstalledAppIndex(it) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun run(
        goal: String,
        modelPreference: ChatModel,
        maxSteps: Int = Int.MAX_VALUE,
    ): AgentTaskRunResult {
        val logs = mutableListOf<AgentTaskStepLog>()
        if (!AgentRuntimeController.isEnabled()) {
            val message = "智能体已关闭，请先打开 Agent 开关。"
            AgentRuntimeController.finishTask(message, completed = false)
            return AgentTaskRunResult(false, false, message, logs)
        }

        val stopGeneration = AgentRuntimeController.currentManualStopGeneration()
        val recentActions = mutableListOf<String>()
        val taskSessionId = "android-agent-${System.currentTimeMillis()}"

        AgentRuntimeController.startTask(goal)

        return try {
            waitForOverlayAndUiFirstFrameBeforeCapture()
            while (!isStopped(stopGeneration)) {
                if (!waitWhileUserTakeoverPaused(stopGeneration)) break

                val observation = captureOnce(forceVisual = true)
                if (!waitWhileUserTakeoverPaused(stopGeneration)) break
                if (!observation.enabled || !observation.serviceConnected) {
                    val message = if (AiAgentAccessibilityService.isConnected()) {
                        "智能体任务已停止，已跳过后台屏幕采集。"
                    } else {
                        "无障碍服务未开启。"
                    }
                    AgentRuntimeController.failTask(message)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                val snapshot = observation.toAgentScreenSnapshot()
                val deviceContext = withContext(Dispatchers.IO) {
                    buildDeviceContext(snapshot, goal)
                }
                if (!waitWhileUserTakeoverPaused(stopGeneration)) break

                val plan = try {
                    withContext(Dispatchers.IO) {
                        aiWorkerClient.requestAgentPlan(
                            goal = goal,
                            snapshot = snapshot,
                            modelPreference = modelPreference,
                            recentActions = recentActions.takeLast(MAX_RECENT_ACTIONS),
                            deviceContext = deviceContext,
                            agentMemory = buildAgentMemory(taskSessionId, logs.size, recentActions),
                        )
                    }
                } catch (error: IOException) {
                    val message = "云端规划超时或失败：${error.message ?: "未知错误"}"
                    AgentRuntimeController.failTask(message)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                if (!waitWhileUserTakeoverPaused(stopGeneration)) break

                val state = plan.state
                if (state != null && state.isComplete && state.confidence >= COMPLETE_CONFIDENCE_THRESHOLD) {
                    val message = state.reason.ifBlank { plan.step.reason ?: "任务完成。" }
                    val finishStep = CloudAgentStep(type = "finish", reason = message, riskLevel = "low", requiresConfirmation = false)
                    val done = AgentExecutionResult(true, message, false)
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, finishStep, done)
                    AgentRuntimeController.finishTask(message, completed = true)
                    return AgentTaskRunResult(true, false, message, logs)
                }

                val step = plan.executableSteps
                    .asSequence()
                    .filterNot { it.type == "finish" }
                    .mapNotNull { sanitizeCloudStep(it, snapshot) }
                    .firstOrNull()

                if (step == null) {
                    val message = "云端没有给出可执行动作，已停止以避免后台持续截图和扫节点。"
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                if (step.type == "need_user_help") {
                    pauseForUserAssistance(step.reason ?: "需要用户协助。", stopGeneration)
                    if (isStopped(stopGeneration)) break
                    continue
                }

                val executableStep = materializeTapCoordinateFrame(step, snapshot)
                val contextualStopMessage = contextBlockingMessage(goal, executableStep, recentActions)
                if (contextualStopMessage != null) {
                    val blockedStep = executableStep.copy(reason = cleanStepReason(contextualStopMessage))
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, blockedStep, null)
                    AgentRuntimeController.finishTask(contextualStopMessage, completed = false)
                    return AgentTaskRunResult(false, false, contextualStopMessage, logs)
                }

                val wasConfirmedByUser = if (AgentSafetyPolicy.requiresConfirmation(goal, executableStep)) {
                    val confirmed = AgentRuntimeController.requestRiskConfirmation(goal, executableStep)
                    if (!confirmed) return stoppedByUserResult(logs)
                    true
                } else {
                    false
                }

                if (!waitWhileUserTakeoverPaused(stopGeneration)) break

                if (!wasConfirmedByUser && !AgentSafetyPolicy.canAutoExecuteInCurrentStage(goal, executableStep)) {
                    if (AgentSafetyPolicy.requiresUserProvidedInput(goal, executableStep)) {
                        pauseForUserAssistance(executableStep.reason ?: "当前步骤需要你接管处理：${executableStep.typeLabel}", stopGeneration)
                        if (isStopped(stopGeneration)) break
                        continue
                    }
                    val message = executableStep.reason ?: "当前动作暂不能自动执行：${executableStep.typeLabel}"
                    AgentRuntimeController.finishTask(message, completed = false)
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, executableStep, null)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                val result = executeAndRecord(executableStep, snapshot.currentApp, logs, stopGeneration)
                    ?: continue
                recentActions += buildRecentActionSummary(executableStep, result)
                if (!result.ok || !result.shouldContinue) {
                    val message = result.message.ifBlank { "智能体动作结束。" }
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                delayForStep(executableStep)
            }

            val message = "用户已手动停止本次智能体任务。"
            AgentRuntimeController.finishTask(message, completed = false)
            AgentTaskRunResult(false, false, message, logs)
        } catch (error: CancellationException) {
            AgentRuntimeController.stopTaskByUser("本次智能体任务已取消。")
            throw error
        } finally {
            AiAgentAccessibilityService.endTaskSession()
            AgentRuntimeController.resetCleanVisualCapture()
        }
    }

    private fun isStopped(startGeneration: Long): Boolean {
        return AgentRuntimeController.currentManualStopGeneration() != startGeneration ||
            !AgentRuntimeController.progress.value.running
    }

    private suspend fun waitWhileUserTakeoverPaused(startGeneration: Long): Boolean {
        while (!isStopped(startGeneration) && AgentRuntimeController.isUserTakeoverPaused()) {
            AgentRuntimeController.ensureOverlayCaptureVisibleIfIdle()
            delay(USER_TAKEOVER_POLL_MS)
        }
        return !isStopped(startGeneration)
    }

    private suspend fun pauseForUserAssistance(message: String, stopGeneration: Long) {
        AgentRuntimeController.pauseForUserTakeover(message.ifBlank { "需要用户协助，智能体已暂停自动执行。" })
        waitWhileUserTakeoverPaused(stopGeneration)
    }

    private fun stoppedByUserResult(logs: List<AgentTaskStepLog>): AgentTaskRunResult {
        val message = "用户已手动停止本次智能体任务。"
        return AgentTaskRunResult(false, false, message, logs)
    }

    private fun buildAgentMemory(
        taskSessionId: String,
        loopIndex: Int,
        recentActions: List<String>,
    ): JSONObject {
        return JSONObject().apply {
            put("schema", "android_agent_loop_memory_v1")
            put("recentActions", JSONArray().apply {
                recentActions.takeLast(MAX_RECENT_ACTIONS).forEach { put(it) }
            })
            put("loopSignals", JSONObject().apply {
                put("agentSessionId", taskSessionId)
                put("loopIndex", loopIndex)
                put("executedStepCount", loopIndex)
            })
        }
    }

    private fun buildRecentActionSummary(step: CloudAgentStep, result: AgentExecutionResult): String {
        val parts = mutableListOf<String>()
        parts += step.typeLabel
        step.appName?.takeIf { it.isNotBlank() }?.let { parts += "app=$it" }
        step.packageName?.takeIf { it.isNotBlank() }?.let { parts += "pkg=$it" }
        step.targetText?.takeIf { it.isNotBlank() }?.let { parts += "target=$it" }
        step.targetNodeId?.takeIf { it.isNotBlank() }?.let { parts += "node=$it" }
        step.text?.takeIf { it.isNotBlank() }?.let { parts += "text=${it.take(40)}" }
        step.direction?.takeIf { it.isNotBlank() }?.let { parts += "direction=$it" }
        cleanStepReason(step.reason)?.takeIf { it.isNotBlank() }?.let { parts += "reason=${it.take(80)}" }
        parts += "result=${if (result.ok) "ok" else "failed"}:${result.message.take(80)}"
        return parts.joinToString(" · ")
    }

    private fun buildDeviceContext(snapshot: AgentScreenSnapshot, goal: String): AgentDeviceContextSnapshot? {
        val context = applicationContext ?: return null
        val index = installedAppIndex ?: InstalledAppIndex(context)
        return runCatching {
            AgentDeviceContextProvider.build(
                context = context,
                screen = snapshot,
                goal = goal,
                installedAppIndex = index,
            )
        }.getOrNull()
    }

    private suspend fun waitForOverlayAndUiFirstFrameBeforeCapture() {
        // 启动任务后先让聊天界面重组、悬浮窗 Service 创建和首帧绘制落地，再进入第一次重采集。
        // 这只发生在第一轮前，不砍循环、不降低观察能力，也不阻塞主线程。
        withContext(Dispatchers.Main.immediate) {
            repeat(FIRST_CAPTURE_UI_FRAME_YIELDS) {
                awaitNextMainFrame()
            }
        }
        if (FIRST_CAPTURE_OVERLAY_SETTLE_MS > 0L) {
            delay(FIRST_CAPTURE_OVERLAY_SETTLE_MS)
        }
    }

    private suspend fun awaitNextMainFrame() = suspendCancellableCoroutine<Unit> { continuation ->
        val callback = Choreographer.FrameCallback {
            if (continuation.isActive) continuation.resume(Unit)
        }
        Choreographer.getInstance().postFrameCallback(callback)
        continuation.invokeOnCancellation {
            Choreographer.getInstance().removeFrameCallback(callback)
        }
    }

    private suspend fun captureOnce(forceVisual: Boolean = false): ScreenObservation {
        if (!AgentRuntimeController.progress.value.running) {
            return ScreenObservation(
                enabled = true,
                serviceConnected = AiAgentAccessibilityService.isConnected(),
                updatedAt = System.currentTimeMillis(),
            )
        }
        return withContext(Dispatchers.Default) {
            AiAgentAccessibilityService.captureFreshSnapshot(forceVisual = forceVisual)
        }
    }

    private suspend fun executeAndRecord(
        step: CloudAgentStep,
        currentApp: String,
        logs: MutableList<AgentTaskStepLog>,
        stopGeneration: Long,
    ): AgentExecutionResult? {
        if (!waitWhileUserTakeoverPaused(stopGeneration)) return null
        AgentRuntimeController.noteAction(step)
        delay(ACTION_OVERLAY_HIDE_STABILIZE_MS)
        if (AgentRuntimeController.isUserTakeoverPaused()) {
            AgentRuntimeController.ensureOverlayCaptureVisibleIfIdle()
            waitWhileUserTakeoverPaused(stopGeneration)
            return null
        }
        if (isStopped(stopGeneration)) return null
        val result = withContext(Dispatchers.Main) {
            AiAgentAccessibilityService.executeStep(step)
        }
        AgentRuntimeController.noteResult(step, result)
        logs += AgentTaskStepLog(logs.size + 1, currentApp, step, result)
        return result
    }

    private suspend fun delayForStep(step: CloudAgentStep) {
        val delayMs = step.durationMs?.coerceIn(MIN_CUSTOM_STEP_DELAY_MS, MAX_CUSTOM_STEP_DELAY_MS)
            ?: when (step.type) {
                "open_app" -> OPEN_APP_DELAY_MS
                "tap_node", "tap_xy" -> TAP_DELAY_MS
                "input_text" -> INPUT_DELAY_MS
                "scroll", "swipe" -> SCROLL_DELAY_MS
                "wait" -> DEFAULT_WAIT_DELAY_MS
                "back", "home", "recents", "notifications", "quick_settings" -> GLOBAL_ACTION_DELAY_MS
                "finish", "need_user_help" -> 0L
                else -> DEFAULT_STEP_DELAY_MS
            }
        if (delayMs > 0L) delay(delayMs)
    }

    private fun materializeTapCoordinateFrame(step: CloudAgentStep, snapshot: AgentScreenSnapshot): CloudAgentStep {
        if (step.type != "tap_xy") return step.copy(reason = cleanStepReason(step.reason))
        val x = step.x ?: return step.copy(reason = cleanStepReason(step.reason))
        val y = step.y ?: return step.copy(reason = cleanStepReason(step.reason))
        if (x !in 0f..1f || y !in 0f..1f) return step.copy(reason = cleanStepReason(step.reason))
        val visual = snapshot.visual ?: return step.copy(reason = cleanStepReason(step.reason))
        val visualWidth = visual.displayWidth.takeIf { it > 0 } ?: visual.width.takeIf { it > 0 } ?: return step.copy(reason = cleanStepReason(step.reason))
        val visualHeight = visual.displayHeight.takeIf { it > 0 } ?: visual.height.takeIf { it > 0 } ?: return step.copy(reason = cleanStepReason(step.reason))
        val pixelX = (x * visualWidth).coerceIn(0f, visualWidth.toFloat())
        val pixelY = (y * visualHeight).coerceIn(0f, visualHeight.toFloat())
        val frameNote = "坐标已按截图参考帧转为物理像素 ${visualWidth}x${visualHeight}"
        val mergedReason = cleanStepReason(listOfNotNull(step.reason, frameNote).joinToString("。"))
        return step.copy(x = pixelX, y = pixelY, reason = mergedReason)
    }

    private fun sanitizeCloudStep(step: CloudAgentStep, snapshot: AgentScreenSnapshot): CloudAgentStep? {
        if (step.type !in CloudAgentStep.supportedTypes) return null
        if (step.type == "tap_xy") {
            val x = step.x ?: return null
            val y = step.y ?: return null
            if (x !in 0f..1f || y !in 0f..1f) return null
        }
        if (step.type == "tap_node" && step.targetNodeId.isNullOrBlank() && step.targetText.isNullOrBlank()) return null
        if (step.type == "input_text" && step.text.isNullOrBlank()) return null
        if (step.type == "open_app" && step.packageName != null && step.packageName == snapshot.currentApp) return null
        return step.copy(reason = cleanStepReason(step.reason))
    }

    private fun contextBlockingMessage(
        goal: String,
        step: CloudAgentStep,
        recentActions: List<String>,
    ): String? {
        if (!isFinancialTradingGoal(goal)) return null
        val stepText = listOfNotNull(step.type, step.typeLabel, step.targetText, step.text, step.reason, step.appName)
            .joinToString(" ")
            .lowercase()
        val historyText = recentActions.joinToString(" ").lowercase()
        val hasTradeProgress = hasTradeInputProgress(historyText) || hasTradeInputProgress(stepText)

        if (step.type in CONTEXT_BREAKING_STEP_TYPES) {
            return "已进入股票交易/下单相关流程，拦截 ${step.typeLabel}，避免离开当前交易页面导致上下文丢失。请在当前页面接管并确认后手动下单。"
        }

        if (step.type == "open_app" && hasTradeProgress) {
            return "已进入股票交易/下单相关流程，拦截重新打开应用，避免覆盖当前交易页面。请在当前页面接管并确认后手动下单。"
        }

        if (step.type == "tap_xy" || step.type == "tap_node") {
            if (hasOrderSubmitIntent(stepText)) {
                return "检测到可能提交/确认真实交易的动作，已按安全策略暂停。请你核对价格、股数和账户后手动下单。"
            }
        }
        return null
    }

    private fun isFinancialTradingGoal(text: String): Boolean {
        val clean = text.lowercase()
        return FINANCIAL_GOAL_KEYWORDS.any { clean.contains(it) } &&
            TRADING_ACTION_KEYWORDS.any { clean.contains(it) }
    }

    private fun hasTradeInputProgress(text: String): Boolean {
        return TRADE_PROGRESS_KEYWORDS.any { text.contains(it) }
    }

    private fun hasOrderSubmitIntent(text: String): Boolean {
        return ORDER_SUBMIT_KEYWORDS.any { text.contains(it) }
    }

    private fun cleanStepReason(reason: String?): String? {
        val raw = reason?.trim().orEmpty()
        if (raw.isBlank()) return null
        val normalized = raw
            .replace("。。", "。")
            .replace("..", ".")
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
        val parts = normalized
            .split('。', '.', '；', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return parts.joinToString("。").takeIf { it.isNotBlank() }
    }

    companion object {
        private const val DEFAULT_STEP_DELAY_MS = 280L
        private const val FIRST_CAPTURE_UI_FRAME_YIELDS = 2
        private const val FIRST_CAPTURE_OVERLAY_SETTLE_MS = 180L
        private const val ACTION_OVERLAY_HIDE_STABILIZE_MS = 260L
        private const val OPEN_APP_DELAY_MS = 640L
        private const val TAP_DELAY_MS = 220L
        private const val INPUT_DELAY_MS = 180L
        private const val SCROLL_DELAY_MS = 260L
        private const val DEFAULT_WAIT_DELAY_MS = 360L
        private const val GLOBAL_ACTION_DELAY_MS = 240L
        private const val MIN_CUSTOM_STEP_DELAY_MS = 60L
        private const val MAX_CUSTOM_STEP_DELAY_MS = 1_000L
        private const val MAX_RECENT_ACTIONS = 6
        private const val COMPLETE_CONFIDENCE_THRESHOLD = 0.72f
        private const val USER_TAKEOVER_POLL_MS = 120L

        private val CONTEXT_BREAKING_STEP_TYPES = setOf("home", "recents", "notifications", "quick_settings")
        private val FINANCIAL_GOAL_KEYWORDS = listOf("股票", "证券", "同花顺", "东方财富", "涨停", "跌停", "股", "基金", "交易", "委托", "账户")
        private val TRADING_ACTION_KEYWORDS = listOf("买", "卖", "下单", "委托", "价格", "股数", "100股", "涨停价", "买入", "卖出")
        private val TRADE_PROGRESS_KEYWORDS = listOf("交易", "买入", "卖出", "买按钮", "价格", "股数", "订单", "委托", "登录", "下单", "涨停", "账户", "验证码", "密码")
        private val ORDER_SUBMIT_KEYWORDS = listOf("确认下单", "提交订单", "提交委托", "确认买入", "确认卖出", "立即买入", "立即卖出", "委托买入", "委托卖出", "下单按钮", "买入委托", "卖出委托")
    }
}
