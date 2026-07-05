package com.yuchen.ailedger.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val EMPTY_INLINE_STICKER_DIAGNOSTICS_JSON =
    """{"schema":"ai_ledger_inline_sticker_diagnostics_v1","state":"empty","hint":"先发送一条带表情设置的云端回复，再复制诊断。"}"""

internal data class InlineStickerDiagnosticsSnapshot(
    val updatedAtMillis: Long = 0L,
    val requestSummary: String = "暂无请求",
    val backendSummary: String = "暂无后端诊断",
    val mergeSummary: String = "暂无合并诊断",
    val exportJson: String = EMPTY_INLINE_STICKER_DIAGNOSTICS_JSON,
)

internal object InlineStickerDiagnosticsStore {
    private const val PreferencesName = "inline_sticker_diagnostics"
    private const val LatestJsonKey = "latest_json"
    private const val EmptyDiagnostics = ""

    private val visibleStickerRegex = Regex(
        """\[\[AI_LEDGER_INLINE_STICKER:[a-z0-9_]{2,48}]]""",
        RegexOption.IGNORE_CASE,
    )

    private val latestJsonState = MutableStateFlow(EmptyDiagnostics)
    private val _snapshot = MutableStateFlow(InlineStickerDiagnosticsSnapshot())

    val snapshot: StateFlow<InlineStickerDiagnosticsSnapshot> = _snapshot

    fun observe(context: Context?): StateFlow<String> {
        loadIfNeeded(context)
        return latestJsonState.asStateFlow()
    }

    fun latestJson(context: Context?): String {
        loadIfNeeded(context)
        return latestJsonState.value
    }

    fun recordLatest(context: Context?, diagnosticsJson: String?) {
        val clean = diagnosticsJson
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return
        latestJsonState.value = clean
        val appContext = context?.applicationContext ?: return
        appContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(LatestJsonKey, clean)
            .apply()
    }

    fun clear(context: Context?) {
        latestJsonState.value = EmptyDiagnostics
        val appContext = context?.applicationContext ?: return
        appContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .remove(LatestJsonKey)
            .apply()
    }

    fun emptyExportJson(): String = JSONObject(EMPTY_INLINE_STICKER_DIAGNOSTICS_JSON).toString(2)

