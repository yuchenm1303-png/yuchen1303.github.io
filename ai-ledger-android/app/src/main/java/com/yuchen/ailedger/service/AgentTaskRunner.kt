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
        val targetApp = detectTargetApp(goal)
        val memory = AgentRunMemory(goal = goal, targetApp = targetApp)

        repeat(maxSteps.coerceAtMost(DEFAULT_MAX_STEPS)) {
            val observation = captureOnce(forceVisual = true)
            if (!observation.enabled || !observation.serviceConnected) {
                val message = "无障碍服务未开启"
                AgentRuntimeController.failTask(message)
                return AgentTaskRunResult(false, false, message, logs)
            }

            val snapshot = observation.toAgentScreenSnapshot()
            val context = buildGoalContext(goal, targetApp)
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

            var workingSnapshot = snapshot
            var workingContext = context
            var cloudStep = withContext(Dispatchers.IO) {
                aiWorkerClient.requestAgentStep(goal = goal, snapshot = workingSnapshot, modelPreference = modelPreference)
            }

            if (cloudStep.type == "need_user_help" && memory.forceVisualPlanAttempts < MAX_FORCE_VISUAL_PLAN_ATTEMPTS) {
                memory.forceVisualPlanAttempts += 1
                val visualObservation = captureOnce(forceVisual = true)
                if (visualObservation.enabled && visualObservation.serviceConnected) {
                    val visualSnapshot = visualObservation.toAgentScreenSnapshot()
                    workingSnapshot = visualSnapshot
                    workingContext = buildGoalContext(goal, targetApp)
                    memory.observe(visualSnapshot, workingContext)
                    val visualStep = withContext(Dispatchers.IO) {
                        aiWorkerClient.requestAgentStep(goal = goal, snapshot = visualSnapshot, modelPreference = modelPreference)
                    }
                    if (visualStep.type != "need_user_help") cloudStep = visualStep
                }
            }

            if (cloudStep.type == "finish") {
                val verification = verifyCloudFinish(goal, cloudStep, workingSnapshot, memory, modelPreference)
                if (verification == FinishVerification.Expected) {
                    val done = AgentExecutionResult(true, "任务完成", false)
                    logs += AgentTaskStepLog(logs.size + 1, workingSnapshot.currentApp, cloudStep, done)
                    val message = memory.withDebug(cloudStep.reason ?: "任务完成")
                    AgentRuntimeController.finishTask(message, completed = true)
                    return AgentTaskRunResult(true, false, message, logs)
                }
                memory.rejectedFinishAttempts += 1
                if (memory.rejectedFinishAttempts >= MAX_REJECTED_FINISH_ATTEMPTS) {
                    val reason = when (verification) {
                        FinishVerification.Progress -> "云端规划器认为完成，但验证器判断仍只是接近目标，继续执行可能会反复。"
                        FinishVerification.Wrong -> "云端规划器认为完成，但验证器判断当前页面偏离目标。"
                        FinishVerification.Uncertain -> "云端规划器认为完成，但验证器无法确认当前页面已满足目标。"
                        FinishVerification.Expected -> "任务完成。"
                    }
                    val message = memory.withDebug(reason)
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(false, false, message, logs)
                }
                delay(DEFAULT_STEP_DELAY_MS)
                return@repeat
            }

            val chosenStep = chooseAction(workingSnapshot, cloudStep, memory, workingContext)
            if (chosenStep == null) {
                logs += AgentTaskStepLog(logs.size + 1, workingSnapshot.currentApp, cloudStep, null)
                val message = memory.withDebug(cloudStep.reason ?: "当前屏幕没有足够线索继续推进")
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, false, message, logs)
            }

            if (AgentSafetyPolicy.requiresConfirmation(goal, chosenStep)) {
                logs += AgentTaskStepLog(logs.size + 1, workingSnapshot.currentApp, chosenStep, null)
                val message = memory.withDebug("动作需要确认：${chosenStep.typeLabel}")
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, true, message, logs)
            }
            if (!AgentSafetyPolicy.canAutoExecuteInCurrentStage(goal, chosenStep)) {
                logs += AgentTaskStepLog(logs.size + 1, workingSnapshot.currentApp, chosenStep, null)
                val message = memory.withDebug(chosenStep.reason ?: "当前动作暂不能自动执行：${chosenStep.typeLabel}")
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, false, message, logs)
            }

            val result = executeAndRecord(chosenStep, workingSnapshot.currentApp, logs)
            memory.remember(chosenStep, result)
            if (!result.ok || !result.shouldContinue) {
                val message = memory.withDebug(result.message)
                AgentRuntimeController.finishTask(message, completed = false)
                return AgentTaskRunResult(false, false, message, logs)
            }

            if (chosenStep.type in VERIFY_AFTER_TAP_TYPES) {
                delay(TAP_VERIFY_DELAY_MS)
                when (verifyTapAfterClick(goal, chosenStep, workingContext, memory, modelPreference)) {
                    TapOutcome.ExpectedPage -> memory.clearUncertainTapCount()
                    TapOutcome.WrongPage -> {
                        memory.markFailedAction(chosenStep, blockFuture = true)
                        val backStep = CloudAgentStep(
                            type = "back",
                            reason = "云端视觉验证明确判断点击后页面偏离目标，自动返回后重新规划。",
                            riskLevel = "low",
                            requiresConfirmation = false,
                        )
                        val latest = captureOnce(forceVisual = true)
                        val backResult = executeAndRecord(backStep, latest.toAgentScreenSnapshot().currentApp, logs)
                        memory.remember(backStep, backResult)
                        if (!backResult.ok || !backResult.shouldContinue) {
                            val message = memory.withDebug("误点恢复失败，请手动回到上一页后继续。")
                            AgentRuntimeController.finishTask(message, completed = false)
                            return AgentTaskRunResult(false, false, message, logs)
                        }
                        delayForStep(backStep)
                        return@repeat
                    }
                    TapOutcome.Uncertain -> {
                        val uncertainCount = memory.recordUncertainTap()
                        if (uncertainCount >= MAX_CONSECUTIVE_UNCERTAIN_TAP_ATTEMPTS) {
                            val message = memory.withDebug("连续多次无法确认点击结果，已暂停避免继续误触。")
                            AgentRuntimeController.finishTask(message, completed = false)
                            return AgentTaskRunResult(false, false, message, logs)
                        }
                        return@repeat
                    }
                    TapOutcome.Unknown -> Unit
                }
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
            reason = "目标应用明确且当前不在目标应用，先打开目标应用。",
            riskLevel = "low",
            requiresConfirmation = false,
        )
    }

    private fun detectMechanicalCompletion(snapshot: AgentScreenSnapshot, context: GoalContext): String? {
        val app = context.targetApp ?: return null
        if (snapshot.currentApp != app.packageName) return null
        return if (context.isPureOpenAppGoal) "目标应用已打开。" else null
    }

    private suspend fun verifyCloudFinish(
        goal: String,
        finishStep: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        memory: AgentRunMemory,
        modelPreference: ChatModel,
    ): FinishVerification {
        if (!snapshot.hasVisualImage) return FinishVerification.Uncertain
        val result = withContext(Dispatchers.IO) {
            runCatching {
                aiWorkerClient.requestAgentOutcomeVerification(
                    goal = goal,
                    action = finishStep.copy(reason = finishStep.reason ?: "规划器请求结束任务，请验证当前截图是否真的已经满足用户目标。"),
                    snapshot = snapshot,
                    modelPreference = modelPreference,
                )
            }.getOrNull()
        } ?: return FinishVerification.Uncertain
        memory.observe(snapshot, buildGoalContext(goal, memory.targetApp))
        return when {
            result.isExpected -> FinishVerification.Expected
            result.isWrong -> FinishVerification.Wrong
            result.expectedProgress -> FinishVerification.Progress
            else -> FinishVerification.Uncertain
        }
    }

    private suspend fun verifyTapAfterClick(
        goal: String,
        step: CloudAgentStep,
        context: GoalContext,
        memory: AgentRunMemory,
        modelPreference: ChatModel,
    ): TapOutcome {
        val firstObservation = captureOnce(forceVisual = true)
        if (!firstObservation.enabled || !firstObservation.serviceConnected) return TapOutcome.Unknown
        val snapshot = firstObservation.toAgentScreenSnapshot()
        memory.observe(snapshot, context)
        val localOutcome = verifyTapOutcomeLocally(step, snapshot, context)
        if (!snapshot.hasVisualImage) return localOutcome

        val cloudOutcome = withContext(Dispatchers.IO) {
            runCatching {
                aiWorkerClient.requestAgentOutcomeVerification(
                    goal = goal,
                    action = step,
                    snapshot = snapshot,
                    modelPreference = modelPreference,
                )
            }.getOrNull()
        } ?: return TapOutcome.Uncertain

        return when {
            cloudOutcome.isExpected || cloudOutcome.expectedProgress -> TapOutcome.ExpectedPage
            cloudOutcome.isWrong -> TapOutcome.WrongPage
            else -> TapOutcome.Uncertain
        }
    }

    private fun verifyTapOutcomeLocally(step: CloudAgentStep, snapshot: AgentScreenSnapshot, context: GoalContext): TapOutcome {
        val visible = snapshot.visibleTextForMatch()
        val leftTargetApp = context.targetApp != null && snapshot.currentApp.isNotBlank() && snapshot.currentApp != context.targetApp.packageName
        if (!snapshot.hasVisualImage && leftTargetApp) return TapOutcome.WrongPage
        if (!snapshot.hasVisualImage && step.type in setOf("tap_node", "tap_xy") && enteredUnexpectedHighRiskSurface(context.goal, visible)) {
            return TapOutcome.WrongPage
        }
        return TapOutcome.Unknown
    }

    private fun chooseAction(snapshot: AgentScreenSnapshot, cloudStep: CloudAgentStep, memory: AgentRunMemory, context: GoalContext): CloudAgentStep? {
        sanitizeCloudStep(cloudStep, snapshot)?.let { candidate ->
            if (!memory.isBlocked(candidate)) return candidate
        }
        val localCandidates = buildLocalFallbackCandidates(snapshot, memory, context)
        if (cloudStep.type == "need_user_help" || memory.isLikelyRepeated(cloudStep)) memory.repeatedCloudRejects += 1
        return localCandidates.firstOrNull { !memory.isBlocked(it) }
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

    private fun buildLocalFallbackCandidates(snapshot: AgentScreenSnapshot, memory: AgentRunMemory, context: GoalContext): List<CloudAgentStep> {
        val candidates = mutableListOf<Pair<CloudAgentStep, Int>>()
        val clickableTargets = snapshot.clickableNodes
            .filter { it.text.isNotBlank() }
            .map { node -> node to scoreNodeText(node.text, context.keywords + context.routeHints) }
            .filter { (_, score) -> score >= MIN_LOCAL_NODE_SCORE }
            .sortedByDescending { (_, score) -> score }

        clickableTargets.take(MAX_LOCAL_NODE_CANDIDATES).forEach { (node, score) ->
            candidates += CloudAgentStep(
                type = "tap_node",
                targetNodeId = node.id,
                targetText = node.text,
                reason = "本地低风险兜底：云端动作不可用时，点击与目标相关的可见节点“${node.text.take(18)}”。",
                riskLevel = "low",
                requiresConfirmation = false,
            ) to score
        }

        if (context.wantsSearch && snapshot.inputNodes.isNotEmpty() && context.keywords.isNotEmpty() && memory.inputAttempts < MAX_INPUT_ATTEMPTS) {
            val input = snapshot.inputNodes.first()
            candidates += CloudAgentStep(
                type = "input_text",
                targetNodeId = input.id,
                text = context.keywords.first(),
                reason = "本地低风险兜底：云端动作不可用时，向已发现输入框输入核心关键词。",
                riskLevel = "low",
                requiresConfirmation = false,
            ) to INPUT_SEARCH_SCORE
        }

        if (memory.scrollAttempts < MAX_SCROLL_ATTEMPTS && !hasUsefulVisibleNode(snapshot, context)) {
            if (snapshot.scrollableNodes.isNotEmpty()) {
                candidates += CloudAgentStep(
                    type = "scroll",
                    targetNodeId = snapshot.scrollableNodes.first().id,
                    direction = if (memory.scrollAttempts % 2 == 0) "down" else "up",
                    reason = "本地低风险兜底：当前未发现可执行云端动作，滚动扩大搜索范围。",
                    riskLevel = "low",
                    requiresConfirmation = false,
                ) to SCROLL_FALLBACK_SCORE
            } else if (!snapshot.hasVisualImage && snapshot.nodeCount >= LOW_SIGNAL_NODE_COUNT) {
                candidates += CloudAgentStep(
                    type = "swipe",
                    direction = if (memory.scrollAttempts % 2 == 0) "up" else "down",
                    reason = "本地低风险兜底：节点可用但未暴露滚动控件，使用普通滑动继续探索。",
                    riskLevel = "low",
                    requiresConfirmation = false,
                ) to SWIPE_FALLBACK_SCORE
            }
        }

        if (!snapshot.hasVisualImage && memory.waitAttempts < MAX_WAIT_ATTEMPTS && snapshot.nodeCount <= LOW_SIGNAL_NODE_COUNT && snapshot.texts.isEmpty()) {
            candidates += CloudAgentStep(
                type = "wait",
                durationMs = DEFAULT_WAIT_DELAY_MS,
                reason = "本地低风险兜底：没有截图且节点几乎为空，短暂等待后重新观察。",
                riskLevel = "low",
                requiresConfirmation = false,
            ) to WAIT_EMPTY_SCREEN_SCORE
        }

        if (memory.backAttempts < MAX_BACK_ATTEMPTS && memory.phase == AgentTaskPhase.Recovering) {
            candidates += CloudAgentStep(
                type = "back",
                reason = "本地低风险兜底：连续重复卡住，返回上一层重新选择路径。",
                riskLevel = "low",
                requiresConfirmation = false,
            ) to BACK_RECOVERY_SCORE
        }

        return candidates.sortedByDescending { it.second }.map { it.first }
    }

    private fun hasUsefulVisibleNode(snapshot: AgentScreenSnapshot, context: GoalContext): Boolean {
        return snapshot.clickableNodes.any { node -> scoreNodeText(node.text, context.keywords + context.routeHints) >= MIN_LOCAL_NODE_SCORE }
    }

    private fun scoreNodeText(text: String, keywords: List<String>): Int {
        val cleanText = normalize(text)
        if (cleanText.isBlank()) return 0
        var score = 0
        keywords.forEach { keyword ->
            val cleanKeyword = normalize(keyword)
            if (cleanKeyword.isBlank()) return@forEach
            if (cleanText == cleanKeyword) score += 8
            else if (cleanText.contains(cleanKeyword) || cleanKeyword.contains(cleanText)) score += 5
            else if (cleanKeyword.length >= 2 && cleanKeyword.any { cleanText.contains(it) }) score += 1
        }
        return score
    }

    private fun buildGoalContext(goal: String, targetApp: TargetApp?): GoalContext {
        val keywords = extractGoalKeywords(goal, targetApp)
        val cleanGoal = normalize(goal)
        val wantsSearch = searchIntentWords.any { cleanGoal.contains(normalize(it)) }
        val routeHints = mutableListOf<String>()
        val completionHints = mutableListOf<String>()
        if (wantsSearch) routeHints += searchWords
        if (socialDiscoverySignals.any { cleanGoal.contains(normalize(it)) }) {
            routeHints += socialDiscoveryRouteWords
            completionHints += socialDiscoverySignals + socialDiscoveryRouteWords
        }
        if (contactSignals.any { cleanGoal.contains(normalize(it)) }) {
            routeHints += contactRouteWords
            completionHints += contactSignals + contactRouteWords
        }
        if (settingsSignals.any { cleanGoal.contains(normalize(it)) }) {
            routeHints += settingsRouteWords
            completionHints += settingsSignals + settingsRouteWords
        }
        if (routeHints.isEmpty()) routeHints += genericRouteWords
        return GoalContext(
            goal = goal,
            targetApp = targetApp,
            keywords = keywords,
            routeHints = routeHints.distinct(),
            completionHints = completionHints.distinct(),
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

    private fun enteredUnexpectedHighRiskSurface(goal: String, visibleText: String): Boolean {
        val cleanGoal = normalize(goal)
        val goalAllowsHighRisk = highRiskWords.any { cleanGoal.contains(normalize(it)) }
        if (goalAllowsHighRisk) return false
        return highRiskWords.any { visibleText.contains(normalize(it)) }
    }

    private fun isLoadingWaitReason(reason: String): Boolean {
        val clean = normalize(reason)
        return loadingWaitWords.any { clean.contains(normalize(it)) }
    }

    private fun AgentScreenSnapshot.visibleTextForMatch(): String {
        return (texts + allNodes.map { it.text } + clickableNodes.map { it.text } + inputNodes.map { it.text } + scrollableNodes.map { it.text })
            .joinToString(" ")
            .let { normalize(it) }
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[\\s\u3000，。,.、:：/\\-]+"), "")

    private fun detectTargetApp(goal: String): TargetApp? {
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

    private enum class TapOutcome { ExpectedPage, WrongPage, Unknown, Uncertain }
    private enum class FinishVerification { Expected, Progress, Wrong, Uncertain }

    private data class GoalContext(
        val goal: String,
        val targetApp: TargetApp?,
        val keywords: List<String>,
        val routeHints: List<String>,
        val completionHints: List<String>,
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
        var uncertainTapAttempts: Int = 0,
        var forceVisualPlanAttempts: Int = 0,
        var repeatedCloudRejects: Int = 0,
        var rejectedFinishAttempts: Int = 0,
        private val recentStepKeys: MutableList<String> = mutableListOf(),
        private val failedStepKeys: MutableSet<String> = mutableSetOf(),
        var lastDebugLine: String = "",
    ) {
        fun observe(snapshot: AgentScreenSnapshot, context: GoalContext) {
            phase = nextPhase(snapshot, context)
            lastDebugLine = "调试：阶段=${phase.label} · app=${snapshot.currentApp.ifBlank { "未知" }} · 节点=${snapshot.nodeCount}/${snapshot.capturedNodeCount} · 全量=${snapshot.allNodes.size} · 点击=${snapshot.clickableNodes.size} · 输入=${snapshot.inputNodes.size} · 滚动=${snapshot.scrollableNodes.size} · 截图=${if (snapshot.hasVisualImage) "有" else "无"}"
        }

        fun withDebug(message: String): String = if (lastDebugLine.isBlank()) message else "$message\n$lastDebugLine"

        fun remember(step: CloudAgentStep, result: AgentExecutionResult) {
            recentStepKeys += stepKey(step)
            if (recentStepKeys.size > 6) recentStepKeys.removeAt(0)
            if (step.type == "input_text") inputAttempts += 1
            if (step.type == "scroll" || step.type == "swipe") scrollAttempts += 1
            if (step.type == "wait") waitAttempts += 1
            if (step.type == "back") {
                backAttempts += 1
                if (result.ok) {
                    recentStepKeys.clear()
                    repeatedCloudRejects = 0
                    uncertainTapAttempts = 0
                    rejectedFinishAttempts = 0
                    phase = AgentTaskPhase.Navigating
                }
            }
            if (result.ok && step.type != "finish") rejectedFinishAttempts = 0
            if (!result.ok) repeatedCloudRejects += 1 else if (step.type != "back") repeatedCloudRejects = 0
        }

        fun recordUncertainTap(): Int {
            uncertainTapAttempts += 1
            recentStepKeys.clear()
            phase = AgentTaskPhase.Verifying
            return uncertainTapAttempts
        }

        fun clearUncertainTapCount() {
            uncertainTapAttempts = 0
        }

        fun markFailedAction(step: CloudAgentStep, blockFuture: Boolean) {
            if (blockFuture) failedStepKeys += stepKey(step)
            recentStepKeys.clear()
            repeatedCloudRejects = 0
            uncertainTapAttempts = 0
            rejectedFinishAttempts = 0
            phase = AgentTaskPhase.Recovering
        }

        fun isLikelyRepeated(step: CloudAgentStep): Boolean = recentStepKeys.count { it == stepKey(step) } >= 1
        fun isBlocked(step: CloudAgentStep): Boolean = isLikelyRepeated(step) || failedStepKeys.contains(stepKey(step))

        private fun nextPhase(snapshot: AgentScreenSnapshot, context: GoalContext): AgentTaskPhase {
            val app = context.targetApp
            if (app != null && snapshot.currentApp != app.packageName) return AgentTaskPhase.OpeningApp
            if (repeatedCloudRejects >= 2) return AgentTaskPhase.Recovering
            if (context.wantsSearch && (snapshot.inputNodes.isNotEmpty() || inputAttempts > 0)) return AgentTaskPhase.Searching
            if (!context.isPureOpenAppGoal && context.keywords.isNotEmpty()) return AgentTaskPhase.Verifying
            return AgentTaskPhase.Navigating
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
        private const val TAP_DELAY_MS = 430L
        private const val TAP_VERIFY_DELAY_MS = 620L
        private const val INPUT_DELAY_MS = 380L
        private const val SCROLL_DELAY_MS = 650L
        private const val DEFAULT_WAIT_DELAY_MS = 850L
        private const val GLOBAL_ACTION_DELAY_MS = 460L
        private const val MIN_CUSTOM_STEP_DELAY_MS = 120L
        private const val MAX_CUSTOM_STEP_DELAY_MS = 2_000L
        private const val MAX_OPEN_APP_ATTEMPTS = 1
        private const val MAX_INPUT_ATTEMPTS = 2
        private const val MAX_SCROLL_ATTEMPTS = 3
        private const val MAX_WAIT_ATTEMPTS = 2
        private const val MAX_BACK_ATTEMPTS = 2
        private const val MAX_FORCE_VISUAL_PLAN_ATTEMPTS = 1
        private const val MAX_CONSECUTIVE_UNCERTAIN_TAP_ATTEMPTS = 3
        private const val MAX_REJECTED_FINISH_ATTEMPTS = 2
        private const val LOW_SIGNAL_NODE_COUNT = 8
        private const val MIN_LOCAL_NODE_SCORE = 5
        private const val MAX_LOCAL_NODE_CANDIDATES = 4
        private const val INPUT_SEARCH_SCORE = 64
        private const val SCROLL_FALLBACK_SCORE = 22
        private const val SWIPE_FALLBACK_SCORE = 20
        private const val WAIT_EMPTY_SCREEN_SCORE = 12
        private const val BACK_RECOVERY_SCORE = 34
        private val VERIFY_AFTER_TAP_TYPES = setOf("tap_node", "tap_xy")

        private val fillerWords = listOf("帮我", "替我", "请", "打开", "开启", "启动", "找到", "进入", "搜索", "查找", "一下", "看看", "去", "里", "里面", "应用", "app")
        private val searchIntentWords = listOf("搜索", "查找", "搜", "找", "search")
        private val searchWords = listOf("搜索", "查找", "搜一搜", "search")
        private val genericRouteWords = listOf("首页", "消息", "发现", "动态", "社区", "广场", "频道", "分类", "更多", "菜单", "搜索", "我的", "我", "设置", "探索")
        private val socialDiscoverySignals = listOf("朋友圈", "动态", "朋友动态", "内容流", "内容", "社区", "广场", "圈子", "关注", "帖子", "视频号")
        private val socialDiscoveryRouteWords = listOf("发现", "动态", "朋友", "内容", "社区", "广场", "频道", "探索", "关注")
        private val contactSignals = listOf("联系人", "通讯录", "好友", "朋友", "群聊")
        private val contactRouteWords = listOf("通讯录", "联系人", "好友", "朋友", "群聊")
        private val settingsSignals = listOf("设置", "个人", "资料", "账号", "隐私", "收藏")
        private val settingsRouteWords = listOf("我的", "我", "设置", "个人", "资料", "账号")
        private val highRiskWords = listOf("支付", "付款", "转账", "红包", "下单", "购买", "删除", "卸载", "授权", "同意", "发送", "提交", "发布", "评论", "验证码", "密码", "登录")
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