package com.yuchen.ailedger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.StockNativePageRepository
import com.yuchen.ailedger.model.StockKLinePoint
import com.yuchen.ailedger.model.StockNativeConstituent
import com.yuchen.ailedger.model.StockNativeDiscussionComment
import com.yuchen.ailedger.model.StockNativeDiscussionPostPage
import com.yuchen.ailedger.model.StockNativeDiscussionPostSummary
import com.yuchen.ailedger.model.StockNativeHotSnapshot
import com.yuchen.ailedger.model.StockNativeHotType
import com.yuchen.ailedger.model.StockNativeIndexDetail
import com.yuchen.ailedger.model.StockNativeRankingItem
import com.yuchen.ailedger.model.StockNativeRankingType
import com.yuchen.ailedger.model.StockNativeSectorDetail
import com.yuchen.ailedger.model.StockSectorSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class StockNativePageUiState(
    val rankingType: StockNativeRankingType = StockNativeRankingType.Gainers,
    val rankingItems: List<StockNativeRankingItem> = emptyList(),
    val rankingLoading: Boolean = false,
    val rankingError: String? = null,
    val hotSnapshot: StockNativeHotSnapshot = StockNativeHotSnapshot(),
    val hotLoading: Boolean = false,
    val hotError: String? = null,
    val conceptSectors: List<StockSectorSnapshot> = emptyList(),
    val conceptLoading: Boolean = false,
    val conceptError: String? = null,
    val sectorDetail: StockNativeSectorDetail? = null,
    val sectorConstituents: List<StockNativeConstituent> = emptyList(),
    val sectorConstituentPage: Int = 0,
    val sectorConstituentTotal: Int = 0,
    val sectorHasMore: Boolean = false,
    val sectorLoading: Boolean = false,
    val sectorConstituentLoading: Boolean = false,
    val sectorError: String? = null,
    val sectorTab: String = "minute",
    val sectorKlines: Map<String, List<StockKLinePoint>> = emptyMap(),
    val sectorKlineLoading: Boolean = false,
    val indexDetail: StockNativeIndexDetail? = null,
    val indexLoading: Boolean = false,
    val indexError: String? = null,
    val discussionCode: String = "",
    val discussionName: String = "",
    val discussions: List<StockNativeDiscussionPostSummary> = emptyList(),
    val discussionPage: Int = 0,
    val discussionHasMore: Boolean = false,
    val discussionSourceUrl: String = "",
    val discussionLoading: Boolean = false,
    val discussionError: String? = null,
    val postDetail: StockNativeDiscussionPostPage? = null,
    val postLoading: Boolean = false,
    val postError: String? = null,
    val comments: List<StockNativeDiscussionComment> = emptyList(),
    val commentsPage: Int = 0,
    val commentsTotal: Int = 0,
    val commentsHasMore: Boolean = false,
    val commentsLoaded: Boolean = false,
    val commentsLoading: Boolean = false,
    val commentsError: String? = null
)

