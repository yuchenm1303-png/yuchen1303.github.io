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
        val targetApp = detectTargetApp(goal)
        val memory = AgentRunMemory(goal = goal, targetApp = targetApp)

        repeat(maxSteps.coerceAtMost(DEFAULT_MAX_STEPS)) {
            val observation = captureOnce()
            if (!observation.enabled || !observation.serviceConnected) {
                return AgentTaskRunResult(false, false, "无障碍服务未开启", logs)
            }
            val snapshot = observation.toAgentScreenSnapshot()
            memory.observe(snapshot)

            val preflight = buildPreflightStep(memory, snapshot)
            if (preflight != null) {
                val result = executeAndRecord(preflight, snapshot.currentApp, logs)
                memory.remember(preflight, result)
                if (!result.ok || !result.shouldContinue) return AgentTaskRunResult(false, false, result.message, logs)
                delayForStep(preflight)
                return@repeat
            }

            val cloudStep = withContext(Dispatchers.IO) {
                aiWorkerClient.requestAgentStep(goal = goal, snapshot = snapshot, modelPreference = modelPreference)
            }

            if (cloudStep.type == "finish") {
                val done = AgentExecutionResult(true, "任务完成", false)
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, cloudStep, done)
                return AgentTaskRunResult(true, false, cloudStep.reason ?: "任务完成", logs)
            }

            val chosenStep = chooseAction(goal, snapshot, cloudStep, memory)
            if (chosenStep == null) {
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, cloudStep, null)
                return AgentTaskRunResult(false, false, cloudStep.reason ?: "当前屏幕没有足够线索继续推进", logs)
            }

            if (AgentSafetyPolicy.requiresConfirmation(goal, chosenStep)) {
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, chosenStep, null)
                return AgentTaskRunResult(false, true, "动作需要确认：${chosenStep.typeLabel}", logs)
            }
            if (!AgentSafetyPolicy.canAutoExecuteInCurrentStage(goal, chosenStep)) {
                logs += AgentTaskStepLog(logs.size + 1, snapshot.currentApp, chosenStep, null)
                return AgentTaskRunResult(false, false, chosenStep.reason ?: "当前动作暂不能自动执行：${chosenStep.typeLabel}", logs)
            }

            val result = executeAndRecord(chosenStep, snapshot.currentApp, logs)
            memory.remember(chosenStep, result)
            if (!result.ok || !result.shouldContinue) return AgentTaskRunResult(false, false, result.message, logs)
            delayForStep(chosenStep)
        }
        return AgentTaskRunResult(false, false, "已达到最大执行步数，请检查当前页面后继续。", logs)
    }

    private suspend fun captureOnce(): ScreenObservation {
        return withContext(Dispatchers.Default) { AiAgentAccessibilityService.captureFreshSnapshot() }
    }

    private suspend fun executeAndRecord(
        step: CloudAgentStep,
        currentApp: String,
        logs: MutableList<AgentTaskStepLog>,
    ): AgentExecutionResult {
        val result = withContext(Dispatchers.Main) { AiAgentAccessibilityService.executeStep(step) }
        logs += AgentTaskStepLog(logs.size + 1, currentApp, step, result)
        return result
    }

    private suspend fun delayForStep(step: CloudAgentStep) {
        delay(step.durationMs?.coerceIn(MIN_STEP_DELAY_MS, MAX_STEP_DELAY_MS) ?: DEFAULT_STEP_DELAY_MS)
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
            reason = "本地控制器：目标应用明确，当前不在目标应用，先打开目标应用。",
            riskLevel = "low",
            requiresConfirmation = false,
        )
    }

    private fun chooseAction(
        goal: String,
        snapshot: AgentScreenSnapshot,
        cloudStep: CloudAgentStep,
        memory: AgentRunMemory,
    ): CloudAgentStep? {
        if (cloudStep.type != "need_user_help" && !memory.isLikelyRepeated(cloudStep)) {
            return cloudStep
        }
        if (cloudStep.type != "need_user_help") memory.repeatedCloudRejects += 1
        val candidates = buildLocalCandidates(goal, snapshot, memory)
        return candidates.firstOrNull { !memory.isLikelyRepeated(it) }
    }

    private fun buildLocalCandidates(
        goal: String,
        snapshot: AgentScreenSnapshot,
        memory: AgentRunMemory,
    ): List<CloudAgentStep> {
        val keywords = extractGoalKeywords(goal, memory.targetApp)
        val candidates = mutableListOf<CloudAgentStep>()

        bestClickableMatch(snapshot, keywords)?.let { node ->
            candidates += CloudAgentStep(
                type = "tap_node",
                targetNodeId = node.id,
                targetText = node.text,
                reason = "本地纠错：屏幕上出现了与目标最接近的可点击文本“${node.text.take(18)}”。",
                riskLevel = "low",
                requiresConfirmation = false,
            )
        }

        bestNavigationEntry(snapshot, keywords)?.let { node ->
            candidates += CloudAgentStep(
                type = "tap_node",
                targetNodeId = node.id,
                targetText = node.text,
                reason = "本地纠错：当前没有直接目标，先进入可能的导航入口“${node.text.take(18)}”。",
                riskLevel = "low",
                requiresConfirmation = false,
            )
        }

        if (snapshot.inputNodes.isNotEmpty() && keywords.isNotEmpty() && memory.inputAttempts < MAX_INPUT_ATTEMPTS) {
            candidates += CloudAgentStep(
                type = "input_text",
                targetNodeId = snapshot.inputNodes.first().id,
                text = keywords.first(),
                reason = "本地纠错：发现输入框，输入核心关键词继续查找。",
                riskLevel = "low",
                requiresConfirmation = false,
            )
        }

        if (snapshot.scrollableNodes.isNotEmpty() && memory.scrollAttempts < MAX_SCROLL_ATTEMPTS) {
            candidates += CloudAgentStep(
                type = "scroll",
                targetNodeId = snapshot.scrollableNodes.first().id,
                direction = if (memory.scrollAttempts % 2 == 0) "down" else "up",
                reason = "本地纠错：当前屏幕没有明确目标，滚动扩大搜索范围。",
                riskLevel = "low",
                requiresConfirmation = false,
            )
        }

        if (memory.waitAttempts < MAX_WAIT_ATTEMPTS && snapshot.nodeCount <= LOW_SIGNAL_NODE_COUNT) {
            candidates += CloudAgentStep(
                type = "wait",
                durationMs = 900L,
                reason = "本地纠错：当前页面节点较少，可能仍在加载，先等待后重新观察。",
                riskLevel = "low",
                requiresConfirmation = false,
            )
        }

        if (memory.backAttempts < MAX_BACK_ATTEMPTS && memory.repeatedCloudRejects >= 2) {
            candidates += CloudAgentStep(
                type = "back",
                reason = "本地纠错：连续重复卡住，返回上一层重新选择路径。",
                riskLevel = "low",
                requiresConfirmation = false,
            )
        }

        return candidates
    }

    private fun bestClickableMatch(snapshot: AgentScreenSnapshot, keywords: List<String>): AgentScreenNode? {
        if (keywords.isEmpty()) return null
        return snapshot.clickableNodes
            .filter { it.text.isNotBlank() }
            .maxByOrNull { node -> scoreNodeText(node.text, keywords) }
            ?.takeIf { scoreNodeText(it.text, keywords) >= DIRECT_MATCH_SCORE }
    }

    private fun bestNavigationEntry(snapshot: AgentScreenSnapshot, keywords: List<String>): AgentScreenNode? {
        val navWords = listOf("搜索", "查找", "发现", "首页", "通讯录", "联系人", "我的", "我", "频道", "分类", "更多", "菜单", "search")
        return snapshot.clickableNodes
            .filter { it.text.isNotBlank() }
            .map { node -> node to (scoreNodeText(node.text, keywords) + scoreNodeText(node.text, navWords)) }
            .filter { (_, score) -> score >= NAVIGATION_SCORE }
            .maxByOrNull { (_, score) -> score }
            ?.first
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

    private fun extractGoalKeywords(goal: String, targetApp: TargetApp?): List<String> {
        var text = goal.trim()
        targetApp?.aliases.orEmpty().forEach { text = text.replace(it, "", ignoreCase = true) }
        fillerWords.forEach { text = text.replace(it, "", ignoreCase = true) }
        val compact = text.replace(Regex("[，。,.、\\s]+"), "").take(24)
        val tokens = Regex("[\\p{L}\\p{N}]{2,}").findAll(text).map { it.value.take(24) }.toList()
        return (listOf(compact) + tokens).filter { it.isNotBlank() }.distinct().take(4)
    }

    private fun normalize(value: String): String {
        return value.lowercase().replace(Regex("[\\s　，。,.、:：/\\-]+"), "")
    }

    private fun detectTargetApp(goal: String): TargetApp? {
        val clean = normalize(goal)
        return TARGET_APPS.firstOrNull { item -> item.aliases.any { alias -> clean.contains(normalize(alias)) } }
    }

    private data class TargetApp(
        val label: String,
        val packageName: String,
        val aliases: List<String>,
    )

    private data class AgentRunMemory(
        val goal: String,
        val targetApp: TargetApp?,
        var openAppAttempts: Int = 0,
        var inputAttempts: Int = 0,
        var scrollAttempts: Int = 0,
        var waitAttempts: Int = 0,
        var backAttempts: Int = 0,
        var repeatedCloudRejects: Int = 0,
        private var lastSnapshotSignature: String = "",
        private val recentStepKeys: MutableList<String> = mutableListOf(),
    ) {
        fun observe(snapshot: AgentScreenSnapshot) {
            lastSnapshotSignature = listOf(
                snapshot.currentApp,
                snapshot.texts.take(8).joinToString("|"),
                snapshot.clickableNodes.take(8).joinToString("|") { it.text },
            ).joinToString("#")
        }

        fun remember(step: CloudAgentStep, result: AgentExecutionResult) {
            recentStepKeys += stepKey(step)
            if (recentStepKeys.size > 6) recentStepKeys.removeAt(0)
            if (step.type == "input_text") inputAttempts += 1
            if (step.type == "scroll" || step.type == "swipe") scrollAttempts += 1
            if (step.type == "wait") waitAttempts += 1
            if (step.type == "back") backAttempts += 1
            if (!result.ok) repeatedCloudRejects += 1 else repeatedCloudRejects = 0
        }

        fun isLikelyRepeated(step: CloudAgentStep): Boolean {
            return recentStepKeys.count { it == stepKey(step) } >= 1
        }

        private fun stepKey(step: CloudAgentStep): String {
            return listOf(step.type, step.targetNodeId, step.targetText, step.text, step.direction).joinToString("|")
        }
    }

    companion object {
        private const val DEFAULT_MAX_STEPS = 8
        private const val DEFAULT_STEP_DELAY_MS = 1_050L
        private const val MIN_STEP_DELAY_MS = 650L
        private const val MAX_STEP_DELAY_MS = 2_500L
        private const val MAX_OPEN_APP_ATTEMPTS = 1
        private const val MAX_INPUT_ATTEMPTS = 2
        private const val MAX_SCROLL_ATTEMPTS = 3
        private const val MAX_WAIT_ATTEMPTS = 2
        private const val MAX_BACK_ATTEMPTS = 1
        private const val LOW_SIGNAL_NODE_COUNT = 8
        private const val DIRECT_MATCH_SCORE = 5
        private const val NAVIGATION_SCORE = 4

        private val fillerWords = listOf(
            "帮我", "替我", "请", "打开", "开启", "启动", "找到", "进入", "搜索", "查找", "一下", "看看", "去", "里", "里面", "应用", "app",
        )

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
