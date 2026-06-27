package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.service.SupabaseAuthClient
import com.yuchen.ailedger.service.SupabaseUserSession
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
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

private const val WATCHLIST_TABLE = "stock_watchlist"
private const val WATCHLIST_CONNECT_TIMEOUT_MS = 12_000
private const val WATCHLIST_READ_TIMEOUT_MS = 18_000
private const val WATCHLIST_MAX_ITEMS = 300

private const val GUEST_WATCHLIST_PREFERENCES = "stock_native_watchlist"
private const val GUEST_WATCHLIST_KEY = "items"
private const val ACCOUNT_WATCHLIST_PREFERENCES = "stock_account_watchlist"
private const val ACCOUNT_CACHE_PREFIX = "cache_"
private const val LOCAL_MIGRATION_OWNER_KEY = "local_migration_owner_user_id"

data class StockWatchlistItem(
    val code: String,
    val name: String,
    val market: String = "",
    val sortOrder: Int = 0
)

data class StockWatchlistState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val accountUserId: String? = null,
    val accountEmail: String? = null,
    val cloudReady: Boolean = false,
    val items: List<StockWatchlistItem> = emptyList(),
    val message: String = "自选股仅保存在当前设备。",
    val error: Boolean = false
) {
    val isLoggedIn: Boolean
        get() = accountUserId != null

    val statusLabel: String
        get() = when {
            loading -> "正在同步账号自选"
            saving -> "正在保存账号自选"
            cloudReady -> "已同步到账号"
            isLoggedIn -> "云同步未就绪"
            else -> "保存在当前设备"
        }
}

class StockWatchlistRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val authRepository = SupabaseAuthRepository.get(appContext)
    private val guestPreferences = appContext.getSharedPreferences(
        GUEST_WATCHLIST_PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val accountPreferences = appContext.getSharedPreferences(
        ACCOUNT_WATCHLIST_PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val client = SupabaseStockWatchlistClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()

    private val _state = MutableStateFlow(
        StockWatchlistState(items = loadGuestWatchlist())
    )
    val state: StateFlow<StockWatchlistState> = _state.asStateFlow()

    @Volatile
    private var currentSession: SupabaseUserSession? = null

    init {
        scope.launch {
            authRepository.state.collectLatest { accountState ->
                val session = accountState.session?.takeIf { accountState.isLoggedIn }
                operationMutex.withLock {
                    if (session == null) {
                        currentSession = null
                        _state.value = StockWatchlistState(
                            items = loadGuestWatchlist(),
                            message = "未登录，自选股仅保存在当前设备。"
                        )
                        return@withLock
                    }

                    val userChanged = currentSession?.userId != session.userId
                    currentSession = session
                    if (userChanged || _state.value.accountUserId == null) {
                        loadAccountWatchlistLocked(session)
                    }
                }
            }
        }
    }

    fun refresh() {
        scope.launch {
            operationMutex.withLock {
                val session = currentSession
                if (session == null) {
                    _state.value = StockWatchlistState(
                        items = loadGuestWatchlist(),
                        message = "本机自选股已刷新。"
                    )
                } else {
                    loadAccountWatchlistLocked(session)
                }
            }
        }
    }

    fun toggle(code: String, name: String, market: String) {
        val item = normalizeWatchlistItem(code, name, market) ?: return
        scope.launch {
            operationMutex.withLock {
                val session = currentSession
                when {
                    session == null -> toggleGuestLocked(item)
                    _state.value.cloudReady -> toggleCloudLocked(session, item)
                    canUseGuestFallback() -> toggleGuestFallbackLocked(session, item)
                    else -> _state.value = _state.value.copy(
                        message = "账号自选暂时无法连接云端，本次未修改。",
                        error = true
                    )
                }
            }
        }
    }

    fun remove(code: String) {
        val cleanCode = normalizeStockCode(code) ?: return
        scope.launch {
            operationMutex.withLock {
                val session = currentSession
                when {
                    session == null -> removeGuestLocked(cleanCode)
                    _state.value.cloudReady -> removeCloudLocked(session, cleanCode)
                    canUseGuestFallback() -> removeGuestFallbackLocked(session, cleanCode)
                    else -> _state.value = _state.value.copy(
                        message = "账号自选暂时无法连接云端，本次未修改。",
                        error = true
                    )
                }
            }
        }
    }

    private suspend fun loadAccountWatchlistLocked(session: SupabaseUserSession) {
        val migrationOwner = localMigrationOwner()
        val guestItems = loadGuestWatchlist()
        val cachedItems = loadAccountCache(session.userId)
        val canMigrateGuest = migrationOwner == null && guestItems.isNotEmpty()
        val fallbackItems = when {
            cachedItems.isNotEmpty() -> cachedItems
            canMigrateGuest -> guestItems
            else -> emptyList()
        }

        _state.value = StockWatchlistState(
            loading = true,
            accountUserId = session.userId,
            accountEmail = session.email,
            cloudReady = false,
            items = fallbackItems,
            message = "正在同步该账号的自选股…"
        )

        try {
            if (canMigrateGuest) {
                client.upsertAll(session, guestItems)
            }
            val cloudItems = client.list(session)
            if (canMigrateGuest) setLocalMigrationOwner(session.userId)
            saveAccountCache(session.userId, cloudItems)
            _state.value = StockWatchlistState(
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = true,
                items = cloudItems,
                message = when {
                    canMigrateGuest -> "本机自选已合并到当前账号。"
                    cloudItems.isEmpty() -> "当前账号还没有自选股。"
                    else -> "已同步 ${cloudItems.size} 只账号自选。"
                }
            )
        } catch (error: Throwable) {
            _state.value = StockWatchlistState(
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = false,
                items = fallbackItems,
                message = error.friendlyWatchlistMessage(),
                error = true
            )
        }
    }

    private fun toggleGuestLocked(item: StockWatchlistItem) {
        val current = loadGuestWatchlist()
        val next = if (current.any { it.code == item.code }) {
            current.filterNot { it.code == item.code }
        } else {
            listOf(item) + current
        }
        saveGuestWatchlist(next)
        _state.value = StockWatchlistState(
            items = next,
            message = if (next.any { it.code == item.code }) {
                "已加入本机自选。"
            } else {
                "已从本机自选移除。"
            }
        )
    }

    private fun removeGuestLocked(code: String) {
        val next = loadGuestWatchlist().filterNot { it.code == code }
        saveGuestWatchlist(next)
        _state.value = StockWatchlistState(
            items = next,
            message = "已从本机自选移除。"
        )
    }

    private fun toggleGuestFallbackLocked(
        session: SupabaseUserSession,
        item: StockWatchlistItem
    ) {
        val current = loadGuestWatchlist()
        val next = if (current.any { it.code == item.code }) {
            current.filterNot { it.code == item.code }
        } else {
            listOf(item) + current
        }
        saveGuestWatchlist(next)
        _state.value = _state.value.copy(
            accountUserId = session.userId,
            accountEmail = session.email,
            items = next,
            message = "云端表尚未接通，改动暂存在本机；接通后会合并到当前账号。",
            error = true
        )
    }

    private fun removeGuestFallbackLocked(session: SupabaseUserSession, code: String) {
        val next = loadGuestWatchlist().filterNot { it.code == code }
        saveGuestWatchlist(next)
        _state.value = _state.value.copy(
            accountUserId = session.userId,
            accountEmail = session.email,
            items = next,
            message = "云端表尚未接通，改动暂存在本机；接通后会合并到当前账号。",
            error = true
        )
    }

    private suspend fun toggleCloudLocked(
        session: SupabaseUserSession,
        item: StockWatchlistItem
    ) {
        val removing = _state.value.items.any { it.code == item.code }
        _state.value = _state.value.copy(
            saving = true,
            message = if (removing) "正在移除账号自选…" else "正在保存账号自选…",
            error = false
        )
        try {
            if (removing) {
                client.delete(session, item.code)
            } else {
                client.upsert(session, item)
            }
            refreshCloudStateLocked(
                session,
                if (removing) "已从账号自选移除。" else "已同步到账号自选。"
            )
        } catch (error: Throwable) {
            _state.value = _state.value.copy(
                saving = false,
                message = error.friendlyWatchlistMessage(),
                error = true
            )
        }
    }

    private suspend fun removeCloudLocked(session: SupabaseUserSession, code: String) {
        if (_state.value.items.none { it.code == code }) return
        _state.value = _state.value.copy(
            saving = true,
            message = "正在移除账号自选…",
            error = false
        )
        try {
            client.delete(session, code)
            refreshCloudStateLocked(session, "已从账号自选移除。")
        } catch (error: Throwable) {
            _state.value = _state.value.copy(
                saving = false,
                message = error.friendlyWatchlistMessage(),
                error = true
            )
        }
    }

    private suspend fun refreshCloudStateLocked(
        session: SupabaseUserSession,
        message: String
    ) {
        val items = client.list(session)
        saveAccountCache(session.userId, items)
        _state.value = StockWatchlistState(
            accountUserId = session.userId,
            accountEmail = session.email,
            cloudReady = true,
            items = items,
            message = message
        )
    }

    private fun canUseGuestFallback(): Boolean = localMigrationOwner() == null

    private fun loadGuestWatchlist(): List<StockWatchlistItem> {
        return parseWatchlistJson(
            guestPreferences.getString(GUEST_WATCHLIST_KEY, "[]").orEmpty()
        )
    }

    private fun saveGuestWatchlist(items: List<StockWatchlistItem>) {
        guestPreferences.edit()
            .putString(GUEST_WATCHLIST_KEY, watchlistToJson(items).toString())
            .apply()
    }

    private fun loadAccountCache(userId: String): List<StockWatchlistItem> {
        return parseWatchlistJson(
            accountPreferences.getString(ACCOUNT_CACHE_PREFIX + userId, "[]").orEmpty()
        )
    }

    private fun saveAccountCache(userId: String, items: List<StockWatchlistItem>) {
        accountPreferences.edit()
            .putString(ACCOUNT_CACHE_PREFIX + userId, watchlistToJson(items).toString())
            .apply()
    }

    private fun localMigrationOwner(): String? {
        return accountPreferences.getString(LOCAL_MIGRATION_OWNER_KEY, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun setLocalMigrationOwner(userId: String) {
        accountPreferences.edit().putString(LOCAL_MIGRATION_OWNER_KEY, userId).apply()
    }

    companion object {
        @Volatile
        private var instance: StockWatchlistRepository? = null

        fun get(context: Context): StockWatchlistRepository {
            return instance ?: synchronized(this) {
                instance ?: StockWatchlistRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}

private class SupabaseStockWatchlistClient(
    private val supabaseUrl: String = SupabaseAuthClient.DEFAULT_SUPABASE_URL,
    private val publishableKey: String = SupabaseAuthClient.DEFAULT_SUPABASE_PUBLISHABLE_KEY
) {
    fun list(session: SupabaseUserSession): List<StockWatchlistItem> {
        val response = request(
            session = session,
            path = "/rest/v1/$WATCHLIST_TABLE?select=symbol,display_name,market,sort_order&user_id=eq.${session.userId.urlEncode()}&order=sort_order.asc,updated_at.desc&limit=$WATCHLIST_MAX_ITEMS",
            method = "GET"
        )
        return parseWatchlistJson(response)
    }

    fun upsert(session: SupabaseUserSession, item: StockWatchlistItem) {
        upsertAll(session, listOf(item))
    }

    fun upsertAll(session: SupabaseUserSession, items: List<StockWatchlistItem>) {
        if (items.isEmpty()) return
        val body = JSONArray()
        items.take(WATCHLIST_MAX_ITEMS).forEachIndexed { index, item ->
            body.put(
                JSONObject()
                    .put("user_id", session.userId)
                    .put("symbol", item.code)
                    .put("display_name", item.name)
                    .put("market", item.market)
                    .put("sort_order", item.sortOrder.takeIf { it >= 0 } ?: index)
            )
        }
        request(
            session = session,
            path = "/rest/v1/$WATCHLIST_TABLE?on_conflict=user_id,symbol",
            method = "POST",
            body = body.toString(),
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

    fun delete(session: SupabaseUserSession, code: String) {
        request(
            session = session,
            path = "/rest/v1/$WATCHLIST_TABLE?user_id=eq.${session.userId.urlEncode()}&symbol=eq.${code.urlEncode()}",
            method = "DELETE"
        )
    }

    private fun request(
        session: SupabaseUserSession,
        path: String,
        method: String,
        body: String? = null,
        prefer: String? = null
    ): String {
        val base = supabaseUrl.trim().trimEnd('/')
        if (base.isBlank() || publishableKey.isBlank()) {
            throw IOException("Supabase 尚未配置完整。")
        }
        val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = WATCHLIST_CONNECT_TIMEOUT_MS
            readTimeout = WATCHLIST_READ_TIMEOUT_MS
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
                    output.write(payload.toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val text = if (status in 200..299) {
                connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            if (status !in 200..299) throw IOException(translateWatchlistError(text, status))
            text.ifBlank { "[]" }
        } finally {
            connection.disconnect()
        }
    }

    private fun translateWatchlistError(raw: String, status: Int): String {
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val code = json?.optString("code").orEmpty()
        val message = json?.let { value ->
            value.optString("message")
                .ifBlank { value.optString("hint") }
                .ifBlank { value.optString("details") }
        }.orEmpty().ifBlank { raw.trim() }
        return when {
            code == "42P01" || code == "PGRST205" ||
                message.contains(WATCHLIST_TABLE, ignoreCase = true) &&
                message.contains("schema cache", ignoreCase = true) ->
                "Supabase 自选股表尚未建立，当前继续使用本机自选。"
            status == 401 -> "登录状态已失效，请重新登录。"
            status == 403 || message.contains("row-level security", true) ||
                message.contains("policy", true) ->
                "Supabase 自选股权限尚未配置，请检查 RLS 策略。"
            message.isNotBlank() -> message
            else -> "自选股同步失败：HTTP $status"
        }
    }
}

internal fun normalizeWatchlistItems(
    items: List<StockWatchlistItem>
): List<StockWatchlistItem> {
    return items.asSequence()
        .mapNotNull { normalizeWatchlistItem(it.code, it.name, it.market, it.sortOrder) }
        .distinctBy { it.code }
        .take(WATCHLIST_MAX_ITEMS)
        .mapIndexed { index, item -> item.copy(sortOrder = index) }
        .toList()
}

private fun normalizeWatchlistItem(
    code: String,
    name: String,
    market: String,
    sortOrder: Int = 0
): StockWatchlistItem? {
    val cleanCode = normalizeStockCode(code) ?: return null
    return StockWatchlistItem(
        code = cleanCode,
        name = name.trim().take(40).ifBlank { cleanCode },
        market = market.trim().take(20),
        sortOrder = sortOrder.coerceAtLeast(0)
    )
}

private fun normalizeStockCode(code: String): String? {
    val clean = code.trim().uppercase().filter { it.isLetterOrDigit() }
    return clean.takeIf { it.length == 6 }
}

private fun parseWatchlistJson(raw: String): List<StockWatchlistItem> {
    return runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val code = item.optString("symbol").ifBlank { item.optString("code") }
                normalizeWatchlistItem(
                    code = code,
                    name = item.optString("display_name").ifBlank { item.optString("name") },
                    market = item.optString("market"),
                    sortOrder = item.optInt("sort_order", index)
                )?.let(::add)
            }
        }
    }.getOrDefault(emptyList())
        .let(::normalizeWatchlistItems)
}

private fun watchlistToJson(items: List<StockWatchlistItem>): JSONArray {
    return JSONArray().apply {
        normalizeWatchlistItems(items).forEach { item ->
            put(
                JSONObject()
                    .put("code", item.code)
                    .put("name", item.name)
                    .put("market", item.market)
                    .put("sort_order", item.sortOrder)
            )
        }
    }
}

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

private fun Throwable.friendlyWatchlistMessage(): String {
    return message.orEmpty().trim().ifBlank { "自选股暂时无法同步，请稍后再试。" }
}
