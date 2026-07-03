package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentAnalyticsTotals
import com.yuchen.ailedger.model.AgentCapabilityAnalytics
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.model.AgentModelAnalytics
import com.yuchen.ailedger.model.AgentTaskAnalytics
import com.yuchen.ailedger.model.AgentTokenAccuracy
import com.yuchen.ailedger.model.AgentTokenUsage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal data class AgentModelCallWrite(
    val taskId: Long? = null,
    val source: String,
    val occurredAtMillis: Long = System.currentTimeMillis(),
    val modelId: String,
    val modelLabel: String,
    val success: Boolean,
    val usage: AgentTokenUsage,
    val latencyMs: Long,
    val requestBytes: Long,
    val responseBytes: Long,
)

internal data class AgentChatCallWrite(
    val modelCall: AgentModelCallWrite,
    val webSearchUsed: Boolean,
    val imageRequest: Boolean,
    val toolKeys: List<Pair<String, String>> = emptyList(),
)

internal data class AgentUsageCounter(
    val displayName: String,
    val uses: Long,
    val successes: Long,
    val failures: Long,
)

internal data class AgentTaskWrite(
    val taskId: Long,
    val goal: String,
    val status: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val latestResult: String,
    val modelCalls: Long,
    val modelFailures: Long,
    val modelTurns: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val cachedInputTokens: Long,
    val totalTokens: Long,
    val providerTokens: Long,
    val estimatedTokens: Long,
    val requestBytes: Long,
    val responseBytes: Long,
    val modelLatencyMs: Long,
    val executedActions: Long,
    val successfulActions: Long,
    val failedActions: Long,
    val observations: Long,
    val reobservations: Long,
    val rejectedPlans: Long,
    val executionFailures: Long,
    val confirmationRequests: Long,
    val confirmationsAccepted: Long,
    val userInputRequests: Long,
    val userInputsSubmitted: Long,
    val userTakeovers: Long,
    val takeoverResumes: Long,
    val appUsage: Map<String, AgentUsageCounter>,
    val actionUsage: Map<String, AgentUsageCounter>,
)

internal data class AgentTaskMetricsPatch(
    val modelTurns: Long,
    val executedActions: Long,
    val observations: Long,
    val reobservations: Long,
    val rejectedPlans: Long,
    val executionFailures: Long,
)

/**
 * 单一所有者的智能体统计仓库。
 *
 * 每个 Supabase userId 对应独立 Room 文件；访客继续使用历史 agent_analytics.db。
 * 仓库实例按 owner storageKey 缓存，账号切换不会复用另一个账号的 DAO 或 StateFlow。
 */
