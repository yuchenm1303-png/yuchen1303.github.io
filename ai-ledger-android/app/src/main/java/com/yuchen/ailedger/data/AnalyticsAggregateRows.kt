package com.yuchen.ailedger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuchen.ailedger.model.AgentCapabilityAnalytics
import com.yuchen.ailedger.model.AgentModelAnalytics

@Entity(tableName = "agent_model_usage")
data class AgentModelUsageEntity(
    @PrimaryKey val modelId: String,
    val displayName: String,
    val calls: Long = 0L,
    val failures: Long = 0L,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val reasoningTokens: Long = 0L,
    val cachedInputTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val providerTokens: Long = 0L,
    val estimatedTokens: Long = 0L,
    val latencyMs: Long = 0L,
    val requestBytes: Long = 0L,
    val responseBytes: Long = 0L,
    val firstUsedAtMillis: Long = 0L,
    val lastUsedAtMillis: Long = 0L,
) {
    fun toModel(): AgentModelAnalytics = AgentModelAnalytics(
        modelId, displayName, calls, failures, inputTokens, outputTokens,
        reasoningTokens, cachedInputTokens, totalTokens, providerTokens,
        estimatedTokens, latencyMs, requestBytes, responseBytes,
        firstUsedAtMillis, lastUsedAtMillis,
    )
}

@Entity(
    tableName = "agent_capability_usage",
    primaryKeys = ["kind", "capabilityKey"],
    indices = [Index("lastUsedAtMillis")],
)
data class AgentCapabilityUsageEntity(
    val kind: String,
    val capabilityKey: String,
    val displayName: String,
    val uses: Long = 0L,
    val successes: Long = 0L,
    val failures: Long = 0L,
    val firstUsedAtMillis: Long = 0L,
    val lastUsedAtMillis: Long = 0L,
) {
    fun toModel(): AgentCapabilityAnalytics = AgentCapabilityAnalytics(
        kind, capabilityKey, displayName, uses, successes, failures,
        firstUsedAtMillis, lastUsedAtMillis,
    )
}
