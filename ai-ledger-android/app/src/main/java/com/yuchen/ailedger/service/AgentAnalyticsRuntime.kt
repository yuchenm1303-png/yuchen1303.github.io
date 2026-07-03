package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.AgentAnalyticsRepository
import com.yuchen.ailedger.data.AgentChatCallWrite
import com.yuchen.ailedger.data.AgentModelCallWrite
import com.yuchen.ailedger.data.AgentTaskMetricsPatch
import com.yuchen.ailedger.data.AgentTaskWrite
import com.yuchen.ailedger.data.AgentUsageCounter
import com.yuchen.ailedger.model.AgentTokenAccuracy
import com.yuchen.ailedger.model.AgentTokenUsage
import java.util.LinkedHashMap
import org.json.JSONObject

/**
 * 智能体统计唯一运行时入口。
 *
 * 这里仅旁路收集已经发生的事实，不参与模型路由、动作选择、完成许可或无障碍模式切换。
 * 所有公开入口都吞掉统计自身异常，任何数据库或解析故障都不能影响聊天与智能体执行。
 */
internal object AgentAnalyticsRuntime {
    private data class MutableUsageCounter(
        var displayName: String,
        var uses: Long = 0L,
        var successes: Long = 0L,
        var failures: Long = 0L,
    )

    private data class RunningTask(
        val taskId: Long,
        var goal: String,
        var startedAtMillis: Long,
        var modelCalls: Long = 0L,
        var modelFailures: Long = 0L,
        var modelTurns: Long = 0L,
        var inputTokens: Long = 0L,
        var outputTokens: Long = 0L,
        var reasoningTokens: Long = 0L,
        var cachedInputTokens: Long = 0L,
        var totalTokens: Long = 0L,
        var providerTokens: Long = 0L,
        var estimatedTokens: Long = 0L,
        var requestBytes: Long = 0L,
        var responseBytes: Long = 0L,
        var modelLatencyMs: Long = 0L,
        var executedActions: Long = 0L,
        var successfulActions: Long = 0L,
        var failedActions: Long = 0L,
        var observations: Long = 0L,
        var reobservations: Long = 0L,
        var rejectedPlans: Long = 0L,
        var executionFailures: Long = 0L,
        var confirmationRequests: Long = 0L,
        var confirmationsAccepted: Long = 0L,
        var userInputRequests: Long = 0L,
        var userInputsSubmitted: Long = 0L,
        var userTakeovers: Long = 0L,
        var takeoverResumes: Long = 0L,
        val seenConfirmationIds: MutableSet<Long> = mutableSetOf(),
        val seenInputIds: MutableSet<Long> = mutableSetOf(),
        val actionUsage: MutableMap<String, MutableUsageCounter> = linkedMapOf(),
        val appUsage: MutableMap<String, MutableUsageCounter> = linkedMapOf(),
    )

    private data class ProgressCursor(
        var logs: List<String> = emptyList(),
        var takeoverPaused: Boolean = false,
        var finished: Boolean = false,
    )

    private val lock = Any()
    private val tasks = mutableMapOf<Long, RunningTask>()
    private val cursors = LinkedHashMap<Long, ProgressCursor>(32, 0.75f, true)

    @Volatile
    private var repositoryCache: AgentAnalyticsRepository? = null

