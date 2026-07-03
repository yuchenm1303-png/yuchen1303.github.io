package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.AgentDailyActivity
import java.io.IOException

internal class AgentAnalyticsCloudClient {
    @Throws(IOException::class)
    fun syncDaily(
        session: SupabaseUserSession,
        deviceId: String,
        changedDaily: List<AgentDailyActivity>,
        sinceDateKey: String,
    ): List<AgentDailyActivity> = emptyList()
}
