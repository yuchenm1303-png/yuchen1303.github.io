package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowAppScope
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode

/**
 * 功能首页专用的操作学习摘要读取器。
 *
 * 只执行工作流主表 + 应用范围关系的一次事务查询，不读取 variables、milestones、steps、
 * selectors 或 state checks，避免首页为每个 Skill 产生 N+1 图谱查询和大量模型分配。
 */
class OperationWorkflowSummaryRepository(context: Context) {
    private val dao = OperationWorkflowDatabase.get(context.applicationContext).workflowDao()

    suspend fun loadSummaries(): List<LearnedWorkflowDraft> {
        return dao.loadActiveWorkflows().map { record ->
            val workflow = record.workflow
            LearnedWorkflowDraft(
                id = workflow.id,
                title = workflow.title,
                goal = workflow.goal,
                appScope = WorkflowAppScope(
                    packageNames = record.appScopes.map { it.packageName },
                    displayNames = record.appScopes.map { it.displayName },
                    allowSystemSurfaces = record.appScopes.any { it.allowSystemSurfaces },
                ),
                executionMode = enumValueOrDefault(
                    workflow.executionMode,
                    WorkflowExecutionMode.CloudVisual,
                ),
                status = enumValueOrDefault(workflow.status, WorkflowDraftStatus.Intent),
                createdAtMillis = workflow.createdAtMillis,
                updatedAtMillis = workflow.updatedAtMillis,
                sourceDemonstrationId = workflow.sourceDemonstrationId,
            )
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T {
        return runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
    }
}
