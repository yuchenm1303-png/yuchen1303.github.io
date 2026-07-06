package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.data.OperationSkillAssetSyncRepository
import com.yuchen.ailedger.model.LearnedVisualSkill
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.OperationSkillAssetSyncReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 异步触发视觉 Skill 账号同步，保证同步失败不会阻塞本地学习、审核或 Replay。 */
internal object OperationSkillAssetSyncRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun requestSyncAfterLearning(
        context: Context,
        workflowId: String,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            runCatchingSync {
                OperationSkillAssetSyncRepository.get(appContext).syncAfterLearning(workflowId)
            }
        }
    }

    fun requestSyncAfterApproval(
        context: Context,
        draft: LearnedWorkflowDraft,
        skill: LearnedVisualSkill,
        versionNumber: Int,
        approvedSnapshotJson: String,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            runCatchingSync {
                OperationSkillAssetSyncRepository.get(appContext).syncAfterApproval(
                    draft = draft,
                    skill = skill,
                    versionNumber = versionNumber,
                    approvedSnapshotJson = approvedSnapshotJson,
                )
            }
        }
    }

    fun requestFullSync(
        context: Context,
        reason: OperationSkillAssetSyncReason,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            runCatchingSync {
                OperationSkillAssetSyncRepository.get(appContext).syncAllVisibleAssets(reason)
            }
        }
    }

    private suspend inline fun runCatchingSync(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // 同步是旁路能力，失败只记录在 Repository 的状态里，不影响本地 Skill 主流程。
        }
    }
}
