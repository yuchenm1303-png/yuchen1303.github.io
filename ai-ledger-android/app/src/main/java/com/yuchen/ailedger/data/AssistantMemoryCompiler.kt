package com.yuchen.ailedger.data

import com.yuchen.ailedger.service.CLOUD_MEMORY_CUSTOM_ORIGIN_ID
import com.yuchen.ailedger.service.CloudMemorySelectionClient
import com.yuchen.ailedger.service.CloudMemorySelectionResult
import com.yuchen.ailedger.service.CloudSelectedMemory
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val CLOUD_PERSONA_BUDGET = 780
private const val CLOUD_PROFILE_BUDGET = 580
private const val CLOUD_PREFERENCE_LIMIT = 10
private const val CLOUD_RELEVANT_LIMIT = 12

@Suppress("unused")
enum class AssistantMemoryIntent(val id: String, val scope: String) {
    CLOUD_ORCHESTRATED("cloud_orchestrated", "cloud"),
    ENGLISH_VOCABULARY("legacy_english_vocabulary", "legacy"),
    ENGLISH_LEARNING("legacy_english_learning", "legacy"),
    ANDROID_DEVELOPMENT("legacy_android_development", "legacy"),
    PROGRAMMING("legacy_programming", "legacy"),
    MATHEMATICS("legacy_mathematics", "legacy"),
    ACADEMIC_WRITING("legacy_academic_writing", "legacy"),
    FINANCE("legacy_finance", "legacy"),
    TRAVEL("legacy_travel", "legacy"),
    GENERAL("legacy_general", "legacy"),
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
    val schema: String = "ai_ledger_cloud_memory_context_v1",
    val intent: AssistantMemoryIntent = AssistantMemoryIntent.CLOUD_ORCHESTRATED,
    val activeScopes: Set<String> = setOf("cloud_model"),
    val personaInstructions: String? = null,
    val memorySnapshot: JSONObject? = null,
    val selectedMemoryIds: List<String> = emptyList(),
    val sources: List<AssistantMemorySource> = emptyList(),
    val suppressedConflictCount: Int = 0,
    val selectionStatus: String = "empty",
    val selectionOwner: String = "cloud_model",
    val errorCode: String = "",
) {
    val hasAnyContext: Boolean
        get() = !personaInstructions.isNullOrBlank() || memorySnapshot != null

    fun personaConfigJson(): JSONObject? {
        val instructions = personaInstructions?.trim().orEmpty()
        if (instructions.isBlank()) return null
        return JSONObject()
            .put("customInstructions", instructions)
            .put("memoryInstructionSchema", schema)
            .put("memoryIntent", intent.id)
            .put("memorySelectionOwner", selectionOwner)
    }

    fun diagnosticsJson(): JSONObject = JSONObject()
        .put("schema", schema)
        .put("intent", intent.id)
        .put("selectionOwner", selectionOwner)
        .put("selectionStatus", selectionStatus)
        .put("errorCode", errorCode)
        .put("activeScopes", JSONArray(activeScopes.toList()))
        .put("selectedMemoryIds", JSONArray(selectedMemoryIds))
        .put("suppressedConflictCount", suppressedConflictCount)
        .put("sources", JSONArray().apply {
            sources.forEach { source ->
                put(JSONObject().apply {
                    put("id", source.id)
                    put("category", source.category)
                    put("scope", source.scope)
                    put("role", source.role)
                    put("score", source.score)
                    put("reason", source.reason)
                })
            }
        })
}

data class AssistantMemoryRuntimeState(
    val compilation: AssistantMemoryCompilation? = null,
    val updatedAtMillis: Long = 0L,
)

object AssistantMemoryRuntime {
    private val mutableState = MutableStateFlow(AssistantMemoryRuntimeState())
    val state: StateFlow<AssistantMemoryRuntimeState> = mutableState.asStateFlow()

    fun record(compilation: AssistantMemoryCompilation) {
        mutableState.value = AssistantMemoryRuntimeState(
            compilation = compilation,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }
}

object AssistantMemoryCompiler {
    fun compile(
        userText: String,
        customInstructions: String?,
        memoryState: AssistantMemoryState,
        nowMillis: Long = System.currentTimeMillis(),
    ): AssistantMemoryCompilation {
        val result = CloudMemorySelectionClient.select(
            userText = userText,
            customInstructions = customInstructions,
            memoryState = memoryState,
            nowMillis = nowMillis,
        )
        return composeCloudCompilation(result)
    }

