package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.AiLedgerApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val MEMORY_DIAGNOSTICS_PREFERENCES = "assistant_memory_diagnostics_v1"
private const val MEMORY_DIAGNOSTICS_KEY = "records"
private const val MEMORY_DIAGNOSTICS_MAX_RECORDS = 30
private const val MEMORY_DIAGNOSTICS_MAX_CANDIDATES_PER_STAGE = 40

/** 云端记忆候选在某一处理阶段的真实状态。 */
data class AssistantMemoryDiagnosticItem(
    val id: String = "",
    val stage: String = "",
    val content: String = "",
    val layer: String = "",
    val authority: String = "",
    val namespaceType: String = "",
    val namespaceId: String = "",
    val subjectKey: String = "",
    val conflictKey: String = "",
    val priority: Int = 0,
    val pinned: Boolean = false,
    val confidence: Double = 0.0,
    val retrievalScore: Double = 0.0,
    val retrievalSource: String = "",
    val retrievalReason: String = "",
    val selectionConfidence: Double = 0.0,
    val selectionReason: String = "",
    val disposition: String = "",
    val dispositionReason: String = "",
)

/** 一句用户消息对应的一条完整记忆调用链。 */
data class AssistantMemoryDiagnosticRecord(
    val requestId: String,
    val createdAtMillis: Long,
    val prompt: String,
    val replyPreview: String,
    val model: String,
    val backendVersion: String,
    val requestMode: String,
    val requestSchema: String,
    val requestEnabled: Boolean,
    val memoryUsed: Boolean,
    val memoryStatus: String,
    val memorySource: String,
    val degraded: Boolean,
    val itemCount: Int,
    val gateStatus: String,
    val budgetLevel: String,
    val retrievalStatus: String,
    val embeddingStatus: String,
    val rerankStatus: String,
    val candidateCount: Int,
    val anchorCandidateCount: Int,
    val dynamicCandidateCount: Int,
    val filteredHistoryCount: Int,
    val filteredSensitiveCount: Int,
    val totalMs: Long,
    val stageTimings: Map<String, Long>,
    val selectedItems: List<AssistantMemoryDiagnosticItem>,
    val anchorCandidates: List<AssistantMemoryDiagnosticItem>,
    val dynamicCandidates: List<AssistantMemoryDiagnosticItem>,
    val mergedCandidates: List<AssistantMemoryDiagnosticItem>,
    val error: String,
    val traceAvailable: Boolean,
    val mutationRequested: Boolean = false,
    val mutationHandled: Boolean = false,
    val mutationAction: String = "",
    val mutationStatus: String = "",
    val mutationApplied: Boolean = false,
    val mutationOperationId: String = "",
    val mutationAffectedCount: Int = 0,
    val mutationIdempotentReplay: Boolean = false,
    val mutationRequiresClarification: Boolean = false,
    val mutationTrigger: String = "",
    val mutationRouterStatus: String = "",
    val mutationTotalMs: Long = 0L,
    val mutationStageTimings: Map<String, Long> = emptyMap(),
    val mutationError: String = "",
) {
    val statusLabel: String
        get() = when {
            error.isNotBlank() || mutationError.isNotBlank() -> "错误"
            mutationApplied -> "记忆已变更"
            mutationRequiresClarification -> "需要确认"
            memoryUsed -> "已注入 $itemCount 条"
            memoryStatus.startsWith("disabled") -> "未启用"
            degraded -> "降级未命中"
            else -> "未命中"
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("requestId", requestId)
        put("createdAtMillis", createdAtMillis)
        put("prompt", prompt)
        put("replyPreview", replyPreview)
        put("model", model)
        put("backendVersion", backendVersion)
        put("requestMode", requestMode)
        put("requestSchema", requestSchema)
        put("requestEnabled", requestEnabled)
        put("memoryUsed", memoryUsed)
        put("memoryStatus", memoryStatus)
        put("memorySource", memorySource)
        put("degraded", degraded)
        put("itemCount", itemCount)
        put("gateStatus", gateStatus)
        put("budgetLevel", budgetLevel)
        put("retrievalStatus", retrievalStatus)
        put("embeddingStatus", embeddingStatus)
        put("rerankStatus", rerankStatus)
        put("candidateCount", candidateCount)
        put("anchorCandidateCount", anchorCandidateCount)
        put("dynamicCandidateCount", dynamicCandidateCount)
        put("filteredHistoryCount", filteredHistoryCount)
        put("filteredSensitiveCount", filteredSensitiveCount)
        put("totalMs", totalMs)
        put("stageTimings", JSONObject(stageTimings))
        put("selectedItems", selectedItems.toJsonArray())
        put("anchorCandidates", anchorCandidates.toJsonArray())
        put("dynamicCandidates", dynamicCandidates.toJsonArray())
        put("mergedCandidates", mergedCandidates.toJsonArray())
        put("error", error)
        put("traceAvailable", traceAvailable)
        put("mutationRequested", mutationRequested)
        put("mutationHandled", mutationHandled)
        put("mutationAction", mutationAction)
        put("mutationStatus", mutationStatus)
        put("mutationApplied", mutationApplied)
        put("mutationOperationId", mutationOperationId)
        put("mutationAffectedCount", mutationAffectedCount)
        put("mutationIdempotentReplay", mutationIdempotentReplay)
        put("mutationRequiresClarification", mutationRequiresClarification)
        put("mutationTrigger", mutationTrigger)
        put("mutationRouterStatus", mutationRouterStatus)
        put("mutationTotalMs", mutationTotalMs)
        put("mutationStageTimings", JSONObject(mutationStageTimings))
        put("mutationError", mutationError)
    }
}

