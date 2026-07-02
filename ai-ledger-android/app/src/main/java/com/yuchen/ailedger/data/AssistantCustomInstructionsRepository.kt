package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.service.SupabaseAuthClient
import com.yuchen.ailedger.service.SupabaseUserSession
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
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

const val ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH = 2_000

private const val CUSTOM_INSTRUCTIONS_TABLE = "assistant_custom_instructions"
private const val CUSTOM_INSTRUCTIONS_CONNECT_TIMEOUT_MS = 12_000
private const val CUSTOM_INSTRUCTIONS_READ_TIMEOUT_MS = 18_000
private val CUSTOM_INSTRUCTIONS_CONTROL_CHAR_REGEX =
    Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")
private val CUSTOM_INSTRUCTIONS_INLINE_SPACE_REGEX = Regex("[\\t ]+")
private val CUSTOM_INSTRUCTIONS_EXCESS_BLANK_LINES_REGEX = Regex("\n{4,}")

data class AssistantCustomInstructionsState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val accountUserId: String? = null,
    val accountEmail: String? = null,
    val cloudReady: Boolean = false,
    val enabled: Boolean = false,
    val content: String = "",
    val updatedAt: String = "",
    val message: String = "登录后可设置自定义指令。",
    val error: Boolean = false
) {
    val isLoggedIn: Boolean
        get() = accountUserId != null

    val contentLength: Int
        get() = customInstructionsCharacterCount(content)

    val isWithinLimit: Boolean
        get() = contentLength <= ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH

    val effectiveLength: Int
        get() = if (enabled && cloudReady && isWithinLimit) contentLength else 0

    fun effectiveText(): String? {
        return normalizeCustomInstructions(content).takeIf {
            enabled && cloudReady && isWithinLimit && it.isNotBlank()
        }
    }
}

class AssistantCustomInstructionsRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val authRepository = SupabaseAuthRepository.get(appContext)
    private val client = SupabaseCustomInstructionsClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val mutationInFlight = AtomicBoolean(false)
    private val sessionGuard = AssistantMemorySessionGuard()

    private val _state = MutableStateFlow(AssistantCustomInstructionsState())
    val state: StateFlow<AssistantCustomInstructionsState> = _state.asStateFlow()

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
                    mutationInFlight.set(false)
                    _state.value = AssistantCustomInstructionsState(
                        message = "登录后可设置自定义指令。"
                    )
                    return@collectLatest
                }

                val userChanged = previousUserId != session.userId
                if (userChanged || _state.value.accountUserId == null) {
                    _state.value = AssistantCustomInstructionsState(
                        loading = true,
                        accountUserId = session.userId,
                        accountEmail = session.email,
                        message = "正在加载该账号的自定义指令…"
                    )
                    // 阻塞式网络请求不再占住账号 Flow；旧账号结果由代际票据直接丢弃。
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

    fun save(content: String, enabled: Boolean) {
        val clean = normalizeCustomInstructions(content)
        val characterCount = customInstructionsCharacterCount(clean)
        if (characterCount > ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH) {
            _state.value = _state.value.copy(
                message = customInstructionsTooLongMessage(characterCount),
                error = true
            )
            return
        }

        runMutation("正在保存自定义指令…") { session, ticket ->
            val next = client.upsert(session, clean, enabled && clean.isNotBlank())
            if (!sessionGuard.isCurrent(ticket)) return@runMutation
            _state.value = next.copy(
                loading = false,
                saving = false,
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = true,
                message = when {
                    next.enabled -> "自定义指令已保存并启用。"
                    next.content.isBlank() -> "自定义指令已清空。"
                    else -> "自定义指令已保存，但当前未启用。"
                },
                error = false
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        val current = _state.value
        if (!current.cloudReady || current.content.isBlank()) {
            _state.value = current.copy(
                message = "请先填写并保存自定义指令。",
                error = true
            )
            return
        }
        if (!current.isWithinLimit) {
            _state.value = current.copy(
                message = customInstructionsTooLongMessage(current.contentLength),
                error = true
            )
            return
        }
        save(current.content, enabled)
    }

    fun clear() {
        runMutation("正在清除自定义指令…") { session, ticket ->
            client.delete(session)
            if (!sessionGuard.isCurrent(ticket)) return@runMutation
            _state.value = AssistantCustomInstructionsState(
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = true,
                enabled = false,
                content = "",
                message = "自定义指令已清除。"
            )
        }
    }

    fun currentInstructionsText(): String? = state.value.effectiveText()

    private suspend fun loadForSessionLocked(
        session: SupabaseUserSession,
        ticket: AssistantMemorySessionTicket,
    ) {
        if (!sessionGuard.isCurrent(ticket)) return
        _state.value = AssistantCustomInstructionsState(
            loading = true,
            accountUserId = session.userId,
            accountEmail = session.email,
            message = "正在加载该账号的自定义指令…"
        )
        try {
            val loaded = client.load(session)
            if (!sessionGuard.isCurrent(ticket)) return
            val overLimit = !loaded.isWithinLimit
            _state.value = loaded.copy(
                loading = false,
                saving = false,
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = true,
                message = when {
                    loaded.content.isBlank() -> "还没有设置自定义指令。"
                    overLimit -> "自定义指令已同步，但超过 ${ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH} 个字符；精简并重新保存后才会生效。"
                    else -> "自定义指令已同步。"
                },
                error = overLimit
            )
        } catch (error: Throwable) {
            if (!sessionGuard.isCurrent(ticket)) return
            _state.value = AssistantCustomInstructionsState(
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = false,
                message = error.friendlyCustomInstructionsMessage(),
                error = true
            )
        }
    }

    private fun runMutation(
        loadingMessage: String,
        block: suspend (SupabaseUserSession, AssistantMemorySessionTicket) -> Unit
    ) {
        if (
            _state.value.saving ||
            _state.value.loading ||
            !_state.value.isLoggedIn ||
            !mutationInFlight.compareAndSet(false, true)
        ) return

        scope.launch {
            var operationTicket: AssistantMemorySessionTicket? = null
            try {
                operationMutex.withLock {
                    val context = currentSessionContext()
                    if (context == null) {
                        _state.value = AssistantCustomInstructionsState(
                            message = "登录状态已失效，请重新登录。",
                            error = true
                        )
                        return@withLock
                    }
                    operationTicket = context.ticket
                    if (!sessionGuard.isCurrent(context.ticket)) return@withLock
                    _state.value = _state.value.copy(
                        saving = true,
                        message = loadingMessage,
                        error = false
                    )
                    block(context.session, context.ticket)
                }
            } catch (error: Throwable) {
                val ticket = operationTicket
                if (ticket != null && sessionGuard.isCurrent(ticket)) {
                    _state.value = _state.value.copy(
                        message = error.friendlyCustomInstructionsMessage(),
                        error = true
                    )
                }
            } finally {
                mutationInFlight.set(false)
                val ticket = operationTicket
                if (ticket != null && sessionGuard.isCurrent(ticket)) {
                    _state.value = _state.value.copy(saving = false)
                }
            }
        }
    }

    private fun currentSessionContext(): CustomInstructionsSessionContext? {
        val session = currentSession?.takeIf { it.isUsable } ?: return null
        val ticket = sessionGuard.currentTicket(session.userId) ?: return null
        return CustomInstructionsSessionContext(session, ticket)
    }

    companion object {
        @Volatile
        private var instance: AssistantCustomInstructionsRepository? = null

        fun get(context: Context): AssistantCustomInstructionsRepository {
            return instance ?: synchronized(this) {
                instance ?: AssistantCustomInstructionsRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}

private data class CustomInstructionsSessionContext(
    val session: SupabaseUserSession,
    val ticket: AssistantMemorySessionTicket,
)

private class SupabaseCustomInstructionsClient(
    private val supabaseUrl: String = SupabaseAuthClient.DEFAULT_SUPABASE_URL,
    private val publishableKey: String = SupabaseAuthClient.DEFAULT_SUPABASE_PUBLISHABLE_KEY
) {
    fun load(session: SupabaseUserSession): AssistantCustomInstructionsState {
        val response = request(
            session = session,
            path = "/rest/v1/$CUSTOM_INSTRUCTIONS_TABLE?select=content,enabled,updated_at&user_id=eq.${session.userId.urlEncode()}&limit=1",
            method = "GET"
        )
        val item = JSONArray(response).optJSONObject(0)
        return AssistantCustomInstructionsState(
            cloudReady = true,
            enabled = item?.optBoolean("enabled", false) == true,
            content = normalizeCustomInstructions(item?.optString("content").orEmpty()),
            updatedAt = item?.optString("updated_at").orEmpty()
        )
    }

    fun upsert(
        session: SupabaseUserSession,
        content: String,
        enabled: Boolean
    ): AssistantCustomInstructionsState {
        val body = JSONObject()
            .put("user_id", session.userId)
            .put("content", content)
            .put("enabled", enabled)
        val response = request(
            session = session,
            path = "/rest/v1/$CUSTOM_INSTRUCTIONS_TABLE?on_conflict=user_id",
            method = "POST",
            body = body,
            prefer = "resolution=merge-duplicates,return=representation"
        )
        val item = JSONArray(response).optJSONObject(0)
            ?: throw IOException("云端没有返回有效的自定义指令数据。")
        return AssistantCustomInstructionsState(
            cloudReady = true,
            enabled = item.optBoolean("enabled", false),
            content = normalizeCustomInstructions(item.optString("content")),
            updatedAt = item.optString("updated_at")
        )
    }

    fun delete(session: SupabaseUserSession) {
        request(
            session = session,
            path = "/rest/v1/$CUSTOM_INSTRUCTIONS_TABLE?user_id=eq.${session.userId.urlEncode()}",
            method = "DELETE"
        )
    }

    private fun request(
        session: SupabaseUserSession,
        path: String,
        method: String,
        body: JSONObject? = null,
        prefer: String? = null
    ): String {
        val base = supabaseUrl.trim().trimEnd('/')
        if (base.isBlank() || publishableKey.isBlank()) {
            throw IOException("Supabase 尚未配置完整。")
        }
        val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CUSTOM_INSTRUCTIONS_CONNECT_TIMEOUT_MS
            readTimeout = CUSTOM_INSTRUCTIONS_READ_TIMEOUT_MS
            doInput = true
            doOutput = body != null
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (!prefer.isNullOrBlank()) setRequestProperty("Prefer", prefer)
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
                throw IOException(translateCustomInstructionsError(text, status))
            }
            text.ifBlank { "[]" }
        } finally {
            connection.disconnect()
        }
    }

    private fun translateCustomInstructionsError(raw: String, status: Int): String {
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val code = json?.optString("code").orEmpty()
        val message = json?.let { value ->
            value.optString("message")
                .ifBlank { value.optString("hint") }
                .ifBlank { value.optString("details") }
        }.orEmpty().ifBlank { raw.trim() }
        return when {
            code == "42P01" || code == "PGRST205" ||
                message.contains(CUSTOM_INSTRUCTIONS_TABLE, true) &&
                message.contains("schema cache", true) ->
                "Supabase 自定义指令表尚未建立，请执行个性化升级 SQL。"
            status == 401 -> "登录状态已失效，请重新登录。"
            status == 403 || message.contains("row-level security", true) ||
                message.contains("policy", true) ->
                "Supabase 自定义指令权限尚未配置，请检查 RLS 策略。"
            message.isNotBlank() -> message
            else -> "自定义指令同步失败：HTTP $status"
        }
    }
}

internal fun normalizeCustomInstructions(value: String): String {
    val withoutControl = value
        .replace(CUSTOM_INSTRUCTIONS_CONTROL_CHAR_REGEX, " ")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    return withoutControl
        .lineSequence()
        .map { line -> line.replace(CUSTOM_INSTRUCTIONS_INLINE_SPACE_REGEX, " ").trimEnd() }
        .joinToString("\n")
        .replace(CUSTOM_INSTRUCTIONS_EXCESS_BLANK_LINES_REGEX, "\n\n\n")
        .trim()
}

internal fun customInstructionsCharacterCount(value: String): Int {
    return value.codePointCount(0, value.length)
}

private fun customInstructionsTooLongMessage(characterCount: Int): String {
    return "自定义指令最多 ${ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH} 个字符，当前 $characterCount 个；请精简后再保存。"
}

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

private fun Throwable.friendlyCustomInstructionsMessage(): String {
    return message.orEmpty().trim().ifBlank {
        "自定义指令暂时无法同步，请稍后再试。"
    }
}
