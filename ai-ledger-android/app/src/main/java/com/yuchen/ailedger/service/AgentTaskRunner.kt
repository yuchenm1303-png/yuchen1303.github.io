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
            val observation = captureOnce()
            if (!observation.enabled || !observation.serviceConnected) {
                val message = "无障碍服务未开启"
                AgentRuntimeController.failTask(message)
                return AgentTaskRunResult(false, false, message, logs)
            }
            val snapshot = observation.toAgentScreenSnapshot()
            val context = buildGoalContext(goal, targetApp)
            memory.observe(snapshot, context)

            val completion = detectCompletion(memory, snapshot, context)
            if (completion != null) {
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

            if (cloudStep.type == "need_user_help" && !workingSnapshot.hasVisualImage && memory.forceVisualPlanAttempts < MAX_FORCE_VISUAL_PLAN_ATTEMPTS) {
                memory.forceVisualPlanAttempts += 1
                val visualObservation = captureOnce(forceVisual = true)
                if (visualObservation.enabled && visualObservation.serviceConnected) {
                    val visualSnapshot = visualObservation.toAgentScreenSnapshot()
                    workingContext = buildGoalContext(goal, targetApp)
                    memory.observe(visualSnapshot, workingContext)
                    val visualStep = withContext(Dispatchers.IO) {
                        aiWorkerClient.requestAgentStep(goal = goal, snapshot = visualSnapshot, modelPreference = modelPreference)
                    }
                    if (visualStep.type != "need_user_help") {
                        workingSnapshot = visualSnapshot
                        cloudStep = visualStep
                    }
                }
            }

            if (cloudStep.type == "finish") {
                val done = AgentExecutionResult(true, "任务完成", false)
                logs += AgentTaskStepLog(logs.size + 1, workingSnapshot.currentApp, cloudStep, done)
                val message = memory.withDebug(cloudStep.reason ?: "任务完成")
                AgentRuntimeController.finishTask(message, completed = true)
                return AgentTaskRunResult(true, false, message, logs)
            }

            val chosenStep = chooseAction(goal, workingSnapshot, cloudStep, memory, workingContext)
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
                val verifyObservation = captureOnce()
                if (verifyObservation.enabled && verifyObservation.serviceConnected) {
                    val verifySnapshot = verifyObservation.toAgentScreenSnapshot()
                    memory.observe(verifySnapshot, workingContext)
                    val verifyResult = verifyTapOutcome(chosenStep, verifySnapshot, workingContext)
                    if (verifyResult == TapOutcome.WrongPage) {
                        memory.markFailedAction(chosenStep)
                        val backStep = CloudAgentStep(
                            type = "back",
                            reason = "点击后页面特征与目标不符，自动返回后重新定位。",
                            riskLevel = "low",
                            requiresConfirmation = false,
                        )
                        val backResult = executeAndRecord(backStep, verifySnapshot.currentApp, logs)
                        memory.remember(backStep, backResult)
                        if (!backResult.ok || !backResult.shouldContinue) {
                            val message = memory.withDebug("误点后自动返回失败，请手动回到上一页后继续。")
                            AgentRuntimeController.finishTask(message, completed = false)
                            return AgentTaskRunResult(false, false, message, logs)
                        }
                        delayForStep(backStep)
                        return@repeat
                    }
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
        return CloudAgentStep(type = "open_app", appName = app.label, packageName = app.packageName, reason = "本地控制器：目标应用明确，当前不在目标应用，先打开目标应用。", riskLevel = "low", requiresConfirmation = false)
    }

    private fun detectCompletion(memory: AgentRunMemory, snapshot: AgentScreenSnapshot, context: GoalContext): String? {
        val app = context.targetApp
        if (app != null && snapshot.currentApp != app.packageName) return null
        if (app != null && context.keywords.isEmpty()) return "目标应用已打开。"
        if (context.keywords.isEmpty()) return null
        if (!memory.hasExecutedAnyAction && app != null) return null
        val visibleText = snapshot.visibleTextForMatch()
        val directKeyword = context.keywords.firstOrNull { keyword ->
            val cleanKeyword = normalize(keyword)
            cleanKeyword.length >= MIN_COMPLETION_KEYWORD_LENGTH && visibleText.contains(cleanKeyword)
        }
        if (directKeyword != null) return "已在当前页面找到目标线索：${directKeyword.take(18)}。"
        val routeHit = context.completionHints.firstOrNull { hint -> visibleText.contains(normalize(hint)) }
        if (routeHit != null && memory.hasExecutedAnyAction) return "已进入目标相关页面：${routeHit.take(18)}。"
        if (context.wantsSearch && memory.inputAttempts > 0 && visibleText.isNotBlank() && context.keywords.any { normalize(it).length >= 2 }) return "已完成搜索输入并刷新到相关结果页。"
        return null
    }

    private fun verifyTapOutcome(step: CloudAgentStep, snapshot: AgentScreenSnapshot, context: GoalContext): TapOutcome {
        val expected = expectedOutcomeFor(step, context) ?: return TapOutcome.Unknown
        val visible = snapshot.visibleTextForMatch()
        if (expected.successTexts.any { visible.contains(normalize(it)) }) return TapOutcome.ExpectedPage
        if (expected.avoidTexts.any { visible.contains(normalize(it)) }) return TapOutcome.WrongPage
        return TapOutcome.Unknown
    }

    private fun expectedOutcomeFor(step: CloudAgentStep, context: GoalContext): ExpectedOutcome? {
        val target = normalize(step.targetText.orEmpty() + step.reason.orEmpty())
        val goal = normalize(context.goal)
        return when {
            socialDiscoveryRouteWords.any { target.contains(normalize(it)) || goal.contains(normalize(it)) } -> ExpectedOutcome(
                successTexts = socialDiscoverySuccessTexts,
                avoidTexts = chatPageAvoidTexts + chatListAvoidWords,
            )
            contactRouteWords.any { target.contains(normalize(it)) || goal.contains(normalize(it)) } -> ExpectedOutcome(
                successTexts = contactSuccessTexts,
                avoidTexts = chatPageAvoidTexts,
            )
            settingsRouteWords.any { target.contains(normalize(it)) || goal.contains(normalize(it)) } -> ExpectedOutcome(
                successTexts = settingsSuccessTexts,
                avoidTexts = chatPageAvoidTexts,
            )
            else -> null
        }
    }

    private fun AgentScreenSnapshot.visibleTextForMatch(): String = (texts + clickableNodes.map { it.text } + inputNodes.map { it.text }).joinToString(" ").let { normalize(it) }

    private fun chooseAction(goal: String, snapshot: AgentScreenSnapshot, cloudStep: CloudAgentStep, memory: AgentRunMemory, context: GoalContext): CloudAgentStep? {
        val ranked = mutableListOf<ScoredStep>()
        scoreCloudStep(cloudStep, snapshot, context, memory)?.let { ranked += it }
        ranked += buildLocalCandidates(snapshot, memory, context)
        if (cloudStep.type == "need_user_help" || memory.isLikelyRepeated(cloudStep)) memory.repeatedCloudRejects += 1
        return ranked.distinctBy { stepSignature(it.step) }.sortedByDescending { it.score }.firstOrNull { !memory.isBlocked(it.step) }?.step
    }

    private fun scoreCloudStep(step: CloudAgentStep, snapshot: AgentScreenSnapshot, context: GoalContext, memory: AgentRunMemory): ScoredStep? {
        if (step.type == "need_user_help") return null
        if (step.type == "tap_xy" && (step.x == null || step.y == null)) return null
        if (step.type == "input_text" && step.text.isNullOrBlank()) return null
        if (step.type == "tap_node" && step.targetNodeId.isNullOrBlank() && step.targetText.isNullOrBlank()) return null
        if (step.type == "open_app" && step.packageName != null && step.packageName == snapshot.currentApp) return null
        val targetScore = step.targetText?.let { scoreNodeText(it, context.keywords) * 10 + scoreNodeText(it, context.routeHints) * 5 } ?: 0
        val phaseBonus = when (memory.phase) {
            AgentTaskPhase.Searching -> if (step.type == "input_text" || scoreNodeText(step.targetText.orEmpty(), searchWords) > 0) 14 else 0
            AgentTaskPhase.Navigating -> if (step.type == "tap_node" || step.type == "tap_xy") 8 else 0
            AgentTaskPhase.Recovering -> if (step.type in setOf("back", "wait", "swipe", "scroll")) 12 else 0
            AgentTaskPhase.Verifying -> if (step.type == "finish") 20 else 0
            AgentTaskPhase.OpeningApp -> if (step.type == "open_app") 12 else 0
        }
        val score = when (step.type) {
            "tap_node", "tap_xy" -> 58 + targetScore
            "input_text" -> if (context.wantsSearch) 62 else 46
            "open_app" -> 76
            "scroll", "swipe" -> if (hasUsefulVisibleAction(snapshot, context)) 18 else 38
            "wait" -> if (snapshot.nodeCount <= LOW_SIGNAL_NODE_COUNT) 40 else 16
            "back" -> if (memory.repeatedCloudRejects >= 1) 42 else 18
            "home", "recents", "notifications", "quick_settings" -> 16
            else -> 34
        } + phaseBonus - if (memory.isLikelyRepeated(step)) REPEATED_ACTION_PENALTY else 0
        return ScoredStep(step, score)
    }

    private fun buildLocalCandidates(snapshot: AgentScreenSnapshot, memory: AgentRunMemory, context: GoalContext): List<ScoredStep> {
        val candidates = mutableListOf<ScoredStep>()
        val geometry = screenGeometry(snapshot)
        val rankedNodes = snapshot.clickableNodes.filter { it.text.isNotBlank() }.map { node -> node to scoreClickableNode(node, context, geometry) }.filter { (_, score) -> score >= MIN_CLICKABLE_CANDIDATE_SCORE }.sortedByDescending { (_, score) -> score }
        rankedNodes.take(MAX_CLICKABLE_CANDIDATES).forEach { (node, score) ->
            candidates += ScoredStep(
                step = CloudAgentStep(type = "tap_node", targetNodeId = node.id, targetText = node.text, reason = if (scoreNodeText(node.text, context.keywords) >= DIRECT_MATCH_SCORE) "本地控制器：屏幕上已有高相关目标“${node.text.take(18)}”，优先点击。" else "本地控制器：当前目标不可见，先进入高价值导航入口“${node.text.take(18)}”。", riskLevel = "low", requiresConfirmation = false),
                score = score + phaseNodeBonus(memory.phase, node, context),
            )
        }
        buildVisualFallbackCandidate(snapshot, memory, context)?.let { candidates += it }
        if (snapshot.inputNodes.isNotEmpty() && context.keywords.isNotEmpty() && memory.inputAttempts < MAX_INPUT_ATTEMPTS) {
            val input = snapshot.inputNodes.first()
            candidates += ScoredStep(step = CloudAgentStep(type = "input_text", targetNodeId = input.id, text = context.keywords.first(), reason = "本地控制器：已发现输入框，输入核心关键词继续查找。", riskLevel = "low", requiresConfirmation = false), score = if (context.wantsSearch || memory.phase == AgentTaskPhase.Searching) INPUT_SEARCH_SCORE else INPUT_NORMAL_SCORE)
        }
        if (memory.scrollAttempts < MAX_SCROLL_ATTEMPTS && !hasUsefulVisibleAction(snapshot, context)) {
            if (snapshot.scrollableNodes.isNotEmpty()) {
                candidates += ScoredStep(step = CloudAgentStep(type = "scroll", targetNodeId = snapshot.scrollableNodes.first().id, direction = if (memory.scrollAttempts % 2 == 0) "down" else "up", reason = "本地控制器：当前屏幕没有可靠入口，再滚动扩大搜索范围。", riskLevel = "low", requiresConfirmation = false), score = SCROLL_FALLBACK_SCORE + if (memory.phase == AgentTaskPhase.Recovering) RECOVERY_ACTION_BONUS else 0)
            } else if (snapshot.nodeCount >= LOW_SIGNAL_NODE_COUNT || snapshot.texts.isNotEmpty()) {
                candidates += ScoredStep(step = CloudAgentStep(type = "swipe", direction = if (memory.scrollAttempts % 2 == 0) "up" else "down", reason = "本地控制器：当前屏幕没有暴露滚动节点，使用普通滑动手势继续探索。", riskLevel = "low", requiresConfirmation = false), score = SWIPE_FALLBACK_SCORE + if (memory.phase == AgentTaskPhase.Recovering) RECOVERY_ACTION_BONUS else 0)
            }
        }
        if (memory.waitAttempts < MAX_WAIT_ATTEMPTS && snapshot.nodeCount <= LOW_SIGNAL_NODE_COUNT) candidates += ScoredStep(step = CloudAgentStep(type = "wait", durationMs = DEFAULT_WAIT_DELAY_MS, reason = "本地控制器：当前页面节点较少，可能仍在加载，先等待后重新观察。", riskLevel = "low", requiresConfirmation = false), score = WAIT_LOW_SIGNAL_SCORE)
        if (memory.backAttempts < MAX_BACK_ATTEMPTS && memory.phase == AgentTaskPhase.Recovering) candidates += ScoredStep(step = CloudAgentStep(type = "back", reason = "本地控制器：连续重复卡住，返回上一层重新选择路径。", riskLevel = "low", requiresConfirmation = false), score = BACK_RECOVERY_SCORE)
        return candidates.sortedByDescending { it.score }
    }

    private fun phaseNodeBonus(phase: AgentTaskPhase, node: AgentScreenNode, context: GoalContext): Int = when (phase) {
        AgentTaskPhase.Searching -> if (scoreNodeText(node.text, searchWords) > 0) 14 else 0
        AgentTaskPhase.Navigating -> if (scoreNodeText(node.text, context.routeHints + navigationWords) > 0) 8 else 0
        AgentTaskPhase.Recovering -> if (scoreNodeText(node.text, context.routeHints + searchWords) > 0) 6 else 0
        else -> 0
    }

    private fun buildVisualFallbackCandidate(snapshot: AgentScreenSnapshot, memory: AgentRunMemory, context: GoalContext): ScoredStep? {
        if (!snapshot.hasVisualImage || memory.visualTapAttempts >= MAX_VISUAL_TAP_ATTEMPTS) return null
        val point = visualFallbackPoint(snapshot, context) ?: return null
        return ScoredStep(step = CloudAgentStep(type = "tap_xy", targetText = point.label, x = point.x, y = point.y, reason = "本地视觉兜底：无障碍节点不足，按目标意图点击${point.source}“${point.label}”。", riskLevel = "low", requiresConfirmation = false), score = VISUAL_FALLBACK_SCORE)
    }

    private fun visualFallbackPoint(snapshot: AgentScreenSnapshot, context: GoalContext): VisualFallbackPoint? {
        val app = context.targetApp ?: return null
        val visual = snapshot.visual?.takeIf { it.hasImage } ?: return null
        val width = (visual.displayWidth.takeIf { it > 0 } ?: visual.width).toFloat().coerceAtLeast(1f)
        val height = (visual.displayHeight.takeIf { it > 0 } ?: visual.height).toFloat().coerceAtLeast(1f)
        val cleanGoal = normalize(context.goal)
        return when (app.packageName) {
            "com.tencent.mm" -> when {
                socialDiscoverySignals.any { cleanGoal.contains(normalize(it)) } -> VisualFallbackPoint(width * 0.625f, height * 0.935f, "发现", "微信底部导航")
                contactSignals.any { cleanGoal.contains(normalize(it)) } -> VisualFallbackPoint(width * 0.375f, height * 0.935f, "通讯录", "微信底部导航")
                settingsSignals.any { cleanGoal.contains(normalize(it)) } -> VisualFallbackPoint(width * 0.875f, height * 0.935f, "我", "微信底部导航")
                else -> null
            }
            "com.tencent.mobileqq" -> when {
                socialDiscoverySignals.any { cleanGoal.contains(normalize(it)) } -> VisualFallbackPoint(width * 0.83f, height * 0.935f, "动态", "QQ 底部导航")
                contactSignals.any { cleanGoal.contains(normalize(it)) } -> VisualFallbackPoint(width * 0.50f, height * 0.935f, "联系人", "QQ 底部导航")
                else -> null
            }
            else -> null
        }
    }

    private fun hasUsefulVisibleAction(snapshot: AgentScreenSnapshot, context: GoalContext): Boolean {
        val geometry = screenGeometry(snapshot)
        return snapshot.clickableNodes.any { node -> scoreClickableNode(node, context, geometry) >= USEFUL_VISIBLE_ACTION_SCORE }
    }

    private fun scoreClickableNode(node: AgentScreenNode, context: GoalContext, geometry: ScreenGeometry): Int {
        val directScore = scoreNodeText(node.text, context.keywords)
        val routeScore = scoreNodeText(node.text, context.routeHints)
        val navScore = scoreNodeText(node.text, navigationWords)
        val avoidScore = scoreNodeText(node.text, context.avoidHints)
        val searchScore = if (context.wantsSearch && scoreNodeText(node.text, searchWords) > 0) 1 else 0
        val structuralScore = when {
            isBottomNavNode(node, geometry) && (routeScore > 0 || navScore > 0 || searchScore > 0) -> BOTTOM_NAV_BONUS
            isTopNavNode(node, geometry) && (routeScore > 0 || navScore > 0 || searchScore > 0) -> TOP_NAV_BONUS
            else -> 0
        }
        val conciseControlBonus = if (node.text.length <= 4 && (routeScore > 0 || navScore > 0 || searchScore > 0)) SHORT_NAV_TEXT_BONUS else 0
        val unrelatedLongTextPenalty = if (node.text.length >= 12 && directScore == 0 && routeScore == 0 && navScore == 0) LONG_UNRELATED_TEXT_PENALTY else 0
        return directScore * DIRECT_TEXT_WEIGHT + routeScore * ROUTE_HINT_WEIGHT + navScore * NAV_TEXT_WEIGHT + searchScore * SEARCH_ENTRY_BONUS + structuralScore + conciseControlBonus - avoidScore * AVOID_HINT_WEIGHT - unrelatedLongTextPenalty
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
        if (socialDiscoverySignals.any { cleanGoal.contains(normalize(it)) }) { routeHints += socialDiscoveryRouteWords; completionHints += socialDiscoverySignals + socialDiscoveryRouteWords }
        if (contactSignals.any { cleanGoal.contains(normalize(it)) }) { routeHints += contactRouteWords; completionHints += contactSignals + contactRouteWords }
        if (settingsSignals.any { cleanGoal.contains(normalize(it)) }) { routeHints += settingsRouteWords; completionHints += settingsSignals + settingsRouteWords }
        val avoidHints = mutableListOf<String>()
        if (socialDiscoverySignals.any { cleanGoal.contains(normalize(it)) }) avoidHints += chatListAvoidWords
        return GoalContext(goal = goal, targetApp = targetApp, keywords = keywords, routeHints = routeHints.distinct(), completionHints = completionHints.distinct(), avoidHints = avoidHints.distinct(), wantsSearch = wantsSearch)
    }

    private fun extractGoalKeywords(goal: String, targetApp: TargetApp?): List<String> {
        var text = goal.trim()
        targetApp?.aliases.orEmpty().forEach { text = text.replace(it, "", ignoreCase = true) }
        fillerWords.forEach { text = text.replace(it, "", ignoreCase = true) }
        val compact = text.replace(Regex("[，。,.、\\s]+"), "").take(24)
        val tokens = Regex("[\\p{L}\\p{N}]{2,}").findAll(text).map { it.value.take(24) }.toList()
        return (listOf(compact) + tokens).filter { it.isNotBlank() }.distinct().take(4)
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[\\s\u3000，。,.、:：/\\-]+"), "")

    private fun screenGeometry(snapshot: AgentScreenSnapshot): ScreenGeometry {
        val rects = (snapshot.clickableNodes + snapshot.inputNodes + snapshot.scrollableNodes).mapNotNull { parseBounds(it.bounds) }
        val top = rects.minOfOrNull { it.top } ?: 0
        val bottom = rects.maxOfOrNull { it.bottom } ?: 1
        return ScreenGeometry(top = top, bottom = bottom.coerceAtLeast(top + 1))
    }

    private fun parseBounds(bounds: String): NodeRect? {
        val values = bounds.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (values.size != 4) return null
        val (left, top, right, bottom) = values
        if (right <= left || bottom <= top) return null
        return NodeRect(left = left, top = top, right = right, bottom = bottom)
    }

    private fun isBottomNavNode(node: AgentScreenNode, geometry: ScreenGeometry): Boolean {
        val rect = parseBounds(node.bounds) ?: return false
        val centerY = (rect.top + rect.bottom) / 2f
        return centerY >= geometry.top + geometry.height * 0.72f && rect.height <= geometry.height * 0.18f
    }

    private fun isTopNavNode(node: AgentScreenNode, geometry: ScreenGeometry): Boolean {
        val rect = parseBounds(node.bounds) ?: return false
        val centerY = (rect.top + rect.bottom) / 2f
        return centerY <= geometry.top + geometry.height * 0.24f && rect.height <= geometry.height * 0.18f
    }

    private fun stepSignature(step: CloudAgentStep): String = listOf(step.type, step.targetNodeId, step.targetText, step.text, step.direction, step.x?.toInt(), step.y?.toInt()).joinToString("|")

    private fun detectTargetApp(goal: String): TargetApp? {
        val clean = normalize(goal)
        return TARGET_APPS.firstOrNull { item -> item.aliases.any { alias -> clean.contains(normalize(alias)) } }
    }

    private enum class AgentTaskPhase(val label: String) { OpeningApp("打开应用"), Navigating("找入口"), Searching("搜索输入"), Verifying("确认结果"), Recovering("恢复路径") }
    private enum class TapOutcome { ExpectedPage, WrongPage, Unknown }
    private data class ExpectedOutcome(val successTexts: List<String>, val avoidTexts: List<String>)
    private data class GoalContext(val goal: String, val targetApp: TargetApp?, val keywords: List<String>, val routeHints: List<String>, val completionHints: List<String>, val avoidHints: List<String>, val wantsSearch: Boolean)
    private data class ScoredStep(val step: CloudAgentStep, val score: Int)
    private data class NodeRect(val left: Int, val top: Int, val right: Int, val bottom: Int) { val height: Int get() = bottom - top }
    private data class ScreenGeometry(val top: Int, val bottom: Int) { val height: Int get() = (bottom - top).coerceAtLeast(1) }
    private data class TargetApp(val label: String, val packageName: String, val aliases: List<String>)
    private data class VisualFallbackPoint(val x: Float, val y: Float, val label: String, val source: String)

    private data class AgentRunMemory(
        val goal: String,
        val targetApp: TargetApp?,
        var phase: AgentTaskPhase = AgentTaskPhase.OpeningApp,
        var openAppAttempts: Int = 0,
        var inputAttempts: Int = 0,
        var scrollAttempts: Int = 0,
        var waitAttempts: Int = 0,
        var backAttempts: Int = 0,
        var visualTapAttempts: Int = 0,
        var forceVisualPlanAttempts: Int = 0,
        var repeatedCloudRejects: Int = 0,
        var hasExecutedAnyAction: Boolean = false,
        private val recentStepKeys: MutableList<String> = mutableListOf(),
        private val failedStepKeys: MutableSet<String> = mutableSetOf(),
        var lastDebugLine: String = "",
    ) {
        fun observe(snapshot: AgentScreenSnapshot, context: GoalContext) {
            phase = nextPhase(snapshot, context)
            lastDebugLine = "调试：阶段=${phase.label} · app=${snapshot.currentApp.ifBlank { "未知" }} · 节点=${snapshot.nodeCount} · 文字=${snapshot.texts.size} · 点击=${snapshot.clickableNodes.size} · 输入=${snapshot.inputNodes.size} · 滚动=${snapshot.scrollableNodes.size} · 截图=${if (snapshot.hasVisualImage) "有" else "无"}"
        }

        fun withDebug(message: String): String = if (lastDebugLine.isBlank()) message else "$message\n$lastDebugLine"

        fun remember(step: CloudAgentStep, result: AgentExecutionResult) {
            recentStepKeys += stepKey(step)
            if (recentStepKeys.size > 6) recentStepKeys.removeAt(0)
            if (step.type == "input_text") inputAttempts += 1
            if (step.type == "scroll" || step.type == "swipe") scrollAttempts += 1
            if (step.type == "wait") waitAttempts += 1
            if (step.type == "tap_xy") visualTapAttempts += 1
            if (step.type == "back") {
                backAttempts += 1
                if (result.ok) {
                    recentStepKeys.clear()
                    visualTapAttempts = 0
                    repeatedCloudRejects = 0
                    phase = AgentTaskPhase.Navigating
                }
            }
            if (result.ok) hasExecutedAnyAction = true
            if (!result.ok) repeatedCloudRejects += 1 else if (step.type != "back") repeatedCloudRejects = 0
        }

        fun markFailedAction(step: CloudAgentStep) {
            failedStepKeys += stepKey(step)
            recentStepKeys.clear()
            visualTapAttempts = 0
            repeatedCloudRejects = 0
            phase = AgentTaskPhase.Recovering
        }

        fun isLikelyRepeated(step: CloudAgentStep): Boolean = recentStepKeys.count { it == stepKey(step) } >= 1
        fun isBlocked(step: CloudAgentStep): Boolean = isLikelyRepeated(step) || failedStepKeys.contains(stepKey(step))

        private fun nextPhase(snapshot: AgentScreenSnapshot, context: GoalContext): AgentTaskPhase {
            val app = context.targetApp
            if (app != null && snapshot.currentApp != app.packageName) return AgentTaskPhase.OpeningApp
            if (repeatedCloudRejects >= 2) return AgentTaskPhase.Recovering
            if (context.wantsSearch && (snapshot.inputNodes.isNotEmpty() || inputAttempts > 0)) return AgentTaskPhase.Searching
            if (hasExecutedAnyAction && context.keywords.isNotEmpty()) return AgentTaskPhase.Verifying
            return AgentTaskPhase.Navigating
        }

        private fun stepKey(step: CloudAgentStep): String = listOf(step.type, step.targetNodeId, step.targetText, step.text, step.direction, step.x?.toInt(), step.y?.toInt()).joinToString("|")
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
        private const val MAX_VISUAL_TAP_ATTEMPTS = 2
        private const val MAX_FORCE_VISUAL_PLAN_ATTEMPTS = 1
        private const val LOW_SIGNAL_NODE_COUNT = 8
        private const val DIRECT_MATCH_SCORE = 5
        private const val MAX_CLICKABLE_CANDIDATES = 4
        private const val MIN_CLICKABLE_CANDIDATE_SCORE = 28
        private const val USEFUL_VISIBLE_ACTION_SCORE = 46
        private const val MIN_COMPLETION_KEYWORD_LENGTH = 2
        private const val DIRECT_TEXT_WEIGHT = 12
        private const val ROUTE_HINT_WEIGHT = 7
        private const val NAV_TEXT_WEIGHT = 4
        private const val AVOID_HINT_WEIGHT = 10
        private const val SEARCH_ENTRY_BONUS = 30
        private const val BOTTOM_NAV_BONUS = 18
        private const val TOP_NAV_BONUS = 10
        private const val SHORT_NAV_TEXT_BONUS = 6
        private const val LONG_UNRELATED_TEXT_PENALTY = 16
        private const val REPEATED_ACTION_PENALTY = 60
        private const val INPUT_SEARCH_SCORE = 64
        private const val INPUT_NORMAL_SCORE = 48
        private const val VISUAL_FALLBACK_SCORE = 54
        private const val SCROLL_FALLBACK_SCORE = 22
        private const val SWIPE_FALLBACK_SCORE = 20
        private const val WAIT_LOW_SIGNAL_SCORE = 36
        private const val BACK_RECOVERY_SCORE = 34
        private const val RECOVERY_ACTION_BONUS = 14
        private val VERIFY_AFTER_TAP_TYPES = setOf("tap_node", "tap_xy")

        private val fillerWords = listOf("帮我", "替我", "请", "打开", "开启", "启动", "找到", "进入", "搜索", "查找", "一下", "看看", "去", "里", "里面", "应用", "app")
        private val searchIntentWords = listOf("搜索", "查找", "搜", "找", "search")
        private val searchWords = listOf("搜索", "查找", "搜一搜", "search")
        private val navigationWords = listOf("搜索", "查找", "发现", "首页", "消息", "通讯录", "联系人", "我的", "我", "频道", "分类", "更多", "菜单", "动态", "社区", "广场", "探索", "search")
        private val socialDiscoverySignals = listOf("朋友圈", "动态", "朋友动态", "内容流", "内容", "社区", "广场", "圈子", "关注", "帖子", "视频号")
        private val socialDiscoveryRouteWords = listOf("发现", "动态", "朋友", "内容", "社区", "广场", "频道", "探索", "关注")
        private val socialDiscoverySuccessTexts = listOf("朋友圈", "视频号", "搜一搜", "看一看", "直播", "小程序", "游戏", "购物")
        private val contactSignals = listOf("联系人", "通讯录", "好友", "朋友", "群聊")
        private val contactRouteWords = listOf("通讯录", "联系人", "好友", "朋友", "群聊")
        private val contactSuccessTexts = listOf("新的朋友", "群聊", "标签", "公众号", "企业微信联系人")
        private val settingsSignals = listOf("设置", "个人", "资料", "账号", "隐私", "收藏")
        private val settingsRouteWords = listOf("我的", "我", "设置", "个人", "资料", "账号")
        private val settingsSuccessTexts = listOf("服务", "收藏", "朋友圈", "视频号", "卡包", "表情", "设置")
        private val chatListAvoidWords = listOf("聊天", "会话", "群聊", "文件传输助手", "订阅号", "服务通知")
        private val chatPageAvoidTexts = listOf("发送", "按住说话", "语音", "表情", "更多功能", "聊天信息", "转账", "红包", "请输入消息")

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