data class AssistantMemoryDiagnosticsState(
    val records: List<AssistantMemoryDiagnosticRecord> = emptyList(),
) {
    val latest: AssistantMemoryDiagnosticRecord? get() = records.firstOrNull()
}

/**
 * 仅保存排障所需的记忆元数据，不保存登录令牌、请求头、图片或完整聊天历史。
 * 每条记录最多保留有限数量的候选，防止设置页和本地存储无限增长。
 */
object AssistantMemoryDiagnostics {
    private val lock = Any()
    private var loaded = false
    private val mutableState = MutableStateFlow(AssistantMemoryDiagnosticsState())
    val state: StateFlow<AssistantMemoryDiagnosticsState> = mutableState.asStateFlow()

    fun record(payload: JSONObject, response: JSONObject?, failure: Throwable? = null) {
        ensureLoaded()
        val trace = response?.optJSONObject("memoryTrace") ?: JSONObject()
        val tracePrompt = trace.optString("prompt").trim()
        val stageTimings = parseLongMap(response?.optJSONObject("memoryStageTimings"))
        val mutation = response?.optJSONObject("memoryMutation")
        val mutationStageTimings = parseLongMap(response?.optJSONObject("memoryMutationStageTimings"))
        val selected = parseDiagnosticItems(
            trace.optJSONArray("selectedCandidates") ?: response?.optJSONArray("memorySelectedItems"),
            fallbackStage = "selected",
        )
        val requestId = response?.optString("memoryRequestId")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: payload.optString("requestId").trim().takeIf(String::isNotBlank)
            ?: "local-${System.currentTimeMillis()}"
        val error = failure?.message.orEmpty().trim().take(600)
            .ifBlank { response?.optString("memoryError").orEmpty().trim().take(600) }
        val mutationAction = mutation?.optString("action").orEmpty()
            .ifBlank { response?.optString("memoryMutationAction").orEmpty() }
            .trim()
            .take(80)
        val mutationStatus = mutation?.optString("status").orEmpty()
            .ifBlank { response?.optString("memoryMutationStatus").orEmpty() }
            .trim()
            .take(120)
        val mutationOperationId = mutation?.optString("operationId").orEmpty()
            .ifBlank { mutation?.optString("operation_id").orEmpty() }
            .ifBlank { response?.optString("memoryMutationOperationId").orEmpty() }
            .trim()
            .take(180)
        val mutationAffectedCount = if (mutation != null) {
            mutation.optInt("affectedCount", mutation.optInt("affected_count", 0))
        } else {
            response?.optInt("memoryMutationAffectedCount", 0) ?: 0
        }
        val mutationIdempotentReplay = if (mutation != null) {
            mutation.optBoolean(
                "idempotentReplay",
                mutation.optBoolean("idempotent_replay", false),
            )
        } else {
            response?.optBoolean("memoryMutationIdempotentReplay", false) == true
        }
        val mutationRequiresClarification = if (mutation != null) {
            mutation.optBoolean("requiresClarification", false)
        } else {
            response?.optBoolean("memoryMutationRequiresClarification", false) == true
        }
        val mutationError = mutation?.optString("error").orEmpty()
            .ifBlank { response?.optString("memoryMutationError").orEmpty() }
            .trim()
            .take(600)
        val hasMemoryMetadata = response?.let {
            it.has("memoryStatus") || it.has("memoryUsed") || it.has("memoryRequestId")
        } == true
        val record = AssistantMemoryDiagnosticRecord(
            requestId = requestId,
            createdAtMillis = System.currentTimeMillis(),
            prompt = tracePrompt.ifBlank {
                payload.optString("message")
                    .ifBlank { payload.optString("prompt") }
                    .trim()
                    .take(1_500)
            },
            replyPreview = response?.optString("reply").orEmpty()
                .ifBlank { response?.optString("response").orEmpty() }
                .trim()
                .take(1_000),
            model = response?.optString("model").orEmpty()
                .ifBlank { payload.optString("model") }
                .trim(),
            backendVersion = response?.optString("version").orEmpty().trim(),
            requestMode = payload.optString("memoryMode")
                .ifBlank { if (payload.optBoolean("memoryEnabled", false)) "legacy_auto" else "off" },
            requestSchema = payload.optString("memorySchema").trim(),
            requestEnabled = payload.optBoolean("memoryEnabled", false) ||
                payload.optString("memoryMode").equals("auto", ignoreCase = true),
            memoryUsed = response?.optBoolean("memoryUsed", false) == true,
            memoryStatus = response?.optString("memoryStatus")
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: if (failure != null) "request_failed" else if (hasMemoryMetadata) "unknown" else "response_missing_memory_metadata",
            memorySource = response?.optString("memorySource").orEmpty().trim(),
            degraded = response?.optBoolean("memoryDegraded", false) == true,
            itemCount = response?.optInt("memoryItemCount", selected.size) ?: selected.size,
            gateStatus = response?.optString("memoryGateStatus").orEmpty().trim(),
            budgetLevel = response?.optString("memoryBudgetLevel").orEmpty().trim(),
            retrievalStatus = response?.optString("memoryRetrievalStatus").orEmpty().trim(),
            embeddingStatus = response?.optString("memoryEmbeddingStatus").orEmpty().trim(),
            rerankStatus = response?.optString("memoryRerankStatus").orEmpty().trim(),
            candidateCount = response?.optInt("memoryCandidateCount", 0) ?: 0,
            anchorCandidateCount = response?.optInt("memoryAnchorCandidateCount", 0) ?: 0,
            dynamicCandidateCount = response?.optInt("memoryDynamicCandidateCount", 0) ?: 0,
            filteredHistoryCount = response?.optInt("memoryFilteredHistoryCount", 0) ?: 0,
            filteredSensitiveCount = response?.optInt("memoryFilteredSensitiveCount", 0) ?: 0,
            totalMs = response?.optLong("memoryTotalMs", 0L) ?: 0L,
            stageTimings = stageTimings,
            selectedItems = selected,
            anchorCandidates = parseDiagnosticItems(trace.optJSONArray("anchorCandidates"), "anchor"),
            dynamicCandidates = parseDiagnosticItems(trace.optJSONArray("dynamicCandidates"), "dynamic"),
            mergedCandidates = parseDiagnosticItems(trace.optJSONArray("mergedCandidates"), "merged"),
            error = error,
            traceAvailable = trace.length() > 0,
            mutationRequested = response?.optBoolean("memoryMutationRequested", mutation != null) == true,
            mutationHandled = response?.optBoolean("memoryMutationHandled", mutation != null) == true,
            mutationAction = mutationAction,
            mutationStatus = mutationStatus,
            mutationApplied = mutation?.optBoolean("applied", false)
                ?: (response?.optBoolean("memoryMutationApplied", false) == true),
            mutationOperationId = mutationOperationId,
            mutationAffectedCount = mutationAffectedCount.coerceAtLeast(0),
            mutationIdempotentReplay = mutationIdempotentReplay,
            mutationRequiresClarification = mutationRequiresClarification,
            mutationTrigger = response?.optString("memoryMutationTrigger").orEmpty().trim().take(120),
            mutationRouterStatus = response?.optString("memoryMutationRouterStatus").orEmpty().trim().take(120),
            mutationTotalMs = response?.optLong("memoryMutationTotalMs", 0L) ?: 0L,
            mutationStageTimings = mutationStageTimings,
            mutationError = mutationError,
        )
        synchronized(lock) {
            val next = listOf(record) + mutableState.value.records.filterNot { it.requestId == requestId }
            mutableState.value = AssistantMemoryDiagnosticsState(next.take(MEMORY_DIAGNOSTICS_MAX_RECORDS))
            persistLocked()
        }
    }

