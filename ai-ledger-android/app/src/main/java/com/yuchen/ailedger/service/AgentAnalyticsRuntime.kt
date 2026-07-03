package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.AgentAnalyticsDatabase
import com.yuchen.ailedger.data.AgentAnalyticsOwnerRuntime
import com.yuchen.ailedger.data.AgentAnalyticsRepository
import com.yuchen.ailedger.data.AgentChatCallWrite
import com.yuchen.ailedger.data.AgentModelCallWrite
import com.yuchen.ailedger.data.AgentTaskWrite
import com.yuchen.ailedger.data.AgentUsageCounter
import com.yuchen.ailedger.model.AgentTokenAccuracy
import com.yuchen.ailedger.model.AgentTokenUsage
import java.util.LinkedHashMap
import org.json.JSONObject

/**
 * 智能体统计唯一运行时入口。
 *
 * 这里只旁路收集已经发生的事实。任务在创建时固定 ownerStorageKey，账号切换不会把
 * 正在执行的任务、模型调用或终态写进另一个账号。
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
        val ownerStorageKey: String,
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
        var lastActionText: String = "",
        val knownAppLabels: MutableSet<String> = linkedSetOf(),
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

    private data class RepositoryCache(
        val ownerStorageKey: String,
        val repository: AgentAnalyticsRepository,
    )

    private val lock = Any()
    private val tasks = mutableMapOf<Long, RunningTask>()
    private val cursors = LinkedHashMap<Long, ProgressCursor>(32, 0.75f, true)

    @Volatile
    private var repositoryCache: RepositoryCache? = null

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

                appendedLogs(cursor.logs, progress.logs).forEach { entry ->
                    when {
                        entry.startsWith("结果：") -> recordActionResultLocked(
                            task = task,
                            actionText = task.lastActionText.ifBlank { progress.currentAction },
                            success = progress.status != "重新规划",
                        )
                        entry.startsWith("确认：继续执行") -> task.confirmationsAccepted += 1L
                        entry.startsWith("输入：用户已提供内容") -> task.userInputsSubmitted += 1L
                        entry.startsWith("恢复：") -> task.takeoverResumes += 1L
                        isActionLogEntry(entry) -> task.lastActionText = entry.trim().take(MAX_ACTION_TEXT_CHARS)
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
            synchronized(lock) {
                val currentProgress = AgentRuntimeController.progress.value.takeIf { it.taskId == taskId }
                val fallbackGoal = currentProgress
                    ?.logs
                    ?.firstOrNull { it.startsWith("目标：") }
                    ?.removePrefix("目标：")
                    .orEmpty()
                val task = tasks[taskId] ?: ensureTaskLocked(
                    taskId = taskId,
                    goal = fallbackGoal,
                    startedAtMillis = currentProgress?.updatedAt ?: System.currentTimeMillis(),
                )

                val nextModelTurns = maxOf(task.modelTurns, modelTurns.toLong().coerceAtLeast(0L))
                val nextExecutedActions = maxOf(task.executedActions, executedActions.toLong().coerceAtLeast(0L))
                val nextReobservations = maxOf(task.reobservations, reobservations.toLong().coerceAtLeast(0L))
                val nextObservations = maxOf(task.observations, nextModelTurns + nextReobservations)
                val nextRejectedPlans = maxOf(task.rejectedPlans, rejectedPlans.toLong().coerceAtLeast(0L))
                val nextExecutionFailures = maxOf(task.executionFailures, executionFailures.toLong().coerceAtLeast(0L))

                if (
                    nextModelTurns == task.modelTurns &&
                    nextExecutedActions == task.executedActions &&
                    nextReobservations == task.reobservations &&
                    nextObservations == task.observations &&
                    nextRejectedPlans == task.rejectedPlans &&
                    nextExecutionFailures == task.executionFailures
                ) return@synchronized

                task.modelTurns = nextModelTurns
                task.executedActions = nextExecutedActions
                task.reobservations = nextReobservations
                task.observations = nextObservations
                task.rejectedPlans = nextRejectedPlans
                task.executionFailures = nextExecutionFailures
            }
        }
    }

    fun recordChatTransport(
        payload: JSONObject,
        response: JSONObject?,
        success: Boolean,
        durationMs: Long,
        requestBytes: Long = -1L,
        responseBytes: Long = -1L,
        ownerStorageKey: String? = null,
    ) {
        runCatching {
            val usage = if (success) {
                AgentAnalyticsTokenParser.resolveUsage(payload, response)
            } else {
                AgentAnalyticsTokenParser.parseProviderUsage(response) ?: AgentTokenUsage(
                    accuracy = AgentTokenAccuracy.Estimated,
                )
            }
            repository(ownerStorageKey)?.recordChatCall(
                AgentChatCallWrite(
                    modelCall = AgentModelCallWrite(
                        source = "chat",
                        modelId = AgentAnalyticsTokenParser.modelId(payload, response),
                        modelLabel = AgentAnalyticsTokenParser.modelLabel(payload, response),
                        success = success,
                        usage = usage,
                        latencyMs = durationMs.coerceAtLeast(0L),
                        requestBytes = requestBytes.takeIf { it >= 0L }
                            ?: AgentAnalyticsTokenParser.requestBytes(payload),
                        responseBytes = responseBytes.takeIf { it >= 0L }
                            ?: AgentAnalyticsTokenParser.responseBytes(response),
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

            val task = if (taskId > 0L) {
                synchronized(lock) {
                    tasks[taskId] ?: run {
                        val currentProgress = AgentRuntimeController.progress.value.takeIf { it.taskId == taskId }
                        val fallbackGoal = currentProgress
                            ?.logs
                            ?.firstOrNull { it.startsWith("目标：") }
                            ?.removePrefix("目标：")
                            .orEmpty()
                        ensureTaskLocked(
                            taskId = taskId,
                            goal = payload.optString("goal").ifBlank { fallbackGoal },
                            startedAtMillis = currentProgress?.updatedAt ?: System.currentTimeMillis(),
                        )
                    }
                }
            } else {
                null
            }
            val ownerKey = task?.ownerStorageKey ?: currentOwnerStorageKey()
            repository(ownerKey)?.recordModelCall(
                AgentModelCallWrite(
                    taskId = taskId.takeIf { it > 0L },
                    source = "visual_agent",
                    modelId = modelId,
                    modelLabel = modelLabel,
                    success = success,
                    usage = usage,
                    latencyMs = durationMs.coerceAtLeast(0L),
                    requestBytes = requestBytes.coerceAtLeast(0L),
                    responseBytes = responseBytes.coerceAtLeast(0L),
                ),
            )
            if (task == null) return@runCatching

            synchronized(lock) {
                task.modelCalls += 1L
                if (!success) task.modelFailures += 1L
                task.inputTokens = safeAdd(task.inputTokens, usage.inputTokens)
                task.outputTokens = safeAdd(task.outputTokens, usage.outputTokens)
                task.reasoningTokens = safeAdd(task.reasoningTokens, usage.reasoningTokens)
                task.cachedInputTokens = safeAdd(task.cachedInputTokens, usage.cachedInputTokens)
                task.totalTokens = safeAdd(task.totalTokens, usage.normalizedTotal)
                if (usage.accuracy == AgentTokenAccuracy.Provider) {
                    task.providerTokens = safeAdd(task.providerTokens, usage.normalizedTotal)
                } else {
                    task.estimatedTokens = safeAdd(task.estimatedTokens, usage.normalizedTotal)
                }
                task.requestBytes = safeAdd(task.requestBytes, requestBytes.toLong())
                task.responseBytes = safeAdd(task.responseBytes, responseBytes.toLong())
                task.modelLatencyMs = safeAdd(task.modelLatencyMs, durationMs)
            }
        }
    }

    private fun ensureTaskLocked(taskId: Long, goal: String, startedAtMillis: Long): RunningTask {
        return tasks.getOrPut(taskId) {
            val ownerKey = currentOwnerStorageKey()
            RunningTask(
                taskId = taskId,
                ownerStorageKey = ownerKey,
                goal = goal.trim().take(MAX_GOAL_CHARS),
                startedAtMillis = startedAtMillis.coerceAtLeast(1L),
            ).also { task ->
                repository(ownerKey)?.beginTask(taskId, task.goal, task.startedAtMillis)
            }
        }.also { task ->
            if (task.goal.isBlank() && goal.isNotBlank()) task.goal = goal.trim().take(MAX_GOAL_CHARS)
            task.startedAtMillis = minOf(task.startedAtMillis, startedAtMillis.coerceAtLeast(1L))
        }
    }

    private fun recordActionResultLocked(task: RunningTask, actionText: String, success: Boolean) {
        val clean = actionText.trim().ifBlank { "未知动作" }
        val parts = clean.split(" · ").map(String::trim).filter(String::isNotBlank)
        val actionLabel = parts.firstOrNull().orEmpty().ifBlank { "未知动作" }.take(MAX_LABEL_CHARS)
        val actionKey = normalizeKey(actionLabel)
        val secondPart = parts.getOrNull(1)?.take(MAX_LABEL_CHARS)
        val isOpenApp = actionLabel.contains("打开应用") ||
            actionLabel.contains("启动应用") ||
            actionLabel.equals("open app", ignoreCase = true)
        if (isOpenApp && !secondPart.isNullOrBlank()) task.knownAppLabels += secondPart
        val appLabel = secondPart?.takeIf { it in task.knownAppLabels }

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
        repository(task.ownerStorageKey)?.finishTask(
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

    private fun isActionLogEntry(entry: String): Boolean {
        val clean = entry.trim()
        if (clean.isBlank()) return false
        return LOG_PREFIXES.none { prefix -> clean.startsWith(prefix) }
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
        "已完成", "执行失败", "失败", "已暂停", "等待确认", "已手动停止", "已达上限",
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

    private fun currentOwnerStorageKey(): String {
        val context = AiLedgerApplication.contextOrNull() ?: return "guest:pending"
        return AgentAnalyticsOwnerRuntime.currentStorageKey(context)
    }

    private fun repository(ownerStorageKey: String? = null): AgentAnalyticsRepository? {
        val context = AiLedgerApplication.contextOrNull() ?: return null
        val key = ownerStorageKey?.trim().takeUnless { it.isNullOrBlank() }
            ?: AgentAnalyticsOwnerRuntime.currentStorageKey(context)
        val databaseName = AgentAnalyticsOwnerRuntime.databaseNameForStorageKey(key)
        if (!AgentAnalyticsDatabase.isAvailable(databaseName)) {
            synchronized(lock) {
                if (repositoryCache?.ownerStorageKey == key) repositoryCache = null
            }
            return null
        }
        repositoryCache?.takeIf { it.ownerStorageKey == key }?.let { return it.repository }
        return runCatching { AgentAnalyticsRepository.get(context, key) }
            .getOrNull()
            ?.also { repository -> repositoryCache = RepositoryCache(key, repository) }
    }

    private fun safeAdd(left: Long, right: Long): Long {
        val safeLeft = left.coerceAtLeast(0L)
        val safeRight = right.coerceAtLeast(0L)
        return if (Long.MAX_VALUE - safeLeft < safeRight) Long.MAX_VALUE else safeLeft + safeRight
    }

    private val LOG_PREFIXES = listOf(
        "目标：", "结果：", "诊断：", "模型：", "模型续：", "等待确认：", "确认：",
        "等待输入：", "输入：", "接管：", "恢复：", "完成：", "失败：", "暂停：", "停止：", "上限：",
    )
    private const val MAX_CURSOR_COUNT = 64
    private const val MAX_GOAL_CHARS = 240
    private const val MAX_RESULT_CHARS = 320
    private const val MAX_ACTION_TEXT_CHARS = 180
    private const val MAX_KEY_CHARS = 120
    private const val MAX_LABEL_CHARS = 80
}
