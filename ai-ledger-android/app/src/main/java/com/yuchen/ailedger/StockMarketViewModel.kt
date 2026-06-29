package com.yuchen.ailedger

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.StockMarketDataRepository
import com.yuchen.ailedger.data.StockRealtimeFrame
import com.yuchen.ailedger.data.StockRealtimeRepository
import com.yuchen.ailedger.data.StockRepository
import com.yuchen.ailedger.model.AppPageActivity
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.StockDepthState
import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.StockKLinePoint
import com.yuchen.ailedger.model.StockMarketHomeSnapshot
import com.yuchen.ailedger.model.StockMinutePoint
import com.yuchen.ailedger.model.StockMoneyFlow
import com.yuchen.ailedger.model.StockSlowDataSnapshot
import com.yuchen.ailedger.model.StockTradeTick
import com.yuchen.ailedger.model.sampleAStockDetailUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class StockMarketUiState(
    val stock: StockDetailUiState = emptyStockState(),
    val marketHome: StockMarketHomeSnapshot = StockMarketHomeSnapshot(),
    val slowData: StockSlowDataSnapshot = StockSlowDataSnapshot(),
    val depthState: StockDepthState = StockDepthState(),
    val query: String = DEFAULT_STOCK_CODE,
    val loading: Boolean = false,
    val marketLoading: Boolean = false,
    val slowDataLoading: Boolean = false,
    val kLineLoading: Boolean = false,
    val showDetail: Boolean = false,
    val selectedTab: String = "分时",
    val depthTab: String = "五档",
    val isWatched: Boolean = false,
    val activeAction: String? = null,
    val selectedHomeAction: String = "热榜",
    val requestMessage: String? = null
)

private const val DEFAULT_STOCK_CODE = "600396"

private fun emptyStockState(code: String = ""): StockDetailUiState {
    val sample = sampleAStockDetailUiState()
    return sample.copy(
        quote = sample.quote.copy(
            name = code,
            code = code,
            market = "",
            price = "--",
            changeAmount = "--",
            changePercent = "--",
            isRising = true,
            previousClose = 0f,
            high = "--",
            low = "--",
            open = "--",
            totalMarketValue = "--",
            floatMarketValue = "--",
            volumeRatio = "--",
            turnoverRate = "--",
            peTtm = "--",
            pb = "--",
            amount = "--",
            popularityRank = "--"
        ),
        topMetrics = emptyList(),
        minutePoints = emptyList(),
        sellLevels = emptyList(),
        buyLevels = emptyList(),
        tradeTicks = emptyList(),
        moneyFlow = StockMoneyFlow("--", "--", "--", "--", "--"),
        fundamentals = emptyList(),
        indices = emptyList(),
        watchlist = emptyList(),
        featureGroups = emptyList(),
        marketBoards = emptyList(),
        aiSummary = "等待真实行情数据",
        kLinePoints = emptyList(),
        dataSourceLabel = "等待真实行情",
        errorMessage = null
    )
}