class AgentAnalyticsRepository private constructor(
    context: Context,
    val ownerStorageKey: String,
) {
    private val databaseName = AgentAnalyticsOwnerRuntime.databaseNameForStorageKey(ownerStorageKey)
    private val dao = AgentAnalyticsDatabase.get(context, databaseName).analyticsDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    val state: StateFlow<AgentAnalyticsSnapshot> = combine(
        dao.observeDailyActivity(),
        dao.observeRecentTasks(RECENT_TASK_STATE_LIMIT),
        dao.observeModelUsage(),
        dao.observeCapabilityUsage(),
        dao.observeLongestTaskDurationMs(),
    ) { daily, tasks, models, capabilities, longestTaskDurationMs ->
        buildSnapshot(daily, tasks, models, capabilities, longestTaskDurationMs)
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        initialValue = AgentAnalyticsSnapshot(),
    )

    init {
        scope.launch {
            runCatching {
                writeMutex.withLock {
                    val now = System.currentTimeMillis()
                    recoverInterruptedTasksLocked(now)
                    dao.deleteOldTokenEvents(now - TOKEN_EVENT_RETENTION_MS)
                    dao.pruneTasks(MAX_TASK_HISTORY)
                    dao.pruneDailyActivity(dateKey(now - DAILY_RETENTION_MS))
                }
            }
        }
    }

    internal fun beginTask(taskId: Long, goal: String, startedAtMillis: Long) {
        if (taskId <= 0L) return
        scope.launch {
            writeMutex.withLock {
                if (dao.getTask(taskId) != null) return@withLock
                val start = startedAtMillis.coerceAtLeast(1L)
                dao.upsertTask(
                    AgentTaskAnalyticsEntity(
                        taskId = taskId,
                        goal = sanitizeStoredText(goal, MAX_GOAL_CHARS),
                        status = STATUS_RUNNING,
                        startedAtMillis = start,
                    ),
                )
                updateDayLocked(start) { day ->
                    day.copy(agentTasks = safeAdd(day.agentTasks, 1L))
                }
            }
        }
    }

    internal fun recordChatCall(write: AgentChatCallWrite) {
        scope.launch {
            writeMutex.withLock {
                recordModelCallLocked(write.modelCall)
                val at = write.modelCall.occurredAtMillis.coerceAtLeast(1L)
                updateDayLocked(at) { day ->
                    day.copy(
                        chatCalls = safeAdd(day.chatCalls, if (write.modelCall.success) 1L else 0L),
                        chatFailures = safeAdd(day.chatFailures, if (write.modelCall.success) 0L else 1L),
                        webSearches = safeAdd(day.webSearches, if (write.webSearchUsed) 1L else 0L),
                        imageRequests = safeAdd(day.imageRequests, if (write.imageRequest) 1L else 0L),
                    )
                }
                if (write.webSearchUsed) {
                    updateCapabilityLocked("feature", "web_search", "联网搜索", write.modelCall.success, at)
                }
                if (write.imageRequest) {
                    updateCapabilityLocked("feature", "image_understanding", "视觉理解", write.modelCall.success, at)
                }
                write.toolKeys.distinctBy { it.first }.forEach { (key, label) ->
                    updateCapabilityLocked("tool", key, label, write.modelCall.success, at)
                }
            }
        }
    }

    internal fun recordModelCall(write: AgentModelCallWrite) {
        scope.launch {
            writeMutex.withLock { recordModelCallLocked(write) }
        }
    }

    internal fun finishTask(write: AgentTaskWrite) {
        if (write.taskId <= 0L) return
        scope.launch {
            writeMutex.withLock {
                val existing = dao.getTask(write.taskId) ?: AgentTaskAnalyticsEntity(
                    taskId = write.taskId,
                    goal = sanitizeStoredText(write.goal, MAX_GOAL_CHARS),
                    status = STATUS_RUNNING,
                    startedAtMillis = write.startedAtMillis.coerceAtLeast(1L),
                )
                val merged = existing.merge(write)
                val firstTerminalWrite = existing.endedAtMillis == null
                val completedNow = firstTerminalWrite && isCompletedStatus(merged.status)
                val assistedCompletion = completedNow && merged.hasUserIntervention()
                dao.upsertTask(merged)
                updateDayLocked(write.endedAtMillis.coerceAtLeast(merged.startedAtMillis)) { day ->
                    val outcome = if (firstTerminalWrite) outcomeDelta(merged.status) else OutcomeDelta()
                    day.copy(
                        completedTasks = safeAdd(day.completedTasks, outcome.completed),
                        autonomousCompletedTasks = safeAdd(
                            day.autonomousCompletedTasks,
                            if (completedNow && !assistedCompletion) 1L else 0L,
                        ),
                        assistedCompletedTasks = safeAdd(
                            day.assistedCompletedTasks,
                            if (assistedCompletion) 1L else 0L,
                        ),
                        failedTasks = safeAdd(day.failedTasks, outcome.failed),
                        pausedTasks = safeAdd(day.pausedTasks, outcome.paused),
                        cancelledTasks = safeAdd(day.cancelledTasks, outcome.cancelled),
                        budgetExceededTasks = safeAdd(day.budgetExceededTasks, outcome.budgetExceeded),
                        agentModelTurns = safeAdd(day.agentModelTurns, positiveDelta(merged.modelTurns, existing.modelTurns)),
                        taskDurationMs = safeAdd(day.taskDurationMs, positiveDelta(merged.durationMs, existing.durationMs)),
                        executedActions = safeAdd(day.executedActions, positiveDelta(merged.executedActions, existing.executedActions)),
                        successfulActions = safeAdd(day.successfulActions, positiveDelta(merged.successfulActions, existing.successfulActions)),
                        failedActions = safeAdd(day.failedActions, positiveDelta(merged.failedActions, existing.failedActions)),
                        observations = safeAdd(day.observations, positiveDelta(merged.observations, existing.observations)),
                        reobservations = safeAdd(day.reobservations, positiveDelta(merged.reobservations, existing.reobservations)),
                        rejectedPlans = safeAdd(day.rejectedPlans, positiveDelta(merged.rejectedPlans, existing.rejectedPlans)),
                        executionFailures = safeAdd(day.executionFailures, positiveDelta(merged.executionFailures, existing.executionFailures)),
                        confirmationRequests = safeAdd(day.confirmationRequests, positiveDelta(merged.confirmationRequests, existing.confirmationRequests)),
                        confirmationsAccepted = safeAdd(day.confirmationsAccepted, positiveDelta(merged.confirmationsAccepted, existing.confirmationsAccepted)),
                        userInputRequests = safeAdd(day.userInputRequests, positiveDelta(merged.userInputRequests, existing.userInputRequests)),
                        userInputsSubmitted = safeAdd(day.userInputsSubmitted, positiveDelta(merged.userInputsSubmitted, existing.userInputsSubmitted)),
                        userTakeovers = safeAdd(day.userTakeovers, positiveDelta(merged.userTakeovers, existing.userTakeovers)),
                        takeoverResumes = safeAdd(day.takeoverResumes, positiveDelta(merged.takeoverResumes, existing.takeoverResumes)),
                    )
                }
                if (firstTerminalWrite) {
                    write.actionUsage.forEach { (key, counter) ->
                        updateCapabilityLocked(
                            kind = "action",
                            key = key,
                            displayName = counter.displayName,
                            uses = counter.uses,
                            successes = counter.successes,
                            failures = counter.failures,
                            atMillis = merged.endedAtMillis ?: write.endedAtMillis,
                        )
                    }
                    write.appUsage.forEach { (key, counter) ->
                        updateCapabilityLocked(
                            kind = "app",
                            key = key,
                            displayName = counter.displayName,
                            uses = counter.uses,
                            successes = counter.successes,
                            failures = counter.failures,
                            atMillis = merged.endedAtMillis ?: write.endedAtMillis,
                        )
                    }
                }
                dao.pruneTasks(MAX_TASK_HISTORY)
            }
        }
    }

    internal fun patchTaskMetrics(taskId: Long, patch: AgentTaskMetricsPatch) {
        if (taskId <= 0L) return
        scope.launch {
            writeMutex.withLock {
                val existing = dao.getTask(taskId) ?: return@withLock
                val updated = existing.copy(
                    modelTurns = maxOf(existing.modelTurns, patch.modelTurns),
                    executedActions = maxOf(existing.executedActions, patch.executedActions),
                    observations = maxOf(existing.observations, patch.observations),
                    reobservations = maxOf(existing.reobservations, patch.reobservations),
                    rejectedPlans = maxOf(existing.rejectedPlans, patch.rejectedPlans),
                    executionFailures = maxOf(existing.executionFailures, patch.executionFailures),
                )
                if (updated == existing) return@withLock
                dao.upsertTask(updated)
                val at = updated.endedAtMillis ?: System.currentTimeMillis()
                updateDayLocked(at) { day ->
                    day.copy(
                        agentModelTurns = safeAdd(day.agentModelTurns, positiveDelta(updated.modelTurns, existing.modelTurns)),
                        executedActions = safeAdd(day.executedActions, positiveDelta(updated.executedActions, existing.executedActions)),
                        observations = safeAdd(day.observations, positiveDelta(updated.observations, existing.observations)),
                        reobservations = safeAdd(day.reobservations, positiveDelta(updated.reobservations, existing.reobservations)),
                        rejectedPlans = safeAdd(day.rejectedPlans, positiveDelta(updated.rejectedPlans, existing.rejectedPlans)),
                        executionFailures = safeAdd(day.executionFailures, positiveDelta(updated.executionFailures, existing.executionFailures)),
                    )
                }
            }
        }
    }

    private suspend fun recoverInterruptedTasksLocked(nowMillis: Long) {
        dao.getOpenTasks().forEach { task ->
            val aggregate = dao.getTaskTokenAggregate(task.taskId)
            val duration = maxOf(task.durationMs, (nowMillis - task.startedAtMillis).coerceAtLeast(0L))
            val recovered = task.copy(
                status = STATUS_INTERRUPTED,
                endedAtMillis = nowMillis,
                durationMs = duration,
                latestResult = "应用进程结束前任务没有写入终态",
                modelCalls = maxOf(task.modelCalls, aggregate.modelCalls),
                modelFailures = maxOf(task.modelFailures, aggregate.modelFailures),
                inputTokens = maxOf(task.inputTokens, aggregate.inputTokens),
                outputTokens = maxOf(task.outputTokens, aggregate.outputTokens),
                reasoningTokens = maxOf(task.reasoningTokens, aggregate.reasoningTokens),
                cachedInputTokens = maxOf(task.cachedInputTokens, aggregate.cachedInputTokens),
                totalTokens = maxOf(task.totalTokens, aggregate.totalTokens),
                providerTokens = maxOf(task.providerTokens, aggregate.providerTokens),
                estimatedTokens = maxOf(task.estimatedTokens, aggregate.estimatedTokens),
                requestBytes = maxOf(task.requestBytes, aggregate.requestBytes),
                responseBytes = maxOf(task.responseBytes, aggregate.responseBytes),
                modelLatencyMs = maxOf(task.modelLatencyMs, aggregate.modelLatencyMs),
            )
            dao.upsertTask(recovered)
            updateDayLocked(nowMillis) { day ->
                day.copy(
                    pausedTasks = safeAdd(day.pausedTasks, 1L),
                    taskDurationMs = safeAdd(day.taskDurationMs, positiveDelta(duration, task.durationMs)),
                )
            }
        }
    }

    private suspend fun recordModelCallLocked(write: AgentModelCallWrite) {
        val modelId = write.modelId.trim().ifBlank { "unknown" }.take(MAX_KEY_CHARS)
        val label = write.modelLabel.trim().ifBlank { modelId }.take(MAX_LABEL_CHARS)
        val usage = write.usage
        val total = usage.normalizedTotal.coerceAtLeast(0L)
        val provider = if (usage.accuracy == AgentTokenAccuracy.Provider) total else 0L
        val estimated = if (usage.accuracy == AgentTokenAccuracy.Estimated) total else 0L
        val at = write.occurredAtMillis.coerceAtLeast(1L)

        dao.insertTokenEvent(
            AgentTokenEventEntity(
                taskId = write.taskId?.takeIf { it > 0L },
                source = write.source.trim().ifBlank { "unknown" }.take(MAX_KEY_CHARS),
                occurredAtMillis = at,
                modelId = modelId,
                modelLabel = label,
                success = write.success,
                inputTokens = usage.inputTokens.coerceAtLeast(0L),
                outputTokens = usage.outputTokens.coerceAtLeast(0L),
                reasoningTokens = usage.reasoningTokens.coerceAtLeast(0L),
                cachedInputTokens = usage.cachedInputTokens.coerceAtLeast(0L),
                totalTokens = total,
                accuracy = usage.accuracy.name,
                latencyMs = write.latencyMs.coerceAtLeast(0L),
                requestBytes = write.requestBytes.coerceAtLeast(0L),
                responseBytes = write.responseBytes.coerceAtLeast(0L),
            ),
        )
        updateDayLocked(at) { day ->
            day.copy(
                modelCalls = safeAdd(day.modelCalls, 1L),
                modelFailures = safeAdd(day.modelFailures, if (write.success) 0L else 1L),
                inputTokens = safeAdd(day.inputTokens, usage.inputTokens.coerceAtLeast(0L)),
                outputTokens = safeAdd(day.outputTokens, usage.outputTokens.coerceAtLeast(0L)),
                reasoningTokens = safeAdd(day.reasoningTokens, usage.reasoningTokens.coerceAtLeast(0L)),
                cachedInputTokens = safeAdd(day.cachedInputTokens, usage.cachedInputTokens.coerceAtLeast(0L)),
                totalTokens = safeAdd(day.totalTokens, total),
                providerTokens = safeAdd(day.providerTokens, provider),
                estimatedTokens = safeAdd(day.estimatedTokens, estimated),
                modelLatencyMs = safeAdd(day.modelLatencyMs, write.latencyMs.coerceAtLeast(0L)),
                requestBytes = safeAdd(day.requestBytes, write.requestBytes.coerceAtLeast(0L)),
                responseBytes = safeAdd(day.responseBytes, write.responseBytes.coerceAtLeast(0L)),
            )
        }
        val old = dao.getModelUsage(modelId)
        dao.upsertModelUsage(
            (old ?: AgentModelUsageEntity(modelId, label)).copy(
                displayName = label,
                calls = safeAdd(old?.calls ?: 0L, 1L),
                failures = safeAdd(old?.failures ?: 0L, if (write.success) 0L else 1L),
                inputTokens = safeAdd(old?.inputTokens ?: 0L, usage.inputTokens.coerceAtLeast(0L)),
                outputTokens = safeAdd(old?.outputTokens ?: 0L, usage.outputTokens.coerceAtLeast(0L)),
                reasoningTokens = safeAdd(old?.reasoningTokens ?: 0L, usage.reasoningTokens.coerceAtLeast(0L)),
                cachedInputTokens = safeAdd(old?.cachedInputTokens ?: 0L, usage.cachedInputTokens.coerceAtLeast(0L)),
                totalTokens = safeAdd(old?.totalTokens ?: 0L, total),
                providerTokens = safeAdd(old?.providerTokens ?: 0L, provider),
                estimatedTokens = safeAdd(old?.estimatedTokens ?: 0L, estimated),
                latencyMs = safeAdd(old?.latencyMs ?: 0L, write.latencyMs.coerceAtLeast(0L)),
                requestBytes = safeAdd(old?.requestBytes ?: 0L, write.requestBytes.coerceAtLeast(0L)),
                responseBytes = safeAdd(old?.responseBytes ?: 0L, write.responseBytes.coerceAtLeast(0L)),
                firstUsedAtMillis = old?.firstUsedAtMillis?.takeIf { it > 0L } ?: at,
                lastUsedAtMillis = maxOf(old?.lastUsedAtMillis ?: 0L, at),
            ),
        )
    }

    private suspend fun updateDayLocked(
        atMillis: Long,
        transform: (AgentDailyActivityEntity) -> AgentDailyActivityEntity,
    ) {
        val safeAt = atMillis.coerceAtLeast(1L)
        val key = dateKey(safeAt)
        val old = dao.getDailyActivity(key) ?: AgentDailyActivityEntity(dateKey = key)
        val transformed = transform(old)
        dao.upsertDailyActivity(
            transformed.copy(
                firstActivityAtMillis = old.firstActivityAtMillis.takeIf { it > 0L }
                    ?.let { minOf(it, safeAt) }
                    ?: safeAt,
                lastActivityAtMillis = maxOf(old.lastActivityAtMillis, safeAt),
            ),
        )
    }

    private suspend fun updateCapabilityLocked(
        kind: String,
        key: String,
        displayName: String,
        success: Boolean,
        atMillis: Long,
    ) = updateCapabilityLocked(
        kind = kind,
        key = key,
        displayName = displayName,
        uses = 1L,
        successes = if (success) 1L else 0L,
        failures = if (success) 0L else 1L,
        atMillis = atMillis,
    )

    private suspend fun updateCapabilityLocked(
        kind: String,
        key: String,
        displayName: String,
        uses: Long,
        successes: Long,
        failures: Long,
        atMillis: Long,
    ) {
        if (uses <= 0L) return
        val normalizedKind = kind.trim().ifBlank { "feature" }.take(MAX_KEY_CHARS)
        val normalizedKey = key.trim().ifBlank { "unknown" }.take(MAX_KEY_CHARS)
        val label = sanitizeStoredText(displayName, MAX_LABEL_CHARS).ifBlank { normalizedKey }
        val old = dao.getCapabilityUsage(normalizedKind, normalizedKey)
        dao.upsertCapabilityUsage(
            (old ?: AgentCapabilityUsageEntity(normalizedKind, normalizedKey, label)).copy(
                displayName = label,
                uses = safeAdd(old?.uses ?: 0L, uses.coerceAtLeast(0L)),
                successes = safeAdd(old?.successes ?: 0L, successes.coerceAtLeast(0L)),
                failures = safeAdd(old?.failures ?: 0L, failures.coerceAtLeast(0L)),
                firstUsedAtMillis = old?.firstUsedAtMillis?.takeIf { it > 0L } ?: atMillis,
                lastUsedAtMillis = maxOf(old?.lastUsedAtMillis ?: 0L, atMillis),
            ),
        )
    }

    private fun buildSnapshot(
        dailyEntities: List<AgentDailyActivityEntity>,
        taskEntities: List<AgentTaskAnalyticsEntity>,
        modelEntities: List<AgentModelUsageEntity>,
        capabilityEntities: List<AgentCapabilityUsageEntity>,
        longestTaskDurationMs: Long,
    ): AgentAnalyticsSnapshot {
        val daily = dailyEntities.map(AgentDailyActivityEntity::toModel)
        val tasks = taskEntities.map(AgentTaskAnalyticsEntity::toModel)
        val models = modelEntities.map(AgentModelUsageEntity::toModel)
        val capabilities = capabilityEntities.map(AgentCapabilityUsageEntity::toModel)
        val terminalTasks = daily.safeSum {
            safeAdd(
                safeAdd(it.completedTasks, it.failedTasks),
                safeAdd(safeAdd(it.pausedTasks, it.cancelledTasks), it.budgetExceededTasks),
            )
        }
        val completed = daily.safeSum(AgentDailyActivity::completedTasks)
        val autonomous = daily.safeSum(AgentDailyActivity::autonomousCompletedTasks)
        val assisted = daily.safeSum(AgentDailyActivity::assistedCompletedTasks)
        val streaks = calculateStreaks(daily)
        return AgentAnalyticsSnapshot(
            dailyActivity = daily,
            recentTasks = tasks,
            modelUsage = models,
            capabilityUsage = capabilities,
            totals = AgentAnalyticsTotals(
                totalTokens = daily.safeSum(AgentDailyActivity::totalTokens),
                providerTokens = daily.safeSum(AgentDailyActivity::providerTokens),
                estimatedTokens = daily.safeSum(AgentDailyActivity::estimatedTokens),
                peakDailyTokens = daily.maxOfOrNull(AgentDailyActivity::totalTokens) ?: 0L,
                chatCalls = daily.safeSum(AgentDailyActivity::chatCalls),
                agentTasks = daily.safeSum(AgentDailyActivity::agentTasks),
                completedTasks = completed,
                autonomousCompletedTasks = autonomous,
                assistedCompletedTasks = assisted,
                taskSuccessRate = if (terminalTasks > 0L) completed.toFloat() / terminalTasks.toFloat() else 0f,
                autonomousCompletionRate = if (completed > 0L) autonomous.toFloat() / completed.toFloat() else 0f,
                executedActions = daily.safeSum(AgentDailyActivity::executedActions),
                agentModelTurns = daily.safeSum(AgentDailyActivity::agentModelTurns),
                modelCalls = daily.safeSum(AgentDailyActivity::modelCalls),
                totalTaskDurationMs = daily.safeSum(AgentDailyActivity::taskDurationMs),
                longestTaskDurationMs = longestTaskDurationMs.coerceAtLeast(0L),
                currentActiveStreakDays = streaks.first,
                longestActiveStreakDays = streaks.second,
            ),
            loaded = true,
        )
    }

    private fun calculateStreaks(daily: List<AgentDailyActivity>): Pair<Int, Int> {
        val activeDates = daily.asSequence()
            .filter { it.totalTokens > 0L || it.chatCalls > 0L || it.agentTasks > 0L || it.executedActions > 0L }
            .mapNotNull { runCatching { LocalDate.parse(it.dateKey) }.getOrNull() }
            .distinct()
            .sorted()
            .toList()
        if (activeDates.isEmpty()) return 0 to 0

        var longest = 1
        var running = 1
        for (index in 1 until activeDates.size) {
            running = if (activeDates[index - 1].plusDays(1L) == activeDates[index]) running + 1 else 1
            longest = maxOf(longest, running)
        }
        val today = LocalDate.now(ZoneId.systemDefault())
        val latest = activeDates.last()
        if (latest != today && latest != today.minusDays(1L)) return 0 to longest
        var current = 1
        for (index in activeDates.lastIndex downTo 1) {
            if (activeDates[index - 1].plusDays(1L) != activeDates[index]) break
            current += 1
        }
        return current to longest
    }

    private fun AgentTaskAnalyticsEntity.merge(write: AgentTaskWrite): AgentTaskAnalyticsEntity {
        val start = startedAtMillis.takeIf { it > 0L } ?: write.startedAtMillis.coerceAtLeast(1L)
        val end = maxOf(endedAtMillis ?: 0L, write.endedAtMillis).takeIf { it > 0L }
        return copy(
            goal = goal.ifBlank { sanitizeStoredText(write.goal, MAX_GOAL_CHARS) },
            status = write.status.trim().lowercase().ifBlank { "failed" },
            startedAtMillis = start,
            endedAtMillis = end,
            durationMs = end?.let { (it - start).coerceAtLeast(0L) } ?: durationMs,
            latestResult = sanitizeStoredText(write.latestResult, MAX_RESULT_CHARS),
            modelCalls = maxOf(modelCalls, write.modelCalls),
            modelFailures = maxOf(modelFailures, write.modelFailures),
            modelTurns = maxOf(modelTurns, write.modelTurns),
            inputTokens = maxOf(inputTokens, write.inputTokens),
            outputTokens = maxOf(outputTokens, write.outputTokens),
            reasoningTokens = maxOf(reasoningTokens, write.reasoningTokens),
            cachedInputTokens = maxOf(cachedInputTokens, write.cachedInputTokens),
            totalTokens = maxOf(totalTokens, write.totalTokens),
            providerTokens = maxOf(providerTokens, write.providerTokens),
            estimatedTokens = maxOf(estimatedTokens, write.estimatedTokens),
            requestBytes = maxOf(requestBytes, write.requestBytes),
            responseBytes = maxOf(responseBytes, write.responseBytes),
            modelLatencyMs = maxOf(modelLatencyMs, write.modelLatencyMs),
            executedActions = maxOf(executedActions, write.executedActions),
            successfulActions = maxOf(successfulActions, write.successfulActions),
            failedActions = maxOf(failedActions, write.failedActions),
            observations = maxOf(observations, write.observations),
            reobservations = maxOf(reobservations, write.reobservations),
            rejectedPlans = maxOf(rejectedPlans, write.rejectedPlans),
            executionFailures = maxOf(executionFailures, write.executionFailures),
            confirmationRequests = maxOf(confirmationRequests, write.confirmationRequests),
            confirmationsAccepted = maxOf(confirmationsAccepted, write.confirmationsAccepted),
            userInputRequests = maxOf(userInputRequests, write.userInputRequests),
            userInputsSubmitted = maxOf(userInputsSubmitted, write.userInputsSubmitted),
            userTakeovers = maxOf(userTakeovers, write.userTakeovers),
            takeoverResumes = maxOf(takeoverResumes, write.takeoverResumes),
            appUsageJson = write.appUsage.takeIf { it.isNotEmpty() }?.toJson() ?: appUsageJson,
            actionUsageJson = write.actionUsage.takeIf { it.isNotEmpty() }?.toJson() ?: actionUsageJson,
        )
    }

    private fun AgentTaskAnalyticsEntity.hasUserIntervention(): Boolean =
        confirmationRequests > 0L || userInputRequests > 0L || userTakeovers > 0L

    private data class OutcomeDelta(
        val completed: Long = 0L,
        val failed: Long = 0L,
        val paused: Long = 0L,
        val cancelled: Long = 0L,
        val budgetExceeded: Long = 0L,
    )

    private fun outcomeDelta(status: String): OutcomeDelta = when (status.lowercase()) {
        "completed", "已完成" -> OutcomeDelta(completed = 1L)
        "cancelled", "canceled", "已手动停止" -> OutcomeDelta(cancelled = 1L)
        "paused", "interrupted", "已暂停", "等待确认" -> OutcomeDelta(paused = 1L)
        "budget_exceeded", "已达上限" -> OutcomeDelta(budgetExceeded = 1L)
        else -> OutcomeDelta(failed = 1L)
    }

    private fun isCompletedStatus(status: String): Boolean = status.lowercase() in setOf("completed", "已完成")

    private fun Map<String, AgentUsageCounter>.toJson(): String = JSONObject().apply {
        this@toJson.forEach { (key, counter) ->
            put(key, JSONObject().apply {
                put("label", sanitizeStoredText(counter.displayName, MAX_LABEL_CHARS))
                put("uses", counter.uses.coerceAtLeast(0L))
                put("successes", counter.successes.coerceAtLeast(0L))
                put("failures", counter.failures.coerceAtLeast(0L))
            })
        }
    }.toString()

    private fun String.toUsageMap(): Map<String, Long> {
        val json = runCatching { JSONObject(this) }.getOrNull() ?: return emptyMap()
        return buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, json.optJSONObject(key)?.optLong("uses")?.coerceAtLeast(0L) ?: 0L)
            }
        }
    }

    private fun AgentDailyActivityEntity.toModel() = AgentDailyActivity(
        dateKey = dateKey,
        firstActivityAtMillis = firstActivityAtMillis,
        lastActivityAtMillis = lastActivityAtMillis,
        chatCalls = chatCalls,
        chatFailures = chatFailures,
        agentTasks = agentTasks,
        completedTasks = completedTasks,
        autonomousCompletedTasks = autonomousCompletedTasks,
        assistedCompletedTasks = assistedCompletedTasks,
        failedTasks = failedTasks,
        pausedTasks = pausedTasks,
        cancelledTasks = cancelledTasks,
        budgetExceededTasks = budgetExceededTasks,
        modelCalls = modelCalls,
        modelFailures = modelFailures,
        agentModelTurns = agentModelTurns,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        reasoningTokens = reasoningTokens,
        cachedInputTokens = cachedInputTokens,
        totalTokens = totalTokens,
        providerTokens = providerTokens,
        estimatedTokens = estimatedTokens,
        modelLatencyMs = modelLatencyMs,
        requestBytes = requestBytes,
        responseBytes = responseBytes,
        taskDurationMs = taskDurationMs,
        executedActions = executedActions,
        successfulActions = successfulActions,
        failedActions = failedActions,
        observations = observations,
        reobservations = reobservations,
        rejectedPlans = rejectedPlans,
        executionFailures = executionFailures,
        confirmationRequests = confirmationRequests,
        confirmationsAccepted = confirmationsAccepted,
        userInputRequests = userInputRequests,
        userInputsSubmitted = userInputsSubmitted,
        userTakeovers = userTakeovers,
        takeoverResumes = takeoverResumes,
        webSearches = webSearches,
        imageRequests = imageRequests,
    )

    private fun AgentTaskAnalyticsEntity.toModel() = AgentTaskAnalytics(
        taskId = taskId,
        goal = goal,
        status = status,
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
        durationMs = durationMs,
        latestResult = latestResult,
        modelCalls = modelCalls,
        modelFailures = modelFailures,
        modelTurns = modelTurns,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        reasoningTokens = reasoningTokens,
        cachedInputTokens = cachedInputTokens,
        totalTokens = totalTokens,
        providerTokens = providerTokens,
        estimatedTokens = estimatedTokens,
        requestBytes = requestBytes,
        responseBytes = responseBytes,
        modelLatencyMs = modelLatencyMs,
        executedActions = executedActions,
        successfulActions = successfulActions,
        failedActions = failedActions,
        observations = observations,
        reobservations = reobservations,
        rejectedPlans = rejectedPlans,
        executionFailures = executionFailures,
        confirmationRequests = confirmationRequests,
        confirmationsAccepted = confirmationsAccepted,
        userInputRequests = userInputRequests,
        userInputsSubmitted = userInputsSubmitted,
        userTakeovers = userTakeovers,
        takeoverResumes = takeoverResumes,
        appUsage = appUsageJson.toUsageMap(),
        actionUsage = actionUsageJson.toUsageMap(),
    )

    private fun AgentModelUsageEntity.toModel() = AgentModelAnalytics(
        modelId = modelId,
        displayName = displayName,
        calls = calls,
        failures = failures,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        reasoningTokens = reasoningTokens,
        cachedInputTokens = cachedInputTokens,
        totalTokens = totalTokens,
        providerTokens = providerTokens,
        estimatedTokens = estimatedTokens,
        latencyMs = latencyMs,
        requestBytes = requestBytes,
        responseBytes = responseBytes,
        firstUsedAtMillis = firstUsedAtMillis,
        lastUsedAtMillis = lastUsedAtMillis,
    )

    private fun AgentCapabilityUsageEntity.toModel() = AgentCapabilityAnalytics(
        kind = kind,
        key = capabilityKey,
        displayName = displayName,
        uses = uses,
        successes = successes,
        failures = failures,
        firstUsedAtMillis = firstUsedAtMillis,
        lastUsedAtMillis = lastUsedAtMillis,
    )

    companion object {
        private const val STATUS_RUNNING = "running"
        private const val STATUS_INTERRUPTED = "interrupted"
        private const val MAX_GOAL_CHARS = 240
        private const val MAX_RESULT_CHARS = 320
        private const val MAX_KEY_CHARS = 120
        private const val MAX_LABEL_CHARS = 80
        private const val MAX_TASK_HISTORY = 1_000
        private const val RECENT_TASK_STATE_LIMIT = 100
        private const val TOKEN_EVENT_RETENTION_MS = 730L * 24L * 60L * 60L * 1_000L
        private const val DAILY_RETENTION_MS = 1_825L * 24L * 60L * 60L * 1_000L

        private val SENSITIVE_PATTERNS = listOf(
            Regex("(?i)(password|passcode|verification\\s*code|otp|pin)\\s*[:=：]?\\s*[^\\s|,，;；]{2,}"),
            Regex("(密码|验证码|支付密码|口令)\\s*[:=：]?\\s*[^\\s|,，;；]{2,}"),
        )
        private val instances = mutableMapOf<String, AgentAnalyticsRepository>()

        fun get(context: Context): AgentAnalyticsRepository {
            val appContext = context.applicationContext
            val ownerKey = AgentAnalyticsOwnerRuntime.currentStorageKey(appContext)
            return get(appContext, ownerKey)
        }

        internal fun get(context: Context, ownerStorageKey: String): AgentAnalyticsRepository {
            val appContext = context.applicationContext
            val key = ownerStorageKey.trim().ifBlank {
                AgentAnalyticsOwnerRuntime.currentStorageKey(appContext)
            }
            return synchronized(instances) {
                instances[key] ?: AgentAnalyticsRepository(appContext, key).also { instances[key] = it }
            }
        }

        private fun dateKey(timestampMillis: Long): String = Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()

        private fun positiveDelta(next: Long, previous: Long): Long = (next - previous).coerceAtLeast(0L)

        private fun safeAdd(left: Long, right: Long): Long {
            val safeLeft = left.coerceAtLeast(0L)
            val safeRight = right.coerceAtLeast(0L)
            return if (Long.MAX_VALUE - safeLeft < safeRight) Long.MAX_VALUE else safeLeft + safeRight
        }

        private inline fun <T> List<T>.safeSum(selector: (T) -> Long): Long {
            var total = 0L
            forEach { total = safeAdd(total, selector(it)) }
            return total
        }

        private fun sanitizeStoredText(input: String, maxChars: Int): String {
            var output = input.trim()
            SENSITIVE_PATTERNS.forEach { pattern ->
                output = output.replace(pattern) { match ->
                    "${match.groupValues[1]}：[敏感内容已隐藏]"
                }
            }
            return output.take(maxChars)
        }
    }
}
