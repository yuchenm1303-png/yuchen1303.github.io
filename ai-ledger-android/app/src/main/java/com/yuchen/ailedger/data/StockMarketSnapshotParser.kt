package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.StockIndexSnapshot
import com.yuchen.ailedger.model.StockInformationItem
import com.yuchen.ailedger.model.StockMarketBoard
import com.yuchen.ailedger.model.StockMarketBreadth
import com.yuchen.ailedger.model.StockMarketHomeSnapshot
import com.yuchen.ailedger.model.StockMarketSentiment
import com.yuchen.ailedger.model.StockModuleMeta
import com.yuchen.ailedger.model.StockModuleStatus
import com.yuchen.ailedger.model.StockRankItem
import com.yuchen.ailedger.model.StockSectorSnapshot
import org.json.JSONObject

/** 市场首页完整快照与分阶段响应共用的唯一解析入口。 */
internal object StockMarketSnapshotParser {
    fun parse(root: JSONObject): StockMarketHomeSnapshot {
        val payload = StockJsonReader.payloadObject(root)
        val indicesModule = payload.optJSONObject("indices")
        val breadthModule = payload.optJSONObject("marketBreadth")
        val sentimentModule = payload.optJSONObject("sentiment")
        val sectorModule = payload.optJSONObject("sectorHotRanking")
        val marketNewsModule = payload.optJSONObject("marketNews")
        val popularityModule = payload.optJSONObject("popularityRanking")
        val limitUpModule = payload.optJSONObject("limitUpSummary")
        val boards = buildList {
            addBoard(payload, "gainers", "涨幅榜", "真实涨幅排序")
            addBoard(payload, "losers", "跌幅榜", "真实跌幅排序")
            addBoard(payload, "amountRanking", "成交额榜", "真实成交额排序")
            addBoard(payload, "turnoverRanking", "换手率榜", "真实换手率排序")
            addBoard(payload, "volumeRatioRanking", "量比榜", "真实量比排序")
            addBoard(payload, "speedRanking", "涨速榜", "真实涨速排序")
            addBoard(payload, "mainInflowRanking", "主力净流入榜", "真实主力净流入排序")
            addBoard(payload, "mainOutflowRanking", "主力净流出榜", "真实主力净流出排序")
        }
        return StockMarketHomeSnapshot(
            indices = parseIndices(indicesModule),
            indicesMeta = metaFromModule(indicesModule),
            marketBreadth = parseBreadth(breadthModule),
            sentiment = parseSentiment(sentimentModule),
            boards = boards.distinctBy { it.title },
            sectors = parseSectors(sectorModule),
            marketNews = parseInformationItems(marketNewsModule),
            marketNewsMeta = metaFromModule(marketNewsModule),
            popularityMeta = metaFromModule(popularityModule),
            limitUpMeta = metaFromModule(limitUpModule),
            updatedAt = StockJsonReader.firstText(payload, "updatedAt").orEmpty(),
            warnings = StockJsonReader.stringList(payload.optJSONArray("warnings"))
        )
    }

    fun metaFromModule(module: JSONObject?): StockModuleMeta {
        if (module == null) return StockModuleMeta()
        return StockModuleMeta(
            status = StockModuleStatus.fromWire(StockJsonReader.firstText(module, "status")),
            source = StockJsonReader.firstText(module, "source").orEmpty(),
            sourceUrlType = StockJsonReader.firstText(module, "sourceUrlType").orEmpty(),
            updatedAt = StockJsonReader.firstText(module, "updatedAt").orEmpty(),
            cacheAgeMs = StockJsonReader.firstLong(module, "cacheAgeMs") ?: 0L,
            isDerived = StockJsonReader.firstBoolean(module, "isDerived") ?: false,
            warnings = StockJsonReader.stringList(module.optJSONArray("warnings"))
        )
    }

