package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.service.SupabaseAuthClient
import com.yuchen.ailedger.service.SupabaseUserSession
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.URL
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

const val ASSISTANT_MEMORY_MAX_CONTENT_LENGTH = 2_000
const val ASSISTANT_MEMORY_MAX_STORED_ITEMS = 300
const val ASSISTANT_MEMORY_SNAPSHOT_MAX_ITEMS = 48
const val ASSISTANT_MEMORY_SNAPSHOT_MAX_LENGTH = 10_000

private const val MEMORY_TABLE = "assistant_memory_items_v4"
private const val MEMORY_SETTINGS_TABLE = "assistant_memory_settings"
private const val MEMORY_MUTATION_RPC = "apply_assistant_memory_mutation_v5"
private const val MEMORY_MUTATION_SCHEMA = "ai_ledger_memory_mutation_receipt_v1"
private const val MEMORY_CONNECT_TIMEOUT_MS = 12_000
private const val MEMORY_READ_TIMEOUT_MS = 18_000
private const val MEMORY_MUTATION_RETRY_DELAY_MS = 140L

private val ALLOWED_MEMORY_CATEGORIES = setOf(
    "profile",
    "preference",
    "project",
    "rule",
    "skill",
    "episode",
    "reflection",
    "other",
    "manual",
)

private val ALLOWED_MEMORY_SCOPES = setOf(
    "auto",
    "global",
    "general",
    "english",
    "android",
    "coding",
    "math",
    "writing",
    "finance",
    "travel",
)

private val ALLOWED_MEMORY_SOURCE_TYPES = setOf(
    "manual",
    "conversation",
    "user_feedback",
    "system_inferred",
    "reflection",
    "migration",
)

private val ALLOWED_MEMORY_STATUS = setOf(
    "active",
    "archived",
    "superseded",
    "deleted",
)

private val ALLOWED_MEMORY_LAYERS = setOf(
    "explicit_core",
    "profile",
    "preference",
    "project",
    "episodic",
    "session",
)

private val ALLOWED_MEMORY_NAMESPACE_TYPES = setOf("account", "project", "session")
private val ALLOWED_SENSITIVE_POLICIES = setOf("confirm", "block", "allow")
private val MEMORY_CONTROL_CHAR_REGEX = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")
private val MEMORY_INLINE_SPACE_REGEX = Regex("[\\t ]+")
private val MEMORY_EXCESS_BLANK_LINES_REGEX = Regex("\n{4,}")

data class AssistantMemoryItem(
    val id: String,
    val content: String,
    val category: String = "other",
    val scope: String = "auto",
    val priority: Int = 1,
    val pinned: Boolean = false,
    val enabled: Boolean = true,
    val confidence: Double = 1.0,
    val sourceType: String = "manual",
    val validFrom: String = "",
    val validUntil: String = "",
    val supersedesId: String = "",
    val status: String = "active",
    val lastUsedAt: String = "",
    val useCount: Long = 0L,
    val createdAt: String = "",
    val updatedAt: String = "",
    val layer: String = "",
    val authority: String = "",
    val namespaceType: String = "account",
    val namespaceId: String = "account",
    val subjectKey: String = "",
    val conflictKey: String = "",
)

data class AssistantMemorySettings(
    val memoryEnabled: Boolean = false,
    val autoMemoryEnabled: Boolean = false,
    val historyReferenceEnabled: Boolean = false,
    val sensitivePolicy: String = "confirm",
)

data class AssistantMemoryState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val accountUserId: String? = null,
    val accountEmail: String? = null,
    val cloudReady: Boolean = false,
    val memoryEnabled: Boolean = false,
    val autoMemoryEnabled: Boolean = false,
    val historyReferenceEnabled: Boolean = false,
    val sensitivePolicy: String = "confirm",
    val memories: List<AssistantMemoryItem> = emptyList(),
    val message: String = "登录后可使用长期记忆。",
    val error: Boolean = false,
    val lastMutationReceipt: AssistantMemoryMutationReceipt? = null,
) {
    val enabledItemCount: Int
        get() = memories.count { it.enabled && it.status == "active" }

    val activeCount: Int
        get() = if (memoryEnabled && cloudReady) memories.count { it.isActiveAt() } else 0

    val canManage: Boolean
        get() = accountUserId != null && cloudReady && !loading && !saving
}

class AssistantMemoryRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val authRepository = SupabaseAuthRepository.get(appContext)
    private val client = SupabaseMemoryV5Client()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val operationInFlight = AtomicBoolean(false)
    private val sessionGuard = AssistantMemorySessionGuard()

    private val _state = MutableStateFlow(AssistantMemoryState())
    val state: StateFlow<AssistantMemoryState> = _state.asStateFlow()

    @Volatile
    private var currentSession: SupabaseUserSession? = null

    init {
        scope.launch {
            authRepository.state.collectLatest { accountState ->
                val session = accountState.session?.takeIf { accountState.isLoggedIn }
                val previousUserId = currentSession?.userId
                currentSession = session
                val ticket = sessionGuard.updateUser(session?.userId)

                if (session == null || ticket == null) {
                    operationInFlight.set(false)
                    _state.value = AssistantMemoryState(message = "登录后可使用长期记忆。")
                    return@collectLatest
                }

                val userChanged = previousUserId != session.userId
                if (userChanged || _state.value.accountUserId == null) {
                    _state.value = AssistantMemoryState(
                        loading = true,
                        accountUserId = session.userId,
                        accountEmail = session.email,
                        message = "正在加载该账号的长期记忆…",
                    )
                    scope.launch {
                        operationMutex.withLock {
                            if (sessionGuard.isCurrent(ticket)) {
                                loadForSessionLocked(session, ticket)
                            }
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        scope.launch {
            operationMutex.withLock {
                val context = currentSessionContext() ?: return@withLock
                loadForSessionLocked(context.session, context.ticket)
            }
        }
    }

    fun refreshAfterCloudMutation(receipt: AssistantMemoryMutationReceipt) {
        if (!receipt.inventoryMayHaveChanged) return
        scope.launch {
            operationMutex.withLock {
                val context = currentSessionContext() ?: return@withLock
                refreshMemoriesAfterMutationLocked(
                    session = context.session,
                    ticket = context.ticket,
                    receipt = receipt,
                    fallbackMessage = receipt.userFacingMessage(),
                )
            }
        }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        updateSettings(
            message = if (enabled) "正在开启长期记忆…" else "正在关闭长期记忆…",
            transform = { it.copy(memoryEnabled = enabled) },
            successMessage = if (enabled) {
                "长期记忆已开启，系统会按当前问题动态检索相关内容。"
            } else {
                "长期记忆已关闭，聊天请求不会检索长期记忆。"
            },
        )
    }

    fun addMemory(
        content: String,
        category: String = "other",
        priority: Int = 1,
        pinned: Boolean = false,
        scope: String = "auto",
        validUntil: String = "",
        sourceType: String = "manual",
        confidence: Double = 1.0,
        supersedesId: String = "",
    ) {
        val clean = normalizeMemoryContent(content) ?: return
        val cleanSupersedesId = supersedesId.trim()
        if (cleanSupersedesId.isNotBlank()) {
            updateMemory(
                id = cleanSupersedesId,
                content = clean,
                category = category,
                priority = priority,
                pinned = pinned,
                scope = scope,
                validUntil = validUntil,
            )
            return
        }
        val normalizedCategory = normalizeMemoryCategory(category)
        runExclusiveOperation("正在保存记忆…") { session, ticket ->
            val receipt = client.mutate(
                session,
                AssistantMemoryMutationRequest(
                    operationId = newMemoryOperationId(),
                    action = "upsert",
                    content = clean,
                    category = normalizedCategory,
                    scope = normalizeMemoryScope(scope),
                    priority = normalizeMemoryPriority(priority),
                    pinned = pinned,
                    validUntil = normalizeMemoryTimestamp(validUntil),
                    sourceType = normalizeMemorySourceType(sourceType),
                    confidence = normalizeMemoryConfidence(confidence),
                    layer = memoryLayerFromCategory(normalizedCategory),
                    reason = "settings_manual_create",
                ),
            )
            applyLocalMutationReceiptLocked(session, ticket, receipt)
        }
    }

    fun updateMemory(
        id: String,
        content: String,
        category: String,
        priority: Int,
        pinned: Boolean,
        scope: String = "auto",
        validUntil: String = "",
    ) {
        val cleanId = id.trim()
        val clean = normalizeMemoryContent(content) ?: return
        if (cleanId.isBlank()) return
        val currentItem = _state.value.memories.firstOrNull { it.id == cleanId }
        val normalizedCategory = normalizeMemoryCategory(category)
        val resolvedLayer = if (currentItem != null && currentItem.category == normalizedCategory) {
            currentItem.layer
        } else {
            memoryLayerFromCategory(normalizedCategory)
        }
        runExclusiveOperation("正在更新记忆…") { session, ticket ->
            val receipt = client.mutate(
                session,
                AssistantMemoryMutationRequest(
                    operationId = newMemoryOperationId(),
                    action = "upsert",
                    targetMemoryId = cleanId,
                    content = clean,
                    category = normalizedCategory,
                    scope = normalizeMemoryScope(scope),
                    priority = normalizeMemoryPriority(priority),
                    pinned = pinned,
                    validUntil = normalizeMemoryTimestamp(validUntil),
                    sourceType = "manual",
                    confidence = currentItem?.confidence ?: 1.0,
                    layer = resolvedLayer,
                    namespaceType = currentItem?.namespaceType.orEmpty().ifBlank { "account" },
                    namespaceId = currentItem?.namespaceId.orEmpty().ifBlank { "account" },
                    subjectKey = currentItem?.subjectKey.orEmpty(),
                    conflictKey = currentItem?.conflictKey.orEmpty(),
                    expectedUpdatedAt = currentItem?.updatedAt.orEmpty(),
                    reason = "settings_manual_update",
                ),
            )
            applyLocalMutationReceiptLocked(session, ticket, receipt)
        }
    }

    fun setItemEnabled(id: String, enabled: Boolean) {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return
        val currentItem = _state.value.memories.firstOrNull { it.id == cleanId }
        runExclusiveOperation(if (enabled) "正在启用记忆…" else "正在停用记忆…") { session, ticket ->
            val receipt = client.mutate(
                session,
                AssistantMemoryMutationRequest(
                    operationId = newMemoryOperationId(),
                    action = if (enabled) "restore" else "archive",
                    targetMemoryId = cleanId,
                    expectedUpdatedAt = currentItem?.updatedAt.orEmpty(),
                    reason = if (enabled) "settings_manual_restore" else "settings_manual_archive",
                ),
            )
            applyLocalMutationReceiptLocked(session, ticket, receipt)
        }
    }

    fun deleteMemory(id: String) {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return
        val currentItem = _state.value.memories.firstOrNull { it.id == cleanId }
        runExclusiveOperation("正在删除记忆…") { session, ticket ->
            val receipt = client.mutate(
                session,
                AssistantMemoryMutationRequest(
                    operationId = newMemoryOperationId(),
                    action = "delete",
                    targetMemoryId = cleanId,
                    expectedUpdatedAt = currentItem?.updatedAt.orEmpty(),
                    deleteScope = "current_only",
                    reason = "settings_manual_delete",
                ),
            )
            applyLocalMutationReceiptLocked(session, ticket, receipt)
        }
    }

    fun clearAll() {
        runExclusiveOperation("正在清除全部记忆…") { session, ticket ->
            val receipt = client.mutate(
                session,
                AssistantMemoryMutationRequest(
                    operationId = newMemoryOperationId(),
                    action = "clear",
                    deleteScope = "all_confirmed",
                    reason = "settings_explicit_clear",
                ),
            )
            applyLocalMutationReceiptLocked(session, ticket, receipt)
        }
    }

    fun compileForRequest(userText: String): AssistantMemoryCompilation {
        val customInstructions = AssistantCustomInstructionsRepository
            .get(appContext)
            .state
            .value
            .effectiveText()
        val compilation = AssistantMemoryCompiler.compile(
            userText = userText,
            customInstructions = customInstructions,
            memoryState = state.value,
        )
        AssistantMemoryRuntime.record(compilation)
        return compilation
    }

    fun recordSuccessfulUsage(ids: List<String>) = Unit

    fun currentSnapshotText(): String? {
        val customInstructions = AssistantCustomInstructionsRepository
            .get(appContext)
            .currentInstructionsText()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null
        return "[[AI_LEDGER_CUSTOM_INSTRUCTIONS_V1]]\n$customInstructions\n[[/AI_LEDGER_CUSTOM_INSTRUCTIONS_V1]]"
    }

    private fun updateSettings(
        message: String,
        transform: (AssistantMemorySettings) -> AssistantMemorySettings,
        successMessage: String,
    ) {
        val current = _state.value
        if (!current.canManage) return
        runExclusiveOperation(message) { session, ticket ->
            val desired = transform(current.toSettings())
            val saved = client.upsertSettings(session, desired)
            if (!sessionGuard.isCurrent(ticket)) return@runExclusiveOperation
            _state.value = _state.value.copy(
                memoryEnabled = saved.memoryEnabled,
                autoMemoryEnabled = saved.autoMemoryEnabled,
                historyReferenceEnabled = saved.historyReferenceEnabled,
                sensitivePolicy = saved.sensitivePolicy,
                message = successMessage,
                error = false,
            )
        }
    }

    private suspend fun applyLocalMutationReceiptLocked(
        session: SupabaseUserSession,
        ticket: AssistantMemorySessionTicket,
        receipt: AssistantMemoryMutationReceipt,
    ) {
        if (!sessionGuard.isCurrent(ticket)) return
        val shouldRefresh = receipt.succeeded || receipt.shouldRefreshAfterConflict || receipt.idempotentReplay
        if (shouldRefresh) {
            refreshMemoriesAfterMutationLocked(
                session = session,
                ticket = ticket,
                receipt = receipt,
                fallbackMessage = receipt.userFacingMessage(),
            )
            return
        }
        if (!sessionGuard.isCurrent(ticket)) return
        _state.value = _state.value.copy(
            lastMutationReceipt = receipt,
            message = receipt.userFacingMessage(),
            error = true,
        )
    }

    private suspend fun refreshMemoriesAfterMutationLocked(
        session: SupabaseUserSession,
        ticket: AssistantMemorySessionTicket,
        receipt: AssistantMemoryMutationReceipt,
        fallbackMessage: String,
    ) {
        try {
            val memories = client.list(session)
            if (!sessionGuard.isCurrent(ticket)) return
            _state.value = _state.value.copy(
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = true,
                memories = memories,
                lastMutationReceipt = receipt,
                message = fallbackMessage,
                error = !receipt.succeeded,
            )
        } catch (error: Throwable) {
            if (!sessionGuard.isCurrent(ticket)) return
            _state.value = _state.value.copy(
                lastMutationReceipt = receipt,
                message = if (receipt.succeeded) {
                    "${fallbackMessage.trimEnd('。')}，但列表同步失败，请稍后刷新。"
                } else {
                    receipt.userFacingMessage()
                },
                error = true,
            )
        }
    }

    private suspend fun loadForSessionLocked(
        session: SupabaseUserSession,
        ticket: AssistantMemorySessionTicket,
    ) {
        if (!sessionGuard.isCurrent(ticket)) return
        val previous = _state.value
        val previousReceipt = previous.lastMutationReceipt.takeIf { previous.accountUserId == session.userId }
        _state.value = AssistantMemoryState(
            loading = true,
            accountUserId = session.userId,
            accountEmail = session.email,
            lastMutationReceipt = previousReceipt,
            message = "正在加载该账号的长期记忆…",
        )
        try {
            val settings = client.loadSettings(session)
            if (!sessionGuard.isCurrent(ticket)) return
            val memories = client.list(session)
            if (!sessionGuard.isCurrent(ticket)) return
            _state.value = AssistantMemoryState(
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = true,
                memoryEnabled = settings.memoryEnabled,
                autoMemoryEnabled = settings.autoMemoryEnabled,
                historyReferenceEnabled = settings.historyReferenceEnabled,
                sensitivePolicy = settings.sensitivePolicy,
                memories = memories,
                lastMutationReceipt = previousReceipt,
                message = if (memories.isEmpty()) {
                    "还没有保存长期记忆。"
                } else {
                    "已同步 ${memories.size} 条长期记忆；聊天时会按当前问题动态检索。"
                },
            )
        } catch (error: Throwable) {
            if (!sessionGuard.isCurrent(ticket)) return
            _state.value = AssistantMemoryState(
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = false,
                memoryEnabled = false,
                memories = emptyList(),
                lastMutationReceipt = previousReceipt,
                message = error.friendlyMemoryMessage(),
                error = true,
            )
        }
    }

    private fun runExclusiveOperation(
        loadingMessage: String,
        block: suspend (SupabaseUserSession, AssistantMemorySessionTicket) -> Unit,
    ) {
        val scheduledState = _state.value
        if (!scheduledState.canManage) return
        val scheduledContext = currentSessionContext()
        if (
            scheduledContext == null ||
            scheduledState.accountUserId != scheduledContext.session.userId ||
            !operationInFlight.compareAndSet(false, true)
        ) return

        scope.launch {
            val operationTicket = scheduledContext.ticket
            try {
                operationMutex.withLock {
                    if (!sessionGuard.isCurrent(operationTicket)) return@withLock
                    _state.value = _state.value.copy(
                        saving = true,
                        message = loadingMessage,
                        error = false,
                    )
                    block(scheduledContext.session, operationTicket)
                }
            } catch (error: Throwable) {
                if (sessionGuard.isCurrent(operationTicket)) {
                    _state.value = _state.value.copy(
                        message = error.friendlyMemoryMessage(),
                        error = true,
                    )
                }
            } finally {
                operationInFlight.set(false)
                if (sessionGuard.isCurrent(operationTicket)) {
                    _state.value = _state.value.copy(saving = false)
                }
            }
        }
    }

    private fun currentSessionContext(): MemorySessionContext? {
        val session = currentSession?.takeIf { it.isUsable } ?: return null
        val ticket = sessionGuard.currentTicket(session.userId) ?: return null
        return MemorySessionContext(session, ticket)
    }

    private fun normalizeMemoryContent(content: String): String? {
        val clean = normalizeMemoryMultilineText(content)
        if (clean.isBlank()) {
            _state.value = _state.value.copy(message = "记忆内容不能为空。", error = true)
            return null
        }
        return clean
    }

    companion object {
        @Volatile
        private var instance: AssistantMemoryRepository? = null

        fun get(context: Context): AssistantMemoryRepository {
            return instance ?: synchronized(this) {
                instance ?: AssistantMemoryRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}

private data class MemorySessionContext(
    val session: SupabaseUserSession,
    val ticket: AssistantMemorySessionTicket,
)

private data class AssistantMemoryMutationRequest(
    val operationId: String,
    val action: String,
    val targetMemoryId: String = "",
    val content: String = "",
    val category: String = "other",
    val scope: String = "auto",
    val priority: Int = 1,
    val pinned: Boolean = false,
    val validUntil: String = "",
    val sourceType: String = "manual",
    val confidence: Double = 1.0,
    val layer: String = "",
    val namespaceType: String = "account",
    val namespaceId: String = "account",
    val subjectKey: String = "",
    val conflictKey: String = "",
    val expectedUpdatedAt: String = "",
    val deleteScope: String = "current_only",
    val reason: String = "settings_manual",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("p_operation_id", operationId)
        put("p_action", action)
        put("p_target_memory_id", targetMemoryId.takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("p_content", content.takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("p_category", category)
        put("p_scope", scope)
        put("p_priority", priority)
        put("p_pinned", pinned)
        put("p_valid_until", validUntil.takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("p_source_type", sourceType)
        put("p_confidence", confidence)
        put("p_layer", layer.takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("p_namespace_type", normalizeMemoryNamespaceType(namespaceType))
        put("p_namespace_id", namespaceId.trim().ifBlank { "account" }.take(180))
        put("p_subject_key", subjectKey.trim().take(160))
        put("p_conflict_key", conflictKey.trim().take(160))
        put("p_expected_updated_at", expectedUpdatedAt.takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("p_delete_scope", deleteScope)
        put("p_reason", reason)
        put("p_metadata", JSONObject().apply {
            put("mutation_schema", MEMORY_MUTATION_SCHEMA)
            put("client", "android-compose")
            put("source", "memory_settings")
        })
    }
}

private class MemoryHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)

private class SupabaseMemoryV5Client(
    private val supabaseUrl: String = SupabaseAuthClient.DEFAULT_SUPABASE_URL,
    private val publishableKey: String = SupabaseAuthClient.DEFAULT_SUPABASE_PUBLISHABLE_KEY,
) {
    fun list(session: SupabaseUserSession): List<AssistantMemoryItem> {
        val response = request(
            session = session,
            path = "/rest/v1/$MEMORY_TABLE?select=id,content,layer,authority,namespace_type,namespace_id,subject_key,conflict_key,status,confidence,priority,pinned,valid_from,valid_until,supersedes_id,last_used_at,use_count,metadata,created_at,updated_at&user_id=eq.${session.userId.urlEncode()}&status=in.(active,archived)&order=pinned.desc,priority.desc,updated_at.desc&limit=$ASSISTANT_MEMORY_MAX_STORED_ITEMS",
            method = "GET",
        )
        val array = JSONArray(response)
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toMemoryItemOrNull()?.let(::add)
            }
        }
    }

    fun loadSettings(session: SupabaseUserSession): AssistantMemorySettings {
        val response = request(
            session = session,
            path = "/rest/v1/$MEMORY_SETTINGS_TABLE?select=memory_enabled,auto_memory_enabled,history_reference_enabled,sensitive_policy&user_id=eq.${session.userId.urlEncode()}&limit=1",
            method = "GET",
        )
        return JSONArray(response).optJSONObject(0)?.toMemorySettings() ?: AssistantMemorySettings()
    }

    fun upsertSettings(
        session: SupabaseUserSession,
        settings: AssistantMemorySettings,
    ): AssistantMemorySettings {
        val body = JSONObject()
            .put("user_id", session.userId)
            .put("memory_enabled", settings.memoryEnabled)
            .put("auto_memory_enabled", settings.autoMemoryEnabled)
            .put("history_reference_enabled", settings.historyReferenceEnabled)
            .put("sensitive_policy", normalizeSensitivePolicy(settings.sensitivePolicy))
        val response = request(
            session = session,
            path = "/rest/v1/$MEMORY_SETTINGS_TABLE?on_conflict=user_id",
            method = "POST",
            body = body,
            prefer = "resolution=merge-duplicates,return=representation",
        )
        return JSONArray(response).optJSONObject(0)?.toMemorySettings()
            ?: throw IOException("云端没有返回有效的记忆设置。")
    }

    suspend fun mutate(
        session: SupabaseUserSession,
        mutation: AssistantMemoryMutationRequest,
    ): AssistantMemoryMutationReceipt {
        val body = mutation.toJson()
        val response = requestMutationWithRetry(session, body)
        val clean = response.trim()
        val item = when {
            clean.startsWith("[") -> JSONArray(clean).optJSONObject(0)
            clean.startsWith("{") -> JSONObject(clean)
            else -> null
        }
        return item?.toAssistantMemoryMutationReceiptOrNull()
            ?: throw IOException("云端没有返回有效的 V5 记忆事务回执。")
    }

    private suspend fun requestMutationWithRetry(
        session: SupabaseUserSession,
        body: JSONObject,
    ): String {
        var lastError: IOException? = null
        repeat(2) { attempt ->
            try {
                return request(
                    session = session,
                    path = "/rest/v1/rpc/$MEMORY_MUTATION_RPC",
                    method = "POST",
                    body = body,
                )
            } catch (error: IOException) {
                lastError = error
                if (attempt > 0 || !error.isRetryableMutationTransportFailure()) throw error
                delay(MEMORY_MUTATION_RETRY_DELAY_MS)
            }
        }
        throw lastError ?: IOException("记忆事务请求失败。")
    }

    private fun request(
        session: SupabaseUserSession,
        path: String,
        method: String,
        body: JSONObject? = null,
        prefer: String = "",
    ): String {
        val base = supabaseUrl.trim().trimEnd('/')
        if (base.isBlank() || publishableKey.isBlank()) {
            throw IOException("Supabase 尚未配置完整。")
        }
        val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = MEMORY_CONNECT_TIMEOUT_MS
            readTimeout = MEMORY_READ_TIMEOUT_MS
            doInput = true
            doOutput = body != null
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (prefer.isNotBlank()) setRequestProperty("Prefer", prefer)
        }
        return try {
            body?.let { payload ->
                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val text = if (status in 200..299) {
                connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            if (status !in 200..299) {
                throw MemoryHttpException(status, translateMemoryError(text, status))
            }
            text.ifBlank { "[]" }
        } finally {
            connection.disconnect()
        }
    }

    private fun translateMemoryError(raw: String, status: Int): String {
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val code = json?.optString("code").orEmpty()
        val message = json?.let { value ->
            value.optString("message")
                .ifBlank { value.optString("hint") }
                .ifBlank { value.optString("details") }
        }.orEmpty().ifBlank { raw.trim() }
        return when {
            message.contains("assistant_memory_mutation_operations_v5", true) ||
                message.contains("assistant_memory_forget_tombstones_v5", true) ->
                "Supabase V203 统一记忆事务结构尚未建立，请执行 V203 迁移 SQL。"
            (code == "42883" || message.contains("function", true) && message.contains("does not exist", true)) &&
                message.contains(MEMORY_MUTATION_RPC, true) ->
                "Supabase V203 统一记忆事务 RPC 尚未建立，请执行 V203 迁移 SQL。"
            code == "42P01" || code == "PGRST205" ||
                message.contains(MEMORY_TABLE, true) && message.contains("schema cache", true) ->
                "Supabase V4 记忆结构尚未建立，请执行第一阶段升级 SQL。"
            code == "42883" || message.contains("function", true) && message.contains("does not exist", true) ->
                "Supabase 记忆 RPC 尚未建立，请检查迁移 SQL。"
            status == 401 -> "登录状态已失效，请重新登录。"
            status == 403 || message.contains("row-level security", true) || message.contains("policy", true) ->
                "Supabase 记忆权限尚未配置，请检查 RLS 策略。"
            message.isNotBlank() -> message
            else -> "长期记忆同步失败：HTTP $status"
        }
    }
}

private fun IOException.isRetryableMutationTransportFailure(): Boolean {
    return when (this) {
        is SocketTimeoutException -> true
        is MemoryHttpException -> statusCode == 408 || statusCode in 500..599
        else -> {
            val text = message.orEmpty().lowercase()
            text.contains("connection reset") ||
                text.contains("unexpected end") ||
                text.contains("failed to connect") ||
                text.contains("network") ||
                text.contains("timeout")
        }
    }
}

private fun JSONObject.toMemorySettings(): AssistantMemorySettings = AssistantMemorySettings(
    memoryEnabled = optBoolean("memory_enabled", false),
    autoMemoryEnabled = optBoolean("auto_memory_enabled", false),
    historyReferenceEnabled = optBoolean("history_reference_enabled", false),
    sensitivePolicy = normalizeSensitivePolicy(optString("sensitive_policy")),
)

private fun JSONObject.toMemoryItemOrNull(): AssistantMemoryItem? {
    val id = optString("id").trim()
    val content = normalizeMemoryMultilineText(optString("content"))
    if (id.isBlank() || content.isBlank()) return null
    val metadata = optJSONObject("metadata") ?: JSONObject()
    val status = normalizeMemoryStatus(optString("status"))
    val layer = normalizeMemoryLayer(optString("layer"))
    val category = normalizeMemoryCategory(
        metadata.optString("category").ifBlank { categoryFromLayer(layer) },
    )
    return AssistantMemoryItem(
        id = id,
        content = content,
        category = category,
        scope = normalizeMemoryScope(
            metadata.optString("scope").ifBlank { optString("subject_key") },
        ),
        priority = normalizeMemoryPriority(optInt("priority", 1)),
        pinned = optBoolean("pinned", false),
        enabled = status == "active",
        confidence = normalizeMemoryConfidence(optDouble("confidence", 1.0)),
        sourceType = normalizeMemorySourceType(
            metadata.optString("source_type").ifBlank { sourceTypeFromAuthority(optString("authority")) },
        ),
        layer = layer,
        authority = optString("authority").trim().take(60),
        namespaceType = normalizeMemoryNamespaceType(optString("namespace_type")),
        namespaceId = optString("namespace_id").trim().ifBlank { "account" }.take(180),
        subjectKey = optString("subject_key").trim().take(160),
        conflictKey = optString("conflict_key").trim().take(160),
        validFrom = optString("valid_from"),
        validUntil = optString("valid_until"),
        supersedesId = optString("supersedes_id"),
        status = status,
        lastUsedAt = optString("last_used_at"),
        useCount = optLong("use_count", 0L).coerceAtLeast(0L),
        createdAt = optString("created_at"),
        updatedAt = optString("updated_at"),
    )
}

private fun AssistantMemoryState.toSettings(): AssistantMemorySettings = AssistantMemorySettings(
    memoryEnabled = memoryEnabled,
    autoMemoryEnabled = autoMemoryEnabled,
    historyReferenceEnabled = historyReferenceEnabled,
    sensitivePolicy = sensitivePolicy,
)

internal fun normalizeMemoryMultilineText(value: String): String {
    val withoutControl = value
        .replace(MEMORY_CONTROL_CHAR_REGEX, " ")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    return withoutControl
        .lineSequence()
        .map { line -> line.replace(MEMORY_INLINE_SPACE_REGEX, " ").trimEnd() }
        .joinToString("\n")
        .replace(MEMORY_EXCESS_BLANK_LINES_REGEX, "\n\n\n")
        .trim()
        .take(ASSISTANT_MEMORY_MAX_CONTENT_LENGTH)
}

internal fun normalizeMemoryCategory(value: String): String {
    val clean = value.trim().lowercase()
    return when {
        clean == "manual" -> "other"
        clean in ALLOWED_MEMORY_CATEGORIES -> clean
        else -> "other"
    }
}

internal fun normalizeMemoryScope(value: String): String {
    val clean = value.trim().lowercase().replace('-', '_')
    return clean.takeIf { it in ALLOWED_MEMORY_SCOPES } ?: "auto"
}

internal fun normalizeMemoryPriority(value: Int): Int = value.coerceIn(0, 3)

internal fun normalizeMemoryConfidence(value: Double): Double {
    return if (value.isFinite()) value.coerceIn(0.0, 1.0) else 1.0
}

internal fun normalizeMemorySourceType(value: String): String {
    val clean = value.trim().lowercase()
    return clean.takeIf { it in ALLOWED_MEMORY_SOURCE_TYPES } ?: "manual"
}

internal fun normalizeMemoryStatus(value: String): String {
    val clean = value.trim().lowercase()
    return clean.takeIf { it in ALLOWED_MEMORY_STATUS } ?: "active"
}

internal fun normalizeMemoryTimestamp(value: String): String {
    val clean = value.trim()
    if (clean.isBlank()) return ""
    return runCatching { Instant.parse(clean).toString() }.getOrDefault(clean.take(64))
}

private fun normalizeMemoryLayer(value: String): String {
    val clean = value.trim().lowercase()
    return clean.takeIf { it in ALLOWED_MEMORY_LAYERS }.orEmpty()
}

private fun normalizeMemoryNamespaceType(value: String): String {
    val clean = value.trim().lowercase()
    return clean.takeIf { it in ALLOWED_MEMORY_NAMESPACE_TYPES } ?: "account"
}

private fun normalizeSensitivePolicy(value: String): String {
    val clean = value.trim().lowercase()
    return clean.takeIf { it in ALLOWED_SENSITIVE_POLICIES } ?: "confirm"
}

private fun memoryLayerFromCategory(category: String): String = when (normalizeMemoryCategory(category)) {
    "rule" -> "explicit_core"
    "preference" -> "preference"
    "project" -> "project"
    "episode" -> "episodic"
    "reflection" -> "session"
    else -> "profile"
}

private fun categoryFromLayer(layer: String): String = when (layer.trim().lowercase()) {
    "explicit_core" -> "rule"
    "profile" -> "profile"
    "preference" -> "preference"
    "project" -> "project"
    "episodic" -> "episode"
    "session" -> "reflection"
    else -> "other"
}

private fun sourceTypeFromAuthority(authority: String): String = when (authority.trim().lowercase()) {
    "system_inferred" -> "system_inferred"
    "migrated" -> "migration"
    else -> "manual"
}

fun memoryCategoryLabel(category: String): String = when (normalizeMemoryCategory(category)) {
    "profile" -> "个人信息"
    "preference" -> "偏好"
    "project" -> "项目"
    "rule" -> "长期规则"
    "skill" -> "场景技能"
    "episode" -> "经历"
    "reflection" -> "归纳"
    else -> "其他"
}

fun memoryScopeLabel(scope: String): String = when (normalizeMemoryScope(scope)) {
    "global" -> "全局"
    "general" -> "通用"
    "english" -> "英语"
    "android" -> "Android"
    "coding" -> "编程"
    "math" -> "数学"
    "writing" -> "写作"
    "finance" -> "金融"
    "travel" -> "旅行"
    else -> "自动识别"
}

fun memoryPriorityLabel(priority: Int): String = when (normalizeMemoryPriority(priority)) {
    0 -> "低"
    2 -> "重要"
    3 -> "核心"
    else -> "普通"
}

private fun newMemoryOperationId(): String {
    return "memop_android_${UUID.randomUUID().toString().replace("-", "")}"
}

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

private fun Throwable.friendlyMemoryMessage(): String {
    return message.orEmpty().trim().ifBlank {
        "长期记忆暂时无法同步，请稍后再试。"
    }
}
