package com.yuchen.ailedger.data

import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class AssistantMemoryIntent(val id: String, val scope: String) {
    CLOUD_ORCHESTRATED("cloud_orchestrated", "cloud"),
}

/**
 * 仅用于兼容当前设置页的数据结构。
 * Android 不创建该列表；真实命中项必须在第二阶段由云端响应回传。
 */
data class AssistantMemorySource(
    val id: String,
    val category: String,
    val scope: String,
    val role: String,
    val score: Int,
    val reason: String,
)

/**
 * Android 端只负责声明用户是否请求云端记忆，以及传递独立的自定义指令。
 * 这里绝不携带本地候选、记忆正文、选中 ID、语义分类或关键词判断结果。
 */
data class AssistantMemoryCompilation(
    val schema: String = "ai_ledger_cloud_memory_request_v2",
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
    /** 只代表长期记忆请求，不受自定义指令是否存在影响。 */
    val hasAnyContext: Boolean
        get() = memoryRequested

    fun personaConfigJson(): JSONObject? {
        val instructions = personaInstructions?.trim().orEmpty()
        if (instructions.isBlank()) return null
        return JSONObject()
            .put("customInstructions", instructions)
            .put("memoryInstructionSchema", schema)
            .put("memoryIntent", intent.id)
            .put("memorySelectionOwner", selectionOwner)
    }

    /**
     * 这是纯传输诊断，不含任何本地语义结论。
     * 真正的候选数、命中项、Gate、重排与 usage 状态必须由云端返回。
     */
    fun diagnosticsJson(): JSONObject = JSONObject()
        .put("schema", schema)
        .put("intent", intent.id)
        .put("selectionOwner", selectionOwner)
        .put("selectionStatus", selectionStatus)
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
        mutableState.value = AssistantMemoryRuntimeState(
            compilation = compilation,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }
}

object AssistantMemoryCompiler {
    /**
     * 纯协议编译：
     * 1. 不读取 memoryState.memories；
     * 2. 不构建候选或快照；
     * 3. 不按关键词、类别、作用域或正文判断；
     * 4. 不调用第二个模型；
     * 5. 不产生本地 selectedMemoryIds。
     *
     * 账号权限、记忆召回、冲突处理和重排全部由云端 Memory Service 负责。
     */
    fun compile(
        userText: String,
        customInstructions: String?,
        memoryState: AssistantMemoryState,
    ): AssistantMemoryCompilation {
        val requestHasText = userText.trim().isNotBlank()
        val accountReady = memoryState.accountUserId != null && memoryState.cloudReady
        val memoryRequested = requestHasText && accountReady && memoryState.memoryEnabled
        val instructions = customInstructions
            ?.trim()
            ?.takeIf { requestHasText && it.isNotBlank() }

        return AssistantMemoryCompilation(
            activeScopes = if (memoryRequested) setOf("backend_cloud_v4") else emptySet(),
            personaInstructions = instructions,
            memorySnapshot = null,
            selectedMemoryIds = emptyList(),
            sources = emptyList(),
            suppressedConflictCount = 0,
            selectionStatus = when {
                memoryRequested -> "backend_cloud_requested"
                memoryState.accountUserId == null -> "disabled_anonymous"
                !memoryState.memoryEnabled -> "disabled_by_user"
                !memoryState.cloudReady -> "disabled_account_unavailable"
                else -> "empty"
            },
            selectionOwner = "backend_cloud_v4",
            memoryRequested = memoryRequested,
        )
    }
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
