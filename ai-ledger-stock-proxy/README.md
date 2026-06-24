# AI Ledger A股行情爬虫教学代理

这是给 AI Ledger Android 原生 App 使用的轻量后端代理。当前方案优先做本地可运行的爬虫教学接口，数据源使用东方财富公开 JSON，解析个股 quote、日 K 和分时数据。

当前保留三个入口：

- `GET /api/stock/crawl/a-share/detail?query=600519`：爬虫教学接口，直接说明东方财富公开 JSON 来源。
- `GET /api/stock/a-share/detail?query=600519`：聚合详情接口，当前复用同一套东方财富解析结果，后续可在这里接正式授权源。
- `GET /api/stock/futu/a-share/detail?query=600519`：临时兼容入口，给 Android 端已有富途优先路径过渡使用。

## A 股全量个股接口

第一版全量能力聚焦 A 股个股行情池，不覆盖港股、美股、基金、期货等市场。

```text
GET /api/stock/crawl/a-share/list?page=1&pageSize=100
GET /api/stock/crawl/a-share/search?query=贵州茅台
GET /api/stock/crawl/a-share/quotes?codes=600519,000001,300750
GET /api/stock/crawl/a-share/detail?query=600519&mode=full
GET /api/stock/crawl/a-share/kline?query=600519&period=daily
GET /api/stock/crawl/a-share/minute?query=600519
GET /api/stock/crawl/a-share/market/overview?query=600519
```

兼容聚合路径也保留了同名能力：

```text
GET /api/stock/a-share/list
GET /api/stock/a-share/search?query=贵州茅台
GET /api/stock/a-share/quotes?codes=600519,000001
GET /api/stock/a-share/kline?query=600519&period=weekly
GET /api/stock/a-share/minute?query=600519
```

## Windows PowerShell 本地运行

在仓库根目录执行：

```powershell
cd ai-ledger-stock-proxy
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
python -m uvicorn main:app --host 127.0.0.1 --port 8000 --reload
```

如果 PowerShell 阻止激活脚本，可以只对当前窗口放开策略：

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
```

## 测试接口

健康检查：

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/health"
```

测试爬虫教学接口：

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/stock/crawl/a-share/detail?query=600519"
```

测试股票池列表：

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/stock/crawl/a-share/list?page=1&pageSize=20"
```

测试搜索：

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/stock/crawl/a-share/search?query=贵州茅台"
```

测试批量报价：

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/stock/crawl/a-share/quotes?codes=600519,000001,300750"
```

测试 K 线和分时：

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/stock/crawl/a-share/kline?query=600519&period=daily"
Invoke-RestMethod "http://127.0.0.1:8000/api/stock/crawl/a-share/minute?query=600519"
```

测试聚合接口：

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/stock/a-share/detail?query=600519"
```

测试临时兼容入口：

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/stock/futu/a-share/detail?query=600519"
```

也可以按名称查询：

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/stock/crawl/a-share/detail?query=贵州茅台"
```

返回字段会适配 Android App 当前的 `StockRepository`：

- `quote`：个股报价、涨跌幅、市值、估值和成交信息。
- `kLinePoints`：日 K 数据，来自东方财富 `kline/get`。
- `minutePoints`：分时数据，来自东方财富 `trends2/get`。
- `fundamentals`：由 quote 字段整理出的基础指标。
- `dataSourceLabel`：数据源说明。
- `warnings`：教学源、兼容入口、缓存等运行说明。
- `aiSummary`：看盘摘要文本。

## 注意

东方财富公开 JSON 适合本地学习、原型验证和字段适配演示。正式对外发布看盘软件时，需要确认行情展示授权、缓存规则、延迟标识和商用范围；聚合接口保留下来就是为了后续平滑切到正式授权源。

## 统一低延迟实时接口

Android 新版主入口：

```text
GET /api/stock/a-share/realtime?query=600396&ndays=1
GET /api/stock/a-share/realtime?query=600396&ndays=5
```

`ndays=1` 返回当日分时；`ndays=5` 首次加载真实五个交易日分时，之后热点后台任务只刷新当日数据并按 `timestamp` 合并。接口一次返回：

- `quote`：实时报价。
- `minutePoints`：包含 `date`、`time`、`timestamp`、价格、均价和成交量。
- `sellLevels` / `buyLevels`：真实五档盘口；上游缺失时保留已有盘口缓存，不伪造。
- `tradeTicks`：优先真实逐笔成交；无法取得时明确标记 `derived` warning。
- `cacheHit`、`cacheAgeMs`、`upstreamLatencyMs`、`totalLatencyMs`、`sourceHost`、`sourceTimestamp`、`payloadBytes`：性能和来源元数据。

旧入口继续兼容，并复用同一份实时缓存：

```text
GET /api/stock/a-share/quotes?codes=600396,000001,300750
GET /api/stock/a-share/minute?query=600396
GET /api/stock/crawl/a-share/quotes?codes=600396,000001
GET /api/stock/crawl/a-share/minute?query=600396
```

实时运行时使用持久 `httpx.AsyncClient`、每股票分层缓存、singleflight、stale-while-revalidate、数据源健康选择和热点股票后台刷新。`push2delay` 仅作为主源失败后的 fallback。非交易时段不会持续每秒主动刷新。

缓存逻辑键包括：

```text
quote:{code}
minute:1d:{code}
minute:5d:{code}
depth:{code}
ticks:{code}
resolve:{query}
kline:{period}:{code}
```

当前实现是可靠的单进程内存缓存。单 worker 部署无需 Redis；多 worker 或多实例部署建议增加 Redis 共享缓存和分布式锁，否则 singleflight 只能在各进程内部生效。Redis 不属于服务启动的强依赖。

诊断接口：

```text
GET /api/stock/a-share/realtime/diagnostics
```

它返回缓存项、热点股票、singleflight 等待次数、逻辑刷新次数和各上游域名的成功率/延迟，不返回完整行情 JSON。

## 实时接口验收

PowerShell：

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/stock/a-share/realtime?query=600396&ndays=1"
Invoke-RestMethod "http://127.0.0.1:8000/api/stock/a-share/realtime?query=600396&ndays=5"
Invoke-RestMethod "http://127.0.0.1:8000/api/stock/a-share/realtime/diagnostics"
```

语法和单元测试：

```powershell
python -m py_compile main.py realtime_runtime.py scripts/benchmark_realtime.py
python -m unittest discover -s tests -v
```

100 次、10 并发压测：

```powershell
python scripts/benchmark_realtime.py --base-url http://127.0.0.1:8000 --query 600396 --requests 100 --concurrency 10
```

WebSocket 暂未加入。本轮优先保证 HTTP snapshot、缓存合并和故障降级完整可靠；Android 可先按 1 秒轮询统一 realtime 接口，后续 WebSocket 仍可在此协议上发送 snapshot/delta，而 HTTP 保留为降级路径。
