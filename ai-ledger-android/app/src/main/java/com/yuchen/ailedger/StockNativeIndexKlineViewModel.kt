package com.yuchen.ailedger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.StockNativeIndexKlineRepository
import com.yuchen.ailedger.model.StockKLinePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class StockNativeIndexKlineUiState(
    val code: String = "",
    val points: List<StockKLinePoint> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class StockNativeIndexKlineViewModel(
    private val repository: StockNativeIndexKlineRepository = StockNativeIndexKlineRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(StockNativeIndexKlineUiState())
    val uiState: StateFlow<StockNativeIndexKlineUiState> = _uiState
    private var loadJob: Job? = null
    private val cache = mutableMapOf<String, List<StockKLinePoint>>()

    fun load(code: String, force: Boolean = false) {
        val normalized = code.filter(Char::isDigit)
        if (normalized.isBlank()) return
        val cached = cache[normalized]
        if (!force && cached?.isNotEmpty() == true) {
            _uiState.value = StockNativeIndexKlineUiState(code = normalized, points = cached)
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    code = normalized,
                    points = if (it.code == normalized) it.points else emptyList(),
                    loading = true,
                    error = null
                )
            }
            val result = withContext(Dispatchers.IO) { repository.loadDaily(normalized) }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { rows ->
                        cache[normalized] = rows
                        state.copy(code = normalized, points = rows, loading = false)
                    },
                    onFailure = { error ->
                        state.copy(code = normalized, loading = false, error = error.message ?: "指数K线加载失败")
                    }
                )
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        super.onCleared()
    }
}
