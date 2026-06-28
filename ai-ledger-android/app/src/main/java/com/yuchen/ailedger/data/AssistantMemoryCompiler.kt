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

        val instructionBlock = composeInstructionBlock(
            result.selections.filter { it.role == "instruction" }
        )
        val profileBlock = composeProfileBlock(
            result.selections.filter { it.role == "profile" }
        )
        val preferenceBlock = composeListBlock(
            selections = result.selections.filter { it.role == "preference" },
            itemLimit = CLOUD_PREFERENCE_LIMIT,
            itemChars = 180,
        )
        val memoryBlock = composeListBlock(
            selections = result.selections.filter { it.role == "memory" },
            itemLimit = CLOUD_RELEVANT_LIMIT,
            itemChars = 220,
        )
        val usedSelections = (
            instructionBlock.used +
                profileBlock.used +
                preferenceBlock.used +
                memoryBlock.used
            ).distinctBy { it.candidate.transportId }

        val memorySnapshot = buildCloudMemorySnapshot(
            profileSummary = profileBlock.text.orEmpty(),
            preferences = preferenceBlock.items,
            memories = memoryBlock.items,
        )
        val sources = usedSelections
            .map { it.toMemorySource() }
            .distinctBy { "${it.id}:${it.role}" }
        val selectedIds = usedSelections
            .asSequence()
            .map { it.candidate.originId }
            .filter { it.isNotBlank() && it != CLOUD_MEMORY_CUSTOM_ORIGIN_ID }
            .distinct()
            .toList()

        return AssistantMemoryCompilation(
            personaInstructions = instructionBlock.text,
            memorySnapshot = memorySnapshot,
            selectedMemoryIds = selectedIds,
            sources = sources,
            suppressedConflictCount = result.suppressedCount,
            selectionStatus = if (usedSelections.isEmpty()) "empty" else "selected",
        )
    }

    private fun composeInstructionBlock(
        selections: List<CloudSelectedMemory>,
    ): TextComposition {
        if (selections.isEmpty()) return TextComposition()
        val builder = StringBuilder(
            "以下是云端模型根据当前请求选出的用户明确长期指令。只要不与系统安全、事实准确性或工具协议冲突，就必须按其适用条件执行："
        )
        val used = mutableListOf<CloudSelectedMemory>()
        selections.distinctBy { it.candidate.content.trim() }.forEach { selection ->
            val content = selection.candidate.content.trim()
            if (content.isBlank()) return@forEach
            val prefix = "\n- "
            val remaining = CLOUD_PERSONA_BUDGET - builder.length - prefix.length
            if (remaining <= 0) return@forEach
            builder.append(prefix).append(content.take(remaining))
            used += selection
        }
        return TextComposition(
            text = builder.toString().takeIf { used.isNotEmpty() },
            used = used,
        )
    }

    private fun composeProfileBlock(
        selections: List<CloudSelectedMemory>,
    ): TextComposition {
        val builder = StringBuilder()
        val used = mutableListOf<CloudSelectedMemory>()
        selections.distinctBy { it.candidate.content.trim() }.forEach { selection ->
            val content = selection.candidate.content.trim()
            if (content.isBlank()) return@forEach
            val separator = if (builder.isEmpty()) "" else "；"
            val remaining = CLOUD_PROFILE_BUDGET - builder.length - separator.length
            if (remaining <= 0) return@forEach
            builder.append(separator).append(content.take(remaining))
            used += selection
        }
        return TextComposition(builder.toString().takeIf(String::isNotBlank), used)
    }

    private fun composeListBlock(
        selections: List<CloudSelectedMemory>,
        itemLimit: Int,
        itemChars: Int,
    ): ListComposition {
        val used = mutableListOf<CloudSelectedMemory>()
        val items = mutableListOf<String>()
        selections.forEach { selection ->
            val text = selection.candidate.content.trim().take(itemChars)
            if (text.isBlank() || text in items || items.size >= itemLimit) return@forEach
            items += text
            used += selection
        }
        return ListComposition(items, used)
    }

    private fun buildCloudMemorySnapshot(
        profileSummary: String,
        preferences: List<String>,
        memories: List<String>,
    ): JSONObject? {
        if (profileSummary.isBlank() && preferences.isEmpty() && memories.isEmpty()) return null
        return JSONObject()
            .put("schema", "ai_ledger_cloud_memory_snapshot_v1")
            .put("intent", AssistantMemoryIntent.CLOUD_ORCHESTRATED.id)
            .put("activeScopes", JSONArray(listOf("cloud_model")))
            .put("profileSummary", profileSummary)
            .put("preferences", JSONArray(preferences))
            .put("relevantMemories", JSONArray(memories))
            .put("sessionSummary", "")
            .put("selectionOwner", "cloud_model")
    }

    private fun CloudSelectedMemory.toMemorySource(): AssistantMemorySource = AssistantMemorySource(
        id = candidate.originId,
        category = candidate.category,
        scope = candidate.scope,
        role = role,
        score = 100,
        reason = reason.ifBlank { "cloud_semantic_selection" },
    )

    private data class TextComposition(
        val text: String? = null,
        val used: List<CloudSelectedMemory> = emptyList(),
    )

    private data class ListComposition(
        val items: List<String>,
        val used: List<CloudSelectedMemory>,
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
