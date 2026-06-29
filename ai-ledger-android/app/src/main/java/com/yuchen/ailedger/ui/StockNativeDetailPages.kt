package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.StockMarketUiState
import com.yuchen.ailedger.StockNativePageUiState
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.StockNativeDiscussionComment
import com.yuchen.ailedger.model.StockNativeDiscussionPostSummary
import java.util.Locale

@Composable
internal fun StockNativeDetailScreen(
    appState: AssistantUiState,
    marketUi: StockMarketUiState,
    nativeUi: StockNativePageUiState,
    startInCommunity: Boolean,
    isWatched: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleWatch: () -> Unit,
    onSelectTab: (String) -> Unit,
    onLoadCommunity: (Boolean) -> Unit,
    onLoadMoreCommunity: () -> Unit,
    onOpenPost: (String) -> Unit
) {
    val code = marketUi.stock.quote.code
    var mode by remember(code) { mutableStateOf(if (startInCommunity) "community" else "market") }
    var sort by remember(code) { mutableStateOf("latest") }
    var readOnlyMessage by remember { mutableStateOf(false) }

    LaunchedEffect(mode, code) {
        if (mode == "community" && code.length == 6) {
            val reset = nativeUi.discussionCode != code || nativeUi.discussions.isEmpty()
            onLoadCommunity(reset)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NativeDetailQuoteCard(
            marketUi = marketUi,
            isWatched = isWatched,
            onBack = onBack,
            onToggleWatch = onToggleWatch,
            modifier = Modifier.fillMaxWidth().height(194.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(17.dp))
                .border(1.dp, Color.White.copy(alpha = 0.075f), RoundedCornerShape(17.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StockNativePill("行情", mode == "market", Modifier.weight(1f).fillMaxHeight(), fontSize = 10) {
                mode = "market"
            }
            StockNativePill("社区", mode == "community", Modifier.weight(1f).fillMaxHeight(), fontSize = 10) {
                mode = "community"
            }
        }

        if (mode == "market") {
            StockNativeGlassPanel(
                modifier = Modifier.fillMaxWidth().weight(1f),
                radius = 30.dp,
                contentPadding = 13.dp
            ) {
                StockWebMirrorTerminal(
                    appState = appState,
                    ui = marketUi,
                    onSelectTab = onSelectTab
                )
            }
        } else {
            StockNativeCommunityPanel(
                ui = nativeUi,
                code = code,
                name = marketUi.stock.quote.name,
                sort = sort,
                onSortChange = { sort = it },
                onRefresh = { onLoadCommunity(true) },
                onLoadMore = onLoadMoreCommunity,
                onOpenPost = onOpenPost,
                onCompose = { readOnlyMessage = true },
                readOnlyMessage = readOnlyMessage,
                onDismissMessage = { readOnlyMessage = false },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun NativeDetailQuoteCard(
    marketUi: StockMarketUiState,
    isWatched: Boolean,
    onBack: () -> Unit,
    onToggleWatch: () -> Unit,
    modifier: Modifier
) {
    val quote = marketUi.stock.quote
    val tone = if (quote.isRising) StockRise else StockFall
    StockNativeGlassPanel(
        modifier = modifier,
        radius = 28.dp,
        contentPadding = 14.dp
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.075f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = Color.White.copy(alpha = 0.96f), fontSize = 29.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        quote.name.ifBlank { "--" },
                        color = Color.White,
                        fontSize = 27.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${quote.code.ifBlank { "------" }} · ${quote.market.ifBlank { "--" }}",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
                Column(
                    modifier = Modifier.width(112.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(
                                if (isWatched) StockYellow.copy(alpha = 0.12f) else StockAqua.copy(alpha = 0.085f),
                                StockPillShape
                            )
                            .border(
                                1.dp,
                                if (isWatched) StockYellow.copy(alpha = 0.25f) else StockAqua.copy(alpha = 0.18f),
                                StockPillShape
                            )
                            .clickable(onClick = onToggleWatch),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(if (isWatched) "★" else "☆", color = if (isWatched) StockYellow else StockAqua, fontSize = 13.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(if (isWatched) "已自选" else "加自选", color = if (isWatched) StockYellow else StockAqua, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                    Text(
                        marketUi.stock.dataSourceLabel.ifBlank { "等待真实后端行情" },
                        color = StockAqua.copy(alpha = 0.55f),
                        fontSize = 7.sp,
                        lineHeight = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            StockDivider()

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.width(136.dp).fillMaxHeight().padding(start = 2.dp, end = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        quote.price.ifBlank { "--" },
                        color = tone,
                        fontSize = 42.sp,
                        lineHeight = 43.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    Text(
                        "${quote.changeAmount.ifBlank { "--" }}  ${quote.changePercent.ifBlank { "--" }}",
                        color = tone,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    Text(
                        "昨收 ${if (quote.previousClose > 0f) String.format(Locale.US, "%.2f", quote.previousClose) else "--"}",
                        color = Color.White.copy(alpha = 0.36f),
                        fontSize = 8.sp,
                        lineHeight = 11.sp,
                        maxLines = 1
                    )
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.085f)))
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    NativeDetailMetricRow(
                        listOf(
                            Triple("高", quote.high, priceTone(quote.high, quote.previousClose)),
                            Triple("市值", quote.totalMarketValue, Color.White.copy(alpha = 0.92f)),
                            Triple("量比", quote.volumeRatio, if ((quote.volumeRatio.toFloatOrNull() ?: 0f) >= 1f) StockRise else StockFall)
                        ),
                        Modifier.weight(1f)
                    )
                    NativeDetailMetricRow(
                        listOf(
                            Triple("低", quote.low, priceTone(quote.low, quote.previousClose)),
                            Triple("流通", quote.floatMarketValue, Color.White.copy(alpha = 0.92f)),
                            Triple("换手", quote.turnoverRate, Color.White.copy(alpha = 0.92f))
                        ),
                        Modifier.weight(1f)
                    )
                    NativeDetailMetricRow(
                        listOf(
                            Triple("开", quote.open, priceTone(quote.open, quote.previousClose)),
                            Triple("市盈TTM", quote.peTtm, Color.White.copy(alpha = 0.92f)),
                            Triple("成交额", quote.amount, Color.White.copy(alpha = 0.92f))
                        ),
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeDetailMetricRow(
    metrics: List<Triple<String, String, Color>>,
    modifier: Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        metrics.forEach { (label, value, tone) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.025f), RoundedCornerShape(9.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.045f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(label, color = Color.White.copy(alpha = 0.36f), fontSize = 7.sp, lineHeight = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(value.ifBlank { "--" }, color = tone, fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StockNativeCommunityPanel(
    ui: StockNativePageUiState,
    code: String,
    name: String,
    sort: String,
    onSortChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenPost: (String) -> Unit,
    onCompose: () -> Unit,
    readOnlyMessage: Boolean,
    onDismissMessage: () -> Unit,
    modifier: Modifier
) {
    val posts = remember(ui.discussions, sort) {
        if (sort == "hot") {
            ui.discussions.sortedByDescending { it.commentCount * 500L + it.readCount }
        } else {
            ui.discussions
        }
    }
    Box(modifier) {
        StockNativeGlassPanel(Modifier.fillMaxSize(), radius = 30.dp, contentPadding = 0.dp) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("社区", color = Color.White.copy(alpha = 0.96f), fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text("$name（$code）· 东方财富股吧只读社区", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    StockNativePill("刷新", false, Modifier.width(62.dp).height(31.dp), fontSize = 10, onClick = onRefresh)
                }
                StockDivider()
                Row(
                    Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    NativeCommunitySortButton("最新发布", sort == "latest") { onSortChange("latest") }
                    NativeCommunitySortButton("热门", sort == "hot") { onSortChange("hot") }
                    Spacer(Modifier.weight(1f))
                    Text("${ui.discussions.size.takeIf { it > 0 } ?: "等待数据"}", color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp, modifier = Modifier.padding(bottom = 13.dp))
                }
                StockDivider()
                if (posts.isEmpty()) {
                    StockLoadingOrError(ui.discussionLoading, ui.discussionError, "当前股票暂未返回可展示的社区帖子", Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(posts, key = { it.postId }) { post ->
                            NativeCommunityPostCard(post, name, code, onOpenPost)
                        }
                        item {
                            StockNativePill(
                                text = when {
                                    ui.discussionLoading -> "加载中…"
                                    ui.discussionHasMore -> "加载更多社区帖子"
                                    else -> "已加载当前社区内容"
                                },
                                active = ui.discussionHasMore,
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                fontSize = 10,
                                onClick = { if (ui.discussionHasMore && !ui.discussionLoading) onLoadMore() }
                            )
                        }
                    }
                }
                Text(
                    when {
                        ui.discussionLoading -> "正在连接东方财富股吧，只读抓取不会参与行情刷新。"
                        !ui.discussionError.isNullOrBlank() -> ui.discussionError
                        ui.discussions.isNotEmpty() -> "讨论 ${ui.discussions.size} 条 · 社区列表按需加载"
                        else -> "社区首次打开时才加载，不增加个股行情首屏开销。"
                    },
                    color = Color.White.copy(alpha = 0.36f),
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 40.dp)
                .size(50.dp)
                .background(Brush.linearGradient(listOf(Color(0xFFFF405F), Color(0xFFFF1F49))), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.13f), CircleShape)
                .clickable(onClick = onCompose),
            contentAlignment = Alignment.Center
        ) {
            Text("✎", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }

        if (readOnlyMessage) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 94.dp)
                    .background(Color(0xF2040713), StockPillShape)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), StockPillShape)
                    .clickable(onClick = onDismissMessage)
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            ) {
                Text("当前为只读社区，暂不支持登录、发帖或回复", color = Color.White.copy(alpha = 0.82f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun NativeCommunitySortButton(text: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(if (text.length > 2) 70.dp else 44.dp).fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(text, color = Color.White.copy(alpha = if (active) 0.94f else 0.44f), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 10.dp))
        Box(Modifier.width(if (active) 30.dp else 0.dp).height(3.dp).background(if (active) Color(0xFFFF3458) else Color.Transparent, StockPillShape))
    }
}

@Composable
private fun NativeCommunityPostCard(
    post: StockNativeDiscussionPostSummary,
    stockName: String,
    stockCode: String,
    onOpenPost: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF214182A), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.055f), RoundedCornerShape(16.dp))
            .clickable { onOpenPost(post.postId) }
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StockTextAvatar(post.author, Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(post.author, color = Color.White.copy(alpha = 0.93f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(post.updatedAt.ifBlank { "时间未知" }, color = Color.White.copy(alpha = 0.40f), fontSize = 9.sp)
            }
            Text("•••", color = Color.White.copy(alpha = 0.35f), fontSize = 17.sp)
        }
        Text(
            buildString {
                append("$")
                append(stockName)
                append("(")
                append(stockCode)
                append(")$ ")
                append(post.title.replace(Regex("^\\$[^$]{1,48}\\$\\s*"), ""))
            },
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 13.sp,
            lineHeight = 21.sp
        )
        Row(
            Modifier.fillMaxWidth().height(34.dp).border(0.dp, Color.Transparent),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NativeFeedAction("↗", "分享", Modifier.weight(1f))
            NativeFeedAction("◯", compactCount(post.commentCount), Modifier.weight(1f))
            NativeFeedAction("♡", "阅读 ${compactCount(post.readCount)}", Modifier.weight(1f))
        }
        Text(
            if (post.commentCount > 0) "网友讨论：已有 ${compactCount(post.commentCount)} 条评论，进入详情后自动加载" else "点击进入独立帖子详情页查看正文",
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 10.sp,
            lineHeight = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.033f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun NativeFeedAction(icon: String, label: String, modifier: Modifier) {
    Row(modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(icon, color = Color.White.copy(alpha = 0.60f), fontSize = 13.sp)
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
internal fun StockNativePostScreen(
    ui: StockNativePageUiState,
    code: String,
    postId: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadComments: (Boolean) -> Unit
) {
    val page = ui.postDetail
    val post = page?.post

    LaunchedEffect(post?.postId) {
        if (post != null && !ui.commentsLoaded && !ui.commentsLoading) {
            onLoadComments(false)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { StockNativePageHeader("帖子详情", onBack, onRefresh, ui.postLoading) }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 28.dp) {
                if (post == null) {
                    StockLoadingOrError(ui.postLoading, ui.postError, "帖子正文暂不可用", Modifier.height(220.dp))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            StockTextAvatar(post.author, Modifier.size(44.dp))
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(post.author, color = Color.White.copy(alpha = 0.94f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                                Text(post.publishedAt.ifBlank { "时间未知" }, color = Color.White.copy(alpha = 0.40f), fontSize = 10.sp)
                            }
                            Text("•••", color = Color.White.copy(alpha = 0.34f), fontSize = 17.sp)
                        }
                        Text("$${page.name.ifBlank { code }}($code)$", color = Color(0xFF7DA2FF), fontSize = 13.sp, fontWeight = FontWeight.Black)
                        Text(post.title.ifBlank { "股吧讨论" }, color = Color.White.copy(alpha = 0.96f), fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
                        Text(post.content.ifBlank { "该帖子没有返回可展示的纯文本正文。" }, color = Color.White.copy(alpha = 0.80f), fontSize = 13.sp, lineHeight = 22.sp)
                        Text(
                            "社区内容来自公开股吧，仅代表发布者个人观点，不构成投资建议。",
                            color = StockYellow.copy(alpha = 0.66f),
                            fontSize = 9.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.fillMaxWidth().background(StockYellow.copy(alpha = 0.045f), RoundedCornerShape(13.dp)).border(1.dp, StockYellow.copy(alpha = 0.10f), RoundedCornerShape(13.dp)).padding(horizontal = 10.dp, vertical = 9.dp)
                        )
                        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                            NativeFeedAction("↗", "分享", Modifier.weight(1f))
                            Row(
                                Modifier.weight(1f).fillMaxHeight().clickable { onLoadComments(ui.commentsLoaded || !ui.commentsError.isNullOrBlank()) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text("◯", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    when {
                                        ui.commentsLoading -> "加载中"
                                        !ui.commentsError.isNullOrBlank() -> "重试评论"
                                        ui.commentsLoaded -> "评论 ${ui.comments.size}"
                                        else -> "查看评论"
                                    },
                                    color = when {
                                        ui.commentsLoading -> StockYellow
                                        !ui.commentsError.isNullOrBlank() -> StockRise
                                        ui.commentsLoaded -> StockAqua
                                        else -> Color.White.copy(alpha = 0.48f)
                                    },
                                    fontSize = 10.sp
                                )
                            }
                            NativeFeedAction("♡", "赞 ${compactCount(post.likeCount)}", Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 28.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    StockSectionTitle(
                        "网友评论",
                        "进入帖子详情后自动加载，失败时可以直接重试",
                        when {
                            ui.commentsLoading -> "加载中"
                            !ui.commentsError.isNullOrBlank() -> "加载失败"
                            ui.commentsLoaded -> "${ui.comments.size} 条"
                            else -> "等待加载"
                        }
                    )
                    StockDivider()
                    when {
                        ui.commentsLoading && ui.comments.isEmpty() -> {
                            StockLoadingOrError(true, null, "正在加载网友评论", Modifier.height(160.dp))
                        }
                        !ui.commentsError.isNullOrBlank() && ui.comments.isEmpty() -> {
                            Column(
                                Modifier.fillMaxWidth().height(184.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("评论加载失败", color = StockRise, fontSize = 14.sp, fontWeight = FontWeight.Black)
                                Text(
                                    ui.commentsError,
                                    color = Color.White.copy(alpha = 0.50f),
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                                StockNativePill("重新加载评论", true, Modifier.width(126.dp).height(38.dp), fontSize = 10) {
                                    onLoadComments(true)
                                }
                            }
                        }
                        !ui.commentsLoaded -> {
                            Column(
                                Modifier.fillMaxWidth().height(170.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("正在准备网友评论", color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                                Text("评论会在正文加载完成后自动请求", color = Color.White.copy(alpha = 0.44f), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                                StockNativePill("立即加载", true, Modifier.width(108.dp).height(36.dp).padding(top = 13.dp), fontSize = 10) { onLoadComments(false) }
                            }
                        }
                        ui.comments.isEmpty() -> {
                            Column(
                                Modifier.fillMaxWidth().height(170.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("当前页面没有返回公开评论", color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                                Text("可以重新请求一次，确认不是临时上游异常", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                                StockNativePill("重新检查评论", true, Modifier.width(126.dp).height(38.dp), fontSize = 10) { onLoadComments(true) }
                            }
                        }
                        else -> {
                            ui.comments.forEach { comment ->
                                NativeCommentRow(comment)
                                StockDivider()
                            }
                            if (!ui.commentsError.isNullOrBlank()) {
                                Text(
                                    "后续评论加载失败：${ui.commentsError}",
                                    color = StockRise.copy(alpha = 0.80f),
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            if (ui.commentsHasMore) {
                                StockNativePill(
                                    text = if (ui.commentsLoading) "加载中…" else "加载更多评论",
                                    active = true,
                                    modifier = Modifier.fillMaxWidth().height(38.dp),
                                    fontSize = 10,
                                    onClick = { if (!ui.commentsLoading) onLoadComments(false) }
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                when {
                    ui.postLoading -> "正在加载正文：评论会在正文完成后自动请求。"
                    ui.commentsLoading -> "正在加载评论：独立请求不会阻塞正文显示。"
                    !ui.postError.isNullOrBlank() -> "正文加载失败：${ui.postError}"
                    !ui.commentsError.isNullOrBlank() -> "评论加载失败：${ui.commentsError}"
                    ui.commentsLoaded -> "正文与评论已加载 · 评论 ${ui.comments.size} 条"
                    else -> "帖子正文已加载 · 正在准备评论请求"
                },
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun NativeCommentRow(comment: StockNativeDiscussionComment) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(comment.author, color = StockAqua.copy(alpha = 0.84f), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(comment.publishedAt, color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp)
        }
        Text(comment.content, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp, lineHeight = 19.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("赞 ${compactCount(comment.likeCount)}", color = Color.White.copy(alpha = 0.40f), fontSize = 9.sp)
            Text("回复 ${compactCount(comment.replyCount)}", color = Color.White.copy(alpha = 0.40f), fontSize = 9.sp)
        }
        if (comment.replies.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.027f), RoundedCornerShape(0.dp, 10.dp, 10.dp, 0.dp)).border(width = 2.dp, color = StockAqua.copy(alpha = 0.15f), shape = RoundedCornerShape(0.dp, 10.dp, 10.dp, 0.dp)).padding(horizontal = 11.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                comment.replies.take(8).forEach { reply ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(reply.author, color = StockAqua.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text(reply.content, color = Color.White.copy(alpha = 0.66f), fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}

private fun priceTone(value: String, previousClose: Float): Color {
    val number = value.replace(",", "").toFloatOrNull() ?: return Color.White.copy(alpha = 0.86f)
    if (previousClose <= 0f) return Color.White.copy(alpha = 0.86f)
    return when {
        number > previousClose -> StockRise
        number < previousClose -> StockFall
        else -> Color.White.copy(alpha = 0.86f)
    }
}
