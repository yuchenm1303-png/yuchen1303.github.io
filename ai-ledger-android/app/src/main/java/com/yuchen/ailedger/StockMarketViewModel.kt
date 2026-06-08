package com.yuchen.ailedger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.StockRealtimeRepository
import com.yuchen.ailedger.data.StockRepository
import com.yuchen.ailedger.model.StockDetailUiState
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
    private var kLineJob: Job? = null
    private var minuteJob: Job? = null
    private var realtimeJob: Job? = null
    private var requestSeq = 0

    init {
        refreshHome()
    }

    fun updateQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun refreshHome() {
        stopRealtimeLoop()
        loadLite(openDetail = false)
    }

    fun searchAndOpen() {
        loadLite(openDetail = true)
    }

    fun openDetail() {
        val state = _uiState.value
        if (state.stock.errorMessage != null) {
            loadLite(openDetail = true)
        } else {
            _uiState.update { it.copy(showDetail = true, activeAction = null) }
            startRealtimeLoop(state.stock.quote.code)
            if (isMinuteTab(state.selectedTab)) loadMinute(daysForTab(state.selectedTab)) else loadKLineForTab(state.selectedTab)
        }
    }

    fun backToHome() {
        stopRealtimeLoop()
        _uiState.update { it.copy(showDetail = false, activeAction = null) }
    }

    fun refreshCurrent() {
        loadLite(openDetail = _uiState.value.showDetail)
    }

    fun selectTab(tab: String) {
        if (isMinuteTab(tab)) {
            kLineJob?.cancel()
            _uiState.update {
                it.copy(
                    selectedTab = tab,
                    requestMessage = if (tab == "五日") "正在同步真实五日分时" else "正在同步真实分时"
                )
            }
            loadMinute(daysForTab(tab))
        } else {
            minuteJob?.cancel()
            _uiState.update {
                it.copy(
                    selectedTab = tab,
                    stock = it.stock.copy(kLinePoints = emptyList()),
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
                "加自选" -> state.copy(isWatched = !state.isWatched, activeAction = "加自选")
                else -> state.copy(activeAction = if (state.activeAction == action) null else action)
            }
        }
    }

    fun closeAction() {
        _uiState.update { it.copy(activeAction = null) }
    }

    fun openCode(code: String) {
        stopRealtimeLoop()
        _uiState.update { it.copy(query = code, selectedTab = "分时", requestMessage = null) }
        loadLite(openDetail = true, forcedQuery = code)
    }

    private fun loadLite(openDetail: Boolean, forcedQuery: String? = null) {
        val seq = ++requestSeq
        val target = (forcedQuery ?: _uiState.value.query).trim().ifBlank { _uiState.value.stock.quote.code }
        quoteJob?.cancel()
        quoteJob = viewModelScope.launch {
            if (openDetail) stopRealtimeLoop()
            _uiState.update { it.copy(loading = true, requestMessage = "连接新版A股行情代理中") }
            val mode = if (openDetail || _uiState.value.showDetail) "full" else "lite"
            val loaded = withContext(Dispatchers.IO) { repository.loadAStock(target, mode = mode) }
            if (seq != requestSeq) return@launch
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
            if (openDetail || _uiState.value.showDetail) startRealtimeLoop(loaded.quote.code)
            val tab = _uiState.value.selectedTab
            if (isMinuteTab(tab)) loadMinute(daysForTab(tab), loaded.quote.code) else loadKLineForTab(tab, loaded.quote.code)
        }
    }

    private fun loadMarketOverview(query: String) {
        marketJob?.cancel()
        marketJob = viewModelScope.launch {
            _uiState.update { it.copy(marketLoading = true) }
            val merged = withContext(Dispatchers.IO) { repository.loadMarketOverview(query, _uiState.value.stock) }
            _uiState.update { state ->
                state.copy(
                    stock = state.stock.copy(
                        indices = merged.indices,
                        watchlist = merged.watchlist,
                        marketBoards = merged.marketBoards,
                        dataSourceLabel = merged.dataSourceLabel
                    ),
                    marketLoading = false
                )
            }
        }
    }

    private fun loadMinute(days: Int = 1, forcedQuery: String? = null) {
        val target = (forcedQuery ?: _uiState.value.stock.quote.code).ifBlank { _uiState.value.query }
        minuteJob?.cancel()
        minuteJob = viewModelScope.launch {
            val message = if (days >= 5) "正在同步真实五日分时" else "正在同步真实分时"
            _uiState.update { it.copy(kLineLoading = true, requestMessage = message) }
            val result = withContext(Dispatchers.IO) { realtimeRepository.loadRealtimeFrame(target, _uiState.value.stock, minuteDays = days) }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { realtime ->
                        state.copy(
                            stock = state.stock.copy(
                                quote = realtime.quote,
                                topMetrics = realtime.topMetrics,
                                minutePoints = realtime.minutePoints,
                                sellLevels = realtime.sellLevels,
                                buyLevels = realtime.buyLevels,
                                tradeTicks = realtime.tradeTicks,
                                dataSourceLabel = realtime.dataSourceLabel
                            ),
                            kLineLoading = false,
                            requestMessage = null
                        )
                    },
                    onFailure = { error ->
                        state.copy(kLineLoading = false, requestMessage = "${if (days >= 5) "五日分时" else "分时"}加载失败：${error.message ?: error.javaClass.simpleName}")
                    }
                )
            }
        }
    }

    private fun loadKLineForTab(tab: String, forcedQuery: String? = null) {
        val target = (forcedQuery ?: _uiState.value.stock.quote.code).ifBlank { _uiState.value.query }
        val period = periodForTab(tab)
        kLineJob?.cancel()
        kLineJob = viewModelScope.launch {
            _uiState.update { it.copy(kLineLoading = true, stock = it.stock.copy(kLinePoints = emptyList()), requestMessage = "正在加载真实${tab}") }
            val result = withContext(Dispatchers.IO) { repository.loadKLinePoints(target, period) }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { points ->
                        if (points.size >= 2) state.copy(stock = state.stock.copy(kLinePoints = points), kLineLoading = false, requestMessage = null)
                        else state.copy(stock = state.stock.copy(kLinePoints = emptyList()), kLineLoading = false, requestMessage = "${tab}接口返回数据不足")
                    },
                    onFailure = { error -> state.copy(stock = state.stock.copy(kLinePoints = emptyList()), kLineLoading = false, requestMessage = "${tab}加载失败：${error.message ?: error.javaClass.simpleName}") }
                )
            }
        }
    }

    private fun startRealtimeLoop(code: String) {
        val target = code.ifBlank { _uiState.value.stock.quote.code }.ifBlank { _uiState.value.query }
        if (realtimeJob?.isActive == true && _uiState.value.stock.quote.code == target) return
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            while (isActive) {
                if (!_uiState.value.showDetail) break
                val currentState = _uiState.value
                val current = currentState.stock
                val activeCode = current.quote.code.ifBlank { target }
                val minuteDays = daysForTab(currentState.selectedTab)
                val result = withContext(Dispatchers.IO) { realtimeRepository.loadRealtimeFrame(activeCode, current, minuteDays = minuteDays) }
                result.onSuccess { realtime ->
                    _uiState.update { state ->
                        if (!state.showDetail || state.stock.quote.code != activeCode) state else state.copy(
                            stock = state.stock.copy(
                                quote = realtime.quote,
                                topMetrics = realtime.topMetrics,
                                minutePoints = realtime.minutePoints,
                                sellLevels = realtime.sellLevels,
                                buyLevels = realtime.buyLevels,
                                tradeTicks = realtime.tradeTicks,
                                dataSourceLabel = realtime.dataSourceLabel
                            ),
                            requestMessage = null
                        )
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun stopRealtimeLoop() {
        realtimeJob?.cancel()
        realtimeJob = null
    }

    override fun onCleared() {
        stopRealtimeLoop()
        super.onCleared()
    }

    private fun isMinuteTab(tab: String): Boolean = tab == "分时" || tab == "五日"

    private fun daysForTab(tab: String): Int = if (tab == "五日") 5 else 1

    private fun periodForTab(tab: String): String = when (tab) {
        "周K" -> "weekly"
        "月K" -> "monthly"
        else -> "daily"
    }
}
