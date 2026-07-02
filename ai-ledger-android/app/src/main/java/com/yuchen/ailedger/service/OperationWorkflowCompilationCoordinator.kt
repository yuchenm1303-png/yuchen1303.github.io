package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.data.OperationTraceStore
import com.yuchen.ailedger.data.OperationWorkflowRepository
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowDraftStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WorkflowCompilationOutcome(
    val completed: Boolean,
    val message: String,
    val draft: LearnedWorkflowDraft? = null,
    val issues: List<WorkflowCompilationIssue> = emptyList(),
)

object OperationWorkflowCompilationCoordinator {
    suspend fun compile(
        context: Context,
        workflowId: String,
    ): WorkflowCompilationOutcome = withContext(Dispatchers.IO) {
        val applicationContext = context.applicationContext
        val repository = OperationWorkflowRepository.get(applicationContext)
        val baseDraft = repository.loadDraft(workflowId)
            ?: return@withContext WorkflowCompilationOutcome(false, "没有找到需要整理的操作草稿。")
        if (baseDraft.status == WorkflowDraftStatus.ReadyForReview) {
            return@withContext WorkflowCompilationOutcome(
                completed = true,
                message = "流程已经整理完成，可以开始审核。",
                draft = baseDraft,
            )
        }
        if (baseDraft.status != WorkflowDraftStatus.Compiling) {
            return@withContext WorkflowCompilationOutcome(false, "当前草稿不处于可整理状态。")
        }
        val demonstrationId = baseDraft.sourceDemonstrationId
            ?: return@withContext WorkflowCompilationOutcome(false, "草稿缺少演示记录，无法整理。")
        val demonstration = repository.loadDemonstration(demonstrationId)
            ?: return@withContext WorkflowCompilationOutcome(false, "演示记录不存在，无法整理。")
        val tracePath = demonstration.encryptedTracePath
            ?: return@withContext WorkflowCompilationOutcome(false, "加密轨迹已经不存在，无法重新整理。")

        val traceStore = OperationTraceStore(applicationContext)
        val records = runCatching {
            traceStore.readSession(
                path = tracePath,
                demonstrationId = demonstrationId,
            )
        }.getOrElse { error ->
            return@withContext WorkflowCompilationOutcome(
                completed = false,
                message = "读取加密轨迹失败：${error.message ?: "轨迹校验未通过"}",
            )
        }
        val result = OperationWorkflowCompiler.compile(
            baseDraft = baseDraft,
            records = records,
        )
        val compiledDraft = result.draft
            ?: return@withContext WorkflowCompilationOutcome(
                completed = false,
                message = result.issues.firstOrNull()?.message ?: "演示轨迹无法整理成流程。",
                issues = result.issues,
            )

        val saved = runCatching {
            repository.saveCompiledDraft(
                draft = compiledDraft,
                demonstrationId = demonstrationId,
            )
        }.isSuccess
        if (!saved) {
            return@withContext WorkflowCompilationOutcome(
                completed = false,
                message = "结构化流程保存失败，原始加密轨迹仍然保留。",
                issues = result.issues,
            )
        }

        traceStore.deleteTrace(tracePath)
        WorkflowCompilationOutcome(
            completed = true,
            message = if (result.issues.any { it.severity == WorkflowCompilationSeverity.Blocking }) {
                "流程已整理，但仍有阻断项需要在审核页处理。"
            } else if (result.issues.isNotEmpty()) {
                "流程已整理完成，请检查人工确认步骤和成功证据。"
            } else {
                "流程已整理完成，可以开始审核。"
            },
            draft = compiledDraft,
            issues = result.issues,
        )
    }
}