    internal fun composeCloudCompilation(
        result: CloudMemorySelectionResult,
    ): AssistantMemoryCompilation {
        if (!result.successful || result.selections.isEmpty()) {
            return AssistantMemoryCompilation(
                selectionStatus = result.status,
                errorCode = result.errorCode,
                suppressedConflictCount = result.suppressedCount,
            )
        }

        val instructions = result.selections.filter { it.role == "instruction" }
        val profiles = result.selections.filter { it.role == "profile" }
        val preferences = result.selections.filter { it.role == "preference" }
        val memories = result.selections.filter { it.role == "memory" }

        val personaInstructions = buildCloudPersonaInstructions(instructions)
        val memorySnapshot = buildCloudMemorySnapshot(profiles, preferences, memories)
        val sources = result.selections
            .map { it.toMemorySource() }
            .distinctBy { "${it.id}:${it.role}" }
        val selectedIds = result.selections
            .asSequence()
            .map { it.candidate.originId }
            .filter { it.isNotBlank() && it != CLOUD_MEMORY_CUSTOM_ORIGIN_ID }
            .distinct()
            .toList()

        return AssistantMemoryCompilation(
            personaInstructions = personaInstructions,
            memorySnapshot = memorySnapshot,
            selectedMemoryIds = selectedIds,
            sources = sources,
            suppressedConflictCount = result.suppressedCount,
            selectionStatus = "selected",
        )
    }

    private fun buildCloudPersonaInstructions(
        selections: List<CloudSelectedMemory>,
    ): String? {
        if (selections.isEmpty()) return null
        val builder = StringBuilder(
            "以下是云端模型根据当前请求选出的用户明确长期指令。只要不与系统安全、事实准确性或工具协议冲突，就必须按其适用条件执行："
        )
        selections
            .map { it.candidate.content.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .forEach { instruction ->
                val line = "\n- $instruction"
                if (builder.length + line.length <= CLOUD_PERSONA_BUDGET) builder.append(line)
            }
        return builder.toString().take(CLOUD_PERSONA_BUDGET)
    }

    private fun buildCloudMemorySnapshot(
        profiles: List<CloudSelectedMemory>,
        preferences: List<CloudSelectedMemory>,
        memories: List<CloudSelectedMemory>,
    ): JSONObject? {
        if (profiles.isEmpty() && preferences.isEmpty() && memories.isEmpty()) return null
        val profileSummary = buildString {
            profiles.map { it.candidate.content.trim() }
                .filter(String::isNotBlank)
                .distinct()
                .forEach { text ->
                    val separator = if (isEmpty()) "" else "；"
                    if (length + separator.length + text.length <= CLOUD_PROFILE_BUDGET) {
                        append(separator).append(text)
                    }
                }
        }
        return JSONObject()
            .put("schema", "ai_ledger_cloud_memory_snapshot_v1")
            .put("intent", AssistantMemoryIntent.CLOUD_ORCHESTRATED.id)
            .put("activeScopes", JSONArray(listOf("cloud_model")))
            .put("profileSummary", profileSummary)
            .put(
                "preferences",
                JSONArray(
                    preferences.map { it.candidate.content.trim().take(180) }
                        .filter(String::isNotBlank)
                        .distinct()
                        .take(CLOUD_PREFERENCE_LIMIT)
                )
            )
            .put(
                "relevantMemories",
                JSONArray(
                    memories.map { it.candidate.content.trim().take(220) }
                        .filter(String::isNotBlank)
                        .distinct()
                        .take(CLOUD_RELEVANT_LIMIT)
                )
            )
            .put("sessionSummary", "")
            .put("selectionOwner", "cloud_model")
    }

    private fun CloudSelectedMemory.toMemorySource(): AssistantMemorySource = AssistantMemorySource(
        id = candidate.originId,
        category = candidate.category,
        scope = "cloud",
        role = role,
        score = 100,
        reason = reason.ifBlank { "cloud_semantic_selection" },
    )
}

internal fun parseIsoInstantMillis(value: String): Long? {
    val clean = value.trim()
    if (clean.isBlank()) return null
    return runCatching { Instant.parse(clean).toEpochMilli() }
        .recoverCatching {
            val normalized = clean.replace(" ", "T").let { if (it.endsWith("Z")) it else "${it}Z" }
            Instant.parse(normalized).toEpochMilli()
        }
        .getOrNull()
}

internal fun AssistantMemoryItem.isActiveAt(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (!enabled || status != "active" || content.isBlank()) return false
    val startsAt = parseIsoInstantMillis(validFrom)
    if (startsAt != null && nowMillis < startsAt) return false
    val expiresAt = parseIsoInstantMillis(validUntil)
    if (expiresAt != null && nowMillis >= expiresAt) return false
    return true
}
