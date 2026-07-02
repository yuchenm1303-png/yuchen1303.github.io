package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowAppScope
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode

class OperationWorkflowRepository private constructor(context: Context) {
    private val dao = OperationWorkflowDatabase.get(context).workflowDao()

    suspend fun loadDrafts(): List<LearnedWorkflowDraft> {
        return dao.loadActiveWorkflows().map { record ->
            val workflow = record.workflow
            LearnedWorkflowDraft(
                id = workflow.id,
                title = workflow.title,
                goal = workflow.goal,
                appScope = WorkflowAppScope(
                    packageNames = record.appScopes.map { it.packageName },
                    displayNames = record.appScopes.mapNotNull {
                        it.displayName.trim().takeIf(String::isNotBlank)
                    },
                    allowSystemSurfaces = record.appScopes.any { it.allowSystemSurfaces },
                ),
                executionMode = runCatching {
                    WorkflowExecutionMode.valueOf(workflow.executionMode)
                }.getOrDefault(WorkflowExecutionMode.Deterministic),
                status = runCatching {
                    WorkflowDraftStatus.valueOf(workflow.status)
                }.getOrDefault(WorkflowDraftStatus.Intent),
                createdAtMillis = workflow.createdAtMillis,
                updatedAtMillis = workflow.updatedAtMillis,
                sourceDemonstrationId = workflow.sourceDemonstrationId,
            )
        }
    }

    suspend fun saveIntent(draft: LearnedWorkflowDraft) {
        val workflow = OperationWorkflowEntity(
            id = draft.id,
            title = draft.title,
            goal = draft.goal,
            executionMode = draft.executionMode.name,
            status = draft.status.name,
            createdAtMillis = draft.createdAtMillis,
            updatedAtMillis = draft.updatedAtMillis,
            sourceDemonstrationId = draft.sourceDemonstrationId,
        )
        val packages = draft.appScope.normalizedPackages
        val appScopes = packages.mapIndexed { index, packageName ->
            OperationWorkflowAppScopeEntity(
                workflowId = draft.id,
                packageName = packageName,
                displayName = draft.appScope.displayNames.getOrNull(index).orEmpty(),
                allowSystemSurfaces = draft.appScope.allowSystemSurfaces,
            )
        }
        dao.saveIntent(workflow, appScopes)
    }

    suspend fun deleteDraft(draftId: String) {
        dao.deleteWorkflow(draftId)
    }

    companion object {
        @Volatile
        private var instance: OperationWorkflowRepository? = null

        fun get(context: Context): OperationWorkflowRepository {
            return instance ?: synchronized(this) {
                instance ?: OperationWorkflowRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