    fun observeProgress(progress: AgentOverlayProgress) {
        if (progress.taskId <= 0L) return
        runCatching {
            synchronized(lock) {
                val task = ensureTaskLocked(
                    taskId = progress.taskId,
                    goal = progress.logs.firstOrNull { it.startsWith("目标：") }
                        ?.removePrefix("目标：")
                        .orEmpty(),
                    startedAtMillis = progress.updatedAt,
                )
                val cursor = cursors.getOrPut(progress.taskId) { ProgressCursor() }
                if (task.goal.isBlank()) {
                    task.goal = progress.logs.firstOrNull { it.startsWith("目标：") }
                        ?.removePrefix("目标：")
                        ?.trim()
                        .orEmpty()
                }

                progress.pendingConfirmation?.let { pending ->
                    if (task.seenConfirmationIds.add(pending.id)) task.confirmationRequests += 1L
                }
                progress.pendingUserInput?.let { pending ->
                    if (task.seenInputIds.add(pending.id)) task.userInputRequests += 1L
                }
                if (!cursor.takeoverPaused && progress.userTakeoverPaused) task.userTakeovers += 1L

                val newLogs = appendedLogs(cursor.logs, progress.logs)
                newLogs.forEach { entry ->
                    when {
                        entry.startsWith("结果：") -> recordActionResultLocked(
                            task = task,
                            actionText = progress.currentAction,
                            success = progress.status != "重新规划",
                        )
                        entry.startsWith("确认：继续执行") -> task.confirmationsAccepted += 1L
                        entry.startsWith("输入：用户已提供内容") -> task.userInputsSubmitted += 1L
                        entry.startsWith("恢复：") -> task.takeoverResumes += 1L
                    }
                }

                cursor.logs = progress.logs
                cursor.takeoverPaused = progress.userTakeoverPaused

                if (!progress.running && !cursor.finished && isTerminalStatus(progress.status)) {
                    cursor.finished = true
                    task.observations = maxOf(task.observations, task.modelTurns + task.reobservations)
                    finishTaskLocked(
                        task = task,
                        status = normalizeStatus(progress.status),
                        result = progress.lastResult,
                        endedAtMillis = progress.updatedAt,
                    )
                    tasks.remove(progress.taskId)
                }
                trimCursorsLocked()
            }
        }
    }

    fun updateVisualLoopMetrics(
        taskId: Long,
        modelTurns: Int,
        executedActions: Int,
        reobservations: Int,
        rejectedPlans: Int,
        executionFailures: Int,
    ) {
        if (taskId <= 0L) return
        runCatching {
            val patch = AgentTaskMetricsPatch(
                modelTurns = modelTurns.toLong().coerceAtLeast(0L),
                executedActions = executedActions.toLong().coerceAtLeast(0L),
                observations = (modelTurns.toLong() + reobservations.toLong()).coerceAtLeast(0L),
                reobservations = reobservations.toLong().coerceAtLeast(0L),
                rejectedPlans = rejectedPlans.toLong().coerceAtLeast(0L),
                executionFailures = executionFailures.toLong().coerceAtLeast(0L),
            )
            // 即使终态已经移出内存，也能补写数据库中的最终循环指标。
            repository()?.patchTaskMetrics(taskId, patch)
            synchronized(lock) {
                val task = tasks[taskId] ?: return@synchronized
                task.modelTurns = maxOf(task.modelTurns, patch.modelTurns)
                task.executedActions = maxOf(task.executedActions, patch.executedActions)
                task.reobservations = maxOf(task.reobservations, patch.reobservations)
                task.observations = maxOf(task.observations, patch.observations)
                task.rejectedPlans = maxOf(task.rejectedPlans, patch.rejectedPlans)
                task.executionFailures = maxOf(task.executionFailures, patch.executionFailures)
            }
        }
    }

    fun recordChatTransport(
        payload: JSONObject,
        response: JSONObject?,
        success: Boolean,
        durationMs: Long,
    ) {
        runCatching {
            val usage = if (success) {
                AgentAnalyticsTokenParser.resolveUsage(payload, response)
            } else {
                AgentAnalyticsTokenParser.parseProviderUsage(response) ?: AgentTokenUsage(
                    accuracy = AgentTokenAccuracy.Estimated,
                )
            }
            repository()?.recordChatCall(
                AgentChatCallWrite(
                    modelCall = AgentModelCallWrite(
                        source = "chat",
                        modelId = AgentAnalyticsTokenParser.modelId(payload, response),
                        modelLabel = AgentAnalyticsTokenParser.modelLabel(payload, response),
                        success = success,
                        usage = usage,
                        latencyMs = durationMs.coerceAtLeast(0L),
                        requestBytes = AgentAnalyticsTokenParser.requestBytes(payload),
                        responseBytes = AgentAnalyticsTokenParser.responseBytes(response),
                    ),
                    webSearchUsed = AgentAnalyticsTokenParser.webSearchUsed(response),
                    imageRequest = AgentAnalyticsTokenParser.imageRequest(payload),
                    toolKeys = AgentAnalyticsTokenParser.toolKeys(response),
                ),
            )
        }
    }

