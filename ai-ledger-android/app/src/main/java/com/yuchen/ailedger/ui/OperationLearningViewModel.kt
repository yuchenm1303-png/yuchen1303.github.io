package com.yuchen.ailedger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.OperationWorkflowRepository
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowAppScope
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode
import com.yuchen.ailedger.service.OperationLearningRecordingCoordinator
import com.yuchen.ailedger.service.OperationRecordingStopReason
import com.yuchen.ailedger.service.OperationWorkflowValidator
import com.yuchen.ailedger.service.WorkflowValidationIssue
import com.yuchen.ailedger.service.WorkflowValidationStage
import java.util.UUID
import kotlinx.coroutines.launch

data class OperationLearningUiState(
    val drafts: List<LearnedWorkflowDraft> = emptyList(),
    val loading: Boolean = false,
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
    private val repository = AiLedgerApplication.contextOrNull()?.let(OperationWorkflowRepository::get)

    var uiState by mutableStateOf(OperationLearningUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        val activeRepository = repository ?: return
        uiState = uiState.copy(loading = true)
        viewModelScope.launch {
            runCatching { activeRepository.loadDrafts() }
                .onSuccess { drafts ->
                    uiState = uiState.copy(
                        drafts = drafts,
                        loading = false,
                        selectedDraftId = uiState.selectedDraftId?.takeIf { selected ->
                            drafts.any { it.id == selected }
                        },
                    )
                }
                .onFailure {
                    uiState = uiState.copy(
                        loading = false,
                        notice = "操作草稿加载失败，请稍后重试。",
                    )
                }
        }
    }

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
            notice = "已创建操作草稿，可以开始演示。",
        )

        repository?.let { activeRepository ->
            viewModelScope.launch {
                runCatching { activeRepository.saveIntent(draft) }
                    .onFailure {
                        uiState = uiState.copy(
                            drafts = uiState.drafts.filterNot { it.id == draft.id },
                            selectedDraftId = uiState.selectedDraftId.takeUnless { it == draft.id },
                            notice = "草稿未能保存到本机，请重新创建。",
                        )
                    }
            }
        }
        return true
    }

    fun selectDraft(draftId: String) {
        if (uiState.drafts.none { it.id == draftId }) return
        uiState = uiState.copy(selectedDraftId = draftId, notice = null)
    }

    fun startRecording(draftId: String) {
        val draft = uiState.drafts.firstOrNull { it.id == draftId } ?: return
        val context = AiLedgerApplication.contextOrNull()
        if (context == null) {
            uiState = uiState.copy(notice = "应用上下文尚未准备完成，请重新进入页面。")
            return
        }
        if (draft.status != WorkflowDraftStatus.Intent) {
            uiState = uiState.copy(notice = "当前草稿已进入后续阶段，不能重复覆盖原始演示。")
            return
        }
        val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.RecordingIntent)
        if (!report.canProceed) {
            uiState = uiState.copy(
                selectedDraftId = draft.id,
                notice = report.blockingIssues.firstOrNull()?.message ?: "草稿尚未满足录制条件。",
            )
            return
        }

        uiState = uiState.copy(selectedDraftId = draft.id, notice = "正在启动录制…")
        viewModelScope.launch {
            val result = OperationLearningRecordingCoordinator.start(context, draft)
            uiState = uiState.copy(notice = result.message)
        }
    }

    fun finishRecording() {
        val context = AiLedgerApplication.contextOrNull()
        viewModelScope.launch {
            OperationLearningRecordingCoordinator.stop(
                context = context,
                reason = OperationRecordingStopReason.UserFinished,
            )
            refresh()
        }
    }

    fun cancelRecording() {
        val context = AiLedgerApplication.contextOrNull()
        viewModelScope.launch {
            OperationLearningRecordingCoordinator.stop(
                context = context,
                reason = OperationRecordingStopReason.UserCancelled,
            )
            refresh()
        }
    }

    fun deleteDraft(draftId: String) {
        val recordingWorkflowId = OperationLearningRecordingCoordinator.state.value.workflowId
        if (OperationLearningRecordingCoordinator.state.value.active && recordingWorkflowId == draftId) {
            uiState = uiState.copy(notice = "请先结束或取消当前录制，再删除草稿。")
            return
        }
        val draft = uiState.drafts.firstOrNull { it.id == draftId } ?: return
        val previousDrafts = uiState.drafts
        val previousSelection = uiState.selectedDraftId
        uiState = uiState.copy(
            drafts = previousDrafts.filterNot { it.id == draftId },
            selectedDraftId = previousSelection.takeUnless { it == draftId },
            notice = "已删除草稿“${draft.title}”。",
        )

        repository?.let { activeRepository ->
            viewModelScope.launch {
                runCatching { activeRepository.deleteDraft(draftId) }
                    .onFailure {
                        uiState = uiState.copy(
                            drafts = previousDrafts,
                            selectedDraftId = previousSelection,
                            notice = "删除失败，草稿已恢复。",
                        )
                    }
            }
        }
    }

    fun clearNotice() {
        if (uiState.notice != null) uiState = uiState.copy(notice = null)
    }
}