    fun clear() {
        ensureLoaded()
        synchronized(lock) {
            mutableState.value = AssistantMemoryDiagnosticsState()
            preferences()?.edit()?.remove(MEMORY_DIAGNOSTICS_KEY)?.apply()
        }
    }

    fun latestReport(): String {
        ensureLoaded()
        val latest = mutableState.value.latest ?: return "AI Ledger 逐轮记忆诊断\n暂无记录。"
        return buildReport(listOf(latest))
    }

    fun fullReport(): String {
        ensureLoaded()
        return buildReport(mutableState.value.records)
    }

    private fun buildReport(records: List<AssistantMemoryDiagnosticRecord>): String = buildString {
        appendLine("AI Ledger 逐轮记忆诊断报告 v2")
        appendLine("生成时间：${formatDiagnosticTime(System.currentTimeMillis())}")
        appendLine("记录数量：${records.size}")
        appendLine("说明：报告不包含登录令牌、请求头、图片或完整聊天历史。")
        records.forEachIndexed { index, record ->
            appendLine()
            appendLine("========== 第 ${index + 1} 轮 ==========")
            appendLine("时间：${formatDiagnosticTime(record.createdAtMillis)}")
            appendLine("请求 ID：${record.requestId}")
            appendLine("用户问题：${record.prompt.ifBlank { "<空>" }}")
            appendLine("模型：${record.model.ifBlank { "未知" }}")
            appendLine("后端版本：${record.backendVersion.ifBlank { "未返回" }}")
            appendLine("客户端请求：mode=${record.requestMode}, enabled=${record.requestEnabled}, schema=${record.requestSchema}")
            appendLine("最终状态：${record.memoryStatus} / used=${record.memoryUsed} / degraded=${record.degraded}")
            appendLine("来源：${record.memorySource.ifBlank { "none" }}")
            if (record.mutationRequested || record.mutationStatus.isNotBlank()) {
                appendLine(
                    "记忆变更：requested=${record.mutationRequested}, handled=${record.mutationHandled}, " +
                        "action=${record.mutationAction.ifBlank { "none" }}, " +
                        "status=${record.mutationStatus.ifBlank { "未返回" }}, applied=${record.mutationApplied}"
                )
                appendLine(
                    "事务：operationId=${record.mutationOperationId.ifBlank { "未返回" }}, " +
                        "affected=${record.mutationAffectedCount}, replay=${record.mutationIdempotentReplay}, " +
                        "clarification=${record.mutationRequiresClarification}"
                )
                appendLine(
                    "变更路由：trigger=${record.mutationTrigger.ifBlank { "未返回" }}, " +
                        "router=${record.mutationRouterStatus.ifBlank { "未返回" }}"
                )
                appendLine(
                    "变更耗时：total=${record.mutationTotalMs}ms" +
                        formatStageTimings(record.mutationStageTimings)
                )
                if (record.mutationError.isNotBlank()) appendLine("变更错误：${record.mutationError}")
            }
            appendLine("Gate：${record.gateStatus.ifBlank { "未返回" }} / budget=${record.budgetLevel.ifBlank { "未返回" }}")
            appendLine("Embedding：${record.embeddingStatus.ifBlank { "未返回" }}")
            appendLine("检索：${record.retrievalStatus.ifBlank { "未返回" }}")
            appendLine("重排：${record.rerankStatus.ifBlank { "未返回" }}")
            appendLine("候选：anchor=${record.anchorCandidateCount}, dynamic=${record.dynamicCandidateCount}, afterFilter=${record.candidateCount}")
            appendLine("过滤：history=${record.filteredHistoryCount}, sensitive=${record.filteredSensitiveCount}")
            appendLine("最终注入：${record.itemCount} 条")
            appendLine("耗时：total=${record.totalMs}ms${formatStageTimings(record.stageTimings)}")
            if (record.error.isNotBlank()) appendLine("错误：${record.error}")
            appendDiagnosticItems("Anchor 候选", record.anchorCandidates)
            appendDiagnosticItems("动态候选", record.dynamicCandidates)
            appendDiagnosticItems("合并候选", record.mergedCandidates)
            appendDiagnosticItems("最终选择", record.selectedItems)
            if (record.replyPreview.isNotBlank()) appendLine("回复预览：${record.replyPreview}")
            appendLine("原始结构化记录：")
            appendLine(record.toJson().toString(2))
        }
    }

