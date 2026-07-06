package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.OperationSkillAssetSyncStatusSnapshot

internal class OperationSkillAssetSyncStatusStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "operation_skill_asset_sync",
        Context.MODE_PRIVATE,
    )

    fun read(): OperationSkillAssetSyncStatusSnapshot = OperationSkillAssetSyncStatusSnapshot(
        lastSuccessAtMillis = preferences.getLong(LAST_SUCCESS_AT_KEY, 0L).coerceAtLeast(0L),
        lastError = preferences.getString(LAST_ERROR_KEY, null)
            ?.trim()
            ?.takeIf(String::isNotBlank),
        pullWatermarkMillis = preferences.getLong(PULL_WATERMARK_KEY, 0L).coerceAtLeast(0L),
        lastReason = preferences.getString(LAST_REASON_KEY, null)
            ?.trim()
            ?.takeIf(String::isNotBlank),
    )

    companion object {
        private const val LAST_SUCCESS_AT_KEY = "last_success_at"
        private const val LAST_ERROR_KEY = "last_error"
        private const val LAST_REASON_KEY = "last_reason"
        private const val PULL_WATERMARK_KEY = "pull_watermark_millis"

        @Volatile
        private var instance: OperationSkillAssetSyncStatusStore? = null

        fun get(context: Context): OperationSkillAssetSyncStatusStore {
            return instance ?: synchronized(this) {
                instance ?: OperationSkillAssetSyncStatusStore(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
