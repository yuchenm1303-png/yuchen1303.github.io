package com.yuchen.ailedger

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.StockRealtimeFrame
import com.yuchen.ailedger.data.StockRealtimeRepository
import com.yuchen.ailedger.data.StockRepository
import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.StockKLinePoint
import com.yuchen.ailedger.model.StockMinutePoint
import com.yuchen.ailedger.model.StockTradeTick
import com.yuchen.ailedger.model.sampleAStockDetailUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StockMarketUiState(
    val stock: StockDetailUiState = sampleAStockDetailUiState(),
    val query: String = "600396",
    val loading: Boolean = false,
    val marketLoading: Boolean = false,
    val kLineLoading: Boolean = false,
    val showDetail: Boolean = false,
    val selectedTab: String = "分时",
    val depthTab: String = "五档",
    val isWatched: Boolean = false,
    val activeAction: String? = null,
    val selectedHomeAction: String = "热榜",
    val requestMessage: String? = null
)

class StockMarketViewModel(
    private val repository: StockRepository = StockRepository(),
    private val realtimeRepository: StockRealtimeRepository = StockRealtimeRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(StockMarketUiState(loading = true, marketLoading = true))
    val uiState: StateFlow<StockMarketUiState> = _uiState

    private var quoteJob: Job? = null
    private var marketJob: Job? = null
    private var slowDetailJob: Job? = null
    private var kLineJob: Job? = null
    private var minuteJob: Job? = null
    private var realtimeJob: Job? = null
    private var requestSeq = 0

    private val minuteCache = mutableMapOf<String, List<StockMinutePoint>>()
    private val kLineCache = mutableMapOf<String, List<StockKLinePoint>>()
    private val lastSequenceByStream = mutableMapOf<String, Long>()

    init {
        refreshHome()
    }

    fun updateQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun refreshHome() {
        stopRealtimeLoop()
        slowDetailJob?.cancel()
        loadLite(openDetail = false)
    }

    fun searchAndOpen() {
        loadLite(openDetail = true)
    }

    fun openDetail() {
        val state = _uiState.value
        if (state.stock.errorMessage != null) {
            loadLite(openDetail = true)
            return
        }
        _uiState.update { it.copy(showDetail = true, activeAction = null) }
        val code = state.stock.quote.code
        startRealtimeLoop(code)
        loadSlowDetail(code)
        if (!isMinuteTab(state.selectedTab)) {
            loadKLineForTab(state.selectedTab)
        }
    }

    fun backToHome() {
        stopRealtimeLoop()
        slowDetailJob?.cancel()
        _uiState.update { it.copy(showDetail = false, activeAction = null) }
    }

    fun refreshCurrent() {
        loadLite(openDetail = _uiState.value.showDetail)
    }

    fun selectTab(tab: String) {
        val code = activeCode()
        if (isMinuteTab(tab)) {
            kLineJob?.cancel()
            val days = daysForTab(tab)
            val cached = minuteCache[minuteKey(code, days)]
            _uiState.update {
                it.copy(
                    selectedTab = tab,
                    stock = it.stock.copy(minutePoints = cached ?: emptyList()),
                    requestMessage = if (tab == "五日") {
                        "正在同步真实五日分时"
                    } else {
                        "正在同步真实分时"
                    }
                )
            }
            loadMinute(days)
        } else {
            minuteJob?.cancel()
            val period = periodForTab(tab)
            val cached = kLineCache[kLineKey(code, period)]
            _uiState.update {
                it.copy(
                    selectedTab = tab,
                    stock = it.stock.copy(
                        kLinePoints = cached ?: emptyList(),
                        minutePoints = emptyList()
                    ),
                    requestMessage = "正在加载真实${tab}"
                )
            }
            loadKLineForTab(tab)
        }
    }

    fun selectDepthTab(tab: String) {
        _uiState.update { it.copy(depthTab = tab) }
    }

    fun selectHomeAction(action: String) {
        _uiState.update { it.copy(selectedHomeAction = action) }
    }

    fun handleAction(action: String) {
        _uiState.update { state ->
            when (action) {
                "加自选" -> state.copy(
                    isWatched = !state.isWatched,
                    activeAction = "加自选"
                )
                else -> state.copy(
                    activeAction = if (state.activeAction == action) null else action
                )
            }
        }
    }

    fun closeAction() {
        _uiState.update { it.copy(activeAction = null) }
    }

    fun openCode(code: String) {
        stopRealtimeLoop()
        slowDetailJob?.cancel()
        _uiState.update {
            it.copy(
                query = code,
                selectedTab = "分时",
                requestMessage = null
            )
        }
        loadLite(openDetail = true, forcedQuery = code)
    }

    private fun loadLite(openDetail: Boolean, forcedQuery: String? = null) {
        val seq = ++requestSeq
        val target = (forcedQuery ?: _uiState.value.query)
            .trim()
            .ifBlank { _uiState.value.stock.quote.code }
        quoteJob?.cancel()
        quoteJob = viewModelScope.launch {
            if (openDetail) stopRealtimeLoop()
            _uiState.update {
                it.copy(
                    loading = true,
                    requestMessage = "连接新版A股行情代理中"
                )
            }
            val loaded = withContext(Dispatchers.IO) {
                repository.loadAStock(target, mode = "lite")
            }
            if (seq != requestSeq) return@launch

            seedCaches(loaded)
            _uiState.update { state ->
                state.copy(
                    stock = loaded,
                    query = loaded.quote.code,
                    loading = false,
                    showDetail = if (openDetail) true else state.showDetail,
                    requestMessage = loaded.errorMessage
                )
            }
            loadMarketOverview(loaded.quote.code)
            if (openDetail || _uiState.value.showDetail) {
                startRealtimeLoop(loaded.quote.code)
                loadSlowDetail(loaded.quote.code)
                val tab = _uiState.value.selectedTab
                if (!isMinuteTab(tab)) {
                    loadKLineForTab(tab, loaded.quote.code)
                }
            }
        }
    }

    private fun seedCaches(loaded: StockDetailUiState) {
        val code = loaded.quote.code
        if (code.isBlank()) return
        if (loaded.minutePoints.isNotEmpty()) {
            minuteCache[minuteKey(code, 1)] = loaded.minutePoints
        }
        if (loaded.kLinePoints.isNotEmpty()) {
            kLineCache[kLineKey(code, "daily")] = loaded.kLinePoints
        }
    }

    private fun loadMarketOverview(query: String) {
        marketJob?.cancel()
        marketJob = viewModelScope.launch {
            _uiState.update { it.copy(marketLoading = true) }
            val merged = withContext(Dispatchers.IO) {
                repository.loadMarketOverview(query, _uiState.value.stock)
            }
            _uiState.update { state ->
                if (state.stock.quote.code != query) {
                    state.copy(marketLoading = false)
                } else {
                    state.copy(
                        stock = state.stock.copy(
                            indices = merged.indices,
                            watchlist = merged.watchlist,
                            marketBoards = merged.marketBoards
                        ),
                        marketLoading = false
                    )
                }
            }
        }
    }

    private fun loadSlowDetail(query: String) {
        if (query.isBlank()) return
        slowDetailJob?.cancel()
        slowDetailJob = viewModelScope.launch {
            delay(SLOW_DETAIL_DELAY_MS)
            if (!_uiState.value.showDetail || activeCode() != query) return@launch
            val full = withContext(Dispatchers.IO) {
                repository.loadAStock(query, mode = "full")
            }
            _uiState.update { state ->
                if (!state.showDetail || state.stock.quote.code != query) {
                    state
                } else {
                    val nextStock = state.stock.copy(
                        moneyFlow = full.moneyFlow,
                        fundamentals = full.fundamentals,
                        aiSummary = full.aiSummary
                    )
                    if (nextStock == state.stock) state else state.copy(stock = nextStock)
                }
            }
        }
    }

    private fun loadMinute(days: Int = 1, forcedQuery: String? = null) {
        val target = (forcedQuery ?: activeCode()).ifBlank { _uiState.value.query }
        minuteJob?.cancel()
        minuteJob = viewModelScope.launch {
            val message = if (days >= 5) {
                "正在同步真实五日分时"
            } else {
                "正在同步真实分时"
            }
            _uiState.update { it.copy(kLineLoading = true, requestMessage = message) }
            val current = _uiState.value.stock
            val result = withContext(Dispatchers.IO) {
                realtimeRepository.loadRealtimeFrame(target, current, minuteDays = days)
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { frame ->
                        if (state.stock.quote.code != target) {
                            state
                        } else {
                            val isCurrentView = isMinuteTab(state.selectedTab) &&
                                daysForTab(state.selectedTab) == days
                            val updated = applyRealtimeFrame(
                                state = state,
                                frame = frame,
                                code = target,
                                minuteDays = days,
                                exposeMinutePoints = isCurrentView
                            )
                            if (isCurrentView) {
                                updated.copy(kLineLoading = false, requestMessage = null)
                            } else {
                                updated
                            }
                        }
                    },
                    onFailure = { error ->
                        val isCurrentView = state.stock.quote.code == target &&
                            isMinuteTab(state.selectedTab) &&
                            daysForTab(state.selectedTab) == days
                        if (!isCurrentView) {
                            state
                        } else {
                            state.copy(
                                kLineLoading = false,
                                requestMessage = "${if (days >= 5) "五日分时" else "分时"}加载失败：${error.message ?: error.javaClass.simpleName}"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun loadKLineForTab(tab: String, forcedQuery: String? = null) {
        val target = (forcedQuery ?: activeCode()).ifBlank { _uiState.value.query }
        val period = periodForTab(tab)
        val cacheKey = kLineKey(target, period)
        val cached = kLineCache[cacheKey]
        kLineJob?.cancel()
        kLineJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    kLineLoading = true,
                    stock = it.stock.copy(
                        kLinePoints = cached ?: emptyList(),
                        minutePoints = emptyList()
                    ),
                    requestMessage = "正在加载真实${tab}"
                )
            }
            val result = withContext(Dispatchers.IO) {
                repository.loadKLinePoints(target, period)
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { points ->
                        if (points.size >= 2) {
                            kLineCache[cacheKey] = points
                        }
                        val isCurrentView = state.stock.quote.code == target &&
                            !isMinuteTab(state.selectedTab) &&
                            periodForTab(state.selectedTab) == period
                        if (!isCurrentView) {
                            state
                        } else if (points.size >= 2) {
                            state.copy(
                                stock = state.stock.copy(kLinePoints = points),
                                kLineLoading = false,
                                requestMessage = null
                            )
                        } else {
                            state.copy(
                                stock = state.stock.copy(kLinePoints = cached ?: emptyList()),
                                kLineLoading = false,
                                requestMessage = if (cached != null) {
                                    "${tab}实时数据不足，当前显示本地缓存"
                                } else {
                                    "${tab}接口返回数据不足"
                                }
                            )
                        }
                    },
                    onFailure = { error ->
                        val isCurrentView = state.stock.quote.code == target &&
                            !isMinuteTab(state.selectedTab) &&
                            periodForTab(state.selectedTab) == period
                        if (!isCurrentView) {
                            state
                        } else {
                            state.copy(
                                stock = state.stock.copy(kLinePoints = cached ?: emptyList()),
                                kLineLoading = false,
                                requestMessage = if (cached != null) {
                                    "${tab}刷新失败，当前显示本地缓存"
                                } else {
                                    "${tab}加载失败：${error.message ?: error.javaClass.simpleName}"
                                }
                            )
                        }
                    }
                )
            }
        }
    }

    private fun startRealtimeLoop(code: String) {
        val target = code.ifBlank { activeCode() }.ifBlank { _uiState.value.query }
        if (realtimeJob?.isActive == true && activeCode() == target) return
        realtimeJob?.cancel()
        lastSequenceByStream.remove(minuteKey(target, 1))
        lastSequenceByStream.remove(minuteKey(target, 5))
        realtimeJob = viewModelScope.launch {
            var nextTickAt = SystemClock.elapsedRealtime()
            while (isActive) {
                val waitMs = nextTickAt - SystemClock.elapsedRealtime()
                if (waitMs > 0L) delay(waitMs)
                if (!_uiState.value.showDetail) break

                val beforeRequest = _uiState.value
                val activeCode = beforeRequest.stock.quote.code.ifBlank { target }
                val minuteDays = daysForTab(beforeRequest.selectedTab)
                val current = beforeRequest.stock
                val result = withContext(Dispatchers.IO) {
                    realtimeRepository.loadRealtimeFrame(
                        query = activeCode,
                        current = current,
                        minuteDays = minuteDays
                    )
                }
                result.onSuccess { frame ->
                    if (acceptSequence(activeCode, minuteDays, frame.sequence)) {
                        _uiState.update { state ->
                            if (!state.showDetail || state.stock.quote.code != activeCode) {
                                state
                            } else {
                                val exposeMinutes = isMinuteTab(state.selectedTab) &&
                                    daysForTab(state.selectedTab) == minuteDays
                                applyRealtimeFrame(
                                    state = state,
                                    frame = frame,
                                    code = activeCode,
                                    minuteDays = minuteDays,
                                    exposeMinutePoints = exposeMinutes
                                )
                            }
                        }
                    }
                }

                nextTickAt += REALTIME_INTERVAL_MS
                val now = SystemClock.elapsedRealtime()
                while (nextTickAt <= now) {
                    nextTickAt += REALTIME_INTERVAL_MS
                }
            }
        }
    }

    private fun applyRealtimeFrame(
        state: StockMarketUiState,
        frame: StockRealtimeFrame,
        code: String,
        minuteDays: Int,
        exposeMinutePoints: Boolean
    ): StockMarketUiState {
        val minuteCacheKey = minuteKey(code, minuteDays)
        val previousMinutes = minuteCache[minuteCacheKey].orEmpty()
        val mergedMinutes = mergeMinutePoints(
            previous = previousMinutes,
            incoming = frame.minutePoints,
            isSnapshot = frame.minuteIsSnapshot,
            maxSize = if (minuteDays >= 5) MAX_FIVE_DAY_POINTS else MAX_ONE_DAY_POINTS
        )
        if (mergedMinutes.isNotEmpty()) {
            minuteCache[minuteCacheKey] = mergedMinutes
        }

        val mergedTicks = mergeTradeTicks(
            previous = state.stock.tradeTicks,
            incoming = frame.tradeTicks
        )
        val visibleMinutes = if (exposeMinutePoints) {
            mergedMinutes.ifEmpty { state.stock.minutePoints }
        } else {
            state.stock.minutePoints
        }
        val nextSellLevels = frame.sellLevels.ifEmpty { state.stock.sellLevels }
        val nextBuyLevels = frame.buyLevels.ifEmpty { state.stock.buyLevels }
        val nextTradeTicks = mergedTicks.ifEmpty { state.stock.tradeTicks }
        val nextTopMetrics = if (frame.quote == state.stock.quote) {
            state.stock.topMetrics
        } else {
            realtimeRepository.topMetricsFor(frame.quote)
        }

        if (
            frame.quote == state.stock.quote &&
            nextTopMetrics == state.stock.topMetrics &&
            visibleMinutes == state.stock.minutePoints &&
            nextSellLevels == state.stock.sellLevels &&
            nextBuyLevels == state.stock.buyLevels &&
            nextTradeTicks == state.stock.tradeTicks &&
            frame.dataSourceLabel == state.stock.dataSourceLabel
        ) {
            return state
        }

        return state.copy(
            stock = state.stock.copy(
                quote = frame.quote,
                topMetrics = nextTopMetrics,
                minutePoints = visibleMinutes,
                sellLevels = nextSellLevels,
                buyLevels = nextBuyLevels,
                tradeTicks = nextTradeTicks,
                dataSourceLabel = frame.dataSourceLabel
            )
        )
    }

    private fun mergeMinutePoints(
        previous: List<StockMinutePoint>,
        incoming: List<StockMinutePoint>,
        isSnapshot: Boolean,
        maxSize: Int
    ): List<StockMinutePoint> {
        if (incoming.isEmpty()) return previous
        val source = if (isSnapshot && incoming.size > 1) incoming else previous + incoming
        val merged = LinkedHashMap<String, StockMinutePoint>()
        source.forEachIndexed { index, point ->
            val key = point.time.ifBlank { "index:$index:${point.price}" }
            merged[key] = point
        }
        return merged.values.toList().takeLast(maxSize)
    }

    private fun mergeTradeTicks(
        previous: List<StockTradeTick>,
        incoming: List<StockTradeTick>
    ): List<StockTradeTick> {
        if (incoming.isEmpty()) return previous
        val seen = HashSet<String>()
        return (incoming + previous).filter { tick ->
            seen.add("${tick.time}|${tick.price}|${tick.volume}|${tick.direction}")
        }.take(MAX_TRADE_TICKS)
    }

    private fun acceptSequence(code: String, minuteDays: Int, sequence: Long): Boolean {
        if (sequence <= 0L) return true
        val streamKey = minuteKey(code, minuteDays)
        val previous = lastSequenceByStream[streamKey] ?: 0L
        if (sequence <= previous) return false
        lastSequenceByStream[streamKey] = sequence
        return true
    }

    private fun stopRealtimeLoop() {
        realtimeJob?.cancel()
        realtimeJob = null
    }

    override fun onCleared() {
        stopRealtimeLoop()
        quoteJob?.cancel()
        marketJob?.cancel()
        slowDetailJob?.cancel()
        kLineJob?.cancel()
        minuteJob?.cancel()
        super.onCleared()
    }

    private fun activeCode(): String {
        return _uiState.value.stock.quote.code.ifBlank { _uiState.value.query }
    }

    private fun isMinuteTab(tab: String): Boolean = tab == "分时" || tab == "五日"

    private fun daysForTab(tab: String): Int = if (tab == "五日") 5 else 1

    private fun periodForTab(tab: String): String = when (tab) {
        "周K" -> "weekly"
        "月K" -> "monthly"
        else -> "daily"
    }

    private fun minuteKey(code: String, days: Int): String = "$code:$days"

    private fun kLineKey(code: String, period: String): String = "$code:$period"

    companion object {
        private const val REALTIME_INTERVAL_MS = 1000L
        private const val SLOW_DETAIL_DELAY_MS = 1200L
        private const val MAX_ONE_DAY_POINTS = 600
        private const val MAX_FIVE_DAY_POINTS = 2600
        private const val MAX_TRADE_TICKS = 120
    }
}