    fun parseInformationItems(module: JSONObject?): List<StockInformationItem> {
        val meta = metaFromModule(module)
        if (!meta.hasRealData) return emptyList()
        val array = StockJsonReader.moduleItemsArray(module) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val title = StockJsonReader.firstText(item, "title", "name").orEmpty()
                if (title.isBlank()) continue
                add(
                    StockInformationItem(
                        id = StockJsonReader.firstText(item, "id", "reportId").orEmpty(),
                        title = title,
                        summary = StockJsonReader.firstText(item, "summary", "description").orEmpty(),
                        publishTime = StockJsonReader.firstText(item, "publishTime", "time", "updatedAt").orEmpty(),
                        source = StockJsonReader.firstText(item, "source", "institution").orEmpty(),
                        url = StockJsonReader.firstText(item, "url", "attachmentUrl").orEmpty()
                    )
                )
            }
        }
    }

    private fun MutableList<StockMarketBoard>.addBoard(
        root: JSONObject,
        key: String,
        title: String,
        subtitle: String
    ) {
        val module = root.optJSONObject(key) ?: return
        val items = parseRankingItems(module)
        val meta = metaFromModule(module)
        if (items.isNotEmpty() && meta.hasRealData) {
            add(
                StockMarketBoard(
                    title = title,
                    subtitle = "$subtitle · ${meta.source.ifBlank { "公开真实数据" }}",
                    items = items
                )
            )
        }
    }

    private fun parseIndices(module: JSONObject?): List<StockIndexSnapshot> {
        val array = StockJsonReader.moduleItemsArray(module) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = StockJsonReader.firstText(item, "name").orEmpty()
                val value = StockJsonReader.firstText(item, "price", "value").orEmpty()
                if (name.isBlank() || value.isBlank() || value == "--") continue
                val changePercent = StockJsonReader.firstText(item, "changePercent", "pct")
                    .orEmpty()
                    .ifBlank { "--" }
                add(
                    StockIndexSnapshot(
                        name = name,
                        value = value,
                        changePercent = changePercent,
                        isRising = !changePercent.startsWith("-")
                    )
                )
            }
        }
    }

    private fun parseBreadth(module: JSONObject?): StockMarketBreadth {
        val item = StockJsonReader.moduleItemsObject(module)
        return StockMarketBreadth(
            upCount = StockJsonReader.firstInt(item, "upCount"),
            downCount = StockJsonReader.firstInt(item, "downCount"),
            flatCount = StockJsonReader.firstInt(item, "flatCount"),
            limitUpCount = StockJsonReader.firstInt(item, "limitUpCount"),
            limitDownCount = StockJsonReader.firstInt(item, "limitDownCount"),
            brokenBoardCount = StockJsonReader.firstInt(item, "brokenBoardCount"),
            brokenBoardRate = StockJsonReader.firstDouble(item, "brokenBoardRate"),
            maxConsecutiveBoards = StockJsonReader.firstInt(item, "maxConsecutiveBoards"),
            redRate = StockJsonReader.firstDouble(item, "redRate"),
            medianChangePercent = StockJsonReader.firstDouble(item, "medianChangePercent"),
            marketAmount = StockJsonReader.firstText(item, "marketAmount").orEmpty().ifBlank { "--" },
            shszAmount = StockJsonReader.firstText(item, "shszAmount").orEmpty().ifBlank { "--" },
            bjAmount = StockJsonReader.firstText(item, "bjAmount").orEmpty().ifBlank { "--" },
            moneyMakingEffect = StockJsonReader.firstDouble(item, "moneyMakingEffect"),
            updatedAt = StockJsonReader.firstText(item, "updatedAt").orEmpty(),
            meta = metaFromModule(module)
        )
    }

    private fun parseSentiment(module: JSONObject?): StockMarketSentiment {
        val item = StockJsonReader.moduleItemsObject(module)
        return StockMarketSentiment(
            temperature = StockJsonReader.firstDouble(item, "sentimentTemperature", "temperature"),
            level = StockJsonReader.firstText(item, "sentimentLevel", "level").orEmpty(),
            formula = StockJsonReader.firstText(item, "formula").orEmpty(),
            redRate = StockJsonReader.firstDouble(item, "redRate"),
            limitUpCount = StockJsonReader.firstInt(item, "limitUpCount"),
            moneyMakingEffect = StockJsonReader.firstDouble(item, "moneyMakingEffect"),
            meta = metaFromModule(module)
        )
    }

    private fun parseSectors(module: JSONObject?): List<StockSectorSnapshot> {
        val array = StockJsonReader.moduleItemsArray(module) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val code = StockJsonReader.firstText(item, "sectorCode", "code").orEmpty()
                val name = StockJsonReader.firstText(item, "sectorName", "name").orEmpty()
                if (code.isBlank() && name.isBlank()) continue
                add(
                    StockSectorSnapshot(
                        sectorCode = code,
                        sectorName = name.ifBlank { code },
                        type = StockJsonReader.firstText(item, "type").orEmpty(),
                        changePercent = StockJsonReader.firstText(item, "changePercent", "pct")
                            .orEmpty()
                            .ifBlank { "--" },
                        upCount = StockJsonReader.firstInt(item, "upCount"),
                        downCount = StockJsonReader.firstInt(item, "downCount"),
                        flatCount = StockJsonReader.firstInt(item, "flatCount"),
                        leaderName = StockJsonReader.firstText(item, "leaderName").orEmpty(),
                        leaderChangePercent = StockJsonReader.firstText(item, "leaderChangePercent").orEmpty(),
                        amount = StockJsonReader.firstText(item, "amount").orEmpty(),
                        turnoverRate = StockJsonReader.firstText(item, "turnoverRate").orEmpty(),
                        mainInflow = StockJsonReader.firstText(item, "mainInflow").orEmpty(),
                        heatRank = StockJsonReader.firstInt(item, "heatRank"),
                        updatedAt = StockJsonReader.firstText(item, "updatedAt").orEmpty()
                    )
                )
            }
        }
    }

    private fun parseRankingItems(module: JSONObject?): List<StockRankItem> {
        val array = StockJsonReader.moduleItemsArray(module) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val code = StockJsonReader.firstText(item, "code", "symbol").orEmpty()
                val name = StockJsonReader.firstText(item, "name", "stockName").orEmpty()
                if (code.isBlank() && name.isBlank()) continue
                val changePercent = StockJsonReader.firstText(item, "changePercent", "pct")
                    .orEmpty()
                    .ifBlank { "--" }
                add(
                    StockRankItem(
                        name = name.ifBlank { code },
                        code = code,
                        value = rankingDisplayValue(item),
                        changePercent = changePercent,
                        isRising = !changePercent.startsWith("-")
                    )
                )
            }
        }
    }

    private fun rankingDisplayValue(item: JSONObject): String {
        for (key in listOf(
            "mainInflow",
            "amount",
            "turnoverRate",
            "volumeRatio",
            "changeSpeed",
            "price",
            "value"
        )) {
            StockJsonReader.firstText(item, key)
                ?.takeIf { it.isNotBlank() && it != "--" }
                ?.let { return it }
        }
        return "--"
    }
}
