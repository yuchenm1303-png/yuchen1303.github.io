package com.yuchen.ailedger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.model.AgentTaskAnalytics
import org.json.JSONObject

@Entity(tableName = "agent_daily_activity")
data class AgentDailyActivityEntity(
    @PrimaryKey val dateKey: String,
    val firstActivityAtMillis: Long = 0L,
    val lastActivityAtMillis: Long = 0L,
    val chatCalls: Long = 0L,
    val chatFailures: Long = 0L,
    val agentTasks: Long = 0L,
    val completedTasks: Long = 0L,
    @ColumnInfo(defaultValue = "0") val autonomousCompletedTasks: Long = 0L,
    @ColumnInfo(defaultValue = "0") val assistedCompletedTasks: Long = 0L,
    val failedTasks: Long = 0L,
    val pausedTasks: Long = 0L,
    val cancelledTasks: Long = 0L,
    val budgetExceededTasks: Long = 0L,
    val modelCalls: Long = 0L,
    val modelFailures: Long = 0L,
    @ColumnInfo(defaultValue = "0") val agentModelTurns: Long = 0L,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val reasoningTokens: Long = 0L,
    val cachedInputTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val providerTokens: Long = 0L,
    val estimatedTokens: Long = 0L,
    val modelLatencyMs: Long = 0L,
    val requestBytes: Long = 0L,
    val responseBytes: Long = 0L,
    val taskDurationMs: Long = 0L,
    val executedActions: Long = 0L,
    val successfulActions: Long = 0L,
    val failedActions: Long = 0L,
    val observations: Long = 0L,
    val reobservations: Long = 0L,
    val rejectedPlans: Long = 0L,
    val executionFailures: Long = 0L,
    val confirmationRequests: Long = 0L,
    val confirmationsAccepted: Long = 0L,
    val userInputRequests: Long = 0L,
    val userInputsSubmitted: Long = 0L,
    val userTakeovers: Long = 0L,
    val takeoverResumes: Long = 0L,
    val webSearches: Long = 0L,
    val imageRequests: Long = 0L,
) {
    fun toModel() = AgentDailyActivity(
        dateKey, firstActivityAtMillis, lastActivityAtMillis, chatCalls, chatFailures,
        agentTasks, completedTasks, autonomousCompletedTasks, assistedCompletedTasks,
        failedTasks, pausedTasks, cancelledTasks, budgetExceededTasks, modelCalls,
        modelFailures, agentModelTurns, inputTokens, outputTokens, reasoningTokens,
        cachedInputTokens, totalTokens, providerTokens, estimatedTokens, modelLatencyMs,
        requestBytes, responseBytes, taskDurationMs, executedActions, successfulActions,
        failedActions, observations, reobservations, rejectedPlans, executionFailures,
        confirmationRequests, confirmationsAccepted, userInputRequests,
        userInputsSubmitted, userTakeovers, takeoverResumes, webSearches, imageRequests,
    )
}

@Entity(
    tableName = "agent_task_analytics",
    indices = [Index("startedAtMillis"), Index("status")],
)
data class AgentTaskAnalyticsEntity(
    @PrimaryKey val taskId: Long,
    val goal: String,
    val status: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val durationMs: Long = 0L,
    val latestResult: String = "",
    val modelCalls: Long = 0L,
    val modelFailures: Long = 0L,
    val modelTurns: Long = 0L,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val reasoningTokens: Long = 0L,
    val cachedInputTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val providerTokens: Long = 0L,
    val estimatedTokens: Long = 0L,
    val requestBytes: Long = 0L,
    val responseBytes: Long = 0L,
    val modelLatencyMs: Long = 0L,
    val executedActions: Long = 0L,
    val successfulActions: Long = 0L,
    val failedActions: Long = 0L,
    val observations: Long = 0L,
    val reobservations: Long = 0L,
    val rejectedPlans: Long = 0L,
    val executionFailures: Long = 0L,
    val confirmationRequests: Long = 0L,
    val confirmationsAccepted: Long = 0L,
    val userInputRequests: Long = 0L,
    val userInputsSubmitted: Long = 0L,
    val userTakeovers: Long = 0L,
    val takeoverResumes: Long = 0L,
    val appUsageJson: String = "{}",
    val actionUsageJson: String = "{}",
) {
    fun toModel() = AgentTaskAnalytics(
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
}

@Entity(
    tableName = "agent_token_events",
    indices = [Index("occurredAtMillis"), Index("modelId"), Index("taskId")],
)
data class AgentTokenEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long?,
    val source: String,
    val occurredAtMillis: Long,
    val modelId: String,
    val modelLabel: String,
    val success: Boolean,
    val inputTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val cachedInputTokens: Long,
    val totalTokens: Long,
    val accuracy: String,
    val latencyMs: Long,
    val requestBytes: Long,
    val responseBytes: Long,
)

data class AgentTaskTokenAggregate(
    val modelCalls: Long,
    val modelFailures: Long,
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
)
