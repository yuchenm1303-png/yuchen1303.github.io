package com.yuchen.ailedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.StockMarketViewModel
import com.yuchen.ailedger.StockNativePageViewModel
import com.yuchen.ailedger.data.StockHttpClient
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
    val context = LocalContext.current.applicationContext
    val watchlistRepository = remember(context) { StockWatchlistRepository.get(context) }
    var route by remember { mutableStateOf<StockNativeRoute>(StockNativeRoute.Home) }
    val routeStack = remember { mutableStateListOf<StockNativeRoute>() }
    var routeDirection by remember { mutableStateOf(SecondaryMotionDirection.Forward) }
    var routeMotionType by remember { mutableStateOf(SecondaryMotionType.Capsule) }

    val baseDensity = LocalDensity.current
    val routeFontScale = when (route) {
        StockNativeRoute.Home -> 0.92f
        is StockNativeRoute.Detail -> 1.00f
        else -> 0.98f
    }
    val routeDensityScale = when (route) {
        is StockNativeRoute.Detail -> 0.90f
        else -> 1.00f
    }
    val stockDensity = remember(
        baseDensity.density,
        baseDensity.fontScale,
        routeFontScale,
        routeDensityScale
    ) {
        Density(
            density = baseDensity.density * routeDensityScale,
            fontScale = (baseDensity.fontScale * routeFontScale / routeDensityScale)
                .coerceIn(0.88f, 1.45f)
        )
    }
    val routeHorizontalPadding = when (route) {
        StockNativeRoute.Home -> 6.dp
        is StockNativeRoute.Detail -> 0.dp
        else -> 4.dp
    }

    DisposableEffect(marketViewModel) {
        marketViewModel.setScreenVisible(true)
        onDispose {
            StockHttpClient.cancelInteractiveRequests()
            marketViewModel.setScreenVisible(false)
        }
    }

    fun navigate(next: StockNativeRoute) {
        if (next == route) return
        routeDirection = SecondaryMotionDirection.Forward
        routeMotionType = if (route == StockNativeRoute.Home) {
            SecondaryMotionType.Capsule
        } else {
            SecondaryMotionType.Push
        }
        routeStack.add(route)
        route = next
    }

    fun replace(next: StockNativeRoute) {
        if (next == route) return
        routeDirection = SecondaryMotionDirection.Forward
        routeMotionType = SecondaryMotionType.Replace
        route = next
    }

    fun openStock(code: String, startInCommunity: Boolean = false) {
        val normalized = code.trim()
        if (normalized.isBlank()) return
        StockHttpClient.cancelInteractiveRequests()
        marketViewModel.openCode(normalized)
        navigate(StockNativeRoute.Detail(normalized, startInCommunity))
    }

    fun navigateBack() {
        val previous = if (routeStack.isNotEmpty()) {
            routeStack.removeAt(routeStack.lastIndex)
        } else {
            StockNativeRoute.Home
        }
        if (route is StockNativeRoute.Detail) {
            StockHttpClient.cancelInteractiveRequests()
            marketViewModel.backToHome()
        }
        routeDirection = SecondaryMotionDirection.Backward
        routeMotionType = if (previous == StockNativeRoute.Home) {
            SecondaryMotionType.Capsule
        } else {
            SecondaryMotionType.Push
        }
        route = previous
    }

    BackHandler {
        if (route == StockNativeRoute.Home) onBack() else navigateBack()
    }

    CompositionLocalProvider(
        LocalStockNativeGlassState provides state,
        LocalDensity provides stockDensity
    ) {
        SecondaryRouteEntrance(
            motionIntensity = state.motionIntensity,
            motionType = SecondaryMotionType.Capsule,
        ) {
            StockFrostBatchHost(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = routeHorizontalPadding)
            ) {
                SecondaryPageTransition(
                    targetState = route,
                    motionIntensity = state.motionIntensity,
                    motionType = routeMotionType,
                    direction = routeDirection,
                    modifier = Modifier.fillMaxSize(),
                ) { current ->
                    when (current) {
                        StockNativeRoute.Home -> {
                            val marketUi by marketViewModel.uiState.collectAsStateWithLifecycle()
                            val nativeUi by nativeViewModel.uiState.collectAsStateWithLifecycle()
                            val watchlistState by watchlistRepository.state.collectAsStateWithLifecycle()
                            StockNativeHomeScreen(
                                marketUi = marketUi,
                                nativeUi = nativeUi,
                                watchlist = watchlistState.items,
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
                                    if (action == "热点") {
                                        nativeViewModel.loadHot(StockNativeHotType.Popularity)
                                    }
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
                        }

                        is StockNativeRoute.Ranking -> {
                            val nativeUi by nativeViewModel.uiState.collectAsStateWithLifecycle()
                            StockNativeRankingScreen(
                                ui = nativeUi,
                                type = current.type,
                                onBack = ::navigateBack,
                                onRefresh = { nativeViewModel.loadRanking(nativeUi.rankingType, true) },
                                onSelectType = { type ->
                                    nativeViewModel.loadRanking(type)
                                    replace(StockNativeRoute.Ranking(type))
                                },
                                onOpenStock = ::openStock
                            )
                        }

                        is StockNativeRoute.Hot -> {
                            val nativeUi by nativeViewModel.uiState.collectAsStateWithLifecycle()
                            StockNativeHotScreen(
                                ui = nativeUi,
                                type = current.type,
                                onBack = ::navigateBack,
                                onRefresh = { nativeViewModel.loadHot(nativeUi.hotSnapshot.type, true) },
                                onSelectType = { type ->
                                    nativeViewModel.loadHot(type)
                                    replace(StockNativeRoute.Hot(type))
                                },
                                onOpenStock = ::openStock
                            )
                        }

                        is StockNativeRoute.Sector -> {
                            val nativeUi by nativeViewModel.uiState.collectAsStateWithLifecycle()
                            StockNativeSectorScreen(
                                ui = nativeUi,
                                code = current.code,
                                onBack = ::navigateBack,
                                onRefresh = { nativeViewModel.loadSector(current.code, true) },
                                onSelectTab = nativeViewModel::selectSectorTab,
                                onLoadMore = nativeViewModel::loadMoreSectorConstituents,
                                onOpenSector = { code ->
                                    nativeViewModel.loadSector(code, true)
                                    replace(StockNativeRoute.Sector(code))
                                },
                                onOpenStock = ::openStock
                            )
                        }

                        is StockNativeRoute.Index -> {
                            val nativeUi by nativeViewModel.uiState.collectAsStateWithLifecycle()
                            StockNativeIndexScreenV2(
                                ui = nativeUi,
                                code = current.code,
                                onBack = ::navigateBack,
                                onRefresh = { nativeViewModel.loadIndex(current.code, true) },
                                onOpenIndex = { code ->
                                    nativeViewModel.loadIndex(code, true)
                                    replace(StockNativeRoute.Index(code))
                                }
                            )
                        }

                        is StockNativeRoute.Detail -> {
                            val marketUi by marketViewModel.uiState.collectAsStateWithLifecycle()
                            val nativeUi by nativeViewModel.uiState.collectAsStateWithLifecycle()
                            val watchlistState by watchlistRepository.state.collectAsStateWithLifecycle()
                            val quote = marketUi.stock.quote
                            StockNativeDetailScreen(
                                appState = state,
                                marketUi = marketUi,
                                nativeUi = nativeUi,
                                startInCommunity = current.startInCommunity,
                                isWatched = watchlistState.items.any { it.code == quote.code },
                                onBack = ::navigateBack,
                                onRefresh = marketViewModel::refreshCurrent,
                                onToggleWatch = {
                                    if (quote.code.length == 6) {
                                        watchlistRepository.toggle(
                                            code = quote.code,
                                            name = quote.name.ifBlank { quote.code },
                                            market = quote.market
                                        )
                                    }
                                },
                                onSelectTab = { tab ->
                                    StockHttpClient.cancelChartRequests()
                                    StockHttpClient.cancelRealtimeRequests()
                                    marketViewModel.selectTab(tab)
                                },
                                onLoadCommunity = { reset ->
                                    nativeViewModel.loadDiscussions(quote.code, reset)
                                },
                                onLoadMoreCommunity = {
                                    nativeViewModel.loadDiscussions(quote.code, false)
                                },
                                onOpenPost = { postId ->
                                    val code = quote.code
                                    nativeViewModel.loadPost(code, postId)
                                    navigate(StockNativeRoute.Post(code, postId))
                                }
                            )
                        }

                        is StockNativeRoute.Post -> {
                            val nativeUi by nativeViewModel.uiState.collectAsStateWithLifecycle()
                            StockNativePostScreen(
                                ui = nativeUi,
                                code = current.code,
                                postId = current.postId,
                                onBack = ::navigateBack,
                                onRefresh = {
                                    nativeViewModel.loadPost(current.code, current.postId, true)
                                },
                                onLoadComments = nativeViewModel::loadComments
                            )
                        }
                    }
                }
            }
        }
    }
}
