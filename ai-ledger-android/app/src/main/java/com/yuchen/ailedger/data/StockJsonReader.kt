package com.yuchen.ailedger.data

import org.json.JSONArray
import org.json.JSONObject

/** 股票接口共享的宽容 JSON 读取工具，统一处理 data/payload/result 多层包装。 */
internal object StockJsonReader {
    fun payloadObject(root: JSONObject): JSONObject {
        for (key in listOf("data", "payload", "result")) {
            val nested = root.optJSONObject(key) ?: continue
            if (hasKnownPayload(nested)) return nested
        }
        return root
    }

    fun moduleItemsArray(module: JSONObject?): JSONArray? {
        if (module == null) return null
        module.optJSONArray("items")?.let { return it }
        module.optJSONArray("data")?.let { return it }
        module.optJSONArray("result")?.let { return it }
        for (key in listOf("data", "result", "payload")) {
            val nested = module.optJSONObject(key) ?: continue
            nested.optJSONArray("items")?.let { return it }
            nested.optJSONArray("data")?.let { return it }
            nested.optJSONArray("result")?.let { return it }
        }
        return null
    }

    fun moduleItemsObject(module: JSONObject?): JSONObject {
        if (module == null) return JSONObject()
        module.optJSONObject("items")?.let { return it }
        module.optJSONObject("data")?.let { data ->
            data.optJSONObject("items")?.let { return it }
            return data
        }
        module.optJSONObject("result")?.let { result ->
            result.optJSONObject("items")?.let { return it }
            return result
        }
        return JSONObject()
    }

    fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    fun firstText(obj: JSONObject?, vararg keys: String): String? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val text = obj.opt(key)?.toString()?.trim().orEmpty()
            if (text.isNotBlank() && text != "null" && text != "NaN") return text
        }
        return null
    }

    fun firstInt(obj: JSONObject?, vararg keys: String): Int? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = when (val raw = obj.opt(key)) {
                is Number -> raw.toInt()
                is String -> raw.toDoubleOrNull()?.toInt()
                else -> null
            }
            if (value != null) return value
        }
        return null
    }

    fun firstLong(obj: JSONObject?, vararg keys: String): Long? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = when (val raw = obj.opt(key)) {
                is Number -> raw.toLong()
                is String -> raw.toDoubleOrNull()?.toLong()
                else -> null
            }
            if (value != null) return value
        }
        return null
    }

    fun firstDouble(obj: JSONObject?, vararg keys: String): Double? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = when (val raw = obj.opt(key)) {
                is Number -> raw.toDouble()
                is String -> raw.replace("%", "").replace(",", "").toDoubleOrNull()
                else -> null
            }
            if (value != null && !value.isNaN()) return value
        }
        return null
    }

    fun firstBoolean(obj: JSONObject?, vararg keys: String): Boolean? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            return when (val raw = obj.opt(key)) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                is String -> when (raw.trim().lowercase()) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> null
                }
                else -> null
            }
        }
        return null
    }

    private fun hasKnownPayload(value: JSONObject): Boolean {
        return KNOWN_PAYLOAD_KEYS.any(value::has)
    }

    private val KNOWN_PAYLOAD_KEYS = setOf(
        "indices",
        "marketBreadth",
        "sentiment",
        "sectorHotRanking",
        "gainers",
        "losers",
        "amountRanking",
        "profile",
        "financialsSummary",
        "capitalSummary",
        "items",
        "status"
    )
}
