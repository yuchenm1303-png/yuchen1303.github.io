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
import com.yuchen.ailedger.data.OperationWorkflowSummaryRepository
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
import kotlinx.coroutines.Job
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
    private val summaryRepository = context?.let(::OperationWorkflowSummaryRepository)
    private val skillStore: OperationSkillArtifactStore? by lazy(LazyThreadSafetyMode.NONE) {
        context?.let(::OperationSkillArtifactStore)
    }
    private val approvalRepository: OperationSkillApprovalRepository? by lazy(LazyThreadSafetyMode.NONE) {
        context?.let(::OperationSkillApprovalRepository)
    }
    private val retryingWorkflowIds = mutableSetOf<String>()
    private val draftSaveJobs = mutableMapOf<String, Job>()
    private var draftsLoadJob: Job? = null
    private var fullDraftsLoaded = false

    var uiState by mutableStateOf(OperationLearningUiState())
        private set

    init {
        loadSummaries()
    }

    /**
     * 功能首页只读取主表摘要，不展开变量、里程碑、步骤、选择器和状态检查图谱。
     * 这条路径固定为一次 Room 事务查询，避免 Skill 数量增加后形成 N+1 查询风暴。
     */
    private fun loadSummaries() {
        val activeRepository = summaryRepository ?: return
        draftsLoadJob?.cancel()
        draftsLoadJob = viewModelScope.launch {
            uiState = uiState.copy(loading = true)
            runCatching { activeRepository.loadSummaries() }
                .onSuccess { drafts ->
                    publishDrafts(drafts, detailsLoaded = false)
                }
                .onFailure {
                    uiState = uiState.copy(
                        loading = false,
                        notice = "Skill 摘要加载失败，请稍后重试。",
                    )
                }
            draftsLoadJob = null
        }
    }

    /**
     * 仅在用户真正进入操作学习页面后加载完整工作流图谱。
     * 录制、审核、批准和运行始终建立在完整模型上，不会使用首页的精简摘要执行功能。
     */
    fun refresh() {
        val activeRepository = repository ?: return
        draftsLoadJob?.cancel()
        draftsLoadJob = viewModelScope.launch {
            uiState = uiState.copy(loading = true)
            runCatching { activeRepository.loadDrafts() }
                .onSuccess { drafts ->
                    publishDrafts(drafts, detailsLoaded = true)
                    uiState.selectedDraftId?.let { selectedId ->
                        loadSkillArtifact(selectedId, retryIfCompiling = false)
                    }
                }
                .onFailure {
                    uiState = uiState.copy(
                        loading = false,
                        notice = "Skill 草稿加载失败，请稍后重试。",
                    )
                }
            draftsLoadJob = null
        }
    }

    private fun publishDrafts(
        drafts: List<LearnedWorkflowDraft>,
        detailsLoaded: Boolean,
    ) {
        val selectedId = uiState.selectedDraftId?.takeIf { selected ->
            drafts.any { it.id == selected }
        }
        fullDraftsLoaded = detailsLoaded
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
            notice = "正在保存 Skill 教学草稿…",
        )

        val activeRepository = repository
        if (activeRepository == null) {
            uiState = uiState.copy(
                drafts = uiState.drafts.filterNot { it.id == draft.id },
                selectedDraftId = uiState.selectedDraftId.takeUnless { it == draft.id },
                notice = "Skill 草稿未能保存到本机，请重新创建。",
            )
            return false
        }

        val saveJob = viewModelScope.launch {
            runCatching { activeRepository.saveIntent(draft) }
                .onSuccess {
                    uiState = uiState.copy(notice = "已创建 Skill 教学草稿，可以开始视觉演示。")
                }
                .onFailure {
                    uiState = uiState.copy(
                        drafts = uiState.drafts.filterNot { it.id == draft.id },
                        selectedDraftId = uiState.selectedDraftId.takeUnless { it == draft.id },
                        notice = "Skill 草稿未能保存到本机，请重新创建。",
                    )
                }
            draftSaveJobs.remove(draft.id)
        }
        draftSaveJobs[draft.id] = saveJob
        return true
    }

    fun selectDraft(draftId: String) {
        val draft = uiState.drafts.firstOrNull { it.id == draftId } ?: return
        uiState = uiState.copy(selectedDraftId = draftId, notice = null)
        if (!fullDraftsLoaded) {
            uiState = uiState.copy(notice = "正在加载完整 Skill 数据…")
            refresh()
            return
        }
        loadSkillArtifact(
            draftId = draftId,
            retryIfCompiling = draft.status == WorkflowDraftStatus.Compiling,
        )
    }

    fun startRecording(draftId: String) {
        val activeContext = context
        val activeRepository = repository
        val optimisticDraft = uiState.drafts.firstOrNull { it.id == draftId }
        if (activeContext == null || activeRepository == null || optimisticDraft == null) {
            uiState = uiState.copy(notice = "Skill 草稿尚未准备完成，请重新进入页面。")
            return
        }

        uiState = uiState.copy(
            selectedDraftId = draftId,
            notice = "正在启动视觉演示…",
        )
        viewModelScope.launch {
            draftSaveJobs[draftId]?.join()
            if (uiState.drafts.none { it.id == draftId }) return@launch

            val draft = runCatching { activeRepository.loadDraft(draftId) }.getOrNull()
            if (draft == null) {
                uiState = uiState.copy(notice = "Skill 草稿仍未保存完成，请稍后再试。")
                return@launch
            }
            if (draft.status != WorkflowDraftStatus.Intent) {
                uiState = uiState.copy(notice = "当前 Skill 已进入后续阶段，不能覆盖原始演示。")
                return@launch
            }
            val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.RecordingIntent)
            if (!report.canProceed) {
                uiState = uiState.copy(
                    selectedDraftId = draft.id,
                    notice = report.blockingIssues.firstOrNull()?.message ?: "Skill 尚未满足演示条件。",
                )
                return@launch
            }

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
        if (!ensureFullDraftsReady()) return
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
        if (!ensureFullDraftsReady()) return
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
        if (!ensureFullDraftsReady()) return
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

    private fun ensureFullDraftsReady(): Boolean {
        if (fullDraftsLoaded) return true
        uiState = uiState.copy(notice = "完整 Skill 数据正在加载，请稍后再试。")
        refresh()
        return false
    }

    fun clearNotice() {
        if (uiState.notice != null) uiState = uiState.copy(notice = null)
    }
}
