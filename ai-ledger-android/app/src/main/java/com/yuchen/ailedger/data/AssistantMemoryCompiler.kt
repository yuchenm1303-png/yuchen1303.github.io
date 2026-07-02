package com.yuchen.ailedger.data

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class AssistantMemoryIntent(val id: String, val scope: String) {
    CLOUD_ORCHESTRATED("cloud_orchestrated", "cloud"),
}

data class AssistantMemorySource(
    val id: String,
    val category: String,
    val scope: String,
    val role: String,
    val score: Int,
    val reason: String,
)

data class AssistantMemoryCompilation(
    val schema: String = "ai_ledger_cloud_memory_request_v3",
    val intent: AssistantMemoryIntent = AssistantMemoryIntent.CLOUD_ORCHESTRATED,
    val activeScopes: Set<String> = emptySet(),
    val personaInstructions: String? = null,
    val memorySnapshot: JSONObject? = null,
    val selectedMemoryIds: List<String> = emptyList(),
    val sources: List<AssistantMemorySource> = emptyList(),
    val suppressedConflictCount: Int = 0,
    val selectionStatus: String = "empty",
    val selectionOwner: String = "backend_cloud_v4",
    val errorCode: String = "",
    val memoryRequested: Boolean = false,
) {
    val hasAnyContext: Boolean
        get() = memoryRequested || memorySnapshot != null

    val requestMode: String
        get() = if (memoryRequested) "auto" else "off"

    fun personaConfigJson(): JSONObject? = null

    fun diagnosticsJson(): JSONObject = JSONObject()
        .put("schema", schema)
        .put("intent", intent.id)
        .put("selectionOwner", selectionOwner)
        .put("selectionStatus", selectionStatus)
        .put("requestMode", requestMode)
        .put("memoryRequested", memoryRequested)
        .put("activeScopes", JSONArray(activeScopes.toList()))
}

data class AssistantMemoryRuntimeState(
    val compilation: AssistantMemoryCompilation? = null,
    val updatedAtMillis: Long = 0L,
)

object AssistantMemoryRuntime {
    private val mutableState = MutableStateFlow(AssistantMemoryRuntimeState())
    val state: StateFlow<AssistantMemoryRuntimeState> = mutableState.asStateFlow()

    fun record(compilation: AssistantMemoryCompilation) {
        val hasLocalCompatibilityContext =
            compilation.memorySnapshot != null ||
                compilation.selectedMemoryIds.isNotEmpty() ||
                compilation.sources.isNotEmpty()
        if (!hasLocalCompatibilityContext) return
        mutableState.value = AssistantMemoryRuntimeState(
            compilation = compilation,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }
}

object AssistantMemoryCompiler {
    fun compileBackendOwned(userText: String): AssistantMemoryCompilation {
        val requestHasText = userText.trim().isNotBlank()
        if (requestHasText) {
            AssistantMemoryRequestContextRuntime.stageCurrentThread()
        } else {
            AssistantMemoryRequestContextRuntime.clearCurrentThread()
        }
        return AssistantMemoryCompilation(
            activeScopes = if (requestHasText) setOf("backend_cloud_v4") else emptySet(),
            selectionStatus = if (requestHasText) "backend_cloud_requested" else "empty",
            memoryRequested = requestHasText,
        )
    }

    fun compile(
        userText: String,
        memoryState: AssistantMemoryState,
    ): AssistantMemoryCompilation {
        val requestHasText = userText.trim().isNotBlank()
        val accountKnown = memoryState.accountUserId != null
        val locallyConfirmedDisabled =
            accountKnown && memoryState.cloudReady && !memoryState.memoryEnabled
        val memoryRequested = requestHasText && !locallyConfirmedDisabled

        if (requestHasText) {
            AssistantMemoryRequestContextRuntime.stageCurrentThread()
        } else {
            AssistantMemoryRequestContextRuntime.clearCurrentThread()
        }

        return AssistantMemoryCompilation(
            activeScopes = if (memoryRequested) setOf("backend_cloud_v4") else emptySet(),
            selectionStatus = when {
                !requestHasText -> "empty"
                locallyConfirmedDisabled -> "disabled_by_user"
                !accountKnown -> "backend_identity_pending"
                else -> "backend_cloud_requested"
            },
            memoryRequested = memoryRequested,
        )
    }
}

private const val INVALID_PARSED_INSTANT_MILLIS = Long.MIN_VALUE
private const val PARSED_INSTANT_CACHE_MAX_ENTRIES = 1_024
private val parsedInstantMillisCache = ConcurrentHashMap<String, Long>()

internal fun parseIsoInstantMillis(value: String): Long? {
    val clean = value.trim()
    if (clean.isBlank()) return null
    parsedInstantMillisCache[clean]?.let { cached ->
        return cached.takeUnless { it == INVALID_PARSED_INSTANT_MILLIS }
    }

    val parsed = runCatching { Instant.parse(clean).toEpochMilli() }
        .recoverCatching {
            val normalized = clean.replace(" ", "T").let { if (it.endsWith("Z")) it else "${it}Z" }
            Instant.parse(normalized).toEpochMilli()
        }
        .getOrNull()

    if (parsedInstantMillisCache.size >= PARSED_INSTANT_CACHE_MAX_ENTRIES) {
        parsedInstantMillisCache.clear()
    }
    parsedInstantMillisCache.putIfAbsent(clean, parsed ?: INVALID_PARSED_INSTANT_MILLIS)
    return parsed
}

internal fun AssistantMemoryItem.isActiveAt(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (!enabled || status != "active" || content.isBlank()) return false
    val startsAt = parseIsoInstantMillis(validFrom)
    if (startsAt != null && nowMillis < startsAt) return false
    val expiresAt = parseIsoInstantMillis(validUntil)
    if (expiresAt != null && nowMillis >= expiresAt) return false
    return true
}