    private fun StringBuilder.appendDiagnosticItems(
        title: String,
        items: List<AssistantMemoryDiagnosticItem>,
    ) {
        appendLine("$title（${items.size}）：")
        if (items.isEmpty()) {
            appendLine("  <无>")
            return
        }
        items.forEachIndexed { index, item ->
            appendLine(
                "  ${index + 1}. id=${item.id} stage=${item.stage} disposition=${item.disposition.ifBlank { "unknown" }} " +
                    "retrievalScore=${item.retrievalScore} selectionConfidence=${item.selectionConfidence}"
            )
            appendLine(
                "     layer=${item.layer} authority=${item.authority} priority=${item.priority} pinned=${item.pinned} " +
                    "namespace=${item.namespaceType}:${item.namespaceId}"
            )
            if (item.retrievalSource.isNotBlank() || item.retrievalReason.isNotBlank()) {
                appendLine("     retrieval=${item.retrievalSource} ${item.retrievalReason}".trimEnd())
            }
            if (item.dispositionReason.isNotBlank() || item.selectionReason.isNotBlank()) {
                appendLine("     reason=${item.dispositionReason.ifBlank { item.selectionReason }}")
            }
            appendLine("     content=${item.content}")
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val raw = preferences()?.getString(MEMORY_DIAGNOSTICS_KEY, null)
            val records = runCatching { parseRecords(raw) }.getOrDefault(emptyList())
            mutableState.value = AssistantMemoryDiagnosticsState(records.take(MEMORY_DIAGNOSTICS_MAX_RECORDS))
            loaded = true
        }
    }

