package com.yuchen.ailedger.service

data class CompleteIndexedFile(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAt: Long,
    val location: String,
)
