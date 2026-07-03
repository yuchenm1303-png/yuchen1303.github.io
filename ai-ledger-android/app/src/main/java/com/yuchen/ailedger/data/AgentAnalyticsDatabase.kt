package com.yuchen.ailedger.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT COUNT(*) AS modelCalls, COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) AS modelFailures, COALESCE(SUM(inputTokens), 0) AS inputTokens, COALESCE(SUM(outputTokens), 0) AS outputTokens, COALESCE(SUM(reasoningTokens), 0) AS reasoningTokens, COALESCE(SUM(cachedInputTokens), 0) AS cachedInputTokens, COALESCE(SUM(totalTokens), 0) AS totalTokens, COALESCE(SUM(CASE WHEN accuracy = 'Provider' THEN totalTokens ELSE 0 END), 0) AS providerTokens, COALESCE(SUM(CASE WHEN accuracy = 'Estimated' THEN totalTokens ELSE 0 END), 0) AS estimatedTokens, COALESCE(SUM(requestBytes), 0) AS requestBytes, COALESCE(SUM(responseBytes), 0) AS responseBytes, COALESCE(SUM(latencyMs), 0) AS modelLatencyMs FROM agent_token_events WHERE taskId = :taskId")
    suspend fun getTaskTokenAggregate(taskId: Long): AgentTaskTokenAggregate

    @Query("SELECT * FROM agent_model_usage WHERE modelId = :modelId LIMIT 1")
    suspend fun getModelUsage(modelId: String): AgentModelUsageEntity?

    @Query("SELECT * FROM agent_capability_usage WHERE kind = :kind AND capabilityKey = :capabilityKey LIMIT 1")
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

    @Query("DELETE FROM agent_task_analytics WHERE taskId NOT IN (SELECT taskId FROM agent_task_analytics ORDER BY startedAtMillis DESC LIMIT :keepCount)")
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
                database.execSQL("ALTER TABLE agent_daily_activity ADD COLUMN autonomousCompletedTasks INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE agent_daily_activity ADD COLUMN assistedCompletedTasks INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE agent_daily_activity ADD COLUMN agentModelTurns INTEGER NOT NULL DEFAULT 0")
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

        /**
         * 仅由统计详情页在 IO 线程调用，提前完成 Room 迁移与 schema 校验。
         * 运行时旁路写入仍保持延迟打开，避免应用启动或智能体进度回调发生同步磁盘访问。
         */
        fun validate(context: Context, databaseName: String) {
            get(context.applicationContext, databaseName).openHelper.writableDatabase
        }
    }
}
