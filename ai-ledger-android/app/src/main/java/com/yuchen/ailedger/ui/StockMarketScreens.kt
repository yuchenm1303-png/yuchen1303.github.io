package com.yuchen.ailedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.yield

private sealed interface StockNativeRoute {
    data object Home : StockNativeRoute
    data class Ranking(val type: StockNativeRankingType) : StockNativeRoute
    data class Hot(val type: StockNativeHotType) : StockNativeRoute
    data class Sector(val code: String) : StockNativeRoute
    data class Index(val code: String) : StockNativeRoute
    data class Detail(val code: String, val startInCommunity: Boolean = false) : StockNativeRoute
    data class Post(val code: String, val postId: String) : StockNativeRoute
}

private data class PendingStockOpen(val code: String)

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
    var pendingOpen by remember { mutableStateOf<PendingStockOpen?>(null) }
    var pendingBackToHome by remember { mutableStateOf(false) }
    val baseDensity = LocalDensity.current

    DisposableEffect(marketViewModel) {
        marketViewModel.setScreenVisible(true)
        onDispose { marketViewModel.setScreenVisible(false) }
    }

    fun navigate(next: StockNativeRoute) {
        if (next == route) return
        routeStack.add(route)
        route = next
    }

    fun openStock(code: String, startInCommunity: Boolean = false) {
        val normalized = code.trim()
        if (normalized.isBlank()) return
        pendingOpen = PendingStockOpen(normalized)
        navigate(StockNativeRoute.Detail(normalized, startInCommunity))
    }

    fun navigateBack() {
        val leavingDetail = route is StockNativeRoute.Detail
        val previous = if (routeStack.isNotEmpty()) {
            routeStack.removeAt(routeStack.lastIndex)
        } else {
            StockNativeRoute.Home
        }
        route = previous
        if (leavingDetail) pendingBackToHome = true
    }

    LaunchedEffect(route, pendingOpen) {
        val pending = pendingOpen
        val current = route
        if (pending != null && current is StockNativeRoute.Detail && current.code == pending.code) {
            yield()
            withFrameNanos { }
            marketViewModel.openCode(pending.code)
            pendingOpen = null
        }
    }

    LaunchedEffect(route, pendingBackToHome) {
        if (pendingBackToHome && route !is StockNativeRoute.Detail) {
            yield()
            withFrameNanos { }
            marketViewModel.backToHome()
            pendingBackToHome = false
        }
    }

    BackHandler {
        if (route == StockNativeRoute.Home) onBack() else navigateBack()
    }

    StockRouteTransitionHost(
        route = route,
        motionIntensity = state.motionIntensity,
        modifier = Modifier.fillMaxSize(),
    ) { current ->
        val routeFontScale = when (current) {
            StockNativeRoute.Home -> 0.92f
            is StockNativeRoute.Detail -> 1.00f
            else -> 0.98f
        }
        val routeDensityScale = when (current) {
            is StockNativeRoute.Detail -> 0.90f
            else -> 1.00f
        }
        val stockDensity = remember(
            baseDensity.density,
            baseDensity.fontScale,
            routeFontScale,
            routeDensityScale,
        ) {
            Density(
                density = baseDensity.density * routeDensityScale,
                fontScale = (baseDensity.fontScale * routeFontScale / routeDensityScale)
                    .coerceIn(0.88f, 1.45f),
            )
        }
        val routeHorizontalPadding = when (current) {
            StockNativeRoute.Home -> 6.dp
            is StockNativeRoute.Detail -> 0.dp
            else -> 4.dp
        }

        CompositionLocalProvider(
            LocalStockNativeGlassState provides state,
            LocalDensity provides stockDensity,
        ) {
            StockFrostBatchHost(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = routeHorizontalPadding),
            ) {
                when (current) {
                    StockNativeRoute.Home -> {
                        val marketUi by marketViewModel.uiState.collectAsState()
                        val nativeUi by nativeViewModel.uiState.collectAsState()
                        val watchlistState by watchlistRepository.state.collectAsState()
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
                            onOpenAssistant = onOpenAssistant,
                        )
                    }

                    is StockNativeRoute.Ranking -> {
                        val nativeUi by nativeViewModel.uiState.collectAsState()
                        StockNativeRankingScreen(
                            ui = nativeUi,
                            type = current.type,
                            onBack = ::navigateBack,
                            onRefresh = { nativeViewModel.loadRanking(nativeUi.rankingType, true) },
                            onSelectType = { type ->
                                nativeViewModel.loadRanking(type)
                                route = StockNativeRoute.Ranking(type)
                            },
                            onOpenStock = ::openStock,
                        )
                    }

                    is StockNativeRoute.Hot -> {
                        val nativeUi by nativeViewModel.uiState.collectAsState()
                        StockNativeHotScreen(
                            ui = nativeUi,
                            type = current.type,
                            onBack = ::navigateBack,
                            onRefresh = { nativeViewModel.loadHot(nativeUi.hotSnapshot.type, true) },
                            onSelectType = { type ->
                                nativeViewModel.loadHot(type)
                                route = StockNativeRoute.Hot(type)
                            },
                            onOpenStock = ::openStock,
                        )
                    }

                    is StockNativeRoute.Sector -> {
                        val nativeUi by nativeViewModel.uiState.collectAsState()
                        StockNativeSectorScreen(
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
                            onOpenStock = ::openStock,
                        )
                    }

                    is StockNativeRoute.Index -> {
                        val nativeUi by nativeViewModel.uiState.collectAsState()
                        StockNativeIndexScreenV2(
                            ui = nativeUi,
                            code = current.code,
                            onBack = ::navigateBack,
                            onRefresh = { nativeViewModel.loadIndex(current.code, true) },
                            onOpenIndex = { code ->
                                nativeViewModel.loadIndex(code, true)
                                route = StockNativeRoute.Index(code)
                            },
                        )
                    }

                    is StockNativeRoute.Detail -> {
                        val marketUi by marketViewModel.uiState.collectAsState()
                        val nativeUi by nativeViewModel.uiState.collectAsState()
                        val watchlistState by watchlistRepository.state.collectAsState()
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
                                        market = quote.market,
                                    )
                                }
                            },
                            onSelectTab = marketViewModel::selectTab,
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
                            },
                        )
                    }

                    is StockNativeRoute.Post -> {
                        val nativeUi by nativeViewModel.uiState.collectAsState()
                        StockNativePostScreen(
                            ui = nativeUi,
                            code = current.code,
                            postId = current.postId,
                            onBack = ::navigateBack,
                            onRefresh = {
                                nativeViewModel.loadPost(current.code, current.postId, true)
                            },
                            onLoadComments = nativeViewModel::loadComments,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StockRouteTransitionHost(
    route: StockNativeRoute,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    content: @Composable (StockNativeRoute) -> Unit,
) {
    var displayedRoute by remember { mutableStateOf(route) }
    val progress = remember { Animatable(1f) }
    val motion = motionIntensity.coerceIn(0f, 1f)

    LaunchedEffect(route, motion) {
        displayedRoute = route
        progress.stop()
        if (motion <= 0.05f) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        withFrameNanos { }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.84f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val p = progress.value
                val detailScaleBase = if (displayedRoute is StockNativeRoute.Detail) 0.992f else 0.986f
                val maxOffset = if (displayedRoute is StockNativeRoute.Detail) 22.dp.toPx() else 16.dp.toPx()
                alpha = p
                translationY = (1f - p) * maxOffset
                val scale = detailScaleBase + (1f - detailScaleBase) * p
                scaleX = scale
                scaleY = scale
                clip = true
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .fillMaxSize(),
    ) {
        content(displayedRoute)
    }
}
