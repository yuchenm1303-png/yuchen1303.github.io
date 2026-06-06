package com.yuchen.ailedger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.StockRepository
import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.sampleAStockDetailUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
    private val repository: StockRepository = StockRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(StockMarketUiState(loading = true, marketLoading = true))
    val uiState: StateFlow<StockMarketUiState> = _uiState

    private var quoteJob: Job? = null
    private var marketJob: Job? = null
    private var kLineJob: Job? = null
    private var requestSeq = 0

    init {
        refreshHome()
    }

    fun updateQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun refreshHome() {
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
        }
    }

    fun backToHome() {
        _uiState.update { it.copy(showDetail = false, activeAction = null) }
    }

    fun refreshCurrent() {
        loadLite(openDetail = _uiState.value.showDetail)
    }

    fun selectTab(tab: String) {
        _uiState.update { it.copy(selectedTab = tab, requestMessage = if (tab == "分时") it.requestMessage else "正在加载真实历史K线") }
        if (tab != "分时" && _uiState.value.stock.kLinePoints.size < 20) {
            loadFullKLine()
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
        _uiState.update { it.copy(query = code, selectedTab = "分时", requestMessage = null) }
        loadLite(openDetail = true, forcedQuery = code)
    }

    private fun loadLite(openDetail: Boolean, forcedQuery: String? = null) {
        val seq = ++requestSeq
        val target = (forcedQuery ?: _uiState.value.query).trim().ifBlank { _uiState.value.stock.quote.code }
        quoteJob?.cancel()
        quoteJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, requestMessage = "连接行情代理中") }
            val loaded = withContext(Dispatchers.IO) { repository.loadAStock(target, mode = "lite") }
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
            if (_uiState.value.selectedTab != "分时") {
                loadFullKLine()
            }
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

    private fun loadFullKLine() {
        val target = _uiState.value.stock.quote.code.ifBlank { _uiState.value.query }
        kLineJob?.cancel()
        kLineJob = viewModelScope.launch {
            _uiState.update { it.copy(kLineLoading = true, requestMessage = "正在加载真实历史K线") }
            val result = withContext(Dispatchers.IO) { repository.loadKLinePoints(target) }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { points ->
                        if (points.size >= 2) {
                            state.copy(
                                stock = state.stock.copy(kLinePoints = points),
                                kLineLoading = false,
                                requestMessage = null
                            )
                        } else {
                            state.copy(
                                kLineLoading = false,
                                requestMessage = "K线接口返回数据不足，请稍后刷新重试"
                            )
                        }
                    },
                    onFailure = { error ->
                        state.copy(
                            kLineLoading = false,
                            requestMessage = "K线加载失败：${error.message ?: error.javaClass.simpleName}"
                        )
                    }
                )
            }
        }
    }
}
