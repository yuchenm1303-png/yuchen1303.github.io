package com.yuchen.ailedger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.OperationSkillApprovalRepository
import com.yuchen.ailedger.data.OperationSkillArtifactStore
import com.yuchen.ailedger.data.OperationWorkflowRepository
import com.yuchen.ailedger.model.LearnedVisualSkill
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowAppScope
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode
import com.yuchen.ailedger.service.OperationLearningRecordingCoordinator
import com.yuchen.ailedger.service.OperationRecordingStopReason
import com.yuchen.ailedger.service.OperationSkillLearningCoordinator
import com.yuchen.ailedger.service.OperationSkillReplayCoordinator
import com.yuchen.ailedger.service.OperationWorkflowValidator
import com.yuchen.ailedger.service.WorkflowValidationIssue
import com.yuchen.ailedger.service.WorkflowValidationStage
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OperationLearningUiState(
    val drafts: List<LearnedWorkflowDraft> = emptyList(),
    val skillArtifacts: Map<String, LearnedVisualSkill> = emptyMap(),
    val replayInputValues: Map<String, Map<String, String>> = emptyMap(),
    val runningSkillId: String? = null,
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
    private val context = AiLedgerApplication.contextOrNull()
    private val repository = context?.let(OperationWorkflowRepository::get)
    private val skillStore: OperationSkillArtifactStore? by lazy(LazyThreadSafetyMode.NONE) {
        context?.let(::OperationSkillArtifactStore)
    }
    private val approvalRepository: OperationSkillApprovalRepository? by lazy(LazyThreadSafetyMode.NONE) {
        context?.let(::OperationSkillApprovalRepository)
    }
    private val retryingWorkflowIds = mutableSetOf<String>()

    var uiState by mutableStateOf(OperationLearningUiState())
        private set

    init {
        refresh()
    }

    /**
     * 工具首页只需要草稿摘要，因此刷新时不再扫描全部 Skill 文件，更不会自动上传视觉演示。
     * 完整 Skill 仅在用户主动展开对应卡片时按需加载。
     */
    fun refresh() {
        val activeRepository = repository ?: return
        uiState = uiState.copy(loading = true)
        viewModelScope.launch {
            runCatching { activeRepository.loadDrafts() }
                .onSuccess { drafts ->
                    val selectedId = uiState.selectedDraftId?.takeIf { selected ->
                        drafts.any { it.id == selected }
                    }
                    uiState = uiState.copy(
                        drafts = drafts,
                        loading = false,
                        selectedDraftId = selectedId,
                        replayInputValues = uiState.replayInputValues.filterKeys { id ->
                            drafts.any { it.id == id }
                        },
                        skillArtifacts = uiState.skillArtifacts.filterKeys { id ->
                            drafts.any { it.id == id }
                        },
                    )
                    selectedId?.let { loadSkillArtifact(it, retryIfCompiling = false) }
                }
                .onFailure {
                    uiState = uiState.copy(
                        loading = false,
                        notice = "Skill 草稿加载失败，请稍后重试。",
                    )
                }
        }
    }

    private fun loadSkillArtifact(
        draftId: String,
        retryIfCompiling: Boolean,
    ) {
        if (uiState.skillArtifacts.containsKey(draftId)) return
        val draft = uiState.drafts.firstOrNull { it.id == draftId } ?: return
        viewModelScope.launch {
            val skill = withContext(Dispatchers.IO) { skillStore?.load(draftId) }
            if (skill != null) {
                uiState = uiState.copy(
                    skillArtifacts = uiState.skillArtifacts + (draftId to skill),
                )
            } else if (retryIfCompiling && draft.status == WorkflowDraftStatus.Compiling) {
                retryCloudLearning(draft)
            }
        }
    }

    private fun retryCloudLearning(draft: LearnedWorkflowDraft) {
        val activeContext = context ?: return
        val activeRepository = repository ?: return
        val demonstrationId = draft.sourceDemonstrationId ?: return
        if (!retryingWorkflowIds.add(draft.id)) return
        uiState = uiState.copy(notice = "正在重新提交这次视觉演示给云端理解…")
        viewModelScope.launch {
            val outcome = runCatching {
                val demonstration = activeRepository.loadDemonstration(demonstrationId)
                    ?: error("找不到原始视觉演示")
                val path = demonstration.encryptedTracePath
                    ?: error("原始视觉演示已清理，无法重新提交")
                OperationSkillLearningCoordinator.learn(
                    context = activeContext,
                    workflowId = draft.id,
                    demonstrationId = demonstrationId,
                    manifestPath = path,
                )
            }.getOrElse { error ->
                retryingWorkflowIds.remove(draft.id)
                uiState = uiState.copy(
                    notice = "视觉演示仍在本机加密保存，云端重试失败：${error.message ?: "未知错误"}",
                )
                return@launch
            }
            retryingWorkflowIds.remove(draft.id)
            uiState = uiState.copy(notice = outcome.message)
            if (outcome.completed) refresh()
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

    fun updateReplayInput(
        draftId: String,
        key: String,
        value: String,
    ) {
        val current = uiState.replayInputValues[draftId].orEmpty()
        uiState = uiState.copy(
            replayInputValues = uiState.replayInputValues + (
                draftId to (current + (key to value.take(500)))
            ),
            notice = null,
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
            executionMode = WorkflowExecutionMode.CloudVisual,
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
            notice = "已创建 Skill 教学草稿，可以开始视觉演示。",
        )

        repository?.let { activeRepository ->
            viewModelScope.launch {
                runCatching { activeRepository.saveIntent(draft) }
                    .onFailure {
                        uiState = uiState.copy(
                            drafts = uiState.drafts.filterNot { it.id == draft.id },
                            selectedDraftId = uiState.selectedDraftId.takeUnless { it == draft.id },
                            notice = "Skill 草稿未能保存到本机，请重新创建。",
                        )
                    }
            }
        }
        return true
    }

    fun selectDraft(draftId: String) {
        val draft = uiState.drafts.firstOrNull { it.id == draftId } ?: return
        uiState = uiState.copy(selectedDraftId = draftId, notice = null)
        loadSkillArtifact(
            draftId = draftId,
            retryIfCompiling = draft.status == WorkflowDraftStatus.Compiling,
        )
    }

    fun startRecording(draftId: String) {
        val draft = uiState.drafts.firstOrNull { it.id == draftId } ?: return
        val activeContext = context
        if (activeContext == null) {
            uiState = uiState.copy(notice = "应用上下文尚未准备完成，请重新进入页面。")
            return
        }
        if (draft.status != WorkflowDraftStatus.Intent) {
            uiState = uiState.copy(notice = "当前 Skill 已进入后续阶段，不能覆盖原始演示。")
            return
        }
        val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.RecordingIntent)
        if (!report.canProceed) {
            uiState = uiState.copy(
                selectedDraftId = draft.id,
                notice = report.blockingIssues.firstOrNull()?.message ?: "Skill 尚未满足演示条件。",
            )
            return
        }

        uiState = uiState.copy(selectedDraftId = draft.id, notice = "正在启动视觉演示…")
        viewModelScope.launch {
            val result = OperationLearningRecordingCoordinator.start(activeContext, draft)
            uiState = uiState.copy(notice = result.message)
        }
    }

    fun finishRecording() {
        viewModelScope.launch {
            OperationLearningRecordingCoordinator.stop(
                context = context,
                reason = OperationRecordingStopReason.UserFinished,
            )
            refresh()
        }
    }

    fun cancelRecording() {
        viewModelScope.launch {
            OperationLearningRecordingCoordinator.stop(
                context = context,
                reason = OperationRecordingStopReason.UserCancelled,
            )
            refresh()
        }
    }

    fun approveSkill(draftId: String) {
        val draft = uiState.drafts.firstOrNull { it.id == draftId } ?: return
        val skill = uiState.skillArtifacts[draftId]
        val activeApprovalRepository = approvalRepository
        if (skill == null || activeApprovalRepository == null) {
            loadSkillArtifact(draftId, retryIfCompiling = false)
            uiState = uiState.copy(notice = "完整 Skill 草稿正在加载，请稍后再批准。")
            return
        }
        val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.Review)
        if (!report.canProceed) {
            uiState = uiState.copy(
                notice = report.blockingIssues.firstOrNull()?.message ?: "Skill 尚未满足审核条件。",
            )
            return
        }
        viewModelScope.launch {
            runCatching { activeApprovalRepository.approve(draft, skill) }
                .onSuccess { version ->
                    uiState = uiState.copy(notice = "已批准视觉 Skill，并冻结为版本 $version。")
                    refresh()
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        notice = "Skill 批准失败：${error.message ?: "未知错误"}",
                    )
                }
        }
    }

    fun runSkill(draftId: String) {
        val activeContext = context
        val draft = uiState.drafts.firstOrNull { it.id == draftId }
        val skill = uiState.skillArtifacts[draftId]
        if (activeContext == null || draft == null || skill == null) {
            loadSkillArtifact(draftId, retryIfCompiling = false)
            uiState = uiState.copy(notice = "完整 Skill 正在加载，请稍后再运行。")
            return
        }
        if (uiState.runningSkillId != null || OperationLearningRecordingCoordinator.state.value.active) {
            uiState = uiState.copy(notice = "请先结束当前演示或正在运行的 Skill。")
            return
        }
        uiState = uiState.copy(
            runningSkillId = draftId,
            notice = "正在启动视觉 Skill…",
        )
        viewModelScope.launch {
            val outcome = OperationSkillReplayCoordinator.run(
                context = activeContext,
                draft = draft,
                skill = skill,
                inputValues = uiState.replayInputValues[draftId].orEmpty(),
            )
            uiState = uiState.copy(
                runningSkillId = null,
                notice = outcome.message,
            )
        }
    }

    fun deleteDraft(draftId: String) {
        val recordingWorkflowId = OperationLearningRecordingCoordinator.state.value.workflowId
        if (OperationLearningRecordingCoordinator.state.value.active && recordingWorkflowId == draftId) {
            uiState = uiState.copy(notice = "请先结束或取消当前视觉演示，再删除 Skill。")
            return
        }
        if (uiState.runningSkillId == draftId) {
            uiState = uiState.copy(notice = "当前 Skill 正在运行，请先停止任务。")
            return
        }
        val draft = uiState.drafts.firstOrNull { it.id == draftId } ?: return
        val previousDrafts = uiState.drafts
        val previousSkills = uiState.skillArtifacts
        val previousInputs = uiState.replayInputValues
        val previousSelection = uiState.selectedDraftId
        uiState = uiState.copy(
            drafts = previousDrafts.filterNot { it.id == draftId },
            skillArtifacts = previousSkills - draftId,
            replayInputValues = previousInputs - draftId,
            selectedDraftId = previousSelection.takeUnless { it == draftId },
            notice = "已删除 Skill“${draft.title}”。",
        )

        repository?.let { activeRepository ->
            viewModelScope.launch {
                runCatching {
                    activeRepository.deleteDraft(draftId)
                    withContext(Dispatchers.IO) { skillStore?.delete(draftId) }
                }.onFailure {
                    uiState = uiState.copy(
                        drafts = previousDrafts,
                        skillArtifacts = previousSkills,
                        replayInputValues = previousInputs,
                        selectedDraftId = previousSelection,
                        notice = "删除失败，Skill 已恢复。",
                    )
                }
            }
        }
    }

    fun clearNotice() {
        if (uiState.notice != null) uiState = uiState.copy(notice = null)
    }
}
