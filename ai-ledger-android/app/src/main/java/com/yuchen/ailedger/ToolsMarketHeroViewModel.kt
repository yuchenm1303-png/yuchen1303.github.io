package com.yuchen.ailedger

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import com.yuchen.ailedger.data.StockHttpClient
import com.yuchen.ailedger.model.StockMinutePoint
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class ToolsMarketIndexSpec(
    val code: String,
    val name: String
)

private val TOOLS_MARKET_INDEX_SPECS = listOf(
    ToolsMarketIndexSpec(code = "000001", name = "上证指数"),
    ToolsMarketIndexSpec(code = "399001", name = "深证成指"),
    ToolsMarketIndexSpec(code = "399006", name = "创业板指")
)

@Immutable
data class ToolsMarketIndexItem(
    val code: String,
    val name: String,
    val price: String = "--",
    val changeAmount: String = "--",
    val changePercent: String = "--",
    val previousClose: Float = 0f,
    val minutePoints: List<StockMinutePoint> = emptyList(),
    val updatedAt: String = ""
) {
    val hasRealQuote: Boolean
        get() = price.isNotBlank() && price != "--"

    val hasRealTrend: Boolean
        get() = minutePoints.size >= 2

    val isRising: Boolean
        get() = !changePercent.trim().startsWith("-")
}