    fun recordVisualModelTransport(
        taskId: Long,
        payload: JSONObject,
        response: JSONObject?,
        success: Boolean,
        durationMs: Long,
        requestBytes: Long,
        responseBytes: Long,
    ) {
        runCatching {
            val usage = if (success) {
                AgentAnalyticsTokenParser.resolveUsage(payload, response)
            } else {
                AgentAnalyticsTokenParser.parseProviderUsage(response) ?: AgentTokenUsage(
                    accuracy = AgentTokenAccuracy.Estimated,
                )
            }
            val modelId = AgentAnalyticsTokenParser.modelId(payload, response)
                .takeUnless { it == "unknown" }
                ?: "gui_plus"
            val modelLabel = AgentAnalyticsTokenParser.modelLabel(payload, response)
                .takeUnless { it == "unknown" || it == "gui_plus" }
                ?: "GUI Plus"
            val call = AgentModelCallWrite(
                taskId = taskId.takeIf { it > 0L },
                source = "visual_agent",
                modelId = modelId,
                modelLabel = modelLabel,
                success = success,
                usage = usage,
                latencyMs = durationMs.coerceAtLeast(0L),
                requestBytes = requestBytes.coerceAtLeast(0L),
                responseBytes = responseBytes.coerceAtLeast(0L),
            )
            repository()?.recordModelCall(call)
            if (taskId <= 0L) return@runCatching
            synchronized(lock) {
                val task = tasks[taskId] ?: ensureTaskLocked(
                    taskId = taskId,
                    goal = payload.optString("goal"),
                    startedAtMillis = System.currentTimeMillis(),
                )
                task.modelCalls += 1L
                if (!success) task.modelFailures += 1L
                task.inputTokens += usage.inputTokens.coerceAtLeast(0L)
                task.outputTokens += usage.outputTokens.coerceAtLeast(0L)
                task.reasoningTokens += usage.reasoningTokens.coerceAtLeast(0L)
                task.cachedInputTokens += usage.cachedInputTokens.coerceAtLeast(0L)
                task.totalTokens += usage.normalizedTotal.coerceAtLeast(0L)
                if (usage.accuracy == AgentTokenAccuracy.Provider) {
                    task.providerTokens += usage.normalizedTotal.coerceAtLeast(0L)
                } else {
                    task.estimatedTokens += usage.normalizedTotal.coerceAtLeast(0L)
                }
                task.requestBytes += requestBytes.coerceAtLeast(0L)
                task.responseBytes += responseBytes.coerceAtLeast(0L)
                task.modelLatencyMs += durationMs.coerceAtLeast(0L)
            }
        }
    }

    private fun ensureTaskLocked(taskId: Long, goal: String, startedAtMillis: Long): RunningTask {
        return tasks.getOrPut(taskId) {
            RunningTask(
                taskId = taskId,
                goal = goal.trim().take(MAX_GOAL_CHARS),
                startedAtMillis = startedAtMillis,
            ).also { task -> repository()?.beginTask(taskId, task.goal, task.startedAtMillis) }
        }.also { task ->
            if (task.goal.isBlank() && goal.isNotBlank()) task.goal = goal.trim().take(MAX_GOAL_CHARS)
            task.startedAtMillis = minOf(task.startedAtMillis, startedAtMillis)
        }
    }

    private fun recordActionResultLocked(task: RunningTask, actionText: String, success: Boolean) {
        val clean = actionText.trim().ifBlank { "未知动作" }
        val parts = clean.split(" · ").map(String::trim).filter(String::isNotBlank)
        val actionLabel = parts.firstOrNull().orEmpty().ifBlank { "未知动作" }.take(MAX_LABEL_CHARS)
        val actionKey = normalizeKey(actionLabel)
        val appLabel = parts.getOrNull(1)
            ?.takeIf { it.isNotBlank() && !it.startsWith("节点 ") && !it.startsWith("输入 ") }
            ?.take(MAX_LABEL_CHARS)

        task.executedActions += 1L
        if (success) task.successfulActions += 1L else task.failedActions += 1L
        task.actionUsage.getOrPut(actionKey) { MutableUsageCounter(actionLabel) }.apply {
            uses += 1L
            if (success) successes += 1L else failures += 1L
        }
        appLabel?.let { label ->
            task.appUsage.getOrPut(normalizeKey(label)) { MutableUsageCounter(label) }.apply {
                uses += 1L
                if (success) successes += 1L else failures += 1L
            }
        }
    }

