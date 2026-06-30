package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.firstNonBlank(vararg names: String): String? {
    for (name in names) if (has(name) && !isNull(name)) optString(name).trim()
        .takeIf { it.isNotBlank() }?.let { return it }
    return null
}

internal fun JSONObject.stringList(vararg names: String): List<String> {
    for (name in names) {
        if (!has(name) || isNull(name)) continue
        return when (val value = opt(name)) {
            is JSONArray -> buildList {
                for (index in 0 until value.length()) value.optString(index).trim()
                    .takeIf { it.isNotBlank() }?.take(160)?.let(::add)
            }
            is String -> value.split('|', ';', '；', '\n')
                .map { it.trim().take(160) }.filter { it.isNotBlank() }
            else -> emptyList()
        }.distinct().take(16)
    }
    return emptyList()
}

internal fun JSONObject.objectList(vararg names: String): List<JSONObject> {
    for (name in names) optJSONArray(name)?.let { array ->
        return buildList { for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add) }
    }
    return emptyList()
}

internal fun JSONObject.flexibleBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    return when (val raw = opt(name)) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> when (raw.trim().lowercase()) {
            "true", "1", "yes", "on", "enabled" -> true
            "false", "0", "no", "off", "disabled" -> false
            else -> null
        }
        else -> null
    }
}

internal fun JSONObject.optFlexibleInt(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return when (val raw = opt(name)) {
        is Number -> raw.toInt()
        is String -> raw.trim().toIntOrNull()
        else -> null
    }
}
