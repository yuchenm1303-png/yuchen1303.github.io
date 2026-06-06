package com.yuchen.ailedger.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

数据 class AgentPendingConfirmation(
    val id: Long = System.currentTimeMillis(),
    val title: String = "需要确认",
    val actionText: String,
    val message: String,
    val positiveText: String = "继续执行",
    val negativeText: String = "取消任务",
)
