package com.yuchen.ailedger.service

data class AgentExecutionResult(
    val ok: Boolean,
    val message: String,
    val shouldContinue: Boolean = true,
)
