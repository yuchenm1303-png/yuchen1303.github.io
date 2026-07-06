package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.LearnedVisualSkill
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.OperationSkillAssetEnvelope
import com.yuchen.ailedger.model.OperationSkillAssetSyncReason
import com.yuchen.ailedger.model.OperationSkillAssetSyncResult
import com.yuchen.ailedger.model.OperationSkillAssetSyncSource
import com.yuchen.ailedger.model.OperationSkillAssetVersionEnvelope
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode
import com.yuchen.ailedger.service.AgentClientIdentity
import com.yuchen.ailedger.service.OperationSkillAssetCloudClient
import com.yuchen.ailedger.service.SupabaseUserSession
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 视觉 Skill 的账号级旁路同步层。
 *
 * 本仓库只同步云端生成后的 Skill 语义、工作流安全边界和用户审核快照；不会上传原始演示截图，
 * 也不会改变 record/replay 主流程。任何网络失败都降级为本地可用，不阻塞学习、审核或运行。
 */
internal class OperationSkillAssetSyncRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val authRepository = SupabaseAuthRepository.get(appContext)
    private val workflowRepository = OperationWorkflowRepository.get(appContext)
    private val skillStore = OperationSkillArtifactStore(appContext)
    private val client = OperationSkillAssetCloudClient()
    private val preferences = appContext.getSharedPreferences(
        "operation_skill_asset_sync",
        Context.MODE_PRIVATE,
    )
    private val mutex = Mutex()

    suspend fun syncAfterLearning(workflowId: String): OperationSkillAssetSyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = currentSession() ?: return@withLock skipped("未登录，Skill 已保留在本机。")
            val draft = workflowRepository.loadDraft(workflowId)
                ?: return@withLock skipped("找不到本机 Skill 草稿。")
            val skill = skillStore.load(workflowId)
                ?: return@withLock skipped("找不到本机 Skill 语义资产。")
            runSyncCatching {
                val uploaded = uploadAssetIfChanged(
                    session = session,
                    asset = buildAssetEnvelope(draft, skill),
                )
                markSuccessIfNeeded(uploaded)
                OperationSkillAssetSyncResult(
                    source = OperationSkillAssetSyncSource.Network,
                    uploadedCount = uploaded,
                    syncedAtMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    suspend fun syncAfterApproval(
        draft: LearnedWorkflowDraft,
        skill: LearnedVisualSkill,
        versionNumber: Int,
        approvedSnapshotJson: String,
    ): OperationSkillAssetSyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = currentSession() ?: return@withLock skipped("未登录，批准版本已保留在本机。")
            runSyncCatching {
                val approvedAtMillis = draft.updatedAtMillis.coerceAtLeast(System.currentTimeMillis())
                val asset = buildAssetEnvelope(
                    draft = draft,
                    skill = skill,
                    approvedSnapshotJson = approvedSnapshotJson,
                    currentVersionNumber = versionNumber,
                    approvedAtMillis = approvedAtMillis,
                )
                val uploadedAsset = uploadAssetIfChanged(session, asset)
                val version = OperationSkillAssetVersionEnvelope(
                    workflowId = draft.id,
                    versionId = stableVersionId(draft.id, versionNumber),
                    versionNumber = versionNumber,
                    snapshotJson = approvedSnapshotJson,
                    skillJson = OperationSkillJsonCodec.encode(skill),
                    contentDigest = digestOf(draft.id, versionNumber.toString(), approvedSnapshotJson),
                    approvedAtMillis = approvedAtMillis,
                )
                client.upsertApprovedVersion(
                    session = session,
                    deviceId = AgentClientIdentity.getOrCreateDeviceId(appContext),
                    version = version,
                )
                markSuccessIfNeeded(uploadedAsset + 1)
                OperationSkillAssetSyncResult(
                    source = OperationSkillAssetSyncSource.Network,
                    uploadedCount = uploadedAsset + 1,
                    syncedAtMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    suspend fun syncAllVisibleAssets(
        reason: OperationSkillAssetSyncReason,
    ): OperationSkillAssetSyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = currentSession() ?: return@withLock skipped("未登录，跳过 Skill 云同步。")
            runSyncCatching {
                var uploaded = 0
                var skipped = 0
                workflowRepository.loadDrafts().forEach { draft ->
                    val skill = skillStore.load(draft.id)
                    if (skill == null || draft.executionMode != WorkflowExecutionMode.CloudVisual) {
                        skipped += 1
                        return@forEach
                    }
                    val latestVersion = workflowRepository.loadLatestVersion(draft.id)
                    val approvedSnapshotJson = latestVersion?.snapshotJson
                        ?.takeIf { draft.status.isApprovedLike() }
                    uploaded += uploadAssetIfChanged(
                        session = session,
                        asset = buildAssetEnvelope(
                            draft = draft,
                            skill = skill,
                            approvedSnapshotJson = approvedSnapshotJson,
                            currentVersionNumber = latestVersion?.versionNumber,
                            approvedAtMillis = latestVersion?.approvedAtMillis,
                        ),
                    )
                }
                val pulled = pullRemoteAssetsLocked(session)
                markSuccessIfNeeded(uploaded + pulled.downloadedCount)
                OperationSkillAssetSyncResult(
                    source = OperationSkillAssetSyncSource.Network,
                    uploadedCount = uploaded,
                    downloadedCount = pulled.downloadedCount,
                    skippedCount = skipped + pulled.skippedCount,
                    syncedAtMillis = System.currentTimeMillis(),
                ).also {
                    preferences.edit().putString(LAST_REASON_KEY, reason.name).apply()
                }
            }
        }
    }

    suspend fun pullRemoteAssets(): OperationSkillAssetSyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = currentSession() ?: return@withLock skipped("未登录，跳过 Skill 云同步。")
            runSyncCatching {
                val result = pullRemoteAssetsLocked(session)
                markSuccessIfNeeded(result.downloadedCount)
                result.copy(
                    source = OperationSkillAssetSyncSource.Network,
                    syncedAtMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    suspend fun markDeleted(workflowId: String): OperationSkillAssetSyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = currentSession() ?: return@withLock skipped("未登录，删除状态只保留在本机。")
            runSyncCatching {
                val deletedAt = System.currentTimeMillis()
                client.markDeleted(
                    session = session,
                    deviceId = AgentClientIdentity.getOrCreateDeviceId(appContext),
                    workflowId = workflowId,
                    deletedAtMillis = deletedAt,
                )
                preferences.edit()
                    .remove(uploadedDigestKey(workflowId))
                    .putLong(LAST_SUCCESS_AT_KEY, deletedAt)
                    .apply()
                OperationSkillAssetSyncResult(
                    source = OperationSkillAssetSyncSource.Network,
                    uploadedCount = 1,
                    syncedAtMillis = deletedAt,
                )
            }
        }
    }

    private fun currentSession(): SupabaseUserSession? {
        val account = authRepository.state.value
        return account.session?.takeIf { account.isLoggedIn && it.isUsable }
    }

    private fun uploadAssetIfChanged(
        session: SupabaseUserSession,
        asset: OperationSkillAssetEnvelope,
    ): Int {
        if (preferences.getString(uploadedDigestKey(asset.workflowId), null) == asset.contentDigest) {
            return 0
        }
        client.upsertAsset(
            session = session,
            deviceId = AgentClientIdentity.getOrCreateDeviceId(appContext),
            asset = asset,
        )
        preferences.edit()
            .putString(uploadedDigestKey(asset.workflowId), asset.contentDigest)
            .apply()
        return 1
    }

    private suspend fun pullRemoteAssetsLocked(session: SupabaseUserSession): OperationSkillAssetSyncResult {
        val watermark = preferences.getLong(PULL_WATERMARK_KEY, 0L).coerceAtLeast(0L)
        val remoteAssets = client.fetchAssets(session, updatedAfterMillis = watermark)
        var downloaded = 0
        var skipped = 0
        var maxWatermark = watermark
        remoteAssets.forEach { asset ->
            maxWatermark = maxOf(maxWatermark, asset.localUpdatedAtMillis)
            val imported = importRemoteAsset(asset)
            if (imported) downloaded += 1 else skipped += 1
        }
        if (maxWatermark > watermark) {
            preferences.edit().putLong(PULL_WATERMARK_KEY, maxWatermark).apply()
        }
        return OperationSkillAssetSyncResult(
            source = OperationSkillAssetSyncSource.Network,
            downloadedCount = downloaded,
            skippedCount = skipped,
            syncedAtMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun importRemoteAsset(asset: OperationSkillAssetEnvelope): Boolean {
        if (asset.deletedAtMillis != null) return false
        val draft = runCatching { OperationWorkflowJsonCodec.decode(asset.workflowJson) }.getOrNull()
            ?: return false
        val skill = runCatching { OperationSkillJsonCodec.decode(asset.skillJson) }.getOrNull()
            ?: return false
        if (draft.id != asset.workflowId || skill.workflowId != asset.workflowId) return false
        val localDraft = workflowRepository.loadDraft(asset.workflowId)
        if (localDraft != null && shouldKeepLocal(localDraft, asset)) return false
        val syncedDraft = draft.copy(
            title = asset.title.ifBlank { draft.title },
            goal = asset.description.ifBlank { draft.goal },
            executionMode = WorkflowExecutionMode.CloudVisual,
            status = asset.status,
            updatedAtMillis = asset.localUpdatedAtMillis.coerceAtLeast(draft.updatedAtMillis),
            sourceDemonstrationId = asset.sourceDemonstrationId ?: draft.sourceDemonstrationId,
            milestones = emptyList(),
            steps = emptyList(),
            completionChecks = emptyList(),
        )
        workflowRepository.saveSyncedCloudVisualDraft(syncedDraft, skill)
        val approvedSnapshotJson = asset.approvedSnapshotJson
        val versionNumber = asset.currentVersionNumber
        if (!approvedSnapshotJson.isNullOrBlank() && versionNumber != null && versionNumber > 0) {
            workflowRepository.saveSyncedApprovedVersion(
                workflowId = asset.workflowId,
                versionId = stableVersionId(asset.workflowId, versionNumber),
                versionNumber = versionNumber,
                snapshotJson = approvedSnapshotJson,
                approvedAtMillis = asset.approvedAtMillis ?: asset.localUpdatedAtMillis,
            )
        }
        preferences.edit()
            .putString(uploadedDigestKey(asset.workflowId), asset.contentDigest)
            .apply()
        return true
    }

    private fun shouldKeepLocal(
        local: LearnedWorkflowDraft,
        remote: OperationSkillAssetEnvelope,
    ): Boolean {
        if (local.status == WorkflowDraftStatus.Compiling) return true
        if (local.status.isApprovedLike() && !remote.status.isApprovedLike()) return true
        if (!local.status.isApprovedLike() && remote.status.isApprovedLike()) return false
        return local.updatedAtMillis > remote.localUpdatedAtMillis
    }

    private fun buildAssetEnvelope(
        draft: LearnedWorkflowDraft,
        skill: LearnedVisualSkill,
        approvedSnapshotJson: String? = null,
        currentVersionNumber: Int? = null,
        approvedAtMillis: Long? = null,
    ): OperationSkillAssetEnvelope {
        val workflowJson = OperationWorkflowJsonCodec.encode(draft)
        val skillJson = OperationSkillJsonCodec.encode(skill)
        val digest = digestOf(
            draft.id,
            draft.status.name,
            draft.executionMode.name,
            workflowJson,
            skillJson,
            approvedSnapshotJson.orEmpty(),
            currentVersionNumber?.toString().orEmpty(),
        )
        return OperationSkillAssetEnvelope(
            workflowId = draft.id,
            title = draft.title.ifBlank { skill.name },
            description = draft.goal.ifBlank { skill.description },
            status = draft.status,
            executionMode = draft.executionMode,
            appPackages = draft.appScope.normalizedPackages,
            workflowJson = workflowJson,
            skillJson = skillJson,
            approvedSnapshotJson = approvedSnapshotJson,
            currentVersionNumber = currentVersionNumber,
            sourceDemonstrationId = draft.sourceDemonstrationId,
            contentDigest = digest,
            learnedAtMillis = skill.learnedAtMillis.coerceAtLeast(0L),
            localUpdatedAtMillis = draft.updatedAtMillis.coerceAtLeast(skill.learnedAtMillis),
            approvedAtMillis = approvedAtMillis,
            deletedAtMillis = null,
        )
    }

    private fun markSuccessIfNeeded(changedCount: Int) {
        if (changedCount <= 0) return
        preferences.edit()
            .putLong(LAST_SUCCESS_AT_KEY, System.currentTimeMillis())
            .remove(LAST_ERROR_KEY)
            .apply()
    }

    private suspend inline fun runSyncCatching(
        block: suspend () -> OperationSkillAssetSyncResult,
    ): OperationSkillAssetSyncResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        val message = error.message?.trim()?.take(180)?.takeIf(String::isNotBlank)
            ?: "视觉 Skill 云端同步暂时不可用，本机功能不受影响。"
        preferences.edit()
            .putString(LAST_ERROR_KEY, message)
            .apply()
        OperationSkillAssetSyncResult(
            source = OperationSkillAssetSyncSource.Failed,
            syncedAtMillis = preferences.getLong(LAST_SUCCESS_AT_KEY, 0L).coerceAtLeast(0L),
            errorMessage = message,
        )
    }

    private fun skipped(message: String): OperationSkillAssetSyncResult = OperationSkillAssetSyncResult(
        source = OperationSkillAssetSyncSource.Skipped,
        syncedAtMillis = preferences.getLong(LAST_SUCCESS_AT_KEY, 0L).coerceAtLeast(0L),
        errorMessage = message,
    )

    private fun WorkflowDraftStatus.isApprovedLike(): Boolean {
        return this == WorkflowDraftStatus.Approved || this == WorkflowDraftStatus.Verified
    }

    private fun uploadedDigestKey(workflowId: String): String {
        return "uploaded_digest_${workflowId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)}"
    }

    private fun stableVersionId(
        workflowId: String,
        versionNumber: Int,
    ): String = "cloud-${workflowId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)}-$versionNumber"

    private fun digestOf(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            digest.update(part.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val LAST_SUCCESS_AT_KEY = "last_success_at"
        private const val LAST_ERROR_KEY = "last_error"
        private const val LAST_REASON_KEY = "last_reason"
        private const val PULL_WATERMARK_KEY = "pull_watermark_millis"

        @Volatile
        private var instance: OperationSkillAssetSyncRepository? = null

        fun get(context: Context): OperationSkillAssetSyncRepository {
            return instance ?: synchronized(this) {
                instance ?: OperationSkillAssetSyncRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
