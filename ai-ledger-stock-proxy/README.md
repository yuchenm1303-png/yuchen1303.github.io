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
