package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.LearnedVisualSkill
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.service.OperationSkillAssetSyncRuntime
import java.util.UUID

/**
 * 用户批准后把完整视觉 Skill 与本地安全边界一起冻结为不可变版本。
 * 审核快照不依赖历史步骤表，也不会把 Skill 降级为空的固定路线壳。
 */
class OperationSkillApprovalRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = OperationWorkflowDatabase.get(appContext).workflowDao()

    suspend fun approve(
        draft: LearnedWorkflowDraft,
        skill: LearnedVisualSkill,
        approvedAtMillis: Long = System.currentTimeMillis(),
    ): Int {
        require(draft.id == skill.workflowId) { "Skill 与草稿不属于同一工作流" }
        require(draft.status == WorkflowDraftStatus.ReadyForReview) { "Skill 尚未进入可审核状态" }
        val approvedDraft = draft.copy(
            status = WorkflowDraftStatus.Approved,
            updatedAtMillis = approvedAtMillis,
        )
        val snapshotJson = OperationSkillJsonCodec.encodeApprovedSnapshot(
            draft = approvedDraft,
            skill = skill,
        )
        val versionNumber = dao.approveWorkflow(
            workflowId = draft.id,
            versionId = UUID.randomUUID().toString(),
            snapshotJson = snapshotJson,
            changeSummary = "由视觉演示生成并经用户审核",
            approvedAtMillis = approvedAtMillis,
        )
        OperationSkillAssetSyncRuntime.requestSyncAfterApproval(
            context = appContext,
            draft = approvedDraft,
            skill = skill,
            versionNumber = versionNumber,
            approvedSnapshotJson = snapshotJson,
        )
        return versionNumber
    }
}
