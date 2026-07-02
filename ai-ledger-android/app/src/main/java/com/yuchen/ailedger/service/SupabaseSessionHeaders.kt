package com.yuchen.ailedger.service

import java.net.HttpURLConnection

internal fun HttpURLConnection.applySupabaseSessionHeaders(
    session: SupabaseUserSession,
    publishableKey: String,
) {
    setRequestProperty("apikey", publishableKey)
    setRequestProperty("Authorization", "Bearer ${session.accessToken}")
}
