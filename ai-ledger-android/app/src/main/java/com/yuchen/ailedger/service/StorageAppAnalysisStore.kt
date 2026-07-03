package com.yuchen.ailedger.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val APP_ANALYSIS_PREFS = "storage_productization"
private const val APP_ANALYSIS_PROGRESS_KEY = "app_scan_progress"
private const val APP_ANALYSIS_RESULTS_KEY = "app_scan_results"

internal class StorageAppAnalysisStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(APP_ANALYSIS_PREFS, Context.MODE_PRIVATE)

    fun readProgress(): StorageAppScanProgress? {
        val raw = prefs.getString(APP_ANALYSIS_PROGRESS_KEY, null).orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            val item = JSONObject(raw)
            StorageAppScanProgress(
                packageSignature = item.optString("packageSignature"),
                processedCount = item.optInt("processedCount"),
                totalCount = item.optInt("totalCount"),
                startedAt = item.optLong("startedAt"),
                updatedAt = item.optLong("updatedAt"),
                complete = item.optBoolean("complete"),
                interrupted = item.optBoolean("interrupted"),
            )
        }.getOrNull()
    }

    fun readResults(): List<StorageAppOptimizationItem> {
        val raw = prefs.getString(APP_ANALYSIS_RESULTS_KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        StorageAppOptimizationItem(
                            label = item.optString("label"),
                            packageName = item.optString("packageName"),
                            apkBytes = item.optLong("apkBytes"),
                            appBytes = item.optionalLong("appBytes"),
                            dataBytes = item.optionalLong("dataBytes"),
                            cacheBytes = item.optionalLong("cacheBytes"),
                            totalBytes = item.optionalLong("totalBytes"),
                            firstInstallTime = item.optLong("firstInstallTime"),
                            lastUsedAt = item.optionalLong("lastUsedAt"),
                            unusedDays = item.optionalInt("unusedDays"),
                            isProtected = item.optBoolean("isProtected"),
                            suggestionReason = item.optString("suggestionReason"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun write(progress: StorageAppScanProgress, results: List<StorageAppOptimizationItem>) {
        val progressJson = JSONObject()
            .put("packageSignature", progress.packageSignature)
            .put("processedCount", progress.processedCount)
            .put("totalCount", progress.totalCount)
            .put("startedAt", progress.startedAt)
            .put("updatedAt", progress.updatedAt)
            .put("complete", progress.complete)
            .put("interrupted", progress.interrupted)
        val resultsJson = JSONArray()
        results.forEach { item ->
            resultsJson.put(
                JSONObject()
                    .put("label", item.label)
                    .put("packageName", item.packageName)
                    .put("apkBytes", item.apkBytes)
                    .putNullable("appBytes", item.appBytes)
                    .putNullable("dataBytes", item.dataBytes)
                    .putNullable("cacheBytes", item.cacheBytes)
                    .putNullable("totalBytes", item.totalBytes)
                    .put("firstInstallTime", item.firstInstallTime)
                    .putNullable("lastUsedAt", item.lastUsedAt)
                    .putNullable("unusedDays", item.unusedDays)
                    .put("isProtected", item.isProtected)
                    .put("suggestionReason", item.suggestionReason),
            )
        }
        prefs.edit()
            .putString(APP_ANALYSIS_PROGRESS_KEY, progressJson.toString())
            .putString(APP_ANALYSIS_RESULTS_KEY, resultsJson.toString())
            .commit()
    }

    private fun JSONObject.optionalLong(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
    private fun JSONObject.optionalInt(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
}
