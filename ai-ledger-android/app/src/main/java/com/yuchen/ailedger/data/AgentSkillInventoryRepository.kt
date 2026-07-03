package com.yuchen.ailedger.data

import android.content.Context
import androidx.room.InvalidationTracker
import com.yuchen.ailedger.model.AgentSkillInventory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 只读聚合操作学习数据库中的长期 Skill 资产。
 *
 * 不复制工作流正文、不读取录制轨迹，也不参与工作流执行；数据库表变化时只重新执行一条聚合查询。
 */
class AgentSkillInventoryRepository private constructor(context: Context) {
    private val database = OperationWorkflowDatabase.get(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(AgentSkillInventory())
    val state: StateFlow<AgentSkillInventory> = mutableState.asStateFlow()

    private val observer = object : InvalidationTracker.Observer(
        arrayOf(
            "operation_workflows",
            "operation_workflow_steps",
            "operation_workflow_app_scopes",
            "operation_demonstrations",
            "operation_workflow_runs",
        ),
    ) {
        override fun onInvalidated(tables: Set<String>) {
            refresh()
        }
    }

    init {
        database.invalidationTracker.addObserver(observer)
        refresh()
    }

    fun refresh() {
        scope.launch {
            runCatching { queryInventory() }
                .onSuccess { inventory -> mutableState.value = inventory }
        }
    }

    private fun queryInventory(): AgentSkillInventory {
        val sql = """
            SELECT
                COUNT(*) AS totalSkills,
                COALESCE(SUM(CASE WHEN status = 'Intent' THEN 1 ELSE 0 END), 0) AS intentSkills,
                COALESCE(SUM(CASE WHEN status = 'Compiling' THEN 1 ELSE 0 END), 0) AS compilingSkills,
                COALESCE(SUM(CASE WHEN status = 'ReadyForReview' THEN 1 ELSE 0 END), 0) AS reviewSkills,
                COALESCE(SUM(CASE WHEN status = 'Approved' THEN 1 ELSE 0 END), 0) AS approvedSkills,
                COALESCE(SUM(CASE WHEN status = 'Verified' THEN 1 ELSE 0 END), 0) AS verifiedSkills,
                COALESCE(SUM(CASE WHEN status = 'Paused' THEN 1 ELSE 0 END), 0) AS pausedSkills,
                (SELECT COUNT(*) FROM operation_workflow_steps) AS totalSteps,
                (
                    SELECT COUNT(DISTINCT packageName)
                    FROM operation_workflow_app_scopes
                    WHERE TRIM(packageName) != ''
                ) AS scopedApps,
                (SELECT COUNT(*) FROM operation_demonstrations) AS demonstrations,
                (SELECT COUNT(*) FROM operation_workflow_runs) AS totalRuns,
                (
                    SELECT COUNT(*)
                    FROM operation_workflow_runs
                    WHERE LOWER(status) IN ('completed', 'succeeded', 'success', 'verified')
                ) AS successfulRuns
            FROM operation_workflows
            WHERE status != 'Archived'
        """.trimIndent()
        return database.openHelper.readableDatabase.query(sql).use { cursor ->
            if (!cursor.moveToFirst()) return@use AgentSkillInventory()
            AgentSkillInventory(
                totalSkills = cursor.longValue("totalSkills"),
                intentSkills = cursor.longValue("intentSkills"),
                compilingSkills = cursor.longValue("compilingSkills"),
                reviewSkills = cursor.longValue("reviewSkills"),
                approvedSkills = cursor.longValue("approvedSkills"),
                verifiedSkills = cursor.longValue("verifiedSkills"),
                pausedSkills = cursor.longValue("pausedSkills"),
                totalSteps = cursor.longValue("totalSteps"),
                scopedApps = cursor.longValue("scopedApps"),
                demonstrations = cursor.longValue("demonstrations"),
                totalRuns = cursor.longValue("totalRuns"),
                successfulRuns = cursor.longValue("successfulRuns"),
            )
        }
    }

    private fun android.database.Cursor.longValue(columnName: String): Long {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index).coerceAtLeast(0L) else 0L
    }

    companion object {
        @Volatile
        private var instance: AgentSkillInventoryRepository? = null

        fun get(context: Context): AgentSkillInventoryRepository {
            return instance ?: synchronized(this) {
                instance ?: AgentSkillInventoryRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
