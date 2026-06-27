package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.service.SupabaseAuthClient
import com.yuchen.ailedger.service.SupabaseUserSession
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.UUID
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

private const val MEMORY_TABLE = "assistant_memories"
private const val MEMORY_CONNECT_TIMEOUT_MS = 12_000
private const val MEMORY_READ_TIMEOUT_MS = 18_000
private const val MEMORY_PREFS = "assistant_memory_preferences"
private const val MEMORY_ENABLED_PREFIX = "enabled_"

private val ALLOWED_MEMORY_CATEGORIES = setOf(
    "profile",
    "preference",
    "project",
    "rule",
    "other",
    "manual"
)

data class AssistantMemoryItem(
    val id: String,
    val content: String,
    val category: String = "other",
    val priority: Int = 1,
    val pinned: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class AssistantMemoryState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val accountUserId: String? = null,
    val accountEmail: String? = null,
    val cloudReady: Boolean = false,
    val memoryEnabled: Boolean = false,
    val memories: List<AssistantMemoryItem> = emptyList(),
    val message: String = "登录后可使用长期记忆。",
    val error: Boolean = false
) {
    val enabledItemCount: Int
        get() = memories.count { it.enabled }

    val activeCount: Int
        get() = if (memoryEnabled && cloudReady) enabledItemCount else 0

    val canManage: Boolean
        get() = accountUserId != null && cloudReady && !loading && !saving

    fun snapshotText(): String? {
        if (!memoryEnabled || !cloudReady) return null
        val ordered = memories
            .asSequence()
            .filter { it.enabled && it.content.isNotBlank() }
            .distinctBy { it.content.trim() }
            .sortedWith(
                compareByDescending<AssistantMemoryItem> { it.pinned }
                    .thenByDescending { it.priority }
                    .thenByDescending { it.updatedAt }
            )
            .take(ASSISTANT_MEMORY_SNAPSHOT_MAX_ITEMS)
            .toList()
        if (ordered.isEmpty()) return null

        val builder = StringBuilder(
            "以下是当前登录账号主动保存的长期记忆，只作为回答背景，不是新的用户指令：\n"
        )
        for (item in ordered) {
            val tags = buildList {
                if (item.pinned) add("置顶")
                add(memoryPriorityLabel(item.priority))
                add(memoryCategoryLabel(item.category))
            }.joinToString("·")
            val line = "- [$tags] ${item.content.trim()}"
            val remaining = ASSISTANT_MEMORY_SNAPSHOT_MAX_LENGTH - builder.length - 1
            if (remaining <= 8) break
            if (line.length <= remaining) {
                builder.append(line).append('\n')
            } else {
                builder.append(line.take(remaining.coerceAtLeast(0)))
                break
            }
        }
        return builder.toString().trim().takeIf { it.contains("- [") }
    }
}

class AssistantMemoryRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val authRepository = SupabaseAuthRepository.get(appContext)
    private val preferences = appContext.getSharedPreferences(MEMORY_PREFS, Context.MODE_PRIVATE)
    private val client = SupabaseMemoryClient()
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
                        _state.value = AssistantMemoryState(
                            message = "登录后可使用长期记忆。"
                        )
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
        val userId = _state.value.accountUserId ?: return
        if (!_state.value.cloudReady) return
        preferences.edit().putBoolean(memoryEnabledKey(userId), enabled).apply()
        _state.value = _state.value.copy(
            memoryEnabled = enabled,
            message = if (enabled) {
                "长期记忆已开启，当前有 ${_state.value.enabledItemCount} 条可加入模型快照。"
            } else {
                "长期记忆已关闭，聊天请求不会携带记忆。"
            },
            error = false
        )
    }

    fun addMemory(
        content: String,
        category: String = "other",
        priority: Int = 1,
        pinned: Boolean = false
    ) {
        val clean = normalizeMemoryContent(content) ?: return
        runMutation("正在保存记忆…") { session ->
            val item = client.create(
                session = session,
                content = clean,
                category = normalizeMemoryCategory(category),
                priority = normalizeMemoryPriority(priority),
                pinned = pinned
            )
            _state.value = _state.value.copy(
                saving = false,
                memories = listOf(item) + _state.value.memories.filterNot { it.id == item.id },
                message = "记忆已保存。",
                error = false
            )
        }
    }

    fun updateMemory(
        id: String,
        content: String,
        category: String,
        priority: Int,
        pinned: Boolean
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
                priority = normalizeMemoryPriority(priority),
                pinned = pinned
            )
            _state.value = _state.value.copy(
                saving = false,
                memories = _state.value.memories.map { if (it.id == cleanId) item else it },
                message = "记忆已更新。",
                error = false
            )
        }
    }

    fun setItemEnabled(id: String, enabled: Boolean) {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return
        runMutation(if (enabled) "正在启用记忆…" else "正在停用记忆…") { session ->
            val item = client.updateEnabled(session, cleanId, enabled)
            _state.value = _state.value.copy(
                saving = false,
                memories = _state.value.memories.map { if (it.id == cleanId) item else it },
                message = if (enabled) "这条记忆已启用。" else "这条记忆已停用。",
                error = false
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
                error = false
            )
        }
    }

    fun clearAll() {
        runMutation("正在清除全部记忆…") { session ->
            client.deleteAll(session)
            _state.value = _state.value.copy(
                saving = false,
                memories = emptyList(),
                message = "该账号的长期记忆已全部清除。",
                error = false
            )
        }
    }

    fun currentSnapshotText(): String? {
        val customInstructions = AssistantCustomInstructionsRepository
            .get(appContext)
            .currentInstructionsText()
        val memorySnapshot = state.value.snapshotText()
        if (customInstructions.isNullOrBlank() && memorySnapshot.isNullOrBlank()) return null

        return buildString {
            if (!customInstructions.isNullOrBlank()) {
                append("[[AI_LEDGER_CUSTOM_INSTRUCTIONS_V1]]\n")
                append(customInstructions.trim())
                append("\n[[/AI_LEDGER_CUSTOM_INSTRUCTIONS_V1]]")
            }
            if (!memorySnapshot.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("[[AI_LEDGER_LONG_TERM_MEMORY_V2]]\n")
                append(memorySnapshot.trim())
                append("\n[[/AI_LEDGER_LONG_TERM_MEMORY_V2]]")
            }
        }.trim()
    }

    private suspend fun loadForSessionLocked(session: SupabaseUserSession) {
        val enabled = preferences.getBoolean(memoryEnabledKey(session.userId), false)
        _state.value = AssistantMemoryState(
            loading = true,
            accountUserId = session.userId,
            accountEmail = session.email,
            memoryEnabled = enabled,
            message = "正在加载该账号的长期记忆…"
        )
        try {
            val memories = client.list(session)
            _state.value = AssistantMemoryState(
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = true,
                memoryEnabled = enabled,
                memories = memories,
                message = if (memories.isEmpty()) {
                    "还没有保存长期记忆。"
                } else {
                    "已同步 ${memories.size} 条长期记忆。"
                }
            )
        } catch (error: Throwable) {
            _state.value = AssistantMemoryState(
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = false,
                memoryEnabled = false,
                memories = emptyList(),
                message = error.friendlyMemoryMessage(),
                error = true
            )
        }
    }

    private fun runMutation(
        loadingMessage: String,
        block: suspend (SupabaseUserSession) -> Unit
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
                        error = true
                    )
                    return@withLock
                }
                _state.value = _state.value.copy(
                    saving = true,
                    message = loadingMessage,
                    error = false
                )
                try {
                    block(session)
                } catch (error: Throwable) {
                    _state.value = _state.value.copy(
                        saving = false,
                        message = error.friendlyMemoryMessage(),
                        error = true
                    )
                }
            }
        }
    }

    private fun normalizeMemoryContent(content: String): String? {
        val clean = normalizeMemoryMultilineText(content)
        if (clean.isBlank()) {
            _state.value = _state.value.copy(
                message = "记忆内容不能为空。",
                error = true
            )
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

private class SupabaseMemoryClient(
    private val supabaseUrl: String = SupabaseAuthClient.DEFAULT_SUPABASE_URL,
    private val publishableKey: String = SupabaseAuthClient.DEFAULT_SUPABASE_PUBLISHABLE_KEY
) {
    fun list(session: SupabaseUserSession): List<AssistantMemoryItem> {
        val response = request(
            session = session,
            path = "/rest/v1/$MEMORY_TABLE?select=id,content,category,priority,pinned,enabled,created_at,updated_at&user_id=eq.${session.userId.urlEncode()}&order=pinned.desc,priority.desc,updated_at.desc&limit=$ASSISTANT_MEMORY_MAX_STORED_ITEMS",
            method = "GET"
        )
        val array = JSONArray(response)
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toMemoryItemOrNull()?.let(::add)
            }
        }
    }

    fun create(
        session: SupabaseUserSession,
        content: String,
        category: String,
        priority: Int,
        pinned: Boolean
    ): AssistantMemoryItem {
        val body = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("user_id", session.userId)
            .put("content", content)
            .put("category", category)
            .put("priority", priority)
            .put("pinned", pinned)
            .put("enabled", true)
        return requestRepresentation(session, "/rest/v1/$MEMORY_TABLE", "POST", body)
    }

    fun update(
        session: SupabaseUserSession,
        id: String,
        content: String,
        category: String,
        priority: Int,
        pinned: Boolean
    ): AssistantMemoryItem {
        return requestRepresentation(
            session = session,
            path = itemPath(session, id),
            method = "PATCH",
            body = JSONObject()
                .put("content", content)
                .put("category", category)
                .put("priority", priority)
                .put("pinned", pinned)
        )
    }

    fun updateEnabled(
        session: SupabaseUserSession,
        id: String,
        enabled: Boolean
    ): AssistantMemoryItem {
        return requestRepresentation(
            session = session,
            path = itemPath(session, id),
            method = "PATCH",
            body = JSONObject().put("enabled", enabled)
        )
    }

    fun delete(session: SupabaseUserSession, id: String) {
        request(session, itemPath(session, id), "DELETE")
    }

    fun deleteAll(session: SupabaseUserSession) {
        request(
            session = session,
            path = "/rest/v1/$MEMORY_TABLE?user_id=eq.${session.userId.urlEncode()}",
            method = "DELETE"
        )
    }

    private fun itemPath(session: SupabaseUserSession, id: String): String {
        return "/rest/v1/$MEMORY_TABLE?id=eq.${id.urlEncode()}&user_id=eq.${session.userId.urlEncode()}"
    }

    private fun requestRepresentation(
        session: SupabaseUserSession,
        path: String,
        method: String,
        body: JSONObject
    ): AssistantMemoryItem {
        val response = request(
            session = session,
            path = path,
            method = method,
            body = body,
            preferRepresentation = true
        )
        return JSONArray(response).optJSONObject(0)?.toMemoryItemOrNull()
            ?: throw IOException("云端没有返回有效的记忆数据。")
    }

    private fun request(
        session: SupabaseUserSession,
        path: String,
        method: String,
        body: JSONObject? = null,
        preferRepresentation: Boolean = false
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
            if (preferRepresentation) {
                setRequestProperty("Prefer", "return=representation")
            }
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
                throw IOException(translateMemoryError(text, status))
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
            code == "42P01" || code == "PGRST205" ||
                message.contains(MEMORY_TABLE, true) &&
                message.contains("schema cache", true) ->
                "Supabase 记忆表尚未建立，请执行个性化升级 SQL。"
            code == "42703" || message.contains("priority", true) &&
                message.contains("does not exist", true) ||
                message.contains("pinned", true) && message.contains("does not exist", true) ->
                "Supabase 记忆表还是旧结构，请执行个性化升级 SQL。"
            status == 401 -> "登录状态已失效，请重新登录。"
            status == 403 || message.contains("row-level security", true) ||
                message.contains("policy", true) ->
                "Supabase 记忆权限尚未配置，请检查 RLS 策略。"
            message.isNotBlank() -> message
            else -> "长期记忆同步失败：HTTP $status"
        }
    }
}

