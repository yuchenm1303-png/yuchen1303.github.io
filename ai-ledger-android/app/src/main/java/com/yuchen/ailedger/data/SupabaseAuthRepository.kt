package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.service.SupabaseAuthClient
import com.yuchen.ailedger.service.SupabaseUserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val SESSION_REFRESH_EARLY_SECONDS = 300L
private const val SESSION_REFRESH_RETRY_DELAY_MS = 60_000L

enum class SupabaseAccountMessageTone { Normal, Success, Error }

data class SupabaseAccountState(
    val loading: Boolean = true,
    val session: SupabaseUserSession? = null,
    val message: String = "正在读取登录状态…",
    val tone: SupabaseAccountMessageTone = SupabaseAccountMessageTone.Normal,
    val requiresEmailConfirmation: Boolean = false
) {
    val isLoggedIn: Boolean
        get() = session?.isUsable == true

    val userId: String?
        get() = session?.userId?.takeIf { it.isNotBlank() }

    val email: String?
        get() = session?.email?.takeIf { it.isNotBlank() }
}

class SupabaseAuthRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val sessionStore = SupabaseSessionStore(appContext)
    private val authClient = SupabaseAuthClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()

    private val _state = MutableStateFlow(SupabaseAccountState())
    val state: StateFlow<SupabaseAccountState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        scope.launch { restoreStoredSession() }
    }

    fun signIn(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            updateError("邮箱和密码都要填写。")
            return
        }
        launchOperation("正在登录…") {
            val result = authClient.signInWithPassword(cleanEmail, password)
            val session = result.session ?: error("登录成功但没有拿到会话，请稍后重试。")
            acceptSession(session, result.message)
        }
    }

    fun signUp(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            updateError("邮箱和密码都要填写。")
            return
        }
        launchOperation("正在注册…") {
            val result = authClient.signUp(cleanEmail, password)
            val session = result.session
            if (session != null) {
                acceptSession(session, result.message)
            } else {
                refreshJob?.cancel()
                _state.value = SupabaseAccountState(
                    loading = false,
                    session = null,
                    message = result.message,
                    tone = SupabaseAccountMessageTone.Normal,
                    requiresEmailConfirmation = result.requiresEmailConfirmation
                )
            }
        }
    }

    fun refreshSession() {
        scope.launch {
            operationMutex.withLock {
                refreshCurrentSession(
                    automatic = false,
                    loadingMessage = "正在刷新登录状态…"
                )
            }
        }
    }

    fun signOut() {
        scope.launch {
            operationMutex.withLock {
                val current = _state.value.session
                refreshJob?.cancel()
                _state.value = _state.value.copy(
                    loading = true,
                    message = "正在退出…",
                    tone = SupabaseAccountMessageTone.Normal,
                    requiresEmailConfirmation = false
                )
                runCatching {
                    current?.accessToken
                        ?.takeIf { it.isNotBlank() }
                        ?.let(authClient::signOut)
                }
                sessionStore.clear()
                _state.value = SupabaseAccountState(
                    loading = false,
                    session = null,
                    message = "已退出登录，长期记忆已锁定。",
                    tone = SupabaseAccountMessageTone.Normal
                )
            }
        }
    }

    private suspend fun restoreStoredSession() {
        operationMutex.withLock {
            val stored = sessionStore.load()
            if (stored == null) {
                _state.value = SupabaseAccountState(
                    loading = false,
                    session = null,
                    message = "未登录时仍可本地聊天；登录后可使用长期记忆。",
                    tone = SupabaseAccountMessageTone.Normal
                )
                return
            }

            if (!stored.hasRequiredCredentials) {
                sessionStore.clear()
                _state.value = SupabaseAccountState(
                    loading = false,
                    session = null,
                    message = "本机会话不完整，请重新登录。",
                    tone = SupabaseAccountMessageTone.Error
                )
                return
            }

            if (stored.shouldRefreshSoon()) {
                _state.value = SupabaseAccountState(
                    loading = true,
                    session = stored.takeIf { it.isUsable },
                    message = "正在恢复登录状态…",
                    tone = SupabaseAccountMessageTone.Normal
                )
                refreshCurrentSession(
                    automatic = true,
                    loadingMessage = "正在恢复登录状态…",
                    fallbackSession = stored
                )
            } else {
                acceptSession(stored, "账号已接通，会话会自动刷新。")
            }
        }
    }

    private fun launchOperation(
        loadingMessage: String,
        block: suspend () -> Unit
    ) {
        if (_state.value.loading) return
        scope.launch {
            operationMutex.withLock {
                _state.value = _state.value.copy(
                    loading = true,
                    message = loadingMessage,
                    tone = SupabaseAccountMessageTone.Normal,
                    requiresEmailConfirmation = false
                )
                try {
                    block()
                } catch (error: Throwable) {
                    _state.value = _state.value.copy(
                        loading = false,
                        message = error.friendlyAccountRepositoryMessage(),
                        tone = SupabaseAccountMessageTone.Error,
                        requiresEmailConfirmation = false
                    )
                }
            }
        }
    }

    private suspend fun refreshCurrentSession(
        automatic: Boolean,
        loadingMessage: String,
        fallbackSession: SupabaseUserSession? = _state.value.session
    ) {
        val current = fallbackSession
        val refreshToken = current?.refreshToken.orEmpty()
        if (current == null || refreshToken.isBlank()) {
            sessionStore.clear()
            _state.value = SupabaseAccountState(
                loading = false,
                session = null,
                message = "当前会话缺少刷新令牌，请重新登录。",
                tone = SupabaseAccountMessageTone.Error
            )
            return
        }

        _state.value = _state.value.copy(
            loading = !automatic,
            session = current.takeIf { it.isUsable },
            message = loadingMessage,
            tone = SupabaseAccountMessageTone.Normal,
            requiresEmailConfirmation = false
        )

        try {
            val result = authClient.refreshSession(refreshToken)
            val nextSession = result.session ?: current
            acceptSession(
                nextSession,
                if (automatic) "账号已接通，会话会自动刷新。" else result.message
            )
        } catch (error: Throwable) {
            if (current.isExpired()) {
                sessionStore.clear()
                refreshJob?.cancel()
                _state.value = SupabaseAccountState(
                    loading = false,
                    session = null,
                    message = "登录状态已过期，请重新登录。",
                    tone = SupabaseAccountMessageTone.Error
                )
            } else {
                _state.value = SupabaseAccountState(
                    loading = false,
                    session = current,
                    message = if (automatic) {
                        "自动刷新暂时失败，会在稍后重试。"
                    } else {
                        error.friendlyAccountRepositoryMessage()
                    },
                    tone = SupabaseAccountMessageTone.Error
                )
                scheduleRefreshRetry(current)
            }
        }
    }

    private fun acceptSession(session: SupabaseUserSession, message: String) {
        sessionStore.save(session)
        _state.value = SupabaseAccountState(
            loading = false,
            session = session,
            message = message,
            tone = SupabaseAccountMessageTone.Success
        )
        scheduleAutomaticRefresh(session)
    }

    private fun scheduleAutomaticRefresh(session: SupabaseUserSession) {
        refreshJob?.cancel()
        if (session.refreshToken.isBlank()) return
        val nowSeconds = System.currentTimeMillis() / 1000L
        val delaySeconds = (session.expiresAtEpochSeconds - nowSeconds - SESSION_REFRESH_EARLY_SECONDS)
            .coerceAtLeast(1L)
        refreshJob = scope.launch {
            delay(delaySeconds * 1000L)
            operationMutex.withLock {
                refreshCurrentSession(
                    automatic = true,
                    loadingMessage = "正在自动刷新登录状态…",
                    fallbackSession = session
                )
            }
        }
    }

    private fun scheduleRefreshRetry(session: SupabaseUserSession) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(SESSION_REFRESH_RETRY_DELAY_MS)
            operationMutex.withLock {
                refreshCurrentSession(
                    automatic = true,
                    loadingMessage = "正在重试登录状态…",
                    fallbackSession = session
                )
            }
        }
    }

    private fun updateError(message: String) {
        _state.value = _state.value.copy(
            loading = false,
            message = message,
            tone = SupabaseAccountMessageTone.Error,
            requiresEmailConfirmation = false
        )
    }

    companion object {
        @Volatile
        private var instance: SupabaseAuthRepository? = null

        fun get(context: Context): SupabaseAuthRepository {
            return instance ?: synchronized(this) {
                instance ?: SupabaseAuthRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

private fun Throwable.friendlyAccountRepositoryMessage(): String {
    val raw = message.orEmpty().trim()
    return raw.ifBlank { "账号服务暂时不可用，请稍后再试。" }
}
