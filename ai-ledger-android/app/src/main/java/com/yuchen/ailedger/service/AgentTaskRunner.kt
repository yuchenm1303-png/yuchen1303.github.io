package com.yuchen.ailedger.service

import android.content.Context
import android.os.SystemClock
import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
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

    suspend fun run(
        goal: String,
        modelPreference: ChatModel,
        maxSteps: Int = DEFAULT_MAX_STEPS,
    ): AgentTaskRunResult {
        val logs = mutableListOf<AgentTaskStepLog>()
        if (!AgentRuntimeController.isEnabled()) {
            val message = "智能体已关闭，请先打开 Agent 开关。"
            AgentRuntimeController.finishTask(message, completed = false)
            return AgentTaskRunResult(false, false, message, logs)
        }

        AgentRuntimeController.startTask(goal)
        val memory = AgentRunMemory()
        installedAppIndex?.let { index ->
            val preloadStart = SystemClock.elapsedRealtime()
            withContext(Dispatchers.IO) { index.getLaunchableApps(forceReload = false) }
            memory.recordTrace("应用索引 ${SystemClock.elapsedRealtime() - preloadStart}ms")
        }

        tryLocalAppLaunchBootstrap(goal, logs, memory)?.let { return it }

        repeat(maxSteps.coerceAtMost(DEFAULT_MAX_STEPS)) {
            val loopStart = SystemClock.elapsedRealtime()
            val decision = chooseObservationDecision(goal, memory)

            var captureStart = SystemClock.elapsedRealtime()
            var observation = captureOnce(forceVisual = decision.forceVisual)
            var captureMs = SystemClock.elapsedRealtime() - captureStart
            if (!observation.enabled || !observation.serviceConnected) {
                val message = "无障碍服务未开启"
                AgentRuntimeController.failTask(message)
                return AgentTaskRunResult(false, false, message, logs)
            }

            var snapshotStart = SystemClock.elapsedRealtime()
            var snapshot = observation.toAgentScreenSnapshot()
            var snapshotMs = SystemClock.elapsedRealtime() - snapshotStart
            memory.observe(snapshot, decision)

            if (!snapshot.hasVisualImage && shouldPromoteToVisualNow(goal, snapshot, decision)) {
                val promoted = ObservationDecision.Visual("轻量观察信息不足，本轮立即补充截图")
                captureStart = SystemClock.elapsedRealtime()
                observation = captureOnce(forceVisual = true)
                captureMs += SystemClock.elapsedRealtime() - captureStart
                if (!observation.enabled || !observation.serviceConnected) {
                    val message = "无障碍服务未开启"
                    AgentRuntimeController.failTask(message)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                snapshotStart = SystemClock.elapsedRealtime()
                snapshot = observation.toAgentScreenSnapshot()
                snapshotMs += SystemClock.elapsedRealtime() - snapshotStart
                memory.observe(snapshot, promoted)
            }

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

            if (state != null && state.isWrong && state.confidence >= WRONG_CONFIDENCE_THRESHOLD) {
                if (memory.backAttempts >= MAX_BACK_ATTEMPTS) {
                    val message = memory.withDebug(state.reason.ifBlank { "云端判断当前页面偏离目标，但已达到最大返回次数。" })
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                val backStep = CloudAgentStep(
                    type = "back",
                    reason = state.reason.ifBlank { "云端状态判断当前页面偏离目标，返回上一层重新观察。" },
                    riskLevel = "low",
                    requiresConfirmation = false,
                )
                val result = executeTimed(backStep, snapshot.currentApp, logs, memory)
                memory.remember(backStep, result)
                if (!result.ok || !result.shouldContinue) {
                    val message = memory.withDebug(result.message)
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                delayForStep(backStep)
                return@repeat
            }

            if (plan.step.type == "finish") {
                memory.rejectedFinishAttempts += 1
                memory.forceNextVisual = true
                if (memory.rejectedFinishAttempts >= MAX_REJECTED_FINISH_ATTEMPTS) {
                    val reason = state?.reason?.takeIf { it.isNotBlank() }
                        ?: "云端返回完成动作，但状态置信度不足，已暂停避免过早结束。"
                    val message = memory.withDebug(reason)
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                memory.recordPlannerRejection(plan.step, "finish 状态置信度不足，下一轮强制视觉复核。")
                delay(REPLAN_DELAY_MS)
                return@repeat
            }

            val chosenStep = chooseAction(snapshot, plan.step, memory)
            if (chosenStep == null) {
                memory.recordPlannerRejection(plan.step, "云端动作不可执行、缺少参数或与近期失败动作重复。")
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, plan.step, null)
                if (memory.repeatedCloudRejects <= MAX_REPLAN_REJECTS) {
                    memory.forceNextVisual = true
                    delay(REPLAN_DELAY_MS)
                    return@repeat
                }
                val message = memory.withDebug(plan.step.reason ?: state?.nextHint ?: "云端连续返回不可执行或重复动作")
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, false, message, logs)
            }

            val wasConfirmedByUser = if (AgentSafetyPolicy.requiresConfirmation(goal, chosenStep)) {
                val confirmed = AgentRuntimeController.requestRiskConfirmation(goal, chosenStep)
                if (!confirmed) {
                    logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, chosenStep, null)
                    val message = memory.withDebug("用户已取消需要确认的动作：${chosenStep.typeLabel}")
                    return AgentTaskRunResult(false, true, message, logs)
                }
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

            val result = executeTimed(chosenStep, snapshot.currentApp, logs, memory)
            memory.remember(chosenStep, result)
            if (!result.ok) {
                if (memory.shouldReplanAfterFailure(chosenStep)) {
                    memory.forceNextVisual = true
                    delay(REPLAN_DELAY_MS)
                    return@repeat
                }
                val message = memory.withDebug(result.message)
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, false, message, logs)
            }
            if (!result.shouldContinue) {
                val message = memory.withDebug(result.message)
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, false, message, logs)
            }

            delayForStep(chosenStep)
        }

        val message = memory.withDebug("已达到最大执行步数，请检查当前页面后继续。")
        AgentRuntimeController.finishTask(message, completed = false)
        return AgentTaskRunResult(false, false, message, logs)
    }

    private suspend fun tryLocalAppLaunchBootstrap(goal: String, logs: MutableList<AgentTaskStepLog>, memory: AgentRunMemory): AgentTaskRunResult? {
        val index = installedAppIndex ?: return null
        val candidates = withContext(Dispatchers.IO) { index.findCandidateApps(goal, limit = 3) }
        val best = candidates.firstOrNull() ?: return null
        if (!isAppMentionedInGoal(goal, best, index)) return null
        val pureOpenGoal = isPureOpenAppGoal(goal, best, index)
        val step = CloudAgentStep(
            type = "open_app",
            appName = best.label,
            packageName = best.packageName,
            reason = if (pureOpenGoal) "本机应用索引命中，纯打开 App 任务跳过云端视觉循环。" else "本机应用索引命中，先打开目标 App，再进入视觉主导页面任务。",
            riskLevel = "low",
            requiresConfirmation = false,
        )
        AgentRuntimeController.noteDiagnostic("本地启动目标应用：${best.label}")
        val result = executeTimed(step, "local_app_bootstrap", logs, memory)
        memory.remember(step, result)
        if (!result.ok) {
            memory.recordTrace("本地启动目标应用失败，转入视觉主导云端循环：${result.message}")
            memory.forceNextVisual = true
            return null
        }
        delayForStep(step)
        if (!pureOpenGoal) {
            memory.recordTrace("已打开 ${best.label}，继续用视觉主导查找 App 内目标。")
            memory.forceNextVisual = true
            return null
        }
        val message = memory.withDebug("已打开 ${best.label}。")
        AgentRuntimeController.finishTask(message, completed = true)
        return AgentTaskRunResult(true, false, message, logs)
    }

    private fun isAppMentionedInGoal(goal: String, app: InstalledAppEntry, index: InstalledAppIndex): Boolean {
        val clean = normalize(goal)
        val aliases = index.aliasesFor(app).map(::normalize).filter { it.length >= 2 }.distinct()
        return aliases.any { clean.contains(it) }
    }

    private fun isPureOpenAppGoal(goal: String, app: InstalledAppEntry, index: InstalledAppIndex): Boolean {
        val clean = normalize(goal)
        if (!isToolOnlyLaunchGoal(clean)) return false
        val aliases = index.aliasesFor(app).map(::normalize).filter { it.length >= 2 }.distinct()
        if (aliases.none { clean.contains(it) }) return false
        var residual = clean
        pureOpenWords.forEach { residual = residual.replace(it, "") }
        aliases.forEach { residual = residual.replace(it, "") }
        residual = residual.replace(Regex("[请帮我把一下这个软件应用app]+"), "")
        return residual.isBlank()
    }

    private fun chooseObservationDecision(goal: String, memory: AgentRunMemory): ObservationDecision {
        val clean = normalize(goal)
        return when {
            memory.forceNextVisual -> ObservationDecision.Visual("动作后按 Computer Use 闭环截图复核")
            memory.executedStepCount == 0 && isToolOnlyLaunchGoal(clean) -> ObservationDecision.Lightweight("纯打开 App 首轮只用 deviceContext/open_app")
            else -> ObservationDecision.Visual("视觉主导观察，节点仅作为辅助 affordance")
        }
    }

    private fun shouldPromoteToVisualNow(goal: String, snapshot: AgentScreenSnapshot, decision: ObservationDecision): Boolean {
        if (decision.forceVisual || snapshot.hasVisualImage) return false
        if (isToolOnlyLaunchGoal(normalize(goal))) return false
        return snapshot.isSparseForComputerUse()
    }

    private fun isToolOnlyLaunchGoal(cleanGoal: String): Boolean {
        if (pureOpenWords.none { cleanGoal.startsWith(it) || cleanGoal.contains(it) }) return false
        if (complexGoalWords.any { cleanGoal.contains(it) }) return false
        if (currentScreenWords.any { cleanGoal.contains(it) }) return false
        if (highRiskGoalWords.any { cleanGoal.contains(it) }) return false
        return true
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

    private fun chooseAction(snapshot: AgentScreenSnapshot, cloudStep: CloudAgentStep, memory: AgentRunMemory): CloudAgentStep? {
        val candidate = sanitizeCloudStep(cloudStep, snapshot) ?: return null
        if (memory.isBlocked(candidate)) return null
        return candidate
    }

    private fun sanitizeCloudStep(step: CloudAgentStep, snapshot: AgentScreenSnapshot): CloudAgentStep? {
        if (step.type == "need_user_help") return null
        if (step.type !in CloudAgentStep.supportedTypes) return null
        if (step.type == "tap_xy") {
            val x = step.x ?: return null
            val y = step.y ?: return null
            if (x !in 0f..1f || y !in 0f..1f) return null
        }
        if (step.type == "tap_node" && step.targetNodeId.isNullOrBlank() && step.targetText.isNullOrBlank()) return null
        if (step.type == "input_text" && step.text.isNullOrBlank()) return null
        if (step.type == "open_app" && step.packageName != null && step.packageName == snapshot.currentApp) return null
        if (step.type == "wait" && snapshot.hasVisualImage && !isLoadingWaitReason(step.reason.orEmpty())) return null
        return step
    }

    private fun isLoadingWaitReason(reason: String): Boolean {
        val clean = normalize(reason)
        return loadingWaitWords.any { clean.contains(normalize(it)) }
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[\\s\u3000，。,.、:：/\\-]+"), "")

    private sealed class ObservationDecision(
        val forceVisual: Boolean,
        val label: String,
        val reason: String,
    ) {
        class Visual(reason: String) : ObservationDecision(true, "视觉", reason)
        class Lightweight(reason: String) : ObservationDecision(false, "轻量", reason)
    }

    private data class AgentRunMemory(
        var phase: AgentTaskPhase = AgentTaskPhase.Verifying,
        var backAttempts: Int = 0,
        var repeatedCloudRejects: Int = 0,
        var rejectedFinishAttempts: Int = 0,
        var recoverableFailures: Int = 0,
        var executedStepCount: Int = 0,
        var loopIndex: Int = 0,
        var forceNextVisual: Boolean = false,
        var lightRoundsSinceVisual: Int = 0,
        private val recentStepKeys: MutableList<String> = mutableListOf(),
        private val recentActionLines: MutableList<String> = mutableListOf(),
        private val failedActionLines: MutableList<String> = mutableListOf(),
        private val blockedActionLines: MutableList<String> = mutableListOf(),
        var lastDebugLine: String = "",
        var lastObservationLine: String = "观察=视觉 · 初始化",
    ) {
        fun observe(snapshot: AgentScreenSnapshot, decision: ObservationDecision) {
            if (snapshot.hasVisualImage) {
                forceNextVisual = false
                lightRoundsSinceVisual = 0
            } else {
                lightRoundsSinceVisual += 1
            }
            lastObservationLine = "观察=${decision.label} · ${decision.reason}"
            lastDebugLine = "调试：阶段=${phase.label} · app=${snapshot.currentApp.ifBlank { "未知" }} · 节点=${snapshot.nodeCount}/${snapshot.capturedNodeCount} · 全量=${snapshot.allNodes.size} · 点击=${snapshot.clickableNodes.size} · 输入=${snapshot.inputNodes.size} · 滚动=${snapshot.scrollableNodes.size} · 截图=${if (snapshot.hasVisualImage) "有" else "无"} · $lastObservationLine · 步数=$executedStepCount"
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
            repeatedCloudRejects += 1
            forceNextVisual = true
            val line = "拒绝执行：${describeStep(step)} · $reason"
            blockedActionLines += line
            recentActionLines += line
            trimHistory()
        }

        fun withDebug(message: String): String = if (lastDebugLine.isBlank()) message else "$message\n$lastDebugLine"

        fun remember(step: CloudAgentStep, result: AgentExecutionResult) {
            val key = stepKey(step)
            recentStepKeys += key
            executedStepCount += 1
            forceNextVisual = shouldForceVisualAfterStep(step, result)
            val line = "动作：${describeStep(step)} → ${if (result.ok) "成功" else "失败"}：${result.message.take(100)}"
            recentActionLines += line
            if (!result.ok) failedActionLines += line
            trimHistory()
            if (recentStepKeys.size > 10) recentStepKeys.removeAt(0)
            if (step.type == "back") {
                backAttempts += 1
                if (result.ok) {
                    recentStepKeys.clear()
                    repeatedCloudRejects = 0
                    rejectedFinishAttempts = 0
                    phase = AgentTaskPhase.Verifying
                }
            }
            if (result.ok && step.type != "finish") {
                repeatedCloudRejects = 0
                rejectedFinishAttempts = 0
                recoverableFailures = 0
            }
            if (!result.ok) repeatedCloudRejects += 1
        }

        fun shouldReplanAfterFailure(step: CloudAgentStep): Boolean {
            if (step.type !in RECOVERABLE_FAILURE_ACTIONS) return false
            recoverableFailures += 1
            forceNextVisual = true
            return recoverableFailures <= MAX_RECOVERABLE_FAILURES
        }

        fun recentActionSummaries(): List<String> = recentActionLines.takeLast(8)

        fun isLikelyRepeated(step: CloudAgentStep): Boolean {
            val count = recentStepKeys.count { it == stepKey(step) }
            return count >= if (step.type in SOFT_REPEATABLE_ACTIONS) 2 else 1
        }

        fun isBlocked(step: CloudAgentStep): Boolean {
            if (!isLikelyRepeated(step)) return false
            if (step.type in SOFT_REPEATABLE_ACTIONS && repeatedCloudRejects == 0 && recoverableFailures == 0) return false
            return true
        }

        fun toJson(): JSONObject = JSONObject().apply {
            put("schema", "agent_loop_memory_v7_visual_first_optimized")
            put("recentActions", JSONArray().apply { recentActionLines.takeLast(8).forEach { put(it) } })
            put("failedActions", JSONArray().apply { failedActionLines.takeLast(6).forEach { put(it) } })
            put("blockedActions", JSONArray().apply { blockedActionLines.takeLast(6).forEach { put(it) } })
            put("loopSignals", JSONObject().apply {
                put("executedStepCount", executedStepCount)
                put("loopIndex", loopIndex)
                put("repeatedCloudRejects", repeatedCloudRejects)
                put("recoverableFailures", recoverableFailures)
                put("backAttempts", backAttempts)
                put("rejectedFinishAttempts", rejectedFinishAttempts)
                put("forceNextVisual", forceNextVisual)
                put("lightRoundsSinceVisual", lightRoundsSinceVisual)
                put("lastObservation", lastObservationLine)
            })
            put("policyHints", JSONArray().apply {
                put("这是视觉主导 Computer Use 循环：除纯 open_app 工具任务外，应以截图为主判断当前状态。")
                put("Accessibility 节点只作为可点击/可输入/可滚动 affordance 提示，不代表页面已完成。")
                put("如果任务包含本机目标 App 名，Android 会先本地 open_app，再进入 App 内视觉观察。")
                put("视觉 tap_xy 坐标会原样执行，只做归一化换算和边界保护，不再进行本地底部导航吸附。")
                put("每次执行 open_app、tap、input、scroll、swipe、back、home、wait 后，下一轮应通过截图复核页面状态。")
                put("不要重复 blockedActions 或 failedActions 中的同一路径。")
                put("如果 open_app 失败，必须改用 deviceContext.targetAppCandidates 或 installedApps 中真实存在的 appName/packageName，或返回 need_user_help。")
            })
        }

        private fun shouldForceVisualAfterStep(step: CloudAgentStep, result: AgentExecutionResult): Boolean {
            if (!result.ok) return true
            return step.type in VISUAL_AFTER_ACTION_TYPES
        }

        private fun trimHistory() {
            while (recentActionLines.size > 10) recentActionLines.removeAt(0)
            while (failedActionLines.size > 8) failedActionLines.removeAt(0)
            while (blockedActionLines.size > 8) blockedActionLines.removeAt(0)
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

        private fun stepKey(step: CloudAgentStep): String = listOf(
            step.type,
            step.targetNodeId,
            step.targetText,
            step.text,
            step.direction,
            step.x?.let { "%.4f".format(it) },
            step.y?.let { "%.4f".format(it) },
            step.appName,
            step.packageName,
        ).joinToString("|")
    }

    private enum class AgentTaskPhase(val label: String) {
        Verifying("云端观察"),
    }

    private fun AgentScreenSnapshot.isSparseForComputerUse(): Boolean {
        if (nodeCount <= SPARSE_NODE_VISUAL_THRESHOLD) return true
        if (allNodes.size <= SPARSE_CAPTURED_NODE_THRESHOLD) return true
        if (clickableNodes.isEmpty() && inputNodes.isEmpty() && scrollableNodes.isEmpty()) return true
        return false
    }

    companion object {
        private const val DEFAULT_MAX_STEPS = 8
        private const val DEFAULT_STEP_DELAY_MS = 360L
        private const val REPLAN_DELAY_MS = 180L
        private const val OPEN_APP_DELAY_MS = 720L
        private const val TAP_DELAY_MS = 240L
        private const val INPUT_DELAY_MS = 220L
        private const val SCROLL_DELAY_MS = 360L
        private const val DEFAULT_WAIT_DELAY_MS = 460L
        private const val GLOBAL_ACTION_DELAY_MS = 300L
        private const val MIN_CUSTOM_STEP_DELAY_MS = 80L
        private const val MAX_CUSTOM_STEP_DELAY_MS = 1_200L
        private const val MAX_BACK_ATTEMPTS = 2
        private const val MAX_REJECTED_FINISH_ATTEMPTS = 2
        private const val MAX_REPLAN_REJECTS = 2
        private const val MAX_RECOVERABLE_FAILURES = 2
        private const val COMPLETE_CONFIDENCE_THRESHOLD = 0.72f
        private const val WRONG_CONFIDENCE_THRESHOLD = 0.78f
        private const val SPARSE_NODE_VISUAL_THRESHOLD = 12
        private const val SPARSE_CAPTURED_NODE_THRESHOLD = 8
        private val RECOVERABLE_FAILURE_ACTIONS = setOf("open_app", "tap_node", "tap_xy", "scroll", "swipe", "wait")
        private val SOFT_REPEATABLE_ACTIONS = setOf("scroll", "swipe", "wait")
        private val VISUAL_AFTER_ACTION_TYPES = setOf("open_app", "tap_node", "tap_xy", "input_text", "scroll", "swipe", "wait", "back", "home", "recents", "notifications", "quick_settings")
        private val loadingWaitWords = listOf("加载", "正在", "等待", "过渡", "动画", "空白", "刷新", "刚变化", "loading", "blank", "transition", "wait")
        private val pureOpenWords = listOf("打开", "开启", "启动", "进入")
        private val currentScreenWords = listOf("当前", "屏幕", "页面", "界面", "这个", "这里", "看一下", "看看", "点击", "输入", "滑动", "滚动", "按钮")
        private val highRiskGoalWords = listOf("支付", "付款", "转账", "红包", "下单", "购买", "删除", "卸载", "授权", "同意", "发送", "发给", "提交", "发布", "评论", "私信", "验证码", "密码", "登录", "pay", "transfer", "delete", "send", "submit", "publish", "password", "login", "otp")
        private val complexGoalWords = listOf("热榜", "联系人", "通讯录", "朋友圈", "发现", "动态", "搜索", "找到", "页面", "界面", "点击", "发送", "发布", "删除", "设置", "消息", "扫一扫", "视频", "直播", "自选", "行情", "新闻", "小程序", "群", "聊天", "给", "评论", "登录", "支付", "转账")
    }
}