class StockMarketViewModel(
    private val repository: StockRepository = StockRepository(),
    private val realtimeRepository: StockRealtimeRepository = StockRealtimeRepository(),
    private val marketDataRepository: StockMarketDataRepository = StockMarketDataRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        StockMarketUiState(marketLoading = true)
    )
    val uiState: StateFlow<StockMarketUiState> = _uiState

    private var quoteJob: Job? = null
    private var slowDetailJob: Job? = null
    private var kLineJob: Job? = null
    private var minuteJob: Job? = null
    private var realtimeJob: Job? = null
    private var marketLoopJob: Job? = null
    private var requestSeq = 0
    private var pageActive = AppPageActivity.activeTab.value == AppTab.Tools

    private val minuteCache = mutableMapOf<String, List<StockMinutePoint>>()
    private val kLineCache = mutableMapOf<String, List<StockKLinePoint>>()
    private val lastSequenceByStream = mutableMapOf<String, Long>()
    private val lastSlowLoadAtByCode = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch {
            AppPageActivity.activeTab.collect { tab ->
                setPageActive(tab == AppTab.Tools)
            }
        }
        refreshHome()
    }

    fun updateQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun refreshHome() {
        stopRealtimeLoop()
        slowDetailJob?.cancel()
        quoteJob?.cancel()
        _uiState.update {
            it.copy(
                loading = false,
                showDetail = false,
                slowDataLoading = false,
                activeAction = null,
                requestMessage = null
            )
        }
        stopMarketLoop()
        startMarketLoop()
    }

    fun searchAndOpen() {
        stopMarketLoop()
        loadRealtimeSnapshot(
            openDetail = true,
            forcedQuery = _uiState.value.query,
            replaceContent = true
        )
    }

    fun openDetail() {
        val state = _uiState.value
        if (state.stock.errorMessage != null || state.stock.quote.code.isBlank()) {
            stopMarketLoop()
            loadRealtimeSnapshot(
                openDetail = true,
                forcedQuery = state.query,
                replaceContent = true
            )
            return
        }
        stopMarketLoop()
        _uiState.update { it.copy(showDetail = true, activeAction = null, requestMessage = null) }
        val code = state.stock.quote.code
        startRealtimeLoop(code)
        loadSlowDetail(code)
        if (!isMinuteTab(state.selectedTab)) loadKLineForTab(state.selectedTab, code)
    }

    fun backToHome() {
        stopRealtimeLoop()
        slowDetailJob?.cancel()
        minuteJob?.cancel()
        kLineJob?.cancel()
        _uiState.update {
            it.copy(
                showDetail = false,
                loading = false,
                activeAction = null,
                slowDataLoading = false,
                kLineLoading = false,
                depthState = StockDepthState(),
                requestMessage = null
            )
        }
        startMarketLoop()
    }

    fun refreshCurrent() {
        val state = _uiState.value
        if (!state.showDetail) {
            refreshHome()
            return
        }
        val code = activeCode()
        loadRealtimeSnapshot(
            openDetail = true,
            forcedQuery = code,
            replaceContent = false
        )
        if (!isMinuteTab(state.selectedTab)) {
            loadKLineForTab(state.selectedTab, code, force = true)
        }
        loadSlowDetail(code)
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
                    requestMessage = if (tab == "五日") "正在同步真实五日分时" else "正在同步真实分时"
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
        val normalized = code.trim()
        if (normalized.isBlank()) return
        stopRealtimeLoop()
        stopMarketLoop()
        slowDetailJob?.cancel()
        minuteJob?.cancel()
        kLineJob?.cancel()
        _uiState.update {
            it.copy(
                query = normalized,
                selectedTab = "分时",
                showDetail = true,
                depthState = StockDepthState(),
                slowData = StockSlowDataSnapshot(),
                slowDataLoading = false,
                kLineLoading = false,
                requestMessage = null
            )
        }
        loadRealtimeSnapshot(
            openDetail = true,
            forcedQuery = normalized,
            replaceContent = true
        )
    }

    private fun setPageActive(active: Boolean) {
        if (pageActive == active) return
        pageActive = active
        if (!active) {
            stopRealtimeLoop()
            stopMarketLoop()
            slowDetailJob?.cancel()
            minuteJob?.cancel()
            kLineJob?.cancel()
            quoteJob?.cancel()
            return
        }

        val state = _uiState.value
        if (!state.showDetail) {
            startMarketLoop()
            return
        }
        val code = activeCode()
        startRealtimeLoop(code)
        loadSlowDetail(code)
        if (!isMinuteTab(state.selectedTab)) {
            loadKLineForTab(state.selectedTab, code)
        }
    }

    private fun loadRealtimeSnapshot(
        openDetail: Boolean,
        forcedQuery: String? = null,
        replaceContent: Boolean
    ) {
        val seq = ++requestSeq
        val target = (forcedQuery ?: _uiState.value.query)
            .trim()
            .ifBlank { activeCode().ifBlank { DEFAULT_STOCK_CODE } }
        quoteJob?.cancel()
        quoteJob = viewModelScope.launch {
            if (openDetail) stopMarketLoop()
            val before = _uiState.value
            val requestStock = if (replaceContent) emptyStockState(target) else before.stock
            _uiState.update { state ->
                state.copy(
                    stock = if (replaceContent) requestStock else state.stock,
                    loading = true,
                    showDetail = if (openDetail) true else state.showDetail,
                    depthState = if (replaceContent) StockDepthState() else state.depthState,
                    slowData = if (replaceContent) StockSlowDataSnapshot() else state.slowData,
                    slowDataLoading = if (replaceContent) false else state.slowDataLoading,
                    requestMessage = if (openDetail) "正在刷新实时行情" else state.requestMessage
                )
            }

            val minuteDays = daysForTab(_uiState.value.selectedTab)
            val result = withContext(Dispatchers.IO) {
                realtimeRepository.loadRealtimeFrame(target, requestStock, minuteDays)
            }
            if (seq != requestSeq) return@launch

            var resolvedCode = target
            var loadedSuccessfully = false
            _uiState.update { state ->
                result.fold(
                    onSuccess = { frame ->
                        resolvedCode = frame.quote.code.ifBlank { target }
                        if (!acceptSequence(resolvedCode, minuteDays, frame.sequence)) {
                            state.copy(loading = false, requestMessage = null)
                        } else {
                            val baseState = if (
                                replaceContent ||
                                state.stock.quote.code.isBlank() ||
                                state.stock.quote.code != resolvedCode
                            ) {
                                state.copy(
                                    stock = emptyStockState(resolvedCode),
                                    depthState = StockDepthState()
                                )
                            } else {
                                state
                            }
                            val updated = applyRealtimeFrame(
                                state = baseState,
                                frame = frame,
                                code = resolvedCode,
                                minuteDays = minuteDays,
                                exposeMinutePoints = isMinuteTab(baseState.selectedTab)
                            )
                            loadedSuccessfully = true
                            updated.copy(
                                query = resolvedCode,
                                loading = false,
                                showDetail = if (openDetail) true else updated.showDetail,
                                requestMessage = null
                            )
                        }
                    },
                    onFailure = { error ->
                        val message = error.message ?: error.javaClass.simpleName
                        if (replaceContent) {
                            val digits = target.filter(Char::isDigit)
                                .takeIf { it.length == 6 }
                                .orEmpty()
                            val empty = emptyStockState(digits).copy(
                                quote = emptyStockState(digits).quote.copy(
                                    name = digits.ifBlank { target }
                                ),
                                aiSummary = "真实行情暂不可用，未展示本地示例数据。",
                                dataSourceLabel = "真实行情暂不可用",
                                errorMessage = message
                            )
                            state.copy(
                                stock = empty,
                                loading = false,
                                showDetail = if (openDetail) true else state.showDetail,
                                requestMessage = "行情刷新失败：$message"
                            )
                        } else {
                            state.copy(
                                loading = false,
                                requestMessage = "行情刷新失败：$message"
                            )
                        }
                    }
                )
            }

            if (openDetail && pageActive && loadedSuccessfully) {
                startRealtimeLoop(resolvedCode)
                loadSlowDetail(resolvedCode)
                val tab = _uiState.value.selectedTab
                if (!isMinuteTab(tab)) loadKLineForTab(tab, resolvedCode)
            }
        }
    }

    private fun startMarketLoop() {
        if (!pageActive || _uiState.value.showDetail) return
        if (marketLoopJob?.isActive == true) return
        marketLoopJob = viewModelScope.launch {
            var firstRound = true
            var warmupRetryIndex = 0
            while (isActive && pageActive && !_uiState.value.showDetail) {
                if (firstRound) _uiState.update { it.copy(marketLoading = true) }
                val result = withContext(Dispatchers.IO) {
                    marketDataRepository.loadMarketHome()
                }
                val warmupComplete = result.getOrNull()?.hasWarmupData() == true
                _uiState.update { state ->
                    result.fold(
                        onSuccess = { snapshot ->
                            state.copy(
                                marketHome = snapshot,
                                stock = state.stock.copy(
                                    indices = snapshot.indices,
                                    marketBoards = snapshot.boards,
                                    watchlist = emptyList()
                                ),
                                marketLoading = false,
                                requestMessage = if (state.requestMessage?.startsWith("市场数据") == true) {
                                    null
                                } else {
                                    state.requestMessage
                                }
                            )
                        },
                        onFailure = { error ->
                            state.copy(
                                marketLoading = false,
                                requestMessage = if (firstRound) {
                                    "市场数据加载失败：${error.message ?: error.javaClass.simpleName}"
                                } else {
                                    state.requestMessage
                                }
                            )
                        }
                    )
                }
                firstRound = false
                if (!pageActive || _uiState.value.showDetail) break

                val nextDelay = if (
                    !warmupComplete && warmupRetryIndex < MARKET_WARMUP_RETRY_DELAYS_MS.size
                ) {
                    MARKET_WARMUP_RETRY_DELAYS_MS[warmupRetryIndex++]
                } else {
                    MARKET_REFRESH_INTERVAL_MS
                }
                delay(nextDelay)
            }
        }
    }

    private fun StockMarketHomeSnapshot.hasWarmupData(): Boolean {
        return indices.isNotEmpty() &&
            marketBreadth.meta.hasRealData &&
            boards.isNotEmpty() &&
            sectors.isNotEmpty()
    }

    private fun stopMarketLoop() {
        marketLoopJob?.cancel()
        marketLoopJob = null
    }

    private fun loadSlowDetail(query: String, force: Boolean = false) {
        if (!pageActive || query.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        val state = _uiState.value
        val visibleSupplementalData = state.stock.quote.code == query && (
            state.slowData.updatedAt.isNotBlank() || state.stock.fundamentals.isNotEmpty()
        )
        val lastLoadedAt = lastSlowLoadAtByCode[query] ?: 0L
        if (!force && visibleSupplementalData && now - lastLoadedAt < SLOW_DETAIL_REFRESH_TTL_MS) {
            return
        }

        slowDetailJob?.cancel()
        slowDetailJob = viewModelScope.launch {
            delay(SLOW_DETAIL_DELAY_MS)
            if (!pageActive || !_uiState.value.showDetail || activeCode() != query) return@launch
            _uiState.update { it.copy(slowDataLoading = true) }
            val result = coroutineScope {
                val full = async(Dispatchers.IO) { repository.loadAStock(query, mode = "full") }
                val slow = async(Dispatchers.IO) { marketDataRepository.loadSlowStock(query) }
                full.await() to slow.await()
            }
            var acceptedRealData = false
            _uiState.update { current ->
                if (!pageActive || !current.showDetail || current.stock.quote.code != query) {
                    current.copy(slowDataLoading = false)
                } else {
                    val full = result.first
                    val slowResult = result.second
                    val slow = slowResult.getOrElse { StockSlowDataSnapshot() }
                    val fullIsReal = full.errorMessage == null &&
                        !full.dataSourceLabel.contains("示例", ignoreCase = true) &&
                        !full.dataSourceLabel.contains("回退", ignoreCase = true)
                    acceptedRealData = fullIsReal || slowResult.isSuccess
                    current.copy(
                        stock = current.stock.copy(
                            moneyFlow = if (fullIsReal) full.moneyFlow else current.stock.moneyFlow,
                            fundamentals = if (fullIsReal) full.fundamentals else current.stock.fundamentals,
                            aiSummary = if (fullIsReal && full.aiSummary.isNotBlank()) {
                                full.aiSummary
                            } else {
                                current.stock.aiSummary
                            }
                        ),
                        slowData = if (slowResult.isSuccess) slow else current.slowData,
                        slowDataLoading = false
                    )
                }
            }
            if (acceptedRealData) {
                lastSlowLoadAtByCode[query] = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun loadMinute(days: Int = 1, forcedQuery: String? = null) {
        if (!pageActive) return
        val target = (forcedQuery ?: activeCode()).ifBlank { _uiState.value.query }
        minuteJob?.cancel()
        minuteJob = viewModelScope.launch {
            val message = if (days >= 5) "正在同步真实五日分时" else "正在同步真实分时"
            _uiState.update { it.copy(kLineLoading = true, requestMessage = message) }
            val current = _uiState.value.stock
            val result = withContext(Dispatchers.IO) {
                realtimeRepository.loadRealtimeFrame(target, current, minuteDays = days)
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { frame ->
                        if (
                            !pageActive ||
                            state.stock.quote.code != target ||
                            !acceptSequence(target, days, frame.sequence)
                        ) {
                            state
                        } else {
                            val currentView = isMinuteTab(state.selectedTab) &&
                                daysForTab(state.selectedTab) == days
                            val updated = applyRealtimeFrame(
                                state,
                                frame,
                                target,
                                days,
                                exposeMinutePoints = currentView
                            )
                            if (currentView) {
                                updated.copy(kLineLoading = false, requestMessage = null)
                            } else {
                                updated
                            }
                        }
                    },
                    onFailure = { error ->
                        val currentView = pageActive &&
                            state.stock.quote.code == target &&
                            isMinuteTab(state.selectedTab) &&
                            daysForTab(state.selectedTab) == days
                        if (!currentView) {
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

    private fun loadKLineForTab(
        tab: String,
        forcedQuery: String? = null,
        force: Boolean = false
    ) {
        if (!pageActive) return
        val target = (forcedQuery ?: activeCode()).ifBlank { _uiState.value.query }
        val period = periodForTab(tab)
        val cacheKey = kLineKey(target, period)
        val cached = kLineCache[cacheKey]
        if (!force && cached != null && cached.size >= 2 && _uiState.value.selectedTab != tab) return
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
                        if (points.size >= 2) kLineCache[cacheKey] = points
                        val currentView = pageActive &&
                            state.stock.quote.code == target &&
                            !isMinuteTab(state.selectedTab) &&
                            periodForTab(state.selectedTab) == period
                        when {
                            !currentView -> state
                            points.size >= 2 -> state.copy(
                                stock = state.stock.copy(kLinePoints = points),
                                kLineLoading = false,
                                requestMessage = null
                            )
                            else -> state.copy(
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
                        val currentView = pageActive &&
                            state.stock.quote.code == target &&
                            !isMinuteTab(state.selectedTab) &&
                            periodForTab(state.selectedTab) == period
                        if (!currentView) {
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
        if (!pageActive) return
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
                if (!pageActive || !_uiState.value.showDetail) break

                val before = _uiState.value
                val activeCode = before.stock.quote.code.ifBlank { target }
                val minuteDays = daysForTab(before.selectedTab)
                val result = withContext(Dispatchers.IO) {
                    realtimeRepository.loadRealtimeFrame(activeCode, before.stock, minuteDays)
                }
                result.onSuccess { frame ->
                    if (pageActive && acceptSequence(activeCode, minuteDays, frame.sequence)) {
                        _uiState.update { state ->
                            if (!pageActive || !state.showDetail || state.stock.quote.code != activeCode) {
                                state
                            } else {
                                val exposeMinutes = isMinuteTab(state.selectedTab) &&
                                    daysForTab(state.selectedTab) == minuteDays
                                applyRealtimeFrame(
                                    state,
                                    frame,
                                    activeCode,
                                    minuteDays,
                                    exposeMinutes
                                )
                            }
                        }
                    }
                }

                nextTickAt += REALTIME_INTERVAL_MS
                val now = SystemClock.elapsedRealtime()
                while (nextTickAt <= now) nextTickAt += REALTIME_INTERVAL_MS
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
        val mergedMinutes = mergeMinutePoints(
            previous = minuteCache[minuteCacheKey].orEmpty(),
            incoming = frame.minutePoints,
            isSnapshot = frame.minuteIsSnapshot,
            maxSize = if (minuteDays >= 5) MAX_FIVE_DAY_POINTS else MAX_ONE_DAY_POINTS
        )
        if (mergedMinutes.isNotEmpty()) minuteCache[minuteCacheKey] = mergedMinutes

        val visibleMinutes = if (exposeMinutePoints) {
            mergedMinutes.ifEmpty { state.stock.minutePoints }
        } else {
            state.stock.minutePoints
        }
        val depthState = depthStateFor(frame)
        val nextSellLevels = if (depthState.canDisplayLevels) frame.sellLevels else emptyList()
        val nextBuyLevels = if (depthState.canDisplayLevels) frame.buyLevels else emptyList()
        val nextTradeTicks = when {
            frame.tradeTicksDerived -> emptyList()
            frame.ticksAreSnapshot -> frame.tradeTicks.takeLast(MAX_TRADE_TICKS)
            frame.tradeTicks.isNotEmpty() -> mergeTradeTicks(state.stock.tradeTicks, frame.tradeTicks)
            else -> state.stock.tradeTicks
        }
        val nextTopMetrics = if (frame.quote == state.stock.quote) {
            state.stock.topMetrics
        } else {
            realtimeRepository.topMetricsFor(frame.quote)
        }
        val sourceLabel = buildString {
            append(frame.dataSourceLabel)
            append(" · 盘口")
            append(depthState.status.displayLabel())
            if (frame.tradeTicksDerived) append(" · 逐笔不可用")
        }

        if (
            frame.quote == state.stock.quote &&
            nextTopMetrics == state.stock.topMetrics &&
            visibleMinutes == state.stock.minutePoints &&
            nextSellLevels == state.stock.sellLevels &&
            nextBuyLevels == state.stock.buyLevels &&
            nextTradeTicks == state.stock.tradeTicks &&
            sourceLabel == state.stock.dataSourceLabel &&
            depthState == state.depthState
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
                dataSourceLabel = sourceLabel,
                errorMessage = null
            ),
            depthState = depthState
        )
    }

    private fun depthStateFor(frame: StockRealtimeFrame): StockDepthState {
        return StockDepthState(
            status = frame.depthStatus,
            source = frame.depthSource,
            isDerived = frame.depthIsDerived,
            updatedAt = frame.depthUpdatedAt,
            sourceTimestamp = frame.depthSourceTimestamp,
            cacheAgeMs = frame.depthCacheAgeMs,
            warnings = frame.depthWarnings
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
            merged[point.time.ifBlank { "index:$index:${point.price}" }] = point
        }
        return merged.values.toList().takeLast(maxSize)
    }

    private fun mergeTradeTicks(
        previous: List<StockTradeTick>,
        incoming: List<StockTradeTick>
    ): List<StockTradeTick> {
        if (incoming.isEmpty()) return previous
        val merged = LinkedHashMap<String, StockTradeTick>()
        (previous + incoming).forEach { tick ->
            merged["${tick.time}|${tick.price}|${tick.volume}|${tick.direction}"] = tick
        }
        return merged.values.toList().takeLast(MAX_TRADE_TICKS)
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
        stopMarketLoop()
        quoteJob?.cancel()
        slowDetailJob?.cancel()
        kLineJob?.cancel()
        minuteJob?.cancel()
        super.onCleared()
    }

    private fun activeCode(): String =
        _uiState.value.stock.quote.code.ifBlank { _uiState.value.query }

    private fun isMinuteTab(tab: String): Boolean = tab == "分时" || tab == "五日"

    private fun daysForTab(tab: String): Int = if (tab == "五日") 5 else 1

    private fun periodForTab(tab: String): String = when (tab) {
        "周K" -> "weekly"
        "月K" -> "monthly"
        else -> "daily"
    }

    private fun minuteKey(code: String, days: Int): String = "$code:$days"

    private fun kLineKey(code: String, period: String): String = "$code:$period"

    private fun com.yuchen.ailedger.model.StockModuleStatus.displayLabel(): String = when (this) {
        com.yuchen.ailedger.model.StockModuleStatus.Ok -> "实时"
        com.yuchen.ailedger.model.StockModuleStatus.Partial -> "部分"
        com.yuchen.ailedger.model.StockModuleStatus.Stale -> "缓存"
        com.yuchen.ailedger.model.StockModuleStatus.Empty -> "空"
        com.yuchen.ailedger.model.StockModuleStatus.Unavailable -> "不可用"
    }

    companion object {
        private const val REALTIME_INTERVAL_MS = 1_000L
        private const val MARKET_REFRESH_INTERVAL_MS = 20_000L
        private val MARKET_WARMUP_RETRY_DELAYS_MS = longArrayOf(1_200L, 2_400L, 4_800L)
        private const val SLOW_DETAIL_DELAY_MS = 900L
        private const val SLOW_DETAIL_REFRESH_TTL_MS = 120_000L
        private const val MAX_ONE_DAY_POINTS = 600
        private const val MAX_FIVE_DAY_POINTS = 2_600
        private const val MAX_TRADE_TICKS = 120
    }
}