    private fun persistLocked() {
        val array = JSONArray().apply {
            mutableState.value.records.forEach { put(it.toJson()) }
        }
        preferences()?.edit()?.putString(MEMORY_DIAGNOSTICS_KEY, array.toString())?.apply()
    }

    private fun preferences() = AiLedgerApplication.contextOrNull()
        ?.getSharedPreferences(MEMORY_DIAGNOSTICS_PREFERENCES, Context.MODE_PRIVATE)
}

private fun parseRecords(raw: String?): List<AssistantMemoryDiagnosticRecord> {
    if (raw.isNullOrBlank()) return emptyList()
    val array = JSONArray(raw)
    return buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.toDiagnosticRecordOrNull()?.let(::add)
        }
    }
}

private fun JSONObject.toDiagnosticRecordOrNull(): AssistantMemoryDiagnosticRecord? {
    val requestId = optString("requestId").trim()
    if (requestId.isBlank()) return null
    return AssistantMemoryDiagnosticRecord(
        requestId = requestId,
        createdAtMillis = optLong("createdAtMillis", 0L),
        prompt = optString("prompt"),
        replyPreview = optString("replyPreview"),
        model = optString("model"),
        backendVersion = optString("backendVersion"),
        requestMode = optString("requestMode"),
        requestSchema = optString("requestSchema"),
        requestEnabled = optBoolean("requestEnabled", false),
        memoryUsed = optBoolean("memoryUsed", false),
        memoryStatus = optString("memoryStatus"),
        memorySource = optString("memorySource"),
        degraded = optBoolean("degraded", false),
        itemCount = optInt("itemCount", 0),
        gateStatus = optString("gateStatus"),
        budgetLevel = optString("budgetLevel"),
        retrievalStatus = optString("retrievalStatus"),
        embeddingStatus = optString("embeddingStatus"),
        rerankStatus = optString("rerankStatus"),
        candidateCount = optInt("candidateCount", 0),
        anchorCandidateCount = optInt("anchorCandidateCount", 0),
        dynamicCandidateCount = optInt("dynamicCandidateCount", 0),
        filteredHistoryCount = optInt("filteredHistoryCount", 0),
        filteredSensitiveCount = optInt("filteredSensitiveCount", 0),
        totalMs = optLong("totalMs", 0L),
        stageTimings = parseLongMap(optJSONObject("stageTimings")),
        selectedItems = parseDiagnosticItems(optJSONArray("selectedItems"), "selected"),
        anchorCandidates = parseDiagnosticItems(optJSONArray("anchorCandidates"), "anchor"),
        dynamicCandidates = parseDiagnosticItems(optJSONArray("dynamicCandidates"), "dynamic"),
        mergedCandidates = parseDiagnosticItems(optJSONArray("mergedCandidates"), "merged"),
        error = optString("error"),
        traceAvailable = optBoolean("traceAvailable", false),
        mutationRequested = optBoolean("mutationRequested", false),
        mutationHandled = optBoolean("mutationHandled", false),
        mutationAction = optString("mutationAction"),
        mutationStatus = optString("mutationStatus"),
        mutationApplied = optBoolean("mutationApplied", false),
        mutationOperationId = optString("mutationOperationId"),
        mutationAffectedCount = optInt("mutationAffectedCount", 0),
        mutationIdempotentReplay = optBoolean("mutationIdempotentReplay", false),
        mutationRequiresClarification = optBoolean("mutationRequiresClarification", false),
        mutationTrigger = optString("mutationTrigger"),
        mutationRouterStatus = optString("mutationRouterStatus"),
        mutationTotalMs = optLong("mutationTotalMs", 0L),
        mutationStageTimings = parseLongMap(optJSONObject("mutationStageTimings")),
        mutationError = optString("mutationError"),
    )
}

