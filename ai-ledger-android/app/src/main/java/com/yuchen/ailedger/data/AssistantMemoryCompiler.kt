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
 * Android 不创建该列表；真实命中项必须由云端响应回传。
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
 * Android 端只声明本轮是否允许云端长期记忆，并独立传递自定义指令。
 *
 * 关键边界：
 * 1. 不读取本地记忆正文或库存；
 * 2. 不在本地做关键词、类别、作用域、冲突或相关性判断；
 * 3. cloudReady 只描述管理页库存是否同步完成，不能替云端账号设置做决定；
 * 4. 只有已从云端完整加载且明确关闭记忆时，本地才发送关闭状态；
 * 5. 账号/JWT 状态暂时未加载时仍发送 auto，由后端认证结果决定是否可用；
 * 6. 这样匿名、令牌缺失、设置关闭和检索失败都能返回可诊断的真实状态。
 */
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
    /** memorySnapshot 保留空值仅用于旧协议兼容，Android 不再生成该字段。 */
    val hasAnyContext: Boolean
        get() = memoryRequested || memorySnapshot != null

    /** 供请求层使用的唯一模式；auto 表示最终由后端账号设置决定。 */
    val requestMode: String
        get() = if (memoryRequested) "auto" else "off"

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
     * 这里只描述请求传输状态，不含任何本地语义结论。
     * 候选数、命中项、Gate、重排与 usage 状态必须来自云端响应。
     */
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

    /**
     * 本地请求契约不能冒充云端命中结果。
     * 设置页逐轮诊断只展示后端真实响应，因此这里不写入伪命中记录。
     */
    fun record(@Suppress("UNUSED_PARAMETER") compilation: AssistantMemoryCompilation) {
        mutableState.value = AssistantMemoryRuntimeState(
            compilation = null,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }
}

object AssistantMemoryCompiler {
    /**
     * 纯协议编译：
     * 1. 不读取 memoryState.memories；
     * 2. 不构建候选、快照或 selectedMemoryIds；
     * 3. 不按关键词、类别、作用域或正文判断；
     * 4. 不调用第二个模型；
     * 5. 不把本地库存同步状态或临时未知身份当作云端记忆开关。
     *
     * 账号权限、真实开关、召回、冲突处理和重排全部由云端 Memory Service 负责。
     */
    fun compile(
        userText: String,
        customInstructions: String?,
        memoryState: AssistantMemoryState,
    ): AssistantMemoryCompilation {
        val requestHasText = userText.trim().isNotBlank()
        val accountKnown = memoryState.accountUserId != null

        // cloudReady=false 只说明 Android 管理页尚未拿到完整库存，不能据此关闭云端记忆。
        // 只有账号已知、完整云端状态已加载且 memoryEnabled=false，才视为用户明确关闭。
        val locallyConfirmedDisabled =
            accountKnown && memoryState.cloudReady && !memoryState.memoryEnabled

        // 身份未知不等于匿名：请求头中的 JWT 与管理页状态可能尚未同步。
        // 普通文本请求统一发送 auto，由后端认证与账号设置返回最终状态及诊断。
        val memoryRequested = requestHasText && !locallyConfirmedDisabled
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
                !requestHasText -> "empty"
                locallyConfirmedDisabled -> "disabled_by_user"
                !accountKnown -> "backend_identity_pending"
                else -> "backend_cloud_requested"
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
