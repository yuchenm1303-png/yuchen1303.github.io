package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    private val installedAppIndex: InstalledAppIndex? = null,
) {
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
        val targetApp = detectTargetApp(goal, installedAppIndex)
        val memory = AgentRunMemory(goal = goal, targetApp = targetApp)

        repeat(maxSteps.coerceAtMost(DEFAULT_MAX_STEPS)) {
            val lightPreflightOnly = memory.shouldUseLightPreflightCapture()
            val observation = captureOnce(forceVisual = !lightPreflightOnly)
            if (!observation.enabled || !observation.serviceConnected) {
                val message = "无障碍服务未开启"
                AgentRuntimeController.failTask(message)
                return AgentTaskRunResult(false, false, message, logs)
            }

            var snapshot = observation.toAgentScreenSnapshot()
            var context = buildGoalContext(goal, targetApp)
            memory.observe(snapshot, context)

            detectMechanicalCompletion(snapshot, context)?.let { completion ->
                val finishStep = CloudAgentStep(type = "finish", reason = completion, riskLevel = "low", requiresConfirmation = false)
                val done = AgentExecutionResult(true, completion, false)
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, finishStep, done)
                val message = memory.withDebug(completion)
                AgentRuntimeController.finishTask(message, completed = true)
                return AgentTaskRunResult(true, false, message, logs)
            }

            val preflight = buildPreflightStep(memory, snapshot)
            if (preflight != null) {
                memory.phase = AgentTaskPhase.OpeningApp
                val result = executeAndRecord(preflight, snapshot.currentApp, logs)
                memory.remember(preflight, result)
                if (!result.ok || !result.shouldContinue) {
                    val message = memory.withDebug(result.message)
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                delayForStep(preflight)
                return@repeat
            }

            if (!snapshot.hasVisualImage) {
                val visualObservation = captureOnce(forceVisual = true)
                if (visualObservation.enabled && visualObservation.serviceConnected) {
                    snapshot = visualObservation.toAgentScreenSnapshot()
                    context = buildGoalContext(goal, targetApp)
                    memory.observe(snapshot, context)
                }
            }

            val plan = withContext(Dispatchers.IO) {
                aiWorkerClient.requestAgentPlan(
                    goal = goal,
                    snapshot = snapshot,
                    modelPreference = modelPreference,
                    recentActions = memory.recentActionSummaries(),
                )
            }
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
                val result = executeAndRecord(backStep, snapshot.currentApp, logs)
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
                if (memory.rejectedFinishAttempts >= MAX_REJECTED_FINISH_ATTEMPTS) {
                    val reason = state?.reason?.takeIf { it.isNotBlank() }
                        ?: "云端返回完成动作，但状态置信度不足，已暂停避免过早结束。"
                    val message = memory.withDebug(reason)
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                delay(DEFAULT_STEP_DELAY_MS)
                return@repeat
            }

            val chosenStep = chooseAction(snapshot, plan.step, memory)
            if (chosenStep == null) {
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, plan.step, null)
                val message = memory.withDebug(plan.step.reason ?: state?.nextHint ?: "当前屏幕没有足够线索继续推进")
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, false, message, logs)
            }

            if (AgentSafetyPolicy.requiresConfirmation(goal, chosenStep)) {
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, chosenStep, null)
                val message = memory.withDebug("动作需要确认：${chosenStep.typeLabel}")
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, true, message, logs)
            }
            if (!AgentSafetyPolicy.canAutoExecuteInCurrentStage(goal, chosenStep)) {
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, chosenStep, null)
                val message = memory.withDebug(chosenStep.reason ?: "当前动作暂不能自动执行：${chosenStep.typeLabel}")
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, false, message, logs)
            }

            val result = executeAndRecord(chosenStep, snapshot.currentApp, logs)
            memory.remember(chosenStep, result)
            if (!result.ok || !result.shouldContinue) {
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

    private suspend fun captureOnce(forceVisual: Boolean = false): ScreenObservation {
        return withContext(Dispatchers.Default) { AiAgentAccessibilityService.captureFreshSnapshot(forceVisual = forceVisual) }
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

    private fun buildPreflightStep(memory: AgentRunMemory, snapshot: AgentScreenSnapshot): CloudAgentStep? {
        val app = memory.targetApp ?: return null
        if (snapshot.currentApp == app.packageName) return null
        if (memory.openAppAttempts >= MAX_OPEN_APP_ATTEMPTS) return null
        memory.openAppAttempts += 1
        return CloudAgentStep(
            type = "open_app",
            appName = app.label,
            packageName = app.packageName,
            reason = "本机已安装应用索引命中目标应用，先直接打开目标应用。",
            riskLevel = "low",
            requiresConfirmation = false,
        )
    }

    private fun detectMechanicalCompletion(snapshot: AgentScreenSnapshot, context: GoalContext): String? {
        val app = context.targetApp ?: return null
        if (snapshot.currentApp != app.packageName) return null
        return if (context.isPureOpenAppGoal) "目标应用已打开。" else null
    }

    private fun chooseAction(snapshot: AgentScreenSnapshot, cloudStep: CloudAgentStep, memory: AgentRunMemory): CloudAgentStep? {
        sanitizeCloudStep(cloudStep, snapshot)?.let { candidate ->
            if (!memory.isBlocked(candidate)) return candidate
        }
        if (cloudStep.type == "need_user_help" || memory.isLikelyRepeated(cloudStep)) memory.repeatedCloudRejects += 1
        return null
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

    private fun buildGoalContext(goal: String, targetApp: TargetApp?): GoalContext {
        val keywords = extractGoalKeywords(goal, targetApp)
        val cleanGoal = normalize(goal)
        val wantsSearch = searchIntentWords.any { cleanGoal.contains(normalize(it)) }
        return GoalContext(
            goal = goal,
            targetApp = targetApp,
            keywords = keywords,
            wantsSearch = wantsSearch,
            isPureOpenAppGoal = targetApp != null && keywords.isEmpty(),
        )
    }

    private fun extractGoalKeywords(goal: String, targetApp: TargetApp?): List<String> {
        var text = goal.trim()
        targetApp?.aliases.orEmpty().forEach { text = text.replace(it, "", ignoreCase = true) }
        fillerWords.forEach { text = text.replace(it, "", ignoreCase = true) }
        val compact = text.replace(Regex("[，。,.、\\s]+"), "").take(24)
        val tokens = Regex("[\\p{L}\\p{N}]{2,}").findAll(text).map { it.value.take(24) }.toList()
        return (listOf(compact) + tokens).filter { it.isNotBlank() }.distinct().take(4)
    }

    private fun isLoadingWaitReason(reason: String): Boolean {
        val clean = normalize(reason)
        return loadingWaitWords.any { clean.contains(normalize(it)) }
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[\\s\u3000，。,.、:：/\\-]+"), "")

    private fun detectTargetApp(goal: String, appIndex: InstalledAppIndex?): TargetApp? {
        appIndex?.findBestApp(goal)?.let { app ->
            return TargetApp(
                label = app.label,
                packageName = app.packageName,
                aliases = appIndex.aliasesFor(app),
            )
        }
        val clean = normalize(goal)
        return TARGET_APPS.firstOrNull { item -> item.aliases.any { alias -> clean.contains(normalize(alias)) } }
    }

    private enum class AgentTaskPhase(val label: String) {
        OpeningApp("打开应用"),
        Navigating("找入口"),
        Searching("搜索输入"),
        Verifying("确认结果"),
        Recovering("恢复路径"),
    }

    private data class GoalContext(
        val goal: String,
        val targetApp: TargetApp?,
        val keywords: List<String>,
        val wantsSearch: Boolean,
        val isPureOpenAppGoal: Boolean,
    )

    private data class TargetApp(val label: String, val packageName: String, val aliases: List<String>)

    private data class AgentRunMemory(
        val goal: String,
        val targetApp: TargetApp?,
        var phase: AgentTaskPhase = AgentTaskPhase.OpeningApp,
        var openAppAttempts: Int = 0,
        var inputAttempts: Int = 0,
        var scrollAttempts: Int = 0,
        var waitAttempts: Int = 0,
        var backAttempts: Int = 0,
        var repeatedCloudRejects: Int = 0,
        var rejectedFinishAttempts: Int = 0,
        private val recentStepKeys: MutableList<String> = mutableListOf(),
        private val recentActionLines: MutableList<String> = mutableListOf(),
        var lastDebugLine: String = "",
    ) {
        fun shouldUseLightPreflightCapture(): Boolean {
            return targetApp != null && openAppAttempts == 0
        }

        fun observe(snapshot: AgentScreenSnapshot, context: GoalContext) {
            phase = nextPhase(snapshot, context)
            lastDebugLine = "调试：阶段=${phase.label} · app=${snapshot.currentApp.ifBlank { "未知" }} · 节点=${snapshot.nodeCount}/${snapshot.capturedNodeCount} · 全量=${snapshot.allNodes.size} · 点击=${snapshot.clickableNodes.size} · 输入=${snapshot.inputNodes.size} · 滚动=${snapshot.scrollableNodes.size} · 截图=${if (snapshot.hasVisualImage) "有" else "无"}"
        }

        fun rememberState(state: CloudAgentState) {
            if (state.reason.isBlank() && state.nextHint.isBlank()) return
            recentActionLines += "状态：complete=${state.isComplete}, progress=${state.expectedProgress}, wrong=${state.isWrong}, confidence=${"%.2f".format(state.confidence)} · ${state.reason.ifBlank { state.nextHint }}"
            trimHistory()
        }

        fun withDebug(message: String): String = if (lastDebugLine.isBlank()) message else "$message\n$lastDebugLine"

        fun remember(step: CloudAgentStep, result: AgentExecutionResult) {
            recentStepKeys += stepKey(step)
            recentActionLines += "动作：${step.type}${step.targetText?.let { " · $it" }.orEmpty()}${step.direction?.let { " · $it" }.orEmpty()}${step.x?.let { " · x=${"%.3f".format(it)}" }.orEmpty()}${step.y?.let { " y=${"%.3f".format(it)}" }.orEmpty()} → ${if (result.ok) "成功" else "失败"}：${result.message.take(80)}"
            trimHistory()
            if (recentStepKeys.size > 6) recentStepKeys.removeAt(0)
            if (step.type == "input_text") inputAttempts += 1
            if (step.type == "scroll" || step.type == "swipe") scrollAttempts += 1
            if (step.type == "wait") waitAttempts += 1
            if (step.type == "back") {
                backAttempts += 1
                if (result.ok) {
                    recentStepKeys.clear()
                    repeatedCloudRejects = 0
                    rejectedFinishAttempts = 0
                    phase = AgentTaskPhase.Navigating
                }
            }
            if (result.ok && step.type != "finish") rejectedFinishAttempts = 0
            if (!result.ok) repeatedCloudRejects += 1 else if (step.type != "back") repeatedCloudRejects = 0
        }

        fun recentActionSummaries(): List<String> = recentActionLines.takeLast(6)

        fun isLikelyRepeated(step: CloudAgentStep): Boolean = recentStepKeys.count { it == stepKey(step) } >= 1
        fun isBlocked(step: CloudAgentStep): Boolean = isLikelyRepeated(step)

        private fun nextPhase(snapshot: AgentScreenSnapshot, context: GoalContext): AgentTaskPhase {
            val app = context.targetApp
            if (app != null && snapshot.currentApp != app.packageName) return AgentTaskPhase.OpeningApp
            if (repeatedCloudRejects >= 2) return AgentTaskPhase.Recovering
            if (context.wantsSearch && (snapshot.inputNodes.isNotEmpty() || inputAttempts > 0)) return AgentTaskPhase.Searching
            if (!context.isPureOpenAppGoal && context.keywords.isNotEmpty()) return AgentTaskPhase.Verifying
            return AgentTaskPhase.Navigating
        }

        private fun trimHistory() {
            while (recentActionLines.size > 8) recentActionLines.removeAt(0)
        }

        private fun stepKey(step: CloudAgentStep): String = listOf(
            step.type,
            step.targetNodeId,
            step.targetText,
            step.text,
            step.direction,
            step.x?.let { "%.4f".format(it) },
            step.y?.let { "%.4f".format(it) },
        ).joinToString("|")
    }

    companion object {
        private const val DEFAULT_MAX_STEPS = 8
        private const val DEFAULT_STEP_DELAY_MS = 520L
        private const val OPEN_APP_DELAY_MS = 1_050L
        private const val TAP_DELAY_MS = 360L
        private const val INPUT_DELAY_MS = 320L
        private const val SCROLL_DELAY_MS = 560L
        private const val DEFAULT_WAIT_DELAY_MS = 720L
        private const val GLOBAL_ACTION_DELAY_MS = 420L
        private const val MIN_CUSTOM_STEP_DELAY_MS = 120L
        private const val MAX_CUSTOM_STEP_DELAY_MS = 2_000L
        private const val MAX_OPEN_APP_ATTEMPTS = 1
        private const val MAX_BACK_ATTEMPTS = 2
        private const val MAX_REJECTED_FINISH_ATTEMPTS = 2
        private const val COMPLETE_CONFIDENCE_THRESHOLD = 0.72f
        private const val WRONG_CONFIDENCE_THRESHOLD = 0.78f

        private val fillerWords = listOf("帮我", "替我", "请", "打开", "开启", "启动", "找到", "进入", "搜索", "查找", "一下", "看看", "去", "里", "里面", "应用", "app")
        private val searchIntentWords = listOf("搜索", "查找", "搜", "找", "search")
        private val loadingWaitWords = listOf("加载", "正在", "等待", "过渡", "动画", "空白", "刷新", "刚变化", "loading", "blank", "transition", "wait")

        private val TARGET_APPS = listOf(
            TargetApp("微信", "com.tencent.mm", listOf("微信", "wechat", "wx")),
            TargetApp("QQ", "com.tencent.mobileqq", listOf("qq", "腾讯qq")),
            TargetApp("哔哩哔哩", "tv.danmaku.bili", listOf("哔哩", "哔哩哔哩", "b站", "bilibili")),
            TargetApp("小红书", "com.xingin.xhs", listOf("小红书")),
            TargetApp("抖音", "com.ss.android.ugc.aweme", listOf("抖音", "douyin")),
            TargetApp("支付宝", "com.eg.android.AlipayGphone", listOf("支付宝", "alipay")),
            TargetApp("高德地图", "com.autonavi.minimap", listOf("高德", "高德地图", "amap")),
            TargetApp("百度地图", "com.baidu.BaiduMap", listOf("百度地图", "baidumap")),
            TargetApp("淘宝", "com.taobao.taobao", listOf("淘宝", "taobao")),
            TargetApp("京东", "com.jingdong.app.mall", listOf("京东", "jd")),
        )
    }
}