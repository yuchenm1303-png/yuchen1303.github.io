package com.yuchen.ailedger.model

internal data class OperationSkillAssetEnvelope(
    val workflowId: String,
    val title: String,
    val description: String,
    val status: WorkflowDraftStatus,
    val executionMode: WorkflowExecutionMode,
    val appPackages: List<String>,
    val workflowJson: String,
    val skillJson: String,
    val approvedSnapshotJson: String? = null,
    val currentVersionNumber: Int? = null,
    val sourceDemonstrationId: String? = null,
    val contentDigest: String,
    val learnedAtMillis: Long,
    val localUpdatedAtMillis: Long,
    val approvedAtMillis: Long? = null,
    val deletedAtMillis: Long? = null,
)

internal data class OperationSkillAssetVersionEnvelope(
    val workflowId: String,
    val versionId: String,
    val versionNumber: Int,
    val snapshotJson: String,
    val skillJson: String,
    val contentDigest: String,
    val approvedAtMillis: Long,
)

internal enum class OperationSkillAssetSyncReason {
    AccountReady,
    LearningCompleted,
    ApprovalCompleted,
    Manual,
}

internal enum class OperationSkillAssetSyncSource {
    Network,
    Skipped,
    Failed,
}

internal data class OperationSkillAssetSyncResult(
    val source: OperationSkillAssetSyncSource = OperationSkillAssetSyncSource.Skipped,
    val uploadedCount: Int = 0,
    val downloadedCount: Int = 0,
    val skippedCount: Int = 0,
    val syncedAtMillis: Long = 0L,
    val errorMessage: String? = null,
)

internal data class OperationSkillAssetSyncStatusSnapshot(
    val lastSuccessAtMillis: Long = 0L,
    val lastError: String? = null,
    val pullWatermarkMillis: Long = 0L,
    val lastReason: String? = null,
) {
    val hasSyncedBefore: Boolean
        get() = lastSuccessAtMillis > 0L

    val hasError: Boolean
        get() = !lastError.isNullOrBlank()
}