private fun parseDiagnosticItems(
    array: JSONArray?,
    fallbackStage: String,
): List<AssistantMemoryDiagnosticItem> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until minOf(array.length(), MEMORY_DIAGNOSTICS_MAX_CANDIDATES_PER_STAGE)) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            if (id.isBlank()) continue
            add(
                AssistantMemoryDiagnosticItem(
                    id = id,
                    stage = item.optString("stage").ifBlank { fallbackStage },
                    content = item.optString("content").trim().take(500),
                    layer = item.optString("layer"),
                    authority = item.optString("authority"),
                    namespaceType = item.optString("namespaceType"),
                    namespaceId = item.optString("namespaceId"),
                    subjectKey = item.optString("subjectKey"),
                    conflictKey = item.optString("conflictKey"),
                    priority = item.optInt("priority", 0),
                    pinned = item.optBoolean("pinned", false),
                    confidence = item.optDouble("confidence", 0.0),
                    retrievalScore = item.optDouble("retrievalScore", 0.0),
                    retrievalSource = item.optString("retrievalSource"),
                    retrievalReason = item.optString("retrievalReason"),
                    selectionConfidence = item.optDouble("selectionConfidence", item.optDouble("confidence", 0.0)),
                    selectionReason = item.optString("selectionReason").ifBlank { item.optString("reason") },
                    disposition = item.optString("disposition").ifBlank {
                        if (fallbackStage == "selected") "selected" else ""
                    },
                    dispositionReason = item.optString("dispositionReason"),
                )
            )
        }
    }
}

private fun List<AssistantMemoryDiagnosticItem>.toJsonArray(): JSONArray = JSONArray().apply {
    forEach { item ->
        put(JSONObject().apply {
            put("id", item.id)
            put("stage", item.stage)
            put("content", item.content)
            put("layer", item.layer)
            put("authority", item.authority)
            put("namespaceType", item.namespaceType)
            put("namespaceId", item.namespaceId)
            put("subjectKey", item.subjectKey)
            put("conflictKey", item.conflictKey)
            put("priority", item.priority)
            put("pinned", item.pinned)
            put("confidence", item.confidence)
            put("retrievalScore", item.retrievalScore)
            put("retrievalSource", item.retrievalSource)
            put("retrievalReason", item.retrievalReason)
            put("selectionConfidence", item.selectionConfidence)
            put("selectionReason", item.selectionReason)
            put("disposition", item.disposition)
            put("dispositionReason", item.dispositionReason)
        })
    }
}

private fun parseLongMap(value: JSONObject?): Map<String, Long> {
    if (value == null) return emptyMap()
    return buildMap {
        value.keys().forEach { key -> put(key, value.optLong(key, 0L)) }
    }
}

private fun formatStageTimings(values: Map<String, Long>): String {
    if (values.isEmpty()) return ""
    return values.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}ms" }
}

private fun formatDiagnosticTime(value: Long): String {
    if (value <= 0L) return "未知"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(value))
}