    fun recordHttpExchange(
        route: AiWorkerModelRoute,
        payload: JSONObject,
        stream: Boolean,
        responseData: JSONObject?,
        streamedReply: String,
        finalReply: String,
        mergedReply: String,
        responseReply: String,
    ) {
        val request = payload.optJSONObject("chatExpressionPreferences") ?: JSONObject()
        val backendDiagnostics = extractBackendDiagnostics(responseData)
        val mergedVisibleReply = mergedReply.ifBlank { responseReply }
        val streamedTrimmed = streamedReply.trim()
        val finalTrimmed = finalReply.trim()
        val streamedCount = countVisibleMarkers(streamedReply)
        val finalCount = countVisibleMarkers(finalReply)
        val mergedCount = countVisibleMarkers(mergedVisibleReply)
        val selected = when {
            !stream -> "non_stream_response"
            streamedReply.isBlank() -> "final_reply"
            finalReply.isBlank() -> "streamed_reply"
            finalTrimmed == streamedTrimmed -> "same"
            finalTrimmed.startsWith(streamedTrimmed) -> "final_extends_stream"
            streamedCount > finalCount -> "streamed_has_more_markers"
            finalCount > 0 || streamedCount > 0 -> "final_has_marker"
            else -> "streamed_default"
        }
        val export = JSONObject()
            .put("schema", "ai_ledger_inline_sticker_diagnostics_v1")
            .put("updatedAtMillis", System.currentTimeMillis())
            .put("route", JSONObject().apply {
                put("requestedModel", route.requested.id)
                put("resolvedModel", route.resolved.id)
                put("autoRequested", route.isAuto)
            })
            .put("request", JSONObject().apply {
                put("requestId", payload.optString("requestId"))
                put("stream", stream)
                put("modelPreference", payload.optString("modelPreference"))
                put("chatExpressionPreferences", JSONObject(request.toString()))
            })
            .put("backend", JSONObject().apply {
                put("hasDiagnostics", backendDiagnostics != null)
                put("diagnostics", backendDiagnostics ?: JSONObject())
                put("finalReplyLength", finalReply.length)
                put("responseReplyLength", responseReply.length)
                put("finalVisibleMarkerCount", finalCount)
            })
            .put("appMerge", JSONObject().apply {
                put("streamedReplyLength", streamedReply.length)
                put("streamedVisibleMarkerCount", streamedCount)
                put("finalReplyLength", finalReply.length)
                put("finalVisibleMarkerCount", finalCount)
                put("mergedReplyLength", mergedVisibleReply.length)
                put("mergedVisibleMarkerCount", mergedCount)
                put("decision", selected)
            })
            .put("acceptedMarkerKeys", JSONArray(extractVisibleKeys(mergedVisibleReply)))
            .put("quickRead", JSONObject().apply {
                put("frequency", request.optInt("inlineStickerFrequency", -1))
                put("intensity", request.optInt("inlineStickerIntensity", -1))
                put("maxPerReply", request.optInt("inlineStickerMaxPerReply", -1))
                put("repeatCount", request.optInt("inlineStickerRepeatCount", -1))
                put("backendTarget", backendDiagnostics?.opt("targetStickerLocationCount") ?: JSONObject.NULL)
                put("backendModelRawLocations", backendDiagnostics?.opt("modelRawMarkerLocationCount") ?: JSONObject.NULL)
                put("backendFinalMarkers", backendDiagnostics?.opt("finalRetainedMarkerCount") ?: JSONObject.NULL)
                put("appMergedMarkers", mergedCount)
            })
        _snapshot.value = InlineStickerDiagnosticsSnapshot(
            updatedAtMillis = export.optLong("updatedAtMillis"),
            requestSummary = "频率 ${request.optInt("inlineStickerFrequency", -1)} · 强度 ${request.optInt("inlineStickerIntensity", -1)} · 上限 ${request.optInt("inlineStickerMaxPerReply", -1)} · 重复 ${request.optInt("inlineStickerRepeatCount", -1)}",
            backendSummary = backendDiagnostics?.let { diagnostics ->
                "目标 ${diagnostics.optString("targetStickerLocationCount", "?")} · 模型 ${diagnostics.optString("modelRawMarkerLocationCount", "?")} · 最终 ${diagnostics.optString("finalRetainedMarkerCount", "?")}"
            } ?: "后端未返回 stickerDiagnostics",
            mergeSummary = "stream $streamedCount · final $finalCount · app $mergedCount · $selected",
            exportJson = export.toString(2),
        )
    }

    private fun loadIfNeeded(context: Context?) {
        if (latestJsonState.value.isNotBlank()) return
        val appContext = context?.applicationContext ?: return
        val saved = appContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getString(LatestJsonKey, EmptyDiagnostics)
            .orEmpty()
            .trim()
        if (saved.isNotBlank()) latestJsonState.value = saved
    }

    private fun extractBackendDiagnostics(data: JSONObject?): JSONObject? {
        if (data == null) return null
        return data.optJSONObject("stickerDiagnostics")
            ?: data.optJSONObject("inlineStickerDiagnostics")
            ?: data.optJSONObject("diagnostics")?.optJSONObject("stickers")
            ?: data.optJSONObject("data")?.optJSONObject("stickerDiagnostics")
            ?: data.optJSONObject("result")?.optJSONObject("stickerDiagnostics")
    }

    private fun countVisibleMarkers(value: String): Int = visibleStickerRegex.findAll(value).count()

    private fun extractVisibleKeys(value: String): List<String> {
        return visibleStickerRegex.findAll(value)
            .map { match -> match.value.substringAfter(':').substringBefore("]]").lowercase() }
            .toList()
    }
}
