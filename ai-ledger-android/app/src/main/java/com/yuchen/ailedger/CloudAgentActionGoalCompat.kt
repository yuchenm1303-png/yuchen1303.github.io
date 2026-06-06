package com.yuchen.ailedger

import com.yuchen.ailedger.service.CloudAgentAction

internal val CloudAgentAction.goal: String?
    get() = reason?.takeIf { it.isNotBlank() } ?: title?.takeIf { it.isNotBlank() }
