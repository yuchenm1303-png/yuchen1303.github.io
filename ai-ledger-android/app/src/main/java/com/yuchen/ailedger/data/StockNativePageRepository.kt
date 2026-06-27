package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.StockKLinePoint
import com.yuchen.ailedger.model.StockMarketBreadth
import com.yuchen.ailedger.model.StockMarketSentiment
import com.yuchen.ailedger.model.StockMinutePoint
import com.yuchen.ailedger.model.StockModuleMeta
import com.yuchen.ailedger.model.StockNativeConstituent
import com.yuchen.ailedger.model.StockNativeConstituentPage
import com.yuchen.ailedger.model.StockNativeDiscussionComment
import com.yuchen.ailedger.model.StockNativeDiscussionList
import com.yuchen.ailedger.model.StockNativeDiscussionPost
import com.yuchen.ailedger.model.StockNativeDiscussionPostPage
import com.yuchen.ailedger.model.StockNativeDiscussionPostSummary
import com.yuchen.ailedger.model.StockNativeDiscussionReply
import com.yuchen.ailedger.model.StockNativeHotItem
import com.yuchen.ailedger.model.StockNativeHotSnapshot
import com.yuchen.ailedger.model.StockNativeHotType
import com.yuchen.ailedger.model.StockNativeIndexDetail
import com.yuchen.ailedger.model.StockNativeIndexLink
import com.yuchen.ailedger.model.StockNativeQuote
import com.yuchen.ailedger.model.StockNativeRankingItem
import com.yuchen.ailedger.model.StockNativeRankingType
import com.yuchen.ailedger.model.StockNativeSectorBreadth
import com.yuchen.ailedger.model.StockNativeSectorDetail
import com.yuchen.ailedger.model.StockNativeSectorLink
import com.yuchen.ailedger.model.StockSectorSnapshot
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class StockNativePageRepository(
    private val proxyBaseUrl: String = "https://ai-ledger-stock-proxy.onrender.com"
) {
    fun loadRanking(type: StockNativeRankingType, limit: Int = 100): Result<List<StockNativeRankingItem>> = runCatching {
        val root = getJson("/api/stock/a-share/rankings?type=${type.wire}&limit=${limit.coerceIn(1, 100)}", 24_000, 1_000)
        val rows = root.optJSONArray("items") ?: JSONArray()
        buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val code = text(item, "code")
                val name = text(item, "name")
                if (code.isBlank() || name.isBlank()) continue
                add(
                    StockNativeRankingItem(
                        rank = int(item, "rank") ?: index + 1,
                        name = name,
                        code = code,
                        industry = text(item, "industry"),
                        price = text(item, "price").ifBlank { "--" },
                        amount = text(item, "amount").ifBlank { "--" },
                        turnoverRate = text(item, "turnoverRate").ifBlank { "--" },
                        volumeRatio = text(item, "volumeRatio").ifBlank { "--" },
                        changeSpeed = text(item, "changeSpeed").ifBlank { "--" },
                        mainInflow = text(item, "mainInflow").ifBlank { "--" },
                        changePercent = text(item, "changePercent").ifBlank { "--" }
                    )
                )
            }
        }
    }

    fun loadHot(type: StockNativeHotType, limit: Int = 100): Result<StockNativeHotSnapshot> = runCatching {
        val root = getJson("/api/stock/a-share/hot/ranking?type=${type.wire}&limit=${limit.coerceIn(1, 100)}", 28_000, 2_000)
        val rows = root.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val code = text(item, "code")
                val name = text(item, "name")
                if (code.isBlank() || name.isBlank()) continue
                add(
                    StockNativeHotItem(
                        rank = int(item, "rank") ?: index + 1,
                        currentRank = int(item, "currentRank") ?: int(item, "rank") ?: index + 1,
                        rankChange = int(item, "rankChange"),
                        code = code,
                        name = name,
                        market = text(item, "market"),
                        industry = text(item, "industry"),
                        price = text(item, "price").ifBlank { "--" },
                        changePercent = text(item, "changePercent").ifBlank { "--" },
                        amount = text(item, "amount").ifBlank { "--" }
                    )
                )
            }
        }
        val summary = root.optJSONObject("summary") ?: JSONObject()
        StockNativeHotSnapshot(
            type = type,
            items = items,
            risingCount = int(summary, "risingCount") ?: 0,
            fallingCount = int(summary, "fallingCount") ?: 0,
            quoteMatchCount = int(summary, "quoteMatchCount") ?: 0,
            sourcePageUrl = text(root, "sourcePageUrl"),
            dataSourceLabel = text(root, "dataSourceLabel"),
            updatedAt = text(root, "updatedAt")
        )
    }

    fun loadSectorCatalog(type: String, limit: Int = 50): Result<List<StockSectorSnapshot>> = runCatching {
        val normalized = if (type == "concept") "concept" else "industry"
        val root = getJson("/api/stock/a-share/sectors?type=$normalized&limit=${limit.coerceIn(1, 200)}", 24_000, 4_000)
        val rows = root.optJSONArray("items") ?: root.optJSONArray("data") ?: JSONArray()
        buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val code = text(item, "sectorCode", "code")
                val name = text(item, "sectorName", "name")
                if (code.isBlank() || name.isBlank()) continue
                add(
                    StockSectorSnapshot(
                        sectorCode = code,
                        sectorName = name,
                        type = text(item, "type").ifBlank { normalized },
                        changePercent = text(item, "changePercent", "pct").ifBlank { "--" },
                        upCount = int(item, "upCount"),
                        downCount = int(item, "downCount"),
                        flatCount = int(item, "flatCount"),
                        leaderName = text(item, "leaderName"),
                        leaderChangePercent = text(item, "leaderChangePercent"),
                        amount = text(item, "amount"),
                        turnoverRate = text(item, "turnoverRate"),
                        mainInflow = text(item, "mainInflow"),
                        heatRank = int(item, "heatRank"),
                        updatedAt = text(item, "updatedAt")
                    )
                )
            }
        }
    }

    fun loadSectorDetail(query: String): Result<StockNativeSectorDetail> = runCatching {
        val root = getJson("/api/stock/a-share/sector/detail?query=${encode(query)}", 28_000, 1_000)
        val quote = parseQuote(root.optJSONObject("quote"))
        val breadth = root.optJSONObject("breadth") ?: JSONObject()
        StockNativeSectorDetail(
            code = text(root, "code").ifBlank { query },
            name = text(root, "name").ifBlank { quote.name },
            type = text(root, "type").ifBlank { "industry" },
            quote = quote,
            minutePoints = parseMinutePoints(root.optJSONArray("minutePoints")),
            breadth = StockNativeSectorBreadth(
                upCount = int(breadth, "upCount"),
                downCount = int(breadth, "downCount"),
                flatCount = int(breadth, "flatCount"),
                redRate = double(breadth, "redRate"),
                leaderName = text(breadth, "leaderName"),
                leaderChangePercent = text(breadth, "leaderChangePercent").ifBlank { "--" },
                mainInflow = text(breadth, "mainInflow").ifBlank { "--" }
            ),
            relatedSectors = parseSectorLinks(root.optJSONArray("relatedSectors")),
            dataSourceLabel = text(root, "dataSourceLabel"),
            updatedAt = text(root, "updatedAt")
        )
    }

    fun loadSectorConstituents(query: String, page: Int, pageSize: Int = 20): Result<StockNativeConstituentPage> = runCatching {
        val root = getJson(
            "/api/stock/a-share/sector/constituents?query=${encode(query)}&page=${page.coerceAtLeast(1)}&pageSize=${pageSize.coerceIn(1, 50)}",
            28_000,
            2_000
        )
        val rows = root.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val code = text(item, "code")
                if (code.isBlank()) continue
                add(
                    StockNativeConstituent(
                        rank = int(item, "rank") ?: ((page - 1) * pageSize + index + 1),
                        code = code,
                        name = text(item, "name").ifBlank { code },
                        price = text(item, "price").ifBlank { "--" },
                        changePercent = text(item, "changePercent").ifBlank { "--" },
                        amount = text(item, "amount").ifBlank { "--" }
                    )
                )
            }
        }
        StockNativeConstituentPage(
            items = items,
            page = int(root, "page") ?: page,
            total = int(root, "total") ?: items.size,
            hasMore = boolean(root, "hasMore") ?: false
        )
    }

    fun loadSectorKline(query: String, period: String): Result<List<StockKLinePoint>> = runCatching {
        val normalized = when (period) {
            "weekly" -> "weekly"
            "monthly" -> "monthly"
            else -> "daily"
        }
        val limit = when (normalized) {
            "weekly" -> 320
            "monthly" -> 180
            else -> 600
        }
        val root = getJson(
            "/api/stock/a-share/kline?query=${encode(query)}&instrument=sector&period=$normalized&limit=$limit",
            30_000,
            10_000
        )
        parseKlinePoints(root.optJSONArray("kLinePoints"))
    }

    fun loadIndexDetail(query: String): Result<StockNativeIndexDetail> = runCatching {
        val root = getJson("/api/stock/a-share/index/detail?query=${encode(query)}", 34_000, 1_000)
        val breadth = root.optJSONObject("marketBreadth") ?: JSONObject()
        val sentiment = root.optJSONObject("sentiment") ?: JSONObject()
        StockNativeIndexDetail(
            code = text(root, "code").ifBlank { query },
            name = text(root, "name"),
            quote = parseQuote(root.optJSONObject("quote")),
            minutePoints = parseMinutePoints(root.optJSONArray("minutePoints")),
            fiveDayPoints = parseMinutePoints(root.optJSONArray("fiveDayPoints")),
            marketBreadth = StockMarketBreadth(
                upCount = int(breadth, "upCount"),
                downCount = int(breadth, "downCount"),
                flatCount = int(breadth, "flatCount"),
                limitUpCount = int(breadth, "limitUpCount"),
                limitDownCount = int(breadth, "limitDownCount"),
                brokenBoardCount = int(breadth, "brokenBoardCount"),
                brokenBoardRate = double(breadth, "brokenBoardRate"),
                maxConsecutiveBoards = int(breadth, "maxConsecutiveBoards"),
                redRate = double(breadth, "redRate"),
                medianChangePercent = double(breadth, "medianChangePercent"),
                marketAmount = text(breadth, "marketAmount").ifBlank { "--" },
                shszAmount = text(breadth, "shszAmount").ifBlank { "--" },
                bjAmount = text(breadth, "bjAmount").ifBlank { "--" },
                moneyMakingEffect = double(breadth, "moneyMakingEffect"),
                updatedAt = text(breadth, "updatedAt"),
                meta = StockModuleMeta()
            ),
            sentiment = StockMarketSentiment(
                temperature = double(sentiment, "sentimentTemperature", "temperature"),
                level = text(sentiment, "sentimentLevel", "level"),
                formula = text(sentiment, "formula"),
                redRate = double(sentiment, "redRate"),
                limitUpCount = int(sentiment, "limitUpCount"),
                moneyMakingEffect = double(sentiment, "moneyMakingEffect"),
                meta = StockModuleMeta()
            ),
            relatedIndices = parseIndexLinks(root.optJSONArray("relatedIndices")),
            dataSourceLabel = text(root, "dataSourceLabel"),
            updatedAt = text(root, "updatedAt")
        )
    }

    fun loadDiscussions(query: String, page: Int, pageSize: Int = 20): Result<StockNativeDiscussionList> = runCatching {
        val root = getJson(
            "/api/stock/a-share/discussions?query=${encode(query)}&page=${page.coerceAtLeast(1)}&pageSize=${pageSize.coerceIn(1, 30)}",
            30_000,
            2_000
        )
        val rows = root.optJSONArray("posts") ?: JSONArray()
        val posts = buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val postId = text(item, "postId")
                val title = text(item, "title")
                if (postId.isBlank() || title.isBlank()) continue
                add(
                    StockNativeDiscussionPostSummary(
                        postId = postId,
                        stockCode = text(item, "stockCode").ifBlank { query },
                        title = title,
                        author = text(item, "author").ifBlank { "股吧用户" },
                        updatedAt = text(item, "updatedAt"),
                        readCount = int(item, "readCount") ?: 0,
                        commentCount = int(item, "commentCount") ?: 0,
                        kind = text(item, "kind").ifBlank { "discussion" },
                        sourceUrl = text(item, "sourceUrl")
                    )
                )
            }
        }
        StockNativeDiscussionList(
            code = text(root, "code").ifBlank { query },
            name = text(root, "name"),
            page = int(root, "page") ?: page,
            posts = posts,
            hasMore = boolean(root, "hasMore") ?: false,
            sourcePageUrl = text(root, "sourcePageUrl")
        )
    }

    fun loadDiscussionPost(query: String, postId: String): Result<StockNativeDiscussionPostPage> = runCatching {
        val root = getJson(
            "/api/stock/a-share/discussion/post?query=${encode(query)}&postId=${encode(postId)}",
            34_000,
            4_000
        )
        StockNativeDiscussionPostPage(
            code = text(root, "code").ifBlank { query },
            name = text(root, "name"),
            post = parsePost(root.optJSONObject("post")),
            sourcePageUrl = text(root, "sourcePageUrl")
        )
    }

    fun loadDiscussionComments(
        query: String,
        postId: String,
        page: Int,
        pageSize: Int = 20
    ): Result<StockNativeDiscussionPostPage> = runCatching {
        val root = getJson(
            "/api/stock/a-share/discussion/detail?query=${encode(query)}&postId=${encode(postId)}&page=${page.coerceAtLeast(1)}&pageSize=${pageSize.coerceIn(1, 30)}",
            36_000,
            3_000
        )
        StockNativeDiscussionPostPage(
            code = text(root, "code").ifBlank { query },
            name = text(root, "name"),
            post = parsePost(root.optJSONObject("post")),
            comments = parseComments(root.optJSONArray("comments")),
            commentPage = int(root, "commentPage") ?: page,
            commentTotal = int(root, "commentTotalParsed", "commentCountParsed") ?: 0,
            hasMoreComments = boolean(root, "hasMoreComments") ?: false,
            sourcePageUrl = text(root, "sourcePageUrl")
        )
    }

    private fun getJson(path: String, timeoutMs: Int, microCacheMs: Long): JSONObject {
        val url = baseUrl() + path
        return JSONObject(
            StockHttpClient.get(
                url = url,
                timeoutMs = timeoutMs,
                emptyMessage = "股票原生页面接口返回为空",
                microCacheMs = microCacheMs
            )
        )
    }

    private fun parseQuote(value: JSONObject?): StockNativeQuote {
        val item = value ?: JSONObject()
        return StockNativeQuote(
            code = text(item, "code"),
            name = text(item, "name"),
            market = text(item, "market"),
            price = text(item, "price").ifBlank { "--" },
            changeAmount = text(item, "changeAmount").ifBlank { "--" },
            changePercent = text(item, "changePercent").ifBlank { "--" },
            open = text(item, "open").ifBlank { "--" },
            high = text(item, "high").ifBlank { "--" },
            low = text(item, "low").ifBlank { "--" },
            previousClose = double(item, "previousClose")?.toFloat() ?: 0f,
            amount = text(item, "amount").ifBlank { "--" },
            volume = text(item, "volume").ifBlank { "--" }
        )
    }

    private fun parseMinutePoints(rows: JSONArray?): List<StockMinutePoint> {
        if (rows == null) return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val price = double(item, "price")?.toFloat() ?: continue
                if (price <= 0f) continue
                add(
                    StockMinutePoint(
                        time = text(item, "time").ifBlank { text(item, "date") },
                        price = price,
                        average = double(item, "average")?.toFloat() ?: price,
                        volumeRatio = double(item, "volumeRatio")?.toFloat() ?: 0f,
                        volume = double(item, "volume")?.toFloat() ?: 0f,
                        phase = text(item, "phase").ifBlank { "continuous" }
                    )
                )
            }
        }
    }

    private fun parseKlinePoints(rows: JSONArray?): List<StockKLinePoint> {
        if (rows == null) return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val open = double(item, "open")?.toFloat() ?: continue
                val close = double(item, "close")?.toFloat() ?: continue
                if (open <= 0f || close <= 0f) continue
                add(
                    StockKLinePoint(
                        date = text(item, "date"),
                        open = open,
                        close = close,
                        high = double(item, "high")?.toFloat() ?: maxOf(open, close),
                        low = double(item, "low")?.toFloat() ?: minOf(open, close),
                        volume = double(item, "volume")?.toFloat() ?: 0f,
                        amount = double(item, "amount")?.toFloat() ?: 0f,
                        changePercent = text(item, "changePercent").ifBlank { "--" },
                        amplitude = text(item, "amplitude").ifBlank { "--" },
                        changeAmount = text(item, "changeAmount").ifBlank { "--" },
                        turnoverRate = text(item, "turnoverRate").ifBlank { "--" }
                    )
                )
            }
        }
    }

    private fun parseSectorLinks(rows: JSONArray?): List<StockNativeSectorLink> {
        if (rows == null) return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val code = text(item, "code", "sectorCode")
                if (code.isBlank()) continue
                add(
                    StockNativeSectorLink(
                        code = code,
                        name = text(item, "name", "sectorName").ifBlank { code },
                        type = text(item, "type").ifBlank { "industry" },
                        changePercent = text(item, "changePercent").ifBlank { "--" }
                    )
                )
            }
        }
    }

    private fun parseIndexLinks(rows: JSONArray?): List<StockNativeIndexLink> {
        if (rows == null) return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val code = text(item, "code")
                if (code.isBlank()) continue
                add(
                    StockNativeIndexLink(
                        code = code,
                        name = text(item, "name").ifBlank { code },
                        changePercent = text(item, "changePercent").ifBlank { "--" }
                    )
                )
            }
        }
    }

    private fun parsePost(value: JSONObject?): StockNativeDiscussionPost {
        val item = value ?: JSONObject()
        return StockNativeDiscussionPost(
            postId = text(item, "postId"),
            title = text(item, "title"),
            author = text(item, "author").ifBlank { "股吧用户" },
            publishedAt = text(item, "publishedAt"),
            content = text(item, "content"),
            likeCount = int(item, "likeCount") ?: 0,
            sourceUrl = text(item, "sourceUrl")
        )
    }

    private fun parseComments(rows: JSONArray?): List<StockNativeDiscussionComment> {
        if (rows == null) return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                add(
                    StockNativeDiscussionComment(
                        commentId = text(item, "commentId").ifBlank { "comment-$index" },
                        author = text(item, "author").ifBlank { "股吧用户" },
                        content = text(item, "content"),
                        publishedAt = text(item, "publishedAt"),
                        likeCount = int(item, "likeCount") ?: 0,
                        replyCount = int(item, "replyCount") ?: 0,
                        replies = parseReplies(item.optJSONArray("replies"))
                    )
                )
            }
        }
    }

    private fun parseReplies(rows: JSONArray?): List<StockNativeDiscussionReply> {
        if (rows == null) return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                add(
                    StockNativeDiscussionReply(
                        author = text(item, "author").ifBlank { "股吧用户" },
                        content = text(item, "content"),
                        publishedAt = text(item, "publishedAt"),
                        likeCount = int(item, "likeCount") ?: 0
                    )
                )
            }
        }
    }

    private fun text(obj: JSONObject?, vararg keys: String): String {
        if (obj == null) return ""
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = obj.opt(key)?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value != "null" && value != "NaN") return value
        }
        return ""
    }

    private fun int(obj: JSONObject?, vararg keys: String): Int? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = when (val raw = obj.opt(key)) {
                is Number -> raw.toInt()
                is String -> raw.replace(",", "").toDoubleOrNull()?.toInt()
                else -> null
            }
            if (value != null) return value
        }
        return null
    }

    private fun double(obj: JSONObject?, vararg keys: String): Double? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = when (val raw = obj.opt(key)) {
                is Number -> raw.toDouble()
                is String -> raw.replace(",", "").replace("%", "").toDoubleOrNull()
                else -> null
            }
            if (value != null && !value.isNaN()) return value
        }
        return null
    }

    private fun boolean(obj: JSONObject?, vararg keys: String): Boolean? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            return when (val raw = obj.opt(key)) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                is String -> raw.equals("true", true) || raw == "1"
                else -> null
            }
        }
        return null
    }

    private fun encode(value: String): String = URLEncoder.encode(value.trim(), "UTF-8")

    private fun baseUrl(): String = proxyBaseUrl.trim().trimEnd('/').ifBlank {
        throw IllegalStateException("股票代理地址为空")
    }
}
