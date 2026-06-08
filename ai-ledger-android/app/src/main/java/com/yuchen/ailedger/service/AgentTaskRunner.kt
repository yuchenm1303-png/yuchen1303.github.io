package com.yuchen.ailedger.service

import android.content.Context
import android.os.SystemClock
import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

        AgentRuntimeController.startTask(goal)
        return try {
            runStartedTask(goal, modelPreference, logs)
        } catch (error: CancellationException) {
            AgentRuntimeController.stopTaskByUser("本次智能体任务已取消。")
            throw error
        } finally {
            AiAgentAccessibilityService.endTaskSession()
            AgentRuntimeController.resetCleanVisualCapture()
        }
    }

    private suspend fun runStartedTask(
        goal: String,
        modelPreference: ChatModel,
        logs: MutableList<AgentTaskStepLog>,
    ): AgentTaskRunResult {
        val stopGeneration = AgentRuntimeController.currentManualStopGeneration()
        val memory = AgentRunMemory()
        installedAppIndex?.let { index ->
            val preloadStart = SystemClock.elapsedRealtime()
            withContext(Dispatchers.IO) { index.getLaunchableApps(forceReload = false) }
            memory.recordTrace("应用索引 ${SystemClock.elapsedRealtime() - preloadStart}ms")
        }
        memory.recordTrace("Agent 开关已开启：Local/Shizuku 只作为执行工具，不抢任务完成权；本次任务统一进入智能体主循环。")

        while (true) {
            if (isStopped(stopGeneration)) return stoppedByUserResult(memory, logs)

            val loopStart = SystemClock.elapsedRealtime()
            val captureStart = SystemClock.elapsedRealtime()
            val observation = captureOnce(forceVisual = true)
            val captureMs = SystemClock.elapsedRealtime() - captureStart
            if (!observation.enabled || !observation.serviceConnected) {
                val message = "无障碍服务未开启"
                AgentRuntimeController.failTask(message)
                return AgentTaskRunResult(false, false, message, logs)
            }

            val snapshotStart = SystemClock.elapsedRealtime()
            val snapshot = observation.toAgentScreenSnapshot()
            val snapshotMs = SystemClock.elapsedRealtime() - snapshotStart
            memory.observe(snapshot)

            if (isStopped(stopGeneration)) return stoppedByUserResult(memory, logs)

            val deviceStart = SystemClock.elapsedRealtime()
            val deviceContext = buildDeviceContext(snapshot, goal)
            val deviceMs = SystemClock.elapsedRealtime() - deviceStart

            val plan = try {
                val cloudStart = SystemClock.elapsedRealtime()
                val result = withContext(Dispatchers.IO) {
                    aiWorkerClient.requestAgentPlan(
                        goal = goal,
                        snapshot = snapshot,
                        modelPreference = modelPreference,
                        recentActions = memory.recentActionSummaries(),
                        deviceContext = deviceContext,
                        agentMemory = memory.toJson(),
                    )
                }
                val cloudMs = SystemClock.elapsedRealtime() - cloudStart
                memory.recordTrace("第${memory.loopIndex + 1}轮：采集${captureMs}ms · 快照${snapshotMs}ms · 设备${deviceMs}ms · 云端${cloudMs}ms · 截图=${if (snapshot.hasVisualImage) "有" else "无"}")
                result
            } catch (error: IOException) {
                val totalMs = SystemClock.elapsedRealtime() - loopStart
                val message = "云端规划超时或失败：${error.message ?: "未知错误"}"
                memory.recordTrace("第${memory.loopIndex + 1}轮失败：采集${captureMs}ms · 快照${snapshotMs}ms · 设备${deviceMs}ms · 总${totalMs}ms")
                AgentRuntimeController.failTask(memory.withDebug(message))
                return AgentTaskRunResult(false, false, memory.withDebug(message), logs)
            }
            memory.loopIndex += 1

            val state = plan.state
            state?.let { memory.rememberState(it) }
            if (state != null && state.isComplete && state.confidence >= COMPLETE_CONFIDENCE_THRESHOLD) {
                val reason = state.reason.ifBlank { plan.step.reason ?: "云端状态判断任务已完成。" }
                val finishStep = CloudAgentStep(type = "finish", reason = reason, riskLevel = "low", requiresConfirmation = false)
                val done = AgentExecutionResult(true, reason, false)
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, finishStep, done)
                val message = memory.withDebug(reason)
                AgentRuntimeController.finishTask(message, completed = true)
                return AgentTaskRunResult(true, false, message, logs)
            }

            if (state != null && state.isWrong && state.confidence >= WRONG_CONFIDENCE_THRESHOLD && memory.backAttempts < MAX_BACK_ATTEMPTS) {
                val backStep = CloudAgentStep(
                    type = "back",
                    reason = state.reason.ifBlank { "云端状态判断当前页面偏离目标，返回上一层重新观察。" },
                    riskLevel = "low",
                    requiresConfirmation = false,
                )
                memory.prepareVerification(snapshot, backStep)
                val result = executeTimed(backStep, snapshot.currentApp, logs, memory)
                if (!result.ok) memory.cancelPendingVerification()
                memory.remember(backStep, result)
                delayForStep(backStep)
                continue
            }

            val steps = plan.executableSteps
                .filterNot { it.type == "finish" }
                .take(CloudAgentPlan.MAX_BATCH_STEPS)
                .ifEmpty { listOf(plan.step) }

            var executedAny = false
            for ((index, rawStep) in steps.withIndex()) {
                if (isStopped(stopGeneration)) return stoppedByUserResult(memory, logs)
                val chosenStep = chooseAction(snapshot, rawStep)
                if (chosenStep == null) {
                    memory.recordPlannerRejection(rawStep, "云端动作不可执行或缺少必要参数。")
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, rawStep, null)
                    continue
                }
                val blockReason = memory.repetitionBlockReason(chosenStep)
                if (blockReason != null) {
                    memory.recordPlannerRejection(chosenStep, blockReason)
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, chosenStep.copy(reason = blockReason), null)
                    continue
                }
                if (chosenStep.type == "need_user_help") {
                    val message = memory.withDebug(chosenStep.reason ?: "需要用户协助。")
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                val wasConfirmedByUser = if (AgentSafetyPolicy.requiresConfirmation(goal, chosenStep)) {
                    val confirmed = AgentRuntimeController.requestRiskConfirmation(goal, chosenStep)
                    if (!confirmed) return stoppedByUserResult(memory, logs)
                    true
                } else {
                    false
                }
                if (!wasConfirmedByUser && !AgentSafetyPolicy.canAutoExecuteInCurrentStage(goal, chosenStep)) {
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, chosenStep, null)
                    val message = memory.withDebug(chosenStep.reason ?: "当前动作暂不能自动执行：${chosenStep.typeLabel}")
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }

                memory.prepareVerification(snapshot, chosenStep)
                val result = executeTimed(chosenStep, snapshot.currentApp, logs, memory)
                if (!result.ok) memory.cancelPendingVerification()
                val isLastStep = index >= steps.lastIndex
                memory.remember(
                    step = chosenStep,
                    result = result,
                    forceVisualAfterSuccess = shouldForceVisualAfterBatchStep(chosenStep, result, isLastStep),
                )
                executedAny = true
                if (!result.ok) {
                    delay(REPLAN_DELAY_MS)
                    break
                }
                if (!result.shouldContinue) {
                    val message = memory.withDebug(result.message)
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                delayForStep(chosenStep)
                if (shouldStopBatchAfterStep(chosenStep, result, isLastStep)) break
            }

            if (!executedAny) delay(REPLAN_DELAY_MS)
        }
    }

    private fun isStopped(startGeneration: Long): Boolean {
        return AgentRuntimeController.currentManualStopGeneration() != startGeneration
    }

    private fun stoppedByUserResult(memory: AgentRunMemory, logs: List<AgentTaskStepLog>): AgentTaskRunResult {
        val message = memory.withDebug("用户已手动停止本次智能体任务。")
        return AgentTaskRunResult(false, false, message, logs)
    }

    private fun shouldForceVisualAfterBatchStep(step: CloudAgentStep, result: AgentExecutionResult, isLastStep: Boolean): Boolean {
        if (!result.ok || isLastStep) return true
        return step.type in BATCH_BREAK_AFTER_ACTION_TYPES
    }

    private fun shouldStopBatchAfterStep(step: CloudAgentStep, result: AgentExecutionResult, isLastStep: Boolean): Boolean {
        if (!result.ok || !result.shouldContinue) return true
        return isLastStep || step.type in BATCH_BREAK_AFTER_ACTION_TYPES
    }

    private fun buildDeviceContext(snapshot: AgentScreenSnapshot, goal: String): AgentDeviceContextSnapshot? {
        val context = applicationContext ?: return null
        val index = installedAppIndex ?: InstalledAppIndex(context)
        return runCatching { AgentDeviceContextProvider.build(context = context, screen = snapshot, goal = goal, installedAppIndex = index) }.getOrNull()
    }

    private suspend fun captureOnce(forceVisual: Boolean = false): ScreenObservation {
        return withContext(Dispatchers.Default) { AiAgentAccessibilityService.captureFreshSnapshot(forceVisual = forceVisual) }
    }

    private suspend fun executeTimed(step: CloudAgentStep, currentApp: String, logs: MutableList<AgentTaskStepLog>, memory: AgentRunMemory): AgentExecutionResult {
        val start = SystemClock.elapsedRealtime()
        val result = executeAndRecord(step, currentApp, logs)
        memory.recordTrace("执行 ${step.type} ${SystemClock.elapsedRealtime() - start}ms")
        return result
    }

    private suspend fun executeAndRecord(step: CloudAgentStep, currentApp: String, logs: MutableList<AgentTaskStepLog>): AgentExecutionResult {
        AgentRuntimeController.noteAction(step)
        // noteAction() 会先要求悬浮窗进入 clean 模式。这里必须用协程 delay 让出主线程，
        // 否则 AccessibilityService 在主线程里马上执行手势时，悬浮窗的隐藏 Flow 还没来得及被 UI 消费，
        // 就会出现“截图看不到浮窗，但真实点击打到浮窗”的竞态。
        delay(ACTION_OVERLAY_HIDE_STABILIZE_MS)
        val result = withContext(Dispatchers.Main) { AiAgentAccessibilityService.executeStep(step) }
        AgentRuntimeController.noteResult(step, result)
        logs += AgentTaskStepLog(logs.size + 1, currentApp, step, result)
        return result
    }

    private suspend fun delayForStep(step: CloudAgentStep) {
        val delayMs = step.durationMs?.coerceIn(MIN_CUSTOM_STEP_DELAY_MS, MAX_CUSTOM_STEP_DELAY_MS) ?: when (step.type) {
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

    private fun chooseAction(snapshot: AgentScreenSnapshot, cloudStep: CloudAgentStep): CloudAgentStep? {
        return sanitizeCloudStep(cloudStep, snapshot)
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
        return step
    }

    private data class AgentRunMemory(
        var backAttempts: Int = 0,
        var executedStepCount: Int = 0,
        var loopIndex: Int = 0,
        var sameScreenCount: Int = 0,
        var noProgressCount: Int = 0,
        private var lastScreenFingerprint: String = "",
        private var lastActionSignature: String = "",
        private var sameActionCount: Int = 0,
        private var pendingVerification: PendingVerification? = null,
        private val blockedActionSignatures: MutableSet<String> = mutableSetOf(),
        private val recentActionLines: MutableList<String> = mutableListOf(),
        private val failedActionLines: MutableList<String> = mutableListOf(),
        private val blockedActionLines: MutableList<String> = mutableListOf(),
        private val verificationLines: MutableList<String> = mutableListOf(),
        var lastDebugLine: String = "",
    ) {
        fun observe(snapshot: AgentScreenSnapshot) {
            val fingerprint = screenFingerprint(snapshot)
            val verificationResolved = resolvePendingVerification(snapshot, fingerprint)
            val sameAsLast = fingerprint == lastScreenFingerprint
            if (sameAsLast) {
                sameScreenCount += 1
                if (!verificationResolved && lastActionSignature.isNotBlank()) noProgressCount += 1
            } else {
                sameScreenCount = 0
                if (!verificationResolved && noProgressCount > 0) noProgressCount -= 1
            }
            lastScreenFingerprint = fingerprint
            val pendingText = pendingVerification?.signature ?: "无"
            lastDebugLine = "调试：阶段=云端观察 · app=${snapshot.currentApp.ifBlank { "未知" }} · 节点=${snapshot.nodeCount}/${snapshot.capturedNodeCount} · 全量=${snapshot.allNodes.size} · 点击=${snapshot.clickableNodes.size} · 输入=${snapshot.inputNodes.size} · 滚动=${snapshot.scrollableNodes.size} · 截图=${if (snapshot.hasVisualImage) "有" else "无"} · 步数=$executedStepCount · 同屏=$sameScreenCount · 无进展=$noProgressCount · 同动作=$sameActionCount · 待验=$pendingText"
        }

        fun rememberState(state: CloudAgentState) {
            if (state.reason.isBlank() && state.nextHint.isBlank()) return
            recentActionLines += "状态：complete=${state.isComplete}, progress=${state.expectedProgress}, wrong=${state.isWrong}, confidence=${"%.2f".format(state.confidence)} · ${state.reason.ifBlank { state.nextHint }}"
            trimHistory()
        }

        fun recordTrace(text: String) {
            val line = text.take(100)
            recentActionLines += "诊断：$line"
            AgentRuntimeController.noteDiagnostic(line)
            trimHistory()
        }

        fun recordPlannerRejection(step: CloudAgentStep, reason: String) {
            val line = "拒绝执行：${describeStep(step)} · $reason"
            blockedActionLines += line
            recentActionLines += line
            AgentRuntimeController.noteDiagnostic(reason.take(90))
            trimHistory()
        }

        fun prepareVerification(snapshot: AgentScreenSnapshot, step: CloudAgentStep) {
            val signature = actionSignature(step)
            if (signature.isBlank() || !step.requiresPostActionVerification()) {
                pendingVerification = null
                return
            }
            val beforeFingerprint = lastScreenFingerprint.ifBlank { screenFingerprint(snapshot) }
            pendingVerification = PendingVerification(
                signature = signature,
                beforeFingerprint = beforeFingerprint,
                beforeEvidenceText = screenEvidenceText(snapshot),
                expectedEvidence = expectedEvidenceFor(step),
                actionSummary = describeStep(step),
                createdAtLoop = loopIndex,
            )
        }

        fun cancelPendingVerification() {
            pendingVerification = null
        }

        fun repetitionBlockReason(step: CloudAgentStep): String? {
            val signature = actionSignature(step)
            if (signature.isBlank()) return null
            if (signature in blockedActionSignatures) {
                return "该动作本轮任务内已被判定为无进展：$signature。请重新观察，换一个入口、搜索、菜单、返回或其他策略。"
            }
            if (signature == lastActionSignature && sameActionCount >= MAX_CONSECUTIVE_SAME_ACTIONS && noProgressCount >= 1) {
                blockedActionSignatures += signature
                return "连续 $sameActionCount 次执行同一动作且画面没有可靠推进：$signature。拒绝继续重复点击，请换路线。"
            }
            if (noProgressCount >= NO_PROGRESS_SPECULATIVE_THRESHOLD && step.isSpeculativeExploration()) {
                blockedActionSignatures += signature
                return "连续 $noProgressCount 轮画面无明显推进，拒绝继续执行推测性探索动作：$signature。请优先使用明确搜索入口、菜单、返回上一层或请求用户协助。"
            }
            return null
        }

        fun withDebug(message: String): String = if (lastDebugLine.isBlank()) message else "$message\n$lastDebugLine"

        fun remember(step: CloudAgentStep, result: AgentExecutionResult, forceVisualAfterSuccess: Boolean = true) {
            executedStepCount += 1
            if (step.type == "back" && result.ok) backAttempts += 1
            val signature = actionSignature(step)
            if (signature.isNotBlank()) {
                if (signature == lastActionSignature) {
                    sameActionCount += 1
                } else {
                    lastActionSignature = signature
                    sameActionCount = 1
                }
            }
            val line = "动作：${describeStep(step)} → ${if (result.ok) "成功" else "失败"}：${result.message.take(100)}"
            recentActionLines += line
            if (!result.ok) failedActionLines += line
            trimHistory()
        }

        fun recentActionSummaries(): List<String> = recentActionLines.takeLast(8)

        fun toJson(): JSONObject = JSONObject().apply {
            put("schema", "agent_loop_memory_v12a_think_act_verify_runtime")
            put("recentActions", JSONArray().apply { recentActionLines.takeLast(8).forEach { put(it) } })
            put("failedActions", JSONArray().apply { failedActionLines.takeLast(6).forEach { put(it) } })
            put("blockedActions", JSONArray().apply { blockedActionLines.takeLast(6).forEach { put(it) } })
            put("verificationEvents", JSONArray().apply { verificationLines.takeLast(6).forEach { put(it) } })
            put("blockedActionSignatures", JSONArray().apply { blockedActionSignatures.take(12).forEach { put(it) } })
            put("pendingVerification", pendingVerification?.toJson() ?: JSONObject.NULL)
            put("loopSignals", JSONObject().apply {
                put("executedStepCount", executedStepCount)
                put("loopIndex", loopIndex)
                put("sameScreenCount", sameScreenCount)
                put("noProgressCount", noProgressCount)
                put("lastActionSignature", lastActionSignature)
                put("sameActionCount", sameActionCount)
                put("pendingVerificationSignature", pendingVerification?.signature ?: "")
            })
            put("policyHints", JSONArray().apply {
                put("Agent 开关开启时，手机任务统一走视觉主导智能体主循环。")
                put("Local/Shizuku/InstalledAppIndex 只能作为执行工具和设备上下文，不能绕过主循环直接宣布复合任务完成或失败。")
                put("点击手势提交成功不等于页面有效推进；必须等待下一轮截图验证是否变化或出现成功证据。")
                put("不要连续点击同一坐标或同一入口；如果 verificationEvents/blockedActions 提示某动作无进展，必须换路线。")
                put("同屏无进展时优先使用明确搜索入口、菜单、返回上一层或请求用户协助。")
                put("每轮最多执行一个会改变页面的动作，然后重新截图观察。")
            })
        }

        private fun resolvePendingVerification(snapshot: AgentScreenSnapshot, currentFingerprint: String): Boolean {
            val pending = pendingVerification ?: return false
            pendingVerification = null
            val currentEvidenceText = screenEvidenceText(snapshot)
            val evidenceHit = pending.expectedEvidence.any { evidence ->
                val normalized = normalizeEvidence(evidence)
                normalized.length >= 2 && currentEvidenceText.contains(normalized) && !pending.beforeEvidenceText.contains(normalized)
            }
            val changed = currentFingerprint != pending.beforeFingerprint
            val message = when {
                evidenceHit -> {
                    noProgressCount = 0
                    sameActionCount = 0
                    blockedActionSignatures.remove(pending.signature)
                    "验证通过：${pending.signature} 后出现新的目标证据。"
                }
                changed -> {
                    if (noProgressCount > 0) noProgressCount -= 1
                    "验证通过：${pending.signature} 后屏幕结构已变化，继续观察是否接近目标。"
                }
                else -> {
                    noProgressCount += 1
                    blockedActionSignatures += pending.signature
                    "验证失败：${pending.signature} 手势已提交但屏幕无可靠变化，已临时拉黑该动作。"
                }
            }
            verificationLines += message
            recentActionLines += "验证：$message"
            AgentRuntimeController.noteDiagnostic(message.take(90))
            trimHistory()
            return true
        }

        private fun trimHistory() {
            while (recentActionLines.size > 10) recentActionLines.removeAt(0)
            while (failedActionLines.size > 8) failedActionLines.removeAt(0)
            while (blockedActionLines.size > 8) blockedActionLines.removeAt(0)
            while (verificationLines.size > 8) verificationLines.removeAt(0)
        }

        private fun screenFingerprint(snapshot: AgentScreenSnapshot): String {
            val textKey = snapshot.allNodes
                .map { it.text.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(18)
                .joinToString("|")
                .replace(Regex("\\s+"), " ")
            return listOf(
                snapshot.currentApp,
                snapshot.allNodes.size.toString(),
                snapshot.clickableNodes.size.toString(),
                snapshot.inputNodes.size.toString(),
                snapshot.scrollableNodes.size.toString(),
                textKey,
            ).joinToString("#").take(600)
        }

        private fun screenEvidenceText(snapshot: AgentScreenSnapshot): String {
            val raw = (snapshot.allNodes.map { it.text } + snapshot.clickableNodes.map { it.text } + snapshot.inputNodes.map { it.text })
                .joinToString(" ")
            return normalizeEvidence(raw)
        }

        private fun normalizeEvidence(value: String?): String {
            return value.orEmpty()
                .lowercase()
                .replace(Regex("[\\s\\u3000，。,.、:：/\\\\_\\-]+"), "")
                .take(800)
        }

        private fun expectedEvidenceFor(step: CloudAgentStep): List<String> {
            val rawParts = listOfNotNull(
                step.targetText,
                if (step.type == "open_app") step.appName else null,
                if (step.type == "input_text") step.text else null,
                step.reason,
            )
            val generic = setOf(
                "点击", "屏幕", "页面", "入口", "按钮", "当前", "目标", "打开", "进入", "返回", "查看", "可能", "尝试", "寻找", "相关", "坐标", "归一化",
                "click", "tap", "screen", "page", "button", "target", "open", "try", "possible"
            )
            return rawParts
                .flatMap { it.split(Regex("[\\s，。,.、:：/\\\\_\\-]+")) }
                .map { it.trim() }
                .filter { it.length in 2..24 }
                .filterNot { normalizeEvidence(it) in generic }
                .distinctBy { normalizeEvidence(it) }
                .take(8)
        }

        private fun actionSignature(step: CloudAgentStep): String {
            return when (step.type) {
                "tap_xy" -> {
                    val x = step.x ?: return ""
                    val y = step.y ?: return ""
                    val qx = (x * 50f).toInt()
                    val qy = (y * 50f).toInt()
                    "tap@$qx,$qy"
                }
                "tap_node" -> "tap_node@${step.targetNodeId ?: step.targetText.orEmpty().take(24)}"
                "open_app" -> "open@${step.packageName ?: step.appName.orEmpty().take(24)}"
                "input_text" -> "input@${step.text.orEmpty().take(24)}"
                "scroll", "swipe" -> "${step.type}@${step.direction.orEmpty().lowercase()}"
                "back", "home", "recents", "notifications", "quick_settings" -> step.type
                else -> ""
            }
        }

        private fun describeStep(step: CloudAgentStep): String = buildString {
            append(step.type)
            step.appName?.let { append(" · app=").append(it) }
            step.packageName?.let { append(" · pkg=").append(it) }
            step.targetText?.let { append(" · target=").append(it) }
            step.direction?.let { append(" · ").append(it) }
            step.x?.let { append(" · x=").append("%.3f".format(it)) }
            step.y?.let { append(" y=").append("%.3f".format(it)) }
        }

        private fun CloudAgentStep.requiresPostActionVerification(): Boolean {
            return type in POST_ACTION_VERIFY_TYPES
        }

        private fun CloudAgentStep.isSpeculativeExploration(): Boolean {
            if (type !in setOf("tap_xy", "tap_node", "scroll", "swipe")) return false
            val text = listOfNotNull(reason, targetText, text, direction)
                .joinToString(" ")
                .lowercase()
            return listOf("可能", "探索", "尝试", "试试", "寻找", "相关", "猜测", "也许", "maybe", "try", "explore", "possible")
                .any { text.contains(it) }
        }

        private data class PendingVerification(
            val signature: String,
            val beforeFingerprint: String,
            val beforeEvidenceText: String,
            val expectedEvidence: List<String>,
            val actionSummary: String,
            val createdAtLoop: Int,
        ) {
            fun toJson(): JSONObject = JSONObject().apply {
                put("signature", signature)
                put("actionSummary", actionSummary)
                put("expectedEvidence", JSONArray().apply { expectedEvidence.forEach { put(it) } })
                put("createdAtLoop", createdAtLoop)
            }
        }

        private companion object {
            private const val MAX_CONSECUTIVE_SAME_ACTIONS = 2
            private const val NO_PROGRESS_SPECULATIVE_THRESHOLD = 3
            private val POST_ACTION_VERIFY_TYPES = setOf(
                "open_app", "tap_node", "tap_xy", "input_text", "scroll", "swipe", "back", "home", "recents", "notifications", "quick_settings"
            )
        }
    }

    companion object {
        private const val DEFAULT_STEP_DELAY_MS = 280L
        private const val REPLAN_DELAY_MS = 140L
        private const val ACTION_OVERLAY_HIDE_STABILIZE_MS = 260L
        private const val OPEN_APP_DELAY_MS = 640L
        private const val TAP_DELAY_MS = 220L
        private const val INPUT_DELAY_MS = 180L
        private const val SCROLL_DELAY_MS = 260L
        private const val DEFAULT_WAIT_DELAY_MS = 360L
        private const val GLOBAL_ACTION_DELAY_MS = 240L
        private const val MIN_CUSTOM_STEP_DELAY_MS = 60L
        private const val MAX_CUSTOM_STEP_DELAY_MS = 1_000L
        private const val MAX_BACK_ATTEMPTS = 2
        private const val COMPLETE_CONFIDENCE_THRESHOLD = 0.72f
        private const val WRONG_CONFIDENCE_THRESHOLD = 0.78f
        private val BATCH_BREAK_AFTER_ACTION_TYPES = setOf(
            "open_app", "tap_node", "tap_xy", "input_text", "scroll", "swipe", "back", "home", "recents", "notifications", "quick_settings"
        )
    }
}