@Immutable
data class ToolsMarketHeroUiState(
    val indices: List<ToolsMarketIndexItem> = defaultToolsMarketIndices(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val lastSuccessfulRefreshMs: Long = 0L
)

private fun defaultToolsMarketIndices(): List<ToolsMarketIndexItem> =
    TOOLS_MARKET_INDEX_SPECS.map { spec ->
        ToolsMarketIndexItem(code = spec.code, name = spec.name)
    }

/**
 * 功能页顶部三大指数的应用级数据源。
 *
 * 初始化只建立极轻量状态；本地缓存恢复固定在 IO 线程，网络刷新等功能页入场动画稳定后再开始。
 * 四路结果仍可独立上屏，但磁盘缓存统一防抖合并写入，不再为每一路结果重复构造完整 JSON。
 */
internal object ToolsMarketHeroStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(ToolsMarketHeroUiState())
    val uiState: StateFlow<ToolsMarketHeroUiState> = state

    private val lifecycleLock = Any()
    private var appContext: Context? = null
    private var restoreJob: Job? = null
    private var visibilityRefreshJob: Job? = null
    private var refreshJob: Job? = null
    private var retryJob: Job? = null
    private var persistJob: Job? = null
    private var initialized = false
    private var screenVisible = false
    private var visibleRetryCount = 0
    private var lastAttemptEpochMs = 0L

    fun initialize(context: Context) {
        if (initialized) return
        val applicationContext = context.applicationContext
        synchronized(lifecycleLock) {
            if (initialized) return
            appContext = applicationContext
            initialized = true
            restoreJob = scope.launch {
                val restored = restorePersistedItems(applicationContext)
                val restoredAt = preferences(applicationContext).getLong(PREFS_KEY_UPDATED_AT, 0L)
                state.update { current ->
                    current.copy(
                        indices = mergeKeepingExisting(
                            current = mergeWithDefaults(restored),
                            incoming = current.indices,
                        ),
                        lastSuccessfulRefreshMs = maxOf(
                            current.lastSuccessfulRefreshMs,
                            restoredAt,
                        ),
                    )
                }
                synchronized(lifecycleLock) { restoreJob = null }
            }
        }
    }

    fun prewarm(context: Context) {
        initialize(context)
        requestRefresh(force = false)
    }

    fun setVisible(context: Context, value: Boolean) {
        initialize(context)
        synchronized(lifecycleLock) {
            screenVisible = value
            visibilityRefreshJob?.cancel()
            visibilityRefreshJob = null
            if (value) {
                visibleRetryCount = 0
                visibilityRefreshJob = scope.launch {
                    delay(VISIBLE_REFRESH_SETTLE_MS)
                    val stillVisible = synchronized(lifecycleLock) { screenVisible }
                    if (stillVisible) requestRefresh(force = false)
                }
            } else {
                retryJob?.cancel()
                retryJob = null
            }
        }
    }

    fun refresh(context: Context, force: Boolean = true) {
        initialize(context)
        requestRefresh(force = force)
    }

    private fun requestRefresh(force: Boolean) {
        val context = appContext ?: return
        synchronized(lifecycleLock) {
            if (refreshJob?.isActive == true) return
            val now = System.currentTimeMillis()
            val current = state.value
            val complete = current.indices.all { it.hasRealQuote && it.hasRealTrend }
            if (
                !force &&
                complete &&
                current.lastSuccessfulRefreshMs > 0L &&
                now - current.lastSuccessfulRefreshMs < REFRESH_TTL_MS
            ) {
                return
            }
            if (!force && now - lastAttemptEpochMs < FAILURE_RETRY_COOLDOWN_MS) return
            lastAttemptEpochMs = now
            refreshJob = scope.launch {
                try {
                    runRefresh(context)
                } finally {
                    finishRefresh()
                }
            }
        }
    }

    private suspend fun runRefresh(context: Context) {
        val hadUsableData = state.value.indices.any { it.hasRealQuote || it.hasRealTrend }
        state.update {
            it.copy(
                loading = !hadUsableData,
                errorMessage = null
            )
        }

        val anyNetworkSuccess = AtomicBoolean(false)
        val networkErrors = ConcurrentHashMap<String, Throwable>()
        val missingTrendRoutes = ConcurrentHashMap.newKeySet<String>()

        coroutineScope {
            val jobs = mutableListOf<Job>()
            jobs += launch {
                runCatching { loadQuotes() }
                    .onSuccess { quotes ->
                        if (quotes.isNotEmpty()) {
                            anyNetworkSuccess.set(true)
                            state.update { current ->
                                current.copy(
                                    indices = mergeQuoteItems(current.indices, quotes),
                                    loading = false,
                                    errorMessage = null
                                )
                            }
                            schedulePersist(context)
                        }
                    }
                    .onFailure { networkErrors["quotes"] = it }
            }
            TOOLS_MARKET_INDEX_SPECS.forEach { spec ->
                jobs += launch {
                    runCatching { loadTrend(spec) }
                        .onSuccess { trend ->
                            if (trend.hasRealTrend) {
                                anyNetworkSuccess.set(true)
                                state.update { current ->
                                    current.copy(
                                        indices = mergeTrendItem(current.indices, trend),
                                        loading = false,
                                        errorMessage = null
                                    )
                                }
                                schedulePersist(context)
                            }
                        }
                        .onFailure { error ->
                            networkErrors["trend:${spec.code}"] = error
                            if (isRouteUnavailable(error)) missingTrendRoutes += spec.code
                        }
                }
            }
            jobs.joinAll()
        }

        if (missingTrendRoutes.size == TOOLS_MARKET_INDEX_SPECS.size) {
            runCatching { loadBatchFallback() }
                .onSuccess { items ->
                    if (items.isNotEmpty()) {
                        anyNetworkSuccess.set(true)
                        state.update { current ->
                            current.copy(
                                indices = mergeKeepingExisting(current.indices, items),
                                loading = false,
                                errorMessage = null
                            )
                        }
                        schedulePersist(context)
                    }
                }
                .onFailure { networkErrors["batchFallback"] = it }
        }

        val finishedAt = System.currentTimeMillis()
        state.update { current ->
            val usable = current.indices.any { it.hasRealQuote || it.hasRealTrend }
            current.copy(
                loading = false,
                errorMessage = if (usable) null else {
                    networkErrors.values.firstOrNull()?.message ?: "三大指数行情暂不可用"
                },
                lastSuccessfulRefreshMs = if (anyNetworkSuccess.get()) {
                    finishedAt
                } else {
                    current.lastSuccessfulRefreshMs
                }
            )
        }
        if (anyNetworkSuccess.get()) {
            preferences(context).edit()
                .putLong(PREFS_KEY_UPDATED_AT, finishedAt)
                .apply()
            schedulePersist(context, immediate = true)
        }
    }

    private fun finishRefresh() {
        var retryDelayMs = 0L
        synchronized(lifecycleLock) {
            refreshJob = null
            val complete = state.value.indices.all { it.hasRealQuote && it.hasRealTrend }
            if (complete) {
                visibleRetryCount = 0
                retryJob?.cancel()
                retryJob = null
            } else if (screenVisible && visibleRetryCount < MAX_VISIBLE_RETRIES) {
                visibleRetryCount += 1
                retryDelayMs = VISIBLE_RETRY_BASE_MS * visibleRetryCount
            }
        }
        if (retryDelayMs <= 0L) return
        synchronized(lifecycleLock) {
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(retryDelayMs)
                requestRefresh(force = true)
            }
        }
    }

    /** 第一阶段：专用路由只取三大指数报价，不等待任何分时曲线。 */
    private fun loadQuotes(): List<ToolsMarketIndexItem> {
        val body = StockHttpClient.get(
            url = "$PROXY_BASE_URL$QUOTES_PATH",
            timeoutMs = QUOTES_TIMEOUT_MS,
            emptyMessage = "三大指数报价返回为空",
            microCacheMs = QUOTE_MICRO_CACHE_MS
        )
        val rows = JSONObject(body).optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val code = text(item, "code")
                val spec = TOOLS_MARKET_INDEX_SPECS.firstOrNull { it.code == code } ?: continue
                val price = text(item, "price", "value", "last")
                if (price.isBlank() || price == "--") continue
                add(
                    ToolsMarketIndexItem(
                        code = spec.code,
                        name = text(item, "name").ifBlank { spec.name },
                        price = price,
                        changeAmount = text(item, "changeAmount", "change").ifBlank { "--" },
                        changePercent = text(item, "changePercent", "pct").ifBlank { "--" },
                        previousClose = number(item, "previousClose", "preClose") ?: 0f,
                        updatedAt = text(item, "updatedAt")
                    )
                )
            }
        }
    }

    /** 第二阶段：每条曲线独立请求、独立写入，最慢的一条不阻塞另外两条上屏。 */
    private fun loadTrend(spec: ToolsMarketIndexSpec): ToolsMarketIndexItem {
        val body = StockHttpClient.get(
            url = "$PROXY_BASE_URL$TREND_PATH?query=${spec.code}",
            timeoutMs = TREND_TIMEOUT_MS,
            emptyMessage = "${spec.name}分时返回为空",
            microCacheMs = TREND_MICRO_CACHE_MS
        )
        val root = JSONObject(body)
        return ToolsMarketIndexItem(
            code = spec.code,
            name = text(root, "name").ifBlank { spec.name },
            minutePoints = compactMinutePoints(parseMinutePoints(root.optJSONArray("minutePoints"))),
            updatedAt = text(root, "updatedAt")
        )
    }

    /** 只用于新 APK 与旧后端短暂错配，后台兜底，不阻塞报价首屏。 */
    private fun loadBatchFallback(): List<ToolsMarketIndexItem> {
        val body = StockHttpClient.get(
            url = "$PROXY_BASE_URL$BATCH_PATH",
            timeoutMs = BATCH_FALLBACK_TIMEOUT_MS,
            emptyMessage = "三大指数批量行情返回为空",
            microCacheMs = TREND_MICRO_CACHE_MS
        )
        return parseBatch(JSONObject(body))
    }

    private fun parseBatch(root: JSONObject): List<ToolsMarketIndexItem> {
        val rows = root.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val code = text(item, "code")
                val spec = TOOLS_MARKET_INDEX_SPECS.firstOrNull { it.code == code } ?: continue
                val quote = item.optJSONObject("quote") ?: JSONObject()
                val parsed = ToolsMarketIndexItem(
                    code = spec.code,
                    name = text(item, "name")
                        .ifBlank { text(quote, "name") }
                        .ifBlank { spec.name },
                    price = text(quote, "price", "last").ifBlank { "--" },
                    changeAmount = text(quote, "changeAmount", "change").ifBlank { "--" },
                    changePercent = text(quote, "changePercent", "pct").ifBlank { "--" },
                    previousClose = number(quote, "previousClose", "preClose") ?: 0f,
                    minutePoints = compactMinutePoints(
                        parseMinutePoints(item.optJSONArray("minutePoints"))
                    ),
                    updatedAt = text(item, "updatedAt")
                )
                if (parsed.hasRealQuote || parsed.hasRealTrend) add(parsed)
            }
        }
    }

    private fun parseMinutePoints(array: JSONArray?): List<StockMinutePoint> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val price = number(item, "price", "close") ?: continue
            if (!price.isFinite() || price <= 0f) continue
            val average = number(item, "average", "avg", "avgPrice")
                ?.takeIf { it.isFinite() && it > 0f }
                ?: price
            add(
                StockMinutePoint(
                    time = text(item, "time", "minute", "datetime"),
                    price = price,
                    average = average,
                    volumeRatio = (number(item, "volumeRatio", "ratio") ?: 0.02f)
                        .coerceIn(0.02f, 1f),
                    volume = (number(item, "volume", "vol") ?: 0f).coerceAtLeast(0f)
                )
            )
        }
    }

    private fun compactMinutePoints(points: List<StockMinutePoint>): List<StockMinutePoint> {
        val real = points.filter { it.price.isFinite() && it.price > 0f }
        if (real.size <= MAX_SPARKLINE_POINTS) return real
        val stride = ((real.size + MAX_SPARKLINE_POINTS - 1) / MAX_SPARKLINE_POINTS)
            .coerceAtLeast(1)
        return buildList {
            real.forEachIndexed { index, point ->
                if (index == 0 || index == real.lastIndex || index % stride == 0) add(point)
            }
        }.takeLast(MAX_SPARKLINE_POINTS)
    }

    private fun mergeQuoteItems(
        current: List<ToolsMarketIndexItem>,
        quotes: List<ToolsMarketIndexItem>
    ): List<ToolsMarketIndexItem> {
        val currentByCode = current.associateBy(ToolsMarketIndexItem::code)
        val quoteByCode = quotes.associateBy(ToolsMarketIndexItem::code)
        return TOOLS_MARKET_INDEX_SPECS.map { spec ->
            val old = currentByCode[spec.code] ?: ToolsMarketIndexItem(spec.code, spec.name)
            val fresh = quoteByCode[spec.code] ?: return@map old
            old.copy(
                name = fresh.name,
                price = fresh.price,
                changeAmount = fresh.changeAmount,
                changePercent = fresh.changePercent,
                previousClose = fresh.previousClose.takeIf { it > 0f } ?: old.previousClose,
                updatedAt = fresh.updatedAt.ifBlank { old.updatedAt }
            )
        }
    }

    private fun mergeTrendItem(
        current: List<ToolsMarketIndexItem>,
        trend: ToolsMarketIndexItem
    ): List<ToolsMarketIndexItem> = current.map { old ->
        if (old.code != trend.code || !trend.hasRealTrend) {
            old
        } else {
            old.copy(
                minutePoints = trend.minutePoints,
                updatedAt = trend.updatedAt.ifBlank { old.updatedAt }
            )
        }
    }

    private fun mergeKeepingExisting(
        current: List<ToolsMarketIndexItem>,
        incoming: List<ToolsMarketIndexItem>
    ): List<ToolsMarketIndexItem> {
        val currentByCode = current.associateBy(ToolsMarketIndexItem::code)
        val incomingByCode = incoming.associateBy(ToolsMarketIndexItem::code)
        return TOOLS_MARKET_INDEX_SPECS.map { spec ->
            val old = currentByCode[spec.code] ?: ToolsMarketIndexItem(spec.code, spec.name)
            val fresh = incomingByCode[spec.code] ?: return@map old
            old.copy(
                name = fresh.name.ifBlank { old.name },
                price = fresh.price.takeIf { fresh.hasRealQuote } ?: old.price,
                changeAmount = fresh.changeAmount.takeIf { fresh.hasRealQuote } ?: old.changeAmount,
                changePercent = fresh.changePercent.takeIf { fresh.hasRealQuote } ?: old.changePercent,
                previousClose = fresh.previousClose.takeIf { it > 0f } ?: old.previousClose,
                minutePoints = fresh.minutePoints.takeIf { fresh.hasRealTrend } ?: old.minutePoints,
                updatedAt = fresh.updatedAt.ifBlank { old.updatedAt }
            )
        }
    }

    private fun mergeWithDefaults(items: List<ToolsMarketIndexItem>): List<ToolsMarketIndexItem> {
        val byCode = items.associateBy(ToolsMarketIndexItem::code)
        return TOOLS_MARKET_INDEX_SPECS.map { spec ->
            byCode[spec.code] ?: ToolsMarketIndexItem(code = spec.code, name = spec.name)
        }
    }

    private fun restorePersistedItems(context: Context): List<ToolsMarketIndexItem> {
        val prefs = preferences(context)
        val stored = prefs.getString(PREFS_KEY_BATCH, null)
            ?: prefs.getString(PREFS_KEY_BATCH_LEGACY, null)
            ?: return emptyList()
        if (stored.isBlank()) return emptyList()
        return runCatching { parseBatch(JSONObject(stored)) }.getOrDefault(emptyList())
    }

    private fun schedulePersist(context: Context, immediate: Boolean = false) {
        synchronized(lifecycleLock) {
            persistJob?.cancel()
            persistJob = scope.launch {
                if (!immediate) delay(PERSIST_DEBOUNCE_MS)
                persistCurrent(context)
                synchronized(lifecycleLock) { persistJob = null }
            }
        }
    }

    private fun persistCurrent(context: Context) {
        val items = state.value.indices
        if (items.none { it.hasRealQuote || it.hasRealTrend }) return
        val root = JSONObject()
        val array = JSONArray()
        items.forEach { item ->
            val quote = JSONObject()
                .put("name", item.name)
                .put("price", item.price)
                .put("changeAmount", item.changeAmount)
                .put("changePercent", item.changePercent)
                .put("previousClose", item.previousClose)
            val minutes = JSONArray()
            item.minutePoints.forEach { point ->
                minutes.put(
                    JSONObject()
                        .put("time", point.time)
                        .put("price", point.price)
                        .put("average", point.average)
                        .put("volumeRatio", point.volumeRatio)
                        .put("volume", point.volume)
                )
            }
            array.put(
                JSONObject()
                    .put("code", item.code)
                    .put("name", item.name)
                    .put("quote", quote)
                    .put("minutePoints", minutes)
                    .put("updatedAt", item.updatedAt)
            )
        }
        root.put("items", array)
        preferences(context).edit()
            .putString(PREFS_KEY_BATCH, root.toString())
            .remove(PREFS_KEY_BATCH_LEGACY)
            .apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun text(obj: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = obj.opt(key)?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value != "null" && value != "NaN") return value
        }
        return ""
    }

    private fun number(obj: JSONObject, vararg keys: String): Float? {
        keys.forEach { key ->
            val value = when (val raw = obj.opt(key)) {
                is Number -> raw.toFloat()
                is String -> raw.replace("%", "").replace(",", "").toFloatOrNull()
                else -> null
            }
            if (value != null && value.isFinite()) return value
        }
        return null
    }

    private fun isRouteUnavailable(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("HTTP 404") || message.contains("HTTP 405")
    }

    private const val PROXY_BASE_URL = "https://ai-ledger-stock-proxy.onrender.com"
    private const val QUOTES_PATH = "/api/stock/a-share/index/compact/quotes"
    private const val TREND_PATH = "/api/stock/a-share/index/compact/trend"
    private const val BATCH_PATH = "/api/stock/a-share/index/compact/batch"
    private const val PREFS_NAME = "tools_market_hero_cache"
    private const val PREFS_KEY_BATCH = "three_indices_v2"
    private const val PREFS_KEY_BATCH_LEGACY = "three_indices_v1"
    private const val PREFS_KEY_UPDATED_AT = "updated_at_epoch_ms"
    private const val QUOTES_TIMEOUT_MS = 2_500
    private const val TREND_TIMEOUT_MS = 4_000
    private const val BATCH_FALLBACK_TIMEOUT_MS = 4_800
    private const val QUOTE_MICRO_CACHE_MS = 8_000L
    private const val TREND_MICRO_CACHE_MS = 10_000L
    private const val REFRESH_TTL_MS = 30_000L
    private const val FAILURE_RETRY_COOLDOWN_MS = 3_000L
    private const val VISIBLE_REFRESH_SETTLE_MS = 360L
    private const val VISIBLE_RETRY_BASE_MS = 1_500L
    private const val PERSIST_DEBOUNCE_MS = 220L
    private const val MAX_VISIBLE_RETRIES = 3
    private const val MAX_SPARKLINE_POINTS = 72
}

class ToolsMarketHeroViewModel(
    application: Application
) : AndroidViewModel(application) {
    val uiState: StateFlow<ToolsMarketHeroUiState> = ToolsMarketHeroStore.uiState

    init {
        ToolsMarketHeroStore.initialize(application)
    }

    fun setVisible(value: Boolean) {
        ToolsMarketHeroStore.setVisible(getApplication(), value)
    }

    fun refreshIfStale(force: Boolean = false) {
        ToolsMarketHeroStore.refresh(getApplication(), force)
    }
}
