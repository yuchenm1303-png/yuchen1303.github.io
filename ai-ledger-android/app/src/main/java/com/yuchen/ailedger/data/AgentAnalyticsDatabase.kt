package com.yuchen.ailedger.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

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
)

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
)

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
)

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
)

@Dao
interface AgentAnalyticsDao {
    @Query("SELECT * FROM agent_daily_activity ORDER BY dateKey ASC")
    fun observeDailyActivity(): Flow<List<AgentDailyActivityEntity>>

    @Query("SELECT * FROM agent_task_analytics ORDER BY startedAtMillis DESC LIMIT :limit")
    fun observeRecentTasks(limit: Int): Flow<List<AgentTaskAnalyticsEntity>>

    @Query("SELECT COALESCE(MAX(durationMs), 0) FROM agent_task_analytics")
    fun observeLongestTaskDurationMs(): Flow<Long>

    @Query("SELECT * FROM agent_model_usage ORDER BY totalTokens DESC, calls DESC")
    fun observeModelUsage(): Flow<List<AgentModelUsageEntity>>

    @Query("SELECT * FROM agent_capability_usage ORDER BY uses DESC, lastUsedAtMillis DESC")
    fun observeCapabilityUsage(): Flow<List<AgentCapabilityUsageEntity>>

    @Query("SELECT * FROM agent_daily_activity WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getDailyActivity(dateKey: String): AgentDailyActivityEntity?

    @Query("SELECT * FROM agent_task_analytics WHERE taskId = :taskId LIMIT 1")
    suspend fun getTask(taskId: Long): AgentTaskAnalyticsEntity?

    @Query("SELECT * FROM agent_task_analytics WHERE endedAtMillis IS NULL OR status = 'running'")
    suspend fun getOpenTasks(): List<AgentTaskAnalyticsEntity>

    @Query(
        """
        SELECT
            COUNT(*) AS modelCalls,
            COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) AS modelFailures,
            COALESCE(SUM(inputTokens), 0) AS inputTokens,
            COALESCE(SUM(outputTokens), 0) AS outputTokens,
            COALESCE(SUM(reasoningTokens), 0) AS reasoningTokens,
            COALESCE(SUM(cachedInputTokens), 0) AS cachedInputTokens,
            COALESCE(SUM(totalTokens), 0) AS totalTokens,
            COALESCE(SUM(CASE WHEN accuracy = 'Provider' THEN totalTokens ELSE 0 END), 0) AS providerTokens,
            COALESCE(SUM(CASE WHEN accuracy = 'Estimated' THEN totalTokens ELSE 0 END), 0) AS estimatedTokens,
            COALESCE(SUM(requestBytes), 0) AS requestBytes,
            COALESCE(SUM(responseBytes), 0) AS responseBytes,
            COALESCE(SUM(latencyMs), 0) AS modelLatencyMs
        FROM agent_token_events
        WHERE taskId = :taskId
        """,
    )
    suspend fun getTaskTokenAggregate(taskId: Long): AgentTaskTokenAggregate

    @Query("SELECT * FROM agent_model_usage WHERE modelId = :modelId LIMIT 1")
    suspend fun getModelUsage(modelId: String): AgentModelUsageEntity?

    @Query(
        "SELECT * FROM agent_capability_usage WHERE kind = :kind AND capabilityKey = :capabilityKey LIMIT 1",
    )
    suspend fun getCapabilityUsage(kind: String, capabilityKey: String): AgentCapabilityUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyActivity(entity: AgentDailyActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(entity: AgentTaskAnalyticsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModelUsage(entity: AgentModelUsageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCapabilityUsage(entity: AgentCapabilityUsageEntity)

    @Insert
    suspend fun insertTokenEvent(entity: AgentTokenEventEntity): Long

    @Query("DELETE FROM agent_token_events WHERE occurredAtMillis < :cutoffMillis")
    suspend fun deleteOldTokenEvents(cutoffMillis: Long)

    @Query(
        """
        DELETE FROM agent_task_analytics
        WHERE taskId NOT IN (
            SELECT taskId FROM agent_task_analytics
            ORDER BY startedAtMillis DESC
            LIMIT :keepCount
        )
        """,
    )
    suspend fun pruneTasks(keepCount: Int)

    @Query("DELETE FROM agent_daily_activity WHERE dateKey < :oldestDateKey")
    suspend fun pruneDailyActivity(oldestDateKey: String)
}

@Database(
    entities = [
        AgentDailyActivityEntity::class,
        AgentTaskAnalyticsEntity::class,
        AgentTokenEventEntity::class,
        AgentModelUsageEntity::class,
        AgentCapabilityUsageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AgentAnalyticsDatabase : RoomDatabase() {
    abstract fun analyticsDao(): AgentAnalyticsDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE agent_daily_activity ADD COLUMN autonomousCompletedTasks INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE agent_daily_activity ADD COLUMN assistedCompletedTasks INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE agent_daily_activity ADD COLUMN agentModelTurns INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val instances = mutableMapOf<String, AgentAnalyticsDatabase>()

        fun get(context: Context): AgentAnalyticsDatabase {
            val owner = AgentAnalyticsOwnerRuntime.current(context.applicationContext)
            return get(context, owner.databaseName)
        }

        fun get(context: Context, databaseName: String): AgentAnalyticsDatabase {
            val safeName = databaseName.trim().takeIf { it.endsWith(".db") } ?: "agent_analytics.db"
            return synchronized(instances) {
                instances[safeName] ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentAnalyticsDatabase::class.java,
                    safeName,
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instances[safeName] = it }
            }
        }
    }
}
