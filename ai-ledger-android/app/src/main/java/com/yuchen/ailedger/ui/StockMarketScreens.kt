package com.yuchen.ailedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.StockMarketViewModel
import com.yuchen.ailedger.StockNativePageViewModel
import com.yuchen.ailedger.data.StockWatchlistRepository
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.StockNativeHotType
import com.yuchen.ailedger.model.StockNativeRankingType

private sealed interface StockNativeRoute {
    data object Home : StockNativeRoute
    data class Ranking(val type: StockNativeRankingType) : StockNativeRoute
    data class Hot(val type: StockNativeHotType) : StockNativeRoute
    data class Sector(val code: String) : StockNativeRoute
    data class Index(val code: String) : StockNativeRoute
    data class Detail(val code: String, val startInCommunity: Boolean = false) : StockNativeRoute
    data class Post(val code: String, val postId: String) : StockNativeRoute
}

@Composable
fun AStockMarketScreenV2(
    state: AssistantUiState,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    val marketViewModel: StockMarketViewModel = viewModel()
    val nativeViewModel: StockNativePageViewModel = viewModel()
    val marketUi by marketViewModel.uiState.collectAsState()
    val nativeUi by nativeViewModel.uiState.collectAsState()
    val context = LocalContext.current.applicationContext
    val watchlistRepository = remember(context) { StockWatchlistRepository.get(context) }
    val watchlistState by watchlistRepository.state.collectAsState()
    val watchlist = watchlistState.items
    var route by remember { mutableStateOf<StockNativeRoute>(StockNativeRoute.Home) }
    val routeStack = remember { mutableStateListOf<StockNativeRoute>() }
    val baseDensity = LocalDensity.current
    val stockDensity = remember(baseDensity.density, baseDensity.fontScale) {
        Density(
            density = baseDensity.density,
            fontScale = (baseDensity.fontScale * 1.10f).coerceAtMost(1.35f)
        )
    }

    fun navigate(next: StockNativeRoute) {
        if (next == route) return
        routeStack.add(route)
        route = next
    }

    fun openStock(code: String, startInCommunity: Boolean = false) {
        val normalized = code.trim()
        if (normalized.isBlank()) return
        marketViewModel.openCode(normalized)
        navigate(StockNativeRoute.Detail(normalized, startInCommunity))
    }

    fun navigateBack() {
        val previous = if (routeStack.isNotEmpty()) routeStack.removeAt(routeStack.lastIndex) else StockNativeRoute.Home
        if (route is StockNativeRoute.Detail) marketViewModel.backToHome()
        route = previous
    }

    fun toggleWatch() {
        val quote = marketUi.stock.quote
        if (quote.code.length != 6) return
        watchlistRepository.toggle(
            code = quote.code,
            name = quote.name.ifBlank { quote.code },
            market = quote.market
        )
    }

    BackHandler {
        if (route == StockNativeRoute.Home) onBack() else navigateBack()
    }

    CompositionLocalProvider(
        LocalStockNativeGlassState provides state,
        LocalDensity provides stockDensity
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp)
        ) {
            when (val current = route) {
                StockNativeRoute.Home -> StockNativeHomeScreen(
                    marketUi = marketUi,
                    nativeUi = nativeUi,
                    watchlist = watchlist,
                    watchlistStatus = watchlistState.statusLabel,
                    watchlistMessage = watchlistState.message,
                    watchlistBusy = watchlistState.loading || watchlistState.saving,
                    onBack = onBack,
                    onRefresh = marketViewModel::refreshHome,
                    onRefreshWatchlist = watchlistRepository::refresh,
                    onQueryChange = marketViewModel::updateQuery,
                    onSearch = {
                        val query = marketUi.query.trim()
                        if (query.isNotBlank()) openStock(query)
                    },
                    onOpenStock = ::openStock,
                    onRemoveWatch = watchlistRepository::remove,
                    onSelectAction = { action ->
                        marketViewModel.selectHomeAction(action)
                        if (action == "热点") nativeViewModel.loadHot(StockNativeHotType.Popularity)
                    },
                    onOpenRanking = { type ->
                        nativeViewModel.loadRanking(type)
                        navigate(StockNativeRoute.Ranking(type))
                    },
                    onOpenHot = { type ->
                        nativeViewModel.loadHot(type)
                        navigate(StockNativeRoute.Hot(type))
                    },
                    onLoadConcept = nativeViewModel::loadConceptSectors,
                    onOpenSector = { code ->
                        nativeViewModel.loadSector(code)
                        navigate(StockNativeRoute.Sector(code))
                    },
                    onOpenIndex = { code ->
                        nativeViewModel.loadIndex(code)
                        navigate(StockNativeRoute.Index(code))
                    },
                    onOpenAssistant = onOpenAssistant
                )

                is StockNativeRoute.Ranking -> StockNativeRankingScreen(
                    ui = nativeUi,
                    type = current.type,
                    onBack = ::navigateBack,
                    onRefresh = { nativeViewModel.loadRanking(nativeUi.rankingType, true) },
                    onSelectType = { type ->
                        nativeViewModel.loadRanking(type)
                        route = StockNativeRoute.Ranking(type)
                    },
                    onOpenStock = ::openStock
                )

                is StockNativeRoute.Hot -> StockNativeHotScreen(
                    ui = nativeUi,
                    type = current.type,
                    onBack = ::navigateBack,
                    onRefresh = { nativeViewModel.loadHot(nativeUi.hotSnapshot.type, true) },
                    onSelectType = { type ->
                        nativeViewModel.loadHot(type)
                        route = StockNativeRoute.Hot(type)
                    },
                    onOpenStock = ::openStock
                )

                is StockNativeRoute.Sector -> StockNativeSectorScreen(
                    ui = nativeUi,
                    code = current.code,
                    onBack = ::navigateBack,
                    onRefresh = { nativeViewModel.loadSector(current.code, true) },
                    onSelectTab = nativeViewModel::selectSectorTab,
                    onLoadMore = nativeViewModel::loadMoreSectorConstituents,
                    onOpenSector = { code ->
                        nativeViewModel.loadSector(code, true)
                        route = StockNativeRoute.Sector(code)
                    },
                    onOpenStock = ::openStock
                )

                is StockNativeRoute.Index -> StockNativeIndexScreenV2(
                    ui = nativeUi,
                    code = current.code,
                    onBack = ::navigateBack,
                    onRefresh = { nativeViewModel.loadIndex(current.code, true) },
                    onOpenIndex = { code ->
                        nativeViewModel.loadIndex(code, true)
                        route = StockNativeRoute.Index(code)
                    }
                )

                is StockNativeRoute.Detail -> StockNativeDetailScreen(
                    appState = state,
                    marketUi = marketUi,
                    nativeUi = nativeUi,
                    startInCommunity = current.startInCommunity,
                    isWatched = watchlist.any { it.code == marketUi.stock.quote.code },
                    onBack = ::navigateBack,
                    onRefresh = marketViewModel::refreshCurrent,
                    onToggleWatch = ::toggleWatch,
                    onSelectTab = marketViewModel::selectTab,
                    onLoadCommunity = { reset -> nativeViewModel.loadDiscussions(marketUi.stock.quote.code, reset) },
                    onLoadMoreCommunity = { nativeViewModel.loadDiscussions(marketUi.stock.quote.code, false) },
                    onOpenPost = { postId ->
                        val code = marketUi.stock.quote.code
                        nativeViewModel.loadPost(code, postId)
                        navigate(StockNativeRoute.Post(code, postId))
                    }
                )

                is StockNativeRoute.Post -> StockNativePostScreen(
                    ui = nativeUi,
                    code = current.code,
                    postId = current.postId,
                    onBack = ::navigateBack,
                    onRefresh = { nativeViewModel.loadPost(current.code, current.postId, true) },
                    onLoadComments = nativeViewModel::loadComments
                )
            }
        }
    }
}
