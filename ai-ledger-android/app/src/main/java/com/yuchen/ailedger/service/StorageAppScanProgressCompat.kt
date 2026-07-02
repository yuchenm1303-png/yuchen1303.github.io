package com.yuchen.ailedger.service

/**
 * analyzeNextAppPage 在校验并初始化进度后，局部变量仍保留可空静态类型。
 * 该重载只负责把已建立的非空进度复制为下一检查点，不改变扫描或持久化语义。
 */
internal fun StorageAppScanProgress?.copy(
    packageSignature: String = requireNotNull(this).packageSignature,
    processedCount: Int = requireNotNull(this).processedCount,
    totalCount: Int = requireNotNull(this).totalCount,
    startedAt: Long = requireNotNull(this).startedAt,
    updatedAt: Long = requireNotNull(this).updatedAt,
    complete: Boolean = requireNotNull(this).complete,
    interrupted: Boolean = requireNotNull(this).interrupted,
): StorageAppScanProgress {
    requireNotNull(this) { "应用扫描进度尚未初始化" }
    return StorageAppScanProgress(
        packageSignature = packageSignature,
        processedCount = processedCount,
        totalCount = totalCount,
        startedAt = startedAt,
        updatedAt = updatedAt,
        complete = complete,
        interrupted = interrupted,
    )
}
