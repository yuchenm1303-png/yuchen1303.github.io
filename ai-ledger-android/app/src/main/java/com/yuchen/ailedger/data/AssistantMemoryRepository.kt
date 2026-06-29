package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.service.SupabaseAuthClient
import com.yuchen.ailedger.service.SupabaseUserSession
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
private const val MEMORY_CREATE_RPC = "create_assistant_memory_v4_manual"
private const val MEMORY_UPDATE_RPC = "update_assistant_memory_v4_manual"
private const val MEMORY_SET_ENABLED_RPC = "set_assistant_memory_v4_enabled"
private const val MEMORY_DELETE_RPC = "delete_assistant_memory_v4"
private const val MEMORY_CLEAR_RPC = "clear_all_assistant_memories_v4"
private const val MEMORY_CONNECT_TIMEOUT_MS = 12_000
private const val MEMORY_READ_TIMEOUT_MS = 18_000

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

private val ALLOWED_SENSITIVE_POLICIES = setOf("confirm", "block", "allow")

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
    private val client = SupabaseMemoryV4Client()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()

    private val _state = MutableStateFlow(AssistantMemoryState())
    val state: StateFlow<AssistantMemoryState> = _state.asStateFlow()

    @Volatile
    private var currentSession: SupabaseUserSession? = null

    init {
        scope.launch {
            authRepository.state.collectLatest { accountState ->
                val session = accountState.session?.takeIf { accountState.isLoggedIn }
                operationMutex.withLock {
                    if (session == null) {
                        currentSession = null
                        _state.value = AssistantMemoryState(message = "登录后可使用长期记忆。")
                        return@withLock
                    }

                    val userChanged = currentSession?.userId != session.userId
                    currentSession = session
                    if (userChanged || _state.value.accountUserId == null) {
                        loadForSessionLocked(session)
                    }
                }
            }
        }
    }

    fun refresh() {
        scope.launch {
            operationMutex.withLock {
                currentSession?.let { loadForSessionLocked(it) }
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
        runMutation("正在保存记忆…") { session ->
            val item = client.create(
                session = session,
                content = clean,
                category = normalizeMemoryCategory(category),
                scope = normalizeMemoryScope(scope),
                priority = normalizeMemoryPriority(priority),
                pinned = pinned,
                validUntil = normalizeMemoryTimestamp(validUntil),
                sourceType = normalizeMemorySourceType(sourceType),
                confidence = normalizeMemoryConfidence(confidence),
            )
            _state.value = _state.value.copy(
                saving = false,
                memories = listOf(item) + _state.value.memories.filterNot { it.id == item.id },
                message = "记忆已保存，后续会按场景和相关性动态检索。",
                error = false,
            )
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
        runMutation("正在更新记忆…") { session ->
            val item = client.update(
                session = session,
                id = cleanId,
                content = clean,
                category = normalizeMemoryCategory(category),
                scope = normalizeMemoryScope(scope),
                priority = normalizeMemoryPriority(priority),
                pinned = pinned,
                validUntil = normalizeMemoryTimestamp(validUntil),
            )
            _state.value = _state.value.copy(
                saving = false,
                memories = listOf(item) + _state.value.memories.filterNot {
                    it.id == cleanId || it.id == item.id
                },
                message = "记忆已更新，旧版本已保留在版本历史中。",
                error = false,
            )
        }
    }

    fun setItemEnabled(id: String, enabled: Boolean) {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return
        runMutation(if (enabled) "正在启用记忆…" else "正在停用记忆…") { session ->
            val item = client.setEnabled(session, cleanId, enabled)
            _state.value = _state.value.copy(
                saving = false,
                memories = _state.value.memories.map { if (it.id == cleanId) item else it },
                message = if (enabled) "这条记忆已启用。" else "这条记忆已归档停用。",
                error = false,
            )
        }
    }

    fun deleteMemory(id: String) {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return
        runMutation("正在删除记忆…") { session ->
            client.delete(session, cleanId)
            _state.value = _state.value.copy(
                saving = false,
                memories = _state.value.memories.filterNot { it.id == cleanId },
                message = "记忆已删除。",
                error = false,
            )
        }
    }

    fun clearAll() {
        runMutation("正在清除全部记忆…") { session ->
            client.clearAll(session)
            _state.value = _state.value.copy(
                saving = false,
                memories = emptyList(),
                message = "该账号的长期记忆已全部清除。",
                error = false,
            )
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

    /** V4 使用记录由云端在真实注入和回答完成阶段统一写入。 */
    fun recordSuccessfulUsage(ids: List<String>) = Unit

    /** 只保留明确自定义指令兼容入口；长期记忆正文不再由 Android 本地拼接。 */
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
        runMutation(message) { session ->
            val desired = transform(current.toSettings())
            val saved = client.upsertSettings(session, desired)
            _state.value = _state.value.copy(
                saving = false,
                memoryEnabled = saved.memoryEnabled,
                autoMemoryEnabled = saved.autoMemoryEnabled,
                historyReferenceEnabled = saved.historyReferenceEnabled,
                sensitivePolicy = saved.sensitivePolicy,
                message = successMessage,
                error = false,
            )
        }
    }

    private suspend fun loadForSessionLocked(session: SupabaseUserSession) {
        _state.value = AssistantMemoryState(
            loading = true,
            accountUserId = session.userId,
            accountEmail = session.email,
            message = "正在加载该账号的 V4 长期记忆…",
        )
        try {
            val settings = client.loadSettings(session)
            val memories = client.list(session)
            _state.value = AssistantMemoryState(
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = true,
                memoryEnabled = settings.memoryEnabled,
                autoMemoryEnabled = settings.autoMemoryEnabled,
                historyReferenceEnabled = settings.historyReferenceEnabled,
                sensitivePolicy = settings.sensitivePolicy,
                memories = memories,
                message = if (memories.isEmpty()) {
                    "还没有保存长期记忆。"
                } else {
                    "已同步 ${memories.size} 条 V4 长期记忆；聊天时会按当前问题动态检索。"
                },
            )
        } catch (error: Throwable) {
            _state.value = AssistantMemoryState(
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = false,
                memoryEnabled = false,
                memories = emptyList(),
                message = error.friendlyMemoryMessage(),
                error = true,
            )
        }
    }

    private fun runMutation(
        loadingMessage: String,
        block: suspend (SupabaseUserSession) -> Unit,
    ) {
        if (_state.value.saving || !_state.value.canManage) return
        scope.launch {
            operationMutex.withLock {
                val session = currentSession
                if (session == null || !session.isUsable) {
                    _state.value = _state.value.copy(
                        saving = false,
                        cloudReady = false,
                        memoryEnabled = false,
                        memories = emptyList(),
                        message = "登录状态已失效，请重新登录。",
                        error = true,
                    )
                    return@withLock
                }
                _state.value = _state.value.copy(
                    saving = true,
                    message = loadingMessage,
                    error = false,
                )
                try {
                    block(session)
                } catch (error: Throwable) {
                    _state.value = _state.value.copy(
                        saving = false,
                        message = error.friendlyMemoryMessage(),
                        error = true,
                    )
                }
            }
        }
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

private class SupabaseMemoryV4Client(
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

    fun create(
        session: SupabaseUserSession,
        content: String,
        category: String,
        scope: String,
        priority: Int,
        pinned: Boolean,
        validUntil: String,
        sourceType: String,
        confidence: Double,
    ): AssistantMemoryItem {
        val body = JSONObject()
            .put("p_content", content)
            .put("p_category", category)
            .put("p_scope", scope)
            .put("p_priority", priority)
            .put("p_pinned", pinned)
            .put("p_source_type", sourceType)
            .put("p_confidence", confidence)
            .put("p_valid_until", validUntil.takeIf(String::isNotBlank) ?: JSONObject.NULL)
        return requestMemoryRpc(session, MEMORY_CREATE_RPC, body)
    }

    fun update(
        session: SupabaseUserSession,
        id: String,
        content: String,
        category: String,
        scope: String,
        priority: Int,
        pinned: Boolean,
        validUntil: String,
    ): AssistantMemoryItem {
        val body = JSONObject()
            .put("p_memory_id", id)
            .put("p_content", content)
            .put("p_category", category)
            .put("p_scope", scope)
            .put("p_priority", priority)
            .put("p_pinned", pinned)
            .put("p_valid_until", validUntil.takeIf(String::isNotBlank) ?: JSONObject.NULL)
        return requestMemoryRpc(session, MEMORY_UPDATE_RPC, body)
    }

    fun setEnabled(
        session: SupabaseUserSession,
        id: String,
        enabled: Boolean,
    ): AssistantMemoryItem {
        return requestMemoryRpc(
            session,
            MEMORY_SET_ENABLED_RPC,
            JSONObject().put("p_memory_id", id).put("p_enabled", enabled),
        )
    }

    fun delete(session: SupabaseUserSession, id: String) {
        request(
            session = session,
            path = "/rest/v1/rpc/$MEMORY_DELETE_RPC",
            method = "POST",
            body = JSONObject().put("p_memory_id", id),
        )
    }

    fun clearAll(session: SupabaseUserSession) {
        request(
            session = session,
            path = "/rest/v1/rpc/$MEMORY_CLEAR_RPC",
            method = "POST",
            body = JSONObject(),
        )
    }

    private fun requestMemoryRpc(
        session: SupabaseUserSession,
        rpc: String,
        body: JSONObject,
    ): AssistantMemoryItem {
        val response = request(
            session = session,
            path = "/rest/v1/rpc/$rpc",
            method = "POST",
            body = body,
        )
        val clean = response.trim()
        val item = when {
            clean.startsWith("[") -> JSONArray(clean).optJSONObject(0)
            clean.startsWith("{") -> JSONObject(clean)
            else -> null
        }
        return item?.toMemoryItemOrNull()
            ?: throw IOException("云端没有返回有效的 V4 记忆数据。")
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
            if (status !in 200..299) throw IOException(translateMemoryError(text, status))
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
            code == "42P01" || code == "PGRST205" ||
                message.contains(MEMORY_TABLE, true) && message.contains("schema cache", true) ->
                "Supabase V4 记忆结构尚未建立，请执行第一阶段升级 SQL。"
            code == "42883" || message.contains("function", true) && message.contains("does not exist", true) ->
                "Supabase V4 记忆 RPC 尚未建立，请执行第一阶段升级 SQL。"
            status == 401 -> "登录状态已失效，请重新登录。"
            status == 403 || message.contains("row-level security", true) || message.contains("policy", true) ->
                "Supabase 记忆权限尚未配置，请检查 RLS 策略。"
            message.isNotBlank() -> message
            else -> "长期记忆同步失败：HTTP $status"
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
    val category = normalizeMemoryCategory(
        metadata.optString("category").ifBlank { categoryFromLayer(optString("layer")) }
    )
    return AssistantMemoryItem(
        id = id,
        content = content,
        category = category,
        scope = normalizeMemoryScope(
            metadata.optString("scope").ifBlank { optString("subject_key") }
        ),
        priority = normalizeMemoryPriority(optInt("priority", 1)),
        pinned = optBoolean("pinned", false),
        enabled = status == "active",
        confidence = normalizeMemoryConfidence(optDouble("confidence", 1.0)),
        sourceType = normalizeMemorySourceType(
            metadata.optString("source_type").ifBlank { sourceTypeFromAuthority(optString("authority")) }
        ),
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
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]"), " ")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    return withoutControl
        .lineSequence()
        .map { line -> line.replace(Regex("[\\t ]+"), " ").trimEnd() }
        .joinToString("\n")
        .replace(Regex("\n{4,}"), "\n\n\n")
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

private fun normalizeSensitivePolicy(value: String): String {
    val clean = value.trim().lowercase()
    return clean.takeIf { it in ALLOWED_SENSITIVE_POLICIES } ?: "confirm"
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

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

private fun Throwable.friendlyMemoryMessage(): String {
    return message.orEmpty().trim().ifBlank {
        "长期记忆暂时无法同步，请稍后再试。"
    }
}
