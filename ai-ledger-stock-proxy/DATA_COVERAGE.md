# A 股数据覆盖与真实盘口说明

本轮只修改 `ai-ledger-stock-proxy/` 后端、测试和文档，没有修改 `ai-ledger-android/**`。新增接口统一遵守：能从公开真实数据源取得的数据才返回真实数组；不能稳定核验的数据返回 `status=unavailable`、空数组和明确 `warnings`，不生成示例、模板、模拟或虚构股票数据。

## 新增或扩充接口

```text
GET  /api/stock/a-share/popularity?query=600396
GET  /api/stock/a-share/rankings/popularity?limit=50
GET  /api/stock/a-share/rankings?type=gainers|losers|amount|turnover|volume_ratio|speed|new_high|new_low|main_inflow|main_outflow
GET  /api/stock/a-share/market/breadth
GET  /api/stock/a-share/market/sentiment
GET  /api/stock/a-share/indices
GET  /api/stock/a-share/sectors?type=industry|concept|region
GET  /api/stock/a-share/sectors/{sectorCode}/constituents
GET  /api/stock/a-share/sectors/flow
GET  /api/stock/a-share/market/home
GET  /api/stock/a-share/stock/full?query=600396
POST /api/stock/a-share/watchlist/quotes
GET  /api/stock/a-share/limit-up
GET  /api/stock/a-share/limit-down
GET  /api/stock/a-share/limit-chain
GET  /api/stock/a-share/broken-board
GET  /api/stock/a-share/auction
GET  /api/stock/a-share/abnormal
GET  /api/stock/a-share/suspensions
GET  /api/stock/a-share/dragon-tiger
GET  /api/stock/a-share/dragon-tiger/{code}
GET  /api/stock/a-share/block-trades
GET  /api/stock/a-share/block-trades/{code}
GET  /api/stock/a-share/capital/stock?query=600396
GET  /api/stock/a-share/capital/market
GET  /api/stock/a-share/capital/northbound
GET  /api/stock/a-share/capital/margin?query=600396
GET  /api/stock/a-share/capital/etf
GET  /api/stock/a-share/profile?query=600396
GET  /api/stock/a-share/financials?query=600396&period=quarterly
GET  /api/stock/a-share/announcements?query=600396
GET  /api/stock/a-share/news?query=600396
GET  /api/stock/a-share/news/market
GET  /api/stock/a-share/news/sectors?sectorCode=BKxxxx
GET  /api/stock/a-share/research?query=600396
GET  /api/stock/a-share/research/latest
GET  /api/stock/a-share/performance-forecast?query=600396
GET  /api/stock/a-share/shareholders?query=600396
GET  /api/stock/a-share/unlocks?query=600396
GET  /api/stock/a-share/dividends?query=600396
```

## 当前真实数据源

- `realtime`、`quotes`、`minute`：东方财富 `qt/stock/get`、`trends2/get`、`details/get`，腾讯五日分时作为真实历史分时补源。
- `kline`：东方财富 `qt/stock/kline/get` 优先，腾讯真实 K 线兜底。
- `rankings`：东方财富 `qt/clist/get`，不同榜单使用不同真实排序字段，例如 `f3/f6/f8/f10/f22/f62`。
- `indices`：东方财富指数 quote，覆盖上证、深证、创业板、沪深300、科创50、中证A500、上证50、中证500、中证1000、北证50。
- `market/breadth`：东方财富 A 股股票池 quote 统计上涨、下跌、平盘和涨跌停近似计数。
- `market/sentiment`：基于真实市场宽度公式派生，返回 `isDerived=true` 和公式说明。
- `sectors`：东方财富板块 `qt/clist/get`，支持行业、概念、地域。

## 暂时 unavailable 的模块

当前没有稳定核验公开 JSON 的模块会返回 `unavailable`：真实人气排名/人气榜、公告、新闻、研报、龙虎榜、大宗交易、停复牌、异动、集合竞价、涨停池明细、跌停池明细、连板梯队、炸板、北向资金、融资融券、ETF 资金、公司资料、财务报表、业绩预告、股东、解禁、分红配股。

后续接入真实源时应只填真实字段；缺字段保持空，不用规则推算。

## 五档盘口

生产路径已经禁用 `_fallback_order_book_from_quote()` 生成模拟盘口。`sellLevels` 和 `buyLevels` 只来自东方财富 quote raw 的真实五档字段：

- 卖一至卖五：`f31/f32`、`f33/f34`、`f35/f36`、`f37/f38`、`f39/f40`
- 买一至买五：`f19/f20`、`f17/f18`、`f15/f16`、`f13/f14`、`f11/f12`

盘口返回增加：

```json
{
  "depthStatus": "ok | partial | empty | unavailable | stale",
  "depthSource": "eastmoney_push2",
  "depthIsDerived": false,
  "depthUpdatedAt": "ISO时间",
  "depthSourceTimestamp": "ISO时间",
  "depthCacheAgeMs": 0,
  "depthWarnings": []
}
```

规则：上游缺档不补造；上游为空且没有真实旧缓存时返回空数组和 `unavailable`；有同股票真实旧缓存时返回 `stale`；缓存 key 按股票代码隔离；卖盘按卖一到卖五递增，买盘按买一到买五递减；卖一不得低于买一；涨停价字段存在时过滤高于涨停价的卖单，跌停价字段存在时过滤低于跌停价的买单；`depthIsDerived` 始终为 `false`。

## 缓存 TTL

- 实时报价、分时、盘口、逐笔：约 1 秒，支持 singleflight 与 stale true cache。
- K 线：沿用慢刷新缓存。
- 榜单、指数、宽度、情绪、板块：沿用后端通用缓存，目前约 18 秒。
- 公司慢数据、公告、新闻、研报、财务等：接口已预留，当前不可用模块不返回本地 sample。