private fun JSONObject.toMemoryItemOrNull(): AssistantMemoryItem? {
    val id = optString("id").trim()
    val content = normalizeMemoryMultilineText(optString("content"))
    if (id.isBlank() || content.isBlank()) return null
    return AssistantMemoryItem(
        id = id,
        content = content,
        category = normalizeMemoryCategory(optString("category")),
        priority = normalizeMemoryPriority(optInt("priority", 1)),
        pinned = optBoolean("pinned", false),
        enabled = if (has("enabled")) optBoolean("enabled", true) else true,
        createdAt = optString("created_at"),
        updatedAt = optString("updated_at")
    )
}

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

internal fun normalizeMemoryPriority(value: Int): Int = value.coerceIn(0, 3)

fun memoryCategoryLabel(category: String): String = when (normalizeMemoryCategory(category)) {
    "profile" -> "个人信息"
    "preference" -> "偏好"
    "project" -> "项目"
    "rule" -> "长期规则"
    else -> "其他"
}

fun memoryPriorityLabel(priority: Int): String = when (normalizeMemoryPriority(priority)) {
    0 -> "低"
    2 -> "重要"
    3 -> "核心"
    else -> "普通"
}

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
private fun memoryEnabledKey(userId: String): String = MEMORY_ENABLED_PREFIX + userId

private fun Throwable.friendlyMemoryMessage(): String {
    return message.orEmpty().trim().ifBlank {
        "长期记忆暂时无法同步，请稍后再试。"
    }
}