class StockNativePageViewModel(
    private val repository: StockNativePageRepository = StockNativePageRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(StockNativePageUiState())
    val uiState: StateFlow<StockNativePageUiState> = _uiState

    private var rankingJob: Job? = null
    private var hotJob: Job? = null
    private var conceptJob: Job? = null
    private var sectorJob: Job? = null
    private var sectorKlineJob: Job? = null
    private var indexJob: Job? = null
    private var discussionJob: Job? = null
    private var postJob: Job? = null
    private var commentsJob: Job? = null
    private var sectorRequestId = 0
    private var discussionRequestId = 0
    private var postRequestId = 0

    fun loadRanking(type: StockNativeRankingType, force: Boolean = false) {
        val current = _uiState.value
        if (!force && current.rankingType == type && current.rankingItems.isNotEmpty()) return
        rankingJob?.cancel()
        rankingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    rankingType = type,
                    rankingItems = if (it.rankingType == type) it.rankingItems else emptyList(),
                    rankingLoading = true,
                    rankingError = null
                )
            }
            val result = withContext(Dispatchers.IO) { repository.loadRanking(type) }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { items -> state.copy(rankingItems = items, rankingLoading = false) },
                    onFailure = { error -> state.copy(rankingLoading = false, rankingError = error.message ?: "榜单加载失败") }
                )
            }
        }
    }

    fun loadHot(type: StockNativeHotType, force: Boolean = false) {
        val current = _uiState.value
        if (!force && current.hotSnapshot.type == type && current.hotSnapshot.items.isNotEmpty()) return
        hotJob?.cancel()
        hotJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    hotSnapshot = if (it.hotSnapshot.type == type) it.hotSnapshot else StockNativeHotSnapshot(type = type),
                    hotLoading = true,
                    hotError = null
                )
            }
            val result = withContext(Dispatchers.IO) { repository.loadHot(type) }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { snapshot -> state.copy(hotSnapshot = snapshot, hotLoading = false) },
                    onFailure = { error -> state.copy(hotLoading = false, hotError = error.message ?: "热点加载失败") }
                )
            }
        }
    }

    fun loadConceptSectors(force: Boolean = false) {
        val current = _uiState.value
        if (!force && current.conceptSectors.isNotEmpty()) return
        conceptJob?.cancel()
        conceptJob = viewModelScope.launch {
            _uiState.update { it.copy(conceptLoading = true, conceptError = null) }
            val result = withContext(Dispatchers.IO) { repository.loadSectorCatalog("concept", 40) }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { items -> state.copy(conceptSectors = items, conceptLoading = false) },
                    onFailure = { error -> state.copy(conceptLoading = false, conceptError = error.message ?: "概念板块加载失败") }
                )
            }
        }
    }

    fun loadSector(code: String, force: Boolean = false) {
        val normalized = code.trim().uppercase()
        val current = _uiState.value
        if (!force && current.sectorDetail?.code == normalized && current.sectorConstituents.isNotEmpty()) return
        sectorJob?.cancel()
        sectorKlineJob?.cancel()
        val requestId = ++sectorRequestId
        sectorJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sectorDetail = if (it.sectorDetail?.code == normalized) it.sectorDetail else null,
                    sectorConstituents = emptyList(),
                    sectorConstituentPage = 0,
                    sectorConstituentTotal = 0,
                    sectorHasMore = false,
                    sectorLoading = true,
                    sectorConstituentLoading = true,
                    sectorError = null,
                    sectorTab = "minute",
                    sectorKlines = emptyMap(),
                    sectorKlineLoading = false
                )
            }
            val pair = coroutineScope {
                val detail = async(Dispatchers.IO) { repository.loadSectorDetail(normalized) }
                val constituents = async(Dispatchers.IO) { repository.loadSectorConstituents(normalized, 1) }
                detail.await() to constituents.await()
            }
            if (requestId != sectorRequestId) return@launch
            _uiState.update { state ->
                val detail = pair.first.getOrNull()
                val page = pair.second.getOrNull()
                state.copy(
                    sectorDetail = detail ?: state.sectorDetail,
                    sectorConstituents = page?.items ?: emptyList(),
                    sectorConstituentPage = page?.page ?: 0,
                    sectorConstituentTotal = page?.total ?: 0,
                    sectorHasMore = page?.hasMore ?: false,
                    sectorLoading = false,
                    sectorConstituentLoading = false,
                    sectorError = pair.first.exceptionOrNull()?.message ?: pair.second.exceptionOrNull()?.message
                )
            }
        }
    }

    fun loadMoreSectorConstituents() {
        val state = _uiState.value
        val code = state.sectorDetail?.code ?: return
        if (state.sectorConstituentLoading || !state.sectorHasMore) return
        val nextPage = state.sectorConstituentPage + 1
        val requestId = sectorRequestId
        viewModelScope.launch {
            _uiState.update { it.copy(sectorConstituentLoading = true, sectorError = null) }
            val result = withContext(Dispatchers.IO) { repository.loadSectorConstituents(code, nextPage) }
            if (requestId != sectorRequestId) return@launch
            _uiState.update { current ->
                result.fold(
                    onSuccess = { page ->
                        val seen = current.sectorConstituents.mapTo(hashSetOf()) { it.code }
                        current.copy(
                            sectorConstituents = current.sectorConstituents + page.items.filter { seen.add(it.code) },
                            sectorConstituentPage = page.page,
                            sectorConstituentTotal = page.total,
                            sectorHasMore = page.hasMore,
                            sectorConstituentLoading = false
                        )
                    },
                    onFailure = { error -> current.copy(sectorConstituentLoading = false, sectorError = error.message ?: "成分股加载失败") }
                )
            }
        }
    }

    fun selectSectorTab(tab: String) {
        val normalized = when (tab) {
            "daily", "weekly", "monthly" -> tab
            else -> "minute"
        }
        _uiState.update { it.copy(sectorTab = normalized) }
        if (normalized != "minute") loadSectorKline(normalized)
    }

    fun loadSectorKline(period: String, force: Boolean = false) {
        val state = _uiState.value
        val code = state.sectorDetail?.code ?: return
        val key = "$code:$period"
        if (!force && state.sectorKlines[key]?.isNotEmpty() == true) return
        sectorKlineJob?.cancel()
        val requestId = sectorRequestId
        sectorKlineJob = viewModelScope.launch {
            _uiState.update { it.copy(sectorKlineLoading = true, sectorError = null) }
            val result = withContext(Dispatchers.IO) { repository.loadSectorKline(code, period) }
            if (requestId != sectorRequestId) return@launch
            _uiState.update { current ->
                result.fold(
                    onSuccess = { rows -> current.copy(sectorKlines = current.sectorKlines + (key to rows), sectorKlineLoading = false) },
                    onFailure = { error -> current.copy(sectorKlineLoading = false, sectorError = error.message ?: "板块K线加载失败") }
                )
            }
        }
    }

    fun loadIndex(code: String, force: Boolean = false) {
        val normalized = code.filter(Char::isDigit)
        val current = _uiState.value
        if (!force && current.indexDetail?.code == normalized) return
        indexJob?.cancel()
        indexJob = viewModelScope.launch {
            _uiState.update { it.copy(indexLoading = true, indexError = null, indexDetail = if (it.indexDetail?.code == normalized) it.indexDetail else null) }
            val result = withContext(Dispatchers.IO) { repository.loadIndexDetail(normalized) }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { detail -> state.copy(indexDetail = detail, indexLoading = false) },
                    onFailure = { error -> state.copy(indexLoading = false, indexError = error.message ?: "指数详情加载失败") }
                )
            }
        }
    }

    fun loadDiscussions(code: String, reset: Boolean = false) {
        val normalized = code.filter(Char::isDigit)
        val current = _uiState.value
        if (current.discussionLoading) return
        val newCode = current.discussionCode != normalized
        val page = if (reset || newCode) 1 else current.discussionPage + 1
        val requestId = if (newCode || reset) ++discussionRequestId else discussionRequestId
        discussionJob?.cancel()
        discussionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    discussionCode = normalized,
                    discussions = if (newCode || reset) emptyList() else it.discussions,
                    discussionPage = if (newCode || reset) 0 else it.discussionPage,
                    discussionHasMore = if (newCode || reset) false else it.discussionHasMore,
                    discussionLoading = true,
                    discussionError = null
                )
            }
            val result = withContext(Dispatchers.IO) { repository.loadDiscussions(normalized, page) }
            if (requestId != discussionRequestId) return@launch
            _uiState.update { state ->
                result.fold(
                    onSuccess = { snapshot ->
                        val base = if (page == 1) emptyList() else state.discussions
                        val seen = base.mapTo(hashSetOf()) { it.postId }
                        state.copy(
                            discussionName = snapshot.name,
                            discussions = base + snapshot.posts.filter { seen.add(it.postId) },
                            discussionPage = snapshot.page,
                            discussionHasMore = snapshot.hasMore,
                            discussionSourceUrl = snapshot.sourcePageUrl,
                            discussionLoading = false
                        )
                    },
                    onFailure = { error -> state.copy(discussionLoading = false, discussionError = error.message ?: "社区加载失败") }
                )
            }
        }
    }

    fun loadPost(code: String, postId: String, force: Boolean = false) {
        val normalized = code.filter(Char::isDigit)
        val current = _uiState.value
        if (!force && current.postDetail?.post?.postId == postId) return
        postJob?.cancel()
        commentsJob?.cancel()
        val requestId = ++postRequestId
        postJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    postDetail = null,
                    postLoading = true,
                    postError = null,
                    comments = emptyList(),
                    commentsPage = 0,
                    commentsTotal = 0,
                    commentsHasMore = false,
                    commentsLoaded = false,
                    commentsLoading = false,
                    commentsError = null
                )
            }
            val result = withContext(Dispatchers.IO) { repository.loadDiscussionPost(normalized, postId) }
            if (requestId != postRequestId) return@launch
            _uiState.update { state ->
                result.fold(
                    onSuccess = { detail -> state.copy(postDetail = detail, postLoading = false) },
                    onFailure = { error -> state.copy(postLoading = false, postError = error.message ?: "帖子正文加载失败") }
                )
            }
        }
    }

    fun loadComments(force: Boolean = false) {
        val state = _uiState.value
        val post = state.postDetail?.post ?: return
        val code = state.postDetail.code
        if (state.commentsLoading) return
        if (!force && state.commentsLoaded && !state.commentsHasMore) return
        val page = if (force || !state.commentsLoaded) 1 else state.commentsPage + 1
        val requestId = postRequestId
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    comments = if (page == 1) emptyList() else it.comments,
                    commentsLoading = true,
                    commentsLoaded = true,
                    commentsError = null
                )
            }
            val result = withContext(Dispatchers.IO) {
                repository.loadDiscussionComments(code, post.postId, page)
            }
            if (requestId != postRequestId) return@launch
            _uiState.update { current ->
                result.fold(
                    onSuccess = { detail ->
                        val base = if (page == 1) emptyList() else current.comments
                        val seen = base.mapTo(hashSetOf()) { it.commentId }
                        current.copy(
                            postDetail = if (current.postDetail?.post?.content.isNullOrBlank() && detail.post.content.isNotBlank()) detail else current.postDetail,
                            comments = base + detail.comments.filter { seen.add(it.commentId) },
                            commentsPage = detail.commentPage,
                            commentsTotal = detail.commentTotal,
                            commentsHasMore = detail.hasMoreComments,
                            commentsLoading = false
                        )
                    },
                    onFailure = { error -> current.copy(commentsLoading = false, commentsError = error.message ?: "评论加载失败") }
                )
            }
        }
    }

    override fun onCleared() {
        rankingJob?.cancel()
        hotJob?.cancel()
        conceptJob?.cancel()
        sectorJob?.cancel()
        sectorKlineJob?.cancel()
        indexJob?.cancel()
        discussionJob?.cancel()
        postJob?.cancel()
        commentsJob?.cancel()
        super.onCleared()
    }
}
