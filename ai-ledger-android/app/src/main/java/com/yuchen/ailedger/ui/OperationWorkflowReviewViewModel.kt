package com.yuchen.ailedger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.OperationWorkflowRepository
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowStateCheckType
import com.yuchen.ailedger.service.OperationWorkflowCompilationCoordinator
import com.yuchen.ailedger.service.OperationWorkflowValidator
import com.yuchen.ailedger.service.WorkflowValidationIssue
import com.yuchen.ailedger.service.WorkflowValidationStage
import kotlinx.coroutines.launch

data class OperationWorkflowReviewUiState(
    val compilingDraftIds: Set<String> = emptySet(),
    val draft: LearnedWorkflowDraft? = null,
    val issues: List<WorkflowValidationIssue> = emptyList(),
    val loading: Boolean = false,
    val approving: Boolean = false,
    val revision: Long = 0L,
    val notice: String? = null,
)

class OperationWorkflowReviewViewModel : ViewModel() {
    private val context = AiLedgerApplication.contextOrNull()
    private val repository = context?.let(OperationWorkflowRepository::get)

    var uiState by mutableStateOf(OperationWorkflowReviewUiState())
        private set

    fun compile(draftId: String) {
        val applicationContext = context ?: run {
            uiState = uiState.copy(notice = "应用存储尚未准备完成，无法整理流程。")
            return
        }
        if (draftId in uiState.compilingDraftIds) return
        uiState = uiState.copy(
            compilingDraftIds = uiState.compilingDraftIds + draftId,
            notice = "正在把演示整理成结构化流程…",
        )
        viewModelScope.launch {
            val outcome = OperationWorkflowCompilationCoordinator.compile(applicationContext, draftId)
            uiState = uiState.copy(
                compilingDraftIds = uiState.compilingDraftIds - draftId,
                draft = outcome.draft,
                issues = outcome.draft?.let(::validateReview).orEmpty(),
                revision = uiState.revision + if (outcome.completed) 1L else 0L,
                notice = outcome.message,
            )
        }
    }

    fun open(draftId: String) {
        val activeRepository = repository ?: run {
            uiState = uiState.copy(notice = "应用存储尚未准备完成。")
            return
        }
        uiState = uiState.copy(loading = true, notice = null)
        viewModelScope.launch {
            val draft = runCatching { activeRepository.loadDraft(draftId) }.getOrNull()
            uiState = if (draft == null) {
                uiState.copy(loading = false, notice = "无法读取待审核流程。")
            } else {
                uiState.copy(
                    loading = false,
                    draft = draft,
                    issues = validateReview(draft),
                )
            }
        }
    }

    fun close() {
        uiState = uiState.copy(
            draft = null,
            issues = emptyList(),
            loading = false,
            approving = false,
            notice = null,
        )
    }

    fun updateVariableLabel(variableKey: String, value: String) {
        val draft = uiState.draft ?: return
        val updated = draft.copy(
            variables = draft.variables.map { variable ->
                if (variable.key == variableKey) variable.copy(label = value.take(48)) else variable
            },
            updatedAtMillis = System.currentTimeMillis(),
        )
        updateDraft(updated)
    }

    fun updateCompletionEvidence(checkId: String, value: String) {
        val draft = uiState.draft ?: return
        val updated = draft.copy(
            completionChecks = draft.completionChecks.map { check ->
                if (check.id == checkId && check.type in EDITABLE_COMPLETION_TYPES) {
                    check.copy(expectedValue = value.take(120))
                } else {
                    check
                }
            },
            updatedAtMillis = System.currentTimeMillis(),
        )
        updateDraft(updated)
    }

    fun approve() {
        val draft = uiState.draft ?: return
        val activeRepository = repository ?: return
        val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.Review)
        if (!report.canProceed) {
            uiState = uiState.copy(
                issues = report.issues,
                notice = report.blockingIssues.firstOrNull()?.message ?: "流程还不能批准。",
            )
            return
        }
        val demonstrationId = draft.sourceDemonstrationId
        if (demonstrationId.isNullOrBlank()) {
            uiState = uiState.copy(notice = "流程缺少来源演示，不能批准。")
            return
        }

        uiState = uiState.copy(approving = true, notice = "正在保存审核版本…")
        viewModelScope.launch {
            val result = runCatching {
                val reviewed = draft.copy(
                    status = WorkflowDraftStatus.ReadyForReview,
                    updatedAtMillis = System.currentTimeMillis(),
                )
                activeRepository.saveCompiledDraft(reviewed, demonstrationId)
                activeRepository.approveDraft(reviewed)
            }
            uiState = if (result.isSuccess) {
                val version = result.getOrNull()
                uiState.copy(
                    draft = null,
                    issues = emptyList(),
                    approving = false,
                    revision = uiState.revision + 1L,
                    notice = "流程已批准并保存为版本 $version。执行器接入前不会自动运行。",
                )
            } else {
                uiState.copy(
                    approving = false,
                    notice = "审核版本保存失败，流程仍保持待审核状态。",
                )
            }
        }
    }

    fun resetForNewDemonstration() {
        val draft = uiState.draft ?: return
        val activeRepository = repository ?: return
        viewModelScope.launch {
            val reset = runCatching {
                activeRepository.resetForNewDemonstration(draft.id)
            }.isSuccess
            uiState = if (reset) {
                uiState.copy(
                    draft = null,
                    issues = emptyList(),
                    revision = uiState.revision + 1L,
                    notice = "已清除结构化草稿，可以重新演示。",
                )
            } else {
                uiState.copy(notice = "无法重置流程，请稍后重试。")
            }
        }
    }

    fun clearNotice() {
        if (uiState.notice != null) uiState = uiState.copy(notice = null)
    }

    private fun updateDraft(draft: LearnedWorkflowDraft) {
        uiState = uiState.copy(
            draft = draft,
            issues = validateReview(draft),
        )
    }

    private fun validateReview(draft: LearnedWorkflowDraft): List<WorkflowValidationIssue> {
        return OperationWorkflowValidator.validate(draft, WorkflowValidationStage.Review).issues
    }

    private companion object {
        val EDITABLE_COMPLETION_TYPES = setOf(
            WorkflowStateCheckType.TextVisible,
            WorkflowStateCheckType.UserConfirmed,
        )
    }
}
