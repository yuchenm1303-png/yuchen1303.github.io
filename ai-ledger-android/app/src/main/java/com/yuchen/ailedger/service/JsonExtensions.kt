package com.yuchen.ailedger.service

import org.json.JSONObject

internal fun JSONObject?.optString(key: String): String {
    return this?.optString(key).orEmpty()
}
