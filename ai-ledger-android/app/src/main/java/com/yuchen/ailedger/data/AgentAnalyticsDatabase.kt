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

    @Query("SELECT * FROM agent_daily_activity ORDER BY dateKey ASC")
    suspend fun getAllDailyActivity(): List<AgentDailyActivityEntity>

    @Query("SELECT * FROM agent_task_analytics ORDER BY startedAtMillis DESC LIMIT :limit")
    suspend fun getRecentTasks(limit: Int): List<AgentTaskAnalyticsEntity>

    @Query("SELECT COALESCE(MAX(durationMs), 0) FROM agent_task_analytics")
    suspend fun getLongestTaskDurationMs(): Long

    @Query("SELECT * FROM agent_model_usage ORDER BY totalTokens DESC, calls DESC")
    suspend fun getAllModelUsage(): List<AgentModelUsageEntity>

    @Query("SELECT * FROM agent_capability_usage ORDER BY uses DESC, lastUsedAtMillis DESC")
    suspend fun getAllCapabilityUsage(): List<AgentCapabilityUsageEntity>

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
        private val unavailableDatabaseNames = mutableSetOf<String>()
        private val repairedDatabaseNames = mutableSetOf<String>()

        fun get(context: Context): AgentAnalyticsDatabase {
            val owner = AgentAnalyticsOwnerRuntime.current(context.applicationContext)
            return get(context, owner.databaseName)
        }

        fun get(context: Context, databaseName: String): AgentAnalyticsDatabase {
            val safeName = safeDatabaseName(databaseName)
            return synchronized(instances) {
                check(safeName !in unavailableDatabaseNames) {
                    "智能体统计数据库在本次进程中不可用：$safeName"
                }
                instances[safeName] ?: buildDatabase(context.applicationContext, safeName)
                    .also { instances[safeName] = it }
            }
        }

        /**
         * 仅由统计详情页在 IO 线程调用，提前完成 Room 迁移与 schema 校验。
         * 校验失败后会对统计库做一次自修复重建；统计是旁路数据，不能因为历史坏库让后续记录永久停摆。
         */
        fun validate(context: Context, databaseName: String) {
            val appContext = context.applicationContext
            val safeName = safeDatabaseName(databaseName)
            if (isUnavailable(safeName)) {
                if (tryRepair(appContext, safeName)) return
                check(!isUnavailable(safeName)) {
                    "智能体统计数据库在本次进程中不可用：$safeName"
                }
            }
            val database = get(appContext, safeName)
            try {
                database.openHelper.writableDatabase
            } catch (error: Throwable) {
                synchronized(instances) {
                    if (instances[safeName] === database) instances.remove(safeName)
                    unavailableDatabaseNames += safeName
                }
                runCatching { database.close() }
                if (tryRepair(appContext, safeName)) return
                throw error
            }
        }

        fun isAvailable(databaseName: String): Boolean {
            val safeName = safeDatabaseName(databaseName)
            return !isUnavailable(safeName)
        }

        private fun buildDatabase(context: Context, safeName: String): AgentAnalyticsDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AgentAnalyticsDatabase::class.java,
                safeName,
            )
                .addMigrations(MIGRATION_1_2)
                .build()

        private fun tryRepair(context: Context, safeName: String): Boolean {
            synchronized(instances) {
                if (safeName in repairedDatabaseNames) return false
                repairedDatabaseNames += safeName
                unavailableDatabaseNames -= safeName
                instances.remove(safeName)?.let { database -> runCatching { database.close() } }
            }
            return runCatching {
                context.applicationContext.deleteDatabase(safeName)
                val repaired = get(context.applicationContext, safeName)
                repaired.openHelper.writableDatabase
                true
            }.getOrElse {
                synchronized(instances) { unavailableDatabaseNames += safeName }
                false
            }
        }

        private fun isUnavailable(safeName: String): Boolean =
            synchronized(instances) { safeName in unavailableDatabaseNames }

        private fun safeDatabaseName(databaseName: String): String =
            databaseName.trim().takeIf { it.endsWith(".db") } ?: "agent_analytics.db"
    }
}
