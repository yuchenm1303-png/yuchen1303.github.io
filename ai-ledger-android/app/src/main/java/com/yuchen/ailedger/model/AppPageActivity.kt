package com.yuchen.ailedger.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local foreground page signal for business loops that outlive their Composable collector.
 *
 * UI drawing still follows CachedAppTabHost's CompositionLocals. This signal is deliberately limited
 * to pausing long-running ViewModel work, so hidden pages cannot keep polling merely because their
 * activity-scoped ViewModel remains alive.
 */
object AppPageActivity {
    private val _activeTab = MutableStateFlow(AppTab.Assistant)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    fun update(tab: AppTab) {
        if (_activeTab.value != tab) _activeTab.value = tab
    }
}
