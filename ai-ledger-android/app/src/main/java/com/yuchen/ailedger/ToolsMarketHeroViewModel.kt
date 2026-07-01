package com.yuchen.ailedger

import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.StockNativePageRepository
import com.yuchen.ailedger.model.StockMinutePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val isRising: Boolean
        get() = !changePercent.trim().startsWith("-")
}

@Immutable
data class ToolsMarketHeroUiState(
    val indices: List<ToolsMarketIndexItem> = TOOLS_MARKET_INDEX_SPECS.map {
        ToolsMarketIndexItem(code = it.code, name = it.name)
    },
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val lastSuccessfulRefreshMs: Long = 0L
)

/**
 * 功能页行情大卡的轻量数据源。
 *
 * 只在功能页可见时按“上证→深证→创业板”顺序加载真实指数详情，避免三个请求同时冲击
 * 股票代理。这里不建立常驻轮询；短时间返回功能页直接复用结果，超过 TTL 后才重新同步。
 */
class ToolsMarketHeroViewModel(
    private val repository: StockNativePageRepository = StockNativePageRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ToolsMarketHeroUiState())
    val uiState: StateFlow<ToolsMarketHeroUiState> = _uiState

    private var loadJob: Job? = null
    private var visible = false

    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        if (value) {
            refreshIfStale()
        } else {
            loadJob?.cancel()
            loadJob = null
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun refreshIfStale(force: Boolean = false) {
        if (!visible || loadJob?.isActive == true) return
        val state = _uiState.value
        val now = SystemClock.elapsedRealtime()
        val complete = state.indices.all { it.hasRealQuote && it.minutePoints.size >= 2 }
        if (
            !force &&
            complete &&
            state.lastSuccessfulRefreshMs > 0L &&
            now - state.lastSuccessfulRefreshMs < REFRESH_TTL_MS
        ) {
            return
        }

        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            var successCount = 0
            var firstError: String? = null

            for (spec in TOOLS_MARKET_INDEX_SPECS) {
                if (!visible) break
                val result = withContext(Dispatchers.IO) {
                    repository.loadIndexDetail(spec.code)
                }
                currentCoroutineContext().ensureActive()
                result.fold(
                    onSuccess = { detail ->
                        successCount += 1
                        val quote = detail.quote
                        val item = ToolsMarketIndexItem(
                            code = spec.code,
                            name = detail.name.ifBlank { quote.name.ifBlank { spec.name } },
                            price = quote.price.ifBlank { "--" },
                            changeAmount = quote.changeAmount.ifBlank { "--" },
                            changePercent = quote.changePercent.ifBlank { "--" },
                            previousClose = quote.previousClose,
                            minutePoints = compactMinutePoints(detail.minutePoints),
                            updatedAt = detail.updatedAt
                        )
                        _uiState.update { current ->
                            current.copy(
                                indices = current.indices.map { existing ->
                                    if (existing.code == spec.code) item else existing
                                },
                                errorMessage = null
                            )
                        }
                    },
                    onFailure = { error ->
                        if (firstError == null) {
                            firstError = error.message ?: "指数数据加载失败"
                        }
                    }
                )
            }

            val finishedAt = SystemClock.elapsedRealtime()
            _uiState.update { current ->
                current.copy(
                    loading = false,
                    errorMessage = if (successCount == 0) firstError else null,
                    lastSuccessfulRefreshMs = if (successCount > 0) {
                        finishedAt
                    } else {
                        current.lastSuccessfulRefreshMs
                    }
                )
            }
            loadJob = null
        }
    }

    private fun compactMinutePoints(points: List<StockMinutePoint>): List<StockMinutePoint> {
        val real = points.filter { it.price.isFinite() && it.price > 0f }
        if (real.size <= MAX_SPARKLINE_POINTS) return real
        val stride = ((real.size - 1) / (MAX_SPARKLINE_POINTS - 1)).coerceAtLeast(1)
        val sampled = buildList {
            real.forEachIndexed { index, point ->
                if (index == 0 || index == real.lastIndex || index % stride == 0) add(point)
            }
        }
        return sampled.takeLast(MAX_SPARKLINE_POINTS)
    }

    override fun onCleared() {
        loadJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val REFRESH_TTL_MS = 45_000L
        private const val MAX_SPARKLINE_POINTS = 72
    }
}
