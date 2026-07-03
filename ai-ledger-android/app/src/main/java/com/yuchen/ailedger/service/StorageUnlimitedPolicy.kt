package com.yuchen.ailedger.service

internal object StorageUnlimitedPolicy {
    const val ENABLE_COMPLETE_SCAN: Boolean = true
    const val UNBOUNDED_COUNT: Int = Int.MAX_VALUE
    const val UNBOUNDED_BYTES: Long = Long.MAX_VALUE
    const val MINIMUM_FILE_BYTES: Long = 0L
}
