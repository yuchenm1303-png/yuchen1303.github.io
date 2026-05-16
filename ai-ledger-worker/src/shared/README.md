# Worker shared helpers

这个目录用于沉淀 Worker 侧可复用的小工具函数，目标是逐步减少 `index-orchestrator.js`、`index-attachments-gateway.js`、`index-orchestrator-diagnostics.js`、`index.js` 里的重复代码。

当前原则：

```txt
先新增 shared 文档和纯函数
再从短文件小步接入
最后才整理 index-orchestrator.js
```

不要为了“看起来清爽”一次性重写长文件。

## 当前模块

### response.js

职责：统一 JSON response、CORS 和 OPTIONS 响应。

当前导出：

```js
json(payload, status, headers)
cors(request, env)
optionsResponse(headers)
JSON_HEADERS
```

适合替换的重复逻辑：

```txt
const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" }
function json(...)
function cors(...)
OPTIONS 204 response
```

### model-meta.js

职责：统一 provider/model/modelLabel 结构，避免前端气泡标签和真实模型不一致。

当前导出：

```js
modelMeta(provider, model, label)
appendRunLabel(version, label)
normalizeModelPreference(value)
```

适合替换的重复逻辑：

```txt
modelMeta
appendRunLabel
normalizeModelPreference
```

这些函数在附件网关和诊断外壳里都有相似实现，后续可以逐步收敛。

## 接入顺序建议

为了降低风险，建议按这个顺序接入 shared：

```txt
1. index-attachments-gateway.js
   原因：结构清楚，函数边界相对独立。

2. index-orchestrator-diagnostics.js
   原因：诊断外壳较独立，适合复用 model-meta 和 response。

3. index.js
   原因：command protocol 逻辑完整，但不是当前主入口。

4. index-orchestrator.js
   原因：当前主业务核心，最后再碰。
```

## 每次接入规则

每次只替换一种 helper，例如：

```txt
只替换 json/cors
或只替换 modelMeta/appendRunLabel
或只替换 normalizeModelPreference
```

不要一次同时移动 provider 调用、prompt、fallback 和 response。

## 禁止事项

```txt
不要在 shared 里写业务流程
不要让 shared 依赖具体 provider API key
不要让 shared 直接调用 Gemini/NVIDIA/Tavily/Workers AI
不要让 shared 直接读写业务状态
不要把 Orchestrator 大段逻辑搬进 shared
```

shared 只放：

```txt
纯函数
通用响应函数
通用模型元信息函数
通用清洗函数
小型无副作用 parser
```

## 后续可新增模块

建议后续逐步新增：

```txt
shared/text.js              cleanText / truncate / safeString
shared/date.js              normalizeIsoDate / shiftDate
shared/command-schema.js    allowed intents / action schema
shared/diagnostics.js       readableError / timeout helper
shared/provider-timeout.js  fetchWithTimeout / raceWithTimeout
```

新增模块时先写文档，再小步接入。

## 测试要求

接入 shared 后至少测试：

```txt
GET /health
OPTIONS /
普通聊天
tackle是什么意思
重庆今天天气如何
今天有什么大新闻吗
上传图片并提取文字
导航坐公交去重庆大学
明天早上8点叫我起床
```

如果只改 Worker，需要跑：

```txt
GitHub Actions → Deploy AI Worker → dev-update-1
```

如果只改文档，不需要部署。