    private fun finishTaskLocked(
        task: RunningTask,
        status: String,
        result: String,
        endedAtMillis: Long,
    ) {
        repository()?.finishTask(
            AgentTaskWrite(
                taskId = task.taskId,
                goal = task.goal,
                status = status,
                startedAtMillis = task.startedAtMillis,
                endedAtMillis = maxOf(endedAtMillis, task.startedAtMillis),
                latestResult = result.trim().take(MAX_RESULT_CHARS),
                modelCalls = task.modelCalls,
                modelFailures = task.modelFailures,
                modelTurns = task.modelTurns,
                inputTokens = task.inputTokens,
                outputTokens = task.outputTokens,
                reasoningTokens = task.reasoningTokens,
                cachedInputTokens = task.cachedInputTokens,
                totalTokens = task.totalTokens,
                providerTokens = task.providerTokens,
                estimatedTokens = task.estimatedTokens,
                requestBytes = task.requestBytes,
                responseBytes = task.responseBytes,
                modelLatencyMs = task.modelLatencyMs,
                executedActions = task.executedActions,
                successfulActions = task.successfulActions,
                failedActions = task.failedActions,
                observations = task.observations,
                reobservations = task.reobservations,
                rejectedPlans = task.rejectedPlans,
                executionFailures = task.executionFailures,
                confirmationRequests = task.confirmationRequests,
                confirmationsAccepted = task.confirmationsAccepted,
                userInputRequests = task.userInputRequests,
                userInputsSubmitted = task.userInputsSubmitted,
                userTakeovers = task.userTakeovers,
                takeoverResumes = task.takeoverResumes,
                appUsage = task.appUsage.mapValues { (_, value) -> value.toImmutable() },
                actionUsage = task.actionUsage.mapValues { (_, value) -> value.toImmutable() },
            ),
        )
    }

    private fun MutableUsageCounter.toImmutable() = AgentUsageCounter(
        displayName = displayName,
        uses = uses,
        successes = successes,
        failures = failures,
    )

    private fun appendedLogs(previous: List<String>, current: List<String>): List<String> {
        if (previous.isEmpty()) return current
        val maxOverlap = minOf(previous.size, current.size)
        for (overlap in maxOverlap downTo 1) {
            if (previous.takeLast(overlap) == current.take(overlap)) return current.drop(overlap)
        }
        return current
    }

    private fun normalizeStatus(status: String): String = when (status.trim()) {
        "已完成" -> "completed"
        "执行失败", "失败", "任务异常" -> "failed"
        "已暂停", "等待确认", "等待输入", "用户接管" -> "paused"
        "已手动停止" -> "cancelled"
        "已达上限" -> "budget_exceeded"
        else -> status.trim().lowercase().ifBlank { "failed" }
    }

    private fun isTerminalStatus(status: String): Boolean = status.trim() in setOf(
        "已完成", "执行失败", "失败", "已暂停", "已手动停止", "已达上限",
    )

    private fun normalizeKey(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[^0-9a-zA-Z\\u4e00-\\u9fff._-]+"), "_")
        .trim('_')
        .take(MAX_KEY_CHARS)
        .ifBlank { "unknown" }

    private fun trimCursorsLocked() {
        while (cursors.size > MAX_CURSOR_COUNT) {
            val oldest = cursors.entries.firstOrNull() ?: break
            cursors.remove(oldest.key)
        }
    }

    private fun repository(): AgentAnalyticsRepository? {
        repositoryCache?.let { return it }
        val context = AiLedgerApplication.contextOrNull() ?: return null
        return runCatching { AgentAnalyticsRepository.get(context) }
            .getOrNull()
            ?.also { repositoryCache = it }
    }

    private const val MAX_CURSOR_COUNT = 64
    private const val MAX_GOAL_CHARS = 240
    private const val MAX_RESULT_CHARS = 320
    private const val MAX_KEY_CHARS = 120
    private const val MAX_LABEL_CHARS = 80
}
