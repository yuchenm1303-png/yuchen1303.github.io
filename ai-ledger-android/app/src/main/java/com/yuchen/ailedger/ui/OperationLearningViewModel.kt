package com.yuchen.ailedger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowAppScope
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode
import com.yuchen.ailedger.service.OperationWorkflowValidator
import com.yuchen.ailedger.service.WorkflowValidationIssue
import com.yuchen.ailedger.service.WorkflowValidationStage
import java.util.UUID

data class OperationLearningUiState(
    val drafts: List<LearnedWorkflowDraft> = emptyList(),
    val editorVisible: Boolean = false,
    val titleInput: String = "",
    val goalInput: String = "",
    val appNameInput: String = "",
    val packageNameInput: String = "",
    val editorIssues: List<WorkflowValidationIssue> = emptyList(),
    val selectedDraftId: String? = null,
    val notice: String? = null,
)

class OperationLearningViewModel : ViewModel() {
    var uiState by mutableStateOf(OperationLearningUiState())
        private set

    fun openIntentEditor() {
        uiState = uiState.copy(
            editorVisible = true,
            editorIssues = emptyList(),
            notice = null,
        )
    }

    fun closeIntentEditor() {
        uiState = uiState.copy(
            editorVisible = false,
            editorIssues = emptyList(),
            notice = null,
        )
    }

    fun updateTitle(value: String) {
        uiState = uiState.copy(titleInput = value.take(60), editorIssues = emptyList())
    }

    fun updateGoal(value: String) {
        uiState = uiState.copy(goalInput = value.take(240), editorIssues = emptyList())
    }

    fun updateAppName(value: String) {
        uiState = uiState.copy(appNameInput = value.take(60), editorIssues = emptyList())
    }

    fun updatePackageName(value: String) {
        uiState = uiState.copy(
            packageNameInput = value.trim().take(160),
            editorIssues = emptyList(),
        )
    }

    fun createIntentDraft(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val draft = LearnedWorkflowDraft(
            id = UUID.randomUUID().toString(),
            title = uiState.titleInput.trim(),
            goal = uiState.goalInput.trim(),
            appScope = WorkflowAppScope(
                packageNames = listOf(uiState.packageNameInput.trim()),
                displayNames = listOf(uiState.appNameInput.trim()).filter(String::isNotBlank),
            ),
            executionMode = WorkflowExecutionMode.Deterministic,
            status = WorkflowDraftStatus.Intent,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
        val report = OperationWorkflowValidator.validate(
            draft = draft,
            stage = WorkflowValidationStage.RecordingIntent,
        )
        if (!report.canProceed) {
            uiState = uiState.copy(editorIssues = report.blockingIssues)
            return false
        }

        uiState = uiState.copy(
            drafts = (uiState.drafts + draft).sortedByDescending { it.updatedAtMillis },
            editorVisible = false,
            titleInput = "",
            goalInput = "",
            appNameInput = "",
            packageNameInput = "",
            editorIssues = emptyList(),
            selectedDraftId = draft.id,
            notice = "已创建操作草稿。下一阶段将从这个明确目标启动演示录制。",
        )
        return true
    }

    fun selectDraft(draftId: String) {
        if (uiState.drafts.none { it.id == draftId }) return
        uiState = uiState.copy(selectedDraftId = draftId, notice = null)
    }

    fun prepareDemonstration(draftId: String) {
        val draft = uiState.drafts.firstOrNull { it.id == draftId } ?: return
        val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.RecordingIntent)
        uiState = if (report.canProceed) {
            uiState.copy(
                selectedDraftId = draft.id,
                notice = "目标和应用范围已通过录制前校验；录制器接入后将从该草稿启动。",
            )
        } else {
            uiState.copy(
                selectedDraftId = draft.id,
                notice = report.blockingIssues.firstOrNull()?.message ?: "草稿尚未满足录制条件。",
            )
        }
    }

    fun deleteDraft(draftId: String) {
        val draft = uiState.drafts.firstOrNull { it.id == draftId } ?: return
        uiState = uiState.copy(
            drafts = uiState.drafts.filterNot { it.id == draftId },
            selectedDraftId = uiState.selectedDraftId.takeUnless { it == draftId },
            notice = "已删除草稿“${draft.title}”。",
        )
    }

    fun clearNotice() {
        if (uiState.notice != null) uiState = uiState.copy(notice = null)
    }
}
