package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.data.OperationSkillArtifactStore
import com.yuchen.ailedger.data.OperationWorkflowRepository
import com.yuchen.ailedger.data.VisualDemonstrationStore
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode
import com.yuchen.ailedger.model.WorkflowVariableDefinition
import com.yuchen.ailedger.model.WorkflowVariableType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SkillLearningOutcome(
    val completed: Boolean,
    val message: String,
)

/** 将整段视觉证据交给云端理解；本地不再把事件编译成固定步骤或节点路线。 */
object OperationSkillLearningCoordinator {
    suspend fun learn(
        context: Context,
        workflowId: String,
        manifestPath: String,
    ): SkillLearningOutcome {
        val applicationContext = context.applicationContext
        val repository = OperationWorkflowRepository.get(applicationContext)
        val draft = repository.loadDraft(workflowId)
            ?: return SkillLearningOutcome(false, "找不到对应的 Skill 草稿")
        val skill = runCatching {
            OperationSkillCloudClient(applicationContext).synthesize(manifestPath)
        }.getOrElse { error ->
            return SkillLearningOutcome(
                completed = false,
                message = "云端暂时无法理解这次视觉演示：${error.message ?: "未知错误"}",
            )
        }
        if (skill.description.isBlank()) {
            return SkillLearningOutcome(false, "云端返回的 Skill 缺少用途说明")
        }

        return runCatching {
            withContext(Dispatchers.IO) {
                OperationSkillArtifactStore(applicationContext).save(skill)
                val learnedDraft = draft.copy(
                    title = skill.name.ifBlank { draft.title },
                    goal = skill.description.ifBlank { draft.goal },
                    variables = skill.inputs.map { input ->
                        WorkflowVariableDefinition(
                            key = input.key,
                            label = input.label,
                            type = if (input.sensitive) {
                                WorkflowVariableType.SecretReference
                            } else {
                                WorkflowVariableType.Text
                            },
                            required = input.required,
                            sensitive = input.sensitive,
                            persistValue = false,
                            description = input.description,
                        )
                    },
                    milestones = emptyList(),
                    steps = emptyList(),
                    completionChecks = emptyList(),
                    executionMode = WorkflowExecutionMode.CloudVisual,
                    status = WorkflowDraftStatus.ReadyForReview,
                    updatedAtMillis = System.currentTimeMillis(),
                    sourceDemonstrationId = skill.workflowId.takeIf { draft.sourceDemonstrationId == null }
                        ?.let { draft.sourceDemonstrationId }
                        ?: draft.sourceDemonstrationId,
                )
                repository.saveCompiledDraft(
                    draft = learnedDraft.copy(sourceDemonstrationId = draft.sourceDemonstrationId),
                    demonstrationId = requireNotNull(draft.sourceDemonstrationId) {
                        "演示会话尚未登记"
                    },
                )
                VisualDemonstrationStore(applicationContext).delete(manifestPath)
            }
            SkillLearningOutcome(
                completed = true,
                message = "云端已理解这次演示并生成视觉 Skill 草稿，等待你审核。",
            )
        }.getOrElse { error ->
            SkillLearningOutcome(
                completed = false,
                message = "Skill 草稿保存失败：${error.message ?: "未知错误"}",
            )
        }
    }
}
