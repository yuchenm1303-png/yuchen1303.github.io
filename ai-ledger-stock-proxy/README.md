# AI Ledger A股行情代理

这是给 Android 原生 Compose App 使用的轻量行情代理服务。

当前数据源：

- AKShare 免费行情接口
- 后续可继续接 Tushare Pro / 正式授权行情源

为什么需要代理：

- App 不直接暴露行情 token
- 第三方接口变化时只改后端
- 可以统一字段、缓存、限流和降级
- 后续切正式授权源时 App 不需要大改

## 本地运行

```bash
cd ai-ledger-stock-proxy
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

Windows PowerShell：

```powershell
cd ai-ledger-stock-proxy
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

## 健康检查

```text
GET /health
```

## 个股详情接口

```text
GET /api/stock/a-share/detail?query=600519
GET /api/stock/a-share/detail?query=贵州茅台
```

返回字段会适配 Android App 当前的 `StockRepository`：

- quote：个股报价
- kLinePoints：日K数据
- minutePoints：分时点位
- dataSourceLabel：数据源说明
- aiSummary：看盘摘要文本

## Android 端接入

Android 端 `StockRepository` 已经预留：

```kotlin
StockRepository(proxyBaseUrl = "https://你的代理服务域名")
```

后续把代理服务部署到 Render / Railway / 自己的服务器后，把这个地址传进去即可。

## 注意

AKShare 适合开发、研究和原型验证。正式对外发布看盘软件时，需要确认行情展示授权、缓存规则、延迟标识和商用范围。
